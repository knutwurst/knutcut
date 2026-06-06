package de.knutwurst.knutcut.ui

import de.knutwurst.knutcut.data.Devices
import de.knutwurst.knutcut.data.PlotterFamily
import de.knutwurst.knutcut.svgcore.LinkTransport
import de.knutwurst.knutcut.svgcore.ManagedLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * End-to-end guard test for [KnutcutViewModel.cut]: the selected model's protocol family must match
 * the live link's transport, so VEVOR JSON+HPGL is never streamed over a BLE Silhouette link (or
 * GPGL over a classic VEVOR link). Runs on the JVM via Robolectric because the ViewModel needs a
 * real [android.app.Application] (SharedPreferences-backed settings).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CutTransportGuardTest {

    /** A link that only reports its transport — write/read/close are no-ops for the guard test. */
    private class FakeLink(override val transport: LinkTransport) : ManagedLink {
        override var onClosed: (() -> Unit)? = null
        override fun write(text: String) {}
        override fun readLine(timeoutMs: Long): String? = null
        override fun close() {}
    }

    private fun viewModel() = KnutcutViewModel(RuntimeEnvironment.getApplication())

    private val vevorModel = Devices.models.first { it.family == PlotterFamily.SPP_VEVOR }
    private val silhouetteModel = Devices.models.first { it.family == PlotterFamily.BLE_SILHOUETTE }

    @Test
    fun bleLinkWithVevorModelIsRefused() {
        val vm = viewModel()
        vm.selectModel(vevorModel)
        vm.attachLinkForTest(FakeLink(LinkTransport.BLE))

        vm.cut()

        // VEVOR can't talk over BLE — abort with the classic-Bluetooth hint, before any design check.
        val status = vm.status
        assertNotNull(status)
        assertTrue("expected classic-Bluetooth hint, was: $status", status!!.contains("klassische Bluetooth"))
        assertFalse("must not start cutting on a transport mismatch", vm.cutting)
    }

    @Test
    fun sppLinkWithSilhouetteModelIsRefused() {
        val vm = viewModel()
        vm.selectModel(silhouetteModel)
        vm.attachLinkForTest(FakeLink(LinkTransport.SPP))

        vm.cut()

        // A Silhouette needs BLE — abort with the BLE hint instead of sending GPGL down a VEVOR link.
        val status = vm.status
        assertNotNull(status)
        assertTrue("expected BLE hint, was: $status", status!!.contains("BLE-Verbindung"))
        assertFalse("must not start cutting on a transport mismatch", vm.cutting)
    }

    @Test
    fun vevorModelOverSppPassesTheTransportGuard() {
        val vm = viewModel()
        vm.selectModel(vevorModel)
        vm.attachLinkForTest(FakeLink(LinkTransport.SPP))

        vm.cut()

        // Matching transport clears the guard; the next gate (no design loaded) proves we got past it.
        assertEquals("Kein Design geladen.", vm.status)
        assertFalse(vm.cutting)
    }

    @Test
    fun silhouetteModelOverBlePassesTheTransportGuard() {
        val vm = viewModel()
        vm.selectModel(silhouetteModel)
        vm.attachLinkForTest(FakeLink(LinkTransport.BLE))

        vm.cut()

        assertEquals("Kein Design geladen.", vm.status)
        assertFalse(vm.cutting)
    }

    @Test
    fun cutWithoutAConnectionAsksToConnectFirst() {
        val vm = viewModel()

        vm.cut()

        assertEquals("Bitte zuerst den Plotter verbinden.", vm.status)
        assertFalse(vm.cutting)
    }
}
