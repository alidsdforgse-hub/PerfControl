package com.example.perfcontrol.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StartupManagerTest {
    private val get = "cmd appops get com.a RUN_ANY_IN_BACKGROUND"
    private val setIgnore = "cmd appops set com.a RUN_ANY_IN_BACKGROUND ignore"
    private val setDefault = "cmd appops set com.a RUN_ANY_IN_BACKGROUND default"

    @Test fun restrictSavesOriginalThenSetsIgnoreThenVerifies() = runBlocking {
        var mode = "allow"
        val r = FakeCommandRunner(handler = { c ->
            when (c) {
                get -> CommandResult.Success("RUN_ANY_IN_BACKGROUND: $mode")
                setIgnore -> { mode = "ignore"; CommandResult.Success("") }
                else -> null
            }
        })
        val store = MemoryStore()
        val res = StartupManager(r, store).restrict("com.a")
        assertTrue(res.ok)
        assertEquals("allow", store.original("com.a"))
    }

    @Test fun restrictFailsWhenReadBackStillAllow() = runBlocking {
        val r = FakeCommandRunner(handler = { c ->
            when (c) {
                get -> CommandResult.Success("RUN_ANY_IN_BACKGROUND: allow")
                setIgnore -> CommandResult.Success("")
                else -> null
            }
        })
        assertFalse(StartupManager(r, MemoryStore()).restrict("com.a").ok)
    }

    @Test fun restrictDoesNotOverwriteSavedOriginalWhenAlreadyRestricted() = runBlocking {
        val r = FakeCommandRunner(handler = { c ->
            when (c) {
                get -> CommandResult.Success("RUN_ANY_IN_BACKGROUND: ignore")
                setIgnore -> CommandResult.Success("")
                else -> null
            }
        })
        val store = MemoryStore().apply { saveOriginal("com.a", "allow") }
        StartupManager(r, store).restrict("com.a")
        assertEquals("allow", store.original("com.a"))
    }

    @Test fun restoreSetsDefaultAndClearsStore() = runBlocking {
        val r = FakeCommandRunner(mutableMapOf(setDefault to CommandResult.Success("")))
        val store = MemoryStore().apply { saveOriginal("com.a", "allow") }
        assertTrue(StartupManager(r, store).restore("com.a").ok)
        assertNull(store.original("com.a"))
    }

    @Test fun restoreWithoutSavedOriginalFailsClearly() = runBlocking {
        val res = StartupManager(FakeCommandRunner(), MemoryStore()).restore("com.a")
        assertFalse(res.ok)
    }

    @Test fun parseAppOpModeHandlesGarbage() {
        assertNull(parseAppOpMode("No operations."))
        assertNull(parseAppOpMode(""))
        assertEquals("allow", parseAppOpMode("RUN_ANY_IN_BACKGROUND: allow"))
    }
}
