package com.example.perfcontrol.core

/** تنبيه الشحن: مرة واحدة لكل جلسة شحن عند بلوغ الحد. */
object ChargeAlertPolicy {
    /** @return true إذا يجب إرسال التنبيه الآن */
    fun shouldNotify(level: Int, charging: Boolean, limit: Int, alreadyNotified: Boolean): Boolean =
        charging && level >= limit && !alreadyNotified

    /** هل ننهي حالة "تم التنبيه" لتصبح الجلسة التالية جديدة؟ */
    fun shouldReset(level: Int, charging: Boolean, limit: Int): Boolean = !charging || level < limit - 5
}

object BatteryHealthParser {
    /** نسبة السعة الحالية القصوى إلى سعة التصميم، أو null إن كانت القراءة غير منطقية. */
    fun capacityPercent(full: Long?, design: Long?): Int? {
        if (full == null || design == null || design <= 0L || full <= 0L) return null
        val p = (full * 100.0 / design).toInt()
        return p.takeIf { it in 40..120 }?.coerceAtMost(100)
    }

    fun firstNumber(out: String): Long? = Regex("-?\\d+").find(out)?.value?.toLongOrNull()
}
