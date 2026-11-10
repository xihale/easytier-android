package com.easytier.android.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.easytier.android.R

/** 确认对话框。 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String? = null,
    dismissText: String? = null,
    destructive: Boolean = false,
) {
    // 默认按钮文案取资源（默认参数处不能调 Composable）
    val confirm = confirmText ?: stringResource(R.string.common_ok)
    val dismiss = dismissText ?: stringResource(R.string.common_cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirm,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismiss) }
        },
    )
}

/** 文本输入对话框。 */
@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String = "",
    confirmText: String? = null,
) {
    var value by remember { mutableStateOf(initialValue) }
    val confirm = confirmText ?: stringResource(R.string.common_ok)
    val cancel = stringResource(R.string.common_cancel)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = if (label.isNotBlank()) ({ Text(label) }) else null,
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.trim()); onDismiss() }) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancel) }
        },
    )
}
