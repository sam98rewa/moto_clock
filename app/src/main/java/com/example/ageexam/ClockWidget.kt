package com.example.ageexam

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.AlarmClock
import android.util.TypedValue
import android.widget.RemoteViews

/**
 * Large stacked clock (hours over minutes, date below). The time is drawn by the launcher itself
 * (TextClock), so it keeps ticking every minute without any background work from this app.
 * Text sizes are recalculated whenever the widget is resized. Tapping opens the clock app.
 */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) draw(context, mgr, id)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) {
        super.onAppWidgetOptionsChanged(context, mgr, id, newOptions)
        draw(context, mgr, id)
    }

    private fun draw(ctx: Context, mgr: AppWidgetManager, id: Int) {
        val o = mgr.getAppWidgetOptions(id)
        var w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        var h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        if (w <= 0) w = 110
        if (h <= 0) h = 110

        // Two stacked lines of digits plus a date line must fit the height; digits are about 0.72 em wide x2.
        val showDate = h >= 90
        val dateSp = if (showDate) (minOf(w / 9f, h / 9f)).coerceIn(10f, 18f) else 0f
        val availH = h - (if (showDate) dateSp * 1.9f else 0f) - 8f
        val digitSp = minOf(availH / 2.45f, (w - 8f) / 1.5f).coerceIn(18f, 150f)

        val rv = RemoteViews(ctx.packageName, R.layout.widget_clock)
        rv.setTextViewTextSize(R.id.clock_hour, TypedValue.COMPLEX_UNIT_SP, digitSp)
        rv.setTextViewTextSize(R.id.clock_min, TypedValue.COMPLEX_UNIT_SP, digitSp)
        if (showDate) {
            rv.setTextViewTextSize(R.id.clock_date, TypedValue.COMPLEX_UNIT_SP, dateSp)
            rv.setViewVisibility(R.id.clock_date, android.view.View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.clock_date, android.view.View.GONE)
        }

        val open = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(ctx, 200000 + id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        rv.setOnClickPendingIntent(R.id.clock_root, pi)

        mgr.updateAppWidget(id, rv)
    }
}
