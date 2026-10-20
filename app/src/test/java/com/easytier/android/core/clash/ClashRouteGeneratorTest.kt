package com.easytier.android.core.clash

import com.easytier.android.data.model.Ipv4Addr
import com.easytier.android.data.model.Ipv4Inet
import com.easytier.android.data.model.MyNodeInfo
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.NetworkInstanceRunningInfo
import com.easytier.android.data.model.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Clash 路由片段生成器行为验证（纯 JVM，无 Android 依赖）。 */
class ClashRouteGeneratorTest {

    private fun inet(addr: Long, len: Long = 24) = Ipv4Inet(address = Ipv4Addr(addr), networkLength = len)

    @Test
    fun `generate emits socks5 proxy and ip-cidr rules`() {
        val text = ClashRouteGenerator.generate(1080, listOf("10.147.0.0/24", "192.168.5.0/24"))
        assertTrue(text.contains("type: socks5"))
        assertTrue(text.contains("server: 127.0.0.1"))
        assertTrue(text.contains("port: 1080"))
        assertTrue(text.contains("- IP-CIDR,10.147.0.0/24,${ClashRouteGenerator.PROXY_NAME},no-resolve"))
        assertTrue(text.contains("- IP-CIDR,192.168.5.0/24,${ClashRouteGenerator.PROXY_NAME},no-resolve"))
        assertTrue(text.indexOf("proxies:") < text.indexOf("rules:"))
        // rules 顺序与传入顺序一致
        assertTrue(text.indexOf("10.147.0.0/24") < text.indexOf("192.168.5.0/24"))
    }

    @Test
    fun `empty cidrs falls back to default virtual cidr`() {
        val text = ClashRouteGenerator.generate(7890, emptyList())
        assertTrue(text.contains("port: 7890"))
        assertTrue(text.contains("IP-CIDR,${ClashRouteGenerator.DEFAULT_VIRTUAL_CIDR}"))
    }

    @Test
    fun `collect from running info and saved configs dedupes and sorts`() {
        val running = listOf(
            NetworkInstanceRunningInfo(
                myNodeInfo = MyNodeInfo(virtualIpv4 = inet(ip("10.147.0.3"))),
                routes = listOf(
                    Route(ipv4Addr = inet(ip("10.147.0.4"))),
                    Route(ipv4Addr = inet(ip("10.147.0.5")), proxyCidrs = listOf("192.168.5.0/24")),
                ),
            ),
        )
        val saved = listOf(
            NetworkConfig(networkName = "a", dhcp = false, virtualIpv4 = "10.147.0.9", networkLength = 24),
            NetworkConfig(networkName = "b", proxyCidrs = listOf("172.16.1.77/24 -> 10.0.0.0/24")),
        )
        val cidrs = ClashRouteGenerator.collectCidrs(running, saved)
        // 主机位归零去重（10.147.0.3/4/5/9 同网段），映射写法取箭头前网段，按起始地址排序
        assertEquals(listOf("10.147.0.0/24", "172.16.1.0/24", "192.168.5.0/24"), cidrs)
    }

    @Test
    fun `dhcp saved configs contribute nothing`() {
        val cidrs = ClashRouteGenerator.collectCidrs(
            running = emptyList(),
            saved = listOf(NetworkConfig(networkName = "a", virtualIpv4 = "10.147.0.9")),
        )
        assertTrue(cidrs.isEmpty())
    }

    @Test
    fun `generate emits domain-suffix rules before ip-cidr`() {
        val text = ClashRouteGenerator.generate(
            1080,
            listOf("10.147.0.0/24"),
            listOf("et.net", "xlan"),
        )
        assertTrue(text.contains("- DOMAIN-SUFFIX,xlan,${ClashRouteGenerator.PROXY_NAME}"))
        assertTrue(text.contains("- DOMAIN-SUFFIX,et.net,${ClashRouteGenerator.PROXY_NAME}"))
        // 域名规则在网段规则之前，且不擅自加 no-resolve（域名必须交给代理解析）
        assertTrue(text.indexOf("DOMAIN-SUFFIX,et.net") < text.indexOf("IP-CIDR,10.147.0.0/24"))
        assertTrue(!text.contains("DOMAIN-SUFFIX,et.net,${ClashRouteGenerator.PROXY_NAME},no-resolve"))
    }

    @Test
    fun `generate without zones emits only ip-cidr`() {
        val text = ClashRouteGenerator.generate(1080, listOf("10.147.0.0/24"))
        assertTrue(!text.contains("DOMAIN-SUFFIX"))
    }

    @Test
    fun `collectDnsZones takes magic dns configs and respects explicit zone`() {
        val zones = ClashRouteGenerator.collectDnsZones(
            listOf(
                NetworkConfig(networkName = "mesh"),                    // 默认后缀 = 网络名
                NetworkConfig(networkName = "home", tldDnsZone = "xlan"),
                NetworkConfig(networkName = "off", enableMagicDns = false),
                NetworkConfig(networkName = "dup", tldDnsZone = "xlan"), // 去重
                NetworkConfig(networkName = "bad name", tldDnsZone = ".."), // 空白后缀回退网络名，空格转连字符
            ),
        )
        assertEquals(listOf("bad-name", "mesh", "xlan"), zones)
    }

    @Test
    fun `collectDnsZones is empty when magic dns disabled everywhere`() {
        assertTrue(
            ClashRouteGenerator.collectDnsZones(
                listOf(NetworkConfig(networkName = "a", enableMagicDns = false)),
            ).isEmpty(),
        )
    }

    @Test
    fun `normalizeCidr handles host bits and mapped syntax`() {
        assertEquals("10.147.0.0/24", ClashRouteGenerator.normalizeCidr("10.147.0.3/24"))
        assertEquals("172.16.1.0/24", ClashRouteGenerator.normalizeCidr("172.16.1.77/24 -> 10.0.0.0/24"))
        assertEquals("10.0.0.0/8", ClashRouteGenerator.normalizeCidr("10.255.255.255/8"))
        assertEquals("0.0.0.0/0", ClashRouteGenerator.normalizeCidr("1.2.3.4/0"))
        assertNull(ClashRouteGenerator.normalizeCidr("not-a-cidr"))
        assertNull(ClashRouteGenerator.normalizeCidr("10.0.0.0/33"))
        assertNull(ClashRouteGenerator.normalizeCidr("256.1.1.1/24"))
        assertNull(ClashRouteGenerator.normalizeCidr("10.147.0.3"))
    }

    @Test
    fun `cidrs sorted by start address then length`() {
        val cidrs = ClashRouteGenerator.collectCidrs(
            running = emptyList(),
            saved = listOf(
                NetworkConfig(networkName = "a", proxyCidrs = listOf("192.168.0.0/16", "192.168.0.0/24")),
                NetworkConfig(networkName = "b", proxyCidrs = listOf("10.0.0.0/8")),
            ),
        )
        assertEquals(listOf("10.0.0.0/8", "192.168.0.0/16", "192.168.0.0/24"), cidrs)
    }

    private fun ip(dotted: String): Long {
        val parts = dotted.split('.').map { it.toLong() }
        return (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
    }
}
