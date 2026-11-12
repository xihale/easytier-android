package com.easytier.android.core.vpn

import com.easytier.android.core.clash.ClashRouteGenerator
import com.easytier.android.core.toml.TomlGenerator
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.NetworkInstanceRunningInfo

/**
 * 系统 VPN（TUN）接口目标规格。
 *
 * 作为不可变值对象用于幂等对账：仅当 [TunSpec] 任一字段真正发生变化
 * （例如 DHCP 虚拟 IP 变更、远端节点通告新的子网代理路由、MTU 或 DNS 变更）时，
 * [VpnController] 才通知 [EasyTierVpnService] 热切换重建 TUN，消除后台冗余轮询。
 */
internal data class TunSpec(
    val instanceName: String,
    val ipv4Cidr: String,
    val routes: List<String>,
    val magicDns: Boolean,
    val dnsZone: String?,
    val mtu: Int,
)

/**
 * 纯 JVM 的 TUN 路由与接口参数规划器（无 Android 依赖，可单测）。
 */
internal object TunRoutePlanner {

    const val DEFAULT_MTU = 1380
    const val MIN_MTU = 1280
    const val MAX_MTU = 1500

    fun plan(
        instanceName: String,
        config: NetworkConfig,
        info: NetworkInstanceRunningInfo,
    ): TunSpec? {
        if (config.noTun == true) return null
        val ipv4Cidr = info.myNodeInfo?.virtualIpv4?.toCidrString() ?: return null

        val rawRoutes = ArrayList<String>(8 + info.routes.size * 2)
        // 显式加入本机虚拟网段路由：部分定制 ROM 仅靠 addAddress 不会自动下发完整子网路由
        rawRoutes.add(ipv4Cidr)

        // 手动路由表开启时按显式路由表下发；否则自动收敛远端对等网段与子网代理
        if (config.enableManualRoutes == true && !config.routes.isNullOrEmpty()) {
            rawRoutes.addAll(config.routes)
        } else {
            for (route in info.routes) {
                // 跨网段静态 IP 对等节点也能直走 TUN，不会漏向物理默认网关
                route.ipv4Addr?.toCidrString()?.let { rawRoutes.add(it) }
                for (proxy in route.proxyCidrs) {
                    rawRoutes.add(extractTargetCidr(proxy))
                }
            }
        }

        val normalizedRoutes = rawRoutes
            .mapNotNull(ClashRouteGenerator::normalizeCidr)
            .distinct()
            .sorted()

        val magicDns = config.enableMagicDns
        val dnsZone = if (magicDns) TomlGenerator.effectiveDnsZone(config) else null
        val mtu = (config.mtu ?: DEFAULT_MTU).coerceIn(MIN_MTU, MAX_MTU)

        return TunSpec(
            instanceName = instanceName,
            ipv4Cidr = ipv4Cidr,
            routes = normalizedRoutes,
            magicDns = magicDns,
            dnsZone = dnsZone,
            mtu = mtu,
        )
    }

    /**
     * 解析子网代理目标网段：若为 `"192.168.1.0/24->10.20.30.0/24"` NAT 映射写法，
     * 本机访问远端子网实际发往映射后的目标网段 `10.20.30.0/24`。
     */
    internal fun extractTargetCidr(raw: String): String {
        val trimmed = raw.trim()
        if ("->" !in trimmed) return trimmed
        val mapped = trimmed.substringAfter("->").trim()
        return mapped.ifEmpty { trimmed.substringBefore("->").trim() }
    }
}
