package com.easytier.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * 后台运行权限（电池优化白名单）。
 *
 * 仅引擎 / SOCKS5 模式没有系统 VPN 保护，进程完全依赖前台服务常驻；
 * 未加入电池优化白名单时进程可能被系统随时回收，SOCKS5 入口随之中断。
 */
object BackgroundPermission {

    /** 是否已加入电池优化白名单（后台常驻的第一道门）。 */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * 弹系统「忽略电池优化」确认框（需 REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 权限声明）。
     * 返回 null 时（系统不支持）调用方应退回应用详情页。
     */
    fun requestIgnoreIntent(context: Context): Intent? = runCatching {
        Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
    }.getOrNull()

    /** 通用应用详情页：国产 ROM（MIUI/HyperOS、ColorOS 等）的自启动 / 省电策略只能引导用户手动开。 */
    fun appDetailsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}"),
    )
}
