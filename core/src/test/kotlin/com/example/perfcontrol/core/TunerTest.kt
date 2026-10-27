package com.example.perfcontrol.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class TunerTest {
    private fun ok() = FakeCommandRunner(handler = { CommandResult.Success("") })

    @Test fun animationScaleSetsThreeKeys() = runBlocking {
        val r = ok()
        SystemTuner(r).setAnimationScale("0.5")
        assertEquals(
            listOf(
                "settings put global window_animation_scale 0.5",
                "settings put global transition_animation_scale 0.5",
                "settings put global animator_duration_scale 0.5",
            ),
            r.log,
        )
    }

    @Test fun invalidAnimationScaleNeverReachesShell() = runBlocking {
        val r = ok()
        val res = SystemTuner(r).setAnimationScale("1; reboot")
        assertTrue(res is CommandResult.Failure)
        assertTrue(r.log.isEmpty())
    }

    @Test fun refreshRateSetsMinAndPeak() = runBlocking {
        val r = ok()
        SystemTuner(r).setRefreshRate(120)
        assertEquals(listOf("settings put system min_refresh_rate 120", "settings put system peak_refresh_rate 120"), r.log)
    }

    @Test fun refreshRateAutoDeletesBoth() = runBlocking {
        val r = ok()
        SystemTuner(r).setRefreshRate(null)
        assertEquals(listOf("settings delete system min_refresh_rate", "settings delete system peak_refresh_rate"), r.log)
    }

    @Test fun unsupportedRefreshRateRejected() = runBlocking {
        val r = ok()
        assertTrue(SystemTuner(r).setRefreshRate(55) is CommandResult.Failure)
        assertTrue(r.log.isEmpty())
    }

    @Test fun batterySaverReadAndWrite() = runBlocking {
        val r = FakeCommandRunner(handler = { c -> if (c == "settings get global low_power") CommandResult.Success("1") else CommandResult.Success("") })
        val t = SystemTuner(r)
        assertTrue(t.isBatterySaver())
        t.setBatterySaver(false)
        assertEquals("settings put global low_power 0", r.log.last())
    }

    @Test fun storageMaintenanceCommand() = runBlocking {
        val r = ok()
        SystemTuner(r).storageMaintenance()
        assertEquals(listOf("sm idle-maint run"), r.log)
    }

    @Test fun powerSaveEnableAndDisableRestores() = runBlocking {
        val values = mutableMapOf("system.min_refresh_rate" to "60.0")
        val store = MemoryStore()
        val r = FakeCommandRunner(handler = { c ->
            when {
                c.startsWith("settings get ") -> CommandResult.Success(values[c.removePrefix("settings get ").replace(' ', '.')] ?: "null")
                c.startsWith("settings put ") -> { val p = c.split(" "); values["${p[2]}.${p[3]}"] = p[4]; CommandResult.Success("") }
                c.startsWith("settings delete ") -> { val p = c.split(" "); values.remove("${p[2]}.${p[3]}"); CommandResult.Success("") }
                else -> CommandResult.Success("")
            }
        })
        val m = PowerSaveMode(r, store)
        assertTrue(m.enable().ok)
        assertTrue(m.isEnabled())
        assertEquals("1", values["global.low_power"])
        m.disable()
        assertFalse(m.isEnabled())
        assertNull(values["global.low_power"])
        assertEquals("60.0", values["system.min_refresh_rate"])
    }
}
