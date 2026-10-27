package com.example.perfcontrol.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.perfcontrol.android.PrefsStateStore
import com.example.perfcontrol.core.GAME_ACTIVE_KEY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object Shortcuts {
    const val ACTION_GAME = "com.example.perfcontrol.ACTION_GAME"
    const val ACTION_BOOST = "com.example.perfcontrol.ACTION_BOOST"
    const val ACTION_CLEAN = "com.example.perfcontrol.ACTION_CLEAN"
    private const val CHANNEL = "shortcuts"
    private const val ID = 5001

    private fun pending(ctx: Context, action: String, code: Int): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun show(ctx: Context, status: String = "اختصارات سريعة بدون فتح التطبيق") {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "اختصارات الأداء", NotificationManager.IMPORTANCE_LOW))
        @Suppress("DEPRECATION")
        val n = Notification.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("متحكم الأداء")
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_play, "وضع الألعاب", pending(ctx, ACTION_GAME, 1))
            .addAction(android.R.drawable.ic_menu_rotate, "رفع الأداء", pending(ctx, ACTION_BOOST, 2))
            .addAction(android.R.drawable.ic_menu_delete, "تنظيف", pending(ctx, ACTION_CLEAN, 3))
            .build()
        runCatching { nm.notify(ID, n) }
    }

    fun hide(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(ID)
    }
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val msg = when (action) {
                    Shortcuts.ACTION_GAME -> BgActions.toggleGame(app)
                    Shortcuts.ACTION_BOOST -> BgActions.boost(app)
                    Shortcuts.ACTION_CLEAN -> BgActions.clean(app)
                    else -> null
                }
                if (msg != null) Shortcuts.show(app, msg)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}

/** مربع في لوحة الإعدادات السريعة لتشغيل/إيقاف وضع الألعاب. */
class GameTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { BgActions.toggleGame(app) }
            refreshTile()
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val on = PrefsStateStore(applicationContext).original(GAME_ACTIVE_KEY) != null
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "وضع الألعاب"
        tile.updateTile()
    }
}
