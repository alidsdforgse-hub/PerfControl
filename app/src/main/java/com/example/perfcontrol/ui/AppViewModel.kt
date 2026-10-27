package com.example.perfcontrol.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfcontrol.alerts.AlertNotifier
import com.example.perfcontrol.alerts.AlertPrefs
import com.example.perfcontrol.alerts.AlertScheduler
import com.example.perfcontrol.android.AppEntry
import com.example.perfcontrol.android.HealthInputReader
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val shizuku = ShizukuManager(app)
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
        val r = if (g.isEnabled()) {
            g.disable()
        } else {
            g.enable(sample.value?.processes?.sortedByDescending { it.rssKb }?.map { it.packageName } ?: emptyList())
        }
        gameEnabled.value = g.isEnabled()
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
        if (shizukuState.value == ShizukuState.Ready) {
            refreshSample()
            heavy.value = HeavyApps.select(sample.value?.processes.orEmpty(), protection())
        } else {
            heavy.value = emptyList()
        }
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
}
