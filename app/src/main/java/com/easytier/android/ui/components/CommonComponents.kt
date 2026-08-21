package com.easytier.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.easytier.android.core.engine.InstanceState
import com.easytier.android.ui.theme.LocalStatusColors

/** 统一卡片外观：所有列表/信息卡走同一容器色与圆角。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors) {
            content()
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors) {
            content()
        }
    }
}

/** 状态圆点（列表/节点状态统一指示）。 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    Box(
        modifier
            .size(size)
            .background(color, CircleShape),
    )
}

/** 紧凑胶囊标签（替代 Material Chip：无交互、无 32dp 最小高度）。 */
@Composable
fun PillBadge(
    text: String,
    containerColor: Color,
    labelColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, null, tint = labelColor, modifier = Modifier.size(12.dp))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = labelColor,
                modifier = Modifier.padding(start = if (icon != null) 4.dp else 0.dp),
            )
        }
    }
}

/** 分组标题（设置页/编辑页/状态页共用，替代各页私有实现）。 */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

/** 居中空状态（大图标 + 标题 + 引导文案）。 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** 信息条目（标签 + 值）。 */
@Composable
fun InfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 速率/流量展示（上/下行，颜色与流量图曲线一致）。 */
@Composable
fun RateRow(
    icon: ImageVector,
    rateText: String,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            rateText,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** 实例状态对应的强调色（点/图标/副标题共用一套语义色）。 */
@Composable
fun stateAccent(state: InstanceState?): Color = when (state) {
    is InstanceState.Running -> LocalStatusColors.current.success
    is InstanceState.Starting -> LocalStatusColors.current.warning
    is InstanceState.Error -> MaterialTheme.colorScheme.error
    null, InstanceState.Stopped -> MaterialTheme.colorScheme.outline
}
