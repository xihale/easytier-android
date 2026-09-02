package com.easytier.android.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 上游 DNS URL 校验：与核心 parse_dns_forward_server 的 scheme/host/port 口径对齐。 */
class DnsServersTest {

    @Test
    fun `accepts supported schemes with host and optional port`() {
        assertTrue(DnsServers.isValid("tls://dns.alidns.com"))
        assertTrue(DnsServers.isValid("tls://dns.alidns.com:853"))
        assertTrue(DnsServers.isValid("https://doh.pub/dns-query"))
        assertTrue(DnsServers.isValid("https://120.53.53.53/dns-query"))
        assertTrue(DnsServers.isValid("udp://223.5.5.5"))
        assertTrue(DnsServers.isValid("udp://223.5.5.5:53"))
        assertTrue(DnsServers.isValid("tcp://1.1.1.1"))
        assertTrue(DnsServers.isValid("TLS://DNS.ALIDNS.COM"))
        assertTrue(DnsServers.isValid("udp://[2001:db8::1]:53"))
    }

    @Test
    fun `rejects malformed entries`() {
        assertFalse(DnsServers.isValid(""))
        assertFalse(DnsServers.isValid("dns.alidns.com")) // 缺 scheme
        assertFalse(DnsServers.isValid("quic://dns.quic.com")) // 未支持的协议
        assertFalse(DnsServers.isValid("tls://")) // 缺 host
        assertFalse(DnsServers.isValid("tls://dns ali.com")) // 非法字符
        assertFalse(DnsServers.isValid("tls://dn_s.example.com")) // 下划线
        assertFalse(DnsServers.isValid("udp://223.5.5.5:99999")) // 端口越界
        assertFalse(DnsServers.isValid("udp://223.5.5.5:abc")) // 端口非数字
        assertFalse(DnsServers.isValid("https://[2001:db8::1")) // 方括号未闭合
    }
}
