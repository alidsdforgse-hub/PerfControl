package com.example.perfcontrol.background

import android.content.Context
import com.example.perfcontrol.PerfApp
import com.example.perfcontrol.android.AutoPrefs
import com.example.perfcontrol.android.DeviceInfo
import com.example.perfcontrol.android.StatsReader
import com.example.perfcontrol.core.GameSessionReport
import com.example.perfcontrol.android.PrefsStateStore
import com.example.perfcontrol.core.AppProtection
import com.example.perfcontrol.core.Booster
import com.example.perfcontrol.core.Cleaner
import com.example.perfcontrol.core.CommandResult
import com.example.perfcontrol.core.GameMode
import com.example.perfcontrol.core.MonitorRepository
import com.example.perfcontrol.core.POWERSAVE_ACTIVE_KEY
import com.example.perfcontrol.core.PowerSaveMode
import com.example.perfcontrol.core.ProcessManager
import com.example.perfcontrol.core.ProtectionContext
import com.example.perfcontrol.shizuku.ShizukuCommandRunner
import kotlinx.coroutines.delay

/** إجراءات تعمل بدون فتح التطبيق (من الإشعار أو المؤقت أو الجدولة). */
object BgActions {

    private suspend fun runner(ctx: Context): ShizukuCommandRunner? {
        val hub = (ctx.applicationContext as PerfApp).shizuku
        hub.refresh()
        var waited = 0
        while (hub.currentService() == null && waited < 40) {
            delay(200)
            waited++
        }
        if (hub.currentService() == null) return null
        return ShizukuCommandRunner({ hub.currentService() })
    }

    private suspend fun candidates(r: ShizukuCommandRunner): List<String> =
        MonitorRepository(r).sample().processes.sortedByDescending { it.rssKb }.map { it.packageName }

    private fun booster(ctx: Context, r: ShizukuCommandRunner, store: PrefsStateStore): Booster {
        val p = AppProtection(ProtectionContext(DeviceInfo(ctx).dynamicProtected(), store.exclusions()))
        return Booster(r, ProcessManager(r, p), Cleaner(r), p)
    }

    private const val NOT_READY = "Shizuku غير جاهز. شغّله ثم أعد المحاولة."

    suspend fun toggleGame(ctx: Context, forceOff: Boolean = false): String {
        val r = runner(ctx) ?: return NOT_READY
        val store = PrefsStateStore(ctx)
        val game = GameMode(r, booster(ctx, r, store), store)
        if (game.isEnabled() || forceOff) {
            if (!game.isEnabled()) return "وضع الألعاب متوقف أصلاً"
            val msg = game.disable().message
            val report = GameSession.finish(ctx)
            return if (report != null) "$msg\n$report" else msg
        }
        if (store.original(POWERSAVE_ACTIVE_KEY) != null) PowerSaveMode(r, store).disable()
        val res = game.enable(candidates(r))
        if (res.ok) GameSession.start(ctx)
        return res.message
    }

    suspend fun boost(ctx: Context): String {
        val r = runner(ctx) ?: return NOT_READY
        val store = PrefsStateStore(ctx)
        val b = booster(ctx, r, store)
        val rep = b.boost(b.plan(candidates(r)))
        return "أُوقف ${rep.batch.succeeded.size} تطبيق، وحُرّر ${rep.freedKb / 1024} ميغابايت"
    }

    suspend fun clean(ctx: Context): String {
        val r = runner(ctx) ?: return NOT_READY
        return when (val res = Cleaner(r).trimAllCaches()) {
            is CommandResult.Success -> "تم تنظيف كاش التطبيقات"
            is CommandResult.Failure -> "تعذر التنظيف: ${res.reason}"
        }
    }
}

object GameSession {
    fun start(ctx: Context) {
        val st = StatsReader(ctx).read()
        val p = AutoPrefs(ctx)
        p.sessionStart = System.currentTimeMillis()
        p.sessionBattery = st.batteryPercent
        p.sessionTemp = st.tempC
    }

    /** يعيد نص التقرير ويحفظه، أو null إن لم تكن هناك جلسة. */
    fun finish(ctx: Context): String? {
        val p = AutoPrefs(ctx)
        if (p.sessionStart <= 0L) return null
        val st = StatsReader(ctx).read()
        val text = GameSessionReport.format(
            System.currentTimeMillis() - p.sessionStart,
            p.sessionBattery, st.batteryPercent, p.sessionTemp, st.tempC,
        )
        p.lastReport = text
        p.sessionStart = 0L
        return text
    }
}
