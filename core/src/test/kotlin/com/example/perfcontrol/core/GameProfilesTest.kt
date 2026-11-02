package com.example.perfcontrol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameProfilesTest {
    private val rates = listOf(60, 90, 120, 144)

    @Test fun coolIs60WithoutBoost() {
        val p = GameProfiles.forMode(PerfMode.Cool, rates)
        assertEquals(60, p.hz)
        assertFalse(p.boost)
    }

    @Test fun balancedPicksNearestBelow90() {
        assertEquals(90, GameProfiles.forMode(PerfMode.Balanced, rates).hz)
        assertEquals(60, GameProfiles.forMode(PerfMode.Balanced, listOf(60, 120)).hz)
    }

    @Test fun performanceUsesDeviceMax() {
        assertEquals(144, GameProfiles.forMode(PerfMode.Performance, rates).hz)
        assertTrue(GameProfiles.forMode(PerfMode.Performance, rates).boost)
    }

    @Test fun extremeDisablesAnimations() {
        assertEquals("0", GameProfiles.forMode(PerfMode.Extreme, rates).anim)
    }

    @Test fun lowOnlyDisplayStillWorks() {
        assertEquals(60, GameProfiles.forMode(PerfMode.Cool, listOf(60)).hz)
        assertEquals(60, GameProfiles.forMode(PerfMode.Extreme, listOf(60)).hz)
    }
}
