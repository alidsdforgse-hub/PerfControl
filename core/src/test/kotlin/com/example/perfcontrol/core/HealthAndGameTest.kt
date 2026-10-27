package com.example.perfcontrol.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class HealthAndGameTest {
    private val ok = HealthInput(uptimeHours = 5, ramAvailPercent = 50, storageFreePercent = 50, tempC = 30f, batteryHealthGood = true)

    @Test fun healthyDeviceScoresFullWithNoIssues() {
        val r = HealthRater.rate(ok)
        assertEquals(100, r.score)
        assertTrue(r.issues.isEmpty())
    }

    @Test fun hotDeviceIsCritical() {
        val r = HealthRater.rate(ok.copy(tempC = 46f))
        assertEquals(Severity.Critical, r.issues.single { it.key == "temp" }.severity)
    }

    @Test fun lowRamAndStorageAreCritical() {
        val r = HealthRater.rate(ok.copy(ramAvailPercent = 5, storageFreePercent = 3))
        assertEquals(setOf("ram", "storage"), r.issues.map { it.key }.toSet())
        assertTrue(r.issues.all { it.severity == Severity.Critical })
    }

    @Test fun longUptimeWarnsAndNotifies() {
        val issue = HealthRater.rate(ok.copy(uptimeHours = 80)).issues.single()
        assertEquals("uptime", issue.key)
        assertTrue(issue.shouldNotify())
    }

    @Test fun mildTempDoesNotNotify() {
        val issue = HealthRater.rate(ok.copy(tempC = 41f)).issues.single()
        assertFalse(issue.shouldNotify())
    }

    @Test fun scoreNeverBelowZero() {
        val r = HealthRater.rate(HealthInput(500, 1, 1, 60f, false))
        assertEquals(0, r.score)
        assertEquals("سيئة", r.label)
    }

    @Test fun cooldownsDiffer() {
        assertTrue(notifyCooldownMs("uptime") > notifyCooldownMs("temp"))
    }

    @Test fun heavyAppsSkipProtectedAndSmallOnes() {
        val prot = AppProtection(ProtectionContext(emptySet(), emptySet()))
        val procs = listOf(
            ProcessInfo(1, "com.android.systemui", 50.0, 900L * 1024),
            ProcessInfo(2, "com.big", 1.0, 500L * 1024),
            ProcessInfo(3, "com.busy", 30.0, 50L * 1024),
            ProcessInfo(4, "com.small", 1.0, 50L * 1024),
        )
        val r = HeavyApps.select(procs, prot)
        assertEquals(setOf("com.big", "com.busy"), r.map { it.packageName }.toSet())
    }

    private val prot = AppProtection(ProtectionContext(emptySet(), emptySet()))
    private fun mk(r: FakeCommandRunner, store: MemoryStore) =
        GameMode(r, Booster(r, ProcessManager(r, prot), Cleaner(r), prot), store)

    private fun settingsRunner(values: MutableMap<String, String>) = FakeCommandRunner(handler = { c ->
        when {
            c.startsWith("settings get ") -> CommandResult.Success(values[c.removePrefix("settings get ").replace(' ', '.')] ?: "null")
            c.startsWith("settings put ") -> {
                val p = c.split(" ")
                values["${p[2]}.${p[3]}"] = p[4]
                CommandResult.Success("")
            }
            c.startsWith("settings delete ") -> {
                val p = c.split(" ")
                values.remove("${p[2]}.${p[3]}")
                CommandResult.Success("")
            }
            c == "cat /proc/meminfo" -> CommandResult.Success("MemTotal: 100 kB\nMemAvailable: 10 kB")
            else -> CommandResult.Success("")
        }
    })

    @Test fun gameModeEnableAppliesAndSavesOriginals() = runBlocking {
        val values = mutableMapOf("global.window_animation_scale" to "1.0")
        val store = MemoryStore()
        val g = mk(settingsRunner(values), store)
        val res = g.enable(listOf("com.game"))
        assertTrue(res.ok)
        assertTrue(g.isEnabled())
        assertEquals("1.0", store.original("game:global.window_animation_scale"))
        assertEquals("0.5", values["global.window_animation_scale"])
        assertEquals("120", values["system.peak_refresh_rate"])
    }

    @Test fun gameModeDisableRestoresAndDeletesUnset() = runBlocking {
        val values = mutableMapOf("global.window_animation_scale" to "1.0")
        val store = MemoryStore()
        val r = settingsRunner(values)
        val g = mk(r, store)
        g.enable(emptyList())
        g.disable()
        assertFalse(g.isEnabled())
        assertEquals("1.0", values["global.window_animation_scale"])
        assertNull(values["system.peak_refresh_rate"])
        assertTrue(r.log.contains("settings delete system peak_refresh_rate"))
    }

    @Test fun optimizerRunsExactCommand() = runBlocking {
        val r = FakeCommandRunner(handler = { CommandResult.Success("") })
        AppOptimizer(r).optimizeAll()
        assertEquals(listOf("cmd package compile -m speed-profile -a"), r.log)
    }
}
