package de.knutwurst.knutcut.svgcore

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Drag-knife compensation, ported from the stock app's `pltFixUtils`. A trailing (drag) blade pivots
 * behind the carriage, so at a sharp corner it must be walked around a small arc, and a closed
 * contour needs a little overshoot so it ends flush. This rewrites a list of `PU`/`PD` commands:
 *
 * - At every interior vertex whose turn is sharp enough (interior angle < 150°) and whose adjoining
 *   segments are long enough (> [MIN_SEG] units), the corner point is replaced by four points that
 *   trace a [OFFSET]-unit arc, letting the blade swivel into the new direction.
 * - A closed contour gets a [CLOSE_LEAD]-unit lead-in appended at the end and its last point pushed
 *   [OFFSET] units further along, so the cut closes without a nick.
 *
 * All distances are in plotter units (40/mm) and rounded, matching the original exactly.
 */
object DragKnife {
    /** Interior angles sharper than this get corner compensation (150°, as in the stock app). */
    private val SHARP = 150.0 * Math.PI / 180.0
    private const val MIN_SEG = 5.0
    private const val OFFSET = 13.0   // blade-offset arc radius / overshoot
    private const val CLOSE_LEAD = 26.0

    private data class P(val x: Int, val y: Int)

    /** Compensate a flat PU/PD command stream by processing each contour (each PU starts one). */
    fun process(commands: List<String>): List<String> {
        if (commands.isEmpty()) return commands
        val out = ArrayList<String>(commands.size + commands.size / 2)
        var contour = ArrayList<String>()
        for (cmd in commands) {
            if (cmd.startsWith("PU") && contour.isNotEmpty()) {
                out.addAll(processContour(contour))
                contour = ArrayList()
            }
            contour.add(cmd)
        }
        if (contour.isNotEmpty()) out.addAll(processContour(contour))
        return out
    }

    /** One contour: a leading PU followed by PD points. Mirrors `pltFixUtils.precessOne`. */
    private fun processContour(cmds: List<String>): List<String> {
        val p = cmds.toMutableList()

        // Pull any trailing PU moves aside; they are re-appended unchanged.
        val tail = ArrayList<String>()
        while (p.isNotEmpty() && p.last().startsWith("PU")) tail.add(p.removeAt(p.lastIndex))
        if (p.isEmpty()) return cmds

        // Is this contour closed (head move returns to the last cut point)?
        val headCoords = if (p[0].startsWith("PU")) p[0].substring(2) else null
        val closed = headCoords != null && p.size >= 2 && headCoords == p.last().substring(2)
        // Closed but the first cut differs from the head: repeat the closing point right after the PU.
        if (closed && headCoords != p[1].substring(2)) p.add(1, "PD" + p.last().substring(2))

        // Pull the leading PU(s) aside; the compensation works on the PD run only.
        val head = ArrayList<String>()
        while (p.isNotEmpty() && p[0].startsWith("PU")) head.add(p.removeAt(0))
        if (p.isEmpty()) return head + tail

        dedupeAdjacent(p)

        // Lead-in: a point CLOSE_LEAD units along the first edge, appended at the end.
        if (closed && p.size >= 2) {
            val k = parse(p[0]); val s = parse(p[1])
            if (k != null && s != null) {
                val c = along(k, s, CLOSE_LEAD)
                p.add("PD${c.x},${c.y}")
            }
        }
        dedupeAdjacent(p)
        if (p.size == 1) p.add(p[0])

        // Corner compensation: replace each sharp interior vertex with a four-point swivel arc.
        val result = p.toMutableList()
        var inserted = 0
        for (i in 1 until p.size - 1) {
            val e = p[i - 1]; val z = p[i]; val j = p[i + 1]
            if (!(e.startsWith("PD") && z.startsWith("PD") && j.startsWith("PD"))) continue
            val l = parse(e) ?: continue
            val u = parse(z) ?: continue
            val n = parse(j) ?: continue
            val angle = cornerAngle(l, u, n)
            if (angle == 0.0) continue
            val cross = (n.x - u.x).toDouble() * (l.y - u.y) - (n.y - u.y).toDouble() * (l.x - u.x)
            val right = cross < 0
            val b = along(l, u, OFFSET)            // overshoot past the corner on the incoming edge
            val g = along(n, u, -OFFSET)           // step toward the outgoing edge
            var h = abs(Math.PI - angle)
            if (right) h = -h
            val v = rotate(u, OFFSET, b, h / 3)
            val w = rotate(u, OFFSET, b, 2 * h / 3)
            val at = i + inserted
            result[at] = "PD${b.x},${b.y}"
            result.add(at + 1, "PD${v.x},${v.y}")
            result.add(at + 2, "PD${w.x},${w.y}")
            result.add(at + 3, "PD${g.x},${g.y}")
            inserted += 3
        }

        // Closing overshoot: push the final point OFFSET units further along its last edge.
        if (closed && p.size >= 2) {
            val y = parse(p.last()); val jj = parse(p[p.size - 2])
            if (y != null && jj != null) {
                val x = along(jj, y, OFFSET)
                result[result.lastIndex] = "PD${x.x},${x.y}"
            }
        }

        return head + result + tail
    }

    private fun dedupeAdjacent(p: MutableList<String>) {
        var i = 0
        while (i < p.size - 1) {
            if (p[i] == p[i + 1]) p.removeAt(i + 1) else i++
        }
    }

    /** Interior angle at [cur]; returns 0.0 when the corner is too shallow or the segments too short. */
    private fun cornerAngle(prev: P, cur: P, next: P): Double {
        val r = dist(cur, prev)
        val s = dist(next, cur)
        val o = dist(next, prev)
        if (r <= MIN_SEG || s <= MIN_SEG) return 0.0
        val cosv = ((r * r + s * s - o * o) / (2 * r * s)).coerceIn(-1.0, 1.0)
        val a = acos(cosv)
        return if (a < SHARP) a else 0.0
    }

    /** A point [n] units beyond [to] along the direction from [from] to [to] (negative steps back). */
    private fun along(from: P, to: P, n: Double): P {
        val a = atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble())
        return P((to.x + n * cos(a)).roundToInt(), (to.y + n * sin(a)).roundToInt())
    }

    /** [ref] rotated by [angle] around [center], placed at distance [radius]. */
    private fun rotate(center: P, radius: Double, ref: P, angle: Double): P {
        val a = atan2((ref.y - center.y).toDouble(), (ref.x - center.x).toDouble()) + angle
        return P((center.x + radius * cos(a)).roundToInt(), (center.y + radius * sin(a)).roundToInt())
    }

    private fun dist(a: P, b: P): Double = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    private fun parse(cmd: String): P? {
        if (!cmd.startsWith("PD") && !cmd.startsWith("PU")) return null
        val parts = cmd.substring(2).split(",")
        if (parts.size < 2) return null
        val x = parts[0].trim().toDoubleOrNull() ?: return null
        val y = parts[1].trim().toDoubleOrNull() ?: return null
        return P(x.roundToInt(), y.roundToInt())
    }
}
