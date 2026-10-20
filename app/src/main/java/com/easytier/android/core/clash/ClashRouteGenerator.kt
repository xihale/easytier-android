package com.easytier.android.core.clash

import com.easytier.android.core.toml.TomlGenerator
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.NetworkInstanceRunningInfo

/**
 * Clash 路由片段生成器：把 EasyTier 虚拟网的网段导流到本机 SOCKS5 监听端口。
 *
 * 典型用法：手机开 EasyTier 并开启 SOCKS5（可关闭系统 VPN），把生成的
 * proxies + rules 片段合并进 Clash 配置，访问虚拟网网段的流量即自动走 EasyTier。
 */
object ClashRouteGenerator {

    /** 片段中 SOCKS5 代理的名称。 */
    const val PROXY_NAME = "EasyTier"

    /** 未检测到任何网段时的回退：EasyTier 默认虚拟网段（DHCP 默认池）。 */
    const val DEFAULT_VIRTUAL_CIDR = "10.126.126.0/24"

    /**
     * 从运行实例与已保存配置收集虚拟网段：
     * - 运行实例：本机与全部路由的虚拟 IP，以及路由携带的代理网段
     * - 已保存配置：手动指定的虚拟 IP（dhcp=false）与代理网段（"cidr" 或 "cidr->mapped"）
     * 全部归一化为网络地址 CIDR，去重后按网段起始地址排序。
     */
    fun collectCidrs(
        running: Collection<NetworkInstanceRunningInfo>,
        saved: List<NetworkConfig>,
    ): List<String> {
        val raw = mutableListOf<String>()
        running.forEach { info ->
            info.myNodeInfo?.virtualIpv4?.toCidrString()?.let { raw.add(it) }
            info.routes.forEach { route ->
                route.ipv4Addr?.toCidrString()?.let { raw.add(it) }
                raw.addAll(route.proxyCidrs)
            }
        }
        saved.forEach { config ->
            if (!config.dhcp) {
                config.virtualIpv4?.takeIf { it.isNotBlank() }
                    ?.let { raw.add("$it/${config.networkLength}") }
            }
            raw.addAll(config.proxyCidrs)
        }
        return raw.mapNotNull(::normalizeCidr).distinct().sortedWith(
            compareBy(
                { parseIpv4(it.substringBefore('/')) ?: Long.MAX_VALUE },
                { it.substringAfter('/').toIntOrNull() ?: 0 },
            ),
        )
    }

    /**
     * 收集 Magic DNS 后缀：只取开启 Magic DNS 的已保存配置，后缀规则与
     * [TomlGenerator.effectiveDnsZone] 完全一致（显式 tld_dns_zone 优先，否则网络名）。
     * 每个网络的「主机名.后缀」都要单独导流，多网络时后缀可能各不相同。
     */
    fun collectDnsZones(saved: List<NetworkConfig>): List<String> =
        saved.filter { it.enableMagicDns }
            .map { TomlGenerator.effectiveDnsZone(it) }
            .distinct()
            .sorted()

    /**
     * 归一化 CIDR：主机位归零（"10.147.0.3/24" → "10.147.0.0/24"），
     * 容忍 "cidr->mapped" 映射写法（取箭头前网段）；非法输入返回 null。
     */
    fun normalizeCidr(raw: String): String? {
        val cidr = raw.trim().substringBefore("->").trim()
        val slash = cidr.indexOf('/')
        if (slash <= 0) return null
        val addr = parseIpv4(cidr.take(slash)) ?: return null
        val len = cidr.substring(slash + 1).trim().toIntOrNull()?.takeIf { it in 0..32 } ?: return null
        val mask = (0xFFFFFFFFL shl (32 - len)) and 0xFFFFFFFFL
        return "${toDotted(addr and mask)}/$len"
    }

    /**
     * 生成可合并进 Clash 配置的 proxies + rules 片段。
     *
     * [dnsZones] 为各网络的 Magic DNS 后缀（如 "xlan"），生成 DOMAIN-SUFFIX 规则：
     * 只发 IP-CIDR 时，「主机名.后缀」在 Clash 侧会先被自己的 DNS 解析、按公网地址
     * 走默认策略，导致 Magic DNS 的主机名访问不到虚拟网。
     */
    fun generate(port: Int, cidrs: List<String>, dnsZones: List<String> = emptyList()): String {
        val effective = cidrs.ifEmpty { listOf(DEFAULT_VIRTUAL_CIDR) }
        return buildString {
            appendLine("# EasyTier → Clash 路由片段（EasyTier Android 生成）")
            appendLine("# 把下列虚拟网网段与 Magic DNS 后缀导流到 EasyTier SOCKS5（本机监听端口 $port）")
            appendLine("# Clash 不在本机运行时，把 server 的 127.0.0.1 改为本机局域网 IP")
            appendLine()
            appendLine("proxies:")
            appendLine("  - name: $PROXY_NAME")
            appendLine("    type: socks5")
            appendLine("    server: 127.0.0.1")
            appendLine("    port: $port")
            appendLine("    udp: true")
            appendLine()
            appendLine("rules:")
            dnsZones.forEach { appendLine("  - DOMAIN-SUFFIX,$it,$PROXY_NAME") }
            if (cidrs.isEmpty()) {
                appendLine("  # 未检测到虚拟网段，使用 EasyTier 默认网段")
            }
            effective.forEach { appendLine("  - IP-CIDR,$it,$PROXY_NAME,no-resolve") }
        }
    }

    /** 点分十进制 → 无符号 32 位（Long 承载）；非法返回 null。 */
    internal fun parseIpv4(dotted: String): Long? {
        val parts = dotted.trim().split('.')
        if (parts.size != 4) return null
        var v = 0L
        for (p in parts) {
            val octet = p.toIntOrNull() ?: return null
            if (octet !in 0..255) return null
            v = (v shl 8) or octet.toLong()
        }
        return v
    }

    private fun toDotted(v: Long): String =
        "${(v shr 24) and 0xFF}.${(v shr 16) and 0xFF}.${(v shr 8) and 0xFF}.${v and 0xFF}"
}
