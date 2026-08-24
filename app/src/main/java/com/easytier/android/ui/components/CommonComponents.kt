package com.easytier.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easytier.android.core.engine.InstanceState
import com.easytier.android.ui.theme.LocalStatusColors

/** 统一卡片外观：扁平（无阴影）+ 极细描边 + surfaceContainerLowest 默认底色。 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    // 扁平化：全部交互态 elevation 归零，仅靠细描边分层
    val elevation = CardDefaults.cardElevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
        draggedElevation = 0.dp,
        disabledElevation = 0.dp,
    )
    val border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = colors,
            elevation = elevation,
            border = border,
        ) {
            content()
        }
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = colors,
            elevation = elevation,
            border = border,
        ) {
            content()
        }
    }
}

/** 状态圆点（列表/节点状态统一指示）。pulse = true 时外圈呼吸光晕。 */
@Composable
fun StatusDot(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
    pulse: Boolean = false,
) {
    if (!pulse) {
        Box(
            modifier
                .size(size)
                .background(color, CircleShape),
        )
    } else {
        PulsingStatusDot(color, modifier, size)
    }
}

/** 呼吸光晕：2000ms 循环，外圈半径缓动扩张、alpha 在 0.18～0.32 间往复。 */
@Composable
private fun PulsingStatusDot(
    color: Color,
    modifier: Modifier,
    size: Dp,
) {
    val transition = rememberInfiniteTransition(label = "statusDotPulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "statusDotPulseProgress",
    )
    // 外层固定为圆点尺寸：光晕溢出绘制，不影响布局，避免相邻元素被呼吸动画推动
    Box(
        modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size * (1f + 1.6f * progress))
                .background(color.copy(alpha = 0.32f - 0.14f * progress), CircleShape),
        )
        Box(Modifier.fillMaxSize().background(color, CircleShape))
    }
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

/** 分组标题（中性色小号，中文不拉开字距）。可选 trailing 与标题同行居中对齐。 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    if (trailing == null) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier.padding(top = 20.dp, bottom = 8.dp),
        )
    } else {
        Row(
            modifier.padding(top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** 居中空状态（72dp 圆形容器图标 + 标题 + 引导文案）。 */
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
        Box(
            Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
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
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 速率/流量展示（上/下行，颜色与流量图曲线一致）。数值用等宽字体。 */
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
            fontFamily = FontFamily.Monospace,
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
