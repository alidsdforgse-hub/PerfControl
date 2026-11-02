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
}
