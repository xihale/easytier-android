package com.easytier.android.util

/**
 * Magic DNS 上游服务器（dns_forward_servers flag）的格式校验与预置建议。
 *
 * 核心侧（patches/dns-forward-servers.patch）按 URL scheme 分派协议：
 * udp/tcp 默认端口 53，tls（DoT）853，https（DoH）443；https 的 path 作为
 * DoH endpoint（默认 /dns-query）。域名由核心在启动时经系统解析器取首个地址。
 */
object DnsServers {

    val SCHEMES = setOf("udp", "tcp", "tls", "https")

    /** 与核心 parse_dns_forward_server 同口径的格式校验。 */
    fun isValid(raw: String): Boolean {
        val s = raw.trim()
        val scheme = s.substringBefore("://", "").lowercase()
        if (scheme !in SCHEMES) return false
        val rest = s.substringAfter("://", "")
        if (rest.isBlank()) return false
        // host[:port][/path]：host 允许域名或方括号 IPv6
        val authority = rest.substringBefore('/')
        if (authority.isBlank()) return false
        val isV6 = authority.startsWith("[")
        val host = if (isV6) {
            authority.substringAfter('[').substringBefore(']', missingDelimiterValue = "")
                .takeIf { it.isNotBlank() } ?: return false
        } else {
            authority.substringBeforeLast(':').ifEmpty { return false }
        }
        // 域名不含下划线（与 DNS 主机名规则一致）；IPv6 允许冒号与十六进制
        if (!host.matches(Regex(if (isV6) "[0-9A-Fa-f:.]+" else "[A-Za-z0-9.-]+"))) return false
        val portPart = if (authority.startsWith("[")) {
            authority.substringAfter(']', "").takeIf { it.startsWith(":") }?.substring(1)
        } else if (authority.contains(':')) {
            authority.substringAfterLast(':')
        } else {
            null
        }
        if (portPart != null && portPart.toIntOrNull() !in 1..65535) return false
        return true
    }

    /** 常用公共 DoT/DoH 预置。 */
    val PRESETS = listOf(
        "https://dns.alidns.com/dns-query" to "阿里 DoH",
        "tls://dns.alidns.com" to "阿里 DoT",
        "https://doh.pub/dns-query" to "腾讯 DoH",
        "tls://dot.pub" to "腾讯 DoT",
        "https://doh.360.cn/dns-query" to "360 DoH",
    )
}
