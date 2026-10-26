package ch.remaker.restart.data

import ch.remaker.restart.data.NotesHome.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class NotesHomeTest {

    private val zone = TimeZone.getTimeZone("Europe/Zurich")

    /** [day] October 2026 at [hour]:00 in Zurich. */
    private fun at(day: Int, hour: Int = 12): Long = Calendar.getInstance(zone).apply {
        clear(); set(2026, Calendar.OCTOBER, day, hour, 0)
    }.timeInMillis

    private val now = at(26, 10)
    private val today get() = NotesHome.startOfDay(now, zone)
    private var nextId = 1L

    private fun item(
        text: String, todo: Boolean = false, done: Boolean = false, important: Boolean = false,
        dueDay: Int? = null, createdDay: Int = 20
    ) = NoteItem(
        id = nextId++, text = text, created = at(createdDay), edited = at(createdDay),
        todo = todo, done = done, important = important, due = dueDay?.let { at(it, 0) }
    )

    private fun texts(items: List<NoteItem>, lines: Int = NotesHome.MAX_LINES) =
        NotesHome.summarize(items, now, lines, zone).featured.map { it.item.text }

    @Test
    fun onlyImportantEntriesAreWrittenOut() {
        val items = listOf(item("plain note"), item("plain todo", todo = true, dueDay = 28), item("plain late", todo = true, dueDay = 22))
        assertEquals(emptyList<String>(), texts(items))
    }

    @Test
    fun aLateToDoThatIsNotImportantOnlyCounts() {
        val late = item("late", todo = true, dueDay = 25)
        assertNull(NotesHome.reasonOf(late, today, zone))
        assertTrue(NotesHome.isOverdue(late, today, zone))
        assertEquals(1, NotesHome.summarize(listOf(late), now, zone = zone).overdue)
    }

    @Test
    fun anImportantLateToDoIsOverdue() {
        assertEquals(Reason.OVERDUE, NotesHome.reasonOf(item("a", todo = true, important = true, dueDay = 25), today, zone))
    }

    @Test
    fun doneToDosNeverShowAndNeverCountAsOverdue() {
        val done = item("a", todo = true, done = true, important = true, dueDay = 20)
        assertNull(NotesHome.reasonOf(done, today, zone))
        assertEquals(0, NotesHome.summarize(listOf(done), now, zone = zone).overdue)
    }

    @Test
    fun anImportantNoteDatedInThePastDoesNotShow() {
        assertNull(NotesHome.reasonOf(item("a", important = true, dueDay = 24), today, zone))
    }

    @Test
    fun overdueComesFirstThenTodayThenUpcomingThenUndated() {
        val items = listOf(
            item("undated", important = true),
            item("upcoming", important = true, dueDay = 29),
            item("today", important = true, dueDay = 26),
            item("overdue", todo = true, important = true, dueDay = 22),
        )
        assertEquals(listOf("overdue", "today", "upcoming"), texts(items))
        assertEquals(listOf("today", "upcoming", "undated"), texts(items.dropLast(1)))
    }

    @Test
    fun theLongestOverdueComesFirstAndTheNearestUpcomingFirst() {
        val late = listOf(item("late 2 days", todo = true, important = true, dueDay = 24), item("late 5 days", todo = true, important = true, dueDay = 21))
        assertEquals(listOf("late 5 days", "late 2 days"), texts(late))
        val coming = listOf(item("in 3 days", important = true, dueDay = 29), item("tomorrow", important = true, dueDay = 27))
        assertEquals(listOf("tomorrow", "in 3 days"), texts(coming))
    }

    @Test
    fun onTheSameDayTheToDoThenTheOlderEntryGoFirst() {
        val items = listOf(
            item("note, older", important = true, dueDay = 27, createdDay = 10),
            item("todo, newer", todo = true, important = true, dueDay = 27, createdDay = 22),
            item("todo, older", todo = true, important = true, dueDay = 27, createdDay = 12),
        )
        assertEquals(listOf("todo, older", "todo, newer", "note, older"), texts(items))
    }

    @Test
    fun undatedImportantEntriesShowNewestFirst() {
        val items = listOf(item("old", important = true, createdDay = 10), item("new", important = true, createdDay = 25))
        assertEquals(listOf("new", "old"), texts(items))
    }

    @Test
    fun neverMoreThanThreeLines() {
        val items = (1..8).map { item("late $it", todo = true, important = true, dueDay = 10 + it) }
        assertEquals(3, NotesHome.summarize(items, now, zone = zone).featured.size)
        assertEquals(3, NotesHome.summarize(items, now, 9, zone).featured.size)
        assertEquals(0, NotesHome.summarize(items, now, 0, zone).featured.size)
    }

    @Test
    fun countsCoverNotesOpenToDosAndEveryOverdueToDo() {
        val items = listOf(
            item("n1"), item("n2", important = true),
            item("t open", todo = true), item("t late", todo = true, dueDay = 25),
            item("t late important", todo = true, important = true, dueDay = 23),
            item("t done", todo = true, done = true, dueDay = 20),
        )
        val s = NotesHome.summarize(items, now, zone = zone)
        assertEquals(2, s.notes)
        assertEquals(3, s.openTodos)
        assertEquals(2, s.overdue)
        assertEquals(listOf("t late important", "n2"), s.featured.map { it.item.text })
    }

    @Test
    fun aDueTimeLaterTodayIsStillTodayNotUpcoming() {
        val tonight = item("tonight", important = true).copy(due = at(26, 23))
        assertEquals(Reason.TODAY, NotesHome.reasonOf(tonight, today, zone))
    }
}
