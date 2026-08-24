package com.easytier.android.ui.screens.settings

import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.easytier.android.ui.components.ChoiceRow
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

    fun setAutoStart(context: android.content.Context, enabled: Boolean) =
        launchIO {
            repo.setAutoStart(enabled)
            // 同步启用/禁用开机广播接收器组件，否则开关只是存了个数字
            BootCompletedReceiver.setEnabled(context, enabled)
        }


    fun setSocks5(enabled: Boolean, port: Int) = launchIO { repo.setSocks5(enabled, port) }

    fun setVpnEnabled(enabled: Boolean) = launchIO { repo.setVpnEnabled(enabled) }

    private fun launchIO(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

private val THEME_OPTIONS = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色")

@Composable
fun SettingsScreen() {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showThemeDialog by remember { mutableStateOf(false) }
    var showSocks5Dialog by remember { mutableStateOf(false) }

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

            // 「关于」分组卡片：图标去重 Info / Shield / Language
            SectionHeader("关于")
            AppCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    // 关于组：每项只留一行，版本号放行尾，不加副标题
                    SettingRow(
                        title = "版本",
                        icon = Icons.Filled.Info,
                        trailing = {
                            Text(
                                com.easytier.android.BuildConfig.VERSION_NAME,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
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
                                    android.net.Uri.parse("https://github.com/EasyTier/EasyTier"),
                                ),
                            )
                        },
                        trailing = { Chevron() },
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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

