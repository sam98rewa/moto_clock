package com.example.ageexam

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import java.time.LocalDateTime

/**
 * Live preview of the widget. Uses the same layout maths as the real widget.
 * Swipe up/down (or tap) to flip between the age page and the exam page.
 */
class PreviewView(ctx: Context) : View(ctx) {

    private var cfg: AppConfig = ConfigStore.defaults()
    private var wDp = 152f
    private var hDp = 172f
    private var pos = 0f
    private var page = 0
    private var downY = 0f
    private var animator: ValueAnimator? = null

    private val d = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clock = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, 1000)
        }
    }

    fun setConfig(c: AppConfig) {
        cfg = c
        invalidate()
    }

    fun setSizeDp(w: Float, h: Float) {
        wDp = w
        hDp = h
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post(clock)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(clock)
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> downY = e.y
            MotionEvent.ACTION_UP -> {
                val dy = e.y - downY
                val target = if (Math.abs(dy) > 24 * d) {
                    if (dy < 0) 1 else 0
                } else {
                    1 - page
                }
                goTo(target)
            }
        }
        return true
    }

    private fun goTo(p: Int) {
        page = p
        animator?.cancel()
        val a = ValueAnimator.ofFloat(pos, p.toFloat())
        a.duration = 320
        a.interpolator = AccelerateDecelerateInterpolator()
        a.addUpdateListener {
            pos = it.animatedValue as Float
            invalidate()
        }
        a.start()
        animator = a
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()

        // Fake wallpaper so transparency is visible
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(
            0f, 0f, w, h, 0xFF4568DC.toInt(), 0xFFB06AB3.toInt(), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(0f, 0f, w, h, 24 * d, 24 * d, paint)
        paint.shader = null

        val cardW = wDp * d
        val cardH = hDp * d
        val margin = 22 * d
        val s = minOf(1f, (w - 2 * margin) / cardW, (h - 2 * margin) / cardH)

        canvas.save()
        canvas.translate(w / 2f, h / 2f)
        canvas.scale(s, s)

        // The widget area is centred on (0,0); the box is drawn inside it, exactly like the real widget.
        Draw.drawBox(canvas, cardW, cardH, d, cfg, -cardW / 2f, -cardH / 2f)

        val box = Draw.boxRect(cardW, cardH, cfg)
        box.offset(-cardW / 2f, -cardH / 2f)
        val radius = minOf(cfg.cornerDp * d, minOf(box.width(), box.height()) / 2f)

        if (cfg.bgAlpha() < 40) {
            // faint outline so a transparent box is still visible in the preview
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1.5f * d / s
            paint.color = 0x66FFFFFF
            canvas.drawRoundRect(box, radius, radius, paint)
            paint.style = Paint.Style.FILL
        }

        // Like the real widget, nothing is drawn outside the widget area while pages slide.
        canvas.clipRect(-cardW / 2f, -cardH / 2f, cardW / 2f, cardH / 2f)

        val pages = Calc.pages(cfg, LocalDateTime.now())
        for (i in pages.indices) {
            // Out-going page fades out during the first half, the new one fades in during the
            // second half, so the two are never visible together (no ghost / double image).
            val a = 1f - 2f * Math.abs(i - pos)
            if (a <= 0f) continue
            val dy = (i - pos) * 16 * d
            Draw.drawPage(canvas, pages[i].segs, wDp, hDp, d, cfg, 0f, dy, Math.round(a * 255f))
        }
        canvas.restore()

        // page dots (preview only, not part of the real widget)
        for (i in 0..1) {
            paint.style = Paint.Style.FILL
            paint.color = if (Math.round(pos) == i) 0xFFFFFFFF.toInt() else 0x66FFFFFF
            canvas.drawCircle(w - 12 * d, h / 2f + (i - 0.5f) * 14 * d, 3 * d, paint)
        }
    }
}
