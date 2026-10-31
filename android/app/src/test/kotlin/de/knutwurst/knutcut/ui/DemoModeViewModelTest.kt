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
        assertTrue("a demo run doesn't count as material use", vm.recentMaterialIds.isEmpty())
        assertEquals(KnutcutViewModel.CutOutcome.SUCCESS, vm.cutOutcome)
        assertEquals("the live trace has the square", 1, vm.cutStrokes.size)
        assertEquals("progress stays full for the finish screen", 1f, vm.progress, 0f)
    }

    @Test
    fun theLiveTraceKnowsWhichCommandsDrawWhichPath() {
        val vm = vm()
        val circle = Polyline((0 until 120).map { k ->
            val a = 2 * Math.PI * k / 120
            Pt(30 + 10 * Math.cos(a), 30 + 10 * Math.sin(a))
        }, true)
        vm.addLayer("Circle", listOf(circle), Tool.KNIFE)
        vm.addLayer("Line", listOf(Polyline(listOf(Pt(10.0, 100.0), Pt(70.0, 100.0)), false)), Tool.PEN)
        vm.changeCutCopies(2)
        vm.connectDemo()
        vm.cut()
        val looper = shadowOf(Looper.getMainLooper())
        var rounds = 0
        while (vm.cutting && rounds++ < 1500) { Thread.sleep(20); looper.idleFor(Duration.ofMillis(500)) }
        assertEquals(KnutcutViewModel.CutOutcome.SUCCESS, vm.cutOutcome)

        val s = vm.cutStrokes
        assertEquals("two copies of each path, knife block first", listOf(Tool.KNIFE, Tool.KNIFE, Tool.PEN, Tool.PEN), s.map { it.tool })
        // SP, FS, circle, circle, SP, FS, line, line, final PU — back to back.
        assertEquals(2, s[0].cmdStart)
        assertEquals(s[0].cmdStart + s[0].cmdCount, s[1].cmdStart)
        assertEquals(s[1].cmdStart + s[1].cmdCount + 2, s[2].cmdStart)
        assertEquals(s[2].cmdStart + s[2].cmdCount, s[3].cmdStart)
        assertEquals(s[3].cmdStart + s[3].cmdCount + 1, vm.cutStreamSize)
        assertTrue("the circle takes far more commands than the long line", s[0].cmdCount > 100 && s[2].cmdCount == 2)
    }

    @Test
    fun cancelingADemoCutEndsAsCanceled() {
        val vm = vm()
        vm.addLayer("Square", listOf(Polyline(listOf(Pt(0.0, 0.0), Pt(20.0, 0.0), Pt(20.0, 20.0), Pt(0.0, 20.0)), true)), Tool.KNIFE)
        vm.connectDemo()
        vm.cut()
        vm.cancelCut()
        val looper = shadowOf(Looper.getMainLooper())
        var rounds = 0
        while (vm.cutting && rounds++ < 200) { Thread.sleep(20); looper.idleFor(Duration.ofMillis(500)) }
        assertFalse(vm.cutting)
        assertEquals(KnutcutViewModel.CutOutcome.CANCELED, vm.cutOutcome)

        vm.clearCutOutcome()
        assertEquals("Back returns to the review", null, vm.cutOutcome)
    }

    @Test
    fun aManualDisconnectSurvivesTheScreenBeingRecreated() {
        val vm = vm()
        vm.autoConnect()                       // nothing remembered yet
        vm.connectDemo()
        vm.disconnect()
        vm.autoConnect()                       // what rotating the phone triggers again
        assertFalse("the demo stays disconnected", vm.connected)
    }

    @Test
    fun launchReconnectsOnlyOnce() {
        vm().connectDemo()
        val restarted = vm()
        restarted.autoConnect()
        assertTrue(restarted.demoConnected)
        restarted.disconnect()
        restarted.autoConnect()
        assertFalse("a second autoConnect in the same run must not reconnect", restarted.connected)
    }
}
