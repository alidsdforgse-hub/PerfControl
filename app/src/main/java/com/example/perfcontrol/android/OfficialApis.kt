package com.example.perfcontrol.android

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings

/** المسار الاحتياطي عندما لا يكون Shizuku جاهزاً. */
class OfficialApis(private val ctx: Context) {

    @Suppress("DEPRECATION")
    fun hasUsageAccess(): Boolean = runCatching {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun mostUsedApps(): List<AppEntry> = runCatching {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val info = DeviceInfo(ctx)
        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 24L * 3600 * 1000, now)
            .filter { it.packageName != ctx.packageName }
            .sortedByDescending { it.lastTimeUsed }
            .distinctBy { it.packageName }
            .take(20)
            .map { AppEntry(it.packageName, info.label(it.packageName)) }
    }.getOrDefault(emptyList())

    private fun start(i: Intent) {
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openUsageAccess() = start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openAppDetails(pkg: String) =
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    fun openStorageSettings() = start(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))

    fun openMiuiAutostart() {
        val i = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { ctx.startActivity(i) }.isSuccess
        if (!ok) openAppDetails(ctx.packageName)
    }
}
