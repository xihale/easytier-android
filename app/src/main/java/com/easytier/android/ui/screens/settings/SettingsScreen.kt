package com.easytier.android.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.EasyTierApp
import com.easytier.android.R
import com.easytier.android.core.vpn.BootCompletedReceiver
import com.easytier.android.data.store.AppSettings
import com.easytier.android.data.store.SettingsRepository
import com.easytier.android.data.update.ReleaseInfo
import com.easytier.android.data.update.UpdateCheckResult
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.AppSnackbarHost
import com.easytier.android.ui.components.CardDivider
import com.easytier.android.ui.components.ChoiceRow
import com.easytier.android.ui.components.EmptyState
import com.easytier.android.ui.components.InlineSearchBar
import com.easytier.android.ui.components.OssLicensesDialog
import com.easytier.android.ui.components.PillBadge
import com.easytier.android.ui.components.SectionHeader
import com.easytier.android.ui.components.SettingRow
import com.easytier.android.ui.components.SwitchRow
import com.easytier.android.ui.components.TextInputDialog
import com.easytier.android.ui.icons.AppIcons
import com.easytier.android.util.BackgroundPermission
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val repo: SettingsRepository =
        EasyTierApp.get().container.settingsRepository

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            repo.initialSettings(),
        )

    fun setTheme(mode: String) = launchIO { repo.setThemeMode(mode) }

    fun setAutoStart(context: android.content.Context, enabled: Boolean) =
        launchIO {
            repo.setAutoStart(enabled)
            // 同步启用/禁用开机广播接收器组件，否则开关只是存了个数字
            BootCompletedReceiver.setEnabled(context, enabled)
        }

    fun setSocks5(enabled: Boolean, port: Int) = launchIO { repo.setSocks5(enabled, port) }

    fun setDnsServers(servers: List<String>) = launchIO { repo.setDnsServers(servers) }

    fun setVpnEnabled(enabled: Boolean) = launchIO { repo.setVpnEnabled(enabled) }

    fun setUpdateInterval(mode: String) = launchIO { repo.setUpdateInterval(mode) }

    private val _updateCheckResult = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Idle)
    val updateCheckResult: StateFlow<UpdateCheckResult> = _updateCheckResult.asStateFlow()

    /**
     * 检查更新的一次性 UI 事件。与上面常驻的 updateCheckResult 状态分离：
     * 状态会在离开页面（进子页/切底栏）后保留，回来时 LaunchedEffect 会重放旧值，
     * 造成反复弹「已是最新」；事件只在检查真正发生时发送一次，收过即没了。
     */
    sealed interface UpdateUiEvent {
        data object UpToDate : UpdateUiEvent
        data object Failed : UpdateUiEvent
        data class Newer(val info: ReleaseInfo) : UpdateUiEvent
    }

    private val _updateEvents = Channel<UpdateUiEvent>(Channel.BUFFERED)
    val updateEvents: Flow<UpdateUiEvent> = _updateEvents.receiveAsFlow()

    // 手动「检查更新」：发一次性事件（轻提示/弹窗），同时落库（用于自动检查触发与关于卡展示）
    fun checkUpdate() = viewModelScope.launch {
        _updateCheckResult.value = UpdateCheckResult.Checking
        val result = EasyTierApp.get().container.updateChecker.checkNow()
        _updateCheckResult.value = result
        val now = System.currentTimeMillis()
        val settingsRepo = EasyTierApp.get().container.settingsRepository
        settingsRepo.setLastUpdateCheckAt(now)
        when (result) {
            is UpdateCheckResult.Newer -> {
                settingsRepo.setPendingUpdate(result.info)
                _updateEvents.trySend(UpdateUiEvent.Newer(result.info))
            }
            is UpdateCheckResult.UpToDate -> {
                settingsRepo.clearPendingUpdate()
                _updateEvents.trySend(UpdateUiEvent.UpToDate)
            }
            UpdateCheckResult.Error -> _updateEvents.trySend(UpdateUiEvent.Failed)
            UpdateCheckResult.Checking, UpdateCheckResult.Idle -> Unit
        }
    }

    private fun launchIO(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

private val THEME_OPTIONS = listOf(
    "system" to R.string.theme_system,
    "light" to R.string.theme_light,
    "dark" to R.string.theme_dark,
)

private val UPDATE_INTERVAL_OPTIONS = listOf(
    "off" to R.string.update_freq_off,
    "startup" to R.string.update_freq_startup,
    "daily" to R.string.update_freq_daily,
    "weekly" to R.string.update_freq_weekly,
)

private enum class AppSettingSection(val titleRes: Int) {
    Connection(R.string.settings_section_connection),
    General(R.string.settings_section_general),
    About(R.string.settings_section_about),
}

private enum class AppSettingId {
    VpnMode,
    Socks5Toggle,
    Socks5Port,
    DnsUpstream,
    ClashHelp,
    AutoStart,
    Theme,
    CheckUpdate,
    AutoCheckInterval,
    ProjectHome,
    Licenses,
}

private data class AppSettingMeta(
    val id: AppSettingId,
    val section: AppSettingSection,
    val titleRes: Int,
    val keywords: List<String>,
)

private val APP_SETTINGS_META = listOf(
    AppSettingMeta(
        AppSettingId.VpnMode,
        AppSettingSection.Connection,
        R.string.settings_vpn_mode,
        listOf("vpn", "tun", "proxy", "代理"),
    ),
    AppSettingMeta(
        AppSettingId.Socks5Toggle,
        AppSettingSection.Connection,
        R.string.settings_socks5,
        listOf("socks", "socks5", "proxy", "代理"),
    ),
    AppSettingMeta(
        AppSettingId.Socks5Port,
        AppSettingSection.Connection,
        R.string.settings_socks5_port,
        listOf("socks", "socks5", "port", "端口", "1080"),
    ),
    AppSettingMeta(
        AppSettingId.DnsUpstream,
        AppSettingSection.Connection,
        R.string.settings_dns_upstream,
        listOf("dns", "dot", "doh", "magic", "上游", "域名"),
    ),
    AppSettingMeta(
        AppSettingId.ClashHelp,
        AppSettingSection.Connection,
        R.string.settings_clash_help,
        listOf("clash", "mihomo", "覆写", "override", "分流", "回环"),
    ),
    AppSettingMeta(
        AppSettingId.AutoStart,
        AppSettingSection.General,
        R.string.settings_start_on_boot,
        listOf("boot", "autostart", "开机", "自启"),
    ),
    AppSettingMeta(
        AppSettingId.Theme,
        AppSettingSection.General,
        R.string.settings_theme,
        listOf("theme", "dark", "light", "system", "主题", "深色", "浅色"),
    ),
    AppSettingMeta(
        AppSettingId.CheckUpdate,
        AppSettingSection.About,
        R.string.settings_check_update,
        listOf("update", "version", "release", "更新", "版本"),
    ),
    AppSettingMeta(
        AppSettingId.AutoCheckInterval,
        AppSettingSection.About,
        R.string.settings_auto_check,
        listOf("update", "interval", "auto", "自动", "检查"),
    ),
    AppSettingMeta(
        AppSettingId.ProjectHome,
        AppSettingSection.About,
        R.string.settings_project_home,
        listOf("github", "repo", "homepage", "开源", "主页"),
    ),
    AppSettingMeta(
        AppSettingId.Licenses,
        AppSettingSection.About,
        R.string.settings_licenses,
        listOf("license", "lgpl", "mit", "oss", "许可"),
    ),
)

@Composable
fun SettingsScreen(onOpenDns: () -> Unit = {}, onOpenClashHelp: () -> Unit = {}) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showSocks5Dialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showUpdateIntervalDialog by remember { mutableStateOf(false) }
    var updateDialogInfo by remember { mutableStateOf<ReleaseInfo?>(null) }
    val checkState by vm.updateCheckResult.collectAsStateWithLifecycle()

    var showBatteryDialog by remember { mutableStateOf(false) }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (BackgroundPermission.isIgnoringBatteryOptimizations(context)) {
            vm.setVpnEnabled(false)
        }
    }

    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }

    val s = settings
    val dnsSummary = when {
        s.dnsServers.isEmpty() -> stringResource(R.string.settings_dns_default_subtitle)
        s.dnsServers.size == 1 -> s.dnsServers.first()
        else -> stringResource(R.string.settings_dns_count_subtitle, s.dnsServers.size)
    }
    val themeLabel = THEME_OPTIONS.firstOrNull { it.first == s.themeMode }
        ?.let { stringResource(it.second) } ?: s.themeMode
    val updateIntervalLabel = UPDATE_INTERVAL_OPTIONS
        .firstOrNull { it.first == s.updateCheckInterval }
        ?.let { stringResource(it.second) }
        ?: stringResource(R.string.update_freq_off)

    val vpnSubtitle = stringResource(
        if (s.enableVpn) R.string.settings_vpn_mode_on_subtitle else R.string.settings_vpn_mode_off_subtitle,
    )
    val socksSubtitle = if (s.enableSocks5) {
        stringResource(R.string.settings_socks5_on_subtitle, s.socks5Port)
    } else {
        stringResource(R.string.settings_socks5_off_subtitle)
    }
    val clashSubtitle = stringResource(R.string.settings_clash_help_subtitle)
    val bootSubtitle = stringResource(R.string.settings_start_on_boot_subtitle)

    val isSearching = searchActive && searchQuery.isNotBlank()
    val filteredMetas = remember(searchQuery, s, dnsSummary, themeLabel, updateIntervalLabel) {
        if (!isSearching) {
            APP_SETTINGS_META
        } else {
            val tokens = searchQuery.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            APP_SETTINGS_META.filter { meta ->
                if (meta.id == AppSettingId.Socks5Port && !s.enableSocks5) return@filter false
                val extra = when (meta.id) {
                    AppSettingId.VpnMode -> vpnSubtitle
                    AppSettingId.Socks5Toggle -> socksSubtitle
                    AppSettingId.Socks5Port -> s.socks5Port.toString()
                    AppSettingId.DnsUpstream -> "$dnsSummary ${s.dnsServers.joinToString(" ")}"
                    AppSettingId.ClashHelp -> clashSubtitle
                    AppSettingId.AutoStart -> bootSubtitle
                    AppSettingId.Theme -> themeLabel
                    AppSettingId.AutoCheckInterval -> updateIntervalLabel
                    else -> ""
                }
                val haystack = "${context.getString(meta.titleRes)} ${meta.keywords.joinToString(" ")} $extra".lowercase()
                tokens.all { it in haystack }
            }
        }
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
                label = "settingsTopBarMode",
            ) { searching ->
                if (searching) {
                    InlineSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onClose = {
                            searchActive = false
                            searchQuery = ""
                        },
                        placeholder = stringResource(R.string.settings_search_placeholder),
                    )
                } else {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { searchActive = true }) {
                            Icon(Icons.Filled.Search, stringResource(R.string.common_search))
                        }
                    }
                }
            }
        },
    ) { padding ->
        @Composable
        fun RenderSettingRow(id: AppSettingId) {
            when (id) {
                AppSettingId.VpnMode -> SwitchRow(
                    title = stringResource(R.string.settings_vpn_mode),
                    subtitle = vpnSubtitle,
                    icon = AppIcons.Shield,
                    checked = s.enableVpn,
                    onCheckedChange = { want ->
                        if (want || BackgroundPermission.isIgnoringBatteryOptimizations(context)) {
                            vm.setVpnEnabled(want)
                        } else {
                            showBatteryDialog = true
                        }
                    },
                )
                AppSettingId.Socks5Toggle -> SwitchRow(
                    title = stringResource(R.string.settings_socks5),
                    subtitle = socksSubtitle,
                    icon = AppIcons.Language,
                    checked = s.enableSocks5,
                    onCheckedChange = { vm.setSocks5(it, s.socks5Port) },
                )
                AppSettingId.Socks5Port -> SettingRow(
                    title = stringResource(R.string.settings_socks5_port),
                    icon = AppIcons.Tune,
                    onClick = { showSocks5Dialog = true },
                    trailing = {
                        Text(
                            s.socks5Port.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
                AppSettingId.DnsUpstream -> SettingRow(
                    title = stringResource(R.string.settings_dns_upstream),
                    subtitle = dnsSummary,
                    icon = AppIcons.Dns,
                    onClick = onOpenDns,
                    trailing = { Chevron() },
                )
                AppSettingId.ClashHelp -> SettingRow(
                    title = stringResource(R.string.settings_clash_help),
                    subtitle = clashSubtitle,
                    icon = AppIcons.Description,
                    onClick = onOpenClashHelp,
                    trailing = { Chevron() },
                )
                AppSettingId.AutoStart -> SwitchRow(
                    title = stringResource(R.string.settings_start_on_boot),
                    subtitle = bootSubtitle,
                    icon = AppIcons.PowerSettingsNew,
                    checked = s.autoStartOnBoot,
                    onCheckedChange = { vm.setAutoStart(context, it) },
                )
                AppSettingId.Theme -> ChoiceRow(
                    title = stringResource(R.string.settings_theme),
                    value = themeLabel,
                    icon = AppIcons.DarkMode,
                    onClick = { showThemeDialog = true },
                )
                AppSettingId.CheckUpdate -> SettingRow(
                    title = stringResource(R.string.settings_check_update),
                    icon = AppIcons.Download,
                    onClick = { vm.checkUpdate() },
                    trailing = {
                        if (checkState is UpdateCheckResult.Checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Chevron()
                        }
                    },
                )
                AppSettingId.AutoCheckInterval -> ChoiceRow(
                    title = stringResource(R.string.settings_auto_check),
                    value = updateIntervalLabel,
                    icon = AppIcons.Schedule,
                    onClick = { showUpdateIntervalDialog = true },
                )
                AppSettingId.ProjectHome -> SettingRow(
                    title = stringResource(R.string.settings_project_home),
                    icon = AppIcons.Language,
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/xihale/easytier-android"),
                            ),
                        )
                    },
                    trailing = {
                        Icon(
                            AppIcons.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
                AppSettingId.Licenses -> SettingRow(
                    title = stringResource(R.string.settings_licenses),
                    icon = AppIcons.Description,
                    onClick = { showLicenseDialog = true },
                    trailing = { Chevron() },
                )
            }
        }

        if (isSearching && filteredMetas.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.settings_search_empty_title),
                    hint = stringResource(R.string.settings_search_empty_hint),
                )
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                if (isSearching) {
                    val grouped = filteredMetas.groupBy { it.section }
                    grouped.forEach { (section, items) ->
                        SectionHeader(
                            title = stringResource(section.titleRes),
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        AppCard {
                            Column(Modifier.padding(vertical = 4.dp)) {
                                items.forEachIndexed { index, meta ->
                                    if (index > 0) CardDivider()
                                    RenderSettingRow(meta.id)
                                }
                            }
                        }
                    }
                } else {
                    SectionHeader(
                        title = stringResource(R.string.settings_section_connection),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    AppCard {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            RenderSettingRow(AppSettingId.VpnMode)
                            CardDivider()
                            RenderSettingRow(AppSettingId.Socks5Toggle)
                            AnimatedVisibility(
                                visible = s.enableSocks5,
                                enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                                exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
                            ) {
                                Column {
                                    CardDivider()
                                    RenderSettingRow(AppSettingId.Socks5Port)
                                }
                            }
                            CardDivider()
                            RenderSettingRow(AppSettingId.DnsUpstream)
                            CardDivider()
                            RenderSettingRow(AppSettingId.ClashHelp)
                        }
                    }

                    SectionHeader(
                        title = stringResource(R.string.settings_section_general),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    AppCard {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            RenderSettingRow(AppSettingId.AutoStart)
                            CardDivider()
                            RenderSettingRow(AppSettingId.Theme)
                        }
                    }

                    SectionHeader(
                        title = stringResource(R.string.settings_section_about),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    AppCard {
                        Column {
                            AboutHeader(s.themeMode)
                            CardDivider()
                            Column(Modifier.padding(vertical = 4.dp)) {
                                AnimatedVisibility(
                                    visible = s.pendingUpdateVersion.isNotEmpty(),
                                    enter = expandVertically(tween(220, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                                    exit = shrinkVertically(tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(120)),
                                ) {
                                    Column {
                                        SettingRow(
                                            title = stringResource(R.string.update_new_version_title, s.pendingUpdateVersion),
                                            icon = AppIcons.Download,
                                            onClick = {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse(s.pendingUpdateUrl)),
                                                )
                                            },
                                            trailing = {
                                                PillBadge(
                                                    text = stringResource(R.string.settings_new_badge),
                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                    labelColor = MaterialTheme.colorScheme.onErrorContainer,
                                                )
                                            },
                                        )
                                        CardDivider()
                                    }
                                }
                                RenderSettingRow(AppSettingId.CheckUpdate)
                                CardDivider()
                                RenderSettingRow(AppSettingId.AutoCheckInterval)
                                CardDivider()
                                RenderSettingRow(AppSettingId.ProjectHome)
                                CardDivider()
                                RenderSettingRow(AppSettingId.Licenses)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }

    if (showThemeDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_theme),
            options = THEME_OPTIONS,
            selected = s.themeMode,
            onSelect = { vm.setTheme(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false },
        )
    }
    if (showLicenseDialog) {
        OssLicensesDialog(onDismiss = { showLicenseDialog = false })
    }
    if (showBatteryDialog) {
        AlertDialog(
            onDismissRequest = { showBatteryDialog = false },
            title = { Text(stringResource(R.string.settings_battery_title)) },
            text = { Text(stringResource(R.string.settings_battery_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showBatteryDialog = false
                    val intent = BackgroundPermission.requestIgnoreIntent(context)
                        ?: BackgroundPermission.appDetailsIntent(context)
                    batteryLauncher.launch(intent)
                }) { Text(stringResource(R.string.common_turn_on)) }
            },
            dismissButton = {
                TextButton(onClick = { showBatteryDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
    if (showSocks5Dialog) {
        TextInputDialog(
            title = stringResource(R.string.settings_socks5_port),
            initialValue = s.socks5Port.toString(),
            label = stringResource(R.string.settings_port_dialog_label),
            keyboardType = KeyboardType.Number,
            monospace = true,
            validator = { raw ->
                val port = raw.toIntOrNull()
                if (port == null || port !in 1024..65535) {
                    context.getString(R.string.settings_port_invalid, 1024, 65535)
                } else {
                    null
                }
            },
            onDismiss = { showSocks5Dialog = false },
            onConfirm = { text ->
                text.toIntOrNull()?.takeIf { it in 1024..65535 }?.let { port ->
                    vm.setSocks5(s.enableSocks5, port)
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        vm.updateEvents.collect { event ->
            when (event) {
                SettingsViewModel.UpdateUiEvent.UpToDate ->
                    snackbar.showSnackbar(context.getString(R.string.settings_uptodate))
                SettingsViewModel.UpdateUiEvent.Failed ->
                    snackbar.showSnackbar(context.getString(R.string.settings_check_failed))
                is SettingsViewModel.UpdateUiEvent.Newer -> updateDialogInfo = event.info
            }
        }
    }
    LaunchedEffect(Unit) {
        EasyTierApp.get().container.vpnController.serviceErrors.collect { message ->
            snackbar.showSnackbar(message)
        }
    }
    if (showUpdateIntervalDialog) {
        ChoiceDialog(
            title = stringResource(R.string.settings_auto_check_dialog_title),
            options = UPDATE_INTERVAL_OPTIONS,
            selected = s.updateCheckInterval,
            onSelect = { vm.setUpdateInterval(it); showUpdateIntervalDialog = false },
            onDismiss = { showUpdateIntervalDialog = false },
        )
    }
    updateDialogInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { updateDialogInfo = null },
            title = { Text(stringResource(R.string.update_new_version_title, info.version)) },
            text = { Text(info.notes?.ifEmpty { null } ?: stringResource(R.string.update_new_version_body)) },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                    updateDialogInfo = null
                }) { Text(stringResource(R.string.update_go)) }
            },
            dismissButton = {
                TextButton(onClick = { updateDialogInfo = null }) { Text(stringResource(R.string.update_later)) }
            },
        )
    }
}

@Composable
private fun AboutHeader(themeMode: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val isDarkTheme = when (themeMode) {
            "light" -> false
            "dark" -> true
            else -> isSystemInDarkTheme()
        }
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDarkTheme) Color(0xFF131313) else Color(0xFFFAFAFC)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = stringResource(R.string.common_logo_cd),
                modifier = Modifier.size(100.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "EasyTier",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(8.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.about_version,
                    com.easytier.android.BuildConfig.VERSION_NAME,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.about_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, Int>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, labelRes) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = key == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(key) },
                            )
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = key == selected, onClick = null)
                        Text(stringResource(labelRes))
                    }
                }
            }
        },
        confirmButton = {},
    )
}
