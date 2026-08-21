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
