package com.easytier.android.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.easytier.android.EasyTierApp
import com.easytier.android.MainActivity
import com.easytier.android.R
import com.easytier.jni.EasyTierJNI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
/**
 * EasyTier VPN 前台服务。
 *
 * 职责：
 * 1. 建立系统 TUN 接口（按虚拟 IP + 代理网段路由）
 * 2. 将 TUN fd 传递给 Rust 核心（setTunFd）
 * 3. 轮询 DHCP 变化（虚拟 IP / 代理网段变化时重建 TUN）
 */
class EasyTierVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var instanceName: String? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentIpv4: String? = null
    private var currentProxyCidrs: List<String> = emptyList()
    private var currentMagicDns = false
    private var currentDnsZone: String? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                // 通知栏停止：停 TUN + 停全部网络实例（服务与网络一起关）
                startForeground(NOTIFICATION_ID, buildNotification("正在停止"))
                stopVpn()
                stopAllExternally()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_TUN -> {
                // 仅关 TUN 与前台服务，不动核心实例（服务/网络解耦）
                startForeground(NOTIFICATION_ID, buildNotification("正在停止"))
                stopVpn()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        instanceName = intent?.getStringExtra(EXTRA_INSTANCE_NAME)
        val ipv4 = intent?.getStringExtra(EXTRA_IPV4)
        val cidrs = intent?.getStringArrayListExtra(EXTRA_PROXY_CIDRS).orEmpty()
        val magicDns = intent?.getBooleanExtra(EXTRA_MAGIC_DNS, false) ?: false
        val dnsZone = intent?.getStringExtra(EXTRA_DNS_ZONE)

        if (instanceName == null || ipv4 == null) {
            Log.e(TAG, "缺少必要参数，停止服务")
            // START_STICKY 重建时 intent 为 null：补一次 startForeground 满足前台服务回调义务
            startForeground(NOTIFICATION_ID, buildNotification("启动中"))
            stopSelf()
            return START_NOT_STICKY
        }

        // instanceName 上面已判空返回，模板里不会出现 null
        startForeground(NOTIFICATION_ID, buildNotification("运行中 · $instanceName"))
        acquireMulticastLock()

        scope.launch {
            runCatching { setupVpn(ipv4, cidrs, magicDns, dnsZone) }
                .onFailure { Log.e(TAG, "VPN 建立失败", it); onEstablishFailed(it) }
        }
        return START_STICKY
    }

    private suspend fun setupVpn(
        ipv4Cidr: String,
        proxyCidrs: List<String>,
        magicDns: Boolean,
        dnsZone: String?,
    ) {
        establish(ipv4Cidr, proxyCidrs, magicDns, dnsZone)

        // DHCP 轮询：IP/网段变化时重建（DNS 配置不变，沿用本次启动的值）
        while (scope.isActive && vpnInterface != null) {
            delay(DHCP_POLL_INTERVAL_MS)
            val (newIp, newCidrs) = queryRuntimeNetwork()
            if (newIp != null && (newIp != currentIpv4 || newCidrs != currentProxyCidrs)) {
                Log.i(TAG, "网络变化，重建 TUN: $currentIpv4 -> $newIp")
                establish(newIp, newCidrs, currentMagicDns, currentDnsZone)
            }
        }
    }

    private fun establish(ipv4Cidr: String, proxyCidrs: List<String>, magicDns: Boolean, dnsZone: String?) {
        vpnInterface?.close()

        val (ip, prefix) = splitCidr(ipv4Cidr, 24)
        val builder = Builder()
            .setSession("EasyTier")
            .setBlocking(false)
            .setMtu(MTU)
            .addAddress(ip, prefix)

        if (magicDns) {
            // Magic DNS：核心在 TUN 数据面应答发往 fake IP 的查询（不监听 53 端口），
            // 路由 + 系统 DNS 都指向它；普通域名由核心转发上游（Android 上为 223.5.5.5 等）
            builder.addRoute(MAGIC_DNS_FAKE_IP, 32)
            builder.addDnsServer(MAGIC_DNS_FAKE_IP)
            dnsZone?.let { zone ->
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

        proxyCidrs.forEach { cidr ->
            runCatching {
                val (routeIp, routePrefix) = splitCidr(cidr, 24)
                builder.addRoute(routeIp, routePrefix)
            }.onFailure { Log.w(TAG, "无效路由 $cidr: ${it.message}") }
        }

        val tun = builder.establish() ?: throw IllegalStateException("TUN establish 返回 null")
        vpnInterface = tun
        currentIpv4 = ipv4Cidr
        currentProxyCidrs = proxyCidrs
        currentMagicDns = magicDns
        currentDnsZone = dnsZone

        val name = instanceName
        if (name != null) {
            val rc = EasyTierJNI.setTunFd(name, tun.fd)
            if (rc != 0) {
                throw IllegalStateException("setTunFd 失败: ${EasyTierJNI.getLastError()}")
            }
        }
        Log.i(TAG, "TUN 已建立: $ip/$prefix, 路由 ${proxyCidrs.size} 条${if (magicDns) ", Magic DNS($dnsZone)" else ""}")
    }

    /** 从核心查询当前虚拟 IP 与代理网段。 */
    private fun queryRuntimeNetwork(): Pair<String?, List<String>> = runCatching {
        val raw = EasyTierJNI.collectNetworkInfos(8) ?: return@runCatching null to emptyList()
        val info = parseRuntimeInfo(raw, instanceName) ?: return@runCatching null to emptyList()
        val ip = info.myNodeInfo?.virtualIpv4?.toCidrString()
        val cidrs = info.routes.flatMap { it.proxyCidrs }.distinct()
        ip to cidrs
    }.getOrDefault(null to emptyList())

    private fun parseRuntimeInfo(
        raw: String,
        name: String?,
    ): com.easytier.android.data.model.NetworkInstanceRunningInfo? = runCatching {
        // 复用数据层模型解析 pbjson
        val map = com.easytier.android.data.model.RuntimeJson.json
            .decodeFromString<com.easytier.android.data.model.NetworkInstanceRunningInfoMap>(raw)
        map.map[name]
    }.getOrNull()

    private fun stopVpn() {
        vpnInterface?.close()
        vpnInterface = null
        releaseMulticastLock()
    }

    /**
     * Android 默认会过滤组播，UPnP SSDP 与局域网 UDP 发现需要 multicast lock。
     * 官方插件清单带 ACCESS_WIFI_STATE；这里显式拿锁，避免打洞/直连退化成中转。
     */
    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        runCatching {
            wifi.createMulticastLock("easytier").apply {
                setReferenceCounted(false)
                acquire()
                multicastLock = this
            }
            Log.i(TAG, "已获取 WiFi multicast lock")
        }.onFailure { Log.w(TAG, "获取 multicast lock 失败: ${it.message}") }
    }

    private fun releaseMulticastLock() {
        val lock = multicastLock ?: return
        multicastLock = null
        runCatching { if (lock.isHeld) lock.release() }
    }

    override fun onRevoke() {
        stopVpn()
        // 服务与网络解耦：VPN 被抢占只重置服务状态，核心实例继续运行
        runCatching { EasyTierApp.get().container.vpnController.onTunDown() }
            .onFailure { Log.w(TAG, "重置服务状态失败", it) }
        stopSelf()
    }

    /**
     * TUN 建立失败（VPN 授权被收回 / 被其他 VPN 占用等）。此前只记日志就停止服务，
     * 用户侧表现为「网络连上了但 VPN 没起来」且磁贴仍是绿色。这里重置服务状态
     * （磁贴/首页回到未运行，下次开启即重试），并发一条可点击授权重试的通知。
     */
    private fun onEstablishFailed(cause: Throwable) {
        runCatching { EasyTierApp.get().container.vpnController.onTunDown() }
            .onFailure { Log.w(TAG, "重置服务状态失败", it) }
        postFailureNotification(cause)
        stopSelf()
    }

    /** 通知栏停止：重置服务开关并停止全部核心实例。 */
    private fun stopAllExternally() {
        runCatching { EasyTierApp.get().container.vpnController.onStoppedExternally() }
            .onFailure { Log.w(TAG, "获取容器失败，跳过停止核心实例", it) }
    }

    override fun onDestroy() {
        scope.cancel()
        stopVpn()
        super.onDestroy()
    }

    // --- 通知 ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "EasyTier VPN",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "EasyTier 网络服务运行状态" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, EasyTierVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle("EasyTier")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .addAction(0, "停止", stopIntent)
            .setOngoing(true)
            .build()
    }

    /** 失败通知：点按打开应用申请授权并自动重试。无通知权限时静默放弃。 */
    private fun postFailureNotification(cause: Throwable) {
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                manager.getNotificationChannel(CHANNEL_ALERT_ID) == null
            ) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ALERT_ID, "连接异常",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply { description = "VPN 建立失败等需要处理的异常" },
                )
            }
            val retryIntent = PendingIntent.getActivity(
                this, 2,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_REQUEST_VPN_AND_START, true),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            manager.notify(
                ALERT_NOTIFICATION_ID,
                NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
                    .setSmallIcon(R.drawable.ic_stat_vpn)
                    .setContentTitle("EasyTier VPN 建立失败")
                    .setContentText("可能未授权或被其他 VPN 占用，点按授权并重试")
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            "可能未授权或被其他 VPN 占用，点按授权并重试。" +
                                "（${cause.message ?: "未知原因"}）"
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
        private const val MTU = 1380
        private const val DHCP_POLL_INTERVAL_MS = 3000L

        const val EXTRA_INSTANCE_NAME = "instance_name"
        const val EXTRA_IPV4 = "ipv4_address"
        const val EXTRA_PROXY_CIDRS = "proxy_cidrs"
        const val EXTRA_MAGIC_DNS = "magic_dns"
        const val EXTRA_DNS_ZONE = "dns_zone"

        /** 核心 Magic DNS 的固定 fake IP（MAGIC_DNS_FAKE_IP），查询由 TUN 数据面拦截应答 */
        private const val MAGIC_DNS_FAKE_IP = "100.100.100.101"
        const val ACTION_STOP = "com.easytier.android.STOP_VPN"
        const val ACTION_STOP_TUN = "com.easytier.android.STOP_VPN_TUN"

        /** 便捷启动。 */
        fun start(
            context: Context,
            instanceName: String,
            ipv4Cidr: String,
            proxyCidrs: List<String>,
            magicDns: Boolean = false,
            dnsZone: String? = null,
        ) {
            val intent = Intent(context, EasyTierVpnService::class.java)
                .putExtra(EXTRA_INSTANCE_NAME, instanceName)
                .putExtra(EXTRA_IPV4, ipv4Cidr)
                .putExtra(EXTRA_MAGIC_DNS, magicDns)
                .putExtra(EXTRA_DNS_ZONE, dnsZone)
                .putStringArrayListExtra(EXTRA_PROXY_CIDRS, ArrayList(proxyCidrs))
            // 磁贴点击时应用可能在后台：O+ 上普通 startService 会被后台启动限制拒绝，
            // 前台服务职责由 onStartCommand 里的 startForeground 满足
            ContextCompat.startForegroundService(context, intent)
        }

        /** 仅关闭 TUN 与前台服务，核心实例不受影响（服务/网络解耦）。 */
        fun stopTun(context: Context) {
            // 磁贴「停止」时应用可能在后台，普通 startService 会被 O+ 后台启动限制拒绝
            ContextCompat.startForegroundService(
                context,
                Intent(context, EasyTierVpnService::class.java).setAction(ACTION_STOP_TUN),
            )
        }

        private fun splitCidr(cidr: String, defaultPrefix: Int): Pair<String, Int> {
            val parts = cidr.trim().split('/')
            return parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: defaultPrefix)
        }
    }
}
