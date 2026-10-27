package com.example.perfcontrol.android

import android.content.Context
import com.example.perfcontrol.core.StateStore

class PrefsStateStore(ctx: Context) : StateStore {
    private val prefs = ctx.getSharedPreferences("perfcontrol_state", Context.MODE_PRIVATE)

    override fun saveOriginal(pkg: String, mode: String) { prefs.edit().putString("orig_$pkg", mode).apply() }
    override fun original(pkg: String): String? = prefs.getString("orig_$pkg", null)
    override fun clear(pkg: String) { prefs.edit().remove("orig_$pkg").apply() }

    fun restrictedPackages(): Set<String> =
        prefs.all.keys.filter { it.startsWith("orig_") && !it.startsWith("orig_game:") }.map { it.removePrefix("orig_") }.toSet()

    fun exclusions(): Set<String> = prefs.getStringSet("exclusions", emptySet()) ?: emptySet()
    fun setExclusions(s: Set<String>) { prefs.edit().putStringSet("exclusions", s.toSet()).apply() }
}
