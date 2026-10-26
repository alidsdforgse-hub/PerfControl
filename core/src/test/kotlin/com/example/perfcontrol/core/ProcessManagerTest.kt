package com.example.perfcontrol.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProcessManagerTest {
    private val prot = AppProtection(ProtectionContext(setOf("com.self"), emptySet()))
    private fun ok() = CommandResult.Success("")

    @Test fun protectedPackagesAreSkippedAndNeverReachRunner() = runBlocking {
        val r = FakeCommandRunner(handler = { ok() })
        val res = ProcessManager(r, prot).forceStop(listOf("com.self", "com.android.systemui", "com.game"))
        assertEquals(listOf("com.game"), res.succeeded)
        assertEquals(setOf("com.self", "com.android.systemui"), res.skippedProtected.toSet())
        assertEquals(listOf("am force-stop com.game"), r.log)
    }

    @Test fun suffixedProtectedProcessIsSkipped() = runBlocking {
        val r = FakeCommandRunner(handler = { ok() })
        val res = ProcessManager(r, prot).forceStop(listOf("com.self:remote"))
        assertTrue(r.log.isEmpty())
        assertEquals(listOf("com.self"), res.skippedProtected)
    }

    @Test fun failureOnOneContinuesWithRest() = runBlocking {
        val r = FakeCommandRunner(handler = { c -> if (c.endsWith("com.a")) CommandResult.Failure("boom") else ok() })
        val res = ProcessManager(r, prot).forceStop(listOf("com.a", "com.b"))
        assertEquals(listOf("com.b"), res.succeeded)
        assertEquals("boom", res.failed["com.a"])
    }

    @Test fun runnerThrowingMarksPackageFailedWithReason() = runBlocking {
        val r = FakeCommandRunner(handler = { throw IllegalStateException("shizuku died") })
        val res = ProcessManager(r, prot).forceStop(listOf("com.a", "com.b"))
        assertEquals(setOf("com.a", "com.b"), res.failed.keys)
        assertEquals("shizuku died", res.failed["com.a"])
    }

    @Test fun duplicatePackagesStoppedOnce() = runBlocking {
        val r = FakeCommandRunner(handler = { ok() })
        ProcessManager(r, prot).forceStop(listOf("com.a", "com.a:x", "com.a"))
        assertEquals(1, r.log.size)
    }

    @Test fun invalidNameNeverReachesShell() = runBlocking {
        val r = FakeCommandRunner(handler = { ok() })
        val res = ProcessManager(r, prot).forceStop(listOf("com.a; reboot"))
        assertTrue(r.log.isEmpty())
        assertTrue(res.failed.isNotEmpty())
    }

    @Test fun emptyListReturnsEmptyResult() = runBlocking {
        val res = ProcessManager(FakeCommandRunner(), prot).forceStop(emptyList())
        assertTrue(res.succeeded.isEmpty() && res.failed.isEmpty() && res.skippedProtected.isEmpty())
    }
}
