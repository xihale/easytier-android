package com.easytier.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** 简易速率曲线图（双线：rx/tx + 曲线下渐变填充）。 */
@Composable
fun RateChart(
    rxHistory: List<Long>,
    txHistory: List<Long>,
    modifier: Modifier = Modifier,
    maxPoints: Int = 60,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(96.dp),
    ) {
        val n = maxOf(rxHistory.size, txHistory.size)
        if (n < 2) return@Canvas
        // 两条曲线共用一个量程（取各自最大值的较大者），否则视觉上无法对比
        val maxV = maxOf(rxHistory.max(), txHistory.max()).coerceAtLeast(1024L).toFloat()

        // 网格线
        for (i in 1..3) {
            val y = size.height * i / 4f
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }

        fun drawSeries(series: List<Long>, color: Color, fill: Boolean) {
            if (series.size < 2) return
            val startIdx = (series.size - maxPoints).coerceAtLeast(0)
            val slice = series.subList(startIdx, series.size)
            val step = size.width / (slice.size - 1).coerceAtLeast(1)
            val path = Path()
            slice.forEachIndexed { i, v ->
                val x = i * step
                val y = size.height - (v.toFloat() / maxV) * size.height * 0.92f
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 4f))
            if (fill && slice.size >= 2) {
                val area = Path().apply {
                    addPath(path)
                    lineTo((slice.size - 1) * step, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                drawPath(
                    area,
                    Brush.verticalGradient(
                        listOf(color.copy(alpha = 0.18f), Color.Transparent),
                        startY = 0f,
                        endY = size.height,
                    ),
                )
            }
        }

        drawSeries(txHistory, secondary, fill = false)
        drawSeries(rxHistory, lineColor, fill = true)
    }
}
