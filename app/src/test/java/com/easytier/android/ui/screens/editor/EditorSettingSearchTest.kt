package com.easytier.android.ui.screens.editor

import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.PortForwardEntry
import com.easytier.android.ui.components.applyQuickPrefix
import com.easytier.android.ui.components.extractProtocolBadge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSettingSearchTest {

    @Test
    fun allEditorSettingIdsAreRegistered() {
        val registeredIds = ALL_EDITOR_SETTINGS.map { it.id }.toSet()
        assertEquals(EditorSettingId.entries.toSet(), registeredIds)
    }

    @Test
    fun filterMatchesEnglishFlagsAndTomlKeys() {
        val cfg = NetworkConfig()
        val kcpHits = filterEditorSettings("kcp", cfg) { it.id.name }.map { it.id }
        assertTrue(EditorSettingId.EnableKcpProxy in kcpHits)
        assertTrue(EditorSettingId.DisableKcpInput in kcpHits)

        val peerHits = filterEditorSettings("peer_urls", cfg) { it.id.name }.map { it.id }
        assertTrue(EditorSettingId.PeerUrls in peerHits)

        val mtuHits = filterEditorSettings("mtu", cfg) { it.id.name }.map { it.id }
        assertEquals(listOf(EditorSettingId.Mtu), mtuHits)
    }

    @Test
    fun filterMatchesMultiTokenAndConfiguredValues() {
        val cfg = NetworkConfig(
            peerUrls = listOf("tcp://203.0.113.10:11010"),
            virtualIpv4 = "10.126.126.5",
            portForwards = listOf(PortForwardEntry(bindPort = 8080, dstIp = "10.126.126.2", dstPort = 80)),
        )

        val peerValueHits = filterEditorSettings("203.0.113.10", cfg) { it.id.name }.map { it.id }
        assertTrue(EditorSettingId.PeerUrls in peerValueHits)

        val ipValueHits = filterEditorSettings("10.126.126.5", cfg) { it.id.name }.map { it.id }
        assertTrue(EditorSettingId.VirtualIpv4 in ipValueHits)

        val pfValueHits = filterEditorSettings("8080", cfg) { it.id.name }.map { it.id }
        assertTrue(EditorSettingId.PortForwards in pfValueHits)

        val multiToken = filterEditorSettings("打洞 对称", cfg) {
            when (it.id) {
                EditorSettingId.DisableSymHolePunching -> "禁用对称 NAT 打洞"
                else -> it.id.name
            }
        }.map { it.id }
        assertEquals(listOf(EditorSettingId.DisableSymHolePunching), multiToken)

        assertTrue(filterEditorSettings("nonexistent_xyz_token", cfg) { it.id.name }.isEmpty())
    }

    @Test
    fun quickPrefixAndBadgeHelpersWork() {
        assertEquals("udp://1.2.3.4:11010", applyQuickPrefix("1.2.3.4:11010", "udp://"))
        assertEquals("wg://1.2.3.4:11010", applyQuickPrefix("tcp://1.2.3.4:11010", "wg://"))
        assertEquals("wss://relay.example.com", applyQuickPrefix("  ws://relay.example.com", "wss://"))

        assertEquals("TCP", extractProtocolBadge("tcp://1.2.3.4:11010"))
        assertEquals("WG", extractProtocolBadge("wg://0.0.0.0:11011"))
        assertEquals("DoT", extractProtocolBadge("tls://223.5.5.5:853"))
        assertEquals("DoH", extractProtocolBadge("https://dns.alidns.com/dns-query"))
        assertNull(extractProtocolBadge("10.0.0.0/24"))
    }

    @Test
    fun bitratePreviewAndModifiedFlagCountersWork() {
        assertEquals("100 Mbps (12.5 MB/s)", formatBitratePreview(100_000_000L))
        assertEquals("8 Mbps (1 MB/s)", formatBitratePreview(8_000_000L))
        assertEquals("500 Kbps (62.5 KB/s)", formatBitratePreview(500_000L))

        val defaultCfg = NetworkConfig()
        assertEquals(0, countModifiedP2pFlags(defaultCfg))
        assertEquals(0, countModifiedTransportFlags(defaultCfg))
        assertEquals(0, countModifiedSecurityFlags(defaultCfg))

        val modifiedCfg = NetworkConfig(
            disableP2p = true,
            p2pOnly = true,
            enableKcpProxy = true,
            bindDevice = false,
            disableEncryption = true,
        )
        assertEquals(2, countModifiedP2pFlags(modifiedCfg))
        assertEquals(1, countModifiedTransportFlags(modifiedCfg))
        assertEquals(2, countModifiedSecurityFlags(modifiedCfg))
    }
}
