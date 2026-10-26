package com.example.perfcontrol.core

import org.junit.Assert.*
import org.junit.Test

class ParsersTest {
    @Test fun cpuUsageBetweenSamples() {
        val a = CpuSample(idle = 100, total = 200)
        val b = CpuSample(idle = 175, total = 300)
        assertEquals(25.0, ProcStatParser.usagePercent(a, b), 0.001)
    }

    @Test fun cpuUsageZeroTotalDeltaIsZero() {
        val a = CpuSample(100, 200)
        assertEquals(0.0, ProcStatParser.usagePercent(a, a), 0.001)
    }

    @Test fun cpuParseGarbageReturnsNull() {
        assertNull(ProcStatParser.parse("hello world"))
        assertNull(ProcStatParser.parse(""))
    }

    @Test fun cpuParseReadsLine() {
        val s = ProcStatParser.parse("cpu  10 0 10 70 10 0 0 0 0 0\ncpu0 1 1 1 1")!!
        assertEquals(80, s.idle)
        assertEquals(100, s.total)
    }

    private val topText = """
        Tasks: 1 total
          PID  RES %CPU ARGS
         1234 512K  1.0 com.example.a
         5678 1.5M  2.5 com.example.b:remote
         9999   2G  0.5 com.example.c
    """.trimIndent()

    @Test fun topParsesRowsAfterHeader() {
        val r = TopParser.parse(topText).associateBy { it.packageName }
        assertEquals(512L, r["com.example.a"]!!.rssKb)
        assertEquals(1536L, r["com.example.b"]!!.rssKb)
        assertEquals(2L * 1024 * 1024, r["com.example.c"]!!.rssKb)
    }

    @Test fun topEmptyOrTruncatedReturnsEmptyList() {
        assertTrue(TopParser.parse("").isEmpty())
        assertTrue(TopParser.parse("  PID  RES %CPU ARGS\n 12").isEmpty())
    }

    @Test fun topSkipsMalformedRow() {
        val r = TopParser.parse("  PID  RES %CPU ARGS\n abc 1M 1.0 com.x\n 10 1M 1.0 com.y")
        assertEquals(listOf("com.y"), r.map { it.packageName })
    }

    @Test fun basePackageStripsColonSuffix() {
        assertEquals("com.app", basePackage("com.app:remote"))
        assertEquals("com.app", basePackage("com.app"))
    }

    @Test fun topMergesProcessesOfSamePackage() {
        val r = TopParser.parse("  PID  RES %CPU ARGS\n 1 1M 1.0 com.x\n 2 1M 2.0 com.x:svc")
        assertEquals(1, r.size)
        assertEquals(2048L, r[0].rssKb)
        assertEquals(3.0, r[0].cpuPercent, 0.001)
    }

    @Test fun memInfoParsesTotalAndAvailable() {
        val m = MemInfoParser.parse("MemTotal:  8000000 kB\nMemFree: 1 kB\nMemAvailable:  4000000 kB")!!
        assertEquals(8000000L, m.totalKb)
        assertEquals(4000000L, m.availableKb)
        assertNull(MemInfoParser.parse("nothing"))
    }

    @Test fun freedKbNeverNegative() {
        assertEquals(0L, MemInfoParser.freedKb(MemInfo(10, 8), MemInfo(10, 5)))
        assertEquals(3L, MemInfoParser.freedKb(MemInfo(10, 5), MemInfo(10, 8)))
    }
}
