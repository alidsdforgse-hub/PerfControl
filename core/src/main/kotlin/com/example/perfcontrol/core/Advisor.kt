package com.example.perfcontrol.core

enum class AdvisorAction(val label: String) {
    Boost("رفع الأداء الآن"),
    StopHeavy("إيقاف التطبيقات الثقيلة"),
    GameMode("تشغيل وضع الألعاب"),
    PowerSave("تشغيل توفير الطاقة"),
    Clean("تنظيف الكاش"),
    ThermalGuard("تفعيل حارس الحرارة"),
}

data class DeviceSnapshot(
    val ramUsedPct: Int,
    val tempC: Float,
    val batteryPct: Int,
    val charging: Boolean,
    val storageUsedPct: Int,
    val uptimeHours: Long,
    val heavyApps: List<String>,
    val shizukuReady: Boolean,
    val gameOn: Boolean,
    val powerSaveOn: Boolean,
    val thermalGuardOn: Boolean,
)

data class AdvisorReply(val text: String, val actions: List<AdvisorAction>)

enum class Topic { Slow, Heat, Battery, Game, Storage, General }

/** مساعد قائم على قواعد (بدون ذكاء اصطناعي): يحلل حالة الجهاز ويجيب عن أسئلة شائعة ويقترح إجراءات. */
object Advisor {
    private val keywords = listOf(
        Topic.Heat to listOf("حرار", "سخن", "ساخن", "تسخين", "يسخن", "سخونة"),
        Topic.Battery to listOf("بطاري", "شحن", "طاقة", "تفرغ", "يفرغ"),
        Topic.Game to listOf("لعب", "العاب", "ألعاب", "ببجي", "pubg", "game", "فري فاير", "كول"),
        Topic.Storage to listOf("تخزين", "مساح", "كاش", "تنظيف", "ممتلئ", "ذاكرة داخلية"),
        Topic.Slow to listOf("بطيء", "بطئ", "لاق", "lag", "تهنيج", "يهنج", "ثقيل", "تقطيع", "يقطع", "ذاكر", "رام", "ram", "سرع"),
    )

    fun topic(q: String): Topic {
        val t = q.lowercase()
        return keywords.firstOrNull { (_, words) -> words.any { t.contains(it) } }?.first ?: Topic.General
    }

    fun answer(question: String, s: DeviceSnapshot): AdvisorReply = when (topic(question)) {
        Topic.Slow -> slow(s)
        Topic.Heat -> heat(s)
        Topic.Battery -> battery(s)
        Topic.Game -> game(s)
        Topic.Storage -> storage(s)
        Topic.General -> diagnose(s)
    }

    private fun gate(s: DeviceSnapshot, actions: List<AdvisorAction>): List<AdvisorAction> =
        if (s.shizukuReady) actions.distinct() else emptyList()

    private fun noShizuku(s: DeviceSnapshot) =
        if (s.shizukuReady) "" else "\nلتنفيذ الإجراءات بزر واحد يلزم أن يكون Shizuku جاهزاً ومعه إذن التطبيق."

    fun diagnose(s: DeviceSnapshot): AdvisorReply {
        val lines = mutableListOf<String>()
        val acts = mutableListOf<AdvisorAction>()
        if (s.ramUsedPct >= 85) { lines += "• الذاكرة ممتلئة (${s.ramUsedPct}%)."; acts += AdvisorAction.Boost }
        if (s.heavyApps.isNotEmpty()) { lines += "• تطبيقات ثقيلة تعمل: ${s.heavyApps.take(4).joinToString("، ")}."; acts += AdvisorAction.StopHeavy }
        if (s.tempC >= 40f) {
            lines += "• الحرارة مرتفعة (${"%.1f".format(s.tempC)}°C)."
            if (!s.thermalGuardOn) acts += AdvisorAction.ThermalGuard
        }
        if (s.storageUsedPct >= 90) { lines += "• التخزين شبه ممتلئ (${s.storageUsedPct}%)."; acts += AdvisorAction.Clean }
        if (s.batteryPct in 0..15 && !s.charging) { lines += "• البطارية منخفضة (${s.batteryPct}%)."; if (!s.powerSaveOn) acts += AdvisorAction.PowerSave }
        if (s.uptimeHours >= 72) lines += "• لم تعد تشغيل الجهاز منذ ${s.uptimeHours} ساعة. إعادة التشغيل تنظّف الذاكرة وتحسّن السلاسة."
        val text = if (lines.isEmpty()) "جهازك بخير الآن: لا أرى مشكلة واضحة." else "هذا ما وجدته:\n" + lines.joinToString("\n")
        return AdvisorReply(text + noShizuku(s), gate(s, acts))
    }

    private fun slow(s: DeviceSnapshot): AdvisorReply {
        val lines = mutableListOf<String>()
        val acts = mutableListOf<AdvisorAction>()
        if (s.ramUsedPct >= 75) { lines += "الذاكرة مستخدمة ${s.ramUsedPct}%، وهذا سبب شائع للبطء."; acts += AdvisorAction.Boost }
        if (s.heavyApps.isNotEmpty()) { lines += "تطبيقات تستهلك كثيراً: ${s.heavyApps.take(4).joinToString("، ")}."; acts += AdvisorAction.StopHeavy }
        if (s.storageUsedPct >= 90) { lines += "التخزين شبه ممتلئ (${s.storageUsedPct}%) ويبطئ الجهاز."; acts += AdvisorAction.Clean }
        if (s.tempC >= 40f) lines += "الحرارة ${"%.1f".format(s.tempC)}°C، والمعالج يخفّض سرعته عند السخونة."
        if (s.uptimeHours >= 72) lines += "لم يُعَد تشغيل الجهاز منذ ${s.uptimeHours} ساعة، جرّب إعادة التشغيل."
        if (lines.isEmpty()) lines += "لا أرى سبباً واضحاً للبطء الآن. إن كان البطء في تطبيق واحد فالسبب غالباً في التطبيق نفسه؛ جرّب تحديثه أو مسح كاشه."
        return AdvisorReply(lines.joinToString("\n") + noShizuku(s), gate(s, acts))
    }

    private fun heat(s: DeviceSnapshot): AdvisorReply {
        val lines = mutableListOf("حرارة البطارية الآن ${"%.1f".format(s.tempC)}°C.")
        val acts = mutableListOf<AdvisorAction>()
        when {
            s.tempC >= 44f -> lines += "هذه حرارة عالية. أوقف الشحن واللعب وأخرج الجهاز من الغطاء حتى يبرد."
            s.tempC >= 40f -> lines += "حرارة مرتفعة قليلاً."
            else -> lines += "الحرارة طبيعية."
        }
        if (s.heavyApps.isNotEmpty()) { lines += "أوقف التطبيقات الثقيلة: ${s.heavyApps.take(4).joinToString("، ")}."; acts += AdvisorAction.StopHeavy }
        if (s.charging) lines += "الجهاز يشحن، والشحن مع الاستخدام الثقيل يرفع الحرارة."
        if (!s.thermalGuardOn) { lines += "فعّل حارس الحرارة ليخفّف الجهاز نفسه تلقائياً."; acts += AdvisorAction.ThermalGuard }
        return AdvisorReply(lines.joinToString("\n") + noShizuku(s), gate(s, acts))
    }

    private fun battery(s: DeviceSnapshot): AdvisorReply {
        val lines = mutableListOf("البطارية ${s.batteryPct}%" + if (s.charging) " (تشحن)." else ".")
        val acts = mutableListOf<AdvisorAction>()
        if (!s.powerSaveOn) { lines += "وضع توفير الطاقة يخفّض معدل التحديث ويقلل النشاط الخلفي."; acts += AdvisorAction.PowerSave }
        else lines += "توفير الطاقة يعمل بالفعل."
        if (s.heavyApps.isNotEmpty()) { lines += "تطبيقات تستهلك كثيراً: ${s.heavyApps.take(4).joinToString("، ")}."; acts += AdvisorAction.StopHeavy }
        lines += "ونصيحة عامة: خفّض سطوع الشاشة وتجنب الشحن الكامل المتكرر."
        return AdvisorReply(lines.joinToString("\n") + noShizuku(s), gate(s, acts))
    }

    private fun game(s: DeviceSnapshot): AdvisorReply {
        val lines = mutableListOf<String>()
        val acts = mutableListOf<AdvisorAction>()
        if (s.gameOn) lines += "وضع الألعاب يعمل بالفعل." else { lines += "لتجهيز الجهاز للعب شغّل وضع الألعاب."; acts += AdvisorAction.GameMode }
        if (s.ramUsedPct >= 75) { lines += "الذاكرة ${s.ramUsedPct}%، حرّر منها قبل اللعب."; acts += AdvisorAction.Boost }
        if (s.tempC >= 40f) lines += "الجهاز ساخن (${"%.1f".format(s.tempC)}°C)؛ انتظر قليلاً أو اختر وضع \"هادئ\" للعبة."
        if (!s.thermalGuardOn) acts += AdvisorAction.ThermalGuard
        return AdvisorReply(lines.joinToString("\n") + noShizuku(s), gate(s, acts))
    }

    private fun storage(s: DeviceSnapshot): AdvisorReply {
        val lines = mutableListOf("التخزين مستخدم ${s.storageUsedPct}%.")
        val acts = mutableListOf<AdvisorAction>()
        if (s.storageUsedPct >= 85) lines += "يُفضّل إبقاء 15% على الأقل فارغة للسرعة."
        lines += "تنظيف الكاش يحرر مساحة بأمان."
        acts += AdvisorAction.Clean
        return AdvisorReply(lines.joinToString("\n") + noShizuku(s), gate(s, acts))
    }
}
