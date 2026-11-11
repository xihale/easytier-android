package com.easytier.android.ui.screens.networks

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.easytier.android.R
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.AppContainer
import com.easytier.android.core.engine.InstanceState
import com.easytier.android.data.model.NetworkingMethod
import com.easytier.android.data.model.SavedNetwork
import com.easytier.android.data.store.NetworksRepository
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.AppSnackbarHost
import com.easytier.android.ui.components.EmptyState
import com.easytier.android.ui.components.SectionHeader
import com.easytier.android.ui.components.ServiceHeroCard
import com.easytier.android.ui.components.TomlImportDialog
import com.easytier.android.ui.components.rememberHaptics
import com.easytier.android.ui.components.rememberWithVpnPermission
import com.easytier.android.ui.components.stateAccent
import com.easytier.android.ui.icons.AppIcons
import com.easytier.android.util.Format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    val fabExpanded by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40
        }
    }
    var pendingDelete by remember { mutableStateOf<SavedNetwork?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

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
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "hero") {
                ServiceHeroCard(
                    running = serviceRunning,
                    statusText = when {
                        !serviceRunning && enabledCount == 0 ->
                            stringResource(R.string.networks_hero_not_running_no_selection)
                        !serviceRunning ->
                            stringResource(R.string.networks_hero_not_running, enabledCount)
                        else ->
                            stringResource(R.string.networks_hero_running, activeCount, networks.size)
                    },
                    headline = when {
                        runningInfos.isEmpty() -> null
                        runningInfos.size == 1 ->
                            runningInfos[0].second.myNodeInfo?.virtualIpv4?.toIpString()
                        else -> stringResource(R.string.networks_hero_multi_running, runningInfos.size)
                    },
                    // 箭头方向由 Hero 卡内图标表达，字符串不再带 ↑/↓ 前缀避免双重箭头
                    stats = if (!serviceRunning || runningInfos.isEmpty()) emptyList() else buildList {
                        add(stringResource(R.string.networks_hero_nodes, nodeCount))
                        add(Format.bytes(txTotal))
                        add(Format.bytes(rxTotal))
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

            item {
                SectionHeader(stringResource(R.string.networks_mine)) {
                    TextButton(onClick = {
                        showImportDialog = true
                    }) {
                        Icon(AppIcons.Upload, null, Modifier.size(16.dp))
                        Text(stringResource(R.string.common_import), Modifier.padding(start = 4.dp))
                    }
                }
            }
            if (networks.isEmpty()) {
                item(key = "empty") {
                    // 空状态直接裸排在页面上（不再包 AppCard），视觉更轻盈
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        EmptyState(
                            icon = AppIcons.CloudOff,
                            title = stringResource(R.string.networks_empty_title),
                            hint = stringResource(R.string.networks_empty_hint),
                        )
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = {
                            showImportDialog = true
                        }) {
                            Icon(AppIcons.Upload, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.networks_import_toml), Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
            items(networks, key = { it.id }, contentType = { "network" }) { network ->
                // animateItem：增删/重排平滑过渡，而非瞬间跳位
                Box(Modifier.animateItem()) {
                    NetworkCard(
                        network = network,
                        state = states[network.config.networkName],
                        onToggleEnabled = { on -> vm.setEnabled(network, on) },
                        onEdit = { onEditNetwork(network.id) },
                        onDelete = { pendingDelete = network },
                    )
                }
            }
        }

        // 左滑删除需确认，误滑可撤销（不落库）
        pendingDelete?.let { target ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.networks_delete_title)) },
                text = { Text(stringResource(R.string.networks_delete_message, target.config.networkName)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingDelete = null
                        vm.delete(target)
                        showSnack(context.getString(R.string.networks_deleted, target.config.networkName))
                    }) {
                        Text(
                            stringResource(R.string.common_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.common_cancel)) }
                },
            )
        }

        // 真悬浮 FAB：叠在列表上方，下滑自动收起文案为圆形图标，回顶展开
        ExtendedFloatingActionButton(
            onClick = {
                onCreateNetwork()
            },
            expanded = fabExpanded,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text(stringResource(R.string.networks_new)) },
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )

        // 首页直接导入为新建网络；同名网络会被引擎视为同一实例，拒绝重复导入
        if (showImportDialog) {
            TomlImportDialog(
                initialText = "",
                onDismiss = { showImportDialog = false },
                onImported = { cfg ->
                    showImportDialog = false
                    if (networks.any { it.config.networkName == cfg.networkName }) {
                        haptics.reject()
                        showSnack(context.getString(R.string.networks_import_dup, cfg.networkName))
                    } else {
                        scope.launch {
                            container.networksRepository.save(NetworksRepository.newNetwork(cfg))
                            showSnack(context.getString(R.string.networks_imported, cfg.networkName))
                        }
                    }
                },
            )
        }

        AppSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/**
 * 网络卡片：自绘 RoundCheckbox 勾选（服务启动时加入）+ 状态色图标 + 名称。
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
    val rawAccent = stateAccent(state)
    val accent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(durationMillis = 250),
        label = "networkCardAccent",
    )
    val haptics = rememberHaptics()
    // semantics/customActions 与图标 contentDescription 处非 Composable 实参位置的闭包需提前解析
    val editAction = stringResource(R.string.networks_a11y_edit)
    val deleteAction = stringResource(R.string.networks_a11y_delete)
    val swipeEditLabel = stringResource(R.string.networks_a11y_swipe_edit)
    val swipeDeleteLabel = stringResource(R.string.networks_a11y_swipe_delete)
    val (subtitle, rawSubtitleColor) = when (state) {
        is InstanceState.Running -> (
            state.info.myNodeInfo?.virtualIpv4?.toIpString()
                ?.let { stringResource(R.string.networks_state_running_ip, it) }
                ?: stringResource(R.string.networks_state_running)
            ) to rawAccent
        is InstanceState.Starting -> stringResource(R.string.state_starting) to rawAccent
        is InstanceState.Error -> state.message to rawAccent
        null, InstanceState.Stopped -> stringResource(network.config.networkingMethod.labelRes) to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    val subtitleColor by animateColorAsState(
        targetValue = rawSubtitleColor,
        animationSpec = tween(durationMillis = 250),
        label = "networkCardSubtitleColor",
    )

    // 确认阈值即触发动作；返回 false 表示不停留在 dismissed，松手自动回弹（带动画/支持 fling）
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> { onEdit(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); false }
                else -> false
            }
        },
    )
    // 越过阈值（targetValue 离开 Settled）时轻震，提示动作已就绪
    LaunchedEffect(dismissState) {
        snapshotFlow { dismissState.targetValue }
            .filter { it != SwipeToDismissBoxValue.Settled }
            .collect { haptics.heavy() }
    }

    Box(
        Modifier
            .fillMaxWidth()
            // TalkBack 用户无法滑动，提供自定义操作直达编辑/删除
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(editAction) { onEdit(); true },
                    CustomAccessibilityAction(deleteAction) { onDelete(); true },
                )
            },
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val armed = dismissState.targetValue != SwipeToDismissBoxValue.Settled
                val iconScale by animateFloatAsState(
                    targetValue = if (armed) 1.15f else 0.88f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                    label = "swipeIconScale",
                )
                val bgColor = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(bgColor, MaterialTheme.shapes.large)
                        .padding(horizontal = 22.dp),
                    contentAlignment = when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                        SwipeToDismissBoxValue.Settled -> Alignment.Center
                    },
                ) {
                    when (direction) {
                        SwipeToDismissBoxValue.StartToEnd -> Icon(
                            Icons.Filled.Edit,
                            swipeEditLabel,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.scale(iconScale),
                        )
                        SwipeToDismissBoxValue.EndToStart -> Icon(
                            Icons.Filled.Delete,
                            swipeDeleteLabel,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.scale(iconScale),
                        )
                        SwipeToDismissBoxValue.Settled -> Unit
                    }
                }
            },
        ) {
            AppCard {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onEdit()
                        }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundCheckbox(
                        checked = network.enabled,
                        onCheckedChange = onToggleEnabled,
                    )
                    Icon(
                        AppIcons.Work,
                        null,
                        tint = accent,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(42.dp)
                            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(13.dp))
                            .padding(11.dp),
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
                }
            }
        }
    }
}

/**
 * 自绘圆形勾选框（替代 M3 Checkbox）：22dp 圆。
 * 未选：2dp outline 描边透明底；选中：primary 实心 + 白色 14dp 对勾，颜色与缩放弹性过渡。
 * 外层保留最小交互尺寸与 Checkbox 语义角色，触控热区与 TalkBack 行为不变。
 */
@Composable
private fun RoundCheckbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val fillColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "roundCheckboxFill",
    )
    val strokeColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        animationSpec = tween(durationMillis = 150),
        label = "roundCheckboxStroke",
    )
    // 新版 foundation 已移除 minimumInteractiveComponentSize：显式 48dp 触控热区（与原 M3 Checkbox 一致）
    Box(
        modifier
            .size(48.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        role = Role.Checkbox,
                        onValueChange = {
                            haptics.toggle(it)
                            onCheckedChange(it)
                        },
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .background(fillColor, CircleShape)
                .border(2.dp, strokeColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedVisibility(
                visible = checked,
                enter = scaleIn(
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                ) + fadeIn(tween(120)),
                exit = scaleOut(tween(110)) + fadeOut(tween(90)),
            ) {
                Icon(
                    Icons.Filled.Check,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

