package com.easytier.android.ui.screens.networks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.EmptyState
import com.easytier.android.ui.components.SectionHeader
import com.easytier.android.ui.components.ScreenHeader
import com.easytier.android.ui.components.VpnHeroCard
import com.easytier.android.ui.components.rememberWithVpnPermission
import com.easytier.android.ui.components.stateAccent
import com.easytier.android.ui.icons.AppIcons
import com.easytier.android.util.Format
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页 ViewModel：网络列表 + 运行状态。 */
class NetworksViewModel(val container: AppContainer) : ViewModel() {

    val networks = container.networksRepository.networks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val states = container.engine.states

    fun start(network: SavedNetwork) =
        viewModelScope.launch {
            container.vpnController.startNetwork(network, withVpn = true)
        }

    fun stop(network: SavedNetwork) =
        viewModelScope.launch {
            container.vpnController.stopNetwork(network)
        }

    fun delete(network: SavedNetwork) =
        viewModelScope.launch {
            container.networksRepository.delete(network.id)
        }
}

/** 网络（首页）：运行中网络的 Hero 速览 + 全部网络列表。 */
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

    // VPN 授权后再启动；待启动网络只存 id（授权框遮挡期间 Activity 重建后对象引用会失效）
    var pendingStartId by rememberSaveable { mutableStateOf<String?>(null) }
    val networksState = networks
    val startWithVpnPermission = rememberWithVpnPermission {
        pendingStartId?.let { id ->
            pendingStartId = null
            networksState.firstOrNull { it.id == id }?.let(vm::start)
        }
    }

    val runningNetwork = networks.firstOrNull {
        states[it.config.networkName] is InstanceState.Running
    }
    val runningState = runningNetwork?.let { states[it.config.networkName] as? InstanceState.Running }
    val runningCount = networks.count {
        states[it.config.networkName] is InstanceState.Running || states[it.config.networkName] is InstanceState.Starting
    }
    val subtitle = when {
        networksState.isEmpty() -> "创建你的第一个组网"
        runningCount > 0 -> "${networksState.size} 个网络 · $runningCount 个运行中"
        else -> "${networksState.size} 个网络"
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("网络", subtitle = subtitle)

        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (networksState.isEmpty()) {
                EmptyState(
                    icon = AppIcons.CloudOff,
                    title = "还没有网络",
                    hint = "点击右下角「新建网络」创建你的第一个组网",
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 108.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    runningNetwork?.let { network ->
                        val info = runningState?.info
                        item(key = "hero") {
                            val rx = info?.peers?.sumOf { p -> p.conns.sumOf { c -> c.stats?.rxBytesLong ?: 0 } } ?: 0
                            val tx = info?.peers?.sumOf { p -> p.conns.sumOf { c -> c.stats?.txBytesLong ?: 0 } } ?: 0
                            VpnHeroCard(
                                networkName = network.config.networkName,
                                virtualIp = info?.myNodeInfo?.virtualIpv4?.toIpString(),
                                nodeCount = ((info?.peers?.size ?: 0) + 1), // 含本机节点，对齐官方客户端口径
                                rxTotalText = Format.bytes(rx),
                                txTotalText = Format.bytes(tx),
                                onToggle = { on -> if (!on) vm.stop(network) },
                                onClick = onOpenStatus,
                            )
                        }
                    }

                    item { SectionHeader("我的网络") }
                    items(networks, key = { it.id }) { network ->
                        NetworkCard(
                            network = network,
                            state = states[network.config.networkName],
                            onToggle = { on ->
                                if (on) {
                                    pendingStartId = network.id
                                    startWithVpnPermission()
                                } else {
                                    vm.stop(network)
                                }
                            },
                            onEdit = { onEditNetwork(network.id) },
                            onDelete = { vm.delete(network) },
                        )
                    }
                }
            }

            // 真悬浮 FAB：叠在列表上方，不再占据底部一整行
            ExtendedFloatingActionButton(
                onClick = onCreateNetwork,
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("新建网络") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
}

/** 网络卡片：左侧状态色图标 + 名称/副标题 + 开关 + 菜单。 */
@Composable
private fun NetworkCard(
    network: SavedNetwork,
    state: InstanceState?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val running = state is InstanceState.Running || state is InstanceState.Starting
    val accent = stateAccent(state)
    val (subtitle, subtitleColor) = when (state) {
        is InstanceState.Running -> (
            state.info.myNodeInfo?.virtualIpv4?.toIpString()?.let { "运行中 · $it" } ?: "运行中"
            ) to accent
        is InstanceState.Starting -> "正在启动…" to accent
        is InstanceState.Error -> state.message to accent
        null, InstanceState.Stopped -> network.config.networkingMethod.label() to
            MaterialTheme.colorScheme.onSurfaceVariant
    }

    AppCard {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 状态色图标底座：颜色即状态，不再单独放一个状态图标
            Icon(
                AppIcons.Work,
                null,
                tint = accent,
                modifier = Modifier
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
            Switch(checked = running, onCheckedChange = onToggle)
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, "菜单")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("编辑") },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
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
