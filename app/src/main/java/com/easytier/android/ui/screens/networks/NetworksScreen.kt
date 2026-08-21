package com.easytier.android.ui.screens.networks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.AppContainer
import com.easytier.android.core.engine.InstanceState
import com.easytier.android.data.model.NetworkingMethod
import com.easytier.android.data.model.SavedNetwork
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.EmptyState
import com.easytier.android.ui.components.SectionHeader
import com.easytier.android.ui.components.ServiceHeroCard
import com.easytier.android.ui.components.rememberWithVpnPermission
import com.easytier.android.ui.components.stateAccent
import com.easytier.android.ui.icons.AppIcons
import com.easytier.android.util.Format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** 首页 ViewModel：网络列表 + 运行状态 + 服务开关状态。 */
class NetworksViewModel(val container: AppContainer) : ViewModel() {

    val networks = container.networksRepository.networks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val states = container.engine.states

    /** 服务（引擎 + TUN）开关状态。 */
    val serviceRunning = container.vpnController.serviceRunning

    /** 勾选/取消勾选网络：持久化 + 服务运行中同步引擎。 */
    fun setEnabled(network: SavedNetwork, enabled: Boolean) =
        viewModelScope.launch {
            container.networksRepository.save(
                network.copy(enabled = enabled, updatedAt = System.currentTimeMillis()),
            )
            container.vpnController.onEnabledChanged(network, enabled)
        }

    /** 启动服务（引擎 + 勾选网络）；没有勾选的网络时报错并不启动。 */
    fun startService(enabledNetworks: List<SavedNetwork>): String? =
        container.vpnController.startService(enabledNetworks).exceptionOrNull()?.message

    /** 停止服务（引擎 + TUN 全停）。 */
    fun stopService() = container.vpnController.stopService()

    fun delete(network: SavedNetwork) =
        viewModelScope.launch {
            container.vpnController.stopNetwork(network)
            container.networksRepository.delete(network.id)
        }
}

/**
 * 网络（首页）：服务级 Hero 开关（引擎 + TUN）+ 勾选式网络列表。
 * 勾选 = 服务启动时加入；右滑卡片编辑，左滑删除（露出色块 + icon）。
 */
@Composable
fun NetworksScreen(
    container: AppContainer,
    onCreateNetwork: () -> Unit,
    onEditNetwork: (String) -> Unit,
    onOpenStatus: () -> Unit,
) {
    val vm: NetworksViewModel = viewModel { NetworksViewModel(container) }
    val networks by vm.networks.collectAsState()
    val states by vm.states.collectAsState()
    val serviceRunning by vm.serviceRunning.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<SavedNetwork?>(null) }

    fun showSnack(message: String) {
        scope.launch { snackbar.showSnackbar(message) }
    }

    // VPN 关闭（仅引擎模式）时无需系统 VPN 权限
    val enableVpn by remember(container) {
        container.settingsRepository.settings.map { it.enableVpn }
    }.collectAsState(initial = true)

    // 开启服务（引擎）前先确保 VPN 已授权；授权通过后再真正启动
    val startServiceWithPermission = rememberWithVpnPermission(enabled = enableVpn) {
        vm.startService(networks.filter { it.enabled })?.let { showSnack(it) }
    }

    // ---- 服务级聚合状态 ----
    val activeCount = states.values.count { it is InstanceState.Running || it is InstanceState.Starting }
    val enabledCount = networks.count { it.enabled }
    val runningInfos = networks.mapNotNull { n ->
        (states[n.config.networkName] as? InstanceState.Running)?.let { n to it.info }
    }
    val rxTotal = runningInfos.sumOf { (_, info) ->
        info.peers.sumOf { p -> p.conns.sumOf { c -> c.stats?.rxBytesLong ?: 0L } }
    }
    val txTotal = runningInfos.sumOf { (_, info) ->
        info.peers.sumOf { p -> p.conns.sumOf { c -> c.stats?.txBytesLong ?: 0L } }
    }
    val nodeCount = runningInfos.flatMap { (_, info) -> info.peers.map { p -> p.peerId } }.distinct().size +
        (if (runningInfos.isNotEmpty()) 1 else 0) // 含本机节点，对齐官方客户端口径

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 108.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "hero") {
                ServiceHeroCard(
                    running = serviceRunning,
                    statusText = when {
                        !serviceRunning && enabledCount == 0 -> "未运行 · 未勾选网络"
                        !serviceRunning -> "未运行 · 已勾选 $enabledCount 个网络"
                        else -> "运行中 · $activeCount/${networks.size} 个网络"
                    },
                    headline = when {
                        runningInfos.isEmpty() -> null
                        runningInfos.size == 1 ->
                            runningInfos[0].second.myNodeInfo?.virtualIpv4?.toIpString()
                        else -> "${runningInfos.size} 个网络运行中"
                    },
                    stats = if (!serviceRunning || runningInfos.isEmpty()) emptyList() else buildList {
                        add("$nodeCount 个节点")
                        add("↑ ${Format.bytes(txTotal)}")
                        add("↓ ${Format.bytes(rxTotal)}")
                    },
                    onToggle = { on ->
                        if (on) {
                            startServiceWithPermission()
                        } else {
                            vm.stopService()
                        }
                    },
                    onClick = onOpenStatus,
                )
            }

            item { SectionHeader("我的网络") }
            if (networks.isEmpty()) {
                item(key = "empty") {
                    AppCard {
                        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
                            EmptyState(
                                icon = AppIcons.CloudOff,
                                title = "还没有网络",
                                hint = "点击右下角「新建网络」创建你的第一个组网",
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                }
            }
            items(networks, key = { it.id }) { network ->
                NetworkCard(
                    network = network,
                    state = states[network.config.networkName],
                    onToggleEnabled = { on -> vm.setEnabled(network, on) },
                    onEdit = { onEditNetwork(network.id) },
                    onDelete = { pendingDelete = network },
                )
            }
        }

        // 左滑删除需确认，误滑可撤销（不落库）
        pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("删除网络") },
                text = { Text("确定删除「${target.config.networkName}」吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDelete = null
                        vm.delete(target)
                        showSnack("已删除 ${target.config.networkName}")
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                },
            )
        }

        // 真悬浮 FAB：叠在列表上方，不再占据底部一整行
        ExtendedFloatingActionButton(
            onClick = onCreateNetwork,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("新建网络") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )

        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** 左滑操作阈值：超过则触发删除。 */
private val SWIPE_ACTION_THRESHOLD = 96.dp

/**
 * 网络卡片：Checkbox 勾选（服务启动时加入）+ 状态色图标 + 名称。
 * 右滑露出编辑 icon 并进入编辑页；左滑露出删除 icon 并删除。
 */
@Composable
private fun NetworkCard(
    network: SavedNetwork,
    state: InstanceState?,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val running = state is InstanceState.Running || state is InstanceState.Starting
    val accent = stateAccent(state)
    var offsetX by remember(network.id) { mutableFloatStateOf(0f) }
    val thresholdPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        SWIPE_ACTION_THRESHOLD.toPx()
    }
    val (subtitle, subtitleColor) = when (state) {
        is InstanceState.Running -> (
            state.info.myNodeInfo?.virtualIpv4?.toIpString()?.let { "运行中 · $it" } ?: "运行中"
            ) to accent
        is InstanceState.Starting -> "正在启动…" to accent
        is InstanceState.Error -> state.message to accent
        null, InstanceState.Stopped -> network.config.networkingMethod.label() to
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(Modifier.fillMaxWidth()) {
        // 底层操作区：左滑露出右侧整块红色删除区，右滑露出左侧编辑区
        Row(Modifier.matchParentSize()) {
            // 右滑编辑：左侧蓝底
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Edit,
                    "右滑编辑",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            // 左滑删除：右侧红底
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Delete,
                    "左滑删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        AppCard(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(network.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                offsetX <= -thresholdPx -> {
                                    offsetX = 0f
                                    onDelete()
                                }
                                offsetX >= thresholdPx -> {
                                    offsetX = 0f
                                    onEdit()
                                }
                                else -> offsetX = 0f // 未达阈值回弹
                            }
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount).coerceIn(
                            -thresholdPx * 1.6f,
                            thresholdPx * 1.6f,
                        )
                    }
                },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onEdit() }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = network.enabled,
                    onCheckedChange = onToggleEnabled,
                )
                Icon(
                    AppIcons.Work,
                    null,
                    tint = accent,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(40.dp)
                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
                        .padding(10.dp),
                )
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(
                        network.config.networkName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // 右缘滑动提示：双向箭头
                Icon(
                    AppIcons.ExpandMore,
                    null,
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(18.dp)
                        .rotate(-90f),
                )
            }
        }
    }
}

/** 未运行时副标题展示联网方式。 */
private fun NetworkingMethod.label(): String = when (this) {
    NetworkingMethod.PublicServer -> "公共服务器"
    NetworkingMethod.Manual -> "手动连接"
    NetworkingMethod.Standalone -> "单机网络"
}
