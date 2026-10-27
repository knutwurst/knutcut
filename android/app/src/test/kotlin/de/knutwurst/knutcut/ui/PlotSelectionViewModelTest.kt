package de.knutwurst.knutcut.ui

import de.knutwurst.knutcut.R
import de.knutwurst.knutcut.data.Devices
import de.knutwurst.knutcut.data.PlotterFamily
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.LinkTransport
import de.knutwurst.knutcut.svgcore.ManagedLink
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** The plot review's layer picking: which layers [KnutcutViewModel.cutLayers] hands to a cut. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlotSelectionViewModelTest {

    private class FakeLink(override val transport: LinkTransport) : ManagedLink {
        override var onClosed: (() -> Unit)? = null
        override fun write(text: String) {}
        override fun readLine(timeoutMs: Long): String? = null
        override fun close() {}
    }

    private val app get() = RuntimeEnvironment.getApplication()

    private fun square(x: Double) =
        Polyline(listOf(Pt(x, 0.0), Pt(x + 10.0, 0.0), Pt(x + 10.0, 10.0), Pt(x, 10.0)), true)

    /** Three visible layers: A, B, C. */
    private fun vmWithThreeLayers(): KnutcutViewModel {
        val vm = KnutcutViewModel(app)
        vm.addLayer("A", listOf(square(0.0)), Tool.KNIFE)
        vm.addLayer("B", listOf(square(20.0)), Tool.KNIFE)
        vm.addLayer("C", listOf(square(40.0)), Tool.PEN)
        return vm
    }

    private fun names(vm: KnutcutViewModel) = vm.cutLayers().map { it.name }

    @Test
    fun allVisibleLayersArePlottedByDefault() {
        val vm = vmWithThreeLayers()
        assertNull(vm.plotSelection)
        assertEquals(listOf("A", "B", "C"), names(vm))
    }

    @Test
    fun togglingLeavesALayerOutAndBringsItBack() {
        val vm = vmWithThreeLayers()
        vm.togglePlotLayer(1)
        assertEquals(listOf("A", "C"), names(vm))
        assertFalse(vm.isPlotIncluded(1))
        vm.togglePlotLayer(1)
        assertEquals(listOf("A", "B", "C"), names(vm))
    }

    @Test
    fun allAndNoneSelectEverythingOrNothing() {
        val vm = vmWithThreeLayers()
        vm.setPlotAll(false)
        assertTrue(vm.cutLayers().isEmpty())
        vm.togglePlotLayer(2)
        assertEquals(listOf("C"), names(vm))
        vm.setPlotAll(true)
        assertEquals(listOf("A", "B", "C"), names(vm))
    }

    @Test
    fun hiddenLayersAreNeverPlottedEvenWhenToggled() {
        val vm = vmWithThreeLayers()
        vm.toggleLayerVisible(0)   // hide A
        assertEquals(listOf("B", "C"), names(vm))
        vm.togglePlotLayer(0)      // no-op: a hidden layer can't be picked
        assertEquals(listOf("B", "C"), names(vm))
        assertFalse(vm.isPlotIncluded(0))
    }

    @Test
    fun cutRefusesAnEmptyPick() {
        val vm = vmWithThreeLayers()
        vm.selectModel(Devices.models.first { it.family == PlotterFamily.SPP_VEVOR })
        vm.attachLinkForTest(FakeLink(LinkTransport.SPP))
        vm.setPlotAll(false)

        vm.cut()

        assertEquals(app.getString(R.string.st_no_visible_layers), vm.status)
        assertFalse("nothing picked must not start a cut", vm.cutting)
    }

    @Test
    fun startingACutResetsThePickForNextTime() {
        val vm = vmWithThreeLayers()
        vm.selectModel(Devices.models.first { it.family == PlotterFamily.SPP_VEVOR })
        vm.attachLinkForTest(FakeLink(LinkTransport.SPP))
        vm.togglePlotLayer(0)

        vm.cut()

        // Every refusal path returns before the snapshot, so a cleared pick proves the cut got past it
        // (asserting `cutting` here would race the cut coroutine).
        assertNull("the pick is captured by the cut and cleared", vm.plotSelection)
    }
}
