package com.example.ageexam

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** One widget page, e.g. segs = ["28", "10", "26"]  ->  "28.10.26". */
data class Page(val segs: List<String>) {
    val text: String get() = segs.joinToString(".")
}

object Calc {

    /**
     * Splits the span between a and b into the selected units only.
     * Unselected larger units roll down into the next selected one.
     */
    fun segs(a: LocalDateTime, b: LocalDateTime, unitsIn: Int, pad: Boolean): List<String> {
        val units = if ((unitsIn and U.ALL) == 0) U.D else unitsIn
        var start = a
        var end = b
        if (end.isBefore(start)) {
            start = b
            end = a
        }
        var cur = start
        val vals = ArrayList<Long>()
        if (U.has(units, U.Y)) {
            val v = ChronoUnit.YEARS.between(cur, end)
            cur = cur.plusYears(v)
            vals.add(v)
        }
        if (U.has(units, U.M)) {
            val v = ChronoUnit.MONTHS.between(cur, end)
            cur = cur.plusMonths(v)
            vals.add(v)
        }
        if (U.has(units, U.D)) {
            val v = ChronoUnit.DAYS.between(cur, end)
            cur = cur.plusDays(v)
            vals.add(v)
        }
        var rem = ChronoUnit.SECONDS.between(cur, end)
        if (U.has(units, U.H)) {
            val h = rem / 3600
            rem -= h * 3600
            vals.add(h)
        }
        if (U.has(units, U.MIN)) {
            vals.add(rem / 60)
        }
        val out = ArrayList<String>()
        for ((i, v) in vals.withIndex()) {
            val s = v.toString()
            out.add(if (pad && i > 0) s.padStart(2, '0') else s)
        }
        return out
    }

    /** Page 0: age (birth -> now). Page 1: exam countdown (now -> exam). */
    fun pages(cfg: AppConfig, now: LocalDateTime): List<Page> {
        val age = Page(segs(cfg.birth, now, cfg.ageUnits, cfg.pad))
        val examEnd = if (cfg.exam.isBefore(now)) now else cfg.exam
        val exam = Page(segs(now, examEnd, cfg.examUnits, cfg.pad))
        return listOf(age, exam)
    }
}
