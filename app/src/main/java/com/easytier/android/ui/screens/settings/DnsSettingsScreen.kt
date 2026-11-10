package com.easytier.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.AppSnackbarHost
import com.easytier.android.ui.components.PillBadge
import com.easytier.android.ui.icons.AppIcons
import com.easytier.android.util.DnsServers
import kotlinx.coroutines.launch

/**
 * DNS 上游独立设置页：Magic DNS 转发普通域名查询的上游服务器（DoT/DoH）。
 * 全局生效，注入所有开启 Magic DNS 的网络；清空列表 = 核心内置默认（纯 UDP）。
 *
 * 版式自上而下：已添加列表（协议徽标 + 地址 + 删除）→ 添加输入框 → 一行说明 → 预置快捷 chip。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DnsSettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val settings by vm.settings.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                }
                Text(
                    "DNS 上游",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { padding ->
        val s = settings
        if (s == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        // 统一添加入口：去重 + 格式校验（与核心 parse_dns_forward_server 同口径）
        fun addServer(raw: String) {
            val v = raw.trim()
            when {
                v.isEmpty() -> Unit
                v in s.dnsServers -> scope.launch { snackbar.showSnackbar("已存在，无需重复添加") }
                !DnsServers.isValid(v) -> scope.launch {
                    snackbar.showSnackbar("格式无效，支持 udp:// tcp:// tls://（DoT） https://（DoH）")
                }
                else -> vm.setDnsServers(s.dnsServers + v)
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            AppCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    // 已添加上游：协议徽标 + 等宽地址 + 删除；空列表回退核心内置默认
                    if (s.dnsServers.isEmpty()) {
                        Text(
                            "尚未添加，使用核心内置默认（纯 UDP）",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    } else {
                        s.dnsServers.forEachIndexed { i, server ->
                            ServerRow(server, onDelete = { vm.setDnsServers(s.dnsServers - server) })
                            if (i < s.dnsServers.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    var input by remember { mutableStateOf("") }
                    fun submitAdd() {
                        addServer(input)
                        input = ""
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("添加上游") },
                        placeholder = { Text(DnsServers.DEFAULT_SERVER) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = ::submitAdd,
                                enabled = input.isNotBlank(),
                            ) {
                                Icon(Icons.Filled.Add, "添加")
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitAdd() }),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            Text(
                "按顺序故障转移，重启网络后生效；清空列表回退核心内置默认（纯 UDP）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )

            // 预置快捷添加：点按加入，再点移除（开关式），已加入的带 ✓
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                DnsServers.PRESETS.forEach { (url, label) ->
                    val added = url in s.dnsServers
                    InputChip(
                        selected = false,
                        onClick = {
                            vm.setDnsServers(if (added) s.dnsServers - url else s.dnsServers + url)
                        },
                        label = { Text(label) },
                        leadingIcon = if (added) {
                            { Icon(AppIcons.Check, "已添加 $label", modifier = Modifier.size(16.dp)) }
                        } else {
                            null
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 上游行：协议徽标（DoT/DoH/UDP/TCP）+ 等宽地址 + 删除按钮。 */
@Composable
private fun ServerRow(server: String, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PillBadge(
            text = schemeLabel(server),
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            server,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(
                AppIcons.Close,
                "删除 $server",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** URL scheme → 展示用协议名。 */
private fun schemeLabel(url: String): String = when (url.substringBefore("://", "").lowercase()) {
    "tls" -> "DoT"
    "https" -> "DoH"
    "udp" -> "UDP"
    "tcp" -> "TCP"
    else -> "DNS"
}
