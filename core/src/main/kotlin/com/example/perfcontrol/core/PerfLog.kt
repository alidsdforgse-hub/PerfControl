package com.example.perfcontrol.core

data class LogEntry(val ts: Long, val tempC: Float, val batteryPct: Int, val ramUsedPct: Int, val charging: Boolean)

data class LogSummary(val avgTemp: Float, val maxTemp: Float, val maxTempAt: Long, val minBattery: Int, val count: Int)

object PerfLog {
    fun toLine(e: LogEntry): String = "${e.ts},${e.tempC},${e.batteryPct},${e.ramUsedPct},${if (e.charging) 1 else 0}"

    fun fromLine(line: String): LogEntry? {
        val p = line.trim().split(",")
        if (p.size != 5) return null
        return LogEntry(
            p[0].toLongOrNull() ?: return null,
            p[1].toFloatOrNull() ?: return null,
            p[2].toIntOrNull() ?: return null,
            p[3].toIntOrNull() ?: return null,
            p[4] == "1",
        )
    }

    fun parse(text: String): List<LogEntry> = text.lineSequence().mapNotNull { fromLine(it) }.toList()

    fun prune(entries: List<LogEntry>, now: Long, keepMs: Long): List<LogEntry> =
        entries.filter { now - it.ts <= keepMs }

    /** يختار حتى maxPoints نقطة موزعة بالتساوي مع الإبقاء على الأولى والأخيرة. */
    fun downsample(entries: List<LogEntry>, maxPoints: Int): List<LogEntry> {
        if (entries.size <= maxPoints || maxPoints < 2) return entries
        val step = (entries.size - 1).toDouble() / (maxPoints - 1)
        return (0 until maxPoints).map { entries[Math.round(it * step).toInt()] }
    }

    fun summary(entries: List<LogEntry>): LogSummary? {
        if (entries.isEmpty()) return null
        val hottest = entries.maxByOrNull { it.tempC }!!
        return LogSummary(
            avgTemp = entries.map { it.tempC }.average().toFloat(),
            maxTemp = hottest.tempC,
            maxTempAt = hottest.ts,
            minBattery = entries.minOf { it.batteryPct },
            count = entries.size,
        )
    }
}
