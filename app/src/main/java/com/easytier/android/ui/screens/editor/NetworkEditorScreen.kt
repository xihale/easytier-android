package com.easytier.android.ui.screens.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.AppContainer
import com.easytier.android.core.toml.TomlGenerator
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.NetworkingMethod
import com.easytier.android.data.model.PortForwardEntry
import com.easytier.android.data.model.SavedNetwork
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.AppSnackbarHost
import com.easytier.android.ui.components.ConfirmDialog
import com.easytier.android.ui.components.EmptyState
import com.easytier.android.ui.components.IntField
import com.easytier.android.ui.components.LongField
import com.easytier.android.ui.components.SectionHeader
import com.easytier.android.ui.components.StringListEditor
import com.easytier.android.ui.components.SwitchRow
import com.easytier.android.ui.components.TomlExportDialog
import com.easytier.android.ui.components.TomlImportDialog
import com.easytier.android.ui.components.rememberWithVpnPermission
import com.easytier.android.ui.icons.AppIcons
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 表单内容的统一水平边距：滚动列本身不再加水平 padding——裸放的 SwitchRow 行内自带
 * 16dp，页面再包一层会双重缩进成 32dp；故改由各非 SwitchRow 元素自行补齐这层 16dp。
 */
private val FormHorizontalPadding = Modifier.padding(horizontal = 16.dp)

/** 编辑页 ViewModel。 */
class EditorViewModel(val container: AppContainer) : ViewModel() {

    private val _network = MutableStateFlow<SavedNetwork?>(null)
    val network = _network.asStateFlow()

    /** 落库时的快照，供 UI 做脏检查（防误退）。 */
    private val _original = MutableStateFlow<SavedNetwork?>(null)
    val original = _original.asStateFlow()

    /** 编辑目标已被删除（列表页先删、编辑页还开着等场景）。 */
    private val _notFound = MutableStateFlow(false)
    val notFound = _notFound.asStateFlow()

    /** 保存被拦截时的提示，展示后调 consumeSaveError() 清除。 */
    private val _saveError = MutableStateFlow<String?>(null)
    val saveError = _saveError.asStateFlow()

    fun consumeSaveError() {
        _saveError.value = null
    }

    fun load(id: String?) {
        viewModelScope.launch {
            if (id == null) {
                val created = NetworksRepository_newNetwork()
                _network.value = created
                _original.value = created
                _notFound.value = false
            } else {
                val loaded = container.networksRepository.get(id)
                _notFound.value = loaded == null
                _network.value = loaded
                _original.value = loaded
            }
        }
    }

    private fun NetworksRepository_newNetwork(): SavedNetwork =
        com.easytier.android.data.store.NetworksRepository.newNetwork()

    fun update(transform: (NetworkConfig) -> NetworkConfig) {
        _network.value = _network.value?.let { it.copy(config = transform(it.config)) }
    }

    /**
     * 保存（可选同时启用）。全部跑在 viewModelScope：
     * 保存后立刻 popBackStack 也不会取消 DataStore 写入（UI 的 rememberCoroutineScope 会随页面销毁被取消）。
     */
    fun save(startEnabled: Boolean, onSaved: (String) -> Unit) {
        val current = _network.value ?: return
        viewModelScope.launch {
            // 引擎按 network_name 组织实例：空名/与其他网络重名都会冲突，统一在落库前拦截
            val name = current.config.networkName.trim()
            when {
                name.isBlank() -> {
                    _saveError.value = "网络名称不能为空"
                    return@launch
                }
                container.networksRepository.networks.first()
                    .any { it.id != current.id && it.config.networkName == name } -> {
                    _saveError.value = "已存在同名网络「$name」，请修改名称后再保存"
                    return@launch
                }
            }
            val updated = current.copy(
                config = current.config.copy(networkName = name),
                enabled = current.enabled || startEnabled,
                updatedAt = System.currentTimeMillis(),
            )
            _saveError.value = null
            container.networksRepository.save(updated)
            _network.value = updated
            _original.value = updated
            if (startEnabled) {
                if (container.vpnController.serviceRunning.value) {
                    container.vpnController.onEnabledChanged(updated, true)
                } else {
                    // 服务未运行时 onEnabledChanged 是空操作：直接拉起服务（含刚保存的此网络）
                    val enabledNetworks = container.networksRepository.networks.first()
                        .filter { it.enabled }
                    container.vpnController.startService(enabledNetworks)
                        .onFailure { _saveError.value = it.message ?: "启动服务失败" }
                }
            }
            onSaved(updated.id)
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
    onSaved: (String) -> Unit = { _ -> onBack() },
) {
    val vm: EditorViewModel = viewModel { EditorViewModel(container) }
    val network by vm.network.collectAsState()
    val original by vm.original.collectAsState()
    val notFound by vm.notFound.collectAsState()
    val saveError by vm.saveError.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // 保存被拦截（空名/重名/启动失败）时提示；空名/重名不离开编辑页
    LaunchedEffect(saveError) {
        saveError?.let {
            snackbar.showSnackbar(it)
            vm.consumeSaveError()
        }
    }

    LaunchedEffect(networkId) { vm.load(networkId) }

    // rememberSaveable：切后台/进程重建后停留在原 tab
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // 脏检查：与落库快照不一致时拦截返回，防误触丢改动
    val dirty = network != null && network != original
    fun attemptBack() {
        if (dirty) showDiscardDialog = true else onBack()
    }
    BackHandler(enabled = dirty) { showDiscardDialog = true }

    // VPN 关闭（仅引擎模式）时无需系统 VPN 权限
    val enableVpn by remember(container) {
        container.settingsRepository.settings.map { it.enableVpn }
    }.collectAsState(initial = true)

    // 保存并启用：启用即可能建 TUN，先确保 VPN 已授权
    val saveAndEnableWithPermission = rememberWithVpnPermission(enabled = enableVpn) {
        vm.save(true, onSaved)
    }

    fun saveOnly() {
        vm.save(false, onSaved)
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        // 外层（MainActivity 的）Scaffold 已处理系统栏/底栏 inset，这里清零杜绝双重 pad
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::attemptBack) {
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
                IconButton(onClick = { showExportDialog = true }) {
                    Icon(AppIcons.Download, "导出 TOML")
                }
            }
        },
    ) { padding ->
        // 数据未就绪时也渲染外壳（顶栏+标签），避免过渡期只剩背景色像蒙了层遮罩；
        // imePadding：键盘弹出时让出输入区
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
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

            val saved = network
            when {
                // 编辑目标已被删除：给出明确出口而不是无限 spinner
                notFound -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        EmptyState(
                            icon = AppIcons.CloudOff,
                            title = "网络不存在",
                            hint = "该网络可能已被删除",
                        )
                        TextButton(onClick = onBack) { Text("返回") }
                    }
                }
                saved == null -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                else -> {
                    // 各 tab 独立滚动：切换 tab 不停留在上个 tab 的滚动位置
                    when (tab) {
                        0 -> Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                        ) {
                            BasicTab(saved.config, vm::update)
                            Spacer(Modifier.height(16.dp))
                        }
                        1 -> Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                        ) {
                            AdvancedTab(saved.config, vm::update)
                            Spacer(Modifier.height(16.dp))
                        }
                        2 -> Column(
                            Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                        ) {
                            PortForwardTab(saved.config, vm::update)
                            Spacer(Modifier.height(16.dp))
                        }
                    }

                    // 底部保存条（weight 布局保证不被滚动区挤掉）
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 层级：仅保存为次要操作走描边按钮，「保存并启用」才是唯一主操作
                        OutlinedButton(
                            onClick = ::saveOnly,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(AppIcons.Save, null, Modifier.size(18.dp))
                            Text("保存", Modifier.padding(start = 6.dp))
                        }
                        Button(
                            onClick = saveAndEnableWithPermission,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Text("保存并启用", Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        ConfirmDialog(
            title = "放弃修改？",
            text = "有未保存的修改，离开将丢失这些改动。",
            confirmText = "放弃",
            destructive = true,
            onConfirm = {
                showDiscardDialog = false
                onBack()
            },
            onDismiss = { showDiscardDialog = false },
        )
    }
    if (showImportDialog) {
        TomlImportDialog(
            initialText = "",
            onDismiss = { showImportDialog = false },
            onImported = { cfg ->
                vm.update { cfg }
                showImportDialog = false
                scope.launch { snackbar.showSnackbar("已导入配置「${cfg.networkName}」，记得保存") }
            },
        )
    }
    if (showExportDialog) {
        network?.let { current ->
            TomlExportDialog(
                // 导出不带本机 instance_id：对方直接用于核心时避免同 peer_id 互踢
                toml = remember(current.config) {
                    TomlGenerator.generate(current.config, includeInstanceId = false)
                },
                networkName = current.config.networkName,
                onDismiss = { showExportDialog = false },
            )
        }
    }
}

// ---------- Basic 标签 ----------

@Composable
private fun BasicTab(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    var showSecret by remember { mutableStateOf(false) }

    SectionHeader("基本设置", modifier = FormHorizontalPadding)
    OutlinedTextField(
        value = config.networkName,
        onValueChange = { v -> update { it.copy(networkName = v) } },
        label = { Text("网络名称") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = config.networkSecret ?: "",
        onValueChange = { v -> update { it.copy(networkSecret = v.ifBlank { null }) } },
        label = { Text("网络密钥") },
        placeholder = { Text("网络成员共享的密码") },
        singleLine = true,
        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            // 更轻的显隐切换：IconButton 承载小号中性色文字，作为输入框附属操作而非独立按钮
            IconButton(onClick = { showSecret = !showSecret }) {
                Text(
                    if (showSecret) "隐藏" else "显示",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
    )
    Spacer(Modifier.height(12.dp))

    // DHCP
    SwitchRow(
        title = "DHCP（自动分配）",
        subtitle = "关闭后手动指定虚拟 IPv4",
        checked = config.dhcp,
        onCheckedChange = { v -> update { it.copy(dhcp = v) } },
    )
    if (config.dhcp && config.networkingMethod == NetworkingMethod.Standalone) {
        Text(
            "注意：独立网络无其他节点，DHCP 无法分配地址，请关闭并手动指定虚拟 IPv4。",
            modifier = FormHorizontalPadding,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (!config.dhcp) {
        Row(
            Modifier.fillMaxWidth().then(FormHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = config.virtualIpv4 ?: "",
                onValueChange = { v -> update { it.copy(virtualIpv4 = v) } },
                label = { Text("虚拟 IPv4") },
                singleLine = true,
                modifier = Modifier.weight(2f),
            )
            IntField(
                value = config.networkLength,
                onValueChange = { v -> update { it.copy(networkLength = v ?: 24) } },
                label = "前缀长度",
                range = 0..32,
                allowEmpty = false,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    // 连接方式
    SectionHeader("联网方式", modifier = FormHorizontalPadding)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().then(FormHorizontalPadding)) {
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
            modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
        )
        NetworkingMethod.Manual -> StringListEditor(
            label = "初始节点（Peer）",
            items = config.peerUrls,
            onChange = { list -> update { it.copy(peerUrls = list) } },
            placeholder = "tcp://203.0.113.10:11010",
            modifier = FormHorizontalPadding,
        )
        NetworkingMethod.Standalone -> Text(
            "独立模式：不连接任何节点，等待其他节点主动连接本机。",
            modifier = FormHorizontalPadding,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    SectionHeader("运行选项", modifier = FormHorizontalPadding)
    // 基本页最后一行，不画分割线
    SwitchRow(
        title = "作为出口节点",
        subtitle = "允许其他节点将本机作为出口网关",
        checked = config.enableExitNode ?: false,
        onCheckedChange = { v -> update { it.copy(enableExitNode = v) } },
    )
}

// ---------- Advanced 标签 ----------

@Composable
private fun AdvancedTab(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    var showAllFlags by remember { mutableStateOf(false) }

    SectionHeader("功能开关", modifier = FormHorizontalPadding)
    SwitchRow("延迟优先", config.latencyFirst ?: false) { v -> update { it.copy(latencyFirst = v) } }
    SwitchRow("使用 SmolTCP", config.useSmoltcp ?: false) { v -> update { it.copy(useSmoltcp = v) } }
    SwitchRow("禁用 IPv6", config.disableIpv6 ?: false) { v -> update { it.copy(disableIpv6 = v) } }
    SwitchRow("KCP 代理", config.enableKcpProxy ?: false) { v -> update { it.copy(enableKcpProxy = v) } }
    SwitchRow("禁用 P2P", config.disableP2p ?: false) { v -> update { it.copy(disableP2p = v) } }
    SwitchRow("无 TUN 模式", config.noTun ?: false) { v -> update { it.copy(noTun = v) } }
    SwitchRow("Magic DNS", config.enableMagicDns ?: false) { v -> update { it.copy(enableMagicDns = v) } }
    SwitchRow("私有模式", config.enablePrivateMode ?: false) { v -> update { it.copy(enablePrivateMode = v) } }
    SwitchRow("多线程", config.multiThread ?: true) { v -> update { it.copy(multiThread = v) } }

    // 可见 9 项 + 隐藏 16 项（出口节点在「基本」页，避免重复字段）
    TextButton(onClick = { showAllFlags = !showAllFlags }, modifier = FormHorizontalPadding) {
        Text(if (showAllFlags) "收起" else "显示全部 25 项")
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

    SectionHeader("网络详情", modifier = FormHorizontalPadding)
    OutlinedTextField(
        value = config.hostname ?: "",
        onValueChange = { v -> update { it.copy(hostname = v.ifBlank { null }) } },
        label = { Text("主机名") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
    )
    Spacer(Modifier.height(12.dp))
    StringListEditor(
        label = "代理网段（CIDR）",
        items = config.proxyCidrs,
        onChange = { list -> update { it.copy(proxyCidrs = list) } },
        placeholder = "10.0.0.0/24",
        modifier = FormHorizontalPadding,
    )
    Spacer(Modifier.height(12.dp))
    IntField(
        value = config.mtu,
        onValueChange = { v -> update { it.copy(mtu = v) } },
        label = "MTU",
        range = 400..1380,
        supporting = "400 - 1380",
        allowEmpty = true,
        modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = config.devName ?: "",
        onValueChange = { v -> update { it.copy(devName = v.ifBlank { null }) } },
        label = { Text("设备名称") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
    )
    Spacer(Modifier.height(12.dp))
    StringListEditor(
        label = "监听地址",
        items = config.listenerUrls,
        onChange = { list -> update { it.copy(listenerUrls = list) } },
        placeholder = "tcp://0.0.0.0:11010",
        modifier = FormHorizontalPadding,
    )

    SectionHeader("路由与限速", modifier = FormHorizontalPadding)
    SwitchRow("手动路由", config.enableManualRoutes ?: false) { v ->
        update { it.copy(enableManualRoutes = v) }
    }
    if (config.enableManualRoutes == true) {
        StringListEditor(
            label = "路由表",
            items = config.routes ?: emptyList(),
            onChange = { list -> update { it.copy(routes = list) } },
            placeholder = "10.147.0.0/16",
            modifier = FormHorizontalPadding,
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
            modifier = FormHorizontalPadding,
        )
    }
    LongField(
        value = config.instanceRecvBpsLimit,
        onValueChange = { v -> update { it.copy(instanceRecvBpsLimit = v) } },
        label = "接收带宽限制（bit/s）",
        allowEmpty = true,
        modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
    )
    Spacer(Modifier.height(12.dp))
    StringListEditor(
        label = "出口节点列表",
        items = config.exitNodes ?: emptyList(),
        onChange = { list -> update { it.copy(exitNodes = list) } },
        placeholder = "10.126.126.1",
        modifier = FormHorizontalPadding,
    )
}

// ---------- Port Forward 标签 ----------

@Composable
private fun PortForwardTab(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    SectionHeader("端口转发", modifier = FormHorizontalPadding)
    Text(
        "将本机端口的流量转发到虚拟网络中的其他节点。",
        modifier = FormHorizontalPadding,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    config.portForwards.forEachIndexed { index, pf ->
        PortForwardEditor(
            pf = pf,
            modifier = FormHorizontalPadding,
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

    TextButton(
        onClick = {
            update { it.copy(portForwards = it.portForwards + PortForwardEntry()) }
        },
        modifier = FormHorizontalPadding,
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
        Text("添加规则", Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun PortForwardEditor(
    pf: PortForwardEntry,
    modifier: Modifier = Modifier,
    onChange: (PortForwardEntry) -> Unit,
    onDelete: () -> Unit,
) {
    // 统一卡片语言：扁平 AppCard（细描边 + surfaceContainerLowest），内边距 16dp
    AppCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${pf.proto.uppercase()} ${pf.bindIp}:${pf.bindPort}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    // IP/端口等技术数值一律等宽字体
                    fontFamily = FontFamily.Monospace,
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Filled.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pf.bindIp,
                    onValueChange = { v -> onChange(pf.copy(bindIp = v)) },
                    label = { Text("绑定 IP") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                IntField(
                    value = pf.bindPort,
                    onValueChange = { v -> onChange(pf.copy(bindPort = v ?: 0)) },
                    label = "绑定端口",
                    range = 1..65535,
                    allowEmpty = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pf.dstIp,
                    onValueChange = { v -> onChange(pf.copy(dstIp = v)) },
                    label = { Text("目标 IP") },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                IntField(
                    value = pf.dstPort,
                    onValueChange = { v -> onChange(pf.copy(dstPort = v ?: 0)) },
                    label = "目标端口",
                    range = 1..65535,
                    allowEmpty = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
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
