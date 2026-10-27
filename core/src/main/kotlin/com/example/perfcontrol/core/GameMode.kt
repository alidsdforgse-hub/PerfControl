package com.example.perfcontrol.core

data class SettingChange(val ns: String, val key: String, val value: String)

private val SAFE_VALUE = Regex("^[0-9.\\-]+$")

/** يطبّق إعدادات النظام ويحفظ قيمها الأصلية ليتمكن من إعادتها. */
class SettingsApplier(
    private val runner: CommandRunner,
    private val store: StateStore,
    private val prefix: String,
) {
    private fun keyOf(s: SettingChange) = "$prefix:${s.ns}.${s.key}"
    private val activeKey get() = "$prefix:active"

    suspend fun read(ns: String, key: String): String? =
        (runner.run("settings get $ns $key") as? CommandResult.Success)?.output?.trim()

    fun isActive(): Boolean = store.original(activeKey) != null

    fun markActive(on: Boolean) {
        if (on) store.saveOriginal(activeKey, "1") else store.clear(activeKey)
    }

    suspend fun apply(list: List<SettingChange>): Int {
        var applied = 0
        for (s in list) {
            val before = read(s.ns, s.key) ?: continue
            if (store.original(keyOf(s)) == null) store.saveOriginal(keyOf(s), before)
            val put = runner.run("settings put ${s.ns} ${s.key} ${s.value}")
            if (put is CommandResult.Success && read(s.ns, s.key) == s.value) applied++
        }
        return applied
    }

    suspend fun restore(list: List<SettingChange>): Int {
        var restored = 0
        for (s in list) {
            val orig = store.original(keyOf(s)) ?: continue
            val r = if (orig.isBlank() || orig == "null" || !SAFE_VALUE.matches(orig)) {
                runner.run("settings delete ${s.ns} ${s.key}")
            } else {
                runner.run("settings put ${s.ns} ${s.key} $orig")
            }
            if (r is CommandResult.Success) {
                store.clear(keyOf(s))
                restored++
            }
        }
        return restored
    }
}

private val GAME_SETTINGS = listOf(
    SettingChange("global", "window_animation_scale", "0.5"),
    SettingChange("global", "transition_animation_scale", "0.5"),
    SettingChange("global", "animator_duration_scale", "0.5"),
    SettingChange("system", "min_refresh_rate", "120"),
    SettingChange("system", "peak_refresh_rate", "120"),
)

private val POWERSAVE_SETTINGS = listOf(
    SettingChange("global", "low_power", "1"),
    SettingChange("system", "min_refresh_rate", "60"),
    SettingChange("system", "peak_refresh_rate", "60"),
)

const val GAME_ACTIVE_KEY = "game:active"
const val POWERSAVE_ACTIVE_KEY = "psave:active"

data class GameModeResult(val ok: Boolean, val message: String, val freedKb: Long = 0)

class GameMode(
    runner: CommandRunner,
    private val booster: Booster,
    store: StateStore,
) {
    private val applier = SettingsApplier(runner, store, "game")

    fun isEnabled(): Boolean = applier.isActive()

    suspend fun enable(candidates: List<String>): GameModeResult {
        val applied = applier.apply(GAME_SETTINGS)
        val report = booster.boost(candidates)
        applier.markActive(true)
        val stopped = report.batch.succeeded.size
        val ok = applied > 0 || stopped > 0
        val msg = buildString {
            append("وضع الألعاب يعمل: طُبّق $applied من ${GAME_SETTINGS.size} إعداد")
            append("، وأُوقف $stopped تطبيق")
            append("، وحُرّر ${report.freedKb / 1024} ميغابايت")
            if (applied == 0) append(". لم يقبل النظام تغيير الإعدادات")
        }
        return GameModeResult(ok, msg, report.freedKb)
    }

    suspend fun disable(): GameModeResult {
        val restored = applier.restore(GAME_SETTINGS)
        applier.markActive(false)
        return GameModeResult(true, "تم إيقاف وضع الألعاب وإعادة $restored إعداد")
    }
}

class PowerSaveMode(runner: CommandRunner, store: StateStore) {
    private val applier = SettingsApplier(runner, store, "psave")

    fun isEnabled(): Boolean = applier.isActive()

    suspend fun enable(): GameModeResult {
        val applied = applier.apply(POWERSAVE_SETTINGS)
        applier.markActive(true)
        val msg = "وضع توفير الطاقة يعمل: طُبّق $applied من ${POWERSAVE_SETTINGS.size} إعداد" +
            (if (applied == 0) ". لم يقبل النظام تغيير الإعدادات" else "")
        return GameModeResult(applied > 0, msg)
    }

    suspend fun disable(): GameModeResult {
        val restored = applier.restore(POWERSAVE_SETTINGS)
        applier.markActive(false)
        return GameModeResult(true, "تم إيقاف وضع توفير الطاقة وإعادة $restored إعداد")
    }
}

class AppOptimizer(private val runner: CommandRunner) {
    /** إعادة تحسين تشغيل كل التطبيقات (قد تستغرق عدة دقائق). */
    suspend fun optimizeAll(): CommandResult = runner.run("cmd package compile -m speed-profile -a")
}
