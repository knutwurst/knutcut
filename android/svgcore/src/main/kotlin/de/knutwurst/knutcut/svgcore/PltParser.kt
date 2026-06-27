package de.knutwurst.knutcut.svgcore

import kotlin.math.abs

/**
 * Parses an HPGL/PLT file into polylines in millimeters. `PU` (pen up) starts a new contour and
 * `PD` (pen down) draws to each following coordinate; both may carry several coordinate pairs.
 * Coordinates are plotter units (40 per mm). Everything else (IN, SP, PG, …) is ignored.
 */
object PltParser {

    fun parse(text: String): List<Polyline> {
        val polys = ArrayList<Polyline>()
        var cur: ArrayList<Pt>? = null
        var x = 0.0
        var y = 0.0

        fun flush() {
            val c = cur
            if (c != null && c.size >= 2) {
                val closed = abs(c.first().xMm - c.last().xMm) < 1e-6 && abs(c.first().yMm - c.last().yMm) < 1e-6
                polys.add(Polyline(c, closed = closed))
            }
            cur = null
        }

        for (raw in text.split(';')) {
            val t = raw.trim()
            if (t.length < 2) continue
            val cmd = t.substring(0, 2).uppercase()
            if (cmd != "PU" && cmd != "PD") continue
            val nums = t.substring(2).split(Regex("[ ,]+")).filter { it.isNotEmpty() }.mapNotNull { it.toDoubleOrNull() }
            val pairs = nums.chunked(2).filter { it.size == 2 }
            if (cmd == "PU") {
                flush()
                if (pairs.isNotEmpty()) { x = pairs.last()[0]; y = pairs.last()[1] }
            } else {
                if (cur == null) cur = arrayListOf(Pt(x / UNITS_PER_MM, y / UNITS_PER_MM))
                for (p in pairs) {
                    x = p[0]; y = p[1]
                    cur!!.add(Pt(x / UNITS_PER_MM, y / UNITS_PER_MM))
                }
            }
        }
        flush()
        return polys
    }
}
