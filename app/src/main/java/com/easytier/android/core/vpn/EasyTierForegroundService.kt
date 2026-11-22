package com.easytier.android.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.easytier.android.EasyTierApp
import com.easytier.android.MainActivity
import com.easytier.android.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * EasyTier 常驻前台服务（无 TUN / SOCKS5 模式或等待连接时保活）。
 *
 * 职责：
 * 1. 提供常驻前台通知，提升进程优先级为 FOREGROUND_SERVICE (oom_adj <= 100)，
 *    防止系统（如 ColorOS、MIUI、HyperOS 等）将无 TUN 的后台进程冻结或剥夺网络能力。
 * 2. 提供通知栏快捷「停止」操作。
 *
 * 注：Wi-Fi MulticastLock 已收口至 [NetworkConnectivityMonitor] 按需脉冲持锁，
 * 不再在此处无期限常驻持有，避免息屏后阻碍 Wi-Fi 芯片进入 802.11 省电休眠。
 */
class EasyTierForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var recoveryJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "收到通知栏停止指令")
                recoveryJob?.cancel()
                safeStartForeground(buildNotification(getString(R.string.vpn_state_stopping)))
                stopAllExternally()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // START_STICKY 进程被回收后系统以 null intent 拉起，或任务划掉兜底恢复：
        // 若控制器未运行则依据持久化开关决定是否自动恢复服务。
        if (intent == null || intent.action == ACTION_RECOVER) {
            val controller = runCatching { EasyTierApp.get().container.vpnController }.getOrNull()
            if (controller != null && controller.isTunEstablished()) {
                safeStartForeground(buildNotification(getString(R.string.vpn_state_starting)))
                stopSelf()
                return START_NOT_STICKY
            }
            if (controller != null && controller.serviceRunning.value) {
                safeStartForeground(buildNotification(getString(R.string.fg_state_running_default)))
                return START_STICKY
            }
            safeStartForeground(buildNotification(getString(R.string.vpn_state_starting)))
            recoveryJob?.cancel()
            recoveryJob = scope.launch {
                val recovered = runCatching {
                    controller?.recoverFromSystemRestart(isAlwaysOnVpn = false) ?: false
                }.getOrDefault(false)
                if (!recovered) {
                    stopSelf()
                }
            }
            return START_STICKY
        }

        recoveryJob?.cancel()
        recoveryJob = null
        val text = intent.getStringExtra(EXTRA_TEXT)
            ?: getString(R.string.fg_state_running_default)
        safeStartForeground(buildNotification(text))
        return START_STICKY
    }

    /**
     * 安全切入前台：兼容 Android 14+ specialUse 类型声明，并兜底捕获 Android 15/16
     * 从开机广播或受限后台启动时可能抛出的 ForegroundServiceStartNotAllowedException，
     * 降级为普通通知保活而不触发主线程崩溃。
     */
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
            Log.w(TAG, "startForeground 受限降级为普通状态通知: ${err.message}")
            runCatching {
                getSystemService(NotificationManager::class.java)
                    ?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun stopAllExternally() {
        runCatching { EasyTierApp.get().container.vpnController.onStoppedExternally() }
            .onFailure { Log.w(TAG, "获取容器失败，跳过停止核心实例", it) }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val shouldRecover = runCatching {
            EasyTierApp.get().container.vpnController.shouldAutoRecover()
        }.getOrDefault(false)
        if (!shouldRecover) return
        EasyTierVpnService.scheduleServiceRecoveryAlarm(
            context = applicationContext,
            intent = Intent(applicationContext, EasyTierForegroundService::class.java).setAction(ACTION_RECOVER),
            requestCode = RECOVER_ALARM_REQUEST_CODE,
        )
    }

    override fun onDestroy() {
        recoveryJob?.cancel()
        scope.cancel()
        runCatching {
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.fg_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.fg_channel_desc)
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
            Intent(this, EasyTierForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.vpn_action_stop), stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "EasyTierForegroundService"
        private const val CHANNEL_ID = "easytier_service_channel"
        private const val NOTIFICATION_ID = 2048
        const val ACTION_STOP = "com.easytier.android.STOP_FOREGROUND_SERVICE"
        const val ACTION_RECOVER = "com.easytier.android.RECOVER_FOREGROUND_SERVICE"
        private const val RECOVER_ALARM_REQUEST_CODE = 1002
        private const val EXTRA_TEXT = "extra_text"

        fun start(context: Context, text: String) {
            val intent = Intent(context, EasyTierForegroundService::class.java).apply {
                putExtra(EXTRA_TEXT, text)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }.onFailure { Log.w(TAG, "启动前台保活服务受限: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, EasyTierForegroundService::class.java))
            }.onFailure { Log.w(TAG, "停止前台服务失败: ${it.message}") }
        }
    }
}
