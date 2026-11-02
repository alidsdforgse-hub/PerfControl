package com.example.perfcontrol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTest {
    private val p = ThermalPolicy(42f)

    @Test fun throttlesAtLimitOnly() {
        assertEquals(ThermalAction.None, p.decide(41.9f, false))
        assertEquals(ThermalAction.Throttle, p.decide(42f, false))
    }

    @Test fun restoresOnlyAfterCoolingWithHysteresis() {
        assertEquals(ThermalAction.None, p.decide(40f, true))
        assertEquals(ThermalAction.Restore, p.decide(38f, true))
    }

    @Test fun noRepeatThrottleWhileHot() {
        assertEquals(ThermalAction.None, p.decide(45f, true))
    }

    private val games = setOf("com.game")

    @Test fun enablesWhenGameOnTop() {
        assertEquals(GameAction.Enable, GameDetector.decide("com.game", games, false, false, 0))
        assertEquals(GameAction.None, GameDetector.decide("com.game", games, true, true, 0))
    }

    @Test fun disablesAfterGraceOnlyIfAutoStarted() {
        assertEquals(GameAction.None, GameDetector.decide("com.chat", games, true, true, 5_000))
        assertEquals(GameAction.Disable, GameDetector.decide("com.chat", games, true, true, 20_000))
        assertEquals(GameAction.None, GameDetector.decide("com.chat", games, true, false, 60_000))
    }

    @Test fun unknownTopDoesNothing() {
        assertEquals(GameAction.None, GameDetector.decide(null, games, false, false, 0))
    }

    @Test fun gameSettingsIncludeDndOnlyWhenAsked() {
        assertTrue(gameSettings(144, true).any { it.key == "zen_mode" })
        assertTrue(gameSettings(144, false).none { it.key == "zen_mode" })
        assertTrue(gameSettings(144, false).any { it.key == "peak_refresh_rate" && it.value == "144" })
    }
}
