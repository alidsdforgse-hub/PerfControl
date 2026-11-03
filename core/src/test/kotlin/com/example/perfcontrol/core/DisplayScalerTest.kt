package com.example.perfcontrol.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DisplayScalerTest {
    @Test fun parsesPhysicalOverSizeOverride() {
        val out = "Physical size: 1220x2712\nOverride size: 900x2000"
        assertEquals(1220 to 2712, DisplayParsers.physicalSize(out))
        assertEquals(900 to 2000, DisplayParsers.overrideSize(out))
        assertNull(DisplayParsers.overrideSize("Physical size: 1220x2712"))
    }

    @Test fun parsesDensityAndGpu() {
        assertEquals(446, DisplayParsers.physicalDensity("Physical density: 446"))
        assertEquals(16, DisplayParsers.gpuBusy("16 %"))
        assertNull(DisplayParsers.gpuBusy("abc"))
    }

    private fun runner(state: MutableMap<String, String>) = FakeCommandRunner(handler = { c ->
        when {
            c == "wm size" -> CommandResult.Success(
                "Physical size: 1000x2000" + (state["size"]?.let { "\nOverride size: $it" } ?: ""),
            )
            c == "wm density" -> CommandResult.Success("Physical density: 400")
            c.startsWith("wm size reset") -> { state.remove("size"); CommandResult.Success("") }
            c.startsWith("wm size ") -> { state["size"] = c.removePrefix("wm size "); CommandResult.Success("") }
            else -> CommandResult.Success("")
        }
    })

    @Test fun appliesScaledSizeAndDensityAndVerifies() = runBlocking {
        val st = mutableMapOf<String, String>()
        val r = runner(st)
        val store = MemoryStore()
        val res = DisplayScaler(r, store).apply(80)
        assertTrue(res.message, res.ok)
        assertEquals("800x1600", st["size"])
        assertTrue(r.log.contains("wm density 320"))
        assertTrue(DisplayScaler(r, store).isActive())
    }

    @Test fun resetClearsState() = runBlocking {
        val st = mutableMapOf<String, String>()
        val store = MemoryStore()
        val s = DisplayScaler(runner(st), store)
        s.apply(75)
        assertTrue(s.reset().ok)
        assertFalse(s.isActive())
        assertNull(st["size"])
    }

    @Test fun refusesBelowFiftyAndHundredResets() = runBlocking {
        val st = mutableMapOf<String, String>()
        val s = DisplayScaler(runner(st), MemoryStore())
        assertFalse(s.apply(40).ok)
        assertTrue(s.apply(100).ok)
    }

    @Test fun failsWhenOverrideDidNotApply() = runBlocking {
        val r = FakeCommandRunner(handler = { c ->
            when (c) {
                "wm size" -> CommandResult.Success("Physical size: 1000x2000")
                "wm density" -> CommandResult.Success("Physical density: 400")
                else -> CommandResult.Success("")
            }
        })
        val store = MemoryStore()
        assertFalse(DisplayScaler(r, store).apply(80).ok)
        assertFalse(DisplayScaler(r, store).isActive())
    }
}
