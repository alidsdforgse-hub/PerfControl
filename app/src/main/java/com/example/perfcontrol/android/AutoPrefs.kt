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
}
