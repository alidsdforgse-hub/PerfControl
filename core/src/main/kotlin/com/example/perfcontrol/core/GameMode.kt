package com.example.perfcontrol.core

private data class GameSetting(val ns: String, val key: String, val value: String)

private val GAME_SETTINGS = listOf(
    GameSetting("global", "window_animation_scale", "0.5"),
    GameSetting("global", "transition_animation_scale", "0.5"),
    GameSetting("global", "animator_duration_scale", "0.5"),
    GameSetting("system", "min_refresh_rate", "120"),
    GameSetting("system", "peak_refresh_rate", "120"),
)

const val GAME_ACTIVE_KEY = "game:active"

private val SAFE_VALUE = Regex("^[0-9.\\-]+$")

data class GameModeResult(val ok: Boolean, val message: String, val freedKb: Long = 0)

class GameMode(
    private val runner: CommandRunner,
    private val booster: Booster,
    private val store: StateStore,
) {
    fun isEnabled(): Boolean = store.original(GAME_ACTIVE_KEY) != null

    private fun keyOf(s: GameSetting) = "game:${s.ns}.${s.key}"

    private suspend fun read(s: GameSetting): String? =
        (runner.run("settings get ${s.ns} ${s.key}") as? CommandResult.Success)?.output?.trim()

    suspend fun enable(candidates: List<String>): GameModeResult {
        var applied = 0
        for (s in GAME_SETTINGS) {
            val before = read(s) ?: continue
            if (store.original(keyOf(s)) == null) store.saveOriginal(keyOf(s), before)
            val put = runner.run("settings put ${s.ns} ${s.key} ${s.value}")
            if (put is CommandResult.Success && read(s) == s.value) applied++
        }
        val report = booster.boost(candidates)
        store.saveOriginal(GAME_ACTIVE_KEY, "1")
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
        var restored = 0
        for (s in GAME_SETTINGS) {
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
        store.clear(GAME_ACTIVE_KEY)
        return GameModeResult(true, "تم إيقاف وضع الألعاب وإعادة $restored إعداد")
    }
}

class AppOptimizer(private val runner: CommandRunner) {
    /** إعادة تحسين تشغيل كل التطبيقات (قد تستغرق عدة دقائق). */
    suspend fun optimizeAll(): CommandResult = runner.run("cmd package compile -m speed-profile -a")
}
