package de.knutwurst.knutcut.ui

import android.os.Looper
import de.knutwurst.knutcut.R
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

/** Demo mode: a simulated plotter so the whole cut flow can be tried without a machine. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoModeViewModelTest {

    private val app get() = RuntimeEnvironment.getApplication()
    private fun vm() = KnutcutViewModel(app)

    @Test
    fun turningDemoOnConnectsTheSimulatedPlotterAndOffDropsIt() {
        val vm = vm()
        vm.changeDemoMode(true)
        assertTrue(vm.demoMode)
        assertTrue(vm.connected)
        assertTrue(vm.demoConnected)
        assertEquals("the demo speaks the VEVOR protocol", PlotterFamily.SPP_VEVOR, vm.model.family)

        vm.changeDemoMode(false)
        assertFalse(vm.demoMode)
        assertFalse(vm.connected)
    }

    @Test
    fun demoModeSurvivesARestart() {
        vm().changeDemoMode(true)
        val restarted = vm()
        assertTrue(restarted.demoMode)
        restarted.autoConnect()
        assertTrue("launch reconnects the simulated plotter", restarted.demoConnected)
    }

    @Test
    fun aWholeCutRunsAgainstTheDemoPlotter() {
        val vm = vm()
        vm.addLayer("Square", listOf(Polyline(listOf(Pt(0.0, 0.0), Pt(20.0, 0.0), Pt(20.0, 20.0), Pt(0.0, 20.0)), true)), Tool.KNIFE)
        vm.changeDemoMode(true)

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
