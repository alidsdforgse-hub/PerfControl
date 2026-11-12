package com.example.perfcontrol.core

import org.junit.Assert.*
import org.junit.Test

class BatteryLogProfilesTest {
    @Test fun chargeAlertOncePerSession() {
        assertTrue(ChargeAlertPolicy.shouldNotify(80, true, 80, false))
        assertFalse(ChargeAlertPolicy.shouldNotify(80, true, 80, true))
        assertFalse(ChargeAlertPolicy.shouldNotify(79, true, 80, false))
        assertFalse(ChargeAlertPolicy.shouldNotify(95, false, 80, false))
    }

    @Test fun chargeAlertResetsWhenUnpluggedOrDropped() {
        assertTrue(ChargeAlertPolicy.shouldReset(90, false, 80))
        assertTrue(ChargeAlertPolicy.shouldReset(70, true, 80))
        assertFalse(ChargeAlertPolicy.shouldReset(82, true, 80))
    }

    @Test fun capacityPercentSane() {
        assertEquals(96, BatteryHealthParser.capacityPercent(4800, 5000))
        assertNull(BatteryHealthParser.capacityPercent(100, 5000))
        assertNull(BatteryHealthParser.capacityPercent(null, 5000))
        assertEquals(100, BatteryHealthParser.capacityPercent(5100, 5000))
        assertEquals(4800L, BatteryHealthParser.firstNumber("4800\n"))
    }

    @Test fun logLineRoundTrip() {
        val e = LogEntry(1000L, 36.5f, 71, 64, true)
        assertEquals(e, PerfLog.fromLine(PerfLog.toLine(e)))
        assertNull(PerfLog.fromLine("garbage"))
        assertNull(PerfLog.fromLine("1,x,2,3,1"))
    }

    @Test fun pruneKeepsRecentOnly() {
        val list = listOf(LogEntry(0, 30f, 50, 50, false), LogEntry(900, 31f, 50, 50, false))
        assertEquals(1, PerfLog.prune(list, 1000, 200).size)
    }

    @Test fun downsampleKeepsEndpoints() {
        val list = (0 until 100).map { LogEntry(it.toLong(), it.toFloat(), 50, 50, false) }
        val d = PerfLog.downsample(list, 10)
        assertEquals(10, d.size)
        assertEquals(0L, d.first().ts)
        assertEquals(99L, d.last().ts)
        assertEquals(5, PerfLog.downsample(list.take(5), 10).size)
    }

    @Test fun summaryFindsHottest() {
        val s = PerfLog.summary(listOf(LogEntry(1, 30f, 80, 50, false), LogEntry(2, 44f, 40, 50, false)))!!
        assertEquals(44f, s.maxTemp, 0f)
        assertEquals(2L, s.maxTempAt)
        assertEquals(40, s.minBattery)
        assertNull(PerfLog.summary(emptyList()))
    }

    @Test fun profilePolicy() {
        val prof = mapOf("com.read" to 60)
        assertEquals(ProfileAction.Apply("com.read", 60), AppProfilePolicy.decide("com.read", prof, null, false))
        assertEquals(ProfileAction.None, AppProfilePolicy.decide("com.read", prof, "com.read", false))
        assertEquals(ProfileAction.Restore, AppProfilePolicy.decide("com.other", prof, "com.read", false))
        assertEquals(ProfileAction.None, AppProfilePolicy.decide("com.other", prof, null, false))
        assertEquals(ProfileAction.Restore, AppProfilePolicy.decide("com.read", prof, "com.read", true))
        assertEquals(ProfileAction.None, AppProfilePolicy.decide(null, prof, null, false))
    }
}
