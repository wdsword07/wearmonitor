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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
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
    unit: String = "%"
) {
    if (points.isEmpty()) {
        Box(modifier = modifier.height(60.dp))
        return
    }

    val minVal = points.minOrNull() ?: 0f
    val maxVal = points.maxOrNull() ?: 0f
    val avgVal = if (points.isNotEmpty()) points.average().toFloat() else 0f

    // 动态缩放纵轴：计算动态跨度，预留上下 10% 缓冲，保证微小波动也能高分辨率展现
    val span = (maxVal - minVal).coerceAtLeast(0.5f)
    val renderMin = minVal - span * 0.1f
    val renderMax = maxVal + span * 0.1f

    Column(modifier = modifier.fillMaxWidth()) {
        // 纵轴动态极值与均值标注栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = String.format(Locale.US, "低: %.1f%s", minVal, unit),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format(Locale.US, "均: %.1f%s", avgVal, unit),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.SemiBold,
                color = lineColor
            )
            Text(
                text = String.format(Locale.US, "高: %.1f%s", maxVal, unit),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(vertical = 4.dp)
        ) {
            val width = size.width
            val height = size.height

            // 1. 绘制横轴时间网格线 (30s 中位虚线，60s / 0s 边界虚线)
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(0f, 0f),
                end = Offset(0f, height),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = Color.White.copy(alpha = 0.12f),
                start = Offset(width / 2f, 0f),
                end = Offset(width / 2f, height),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
            )
            drawLine(
                color = Color.White.copy(alpha = 0.08f),
                start = Offset(width, 0f),
                end = Offset(width, height),
                strokeWidth = 1.dp.toPx()
            )

            // 2. 绘制纵轴动态均值参考虚线
            val avgNormY = ((avgVal - renderMin) / (renderMax - renderMin)).coerceIn(0f, 1f)
            val avgY = height - (avgNormY * (height - 8f)) - 4f
            drawLine(
                color = lineColor.copy(alpha = 0.35f),
                start = Offset(0f, avgY),
                end = Offset(width, avgY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)
            )

            // 3. 计算 60 秒时间窗口内的 X 坐标（30 个点，间隔 2 秒，向右锚定至当前时刻）
            val maxSpanSteps = 29f
            val stepX = width / maxSpanSteps

            if (points.size == 1) {
                val normY = ((points[0] - renderMin) / (renderMax - renderMin)).coerceIn(0f, 1f)
                val y = height - (normY * (height - 8f)) - 4f
                drawCircle(
                    color = lineColor,
                    radius = 3.dp.toPx(),
                    center = Offset(width, y)
                )
                return@Canvas
            }

            val strokePath = Path()
            val fillPath = Path()

            var startX = 0f
            points.forEachIndexed { index, value ->
                // 右端 index == lastIndex 为当前 0s，向左倒推
                val x = (width - (points.lastIndex - index) * stepX).coerceAtLeast(0f)
                val normY = ((value - renderMin) / (renderMax - renderMin)).coerceIn(0f, 1f)
                val y = height - (normY * (height - 8f)) - 4f

                if (index == 0) {
                    startX = x
                    strokePath.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    strokePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }

                if (index == points.lastIndex) {
                    fillPath.lineTo(x, height)
                    fillPath.lineTo(startX, height)
                    fillPath.close()

                    // 最新末端指示光点
                    drawCircle(
                        color = lineColor,
                        radius = 3.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }

            // 绘制动态折线与渐变背景
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(fillColor, Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            drawPath(
                path = strokePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 横轴 60s / 30s / 0s 时间标注栏
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "60s",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "30s",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "0s",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
