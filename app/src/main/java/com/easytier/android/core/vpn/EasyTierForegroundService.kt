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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "收到通知栏停止指令")
                safeStartForeground(buildNotification(getString(R.string.vpn_state_stopping)))
                stopAllExternally()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        // START_STICKY 进程被回收后系统以 null intent 拉起时：核对控制器真实状态，
        // 若引擎并未运行则立即退场，杜绝“僵尸前台通知”空转耗电。
        if (intent == null) {
            val active = runCatching {
                EasyTierApp.get().container.vpnController.serviceRunning.value
            }.getOrDefault(false)
            if (!active) {
                safeStartForeground(buildNotification(getString(R.string.vpn_state_stopping)))
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val text = intent?.getStringExtra(EXTRA_TEXT)
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

    override fun onDestroy() {
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
