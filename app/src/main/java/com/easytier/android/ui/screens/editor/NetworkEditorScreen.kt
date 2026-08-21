package com.easytier.android.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.AppContainer
import com.easytier.android.core.toml.TomlGenerator
import com.easytier.android.core.toml.TomlImporter
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.NetworkingMethod
import com.easytier.android.data.model.PortForwardEntry
import com.easytier.android.data.model.SavedNetwork
import com.easytier.android.ui.components.SectionHeader
import com.easytier.android.ui.components.StringListEditor
import com.easytier.android.ui.components.SwitchRow
import com.easytier.android.ui.components.rememberWithVpnPermission
import com.easytier.android.ui.icons.AppIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 编辑页 ViewModel。 */
class EditorViewModel(val container: AppContainer) : ViewModel() {

    private val _network = MutableStateFlow<SavedNetwork?>(null)
    val network = _network.asStateFlow()

    /** 保存后自动启动。 */
    var autoStartAfterSave = false

    fun load(id: String?) {
        viewModelScope.launch {
            _network.value = if (id == null) {
                NetworksRepository_newNetwork()
            } else {
                container.networksRepository.get(id)
            }
        }
    }

    private fun NetworksRepository_newNetwork(): SavedNetwork =
        com.easytier.android.data.store.NetworksRepository.newNetwork()

    fun update(transform: (NetworkConfig) -> NetworkConfig) {
        _network.value = _network.value?.let { it.copy(config = transform(it.config)) }
    }

    fun save(onSaved: (SavedNetwork, Boolean) -> Unit) {
        val current = _network.value ?: return
        viewModelScope.launch {
            val updated = current.copy(updatedAt = System.currentTimeMillis())
            container.networksRepository.save(updated)
            _network.value = updated
            onSaved(updated, autoStartAfterSave)
        }
    }
}

/** 网络编辑页（设计稿 03 Basic / 04 Advanced + Port Forward）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkEditorScreen(
    container: AppContainer,
    networkId: String?,
    onBack: () -> Unit,
) {
    val vm: EditorViewModel = viewModel { EditorViewModel(container) }
    val network by vm.network.collectAsState()
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settings.collectAsState(initial = null)

    LaunchedEffect(networkId) { vm.load(networkId) }

    var tab by remember { mutableIntStateOf(0) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }

    val saved = network ?: return

    // VPN 授权后再运行；只存网络 id，授权框遮挡导致 Activity 重建后凭 id 从仓库重查
    var pendingRunId by rememberSaveable { mutableStateOf<String?>(null) }
    val runWithVpnPermission = rememberWithVpnPermission {
        pendingRunId?.let { id ->
            pendingRunId = null
            scope.launch {
                container.networksRepository.get(id)?.let { n ->
                    val withVpn = container.settingsRepository.settings.first().startVpnWithNetwork
                    container.vpnController.startNetwork(n, withVpn)
                }
            }
        }
        // 启动已发起（或无需授权）后再返回；授权弹窗期间留在本页，避免 launcher 随页面销毁
        onBack()
    }

    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
                Text(
                    if (networkId == null) "新建网络" else "编辑网络",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { showImportDialog = true }) {
                    Icon(AppIcons.Upload, "导入 TOML")
                }
                IconButton(onClick = {
                    vm.autoStartAfterSave = false
                    vm.save { _, _ -> }
                }) {
                    Icon(AppIcons.Save, "保存")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // 分段标签
            TabRow(selectedTabIndex = tab) {
                listOf("基本", "高级", "端口转发").forEachIndexed { i, label ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(label) },
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                when (tab) {
                    0 -> BasicTab(saved.config, vm::update)
                    1 -> AdvancedTab(saved.config, vm::update)
                    2 -> PortForwardTab(saved.config, vm::update)
                }
                Spacer(Modifier.height(120.dp))
            }

            // 底部保存条
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        vm.autoStartAfterSave = false
                        vm.save { _, _ -> }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(AppIcons.Save, null, Modifier.size(18.dp))
                    Text("保存", Modifier.padding(start = 6.dp))
                }
                Button(
                    onClick = {
                        vm.autoStartAfterSave = true
                        vm.save { n, _ ->
                            if (settings?.startVpnWithNetwork != false) {
                                pendingRunId = n.id
                                runWithVpnPermission()
                            } else {
                                container.vpnController.startNetwork(n, withVpn = false)
                                onBack()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                    Text("保存并运行", Modifier.padding(start = 6.dp))
                }
            }
        }
    }

    if (showImportDialog) {
        com.easytier.android.ui.components.TextInputDialog(
            title = "导入 TOML",
            initialValue = importText,
            label = "粘贴 EasyTier TOML 配置",
            onDismiss = { showImportDialog = false },
            onConfirm = { text ->
                TomlImporter.parse(text)
                    .onSuccess { cfg ->
                        vm.update { cfg }
                        importError = null
                        showImportDialog = false
                    }
                    .onFailure { importError = it.message }
            },
        )
    }
    importError?.let { err ->
        // 简单错误提示：显示在导入按钮下方
        Text(
            "导入失败: $err",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

// ---------- Basic 标签 ----------

@Composable
private fun BasicTab(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    var showSecret by remember { mutableStateOf(false) }

    SectionHeader("基本设置")
    OutlinedTextField(
        value = config.networkName,
        onValueChange = { v -> update { it.copy(networkName = v) } },
        label = { Text("网络名称") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = config.networkSecret ?: "",
        onValueChange = { v -> update { it.copy(networkSecret = v.ifBlank { null }) } },
        label = { Text("网络密钥") },
        placeholder = { Text("网络成员共享的密码") },
        singleLine = true,
        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { showSecret = !showSecret }) {
                Text(if (showSecret) "隐藏" else "显示")
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))

    // DHCP
    SwitchRow(
        title = "DHCP（自动分配）",
        subtitle = "关闭后手动指定虚拟 IPv4",
        checked = config.dhcp,
        onCheckedChange = { v -> update { it.copy(dhcp = v) } },
    )
    if (!config.dhcp) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = config.virtualIpv4 ?: "",
                onValueChange = { v -> update { it.copy(virtualIpv4 = v) } },
                label = { Text("虚拟 IPv4") },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            OutlinedTextField(
                value = config.networkLength.toString(),
                onValueChange = { v ->
                    update { it.copy(networkLength = v.toIntOrNull() ?: 24) }
                },
                label = { Text("前缀长度") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    // 连接方式
    SectionHeader("联网方式")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        NetworkingMethod.entries.forEachIndexed { i, method ->
            SegmentedButton(
                selected = config.networkingMethod == method,
                onClick = { update { it.copy(networkingMethod = method) } },
                shape = SegmentedButtonDefaults.itemShape(i, NetworkingMethod.entries.size),
            ) {
                Text(method.label)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    when (config.networkingMethod) {
        NetworkingMethod.PublicServer -> OutlinedTextField(
            value = config.publicServerUrl ?: "",
            onValueChange = { v -> update { it.copy(publicServerUrl = v) } },
            label = { Text("公共服务器 URL") },
            placeholder = { Text("tcp://public.easytier.cn:11010") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        NetworkingMethod.Manual -> StringListEditor(
            label = "初始节点（Peer）",
            items = config.peerUrls,
            onChange = { list -> update { it.copy(peerUrls = list) } },
            placeholder = "tcp://203.0.113.10:11010",
        )
        NetworkingMethod.Standalone -> Text(
            "独立模式：不连接任何节点，等待其他节点主动连接本机。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionHeader("运行选项")
    SwitchRow(
        title = "作为出口节点",
        subtitle = "允许其他节点将本机作为出口网关",
        checked = config.enableExitNode ?: false,
        onCheckedChange = { v -> update { it.copy(enableExitNode = v) } },
    )
    Text(
        "SOCKS5 代理等应用层设置已移至「设置 → 应用层」。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------- Advanced 标签 ----------

@Composable
private fun AdvancedTab(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    var showAllFlags by remember { mutableStateOf(false) }

    SectionHeader("功能开关")
    SwitchRow("延迟优先", config.latencyFirst ?: false) { v -> update { it.copy(latencyFirst = v) } }
    SwitchRow("使用 SmolTCP", config.useSmoltcp ?: false) { v -> update { it.copy(useSmoltcp = v) } }
    SwitchRow("禁用 IPv6", config.disableIpv6 ?: false) { v -> update { it.copy(disableIpv6 = v) } }
    SwitchRow("KCP 代理", config.enableKcpProxy ?: false) { v -> update { it.copy(enableKcpProxy = v) } }
    SwitchRow("禁用 P2P", config.disableP2p ?: false) { v -> update { it.copy(disableP2p = v) } }
    SwitchRow("无 TUN 模式", config.noTun ?: false) { v -> update { it.copy(noTun = v) } }
    SwitchRow("出口节点", config.enableExitNode ?: false) { v -> update { it.copy(enableExitNode = v) } }
    SwitchRow("Magic DNS", config.enableMagicDns ?: false) { v -> update { it.copy(enableMagicDns = v) } }
    SwitchRow("私有模式", config.enablePrivateMode ?: false) { v -> update { it.copy(enablePrivateMode = v) } }
    SwitchRow("多线程", config.multiThread ?: true) { v -> update { it.copy(multiThread = v) } }

    TextButton(onClick = { showAllFlags = !showAllFlags }) {
        Text(if (showAllFlags) "收起" else "显示全部 26 项")
    }
    if (showAllFlags) {
        SwitchRow("绑定设备", config.bindDevice ?: true) { v -> update { it.copy(bindDevice = v) } }
        SwitchRow("转发所有 Peer RPC", config.relayAllPeerRpc ?: false) { v -> update { it.copy(relayAllPeerRpc = v) } }
        SwitchRow("系统代理转发", config.proxyForwardBySystem ?: false) { v -> update { it.copy(proxyForwardBySystem = v) } }
        SwitchRow("禁用加密", config.disableEncryption ?: false) { v -> update { it.copy(disableEncryption = v) } }
        SwitchRow("禁用 UDP 打洞", config.disableUdpHolePunching ?: false) { v -> update { it.copy(disableUdpHolePunching = v) } }
        SwitchRow("禁用 TCP 打洞", config.disableTcpHolePunching ?: false) { v -> update { it.copy(disableTcpHolePunching = v) } }
        SwitchRow("禁用对称 NAT 打洞", config.disableSymHolePunching ?: false) { v -> update { it.copy(disableSymHolePunching = v) } }
        SwitchRow("禁用 UPnP", config.disableUpnp ?: false) { v -> update { it.copy(disableUpnp = v) } }
        SwitchRow("禁用数据中继", config.disableRelayData ?: false) { v -> update { it.copy(disableRelayData = v) } }
        SwitchRow("启用 UDP 广播中继", config.enableUdpBroadcastRelay ?: false) { v -> update { it.copy(enableUdpBroadcastRelay = v) } }
        SwitchRow("仅 P2P", config.p2pOnly ?: false) { v -> update { it.copy(p2pOnly = v) } }
        SwitchRow("惰性 P2P", config.lazyP2p ?: false) { v -> update { it.copy(lazyP2p = v) } }
        SwitchRow("要求 P2P", config.needP2p ?: false) { v -> update { it.copy(needP2p = v) } }
        SwitchRow("QUIC 代理", config.enableQuicProxy ?: false) { v -> update { it.copy(enableQuicProxy = v) } }
        SwitchRow("禁用 QUIC 监听", config.disableQuicInput ?: false) { v -> update { it.copy(disableQuicInput = v) } }
        SwitchRow("禁用 KCP 监听", config.disableKcpInput ?: false) { v -> update { it.copy(disableKcpInput = v) } }
    }

    SectionHeader("网络详情")
    OutlinedTextField(
        value = config.hostname ?: "",
        onValueChange = { v -> update { it.copy(hostname = v.ifBlank { null }) } },
        label = { Text("主机名") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    StringListEditor(
        label = "代理网段（CIDR）",
        items = config.proxyCidrs,
        onChange = { list -> update { it.copy(proxyCidrs = list) } },
        placeholder = "10.0.0.0/24",
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = config.mtu?.toString() ?: "",
        onValueChange = { v -> update { it.copy(mtu = v.toIntOrNull()) } },
        label = { Text("MTU") },
        supportingText = { Text("400 - 1380") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = config.devName ?: "",
        onValueChange = { v -> update { it.copy(devName = v.ifBlank { null }) } },
        label = { Text("设备名称") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    StringListEditor(
        label = "监听地址",
        items = config.listenerUrls,
        onChange = { list -> update { it.copy(listenerUrls = list) } },
        placeholder = "tcp://0.0.0.0:11010",
    )

    SectionHeader("路由与限速")
    SwitchRow("手动路由", config.enableManualRoutes ?: false) { v ->
        update { it.copy(enableManualRoutes = v) }
    }
    if (config.enableManualRoutes == true) {
        StringListEditor(
            label = "路由表",
            items = config.routes ?: emptyList(),
            onChange = { list -> update { it.copy(routes = list) } },
            placeholder = "10.147.0.0/16",
        )
    }
    SwitchRow("中继网络白名单", config.enableRelayNetworkWhitelist ?: false) { v ->
        update { it.copy(enableRelayNetworkWhitelist = v) }
    }
    if (config.enableRelayNetworkWhitelist == true) {
        StringListEditor(
            label = "白名单网段",
            items = config.relayNetworkWhitelist ?: emptyList(),
            onChange = { list -> update { it.copy(relayNetworkWhitelist = list) } },
            placeholder = "172.16.0.0/12",
        )
    }
    OutlinedTextField(
        value = config.instanceRecvBpsLimit?.toString() ?: "",
        onValueChange = { v -> update { it.copy(instanceRecvBpsLimit = v.toLongOrNull()) } },
        label = { Text("接收带宽限制（bit/s）") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    StringListEditor(
        label = "出口节点列表",
        items = config.exitNodes ?: emptyList(),
        onChange = { list -> update { it.copy(exitNodes = list) } },
        placeholder = "10.126.126.1",
    )
}

// ---------- Port Forward 标签 ----------

@Composable
private fun PortForwardTab(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    SectionHeader("端口转发")
    Text(
        "将本机端口的流量转发到虚拟网络中的其他节点。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    config.portForwards.forEachIndexed { index, pf ->
        PortForwardEditor(
            pf = pf,
            onChange = { newPf ->
                update { c ->
                    c.copy(portForwards = c.portForwards.toMutableList().apply { set(index, newPf) })
                }
            },
            onDelete = {
                update { c ->
                    c.copy(portForwards = c.portForwards.toMutableList().apply { removeAt(index) })
                }
            },
        )
        Spacer(Modifier.height(12.dp))
    }

    TextButton(onClick = {
        update { it.copy(portForwards = it.portForwards + PortForwardEntry()) }
    }) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
        Text("添加规则", Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun PortForwardEditor(
    pf: PortForwardEntry,
    onChange: (PortForwardEntry) -> Unit,
    onDelete: () -> Unit,
) {
    androidx.compose.material3.Card {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${pf.proto.uppercase()} ${pf.bindIp}:${pf.bindPort}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pf.bindIp,
                    onValueChange = { v -> onChange(pf.copy(bindIp = v)) },
                    label = { Text("绑定 IP") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = pf.bindPort.toString(),
                    onValueChange = { v -> onChange(pf.copy(bindPort = v.toIntOrNull() ?: 0)) },
                    label = { Text("绑定端口") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = pf.dstIp,
                    onValueChange = { v -> onChange(pf.copy(dstIp = v)) },
                    label = { Text("目标 IP") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = pf.dstPort.toString(),
                    onValueChange = { v -> onChange(pf.copy(dstPort = v.toIntOrNull() ?: 0)) },
                    label = { Text("目标端口") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("tcp", "udp").forEachIndexed { i, proto ->
                    SegmentedButton(
                        selected = pf.proto == proto,
                        onClick = { onChange(pf.copy(proto = proto)) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(proto.uppercase()) }
                }
            }
        }
    }
}
