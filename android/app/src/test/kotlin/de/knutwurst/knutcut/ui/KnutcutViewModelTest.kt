package de.knutwurst.knutcut.ui

import de.knutwurst.knutcut.data.Layer
import de.knutwurst.knutcut.data.PlotterSvgLibrary
import de.knutwurst.knutcut.data.ProjectIO
import de.knutwurst.knutcut.data.TextSpec
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.Polyline
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

/** Layer-editing logic in the ViewModel (reorder, rename, tile, copies). Robolectric for the
 *  SharedPreferences-backed Application the ViewModel needs. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KnutcutViewModelTest {

    private fun vm() = KnutcutViewModel(RuntimeEnvironment.getApplication())

    private fun square(at: Double) =
        listOf(Polyline(listOf(Pt(at, at), Pt(at + 10, at), Pt(at + 10, at + 10), Pt(at, at + 10)), closed = true))

    private fun addTwo(vm: KnutcutViewModel) {
        vm.addLayer("A", square(0.0), Tool.KNIFE)
        vm.addLayer("B", square(50.0), Tool.PEN)
    }

    @Test
    fun moveLayerReordersAndFollowsSelection() {
        val vm = vm()
        addTwo(vm)
        val first = vm.layers[0].name
        val second = vm.layers[1].name

        vm.selectLayer(0)
        vm.moveLayer(0, +1)

        assertEquals(listOf(second, first), vm.layers.map { it.name })
        assertEquals("selection follows the moved layer", 1, vm.selectedLayer)
    }

    @Test
    fun moveLayerClearsStaleMarks() {
        val vm = vm()
        addTwo(vm)
        vm.toggleMarked(0)
        vm.toggleMarked(1)
        assertEquals(2, vm.markedLayers.size)

        vm.moveLayer(0, +1)

        // Indices no longer map to the same layers, so marks must be dropped, not left dangling.
        assertTrue("marks cleared after reorder", vm.markedLayers.isEmpty())
    }

    @Test
    fun moveLayerIgnoresOutOfRange() {
        val vm = vm()
        addTwo(vm)
        vm.moveLayer(0, -1) // already at the top
        assertEquals(listOf("A", "B"), vm.layers.map { it.name })
    }

    @Test
    fun renameLayerTrimsAndIgnoresBlank() {
        val vm = vm()
        addTwo(vm)
        vm.renameLayer(0, "  Herz  ")
        assertEquals("Herz", vm.layers[0].name)

        vm.renameLayer(0, "   ")
        assertEquals("blank is ignored", "Herz", vm.layers[0].name)
    }

    @Test
    fun tileSelectedFillsAGrid() {
        val vm = vm()
        vm.addLayer("Tile", square(0.0), Tool.KNIFE)
        vm.selectLayer(0)

        vm.tileSelected(cols = 2, rows = 3)

        // One original + (2*3 - 1) copies.
        assertEquals(6, vm.layers.size)
        // The copies are offset, not stacked on the original.
        val centers = vm.layers.map { it.centerMm }.toSet()
        assertEquals("each tile sits at its own position", 6, centers.size)
    }

    @Test
    fun tileSelectedNoOpForOneByOne() {
        val vm = vm()
        vm.addLayer("Tile", square(0.0), Tool.KNIFE)
        vm.selectLayer(0)
        vm.tileSelected(1, 1)
        assertEquals(1, vm.layers.size)
    }

    @Test
    fun loadDesignImportsAKcpProjectByContent() {
        // A .kcp opened through the generic file path must load as a project, not error as "no SVG".
        val vm = vm()
        val json = ProjectIO.toJson(listOf(Layer("Profil", square(0.0), Tool.KNIFE, true)))
        vm.loadDesign(json)
        assertEquals(1, vm.layers.size)
        assertEquals("Profil", vm.layers[0].name)
    }

    @Test
    fun addLibrarySvgMergesMotifIntoOneLayer() {
        val vm = vm()
        val flower = PlotterSvgLibrary.items.first { it.id == "mdi-flower" }

        vm.addLibrarySvg(flower.name, flower.svg)

        assertEquals(1, vm.layers.size)
        assertEquals("Flower", vm.layers[0].name)
        assertTrue("multi-part motif kept together", vm.layers[0].polylines.size > 1)
    }

    @Test
    fun addLibrarySvgClosesAllContoursSoTheyAreCuttable() {
        val vm = vm()
        // "Ab Testing" has subpaths without a trailing Z (inner counters): they must still be
        // imported as closed contours, otherwise the plotter cuts open lines.
        val abTesting = PlotterSvgLibrary.items.first { it.id == "mdi-ab-testing" }

        vm.addLibrarySvg(abTesting.name, abTesting.svg)

        assertEquals(1, vm.layers.size)
        assertTrue("every contour must be closed for cutting", vm.layers[0].polylines.all { it.closed })
    }

    @Test
    fun cutCopiesIsClamped() {
        val vm = vm()
        vm.changeCutCopies(0)
        assertEquals(1, vm.cutCopies)
        vm.changeCutCopies(99)
        assertEquals(10, vm.cutCopies)
        vm.changeCutCopies(3)
        assertEquals(3, vm.cutCopies)
    }

    // -----------------------------------------------------------------------
    // applyTextCurve tests
    // -----------------------------------------------------------------------

    private fun textLayer(curve: Int = 0) = listOf(
        Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 5.0)), closed = true)
    )

    @Test
    fun applyTextCurveUpdatesPolylinesAndCurveValue() {
        val vm = vm()
        val spec = TextSpec("Hi", fontIndex = 0, heightMm = 20.0, curve = 0)
        vm.addLayer("Text: Hi", textLayer(), Tool.PEN, textSpec = spec)
        vm.selectLayer(0)

        val newPoly = listOf(Polyline(listOf(Pt(1.0, 2.0), Pt(3.0, 4.0)), closed = false))
        vm.applyTextCurve(0, 50, newPoly)

        assertEquals("polylines updated", newPoly, vm.layers[0].polylines)
        assertEquals("curve stored in textSpec", 50, vm.layers[0].textSpec?.curve)
    }

    @Test
    fun applyTextCurveIsOneUndoStep() {
        val vm = vm()
        val spec = TextSpec("Hi", fontIndex = 0, heightMm = 20.0, curve = 0)
        vm.addLayer("Text: Hi", textLayer(), Tool.PEN, textSpec = spec)
        vm.selectLayer(0)

        val newPoly = listOf(Polyline(listOf(Pt(1.0, 2.0), Pt(3.0, 4.0)), closed = false))
        vm.applyTextCurve(0, 50, newPoly)

        assertTrue("can undo after applyTextCurve", vm.canUndo)
        vm.undo()
        assertEquals("undo restores original curve", 0, vm.layers[0].textSpec?.curve)
    }

    @Test
    fun setSelectedTextCurveStoresCurveAndIsOneUndoStep() {
        val vm = vm()
        val spec = TextSpec("Hi", fontIndex = 0, heightMm = 20.0, curve = 0)
        vm.addLayer("Text: Hi", textLayer(), Tool.PEN, textSpec = spec)
        vm.selectLayer(0)

        // One drag: snapshot once, then several live ticks.
        vm.beginTextCurve()
        vm.setSelectedTextCurve(60)
        vm.setSelectedTextCurve(80)

        assertEquals("latest curve stored", 80, vm.layers[0].textSpec?.curve)
        assertTrue("can undo the bend", vm.canUndo)
        vm.undo()
        assertEquals("one undo step restores the pre-bend curve", 0, vm.layers[0].textSpec?.curve)
    }

    @Test
    fun exitActiveModeStepsOutOfModesThenReportsFalse() {
        val vm = vm()
        vm.addLayer("Text: Hi", textLayer(), Tool.PEN, textSpec = TextSpec("Hi", 0, 20.0, 0))
        vm.selectLayer(0)

        vm.startBendingText()
        assertTrue("exits bend mode", vm.exitActiveMode())
        assertTrue("bend cleared", !vm.bendingText)

        vm.editorTool = EditorTool.NODES
        assertTrue("exits node mode", vm.exitActiveMode())
        assertEquals("back to select", EditorTool.SELECT, vm.editorTool)

        assertTrue("nothing left to exit", !vm.exitActiveMode())
    }

    @Test
    fun startBendingTextOnlyForTextLayers() {
        val vm = vm()
        vm.addLayer("Form", square(0.0), Tool.KNIFE)
        vm.selectLayer(0)
        vm.startBendingText()
        assertTrue("a non-text layer cannot be bent", !vm.bendingText)

        vm.addLayer("Text: Hi", textLayer(), Tool.PEN, textSpec = TextSpec("Hi", 0, 20.0, 0))
        vm.selectLayer(1)
        vm.startBendingText()
        assertTrue("a text layer enters bend mode", vm.bendingText)
        vm.stopBendingText()
        assertTrue("stop leaves bend mode", !vm.bendingText)
    }

    @Test
    fun applyTextCurveNoOpOnNonTextLayer() {
        val vm = vm()
        vm.addLayer("Form", square(0.0), Tool.KNIFE)
        vm.selectLayer(0)
        val before = vm.layers.toList()

        vm.applyTextCurve(0, 50, square(0.0))

        // No-op: layer without textSpec must not be changed, and no history pushed.
        assertEquals("layer unchanged", before, vm.layers.toList())
    }

    // -----------------------------------------------------------------------
    // Editor-mode hygiene: a node-edit / bend mode must not linger on a layer
    // after an operation that changes the selection to a different layer.
    // -----------------------------------------------------------------------

    private fun assertCleanMode(vm: KnutcutViewModel, msg: String) {
        assertEquals("$msg: back to SELECT", EditorTool.SELECT, vm.editorTool)
        assertTrue("$msg: bend cleared", !vm.bendingText)
    }

    @Test
    fun addLayerClearsActiveEditorMode() {
        val vm = vm()
        vm.addLayer("A", square(0.0), Tool.KNIFE)
        vm.selectLayer(0)
        vm.editorTool = EditorTool.NODES
        vm.addLayer("B", square(50.0), Tool.KNIFE)
        assertCleanMode(vm, "addLayer")
    }

    @Test
    fun autoArrangeClearsBendMode() {
        val vm = vm()
        vm.addLayer("Text: Hi", textLayer(), Tool.PEN, textSpec = TextSpec("Hi", 0, 20.0, 0))
        vm.selectLayer(0)
        vm.startBendingText()
        assertTrue("precondition: bending", vm.bendingText)
        vm.autoArrange(false)
        assertCleanMode(vm, "autoArrange")
    }

    @Test
    fun splitLayersClearsActiveEditorMode() {
        val vm = vm()
        vm.addLayer("A", square(0.0), Tool.KNIFE)
        vm.selectLayer(0)
        vm.editorTool = EditorTool.NODES
        vm.splitLayers()
        assertCleanMode(vm, "splitLayers")
    }

    @Test
    fun mergeLayersClearsActiveEditorMode() {
        val vm = vm()
        addTwo(vm)
        vm.selectLayer(0)
        vm.editorTool = EditorTool.NODES
        vm.mergeLayers()
        assertCleanMode(vm, "mergeLayers")
    }

    @Test
    fun libraryAppendClearsActiveEditorMode() {
        val vm = vm()
        val flower = PlotterSvgLibrary.items.first { it.id == "mdi-flower" }
        vm.addLibrarySvg(flower.name, flower.svg)   // first add (replace branch)
        vm.selectLayer(0)
        vm.editorTool = EditorTool.NODES
        vm.addLibrarySvg(flower.name, flower.svg)   // append branch
        assertCleanMode(vm, "addLibrarySvg append")
    }

    @Test
    fun deselectClearsActiveEditorMode() {
        val vm = vm()
        vm.addLayer("A", square(0.0), Tool.KNIFE)
        vm.selectLayer(0)
        vm.editorTool = EditorTool.NODES
        vm.deselectLayers()
        assertCleanMode(vm, "deselectLayers")
    }

    // -----------------------------------------------------------------------
    // toggleSelectedNodeSmooth bounds guard
    // -----------------------------------------------------------------------

    @Test
    fun toggleNodeSmoothIgnoresOutOfRangeIndex() {
        val vm = vm()
        vm.addLayer("A", square(0.0), Tool.KNIFE)
        vm.selectLayer(0)
        vm.convertSelectedToEditablePath()
        assertNotNull("precondition: has an editable path", vm.layers[0].editPath)

        // A stale or future index must be a no-op, not an index crash.
        vm.toggleSelectedNodeSmooth(999)
        vm.toggleSelectedNodeSmooth(-1)

        assertNotNull("path still intact", vm.layers[0].editPath)
    }

    // -----------------------------------------------------------------------
    // tileSelected uses the rotated footprint
    // -----------------------------------------------------------------------

    // -----------------------------------------------------------------------
    // setSelectedColor
    // -----------------------------------------------------------------------

    @Test
    fun setSelectedColorSetsColourAndIsOneUndoStep() {
        val vm = vm()
        vm.addLayer("A", square(0.0), Tool.KNIFE)
        vm.selectLayer(0)

        vm.setSelectedColor(0xFFE53935.toInt())

        assertEquals(0xFFE53935.toInt(), vm.layers[0].colorArgb)
        assertNull("per-polyline colors cleared so the layer takes one color", vm.layers[0].polylineColors)
        assertTrue("one undo step", vm.canUndo)
        vm.undo()
        assertNull("undo restores the original (no) color", vm.layers[0].colorArgb)
    }

    @Test
    fun setSelectedColorNoneClearsColour() {
        val vm = vm()
        vm.addLayer("A", square(0.0), Tool.KNIFE)
        vm.selectLayer(0)
        vm.setSelectedColor(0xFF00FF00.toInt())
        vm.setSelectedColor(null)
        assertNull(vm.layers[0].colorArgb)
    }

    @Test
    fun setSelectedColorNoOpWithoutSelection() {
        val vm = vm()
        vm.addLayer("A", square(0.0), Tool.KNIFE)
        vm.deselectLayers() // mat selected, no layer
        val before = vm.layers.toList()
        vm.setSelectedColor(0xFF0000FF.toInt())
        assertEquals("no layer selected: nothing changes", before, vm.layers.toList())
    }

    @Test
    fun tileSelectedSpacesByTheRotatedFootprint() {
        val vm = vm()
        // A tall, narrow bar: 10 mm wide × 40 mm tall.
        val bar = listOf(Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 40.0), Pt(0.0, 40.0)), closed = true))
        vm.addLayer("Bar", bar, Tool.KNIFE)
        vm.selectLayer(0)
        vm.rotationDeg = 90.0   // now effectively 40 mm wide × 10 mm tall
        val startX = vm.layers[0].centerMm.xMm

        vm.tileSelected(cols = 2, rows = 1)

        // The copy must step by the ROTATED width (~40 mm + gap), not the unrotated 10 mm.
        val dx = vm.layers[1].centerMm.xMm - startX
        assertTrue("stepped by the rotated footprint (~45 mm), got $dx", dx > 40.0)
    }
}
