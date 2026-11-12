package com.easytier.android.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.easytier.android.EasyTierApp
import com.easytier.android.MainActivity
import com.easytier.android.R
import com.easytier.android.core.clash.ClashRouteGenerator
import com.easytier.jni.EasyTierJNI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * EasyTier VPN 前台服务。
 *
 * 职责：
 * 1. 按 [VpnController] 规划的 [TunSpec] 建立或热更新系统 TUN 接口
 * 2. 将新 TUN fd 原子切换至 Rust 核心（setTunFd）后再回收旧 fd，消除切网/加路由断流空窗
 *
 * 注：
 * - 已移除独立 3s JNI 轮询协程，统一由 [EasyTierEngine] 自适应轮询 + [VpnController] 差量下发驱动，
 *   消除后台双重唤醒与重复 pbjson 反序列化。
 * - Wi-Fi MulticastLock 已统一由 [NetworkConnectivityMonitor] 按需脉冲调度，不再常驻持锁。
 */
class EasyTierVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var appliedSpec: TunSpec? = null
    private val tunMutex = Mutex()
    private var applyJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        isServiceAlive = true
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // 通知栏停止：停 TUN + 停全部网络实例（服务与网络一起关）
                safeStartForeground(buildNotification(getString(R.string.vpn_state_stopping)))
                stopVpn()
                stopAllExternally()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_TUN -> {
                // 仅关 TUN 与前台服务，不动核心实例（服务/网络解耦）
                safeStartForeground(buildNotification(getString(R.string.vpn_state_stopping)))
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val name = intent?.getStringExtra(EXTRA_INSTANCE_NAME)
        val ipv4 = intent?.getStringExtra(EXTRA_IPV4)
        val cidrs = intent?.getStringArrayListExtra(EXTRA_PROXY_CIDRS).orEmpty()
        val magicDns = intent?.getBooleanExtra(EXTRA_MAGIC_DNS, false) ?: false
        val dnsZone = intent?.getStringExtra(EXTRA_DNS_ZONE)
        val mtu = intent?.getIntExtra(EXTRA_MTU, TunRoutePlanner.DEFAULT_MTU)
            ?: TunRoutePlanner.DEFAULT_MTU

        if (name == null || ipv4 == null) {
            Log.w(TAG, "缺少必要参数或进程重建空 Intent，停止 VPN 服务")
            safeStartForeground(buildNotification(getString(R.string.vpn_state_starting)))
            stopSelf()
            return START_NOT_STICKY
        }

        safeStartForeground(buildNotification(getString(R.string.vpn_state_running, name)))

        val targetSpec = TunSpec(
            instanceName = name,
            ipv4Cidr = ipv4,
            routes = cidrs,
            magicDns = magicDns,
            dnsZone = dnsZone,
            mtu = mtu.coerceIn(TunRoutePlanner.MIN_MTU, TunRoutePlanner.MAX_MTU),
        )

        applyJob?.cancel()
        applyJob = scope.launch {
            tunMutex.withLock {
                if (vpnInterface != null && appliedSpec == targetSpec) {
                    return@withLock
                }
                runCatching { establish(targetSpec) }
                    .onFailure {
                        Log.e(TAG, "VPN 建立失败", it)
                        onEstablishFailed(it)
                    }
            }
        }
        return START_STICKY
    }

    private fun safeStartForeground(notification: Notification) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { err ->
            Log.w(TAG, "VPN startForeground 受限降级为普通状态通知: ${err.message}")
            runCatching {
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun establish(spec: TunSpec) {
        val (ip, prefix) = splitCidr(spec.ipv4Cidr, 24)
        val builder = Builder()
            .setSession("EasyTier")
            .setBlocking(false)
            .setMtu(spec.mtu)
            .addAddress(ip, prefix)

        if (spec.magicDns) {
            // Magic DNS：核心在 TUN 数据面应答发往 fake IP 的查询（不监听 53 端口），
            // 路由 + 系统 DNS 都指向它；普通域名由核心转发配置的上游（DoT/DoH/UDP）
            builder.addRoute(MAGIC_DNS_FAKE_IP, 32)
            builder.addDnsServer(MAGIC_DNS_FAKE_IP)
            spec.dnsZone?.let { zone ->
                runCatching { builder.addSearchDomain(zone) }
                    .onFailure { Log.w(TAG, "添加 DNS 搜索域失败: ${it.message}") }
            }
        } else {
            builder.addDnsServer("223.5.5.5")
            builder.addDnsServer("114.114.114.114")
        }

        // 与官方 TauriVpnService 对齐：给 TUN 一个 ULA IPv6 地址，避免核心 IPv6 路径无接口。
        runCatching { builder.addAddress("fd00::1", 128) }
            .onFailure { Log.w(TAG, "添加 IPv6 地址失败: ${it.message}") }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        // 必须把自身踢出 VPN，否则 STUN / 打洞 UDP 会进 TUN，P2P 永远失败。
        runCatching { builder.addDisallowedApplication(packageName) }
            .onFailure { Log.e(TAG, "无法排除自身应用出 VPN，打洞会走隧道", it) }

        spec.routes.forEach { rawCidr ->
            val normalized = ClashRouteGenerator.normalizeCidr(rawCidr) ?: rawCidr
            runCatching {
                val (routeIp, routePrefix) = splitCidr(normalized, 24)
                builder.addRoute(routeIp, routePrefix)
            }.onFailure { Log.w(TAG, "无效路由 $rawCidr: ${it.message}") }
        }

        val newTun = builder.establish()
            ?: throw IllegalStateException(getString(R.string.vpn_err_establish_null))

        val rc = EasyTierJNI.setTunFd(spec.instanceName, newTun.fd)
        if (rc != 0) {
            runCatching { newTun.close() }
            throw IllegalStateException(
                getString(R.string.vpn_err_set_tun_fd, EasyTierJNI.getLastError()),
            )
        }

        // 先切新 fd 再关旧 fd：消除热更新期间的数据包黑洞与泄漏窗口
        val oldTun = vpnInterface
        vpnInterface = newTun
        appliedSpec = spec
        runCatching { oldTun?.close() }

        Log.i(
            TAG,
            "TUN 已就绪: $ip/$prefix, MTU=${spec.mtu}, 路由 ${spec.routes.size} 条" +
                if (spec.magicDns) ", Magic DNS(${spec.dnsZone})" else "",
        )
    }

    private fun stopVpn() {
        applyJob?.cancel()
        applyJob = null
        appliedSpec = null
        val old = vpnInterface
        vpnInterface = null
        runCatching { old?.close() }
    }

    override fun onRevoke() {
        stopVpn()
        // VPN 被挤掉即整体收摊：核心实例一起停，进程不留在后台空转、不再阻碍系统休眠
        stopAllExternally()
        stopSelf()
    }

    /**
     * TUN 建立失败（VPN 授权被收回 / 被其他 VPN 占用等）。重置服务状态（磁贴/首页回到
     * 未运行），停掉全部核心实例让进程尽快退场，并发一条可点击授权重试的通知。
     */
    private fun onEstablishFailed(cause: Throwable) {
        stopVpn()
        stopAllExternally()
        postFailureNotification(cause)
        stopSelf()
    }

    /** 外部停止（通知栏「停止」/ VPN 被撤销 / TUN 建立失败）：重置服务开关并停全部核心实例。 */
    private fun stopAllExternally() {
        runCatching { EasyTierApp.get().container.vpnController.onStoppedExternally() }
            .onFailure { Log.w(TAG, "获取容器失败，跳过停止核心实例", it) }
    }

    override fun onDestroy() {
        isServiceAlive = false
        scope.cancel()
        stopVpn()
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
        super.onDestroy()
    }

    // --- 通知 ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.vpn_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, EasyTierVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.vpn_action_stop), stopIntent)
            .setOngoing(true)
            .build()
    }

    /** 失败通知：点按打开应用申请授权并自动重试。无通知权限时静默放弃。 */
    private fun postFailureNotification(cause: Throwable) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager.getNotificationChannel(CHANNEL_ALERT_ID) == null
            ) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ALERT_ID,
                        getString(R.string.vpn_alert_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = getString(R.string.vpn_alert_channel_desc) },
                )
            }
            val retryIntent = PendingIntent.getActivity(
                this,
                2,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_REQUEST_VPN_AND_START, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.notify(
                ALERT_NOTIFICATION_ID,
                NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
                    .setSmallIcon(R.drawable.ic_stat_vpn)
                    .setContentTitle(getString(R.string.vpn_alert_title))
                    .setContentText(getString(R.string.vpn_alert_text))
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            getString(
                                R.string.vpn_alert_big_text,
                                cause.message ?: getString(R.string.vpn_alert_unknown),
                            ),
                        ),
                    )
                    .setContentIntent(retryIntent)
                    .setAutoCancel(true)
                    .build(),
            )
        }.onFailure { Log.w(TAG, "发送失败通知出错", it) }
    }

    companion object {
        private const val TAG = "EasyTierVpnService"
        private const val CHANNEL_ID = "easytier_vpn"
        private const val CHANNEL_ALERT_ID = "easytier_vpn_alert"
        private const val NOTIFICATION_ID = 1
        private const val ALERT_NOTIFICATION_ID = 2

        @Volatile
        private var isServiceAlive: Boolean = false

        const val EXTRA_INSTANCE_NAME = "instance_name"
        const val EXTRA_IPV4 = "ipv4_address"
        const val EXTRA_PROXY_CIDRS = "proxy_cidrs"
        const val EXTRA_MAGIC_DNS = "magic_dns"
        const val EXTRA_DNS_ZONE = "dns_zone"
        const val EXTRA_MTU = "mtu"

        /** 核心 Magic DNS 的固定 fake IP（MAGIC_DNS_FAKE_IP），查询由 TUN 数据面拦截应答 */
        private const val MAGIC_DNS_FAKE_IP = "100.100.100.101"
        const val ACTION_STOP = "com.easytier.android.STOP_VPN"
        const val ACTION_STOP_TUN = "com.easytier.android.STOP_VPN_TUN"

        /** 按 [TunSpec] 启动或平滑热更新系统 TUN。 */
        internal fun start(context: Context, spec: TunSpec) {
            start(
                context = context,
                instanceName = spec.instanceName,
                ipv4Cidr = spec.ipv4Cidr,
                proxyCidrs = spec.routes,
                magicDns = spec.magicDns,
                dnsZone = spec.dnsZone,
                mtu = spec.mtu,
            )
        }

        /** 便捷启动。 */
        fun start(
            context: Context,
            instanceName: String,
            ipv4Cidr: String,
            proxyCidrs: List<String>,
            magicDns: Boolean = false,
            dnsZone: String? = null,
            mtu: Int = TunRoutePlanner.DEFAULT_MTU,
        ) {
            val intent = Intent(context, EasyTierVpnService::class.java)
                .putExtra(EXTRA_INSTANCE_NAME, instanceName)
                .putExtra(EXTRA_IPV4, ipv4Cidr)
                .putExtra(EXTRA_MAGIC_DNS, magicDns)
                .putExtra(EXTRA_DNS_ZONE, dnsZone)
                .putExtra(EXTRA_MTU, mtu)
                .putStringArrayListExtra(EXTRA_PROXY_CIDRS, ArrayList(proxyCidrs))
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { Log.e(TAG, "启动 EasyTierVpnService 失败: ${it.message}", it) }
        }

        /** 仅关闭 TUN 与前台服务，核心实例不受影响（服务/网络解耦）。 */
        fun stopTun(context: Context) {
            if (!isServiceAlive) {
                runCatching { context.stopService(Intent(context, EasyTierVpnService::class.java)) }
                return
            }
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, EasyTierVpnService::class.java).setAction(ACTION_STOP_TUN),
                )
            }.onFailure {
                runCatching { context.stopService(Intent(context, EasyTierVpnService::class.java)) }
            }
        }

        private fun splitCidr(cidr: String, defaultPrefix: Int): Pair<String, Int> {
            val parts = cidr.trim().split('/')
            return parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: defaultPrefix)
        }
    }
}
