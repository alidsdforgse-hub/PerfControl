package com.example.perfcontrol.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun LineChart(title: String, current: String, values: List<Float>, maxValue: Float, color: Color) {
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column {
        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth()) {
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(current, color = color, fontWeight = FontWeight.Bold)
        }
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val w = size.width
            val h = size.height
            for (i in 0..2) {
                val y = h * i / 2f
                drawLine(grid, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
            }
            if (values.size >= 2) {
                val top = maxValue.coerceAtLeast(1f)
                val path = Path()
                values.forEachIndexed { i, v ->
                    val x = w * i / (values.size - 1)
                    val y = h - (v.coerceIn(0f, top) / top) * h
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color, style = Stroke(width = 4f))
            }
        }
    }
}

@Composable
fun LiveCharts(history: List<AppViewModel.ChartPoint>) {
    SoftCard {
        Text("الرسم المباشر", fontWeight = FontWeight.SemiBold)
        Caption("آخر ${history.size * 3 / 2} ثانية تقريباً")
        val last = history.lastOrNull()
        val tempMax = (history.maxOfOrNull { it.tempC } ?: 40f).coerceAtLeast(50f)
        LineChart("المعالج", last?.let { "%.0f%%".format(it.cpu) } ?: "—", history.map { it.cpu }, 100f, Good)
        LineChart("الذاكرة", last?.let { "%.0f%%".format(it.ramPct) } ?: "—", history.map { it.ramPct }, 100f, Warn)
        LineChart("الحرارة", last?.let { "%.1f°C".format(it.tempC) } ?: "—", history.map { it.tempC }, tempMax, Bad)
    }
}
