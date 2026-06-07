package de.knutwurst.knutcut.ui

import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
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
        val centres = vm.layers.map { it.centerMm }.toSet()
        assertEquals("each tile sits at its own position", 6, centres.size)
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
    fun cutCopiesIsClamped() {
        val vm = vm()
        vm.changeCutCopies(0)
        assertEquals(1, vm.cutCopies)
        vm.changeCutCopies(99)
        assertEquals(10, vm.cutCopies)
        vm.changeCutCopies(3)
        assertEquals(3, vm.cutCopies)
    }
}
