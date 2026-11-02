package com.example.perfcontrol.background

import android.content.Context
import com.example.perfcontrol.PerfApp
import com.example.perfcontrol.android.AutoPrefs
import com.example.perfcontrol.android.DeviceInfo
import com.example.perfcontrol.android.StatsReader
import com.example.perfcontrol.core.GameSessionReport
import com.example.perfcontrol.core.HeavyApps
import com.example.perfcontrol.core.SettingChange
import com.example.perfcontrol.core.SettingsApplier
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

    suspend fun runnerFor(ctx: Context): ShizukuCommandRunner? = runner(ctx)

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

    private suspend fun candidates(r: ShizukuCommandRunner, keep: String? = null): List<String> =
        MonitorRepository(r).sample().processes.sortedByDescending { it.rssKb }.map { it.packageName }
            .filter { keep == null || it != keep }

    private fun booster(ctx: Context, r: ShizukuCommandRunner, store: PrefsStateStore): Booster {
        val p = AppProtection(ProtectionContext(DeviceInfo(ctx).dynamicProtected(), store.exclusions()))
        return Booster(r, ProcessManager(r, p), Cleaner(r), p)
    }

    private const val NOT_READY = "Shizuku غير جاهز. شغّله ثم أعد المحاولة."

    suspend fun toggleGame(ctx: Context, forceOff: Boolean = false, keep: String? = null): String {
        val r = runner(ctx) ?: return NOT_READY
        val store = PrefsStateStore(ctx)
        val prefs = AutoPrefs(ctx)
        val hz = DeviceInfo(ctx).supportedRefreshRates().maxOrNull() ?: 120
        val game = GameMode(r, booster(ctx, r, store), store, hz, prefs.gameDnd)
        if (game.isEnabled() || forceOff) {
            if (!game.isEnabled()) return "وضع الألعاب متوقف أصلاً"
            restoreThermal(r, store)
            val msg = game.disable().message
            val report = GameSession.finish(ctx)
            return if (report != null) "$msg\n$report" else msg
        }
        if (store.original(POWERSAVE_ACTIVE_KEY) != null) PowerSaveMode(r, store).disable()
        val res = game.enable(candidates(r, keep))
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

/** حارس الحرارة: تخفيض معدل التحديث وإيقاف التطبيقات الثقيلة، ثم الإعادة بعد التبريد. */
suspend fun BgActions.restoreThermal(r: ShizukuCommandRunner, store: PrefsStateStore) {
    val a = SettingsApplier(r, store, "thermal")
    if (a.isActive()) {
        a.restore(THERMAL_SETTINGS)
        a.markActive(false)
    }
}

val THERMAL_SETTINGS = listOf(
    SettingChange("system", "min_refresh_rate", "60"),
    SettingChange("system", "peak_refresh_rate", "60"),
)

suspend fun BgActions.thermalThrottle(ctx: Context, keep: String?): String {
    val r = BgActions.runnerFor(ctx) ?: return "Shizuku غير جاهز"
    val store = PrefsStateStore(ctx)
    val a = SettingsApplier(r, store, "thermal")
    val applied = a.apply(THERMAL_SETTINGS)
    a.markActive(true)
    val p = AppProtection(ProtectionContext(DeviceInfo(ctx).dynamicProtected(), store.exclusions()))
    val heavy = HeavyApps.select(MonitorRepository(r).sample().processes, p)
        .map { it.packageName }.filter { it != keep }
    val stopped = if (heavy.isNotEmpty()) ProcessManager(r, p).forceStop(heavy).succeeded.size else 0
    return "ارتفعت الحرارة: خُفّض معدل التحديث ($applied إعداد) وأُوقف $stopped تطبيق ثقيل"
}

suspend fun BgActions.thermalRelease(ctx: Context): String {
    val r = BgActions.runnerFor(ctx) ?: return "Shizuku غير جاهز"
    restoreThermal(r, PrefsStateStore(ctx))
    return "انخفضت الحرارة وأُعيد معدل التحديث"
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
