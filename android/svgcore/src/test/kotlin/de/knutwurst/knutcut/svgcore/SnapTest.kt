package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SnapTest {

    @Test fun `toStep rounds to the nearest multiple`() {
        assertEquals(10.0, Snap.toStep(12.0, 10.0), 1e-9)
        assertEquals(5.0, Snap.toStep(7.0, 5.0), 1e-9)
        assertEquals(10.0, Snap.toStep(8.0, 5.0), 1e-9)
        assertEquals(0.0, Snap.toStep(0.4, 1.0), 1e-9)
        assertEquals(1.0, Snap.toStep(0.6, 1.0), 1e-9)
    }

    @Test fun `toStep is a no-op when the step is zero or negative`() {
        assertEquals(12.34, Snap.toStep(12.34, 0.0), 1e-9)
        assertEquals(12.34, Snap.toStep(12.34, -1.0), 1e-9)
    }

    @Test fun `gridCenter snaps the top-left, not the centre`() {
        // A 40 mm wide box: top-left is 20 mm left of the centre.
        val tlOffset = Pt(-20.0, -10.0)
        // Centre at 31/26 → top-left at 11/16 → snaps to 10/15 → centre back to 30/25.
        val c = Snap.gridCenter(Pt(31.0, 26.0), tlOffset, 5.0)
        assertEquals(30.0, c.xMm, 1e-9)
        assertEquals(15.0 + 10.0, c.yMm, 1e-9)
    }

    @Test fun `nearestWithin picks the closest candidate inside the tolerance`() {
        assertEquals(50.0, Snap.nearestWithin(52.0, listOf(10.0, 50.0, 90.0), 5.0))
        assertNull(Snap.nearestWithin(52.0, listOf(10.0, 90.0), 5.0))
        assertNull(Snap.nearestWithin(52.0, listOf(50.0), 0.0))
    }

    @Test fun `uniformScaleFactor is 1 at the start of a drag and scales along the diagonal`() {
        // Start diagonal (40, 30); dragging exactly to it → factor 1.
        assertEquals(1.0, Snap.uniformScaleFactor(40.0, 30.0, 40.0, 30.0), 1e-9)
        // Twice as far along the diagonal → factor 2.
        assertEquals(2.0, Snap.uniformScaleFactor(80.0, 60.0, 40.0, 30.0), 1e-9)
        // Degenerate diagonal → 1.
        assertEquals(1.0, Snap.uniformScaleFactor(5.0, 5.0, 0.0, 0.0), 1e-9)
    }

    @Test fun `uniformScaleFactor clamps to a small positive minimum`() {
        assertEquals(0.02, Snap.uniformScaleFactor(-40.0, -30.0, 40.0, 30.0), 1e-9)
    }
}
