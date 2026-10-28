package ch.remaker.restart.data

import androidx.core.content.edit
import ch.remaker.common.AppLogger
import ch.remaker.common.getLocalizedStringArray
import ch.remaker.restart.R
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * One entry on the notes page: a note, or a to-do that can be ticked off.
 * [due] is an optional day (epoch millis at local midnight) for both kinds; a to-do past it and
 * not done is overdue. [important] and the day are what the home screen will show later.
 * [category] is the user's own word ("" = none); categories exist as long as an entry uses one.
 */
@JsonClass(generateAdapter = true)
data class NoteItem(
    val id: Long,
    val text: String,
    val created: Long,
    val edited: Long,
    val todo: Boolean = false,
    val done: Boolean = false,
    val due: Long? = null,
    val important: Boolean = false,
    val category: String = "",
)

/** Reads and writes the notes page. Entries are kept as JSON under one key, written at once. */
class NotesStore(private val prefs: Prefs) {

    private val adapter = Moshi.Builder().build()
        .adapter<List<NoteItem>>(Types.newParameterizedType(List::class.java, NoteItem::class.java))
    private val namesAdapter = Moshi.Builder().build()
        .adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
    private val idsAdapter = Moshi.Builder().build()
        .adapter<List<Long>>(Types.newParameterizedType(List::class.java, java.lang.Long::class.java))

    fun load(): List<NoteItem> {
        val json = prefs.prefsNormal.getString(NOTES_ITEMS, null)
        if (json == null) return migrateOldMessages()
        return try {
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            AppLogger.e("NotesStore", "Could not read notes", e)
            emptyList()
        }
    }

    fun save(items: List<NoteItem>) {
        prefs.prefsNormal.edit(commit = true) { putString(NOTES_ITEMS, adapter.toJson(items)) }
    }

    /**
     * Ids of deleted entries, so that merging a backup taken before the deletion does not bring
     * them back. Only ids, no text; the oldest drop off past [MAX_DELETED].
     */
    fun deletedIds(): Set<Long> = try {
        prefs.prefsNormal.getString(NOTES_DELETED, null)?.let { idsAdapter.fromJson(it) }.orEmpty().toSet()
    } catch (e: Exception) {
        AppLogger.e("NotesStore", "Could not read deleted note ids", e)
        emptySet()
    }

    fun markDeleted(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val all = (deletedIds().toList() + ids).distinct().takeLast(MAX_DELETED)
        prefs.prefsNormal.edit(commit = true) { putString(NOTES_DELETED, idsAdapter.toJson(all)) }
    }

    /** The categories the user created, kept even while no entry carries them. */
    fun savedCategories(): List<String> = try {
        prefs.prefsNormal.getString(NOTES_CATEGORIES, null)?.let { namesAdapter.fromJson(it) } ?: emptyList()
    } catch (e: Exception) {
        AppLogger.e("NotesStore", "Could not read note categories", e)
        emptyList()
    }

    fun saveCategories(names: List<String>) {
        prefs.prefsNormal.edit(commit = true) { putString(NOTES_CATEGORIES, namesAdapter.toJson(names)) }
    }

    /** Every category, created or in use, alphabetically, each once (see [NotesCategories]). */
    fun categories(items: List<NoteItem>): List<String> = NotesCategories.all(savedCategories(), items)

    fun addCategory(name: String) = saveCategories(NotesCategories.add(savedCategories(), name))

    /** Renames [old] on the list and on every entry; false when [new] is blank or taken. */
    fun renameCategory(old: String, new: String): Boolean {
        val items = load()
        if (!NotesCategories.canRename(categories(items), old, new)) return false
        val (names, entries) = NotesCategories.rename(savedCategories(), items, old, new)
        save(entries)
        saveCategories(names)
        return true
    }

    /** Removes [name]; its entries stay, without a category. */
    fun removeCategory(name: String) {
        val (names, entries) = NotesCategories.remove(savedCategories(), load(), name)
        save(entries)
        saveCategories(names)
    }

    /**
     * The old notes kept text, a formatted date, and the category and priority as translated
     * words. They become notes: "None" in any form drops the category, the highest priority becomes
     * "important", the date is parsed where it can be. The old key stays (migration rule: move,
     * never delete).
     */
    private fun migrateOldMessages(): List<NoteItem> {
        val old = try {
            prefs.loadMessages()
        } catch (_: Exception) {
            emptyList()
        }
        val items = fromOldMessages(old)
        save(items)
        if (items.isNotEmpty()) AppLogger.i("NotesStore", "Moved ${items.size} old notes")
        return items
    }

    /** The conversion of [migrateOldMessages], also used for the notes in a backup from before 1.2.0. */
    fun fromOldMessages(old: List<Message>): List<NoteItem> {
        val now = System.currentTimeMillis()
        val none = setOf("None", getLocalizedStringArray(R.array.categories).firstOrNull().orEmpty())
        val high = setOf("High", getLocalizedStringArray(R.array.priorities).lastOrNull().orEmpty())
        return old.mapIndexed { i, m ->
            val created = parseOldTimestamp(m.timestamp) ?: (now - i * 60_000L)
            NoteItem(
                id = now - i,
                text = m.text,
                created = created,
                edited = created,
                important = m.priority in high,
                category = if (m.category in none) "" else m.category.trim(),
            )
        }
    }

    companion object {
        private const val MAX_DELETED = 2000
    }

    /** Old stamps look like "Mon, 26 Oct - 09:40" in the app language; without a year, this year is assumed. */
    private fun parseOldTimestamp(stamp: String): Long? {
        val (date, time) = stamp.split(" - ").let { if (it.size == 2) it[0] to it[1] else return null }
        val year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val patterns = listOf("EEE, dd MMM HH:mm", "EEE dd MMM HH:mm", "EEE, MMM dd HH:mm", "EEE dd MMM h:mm a", "EEE, MMM dd h:mm a")
        for (locale in listOf(Locale.getDefault(), Locale.ENGLISH)) for (p in patterns) {
            try {
                val parsed = SimpleDateFormat("$p yyyy", locale).apply { isLenient = true }.parse("$date $time $year")
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return null
    }
}
