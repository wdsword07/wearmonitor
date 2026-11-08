package com.github.wdsword07.wearmonitor.presentation.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import java.util.Locale

@Composable
fun MetricTrendChart(
    points: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary,
    fillColor: Color = lineColor.copy(alpha = 0.25f),
    fixedMinY: Float? = null,
    fixedMaxY: Float? = null,
    unit: String = "%"
) {
    if (points.isEmpty()) {
        Box(modifier = modifier.height(60.dp))
        return
    }

    val minVal = fixedMinY ?: (points.minOrNull() ?: 0f)
    val maxVal = (fixedMaxY ?: (points.maxOrNull() ?: 100f)).coerceAtLeast(minVal + 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        // 极值标注栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = String.format(Locale.US, "近1min: 最低 %.0f%s", minVal, unit),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = String.format(Locale.US, "最高 %.0f%s", maxVal, unit),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.outline
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(vertical = 4.dp)
        ) {
            val width = size.width
            val height = size.height

            // 绘制中位参考虚线
            val midY = height / 2f
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )

            if (points.size == 1) {
                // 单点绘制水平线
                val y = height - ((points[0] - minVal) / (maxVal - minVal)).coerceIn(0f, 1f) * height
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 2.dp.toPx()
                )
                return@Canvas
            }

            val stepX = width / (points.size - 1).coerceAtLeast(1)
            val strokePath = Path()
            val fillPath = Path()

            points.forEachIndexed { index, value ->
                val x = index * stepX
                val normY = ((value - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                val y = height - (normY * (height - 8f)) - 4f // 预留上下 4px 避免边缘切顶

                if (index == 0) {
                    strokePath.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }

                if (index == points.lastIndex) {
                    fillPath.lineTo(x, height)
                    fillPath.close()

                    // 绘制最新末端指示光点
                    drawCircle(
                        color = lineColor,
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }

            // 绘制半透明渐变区域
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(fillColor, Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            // 绘制主折线
            drawPath(
                path = strokePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
