package com.example.ageexam

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime

/** Unit flags (bit mask). */
object U {
    const val Y = 1
    const val M = 2
    const val D = 4
    const val H = 8
    const val MIN = 16
    const val ALL = 31

    fun has(mask: Int, flag: Int): Boolean = (mask and flag) != 0
}

/** One global configuration shared by the app preview and every widget. */
data class AppConfig(
    val birth: LocalDateTime,
    val exam: LocalDateTime,
    val ageUnits: Int = U.Y or U.M or U.D,
    val examUnits: Int = U.D or U.H,
    val pad: Boolean = true,
    val weight: Int = 1,              // 0 thin, 1 light, 2 bold
    val layout: Int = 0,              // 0 auto, 1 horizontal (one line), 2 stacked
    val boxSize: Int = 100,           // % of the widget area covered by the box (30..100)
    val fontSize: Int = 100,          // % of the largest text that fits the box (20..100)
    val iconMode: Boolean = false,    // true: box is a square like an app icon (side = boxSize % of the shorter widget side)
    val cornerDp: Int = 24,           // corner radius of the box in dp (0..60; large = circle)
    val vOffset: Int = 0,             // box position in the free vertical space: -100 top, 0 centre, 100 bottom
    val doubleTap: Boolean = true,    // double tap on the widget opens the app
    val transparent: Boolean = false,
    val bgColor: Int = 0x1B1B1F,
    val bgOpacity: Int = 90,          // percent
    val textColor: Int = 0xFFFFFF
) {
    fun bgAlpha(): Int = if (transparent) 0 else bgOpacity.coerceIn(0, 100) * 255 / 100
}

object ConfigStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ageexam", Context.MODE_PRIVATE)

    fun defaults(): AppConfig = AppConfig(
        birth = LocalDateTime.of(2000, 1, 1, 0, 0),
        exam = LocalDate.now().plusDays(90).atTime(10, 0)
    )

    private fun parse(s: String?, fallback: LocalDateTime): LocalDateTime {
        return try {
            if (s == null) fallback else LocalDateTime.parse(s)
        } catch (e: Exception) {
            fallback
        }
    }

    fun load(ctx: Context): AppConfig {
        val sp = prefs(ctx)
        val d = defaults()
        if (!sp.contains("exam")) {
            save(ctx, d)
            return d
        }
        return AppConfig(
            birth = parse(sp.getString("birth", null), d.birth),
            exam = parse(sp.getString("exam", null), d.exam),
            ageUnits = sp.getInt("ageUnits", d.ageUnits),
            examUnits = sp.getInt("examUnits", d.examUnits),
            pad = sp.getBoolean("pad", d.pad),
            weight = sp.getInt("weight", d.weight),
            layout = sp.getInt("layout", d.layout),
            boxSize = sp.getInt("boxSize", d.boxSize),
            fontSize = sp.getInt("fontSize", d.fontSize),
            iconMode = sp.getBoolean("iconMode", d.iconMode),
            cornerDp = sp.getInt("cornerDp", d.cornerDp),
            vOffset = sp.getInt("vOffset", d.vOffset),
            doubleTap = sp.getBoolean("doubleTap", d.doubleTap),
            transparent = sp.getBoolean("transparent", d.transparent),
            bgColor = sp.getInt("bgColor", d.bgColor),
            bgOpacity = sp.getInt("bgOpacity", d.bgOpacity),
            textColor = sp.getInt("textColor", d.textColor)
        )
    }

    fun save(ctx: Context, c: AppConfig) {
        prefs(ctx).edit()
            .putString("birth", c.birth.toString())
            .putString("exam", c.exam.toString())
            .putInt("ageUnits", c.ageUnits)
            .putInt("examUnits", c.examUnits)
            .putBoolean("pad", c.pad)
            .putInt("weight", c.weight)
            .putInt("layout", c.layout)
            .putInt("boxSize", c.boxSize)
            .putInt("fontSize", c.fontSize)
            .putBoolean("iconMode", c.iconMode)
            .putInt("cornerDp", c.cornerDp)
            .putInt("vOffset", c.vOffset)
            .putBoolean("doubleTap", c.doubleTap)
            .putBoolean("transparent", c.transparent)
            .putInt("bgColor", c.bgColor)
            .putInt("bgOpacity", c.bgOpacity)
            .putInt("textColor", c.textColor)
            .apply()
    }
}
