package com.easytier.android.core.vpn

import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.NetworkInstanceRunningInfo
import com.easytier.android.data.model.NetworkingMethod
import com.easytier.android.data.model.PeerInfo
import com.easytier.android.data.model.Route
import com.easytier.android.data.model.SavedNetwork
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnConnectionHealthTest {

    private fun network(
        name: String = "net",
        method: NetworkingMethod = NetworkingMethod.PublicServer,
        peers: List<String> = emptyList(),
    ) = SavedNetwork(
        id = name,
        config = NetworkConfig(
            networkName = name,
            networkingMethod = method,
            peerUrls = peers,
        ),
    )

    @Test
    fun `requiresPhysicalNetwork is false only when all enabled networks are standalone`() {
        assertFalse(VpnController.requiresPhysicalNetwork(listOf(network(method = NetworkingMethod.Standalone))))
        assertTrue(VpnController.requiresPhysicalNetwork(listOf(network(method = NetworkingMethod.PublicServer))))
        assertTrue(VpnController.requiresPhysicalNetwork(listOf(network(method = NetworkingMethod.Manual))))
        assertTrue(
            VpnController.requiresPhysicalNetwork(
                listOf(
                    network(name = "a", method = NetworkingMethod.Standalone),
                    network(name = "b", method = NetworkingMethod.PublicServer),
                ),
            ),
        )
    }

    @Test
    fun `needsConnectionWatchdog matches networking method and peer configuration`() {
        assertTrue(VpnController.needsConnectionWatchdog(NetworkConfig(networkingMethod = NetworkingMethod.PublicServer)))
        assertTrue(
            VpnController.needsConnectionWatchdog(
                NetworkConfig(networkingMethod = NetworkingMethod.Manual, peerUrls = listOf("tcp://1.2.3.4:11010")),
            ),
        )
        assertFalse(
            VpnController.needsConnectionWatchdog(
                NetworkConfig(networkingMethod = NetworkingMethod.Manual, peerUrls = emptyList()),
            ),
        )
        assertFalse(VpnController.needsConnectionWatchdog(NetworkConfig(networkingMethod = NetworkingMethod.Standalone)))
    }

    @Test
    fun `hasConnectedPeers checks both peers and routes`() {
        assertFalse(VpnController.hasConnectedPeers(NetworkInstanceRunningInfo()))
        assertTrue(VpnController.hasConnectedPeers(NetworkInstanceRunningInfo(peers = listOf(PeerInfo(peerId = 1)))))
        assertTrue(VpnController.hasConnectedPeers(NetworkInstanceRunningInfo(routes = listOf(Route(peerId = 1)))))
    }

    @Test
    fun `isLocalPeerUrl distinguishes LAN peers from remote peers`() {
        assertTrue(VpnController.isLocalPeerUrl("tcp://192.168.1.20:11010"))
        assertTrue(VpnController.isLocalPeerUrl("udp://10.0.0.5:11010"))
        assertTrue(VpnController.isLocalPeerUrl("tcp://[fe80::1]:11010"))
        assertFalse(VpnController.isLocalPeerUrl("tcp://public.easytier.cn:11010"))
        assertFalse(VpnController.isLocalPeerUrl("udp://8.8.8.8:11010"))
    }

    @Test
    fun `evaluateUnconnectedFailure classifies offline, unvalidated internet, and unreachable public server`() {
        val pubCfg = NetworkConfig(networkingMethod = NetworkingMethod.PublicServer)
        assertEquals(
            VpnController.ConnectionFailureReason.NO_NETWORK,
            VpnController.evaluateUnconnectedFailure(pubCfg, hasPhysicalNetwork = false, hasValidatedInternet = false),
        )
        assertEquals(
            VpnController.ConnectionFailureReason.NO_INTERNET_ACCESS,
            VpnController.evaluateUnconnectedFailure(pubCfg, hasPhysicalNetwork = true, hasValidatedInternet = false),
        )
        assertEquals(
            VpnController.ConnectionFailureReason.PUBLIC_SERVER_UNREACHABLE,
            VpnController.evaluateUnconnectedFailure(pubCfg, hasPhysicalNetwork = true, hasValidatedInternet = true),
        )

        val remoteManual = NetworkConfig(
            networkingMethod = NetworkingMethod.Manual,
            peerUrls = listOf("tcp://peer.example.com:11010"),
        )
        assertEquals(
            VpnController.ConnectionFailureReason.NO_NETWORK,
            VpnController.evaluateUnconnectedFailure(remoteManual, hasPhysicalNetwork = false, hasValidatedInternet = false),
        )
        assertEquals(
            VpnController.ConnectionFailureReason.NO_INTERNET_ACCESS,
            VpnController.evaluateUnconnectedFailure(remoteManual, hasPhysicalNetwork = true, hasValidatedInternet = false),
        )
        assertNull(
            VpnController.evaluateUnconnectedFailure(remoteManual, hasPhysicalNetwork = true, hasValidatedInternet = true),
        )

        val lanManual = NetworkConfig(
            networkingMethod = NetworkingMethod.Manual,
            peerUrls = listOf("tcp://192.168.1.50:11010"),
        )
        assertNull(
            VpnController.evaluateUnconnectedFailure(lanManual, hasPhysicalNetwork = true, hasValidatedInternet = false),
        )

        val standalone = NetworkConfig(networkingMethod = NetworkingMethod.Standalone)
        assertNull(
            VpnController.evaluateUnconnectedFailure(standalone, hasPhysicalNetwork = false, hasValidatedInternet = false),
        )
    }

    @Test
    fun `shouldRecoverOnSystemRestart requires enabled networks, permission, and persistent flag`() {
        assertTrue(
            VpnController.shouldRecoverOnSystemRestart(
                isAlwaysOnVpn = true,
                serviceDesiredRunning = false,
                autoStartOnBoot = false,
                hasEnabledNetworks = true,
                hasVpnPermission = true,
            ),
        )
        assertTrue(
            VpnController.shouldRecoverOnSystemRestart(
                isAlwaysOnVpn = false,
                serviceDesiredRunning = true,
                autoStartOnBoot = false,
                hasEnabledNetworks = true,
                hasVpnPermission = true,
            ),
        )
        assertTrue(
            VpnController.shouldRecoverOnSystemRestart(
                isAlwaysOnVpn = false,
                serviceDesiredRunning = false,
                autoStartOnBoot = true,
                hasEnabledNetworks = true,
                hasVpnPermission = true,
            ),
        )
        assertFalse(
            VpnController.shouldRecoverOnSystemRestart(
                isAlwaysOnVpn = false,
                serviceDesiredRunning = false,
                autoStartOnBoot = false,
                hasEnabledNetworks = true,
                hasVpnPermission = true,
            ),
        )
        assertFalse(
            VpnController.shouldRecoverOnSystemRestart(
                isAlwaysOnVpn = true,
                serviceDesiredRunning = true,
                autoStartOnBoot = true,
                hasEnabledNetworks = false,
                hasVpnPermission = true,
            ),
        )
        assertFalse(
            VpnController.shouldRecoverOnSystemRestart(
                isAlwaysOnVpn = true,
                serviceDesiredRunning = true,
                autoStartOnBoot = true,
                hasEnabledNetworks = true,
                hasVpnPermission = false,
            ),
        )
    }

    @Test
    fun `watchdog abort and recovery backoff match persistent mode policy`() {
        assertTrue(VpnController.shouldAbortOnWatchdog(autoRecoverMode = false))
        assertFalse(VpnController.shouldAbortOnWatchdog(autoRecoverMode = true))

        assertEquals(3_000L, VpnController.nextRecoveryBackoffMs(0))
        assertEquals(6_000L, VpnController.nextRecoveryBackoffMs(1))
        assertEquals(12_000L, VpnController.nextRecoveryBackoffMs(2))
        assertEquals(24_000L, VpnController.nextRecoveryBackoffMs(3))
        assertEquals(30_000L, VpnController.nextRecoveryBackoffMs(4))
        assertEquals(30_000L, VpnController.nextRecoveryBackoffMs(10))
    }
}
