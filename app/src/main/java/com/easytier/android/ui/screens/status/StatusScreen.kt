package com.easytier.android.ui.screens.status

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.AppContainer
import com.easytier.android.core.engine.InstanceState
import com.easytier.android.data.model.NetworkInstanceRunningInfo
import com.easytier.android.data.model.LatencyTier
import com.easytier.android.data.model.PeerRoutePair
import com.easytier.android.data.model.peerLatencyBadge
import com.easytier.android.data.model.peerRoutePairs

import com.easytier.android.data.model.publicIps
import com.easytier.android.data.model.publicListeners
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.EmptyState
import com.easytier.android.ui.components.InfoRow
import com.easytier.android.ui.components.PillBadge
import com.easytier.android.ui.components.RateChart
import com.easytier.android.ui.components.SectionHeader
import com.easytier.android.ui.components.StatusDot
import com.easytier.android.ui.icons.AppIcons
import com.easytier.android.ui.theme.LocalStatusColors
import com.easytier.android.util.Format
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

/** 状态页：汇总全部运行中网络的本机信息 / 流量 / 对等节点。 */
@Composable
fun StatusScreen(
    container: AppContainer,
) {
    val vm: StatusViewModel = viewModel { StatusViewModel(container) }
    val states by vm.states.collectAsState()
    val rxHistory by vm.rxRateHistory.collectAsState()
    val txHistory by vm.txRateHistory.collectAsState()

    // IP 点击复制反馈：剪贴板写入 + 底部 Snackbar「已复制」（LocalClipboardManager + AnnotatedString）
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val copyIpToClipboard: (String) -> Unit = { ip ->
        clipboard.setText(AnnotatedString(ip))
        scope.launch { snackbarHostState.showSnackbar("已复制") }
    }

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
    val rxTotal = running.sumOf { (_, info) ->
        info.peers.sumOf { p -> p.conns.sumOf { c -> c.stats?.rxBytesLong ?: 0 } }
    }
    val txTotal = running.sumOf { (_, info) ->
        info.peers.sumOf { p -> p.conns.sumOf { c -> c.stats?.txBytesLong ?: 0 } }
    }

    // 外层 Box 承载内容与全局 SnackbarHost；空状态与列表两个分支都包在 Box 内
    Box(Modifier.fillMaxSize()) {
        if (running.isEmpty()) {
            val starting = states.values.any { it is InstanceState.Starting }
            val error = states.values.filterIsInstance<InstanceState.Error>().firstOrNull()
            // 居中空状态：Box 垂直水平居中，避免写死 top 偏移
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = AppIcons.CloudOff,
                    title = when {
                        error != null -> "实例出错"
                        starting -> "正在启动…"
                        else -> "没有运行中的实例"
                    },
                    hint = error?.message ?: "回到「网络」页打开一个网络的开关",
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { NodeInfoCard(running, onCopyIp = copyIpToClipboard) }
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
                    items(
                        taggedPeers,
                        // peerId 在同一 network 内唯一；不要掺入位置 index，否则 key 随重排变化失去稳定意义
                        key = { p -> "${p.network}-${p.pair.route.peerId}-${p.pair.defaultConn?.connId ?: ""}" },
                        contentType = { "peer" },
                    ) { tagged ->
                        Box(Modifier.animateItem()) {
                            PeerCard(tagged.pair, networkName = if (multi) tagged.network else null)
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 监听胶囊：按端口合并协议，v4/v6 不重复（如 UDP+TCP 11010 / WG 11011）。 */
@Composable
private fun ListenerChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/**
 * 监听 URL → 按端口分组的标签。
 * tcp/0.0.0.0:11010 与 tcp://[::]:11010 是同一个端口，只显示一次。
 * 协议顺序固定（TCP/UDP/WG/WS/WSS/QUIC），端口相同则合并为 "UDP+TCP 11010"。
 */
private fun listenerLabels(urls: List<String>): List<String> {
    data class Entry(val protos: MutableSet<String>, val port: Int?)
    val byPort = LinkedHashMap<Int?, Entry>()
    urls.forEach { url ->
        val scheme = url.substringBefore("://", missingDelimiterValue = "").uppercase()
        if (scheme.isBlank()) return@forEach
        val port = url.substringAfterLast(':').trimEnd('/').toIntOrNull()
        val entry = byPort.getOrPut(port) { Entry(mutableSetOf(), port) }
        entry.protos.add(scheme)
    }
    return byPort.values
        .sortedBy { it.port ?: Int.MAX_VALUE }
        .map { e ->
            val protos = e.protos.toList().sortedBy { PROTO_ORDER.indexOf(it) }
            val port = e.port?.toString() ?: "--"
            "${protos.joinToString("+")} $port"
        }
}

private val PROTO_ORDER = listOf("TCP", "UDP", "WG", "WS", "WSS", "QUIC", "TLS")

/** 本机节点信息卡：多个运行中网络并列展示。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeInfoCard(
    running: List<Pair<String, NetworkInstanceRunningInfo>>,
    onCopyIp: (String) -> Unit,
) {
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
                        modifier = Modifier
                            .padding(top = 8.dp)
                            // 点击 IP 复制到剪贴板；clip 让涟漪不超出文本区域
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClickLabel = "复制 IP 地址") { onCopyIp(ip) },
                    )
                }
                Column(Modifier.padding(top = 8.dp)) {
                    InfoRow("主机名", single.myNodeInfo?.hostname ?: "--")
                    // myNodeInfo 为 null 时 toString() 会显示字面量 "null"，改为占位符
                    InfoRow("Peer ID", single.myNodeInfo?.peerId?.takeIf { it != 0L }?.toString() ?: "--")
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
                                InfoRow("Peer ID", info.myNodeInfo?.peerId?.takeIf { it != 0L }?.toString() ?: "--")
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
        stun.publicIps().takeIf { it.isNotEmpty() }?.let { ips ->
            InfoRow("公网 IP", ips.joinToString("\n"))
        }
    }
    InfoRow("版本", info.myNodeInfo?.version ?: "--")
    val listeners = info.myNodeInfo?.publicListeners().orEmpty()
    // 监听行：与 InfoRow 相同的「标签左 / 内容右」结构；FlowRow 负责换行，
    // 横向 spacedBy 是关键——否则同一行的胶囊会直接贴在一起
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.Top) {
        Text(
            "监听",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        if (listeners.isEmpty()) {
            Text(
                "无对外监听",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        } else {
            FlowRow(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listenerLabels(listeners).forEach { label -> ListenerChip(label) }
            }
        }
    }
}

/** 流量卡：速率曲线 + 下/上行两列（实时速率与累计流量，颜色对应曲线）。 */
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
            Row(Modifier.fillMaxWidth()) {
                TrafficStat(
                    icon = AppIcons.ArrowDownward,
                    label = "下行",
                    rate = Format.bps(rxRate),
                    total = Format.bytes(rxTotal),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                TrafficStat(
                    icon = AppIcons.ArrowUpward,
                    label = "上行",
                    rate = Format.bps(txRate),
                    total = Format.bytes(txTotal),
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 单方向速率块：方向标题 + 实时速率（等宽加粗）+ 累计流量。 */
@Composable
private fun TrafficStat(
    icon: ImageVector,
    label: String,
    rate: String,
    total: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            rate,
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            "累计 $total",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** 对等节点卡：名称 + 延迟，IP + 路由代价（官方 cost==1 p2p / relay(n)）。 */
@Composable
private fun PeerCard(pair: PeerRoutePair, networkName: String? = null) {
    val statusColors = LocalStatusColors.current
    val route = pair.route
    val conn = pair.defaultConn
    val connLatencyRaw = conn?.stats?.latencyMs
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

    // 延迟分级：连接中/未知用中性色；<80 良好，<200 一般，其余较差（规则见 peerLatencyBadge）
    val badge = peerLatencyBadge(connLatencyRaw, route.pathLatency)
    val (latencyText, latencyContainer, latencyLabel) = when (badge.tier) {
        LatencyTier.NEUTRAL -> Triple(
            badge.text,
            MaterialTheme.colorScheme.surfaceContainerHigh,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LatencyTier.GOOD -> Triple(badge.text, statusColors.successContainer, statusColors.onSuccessContainer)
        LatencyTier.FAIR -> Triple(badge.text, statusColors.warningContainer, statusColors.onWarningContainer)
        LatencyTier.BAD -> Triple(badge.text, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
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
                        // 直接连接呼吸光晕，一眼区分 P2P 链路
                        pulse = isDirect,
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
                Modifier.fillMaxWidth().padding(top = 6.dp),
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
                Modifier.fillMaxWidth().padding(top = 6.dp),
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

