package com.example.perfcontrol.android

import android.content.Context

class AutoPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("auto", Context.MODE_PRIVATE)

    /** مؤقت وضع الألعاب بالدقائق، 0 = بدون */
    var gameMinutes: Int
        get() = p.getInt("game_minutes", 0)
        set(v) { p.edit().putInt("game_minutes", v).apply() }

    /** التنظيف التلقائي كل كم ساعة، 0 = متوقف */
    var cleanHours: Int
        get() = p.getInt("clean_hours", 0)
        set(v) { p.edit().putInt("clean_hours", v).apply() }

    var shortcuts: Boolean
        get() = p.getBoolean("shortcuts", false)
        set(v) { p.edit().putBoolean("shortcuts", v).apply() }

    var games: Set<String>
        get() = p.getStringSet("games", emptySet()) ?: emptySet()
        set(v) { p.edit().putStringSet("games", v.toSet()).apply() }

    var sessionStart: Long
        get() = p.getLong("s_start", 0L)
        set(v) { p.edit().putLong("s_start", v).apply() }
    var sessionBattery: Int
        get() = p.getInt("s_bat", -1)
        set(v) { p.edit().putInt("s_bat", v).apply() }
    var sessionTemp: Float
        get() = p.getFloat("s_temp", 0f)
        set(v) { p.edit().putFloat("s_temp", v).apply() }
    var lastReport: String
        get() = p.getString("last_report", "") ?: ""
        set(v) { p.edit().putString("last_report", v).apply() }

    var gamesInitialized: Boolean
        get() = p.getBoolean("games_init", false)
        set(v) { p.edit().putBoolean("games_init", v).apply() }

    /** تشغيل وضع الألعاب تلقائياً عند فتح لعبة من القائمة */
    var autoGame: Boolean
        get() = p.getBoolean("auto_game", false)
        set(v) { p.edit().putBoolean("auto_game", v).apply() }
    /** عدم الإزعاج أثناء وضع الألعاب */
    var gameDnd: Boolean
        get() = p.getBoolean("game_dnd", false)
        set(v) { p.edit().putBoolean("game_dnd", v).apply() }
    var thermalGuard: Boolean
        get() = p.getBoolean("thermal", false)
        set(v) { p.edit().putBoolean("thermal", v).apply() }
    var thermalLimit: Int
        get() = p.getInt("thermal_limit", 42)
        set(v) { p.edit().putInt("thermal_limit", v).apply() }

    /** وضع الأداء العام: 0 هادئ، 1 متوازن، 2 أداء، 3 أقصى */
    var globalMode: Int
        get() = p.getInt("global_mode", 2).coerceIn(0, 3)
        set(v) { p.edit().putInt("global_mode", v.coerceIn(0, 3)).apply() }

    /** وضع خاص بلعبة، أو null إن كانت تتبع الوضع العام */
    fun gameModeOf(pkg: String): Int? = p.getInt("pm_$pkg", -1).takeIf { it in 0..3 }
    fun setGameModeOf(pkg: String, mode: Int?) {
        p.edit().apply { if (mode == null) remove("pm_$pkg") else putInt("pm_$pkg", mode) }.apply()
    }
    fun perGameModes(): Map<String, Int> =
        p.all.filterKeys { it.startsWith("pm_") }.mapNotNull { (k, v) -> (v as? Int)?.let { k.removePrefix("pm_") to it } }.toMap()
    fun modeFor(pkg: String?): Int = (pkg?.let { gameModeOf(it) }) ?: globalMode

    var overlay: Boolean
        get() = p.getBoolean("overlay", false)
        set(v) { p.edit().putBoolean("overlay", v).apply() }

    /** نسبة دقة الشاشة أثناء اللعب (100 = بدون تخفيض) */
    var gameScale: Int
        get() = p.getInt("game_scale", 100)
        set(v) { p.edit().putInt("game_scale", v).apply() }

    var chargeAlert: Boolean
        get() = p.getBoolean("charge_alert", false)
        set(v) { p.edit().putBoolean("charge_alert", v).apply() }
    var chargeLimit: Int
        get() = p.getInt("charge_limit", 80)
        set(v) { p.edit().putInt("charge_limit", v).apply() }

    var appProfiles: Boolean
        get() = p.getBoolean("app_profiles", false)
        set(v) { p.edit().putBoolean("app_profiles", v).apply() }
    /** pkg -> hz (غير موجود = بدون ملف) */
    fun profiles(): Map<String, Int> =
        p.all.filterKeys { it.startsWith("ap_") }.mapNotNull { (k, v) -> (v as? Int)?.takeIf { it > 0 }?.let { k.removePrefix("ap_") to it } }.toMap()
    fun setProfile(pkg: String, hz: Int?) {
        p.edit().apply { if (hz == null || hz <= 0) remove("ap_$pkg") else putInt("ap_$pkg", hz) }.apply()
    }

    var logEnabled: Boolean
        get() = p.getBoolean("log_enabled", true)
        set(v) { p.edit().putBoolean("log_enabled", v).apply() }
}
