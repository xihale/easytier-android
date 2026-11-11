package com.easytier.android.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** 速率曲线图（双线：rx/tx 平滑贝塞尔曲线 + 渐变填充 + 最新采样点指示）。 */
@Composable
fun RateChart(
    rxHistory: List<Long>,
    txHistory: List<Long>,
    modifier: Modifier = Modifier,
    maxPoints: Int = 60,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)

    // 两条曲线共用一个量程（取各自最大值的较大者），平滑过渡量程跳变
    val rawMax = maxOf(
        rxHistory.takeLast(maxPoints).maxOrNull() ?: 0L,
        txHistory.takeLast(maxPoints).maxOrNull() ?: 0L,
    ).coerceAtLeast(1024L).toFloat()
    val maxV by animateFloatAsState(
        targetValue = rawMax,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "rateChartMaxScale",
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        val n = maxOf(rxHistory.size, txHistory.size)
        val gridStroke = 0.75.dp.toPx()
        // 网格线（无数据时也绘制底格，保持卡片视觉结构稳定）
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = gridStroke)
        }
        if (n < 2) return@Canvas

        val strokeWidth = 2.dp.toPx()
        val topPad = 4.dp.toPx()
        val usableHeight = (size.height - topPad).coerceAtLeast(1f)
        val dotRadius = 3.dp.toPx()
        val haloRadius = 6.dp.toPx()

        fun drawSeries(series: List<Long>, color: Color, fillAlpha: Float) {
            if (series.size < 2) return
            val startIdx = (series.size - maxPoints).coerceAtLeast(0)
            val slice = series.subList(startIdx, series.size)
            val step = size.width / (slice.size - 1).coerceAtLeast(1)
            val path = Path()
            var prevX = 0f
            var prevY = size.height
            slice.forEachIndexed { i, v ->
                val ratio = (v.toFloat() / maxV).coerceIn(0f, 1f)
                val x = i * step
                val y = size.height - ratio * usableHeight * 0.92f
                if (i == 0) {
                    path.moveTo(x, y)
                } else {
                    // 水平中点控制点的三次贝塞尔：平滑过渡且严格单调不越界（不会跌破底线或冲出峰值）
                    val midX = (prevX + x) * 0.5f
                    path.cubicTo(midX, prevY, midX, y, x, y)
                }
                prevX = x
                prevY = y
            }
            if (fillAlpha > 0f) {
                val area = Path().apply {
                    addPath(path)
                    lineTo(prevX, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    area,
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = fillAlpha), Color.Transparent),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            }
            drawPath(
                path,
                color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            // 末端采样点锚点
            val endCenter = Offset(prevX, prevY)
            drawCircle(color.copy(alpha = 0.24f), radius = haloRadius, center = endCenter)
            drawCircle(color, radius = dotRadius, center = endCenter)
        }

        drawSeries(txHistory, secondary, fillAlpha = 0.10f)
        drawSeries(rxHistory, lineColor, fillAlpha = 0.20f)
    }
}
