package com.example.ageexam

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface

/**
 * Single source of truth for how the widget looks.
 * The in-app preview and the real home-screen widget both call these functions,
 * so they are the same at any size.
 *
 * Terms: the "widget" is the cell area the launcher gives us. The "box" is the
 * coloured rounded rectangle drawn inside it (boxSize % of the widget, centred).
 * At font size 100% the text grows until it is about 1 mm from the box edge.
 * Lower font sizes scale it down from there, so it never goes beyond the box.
 */
object Draw {
    /** Height of a digit relative to the font size (sans-serif digits are ~0.71). */
    private const val CAP = 0.72f

    /** Space between stacked lines, relative to the font size. */
    private const val GAP = 0.22f

    /** One millimetre in dp (dp is 1/160 inch, an inch is 25.4 mm). */
    private const val MM_DP = 160f / 25.4f

    class Plan(val stacked: Boolean, val sizePx: Float)

    fun typeface(weight: Int): Typeface = when (weight) {
        0 -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
        2 -> Typeface.create("sans-serif", Typeface.BOLD)
        else -> Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private fun boxFraction(cfg: AppConfig): Float = cfg.boxSize.coerceIn(30, 100) / 100f

    private fun fontFraction(cfg: AppConfig): Float = cfg.fontSize.coerceIn(20, 100) / 100f

    /**
     * The box rectangle inside a widget of size (wPx, hPx).
     * Normal mode: boxSize % of the widget in both directions.
     * App-icon mode: a square whose side is boxSize % of the shorter widget side.
     * It is always centred horizontally; vOffset moves it inside the free vertical space.
     */
    fun boxRect(wPx: Float, hPx: Float, cfg: AppConfig): RectF {
        val f = boxFraction(cfg)
        val bw: Float
        val bh: Float
        if (cfg.iconMode) {
            val side = minOf(wPx, hPx) * f
            bw = side
            bh = side
        } else {
            bw = wPx * f
            bh = hPx * f
        }
        val l = (wPx - bw) / 2f
        val free = (hPx - bh) / 2f
        val t = free * (1f + cfg.vOffset.coerceIn(-100, 100) / 100f)
        return RectF(l, t, l + bw, t + bh)
    }

    /** Draws the coloured box. ox/oy move the whole widget area (used by the preview). */
    fun drawBox(canvas: Canvas, wPx: Float, hPx: Float, density: Float, cfg: AppConfig, ox: Float, oy: Float) {
        if (cfg.bgAlpha() <= 0) return
        val r = boxRect(wPx, hPx, cfg)
        r.offset(ox, oy)
        val radius = minOf(cfg.cornerDp.coerceIn(0, 100) * density, minOf(r.width(), r.height()) / 2f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.FILL
        p.color = (cfg.bgAlpha() shl 24) or (cfg.bgColor and 0xFFFFFF)
        canvas.drawRoundRect(r, radius, radius, p)
    }

    /** Digits all measure like "0" so the size does not jump when the numbers change. */
    private fun stable(s: String): String {
        val sb = StringBuilder()
        for (ch in s) sb.append(if (ch.isDigit()) '0' else ch)
        return sb.toString()
    }

    /** Width of the visible ink of a string (without the empty space around the glyphs). */
    private fun ink(p: Paint, s: String): Float {
        val r = Rect()
        p.getTextBounds(s, 0, s.length, r)
        return maxOf(r.width().toFloat(), 1f)
    }

    fun plan(segs: List<String>, wDp: Float, hDp: Float, density: Float, cfg: AppConfig): Plan {
        // Text must stay inside the box, so fit it to the box and not to the whole widget.
        val box = boxRect(wDp, hDp, cfg)
        val boxW = box.width() * density
        val boxH = box.height() * density
        // About 1 mm of breathing room, but never more than 15% of a very small box.
        val pad = minOf(MM_DP * density, minOf(boxW, boxH) * 0.15f)
        val availW = maxOf(boxW - 2f * pad, 1f)
        val availH = maxOf(boxH - 2f * pad, 1f)
        val k = fontFraction(cfg)

        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = typeface(cfg.weight)
        p.textSize = 100f

        val single = minOf(availW * 100f / ink(p, stable(segs.joinToString("."))), availH / CAP)
        if (segs.size < 2) return Plan(false, single * k)

        var widest = 1f
        for (s in segs) widest = maxOf(widest, ink(p, stable(s)))
        val n = segs.size
        val block = CAP * n + GAP * (n - 1)
        val stackedSize = minOf(availW * 100f / widest, availH / block)

        return when (cfg.layout) {
            1 -> Plan(false, single * k)
            2 -> Plan(true, stackedSize * k)
            // Auto: stack the numbers whenever that lets the text be clearly bigger.
            else -> if (stackedSize > single * 1.15f) Plan(true, stackedSize * k) else Plan(false, single * k)
        }
    }

    /** Draws one line so that its visible ink is centred on cx (not just its advance box). */
    private fun line(canvas: Canvas, text: String, cx: Float, baseline: Float, p: Paint) {
        val r = Rect()
        p.getTextBounds(text, 0, text.length, r)
        canvas.drawText(text, cx - (r.left + r.right) / 2f, baseline, p)
    }

    /** Draws one page's text centred on (cx, cy). Sizes are in dp, converted with density. */
    fun drawPage(
        canvas: Canvas, segs: List<String>, wDp: Float, hDp: Float,
        density: Float, cfg: AppConfig, cx: Float, cy: Float, alpha: Int = 255
    ) {
        val plan = plan(segs, wDp, hDp, density, cfg)
        // cx/cy are the widget centre; the box (and so the text) may sit off-centre.
        val box = boxRect(wDp * density, hDp * density, cfg)
        val cx = cx + (box.left + box.right) / 2f - wDp * density / 2f
        val cy = cy + (box.top + box.bottom) / 2f - hDp * density / 2f
        val s = plan.sizePx
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.typeface = typeface(cfg.weight)
        p.color = (alpha.coerceIn(0, 255) shl 24) or (cfg.textColor and 0xFFFFFF)
        p.textAlign = Paint.Align.LEFT
        p.textSize = s

        if (plan.stacked) {
            val n = segs.size
            val block = s * (CAP * n + GAP * (n - 1))
            var base = cy - block / 2f + s * CAP
            for (seg in segs) {
                line(canvas, seg, cx, base, p)
                base += s * (CAP + GAP)
            }
        } else {
            line(canvas, segs.joinToString("."), cx, cy + s * CAP / 2f, p)
        }
    }
}
