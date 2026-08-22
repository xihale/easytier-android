package com.easytier.android.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType

/** 数字输入框：本地持有文本，合法才提交；range 外/空值给 isError 提示，不再把非法输入静默改成默认值。 */
@Composable
fun IntField(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    range: ClosedRange<Int>? = null,
    supporting: String? = null,
    allowEmpty: Boolean = false,
) {
    NumberFieldImpl(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        range = range,
        supporting = supporting,
        allowEmpty = allowEmpty,
        parse = String::toIntOrNull,
    )
}

/** 同 [IntField] 的 Long 版本（带宽限制等大数场景）。 */
@Composable
fun LongField(
    value: Long?,
    onValueChange: (Long?) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    range: ClosedRange<Long>? = null,
    supporting: String? = null,
    allowEmpty: Boolean = false,
) {
    NumberFieldImpl(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        range = range,
        supporting = supporting,
        allowEmpty = allowEmpty,
        parse = String::toLongOrNull,
    )
}

/** 数字输入的通用实现：Int/Long 仅解析器不同。 */
@Composable
private fun <T : Comparable<T>> NumberFieldImpl(
    value: T?,
    onValueChange: (T?) -> Unit,
    label: String,
    modifier: Modifier,
    range: ClosedRange<T>?,
    supporting: String?,
    allowEmpty: Boolean,
    parse: (String) -> T?,
) {
    // 以外部 value 为 key：提交成功才重置文本；非法暂存期间（未提交）不覆盖用户输入
    var text by remember(value) { mutableStateOf(value?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit(raw: String) {
        val parsed = parse(raw)
        when {
            raw.isEmpty() -> if (allowEmpty) {
                error = null
                onValueChange(null)
            } else {
                error = "不能为空"
            }
            parsed == null -> error = "请输入数字"
            range != null && parsed !in range ->
                error = "需在 ${range.start} - ${range.endInclusive} 之间"
            else -> {
                error = null
                onValueChange(parsed)
            }
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            submit(it)
        },
        label = { Text(label) },
        isError = error != null,
        supportingText = {
            val msg = error ?: supporting
            if (msg != null) {
                Text(
                    msg,
                    color = if (error != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}
