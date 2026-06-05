package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NestTest {

    @Test fun `two pieces that fit share the top row`() {
        val placed = Nest.pack(listOf(Nest.Box(0, 40.0, 30.0), Nest.Box(1, 40.0, 30.0)), areaWidth = 120.0, gap = 5.0, allow90 = false)
        val a = placed.first { it.id == 0 }
        val b = placed.first { it.id == 1 }
        assertEquals(0.0, a.y, 1e-9)
        assertEquals(0.0, b.y, 1e-9)
        assertEquals(0.0, a.x, 1e-9)
        assertEquals(45.0, b.x, 1e-9) // 40 + 5 gap
    }

    @Test fun `a piece that overflows the width wraps to a new row`() {
        val placed = Nest.pack(listOf(Nest.Box(0, 80.0, 30.0), Nest.Box(1, 80.0, 20.0)), areaWidth = 100.0, gap = 5.0, allow90 = false)
        val a = placed.first { it.id == 0 }
        val b = placed.first { it.id == 1 }
        assertEquals(0.0, a.x, 1e-9)
        assertEquals(0.0, b.x, 1e-9)
        assertTrue("second row sits below the first", b.y >= a.h)
    }

    @Test fun `allow90 rotates a tall piece to landscape`() {
        val placed = Nest.pack(listOf(Nest.Box(0, 20.0, 90.0)), areaWidth = 120.0, gap = 5.0, allow90 = true)
        val p = placed.first { it.id == 0 }
        assertTrue(p.rotated)
        assertEquals(90.0, p.w, 1e-9) // w/h swapped
        assertEquals(20.0, p.h, 1e-9)
    }

    @Test fun `without allow90 a tall piece keeps its orientation`() {
        val placed = Nest.pack(listOf(Nest.Box(0, 20.0, 90.0)), areaWidth = 120.0, gap = 5.0, allow90 = false)
        val p = placed.first { it.id == 0 }
        assertEquals(false, p.rotated)
        assertEquals(20.0, p.w, 1e-9)
    }
}
