package com.example.perfcontrol.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.perfcontrol.android.AutoPrefs
import com.example.perfcontrol.android.StatsReader
import com.example.perfcontrol.core.LogEntry
import com.example.perfcontrol.core.PerfLog
import java.io.File
import java.util.concurrent.TimeUnit

object PerfLogStore {
    private const val KEEP_MS = 7L * 24 * 3600 * 1000

    private fun file(ctx: Context) = File(ctx.filesDir, "perf_log.csv")

    @Synchronized
    fun append(ctx: Context, e: LogEntry) {
        val f = file(ctx)
        f.appendText(PerfLog.toLine(e) + "\n")
        val all = PerfLog.parse(f.readText())
        if (all.size > 700) {
            val kept = PerfLog.prune(all, System.currentTimeMillis(), KEEP_MS)
            f.writeText(kept.joinToString("\n") { PerfLog.toLine(it) } + "\n")
        }
    }

    @Synchronized
    fun read(ctx: Context): List<LogEntry> {
        val f = file(ctx)
        if (!f.exists()) return emptyList()
        return PerfLog.prune(PerfLog.parse(f.readText()), System.currentTimeMillis(), KEEP_MS)
    }

    @Synchronized
    fun clear(ctx: Context) {
        file(ctx).delete()
    }

    fun sampleNow(ctx: Context) {
        val st = StatsReader(ctx).read()
        val ram = 100 - (st.ramAvailMb * 100 / st.ramTotalMb.coerceAtLeast(1L)).toInt()
        append(ctx, LogEntry(System.currentTimeMillis(), st.tempC, st.batteryPercent, ram, st.charging))
    }
}

class LogWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        runCatching { PerfLogStore.sampleNow(applicationContext) }
        return Result.success()
    }
}

object PerfLogger {
    private const val NAME = "perf_log"

    fun sync(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (AutoPrefs(ctx).logEnabled) {
            val req = PeriodicWorkRequestBuilder<LogWorker>(15, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        } else {
            wm.cancelUniqueWork(NAME)
        }
    }
}
