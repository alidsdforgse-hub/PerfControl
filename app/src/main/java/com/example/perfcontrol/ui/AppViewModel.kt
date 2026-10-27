package com.example.perfcontrol.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfcontrol.alerts.AlertNotifier
import com.example.perfcontrol.alerts.AlertPrefs
import com.example.perfcontrol.alerts.AlertScheduler
import com.example.perfcontrol.android.AppEntry
import com.example.perfcontrol.android.HealthInputReader
import com.example.perfcontrol.android.UiPrefs
import com.example.perfcontrol.core.POWERSAVE_ACTIVE_KEY
import com.example.perfcontrol.core.PowerSaveMode
import com.example.perfcontrol.core.SystemTuner
import com.example.perfcontrol.core.AppOptimizer
import com.example.perfcontrol.core.GAME_ACTIVE_KEY
import com.example.perfcontrol.core.GameMode
import com.example.perfcontrol.core.HealthRater
import com.example.perfcontrol.core.HealthReport
import com.example.perfcontrol.core.HeavyApp
import com.example.perfcontrol.core.HeavyApps
import com.example.perfcontrol.android.DeviceInfo
import com.example.perfcontrol.android.OfficialApis
import com.example.perfcontrol.android.OwnCache
import com.example.perfcontrol.android.PrefsStateStore
import com.example.perfcontrol.android.StatsReader
import com.example.perfcontrol.android.SystemStats
import com.example.perfcontrol.core.AppProtection
import com.example.perfcontrol.core.Booster
import com.example.perfcontrol.core.CheckItem
import com.example.perfcontrol.core.Cleaner
import com.example.perfcontrol.core.CommandResult
import com.example.perfcontrol.core.CompatibilityChecker
import com.example.perfcontrol.core.MonitorRepository
import com.example.perfcontrol.core.MonitorSample
import com.example.perfcontrol.core.ProcessManager
import com.example.perfcontrol.core.ProtectionContext
import com.example.perfcontrol.core.ShizukuState
import com.example.perfcontrol.core.StartupManager
import com.example.perfcontrol.shizuku.ShizukuCommandRunner
import com.example.perfcontrol.shizuku.ShizukuManager
import com.example.perfcontrol.android.AutoPrefs
import com.example.perfcontrol.background.AutoScheduler
import com.example.perfcontrol.background.Shortcuts
import com.example.perfcontrol.core.BenchRow
import com.example.perfcontrol.core.BenchmarkRunner
import com.example.perfcontrol.core.FreezeManager
import com.example.perfcontrol.core.NotificationProtector
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val shizuku = (app as com.example.perfcontrol.PerfApp).shizuku
    val shizukuState: StateFlow<ShizukuState> = shizuku.state

    private val runner = ShizukuCommandRunner({ shizuku.currentService() })
    val device = DeviceInfo(app)
    val official = OfficialApis(app)
    private val ownCache = OwnCache(app)
    private val store = PrefsStateStore(app)
    private val statsReader = StatsReader(app)
    private val monitor = MonitorRepository(runner)
    private val cleaner = Cleaner(runner)
    private val startup = StartupManager(runner, store)

    val sample = MutableStateFlow<MonitorSample?>(null)
    val stats = MutableStateFlow<SystemStats?>(null)
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val ownCacheBytes = MutableStateFlow(0L)
    val startupApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val restricted = MutableStateFlow<Set<String>>(emptySet())
    val allUserApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val exclusions = MutableStateFlow<Set<String>>(emptySet())
    val compat = MutableStateFlow<List<CheckItem>?>(null)
    val fallbackApps = MutableStateFlow<List<AppEntry>>(emptyList())
    val hasUsageAccess = MutableStateFlow(false)

    init {
        shizuku.refresh()
        exclusions.value = store.exclusions()
        restricted.value = store.restrictedPackages()
    }

    fun refreshShizuku() = shizuku.refresh()
    fun requestPermission() = shizuku.requestPermission()
    fun consumeMessage() { message.value = null }

    private fun protection() = AppProtection(ProtectionContext(device.dynamicProtected(), store.exclusions()))

    private fun booster(): Booster {
        val p = protection()
        return Booster(runner, ProcessManager(runner, p), cleaner, p)
    }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } catch (e: Exception) {
                message.value = e.message ?: "حدث خطأ"
            } finally {
                busy.value = false
            }
        }
    }

    // ---- الأداء ----
    suspend fun refreshSample() {
        sample.value = monitor.sample()
        stats.value = withContext(Dispatchers.IO) { statsReader.read() }
        stats.value?.let { st ->
            val ram = 100f - (st.ramAvailMb * 100f / st.ramTotalMb.coerceAtLeast(1L))
            val point = ChartPoint((sample.value?.cpuPercent ?: 0.0).toFloat(), ram, st.tempC)
            history.value = (history.value + point).takeLast(120)
        }
        heavy.value = if (shizukuState.value == ShizukuState.Ready) {
            HeavyApps.select(sample.value?.processes.orEmpty(), protection())
        } else {
            emptyList()
        }
    }

    fun boostPlan(): List<String> =
        booster().plan(sample.value?.processes?.sortedByDescending { it.rssKb }?.map { it.packageName } ?: emptyList())

    fun boost(packages: List<String>) = launchBusy {
        val r = booster().boost(packages)
        message.value = buildString {
            append("تم إيقاف ${r.batch.succeeded.size} تطبيق")
            append("، وتحرير ${r.freedKb / 1024} ميغابايت")
            if (r.batch.failed.isNotEmpty()) append("، وفشل ${r.batch.failed.size}")
            if (!r.cacheTrimmed) append("، وتعذر تنظيف الكاش")
        }
    }

    // ---- العمليات ----
    fun forceStop(pkg: String) = launchBusy {
        val p = protection()
        val r = ProcessManager(runner, p).forceStop(listOf(pkg))
        message.value = when {
            r.skippedProtected.isNotEmpty() -> "هذا تطبيق محمي ولا يمكن إيقافه"
            r.failed.isNotEmpty() -> "فشل الإيقاف: ${r.failed.values.first()}"
            else -> "تم إيقاف التطبيق"
        }
    }

    fun loadFallback() {
        viewModelScope.launch(Dispatchers.IO) {
            val access = official.hasUsageAccess()
            hasUsageAccess.value = access
            fallbackApps.value = if (access) official.mostUsedApps() else emptyList()
        }
    }

    // ---- التنظيف ----
    fun loadCleanup() {
        viewModelScope.launch(Dispatchers.IO) { ownCacheBytes.value = ownCache.sizeBytes() }
    }

    fun clearOwnCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = ownCache.clear()
            ownCacheBytes.value = ownCache.sizeBytes()
            message.value = "تم تحرير ${freed / 1024 / 1024} ميغابايت من كاش التطبيق"
        }
    }

    fun trimAll() = launchBusy {
        message.value = when (val r = cleaner.trimAllCaches()) {
            is CommandResult.Success -> "تم تنظيف كاش التطبيقات"
            is CommandResult.Failure -> "تعذر التنظيف: ${r.reason}"
        }
    }

    // ---- بدء التشغيل ----
    fun loadStartup() {
        viewModelScope.launch(Dispatchers.IO) {
            startupApps.value = device.bootCapableApps()
            restricted.value = store.restrictedPackages()
        }
    }

    fun setRestricted(pkg: String, on: Boolean) = launchBusy {
        val r = if (on) startup.restrict(pkg) else startup.restore(pkg)
        message.value = r.message
        restricted.value = store.restrictedPackages()
    }

    // ---- الإعدادات ----
    fun runCompat() = launchBusy {
        compat.value = CompatibilityChecker(runner, getApplication<Application>().packageName).run()
    }

    fun loadExclusions() {
        exclusions.value = store.exclusions()
        viewModelScope.launch(Dispatchers.IO) { allUserApps.value = device.userApps() }
    }

    fun toggleExclusion(pkg: String, on: Boolean) {
        val s = store.exclusions().toMutableSet()
        if (on) s += pkg else s -= pkg
        store.setExclusions(s)
        exclusions.value = s
    }

    // ---- ميزات جديدة ----
    private val longRunner = ShizukuCommandRunner({ shizuku.currentService() }, 30 * 60 * 1000L)
    private val healthReader = HealthInputReader(app)
    private val alertPrefs = AlertPrefs(app)

    val gameEnabled = MutableStateFlow(store.original(GAME_ACTIVE_KEY) != null)
    val health = MutableStateFlow<HealthReport?>(null)
    val heavy = MutableStateFlow<List<HeavyApp>>(emptyList())
    val uptimeHours = MutableStateFlow(0L)
    val alertsEnabled = MutableStateFlow(alertPrefs.enabled)

    fun notifyUser(text: String) { message.value = text }
    fun selfPackage(): String = getApplication<Application>().packageName

    fun toggleGame() = launchBusy {
        val p = protection()
        val booster = Booster(runner, ProcessManager(runner, p), cleaner, p)
        val g = GameMode(runner, booster, store)
        if (!g.isEnabled()) disablePowerSaveIfActive()
        val r = if (g.isEnabled()) {
            g.disable()
        } else {
            g.enable(sample.value?.processes?.sortedByDescending { it.rssKb }?.map { it.packageName } ?: emptyList())
        }
        gameEnabled.value = g.isEnabled()
        if (g.isEnabled()) {
            AutoScheduler.startGameTimer(getApplication(), autoPrefs.gameMinutes)
        } else {
            AutoScheduler.cancelGameTimer(getApplication())
        }
        message.value = r.message
    }

    fun optimizeApps() = launchBusy {
        message.value = "بدأ تحسين التطبيقات، وقد يستغرق عدة دقائق. أبقِ التطبيق مفتوحاً."
        message.value = when (val r = AppOptimizer(longRunner).optimizeAll()) {
            is CommandResult.Success -> "اكتمل تحسين التطبيقات"
            is CommandResult.Failure -> "تعذر التحسين: ${r.reason}"
        }
    }

    suspend fun refreshHealth() {
        val input = withContext(Dispatchers.IO) { healthReader.read() }
        health.value = HealthRater.rate(input)
        uptimeHours.value = input.uptimeHours
    }

    fun stopHeavy(pkgs: List<String>) = launchBusy {
        val r = ProcessManager(runner, protection()).forceStop(pkgs)
        message.value = "تم إيقاف ${r.succeeded.size} تطبيق" +
            (if (r.failed.isNotEmpty()) "، وفشل ${r.failed.size}" else "")
    }

    fun setAlerts(on: Boolean) {
        alertPrefs.enabled = on
        alertsEnabled.value = on
        if (on) AlertScheduler.enable(getApplication()) else AlertScheduler.disable(getApplication())
    }

    fun testAlert() {
        AlertNotifier(getApplication()).notify(999, "هذا تنبيه تجريبي: التنبيهات تعمل بنجاح")
    }

    // ---- خيارات التحسين والمظهر ----
    private val tuner = SystemTuner(runner)
    private val uiPrefs = UiPrefs(app)

    val themeMode = MutableStateFlow(uiPrefs.themeMode)
    val animChoice = MutableStateFlow<Int?>(null)
    val refreshChoice = MutableStateFlow<Int?>(null)
    val batterySaver = MutableStateFlow(false)
    val powerSaveEnabled = MutableStateFlow(store.original(POWERSAVE_ACTIVE_KEY) != null)

    fun setTheme(mode: Int) {
        uiPrefs.themeMode = mode
        themeMode.value = mode
    }

    private suspend fun disablePowerSaveIfActive() {
        val ps = PowerSaveMode(runner, store)
        if (ps.isEnabled()) {
            ps.disable()
            powerSaveEnabled.value = false
        }
    }

    fun togglePowerSave() = launchBusy {
        val ps = PowerSaveMode(runner, store)
        val r = if (ps.isEnabled()) {
            ps.disable()
        } else {
            val p = protection()
            val g = GameMode(runner, Booster(runner, ProcessManager(runner, p), cleaner, p), store)
            if (g.isEnabled()) {
                g.disable()
                gameEnabled.value = false
            }
            ps.enable()
        }
        powerSaveEnabled.value = ps.isEnabled()
        message.value = r.message
    }

    fun loadTune() {
        loadCleanup()
        if (shizukuState.value != ShizukuState.Ready) return
        viewModelScope.launch {
            animChoice.value = when (tuner.animationScale()?.toFloatOrNull()) {
                0f -> 0
                0.5f -> 1
                1f -> 2
                else -> if (tuner.animationScale() == "null") 2 else null
            }
            refreshChoice.value = when (val v = tuner.peakRefreshRate()) {
                "null" -> 0
                else -> when (v?.toFloatOrNull()?.toInt()) {
                    60 -> 1
                    90 -> 2
                    120 -> 3
                    else -> null
                }
            }
            batterySaver.value = tuner.isBatterySaver()
        }
    }

    fun setAnim(choice: Int) = launchBusy {
        val value = listOf("0", "0.5", "1")[choice]
        val r = tuner.setAnimationScale(value)
        if (r is CommandResult.Success) animChoice.value = choice
        message.value = if (r is CommandResult.Success) "تم ضبط سرعة الرسوم" else "تعذر الضبط: ${(r as CommandResult.Failure).reason}"
    }

    fun setRefresh(choice: Int) = launchBusy {
        val hz = listOf<Int?>(null, 60, 90, 120)[choice]
        val r = tuner.setRefreshRate(hz)
        if (r is CommandResult.Success) refreshChoice.value = choice
        message.value = if (r is CommandResult.Success) "تم ضبط معدل التحديث" else "تعذر الضبط: ${(r as CommandResult.Failure).reason}"
    }

    fun setBatterySaver(on: Boolean) = launchBusy {
        val r = tuner.setBatterySaver(on)
        if (r is CommandResult.Success) batterySaver.value = on
        message.value = if (r is CommandResult.Success) {
            if (on) "تم تشغيل توفير الطاقة" else "تم إيقاف توفير الطاقة"
        } else {
            "تعذر الضبط: ${(r as CommandResult.Failure).reason}"
        }
    }

    fun maintainStorage() = launchBusy {
        message.value = "بدأت صيانة التخزين..."
        message.value = when (val r = tuner.storageMaintenance()) {
            is CommandResult.Success -> "اكتملت صيانة التخزين"
            is CommandResult.Failure -> "تعذرت الصيانة: ${r.reason}"
        }
    }

    // ---- ميزات الإصدار 1.3 ----
    data class ChartPoint(val cpu: Float, val ramPct: Float, val tempC: Float)

    private val autoPrefs = AutoPrefs(app)
    val history = MutableStateFlow<List<ChartPoint>>(emptyList())
    val gameMinutes = MutableStateFlow(autoPrefs.gameMinutes)
    val cleanHours = MutableStateFlow(autoPrefs.cleanHours)
    val shortcutsOn = MutableStateFlow(autoPrefs.shortcuts)
    val frozen = MutableStateFlow<Set<String>>(emptySet())
    val notifProtected = MutableStateFlow<Set<String>>(emptySet())
    val benchRows = MutableStateFlow<List<BenchRow>?>(null)
    val benchStage = MutableStateFlow<String?>(null)

    fun setGameMinutes(m: Int) {
        autoPrefs.gameMinutes = m
        gameMinutes.value = m
        if (gameEnabled.value) {
            if (m > 0) AutoScheduler.startGameTimer(getApplication(), m) else AutoScheduler.cancelGameTimer(getApplication())
        }
    }

    fun setCleanHours(h: Int) {
        autoPrefs.cleanHours = h
        cleanHours.value = h
        AutoScheduler.setAutoClean(getApplication(), h)
        message.value = if (h > 0) "سيُنظَّف الكاش تلقائياً كل $h ساعة" else "تم إيقاف التنظيف التلقائي"
    }

    fun setShortcuts(on: Boolean) {
        autoPrefs.shortcuts = on
        shortcutsOn.value = on
        if (on) Shortcuts.show(getApplication()) else Shortcuts.hide(getApplication())
    }

    fun loadFreeze() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { allUserApps.value = device.userApps() }
            frozen.value = FreezeManager(runner, protection()).frozen() ?: emptySet()
        }
    }

    fun setFrozen(pkg: String, on: Boolean) = launchBusy {
        val m = FreezeManager(runner, protection())
        val r = if (on) m.freeze(pkg) else m.unfreeze(pkg)
        message.value = r.message
        frozen.value = m.frozen() ?: frozen.value
    }

    fun loadProtect() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { allUserApps.value = device.userApps() }
            notifProtected.value = NotificationProtector(runner).protectedApps() ?: emptySet()
        }
    }

    fun setNotifProtected(pkg: String, on: Boolean) = launchBusy {
        val p = NotificationProtector(runner)
        val r = if (on) p.protect(pkg) else p.unprotect(pkg)
        message.value = r.message
        if (r.ok) toggleExclusion(pkg, on)
        notifProtected.value = p.protectedApps() ?: notifProtected.value
    }

    fun runBenchmark(pkg: String?) = launchBusy {
        benchRows.value = null
        val self = selfPackage() + "/com.example.perfcontrol.ui.MainActivity"
        val bench = BenchmarkRunner(runner, self)
        try {
            benchStage.value = "قياس الحالة قبل التحسين..."
            val before = bench.snapshot(pkg)
            benchStage.value = "جارٍ التحسين..."
            val b = booster()
            b.boost(b.plan(sample.value?.processes?.sortedByDescending { it.rssKb }?.map { it.packageName } ?: emptyList()))
            delay(2500)
            benchStage.value = "قياس الحالة بعد التحسين..."
            val after = bench.snapshot(pkg)
            val rows = bench.compare(before, after)
            benchRows.value = rows
            if (rows.isEmpty()) message.value = "تعذر القياس. تحتاج Shizuku جاهزاً."
        } finally {
            bench.returnToSelf()
            benchStage.value = null
        }
    }
}
