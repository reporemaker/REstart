package ch.remaker.restart.data

import java.util.TimeZone

/**
 * The order inside the notes page sections (decided 26 Oct 2026): the date first, and on the
 * same day the important entries first. Days, not times: two entries of one day are equal by
 * date, whatever the hour. Pure logic, pinned by NotesOrderTest.
 */
object NotesOrder {

    private fun day(millis: Long, zone: TimeZone) = NotesHome.startOfDay(millis, zone)

    /** Open to-dos: by their day, the longest overdue first and the furthest last; undated at the end. */
    fun openTodos(items: List<NoteItem>, zone: TimeZone = TimeZone.getDefault()): List<NoteItem> =
        items.filter { it.todo && !it.done }.sortedWith(
            compareBy<NoteItem> { it.due?.let { d -> day(d, zone) } ?: Long.MAX_VALUE }
                .thenBy { !it.important }
                .thenByDescending { it.created }
                .thenBy { it.id }
        )

    /**
     * Notes: those dated today or later first, the nearest first; then every other note by the day
     * it shows (its date, or the day it was written), the newest first.
     */
    fun notes(items: List<NoteItem>, now: Long, zone: TimeZone = TimeZone.getDefault()): List<NoteItem> {
        val today = day(now, zone)
        fun coming(item: NoteItem) = item.due != null && day(item.due, zone) >= today
        return items.filter { !it.todo }.sortedWith(
            compareBy<NoteItem> { if (coming(it)) 0 else 1 }
                .thenBy { if (coming(it)) day(it.due!!, zone) else -day(it.due ?: it.created, zone) }
                .thenBy { !it.important }
                .thenByDescending { it.created }
                .thenBy { it.id }
        )
    }

    /** Done to-dos: the last one ticked first. */
    fun done(items: List<NoteItem>): List<NoteItem> =
        items.filter { it.todo && it.done }.sortedWith(compareByDescending<NoteItem> { it.edited }.thenBy { it.id })
}
