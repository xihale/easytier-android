package com.easytier.android.ui.screens.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import com.easytier.android.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.EasyTierApp
import com.easytier.android.core.clash.ClashRouteGenerator
import com.easytier.android.core.engine.InstanceState
import com.easytier.android.core.vpn.BootCompletedReceiver
import com.easytier.android.data.store.AppSettings
import com.easytier.android.data.store.SettingsRepository
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.AppSnackbarHost
import com.easytier.android.ui.components.ChoiceRow
import com.easytier.android.ui.components.OssLicensesDialog
import com.easytier.android.ui.components.SettingRow
import com.easytier.android.ui.components.SwitchRow
import android.net.Uri
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.easytier.android.data.update.ReleaseInfo
import com.easytier.android.data.update.UpdateCheckResult
import com.easytier.android.ui.components.PillBadge
import com.easytier.android.ui.icons.AppIcons
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
class SettingsViewModel : ViewModel() {

    private val repo: SettingsRepository =
        EasyTierApp.get().container.settingsRepository

    val settings: StateFlow<AppSettings?> =
        repo.settings.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )

    /** 引擎运行状态：复制 Clash 路由时用于收集运行中实例的虚拟网段。 */
    val engineStates = EasyTierApp.get().container.engine.states

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

// 自动检测频率选项（默认关闭）。更新检测从 GitHub Releases 检查；标签为字符串资源（i18n）
private val UPDATE_INTERVAL_OPTIONS = listOf(
    "off" to R.string.update_freq_off,
    "startup" to R.string.update_freq_startup,
    "daily" to R.string.update_freq_daily,
    "weekly" to R.string.update_freq_weekly,
)

@Composable
fun SettingsScreen(onOpenDns: () -> Unit = {}) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var showThemeDialog by remember { mutableStateOf(false) }
    var showSocks5Dialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showUpdateIntervalDialog by remember { mutableStateOf(false) }
    // 发现新版本弹窗的内容（一次性事件驱动，非页面间常驻状态）
    var updateDialogInfo by remember { mutableStateOf<ReleaseInfo?>(null) }
    // 检查按钮的即时状态（用于显示检查中菊叶）
    val checkState by vm.updateCheckResult.collectAsState()

    val s = settings ?: run {
        // 首帧设置未加载时给加载指示，避免整页空白
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        ) {
            // 设置卡片：SettingRow 行内已带 horizontal 16dp，卡内 Column 只补 vertical 4dp，
            // 避免双倍横向缩进。原「应用层」「外观」小标题已去掉，卡片即分组。
            AppCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SwitchRow(
                        title = stringResource(R.string.settings_vpn_mode),
                        icon = AppIcons.Shield,
                        checked = s.enableVpn,
                        onCheckedChange = { vm.setVpnEnabled(it) },
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_socks5),
                        icon = AppIcons.Language,
                        checked = s.enableSocks5,
                        onCheckedChange = { vm.setSocks5(it, s.socks5Port) },
                    )
                    if (s.enableSocks5) {
                        SettingRow(
                            title = stringResource(R.string.settings_socks5_port),
                            icon = AppIcons.Terminal,
                            onClick = { showSocks5Dialog = true },
                            trailing = {
                                Text(
                                    s.socks5Port.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_copy_clash),
                            icon = AppIcons.ContentCopy,
                            onClick = {
                                scope.launch {
                                    val running = vm.engineStates.value.values
                                        .mapNotNull { it as? InstanceState.Running }
                                        .map { it.info }
                                    val saved = EasyTierApp.get().container.networksRepository
                                        .networks.first().map { it.config }
                                    val cidrs = ClashRouteGenerator.collectCidrs(running, saved)
                                    val zones = ClashRouteGenerator.collectDnsZones(saved)
                                    clipboard.setText(
                                        AnnotatedString(
                                            ClashRouteGenerator.generate(s.socks5Port, cidrs, zones),
                                        ),
                                    )
                                    val summary = buildList {
                                        if (zones.isNotEmpty()) {
                                            add(context.getString(R.string.clash_summary_zones, zones.size))
                                        }
                                        add(
                                            if (cidrs.isEmpty()) {
                                                context.getString(R.string.clash_summary_default)
                                            } else {
                                                context.getString(R.string.clash_summary_cidrs, cidrs.size)
                                            },
                                        )
                                    }.joinToString(context.getString(R.string.common_list_separator))
                                    snackbar.showSnackbar(context.getString(R.string.clash_copied, summary))
                                }
                            },
                            trailing = { Chevron() },
                        )
                    }
                    SwitchRow(
                        title = stringResource(R.string.settings_start_on_boot),
                        icon = AppIcons.Terminal,
                        checked = s.autoStartOnBoot,
                        onCheckedChange = { vm.setAutoStart(context, it) },
                    )
                    SettingRow(
                        title = stringResource(R.string.settings_dns_upstream),
                        icon = AppIcons.Dns,
                        onClick = onOpenDns,
                        trailing = { Chevron() },
                    )
                    ChoiceRow(
                        title = stringResource(R.string.settings_theme),
                        value = THEME_OPTIONS.firstOrNull { it.first == s.themeMode }
                            ?.let { stringResource(it.second) } ?: s.themeMode,
                        icon = AppIcons.DarkMode,
                        onClick = { showThemeDialog = true },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 关于卡片（无小标题）
            AppCard {
                Column {
                    // 头部：大应用图标、名称、版本胶囊、简述
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Logo 随应用内深浅色主题切换底色，字形复用桌面自适应图标的前景并放大铺满，
                        // 避免整图 logo（背景自带大留白）在应用内显得字形过小
                        val isDarkTheme = when (s.themeMode) {
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

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )

                    Column(Modifier.padding(vertical = 4.dp)) {
                        // 待查看的新版本，突出展示
                        if (s.pendingUpdateVersion.isNotEmpty()) {
                            SettingRow(
                                title = stringResource(R.string.update_new_version_title, s.pendingUpdateVersion),
                                icon = AppIcons.Download,
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(s.pendingUpdateUrl))
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
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            )
                        }
                        SettingRow(
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
                        ChoiceRow(
                            title = stringResource(R.string.settings_auto_check),
                            value = UPDATE_INTERVAL_OPTIONS
                                .firstOrNull { it.first == s.updateCheckInterval }
                                ?.let { stringResource(it.second) }
                                ?: stringResource(R.string.update_freq_off),
                            icon = AppIcons.Schedule,
                            onClick = { showUpdateIntervalDialog = true },
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_project_home),
                            icon = AppIcons.Language,
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://github.com/xihale/easytier-android"),
                                    ),
                                )
                            },
                            trailing = { Chevron() },
                        )
                        SettingRow(
                            title = stringResource(R.string.settings_licenses),
                            icon = AppIcons.Description,
                            onClick = { showLicenseDialog = true },
                            trailing = { Chevron() },
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
        AppSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
    if (showSocks5Dialog) {
        com.easytier.android.ui.components.TextInputDialog(
            title = stringResource(R.string.settings_socks5_port),
            initialValue = s.socks5Port.toString(),
            label = stringResource(R.string.settings_port_dialog_label),
            onDismiss = { showSocks5Dialog = false },
            onConfirm = { text ->
                // 非数字/越界不落库（此前会被静默吞掉或任意数字直接生效）
                val port = text.toIntOrNull()
                if (port != null && port in 1024..65535) {
                    vm.setSocks5(s.enableSocks5, port)
                } else {
                    scope.launch {
                        snackbar.showSnackbar(
                            context.getString(R.string.settings_port_invalid, 1024, 65535),
                        )
                    }
                }
                showSocks5Dialog = false
            },
        )
    }

    // 检查更新结果 → 一次性事件：只在检查发生时提示一次，进出子页/切页不会重放
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
                    // 整行可点（带 RadioButton 语义）；选中即关闭，无需确认按钮
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
