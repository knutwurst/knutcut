package de.knutwurst.knutcut.ui

import de.knutwurst.knutcut.data.Layer
import de.knutwurst.knutcut.data.ProjectIO
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.Bounds
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrawToolTest {

    private fun vm() = KnutcutViewModel(RuntimeEnvironment.getApplication())

    // -----------------------------------------------------------------------
    // addDrawnPath
    // -----------------------------------------------------------------------

    @Test
    fun addDrawnPathIgnoredWhenFewerThanTwoDistinctPoints() {
        val vm = vm()
        vm.addDrawnPath(emptyList())
        assertEquals(0, vm.layers.size)

        vm.addDrawnPath(listOf(Pt(5.0, 5.0)))
        assertEquals(0, vm.layers.size)

        // All duplicates = effectively 1 distinct point.
        vm.addDrawnPath(listOf(Pt(5.0, 5.0), Pt(5.0, 5.0), Pt(5.0, 5.0)))
        assertEquals(0, vm.layers.size)
    }

    @Test
    fun addDrawnPathCreatesPenLayer() {
        val vm = vm()
        val stroke = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(20.0, 5.0), Pt(30.0, 0.0))
        vm.addDrawnPath(stroke)

        assertEquals(1, vm.layers.size)
        val layer = vm.layers[0]
        assertEquals(Tool.PEN, layer.tool)
        assertTrue(layer.visible)
    }

    @Test
    fun addDrawnPathLayerHasEditPath() {
        val vm = vm()
        val stroke = (0..5).map { Pt(it * 10.0, it.toDouble()) }
        vm.addDrawnPath(stroke)

        val layer = vm.layers[0]
        assertNotNull("editPath must not be null for a drawn layer", layer.editPath)
    }

    @Test
    fun addDrawnPathPolylineMatchesEditPathToPolyline() {
        val vm = vm()
        val stroke = listOf(Pt(0.0, 0.0), Pt(15.0, 10.0), Pt(30.0, 0.0))
        vm.addDrawnPath(stroke)

        val layer = vm.layers[0]
        val editPath = layer.editPath!!
        val expected = editPath.toPolyline()
        // polylines in the layer must equal the flattened edit path.
        assertEquals(1, layer.polylines.size)
        val actual = layer.polylines[0]
        assertEquals("point counts must match", expected.points.size, actual.points.size)
        for (i in expected.points.indices) {
            assertEquals("x[$i]", expected.points[i].xMm, actual.points[i].xMm, 1e-9)
            assertEquals("y[$i]", expected.points[i].yMm, actual.points[i].yMm, 1e-9)
        }
    }

    @Test
    fun addDrawnPathCenterMmEqualsPolylineBoundsCenter() {
        val vm = vm()
        val stroke = listOf(Pt(10.0, 20.0), Pt(30.0, 40.0), Pt(50.0, 20.0))
        vm.addDrawnPath(stroke)

        val layer = vm.layers[0]
        val poly = layer.polylines[0]
        val pts = poly.points
        val minX = pts.minOf { it.xMm }; val maxX = pts.maxOf { it.xMm }
        val minY = pts.minOf { it.yMm }; val maxY = pts.maxOf { it.yMm }
        val expectedCx = (minX + maxX) / 2
        val expectedCy = (minY + maxY) / 2

        assertEquals("centerMm.x == bounds center x", expectedCx, layer.centerMm.xMm, 1e-9)
        assertEquals("centerMm.y == bounds center y", expectedCy, layer.centerMm.yMm, 1e-9)
    }

    @Test
    fun addDrawnPathPolylineStaysCloseToOriginalStroke() {
        val vm = vm()
        // A stroke with enough spacing that RDP does not collapse it.
        val stroke = listOf(Pt(0.0, 0.0), Pt(20.0, 15.0), Pt(40.0, 0.0), Pt(60.0, 15.0))
        vm.addDrawnPath(stroke)

        val poly = vm.layers[0].polylines[0]
        val maxAllowedGap = 5.0  // mm
        for (input in stroke) {
            val closest = poly.points.minByOrNull { hypot(it.xMm - input.xMm, it.yMm - input.yMm) }!!
            assertTrue(
                "Stroke point $input not represented in polyline",
                hypot(closest.xMm - input.xMm, closest.yMm - input.yMm) < maxAllowedGap
            )
        }
    }

    @Test
    fun addDrawnPathAutoClosesARoughlyClosedLoop() {
        val vm = vm()
        // A loop sketched back to near the start (last point close to the first): should become a
        // closed, plottable shape without the user having to land exactly on the start point.
        val loop = listOf(
            Pt(0.0, 0.0), Pt(40.0, 0.0), Pt(40.0, 40.0), Pt(0.0, 40.0), Pt(2.0, 3.0),
        )
        vm.addDrawnPath(loop)

        val layer = vm.layers[0]
        assertTrue("a near-closed sketch must yield a closed polyline", layer.polylines[0].closed)
        assertTrue("the editable path must be closed too", layer.editPath!!.closed)
    }

    @Test
    fun addDrawnPathLeavesAnOpenLineOpen() {
        val vm = vm()
        // An open arc: the ends sit far apart, so it must stay an open line, not snap shut.
        val line = listOf(Pt(0.0, 0.0), Pt(20.0, 0.0), Pt(40.0, 10.0), Pt(60.0, 0.0))
        vm.addDrawnPath(line)

        val layer = vm.layers[0]
        assertEquals("an open stroke must stay open", false, layer.polylines[0].closed)
        assertEquals("the editable path must stay open", false, layer.editPath!!.closed)
    }

    @Test
    fun autoCloseOffKeepsARoughlyClosedLoopOpen() {
        val vm = vm()
        vm.changeAutoCloseDrawn(false)
        val loop = listOf(
            Pt(0.0, 0.0), Pt(40.0, 0.0), Pt(40.0, 40.0), Pt(0.0, 40.0), Pt(2.0, 3.0),
        )
        vm.addDrawnPath(loop)
        assertEquals("with auto-close off, a loop stays open", false, vm.layers[0].editPath!!.closed)
    }

    @Test
    fun toggleSelectedPathClosedFlipsOpenAndClosed() {
        val vm = vm()
        // An open line (auto-close leaves it open).
        vm.addDrawnPath(listOf(Pt(0.0, 0.0), Pt(20.0, 0.0), Pt(40.0, 10.0), Pt(60.0, 0.0)))
        vm.selectLayer(0)
        assertEquals("starts open", false, vm.selectedPathClosed)

        vm.toggleSelectedPathClosed()
        assertTrue("closed after toggle", vm.selectedPathClosed)
        assertTrue("polyline reflects closed", vm.layers[0].polylines[0].closed)

        vm.toggleSelectedPathClosed()
        assertEquals("open again after second toggle", false, vm.selectedPathClosed)
    }

    @Test
    fun addDrawnPathHonoursExplicitClosedFlag() {
        val vm = vm()
        // Forcing closed = true must win even over an open-looking stroke.
        val line = listOf(Pt(0.0, 0.0), Pt(20.0, 0.0), Pt(40.0, 10.0), Pt(60.0, 0.0))
        vm.addDrawnPath(line, closed = true)
        assertTrue("explicit closed=true must close the shape", vm.layers[0].polylines[0].closed)
    }

    @Test
    fun addDrawnPathReducesDenseStrokeToFewNodes() {
        val vm = vm()
        // A dense freehand circle: many samples in, a handful of editable nodes out.
        val n = 240
        val stroke = (0 until n).map { i ->
            val a = 2 * Math.PI * i / n
            Pt(40.0 + 30.0 * kotlin.math.cos(a), 40.0 + 30.0 * kotlin.math.sin(a))
        }
        vm.addDrawnPath(stroke)

        val editPath = vm.layers[0].editPath!!
        assertTrue("drawn shape must close into a loop", editPath.closed)
        assertTrue("dense stroke must reduce to a small node count, got ${editPath.nodes.size}", editPath.nodes.size <= 20)
    }

    @Test
    fun addDrawnPathKeepsNonConsecutiveRevisitedPoints() {
        val vm = vm()
        // Out to (10,0) and back to the start. A global de-dup would collapse the revisited start and
        // leave a 2-point line; consecutive-only de-dup keeps the out-and-back (3 nodes).
        vm.addDrawnPath(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(0.0, 0.0)))
        val editPath = vm.layers[0].editPath!!
        assertTrue("revisited point kept, not collapsed to a line", editPath.nodes.size >= 3)
    }

    @Test
    fun addDrawnPathDropsConsecutiveDuplicates() {
        val vm = vm()
        // Repeated identical samples (zero-length segments) collapse to a single 2-node line.
        vm.addDrawnPath(listOf(Pt(0.0, 0.0), Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 0.0)))
        val editPath = vm.layers[0].editPath!!
        assertEquals("consecutive duplicates removed", 2, editPath.nodes.size)
    }

    @Test
    fun addDrawnPathSelectsNewLayer() {
        val vm = vm()
        vm.addDrawnPath(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)))
        assertEquals(0, vm.selectedLayer)
    }

    @Test
    fun addDrawnPathIsUndoable() {
        val vm = vm()
        vm.addDrawnPath(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)))
        assertEquals(1, vm.layers.size)
        vm.undo()
        assertEquals(0, vm.layers.size)
    }

    // -----------------------------------------------------------------------
    // editPath ProjectIO round-trip
    // -----------------------------------------------------------------------

    @Test
    fun editPathRoundTripPreservesNodesAndClosed() {
        val vm = vm()
        val stroke = listOf(Pt(0.0, 0.0), Pt(20.0, 10.0), Pt(40.0, 0.0), Pt(60.0, 10.0))
        vm.addDrawnPath(stroke, closed = false)

        val layer = vm.layers[0]
        val origPath = layer.editPath!!

        val json = ProjectIO.toJson(vm.layers)
        val restored = ProjectIO.fromJson(json)

        assertEquals(1, restored.size)
        val restoredPath = restored[0].editPath
        assertNotNull("editPath must survive the round-trip", restoredPath)
        restoredPath!!

        assertEquals("node count preserved", origPath.nodes.size, restoredPath.nodes.size)
        assertEquals("closed preserved", origPath.closed, restoredPath.closed)
        for (i in origPath.nodes.indices) {
            val o = origPath.nodes[i]
            val r = restoredPath.nodes[i]
            assertEquals("node[$i] anchor.x", o.anchor.xMm, r.anchor.xMm, 1e-9)
            assertEquals("node[$i] anchor.y", o.anchor.yMm, r.anchor.yMm, 1e-9)
            if (o.handleIn != null) {
                assertNotNull("node[$i] handleIn preserved", r.handleIn)
                assertEquals("node[$i] handleIn.x", o.handleIn!!.xMm, r.handleIn!!.xMm, 1e-9)
                assertEquals("node[$i] handleIn.y", o.handleIn!!.yMm, r.handleIn!!.yMm, 1e-9)
            } else {
                assertNull("node[$i] handleIn must stay null", r.handleIn)
            }
            if (o.handleOut != null) {
                assertNotNull("node[$i] handleOut preserved", r.handleOut)
                assertEquals("node[$i] handleOut.x", o.handleOut!!.xMm, r.handleOut!!.xMm, 1e-9)
                assertEquals("node[$i] handleOut.y", o.handleOut!!.yMm, r.handleOut!!.yMm, 1e-9)
            } else {
                assertNull("node[$i] handleOut must stay null", r.handleOut)
            }
        }
    }

    @Test
    fun editOriginRoundTripsAndIsAbsentForPlainLayers() {
        val vm = vm()
        vm.addDrawnPath(listOf(Pt(0.0, 0.0), Pt(20.0, 10.0), Pt(40.0, 0.0), Pt(60.0, 10.0)))
        val origin = vm.layers[0].editOriginMm
        assertNotNull("a drawn layer carries a frozen edit origin", origin)

        val restored = ProjectIO.fromJson(ProjectIO.toJson(vm.layers))
        val r = restored[0].editOriginMm
        assertNotNull("edit origin must survive the round-trip", r)
        assertEquals("edit origin x", origin!!.xMm, r!!.xMm, 1e-9)
        assertEquals("edit origin y", origin.yMm, r.yMm, 1e-9)

        // A plain layer (no editPath) must not gain an edit origin.
        val plain = Layer("Plain", listOf(de.knutwurst.knutcut.svgcore.Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)), false)), Tool.KNIFE, true)
        assertNull("plain layer keeps editOrigin null", ProjectIO.fromJson(ProjectIO.toJson(listOf(plain)))[0].editOriginMm)
    }

    @Test
    fun editPathAbsentInOldJsonLoadsAsNull() {
        // A legacy project JSON without the editPath key must load without error.
        val json = """[{"name":"Old","tool":"PEN","visible":true,"cx":5,"cy":5,
            "sx":1,"sy":1,"rot":0,"fx":false,"fy":false,
            "polys":[{"c":false,"p":[[0,0],[10,0]]}]}]"""
        val layers = ProjectIO.fromJson(json)
        assertEquals(1, layers.size)
        assertNull("editPath must be null when absent from JSON", layers[0].editPath)
    }

    @Test
    fun editPathRoundTripLayerWithoutEditPathIsUnchanged() {
        // A plain layer must not gain an editPath field and must survive unchanged.
        val layer = Layer("Plain", listOf(de.knutwurst.knutcut.svgcore.Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)), false)), Tool.KNIFE, true)
        val restored = ProjectIO.fromJson(ProjectIO.toJson(listOf(layer)))
        assertEquals(1, restored.size)
        assertNull("editPath must remain null for a non-drawn layer", restored[0].editPath)
    }
}
