package com.example.perfcontrol.core

data class BenchSnapshot(val ramAvailKb: Long?, val appCount: Int?, val startMs: Long?)

enum class Verdict { Better, Same, Worse }

data class BenchRow(val label: String, val before: String, val after: String, val verdict: Verdict)

object BenchParsers {
    /** عدد عمليات التطبيقات (أسماء بحزم مثل com.x) من ناتج ps -A -o NAME */
    fun processCount(out: String): Int = out.lineSequence()
        .map { it.trim() }
        .count { it.contains('.') && !it.startsWith("[") && !it.startsWith("/") && !it.contains(' ') }

    /** "TotalTime: 523" -> 523 */
    fun startTotalMs(out: String): Long? =
        Regex("TotalTime:\\s*(\\d+)").find(out)?.groupValues?.get(1)?.toLongOrNull()
}

class BenchmarkRunner(private val runner: CommandRunner, private val selfComponent: String) {

    suspend fun snapshot(pkg: String?, runs: Int = 3): BenchSnapshot {
        val ram = (runner.run("cat /proc/meminfo") as? CommandResult.Success)
            ?.let { MemInfoParser.parse(it.output) }?.availableKb
        val count = (runner.run("ps -A -o NAME") as? CommandResult.Success)
            ?.let { BenchParsers.processCount(it.output) }
        var start: Long? = null
        if (pkg != null && isValidPackageName(pkg)) {
            val times = (1..runs).mapNotNull {
                (runner.run("am start -W -S -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $pkg")
                    as? CommandResult.Success)?.let { r -> BenchParsers.startTotalMs(r.output) }
            }
            if (times.isNotEmpty()) start = times.sum() / times.size
            runner.run("am force-stop $pkg")
        }
        return BenchSnapshot(ram, count, start)
    }

    suspend fun returnToSelf() {
        runner.run("am start -n $selfComponent")
    }

    private fun judge(before: Double, after: Double, higherIsBetter: Boolean, threshold: Double): Verdict {
        if (before <= 0.0) return Verdict.Same
        val change = (after - before) / before
        return when {
            kotlin.math.abs(change) < threshold -> Verdict.Same
            (change > 0) == higherIsBetter -> Verdict.Better
            else -> Verdict.Worse
        }
    }

    fun compare(b: BenchSnapshot, a: BenchSnapshot): List<BenchRow> {
        val rows = mutableListOf<BenchRow>()
        val rb = b.ramAvailKb
        val ra = a.ramAvailKb
        if (rb != null && ra != null) {
            rows += BenchRow("الذاكرة المتاحة", "${rb / 1024} MB", "${ra / 1024} MB", judge(rb.toDouble(), ra.toDouble(), true, 0.02))
        }
        val cb = b.appCount
        val ca = a.appCount
        if (cb != null && ca != null) {
            rows += BenchRow("عمليات التطبيقات", "$cb", "$ca", judge(cb.toDouble(), ca.toDouble(), false, 0.02))
        }
        val sb = b.startMs
        val sa = a.startMs
        if (sb != null && sa != null) {
            rows += BenchRow("سرعة فتح التطبيق", "$sb ms", "$sa ms", judge(sb.toDouble(), sa.toDouble(), false, 0.05))
        }
        return rows
    }
}
