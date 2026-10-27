package com.example.perfcontrol.core

enum class Severity { Info, Warning, Critical }

data class HealthIssue(val key: String, val message: String, val severity: Severity)

data class HealthInput(
    val uptimeHours: Long,
    val ramAvailPercent: Int,
    val storageFreePercent: Int,
    val tempC: Float,
    val batteryHealthGood: Boolean,
)

data class HealthReport(val score: Int, val label: String, val issues: List<HealthIssue>)

object HealthRater {
    fun rate(i: HealthInput): HealthReport {
        var score = 100
        val issues = mutableListOf<HealthIssue>()
        fun add(key: String, msg: String, sev: Severity, penalty: Int) {
            issues += HealthIssue(key, msg, sev)
            score -= penalty
        }

        when {
            i.tempC >= 45f -> add("temp", "حرارة الجهاز مرتفعة جداً (${i.tempC.toInt()}°C). أوقف الاستخدام الثقيل ودعه يبرد.", Severity.Critical, 30)
            i.tempC >= 40f -> add("temp", "حرارة الجهاز مرتفعة (${i.tempC.toInt()}°C).", Severity.Warning, 15)
        }
        when {
            i.ramAvailPercent < 10 -> add("ram", "الذاكرة المتاحة قليلة جداً (${i.ramAvailPercent}%). استخدم رفع الأداء.", Severity.Critical, 25)
            i.ramAvailPercent < 20 -> add("ram", "الذاكرة المتاحة منخفضة (${i.ramAvailPercent}%).", Severity.Warning, 10)
        }
        when {
            i.storageFreePercent < 5 -> add("storage", "التخزين ممتلئ تقريباً (المتاح ${i.storageFreePercent}%). احذف ملفات غير ضرورية.", Severity.Critical, 25)
            i.storageFreePercent < 15 -> add("storage", "مساحة التخزين المتاحة قليلة (${i.storageFreePercent}%).", Severity.Warning, 10)
        }
        when {
            i.uptimeHours >= 168 -> add("uptime", "لم يُعَد تشغيل الجهاز منذ ${i.uptimeHours / 24} يوماً. أعد تشغيله لتحسين السرعة والاستقرار.", Severity.Critical, 20)
            i.uptimeHours >= 72 -> add("uptime", "مضت ${i.uptimeHours / 24} أيام على آخر تشغيل. يُنصح بإعادة التشغيل.", Severity.Warning, 10)
        }
        if (!i.batteryHealthGood) {
            add("battery", "حالة البطارية غير جيدة بحسب النظام.", Severity.Warning, 20)
        }

        val s = score.coerceIn(0, 100)
        val label = when {
            s >= 80 -> "جيدة"
            s >= 50 -> "تحتاج انتباه"
            else -> "سيئة"
        }
        return HealthReport(s, label, issues)
    }
}

/** تنبيهات الإشعارات: الحرجة فقط، إضافة إلى تذكير إعادة التشغيل. */
fun HealthIssue.shouldNotify(): Boolean =
    severity == Severity.Critical || (key == "uptime" && severity != Severity.Info)

fun notifyCooldownMs(key: String): Long =
    if (key == "uptime") 24L * 3600 * 1000 else 2L * 3600 * 1000
