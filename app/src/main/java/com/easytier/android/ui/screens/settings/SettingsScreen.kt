package com.easytier.android.ui.screens.settings

import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.EasyTierApp
import com.easytier.android.core.vpn.BootCompletedReceiver
import com.easytier.android.data.store.AppSettings
import com.easytier.android.data.store.SettingsRepository
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.AppSnackbarHost
import com.easytier.android.ui.components.ChoiceRow
import com.easytier.android.ui.components.OssLicensesDialog
import com.easytier.android.ui.components.SectionHeader
import com.easytier.android.ui.components.SettingRow
import com.easytier.android.ui.components.SwitchRow
import android.net.Uri
import com.easytier.android.data.update.UpdateCheckResult
import com.easytier.android.ui.components.PillBadge
import com.easytier.android.ui.icons.AppIcons
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class SettingsViewModel : ViewModel() {

    private val repo: SettingsRepository =
        EasyTierApp.get().container.settingsRepository

    val settings: StateFlow<AppSettings?> =
        repo.settings.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            null,
        )

    fun setTheme(mode: String) = launchIO { repo.setThemeMode(mode) }

    fun setAutoStart(context: android.content.Context, enabled: Boolean) =
        launchIO {
            repo.setAutoStart(enabled)
            // 同步启用/禁用开机广播接收器组件，否则开关只是存了个数字
            BootCompletedReceiver.setEnabled(context, enabled)
        }


    fun setSocks5(enabled: Boolean, port: Int) = launchIO { repo.setSocks5(enabled, port) }

    fun setVpnEnabled(enabled: Boolean) = launchIO { repo.setVpnEnabled(enabled) }

    fun setUpdateInterval(mode: String) = launchIO { repo.setUpdateInterval(mode) }

    private val _updateCheckResult = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.Idle)
    val updateCheckResult: StateFlow<UpdateCheckResult> = _updateCheckResult.asStateFlow()

    // 手动「检查更新」：在 UI 显示检查中→结果，同时落库（用于自动检查触发与关于页显示）
    fun checkUpdate() = viewModelScope.launch {
        _updateCheckResult.value = UpdateCheckResult.Checking
        val result = EasyTierApp.get().container.updateChecker.checkNow()
        _updateCheckResult.value = result
        val now = System.currentTimeMillis()
        val settingsRepo = EasyTierApp.get().container.settingsRepository
        settingsRepo.setLastUpdateCheckAt(now)
        when (result) {
            is UpdateCheckResult.Newer -> settingsRepo.setPendingUpdate(result.info)
            is UpdateCheckResult.UpToDate -> settingsRepo.clearPendingUpdate()
            UpdateCheckResult.Checking, UpdateCheckResult.Idle, UpdateCheckResult.Error -> Unit
        }
    }

    private fun launchIO(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

private val THEME_OPTIONS = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")

// 自动检测频率选项（默认关闭）。更新检测从 GitHub Releases 检查
private val UPDATE_INTERVAL_OPTIONS = listOf(
    "off" to "关闭", "startup" to "每次启动", "daily" to "每天", "weekly" to "每周",
)

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showSocks5Dialog by remember { mutableStateOf(false) }
    var showLicenseDialog by remember { mutableStateOf(false) }
    var showUpdateIntervalDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    // 检查按钮的即时状态（用于显示检查中菊叶与发现新版本是否该弹窗）
    val checkState by vm.updateCheckResult.collectAsState()

    // VPN 权限申请：未授权时弹系统授权框；已授权也提示用户当前状态
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        scope.launch {
            snackbar.showSnackbar(
                if (result.resultCode == android.app.Activity.RESULT_OK) "VPN 权限已授予"
                else "未授予 VPN 权限",
            )
        }
    }
    val requestVpnPermission: () -> Unit = {
        val intent = VpnService.prepare(context)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            scope.launch { snackbar.showSnackbar("VPN 权限已授予") }
        }
    }

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
                .padding(start = 16.dp, end = 16.dp, top = 4.dp),
        ) {
            // 「应用层」分组卡片：SettingRow 行内已带 horizontal 16dp，
            // 卡内 Column 只补 vertical 4dp，避免双倍横向缩进
            SectionHeader("应用层")
            AppCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SwitchRow(
                        title = "启动 VPN",
                        subtitle = "关闭后不创建系统 VPN（TUN），仅运行引擎，可通过 SOCKS5 代理访问虚拟网络",
                        icon = AppIcons.Shield,
                        checked = s.enableVpn,
                        onCheckedChange = { vm.setVpnEnabled(it) },
                    )
                    SwitchRow(
                        title = "SOCKS5 代理",
                        subtitle = "在本机开启 SOCKS5 服务，作为访问虚拟网络的入口",
                        icon = AppIcons.Language,
                        checked = s.enableSocks5,
                        onCheckedChange = { vm.setSocks5(it, s.socks5Port) },
                    )
                    if (s.enableSocks5) {
                        SettingRow(
                            title = "SOCKS5 端口",
                            subtitle = "重启网络后生效",
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
                    }
                    
                    SwitchRow(
                        title = "开机自启",
                        subtitle = "设备开机后自动启动服务，拉起全部勾选的网络",
                        icon = AppIcons.Terminal,
                        checked = s.autoStartOnBoot,
                        onCheckedChange = { vm.setAutoStart(context, it) },
                    )
                }
            }

            // 「外观」分组卡片：组间距由 SectionHeader 自带 top 20dp 提供，不再手动 Spacer
            SectionHeader("外观")
            AppCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    ChoiceRow(
                        title = "主题",
                        value = THEME_OPTIONS.firstOrNull { it.first == s.themeMode }?.second ?: s.themeMode,
                        icon = AppIcons.DarkMode,
                        onClick = { showThemeDialog = true },
                    )
                }
            }

            // 「关于」分组卡片
            SectionHeader("关于")
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
                                contentDescription = "EasyTier Logo",
                                modifier = Modifier.size(90.dp),
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
                                text = "v${com.easytier.android.BuildConfig.VERSION_NAME} (Native)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "去中心化 P2P 虚拟网状网络客户端",
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
                                title = "发现新版本 v${s.pendingUpdateVersion}",
                                subtitle = "点击前往下载",
                                icon = AppIcons.Download,
                                onClick = {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(s.pendingUpdateUrl))
                                    )
                                },
                                trailing = {
                                    PillBadge(
                                        text = "新",
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
                            title = "检查更新",
                            subtitle = UpdateCheckSubtitle(checkState, s),
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
                            title = "自动检查",
                            value = UPDATE_INTERVAL_OPTIONS
                                .firstOrNull { it.first == s.updateCheckInterval }?.second ?: "关闭",
                            icon = AppIcons.Schedule,
                            onClick = { showUpdateIntervalDialog = true },
                        )
                        SettingRow(
                            title = "VPN 权限",
                            icon = AppIcons.Shield,
                            onClick = requestVpnPermission,
                            trailing = { Chevron() },
                        )
                        SettingRow(
                            title = "项目主页",
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
                            title = "开源许可",
                            subtitle = "本应用及内置组件的许可证",
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
            title = "主题",
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
            title = "SOCKS5 端口",
            initialValue = s.socks5Port.toString(),
            label = "端口（1024 - 65535）",
            onDismiss = { showSocks5Dialog = false },
            onConfirm = { text ->
                // 非数字/越界不落库（此前会被静默吞掉或任意数字直接生效）
                val port = text.toIntOrNull()
                if (port != null && port in 1024..65535) {
                    vm.setSocks5(s.enableSocks5, port)
                } else {
                    scope.launch { snackbar.showSnackbar("端口需为 1024 - 65535 之间的数字") }
                }
                showSocks5Dialog = false
            },
        )
    }

    // 检查更新结果 → 轻提示；发现新版本 → 弹窗
    LaunchedEffect(checkState) {
        when (checkState) {
            is UpdateCheckResult.UpToDate -> snackbar.showSnackbar("已是最新版本")
            is UpdateCheckResult.Error -> snackbar.showSnackbar("检查更新失败，请检查网络")
            is UpdateCheckResult.Newer -> showUpdateDialog = true
            else -> Unit
        }
    }
    if (showUpdateIntervalDialog) {
        ChoiceDialog(
            title = "自动检查更新",
            options = UPDATE_INTERVAL_OPTIONS,
            selected = s.updateCheckInterval,
            onSelect = { vm.setUpdateInterval(it); showUpdateIntervalDialog = false },
            onDismiss = { showUpdateIntervalDialog = false },
        )
    }
    if (showUpdateDialog && checkState is UpdateCheckResult.Newer) {
        val info = (checkState as UpdateCheckResult.Newer).info
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            title = { Text("发现新版本 v${info.version}") },
            text = { Text(info.notes?.ifEmpty { null } ?: "点击前往发布页下载更新。") } ,
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                    showUpdateDialog = false
                }) { Text("去更新") }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) { Text("以后再说") }
            },
        )
    }
}

@Composable
private fun UpdateCheckSubtitle(state: UpdateCheckResult, s: AppSettings): String {
    if (state is UpdateCheckResult.Checking) return "检查中…"
    if (s.lastUpdateCheckAt == 0L) return "从未检查"
    val whenStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        .format(Date(s.lastUpdateCheckAt))
    return if (s.pendingUpdateVersion.isNotEmpty())
        "发现 v${s.pendingUpdateVersion} · 上次 ${whenStr}"
    else "上次检查 ${whenStr}"
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
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, label) ->
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
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
    )
}

