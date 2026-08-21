package com.easytier.android.ui.screens.settings

import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.EasyTierApp
import com.easytier.android.data.store.AppSettings
import com.easytier.android.data.store.SettingsRepository
import com.easytier.android.ui.components.ChoiceRow
import com.easytier.android.ui.components.ScreenHeader
import com.easytier.android.ui.components.SectionHeader
import com.easytier.android.ui.components.SettingRow
import com.easytier.android.ui.components.SwitchRow
import com.easytier.android.ui.icons.AppIcons
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun setTheme(mode: String) = launchIO { repo.setThemeMode(mode) }
    fun setLanguage(lang: String) = launchIO { repo.setLanguage(lang) }
    fun setLogLevel(level: String) = launchIO { repo.setLogLevel(level) }
    fun setAutoStart(enabled: Boolean) =
        launchIO {
            val current = settings.value
            repo.setAutoStart(enabled, current?.autoStartNetworkId)
        }

    fun setVpnWithNetwork(enabled: Boolean) = launchIO { repo.setVpnWithNetwork(enabled) }

    private fun launchIO(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

private val THEME_OPTIONS = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")
private val LANGUAGE_OPTIONS =
    listOf("system" to "跟随系统", "zh" to "简体中文", "en" to "English")
private val LOG_LEVEL_OPTIONS =
    listOf("trace" to "Trace", "debug" to "Debug", "info" to "Info", "warn" to "Warn", "error" to "Error")

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showLangDialog by remember { mutableStateOf(false) }
    var showLogDialog by remember { mutableStateOf(false) }

    // VPN 权限申请（prepare 返回非 null 时发起系统授权）
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { }
    val requestVpnPermission: () -> Unit = {
        val intent = VpnService.prepare(context)
        if (intent != null) vpnPermissionLauncher.launch(intent)
    }

    val s = settings ?: return

    // 底部导航的顶层页：统一大标题头，无返回箭头（返回交给系统手势/底部导航）
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("设置")
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SectionHeader("连接")
            SwitchRow(
                title = "VPN 模式",
                subtitle = "使用系统 VPN（TUN）接管全部流量",
                icon = AppIcons.Shield,
                checked = s.startVpnWithNetwork,
                onCheckedChange = { vm.setVpnWithNetwork(it) },
            )
            SwitchRow(
                title = "开机自启",
                subtitle = "设备开机后自动启动选定的网络",
                icon = AppIcons.Terminal,
                checked = s.autoStartOnBoot,
                onCheckedChange = { vm.setAutoStart(it) },
            )

            Spacer(Modifier.height(12.dp))
            SectionHeader("日志")
            ChoiceRow(
                title = "日志级别",
                value = s.logLevel,
                icon = AppIcons.Terminal,
                onClick = { showLogDialog = true },
            )

            Spacer(Modifier.height(12.dp))
            SectionHeader("外观")
            ChoiceRow(
                title = "主题",
                value = THEME_OPTIONS.firstOrNull { it.first == s.themeMode }?.second ?: s.themeMode,
                icon = AppIcons.DarkMode,
                onClick = { showThemeDialog = true },
            )
            ChoiceRow(
                title = "语言",
                value = LANGUAGE_OPTIONS.firstOrNull { it.first == s.language }?.second ?: s.language,
                icon = AppIcons.Language,
                onClick = { showLangDialog = true },
            )

            Spacer(Modifier.height(12.dp))
            SectionHeader("关于")
            SettingRow(
                title = "版本",
                subtitle = com.easytier.android.BuildConfig.VERSION_NAME,
                icon = Icons.Filled.Info,
            )
            SettingRow(
                title = "VPN 权限",
                subtitle = "检查 / 授予 Always-On VPN 权限",
                icon = AppIcons.Shield,
                onClick = requestVpnPermission,
            )
            SettingRow(
                title = "开源许可",
                subtitle = "EasyTier 核心（Rust）与开源组件",
                icon = Icons.Filled.Info,
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://github.com/EasyTier/EasyTier"),
                        ),
                    )
                },
            )
            Spacer(Modifier.height(32.dp))
        }
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
    if (showLangDialog) {
        ChoiceDialog(
            title = "语言",
            options = LANGUAGE_OPTIONS,
            selected = s.language,
            onSelect = { vm.setLanguage(it); showLangDialog = false },
            onDismiss = { showLangDialog = false },
        )
    }
    if (showLogDialog) {
        ChoiceDialog(
            title = "日志级别",
            options = LOG_LEVEL_OPTIONS,
            selected = s.logLevel,
            onSelect = { vm.setLogLevel(it); showLogDialog = false },
            onDismiss = { showLogDialog = false },
        )
    }
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
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RadioButton(selected = key == selected, onClick = { onSelect(key) })
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("确定") }
        },
    )
}
