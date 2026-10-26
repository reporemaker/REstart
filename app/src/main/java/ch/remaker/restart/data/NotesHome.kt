package ch.remaker.restart.data

import java.util.Calendar
import java.util.TimeZone

/**
 * What the home screen shows from the notes page: counts and a few entries that need attention.
 * Pure logic, no Android: every rule below is pinned by NotesHomeTest.
 *
 * Only entries marked important are written on the home screen (done to-dos never are):
 *  - OVERDUE   an important open to-do whose day is before today;
 *  - TODAY     an important entry (note or open to-do) dated today;
 *  - UPCOMING  an important entry dated after today;
 *  - IMPORTANT an important entry without a day.
 *  An important note dated in the past is not shown: it cannot be done and is not coming.
 *  Everything else only counts: an open to-do past its day that is not important adds to the
 *  overdue number and is never written out. Nothing important: the counts alone.
 *
 * Order: the groups in the order above; inside a group OVERDUE and UPCOMING by day (the longest
 * overdue first, the nearest coming first), IMPORTANT newest first; ties go to the to-do, then
 * the older entry, then the id, so the order never flickers.
 */
object NotesHome {

    enum class Reason { OVERDUE, TODAY, UPCOMING, IMPORTANT }

    data class Featured(val item: NoteItem, val reason: Reason)

    data class Summary(val notes: Int, val openTodos: Int, val overdue: Int, val featured: List<Featured>)

    /** At most three lines on the home screen (decided 26 Oct 2026: no setting). */
    const val MAX_LINES = 3

    /** Midnight at the start of the day [millis] falls on, in [zone]. */
    fun startOfDay(millis: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** An open to-do past its day, important or not: what the overdue number counts. */
    fun isOverdue(item: NoteItem, today: Long, zone: TimeZone = TimeZone.getDefault()): Boolean =
        item.todo && !item.done && item.due != null && startOfDay(item.due, zone) < today

    /** Why [item] is written on the home screen, or null when it only counts. */
    fun reasonOf(item: NoteItem, today: Long, zone: TimeZone = TimeZone.getDefault()): Reason? {
        if (item.todo && item.done) return null
        if (!item.important) return null
        val day = item.due?.let { startOfDay(it, zone) }
        return when {
            item.todo && day != null && day < today -> Reason.OVERDUE
            day == null -> Reason.IMPORTANT
            day == today -> Reason.TODAY
            day > today -> Reason.UPCOMING
            else -> null // an important note dated in the past
        }
    }

    fun summarize(items: List<NoteItem>, now: Long, lines: Int = MAX_LINES, zone: TimeZone = TimeZone.getDefault()): Summary {
        val today = startOfDay(now, zone)
        val featured = items.mapNotNull { item -> reasonOf(item, today, zone)?.let { Featured(item, it) } }
            .sortedWith(
                compareBy<Featured> { it.reason.ordinal }
                    .thenBy { if (it.reason == Reason.IMPORTANT) -it.item.created else it.item.due ?: 0L }
                    .thenBy { !it.item.todo }
                    .thenBy { it.item.created }
                    .thenBy { it.item.id }
            )
            .take(lines.coerceIn(0, MAX_LINES))
        return Summary(
            notes = items.count { !it.todo },
            openTodos = items.count { it.todo && !it.done },
            overdue = items.count { isOverdue(it, today, zone) },
            featured = featured,
        )
    }
}
