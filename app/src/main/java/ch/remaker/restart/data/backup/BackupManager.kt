package ch.remaker.restart.data.backup

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.UserManager
import androidx.core.content.edit
import ch.remaker.common.AppLogger
import ch.remaker.restart.BuildConfig
import ch.remaker.restart.data.FIRST_SETTINGS_OPEN
import ch.remaker.restart.data.GESTURES_MERGED
import ch.remaker.restart.data.LAUNCHER_FONT
import ch.remaker.restart.data.NOTES_CATEGORIES
import ch.remaker.restart.data.NOTES_DELETED
import ch.remaker.restart.data.NOTES_ITEMS
import ch.remaker.restart.data.NoteItem
import ch.remaker.restart.data.NotesStore
import ch.remaker.restart.data.Prefs
import ch.remaker.restart.data.RESTORE_SUMMARY
import ch.remaker.restart.data.SettingsSnapshots
import ch.remaker.restart.data.database.WidgetDatabase
import ch.remaker.restart.helper.ShortcutHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.runBlocking
import java.io.File

/**
 * Builds a backup from this phone and puts one back. Both run off the main thread (the widget
 * database, the font file). Restoring changes only the sections chosen, never the state of this
 * install (version number, first-run marks), and keeps a copy of everything first.
 */
object BackupManager {

    data class Result(val pendingShortcuts: Int, val pendingWidgets: Int)

    private val moshi = Moshi.Builder().build()
    private val notesAdapter = moshi.adapter<List<NoteItem>>(Types.newParameterizedType(List::class.java, NoteItem::class.java))
    private val namesAdapter = moshi.adapter<List<String>>(Types.newParameterizedType(List::class.java, String::class.java))
    private val idsAdapter = moshi.adapter<List<Long>>(Types.newParameterizedType(List::class.java, java.lang.Long::class.java))

    private fun fontFile(context: Context) = File(context.filesDir, "CustomFont.ttf")

    fun collect(context: Context, sections: Set<BackupSection>): BackupContent {
        val prefs = Prefs(context)
        val values = mutableMapOf<BackupSection, MutableMap<String, Any>>()
        prefs.prefsNormal.all.forEach { (key, value) ->
            val section = BackupKeys.sectionOf(key) ?: return@forEach
            if (section !in sections || value == null) return@forEach
            values.getOrPut(section) { mutableMapOf() }[key] = value
        }
        // Lock and hide marks of shortcuts travel with the drawer lists only when shortcuts are in too
        if (BackupSection.SHORTCUTS !in sections) values[BackupSection.DRAWER]?.let { drawer ->
            BackupKeys.APP_SETS.forEach { key ->
                (drawer[key] as? Set<*>)?.let { set -> drawer[key] = set.map { it.toString() }.filterNot(BackupKeys::isShortcutEntry).toSet() }
            }
        }
        val shortcuts = if (BackupSection.SHORTCUTS in sections) pinnedShortcuts(context) + PendingShortcuts.list(prefs) else emptyList()
        val widgets = if (BackupSection.WIDGETS in sections) placedWidgets(context) + PendingWidgets.list(prefs) else emptyList()
        val font = if (BackupSection.FONT in sections && prefs.prefsNormal.getString(LAUNCHER_FONT, null) == "Custom")
            fontFile(context).takeIf { it.exists() }?.readBytes() else null
        return BackupContent(
            versionCode = BuildConfig.VERSION_CODE,
            versionName = BuildConfig.VERSION_NAME,
            created = System.currentTimeMillis(),
            sections = if (font == null) sections - BackupSection.FONT else sections,
            prefs = values,
            shortcuts = shortcuts.distinctBy { PendingShortcuts.keyOf(it) },
            widgets = widgets,
            font = font,
        )
    }

    private fun pinnedShortcuts(context: Context): List<ShortcutEntry> {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        return userManager.userProfiles.flatMap { user ->
            ShortcutHelper.queryPinnedShortcuts(context, user).orEmpty().map {
                ShortcutEntry(it.`package`, it.id, (it.shortLabel ?: it.longLabel ?: it.id).toString(), serialOf(context, user))
            }
        }
    }

    private fun placedWidgets(context: Context): List<WidgetEntry> {
        val manager = AppWidgetManager.getInstance(context)
        val saved = runBlocking { WidgetDatabase.getDatabase(context).widgetDao().getAll() }
        return saved.mapNotNull { widget ->
            val info = runCatching { manager.getAppWidgetInfo(widget.appWidgetId) }.getOrNull() ?: return@mapNotNull null
            WidgetEntry(
                info.provider.flattenToString(), info.loadLabel(context.packageManager).toString(),
                widget.col, widget.row, widget.cellsW, widget.cellsH, serialOf(context, info.profile),
            )
        }
    }

    /**
     * Puts the chosen [sections] of [content] back. Settings of a section are replaced; notes are
     * merged unless [replaceNotes]; shortcuts and widgets that cannot come back at once wait.
     */
    fun apply(context: Context, content: BackupContent, sections: Set<BackupSection>, replaceNotes: Boolean, keepCopy: Boolean = true): Result {
        val chosen = sections intersect content.sections
        val prefs = Prefs(context)
        // A way back: the state before this restore, among the copies taken before updates
        if (keepCopy) SettingsSnapshots.save(context, BuildConfig.VERSION_CODE)

        val current = prefs.prefsNormal.all
        val prefSections = chosen intersect setOf(BackupSection.SETTINGS, BackupSection.HOME, BackupSection.DRAWER, BackupSection.SHORTCUTS)
        prefs.prefsNormal.edit(commit = true) {
            current.keys.filter { BackupKeys.sectionOf(it) in prefSections && it !in BackupKeys.APP_SETS }.forEach { remove(it) }
            prefSections.forEach { section ->
                content.prefsOf(section).forEach { (key, value) -> if (key !in BackupKeys.APP_SETS) put(this, key, value) }
            }
            // The app lists hold apps (drawer) and shortcuts; each part comes from where it was chosen
            if (BackupSection.DRAWER in chosen || BackupSection.SHORTCUTS in chosen) BackupKeys.APP_SETS.forEach { key ->
                val phone = (current[key] as? Set<*>).orEmpty().map { it.toString() }
                val backup = (content.prefsOf(BackupSection.DRAWER)[key] as? Set<*>)?.map { it.toString() }
                val apps = if (BackupSection.DRAWER in chosen && backup != null) backup.filterNot(BackupKeys::isShortcutEntry)
                else phone.filterNot(BackupKeys::isShortcutEntry)
                val shortcuts = if (BackupSection.SHORTCUTS in chosen && backup != null) backup.filter(BackupKeys::isShortcutEntry)
                else phone.filter(BackupKeys::isShortcutEntry)
                putStringSet(key, (apps + shortcuts).toSet())
            }
            // A file from before 1.2.0 holds long swipes that still have to be merged into the swipes
            if (BackupSection.HOME in chosen && content.needsGestureMerge) putBoolean(GESTURES_MERGED, false)
        }

        if (BackupSection.NOTES in chosen) restoreNotes(prefs, content, replaceNotes)

        var waitingShortcuts = 0
        if (BackupSection.SHORTCUTS in chosen) {
            val pinned = pinnedShortcuts(context).map { PendingShortcuts.keyOf(it) }.toSet()
            PendingShortcuts.add(prefs, content.shortcuts.filter { PendingShortcuts.keyOf(it) !in pinned })
            PendingShortcuts.retry(context)
            waitingShortcuts = PendingShortcuts.list(prefs).size
        }

        if (BackupSection.WIDGETS in chosen) PendingWidgets.save(prefs, (PendingWidgets.list(prefs) + content.widgets).distinct())

        if (BackupSection.FONT in chosen) content.font?.let { fontFile(context).writeBytes(it) }

        prefs.prefsNormal.edit(commit = true) {
            putBoolean(RESTORE_SUMMARY, true)
            // Whoever restores a backup is no new user: the first-run tips stay away
            putBoolean(FIRST_SETTINGS_OPEN, false)
        }
        AppLogger.i("BackupManager", "Restored ${chosen.joinToString()}")
        return Result(waitingShortcuts, PendingWidgets.list(prefs).size)
    }

    private fun restoreNotes(prefs: Prefs, content: BackupContent, replace: Boolean) {
        val store = NotesStore(prefs)
        val notes = content.prefsOf(BackupSection.NOTES)
        val backupItems = (notes[NOTES_ITEMS] as? String)?.let { runCatching { notesAdapter.fromJson(it) }.getOrNull() }
            ?: content.legacyNotesMessages?.let { json ->
                runCatching { prefs.messageAdapter.fromJson(json) }.getOrNull()?.let { store.fromOldMessages(it) }
            }
            ?: emptyList()
        val backupCategories = (notes[NOTES_CATEGORIES] as? String)?.let { runCatching { namesAdapter.fromJson(it) }.getOrNull() }.orEmpty()
        val backupDeleted = (notes[NOTES_DELETED] as? String)?.let { runCatching { idsAdapter.fromJson(it) }.getOrNull() }.orEmpty().toSet()
        val result = if (replace) NotesMerge.replace(backupItems, backupDeleted, backupCategories)
        else NotesMerge.merge(store.load(), store.deletedIds(), store.savedCategories(), backupItems, backupDeleted, backupCategories)
        store.save(result.items)
        store.saveCategories(result.categories)
        prefs.prefsNormal.edit(commit = true) { putString(NOTES_DELETED, idsAdapter.toJson(result.deleted.toList())) }
    }

    private fun put(editor: android.content.SharedPreferences.Editor, key: String, value: Any) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is String -> editor.putString(key, value)
            is Set<*> -> editor.putStringSet(key, value.map { it.toString() }.toSet())
        }
    }
}
