package com.example.perfcontrol.core

data class HeavyApp(val packageName: String, val rssKb: Long, val cpuPercent: Double, val reason: String)

object HeavyApps {
    fun select(
        processes: List<ProcessInfo>,
        protection: AppProtection,
        memKb: Long = 400L * 1024,
        cpu: Double = 10.0,
        limit: Int = 10,
    ): List<HeavyApp> =
        processes
            .filter { !protection.isProtected(it.packageName) && (it.rssKb >= memKb || it.cpuPercent >= cpu) }
            .map {
                val reason = when {
                    it.rssKb >= memKb && it.cpuPercent >= cpu -> "ذاكرة ومعالج"
                    it.rssKb >= memKb -> "ذاكرة عالية"
                    else -> "معالج عالٍ"
                }
                HeavyApp(it.packageName, it.rssKb, it.cpuPercent, reason)
            }
            .sortedByDescending { it.rssKb / 1024.0 + it.cpuPercent * 50 }
            .take(limit)
}
