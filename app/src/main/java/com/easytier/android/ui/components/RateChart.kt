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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
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

    val strokeWidthPx = with(LocalDensity.current) { 2.dp.toPx() }
    val lineStroke = remember(strokeWidthPx) {
        Stroke(width = strokeWidthPx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    }
    val linePath = remember { Path() }
    val areaPath = remember { Path() }
    val rxBrush = remember(lineColor) {
        Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.20f), Color.Transparent))
    }
    val txBrush = remember(secondary) {
        Brush.verticalGradient(listOf(secondary.copy(alpha = 0.10f), Color.Transparent))
    }

    // 两条曲线共用一个量程（取各自最大值的较大者），平滑过渡量程跳变
    val rxOffset = (rxHistory.size - maxPoints).coerceAtLeast(0)
    var rxMax = 0L
    for (i in rxOffset until rxHistory.size) {
        val v = rxHistory[i]
        if (v > rxMax) rxMax = v
    }
    val txOffset = (txHistory.size - maxPoints).coerceAtLeast(0)
    var txMax = 0L
    for (i in txOffset until txHistory.size) {
        val v = txHistory[i]
        if (v > txMax) txMax = v
    }
    val rawMax = maxOf(rxMax, txMax).coerceAtLeast(1024L).toFloat()
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

        val topPad = 4.dp.toPx()
        val usableHeight = (size.height - topPad).coerceAtLeast(1f)
        val dotRadius = 3.dp.toPx()
        val haloRadius = 6.dp.toPx()

        fun drawSeries(series: List<Long>, color: Color, brush: Brush?) {
            if (series.size < 2) return
            val startIdx = (series.size - maxPoints).coerceAtLeast(0)
            val count = series.size - startIdx
            if (count < 2) return
            val step = size.width / (count - 1).coerceAtLeast(1)
            linePath.reset()
            var prevX = 0f
            var prevY = size.height
            for (i in 0 until count) {
                val v = series[startIdx + i]
                val ratio = (v.toFloat() / maxV).coerceIn(0f, 1f)
                val x = i * step
                val y = size.height - ratio * usableHeight * 0.92f
                if (i == 0) {
                    linePath.moveTo(x, y)
                } else {
                    // 水平中点控制点的三次贝塞尔：平滑过渡且严格单调不越界（不会跌破底线或冲出峰值）
                    val midX = (prevX + x) * 0.5f
                    linePath.cubicTo(midX, prevY, midX, y, x, y)
                }
                prevX = x
                prevY = y
            }
            if (brush != null) {
                areaPath.reset()
                areaPath.addPath(linePath)
                areaPath.lineTo(prevX, size.height)
                areaPath.lineTo(0f, size.height)
                areaPath.close()
                drawPath(areaPath, brush)
            }
            drawPath(
                linePath,
                color,
                style = lineStroke,
            )
            // 末端采样点锚点
            val endCenter = Offset(prevX, prevY)
            drawCircle(color.copy(alpha = 0.24f), radius = haloRadius, center = endCenter)
            drawCircle(color, radius = dotRadius, center = endCenter)
        }

        drawSeries(txHistory, secondary, txBrush)
        drawSeries(rxHistory, lineColor, rxBrush)
    }
}
