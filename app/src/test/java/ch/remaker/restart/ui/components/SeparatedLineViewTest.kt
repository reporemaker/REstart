package ch.remaker.restart.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class SeparatedLineViewTest {

    // One unit per character, as a monospaced font would measure
    private fun wrap(items: List<String>, room: Int) = SeparatedLineView.wrap(items, room.toFloat()) { it.length.toFloat() }

    private val counts = listOf("Notes 4", "To-do 3", "Overdue 2")

    @Test
    fun allOnOneLineWhenThereIsRoom() {
        assertEquals("Notes 4 · To-do 3 · Overdue 2", wrap(counts, 40))
    }

    @Test
    fun breaksBetweenItemsAndDropsTheSeparator() {
        // "Notes 4 · To-do 3 · " would fit 20 characters; the dot must not end the line
        assertEquals("Notes 4 · To-do 3\nOverdue 2", wrap(counts, 20))
    }

    @Test
    fun oneItemPerLineInANarrowColumn() {
        assertEquals("Notes 4\nTo-do 3\nOverdue 2", wrap(counts, 10))
    }

    @Test
    fun anItemWiderThanTheLineStandsAlone() {
        assertEquals("Notes 4\nOverdue 2", wrap(listOf("Notes 4", "Overdue 2"), 5))
    }

    @Test
    fun nothingToWrap() {
        assertEquals("", wrap(emptyList(), 20))
    }
}
