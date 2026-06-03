package de.knutwurst.knutcut.svgcore

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/** A path segment in absolute user coordinates. Arcs/quads are normalised to cubics. */
sealed class Seg
data class Line(val to: Pt) : Seg()
data class Cubic(val c1: Pt, val c2: Pt, val to: Pt) : Seg()

/** One subpath: a start point, its segments, and whether it closes back to the start. */
data class SubPath(val start: Pt, val segs: List<Seg>, val closed: Boolean)

/** Parses an SVG path "d" string into absolute subpaths (coordinates in the path's own user units). */
object SvgPath {

    fun parse(d: String): List<SubPath> {
        val t = Tokenizer(d)
        val out = ArrayList<SubPath>()
        var cur = Pt(0.0, 0.0)
        var startPt = Pt(0.0, 0.0)
        var segs = ArrayList<Seg>()
        var open = false
        var cubicRefl: Pt? = null   // reflected cubic control for a following S
        var quadRefl: Pt? = null    // reflected quad control for a following T

        fun flush(closed: Boolean) {
            if (open && segs.isNotEmpty()) out.add(SubPath(startPt, ArrayList(segs), closed))
            segs.clear()
            open = false
        }

        while (true) {
            val cmd = t.command() ?: break
            val rel = cmd.isLowerCase()
            when (cmd.uppercaseChar()) {
                'M' -> {
                    flush(false)
                    var x = t.num(); var y = t.num()
                    if (rel) { x += cur.xMm; y += cur.yMm }
                    cur = Pt(x, y); startPt = cur; open = true
                    cubicRefl = null; quadRefl = null
                    while (t.moreNums()) { // implicit line-to
                        var lx = t.num(); var ly = t.num()
                        if (rel) { lx += cur.xMm; ly += cur.yMm }
                        cur = Pt(lx, ly); segs.add(Line(cur))
                    }
                }
                'L' -> { while (t.moreNums()) { var x = t.num(); var y = t.num(); if (rel) { x += cur.xMm; y += cur.yMm }; cur = Pt(x, y); segs.add(Line(cur)) }; cubicRefl = null; quadRefl = null }
                'H' -> { while (t.moreNums()) { var x = t.num(); if (rel) x += cur.xMm; cur = Pt(x, cur.yMm); segs.add(Line(cur)) }; cubicRefl = null; quadRefl = null }
                'V' -> { while (t.moreNums()) { var y = t.num(); if (rel) y += cur.yMm; cur = Pt(cur.xMm, y); segs.add(Line(cur)) }; cubicRefl = null; quadRefl = null }
                'C' -> { while (t.moreNums()) {
                    val c1 = readPt(t, rel, cur); val c2 = readPt(t, rel, cur); val to = readPt(t, rel, cur)
                    segs.add(Cubic(c1, c2, to)); cubicRefl = reflect(c2, to); quadRefl = null; cur = to
                } }
                'S' -> { while (t.moreNums()) {
                    val c1 = cubicRefl ?: cur; val c2 = readPt(t, rel, cur); val to = readPt(t, rel, cur)
                    segs.add(Cubic(c1, c2, to)); cubicRefl = reflect(c2, to); quadRefl = null; cur = to
                } }
                'Q' -> { while (t.moreNums()) {
                    val q = readPt(t, rel, cur); val to = readPt(t, rel, cur)
                    segs.add(quadToCubic(cur, q, to)); quadRefl = reflect(q, to); cubicRefl = null; cur = to
                } }
                'T' -> { while (t.moreNums()) {
                    val q = quadRefl ?: cur; val to = readPt(t, rel, cur)
                    segs.add(quadToCubic(cur, q, to)); quadRefl = reflect(q, to); cubicRefl = null; cur = to
                } }
                'A' -> { while (t.moreNums()) {
                    val rx = t.num(); val ry = t.num(); val rot = t.num()
                    val large = t.flag(); val sweep = t.flag()
                    var x = t.num(); var y = t.num()
                    if (rel) { x += cur.xMm; y += cur.yMm }
                    val to = Pt(x, y)
                    for (cb in arcToCubics(cur, rx, ry, rot, large, sweep, to)) segs.add(cb)
                    cubicRefl = null; quadRefl = null; cur = to
                } }
                'Z' -> { if (open) { if (cur != startPt) segs.add(Line(startPt)); flush(true); cur = startPt; cubicRefl = null; quadRefl = null } }
            }
        }
        flush(false)
        return out
    }

    private fun readPt(t: Tokenizer, rel: Boolean, cur: Pt): Pt {
        var x = t.num(); var y = t.num()
        if (rel) { x += cur.xMm; y += cur.yMm }
        return Pt(x, y)
    }

    private fun reflect(ctrl: Pt, about: Pt) = Pt(2 * about.xMm - ctrl.xMm, 2 * about.yMm - ctrl.yMm)

    private fun quadToCubic(p0: Pt, q: Pt, p2: Pt): Cubic {
        val c1 = Pt(p0.xMm + 2.0 / 3.0 * (q.xMm - p0.xMm), p0.yMm + 2.0 / 3.0 * (q.yMm - p0.yMm))
        val c2 = Pt(p2.xMm + 2.0 / 3.0 * (q.xMm - p2.xMm), p2.yMm + 2.0 / 3.0 * (q.yMm - p2.yMm))
        return Cubic(c1, c2, p2)
    }

    /** SVG elliptical arc to a list of cubic segments (endpoint -> centre parameterisation, W3C appendix). */
    private fun arcToCubics(p0: Pt, rxIn: Double, ryIn: Double, xRotDeg: Double, large: Int, sweep: Int, p1: Pt): List<Cubic> {
        if (rxIn == 0.0 || ryIn == 0.0 || (p0 == p1)) return listOf(Cubic(p0, p1, p1))
        var rx = abs(rxIn); var ry = abs(ryIn)
        val phi = Math.toRadians(xRotDeg % 360.0)
        val cosP = cos(phi); val sinP = sin(phi)
        val dx = (p0.xMm - p1.xMm) / 2.0; val dy = (p0.yMm - p1.yMm) / 2.0
        val x1p = cosP * dx + sinP * dy
        val y1p = -sinP * dx + cosP * dy
        var lambda = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
        if (lambda > 1.0) { val s = sqrt(lambda); rx *= s; ry *= s }
        val sign = if (large != sweep) 1.0 else -1.0
        var num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
        if (num < 0) num = 0.0
        val den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
        val coef = sign * sqrt(num / den)
        val cxp = coef * (rx * y1p / ry)
        val cyp = coef * (-(ry * x1p / rx))
        val cx = cosP * cxp - sinP * cyp + (p0.xMm + p1.xMm) / 2.0
        val cy = sinP * cxp + cosP * cyp + (p0.yMm + p1.yMm) / 2.0

        fun angle(ux: Double, uy: Double, vx: Double, vy: Double): Double {
            val dot = ux * vx + uy * vy
            val len = sqrt((ux * ux + uy * uy) * (vx * vx + vy * vy))
            var ang = acos((dot / len).coerceIn(-1.0, 1.0))
            if (ux * vy - uy * vx < 0) ang = -ang
            return ang
        }
        val theta1 = angle(1.0, 0.0, (x1p - cxp) / rx, (y1p - cyp) / ry)
        var dTheta = angle((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
        if (sweep == 0 && dTheta > 0) dTheta -= 2 * Math.PI
        if (sweep == 1 && dTheta < 0) dTheta += 2 * Math.PI

        val segCount = ceil(abs(dTheta) / (Math.PI / 2)).toInt().coerceAtLeast(1)
        val delta = dTheta / segCount
        val tParam = 4.0 / 3.0 * tan(delta / 4.0)
        val out = ArrayList<Cubic>(segCount)
        var th = theta1
        var startX = p0.xMm; var startY = p0.yMm
        for (i in 0 until segCount) {
            val th2 = th + delta
            val cosTh = cos(th); val sinTh = sin(th)
            val cosTh2 = cos(th2); val sinTh2 = sin(th2)
            // endpoint of this segment on the ellipse, then rotated
            val ex = cx + (rx * cosTh2 * cosP - ry * sinTh2 * sinP)
            val ey = cy + (rx * cosTh2 * sinP + ry * sinTh2 * cosP)
            // tangents
            val dx1 = -rx * sinTh; val dy1 = ry * cosTh
            val dx2 = -rx * sinTh2; val dy2 = ry * cosTh2
            val c1x = startX + tParam * (cosP * dx1 - sinP * dy1)
            val c1y = startY + tParam * (sinP * dx1 + cosP * dy1)
            val c2x = ex - tParam * (cosP * dx2 - sinP * dy2)
            val c2y = ey - tParam * (sinP * dx2 + cosP * dy2)
            out.add(Cubic(Pt(c1x, c1y), Pt(c2x, c2y), Pt(ex, ey)))
            startX = ex; startY = ey; th = th2
        }
        return out
    }

    /** Minimal, tolerant scanner for SVG path data. */
    private class Tokenizer(private val s: String) {
        private var i = 0
        private fun skipSep() { while (i < s.length && (s[i] == ' ' || s[i] == ',' || s[i] == '\n' || s[i] == '\t' || s[i] == '\r')) i++ }

        fun command(): Char? {
            skipSep()
            if (i >= s.length) return null
            val c = s[i]
            return if (c.isLetter()) { i++; c } else null
        }

        fun moreNums(): Boolean {
            skipSep()
            if (i >= s.length) return false
            val c = s[i]
            return c.isDigit() || c == '.' || c == '+' || c == '-'
        }

        fun num(): Double {
            skipSep()
            val st = i
            if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
            var sawDot = false
            while (i < s.length) {
                val c = s[i]
                if (c.isDigit()) i++
                else if (c == '.' && !sawDot) { sawDot = true; i++ }
                else if (c == 'e' || c == 'E') { i++; if (i < s.length && (s[i] == '+' || s[i] == '-')) i++ }
                else break
            }
            return s.substring(st, i).toDouble()
        }

        fun flag(): Int {
            skipSep()
            val c = s[i]; i++
            return if (c == '1') 1 else 0
        }
    }
}
