package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Bounds
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for envelopeDeformDefault and the identity-warp contract. */
class EnvelopeDeformDefaultTest {

    private val EPS = 1e-9

    @Test
    fun defaultCornersMatchSourceBounds() {
        val b = Bounds(10.0, 5.0, 80.0, 60.0)
        val spec = envelopeDeformDefault(b)
        assertEquals(b.minX, spec.tl.xMm, EPS)
        assertEquals(b.minY, spec.tl.yMm, EPS)
        assertEquals(b.maxX, spec.tr.xMm, EPS)
        assertEquals(b.minY, spec.tr.yMm, EPS)
        assertEquals(b.maxX, spec.br.xMm, EPS)
        assertEquals(b.maxY, spec.br.yMm, EPS)
        assertEquals(b.minX, spec.bl.xMm, EPS)
        assertEquals(b.maxY, spec.bl.yMm, EPS)
    }

    @Test
    fun applyingDefaultViaEngineReproducesSource() {
        val pts = listOf(
            Pt(0.0, 0.0), Pt(50.0, 0.0), Pt(50.0, 30.0), Pt(25.0, 15.0),
        )
        val source = listOf(Polyline(pts, closed = false))
        // The identity spec must be built from the actual source bounds (matching what DeformEngine uses).
        val b = Bounds.of(pts)
        val spec = envelopeDeformDefault(b)

        val result = DeformEngine.apply(spec, source)

        assertEquals(source.size, result.size)
        val resPoints = result.first().points
        for (i in pts.indices) {
            assertEquals("x[$i]", pts[i].xMm, resPoints[i].xMm, EPS)
            assertEquals("y[$i]", pts[i].yMm, resPoints[i].yMm, EPS)
        }
    }
}
