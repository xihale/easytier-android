package com.easytier.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 运行时网络信息模型，与 NetworkInstanceRunningInfoMap 的 pbjson 输出对齐。
 * 实测 pbjson 输出 snake_case 原始字段名（dev_name/my_node_info/...），uint64 为字符串。
 * 多词字段必须加 @SerialName，否则被 ignoreUnknownKeys 静默丢弃、全部读到默认值。
 */
object RuntimeJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
}

/** 是否本地/内网地址（不对外展示为公网 IP）。 */
fun isLocalAddress(ip: String): Boolean {
    val v = ip.trim().substringBefore('%').lowercase()
    return when {
        v == "::1" || v == "::" || v == "localhost" -> true
        v.startsWith("127.") -> true
        v.startsWith("10.") -> true
        v.startsWith("192.168.") -> true
        v.startsWith("169.254.") || v.startsWith("fe80") || v.startsWith("fe90") -> true
        v.startsWith("172.") -> {
            val second = v.split('.').getOrNull(1)?.toIntOrNull() ?: 0
            second in 16..31
        }
        v.startsWith("fc") || v.startsWith("fd") -> true // IPv6 ULA
        else -> false
    }
}

/** 公网 IP 列表：过滤本地/内网地址（::1、127.x、192.168.x 等不展示）。 */
fun StunInfo.publicIps(): List<String> = publicIp
    .filter { it.isNotBlank() && !isLocalAddress(it) }

@Serializable
data class NetworkInstanceRunningInfoMap(
    val map: Map<String, NetworkInstanceRunningInfo> = emptyMap(),
)

@Serializable
data class NetworkInstanceRunningInfo(
    @SerialName("dev_name") val devName: String = "",
    @SerialName("my_node_info") val myNodeInfo: MyNodeInfo? = null,
    val events: List<String> = emptyList(),
    val routes: List<Route> = emptyList(),
    val peers: List<PeerInfo> = emptyList(),
    val running: Boolean = false,
    @SerialName("error_msg") val errorMsg: String? = null,
)

@Serializable
data class MyNodeInfo(
    @SerialName("virtual_ipv4") val virtualIpv4: Ipv4Inet? = null,
    val hostname: String = "",
    val version: String = "",
    @SerialName("stun_info") val stunInfo: StunInfo? = null,
    val listeners: List<UrlValue> = emptyList(),
    @SerialName("peer_id") val peerId: Long = 0,
)

@Serializable
data class Ipv4Inet(
    val address: Ipv4Addr? = null,
    @SerialName("network_length") val networkLength: Long = 24,
) {
    /** 转成 "10.1.1.1/24" 形式。 */
    fun toCidrString(): String? = address?.let { "${it.toDottedString()}/$networkLength" }

    /** 仅 IP 部分 "10.1.1.1"。 */
    fun toIpString(): String? = address?.toDottedString()
}

@Serializable
data class Ipv4Addr(val addr: Long = 0) {
    fun toDottedString(): String =
        "%d.%d.%d.%d".format(
            (addr shr 24) and 0xFF,
            (addr shr 16) and 0xFF,
            (addr shr 8) and 0xFF,
            addr and 0xFF,
        )
}

@Serializable
data class StunInfo(
    @SerialName("udp_nat_type") val udpNatType: NatType = NatType.Unknown,
    @SerialName("tcp_nat_type") val tcpNatType: NatType = NatType.Unknown,
    @SerialName("public_ip") val publicIp: List<String> = emptyList(),
)

@Serializable
enum class NatType {
    Unknown, OpenInternet, NoPAT, FullCone, Restricted, PortRestricted,
    Symmetric, SymUdpFirewall, SymmetricEasyInc, SymmetricEasyDec;

    val label: String
        get() = when (this) {
            Unknown -> "未知"
            OpenInternet -> "公网开放"
            NoPAT -> "NoPAT"
            FullCone -> "FullCone"
            Restricted -> "Restricted"
            PortRestricted -> "PortRestricted"
            Symmetric -> "对称型"
            SymUdpFirewall -> "UDP防火墙"
            SymmetricEasyInc -> "对称易入"
            SymmetricEasyDec -> "对称易出"
        }
}

@Serializable
data class Route(
    @SerialName("peer_id") val peerId: Long = 0,
    @SerialName("ipv4_addr") val ipv4Addr: Ipv4Inet? = null,
    @SerialName("next_hop_peer_id") val nextHopPeerId: Long = 0,
    val cost: Long = 0,
    @SerialName("path_latency") val pathLatency: Long = 0,
    @SerialName("proxy_cidrs") val proxyCidrs: List<String> = emptyList(),
    val hostname: String = "",
    val version: String = "",
    @SerialName("stun_info") val stunInfo: StunInfo? = null,
    @SerialName("feature_flag") val featureFlag: PeerFeatureFlag? = null,
) {
    /** 下一跳就是自己：直连（官方 GUI 的 cost==1 / p2p）。 */
    val isDirect: Boolean
        get() = (nextHopPeerId != 0L && nextHopPeerId == peerId) || cost == 1L

    val isPublicServer: Boolean get() = featureFlag?.isPublicServer == true
}

@Serializable
data class PeerFeatureFlag(
    @SerialName("is_public_server") val isPublicServer: Boolean = false,
    @SerialName("avoid_relay_data") val avoidRelayData: Boolean = false,
)

/** 路由表节点 + 可选直连隧道（镜像官方 peer_route_pairs）。 */
data class PeerRoutePair(
    val route: Route,
    val peer: PeerInfo? = null,
) {
    val defaultConn: PeerConnInfo? get() = peer?.defaultConn
}

/** 官方 Status 页用 peer_route_pairs：全网节点，不只是直连 peers。 */
fun NetworkInstanceRunningInfo.peerRoutePairs(): List<PeerRoutePair> {
    val peerById = peers.associateBy { it.peerId }
    return routes
        .map { route -> PeerRoutePair(route = route, peer = peerById[route.peerId]) }
        .sortedWith(
            compareBy<PeerRoutePair> { !it.route.isPublicServer }
                .thenBy { it.route.ipv4Addr?.address?.addr ?: Long.MAX_VALUE },
        )
}

@Serializable
data class PeerInfo(
    @SerialName("peer_id") val peerId: Long = 0,
    val conns: List<PeerConnInfo> = emptyList(),
) {
    /** 展示用默认连接：优先未关闭的直连。 */
    val defaultConn: PeerConnInfo?
        get() = conns.firstOrNull { !it.isClosed } ?: conns.firstOrNull()
}

@Serializable
data class PeerConnInfo(
    @SerialName("conn_id") val connId: String = "",
    val features: List<String> = emptyList(),
    val tunnel: TunnelInfo? = null,
    val stats: PeerConnStats? = null,
    @SerialName("loss_rate") val lossRate: Float = 0f,
    @SerialName("is_client") val isClient: Boolean = false,
    @SerialName("is_closed") val isClosed: Boolean = false,
)

@Serializable
data class TunnelInfo(
    @SerialName("tunnel_type") val tunnelType: String = "",
    @SerialName("local_addr") val localAddr: UrlValue? = null,
    @SerialName("remote_addr") val remoteAddr: UrlValue? = null,
) {
    /** 展示用协议名（tcp / udp / wg / …）。 */
    val protoLabel: String
        get() = tunnelType.substringBefore("://").ifBlank { tunnelType }.ifBlank { "--" }
}

@Serializable
data class PeerConnStats(
    @SerialName("rx_bytes") val rxBytes: String = "0",
    @SerialName("tx_bytes") val txBytes: String = "0",
    @SerialName("latency_us") val latencyUs: String = "0",
) {
    val rxBytesLong: Long get() = rxBytes.toLongOrNull() ?: 0
    val txBytesLong: Long get() = txBytes.toLongOrNull() ?: 0
    val latencyMs: Long get() = (latencyUs.toLongOrNull() ?: 0) / 1000
}

@Serializable
data class UrlValue(val url: String = "")
