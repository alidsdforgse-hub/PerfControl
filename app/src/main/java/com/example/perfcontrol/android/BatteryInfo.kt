package com.example.perfcontrol.android

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build

data class BatteryDetail(
    val health: String,
    val cycles: Int?,
    val voltageMv: Int,
    val plugged: String,
    val tempC: Float,
    val level: Int,
)

object BatteryInfo {
    fun read(ctx: Context): BatteryDetail? {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val health = when (i.getIntExtra(BatteryManager.EXTRA_HEALTH, 1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "جيدة"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "ارتفاع حرارة"
            BatteryManager.BATTERY_HEALTH_DEAD -> "تالفة"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "جهد زائد"
            BatteryManager.BATTERY_HEALTH_COLD -> "باردة"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "عطل غير محدد"
            else -> "غير معروفة"
        }
        val cycles = if (Build.VERSION.SDK_INT >= 34) i.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it > 0 } else null
        val plugged = when (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "شاحن جداري"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "لاسلكي"
            else -> "غير موصول"
        }
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        return BatteryDetail(
            health = health,
            cycles = cycles,
            voltageMv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0),
            plugged = plugged,
            tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
            level = level * 100 / scale,
        )
    }
}
