package com.easytier.android

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration

/**
 * 桌面图标刷新触发组件：自身无任何逻辑，仅作为可安全翻转启用状态的空 receiver 存在。
 *
 * MIUI 等深度定制桌面会把应用图标渲染结果缓存下来，系统深浅色切换时不会重新解析
 * `-night` 资源变体。翻转本组件的启用状态会产生 PACKAGE_CHANGED 广播，促使桌面
 * 按当前配置重新渲染应用图标。组件 exported 且无 intent-filter，翻转无功能影响。
 */
class LauncherIconRefresher : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) = Unit

    companion object {
        private const val PREFS = "launcher_icon_sync"
        private const val KEY_LAST_NIGHT = "last_synced_night"

        /**
         * 系统深浅色与上次同步时不一致时翻转一次组件状态，促使桌面重渲染图标。
         * 进程存活时由 onConfigurationChanged 触发（VPN 常驻即实时跟随）；
         * 进程不在时的切换由下次进程启动（打开应用/服务拉起/开机）兜底同步。
         */
        fun syncIfNeeded(context: Context) {
            val appContext = context.applicationContext
            val night = (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.contains(KEY_LAST_NIGHT)) {
                // 首次只记录当前模式；此时图标刚由桌面按当前配置渲染，无需翻转
                prefs.edit().putBoolean(KEY_LAST_NIGHT, night).apply()
                return
            }
            if (prefs.getBoolean(KEY_LAST_NIGHT, night) == night) return
            val pm = appContext.packageManager
            val comp = ComponentName(appContext, LauncherIconRefresher::class.java)
            val enable = pm.getComponentEnabledSetting(comp) !=
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            pm.setComponentEnabledSetting(
                comp,
                if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            prefs.edit().putBoolean(KEY_LAST_NIGHT, night).apply()
        }
    }
}
