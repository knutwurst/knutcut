package de.knutwurst.knutcut.ui

import de.knutwurst.knutcut.data.CircleDeform
import de.knutwurst.knutcut.data.DeformBaseline
import de.knutwurst.knutcut.data.EnvelopeDeform
import de.knutwurst.knutcut.data.Layer
import de.knutwurst.knutcut.data.Tool
import de.knutwurst.knutcut.data.envelopeDeformDefault
import de.knutwurst.knutcut.svgcore.Bounds
import de.knutwurst.knutcut.svgcore.Polyline
import de.knutwurst.knutcut.svgcore.Pt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    // ---------------------------------------------------------------------------
    // moveEnvelopeCorner + beginDeformEdit
    // ---------------------------------------------------------------------------

    private fun rectLayer(): Layer {
        // A simple rectangle in local space: (0,0)-(100,50)
        val polys = listOf(
            Polyline(
                listOf(Pt(0.0, 0.0), Pt(100.0, 0.0), Pt(100.0, 50.0), Pt(0.0, 50.0), Pt(0.0, 0.0)),
                closed = true,
            )
        )
        return Layer("Rect", polys, Tool.KNIFE, visible = true)
    }

    @Test
    fun moveEnvelopeCornerUpdatesTLCorner() {
        val vm = vm()
        val layer = rectLayer()
        vm.addLayer(layer.name, layer.polylines, layer.tool)
        vm.selectLayer(0)

        val bounds = Bounds(0.0, 0.0, 100.0, 50.0)
        vm.setSelectedDeform(envelopeDeformDefault(bounds))

        // Move TL corner to a new position.
        val newTL = Pt(10.0, 8.0)
        vm.moveEnvelopeCorner(0, newTL)

        val updated = vm.layers[0].deform as? EnvelopeDeform
        assertNotNull(updated)
        assertEquals(newTL.xMm, updated!!.tl.xMm, 1e-9)
        assertEquals(newTL.yMm, updated.tl.yMm, 1e-9)
        // Other corners unchanged
        assertEquals(100.0, updated.tr.xMm, 1e-9)
        assertEquals(0.0,   updated.tr.yMm, 1e-9)
    }

    @Test
    fun moveEnvelopeCornerUpdatesBRCorner() {
        val vm = vm()
        val layer = rectLayer()
        vm.addLayer(layer.name, layer.polylines, layer.tool)
        vm.selectLayer(0)

        val bounds = Bounds(0.0, 0.0, 100.0, 50.0)
        vm.setSelectedDeform(envelopeDeformDefault(bounds))

        val newBR = Pt(120.0, 60.0)
        vm.moveEnvelopeCorner(2, newBR)

        val updated = vm.layers[0].deform as? EnvelopeDeform
        assertNotNull(updated)
        assertEquals(newBR.xMm, updated!!.br.xMm, 1e-9)
        assertEquals(newBR.yMm, updated.br.yMm, 1e-9)
    }

    @Test
    fun moveEnvelopeCornerIsNoOpWhenDeformIsNotEnvelope() {
        val vm = vm()
        val layer = stripLayer(40.0)
        vm.addLayer(layer.name, layer.polylines, layer.tool)
        vm.selectLayer(0)

        // Apply a circle deform, then call moveEnvelopeCorner — should not crash or change anything.
        vm.setSelectedDeform(specForRadius(40.0))
        val beforePolys = vm.layers[0].polylines
        vm.moveEnvelopeCorner(0, Pt(99.0, 99.0))
        assertEquals("polylines unchanged when deform is not envelope", beforePolys, vm.layers[0].polylines)
    }

    @Test
    fun moveEnvelopeCornerOutOfRangeIsNoOp() {
        val vm = vm()
        val layer = rectLayer()
        vm.addLayer(layer.name, layer.polylines, layer.tool)
        vm.selectLayer(0)
        val bounds = Bounds(0.0, 0.0, 100.0, 50.0)
        vm.setSelectedDeform(envelopeDeformDefault(bounds))
        val specBefore = vm.layers[0].deform
        vm.moveEnvelopeCorner(4, Pt(99.0, 99.0))  // index 4 is out of range
        assertEquals("spec unchanged for out-of-range corner", specBefore, vm.layers[0].deform)
    }

    @Test
    fun moveEnvelopeCornerRewarpsPolylines() {
        val vm = vm()
        val layer = rectLayer()
        vm.addLayer(layer.name, layer.polylines, layer.tool)
        vm.selectLayer(0)

        val bounds = Bounds(0.0, 0.0, 100.0, 50.0)
        vm.setSelectedDeform(envelopeDeformDefault(bounds))

        val polysBefore = vm.layers[0].polylines

        // Move TR corner to a different position — the warped polylines must change.
        vm.moveEnvelopeCorner(1, Pt(80.0, 5.0))
        val polysAfter = vm.layers[0].polylines

        assertNotEquals("polylines must change after corner move", polysBefore, polysAfter)
    }

    @Test
    fun moveEnvelopeCornerIsOneUndoStepViaBeginDeformEdit() {
        val vm = vm()
        val layer = rectLayer()
        vm.addLayer(layer.name, layer.polylines, layer.tool)
        vm.selectLayer(0)

        val bounds = Bounds(0.0, 0.0, 100.0, 50.0)
        vm.setSelectedDeform(envelopeDeformDefault(bounds))

        // Simulate one drag gesture: beginDeformEdit once, then several corner moves.
        vm.beginDeformEdit()
        vm.moveEnvelopeCorner(0, Pt(5.0, 2.0))
        vm.moveEnvelopeCorner(0, Pt(8.0, 4.0))
        vm.moveEnvelopeCorner(0, Pt(12.0, 6.0))

        val tlAfter = (vm.layers[0].deform as? EnvelopeDeform)?.tl
        assertNotNull(tlAfter)
        assertEquals(12.0, tlAfter!!.xMm, 1e-9)

        // One undo should revert to the state before beginDeformEdit.
        assertTrue(vm.canUndo)
        vm.undo()
        val tlAfterUndo = (vm.layers[0].deform as? EnvelopeDeform)?.tl
        // After undo the TL should be back to the default (0,0).
        assertNotNull(tlAfterUndo)
        assertEquals(0.0, tlAfterUndo!!.xMm, 1e-9)
        assertEquals(0.0, tlAfterUndo.yMm, 1e-9)
    }
}
