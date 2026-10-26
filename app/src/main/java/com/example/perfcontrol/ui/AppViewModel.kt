package com.example.perfcontrol.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.perfcontrol.android.AppEntry
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
}
