package de.knutwurst.knutcut.ui

import de.knutwurst.knutcut.data.CircleDeform
import de.knutwurst.knutcut.data.DeformBaseline
import de.knutwurst.knutcut.data.Layer
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.hypot

/** Verifies the deform/clearDeform ViewModel functions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeformViewModelTest {

    private fun vm() = KnutcutViewModel(RuntimeEnvironment.getApplication())

    private fun stripLayer(radiusMm: Double = 40.0): Layer {
        val circum = 2 * PI * radiusMm
        val n = 20
        val polylines = listOf(
            Polyline((0..n).map { i -> Pt(i.toDouble() / n * circum, 5.0) }, closed = false)
        )
        return Layer("Strip", polylines, Tool.KNIFE, visible = true)
    }

    private fun specForRadius(radius: Double) = CircleDeform(
        centerXMm = 50.0, centerYMm = 50.0,
        radiusMm = radius, startAngleDeg = 0.0,
        clockwise = false, baseline = DeformBaseline.BOTTOM,
    )

    // --- setLayerDeform + clearLayerDeform ------------------------------------------

    @Test
    fun clearDeformRestoresExactOriginalPolylines() {
        val vm = vm()
        val layer = stripLayer()
        val original = layer.polylines
        vm.addLayer(layer.name, layer.polylines, layer.tool)
        vm.selectLayer(0)

        vm.setLayerDeform(0, specForRadius(40.0))
        // polylines are now warped — they must differ from the originals
        assertNotEquals(original, vm.layers[0].polylines)

        vm.clearLayerDeform(0)
        assertEquals("polylines restored exactly after clear", original, vm.layers[0].polylines)
        assertNull("deform is null after clear", vm.layers[0].deform)
        assertNull("deformSource is null after clear", vm.layers[0].deformSource)
    }

    @Test
    fun setLayerDeformTwiceRewarpsfromOriginalSource() {
        val vm = vm()
        val layer = stripLayer(40.0)
        val original = layer.polylines
        vm.addLayer(layer.name, layer.polylines, layer.tool)

        // First deform
        vm.setLayerDeform(0, specForRadius(40.0))
        val firstResult = vm.layers[0].polylines

        // Second deform with a different radius — must re-warp from the original, not from firstResult
        vm.setLayerDeform(0, specForRadius(60.0))
        val secondResult = vm.layers[0].polylines

        // The source preserved inside the layer should still equal the original
        assertEquals("deformSource unchanged by second spec", original, vm.layers[0].deformSource)

        // The second result must differ from both the first and the original
        assertNotEquals("second warp differs from first warp", firstResult, secondResult)
        assertNotEquals("second warp differs from original", original, secondResult)
    }

    @Test
    fun setLayerDeformProducesPointsAtExpectedRadius() {
        val radius = 40.0
        val vm = vm()
        val layer = stripLayer(radius)
        vm.addLayer(layer.name, layer.polylines, layer.tool)

        vm.setLayerDeform(0, specForRadius(radius))

        val cx = 50.0; val cy = 50.0
        for (p in vm.layers[0].polylines.flatMap { it.points }) {
            val dist = hypot(p.xMm - cx, p.yMm - cy)
            assertEquals("warped point near circle radius", radius, dist, 3.0)
        }
    }

    @Test
    fun setLayerDeformOnOutOfRangeIndexIsNoOp() {
        val vm = vm()
        vm.setLayerDeform(99, specForRadius(30.0))  // no exception, no state change
        assertEquals(0, vm.layers.size)
    }

    @Test
    fun clearLayerDeformIsNoOpWhenNoDeformActive() {
        val vm = vm()
        val layer = stripLayer()
        vm.addLayer(layer.name, layer.polylines, layer.tool)
        val before = vm.layers[0]

        vm.clearLayerDeform(0)   // nothing active — must not crash or alter the layer

        assertEquals("layer unchanged when no deform was active", before, vm.layers[0])
    }

    // --- convenience wrappers ---------------------------------------------------

    @Test
    fun setSelectedDeformTargetsSelectedLayer() {
        val vm = vm()
        val layer = stripLayer(40.0)
        vm.addLayer(layer.name, layer.polylines, layer.tool)
        vm.selectLayer(0)

        vm.setSelectedDeform(specForRadius(40.0))

        assertNotEquals(layer.polylines, vm.layers[0].polylines)
        assertEquals(specForRadius(40.0), vm.layers[0].deform)
    }

    @Test
    fun clearSelectedDeformTargetsSelectedLayer() {
        val vm = vm()
        val layer = stripLayer(40.0)
        val original = layer.polylines
        vm.addLayer(layer.name, layer.polylines, layer.tool)
        vm.selectLayer(0)

        vm.setSelectedDeform(specForRadius(40.0))
        vm.clearSelectedDeform()

        assertEquals(original, vm.layers[0].polylines)
    }
}
