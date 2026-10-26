package ch.remaker.restart.data

import ch.remaker.restart.data.NotesHome.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class NotesHomeTest {

    private val zone = TimeZone.getTimeZone("Europe/Zurich")

    /** 26 Oct 2026 at [hour]:00 in Zurich. */
    private fun at(day: Int, hour: Int = 12): Long = Calendar.getInstance(zone).apply {
        clear(); set(2026, Calendar.OCTOBER, day, hour, 0)
    }.timeInMillis

    private val now = at(26, 10)
    private var nextId = 1L

    private fun item(
        text: String, todo: Boolean = false, done: Boolean = false, important: Boolean = false,
        dueDay: Int? = null, createdDay: Int = 20
    ) = NoteItem(
        id = nextId++, text = text, created = at(createdDay), edited = at(createdDay),
        todo = todo, done = done, important = important, due = dueDay?.let { at(it, 0) }
    )

    private fun texts(items: List<NoteItem>, lines: Int = 5) =
        NotesHome.summarize(items, now, lines, zone).featured.map { it.item.text }

    @Test
    fun anOpenToDoPastItsDayIsOverdueEvenWhenNotImportant() {
        assertEquals(Reason.OVERDUE, NotesHome.reasonOf(item("a", todo = true, dueDay = 25), NotesHome.startOfDay(now, zone), zone))
    }

    @Test
    fun doneToDosNeverShow() {
        assertNull(NotesHome.reasonOf(item("a", todo = true, done = true, important = true, dueDay = 20), NotesHome.startOfDay(now, zone), zone))
    }

    @Test
    fun anImportantNoteDatedInThePastDoesNotShow() {
        assertNull(NotesHome.reasonOf(item("a", important = true, dueDay = 24), NotesHome.startOfDay(now, zone), zone))
    }

    @Test
    fun ordinaryEntriesWithoutImportanceOrDelayDoNotShow() {
        assertEquals(emptyList<String>(), texts(listOf(item("note"), item("todo", todo = true, dueDay = 28), item("dated note", dueDay = 27))))
    }

    @Test
    fun overdueComesFirstThenTodayThenUpcomingThenUndatedImportant() {
        val items = listOf(
            item("undated", important = true),
            item("upcoming", important = true, dueDay = 29),
            item("today", important = true, dueDay = 26),
            item("overdue", todo = true, dueDay = 22),
        )
        assertEquals(listOf("overdue", "today", "upcoming", "undated"), texts(items))
    }

    @Test
    fun theLongestOverdueComesFirstAndTheNearestUpcomingFirst() {
        val items = listOf(
            item("late 2 days", todo = true, dueDay = 24),
            item("late 5 days", todo = true, dueDay = 21),
            item("in 3 days", important = true, dueDay = 29),
            item("tomorrow", important = true, dueDay = 27),
        )
        assertEquals(listOf("late 5 days", "late 2 days", "tomorrow", "in 3 days"), texts(items))
    }

    @Test
    fun onTheSameDayTheImportantThenTheToDoThenTheOlderEntryGoFirst() {
        val items = listOf(
            item("plain late, newer", todo = true, dueDay = 24, createdDay = 22),
            item("plain late, older", todo = true, dueDay = 24, createdDay = 18),
            item("important late", todo = true, important = true, dueDay = 24, createdDay = 23),
        )
        assertEquals(listOf("important late", "plain late, older", "plain late, newer"), texts(items))
        val sameDay = listOf(item("note today", important = true, dueDay = 26), item("todo today", todo = true, important = true, dueDay = 26))
        assertEquals(listOf("todo today", "note today"), texts(sameDay))
    }

    @Test
    fun undatedImportantEntriesShowNewestFirst() {
        val items = listOf(item("old", important = true, createdDay = 10), item("new", important = true, createdDay = 25))
        assertEquals(listOf("new", "old"), texts(items))
    }

    @Test
    fun theNumberOfLinesStaysBetweenOneAndFive() {
        val items = (1..8).map { item("late $it", todo = true, dueDay = 10 + it) }
        assertEquals(3, NotesHome.summarize(items, now, 3, zone).featured.size)
        assertEquals(1, NotesHome.summarize(items, now, 0, zone).featured.size)
        assertEquals(5, NotesHome.summarize(items, now, 9, zone).featured.size)
    }

    @Test
    fun countsCoverNotesOpenToDosAndOverdue() {
        val items = listOf(
            item("n1"), item("n2", important = true),
            item("t open", todo = true), item("t late", todo = true, dueDay = 25), item("t done", todo = true, done = true, dueDay = 20),
        )
        val s = NotesHome.summarize(items, now, 3, zone)
        assertEquals(2, s.notes)
        assertEquals(2, s.openTodos)
        assertEquals(1, s.overdue)
    }

    @Test
    fun aDueTimeLaterTodayIsStillTodayNotUpcoming() {
        val late = item("tonight", important = true).copy(due = at(26, 23))
        assertEquals(Reason.TODAY, NotesHome.reasonOf(late, NotesHome.startOfDay(now, zone), zone))
    }
}
