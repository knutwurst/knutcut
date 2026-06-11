package de.knutwurst.knutcut.ui

import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Editor-mode hygiene and node-edit bounds guards: an active node-edit/bend tool must not outlive the
 * layer it edits, and a stale node index must be a no-op rather than an index crash.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EditorHygieneTest {

    private fun vm() = KnutcutViewModel(RuntimeEnvironment.getApplication())

    private fun triangle() =
        listOf(Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 10.0)), closed = true))

    // A VM with a freehand-drawn layer (editPath present), for the node-delete guards.
    private fun vmWithDrawnLayer(): KnutcutViewModel {
        val vm = vm()
        vm.addDrawnPath(listOf(Pt(0.0, 0.0), Pt(20.0, 0.0), Pt(40.0, 10.0), Pt(60.0, 0.0)))
        assertNotNull("precondition: drawn layer has editPath", vm.layers[0].editPath)
        return vm
    }

    @Test
    fun deleteSelectedResetsEditorToolToSelect() {
        val vm = vm()
        vm.addLayer("A", triangle(), Tool.KNIFE)
        vm.selectLayer(0)
        vm.editorTool = EditorTool.NODES
        vm.deleteSelected()
        assertEquals(EditorTool.SELECT, vm.editorTool)
    }

    @Test
    fun clearAllResetsEditorToolToSelect() {
        val vm = vm()
        vm.addLayer("A", triangle(), Tool.KNIFE)
        vm.editorTool = EditorTool.DRAW
        vm.clearAll()
        assertEquals(EditorTool.SELECT, vm.editorTool)
    }

    @Test
    fun deleteSelectedNodeWithOutOfRangeIndexIsNoOp() {
        val vm = vmWithDrawnLayer()
        val nodesBefore = vm.layers[0].editPath!!.nodes.size
        val polysBefore = vm.layers[0].polylines

        vm.deleteSelectedNode(9999) // well out of range — must not crash or change the layer

        assertEquals("node count unchanged", nodesBefore, vm.layers[0].editPath!!.nodes.size)
        assertEquals("polylines unchanged", polysBefore, vm.layers[0].polylines)
    }

    @Test
    fun deleteSelectedNodeWithNegativeIndexIsNoOp() {
        val vm = vmWithDrawnLayer()
        val nodesBefore = vm.layers[0].editPath!!.nodes.size

        vm.deleteSelectedNode(-1)

        assertEquals("node count unchanged", nodesBefore, vm.layers[0].editPath!!.nodes.size)
    }
}
