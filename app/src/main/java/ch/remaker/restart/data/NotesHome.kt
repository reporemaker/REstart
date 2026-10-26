package ch.remaker.restart.data

import java.util.Calendar
import java.util.TimeZone

/**
 * What the home screen shows from the notes page: counts and a few entries that need attention.
 * Pure logic, no Android: every rule below is pinned by NotesHomeTest.
 *
 * Which entries can appear (done to-dos never do):
 *  - OVERDUE   an open to-do whose day is before today, important or not;
 *  - TODAY     an important entry (note or open to-do) dated today;
 *  - UPCOMING  an important entry dated after today;
 *  - IMPORTANT an important entry without a day.
 *  A note dated in the past is not shown: it cannot be done and is not coming.
 *
 * Order: the groups in the order above; inside a group OVERDUE and UPCOMING by day (the longest
 * overdue first, the nearest coming first), IMPORTANT newest first; ties go to the important
 * entry, then the to-do, then the older entry, then the id, so the order never flickers.
 */
object NotesHome {

    enum class Reason { OVERDUE, TODAY, UPCOMING, IMPORTANT }

    data class Featured(val item: NoteItem, val reason: Reason)

    data class Summary(val notes: Int, val openTodos: Int, val overdue: Int, val featured: List<Featured>)

    const val MIN_LINES = 1
    const val MAX_LINES = 5
    const val DEFAULT_LINES = 3

    /** Midnight at the start of the day [millis] falls on, in [zone]. */
    fun startOfDay(millis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    fun reasonOf(item: NoteItem, today: Long, zone: TimeZone = TimeZone.getDefault()): Reason? {
        if (item.todo && item.done) return null
        val day = item.due?.let { startOfDay(it, zone) }
        return when {
            item.todo && day != null && day < today -> Reason.OVERDUE
            !item.important -> null
            day == null -> Reason.IMPORTANT
            day == today -> Reason.TODAY
            day > today -> Reason.UPCOMING
            else -> null // an important note dated in the past
        }
    }

    fun summarize(items: List<NoteItem>, now: Long, lines: Int = DEFAULT_LINES, zone: TimeZone = TimeZone.getDefault()): Summary {
        val today = startOfDay(now, zone)
        val featured = items.mapNotNull { item -> reasonOf(item, today, zone)?.let { Featured(item, it) } }
            .sortedWith(
                compareBy<Featured> { it.reason.ordinal }
                    .thenBy { if (it.reason == Reason.IMPORTANT) -it.item.created else it.item.due ?: 0L }
                    .thenBy { !it.item.important }
                    .thenBy { !it.item.todo }
                    .thenBy { it.item.created }
                    .thenBy { it.item.id }
            )
            .take(lines.coerceIn(MIN_LINES, MAX_LINES))
        return Summary(
            notes = items.count { !it.todo },
            openTodos = items.count { it.todo && !it.done },
            overdue = items.count { reasonOf(it, today, zone) == Reason.OVERDUE },
            featured = featured,
        )
    }
}
