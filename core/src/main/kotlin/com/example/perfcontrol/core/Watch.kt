package com.example.perfcontrol.core

enum class ThermalAction { None, Throttle, Restore }

/** قرار حارس الحرارة: يخفّف عند تجاوز الحد، ويعيد الوضع بعد انخفاض كافٍ (فارق تثبيت لمنع التذبذب). */
class ThermalPolicy(private val limitC: Float, private val hysteresisC: Float = 4f) {
    fun decide(tempC: Float, throttled: Boolean): ThermalAction = when {
        !throttled && tempC >= limitC -> ThermalAction.Throttle
        throttled && tempC <= limitC - hysteresisC -> ThermalAction.Restore
        else -> ThermalAction.None
    }
}

enum class GameAction { None, Enable, Disable }

/** قرار التشغيل التلقائي لوضع الألعاب حسب التطبيق الظاهر حالياً. */
object GameDetector {
    const val LEAVE_GRACE_MS = 15_000L

    /**
     * @param top التطبيق الظاهر الآن (أو null إن لم يُعرف)
     * @param autoStarted هل شغّل التطبيق نفسه وضع الألعاب تلقائياً (لا نوقف ما شغّله المستخدم يدوياً)
     * @param awayMs منذ كم ميلي ثانية لم تكن أي لعبة ظاهرة
     */
    fun decide(top: String?, games: Set<String>, gameModeOn: Boolean, autoStarted: Boolean, awayMs: Long): GameAction {
        val inGame = top != null && top in games
        return when {
            inGame && !gameModeOn -> GameAction.Enable
            !inGame && gameModeOn && autoStarted && awayMs >= LEAVE_GRACE_MS -> GameAction.Disable
            else -> GameAction.None
        }
    }
}
