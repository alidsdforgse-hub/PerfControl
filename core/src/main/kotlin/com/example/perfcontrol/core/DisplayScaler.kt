package com.example.perfcontrol.core

object DisplayParsers {
    private val sizeRe = Regex("(\\d+)\\s*x\\s*(\\d+)")

    /** من ناتج `wm size`: يفضّل "Physical size" الحقيقي لا المعدَّل. */
    fun physicalSize(out: String): Pair<Int, Int>? {
        val line = out.lineSequence().firstOrNull { it.contains("Physical size", true) } ?: return null
        val m = sizeRe.find(line) ?: return null
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    fun physicalDensity(out: String): Int? {
        val line = out.lineSequence().firstOrNull { it.contains("Physical density", true) } ?: return null
        return Regex("(\\d+)").find(line.substringAfter(":"))?.groupValues?.get(1)?.toIntOrNull()
    }

    fun overrideSize(out: String): Pair<Int, Int>? {
        val line = out.lineSequence().firstOrNull { it.contains("Override size", true) } ?: return null
        val m = sizeRe.find(line) ?: return null
        return m.groupValues[1].toInt() to m.groupValues[2].toInt()
    }

    /** ناتج gpu_busy_percentage مثل "16 %" */
    fun gpuBusy(out: String): Int? = Regex("(\\d{1,3})").find(out)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 0..100 }
}

/**
 * خفض دقة العرض (wm size / wm density) لتقليل حمل كرت الشاشة والحرارة.
 * يحفظ حالة التفعيل ويمكن التراجع دائماً بـ reset.
 */
class DisplayScaler(private val runner: CommandRunner, private val store: StateStore) {
    companion object {
        const val ACTIVE_KEY = "display:active"
    }

    fun isActive(): Boolean = store.original(ACTIVE_KEY) != null

    suspend fun apply(percent: Int): StartupResult {
        if (percent >= 100) return reset()
        if (percent < 50) return StartupResult(false, "أقل نسبة مسموحة 50%")
        val sizeOut = (runner.run("wm size") as? CommandResult.Success)?.output ?: return StartupResult(false, "تعذر قراءة دقة الشاشة")
        val (pw, ph) = DisplayParsers.physicalSize(sizeOut) ?: return StartupResult(false, "تعذر قراءة دقة الشاشة")
        val densOut = (runner.run("wm density") as? CommandResult.Success)?.output
        val pd = densOut?.let { DisplayParsers.physicalDensity(it) }
        val w = (pw * percent / 100 / 2) * 2
        val h = (ph * percent / 100 / 2) * 2
        val a = runner.run("wm size ${w}x$h")
        if (a is CommandResult.Failure) return StartupResult(false, "رفض النظام تغيير الدقة: ${a.reason}")
        if (pd != null) runner.run("wm density ${pd * percent / 100}")
        val check = (runner.run("wm size") as? CommandResult.Success)?.output?.let { DisplayParsers.overrideSize(it) }
        if (check != w to h) return StartupResult(false, "لم تتغير الدقة فعلياً")
        store.saveOriginal(ACTIVE_KEY, "1")
        return StartupResult(true, "خُفّضت الدقة إلى ${w}x$h")
    }

    suspend fun reset(): StartupResult {
        val a = runner.run("wm size reset")
        runner.run("wm density reset")
        if (a is CommandResult.Failure) return StartupResult(false, "تعذرت إعادة الدقة: ${a.reason}")
        store.clear(ACTIVE_KEY)
        return StartupResult(true, "أُعيدت دقة الشاشة الأصلية")
    }

    suspend fun resetIfActive() {
        if (isActive()) reset()
    }
}
