package com.easytier.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 设置行（图标 + 标题 + 副标题 + 右侧内容/开关）。
 * 镜像设计稿 Settings 页与 Advanced 页的行样式。
 */
@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable () -> Unit = {},
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
) {
    val rowModifier = if (onClick != null) {
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp)
    } else {
        Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp)
    }
    Row(
        rowModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
    // 分组最后一行传 divider = false，避免分割线贴着下一组标题
    if (divider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

/** 带开关的设置行（onCheckedChange 放最后，支持尾随 lambda）。 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    divider: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = modifier,
        // 整行可点；Switch 只做展示（onCheckedChange = null），避免行点击 + 开关自身点击双触发
        onClick = { onCheckedChange(!checked) },
        divider = divider,
        trailing = {
            Switch(checked = checked, onCheckedChange = null)
        },
    )
}

/** 标题 + 值的设置行（点击弹选择）。 */
@Composable
fun ChoiceRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    divider: Boolean = true,
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        modifier = modifier,
        onClick = onClick,
        divider = divider,
        trailing = {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}
