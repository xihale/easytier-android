package com.easytier.android.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.easytier.android.ui.components.SettingRow
import com.easytier.android.ui.icons.AppIcons
import com.easytier.android.ui.theme.LocalStatusColors
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.easytier.android.util.BackgroundPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// 终端风格深色代码面板色板（与 TomlIoDialogs 保持一致）
private val CodeBg = Color(0xFF171A22)
private val CodeHeaderBg = Color(0xFF1E222E)
private val CodeFg = Color(0xFFD5D9E8)
private val CodeMuted = Color(0xFF8D92A8)
private val CodeLine = Color(0xFF323748)
private val CodeIndigo = Color(0xFFB7C3FF)
private val CodeEmerald = Color(0xFF80D9C7)
private val CodeAmber = Color(0xFFFBBF24)

private data class ClashInputs(
    val socks5Enabled: Boolean,
    val socks5Port: Int,
    val cidrs: List<String>,
    val zones: List<String>,
    val hasRunningInstance: Boolean,
    val savedConfigsSummary: String = "",
    val directRules: String = "",
)

/**
 * Clash 对接页：
 * 1. 路由覆写（实时生成 YAML + 终端高亮代码面板）
 * 2. 防止回环（分应用排除 / 端点直连规则）
 * 3. 后台常驻（电池优化白名单 + 自启动）
 * 4. 排障速查
 */
@Composable
fun ClashHelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var batteryWhitelisted by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        batteryWhitelisted = BackgroundPermission.isIgnoringBatteryOptimizations(context)
    }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        batteryWhitelisted = BackgroundPermission.isIgnoringBatteryOptimizations(context)
    }

    var snippet by remember { mutableStateOf("") }
    var directRules by remember { mutableStateOf("") }
    var socks5Enabled by remember { mutableStateOf(true) }
    var detectedRoutes by remember { mutableStateOf(false) }

    // 片段随配置按需生成：进入 STARTED 后才监听，提取路由/DNS 特征并 distinctUntilChanged，
    // 避免 2 秒一次的流量字节计数器跳动触发高开销的 YAML 语法树与字符串重复生成。
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val container = EasyTierApp.get().container
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            combine(
                container.settingsRepository.settings,
                container.engine.states,
                container.networksRepository.networks,
            ) { settings, states, networks ->
                val running = states.values
                    .mapNotNull { it as? InstanceState.Running }
                    .map { it.info }
                val saved = networks.map { it.config }
                val cidrs = ClashRouteGenerator.collectCidrs(running, saved)
                val zones = ClashRouteGenerator.collectDnsZones(saved)
                val direct = buildDirectRules(saved)
                ClashInputs(
                    socks5Enabled = settings.enableSocks5,
                    socks5Port = settings.socks5Port,
                    cidrs = cidrs,
                    zones = zones,
                    hasRunningInstance = running.isNotEmpty(),
                    savedConfigsSummary = direct,
                    directRules = direct,
                )
            }
                .distinctUntilChanged()
                .collect { inputs ->
                    socks5Enabled = inputs.socks5Enabled
                    detectedRoutes = inputs.cidrs.isNotEmpty() || inputs.zones.isNotEmpty()
                    snippet = ClashRouteGenerator.generate(inputs.socks5Port, inputs.cidrs, inputs.zones)
                    directRules = inputs.directRules
                }
        }
    }

    fun copy(text: String, message: String) {
        clipboard.setText(AnnotatedString(text))
        scope.launch { snackbar.showSnackbar(message) }
    }

    Scaffold(
        snackbarHost = { AppSnackbarHost(snackbar) },
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
            // --- 1. 路由覆写 ---
            SectionHeader(stringResource(R.string.clash_help_section_import))
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.clash_help_import_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(R.string.clash_help_intro_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!socks5Enabled) {
                        Text(
                            stringResource(R.string.clash_help_socks5_off_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalStatusColors.current.warning,
                        )
                    }
                    if (snippet.isNotEmpty()) {
                        CodeBlock(
                            code = snippet,
                            tag = "YAML",
                            onCopy = {
                                copy(snippet, context.getString(R.string.clash_help_copied_snippet))
                            },
                        )
                    }
                    if (snippet.isNotEmpty() && !detectedRoutes) {
                        Text(
                            stringResource(R.string.clash_help_no_cidr_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalStatusColors.current.warning,
                        )
                    }
                }
            }

            // --- 2. 防止回环 ---
            SectionHeader(stringResource(R.string.clash_help_section_loop))
            AppCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                stringResource(R.string.clash_help_loop_plan_a_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            PillBadge(
                                text = stringResource(R.string.clash_help_loop_plan_a_badge),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(
                            stringResource(R.string.clash_help_loop_plan_a),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (directRules.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.clash_help_loop_plan_b_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(R.string.clash_help_loop_plan_b),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            CodeBlock(
                                code = directRules,
                                tag = "RULES",
                                onCopy = {
                                    copy(directRules, context.getString(R.string.clash_help_copied_rules))
                                },
                            )
                        }
                    }
                }
            }

            // --- 3. 后台常驻 ---
            SectionHeader(stringResource(R.string.clash_help_section_background))
            AppCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    val requestWhitelist: () -> Unit = {
                        val intent = BackgroundPermission.requestIgnoreIntent(context)
                            ?: BackgroundPermission.appDetailsIntent(context)
                        batteryLauncher.launch(intent)
                    }
                    SettingRow(
                        title = stringResource(R.string.clash_help_battery_title),
                        subtitle = stringResource(
                            if (batteryWhitelisted) R.string.clash_help_battery_ok
                            else R.string.clash_help_battery_missing,
                        ),
                        icon = AppIcons.Shield,
                        onClick = if (!batteryWhitelisted) requestWhitelist else null,
                        trailing = {
                            if (batteryWhitelisted) {
                                PillBadge(
                                    text = stringResource(R.string.clash_help_battery_badge),
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            } else {
                                FilledTonalButton(
                                    onClick = requestWhitelist,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp),
                                ) {
                                    Text(
                                        stringResource(R.string.common_turn_on),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                        },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    SettingRow(
                        title = stringResource(R.string.clash_help_autostart_title),
                        subtitle = stringResource(R.string.clash_help_autostart_hint),
                        icon = Icons.Filled.Settings,
                        onClick = {
                            context.startActivity(BackgroundPermission.appDetailsIntent(context))
                        },
                        trailing = {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }

            // --- 4. 排障速查 ---
            SectionHeader(stringResource(R.string.clash_help_section_faq))
            AppCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    TroubleshootItem(
                        title = stringResource(R.string.clash_help_faq_deadline_title),
                        body = stringResource(R.string.clash_help_faq_deadline_body),
                        monospaceTitle = true,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    TroubleshootItem(
                        title = stringResource(R.string.clash_help_faq_refused_title),
                        body = stringResource(R.string.clash_help_faq_refused_body),
                        monospaceTitle = true,
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    TroubleshootItem(
                        title = stringResource(R.string.clash_help_faq_vpn_lost_title),
                        body = stringResource(R.string.clash_help_faq_vpn_lost_body),
                        monospaceTitle = false,
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

/**
 * 终端风格语法高亮代码块：深色底 + 顶栏标签与内联复制按钮（点击变绿勾 2 秒）+ 横向滚动不折行。
 */
@Composable
private fun CodeBlock(
    code: String,
    tag: String,
    onCopy: () -> Unit,
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2000)
            copied = false
        }
    }
    val actionTint by animateColorAsState(
        targetValue = if (copied) CodeEmerald else CodeFg,
        animationSpec = tween(durationMillis = 180),
        label = "copyActionTint",
    )
    val highlighted = remember(code) { highlightClashYaml(code) }

    Surface(
        color = CodeBg,
        contentColor = CodeFg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, CodeLine),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(CodeHeaderBg)
                    .padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tag,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = CodeMuted,
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            copied = true
                            onCopy()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedContent(
                        targetState = copied,
                        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(100)) },
                        label = "copyIcon",
                    ) { isCopied ->
                        Icon(
                            if (isCopied) AppIcons.Check else AppIcons.ContentCopy,
                            contentDescription = null,
                            tint = actionTint,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        stringResource(if (copied) R.string.common_copied else R.string.common_copy),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = actionTint,
                    )
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = CodeLine)
            SelectionContainer {
                Text(
                    highlighted,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 19.sp,
                        color = CodeFg,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
        }
    }
}

/**
 * Clash YAML 片段与规则高亮：
 * - 注释行：灰斜体
 * - 规则行（含逗号）：规则类型靛蓝、目标亮白、策略青绿加粗、附加参数琥珀
 * - 键值行（含冒号）：键名靛蓝、布尔/数值/IP 琥珀、字符串值青绿
 */
private fun highlightClashYaml(text: String): AnnotatedString {
    val builder = AnnotatedString.Builder(text.length)
    builder.append(text)
    var lineStart = 0
    val n = text.length

    while (lineStart < n) {
        var lineEnd = text.indexOf('\n', lineStart)
        if (lineEnd == -1) lineEnd = n

        var cur = lineStart
        while (cur < lineEnd && (text[cur] == ' ' || text[cur] == '\t')) cur++

        if (cur < lineEnd) {
            if (text[cur] == '#') {
                builder.addStyle(
                    SpanStyle(color = CodeMuted, fontStyle = FontStyle.Italic),
                    cur,
                    lineEnd,
                )
            } else {
                // 处理列表前缀 "- "
                if (text.startsWith("- ", cur)) {
                    builder.addStyle(SpanStyle(color = CodeMuted), cur, cur + 1)
                    cur += 2
                    while (cur < lineEnd && text[cur] == ' ') cur++
                }
                val commaIdx = text.indexOf(',', cur).takeIf { it in cur until lineEnd }
                val colonIdx = text.indexOf(':', cur).takeIf { it in cur until lineEnd }

                if (commaIdx != null) {
                    // Clash 规则行：TYPE,VALUE,POLICY[,OPTION]
                    var segStart = cur
                    var segIndex = 0
                    while (segStart <= lineEnd) {
                        val nextComma = text.indexOf(',', segStart)
                            .takeIf { it in segStart until lineEnd } ?: lineEnd
                        val segStyle = when (segIndex) {
                            0 -> SpanStyle(color = CodeIndigo, fontWeight = FontWeight.Medium)
                            1 -> SpanStyle(color = CodeFg)
                            2 -> SpanStyle(color = CodeEmerald, fontWeight = FontWeight.SemiBold)
                            else -> SpanStyle(color = CodeAmber)
                        }
                        if (nextComma > segStart) {
                            builder.addStyle(segStyle, segStart, nextComma)
                        }
                        if (nextComma < lineEnd) {
                            builder.addStyle(SpanStyle(color = CodeMuted), nextComma, nextComma + 1)
                        }
                        segStart = nextComma + 1
                        segIndex++
                    }
                } else if (colonIdx != null) {
                    // YAML 键值对或顶层段落名
                    val valStart = (colonIdx + 1).coerceAtMost(lineEnd)
                    val valueTrimmed = text.substring(valStart, lineEnd).trim()
                    builder.addStyle(
                        SpanStyle(
                            color = CodeIndigo,
                            fontWeight = if (valueTrimmed.isEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                        ),
                        cur,
                        colonIdx,
                    )
                    builder.addStyle(SpanStyle(color = CodeMuted), colonIdx, colonIdx + 1)
                    if (valueTrimmed.isNotEmpty()) {
                        val valStyle = if (
                            valueTrimmed == "true" ||
                            valueTrimmed == "false" ||
                            valueTrimmed.first().isDigit()
                        ) {
                            SpanStyle(color = CodeAmber)
                        } else {
                            SpanStyle(color = CodeEmerald)
                        }
                        builder.addStyle(valStyle, valStart, lineEnd)
                    }
                }
            }
        }
        lineStart = lineEnd + 1
    }
    return builder.toAnnotatedString()
}

@Composable
private fun TroubleshootItem(
    title: String,
    body: String,
    monospaceTitle: Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontFamily = if (monospaceTitle) FontFamily.Monospace else null,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

