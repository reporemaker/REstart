package ch.remaker.restart.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class NotesOrderTest {

    private val zone = TimeZone.getTimeZone("Europe/Zurich")

    /** [day] October 2026 at [hour]:00 in Zurich. */
    private fun at(day: Int, hour: Int = 12): Long = Calendar.getInstance(zone).apply {
        clear(); set(2026, Calendar.OCTOBER, day, hour, 0)
    }.timeInMillis

    private val now = at(26, 10)
    private var nextId = 1L

    private fun item(
        text: String, todo: Boolean = false, done: Boolean = false, important: Boolean = false,
        dueDay: Int? = null, dueHour: Int = 0, createdDay: Int = 20, createdHour: Int = 12
    ) = NoteItem(
        id = nextId++, text = text, created = at(createdDay, createdHour), edited = at(createdDay, createdHour),
        todo = todo, done = done, important = important, due = dueDay?.let { at(it, dueHour) }
    )

    @Test
    fun todosGoByDayThenImportantFirst() {
        val items = listOf(
            item("undated", todo = true),
            item("28 plain", todo = true, dueDay = 28),
            item("28 important", todo = true, dueDay = 28, important = true),
            item("22 late", todo = true, dueDay = 22),
            item("26 today", todo = true, dueDay = 26),
        )
        assertEquals(
            listOf("22 late", "26 today", "28 important", "28 plain", "undated"),
            NotesOrder.openTodos(items, zone).map { it.text }
        )
    }

    @Test
    fun theHourDoesNotBeatImportance() {
        // Same day: an earlier hour must not put a plain to-do above the important one
        val items = listOf(
            item("plain at 8", todo = true, dueDay = 28, dueHour = 8),
            item("important at 20", todo = true, dueDay = 28, dueHour = 20, important = true),
        )
        assertEquals(listOf("important at 20", "plain at 8"), NotesOrder.openTodos(items, zone).map { it.text })
    }

    @Test
    fun undatedTodosImportantFirstThenNewest() {
        val items = listOf(
            item("old", todo = true, createdDay = 10),
            item("new", todo = true, createdDay = 24),
            item("important old", todo = true, createdDay = 5, important = true),
        )
        assertEquals(listOf("important old", "new", "old"), NotesOrder.openTodos(items, zone).map { it.text })
    }

    @Test
    fun doneTodosAreNotInTheOpenList() {
        val items = listOf(item("done", todo = true, done = true, dueDay = 22), item("open", todo = true))
        assertEquals(listOf("open"), NotesOrder.openTodos(items, zone).map { it.text })
        assertEquals(listOf("done"), NotesOrder.done(items).map { it.text })
    }

    @Test
    fun notesComingFirstThenNewestWritten() {
        val items = listOf(
            item("written 24", createdDay = 24),
            item("written 12", createdDay = 12),
            item("dated 30", dueDay = 30),
            item("dated today", dueDay = 26),
            item("dated past 18", dueDay = 18),
        )
        assertEquals(
            listOf("dated today", "dated 30", "written 24", "dated past 18", "written 12"),
            NotesOrder.notes(items, now, zone).map { it.text }
        )
    }

    @Test
    fun notesSameDayImportantFirst() {
        val items = listOf(
            item("plain later", createdDay = 24, createdHour = 18),
            item("important earlier", createdDay = 24, createdHour = 8, important = true),
            item("coming plain", dueDay = 29),
            item("coming important", dueDay = 29, important = true),
        )
        assertEquals(
            listOf("coming important", "coming plain", "important earlier", "plain later"),
            NotesOrder.notes(items, now, zone).map { it.text }
        )
    }

    @Test
    fun todosAreNotInTheNotesList() {
        val items = listOf(item("todo", todo = true), item("note"))
        assertEquals(listOf("note"), NotesOrder.notes(items, now, zone).map { it.text })
    }
}
