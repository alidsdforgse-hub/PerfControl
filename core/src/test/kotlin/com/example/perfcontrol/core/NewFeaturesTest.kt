package com.example.perfcontrol.core

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NewFeaturesTest {
    @Test fun startTimeParsed() {
        assertEquals(523L, BenchParsers.startTotalMs("Status: ok\nTotalTime: 523\nWaitTime: 540"))
        assertNull(BenchParsers.startTotalMs("Error: Activity not started"))
    }

    @Test fun processCountIgnoresKernelAndHeader() {
        val out = "NAME\n[kthreadd]\nzygote64\ncom.android.systemui\ncom.whatsapp\n/system/bin/init\ncom.app:remote"
        assertEquals(3, BenchParsers.processCount(out))
    }

    @Test fun compareJudgesDirection() {
        val rows = BenchmarkRunner(FakeCommandRunner(), "x/y").compare(
            BenchSnapshot(1_000_000, 100, 800),
            BenchSnapshot(1_500_000, 80, 805),
        )
        assertEquals(Verdict.Better, rows.first { it.label == "الذاكرة المتاحة" }.verdict)
        assertEquals(Verdict.Better, rows.first { it.label == "عمليات التطبيقات" }.verdict)
        assertEquals(Verdict.Same, rows.first { it.label == "سرعة فتح التطبيق" }.verdict)
    }

    @Test fun compareWorseWhenSlower() {
        val rows = BenchmarkRunner(FakeCommandRunner(), "x/y").compare(
            BenchSnapshot(null, null, 500),
            BenchSnapshot(null, null, 700),
        )
        assertEquals(Verdict.Worse, rows.single().verdict)
    }

    @Test fun snapshotMeasuresAndStopsApp() = runBlocking {
        val r = FakeCommandRunner(handler = { c ->
            when {
                c == "cat /proc/meminfo" -> CommandResult.Success("MemTotal: 100 kB\nMemAvailable: 40 kB")
                c.startsWith("ps ") -> CommandResult.Success("NAME\ncom.a\ncom.b")
                c.startsWith("am start") -> CommandResult.Success("TotalTime: 300")
                else -> CommandResult.Success("")
            }
        })
        val s = BenchmarkRunner(r, "x/y").snapshot("com.target", runs = 3)
        assertEquals(40L, s.ramAvailKb)
        assertEquals(2, s.appCount)
        assertEquals(300L, s.startMs)
        assertEquals("am force-stop com.target", r.log.last())
    }

    @Test fun snapshotRejectsInvalidPackage() = runBlocking {
        val r = FakeCommandRunner(handler = { CommandResult.Success("") })
        BenchmarkRunner(r, "x/y").snapshot("com.a; reboot")
        assertTrue(r.log.none { it.startsWith("am start") })
    }

    @Test fun packageListParser() {
        assertEquals(setOf("com.a", "com.b"), PackageListParser.parse("package:com.a\npackage:com.b\nnoise"))
    }

    private val prot = AppProtection(ProtectionContext(setOf("com.home"), emptySet()))

    private fun freezeRunner(disabled: MutableSet<String>) = FakeCommandRunner(handler = { c ->
        when {
            c == "pm list packages -d" -> CommandResult.Success(disabled.joinToString("\n") { "package:$it" })
            c.startsWith("pm disable-user --user 0 ") -> { disabled += c.substringAfterLast(' '); CommandResult.Success("") }
            c.startsWith("pm enable ") -> { disabled -= c.substringAfterLast(' '); CommandResult.Success("") }
            else -> null
        }
    })

    @Test fun freezeAndUnfreezeVerify() = runBlocking {
        val d = mutableSetOf<String>()
        val m = FreezeManager(freezeRunner(d), prot)
        assertTrue(m.freeze("com.game").ok)
        assertTrue("com.game" in d)
        assertTrue(m.unfreeze("com.game").ok)
        assertTrue(d.isEmpty())
    }

    @Test fun freezeRefusesProtectedAndInvalid() = runBlocking {
        val r = freezeRunner(mutableSetOf())
        val m = FreezeManager(r, prot)
        assertFalse(m.freeze("com.home").ok)
        assertFalse(m.freeze("com.a; reboot").ok)
        assertTrue(r.log.none { it.startsWith("pm disable") })
    }

    @Test fun freezeFailsWhenNotReallyDisabled() = runBlocking {
        val r = FakeCommandRunner(handler = { c -> if (c == "pm list packages -d") CommandResult.Success("") else CommandResult.Success("") })
        assertFalse(FreezeManager(r, prot).freeze("com.game").ok)
    }

    @Test fun deviceIdleParser() {
        val out = "system-excidle,com.android.shell,2000\nuser,com.whatsapp,10188\n"
        assertEquals(setOf("com.android.shell", "com.whatsapp"), DeviceIdleParser.whitelist(out))
    }

    @Test fun protectVerifiesViaWhitelist() = runBlocking {
        val wl = mutableSetOf<String>()
        val r = FakeCommandRunner(handler = { c ->
            when {
                c == "cmd deviceidle whitelist" -> CommandResult.Success(wl.joinToString("\n") { "user,$it,1" })
                c.startsWith("cmd deviceidle whitelist +") -> { wl += c.substringAfter('+'); CommandResult.Success("") }
                c.startsWith("cmd deviceidle whitelist -") -> { wl -= c.substringAfter('-'); CommandResult.Success("") }
                else -> CommandResult.Success("")
            }
        })
        val p = NotificationProtector(r)
        assertTrue(p.protect("com.whatsapp").ok)
        assertTrue("com.whatsapp" in wl)
        assertTrue(p.unprotect("com.whatsapp").ok)
        assertTrue(wl.isEmpty())
        assertFalse(p.protect("bad pkg").ok)
    }
}
