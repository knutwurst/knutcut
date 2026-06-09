package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI

/** ProjectIO round-trip tests for layers with deformation data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeformProjectIOTest {

    private fun polylines() = listOf(
        Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 5.0)), closed = false),
        Polyline(listOf(Pt(1.0, 1.0), Pt(2.0, 2.0)), closed = false),
    )

    private fun warpedPolylines(radius: Double): List<Polyline> {
        val circum = 2 * PI * radius
        return listOf(
            Polyline(listOf(Pt(0.0, radius), Pt(circum / 4, 0.0)), closed = false)
        )
    }

    private fun spec() = CircleDeform(
        centerXMm = 100.0,
        centerYMm = 100.0,
        radiusMm = 30.0,
        startAngleDeg = 45.0,
        clockwise = true,
        baseline = DeformBaseline.CENTER,
    )

    @Test
    fun roundTripPreservesDeformFieldsAndPolylines() {
        val src = polylines()
        val warped = warpedPolylines(30.0)
        val spec = spec()
        val layer = Layer(
            name = "Warped",
            polylines = warped,
            tool = Tool.KNIFE,
            visible = true,
            deform = spec,
            deformSource = src,
        )

        val back = ProjectIO.fromJson(ProjectIO.toJson(listOf(layer)))

        assertEquals(1, back.size)
        val restored = back[0]
        assertEquals("polylines (warped result) preserved", warped, restored.polylines)
        assertEquals("deformSource preserved", src, restored.deformSource)

        val restoredSpec = restored.deform as? CircleDeform
        requireNotNull(restoredSpec) { "deform must be a CircleDeform" }
        assertEquals(spec.centerXMm, restoredSpec.centerXMm, 1e-9)
        assertEquals(spec.centerYMm, restoredSpec.centerYMm, 1e-9)
        assertEquals(spec.radiusMm, restoredSpec.radiusMm, 1e-9)
        assertEquals(spec.startAngleDeg, restoredSpec.startAngleDeg, 1e-9)
        assertEquals(spec.clockwise, restoredSpec.clockwise)
        assertEquals(spec.baseline, restoredSpec.baseline)
    }

    @Test
    fun backCompatNoDeformFields() {
        // A layer JSON that predates the deform feature must load with deform = null.
        val layer = Layer("Plain", polylines(), Tool.PEN, true)
        val json = ProjectIO.toJson(listOf(layer))

        // Verify the produced JSON really has no deform keys (belt-and-suspenders).
        assert(!json.contains("\"deform\"")) { "legacy layer must not contain a deform field" }

        val back = ProjectIO.fromJson(json)
        assertEquals(1, back.size)
        assertNull("deform must be null for a legacy layer", back[0].deform)
        assertNull("deformSource must be null for a legacy layer", back[0].deformSource)
    }

    @Test
    fun roundTripLayerWithoutDeformIsUnchanged() {
        val layer = Layer("NoWarp", polylines(), Tool.KNIFE, visible = false,
            centerMm = Pt(5.0, 10.0), scaleX = 2.0, scaleY = 0.5)
        val back = ProjectIO.fromJson(ProjectIO.toJson(listOf(layer)))[0]

        assertNull(back.deform)
        assertNull(back.deformSource)
        assertEquals(layer.polylines, back.polylines)
        assertEquals(layer.centerMm, back.centerMm)
        assertEquals(layer.scaleX, back.scaleX, 1e-9)
    }
}
