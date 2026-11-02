package com.example.perfcontrol.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvisorTest {
    private fun snap(
        ram: Int = 50, temp: Float = 33f, bat: Int = 80, charging: Boolean = false, storage: Int = 50,
        up: Long = 10, heavy: List<String> = emptyList(), ready: Boolean = true,
        game: Boolean = false, save: Boolean = false, thermal: Boolean = false,
    ) = DeviceSnapshot(ram, temp, bat, charging, storage, up, heavy, ready, game, save, thermal)

    @Test fun topicsRouteByKeyword() {
        assertEquals(Topic.Slow, Advisor.topic("جهازي بطيء ولاق"))
        assertEquals(Topic.Heat, Advisor.topic("ليش الجوال يسخن؟"))
        assertEquals(Topic.Battery, Advisor.topic("البطارية تفرغ بسرعة"))
        assertEquals(Topic.Game, Advisor.topic("جهزني للعب ببجي"))
        assertEquals(Topic.Storage, Advisor.topic("المساحة ممتلئة"))
        assertEquals(Topic.General, Advisor.topic("مرحبا"))
    }

    @Test fun healthyDeviceReportsAllGood() {
        val r = Advisor.diagnose(snap())
        assertTrue(r.text.contains("بخير"))
        assertTrue(r.actions.isEmpty())
    }

    @Test fun fullRamAndHeavyAppsSuggestActions() {
        val r = Advisor.diagnose(snap(ram = 90, heavy = listOf("Chrome")))
        assertTrue(r.actions.contains(AdvisorAction.Boost))
        assertTrue(r.actions.contains(AdvisorAction.StopHeavy))
        assertTrue(r.text.contains("Chrome"))
    }

    @Test fun noActionsWithoutShizukuButExplains() {
        val r = Advisor.diagnose(snap(ram = 90, ready = false))
        assertTrue(r.actions.isEmpty())
        assertTrue(r.text.contains("Shizuku"))
    }

    @Test fun heatSuggestsThermalGuardOnlyWhenOff() {
        assertTrue(Advisor.answer("حرارة", snap(temp = 45f)).actions.contains(AdvisorAction.ThermalGuard))
        assertFalse(Advisor.answer("حرارة", snap(temp = 45f, thermal = true)).actions.contains(AdvisorAction.ThermalGuard))
    }

    @Test fun gameSuggestsGameModeUnlessOn() {
        assertTrue(Advisor.answer("للعب", snap()).actions.contains(AdvisorAction.GameMode))
        assertFalse(Advisor.answer("للعب", snap(game = true)).actions.contains(AdvisorAction.GameMode))
    }

    @Test fun longUptimeAdvisesRestart() {
        assertTrue(Advisor.diagnose(snap(up = 100)).text.contains("إعادة"))
    }

    @Test fun lowBatterySuggestsPowerSave() {
        assertTrue(Advisor.diagnose(snap(bat = 10)).actions.contains(AdvisorAction.PowerSave))
        assertFalse(Advisor.diagnose(snap(bat = 10, charging = true)).actions.contains(AdvisorAction.PowerSave))
    }
}
