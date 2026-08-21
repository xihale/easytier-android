package com.easytier.android.core.toml

import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.NetworkingMethod
import com.easytier.android.data.model.PortForwardEntry
import org.tomlj.Toml

/**
 * EasyTier TOML -> NetworkConfig 解析器（导入配置用）。
 *
 * 支持完整 TOML 结构（instance_name/[network_identity]/[[peer]]/[[proxy_network]]/
 * [flags]/[[port_forward]]/[vpn_portal_config]），未知 flags 保留原值。
 */
object TomlImporter {

    fun parse(tomlString: String): Result<NetworkConfig> = runCatching {
        val toml = Toml.parse(tomlString)
        if (toml.hasErrors()) {
            throw IllegalArgumentException(
                toml.errors().joinToString("\n") { "${it.position()}: ${it.message}" },
            )
        }
        val identity = toml.getTable("network_identity")
        val peers = toml.getArray("peer")?.toList().orEmpty()
            .mapNotNull { (it as? org.tomlj.TomlTable)?.getString("uri") }
            .filter { it.isNotBlank() }

        val ipv4 = toml.getString("ipv4")
        val (virtualIp, networkLen) = parseCidr(ipv4)

        val flags = toml.getTable("flags")
        val proxyNetworks = toml.getArray("proxy_network")?.toList().orEmpty()
            .mapNotNull { it as? org.tomlj.TomlTable }
            .mapNotNull { t ->
                val cidr = t.getString("cidr") ?: return@mapNotNull null
                val mapped = t.getString("mapped_cidr")
                if (mapped != null) "$cidr->$mapped" else cidr
            }

        val portForwards = toml.getArray("port_forward")?.toList().orEmpty()
            .mapNotNull { it as? org.tomlj.TomlTable }
            .mapNotNull { t ->
                val bind = t.getString("bind_addr") ?: return@mapNotNull null
                val dst = t.getString("dst_addr") ?: return@mapNotNull null
                val (bindIp, bindPort) = parseAddr(bind)
                val (dstIp, dstPort) = parseAddr(dst)
                PortForwardEntry(
                    bindIp = bindIp,
                    bindPort = bindPort,
                    dstIp = dstIp,
                    dstPort = dstPort,
                    proto = t.getString("proto") ?: "tcp",
                )
            }

        val vpnPortal = toml.getTable("vpn_portal_config")

        // 推断 networking method
        val networkingMethod = when {
            peers.isEmpty() -> NetworkingMethod.Standalone
            peers.size == 1 && isPublicServerUrl(peers[0]) -> NetworkingMethod.PublicServer
            else -> NetworkingMethod.Manual
        }

        NetworkConfig(
            instanceId = null, // 导入的身份属于导出方安装，必须换新身份，避免两个客户端同 peer_id 互踢（null 保存时会生成并持久化新 UUID）
            dhcp = toml.getBoolean("dhcp") ?: true,
            virtualIpv4 = virtualIp,
            networkLength = networkLen,
            hostname = toml.getString("hostname"),
            networkName = identity?.getString("network_name")
                ?: toml.getString("instance_name") ?: "default",
            networkSecret = identity?.getString("network_secret"),
            networkingMethod = networkingMethod,
            publicServerUrl = if (networkingMethod == NetworkingMethod.PublicServer) peers.firstOrNull() else null,
            peerUrls = if (networkingMethod == NetworkingMethod.Manual) peers else emptyList(),
            proxyCidrs = proxyNetworks,
            listenerUrls = toml.getArray("listeners")?.toList().orEmpty().mapNotNull { it as? String },
            exitNodes = toml.getArray("exit_nodes")?.toList().orEmpty().mapNotNull { it as? String },
            advancedSettings = flags != null,
            latencyFirst = flags?.getBoolean("latency_first"),
            useSmoltcp = flags?.getBoolean("use_smoltcp"),
            enableKcpProxy = flags?.getBoolean("enable_kcp_proxy"),
            disableKcpInput = flags?.getBoolean("disable_kcp_input"),
            disableP2p = flags?.getBoolean("disable_p2p"),
            bindDevice = flags?.getBoolean("bind_device"),
            noTun = flags?.getBoolean("no_tun"),
            enableExitNode = flags?.getBoolean("enable_exit_node"),
            relayAllPeerRpc = flags?.getBoolean("relay_all_peer_rpc"),
            multiThread = flags?.getBoolean("multi_thread"),
            proxyForwardBySystem = flags?.getBoolean("proxy_forward_by_system"),
            disableEncryption = flags?.getBoolean("enable_encryption")?.let { !it },
            disableUdpHolePunching = flags?.getBoolean("disable_udp_hole_punching"),
            disableTcpHolePunching = flags?.getBoolean("disable_tcp_hole_punching"),
            disableSymHolePunching = flags?.getBoolean("disable_sym_hole_punching"),
            mtu = (flags?.get("mtu") as? Number)?.toInt(),
            p2pOnly = flags?.getBoolean("p2p_only"),
            lazyP2p = flags?.getBoolean("lazy_p2p"),
            needP2p = flags?.getBoolean("need_p2p"),
            disableUpnp = flags?.getBoolean("disable_upnp"),
            disableRelayData = flags?.getBoolean("disable_relay_data"),
            enableUdpBroadcastRelay = flags?.getBoolean("enable_udp_broadcast_relay"),
            disableIpv6 = flags?.getBoolean("enable_ipv6")?.let { !it },
            enableSocks5 = toml.getString("socks5_proxy")?.let { true },
            socks5Port = toml.getString("socks5_proxy")?.substringAfterLast(':')?.toIntOrNull(),
            dataCompressAlgo = flags?.getString("data_compress_algo"),
            encryptionAlgorithm = flags?.getString("encryption_algorithm"),
            devName = flags?.getString("dev_name"),
            instanceRecvBpsLimit = (flags?.get("instance_recv_bps_limit") as? Number)?.toLong(),
            enableVpnPortal = vpnPortal != null,
            vpnPortalListenPort = vpnPortal?.getString("wireguard_listen")
                ?.substringAfterLast(':')?.toIntOrNull() ?: 11010,
            vpnPortalClientNetworkAddr = vpnPortal?.getString("client_cidr")
                ?.substringBefore('/') ?: "10.126.126.0",
            vpnPortalClientNetworkLen = vpnPortal?.getString("client_cidr")
                ?.substringAfterLast('/')?.toIntOrNull() ?: 24,
            portForwards = portForwards,
            acl = extractAclToml(tomlString), // 原样保留 [acl] 段，生成器会原样回写
        )
    }

    /** 提取 TOML 中的 [acl] 段原文（供重新导出保留）。容忍行首缩进，截取到下一个表头或文末。 */
    fun extractAclToml(tomlString: String): String? {
        val match = Regex("(?ms)^[ \\t]*\\[acl\\].*?(?=^[ \\t]*\\[|\\z)").find(tomlString.trim())
        return match?.value?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun parseCidr(ipv4: String?): Pair<String?, Int> {
        if (ipv4 == null) return null to 24
        val parts = ipv4.split('/')
        return parts[0] to (parts.getOrNull(1)?.toIntOrNull() ?: 24)
    }

    private fun parseAddr(addr: String): Pair<String, Int> {
        val ip = addr.substringBeforeLast(':')
        val port = addr.substringAfterLast(':').toIntOrNull() ?: 0
        return ip to port
    }

    private fun isPublicServerUrl(url: String): Boolean {
        // 简单启发式：公共服务器 URL 通常含公共域名/IP 且无内网特征
        return !url.contains("192.168.") && !url.contains("10.") && !url.contains("172.16.")
    }
}
