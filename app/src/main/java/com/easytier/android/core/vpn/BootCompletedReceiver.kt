package com.easytier.android.core.vpn

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.easytier.android.EasyTierApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 开机自启：恢复用户指定的网络实例。
 * 组件默认禁用，由设置页开关控制启用状态。
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as? EasyTierApp ?: return
        val pending = goAsync()
        app.applicationScope.launch {
            try {
                val settings = app.container.settingsRepository.settings.first()
                if (!settings.autoStartOnBoot) return@launch
                val id = settings.autoStartNetworkId ?: return@launch
                val network = app.container.networksRepository.get(id) ?: return@launch
                // 服务与网络解耦：开机自启 = 先标记服务运行意图再启动网络，TUN 随 IP 就绪建立
                app.container.vpnController.startServiceWithNetworks(listOf(network))
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** 设置开机自启组件启用状态。 */
        fun setEnabled(context: Context, enabled: Boolean) {
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, BootCompletedReceiver::class.java),
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
