package com.example.ageexam

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import java.time.LocalDateTime

/**
 * The one widget. It is a single picture drawn by Draw (the same code as the app preview),
 * so there is no scrolling list or background service that a launcher could fail to load.
 * Resizable from 1x1 up; the picture is redrawn for the new size.
 */
class AgeExamWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            try {
                WidgetRenderer.update(context, id)
            } catch (e: Exception) {
                Diag.error(context, e)
            }
        }
        Ticker.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // New size => redraw so the text fills the box again.
        try {
            WidgetRenderer.update(context, appWidgetId)
        } catch (e: Exception) {
            Diag.error(context, e)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (id in appWidgetIds) Tap.clear(context, id)
    }

    override fun onEnabled(context: Context) {
        Ticker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        Ticker.schedule(context)
    }
}

object WidgetRenderer {

    /** Current widget size in dp (width, height), as reported by the launcher. */
    fun sizeDp(ctx: Context, id: Int): Pair<Float, Float> {
        val o = AppWidgetManager.getInstance(ctx).getAppWidgetOptions(id)
        val land = ctx.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        var w = o.getInt(
            if (land) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0
        )
        var h = o.getInt(
            if (land) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0
        )
        if (w <= 0) w = 70
        if (h <= 0) h = 70
        return Pair(w.toFloat(), h.toFloat())
    }

    /** Keeps the picture within a safe memory size on very large widgets. */
    fun bitmapScale(wDp: Float, hDp: Float, density: Float): Float {
        val longest = maxOf(wDp, hDp) * density
        return if (longest > 1400f) 1400f / longest else 1f
    }

    fun updateAll(ctx: Context) {
        for (id in Ticker.allIds(ctx)) {
            try {
                update(ctx, id)
            } catch (e: Exception) {
                Diag.error(ctx, e)
            }
        }
    }

    /** What a tap on the widget does: a normal tap goes to the receiver, which switches the page. */
    private fun tapIntent(ctx: Context, id: Int): PendingIntent {
        val i = Intent(ctx, TickReceiver::class.java).setAction(Ticker.TAP)
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        return PendingIntent.getBroadcast(ctx, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** What a tap does for a short moment after a tap: open Timer (that is the double tap). */
    private fun openIntent(ctx: Context, id: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
        i.putExtra(MainActivity.EXTRA_UNDO, id)
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            ctx, 100000 + id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Draws the current page of one widget (box + text) as one picture and shows it. */
    fun update(ctx: Context, id: Int, doubleTapWindow: Boolean = false) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val cfg = ConfigStore.load(ctx)
        val pages = Calc.pages(cfg, LocalDateTime.now())
        val page = Tap.page(ctx, id).coerceIn(0, pages.size - 1)

        val size = sizeDp(ctx, id)
        val density = ctx.resources.displayMetrics.density
        val scale = bitmapScale(size.first, size.second, density)
        val bw = maxOf(1, Math.round(size.first * density * scale))
        val bh = maxOf(1, Math.round(size.second * density * scale))

        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        Draw.drawBox(canvas, bw.toFloat(), bh.toFloat(), density * scale, cfg, 0f, 0f)
        Draw.drawPage(canvas, pages[page].segs, size.first, size.second, density * scale, cfg, bw / 2f, bh / 2f)

        val rv = RemoteViews(ctx.packageName, R.layout.widget_root)
        rv.setImageViewBitmap(R.id.img, bmp)
        val click = if (doubleTapWindow && cfg.doubleTap) openIntent(ctx, id) else tapIntent(ctx, id)
        rv.setOnClickPendingIntent(R.id.root, click)
        rv.setOnClickPendingIntent(R.id.img, click)

        mgr.updateAppWidget(id, rv)
        Diag.drew(ctx)
    }

    /** Puts the normal tap action back (no redraw), once the double-tap window is over. */
    fun armTap(ctx: Context, id: Int) {
        val rv = RemoteViews(ctx.packageName, R.layout.widget_root)
        val click = tapIntent(ctx, id)
        rv.setOnClickPendingIntent(R.id.root, click)
        rv.setOnClickPendingIntent(R.id.img, click)
        AppWidgetManager.getInstance(ctx).partiallyUpdateAppWidget(id, rv)
    }
}

/** Remembers which page each widget shows. */
object Tap {
    /** After a tap, a second tap within this time opens Timer instead of switching the page. */
    const val WINDOW_MS = 400L

    /** A tap this soon after the previous one counts as a double tap, whichever way it arrives. */
    const val LATE_MS = 650L

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("timer_state", Context.MODE_PRIVATE)

    fun page(ctx: Context, id: Int): Int = prefs(ctx).getInt("page_$id", 0)

    /** Time of the last tap, used to spot a double tap even if the launcher was slow to swap the click action. */
    fun lastTap(ctx: Context, id: Int): Long = prefs(ctx).getLong("tapms_$id", 0L)

    fun setLastTap(ctx: Context, id: Int, t: Long) {
        prefs(ctx).edit().putLong("tapms_$id", t).apply()
    }

    fun clear(ctx: Context, id: Int) {
        prefs(ctx).edit().remove("page_$id").remove("tapms_$id").apply()
    }

    /** Switches to the other page. Pass doubleTapWindow=true to arm "second tap opens Timer". */
    fun toggle(ctx: Context, id: Int, doubleTapWindow: Boolean = false) {
        val sp = prefs(ctx)
        sp.edit().putInt("page_$id", (sp.getInt("page_$id", 0) + 1) % 2).apply()
        try {
            WidgetRenderer.update(ctx, id, doubleTapWindow)
        } catch (e: Exception) {
            Diag.error(ctx, e)
        }
    }
}

/** Small log kept in storage so problems on a phone can be seen inside the app. */
object Diag {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("timer_state", Context.MODE_PRIVATE)

    fun tapped(ctx: Context) {
        val sp = prefs(ctx)
        sp.edit().putInt("taps", sp.getInt("taps", 0) + 1).putLong("last_tap", System.currentTimeMillis()).apply()
    }

    fun drew(ctx: Context) {
        prefs(ctx).edit().putLong("last_draw", System.currentTimeMillis()).apply()
    }

    fun error(ctx: Context, e: Exception) {
        prefs(ctx).edit().putString("error", e.toString().take(180)).apply()
    }

    private fun ago(t: Long): String {
        if (t == 0L) return "never"
        val s = (System.currentTimeMillis() - t) / 1000
        return if (s < 60) "${s}s ago" else if (s < 3600) "${s / 60} min ago" else "${s / 3600} h ago"
    }

    fun summary(ctx: Context): String {
        val sp = prefs(ctx)
        return "Taps received: ${sp.getInt("taps", 0)} (last ${ago(sp.getLong("last_tap", 0L))})\n" +
            "Last redraw: ${ago(sp.getLong("last_draw", 0L))}\n" +
            "Last error: ${sp.getString("error", null) ?: "none"}"
    }
}

/** Keeps the widget numbers fresh and handles taps. */
object Ticker {
    const val ACTION = "com.example.ageexam.TICK"
    const val TAP = "com.example.ageexam.TAP"

    fun allIds(ctx: Context): IntArray =
        AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, AgeExamWidgetProvider::class.java))

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, TickReceiver::class.java).setAction(ACTION)
        return PendingIntent.getBroadcast(
            ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** Every minute if minutes are shown, every 5 min for hours, otherwise every 30 min. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx)
        if (allIds(ctx).isEmpty()) {
            am.cancel(pi)
            return
        }
        val c = ConfigStore.load(ctx)
        val units = c.ageUnits or c.examUnits
        val step = if (U.has(units, U.MIN)) 60_000L else if (U.has(units, U.H)) 300_000L else 1_800_000L
        val next = (System.currentTimeMillis() / step + 1) * step
        am.setAndAllowWhileIdle(AlarmManager.RTC, next, pi)
    }
}

class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Ticker.TAP) {
            handleTap(context, intent)
            return
        }
        // Timer tick, time change, reboot or app update: redraw everything.
        WidgetRenderer.updateAll(context)
        Ticker.schedule(context)
    }

    private fun handleTap(context: Context, intent: Intent) {
        val id = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return

        Diag.tapped(context)
        if (!ConfigStore.load(context).doubleTap) {
            Tap.toggle(context, id, false)   // single tap only: switch page, nothing else
            return
        }
        // Second tap that still arrived as a normal tap (slow launcher / second space / dual profile):
        // detect it by time, undo the first switch and open Timer.
        val now = System.currentTimeMillis()
        val last = Tap.lastTap(context, id)
        if (last != 0L && now - last < Tap.LATE_MS) {
            Tap.setLastTap(context, id, 0L)
            Tap.toggle(context, id, false)
            try {
                val i = Intent(context, MainActivity::class.java)
                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                context.startActivity(i)
            } catch (e: Exception) {
                Diag.error(context, e)
            }
            return
        }
        Tap.setLastTap(context, id, now)
        // Stay alive for the double-tap window, then restore the normal tap action.
        val pending = goAsync()
        Tap.toggle(context, id, true)
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                WidgetRenderer.armTap(context, id)
            } catch (e: Exception) {
                Diag.error(context, e)
            }
            pending.finish()
        }, Tap.WINDOW_MS)
    }
}
