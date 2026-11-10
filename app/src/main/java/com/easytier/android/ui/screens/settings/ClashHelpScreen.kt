package com.easytier.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.EasyTierApp
import com.easytier.android.R
import com.easytier.android.core.clash.ClashRouteGenerator
import com.easytier.android.core.engine.InstanceState
import com.easytier.android.data.model.NetworkConfig
import com.easytier.android.data.model.NetworkingMethod
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.AppSnackbarHost
import com.easytier.android.ui.components.PillBadge
import com.easytier.android.ui.components.SectionHeader
import com.easytier.android.ui.icons.AppIcons
import com.easytier.android.util.BackgroundPermission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Clash 对接帮助页：
 * 1. 导入路由片段（proxies + rules）
 * 2. 防止流量回环（分应用排除 / 底层端点直连）
 * 3. 后台运行权限（仅引擎 / SOCKS5 模式常驻）
 * 4. 常见问题排查
 */
@Composable
fun ClashHelpScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    // 电池优化白名单状态：进入页面与「去开启」返回后刷新
    var batteryWhitelisted by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        batteryWhitelisted = BackgroundPermission.isIgnoringBatteryOptimizations(context)
    }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        batteryWhitelisted = BackgroundPermission.isIgnoringBatteryOptimizations(context)
    }

    // 路由片段与底层端点 DIRECT 规则（随 SOCKS5 端口 / 网络配置刷新）
    var snippet by remember { mutableStateOf("") }
    var directRules by remember { mutableStateOf("") }
    LaunchedEffect(settings?.socks5Port) {
        val port = settings?.socks5Port ?: return@LaunchedEffect
        val container = EasyTierApp.get().container
        val running = container.engine.states.value.values
            .mapNotNull { it as? InstanceState.Running }
            .map { it.info }
        val saved = container.networksRepository.networks.first().map { it.config }
        val cidrs = ClashRouteGenerator.collectCidrs(running, saved)
        val zones = ClashRouteGenerator.collectDnsZones(saved)
        snippet = ClashRouteGenerator.generate(port, cidrs, zones)
        directRules = buildDirectRules(saved)
    }

    fun copy(text: String, message: String) {
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbar.showSnackbar(message) }
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
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                }
                Text(
                    stringResource(R.string.clash_help_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // --- ① 导入路由片段 ---
            SectionHeader(stringResource(R.string.clash_help_section_import))
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpText(stringResource(R.string.clash_help_import_intro))
                    HelpStep("1", stringResource(R.string.clash_help_import_step1))
                    HelpStep("2", stringResource(R.string.clash_help_import_step2))
                    HelpStep("3", stringResource(R.string.clash_help_import_step3))
                    HelpStep("4", stringResource(R.string.clash_help_import_step4))
                    if (snippet.isNotEmpty()) {
                        SnippetBlock(snippet) {
                            copy(snippet, context.getString(R.string.clash_help_copied_snippet))
                        }
                    }
                }
            }

            // --- ② 防止流量回环 ---
            SectionHeader(stringResource(R.string.clash_help_section_loop))
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpText(stringResource(R.string.clash_help_loop_intro))
                    HelpStep(
                        stringResource(R.string.clash_help_loop_plan_a_label),
                        stringResource(R.string.clash_help_loop_plan_a),
                    )
                    HelpStep(
                        stringResource(R.string.clash_help_loop_plan_b_label),
                        stringResource(R.string.clash_help_loop_plan_b),
                    )
                    if (directRules.isNotEmpty()) {
                        SnippetBlock(directRules) {
                            copy(directRules, context.getString(R.string.clash_help_copied_rules))
                        }
                    }
                    HelpText(stringResource(R.string.clash_help_loop_note))
                }
            }

            // --- ③ 后台运行权限 ---
            SectionHeader(stringResource(R.string.clash_help_section_background))
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpText(stringResource(R.string.clash_help_bg_intro))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.clash_help_battery_title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(
                                    if (batteryWhitelisted) R.string.clash_help_battery_ok
                                    else R.string.clash_help_battery_missing,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (batteryWhitelisted) {
                            PillBadge(
                                text = stringResource(R.string.clash_help_battery_badge),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else {
                            Button(onClick = {
                                val intent = BackgroundPermission.requestIgnoreIntent(context)
                                    ?: BackgroundPermission.appDetailsIntent(context)
                                batteryLauncher.launch(intent)
                            }) { Text(stringResource(R.string.common_turn_on)) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.clash_help_autostart_title),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(R.string.clash_help_autostart_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(onClick = {
                            context.startActivity(BackgroundPermission.appDetailsIntent(context))
                        }) { Text(stringResource(R.string.clash_help_app_info)) }
                    }
                    HelpText(stringResource(R.string.clash_help_bg_note))
                }
            }

            // --- ④ 常见问题排查 ---
            SectionHeader(stringResource(R.string.clash_help_section_faq))
            AppCard {
                Column(Modifier.padding(vertical = 8.dp)) {
                    TroubleshootItem(
                        stringResource(R.string.clash_help_faq_deadline_title),
                        stringResource(R.string.clash_help_faq_deadline_body),
                    )
                    TroubleshootItem(
                        stringResource(R.string.clash_help_faq_refused_title),
                        stringResource(R.string.clash_help_faq_refused_body),
                    )
                    TroubleshootItem(
                        stringResource(R.string.clash_help_faq_vpn_lost_title),
                        stringResource(R.string.clash_help_faq_vpn_lost_body),
                    )
                    TroubleshootItem(
                        stringResource(R.string.clash_help_faq_unreachable_title),
                        stringResource(R.string.clash_help_faq_unreachable_body),
                    )
                }
            }

            Spacer(Modifier.size(32.dp))
        }
    }
}

/** 从已保存网络配置提取全部底层端点 host，生成 DIRECT 规则（IP → IP-CIDR，域名 → DOMAIN）。 */
private fun buildDirectRules(saved: List<NetworkConfig>): String {
    val hosts = sortedSetOf<String>()
    saved.forEach { config ->
        val urls = buildList {
            when (config.networkingMethod) {
                NetworkingMethod.Manual -> addAll(config.peerUrls)
                NetworkingMethod.PublicServer -> config.publicServerUrl?.let { add(it) }
                NetworkingMethod.Standalone -> Unit
            }
        }
        urls.forEach { url -> extractHost(url)?.let { hosts.add(it) } }
    }
    if (hosts.isEmpty()) return ""
    return hosts.joinToString("\n") { host ->
        if (ClashRouteGenerator.parseIpv4(host) != null) {
            "IP-CIDR,$host/32,DIRECT,no-resolve"
        } else {
            "DOMAIN,$host,DIRECT"
        }
    }
}

/** 从 scheme://host:port/path 提取 host；跳过通配监听地址与 IPv6 字面量。 */
private fun extractHost(url: String): String? = runCatching {
    val raw = url.trim().substringBefore('#')
    val hostPort = raw.substringAfter("://", raw).substringBefore('/')
    val host = hostPort.substringBeforeLast(':')
    host.takeIf { it.isNotEmpty() && it != "0.0.0.0" && !it.contains(':') }
}.getOrNull()

@Composable
private fun HelpText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun HelpStep(label: String, text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SnippetBlock(text: String, onCopy: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            SelectionContainer {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCopy) {
                    Icon(AppIcons.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.common_copy))
                }
            }
        }
    }
}

@Composable
private fun TroubleshootItem(title: String, body: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
