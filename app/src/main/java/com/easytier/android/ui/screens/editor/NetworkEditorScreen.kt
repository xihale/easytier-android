package com.easytier.android.ui.screens.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.easytier.android.R
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
import com.easytier.android.ui.components.rememberHaptics
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

    /** 非 Composable 作用域取本地化文案用。 */
    private val appContext = container.appContext

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
                    _saveError.value = appContext.getString(R.string.editor_err_empty_name)
                    return@launch
                }
                container.networksRepository.networks.first()
                    .any { it.id != current.id && it.config.networkName == name } -> {
                    _saveError.value = appContext.getString(R.string.editor_err_dup_name, name)
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
                        .onFailure {
                            _saveError.value = it.message
                                ?: appContext.getString(R.string.editor_err_start_failed)
                        }
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
    val context = LocalContext.current
    val haptics = rememberHaptics()

    // 保存被拦截（空名/重名/启动失败）时提示；空名/重名不离开编辑页
    LaunchedEffect(saveError) {
        saveError?.let {
            haptics.reject()
            snackbar.showSnackbar(it)
            vm.consumeSaveError()
        }
    }

    LaunchedEffect(networkId) { vm.load(networkId) }

    // rememberSaveable：切后台/进程重建后停留在原 tab
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val basicScroll = rememberScrollState()
    val advancedScroll = rememberScrollState()
    val pfScroll = rememberScrollState()
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    // 脏检查：与落库快照不一致时拦截返回，防误触丢改动
    val dirty = network != null && network != original
    fun attemptBack() {
        if (dirty) {
            haptics.reject()
            showDiscardDialog = true
        } else {
            onBack()
        }
    }
    BackHandler(enabled = dirty) {
        haptics.reject()
        showDiscardDialog = true
    }

    // VPN 关闭（仅引擎模式）时无需系统 VPN 权限
    val enableVpn by remember(container) {
        container.settingsRepository.settings.map { it.enableVpn }
    }.collectAsState(initial = true)

    // 保存并启用：启用即可能建 TUN，先确保 VPN 已授权
    val saveAndEnableWithPermission = rememberWithVpnPermission(enabled = enableVpn) {
        vm.save(true) { id ->
            onSaved(id)
        }
    }

    fun saveOnly() {
        vm.save(false) { id ->
            onSaved(id)
        }
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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                }
                Text(
                    if (networkId == null) {
                        stringResource(R.string.editor_new)
                    } else {
                        stringResource(R.string.editor_edit)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = {
                    showImportDialog = true
                }) {
                    Icon(AppIcons.Upload, stringResource(R.string.editor_import_toml))
                }
                IconButton(onClick = {
                    showExportDialog = true
                }) {
                    Icon(AppIcons.Download, stringResource(R.string.editor_export_toml))
                }
            }
        },
    ) { padding ->
        // 数据未就绪时也渲染外壳（顶栏+标签），避免过渡期只剩背景色像蒙了层遮罩；
        // imePadding：键盘弹出时让出输入区
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            // 分段标签
            TabRow(selectedTabIndex = tab) {
                listOf(
                    R.string.editor_tab_basic,
                    R.string.editor_tab_advanced,
                    R.string.editor_tab_port_forward,
                ).forEachIndexed { i, labelRes ->
                    Tab(
                        selected = tab == i,
                        onClick = { tab = i },
                        text = { Text(stringResource(labelRes)) },
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
                            title = stringResource(R.string.editor_not_found_title),
                            hint = stringResource(R.string.editor_not_found_hint),
                        )
                        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                    }
                }
                saved == null -> Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                else -> {
                    // 各 tab 独立滚动状态 + 方向性横向过渡
                    AnimatedContent(
                        targetState = tab,
                        modifier = Modifier.weight(1f),
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (fadeIn(tween(180, easing = FastOutSlowInEasing)) +
                                slideInHorizontally(tween(200, easing = FastOutSlowInEasing)) { dir * (it / 10) })
                                .togetherWith(
                                    fadeOut(tween(120)) +
                                        slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -dir * (it / 14) },
                                )
                        },
                        label = "editorTabContent",
                    ) { currentTab ->
                        when (currentTab) {
                            0 -> Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(basicScroll)
                                    .padding(vertical = 8.dp),
                            ) {
                                BasicTab(saved.config, vm::update)
                                Spacer(Modifier.height(16.dp))
                            }
                            1 -> Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(advancedScroll)
                                    .padding(vertical = 8.dp),
                            ) {
                                AdvancedTab(saved.config, vm::update)
                                Spacer(Modifier.height(16.dp))
                            }
                            2 -> Column(
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(pfScroll)
                                    .padding(vertical = 8.dp),
                            ) {
                                PortForwardTab(saved.config, vm::update)
                                Spacer(Modifier.height(16.dp))
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
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
                            Text(stringResource(R.string.editor_save), Modifier.padding(start = 6.dp))
                        }
                        Button(
                            onClick = saveAndEnableWithPermission,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Text(stringResource(R.string.editor_save_and_enable), Modifier.padding(start = 6.dp))
                        }
                    }
                }
            }
        }
    }

    if (showDiscardDialog) {
        ConfirmDialog(
            title = stringResource(R.string.editor_discard_title),
            text = stringResource(R.string.editor_discard_text),
            confirmText = stringResource(R.string.editor_discard_confirm),
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
                scope.launch {
                    snackbar.showSnackbar(context.getString(R.string.editor_imported_snack, cfg.networkName))
                }
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
    val hideLabel = stringResource(R.string.editor_hide)
    val showLabel = stringResource(R.string.editor_show)
    val prefixLenLabel = stringResource(R.string.editor_prefix_len)
    val initialPeersLabel = stringResource(R.string.editor_initial_peers)

    SectionHeader(stringResource(R.string.editor_section_basic), modifier = FormHorizontalPadding)
    OutlinedTextField(
        value = config.networkName,
        onValueChange = { v -> update { it.copy(networkName = v) } },
        label = { Text(stringResource(R.string.editor_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = config.networkSecret ?: "",
        onValueChange = { v -> update { it.copy(networkSecret = v.ifBlank { null }) } },
        label = { Text(stringResource(R.string.editor_secret)) },
        placeholder = { Text(stringResource(R.string.editor_secret_hint)) },
        singleLine = true,
        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            // 更轻的显隐切换：IconButton 承载小号中性色文字，作为输入框附属操作而非独立按钮
            IconButton(onClick = {
                showSecret = !showSecret
            }) {
                Text(
                    if (showSecret) showLabel else hideLabel,
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
        title = stringResource(R.string.editor_dhcp),
        subtitle = stringResource(R.string.editor_dhcp_subtitle),
        checked = config.dhcp,
        onCheckedChange = { v -> update { it.copy(dhcp = v) } },
    )
    AnimatedVisibility(
        visible = config.dhcp && config.networkingMethod == NetworkingMethod.Standalone,
        enter = expandVertically(tween(200, easing = FastOutSlowInEasing)) + fadeIn(tween(160)),
        exit = shrinkVertically(tween(160, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
    ) {
        Text(
            stringResource(R.string.editor_standalone_dhcp_warn),
            modifier = FormHorizontalPadding,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    AnimatedVisibility(
        visible = !config.dhcp,
        enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().then(FormHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = config.virtualIpv4 ?: "",
                    onValueChange = { v -> update { it.copy(virtualIpv4 = v) } },
                    label = { Text(stringResource(R.string.editor_virtual_ipv4)) },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                IntField(
                    value = config.networkLength,
                    onValueChange = { v -> update { it.copy(networkLength = v ?: 24) } },
                    label = prefixLenLabel,
                    range = 0..32,
                    allowEmpty = false,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    // 连接方式
    SectionHeader(stringResource(R.string.editor_section_networking), modifier = FormHorizontalPadding)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().then(FormHorizontalPadding)) {
        NetworkingMethod.entries.forEachIndexed { i, method ->
            SegmentedButton(
                selected = config.networkingMethod == method,
                onClick = {
                    update { it.copy(networkingMethod = method) }
                },
                shape = SegmentedButtonDefaults.itemShape(i, NetworkingMethod.entries.size),
            ) {
                Text(stringResource(method.labelRes))
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    AnimatedContent(
        targetState = config.networkingMethod,
        transitionSpec = {
            fadeIn(tween(180, easing = FastOutSlowInEasing)) togetherWith
                fadeOut(tween(120))
        },
        label = "networkingMethodInput",
    ) { method ->
        when (method) {
            NetworkingMethod.PublicServer -> OutlinedTextField(
                value = config.publicServerUrl ?: "",
                onValueChange = { v -> update { it.copy(publicServerUrl = v) } },
                label = { Text(stringResource(R.string.editor_public_server_url)) },
                placeholder = { Text("tcp://public.easytier.cn:11010") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
            )
            NetworkingMethod.Manual -> StringListEditor(
                label = initialPeersLabel,
                items = config.peerUrls,
                onChange = { list -> update { it.copy(peerUrls = list) } },
                placeholder = "tcp://203.0.113.10:11010",
                modifier = FormHorizontalPadding,
            )
            NetworkingMethod.Standalone -> Text(
                stringResource(R.string.editor_standalone_desc),
                modifier = FormHorizontalPadding,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    SectionHeader(stringResource(R.string.editor_section_runtime), modifier = FormHorizontalPadding)
    // 基本页最后一行，不画分割线
    SwitchRow(
        title = stringResource(R.string.editor_exit_node),
        subtitle = stringResource(R.string.editor_exit_node_subtitle),
        checked = config.enableExitNode ?: false,
        onCheckedChange = { v -> update { it.copy(enableExitNode = v) } },
    )
}

// ---------- Advanced 标签 ----------

@Composable
private fun AdvancedTab(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    var showAllFlags by remember { mutableStateOf(false) }
    val flagsChevronRotation by animateFloatAsState(
        targetValue = if (showAllFlags) 180f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "flagsChevron",
    )
    val collapseLabel = stringResource(R.string.common_collapse)
    val hostnameLabel = stringResource(R.string.editor_hostname)
    val proxyCidrsLabel = stringResource(R.string.editor_proxy_cidrs)
    val mtuLabel = stringResource(R.string.editor_mtu)
    val listenersLabel = stringResource(R.string.editor_listeners)
    val routeTableLabel = stringResource(R.string.editor_route_table)
    val whitelistCidrsLabel = stringResource(R.string.editor_whitelist_cidrs)
    val recvBpsLimitLabel = stringResource(R.string.editor_recv_bps_limit)
    val exitNodesLabel = stringResource(R.string.editor_exit_nodes)

    SectionHeader(stringResource(R.string.editor_section_flags), modifier = FormHorizontalPadding)
    SwitchRow(stringResource(R.string.flag_latency_first), config.latencyFirst ?: false) { v -> update { it.copy(latencyFirst = v) } }
    SwitchRow(stringResource(R.string.flag_smoltcp), config.useSmoltcp ?: false) { v -> update { it.copy(useSmoltcp = v) } }
    SwitchRow(stringResource(R.string.flag_disable_ipv6), config.disableIpv6 ?: false) { v -> update { it.copy(disableIpv6 = v) } }
    SwitchRow(stringResource(R.string.flag_kcp_proxy), config.enableKcpProxy ?: false) { v -> update { it.copy(enableKcpProxy = v) } }
    SwitchRow(stringResource(R.string.flag_disable_p2p), config.disableP2p ?: false) { v -> update { it.copy(disableP2p = v) } }
    SwitchRow(stringResource(R.string.flag_no_tun), config.noTun ?: false) { v -> update { it.copy(noTun = v) } }
    SwitchRow(stringResource(R.string.flag_magic_dns), config.enableMagicDns, subtitle = stringResource(R.string.flag_magic_dns_subtitle)) { v -> update { it.copy(enableMagicDns = v) } }
    AnimatedVisibility(
        visible = config.enableMagicDns,
        enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
    ) {
        Column {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = config.tldDnsZone ?: "",
                onValueChange = { v -> update { it.copy(tldDnsZone = v.trim().ifBlank { null }) } },
                label = { Text(stringResource(R.string.editor_dns_zone)) },
                placeholder = { Text(config.networkName) },
                supportingText = {
                    Text(stringResource(R.string.editor_dns_zone_support, TomlGenerator.effectiveDnsZone(config)))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
            )
        }
    }
    SwitchRow(stringResource(R.string.flag_private_mode), config.enablePrivateMode ?: false) { v -> update { it.copy(enablePrivateMode = v) } }
    SwitchRow(stringResource(R.string.flag_multi_thread), config.multiThread ?: true) { v -> update { it.copy(multiThread = v) } }

    // 可见 9 项 + 隐藏 16 项（出口节点在「基本」页，避免重复字段）
    TextButton(
        onClick = { showAllFlags = !showAllFlags },
        modifier = FormHorizontalPadding,
    ) {
        Text(if (showAllFlags) collapseLabel else stringResource(R.string.editor_show_all_flags))
        Icon(
            AppIcons.ExpandMore,
            null,
            modifier = Modifier.padding(start = 4.dp).size(18.dp).rotate(flagsChevronRotation),
        )
    }
    AnimatedVisibility(
        visible = showAllFlags,
        enter = expandVertically(tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(200, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
    ) {
        Column {
            SwitchRow(stringResource(R.string.flag_bind_device), config.bindDevice ?: true) { v -> update { it.copy(bindDevice = v) } }
            SwitchRow(stringResource(R.string.flag_relay_all_peer_rpc), config.relayAllPeerRpc ?: false) { v -> update { it.copy(relayAllPeerRpc = v) } }
            SwitchRow(stringResource(R.string.flag_proxy_forward_by_system), config.proxyForwardBySystem ?: false) { v -> update { it.copy(proxyForwardBySystem = v) } }
            SwitchRow(stringResource(R.string.flag_disable_encryption), config.disableEncryption ?: false) { v -> update { it.copy(disableEncryption = v) } }
            SwitchRow(stringResource(R.string.flag_disable_udp_hole_punching), config.disableUdpHolePunching ?: false) { v -> update { it.copy(disableUdpHolePunching = v) } }
            SwitchRow(stringResource(R.string.flag_disable_tcp_hole_punching), config.disableTcpHolePunching ?: false) { v -> update { it.copy(disableTcpHolePunching = v) } }
            SwitchRow(stringResource(R.string.flag_disable_sym_hole_punching), config.disableSymHolePunching ?: false) { v -> update { it.copy(disableSymHolePunching = v) } }
            SwitchRow(stringResource(R.string.flag_disable_upnp), config.disableUpnp ?: false) { v -> update { it.copy(disableUpnp = v) } }
            SwitchRow(stringResource(R.string.flag_disable_relay_data), config.disableRelayData ?: false) { v -> update { it.copy(disableRelayData = v) } }
            SwitchRow(stringResource(R.string.flag_udp_broadcast_relay), config.enableUdpBroadcastRelay ?: false) { v -> update { it.copy(enableUdpBroadcastRelay = v) } }
            SwitchRow(stringResource(R.string.flag_p2p_only), config.p2pOnly ?: false) { v -> update { it.copy(p2pOnly = v) } }
            SwitchRow(stringResource(R.string.flag_lazy_p2p), config.lazyP2p ?: false) { v -> update { it.copy(lazyP2p = v) } }
            SwitchRow(stringResource(R.string.flag_need_p2p), config.needP2p ?: false) { v -> update { it.copy(needP2p = v) } }
            SwitchRow(stringResource(R.string.flag_quic_proxy), config.enableQuicProxy ?: false) { v -> update { it.copy(enableQuicProxy = v) } }
            SwitchRow(stringResource(R.string.flag_disable_quic_input), config.disableQuicInput ?: false) { v -> update { it.copy(disableQuicInput = v) } }
            SwitchRow(stringResource(R.string.flag_disable_kcp_input), config.disableKcpInput ?: false) { v -> update { it.copy(disableKcpInput = v) } }
        }
    }

    SectionHeader(stringResource(R.string.editor_section_details), modifier = FormHorizontalPadding)
    OutlinedTextField(
        value = config.hostname ?: "",
        onValueChange = { v -> update { it.copy(hostname = v.ifBlank { null }) } },
        label = { Text(hostnameLabel) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
    )
    Spacer(Modifier.height(12.dp))
    StringListEditor(
        label = proxyCidrsLabel,
        items = config.proxyCidrs,
        onChange = { list -> update { it.copy(proxyCidrs = list) } },
        placeholder = "10.0.0.0/24",
        modifier = FormHorizontalPadding,
    )
    Spacer(Modifier.height(12.dp))
    IntField(
        value = config.mtu,
        onValueChange = { v -> update { it.copy(mtu = v) } },
        label = mtuLabel,
        range = 400..1380,
        supporting = "400 - 1380",
        allowEmpty = true,
        modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = config.devName ?: "",
        onValueChange = { v -> update { it.copy(devName = v.ifBlank { null }) } },
        label = { Text(stringResource(R.string.editor_dev_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
    )
    Spacer(Modifier.height(12.dp))
    StringListEditor(
        label = listenersLabel,
        items = config.listenerUrls,
        onChange = { list -> update { it.copy(listenerUrls = list) } },
        placeholder = "tcp://0.0.0.0:11010",
        modifier = FormHorizontalPadding,
    )

    SectionHeader(stringResource(R.string.editor_section_routes), modifier = FormHorizontalPadding)
    SwitchRow(stringResource(R.string.flag_manual_routes), config.enableManualRoutes ?: false) { v ->
        update { it.copy(enableManualRoutes = v) }
    }
    AnimatedVisibility(
        visible = config.enableManualRoutes == true,
        enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
    ) {
        StringListEditor(
            label = routeTableLabel,
            items = config.routes ?: emptyList(),
            onChange = { list -> update { it.copy(routes = list) } },
            placeholder = "10.147.0.0/16",
            modifier = FormHorizontalPadding,
        )
    }
    SwitchRow(stringResource(R.string.flag_relay_whitelist), config.enableRelayNetworkWhitelist ?: false) { v ->
        update { it.copy(enableRelayNetworkWhitelist = v) }
    }
    AnimatedVisibility(
        visible = config.enableRelayNetworkWhitelist == true,
        enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
        exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
    ) {
        StringListEditor(
            label = whitelistCidrsLabel,
            items = config.relayNetworkWhitelist ?: emptyList(),
            onChange = { list -> update { it.copy(relayNetworkWhitelist = list) } },
            placeholder = "172.16.0.0/12",
            modifier = FormHorizontalPadding,
        )
    }
    LongField(
        value = config.instanceRecvBpsLimit,
        onValueChange = { v -> update { it.copy(instanceRecvBpsLimit = v) } },
        label = recvBpsLimitLabel,
        allowEmpty = true,
        modifier = Modifier.fillMaxWidth().then(FormHorizontalPadding),
    )
    Spacer(Modifier.height(12.dp))
    StringListEditor(
        label = exitNodesLabel,
        items = config.exitNodes ?: emptyList(),
        onChange = { list -> update { it.copy(exitNodes = list) } },
        placeholder = "10.126.126.1",
        modifier = FormHorizontalPadding,
    )
}

// ---------- Port Forward 标签 ----------

@Composable
private fun PortForwardTab(config: NetworkConfig, update: ((NetworkConfig) -> NetworkConfig) -> Unit) {
    SectionHeader(stringResource(R.string.editor_section_pf), modifier = FormHorizontalPadding)
    Text(
        stringResource(R.string.editor_pf_desc),
        modifier = FormHorizontalPadding,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))

    Column(Modifier.animateContentSize(animationSpec = tween(durationMillis = 200))) {
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
    }

    TextButton(
        onClick = {
            update { it.copy(portForwards = it.portForwards + PortForwardEntry()) }
        },
        modifier = FormHorizontalPadding,
    ) {
        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
        Text(stringResource(R.string.editor_pf_add), Modifier.padding(start = 6.dp))
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
    val deleteLabel = stringResource(R.string.common_delete)
    val bindPortLabel = stringResource(R.string.editor_pf_bind_port)
    val dstPortLabel = stringResource(R.string.editor_pf_dst_port)
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
                    Icon(Icons.Filled.Delete, deleteLabel, tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = pf.bindIp,
                    onValueChange = { v -> onChange(pf.copy(bindIp = v)) },
                    label = { Text(stringResource(R.string.editor_pf_bind_ip)) },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                IntField(
                    value = pf.bindPort,
                    onValueChange = { v -> onChange(pf.copy(bindPort = v ?: 0)) },
                    label = bindPortLabel,
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
                    label = { Text(stringResource(R.string.editor_pf_dst_ip)) },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                IntField(
                    value = pf.dstPort,
                    onValueChange = { v -> onChange(pf.copy(dstPort = v ?: 0)) },
                    label = dstPortLabel,
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
                        onClick = {
                            onChange(pf.copy(proto = proto))
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(proto.uppercase()) }
                }
            }
        }
    }
}
