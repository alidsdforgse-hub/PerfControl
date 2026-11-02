package com.example.perfcontrol.core

import org.junit.Assert.*
import org.junit.Test

class GameSessionTest {
    @Test fun reportShowsDurationBatteryAndTemp() {
        val s = GameSessionReport.format(45 * 60_000L, 80, 71, 33.0f, 39.5f)
        assertTrue(s.contains("45 دقيقة"))
        assertTrue(s.contains("9%"))
        assertTrue(s.contains("39.5"))
    }

    @Test fun shortSessionAndCharging() {
        val s = GameSessionReport.format(20_000L, 50, 52, 30f, 31f)
        assertTrue(s.contains("أقل من دقيقة"))
        assertTrue(s.contains("يشحن"))
    }

    @Test fun unknownBatteryOmitted() {
        val s = GameSessionReport.format(120_000L, -1, -1, 30f, 31f)
        assertFalse(s.contains("البطارية"))
    }
}
