package com.example.perfcontrol.core

data class BoostReport(
    val freedKb: Long,
    val batch: BatchResult,
    val cacheTrimmed: Boolean,
)

class Booster(
    private val runner: CommandRunner,
    private val processes: ProcessManager,
    private val cleaner: Cleaner,
    private val protection: AppProtection,
) {
    /** التطبيقات التي ستُوقَف فعلاً (بعد استبعاد المحمي) ليعرضها التطبيق للتأكيد. */
    fun plan(candidates: List<String>): List<String> =
        candidates.map(::basePackage).distinct().filter { !protection.isProtected(it) && isValidPackageName(it) }

    private suspend fun readMem(): MemInfo? =
        (runner.run("cat /proc/meminfo") as? CommandResult.Success)?.let { MemInfoParser.parse(it.output) }

    suspend fun boost(packages: List<String>): BoostReport {
        val before = readMem()
        val batch = processes.forceStop(plan(packages))
        val trimmed = cleaner.trimAllCaches() is CommandResult.Success
        val after = readMem()
        val freed = if (before != null && after != null) MemInfoParser.freedKb(before, after) else 0L
        return BoostReport(freed, batch, trimmed)
    }
}
