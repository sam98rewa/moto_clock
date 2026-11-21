package com.example.ageexam

import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** HSV <-> RGB without framework helpers (hue 0..360, s/v 0..1). */
object Hsv {
    fun toRgb(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f / 60f
        val i = hh.toInt()
        val f = hh - i
        val p = v * (1f - s)
        val q = v * (1f - s * f)
        val t = v * (1f - s * (1f - f))
        val (r, g, b) = when (i) {
            0 -> Triple(v, t, p)
            1 -> Triple(q, v, p)
            2 -> Triple(p, v, t)
            3 -> Triple(p, q, v)
            4 -> Triple(t, p, v)
            else -> Triple(v, p, q)
        }
        return (Math.round(r * 255f) shl 16) or (Math.round(g * 255f) shl 8) or Math.round(b * 255f)
    }

    /** Returns [hue 0..360, saturation 0..1, value 0..1]. */
    fun fromRgb(rgb: Int): FloatArray {
        val r = ((rgb shr 16) and 255) / 255f
        val g = ((rgb shr 8) and 255) / 255f
        val b = (rgb and 255) / 255f
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        val d = mx - mn
        var h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        if (h < 0f) h += 360f
        return floatArrayOf(h, if (mx == 0f) 0f else d / mx, mx)
    }
}

/** Big square: saturation to the right, brightness upwards, for the current hue. */
private class SvView(ctx: Context, val onChange: () -> Unit) : View(ctx) {
    var hue = 0f
    var sat = 1f
    var value = 1f
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        r.set(0f, 0f, w, h)
        val rad = 18 * d
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, w, 0f, 0xFFFFFFFF.toInt(), 0xFF000000.toInt() or Hsv.toRgb(hue, 1f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rad, rad, p)
        p.shader = LinearGradient(0f, 0f, 0f, h, 0x00000000, 0xFF000000.toInt(), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rad, rad, p)
        p.shader = null
        val x = sat * w
        val y = (1f - value) * h
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3.5f * d
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x, y, 11 * d, p)
        p.strokeWidth = 1f * d
        p.color = 0x55000000
        c.drawCircle(x, y, 13.5f * d, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        sat = (e.x / width).coerceIn(0f, 1f)
        value = 1f - (e.y / height).coerceIn(0f, 1f)
        invalidate()
        onChange()
        return true
    }
}

/** Rainbow bar for the hue. */
private class HueView(ctx: Context, val onChange: () -> Unit) : View(ctx) {
    var hue = 0f
    private val d = ctx.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cols = IntArray(7) { 0xFF000000.toInt() or Hsv.toRgb(it * 60f, 1f, 1f) }
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, w, 0f, cols, null, Shader.TileMode.CLAMP)
        r.set(0f, h * 0.15f, w, h * 0.85f)
        c.drawRoundRect(r, h, h, p)
        p.shader = null
        val x = (hue / 360f * (w - h)) + h / 2f
        p.color = 0xFFFFFFFF.toInt()
        c.drawCircle(x, h / 2f, h * 0.46f, p)
        p.color = 0xFF000000.toInt() or Hsv.toRgb(hue, 1f, 1f)
        c.drawCircle(x, h / 2f, h * 0.32f, p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        val h = height.toFloat()
        hue = (((e.x - h / 2f) / (width - h)).coerceIn(0f, 1f)) * 360f
        invalidate()
        onChange()
        return true
    }
}

/** Colour picker dialog: square + hue bar + preview + Cancel / Set. Calls onPick with 0xRRGGBB. */
object ColorPicker {
    fun show(ctx: Context, start: Int, onPick: (Int) -> Unit) {
        val d = ctx.resources.displayMetrics.density
        fun px(v: Int) = (v * d).toInt()
        fun rounded(color: Int, r: Int, stroke: Int = 0): GradientDrawable {
            val g = GradientDrawable()
            g.setColor(color)
            g.cornerRadius = r * d
            if (stroke != 0) g.setStroke(px(1), stroke)
            return g
        }

        val dialog = Dialog(ctx)
        val col = LinearLayout(ctx)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(px(18), px(18), px(18), px(18))
        col.background = rounded(0xFFFFFFFF.toInt(), 28)

        val hsv = Hsv.fromRgb(start and 0xFFFFFF)
        val preview = View(ctx)
        lateinit var sv: SvView
        lateinit var hue: HueView
        fun cur() = Hsv.toRgb(hue.hue, sv.sat, sv.value)
        fun sync() {
            sv.hue = hue.hue
            preview.background = rounded(0xFF000000.toInt() or cur(), 16)
            sv.invalidate()
        }
        sv = SvView(ctx) { sync() }
        hue = HueView(ctx) { sync() }
        sv.hue = hsv[0]; sv.sat = hsv[1]; sv.value = hsv[2]; hue.hue = hsv[0]

        val svLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(240))
        col.addView(sv, svLp)
        val hueLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(40))
        hueLp.topMargin = px(14)
        col.addView(hue, hueLp)
        val pvLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(52))
        pvLp.topMargin = px(12)
        col.addView(preview, pvLp)
        sync()

        val btns = LinearLayout(ctx)
        btns.orientation = LinearLayout.HORIZONTAL
        fun button(text: String, filled: Boolean, click: () -> Unit): TextView {
            val t = TextView(ctx)
            t.text = text
            t.gravity = Gravity.CENTER
            t.textSize = 16f
            t.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            t.setTextColor(if (filled) 0xFFFFFFFF.toInt() else 0xFF1B1D3A.toInt())
            if (filled) {
                val g = GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFF4F63E8.toInt(), 0xFF9B59F0.toInt())
                )
                g.cornerRadius = 18 * d
                t.background = g
            } else {
                t.background = rounded(0xFFFFFFFF.toInt(), 18, 0xFFE3E4EA.toInt())
            }
            t.setOnClickListener { click() }
            return t
        }
        val cancel = button("Cancel", false) { dialog.dismiss() }
        val set = button("Set", true) {
            onPick(cur())
            dialog.dismiss()
        }
        val bl = { first: Boolean ->
            val lp = LinearLayout.LayoutParams(0, px(54), 1f)
            if (first) lp.rightMargin = px(8) else lp.leftMargin = px(8)
            lp
        }
        btns.addView(cancel, bl(true))
        btns.addView(set, bl(false))
        val bLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        bLp.topMargin = px(14)
        col.addView(btns, bLp)

        dialog.setContentView(col)
        dialog.window?.setBackgroundDrawable(ColorDrawable(0))
        dialog.window?.setLayout(
            (ctx.resources.displayMetrics.widthPixels * 0.88f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.show()
    }
}
