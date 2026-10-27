package com.example.perfcontrol.android

import android.content.Context

class UiPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)

    /** 0 = تلقائي (حسب النظام)، 1 = فاتح، 2 = داكن */
    var themeMode: Int
        get() = p.getInt("theme", 0)
        set(v) { p.edit().putInt("theme", v).apply() }
}
