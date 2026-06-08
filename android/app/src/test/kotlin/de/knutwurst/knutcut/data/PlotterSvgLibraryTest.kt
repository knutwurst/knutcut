package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.SvgParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlotterSvgLibraryTest {

    @Test
    fun allLibraryItemsHaveUniqueIds() {
        val ids = PlotterSvgLibrary.items.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun libraryHasLargeOfflineSelection() {
        assertTrue("expected at least 500 library items", PlotterSvgLibrary.items.size >= 500)
        assertEquals(8, PlotterSvgLibrary.categories.size)
    }

    @Test
    fun allLibraryItemsParseToCuttablePaths() {
        for (item in PlotterSvgLibrary.items) {
            val polylines = SvgParser.parse(item.svg)
            assertTrue("${item.id} has no cuttable paths", polylines.isNotEmpty())
            assertTrue("${item.id} has no points", polylines.sumOf { it.points.size } > 1)
        }
    }

    @Test
    fun searchMatchesNameTagsAndSource() {
        val heart = PlotterSvgLibrary.items.first { it.id == "mdi-heart" }

        assertTrue(heart.matches("Heart"))
        assertTrue(heart.matches("heart"))
        assertTrue(heart.matches("Iconify"))
    }
}
