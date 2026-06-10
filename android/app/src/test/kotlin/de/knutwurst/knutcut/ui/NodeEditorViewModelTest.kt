package de.knutwurst.knutcut.ui

import de.knutwurst.knutcut.data.Layer
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.EditablePath
import de.knutwurst.knutcut.svgcore.PathNode
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Unit tests for the Phase 4b node-editor ViewModel operations. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NodeEditorViewModelTest {

    private fun vm() = KnutcutViewModel(RuntimeEnvironment.getApplication())

    // Create a VM with a drawn layer (which carries an editPath) and return it.
    private fun vmWithDrawnLayer(): KnutcutViewModel {
        val vm = vm()
        // A stroke wide enough to survive RDP simplification.
        val stroke = listOf(Pt(0.0, 0.0), Pt(20.0, 0.0), Pt(40.0, 10.0), Pt(60.0, 0.0))
        vm.addDrawnPath(stroke)
        assertNotNull("precondition: drawn layer has editPath", vm.layers[0].editPath)
        return vm
    }

    // Create a VM with a plain (no editPath) layer that has a single polyline.
    private fun vmWithPlainLayer(): KnutcutViewModel {
        val vm = vm()
        val polylines = listOf(Polyline(listOf(Pt(0.0, 0.0), Pt(20.0, 0.0), Pt(40.0, 0.0)), false))
        vm.addLayer("Plain", polylines, Tool.PEN)
        return vm
    }

    // -------------------------------------------------------------------------
    // worldToLayerLocal / layerLocalToWorld round-trip
    // -------------------------------------------------------------------------

    @Test
    fun worldToLayerLocalRoundTripOnPlacedLayer() {
        val vm = vmWithDrawnLayer()
        // With the default placement produced by addDrawnPath the round-trip must be lossless.
        val world = Pt(5.0, 0.0)
        val local = vm.worldToLayerLocal(0, world) ?: error("worldToLayerLocal returned null")
        val backToWorld = vm.layerLocalToWorld(0, local) ?: error("layerLocalToWorld returned null")

        assertEquals("round-trip x", world.xMm, backToWorld.xMm, 1e-9)
        assertEquals("round-trip y", world.yMm, backToWorld.yMm, 1e-9)
    }

    @Test
    fun worldToLayerLocalReturnsNullForOutOfRangeIndex() {
        val vm = vm()
        assertNull(vm.worldToLayerLocal(99, Pt(0.0, 0.0)))
        assertNull(vm.layerLocalToWorld(99, Pt(0.0, 0.0)))
    }

    @Test
    fun layerLocalToWorldNonTrivialPlacement() {
        // After scaling, a local point should map to a different world point than itself.
        val vm = vmWithDrawnLayer()
        // Scale to 2x.
        vm.scaleX = 2.0
        val localOrigin = Pt(0.0, 0.0)
        val w = vm.layerLocalToWorld(0, localOrigin)
        // The world point for the local origin is not necessarily the same as localOrigin
        // because the placement matrix includes the layer's centre offset.  We just verify
        // that the round-trip still holds.
        val w2 = w ?: error("layerLocalToWorld returned null")
        val backLocal = vm.worldToLayerLocal(0, w2) ?: error("worldToLayerLocal returned null")
        assertEquals("round-trip x after scale", localOrigin.xMm, backLocal.xMm, 1e-9)
        assertEquals("round-trip y after scale", localOrigin.yMm, backLocal.yMm, 1e-9)
    }

    // -------------------------------------------------------------------------
    // moveSelectedAnchor
    // -------------------------------------------------------------------------

    @Test
    fun moveSelectedAnchorUpdatesPolylines() {
        val vm = vmWithDrawnLayer()
        val originalPath = vm.layers[0].editPath!!
        val anchorIdx = 0
        val newLocal = Pt(originalPath.nodes[anchorIdx].anchor.xMm + 5.0,
                         originalPath.nodes[anchorIdx].anchor.yMm + 3.0)

        vm.moveSelectedAnchor(anchorIdx, newLocal)

        val updatedPath = vm.layers[0].editPath!!
        assertEquals("anchor x moved", newLocal.xMm, updatedPath.nodes[anchorIdx].anchor.xMm, 1e-9)
        assertEquals("anchor y moved", newLocal.yMm, updatedPath.nodes[anchorIdx].anchor.yMm, 1e-9)

        // polylines must be consistent with editPath.toPolyline().
        val expected = updatedPath.toPolyline().points
        val actual = vm.layers[0].polylines[0].points
        assertEquals("polyline point count matches editPath", expected.size, actual.size)
        for (i in expected.indices) {
            assertEquals("poly x[$i]", expected[i].xMm, actual[i].xMm, 1e-9)
            assertEquals("poly y[$i]", expected[i].yMm, actual[i].yMm, 1e-9)
        }
    }

    @Test
    fun moveSelectedAnchorDoesNotPushHistoryItself() {
        val vm = vmWithDrawnLayer()
        val undoBefore = vm.canUndo
        vm.moveSelectedAnchor(0, Pt(1.0, 1.0))
        // The mutator must not add to history on its own.
        assertEquals("no implicit history push in moveSelectedAnchor", undoBefore, vm.canUndo)
    }

    @Test
    fun beginNodeEditPushesHistory() {
        val vm = vmWithDrawnLayer()
        // addDrawnPath already pushed one step; consume it so we can verify beginNodeEdit adds another.
        vm.undo()
        assertFalse("no undo before beginNodeEdit", vm.canUndo)
        vm.beginNodeEdit()
        assertTrue("undo available after beginNodeEdit", vm.canUndo)
    }

    // -------------------------------------------------------------------------
    // insertSelectedNode
    // -------------------------------------------------------------------------

    @Test
    fun insertSelectedNodeIncreasesNodeCountAndPushesHistory() {
        val vm = vmWithDrawnLayer()
        val before = vm.layers[0].editPath!!.nodes.size

        vm.insertSelectedNode(0, 0.5)

        val after = vm.layers[0].editPath!!.nodes.size
        assertEquals("node count increased by one", before + 1, after)
        assertTrue("undo available after insert", vm.canUndo)
    }

    @Test
    fun insertSelectedNodePreservesShape() {
        // Use a horizontal plain layer converted to an editable path so the geometry is simple.
        val vm = vmWithPlainLayer()
        vm.convertSelectedToEditablePath()
        // Now the layer has editPath with corner nodes at x=0,20,40.
        val path = vm.layers[0].editPath!!
        assertEquals("3 nodes before insert", 3, path.nodes.size)

        vm.insertSelectedNode(0, 0.5)

        val nodes = vm.layers[0].editPath!!.nodes
        assertEquals("4 nodes after insert", 4, nodes.size)
        // The inserted node at index 1 should be near the midpoint of segment 0→1.
        val mid = nodes[1].anchor
        assertEquals("inserted x near midpoint", 10.0, mid.xMm, 1.0)
    }

    // -------------------------------------------------------------------------
    // toggleSelectedNodeSmooth
    // -------------------------------------------------------------------------

    @Test
    fun toggleSelectedNodeSmoothFlipsFlagAndPushesHistory() {
        val vm = vmWithDrawnLayer()
        val initial = vm.layers[0].editPath!!.nodes[1].smooth

        vm.toggleSelectedNodeSmooth(1)
        assertEquals("smooth flag toggled", !initial, vm.layers[0].editPath!!.nodes[1].smooth)
        assertTrue("undo available after toggle", vm.canUndo)
    }

    @Test
    fun toggleSelectedNodeSmoothIsUndoable() {
        val vm = vmWithDrawnLayer()
        val initial = vm.layers[0].editPath!!.nodes[1].smooth

        vm.toggleSelectedNodeSmooth(1)
        vm.undo()
        assertEquals("smooth flag reverted after undo", initial, vm.layers[0].editPath!!.nodes[1].smooth)
    }

    // -------------------------------------------------------------------------
    // deleteSelectedNode
    // -------------------------------------------------------------------------

    @Test
    fun deleteSelectedNodeDecreasesCountAndPushesHistory() {
        val vm = vmWithDrawnLayer()
        val before = vm.layers[0].editPath!!.nodes.size

        vm.deleteSelectedNode(1)
        val after = vm.layers[0].editPath!!.nodes.size

        assertEquals("node count reduced by one", before - 1, after)
        assertTrue("undo available after delete", vm.canUndo)
    }

    @Test
    fun deleteSelectedNodeIsUndoable() {
        val vm = vmWithDrawnLayer()
        val before = vm.layers[0].editPath!!.nodes.size
        vm.deleteSelectedNode(1)
        vm.undo()
        assertEquals("node count restored after undo", before, vm.layers[0].editPath!!.nodes.size)
    }

    // -------------------------------------------------------------------------
    // convertSelectedToEditablePath
    // -------------------------------------------------------------------------

    @Test
    fun convertSelectedToEditablePathCreatesEditPath() {
        val vm = vmWithPlainLayer()
        assertNull("no editPath before convert", vm.layers[0].editPath)

        vm.convertSelectedToEditablePath()

        assertNotNull("editPath created after convert", vm.layers[0].editPath)
        assertTrue("undo available after convert", vm.canUndo)
    }

    @Test
    fun convertSelectedToEditablePathIsUndoable() {
        val vm = vmWithPlainLayer()
        vm.convertSelectedToEditablePath()
        assertNotNull(vm.layers[0].editPath)
        vm.undo()
        assertNull("editPath gone after undo", vm.layers[0].editPath)
    }

    @Test
    fun convertSelectedToEditablePathNoOpWhenAlreadyHasEditPath() {
        val vm = vmWithDrawnLayer()
        val nodesBefore = vm.layers[0].editPath!!.nodes.size
        val undoBefore = vm.canUndo

        vm.convertSelectedToEditablePath()

        // editPath unchanged (same node count), no undo step added.
        assertEquals("node count unchanged", nodesBefore, vm.layers[0].editPath!!.nodes.size)
        assertEquals("no undo step added", undoBefore, vm.canUndo)
    }

    @Test
    fun convertSelectedToEditablePathNoOpWhenMultiplePolylines() {
        val vm = vm()
        val polylines = listOf(
            Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0)), false),
            Polyline(listOf(Pt(0.0, 5.0), Pt(10.0, 5.0)), false),
        )
        vm.addLayer("Multi", polylines, Tool.PEN)
        vm.selectLayer(0)

        val undoBefore = vm.canUndo
        vm.convertSelectedToEditablePath()

        // editPath must remain null — the layer is not convertible (two polylines).
        assertNull("editPath stays null for multi-polyline layer", vm.layers[0].editPath)
        // convert must not have added a new undo step beyond what was already there.
        assertEquals("no extra undo step added by no-op convert", undoBefore, vm.canUndo)
    }

    // -------------------------------------------------------------------------
    // Each discrete action is one undo step
    // -------------------------------------------------------------------------

    @Test
    fun eachDiscreteActionIsOneUndoStep() {
        val vm = vmWithDrawnLayer()
        // Count baseline undo depth (addDrawnPath pushes one step).
        var baseDepth = 0
        while (vm.canUndo) { vm.undo(); baseDepth++ }
        // Redo to restore the drawn layer so the editPath is present again.
        while (vm.canRedo) vm.redo()

        val beforeActions = vm.canUndo  // true: the addDrawnPath step was just redone

        vm.insertSelectedNode(0, 0.5)     // +1 undo step
        vm.toggleSelectedNodeSmooth(1)    // +1 undo step
        vm.deleteSelectedNode(0)          // +1 undo step

        // Exactly three more steps than before the actions.
        vm.undo()  // undo delete
        vm.undo()  // undo toggle
        vm.undo()  // undo insert
        // Back to the baseline state — canUndo equals beforeActions.
        assertEquals("back to pre-action undo state after 3 undos", beforeActions, vm.canUndo)
    }

    // -------------------------------------------------------------------------
    // dragSelectedSegment
    // -------------------------------------------------------------------------

    @Test
    fun dragSelectedSegmentUpdatesPolylinesAndEditPath() {
        val vm = vmWithDrawnLayer()
        val pathBefore = vm.layers[0].editPath!!
        val polyBefore = vm.layers[0].polylines[0].points.toList()

        // Simulate a segment drag: beginNodeEdit (history) + dragSelectedSegment (no extra history).
        vm.beginNodeEdit()
        vm.dragSelectedSegment(0, 0.5, Pt(pathBefore.nodes[0].anchor.xMm + 5.0, pathBefore.nodes[0].anchor.yMm + 5.0))

        val pathAfter = vm.layers[0].editPath!!
        val polyAfter = vm.layers[0].polylines[0].points

        // The path must have changed.
        assertFalse("editPath changed after segment drag", pathBefore == pathAfter)
        // polylines must stay in sync with editPath.toPolyline().
        val expected = pathAfter.toPolyline().points
        assertEquals("polyline point count matches editPath", expected.size, polyAfter.size)

        // The entire drag is ONE undo step (beginNodeEdit pushed it, dragSelectedSegment does not).
        vm.undo()
        assertEquals("polyline restored after undo", polyBefore.size, vm.layers[0].polylines[0].points.size)
    }

    @Test
    fun dragSelectedSegmentIsOneUndoStep() {
        val vm = vmWithDrawnLayer()
        // Capture the layer state before the drag (the drawn layer's polyline).
        val polylinesBefore = vm.layers[0].polylines

        // One beginNodeEdit + several dragSelectedSegment calls must add exactly one undo step.
        vm.beginNodeEdit()
        val anchor0 = vm.layers[0].editPath!!.nodes[0].anchor
        repeat(5) { i ->
            vm.dragSelectedSegment(0, 0.5, Pt(anchor0.xMm + i.toDouble(), anchor0.yMm + i.toDouble()))
        }

        // After the drag the polylines differ from the pre-drag state.
        val polylinesAfterDrag = vm.layers[0].polylines
        assertFalse("polylines changed by drag", polylinesBefore === polylinesAfterDrag)

        // One undo must restore exactly the pre-drag state (proving only one step was pushed).
        assertTrue("undo available", vm.canUndo)
        vm.undo()
        val polylinesAfterUndo = vm.layers[0].polylines
        assertEquals("polyline point count restored after one undo",
            polylinesBefore[0].points.size, polylinesAfterUndo[0].points.size)
    }

    @Test
    fun dragSelectedSegmentOutOfRangeIsNoOp() {
        val vm = vmWithDrawnLayer()
        val polyBefore = vm.layers[0].polylines[0].points.toList()

        // Out-of-range segment index: no crash, no state change.
        vm.beginNodeEdit()
        vm.dragSelectedSegment(999, 0.5, Pt(0.0, 0.0))

        assertEquals("polyline unchanged on out-of-range drag",
            polyBefore.size, vm.layers[0].polylines[0].points.size)
    }

    // -------------------------------------------------------------------------
    // convertSelectedToEditablePath — budget node count on dense input
    // -------------------------------------------------------------------------

    @Test
    fun draggingAnAnchorWithAHeldFingerStaysBounded() {
        // Reproduction for the "node runs away to a huge coordinate" bug: during a node drag the
        // editor maps the finger (a fixed world point while held still) to layer-local coords each
        // frame, then moves the anchor there. If the layer's coordinate frame is re-derived from the
        // (now changing) geometry every frame, the mapping feeds back on itself and the anchor blows
        // up. Holding the finger still must converge to a stable point, not diverge.
        val n = 24
        val stroke = (0 until n).map { i ->
            val a = 2 * Math.PI * i / n
            Pt(150.0 + 30.0 * kotlin.math.cos(a), 150.0 + 30.0 * kotlin.math.sin(a))
        }
        val vm = vm()
        vm.addDrawnPath(stroke)            // closed circle with an editPath
        vm.selectLayer(0)
        assertNotNull("precondition: editPath present", vm.selectedEditPath)

        val targetWorld = Pt(190.0, 150.0) // finger held just outside the circle, on the mat
        repeat(60) {
            val local = vm.worldToLayerLocal(0, targetWorld) ?: return@repeat
            vm.moveSelectedAnchor(0, local)
        }

        val maxCoord = vm.layers[0].polylines.flatMap { it.points }
            .maxOf { maxOf(kotlin.math.abs(it.xMm), kotlin.math.abs(it.yMm)) }
        assertTrue("anchor coords must stay bounded, got $maxCoord", maxCoord < 1000.0)
    }

    @Test
    fun movingOneAnchorLeavesTheOtherAnchorsWhereTheyAre() {
        // Root cause of the runaway: the layer's matrix re-centres on the live polyline bounds, so
        // dragging one node shifts the whole coordinate frame and every OTHER node jumps in world
        // space. During a drag that feeds back on the finger mapping and the node shoots off. Editing
        // one anchor must not move the others.
        val n = 12
        val stroke = (0 until n).map { i ->
            val a = 2 * Math.PI * i / n
            Pt(150.0 + 30.0 * kotlin.math.cos(a), 150.0 + 30.0 * kotlin.math.sin(a))
        }
        val vm = vm()
        vm.addDrawnPath(stroke)
        vm.selectLayer(0)
        val path = vm.selectedEditPath!!

        // World positions of nodes 1 and 2 before touching node 0.
        val w1Before = vm.layerLocalToWorld(0, path.nodes[1].anchor)!!
        val w2Before = vm.layerLocalToWorld(0, path.nodes[2].anchor)!!

        // Drag node 0 well away from its spot.
        val n0 = path.nodes[0].anchor
        vm.moveSelectedAnchor(0, Pt(n0.xMm + 40.0, n0.yMm + 25.0))

        val w1After = vm.layerLocalToWorld(0, vm.selectedEditPath!!.nodes[1].anchor)!!
        val w2After = vm.layerLocalToWorld(0, vm.selectedEditPath!!.nodes[2].anchor)!!

        assertEquals("node 1 must not move when node 0 is edited (x)", w1Before.xMm, w1After.xMm, 1e-6)
        assertEquals("node 1 must not move when node 0 is edited (y)", w1Before.yMm, w1After.yMm, 1e-6)
        assertEquals("node 2 must not move when node 0 is edited (x)", w2Before.xMm, w2After.xMm, 1e-6)
        assertEquals("node 2 must not move when node 0 is edited (y)", w2Before.yMm, w2After.yMm, 1e-6)
    }

    @Test
    fun convertDensePolylineYieldsFewNodes() {
        val vm = vm()
        // Build a 300-point single polyline (far denser than the node budget).
        val points = (0 until 300).map { i -> Pt(i * 0.5, kotlin.math.sin(i * 0.1) * 10) }
        val polylines = listOf(de.knutwurst.knutcut.svgcore.Polyline(points, false))
        vm.addLayer("Dense", polylines, Tool.PEN)
        vm.selectLayer(0)

        vm.convertSelectedToEditablePath()

        val editPath = vm.layers[0].editPath
        assertNotNull("editPath created for dense layer", editPath)
        val nodeCount = editPath!!.nodes.size
        // simplifyToBudget targets a small node count (hard cap 40). A 300-point input must land
        // well within the hard cap and close to the low budget — a shape should be editable with a
        // handful of nodes, not dozens.
        assertTrue("node count ≤ 40 (hard cap)", nodeCount <= 40)
        assertTrue("node count ≤ 20 (near the small budget)", nodeCount <= 20)
    }
}
