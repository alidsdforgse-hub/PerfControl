package com.example.perfcontrol.core

data class CpuSample(val idle: Long, val total: Long)
data class MemInfo(val totalKb: Long, val availableKb: Long)
data class ProcessInfo(val pid: Int, val packageName: String, val cpuPercent: Double, val rssKb: Long)

/** "com.app:remote" -> "com.app" */
fun basePackage(processName: String): String = processName.substringBefore(':')

object ProcStatParser {
    fun parse(text: String): CpuSample? {
        val line = text.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
        val nums = line.trim().split(Regex("\\s+")).drop(1).mapNotNull { it.toLongOrNull() }
        if (nums.size < 4) return null
        val idle = nums[3] + (nums.getOrNull(4) ?: 0L)
        return CpuSample(idle = idle, total = nums.take(8).sum())
    }

    fun usagePercent(prev: CpuSample, cur: CpuSample): Double {
        val dTotal = cur.total - prev.total
        if (dTotal <= 0) return 0.0
        val dIdle = cur.idle - prev.idle
        return ((dTotal - dIdle) * 100.0 / dTotal).coerceIn(0.0, 100.0)
    }
}

object TopParser {
    private fun parseRssKb(token: String): Long? {
        val m = Regex("^([0-9]+(?:\\.[0-9]+)?)([KMGkmg]?)$").matchEntire(token) ?: return null
        val value = m.groupValues[1].toDouble()
        val factor = when (m.groupValues[2].uppercase()) {
            "M" -> 1024.0
            "G" -> 1024.0 * 1024.0
            else -> 1.0
        }
        return (value * factor).toLong()
    }

    fun parse(text: String): List<ProcessInfo> {
        val lines = text.lines()
        val headerIdx = lines.indexOfFirst { l ->
            val t = l.trim().split(Regex("\\s+"))
            t.contains("PID") && t.contains("RES")
        }
        if (headerIdx < 0) return emptyList()
        val merged = linkedMapOf<String, ProcessInfo>()
        for (line in lines.drop(headerIdx + 1)) {
            val t = line.trim().split(Regex("\\s+"))
            if (t.size < 4) continue
            val pid = t[0].toIntOrNull() ?: continue
            val rss = parseRssKb(t[1]) ?: continue
            val cpu = t[2].toDoubleOrNull() ?: continue
            val name = basePackage(t[3])
            if (!name.contains('.') || name.startsWith("/") || name.startsWith("[")) continue
            val old = merged[name]
            merged[name] = if (old == null) {
                ProcessInfo(pid, name, cpu, rss)
            } else {
                old.copy(cpuPercent = old.cpuPercent + cpu, rssKb = old.rssKb + rss)
            }
        }
        return merged.values.toList()
    }
}

object MemInfoParser {
    private fun field(text: String, key: String): Long? =
        text.lineSequence().firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.toLongOrNull()

    fun parse(text: String): MemInfo? {
        val total = field(text, "MemTotal") ?: return null
        val avail = field(text, "MemAvailable") ?: return null
        return MemInfo(total, avail)
    }

    fun freedKb(before: MemInfo, after: MemInfo): Long =
        (after.availableKb - before.availableKb).coerceAtLeast(0L)
}
