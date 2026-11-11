package com.easytier.android.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.easytier.android.R

/**
 * 字符串列表编辑器（peer urls / proxy cidrs / listeners 等）。
 * 顶部输入框 + 添加按钮，下方 chip 流式展示（放不下自动换行），点 chip 删除。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StringListEditor(
    label: String,
    items: List<String>,
    onChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    var input by remember { mutableStateOf("") }
    val haptics = rememberHaptics()

    fun submitAdd() {
        val v = input.trim()
        if (v.isBlank()) return
        // 去重：同名条目对引擎是重复配置
        if (v !in items) {
            onChange(items + v)
        } else {
            haptics.reject()
        }
        input = ""
    }

    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 180)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(label) },
                placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submitAdd() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = ::submitAdd,
                enabled = input.isNotBlank(),
            ) {
                Icon(Icons.Filled.Add, stringResource(R.string.common_add))
            }
        }
        if (items.isNotEmpty()) {
            // FlowRow：条目多时换行而非横向溢出
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEach { item ->
                    // M3 InputChip 的 trailingIcon 无独立点击事件，整 chip 可点即删除
                    val deleteLabel = stringResource(R.string.common_delete_item, item)
                    InputChip(
                        selected = false,
                        onClick = { onChange(items - item) },
                        label = { Text(item) },
                        trailingIcon = { Icon(Icons.Filled.Close, deleteLabel) },
                    )
                }
            }
        }
    }
}
