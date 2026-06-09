package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.Bounds
import de.knutwurst.knutcut.svgcore.PathNode
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    // ---------------------------------------------------------------------------
    // PathDeform round-trip tests
    // ---------------------------------------------------------------------------

    private fun pathDeformSpec() = bendDeformDefault(
        bounds = Bounds(0.0, 0.0, 100.0, 20.0),
        curvatureMm = 25.0,
        baseline = DeformBaseline.CENTER,
    )

    @Test
    fun roundTripPreservesPathDeformNodes() {
        val spec = pathDeformSpec()
        val layer = Layer(
            name = "Arc",
            polylines = polylines(),
            tool = Tool.KNIFE,
            visible = true,
            deform = spec,
            deformSource = polylines(),
        )
        val back = ProjectIO.fromJson(ProjectIO.toJson(listOf(layer)))
        assertEquals(1, back.size)
        val restoredSpec = back[0].deform as? PathDeform
        assertNotNull("deform must be a PathDeform", restoredSpec)
        restoredSpec!!
        assertEquals("guide node count preserved", spec.guide.size, restoredSpec.guide.size)
        for (i in spec.guide.indices) {
            val orig = spec.guide[i]
            val rest = restoredSpec.guide[i]
            assertEquals("node[$i] anchor x", orig.anchor.xMm, rest.anchor.xMm, 1e-9)
            assertEquals("node[$i] anchor y", orig.anchor.yMm, rest.anchor.yMm, 1e-9)
            orig.handleIn?.let {
                assertEquals("node[$i] handleIn x", it.xMm, rest.handleIn!!.xMm, 1e-9)
                assertEquals("node[$i] handleIn y", it.yMm, rest.handleIn!!.yMm, 1e-9)
            }
            orig.handleOut?.let {
                assertEquals("node[$i] handleOut x", it.xMm, rest.handleOut!!.xMm, 1e-9)
                assertEquals("node[$i] handleOut y", it.yMm, rest.handleOut!!.yMm, 1e-9)
            }
        }
        assertEquals("baseline preserved", spec.baseline, restoredSpec.baseline)
        assertEquals("closed preserved",   spec.closed,   restoredSpec.closed)
    }

    @Test
    fun roundTripPathDeformWithNullHandles() {
        // A PathDeform whose nodes have no handles (straight-line path) must survive the round-trip.
        val spec = PathDeform(
            guide = listOf(
                PathNode(Pt(0.0, 10.0)),
                PathNode(Pt(100.0, 10.0)),
            ),
            closed = false,
            baseline = DeformBaseline.TOP,
        )
        val layer = Layer("Straight", polylines(), Tool.PEN, true, deform = spec)
        val back = ProjectIO.fromJson(ProjectIO.toJson(listOf(layer)))
        val restored = back[0].deform as? PathDeform
        assertNotNull(restored)
        assertEquals(2, restored!!.guide.size)
        assertNull("handleIn of node 0 must be null", restored.guide[0].handleIn)
        assertNull("handleOut of node 0 must be null", restored.guide[0].handleOut)
        assertEquals(DeformBaseline.TOP, restored.baseline)
    }

    @Test
    fun oldFileWithUnknownDeformTypeLoadsWithNullDeform() {
        // Files with an unrecognised type tag must not crash; deform must be null.
        val json = """[{"name":"X","tool":"KNIFE","visible":true,"cx":0,"cy":0,"sx":1,"sy":1,"rot":0,"fx":false,"fy":false,
            "polys":[{"c":false,"p":[[0,0],[10,0]]}],
            "deform":{"type":"future_unknown_type","someField":42}}]"""
        val layers = ProjectIO.fromJson(json)
        assertEquals(1, layers.size)
        assertNull("unknown deform type must deserialise to null", layers[0].deform)
    }
}
