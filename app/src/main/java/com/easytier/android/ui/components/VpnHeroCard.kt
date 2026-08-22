package com.easytier.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.easytier.android.ui.icons.AppIcons

/**
 * 首页 Hero 卡：EasyTier 服务（TUN）开关，与下面各网络的启停开关解耦。
 * 开 = 建立系统 VPN（TUN）；关 = 仅关闭 TUN，网络实例不受影响。
 * headline 为 null 时不渲染大字行（不再显示提示文案）。
 * 渐变用固定深蓝（不取主题色）：深色主题下 primary 是浅色，白字会失去对比度；
 * 未运行时用灰色渐变弱化。
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
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    AppIcons.Shield,
                    null,
                    tint = Color.White,
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                        .padding(8.dp)
                        .size(20.dp),
                )
                Column(Modifier.padding(start = 12.dp)) {
                    Text(
                        "EasyTier 服务",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(if (running) StatusGreen else Color.White.copy(alpha = 0.6f), Modifier.size(8.dp))
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
            Text(
                headline,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }

        if (stats.isNotEmpty()) {
            // FlowRow：窄屏时胶囊换行而不是溢出
            FlowRow(
                Modifier.fillMaxWidth().padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                stats.forEach { text -> HeroStat(text) }
            }
        }
    }
}

/** Hero 卡内的小统计胶囊（白色半透明底，保证渐变上可读）。 */
@Composable
private fun HeroStat(text: String) {
    Surface(
        color = Color.White.copy(alpha = 0.15f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private val HeroStart = Color(0xFF0A4CBE)
private val HeroEnd = Color(0xFF2563EB)
private val HeroIdleStart = Color(0xFF3B4252)
private val HeroIdleEnd = Color(0xFF4C566A)
private val StatusGreen = Color(0xFF34D399)
