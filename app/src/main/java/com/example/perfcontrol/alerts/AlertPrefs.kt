package com.example.perfcontrol.alerts

import android.content.Context

class AlertPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("alerts", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = p.getBoolean("enabled", false)
        set(v) { p.edit().putBoolean("enabled", v).apply() }

    fun lastSent(key: String): Long = p.getLong("last_$key", 0L)
    fun setLastSent(key: String, time: Long) { p.edit().putLong("last_$key", time).apply() }
}
