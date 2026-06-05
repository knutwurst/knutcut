package de.knutwurst.knutcut.svgcore

/**
 * A bounded undo/redo stack, kept Android-free so it can be unit-tested. [push] records a restore
 * point before a mutating edit; a push that [sameAs] the current top is ignored, so nested or no-op
 * edits don't pile up. [undo] and [redo] shuttle the caller's current value between the two stacks
 * and return the value to restore (or null when there is nothing to do).
 */
class History<T>(
    private val max: Int = 40,
    private val sameAs: (T, T) -> Boolean = { a, b -> a === b },
) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Record [value] as a restore point before a mutating edit. */
    fun push(value: T) {
        if (undoStack.lastOrNull()?.let { sameAs(it, value) } == true) return
        undoStack.addLast(value)
        while (undoStack.size > max) undoStack.removeFirst()
        redoStack.clear()
    }

    /** The value to restore for an undo, banking [current] for a later redo; null if nothing to undo. */
    fun undo(current: T): T? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(current)
        return undoStack.removeLast()
    }

    /** The value to restore for a redo, banking [current] for a later undo; null if nothing to redo. */
    fun redo(current: T): T? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(current)
        return redoStack.removeLast()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
