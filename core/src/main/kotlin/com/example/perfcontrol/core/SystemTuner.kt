package com.example.perfcontrol.core

/** ضبط مباشر لإعدادات النظام (قيم محددة مسبقاً فقط لتجنب حقن الأوامر). */
class SystemTuner(private val runner: CommandRunner) {
    private val allowedScales = setOf("0", "0.5", "1")
    private val allowedHz = setOf(60, 90, 120)

    private suspend fun read(ns: String, key: String): String? =
        (runner.run("settings get $ns $key") as? CommandResult.Success)?.output?.trim()

    suspend fun animationScale(): String? = read("global", "window_animation_scale")
    suspend fun peakRefreshRate(): String? = read("system", "peak_refresh_rate")
    suspend fun isBatterySaver(): Boolean = read("global", "low_power") == "1"

    suspend fun setAnimationScale(value: String): CommandResult {
        if (value !in allowedScales) return CommandResult.Failure("قيمة غير مدعومة")
        var last: CommandResult = CommandResult.Success("")
        for (key in listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")) {
            last = runner.run("settings put global $key $value")
            if (last is CommandResult.Failure) return last
        }
        return last
    }

    /** null = تلقائي (يحذف القيمتين فيعود النظام للإعداد الافتراضي). */
    suspend fun setRefreshRate(hz: Int?): CommandResult {
        if (hz == null) {
            val a = runner.run("settings delete system min_refresh_rate")
            val b = runner.run("settings delete system peak_refresh_rate")
            return if (a is CommandResult.Failure) a else b
        }
        if (hz !in allowedHz) return CommandResult.Failure("قيمة غير مدعومة")
        val a = runner.run("settings put system min_refresh_rate $hz")
        if (a is CommandResult.Failure) return a
        return runner.run("settings put system peak_refresh_rate $hz")
    }

    suspend fun setBatterySaver(on: Boolean): CommandResult =
        runner.run("settings put global low_power ${if (on) 1 else 0}")

    suspend fun storageMaintenance(): CommandResult = runner.run("sm idle-maint run")
}
