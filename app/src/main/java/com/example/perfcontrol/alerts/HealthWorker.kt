package com.example.perfcontrol.alerts

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.perfcontrol.android.HealthInputReader
import com.example.perfcontrol.core.HealthRater
import com.example.perfcontrol.core.notifyCooldownMs
import com.example.perfcontrol.core.shouldNotify
import java.util.concurrent.TimeUnit

class HealthWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): ListenableWorker.Result {
        val prefs = AlertPrefs(applicationContext)
        if (!prefs.enabled) return ListenableWorker.Result.success()
        val report = HealthRater.rate(HealthInputReader(applicationContext).read())
        val now = System.currentTimeMillis()
        for (issue in report.issues.filter { it.shouldNotify() }) {
            if (now - prefs.lastSent(issue.key) >= notifyCooldownMs(issue.key)) {
                AlertNotifier(applicationContext).notify(issue.key.hashCode(), issue.message)
                prefs.setLastSent(issue.key, now)
            }
        }
        return ListenableWorker.Result.success()
    }
}

object AlertScheduler {
    private const val NAME = "health_alerts"

    fun enable(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<HealthWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun disable(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(NAME)
    }
}
