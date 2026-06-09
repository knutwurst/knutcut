package de.knutwurst.knutcut.data

import de.knutwurst.knutcut.svgcore.SvgParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertTrue("expected at least 5000 library items", PlotterSvgLibrary.items.size >= 5000)
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
    fun searchMatchesNameAndTagsNotConstantSource() {
        val heart = PlotterSvgLibrary.items.first { it.id == "mdi-heart" }

        assertTrue(heart.matches("Heart"))
        assertTrue(heart.matches("heart"))
        // The shared source/attribution and the category id must NOT make a query match every
        // item; otherwise common substrings ("icon", "material", single letters) flood the list.
        assertFalse(heart.matches("Iconify"))
        assertFalse(heart.matches("material"))
    }

    @Test
    fun commonSubstringDoesNotReturnWholeLibrary() {
        // "a" appears in the constant source string; with the old matcher it returned everything.
        val hits = PlotterSvgLibrary.items.count { it.matches("a") }
        assertTrue("query 'a' should not match the entire library", hits < PlotterSvgLibrary.items.size)
    }
}
