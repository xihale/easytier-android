package com.easytier.android.ui.screens.editor

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.AppContainer
import com.easytier.android.R
import com.easytier.android.core.toml.TomlGenerator
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.SavedNetwork
import com.easytier.android.ui.components.AppSnackbarHost
import com.easytier.android.ui.components.ConfirmDialog
import com.easytier.android.ui.components.EmptyState
import com.easytier.android.ui.components.InlineSearchBar
import com.easytier.android.ui.components.PillBadge
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

    val serviceRunning = container.vpnController.serviceRunning

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
                            return@launch
                        }
                }
            }
            onSaved(updated.id)
        }
    }
}

/** 网络编辑页（支持跨 Tab 即时搜索、滑动分页、列表条目点按修改、分组折叠）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkEditorScreen(
    container: AppContainer,
    networkId: String?,
    onBack: () -> Unit,
    onSaved: (String) -> Unit = { _ -> onBack() },
) {
    val vm: EditorViewModel = viewModel { EditorViewModel(container) }
    val network by vm.network.collectAsStateWithLifecycle()
    val original by vm.original.collectAsStateWithLifecycle()
    val notFound by vm.notFound.collectAsStateWithLifecycle()
    val saveError by vm.saveError.collectAsStateWithLifecycle()
    val serviceRunning by vm.serviceRunning.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val haptics = rememberHaptics()

    LaunchedEffect(saveError) {
        saveError?.let {
            haptics.reject()
            snackbar.showSnackbar(it)
            vm.consumeSaveError()
        }
    }

    LaunchedEffect(networkId) { vm.load(networkId) }

    val pagerState = rememberPagerState(pageCount = { 3 })
    val basicScroll = rememberScrollState()
    val advancedScroll = rememberScrollState()
    val pfScroll = rememberScrollState()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val dirty = network != null && network != original
    fun attemptBack() {
        if (dirty) {
            haptics.reject()
            showDiscardDialog = true
        } else {
            onBack()
        }
    }

    // 顺序关键：先注册 dirty 拦截，再注册 searchActive 拦截（后注册的生效优先），
    // 确保搜索态按返回键先退出搜索，而非直接弹窗放弃编辑。
    BackHandler(enabled = dirty) {
        haptics.reject()
        showDiscardDialog = true
    }
    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }

    val enableVpn by remember(container) {
        container.settingsRepository.settings.map { it.enableVpn }
    }.collectAsStateWithLifecycle(initialValue = true)

    val saveAndEnableWithPermission = rememberWithVpnPermission(enabled = enableVpn) {
        vm.save(true) { id -> onSaved(id) }
    }

    fun saveOnly() {
        vm.save(false) { id -> onSaved(id) }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AnimatedContent(
                targetState = searchActive,
                transitionSpec = {
                    fadeIn(tween(160, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(120))
                },
                label = "editorTopBarMode",
            ) { searching ->
                if (searching) {
                    InlineSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = {
                            searchActive = false
                            searchQuery = ""
                        },
                        placeholder = stringResource(R.string.editor_search_placeholder),
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = ::attemptBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(end = 4.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = if (networkId == null) {
                                        stringResource(R.string.editor_new)
                                    } else {
                                        stringResource(R.string.editor_edit)
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (dirty) {
                                    PillBadge(
                                        text = stringResource(R.string.editor_unsaved_badge),
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                            network?.config?.networkName?.takeIf { it.isNotBlank() }?.let { name ->
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (network != null && !notFound) {
                            IconButton(onClick = { searchActive = true }) {
                                Icon(Icons.Filled.Search, stringResource(R.string.common_search))
                            }
                        }
                        Box {
                            IconButton(onClick = { showOverflowMenu = true }) {
                                Icon(Icons.Filled.MoreVert, stringResource(R.string.common_more))
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.editor_import_toml)) },
                                    leadingIcon = { Icon(AppIcons.Upload, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showImportDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.editor_export_toml)) },
                                    leadingIcon = { Icon(AppIcons.Download, null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showExportDialog = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            val saved = network
            val showSearchResults = searchActive && searchQuery.isNotBlank() && saved != null

            if (!showSearchResults) {
                val pfCount = saved?.config?.portForwards?.size ?: 0
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                    listOf(
                        stringResource(R.string.editor_tab_basic),
                        stringResource(R.string.editor_tab_advanced),
                        if (pfCount > 0) {
                            "${stringResource(R.string.editor_tab_port_forward)} ($pfCount)"
                        } else {
                            stringResource(R.string.editor_tab_port_forward)
                        },
                    ).forEachIndexed { i, title ->
                        Tab(
                            selected = pagerState.currentPage == i,
                            onClick = {
                                scope.launch { pagerState.animateScrollToPage(i) }
                            },
                            text = {
                                Text(
                                    text = title,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }

            when {
                notFound -> Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
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
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                else -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        if (showSearchResults) {
                            EditorSearchResults(
                                query = searchQuery,
                                config = saved.config,
                                update = vm::update,
                            )
                        } else {
                            HorizontalPager(
                                state = pagerState,
                                beyondViewportPageCount = 1,
                                modifier = Modifier.fillMaxSize(),
                            ) { page ->
                                when (page) {
                                    0 -> Column(
                                        Modifier
                                            .fillMaxSize()
                                            .verticalScroll(basicScroll)
                                            .padding(horizontal = 16.dp),
                                    ) {
                                        BasicTab(saved.config, vm::update)
                                        Spacer(Modifier.height(24.dp))
                                    }
                                    1 -> Column(
                                        Modifier
                                            .fillMaxSize()
                                            .verticalScroll(advancedScroll)
                                            .padding(horizontal = 16.dp),
                                    ) {
                                        AdvancedTab(saved.config, vm::update)
                                        Spacer(Modifier.height(24.dp))
                                    }
                                    2 -> Column(
                                        Modifier
                                            .fillMaxSize()
                                            .verticalScroll(pfScroll)
                                            .padding(horizontal = 16.dp),
                                    ) {
                                        PortForwardTab(saved.config, vm::update)
                                        Spacer(Modifier.height(24.dp))
                                    }
                                }
                            }
                        }
                    }

                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Column {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
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
                                    Text(
                                        text = if (saved.enabled && serviceRunning) {
                                            stringResource(R.string.editor_save_and_apply)
                                        } else {
                                            stringResource(R.string.editor_save_and_enable)
                                        },
                                        Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
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
                toml = remember(current.config) {
                    TomlGenerator.generate(current.config, includeInstanceId = false)
                },
                networkName = current.config.networkName,
                onDismiss = { showExportDialog = false },
            )
        }
    }
}
