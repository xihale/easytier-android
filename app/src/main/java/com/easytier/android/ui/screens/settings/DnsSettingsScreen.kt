package com.easytier.android.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.AppSnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.easytier.android.ui.components.StringListEditor
import com.easytier.android.util.DnsServers
import kotlinx.coroutines.launch

/**
 * DNS 上游独立设置页：Magic DNS 转发普通域名查询的上游服务器（DoT/DoH）。
 * 全局生效，注入所有开启 Magic DNS 的网络；清空列表 = 核心内置默认（纯 UDP）。
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            if (s == null) return@Column
            AppCard {
                Column(Modifier.padding(vertical = 4.dp)) {
                    StringListEditor(
                        label = "上游 DNS 服务器",
                        placeholder = DnsServers.DEFAULT_SERVER,
                        items = s.dnsServers,
                        onChange = { next ->
                            val valid = next.filter { DnsServers.isValid(it) }
                            if (valid.size != next.size) {
                                scope.launch {
                                    snackbar.showSnackbar("已忽略无效地址，仅支持 udp tcp tls https 服务器")
                                }
                            }
                            vm.setDnsServers(valid)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Text(
                        "Magic DNS 转发普通域名查询的上游，按顺序故障转移；重启网络后生效。" +
                            "tls:// 即 DoT，https:// 即 DoH。清空列表使用核心内置默认（纯 UDP）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    // 预置快捷导入：名称在左，右侧图标点按加入；已加入的变 ✓ 并禁用
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        DnsServers.PRESETS.forEach { (url, label) ->
                            val added = url in s.dnsServers
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                IconButton(
                                    onClick = { vm.setDnsServers(s.dnsServers + url) },
                                    enabled = !added,
                                ) {
                                    Icon(
                                        if (added) Icons.Filled.Check else Icons.Filled.Add,
                                        if (added) "已导入 $label" else "导入 $label",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
