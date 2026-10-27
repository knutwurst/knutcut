package de.knutwurst.knutcut.ui

import android.os.Looper
import de.knutwurst.knutcut.R
import de.knutwurst.knutcut.data.Devices
import de.knutwurst.knutcut.data.PlotterFamily
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

/** The demo plotter: a simulated device so the whole cut flow can be tried without a machine. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoModeViewModelTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private fun vm() = KnutcutViewModel(app)

    @Test
    fun pickingTheDemoPlotterConnectsItAndDisconnectDropsIt() {
        val vm = vm()
        vm.selectModel(Devices.models.first { it.family == PlotterFamily.BLE_SILHOUETTE })
        vm.connectDemo()
        assertTrue(vm.connected)
        assertTrue(vm.demoConnected)
        assertEquals("the demo speaks the VEVOR protocol", PlotterFamily.SPP_VEVOR, vm.model.family)

        vm.disconnect()
        assertFalse(vm.connected)
        assertFalse(vm.demoConnected)
    }

    @Test
    fun theDemoPlotterIsRememberedLikeAnyDevice() {
        vm().connectDemo()
        val restarted = vm()
        assertFalse(restarted.connected)
        restarted.autoConnect()
        assertTrue("launch reconnects the demo plotter", restarted.demoConnected)
    }

    @Test
    fun aWholeCutRunsAgainstTheDemoPlotter() {
        val vm = vm()
        vm.addLayer("Square", listOf(Polyline(listOf(Pt(0.0, 0.0), Pt(20.0, 0.0), Pt(20.0, 20.0), Pt(0.0, 20.0)), true)), Tool.KNIFE)
        vm.connectDemo()

        vm.cut()
        assertTrue(vm.cutting)

        // The link work runs on real IO threads; the feed/start polling and the 8 s drain are delays on
        // the main looper, which Robolectric's virtual clock can skip through.
        val looper = shadowOf(Looper.getMainLooper())
        var rounds = 0
        while (vm.cutting && rounds++ < 500) {
            Thread.sleep(20)
            looper.idleFor(Duration.ofMillis(500))
        }

        assertFalse("the cut finished", vm.cutting)
        val done = setOf(app.getString(R.string.st_plot_done_eject), app.getString(R.string.st_plot_done))
        assertTrue("finished with the done message, got: ${vm.status}", vm.status in done)
    }
}
