package de.knutwurst.knutcut.svgcore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {

    @Test fun `undo walks back through pushed snapshots`() {
        val h = History<String>()
        h.push("A")
        h.push("B")
        assertEquals("B", h.undo("C"))
        assertEquals("A", h.undo("B"))
        assertNull(h.undo("A"))
    }

    @Test fun `redo replays what undo banked`() {
        val h = History<String>()
        h.push("A")
        h.push("B")
        assertEquals("B", h.undo("C")) // now at B, redo holds C
        assertEquals("C", h.redo("B"))
        assertNull(h.redo("C"))
    }

    @Test fun `a new push clears the redo stack`() {
        val h = History<String>()
        h.push("A")
        h.undo("B")
        assertTrue(h.canRedo)
        h.push("X")
        assertFalse(h.canRedo)
    }

    @Test fun `consecutive equal pushes are de-duped`() {
        val h = History<String>(sameAs = { a, b -> a == b })
        h.push("A")
        h.push("A")
        assertEquals("A", h.undo("B"))
        assertNull(h.undo("A"))
    }

    @Test fun `respects the max size`() {
        val h = History<Int>(max = 2)
        h.push(1)
        h.push(2)
        h.push(3) // drops 1
        assertEquals(3, h.undo(4))
        assertEquals(2, h.undo(3))
        assertNull(h.undo(2))
    }

    @Test fun `undo and redo are no-ops on empty stacks`() {
        val h = History<String>()
        assertFalse(h.canUndo)
        assertFalse(h.canRedo)
        assertNull(h.undo("A"))
        assertNull(h.redo("A"))
    }
}
