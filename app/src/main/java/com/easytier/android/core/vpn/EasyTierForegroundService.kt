package com.easytier.android.core.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
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
 * 2. 持有 WiFi MulticastLock，保证局域网 UDP 发现与 UPnP 在无 TUN 模式下正常工作。
 * 3. 提供通知栏快捷「停止」操作。
 */
class EasyTierForegroundService : Service() {

    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "收到通知栏停止指令")
                startForeground(NOTIFICATION_ID, buildNotification("正在停止"))
                stopAllExternally()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "网络核心运行中"
        val notification = buildNotification(text)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquireMulticastLock()
        return START_STICKY
    }

    private fun acquireMulticastLock() {
        if (multicastLock?.isHeld == true) return
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
        runCatching {
            wifi.createMulticastLock("easytier_fg").apply {
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

    private fun stopAllExternally() {
        runCatching { EasyTierApp.get().container.vpnController.onStoppedExternally() }
            .onFailure { Log.w(TAG, "获取容器失败，跳过停止核心实例", it) }
    }

    override fun onDestroy() {
        releaseMulticastLock()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "EasyTier 后台服务",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "EasyTier 网络核心与 SOCKS5 代理常驻状态"
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
            .setContentTitle("EasyTier")
            .setContentText(text)
            .setContentIntent(contentIntent)
            .addAction(0, "停止", stopIntent)
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

        fun start(context: Context, text: String = "网络核心运行中") {
            val intent = Intent(context, EasyTierForegroundService::class.java).apply {
                putExtra(EXTRA_TEXT, text)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, EasyTierForegroundService::class.java))
            }.onFailure { Log.w(TAG, "停止前台服务失败: ${it.message}") }
        }
    }
}
