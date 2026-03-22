package com.mh.icmpclient.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp

data class ChartDataPoint(
    val sequenceNumber: Int,
    val rttMs: Double?,
    val isSuccess: Boolean,
)

@Composable
fun LatencyChart(
    dataPoints: List<ChartDataPoint>,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val failColor = Color.Red
    val avgLineColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        if (dataPoints.isEmpty()) return@Canvas

        val padding = 48f
        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2

        val successPoints = dataPoints.filter { it.isSuccess && it.rttMs != null }
        if (successPoints.isEmpty()) {
            drawFailedOnly(dataPoints, padding, chartWidth, chartHeight, failColor)
            return@Canvas
        }

        val maxRtt = successPoints.maxOf { it.rttMs!! }.coerceAtLeast(1.0)
        val minRtt = successPoints.minOf { it.rttMs!! }
        val avgRtt = successPoints.map { it.rttMs!! }.average()
        val yRange = (maxRtt * 1.1).coerceAtLeast(minRtt + 1.0)

        // Grid lines
        val gridSteps = 4
        for (i in 0..gridSteps) {
            val y = padding + chartHeight - (chartHeight * i / gridSteps)
            drawLine(gridColor, Offset(padding, y), Offset(padding + chartWidth, y))
            val label = "%.0f".format(yRange * i / gridSteps)
            drawContext.canvas.nativeCanvas.drawText(
                label, 4f, y + 4f,
                android.graphics.Paint().apply {
                    textSize = 24f
                    color = android.graphics.Color.GRAY
                }
            )
        }

        // Avg reference line
        val avgY = padding + chartHeight - (chartHeight * (avgRtt / yRange)).toFloat()
        drawLine(
            avgLineColor,
            Offset(padding, avgY),
            Offset(padding + chartWidth, avgY),
            strokeWidth = 2f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(10f, 10f)
            )
        )

        // Data points and line
        val xStep = if (dataPoints.size > 1) chartWidth / (dataPoints.size - 1) else chartWidth
        val path = Path()
        var pathStarted = false

        dataPoints.forEachIndexed { index, point ->
            val x = padding + xStep * index

            if (point.isSuccess && point.rttMs != null) {
                val y = padding + chartHeight - (chartHeight * (point.rttMs / yRange)).toFloat()

                if (!pathStarted) {
                    path.moveTo(x, y)
                    pathStarted = true
                } else {
                    path.lineTo(x, y)
                }

                drawCircle(lineColor, radius = 4f, center = Offset(x, y))
            } else {
                // Failed ping — red dot at top
                drawCircle(failColor, radius = 6f, center = Offset(x, padding))
            }
        }

        drawPath(path, lineColor, style = Stroke(width = 2f))
    }
}

private fun DrawScope.drawFailedOnly(
    dataPoints: List<ChartDataPoint>,
    padding: Float,
    chartWidth: Float,
    chartHeight: Float,
    failColor: Color,
) {
    val xStep = if (dataPoints.size > 1) chartWidth / (dataPoints.size - 1) else chartWidth
    dataPoints.forEachIndexed { index, _ ->
        val x = padding + xStep * index
        drawCircle(failColor, radius = 6f, center = Offset(x, padding))
    }
}
