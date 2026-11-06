package de.knutwurst.knutcut.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SheetInsetsTest {

    @Test
    fun padsTheWholeBarWhenComposeSeesNoInset() {
        // Android 12, three-button bar: the window reports the bar, Compose reports nothing.
        assertEquals(126, navBarShortfall(systemPx = 126, composePx = 0))
    }

    @Test
    fun addsNothingWhereMaterialAlreadyPadsTheSheet() {
        assertEquals(0, navBarShortfall(systemPx = 126, composePx = 126))
        assertEquals(0, navBarShortfall(systemPx = 0, composePx = 0))
    }

    @Test
    fun neverGoesNegative() {
        assertEquals(0, navBarShortfall(systemPx = 0, composePx = 48))
    }
}
