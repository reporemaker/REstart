package ch.remaker.restart.data.backup

import ch.remaker.restart.data.APP_VERSION
import ch.remaker.restart.data.GESTURES_MERGED
import ch.remaker.restart.data.NOTES_ITEMS
import ch.remaker.restart.data.NOTES_MESSAGES
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.Base64

/** A shortcut pinned to the launcher: what is needed to pin it again or to wait for it. */
data class ShortcutEntry(val packageName: String, val id: String, val label: String, val userSerial: Long)

/** A widget by its provider and place on the grid; widget ids are only valid on the phone that made them. */
data class WidgetEntry(
    val provider: String,
    val label: String,
    val col: Int,
    val row: Int,
    val cellsW: Int,
    val cellsH: Int,
    val userSerial: Long,
)

/**
 * What a backup holds. [prefs] keeps each setting with its own type (Int, Long, Float, Boolean,
 * String, Set<String>), per section. Only the [sections] listed were backed up; a section can be
 * present and empty (no notes yet).
 */
data class BackupContent(
    val versionCode: Int,
    val versionName: String,
    val created: Long,
    val sections: Set<BackupSection>,
    val prefs: Map<BackupSection, Map<String, Any>>,
    val shortcuts: List<ShortcutEntry> = emptyList(),
    val widgets: List<WidgetEntry> = emptyList(),
    val font: ByteArray? = null,
    /** A file from before 1.3.0 (one flat list of settings, no password). */
    val legacy: Boolean = false,
    /** Notes of a file from before 1.2.0, in the old form, converted when restored. */
    val legacyNotesMessages: String? = null,
    /** A file from before 1.2.0 lacks the merged-gestures mark, so the merge has to run again. */
    val needsGestureMerge: Boolean = false,
) {
    fun prefsOf(section: BackupSection): Map<String, Any> = prefs[section].orEmpty()
}

/**
 * The backup as JSON (the encrypted part of a file, or an internal copy) and the reading of the
 * files of earlier versions, which were one flat JSON object of every setting.
 */
object BackupCodec {

    const val FORMAT = 2

    private val mapAdapter = Moshi.Builder().build()
        .adapter<Map<String, Any?>>(Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))

    fun toJson(content: BackupContent): String {
        val root = linkedMapOf<String, Any?>(
            "format" to FORMAT,
            "versionCode" to content.versionCode,
            "versionName" to content.versionName,
            "created" to content.created.toString(),
            "sections" to content.sections.map { it.name },
            "prefs" to content.prefs.filterKeys { it in content.sections }.entries.associate { (section, values) ->
                section.name to values.toSortedMap().mapValues { (_, v) -> typed(v) }
            },
        )
        if (BackupSection.SHORTCUTS in content.sections) root["shortcuts"] = content.shortcuts.map {
            mapOf("package" to it.packageName, "id" to it.id, "label" to it.label, "user" to it.userSerial.toString())
        }
        if (BackupSection.WIDGETS in content.sections) root["widgets"] = content.widgets.map {
            mapOf(
                "provider" to it.provider, "label" to it.label, "col" to it.col, "row" to it.row,
                "cellsW" to it.cellsW, "cellsH" to it.cellsH, "user" to it.userSerial.toString(),
            )
        }
        if (BackupSection.FONT in content.sections) content.font?.let { root["font"] = Base64.getEncoder().encodeToString(it) }
        return mapAdapter.toJson(root)
    }

    /** Reads a backup of any version; null when [json] is not one. */
    fun fromJson(json: String): BackupContent? {
        val root = try {
            mapAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        } ?: return null
        return if (root["format"] != null) readCurrent(root) else readLegacy(root)
    }

    private fun readCurrent(root: Map<String, Any?>): BackupContent? {
        if ((root["format"] as? Number)?.toInt() != FORMAT) return null
        val sections = (root["sections"] as? List<*>).orEmpty()
            .mapNotNull { name -> BackupSection.entries.firstOrNull { it.name == name } }.toSet()
        val prefs = (root["prefs"] as? Map<*, *>).orEmpty().entries.mapNotNull { (name, values) ->
            val section = BackupSection.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
            // Only keys that belong to this section are taken, whatever else a file carries
            section to (values as? Map<*, *>).orEmpty().entries.mapNotNull { (key, value) ->
                val k = key as? String ?: return@mapNotNull null
                if (BackupKeys.sectionOf(k) != section) return@mapNotNull null
                untyped(value as? Map<*, *>)?.let { k to it }
            }.toMap()
        }.toMap()
        fun Map<*, *>.int(key: String) = (this[key] as? Number)?.toInt() ?: 0
        fun Map<*, *>.str(key: String) = this[key] as? String ?: ""
        val shortcuts = (root["shortcuts"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>) }.mapNotNull {
            if (it.str("package").isEmpty() || it.str("id").isEmpty()) null
            else ShortcutEntry(it.str("package"), it.str("id"), it.str("label"), it.str("user").toLongOrNull() ?: 0L)
        }
        val widgets = (root["widgets"] as? List<*>).orEmpty().mapNotNull { (it as? Map<*, *>) }.mapNotNull {
            if (it.str("provider").isEmpty()) null
            else WidgetEntry(
                it.str("provider"), it.str("label"), it.int("col"), it.int("row"),
                it.int("cellsW").coerceAtLeast(1), it.int("cellsH").coerceAtLeast(1), it.str("user").toLongOrNull() ?: 0L,
            )
        }
        val font = (root["font"] as? String)?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
        return BackupContent(
            versionCode = (root["versionCode"] as? Number)?.toInt() ?: 0,
            versionName = root["versionName"] as? String ?: "",
            created = (root["created"] as? String)?.toLongOrNull() ?: 0L,
            sections = sections,
            prefs = prefs,
            shortcuts = shortcuts,
            widgets = widgets,
            font = font,
        )
    }

    /**
     * A file of RE*start 1.0 to 1.2 (and Panther): every setting in one object, numbers without a
     * type. The settings are sorted into sections; the old version number in it is ignored.
     */
    private fun readLegacy(root: Map<String, Any?>): BackupContent? {
        if (root.isEmpty()) return null
        val prefs = mutableMapOf<BackupSection, MutableMap<String, Any>>()
        root.forEach { (key, value) ->
            val section = BackupKeys.sectionOf(key) ?: return@forEach
            val v: Any = when (value) {
                is String -> value
                is Boolean -> value
                // JSON has one number type: a Float setting cannot be told from an Int by its value
                is Double -> if (key in BackupKeys.FLOAT_KEYS || value % 1 != 0.0) value.toFloat() else value.toInt()
                is List<*> -> value.filterIsInstance<String>().toSet()
                else -> return@forEach
            }
            prefs.getOrPut(section) { mutableMapOf() }[key] = v
        }
        val shortcutKeys = prefs[BackupSection.SHORTCUTS].orEmpty().isNotEmpty() ||
            prefs[BackupSection.DRAWER].orEmpty().filterKeys { it in BackupKeys.APP_SETS }.values
                .any { set -> (set as? Set<*>)?.any { BackupKeys.isShortcutEntry(it.toString()) } == true }
        val notes = root[NOTES_MESSAGES] as? String
        val sections = buildSet {
            add(BackupSection.SETTINGS); add(BackupSection.HOME); add(BackupSection.DRAWER)
            if (prefs.containsKey(BackupSection.NOTES) || notes != null) add(BackupSection.NOTES)
            if (shortcutKeys) add(BackupSection.SHORTCUTS)
        }
        return BackupContent(
            versionCode = (root[APP_VERSION] as? Number)?.toInt() ?: 0,
            versionName = "",
            created = 0L,
            sections = sections,
            prefs = prefs,
            legacy = true,
            legacyNotesMessages = notes.takeIf { !prefs[BackupSection.NOTES].orEmpty().containsKey(NOTES_ITEMS) },
            needsGestureMerge = !root.containsKey(GESTURES_MERGED),
        )
    }

    /** A setting with its type, so 1.0f does not come back as the Int 1. Longs travel as text. */
    private fun typed(value: Any): Map<String, Any> = when (value) {
        is Boolean -> mapOf("t" to "b", "v" to value)
        is Int -> mapOf("t" to "i", "v" to value)
        is Long -> mapOf("t" to "l", "v" to value.toString())
        is Float -> mapOf("t" to "f", "v" to value.toDouble())
        is Set<*> -> mapOf("t" to "set", "v" to value.map { it.toString() }.sorted())
        else -> mapOf("t" to "s", "v" to value.toString())
    }

    private fun untyped(entry: Map<*, *>?): Any? {
        val v = entry?.get("v") ?: return null
        return when (entry["t"]) {
            "b" -> v as? Boolean
            "i" -> (v as? Number)?.toInt()
            "l" -> (v as? String)?.toLongOrNull()
            "f" -> (v as? Number)?.toFloat()
            "s" -> v as? String
            "set" -> (v as? List<*>)?.filterIsInstance<String>()?.toSet()
            else -> null
        }
    }
}
