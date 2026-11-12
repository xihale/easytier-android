package com.easytier.android.core.engine

import com.easytier.android.data.model.Ipv4Addr
import com.easytier.android.data.model.Ipv4Inet
import com.easytier.android.data.model.MyNodeInfo
import com.easytier.android.data.model.NetworkInstanceRunningInfo
import com.easytier.android.data.model.PeerConnInfo
import com.easytier.android.data.model.PeerConnStats
import com.easytier.android.data.model.PeerInfo
import com.easytier.android.data.model.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EasyTierEnginePollingTest {

    @Test
    fun `fast bootstrap window uses 500ms until all instances settle`() {
        assertEquals(
            EasyTierEngine.FAST_POLL_INTERVAL_MS,
            EasyTierEngine.computePollIntervalMs(
                isForeground = true,
                inFastWindow = true,
                allSettled = false,
                idleBackoffSteps = 0,
            ),
        )
        // Once settled, foreground uses 2000ms even inside fast window
        assertEquals(
            EasyTierEngine.FOREGROUND_POLL_INTERVAL_MS,
            EasyTierEngine.computePollIntervalMs(
                isForeground = true,
                inFastWindow = true,
                allSettled = true,
                idleBackoffSteps = 0,
            ),
        )
    }

    @Test
    fun `background backs off from 5s converging to 30s idle and 60s deep idle`() {
        assertEquals(
            EasyTierEngine.BG_CONVERGING_POLL_INTERVAL_MS,
            EasyTierEngine.computePollIntervalMs(
                isForeground = false,
                inFastWindow = false,
                allSettled = false,
                idleBackoffSteps = 0,
            ),
        )
        assertEquals(
            EasyTierEngine.BG_IDLE_POLL_INTERVAL_MS,
            EasyTierEngine.computePollIntervalMs(
                isForeground = false,
                inFastWindow = false,
                allSettled = true,
                idleBackoffSteps = 1,
            ),
        )
        assertEquals(
            EasyTierEngine.BG_DEEP_IDLE_POLL_INTERVAL_MS,
            EasyTierEngine.computePollIntervalMs(
                isForeground = false,
                inFastWindow = false,
                allSettled = true,
                idleBackoffSteps = 2,
            ),
        )
    }

    @Test
    fun `isSameTopologyState ignores byte counters and latency jitter but detects ip or route changes`() {
        val ip1 = Ipv4Inet(Ipv4Addr(0x0A909002L), 24)
        val ip2 = Ipv4Inet(Ipv4Addr(0x0A909009L), 24)

        val s1 = InstanceState.Running(
            NetworkInstanceRunningInfo(
                running = true,
                myNodeInfo = MyNodeInfo(virtualIpv4 = ip1),
                routes = listOf(Route(peerId = 10, proxyCidrs = listOf("192.168.1.0/24"), pathLatency = 20)),
                peers = listOf(PeerInfo(peerId = 10, conns = listOf(PeerConnInfo(stats = PeerConnStats("100", "200", "9000"))))),
            ),
        )
        val s2 = InstanceState.Running(
            NetworkInstanceRunningInfo(
                running = true,
                myNodeInfo = MyNodeInfo(virtualIpv4 = ip1),
                routes = listOf(Route(peerId = 10, proxyCidrs = listOf("192.168.1.0/24"), pathLatency = 45)),
                peers = listOf(PeerInfo(peerId = 10, conns = listOf(PeerConnInfo(stats = PeerConnStats("88888", "99999", "12000"), lossRate = 0.05f)))),
            ),
        )
        assertTrue(EasyTierEngine.isSameTopologyState(s1, s2))

        val s3 = InstanceState.Running(
            s1.info.copy(myNodeInfo = MyNodeInfo(virtualIpv4 = ip2)),
        )
        assertFalse(EasyTierEngine.isSameTopologyState(s1, s3))

        val s4 = InstanceState.Running(
            s1.info.copy(routes = listOf(Route(peerId = 10, proxyCidrs = listOf("192.168.1.0/24", "10.8.0.0/16")))),
        )
        assertFalse(EasyTierEngine.isSameTopologyState(s1, s4))
    }
}
