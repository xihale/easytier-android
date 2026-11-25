package com.easytier.android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
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

/** 文本输入对话框：支持自动聚焦、实时校验拦截错误提交、键盘类型与等宽字样。 */
@Composable
fun TextInputDialog(
    title: String,
    initialValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    label: String = "",
    placeholder: String = "",
    confirmText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    monospace: Boolean = false,
    validator: ((String) -> String?)? = null,
) {
    var fieldValue by remember(initialValue) {
        mutableStateOf(TextFieldValue(initialValue, selection = TextRange(initialValue.length)))
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }
    val haptics = rememberHaptics()
    val confirm = confirmText ?: stringResource(R.string.common_ok)
    val cancel = stringResource(R.string.common_cancel)

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun trySubmit() {
        val trimmed = fieldValue.text.trim()
        val err = validator?.invoke(trimmed)
        if (err != null) {
            errorMessage = err
            haptics.reject()
            return
        }
        onConfirm(trimmed)
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = { next ->
                    fieldValue = next
                    if (errorMessage != null) {
                        errorMessage = validator?.invoke(next.text.trim())
                    }
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
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { trySubmit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = ::trySubmit) { Text(confirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(cancel) }
        },
    )
}
