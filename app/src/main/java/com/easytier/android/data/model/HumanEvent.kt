package com.easytier.android.data.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

/**
 * 核心事件 JSON → 人性化文案。
 *
 * 核心 journal 每条是 `{"time": RFC3339, "event": {Variant: payload}}`（serde 外部标签）。
 * ring/tun/unix 是进程内通道，对用户无意义，监听列表和事件都过滤掉。
 */
data class HumanEvent(
    val time: String,
    val title: String,
    val detail: String? = null,
    val kind: Kind = Kind.Info,
    val network: String? = null,
    val sortTime: String = "",
) {
    enum class Kind { Info, Success, Warning, Error }
}

private val INTERNAL_SCHEMES = setOf("ring", "tun", "unix")

/** 对外监听 URL；内部通道返回 null。 */
fun publicListenerUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return null
    val scheme = trimmed.substringBefore("://", missingDelimiterValue = trimmed).lowercase()
    if (scheme in INTERNAL_SCHEMES) return null
    return trimmed
}

fun MyNodeInfo.publicListeners(): List<String> =
    listeners.map { it.url }.mapNotNull(::publicListenerUrl)

fun parseHumanEvents(
    rawEvents: List<String>,
    peerNames: Map<Long, String> = emptyMap(),
    network: String? = null,
): List<HumanEvent> = rawEvents.mapNotNull { parseHumanEvent(it, peerNames, network) }

fun parseHumanEvent(
    raw: String,
    peerNames: Map<Long, String> = emptyMap(),
    network: String? = null,
): HumanEvent? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (!trimmed.startsWith("{")) {
        return HumanEvent(time = "", title = trimmed, network = network)
    }
    val root = runCatching { RuntimeJson.json.parseToJsonElement(trimmed) }.getOrNull()
        as? JsonObject ?: return HumanEvent(time = "", title = trimmed, network = network)
    val rawTime = root["time"]?.asPlain().orEmpty()
    val time = rawTime.toClockTime()
    val eventEl = root["event"] ?: return HumanEvent(time = time, title = trimmed, network = network, sortTime = rawTime)
    return humanizeEvent(eventEl, time, peerNames)?.copy(network = network, sortTime = rawTime)
}

private fun humanizeEvent(
    eventEl: JsonElement,
    time: String,
    peerNames: Map<Long, String>,
): HumanEvent? {
    val (key, payload) = eventVariant(eventEl) ?: return HumanEvent(time = time, title = eventEl.toString())
    return when (key) {
        "TunDeviceReady" ->
            HumanEvent(time, "TUN 设备就绪", payload.asPlain()?.takeIf { it.isNotBlank() }, HumanEvent.Kind.Success)
        "TunDeviceError" ->
            HumanEvent(time, "TUN 设备出错", payload.asPlain(), HumanEvent.Kind.Error)
        "PeerAdded" ->
            HumanEvent(time, "节点 ${peerLabel(payload, peerNames)} 加入网络", kind = HumanEvent.Kind.Success)
        "PeerRemoved" ->
            HumanEvent(time, "节点 ${peerLabel(payload, peerNames)} 离开网络")
        "PeerConnAdded" -> connEvent(time, payload, peerNames, added = true)
        "PeerConnRemoved" -> connEvent(time, payload, peerNames, added = false)
        "ListenerAdded" -> listenerAdded(time, payload)
        "ListenerRemoved" -> {
            val url = payload.asUrl() ?: return null
            if (publicListenerUrl(url) == null) return null
            HumanEvent(time, "停止监听 $url")
        }
        "ListenerAddFailed" -> {
            val (url, err) = payload.asPair()
            HumanEvent(time, "监听失败${url?.let { " $it" } ?: ""}", err, HumanEvent.Kind.Error)
        }
        "ListenerAcceptFailed" -> {
            val (url, err) = payload.asPair()
            HumanEvent(time, "接受连接失败${url?.let { " $it" } ?: ""}", err, HumanEvent.Kind.Warning)
        }
        "ConnectionAccepted" -> {
            val (local, remote) = payload.asPair()
            if (local != null && publicListenerUrl(local) == null) return null
            HumanEvent(time, "接受来自 ${remote.orDash()} 的连接", local)
        }
        "ConnectionError" -> {
            val parts = payload.asList()
            HumanEvent(
                time,
                "连接出错 ${parts.getOrNull(1).orDash()}",
                parts.getOrNull(2) ?: parts.getOrNull(0),
                HumanEvent.Kind.Warning,
            )
        }
        "Connecting" -> {
            val url = payload.asUrl() ?: payload.asPlain()
            HumanEvent(time, "正在连接 ${url.orDash()}")
        }
        "ConnectError" -> {
            val parts = payload.asList()
            HumanEvent(time, "连接 ${parts.getOrNull(0).orDash()} 失败", parts.getOrNull(2), HumanEvent.Kind.Warning)
        }
        "VpnPortalStarted" ->
            HumanEvent(time, "VPN Portal 已启动", payload.asPlain(), HumanEvent.Kind.Success)
        "VpnPortalClientConnected" -> {
            val (portal, client) = payload.asPair()
            HumanEvent(time, "Portal 客户端 ${client.orDash()} 已连接", portal, HumanEvent.Kind.Success)
        }
        "VpnPortalClientDisconnected" -> {
            val parts = payload.asList()
            HumanEvent(time, "Portal 客户端 ${parts.getOrNull(1).orDash()} 已断开", parts.getOrNull(0))
        }
        "DhcpIpv4Changed" -> {
            val parts = payload.asList()
            val old = parts.getOrNull(0)
            val new = parts.getOrNull(1)
            HumanEvent(
                time,
                when {
                    new.isNullOrBlank() -> "虚拟 IP 已释放"
                    old.isNullOrBlank() -> "获得虚拟 IP $new"
                    else -> "虚拟 IP $old → $new"
                },
                kind = HumanEvent.Kind.Success,
            )
        }
        "DhcpIpv4Conflicted" ->
            HumanEvent(time, "虚拟 IP 冲突", payload.asPlain(), HumanEvent.Kind.Error)
        "PortForwardAdded" ->
            HumanEvent(time, "添加端口转发", payload.asPlain() ?: payload.toString())
        "ProxyCidrsUpdated" -> {
            val added = payload.asObject()?.get("added")?.asList()
                ?: payload.asList().firstOrNull()?.let { listOf(it) }.orEmpty()
            val removed = payload.asObject()?.get("removed")?.asList().orEmpty()
            val bits = buildList {
                if (added.isNotEmpty()) add("新增 ${added.joinToString("、")}")
                if (removed.isNotEmpty()) add("移除 ${removed.joinToString("、")}")
            }
            HumanEvent(time, "代理网段更新", bits.joinToString("；").ifBlank { null })
        }
        "ListenerPortMappingEstablished", "UdpPortMappingEstablished" -> {
            val obj = payload.asObject()
            val mapped = obj?.get("mapped_listener")?.asUrl() ?: obj?.get("mapped_listener")?.asPlain()
            val backend = obj?.get("backend")?.asPlain()
            HumanEvent(
                time,
                "端口映射已建立${mapped?.let { " $it" } ?: ""}",
                backend?.let { "通过 $it" },
                HumanEvent.Kind.Success,
            )
        }
        "UdpBroadcastRelayStartResult" -> {
            val obj = payload.asObject()
            val err = obj?.get("error")?.asPlain()
            if (err != null) HumanEvent(time, "UDP 广播中继启动失败", err, HumanEvent.Kind.Warning)
            else HumanEvent(time, "UDP 广播中继已启动", obj?.get("capture_backend")?.asPlain(), HumanEvent.Kind.Success)
        }
        "PublicIpv6Changed", "PublicIpv6LeaseChanged" -> {
            val parts = payload.asList()
            HumanEvent(time, "公网 IPv6 ${parts.getOrNull(0).orDash()} → ${parts.getOrNull(1).orDash()}")
        }
        "CredentialChanged" -> HumanEvent(time, "凭证已更新")
        "ConfigPatched" -> HumanEvent(time, "配置已热更新")
        else -> HumanEvent(time, eventTitleFallback(key), payload.asPlain() ?: payload?.compact())
    }
}

private fun listenerAdded(time: String, payload: JsonElement?): HumanEvent? {
    val url = payload.asUrl() ?: payload.asPlain() ?: return null
    if (publicListenerUrl(url) == null) return null
    return HumanEvent(time, "开始监听 $url", kind = HumanEvent.Kind.Success)
}

private fun connEvent(
    time: String,
    payload: JsonElement?,
    peerNames: Map<Long, String>,
    added: Boolean,
): HumanEvent? {
    val obj = payload.asObject()
    val peerId = obj?.get("peer_id")?.asLong() ?: obj?.get("peerId")?.asLong()
    val label = peerId?.let { peerNames[it] ?: it.toString() } ?: "对端"
    val tunnel = obj?.get("tunnel")?.asObject()
    val proto = (tunnel?.get("tunnel_type") ?: tunnel?.get("tunnelType"))
        ?.asPlain()?.substringBefore("://")?.uppercase()
    val remoteAddr = tunnel?.get("remote_addr") ?: tunnel?.get("remoteAddr")
    val remote = remoteAddr.asUrl() ?: remoteAddr.asObject()?.get("url")?.asPlain()
    if (remote != null && publicListenerUrl(remote) == null &&
        remote.substringBefore("://").equals("ring", true)
    ) {
        return null
    }
    val verb = if (added) "建立" else "断开"
    val kind = if (added) HumanEvent.Kind.Success else HumanEvent.Kind.Info
    val protoBit = proto?.takeIf { it.isNotBlank() && it != "RING" }?.let { " $it" } ?: ""
    return HumanEvent(time, "与 $label ${verb}${protoBit}连接", remote, kind)
}

private fun eventTitleFallback(key: String): String = when (key) {
    "Unknown" -> "未知事件"
    else -> key
}

private fun eventVariant(el: JsonElement): Pair<String, JsonElement?>? = when (el) {
    is JsonPrimitive -> el.contentOrNull?.let { it to null }
    is JsonObject -> {
        val key = el.keys.firstOrNull() ?: return null
        key to el[key]
    }
    else -> null
}

private fun peerLabel(payload: JsonElement?, names: Map<Long, String>): String {
    val id = payload.asLong() ?: return payload.asPlain() ?: "未知"
    return names[id] ?: id.toString()
}

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject
private fun JsonElement?.asPlain(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> contentOrNull?.takeIf { it != "null" && it.isNotBlank() }
    is JsonArray, is JsonObject -> null
}
private fun JsonElement?.asLong(): Long? = when (this) {
    is JsonPrimitive -> longOrNull ?: contentOrNull?.toLongOrNull()
    else -> null
}
private fun JsonElement?.asUrl(): String? {
    asPlain()?.let { return it }
    return asObject()?.get("url")?.asPlain()
}
private fun JsonElement?.asList(): List<String?> = when (this) {
    is JsonArray -> map { it.asPlain() ?: it.asUrl() }
    is JsonPrimitive -> listOf(asPlain())
    is JsonObject -> emptyList()
    else -> emptyList()
}
private fun JsonElement?.asPair(): Pair<String?, String?> {
    val list = asList()
    return list.getOrNull(0) to list.getOrNull(1)
}
private fun JsonElement.compact(): String {
    val raw = toString()
    val collapsed = buildString(raw.length) {
        var space = false
        for (c in raw) {
            if (c.isWhitespace()) {
                if (!space) {
                    append(' ')
                    space = true
                }
            } else {
                space = false
                append(c)
            }
        }
    }
    return collapsed.take(160)
}

private fun String.toClockTime(): String {
    val t = indexOf('T')
    if (t < 0) return this.take(8)
    return substring(t + 1).take(8)
}

private fun String?.orDash(): String = if (isNullOrBlank()) "—" else this
