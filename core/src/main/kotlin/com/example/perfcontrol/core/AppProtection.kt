package com.example.perfcontrol.core

val FIXED_PROTECTED: Set<String> = setOf(
    "android",
    "com.android.systemui",
    "com.android.phone",
    "com.miui.securitycenter",
    "com.miui.home",
    "com.xiaomi.xmsf",
    "com.miui.powerkeeper",
    "moe.shizuku.privileged.api",
)

data class ProtectionContext(
    /** تُحدَّد وقت التشغيل: الشاشة الرئيسية، لوحة المفاتيح، الاتصال، الرسائل، التطبيق نفسه. */
    val dynamic: Set<String>,
    /** استثناءات يحددها المستخدم. */
    val userExclusions: Set<String>,
)

class AppProtection(private val ctx: ProtectionContext) {
    fun isProtected(pkg: String): Boolean {
        val base = basePackage(pkg)
        return base in FIXED_PROTECTED || base in ctx.dynamic || base in ctx.userExclusions
    }
}
