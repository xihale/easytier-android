package com.easytier.android.ui.screens.status

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.AppContainer
import com.easytier.android.core.engine.InstanceState
import com.easytier.android.data.model.HumanEvent
import com.easytier.android.data.model.NetworkInstanceRunningInfo
import com.easytier.android.data.model.PeerRoutePair
import com.easytier.android.data.model.parseHumanEvents
import com.easytier.android.data.model.peerRoutePairs
import com.easytier.android.data.model.publicListeners
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.EmptyState
import com.easytier.android.ui.components.InfoRow
import com.easytier.android.ui.components.PillBadge
import com.easytier.android.ui.components.RateChart
import com.easytier.android.ui.components.RateRow
import com.easytier.android.ui.components.SectionHeader
import com.easytier.android.ui.components.StatusDot
import com.easytier.android.ui.icons.AppIcons
import com.easytier.android.ui.theme.LocalStatusColors
import com.easytier.android.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 状态页 ViewModel：汇总全部运行中实例的流量。 */
class StatusViewModel(val container: AppContainer) : ViewModel() {

    val states = container.engine.states

    private val _rxRateHistory = MutableStateFlow<List<Long>>(emptyList())
    val rxRateHistory = _rxRateHistory.asStateFlow()

    private val _txRateHistory = MutableStateFlow<List<Long>>(emptyList())
    val txRateHistory = _txRateHistory.asStateFlow()

    private var lastRxTotal: Long = -1
    private var lastTxTotal: Long = -1

    /** 由 UI 周期调用：所有运行中网络的流量合计。 */
    fun tick() {
        val running = states.value.values.mapNotNull { it as? InstanceState.Running }
        if (running.isEmpty()) {
            lastRxTotal = -1
            lastTxTotal = -1
            return
        }
        val rx = running.sumOf { it.info.peers.sumOf { p -> p.conns.sumOf { c -> c.stats?.rxBytesLong ?: 0 } } }
        val tx = running.sumOf { it.info.peers.sumOf { p -> p.conns.sumOf { c -> c.stats?.txBytesLong ?: 0 } } }

        val rxRate = if (lastRxTotal in 0 until rx) (rx - lastRxTotal) / TICK_SEC else 0
        val txRate = if (lastTxTotal in 0 until tx) (tx - lastTxTotal) / TICK_SEC else 0
        lastRxTotal = rx
        lastTxTotal = tx

        _rxRateHistory.value = (_rxRateHistory.value + rxRate).takeLast(HISTORY_LEN)
        _txRateHistory.value = (_txRateHistory.value + txRate).takeLast(HISTORY_LEN)
    }

    companion object {
        private const val HISTORY_LEN = 60
        private const val TICK_SEC = 2L
    }
}

private data class TaggedPeer(
    val network: String,
    val pair: PeerRoutePair,
)

/** 状态页：汇总全部运行中网络的本机信息 / 流量 / 对等节点 / 事件。 */
@Composable
fun StatusScreen(
    container: AppContainer,
    initialNetworkName: String?,
) {
    val vm: StatusViewModel = viewModel { StatusViewModel(container) }
    val states by vm.states.collectAsState()
    val rxHistory by vm.rxRateHistory.collectAsState()
    val txHistory by vm.txRateHistory.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            vm.tick()
        }
    }

    val running = remember(states) {
        states.entries
            .mapNotNull { (name, state) -> (state as? InstanceState.Running)?.let { name to it.info } }
            .sortedBy { it.first }
    }
    val multi = running.size > 1
    val taggedPeers = remember(running) {
        running.flatMap { (name, info) ->
            info.peerRoutePairs().map { TaggedPeer(name, it) }
        }
    }
    val events = remember(running) {
        running.flatMap { (name, info) ->
            val names = info.peerRoutePairs().associate { p ->
                p.route.peerId to p.route.hostname.ifBlank { p.route.peerId.toString() }
            }
            parseHumanEvents(info.events, names, name)
        }.sortedByDescending { it.sortTime.ifBlank { it.time } }
    }
    val rxTotal = running.sumOf { (_, info) ->
        info.peers.sumOf { p -> p.conns.sumOf { c -> c.stats?.rxBytesLong ?: 0 } }
    }
    val txTotal = running.sumOf { (_, info) ->
        info.peers.sumOf { p -> p.conns.sumOf { c -> c.stats?.txBytesLong ?: 0 } }
    }

    if (running.isEmpty()) {
        val starting = states.values.any { it is InstanceState.Starting }
        val error = states.values.filterIsInstance<InstanceState.Error>().firstOrNull()
        Column(Modifier.fillMaxSize()) {
            EmptyState(
                icon = AppIcons.CloudOff,
                title = when {
                    error != null -> "实例出错"
                    starting -> "正在启动…"
                    else -> "没有运行中的实例"
                },
                hint = error?.message ?: "回到「网络」页打开一个网络的开关",
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 96.dp),
            )
        }
    } else {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
                item { NodeInfoCard(running) }
                item {
                    TrafficCard(
                        rxRate = rxHistory.lastOrNull() ?: 0,
                        txRate = txHistory.lastOrNull() ?: 0,
                        rxHistory = rxHistory,
                        txHistory = txHistory,
                        rxTotal = rxTotal,
                        txTotal = txTotal,
                    )
                }

                item { SectionHeader("对等节点 · ${taggedPeers.size}") }
                if (taggedPeers.isEmpty()) {
                    item {
                        AppCard {
                            Text(
                                "暂无对等节点，等待其他节点加入…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                } else {
                    itemsIndexed(
                        taggedPeers,
                        key = { i, p -> "${p.network}-${p.pair.route.peerId}-${p.pair.defaultConn?.connId ?: ""}-$i" },
                    ) { _, tagged ->
                        PeerCard(tagged.pair, networkName = if (multi) tagged.network else null)
                    }
                }

                item { SectionHeader("事件 · ${events.size}") }
                item { EventsCard(events, showNetwork = multi) }
            }
    }
}

@Composable
private fun ListenerChip(url: String) {
    val scheme = url.substringBefore("://", missingDelimiterValue = url).uppercase()
    val rest = url.substringAfter("://", missingDelimiterValue = "")
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(
            if (rest.isBlank()) scheme else "$scheme  $rest",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** 本机节点信息卡：多个运行中网络并列展示。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeInfoCard(running: List<Pair<String, NetworkInstanceRunningInfo>>) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200), label = "chevron")
    val single = running.singleOrNull()?.second

    AppCard(Modifier.animateContentSize(animationSpec = tween(200))) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "本机节点",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    Modifier.clickable { expanded = !expanded }.padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (expanded) "收起" else "详情",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        AppIcons.ExpandMore,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 2.dp).size(16.dp).rotate(chevronRotation),
                    )
                }
            }

            if (single != null) {
                val ip = single.myNodeInfo?.virtualIpv4?.toIpString()
                if (ip != null) {
                    Text(
                        ip,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Column(Modifier.padding(top = 8.dp)) {
                    InfoRow("主机名", single.myNodeInfo?.hostname ?: "--")
                    InfoRow("Peer ID", single.myNodeInfo?.peerId.toString())
                    if (expanded) NodeDetails(single)
                }
            } else {
                Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    running.forEach { (name, info) ->
                        val ip = info.myNodeInfo?.virtualIpv4?.toIpString() ?: "--"
                        InfoRow(name, ip)
                        if (expanded) {
                            Column(Modifier.padding(start = 4.dp, bottom = 8.dp)) {
                                InfoRow("主机名", info.myNodeInfo?.hostname ?: "--")
                                InfoRow("Peer ID", info.myNodeInfo?.peerId.toString())
                                NodeDetails(info)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeDetails(info: NetworkInstanceRunningInfo) {
    info.myNodeInfo?.stunInfo?.let { stun ->
        InfoRow("NAT 类型", stun.udpNatType.label)
        InfoRow("公网 IP", stun.publicIp.filter { it.isNotBlank() }.joinToString("\n").ifBlank { "--" })
    }
    InfoRow("版本", info.myNodeInfo?.version ?: "--")
    val listeners = info.myNodeInfo?.publicListeners().orEmpty()
    Text(
        "监听",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    if (listeners.isEmpty()) {
        Text(
            "无对外监听",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listeners.forEach { url -> ListenerChip(url) }
        }
    }
}

/** 流量卡：速率曲线 + 实时速率 + 累计流量。 */
@Composable
private fun TrafficCard(
    rxRate: Long,
    txRate: Long,
    rxHistory: List<Long>,
    txHistory: List<Long>,
    rxTotal: Long,
    txTotal: Long,
) {

    AppCard {
        Column(Modifier.padding(16.dp)) {
            Text("流量", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            RateChart(rxHistory = rxHistory, txHistory = txHistory)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                RateRow(AppIcons.ArrowDownward, Format.bps(rxRate), tint = MaterialTheme.colorScheme.primary)
                RateRow(AppIcons.ArrowUpward, Format.bps(txRate), tint = MaterialTheme.colorScheme.tertiary)
                Text(
                    "累计 ↓${Format.bytes(rxTotal)} ↑${Format.bytes(txTotal)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 对等节点卡：名称 + 延迟，IP + 路由代价（官方 cost==1 p2p / relay(n)）。 */
@Composable
private fun PeerCard(pair: PeerRoutePair, networkName: String? = null) {
    val statusColors = LocalStatusColors.current
    val route = pair.route
    val conn = pair.defaultConn
    val latency = conn?.stats?.latencyMs ?: if (route.pathLatency > 0) route.pathLatency else -1
    val loss = (conn?.lossRate ?: 0f) * 100
    val isDirect = route.isDirect
    val hostname = route.hostname.takeIf { it.isNotBlank() } ?: "peer-${route.peerId}"
    val ip = route.ipv4Addr?.toIpString() ?: "--"
    val proto = conn?.tunnel?.protoLabel
    val costLabel = when {
        route.isPublicServer -> "公共服务器"
        isDirect && proto != null -> "P2P · $proto"
        isDirect -> "P2P"
        else -> "中转(${route.cost})"
    }

    // 延迟分级：<0 未知（纯中转无直连隧道），<80 良好，<200 一般，其余较差
    val (latencyText, latencyContainer, latencyLabel) = when {
        latency < 0 -> Triple(
            if (isDirect) "测量中" else "${route.pathLatency} ms",
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        latency < 80 -> Triple("${latency} ms", statusColors.successContainer, statusColors.onSuccessContainer)
        latency < 200 -> Triple("${latency} ms", statusColors.warningContainer, statusColors.onWarningContainer)
        else -> Triple("${latency} ms", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }

    AppCard {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    StatusDot(
                        when {
                            route.isPublicServer -> MaterialTheme.colorScheme.primary
                            isDirect -> statusColors.success
                            else -> statusColors.warning
                        },
                    )
                    Text(
                        hostname,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
                    )
                    if (networkName != null) {
                        PillBadge(
                            text = networkName,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                PillBadge(latencyText, latencyContainer, latencyLabel)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    ip,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PillBadge(
                    text = costLabel,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "↑ ${Format.bytes(conn?.stats?.txBytesLong ?: 0)}   ↓ ${Format.bytes(conn?.stats?.rxBytesLong ?: 0)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (loss > 0.5f) "丢包 %.1f%%".format(loss) else "丢包 0%",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (loss > 5f) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 事件卡：时间轴 + 人性化文案（核心 JSON 已在 parseHumanEvents 转成中文）。 */
@Composable
private fun EventsCard(events: List<HumanEvent>, showNetwork: Boolean = false) {
    val statusColors = LocalStatusColors.current
    AppCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (events.isEmpty()) {
                Text(
                    "暂无事件",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                events.forEachIndexed { i, event ->
                    val dot = when (event.kind) {
                        HumanEvent.Kind.Success -> statusColors.success
                        HumanEvent.Kind.Warning -> statusColors.warning
                        HumanEvent.Kind.Error -> MaterialTheme.colorScheme.error
                        HumanEvent.Kind.Info -> MaterialTheme.colorScheme.outline
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(16.dp),
                        ) {
                            Box(
                                Modifier.size(8.dp).background(dot, CircleShape),
                            )
                            if (i != events.lastIndex) {
                                Box(
                                    Modifier
                                        .padding(top = 4.dp)
                                        .width(1.dp)
                                        .height(36.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                )
                            }
                        }
                        Column(Modifier.padding(start = 12.dp, bottom = 14.dp).weight(1f)) {
                            Text(
                                event.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            val subtitle = listOfNotNull(
                                event.time.takeIf { it.isNotBlank() },
                                event.network?.takeIf { showNetwork && it.isNotBlank() },
                                event.detail?.takeIf { it.isNotBlank() },
                            ).joinToString("  ·  ")
                            if (subtitle.isNotBlank()) {
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
