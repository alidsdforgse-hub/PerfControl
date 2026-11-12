package com.example.perfcontrol.core

sealed class ProfileAction {
    object None : ProfileAction()
    data class Apply(val pkg: String, val hz: Int) : ProfileAction()
    object Restore : ProfileAction()
}

/** ملف تعريف لكل تطبيق: معدل تحديث يُطبَّق عند ظهور التطبيق ويُعاد عند مغادرته. */
object AppProfilePolicy {
    fun decide(top: String?, profiles: Map<String, Int>, appliedFor: String?, gameOn: Boolean): ProfileAction {
        if (gameOn) return if (appliedFor != null) ProfileAction.Restore else ProfileAction.None
        if (top == null) return ProfileAction.None
        val hz = profiles[top]
        return when {
            hz != null && hz > 0 && appliedFor != top -> ProfileAction.Apply(top, hz)
            (hz == null || hz <= 0) && appliedFor != null -> ProfileAction.Restore
            else -> ProfileAction.None
        }
    }
}
