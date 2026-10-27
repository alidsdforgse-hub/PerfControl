package com.example.perfcontrol.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.perfcontrol.alerts.AlertNotifier
import java.util.concurrent.TimeUnit

class GameTimerWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val msg = BgActions.toggleGame(applicationContext, forceOff = true)
        if (msg.startsWith("Shizuku") && runAttemptCount < 3) return Result.retry()
        AlertNotifier(applicationContext).notify(7001, "انتهى مؤقت وضع الألعاب. $msg")
        return Result.success()
    }
}

class AutoCleanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val msg = BgActions.clean(applicationContext)
        if (msg.startsWith("Shizuku")) return Result.success() // سيُعاد في الدورة القادمة
        return Result.success()
    }
}

object AutoScheduler {
    private const val GAME = "game_timer"
    private const val CLEAN = "auto_clean"

    fun startGameTimer(ctx: Context, minutes: Int) {
        if (minutes <= 0) return
        val req = OneTimeWorkRequestBuilder<GameTimerWorker>()
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(GAME, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelGameTimer(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(GAME)
    }

    fun setAutoClean(ctx: Context, hours: Int) {
        val wm = WorkManager.getInstance(ctx)
        if (hours <= 0) {
            wm.cancelUniqueWork(CLEAN)
            return
        }
        val req = PeriodicWorkRequestBuilder<AutoCleanWorker>(hours.toLong(), TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(CLEAN, ExistingPeriodicWorkPolicy.UPDATE, req)
    }
}
