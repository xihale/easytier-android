package com.easytier.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp

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

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text(label) },
                placeholder = if (placeholder.isNotBlank()) ({ Text(placeholder) }) else null,
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val v = input.trim()
                    // 去重：同名条目对引擎是重复配置
                    if (v.isNotBlank() && v !in items) onChange(items + v)
                    input = ""
                },
            ) {
                Icon(Icons.Filled.Add, "添加")
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
                    InputChip(
                        selected = false,
                        onClick = { onChange(items - item) },
                        label = { Text(item) },
                        trailingIcon = { Icon(Icons.Filled.Close, "删除 $item") },
                    )
                }
            }
        }
    }
}
