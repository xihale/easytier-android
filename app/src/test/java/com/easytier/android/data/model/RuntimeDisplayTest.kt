package com.easytier.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 公网 IP 过滤与监听标签分组的纯函数测试。 */
class RuntimeDisplayTest {

    @Test
    fun `local addresses are filtered from public ips`() {
        val stun = StunInfo(publicIp = listOf("::1", "127.0.0.1", "192.168.1.5", "10.0.0.2", "203.0.113.10"))
        assertEquals(listOf("203.0.113.10"), stun.publicIps())
    }

    @Test
    fun `ipv6 local and ula addresses are filtered`() {
        val stun = StunInfo(
            publicIp = listOf(
                "::1",
                "fe80::1%wlan0",
                "fd00::1",
                "fc00::2",
                "169.254.1.1",
                "172.16.0.1",
                "2001:db8::1",
            ),
        )
        assertEquals(listOf("2001:db8::1"), stun.publicIps())
    }

    @Test
    fun `empty public ips stay empty`() {
        assertTrue(StunInfo(publicIp = listOf("", "  ")).publicIps().isEmpty())
    }

    @Test
    fun `isLocalAddress covers rfc1918 and special ranges`() {
        assertTrue(isLocalAddress("::1"))
        assertTrue(isLocalAddress("localhost"))
        assertTrue(isLocalAddress("127.0.0.1"))
        assertTrue(isLocalAddress("10.1.2.3"))
        assertTrue(isLocalAddress("172.16.0.1"))
        assertTrue(isLocalAddress("172.31.255.255"))
        assertTrue(isLocalAddress("192.168.0.1"))
        assertTrue(isLocalAddress("169.254.100.1"))
        assertTrue(isLocalAddress("fe80::abcd"))
        assertTrue(isLocalAddress("fdab::1"))
        assertFalse(isLocalAddress("8.8.8.8"))
        assertFalse(isLocalAddress("172.32.0.1")) // 172.32 不在私有段
        assertFalse(isLocalAddress("2402:9400::1"))
    }
}

class PeerLatencyBadgeTest {

    @Test
    fun `unmeasured latency shows connecting`() {
        // EasyTier 用 1000ms（1000000us）表示未测得
        assertEquals(
            PeerLatencyBadge(true, "连接中", LatencyTier.NEUTRAL),
            peerLatencyBadge(connLatencyMs = 1000, pathLatency = 0),
        )
        // 0ms 同样视为未测得
        assertEquals(
            PeerLatencyBadge(true, "连接中", LatencyTier.NEUTRAL),
            peerLatencyBadge(connLatencyMs = 0, pathLatency = 0),
        )
        // 无直连隧道且无路径延迟
        assertEquals(
            PeerLatencyBadge(true, "连接中", LatencyTier.NEUTRAL),
            peerLatencyBadge(connLatencyMs = null, pathLatency = 0),
        )
    }

    @Test
    fun `measured latency falls back to path latency when unmeasured`() {
        val badge = peerLatencyBadge(connLatencyMs = 1500, pathLatency = 42)
        assertEquals(PeerLatencyBadge(false, "42 ms", LatencyTier.GOOD), badge)
    }

    @Test
    fun `measured latency tiers by threshold`() {
        assertEquals(LatencyTier.GOOD, peerLatencyBadge(1, 0).tier)
        assertEquals(LatencyTier.GOOD, peerLatencyBadge(79, 0).tier)
        assertEquals(LatencyTier.FAIR, peerLatencyBadge(80, 0).tier)
        assertEquals(LatencyTier.FAIR, peerLatencyBadge(199, 0).tier)
        assertEquals(LatencyTier.BAD, peerLatencyBadge(200, 0).tier)
        assertEquals("123 ms", peerLatencyBadge(123, 999).text)
    }

    @Test
    fun `path latency is not shown as connecting when positive`() {
        // 纯中转：无直连隧道但有路径延迟，应显示路径延迟而非「连接中」
        val badge = peerLatencyBadge(connLatencyMs = null, pathLatency = 250)
        assertEquals(PeerLatencyBadge(false, "250 ms", LatencyTier.BAD), badge)
    }
}
