package com.example.perfcontrol.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BoosterAndMonitorTest {
    private val prot = AppProtection(ProtectionContext(emptySet(), emptySet()))
    private fun mem(avail: Long) = CommandResult.Success("MemTotal: 100 kB\nMemAvailable: $avail kB")

    private fun booster(r: FakeCommandRunner) =
        Booster(r, ProcessManager(r, prot), Cleaner(r), prot)

    @Test fun planExcludesProtected() {
        val r = FakeCommandRunner()
        assertEquals(listOf("com.game"), booster(r).plan(listOf("com.android.systemui", "com.game")))
    }

    @Test fun boostOrderIsMeasureStopTrimMeasure() = runBlocking {
        val r = FakeCommandRunner(handler = { c -> if (c == "cat /proc/meminfo") mem(10) else CommandResult.Success("") })
        booster(r).boost(listOf("com.game"))
        assertEquals(
            listOf("cat /proc/meminfo", "am force-stop com.game", "cmd package trim-caches 999G", "cat /proc/meminfo"),
            r.log,
        )
    }

    @Test fun freedKbIsDifferenceOfAvailable() = runBlocking {
        var n = 0
        val r = FakeCommandRunner(handler = { c ->
            if (c == "cat /proc/meminfo") { n++; mem(if (n == 1) 10 else 30) } else CommandResult.Success("")
        })
        assertEquals(20L, booster(r).boost(listOf("com.game")).freedKb)
    }

    @Test fun freedKbIsZeroWhenMemoryGotWorse() = runBlocking {
        var n = 0
        val r = FakeCommandRunner(handler = { c ->
            if (c == "cat /proc/meminfo") { n++; mem(if (n == 1) 30 else 10) } else CommandResult.Success("")
        })
        assertEquals(0L, booster(r).boost(listOf("com.game")).freedKb)
    }

    @Test fun trimFailureStillReportsStoppedApps() = runBlocking {
        val r = FakeCommandRunner(handler = { c ->
            when {
                c == "cat /proc/meminfo" -> mem(10)
                c.startsWith("cmd package") -> CommandResult.Failure("no")
                else -> CommandResult.Success("")
            }
        })
        val rep = booster(r).boost(listOf("com.game"))
        assertFalse(rep.cacheTrimmed)
        assertEquals(listOf("com.game"), rep.batch.succeeded)
    }

    @Test fun firstSampleHasNoCpuPercentAndSecondComputesIt() = runBlocking {
        var n = 0
        val r = FakeCommandRunner(handler = { c ->
            when (c) {
                "cat /proc/stat" -> { n++; CommandResult.Success(if (n == 1) "cpu 0 0 0 100 0 0 0 0" else "cpu 25 0 0 175 0 0 0 0") }
                else -> CommandResult.Failure("x")
            }
        })
        val repo = MonitorRepository(r)
        assertNull(repo.sample().cpuPercent)
        assertEquals(25.0, repo.sample().cpuPercent!!, 0.001)
    }

    @Test fun failedCommandYieldsNullOrEmptyNotException() = runBlocking {
        val s = MonitorRepository(FakeCommandRunner()).sample()
        assertNull(s.cpuPercent); assertNull(s.mem); assertTrue(s.processes.isEmpty())
    }

    @Test fun cleanerRunsExactCommand() = runBlocking {
        val r = FakeCommandRunner(handler = { CommandResult.Success("") })
        Cleaner(r).trimAllCaches()
        assertEquals(listOf("cmd package trim-caches 999G"), r.log)
    }

    @Test fun compatibilityNeverRunsDestructiveCommands() = runBlocking {
        val r = FakeCommandRunner(handler = { CommandResult.Success("") })
        val items = CompatibilityChecker(r, "com.self").run()
        assertEquals(6, items.size)
        assertTrue(r.log.none { it.contains("force-stop") || it.contains("appops set") })
    }

    @Test fun shizukuStateOrdering() {
        assertEquals(ShizukuState.NotInstalled, resolveShizukuState(false, true, true))
        assertEquals(ShizukuState.NotRunning, resolveShizukuState(true, false, true))
        assertEquals(ShizukuState.NoPermission, resolveShizukuState(true, true, false))
        assertEquals(ShizukuState.Ready, resolveShizukuState(true, true, true))
    }
}
