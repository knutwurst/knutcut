package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FillNestingTest {

    private val CYAN = 0xFF00ACC1.toInt()
    private val RED = 0xFFE53935.toInt()

    /** Axis-aligned closed square at (x,y) with side s. */
    private fun square(x: Double, y: Double, s: Double) =
        Polyline(listOf(Pt(x, y), Pt(x + s, y), Pt(x + s, y + s), Pt(x, y + s)), closed = true)

    @Test
    fun nestedSameColourFormsOneGroupSoTheHoleCarves() {
        // Outer square with a smaller square fully inside (a donut / letter counter), same color.
        val outer = square(0.0, 0.0, 100.0)
        val inner = square(30.0, 30.0, 40.0)
        val groups = FillNesting.fillGroups(listOf(outer, inner), listOf(CYAN, CYAN))
        assertEquals("nested same-color contours share one even-odd group", 1, groups.size)
        assertEquals(listOf(0, 1), groups[0])
    }

    @Test
    fun overlappingSameColourStaysSeparateSoTheyUnion() {
        // Two squares that overlap but neither contains the other — the regression case.
        val a = square(0.0, 0.0, 60.0)
        val b = square(40.0, 40.0, 60.0)
        val groups = FillNesting.fillGroups(listOf(a, b), listOf(CYAN, CYAN))
        assertEquals("overlapping shapes fill independently (no even-odd cancellation)", 2, groups.size)
    }

    @Test
    fun separateSameColourStaysSeparate() {
        val a = square(0.0, 0.0, 20.0)
        val b = square(80.0, 80.0, 20.0)
        val groups = FillNesting.fillGroups(listOf(a, b), listOf(CYAN, CYAN))
        assertEquals(2, groups.size)
    }

    @Test
    fun nestedDifferentColoursStaySeparate() {
        // A small shape inside a big one but a different color: the inner is its own fill on top,
        // not a hole in the outer.
        val outer = square(0.0, 0.0, 100.0)
        val inner = square(30.0, 30.0, 40.0)
        val groups = FillNesting.fillGroups(listOf(outer, inner), listOf(CYAN, RED))
        assertEquals(2, groups.size)
    }

    @Test
    fun threeLevelNestingSameColourIsOneGroup() {
        val outer = square(0.0, 0.0, 100.0)
        val mid = square(20.0, 20.0, 60.0)
        val core = square(40.0, 40.0, 20.0)
        val groups = FillNesting.fillGroups(listOf(outer, mid, core), listOf(CYAN, CYAN, CYAN))
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].size)
    }

    @Test
    fun openContourIsItsOwnGroup() {
        val open = Polyline(listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 10.0)), closed = false)
        val closed = square(0.0, 0.0, 100.0)
        val groups = FillNesting.fillGroups(listOf(closed, open), listOf(CYAN, CYAN))
        // The open contour can't be a hole; it stays separate from the closed shape.
        assertEquals(2, groups.size)
    }

    @Test
    fun containmentPairsAreColourIndependentAndComposeIntoGroups() {
        // containmentPairs is the cacheable geometry step; groups() applies colors cheaply.
        val outer = square(0.0, 0.0, 100.0)
        val inner = square(30.0, 30.0, 40.0)
        val contours = listOf(outer, inner)
        val pairs = FillNesting.containmentPairs(contours)
        assertEquals("outer contains inner", listOf(0 to 1), pairs)
        // Same color → one group (hole carves); different color → two groups (inner on top).
        assertEquals(1, FillNesting.groups(2, pairs, listOf(CYAN, CYAN)).size)
        assertEquals(2, FillNesting.groups(2, pairs, listOf(CYAN, RED)).size)
    }

    @Test
    fun scalesToManyContoursWithoutMerging() {
        // 50 separate, non-overlapping same-color squares → 50 independent groups. Guards that the
        // grouping has no contour-count cap (the renderer no longer falls back above a threshold).
        val contours = (0 until 50).map { square(it * 30.0, 0.0, 20.0) }
        val groups = FillNesting.fillGroups(contours, List(50) { CYAN })
        assertEquals(50, groups.size)
    }

    @Test
    fun groupsKeepDocumentOrder() {
        val a = square(0.0, 0.0, 20.0)
        val b = square(80.0, 80.0, 20.0)
        val c = square(160.0, 160.0, 20.0)
        val groups = FillNesting.fillGroups(listOf(a, b, c), listOf(CYAN, CYAN, CYAN))
        assertEquals(3, groups.size)
        assertTrue("groups ordered by first index", groups[0][0] < groups[1][0] && groups[1][0] < groups[2][0])
    }
}
