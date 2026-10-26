package com.example.perfcontrol.android

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.content.ComponentName
import java.io.File

data class AppEntry(val pkg: String, val label: String)

class DeviceInfo(private val ctx: Context) {
    private val pm: PackageManager get() = ctx.packageManager

    fun dynamicProtected(): Set<String> {
        val set = mutableSetOf(ctx.packageName)
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.let { set += it }
        }
        runCatching {
            Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
                ?.let { ComponentName.unflattenFromString(it)?.packageName }?.let { set += it }
        }
        runCatching {
            (ctx.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage?.let { set += it }
        }
        runCatching { Telephony.Sms.getDefaultSmsPackage(ctx)?.let { set += it } }
        return set
    }

    @Suppress("DEPRECATION")
    fun userApps(): List<AppEntry> = runCatching {
        pm.getInstalledApplications(0)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && it.packageName != ctx.packageName }
            .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    fun bootCapableApps(): List<AppEntry> = runCatching {
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .filter { pi ->
                val ai = pi.applicationInfo ?: return@filter false
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 &&
                    pi.packageName != ctx.packageName &&
                    pi.requestedPermissions?.contains("android.permission.RECEIVE_BOOT_COMPLETED") == true
            }
            .map { AppEntry(it.packageName, pm.getApplicationLabel(it.applicationInfo!!).toString()) }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())

    fun label(pkg: String): String = runCatching {
        @Suppress("DEPRECATION")
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)
}

data class SystemStats(
    val batteryPercent: Int,
    val tempC: Float,
    val charging: Boolean,
    val storageUsedGb: Double,
    val storageTotalGb: Double,
    val ramTotalMb: Long,
    val ramAvailMb: Long,
    val rxKbps: Long,
    val txKbps: Long,
    val cpuFreqMhz: Int?,
)

class StatsReader(private val ctx: Context) {
    private var lastRx = TrafficStats.getTotalRxBytes()
    private var lastTx = TrafficStats.getTotalTxBytes()
    private var lastTime = SystemClock.elapsedRealtime()

    fun read(): SystemStats {
        val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val status = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        val stat = StatFs(Environment.getDataDirectory().path)
        val total = stat.totalBytes.toDouble()
        val used = total - stat.availableBytes.toDouble()

        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)

        val now = SystemClock.elapsedRealtime()
        val rx = TrafficStats.getTotalRxBytes()
        val tx = TrafficStats.getTotalTxBytes()
        val dt = (now - lastTime).coerceAtLeast(1)
        val rxK = if (rx >= 0 && lastRx >= 0) (rx - lastRx) * 1000 / dt / 1024 else 0
        val txK = if (tx >= 0 && lastTx >= 0) (tx - lastTx) * 1000 / dt / 1024 else 0
        lastRx = rx; lastTx = tx; lastTime = now

        return SystemStats(
            batteryPercent = if (level >= 0) level * 100 / scale else -1,
            tempC = temp,
            charging = charging,
            storageUsedGb = used / GB,
            storageTotalGb = total / GB,
            ramTotalMb = mi.totalMem / MB,
            ramAvailMb = mi.availMem / MB,
            rxKbps = rxK.coerceAtLeast(0),
            txKbps = txK.coerceAtLeast(0),
            cpuFreqMhz = readCpuFreqMhz(),
        )
    }

    private fun readCpuFreqMhz(): Int? {
        val freqs = (0 until 16).mapNotNull { i ->
            runCatching { File("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_cur_freq").readText().trim().toLong() }.getOrNull()
        }
        return if (freqs.isEmpty()) null else (freqs.max() / 1000).toInt()
    }

    private companion object {
        const val GB = 1024.0 * 1024 * 1024
        const val MB = 1024L * 1024
    }
}
