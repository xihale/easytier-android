package com.easytier.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easytier.android.ui.icons.AppIcons

/**
 * 首页 Hero 卡：EasyTier 服务（TUN）开关，与下面各网络的启停开关解耦。
 * 开 = 建立系统 VPN（TUN）；关 = 仅关闭 TUN，网络实例不受影响。
 * headline 为 null 时不渲染大字行（不再显示提示文案）。
 * 未运行时显示「EasyTier 服务」标题；运行中隐藏（品牌名在卡片上方），只保留状态行。
 * 渐变用固定深蓝（不取主题色）：深色主题下 primary 是浅色，白字会失去对比度；
 * 未运行时用灰色渐变弱化。右上角 mesh 节点连线是全应用的「特色符号」装饰。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceHeroCard(
    running: Boolean,
    statusText: String,
    headline: String?,
    stats: List<String>,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val start = if (running) HeroStart else HeroIdleStart
    val end = if (running) HeroEnd else HeroIdleEnd
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(start, end)),
                MaterialTheme.shapes.extraLarge,
            )
            .clip(MaterialTheme.shapes.extraLarge)
            .drawBehind { drawMeshDecoration() }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 盾牌图标放 44dp 磨砂圆（白 18% 底）
                Box(
                    Modifier
                        .size(44.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        AppIcons.Shield,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.padding(start = 12.dp)) {
                    // 运行中不再重复显示「EasyTier 服务」标题（品牌名就在卡片正上方），只留状态行
                    if (!running) {
                        Text(
                            "EasyTier 服务",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(
                            if (running) StatusGreen else Color.White.copy(alpha = 0.6f),
                            size = 8.dp,
                            pulse = running,
                        )
                        Text(
                            statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            Switch(
                checked = running,
                onCheckedChange = onToggle,
                // TalkBack 默认只读「开/关」，补上服务名语义
                modifier = Modifier.semantics { contentDescription = "EasyTier 服务" },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = start,
                    checkedTrackColor = Color.White,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Color.White.copy(alpha = 0.3f),
                ),
            )
        }

        if (headline != null) {
            Spacer(Modifier.height(16.dp))
            // IPv4 等技术数值用等宽字体（含 '.' 且长度 < 21 视为 IPv4 形态）
            val baseStyle = MaterialTheme.typography.headlineSmall
            Text(
                headline,
                style = if (headline.contains('.') && headline.length < 21) {
                    baseStyle.copy(fontFamily = FontFamily.Monospace)
                } else {
                    baseStyle
                },
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }

        if (stats.isNotEmpty()) {
            // 分段式统计行：图标 + 文本 + 竖分隔线；FlowRow 保证窄屏换行而非溢出
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                stats.forEachIndexed { index, text ->
                    // 分隔线并入段尾：FlowRow 换行时残留的是上一行行尾而非下一行行首
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HeroStatSegment(text, heroStatIcon(index))
                        if (index < stats.lastIndex) {
                            Spacer(Modifier.width(12.dp))
                            HeroStatDivider()
                        }
                    }
                }
            }
        }
    }
}

/** 按顺序为统计段配图标：[Work、ArrowUpward、ArrowDownward]，超出数量不配。 */
private fun heroStatIcon(index: Int): ImageVector? = when (index) {
    0 -> AppIcons.Work
    1 -> AppIcons.ArrowUpward
    2 -> AppIcons.ArrowDownward
    else -> null
}

/** 分段统计段：小图标（白 80%）+ labelMedium 白色文本。 */
@Composable
private fun HeroStatSegment(text: String, icon: ImageVector?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                icon,
                null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}

/** 分段之间的竖分隔线（1dp 宽 × 14dp 高，白 25%）。 */
@Composable
private fun HeroStatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(14.dp)
            .background(Color.White.copy(alpha = 0.25f)),
    )
}

/** 右上角 mesh 母题装饰：白色节点圆点 + 连线（alpha≈0.10），占据约 160dp 区域，已被裁剪在卡内。 */
private fun DrawScope.drawMeshDecoration() {
    val lineColor = Color.White.copy(alpha = 0.10f)
    val dotColor = Color.White.copy(alpha = 0.10f)
    val right = size.width
    // 节点坐标锚定右上角（单位 px），分布在约 160dp 见方区域内
    val nodes = listOf(
        Offset(right - 24.dp.toPx(), 20.dp.toPx()),
        Offset(right - 76.dp.toPx(), 42.dp.toPx()),
        Offset(right - 128.dp.toPx(), 16.dp.toPx()),
        Offset(right - 48.dp.toPx(), 94.dp.toPx()),
        Offset(right - 108.dp.toPx(), 86.dp.toPx()),
        Offset(right - 148.dp.toPx(), 58.dp.toPx()),
    )
    val edges = listOf(
        0 to 1, 1 to 2, 1 to 4, 2 to 5, 3 to 4, 4 to 5, 1 to 3,
    )
    edges.forEach { (a, b) ->
        drawLine(lineColor, nodes[a], nodes[b], strokeWidth = 1.5f)
    }
    val radii = listOf(3.dp, 2.dp, 2.5.dp, 2.dp, 3.dp, 2.dp)
    nodes.forEachIndexed { i, center ->
        drawCircle(dotColor, radius = radii[i].toPx(), center = center)
    }
}

private val HeroStart = Color(0xFF2A4FD0)
private val HeroEnd = Color(0xFF5E7CF5)
private val HeroIdleStart = Color(0xFF454B5E)
private val HeroIdleEnd = Color(0xFF5C6377)
private val StatusGreen = Color(0xFF34D399)
