package de.knutwurst.knutcut.ui

import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The live trace must follow the command stream the machine gets, not the path length. */
class TraceGeometryTest {

    /** A small, finely flattened circle: many commands over a short distance. */
    private fun circle(n: Int) = Polyline((0 until n).map { k ->
        val a = 2 * PI * k / n
        Pt(20 + 5 * cos(a), 20 + 5 * sin(a))
    }, closed = true)

    /** A big rectangle: few commands over a long distance. */
    private val rect = Polyline(listOf(Pt(0.0, 50.0), Pt(300.0, 50.0), Pt(300.0, 300.0), Pt(0.0, 300.0)), closed = true)

    @Test
    fun halfTheStreamSentMeansTheToolIsStillOnTheDenseShape() {
        // SP, FS, then the circle (1 PU + 199 PD + closing PD = 201 commands), the rectangle (5), final PU.
        val strokes = listOf(
            KnutcutViewModel.CutStroke(Tool.KNIFE, circle(200), cmdStart = 2, cmdCount = 201),
            KnutcutViewModel.CutStroke(Tool.KNIFE, rect, cmdStart = 203, cmdCount = 5),
        )
        val geo = TraceGeometry(strokes, streamSize = 209)

        val at = geo.locate(0.5f)
        assertEquals("the circle is being cut at 50 % of the stream", 0, at.stroke)
        // 104.5 of 209 commands sent → 102.5 into the circle's 201 → about halfway round it.
        assertEquals(0.5, at.pointPos / (geo.pts[0].size - 1), 0.02)
    }

    @Test
    fun theRectangleStartsOnlyOnceTheCircleIsFullySent() {
        val strokes = listOf(
            KnutcutViewModel.CutStroke(Tool.KNIFE, circle(200), cmdStart = 2, cmdCount = 201),
            KnutcutViewModel.CutStroke(Tool.KNIFE, rect, cmdStart = 203, cmdCount = 5),
        )
        val geo = TraceGeometry(strokes, streamSize = 209)
        assertEquals(1, geo.locate(205.5f / 209).stroke)
        assertEquals(0, geo.locate(1f / 209).stroke)
        assertEquals("nothing drawn before the first path", 0.0, geo.locate(1f / 209).pointPos, 0.0)
    }

    @Test
    fun withoutStreamInfoTheTraceFallsBackToPathLength() {
        // Silhouette cuts have no per-command mapping: progress maps onto the drawn length.
        val a = Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)), closed = false)
        val b = Polyline(listOf(Pt(0.0, 10.0), Pt(30.0, 10.0)), closed = false)
        val geo = TraceGeometry(listOf(KnutcutViewModel.CutStroke(Tool.KNIFE, a), KnutcutViewModel.CutStroke(Tool.KNIFE, b)), streamSize = 0)
        val at = geo.locate(0.5f)          // 20 of 40 mm → 10 mm into b
        assertEquals(1, at.stroke)
        assertEquals(1.0 / 3, at.pointPos, 1e-9)
    }
}
