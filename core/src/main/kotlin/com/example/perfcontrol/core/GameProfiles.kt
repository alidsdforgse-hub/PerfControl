package com.example.perfcontrol.core

/** أوضاع الأداء على طريقة Game Space: هادئ، متوازن، أداء، أقصى. */
enum class PerfMode(val title: String, val desc: String) {
    Cool("هادئ", "حرارة وبطارية أقل: 60 هرتز بدون إيقاف تطبيقات."),
    Balanced("متوازن", "حل وسط: نحو 90 هرتز وإيقاف التطبيقات الخلفية."),
    Performance("أداء", "أعلى معدل تحديث تدعمه الشاشة وإيقاف التطبيقات الخلفية."),
    Extreme("أقصى", "كالأداء مع إلغاء الرسوم المتحركة تماماً."),
}

data class GameProfile(val hz: Int, val anim: String, val boost: Boolean)

object GameProfiles {
    /** أقرب معدل مدعوم لا يتجاوز target، وإلا أقل معدل مدعوم. */
    private fun nearestAtMost(target: Int, rates: List<Int>): Int =
        rates.filter { it <= target }.maxOrNull() ?: rates.minOrNull() ?: target

    fun forMode(mode: PerfMode, rates: List<Int>): GameProfile {
        val max = rates.maxOrNull() ?: 120
        return when (mode) {
            PerfMode.Cool -> GameProfile(nearestAtMost(60, rates), "1.0", false)
            PerfMode.Balanced -> GameProfile(nearestAtMost(90, rates), "0.5", true)
            PerfMode.Performance -> GameProfile(max, "0.5", true)
            PerfMode.Extreme -> GameProfile(max, "0", true)
        }
    }
}
