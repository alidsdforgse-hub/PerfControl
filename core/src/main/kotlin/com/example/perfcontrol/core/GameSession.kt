package com.example.perfcontrol.core

object GameSessionReport {
    fun format(
        durationMs: Long,
        batteryStart: Int,
        batteryEnd: Int,
        tempStart: Float,
        tempEnd: Float,
    ): String {
        val minutes = (durationMs / 60_000).coerceAtLeast(0)
        val drop = if (batteryStart >= 0 && batteryEnd >= 0) batteryStart - batteryEnd else null
        return buildString {
            append("مدة الجلسة: ")
            append(if (minutes < 1) "أقل من دقيقة" else "$minutes دقيقة")
            if (drop != null) {
                append(" — ")
                append(if (drop > 0) "استهلكت البطارية $drop%" else "لم تنقص البطارية (قد يكون الجهاز يشحن)")
            }
            append(" — الحرارة من ${"%.1f".format(tempStart)} إلى ${"%.1f".format(tempEnd)}°C")
        }
    }
}
