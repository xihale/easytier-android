package com.easytier.android.ui.screens.editor

import androidx.annotation.StringRes
import com.easytier.android.R
import com.easytier.android.data.model.NetworkConfig

/** 编辑页分区枚举，决定搜索结果卡片分组与所在 Tab 索引。 */
enum class EditorCategory(
    @StringRes val titleRes: Int,
    val tabIndex: Int,
) {
    Basic(R.string.editor_section_basic, 0),
    Address(R.string.editor_section_address, 0),
    Networking(R.string.editor_section_networking, 0),
    Runtime(R.string.editor_section_runtime, 0),
    Details(R.string.editor_section_details, 1),
    MagicDns(R.string.flag_magic_dns, 1),
    CommonFlags(R.string.editor_section_common_flags, 1),
    P2pFlags(R.string.editor_section_p2p, 1),
    TransportFlags(R.string.editor_section_transport, 1),
    SecurityFlags(R.string.editor_section_security, 1),
    RoutesAndLimits(R.string.editor_section_routes, 1),
    PortForward(R.string.editor_section_pf, 2),
}

/** 网络编辑页可搜索的设置项标识（覆盖基本、高级全部 25 个开关、详情/路由、端口转发）。 */
enum class EditorSettingId {
    NetworkName,
    NetworkSecret,
    Dhcp,
    VirtualIpv4,
    NetworkingMethod,
    PublicServerUrl,
    PeerUrls,
    EnableExitNode,
    Hostname,
    DevName,
    Mtu,
    ListenerUrls,
    MagicDns,
    DnsZone,
    LatencyFirst,
    EnablePrivateMode,
    DisableIpv6,
    MultiThread,
    NoTun,
    DisableP2p,
    P2pOnly,
    LazyP2p,
    NeedP2p,
    DisableUdpHolePunching,
    DisableTcpHolePunching,
    DisableSymHolePunching,
    DisableUpnp,
    DisableRelayData,
    RelayAllPeerRpc,
    EnableKcpProxy,
    DisableKcpInput,
    EnableQuicProxy,
    DisableQuicInput,
    UseSmoltcp,
    EnableUdpBroadcastRelay,
    DisableEncryption,
    BindDevice,
    ProxyForwardBySystem,
    ProxyCidrs,
    ManualRoutes,
    RelayWhitelist,
    ExitNodes,
    RecvBpsLimit,
    PortForwards,
}

/** 编辑页设置项元数据，用于跨 Tab 即时搜索与分组渲染。 */
data class EditorSettingMeta(
    val id: EditorSettingId,
    val category: EditorCategory,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int? = null,
    val keywords: List<String> = emptyList(),
)

/** 全部可搜索网络设置项注册表。 */
val ALL_EDITOR_SETTINGS: List<EditorSettingMeta> = listOf(
    // --- Basic ---
    EditorSettingMeta(
        id = EditorSettingId.NetworkName,
        category = EditorCategory.Basic,
        titleRes = R.string.editor_name,
        keywords = listOf("network_name", "instance_name", "name", "名称", "网络名"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.NetworkSecret,
        category = EditorCategory.Basic,
        titleRes = R.string.editor_secret,
        subtitleRes = R.string.editor_secret_hint,
        keywords = listOf("network_secret", "secret", "password", "密钥", "密码"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.Dhcp,
        category = EditorCategory.Address,
        titleRes = R.string.editor_dhcp,
        subtitleRes = R.string.editor_dhcp_subtitle,
        keywords = listOf("dhcp", "auto", "ip", "自动分配"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.VirtualIpv4,
        category = EditorCategory.Address,
        titleRes = R.string.editor_virtual_ipv4,
        keywords = listOf("ipv4", "virtual_ipv4", "cidr", "prefix", "network_length", "虚拟ip", "前缀"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.NetworkingMethod,
        category = EditorCategory.Networking,
        titleRes = R.string.editor_section_networking,
        keywords = listOf("networking_method", "public", "manual", "standalone", "联网", "模式"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.PublicServerUrl,
        category = EditorCategory.Networking,
        titleRes = R.string.editor_public_server_url,
        keywords = listOf("public_server_url", "server", "peer", "公共服务器"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.PeerUrls,
        category = EditorCategory.Networking,
        titleRes = R.string.editor_initial_peers,
        keywords = listOf("peer", "peers", "peer_urls", "initial", "节点", "对等", "初始节点"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.EnableExitNode,
        category = EditorCategory.Runtime,
        titleRes = R.string.editor_exit_node,
        subtitleRes = R.string.editor_exit_node_subtitle,
        keywords = listOf("enable_exit_node", "exit", "gateway", "出口节点", "网关"),
    ),

    // --- Advanced: Details ---
    EditorSettingMeta(
        id = EditorSettingId.Hostname,
        category = EditorCategory.Details,
        titleRes = R.string.editor_hostname,
        keywords = listOf("hostname", "host", "主机名"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.DevName,
        category = EditorCategory.Details,
        titleRes = R.string.editor_dev_name,
        keywords = listOf("dev_name", "tun", "interface", "设备名称", "网卡"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.Mtu,
        category = EditorCategory.Details,
        titleRes = R.string.editor_mtu,
        subtitleRes = R.string.editor_mtu_support,
        keywords = listOf("mtu", "packet", "分片"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.ListenerUrls,
        category = EditorCategory.Details,
        titleRes = R.string.editor_listeners,
        keywords = listOf("listeners", "listener_urls", "listen", "port", "监听", "端口"),
    ),

    // --- Advanced: Magic DNS ---
    EditorSettingMeta(
        id = EditorSettingId.MagicDns,
        category = EditorCategory.MagicDns,
        titleRes = R.string.flag_magic_dns,
        subtitleRes = R.string.flag_magic_dns_subtitle,
        keywords = listOf("magic_dns", "accept_dns", "dns", "domain", "域名"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.DnsZone,
        category = EditorCategory.MagicDns,
        titleRes = R.string.editor_dns_zone,
        keywords = listOf("tld_dns_zone", "zone", "suffix", "dns", "后缀", "域名"),
    ),

    // --- Advanced: Common Flags ---
    EditorSettingMeta(
        id = EditorSettingId.LatencyFirst,
        category = EditorCategory.CommonFlags,
        titleRes = R.string.flag_latency_first,
        subtitleRes = R.string.flag_latency_first_subtitle,
        keywords = listOf("latency_first", "latency", "ping", "延迟"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.EnablePrivateMode,
        category = EditorCategory.CommonFlags,
        titleRes = R.string.flag_private_mode,
        subtitleRes = R.string.flag_private_mode_subtitle,
        keywords = listOf("enable_private_mode", "private", "私有"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.DisableIpv6,
        category = EditorCategory.CommonFlags,
        titleRes = R.string.flag_disable_ipv6,
        subtitleRes = R.string.flag_disable_ipv6_subtitle,
        keywords = listOf("disable_ipv6", "enable_ipv6", "ipv6"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.MultiThread,
        category = EditorCategory.CommonFlags,
        titleRes = R.string.flag_multi_thread,
        subtitleRes = R.string.flag_multi_thread_subtitle,
        keywords = listOf("multi_thread", "thread", "多线程"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.NoTun,
        category = EditorCategory.CommonFlags,
        titleRes = R.string.flag_no_tun,
        subtitleRes = R.string.flag_no_tun_subtitle,
        keywords = listOf("no_tun", "tun", "无tun", "无网卡"),
    ),

    // --- Advanced: P2P & Hole Punching ---
    EditorSettingMeta(
        id = EditorSettingId.DisableP2p,
        category = EditorCategory.P2pFlags,
        titleRes = R.string.flag_disable_p2p,
        subtitleRes = R.string.flag_disable_p2p_subtitle,
        keywords = listOf("disable_p2p", "p2p", "relay", "禁用p2p"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.P2pOnly,
        category = EditorCategory.P2pFlags,
        titleRes = R.string.flag_p2p_only,
        subtitleRes = R.string.flag_p2p_only_subtitle,
        keywords = listOf("p2p_only", "p2p", "仅p2p", "直连"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.LazyP2p,
        category = EditorCategory.P2pFlags,
        titleRes = R.string.flag_lazy_p2p,
        subtitleRes = R.string.flag_lazy_p2p_subtitle,
        keywords = listOf("lazy_p2p", "p2p", "惰性"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.NeedP2p,
        category = EditorCategory.P2pFlags,
        titleRes = R.string.flag_need_p2p,
        subtitleRes = R.string.flag_need_p2p_subtitle,
        keywords = listOf("need_p2p", "p2p", "要求p2p"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.DisableUdpHolePunching,
        category = EditorCategory.P2pFlags,
        titleRes = R.string.flag_disable_udp_hole_punching,
        subtitleRes = R.string.flag_disable_udp_hole_punching_subtitle,
        keywords = listOf("disable_udp_hole_punching", "udp", "hole", "punch", "打洞"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.DisableTcpHolePunching,
        category = EditorCategory.P2pFlags,
        titleRes = R.string.flag_disable_tcp_hole_punching,
        subtitleRes = R.string.flag_disable_tcp_hole_punching_subtitle,
        keywords = listOf("disable_tcp_hole_punching", "tcp", "hole", "punch", "打洞"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.DisableSymHolePunching,
        category = EditorCategory.P2pFlags,
        titleRes = R.string.flag_disable_sym_hole_punching,
        subtitleRes = R.string.flag_disable_sym_hole_punching_subtitle,
        keywords = listOf("disable_sym_hole_punching", "sym", "nat", "hole", "punch", "对称", "打洞"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.DisableUpnp,
        category = EditorCategory.P2pFlags,
        titleRes = R.string.flag_disable_upnp,
        subtitleRes = R.string.flag_disable_upnp_subtitle,
        keywords = listOf("disable_upnp", "upnp", "端口映射"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.DisableRelayData,
        category = EditorCategory.P2pFlags,
        titleRes = R.string.flag_disable_relay_data,
        subtitleRes = R.string.flag_disable_relay_data_subtitle,
        keywords = listOf("disable_relay_data", "relay", "中继"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.RelayAllPeerRpc,
        category = EditorCategory.P2pFlags,
        titleRes = R.string.flag_relay_all_peer_rpc,
        subtitleRes = R.string.flag_relay_all_peer_rpc_subtitle,
        keywords = listOf("relay_all_peer_rpc", "rpc", "peer", "中继"),
    ),

    // --- Advanced: Transport & Proxy ---
    EditorSettingMeta(
        id = EditorSettingId.EnableKcpProxy,
        category = EditorCategory.TransportFlags,
        titleRes = R.string.flag_kcp_proxy,
        subtitleRes = R.string.flag_kcp_proxy_subtitle,
        keywords = listOf("enable_kcp_proxy", "kcp", "proxy"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.DisableKcpInput,
        category = EditorCategory.TransportFlags,
        titleRes = R.string.flag_disable_kcp_input,
        subtitleRes = R.string.flag_disable_kcp_input_subtitle,
        keywords = listOf("disable_kcp_input", "kcp", "input", "监听"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.EnableQuicProxy,
        category = EditorCategory.TransportFlags,
        titleRes = R.string.flag_quic_proxy,
        subtitleRes = R.string.flag_quic_proxy_subtitle,
        keywords = listOf("enable_quic_proxy", "quic", "proxy"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.DisableQuicInput,
        category = EditorCategory.TransportFlags,
        titleRes = R.string.flag_disable_quic_input,
        subtitleRes = R.string.flag_disable_quic_input_subtitle,
        keywords = listOf("disable_quic_input", "quic", "input", "监听"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.UseSmoltcp,
        category = EditorCategory.TransportFlags,
        titleRes = R.string.flag_smoltcp,
        subtitleRes = R.string.flag_smoltcp_subtitle,
        keywords = listOf("use_smoltcp", "smoltcp", "tcpip"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.EnableUdpBroadcastRelay,
        category = EditorCategory.TransportFlags,
        titleRes = R.string.flag_udp_broadcast_relay,
        subtitleRes = R.string.flag_udp_broadcast_relay_subtitle,
        keywords = listOf("enable_udp_broadcast_relay", "udp", "broadcast", "广播", "局域网"),
    ),

    // --- Advanced: Security & System ---
    EditorSettingMeta(
        id = EditorSettingId.DisableEncryption,
        category = EditorCategory.SecurityFlags,
        titleRes = R.string.flag_disable_encryption,
        subtitleRes = R.string.flag_disable_encryption_subtitle,
        keywords = listOf("disable_encryption", "enable_encryption", "encrypt", "加密"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.BindDevice,
        category = EditorCategory.SecurityFlags,
        titleRes = R.string.flag_bind_device,
        subtitleRes = R.string.flag_bind_device_subtitle,
        keywords = listOf("bind_device", "device", "网卡"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.ProxyForwardBySystem,
        category = EditorCategory.SecurityFlags,
        titleRes = R.string.flag_proxy_forward_by_system,
        subtitleRes = R.string.flag_proxy_forward_by_system_subtitle,
        keywords = listOf("proxy_forward_by_system", "proxy", "forward", "系统转发"),
    ),

    // --- Advanced: Routes & Limits ---
    EditorSettingMeta(
        id = EditorSettingId.ProxyCidrs,
        category = EditorCategory.RoutesAndLimits,
        titleRes = R.string.editor_proxy_cidrs,
        keywords = listOf("proxy_cidrs", "proxy_network", "cidr", "subnet", "网段", "子网"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.ManualRoutes,
        category = EditorCategory.RoutesAndLimits,
        titleRes = R.string.flag_manual_routes,
        subtitleRes = R.string.flag_manual_routes_subtitle,
        keywords = listOf("enable_manual_routes", "routes", "route", "路由"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.RelayWhitelist,
        category = EditorCategory.RoutesAndLimits,
        titleRes = R.string.flag_relay_whitelist,
        subtitleRes = R.string.flag_relay_whitelist_subtitle,
        keywords = listOf("enable_relay_network_whitelist", "relay_network_whitelist", "whitelist", "白名单"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.ExitNodes,
        category = EditorCategory.RoutesAndLimits,
        titleRes = R.string.editor_exit_nodes,
        keywords = listOf("exit_nodes", "exit", "出口节点"),
    ),
    EditorSettingMeta(
        id = EditorSettingId.RecvBpsLimit,
        category = EditorCategory.RoutesAndLimits,
        titleRes = R.string.editor_recv_bps_limit,
        keywords = listOf("instance_recv_bps_limit", "bps", "bandwidth", "limit", "rate", "限速", "带宽"),
    ),

    // --- Port Forward ---
    EditorSettingMeta(
        id = EditorSettingId.PortForwards,
        category = EditorCategory.PortForward,
        titleRes = R.string.editor_section_pf,
        subtitleRes = R.string.editor_pf_desc,
        keywords = listOf("port_forward", "port", "forward", "tcp", "udp", "端口转发"),
    ),
)

/** 过滤编辑页设置：按空白切词 AND 匹配，覆盖本地化文案、英文参数名与当前已填配置值。 */
fun filterEditorSettings(
    query: String,
    config: NetworkConfig,
    resolveText: (EditorSettingMeta) -> String,
): List<EditorSettingMeta> {
    val tokens = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return ALL_EDITOR_SETTINGS
    return ALL_EDITOR_SETTINGS.filter { meta ->
        val haystack = buildString {
            append(resolveText(meta).lowercase())
            append(' ')
            meta.keywords.forEach { append(it.lowercase()).append(' ') }
            append(currentValueSearchText(meta.id, config).lowercase())
        }
        tokens.all { token -> token in haystack }
    }
}

private fun currentValueSearchText(id: EditorSettingId, config: NetworkConfig): String = when (id) {
    EditorSettingId.NetworkName -> config.networkName
    EditorSettingId.NetworkSecret -> "" // 不匹配密钥原文，避免敏感内容通过搜索旁路探测
    EditorSettingId.Dhcp -> if (config.dhcp) "dhcp on" else "manual static"
    EditorSettingId.VirtualIpv4 -> "${config.virtualIpv4.orEmpty()}/${config.networkLength}"
    EditorSettingId.NetworkingMethod -> config.networkingMethod.name
    EditorSettingId.PublicServerUrl -> config.publicServerUrl.orEmpty()
    EditorSettingId.PeerUrls -> config.peerUrls.joinToString(" ")
    EditorSettingId.EnableExitNode -> ""
    EditorSettingId.Hostname -> config.hostname.orEmpty()
    EditorSettingId.DevName -> config.devName.orEmpty()
    EditorSettingId.Mtu -> config.mtu?.toString().orEmpty()
    EditorSettingId.ListenerUrls -> config.listenerUrls.joinToString(" ")
    EditorSettingId.MagicDns, EditorSettingId.DnsZone -> config.tldDnsZone.orEmpty()
    EditorSettingId.ProxyCidrs -> config.proxyCidrs.joinToString(" ")
    EditorSettingId.ManualRoutes -> config.routes.orEmpty().joinToString(" ")
    EditorSettingId.RelayWhitelist -> config.relayNetworkWhitelist.orEmpty().joinToString(" ")
    EditorSettingId.ExitNodes -> config.exitNodes.orEmpty().joinToString(" ")
    EditorSettingId.RecvBpsLimit -> config.instanceRecvBpsLimit?.toString().orEmpty()
    EditorSettingId.PortForwards -> config.portForwards.joinToString(" ") {
        "${it.proto} ${it.bindIp}:${it.bindPort} ${it.dstIp}:${it.dstPort}"
    }
    else -> ""
}

/** 统计「P2P 与打洞」折叠分组中偏离核心默认值的开关数。 */
fun countModifiedP2pFlags(config: NetworkConfig): Int = listOf(
    config.disableP2p == true,
    config.p2pOnly == true,
    config.lazyP2p == true,
    config.needP2p == true,
    config.disableUdpHolePunching == true,
    config.disableTcpHolePunching == true,
    config.disableSymHolePunching == true,
    config.disableUpnp == true,
    config.disableRelayData == true,
    config.relayAllPeerRpc == true,
).count { it }

/** 统计「传输协议与代理」折叠分组中偏离核心默认值的开关数。 */
fun countModifiedTransportFlags(config: NetworkConfig): Int = listOf(
    config.enableKcpProxy == true,
    config.disableKcpInput == true,
    config.enableQuicProxy == true,
    config.disableQuicInput == true,
    config.useSmoltcp == true,
    config.enableUdpBroadcastRelay == true,
).count { it }

/** 统计「安全与底层」折叠分组中偏离核心默认值的开关数（bindDevice 默认 true）。 */
fun countModifiedSecurityFlags(config: NetworkConfig): Int = listOf(
    config.disableEncryption == true,
    config.bindDevice == false,
    config.proxyForwardBySystem == true,
).count { it }

/** 将 bit/s 数值格式化为可读速率预览（如 `100 Mbps (12.5 MB/s)`）。 */
fun formatBitratePreview(bps: Long): String {
    if (bps <= 0L) return "0 bps"
    val bitUnit = when {
        bps >= 1_000_000_000L -> formatCompact(bps / 1_000_000_000.0) + " Gbps"
        bps >= 1_000_000L -> formatCompact(bps / 1_000_000.0) + " Mbps"
        bps >= 1_000L -> formatCompact(bps / 1_000.0) + " Kbps"
        else -> "$bps bps"
    }
    val bytesPerSec = bps / 8.0
    val byteUnit = when {
        bytesPerSec >= 1_000_000.0 -> formatCompact(bytesPerSec / 1_000_000.0) + " MB/s"
        bytesPerSec >= 1_000.0 -> formatCompact(bytesPerSec / 1_000.0) + " KB/s"
        else -> formatCompact(bytesPerSec) + " B/s"
    }
    return "$bitUnit ($byteUnit)"
}

private fun formatCompact(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}
