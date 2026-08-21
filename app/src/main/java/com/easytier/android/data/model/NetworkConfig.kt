package com.easytier.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 网络配置模型。
 *
 * 字段与 easytier-proto 的 api.manage.NetworkConfig 对齐（snake_case 序列化），
 * TOML 生成逻辑镜像 easytier-core/src/config/api_input.rs 的 gen_config。
 */
@Serializable
data class NetworkConfig(
    @SerialName("instance_id") val instanceId: String? = null,
    @SerialName("dhcp") val dhcp: Boolean = true,
    @SerialName("virtual_ipv4") val virtualIpv4: String? = null,
    @SerialName("network_length") val networkLength: Int = 24,
    @SerialName("hostname") val hostname: String? = null,
    @SerialName("network_name") val networkName: String = "default",
    @SerialName("network_secret") val networkSecret: String? = null,
    @SerialName("networking_method") val networkingMethod: NetworkingMethod = NetworkingMethod.PublicServer,
    @SerialName("public_server_url") val publicServerUrl: String? = null,
    @SerialName("peer_urls") val peerUrls: List<String> = emptyList(),
    @SerialName("proxy_cidrs") val proxyCidrs: List<String> = emptyList(),
    @SerialName("listener_urls") val listenerUrls: List<String> = listOf(
        "tcp://0.0.0.0:11010",
        "udp://0.0.0.0:11010",
        "wg://0.0.0.0:11011",
    ),
    @SerialName("advanced_settings") val advancedSettings: Boolean = false,
    // --- 高级开关（null 表示使用核心默认值）---
    @SerialName("latency_first") val latencyFirst: Boolean? = null,
    @SerialName("use_smoltcp") val useSmoltcp: Boolean? = null,
    @SerialName("enable_kcp_proxy") val enableKcpProxy: Boolean? = null,
    @SerialName("disable_kcp_input") val disableKcpInput: Boolean? = null,
    @SerialName("disable_p2p") val disableP2p: Boolean? = null,
    @SerialName("bind_device") val bindDevice: Boolean? = null,
    @SerialName("no_tun") val noTun: Boolean? = null,
    @SerialName("enable_exit_node") val enableExitNode: Boolean? = null,
    @SerialName("relay_all_peer_rpc") val relayAllPeerRpc: Boolean? = null,
    @SerialName("multi_thread") val multiThread: Boolean? = null,
    @SerialName("enable_relay_network_whitelist") val enableRelayNetworkWhitelist: Boolean? = null,
    @SerialName("relay_network_whitelist") val relayNetworkWhitelist: List<String>? = null,
    @SerialName("enable_manual_routes") val enableManualRoutes: Boolean? = null,
    @SerialName("routes") val routes: List<String>? = null,
    @SerialName("exit_nodes") val exitNodes: List<String>? = null,
    @SerialName("proxy_forward_by_system") val proxyForwardBySystem: Boolean? = null,
    @SerialName("disable_encryption") val disableEncryption: Boolean? = null,
    @SerialName("enable_socks5") val enableSocks5: Boolean? = null,
    @SerialName("socks5_port") val socks5Port: Int? = null,
    @SerialName("disable_udp_hole_punching") val disableUdpHolePunching: Boolean? = null,
    @SerialName("mtu") val mtu: Int? = null,
    @SerialName("mapped_listeners") val mappedListeners: List<String>? = null,
    @SerialName("enable_magic_dns") val enableMagicDns: Boolean? = null,
    @SerialName("enable_private_mode") val enablePrivateMode: Boolean? = null,
    @SerialName("enable_quic_proxy") val enableQuicProxy: Boolean? = null,
    @SerialName("disable_quic_input") val disableQuicInput: Boolean? = null,
    @SerialName("disable_sym_hole_punching") val disableSymHolePunching: Boolean? = null,
    @SerialName("p2p_only") val p2pOnly: Boolean? = null,
    @SerialName("disable_tcp_hole_punching") val disableTcpHolePunching: Boolean? = null,
    @SerialName("disable_ipv6") val disableIpv6: Boolean? = null,
    @SerialName("disable_upnp") val disableUpnp: Boolean? = null,
    @SerialName("disable_relay_data") val disableRelayData: Boolean? = null,
    @SerialName("enable_udp_broadcast_relay") val enableUdpBroadcastRelay: Boolean? = null,
    @SerialName("lazy_p2p") val lazyP2p: Boolean? = null,
    @SerialName("need_p2p") val needP2p: Boolean? = null,
    @SerialName("instance_recv_bps_limit") val instanceRecvBpsLimit: Long? = null,
    @SerialName("data_compress_algo") val dataCompressAlgo: String? = null,
    @SerialName("encryption_algorithm") val encryptionAlgorithm: String? = null,
    @SerialName("dev_name") val devName: String? = null,
    // --- VPN Portal ---
    @SerialName("enable_vpn_portal") val enableVpnPortal: Boolean = false,
    @SerialName("vpn_portal_listen_port") val vpnPortalListenPort: Int = 11010,
    @SerialName("vpn_portal_client_network_addr") val vpnPortalClientNetworkAddr: String = "10.126.126.0",
    @SerialName("vpn_portal_client_network_len") val vpnPortalClientNetworkLen: Int = 24,
    // --- 端口转发 ---
    @SerialName("port_forwards") val portForwards: List<PortForwardEntry> = emptyList(),
    // --- ACL ---
    @SerialName("acl") val acl: String? = null, // TOML 内联 ACL 片段
)

@Serializable
enum class NetworkingMethod {
    @SerialName("0") PublicServer,
    @SerialName("1") Manual,
    @SerialName("2") Standalone;

    val label: String
        get() = when (this) {
            PublicServer -> "公共服务器"
            Manual -> "手动节点"
            Standalone -> "独立模式"
        }
}

@Serializable
data class PortForwardEntry(
    @SerialName("bind_ip") val bindIp: String = "0.0.0.0",
    @SerialName("bind_port") val bindPort: Int = 0,
    @SerialName("dst_ip") val dstIp: String = "",
    @SerialName("dst_port") val dstPort: Int = 0,
    @SerialName("proto") val proto: String = "tcp",
)

/** 已保存的网络条目（配置 + 展示元数据）。enabled = 服务启动时加入该网络。 */
@Serializable
data class SavedNetwork(
    val id: String, // instance_id（UUID）
    val config: NetworkConfig,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
