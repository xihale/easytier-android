package com.easytier.android.ui.screens.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.easytier.android.R
import androidx.lifecycle.viewmodel.compose.viewModel
import com.easytier.android.ui.components.AppCard
import com.easytier.android.ui.components.AppSnackbarHost
import com.easytier.android.ui.components.PillBadge
import com.easytier.android.ui.components.applyQuickPrefix
import com.easytier.android.ui.components.rememberHaptics
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
    val settings by vm.settings.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = rememberHaptics()

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
                    stringResource(R.string.dns_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) { padding ->
        val s = settings
        var editingIndex by remember { mutableStateOf<Int?>(null) }

        // 统一添加入口：去重 + 格式校验（与核心 parse_dns_forward_server 同口径）
        fun addServer(raw: String) {
            val v = raw.trim()
            when {
                v.isEmpty() -> Unit
                v in s.dnsServers -> {
                    haptics.reject()
                    scope.launch {
                        snackbar.showSnackbar(context.getString(R.string.dns_dup_snack))
                    }
                }
                !DnsServers.isValid(v) -> {
                    haptics.reject()
                    scope.launch {
                        snackbar.showSnackbar(context.getString(R.string.dns_invalid_snack))
                    }
                }
                else -> {
                    vm.setDnsServers(s.dnsServers + v)
                }
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
            AppCard(Modifier.animateContentSize(animationSpec = tween(durationMillis = 200))) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    // 已添加上游：协议徽标 + 等宽地址 + 删除；空列表回退核心内置默认
                    if (s.dnsServers.isEmpty()) {
                        Text(
                            stringResource(R.string.dns_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    } else {
                        s.dnsServers.forEachIndexed { i, server ->
                            ServerRow(
                                server = server,
                                onEdit = { editingIndex = i },
                                onDelete = {
                                    vm.setDnsServers(s.dnsServers - server)
                                },
                            )
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
                        label = { Text(stringResource(R.string.dns_add)) },
                        placeholder = { Text(DnsServers.DEFAULT_SERVER) },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = ::submitAdd,
                                enabled = input.isNotBlank(),
                            ) {
                                Icon(Icons.Filled.Add, stringResource(R.string.common_add))
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
                stringResource(R.string.dns_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )

            // 预置快捷添加：点按加入，再点移除（开关式），已加入的带选中态与 ✓
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 4.dp),
            ) {
                DnsServers.PRESETS.forEach { (url, labelRes) ->
                    val added = url in s.dnsServers
                    val label = stringResource(labelRes)
                    InputChip(
                        selected = added,
                        onClick = {
                            haptics.toggle(!added)
                            vm.setDnsServers(if (added) s.dnsServers - url else s.dnsServers + url)
                        },
                        label = { Text(label) },
                        leadingIcon = if (added) {
                            {
                                Icon(
                                    AppIcons.Check,
                                    stringResource(R.string.dns_preset_added_cd, label),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        val activeIndex = editingIndex
        if (activeIndex != null && activeIndex in s.dnsServers.indices) {
            DnsEditDialog(
                initialValue = s.dnsServers[activeIndex],
                existingServers = s.dnsServers,
                editingIndex = activeIndex,
                onDismiss = { editingIndex = null },
                onConfirm = { newUrl ->
                    vm.setDnsServers(
                        s.dnsServers.toMutableList().apply { set(activeIndex, newUrl) },
                    )
                    editingIndex = null
                },
            )
        }
    }
}

/** 上游行：协议徽标（DoT/DoH/UDP/TCP）+ 等宽地址（点按可编辑）+ 删除按钮。 */
@Composable
private fun ServerRow(server: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    val deleteLabel = stringResource(R.string.common_delete_item, server)
    val editLabel = stringResource(R.string.common_edit_item, server)
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClickLabel = editLabel, onClick = onEdit)
            .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
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
        Icon(
            Icons.Filled.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(16.dp),
        )
        IconButton(onClick = onDelete) {
            Icon(
                AppIcons.Close,
                deleteLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DnsEditDialog(
    initialValue: String,
    existingServers: List<String>,
    editingIndex: Int,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var fieldValue by remember(initialValue) {
        mutableStateOf(TextFieldValue(initialValue, selection = TextRange(initialValue.length)))
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val haptics = rememberHaptics()
    val dupMsg = stringResource(R.string.dns_dup_snack)
    val invalidMsg = stringResource(R.string.dns_invalid_snack)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun validate(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return null
        val isDup = existingServers.withIndex().any { (idx, item) -> idx != editingIndex && item == v }
        return when {
            isDup -> dupMsg
            !DnsServers.isValid(v) -> invalidMsg
            else -> null
        }
    }

    fun trySubmit() {
        val v = fieldValue.text.trim()
        if (v.isEmpty()) {
            haptics.reject()
            return
        }
        val err = validate(v)
        if (err != null) {
            errorMessage = err
            haptics.reject()
            return
        }
        onConfirm(v)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dns_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { next ->
                        fieldValue = next
                        errorMessage = validate(next.text)
                    },
                    label = { Text(stringResource(R.string.dns_add)) },
                    placeholder = { Text(DnsServers.DEFAULT_SERVER) },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { err -> { Text(err) } },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { trySubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    listOf("tls://", "https://", "udp://", "tcp://").forEach { prefix ->
                        AssistChip(
                            onClick = {
                                val updated = applyQuickPrefix(fieldValue.text, prefix)
                                fieldValue = TextFieldValue(updated, selection = TextRange(updated.length))
                                errorMessage = validate(updated)
                            },
                            label = {
                                Text(
                                    prefix,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = ::trySubmit,
                enabled = fieldValue.text.isNotBlank(),
            ) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/** URL scheme → 展示用协议名。 */
private fun schemeLabel(url: String): String = when (url.substringBefore("://", "").lowercase()) {
    "tls" -> "DoT"
    "https" -> "DoH"
    "udp" -> "UDP"
    "tcp" -> "TCP"
    else -> "DNS"
}
