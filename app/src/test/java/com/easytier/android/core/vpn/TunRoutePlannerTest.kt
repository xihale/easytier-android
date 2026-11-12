package com.easytier.android.core.vpn

import com.easytier.android.data.model.Ipv4Addr
import com.easytier.android.data.model.Ipv4Inet
import com.easytier.android.data.model.MyNodeInfo
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.NetworkInstanceRunningInfo
import com.easytier.android.data.model.PeerConnInfo
import com.easytier.android.data.model.PeerConnStats
import com.easytier.android.data.model.PeerInfo
import com.easytier.android.data.model.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TunRoutePlannerTest {

    private fun inet(dotted: String, len: Long = 24): Ipv4Inet {
        val parts = dotted.split('.').map { it.toLong() }
        val addr = (parts[0] shl 24) or (parts[1] shl 16) or (parts[2] shl 8) or parts[3]
        return Ipv4Inet(address = Ipv4Addr(addr), networkLength = len)
    }

    @Test
    fun `returns null when virtual ip is not yet assigned or noTun is enabled`() {
        val cfg = NetworkConfig(networkName = "mesh")
        assertNull(TunRoutePlanner.plan("mesh", cfg, NetworkInstanceRunningInfo()))

        val noTunCfg = NetworkConfig(networkName = "mesh", noTun = true)
        val infoWithIp = NetworkInstanceRunningInfo(
            myNodeInfo = MyNodeInfo(virtualIpv4 = inet("10.144.144.2")),
        )
        assertNull(TunRoutePlanner.plan("mesh", noTunCfg, infoWithIp))
    }

    @Test
    fun `normalizes host bits and extracts mapped cidr for remote subnet proxies`() {
        val cfg = NetworkConfig(networkName = "home", mtu = 1420)
        val info = NetworkInstanceRunningInfo(
            myNodeInfo = MyNodeInfo(virtualIpv4 = inet("10.144.144.2", 24)),
            routes = listOf(
                Route(
                    peerId = 2,
                    ipv4Addr = inet("10.144.144.5", 24),
                    proxyCidrs = listOf(
                        "192.168.50.9/24",
                        "192.168.1.0/24->10.20.30.7/24",
                        "invalid-cidr",
                    ),
                ),
                Route(
                    peerId = 3,
                    ipv4Addr = inet("10.199.0.8", 24),
                ),
            ),
        )
        val spec = TunRoutePlanner.plan("home", cfg, info)!!
        assertEquals("10.144.144.2/24", spec.ipv4Cidr)
        assertEquals(
            listOf("10.144.144.0/24", "10.199.0.0/24", "10.20.30.0/24", "192.168.50.0/24"),
            spec.routes,
        )
        assertEquals(1420, spec.mtu)
        assertEquals(true, spec.magicDns)
        assertEquals("home", spec.dnsZone)
    }

    @Test
    fun `traffic counter changes do not alter TunSpec equality`() {
        val cfg = NetworkConfig(networkName = "mesh")
        val base = NetworkInstanceRunningInfo(
            myNodeInfo = MyNodeInfo(virtualIpv4 = inet("10.144.144.2", 24)),
            routes = listOf(Route(peerId = 2, proxyCidrs = listOf("172.16.0.0/16"))),
            peers = listOf(
                PeerInfo(
                    peerId = 2,
                    conns = listOf(PeerConnInfo(stats = PeerConnStats("100", "200", "5000"))),
                ),
            ),
        )
        val later = base.copy(
            peers = listOf(
                PeerInfo(
                    peerId = 2,
                    conns = listOf(PeerConnInfo(stats = PeerConnStats("999999", "888888", "8000"), lossRate = 0.02f)),
                ),
            ),
        )
        assertEquals(
            TunRoutePlanner.plan("mesh", cfg, base),
            TunRoutePlanner.plan("mesh", cfg, later),
        )

        val dhcpChanged = base.copy(
            myNodeInfo = MyNodeInfo(virtualIpv4 = inet("10.144.144.9", 24)),
        )
        assertNotEquals(
            TunRoutePlanner.plan("mesh", cfg, base),
            TunRoutePlanner.plan("mesh", cfg, dhcpChanged),
        )
    }

    @Test
    fun `clamps out of range mtu safely and respects manual routes`() {
        val cfg = NetworkConfig(
            networkName = "manual",
            mtu = 9000,
            enableMagicDns = false,
            enableManualRoutes = true,
            routes = listOf("10.88.1.5/24"),
        )
        val info = NetworkInstanceRunningInfo(
            myNodeInfo = MyNodeInfo(virtualIpv4 = inet("10.144.0.2", 24)),
            routes = listOf(Route(peerId = 2, proxyCidrs = listOf("192.168.99.0/24"))),
        )
        val spec = TunRoutePlanner.plan("manual", cfg, info)!!
        assertEquals(TunRoutePlanner.MAX_MTU, spec.mtu)
        assertEquals(false, spec.magicDns)
        assertNull(spec.dnsZone)
        assertEquals(listOf("10.144.0.0/24", "10.88.1.0/24"), spec.routes)
    }
}
