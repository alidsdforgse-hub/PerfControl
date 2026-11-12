package com.example.perfcontrol.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.perfcontrol.alerts.AlertNotifier
import com.example.perfcontrol.android.AutoPrefs
import com.example.perfcontrol.android.PrefsStateStore
import com.example.perfcontrol.android.StatsReader
import com.example.perfcontrol.core.AppProfilePolicy
import com.example.perfcontrol.core.ChargeAlertPolicy
import com.example.perfcontrol.core.CommandResult
import com.example.perfcontrol.core.ProfileAction
import com.example.perfcontrol.core.SettingChange
import com.example.perfcontrol.core.SettingsApplier
import com.example.perfcontrol.core.CpuSample
import com.example.perfcontrol.core.DisplayParsers
import com.example.perfcontrol.core.GAME_ACTIVE_KEY
import com.example.perfcontrol.core.ProcStatParser
import com.example.perfcontrol.core.GameAction
import com.example.perfcontrol.core.GameDetector
import com.example.perfcontrol.core.ThermalAction
import com.example.perfcontrol.core.ThermalPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** خدمة حراسة خفيفة: تكتشف فتح الألعاب وتراقب الحرارة. تعمل فقط إن فُعّلت إحدى الميزتين. */
class WatchService : Service() {
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "الحراسة", NotificationManager.IMPORTANCE_MIN))
            val n = Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("متحكم الأداء")
                .setContentText("الحراسة تعمل (الألعاب / الحرارة)")
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(ID, n)
            }
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (job == null) job = CoroutineScope(Dispatchers.Default).launch { loop() }
        return START_STICKY
    }

    override fun onDestroy() {
        GameOverlay.hide(applicationContext)
        job?.cancel()
        job = null
        super.onDestroy()
    }

    private suspend fun loop() {
        val ctx = applicationContext
        val prefs = AutoPrefs(ctx)
        val store = PrefsStateStore(ctx)
        val stats = StatsReader(ctx)
        var lastTop: String? = null
        var lastQuery = System.currentTimeMillis() - 60_000
        var awayStart = System.currentTimeMillis()
        var autoStarted = false
        var throttled = false
        var retryAt = 0L
        var prevCpu: CpuSample? = null
        var chargeNotified = false
        var profileApplied: String? = null

        while (currentCoroutineContext().isActive) {
            if (!prefs.autoGame && !prefs.thermalGuard && !prefs.overlay && !prefs.chargeAlert && !prefs.appProfiles) {
                stopSelf()
                return
            }
            val now = System.currentTimeMillis()
            try {
                if (prefs.autoGame || prefs.appProfiles) {
                    val (newTop, q) = readTop(ctx, lastQuery)
                    lastQuery = q
                    if (newTop != null) lastTop = newTop
                }
                if (prefs.chargeAlert) {
                    val st = stats.read()
                    if (ChargeAlertPolicy.shouldReset(st.batteryPercent, st.charging, prefs.chargeLimit)) chargeNotified = false
                    if (ChargeAlertPolicy.shouldNotify(st.batteryPercent, st.charging, prefs.chargeLimit, chargeNotified)) {
                        chargeNotified = true
                        AlertNotifier(ctx).notify(7004, "وصلت البطارية ${st.batteryPercent}%. افصل الشاحن لحماية البطارية.")
                    }
                }
                if (prefs.appProfiles) {
                    val gameOn = store.original(GAME_ACTIVE_KEY) != null
                    val act = AppProfilePolicy.decide(lastTop, prefs.profiles(), profileApplied, gameOn)
                    val nr = BgActions.runnerNow(ctx)
                    if (nr != null && act !is ProfileAction.None) {
                        val ap = SettingsApplier(nr, store, "appprof")
                        val keys = listOf(SettingChange("system", "min_refresh_rate", "60"), SettingChange("system", "peak_refresh_rate", "60"))
                        when (act) {
                            is ProfileAction.Apply -> {
                                ap.apply(listOf(
                                    SettingChange("system", "min_refresh_rate", "${act.hz}"),
                                    SettingChange("system", "peak_refresh_rate", "${act.hz}"),
                                ))
                                profileApplied = act.pkg
                            }
                            ProfileAction.Restore -> {
                                ap.restore(keys)
                                profileApplied = null
                            }
                            ProfileAction.None -> {}
                        }
                    }
                } else if (profileApplied != null) {
                    BgActions.runnerNow(ctx)?.let { SettingsApplier(it, store, "appprof").restore(
                        listOf(SettingChange("system", "min_refresh_rate", "60"), SettingChange("system", "peak_refresh_rate", "60")),
                    ) }
                    profileApplied = null
                }
                if (prefs.autoGame) {
                    val games = prefs.games
                    val on = store.original(GAME_ACTIVE_KEY) != null
                    if (!on) autoStarted = false
                    if (lastTop != null && lastTop in games) awayStart = now
                    if (now >= retryAt) {
                        when (GameDetector.decide(lastTop, games, on, autoStarted, now - awayStart)) {
                            GameAction.Enable -> {
                                val msg = BgActions.toggleGame(ctx, keep = lastTop)
                                if (msg.startsWith("Shizuku")) retryAt = now + 30_000 else autoStarted = true
                            }
                            GameAction.Disable -> {
                                val msg = BgActions.toggleGame(ctx)
                                autoStarted = false
                                AlertNotifier(ctx).notify(7002, msg)
                            }
                            GameAction.None -> {}
                        }
                    }
                }
                if (prefs.overlay) {
                    val gameOn = store.original(GAME_ACTIVE_KEY) != null
                    if (gameOn && GameOverlay.canShow(ctx)) {
                        val st = stats.read()
                        val ram = 100 - (st.ramAvailMb * 100 / st.ramTotalMb.coerceAtLeast(1L)).toInt()
                        val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
                        var loads = ""
                        val nr = BgActions.runnerNow(ctx)
                        if (nr != null) {
                            val cur = (nr.run("cat /proc/stat") as? CommandResult.Success)?.let { ProcStatParser.parse(it.output) }
                            val pv = prevCpu
                            if (cur != null && pv != null) loads += "CPU ${ProcStatParser.usagePercent(pv, cur).toInt()}%   "
                            if (cur != null) prevCpu = cur
                            val gpu = (nr.run("cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage") as? CommandResult.Success)
                                ?.let { DisplayParsers.gpuBusy(it.output) }
                            if (gpu != null) loads += "GPU $gpu%   "
                        }
                        GameOverlay.show(ctx, "${loads}حرارة ${"%.1f".format(st.tempC)}°   بطارية ${st.batteryPercent}%   رام $ram%   $clock")
                    } else {
                        GameOverlay.hide(ctx)
                    }
                } else {
                    GameOverlay.hide(ctx)
                }
                if (prefs.thermalGuard) {
                    val temp = stats.read().tempC
                    when (ThermalPolicy(prefs.thermalLimit.toFloat()).decide(temp, throttled)) {
                        ThermalAction.Throttle -> {
                            val msg = BgActions.thermalThrottle(ctx, lastTop)
                            if (!msg.startsWith("Shizuku")) {
                                throttled = true
                                AlertNotifier(ctx).notify(7003, "حرارة البطارية ${"%.1f".format(temp)}°C. $msg")
                            }
                        }
                        ThermalAction.Restore -> {
                            BgActions.thermalRelease(ctx)
                            throttled = false
                            AlertNotifier(ctx).notify(7003, "انخفضت الحرارة وأُعيد معدل التحديث")
                        }
                        ThermalAction.None -> {}
                    }
                }
            } catch (_: Exception) {
            }
            delay(3000)
        }
    }

    /** أحدث تطبيق ظهر في المقدمة منذ آخر استعلام. يعيد (التطبيق أو null، وقت الاستعلام). */
    @Suppress("DEPRECATION")
    private fun readTop(ctx: Context, since: Long): Pair<String?, Long> {
        val end = System.currentTimeMillis()
        return try {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(since, end)
            val e = UsageEvents.Event()
            var top: String? = null
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED || e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    top = e.packageName
                }
            }
            top to end
        } catch (_: Exception) {
            null to end
        }
    }

    companion object {
        private const val CHANNEL = "watch"
        private const val ID = 5002

        fun sync(ctx: Context) {
            val p = AutoPrefs(ctx)
            val i = Intent(ctx, WatchService::class.java)
            try {
                if (p.autoGame || p.thermalGuard || p.overlay || p.chargeAlert || p.appProfiles) ctx.startForegroundService(i) else ctx.stopService(i)
            } catch (_: Exception) {
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) WatchService.sync(context)
    }
}

/** شريط معلومات صغير يطفو أعلى الشاشة أثناء اللعب (لا يلتقط اللمس). */
object GameOverlay {
    private var view: android.widget.TextView? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    fun canShow(ctx: Context): Boolean = android.provider.Settings.canDrawOverlays(ctx)

    fun show(ctx: Context, text: String) {
        val app = ctx.applicationContext
        handler.post {
            try {
                val wm = app.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                var v = view
                if (v == null) {
                    v = android.widget.TextView(app).apply {
                        setTextColor(android.graphics.Color.WHITE)
                        textSize = 12f
                        setPadding(32, 10, 32, 10)
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(0xAA000000.toInt())
                            cornerRadius = 48f
                        }
                    }
                    val lp = android.view.WindowManager.LayoutParams(
                        android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                        android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                        android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        android.graphics.PixelFormat.TRANSLUCENT,
                    ).apply {
                        gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
                        y = 12
                    }
                    wm.addView(v, lp)
                    view = v
                }
                v.text = text
            } catch (_: Exception) {
            }
        }
    }

    fun hide(ctx: Context) {
        val app = ctx.applicationContext
        handler.post {
            try {
                val v = view ?: return@post
                (app.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).removeView(v)
            } catch (_: Exception) {
            }
            view = null
        }
    }
}
