package com.example.perfcontrol.background

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.perfcontrol.R
import com.example.perfcontrol.android.StatsReader
import com.example.perfcontrol.ui.MainActivity

class PerfWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(context))
    }

    companion object {
        fun build(ctx: Context): RemoteViews {
            val st = runCatching { StatsReader(ctx).read() }.getOrNull()
            val ramUsed = st?.let { 100 - (it.ramAvailMb * 100 / it.ramTotalMb.coerceAtLeast(1L)).toInt() }
            val v = RemoteViews(ctx.packageName, R.layout.widget_perf)
            v.setTextViewText(R.id.w_ram, if (ramUsed != null) "الذاكرة $ramUsed%" else "الذاكرة —")
            v.setTextViewText(R.id.w_temp, if (st != null) "${"%.1f".format(st.tempC)}°C · ${st.batteryPercent}%" else "—")

            val boost = Intent(ctx, ActionReceiver::class.java).setAction(Shortcuts.ACTION_BOOST)
            v.setOnClickPendingIntent(
                R.id.w_boost,
                PendingIntent.getBroadcast(ctx, 11, boost, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            val open = Intent(ctx, MainActivity::class.java)
            v.setOnClickPendingIntent(
                R.id.w_root,
                PendingIntent.getActivity(ctx, 12, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            return v
        }

        fun refresh(ctx: Context) {
            val m = AppWidgetManager.getInstance(ctx)
            val ids = m.getAppWidgetIds(ComponentName(ctx, PerfWidget::class.java))
            for (id in ids) m.updateAppWidget(id, build(ctx))
        }
    }
}
