package com.easytier.android.core.vpn

import com.easytier.android.data.model.NetworkConfig

/**
 * 多实例端口避让（纯逻辑，无 Android 依赖，可 JVM 单测）。
 *
 * 规则：
 * - 只对其他运行实例占用的端口避让。[runningConfigs] 可包含自身旧记录，函数内部
 *   按 [selfName] 排除：否则重启实例或同步配置时会把自身端口误判为冲突，导致每次
 *   重启 socks5 端口 +1、监听端口 +10 持续漂移（issue #1）。
 * - 监听端口冲突从原端口 +10 起找空闲端口；SOCKS5 与 VPN Portal 端口冲突 +1。
 * - 其他实例的 SOCKS5 / VPN Portal 端口与监听端口一样计入占用（issue #3）；自身
 *   避让后的监听端口也计入占用，避免自身的 SOCKS5 / VPN Portal 与自己的监听端口
 *   撞车（默认 vpnPortalListenPort 11010 与默认监听 11010 同端口）。
 * - 避让得到的新端口会加入占用集，避免同一配置内互相撞车。
 */
internal object PortAvoidance {

    /** 计算启动 [config] 时实际生效的配置：必要时替换被占用的监听端口与 SOCKS5 端口。
     *  [runningConfigs] 可包含自身旧记录，函数内部按 [selfName] 排除（issue #1 的病灶所在）。 */
    fun resolve(
        config: NetworkConfig,
        runningConfigs: Collection<NetworkConfig>,
        selfName: String,
        socks5Enabled: Boolean,
        requestedSocks5Port: Int?,
    ): NetworkConfig {
        val otherRunningConfigs = runningConfigs.filter { it.networkName != selfName }
        val usedPorts = otherRunningConfigs
            .flatMap { it.listenerUrls }
            .mapNotNull { it.substringAfterLast(':').toIntOrNull() }
            .toMutableSet()
        otherRunningConfigs.forEach {
            if (it.enableSocks5 == true) it.socks5Port?.let { p -> usedPorts.add(p) }
            if (it.enableVpnPortal) usedPorts.add(it.vpnPortalListenPort)
        }
        val listeners = if (config.listenerUrls.any {
                it.substringAfterLast(':').toIntOrNull() in usedPorts
            }
        ) {
            config.listenerUrls.map { url -> avoidPortConflict(url, usedPorts) }
        } else {
            config.listenerUrls
        }
        val effectiveConfig = config.copy(listenerUrls = listeners)
        // 自身监听端口（含避让后的）也计入占用：自身的 SOCKS5 / VPN Portal 不能与自己的监听端口撞车
        listeners.forEach { it.substringAfterLast(':').toIntOrNull()?.let(usedPorts::add) }
        var socks5Port = if (socks5Enabled) requestedSocks5Port else null
        if (socks5Port != null) {
            var candidate = socks5Port
            while (candidate in usedPorts) candidate += 1
            usedPorts.add(candidate)
            socks5Port = candidate
        }
        var vpnPortalPort = if (config.enableVpnPortal) config.vpnPortalListenPort else null
        if (vpnPortalPort != null) {
            var candidate = vpnPortalPort
            while (candidate in usedPorts) candidate += 1
            usedPorts.add(candidate)
            vpnPortalPort = candidate
        }
        return effectiveConfig.copy(
            enableSocks5 = socks5Port != null,
            socks5Port = socks5Port,
            vpnPortalListenPort = vpnPortalPort ?: config.vpnPortalListenPort,
        )
    }

    /** 监听 URL 的端口被占用时，从原端口 +10 起找一个未占用端口。 */
    private fun avoidPortConflict(url: String, usedPorts: MutableSet<Int>): String {
        val port = url.substringAfterLast(':').toIntOrNull() ?: return url
        if (port !in usedPorts) return url
        var candidate = port
        while (candidate in usedPorts) candidate += 10
        usedPorts.add(candidate)
        return url.substringBeforeLast(':') + ":" + candidate
    }
}
