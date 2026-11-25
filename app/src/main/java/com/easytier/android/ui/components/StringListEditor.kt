package com.easytier.android.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.easytier.android.R

private val SCHEME_PREFIX_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

/** 快捷协议前缀切换：已有协议头则原地替换，否则直接前置，保留其余地址部分。 */
fun applyQuickPrefix(current: String, prefix: String): String {
    val trimmed = current.trimStart()
    return if (SCHEME_PREFIX_REGEX.containsMatchIn(trimmed)) {
        trimmed.replaceFirst(SCHEME_PREFIX_REGEX, prefix)
    } else {
        prefix + trimmed
    }
}

/** 从 URL 中提取简短协议徽标（如 tcp:// -> TCP、tls:// -> DoT）。 */
fun extractProtocolBadge(value: String): String? {
    val idx = value.indexOf("://")
    if (idx <= 0) return null
    return when (val scheme = value.substring(0, idx).trim().lowercase()) {
        "tls" -> "DoT"
        "https" -> "DoH"
        else -> scheme.uppercase()
    }
}

/**
 * 字符串列表编辑器（peer urls / proxy cidrs / listeners / routes / exit nodes 等）。
 * 列表内每行独立展示，点按行可直接修改，右侧独立删除按钮防误触，底部 "+ 添加" 弹窗录入。
 */
@Composable
fun StringListEditor(
    label: String,
    items: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    emptyHint: String? = null,
    addButtonText: String? = null,
    badgeProvider: ((String) -> String?)? = null,
    quickPrefixes: List<String> = emptyList(),
    validator: ((String) -> String?)? = null,
    monospace: Boolean = true,
) {
    // null = 弹窗关闭；-1 = 新增；>=0 = 编辑对应下标条目
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    val resolvedEmptyHint = emptyHint ?: stringResource(R.string.list_empty_default)
    val resolvedAddText = addButtonText ?: stringResource(R.string.list_add_title, label)

    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 180)),
    ) {
        if (label.isNotBlank()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (items.isNotEmpty()) {
                    PillBadge(
                        text = items.size.toString(),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.fillMaxWidth()) {
                if (items.isEmpty()) {
                    Text(
                        text = resolvedEmptyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                } else {
                    items.forEachIndexed { index, item ->
                        val badge = badgeProvider?.invoke(item)
                        val deleteLabel = stringResource(R.string.common_delete_item, item)
                        val editLabel = stringResource(R.string.common_edit_item, item)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable(onClickLabel = editLabel) { editingIndex = index }
                                .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (badge != null) {
                                PillBadge(
                                    text = badge,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                            Text(
                                text = item,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = if (monospace) FontFamily.Monospace else null,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.size(16.dp),
                            )
                            IconButton(
                                onClick = {
                                    onChange(items.toMutableList().apply { removeAt(index) })
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = deleteLabel,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        if (index < items.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 46.dp)
                        .clickable { editingIndex = -1 }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = resolvedAddText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    if (items.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.list_tap_to_edit_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    val activeIndex = editingIndex
    if (activeIndex != null) {
        val isNew = activeIndex < 0
        val initial = if (isNew) "" else items.getOrElse(activeIndex) { "" }
        ListItemEditDialog(
            title = if (isNew) {
                stringResource(R.string.list_add_title, label)
            } else {
                stringResource(R.string.list_edit_title, label)
            },
            label = label,
            initialValue = initial,
            placeholder = placeholder,
            quickPrefixes = quickPrefixes,
            monospace = monospace,
            existingItems = items,
            editingIndex = activeIndex,
            validator = validator,
            onDismiss = { editingIndex = null },
            onConfirm = { newValue ->
                val updated = if (isNew) {
                    items + newValue
                } else {
                    items.toMutableList().apply {
                        if (activeIndex in indices) set(activeIndex, newValue)
                    }
                }
                onChange(updated)
                editingIndex = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListItemEditDialog(
    title: String,
    label: String,
    initialValue: String,
    placeholder: String,
    quickPrefixes: List<String>,
    monospace: Boolean,
    existingItems: List<String>,
    editingIndex: Int,
    validator: ((String) -> String?)?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var fieldValue by remember(initialValue) {
        mutableStateOf(TextFieldValue(initialValue, selection = TextRange(initialValue.length)))
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val haptics = rememberHaptics()
    val dupError = stringResource(R.string.list_dup_error)
    val okLabel = stringResource(R.string.common_ok)
    val cancelLabel = stringResource(R.string.common_cancel)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun validate(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val isDup = existingItems.withIndex().any { (idx, existing) ->
            idx != editingIndex && existing == trimmed
        }
        if (isDup) return dupError
        return validator?.invoke(trimmed)
    }

    fun trySubmit() {
        val trimmed = fieldValue.text.trim()
        if (trimmed.isEmpty()) {
            haptics.reject()
            return
        }
        val err = validate(trimmed)
        if (err != null) {
            errorMessage = err
            haptics.reject()
            return
        }
        onConfirm(trimmed)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = { next ->
                        fieldValue = next
                        errorMessage = validate(next.text)
                    },
                    label = if (label.isNotBlank()) ({ Text(label) }) else null,
                    placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { err -> { Text(err) } },
                    textStyle = if (monospace) {
                        MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { trySubmit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                if (quickPrefixes.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        quickPrefixes.forEach { prefix ->
                            AssistChip(
                                onClick = {
                                    val updated = applyQuickPrefix(fieldValue.text, prefix)
                                    fieldValue = TextFieldValue(
                                        updated,
                                        selection = TextRange(updated.length),
                                    )
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = ::trySubmit,
                enabled = fieldValue.text.isNotBlank(),
            ) {
                Text(okLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancelLabel) }
        },
    )
}
