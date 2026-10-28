package ch.remaker.restart.data.backup

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.core.content.edit
import ch.remaker.common.AppLogger
import ch.remaker.restart.data.PENDING_SHORTCUTS
import ch.remaker.restart.data.PENDING_WIDGETS
import ch.remaker.restart.data.Prefs
import ch.remaker.restart.data.SHORTCUT_PREFIX
import ch.remaker.restart.data.settingsKeyOf
import ch.remaker.restart.helper.ShortcutHelper
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

private val listAdapter = Moshi.Builder().build()
    .adapter<List<Map<String, Any?>>>(
        Types.newParameterizedType(List::class.java, Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java))
    )

private fun readList(prefs: Prefs, key: String): List<Map<String, Any?>> = try {
    prefs.prefsNormal.getString(key, null)?.let { listAdapter.fromJson(it) }.orEmpty()
} catch (e: Exception) {
    AppLogger.e("Pending", "Could not read $key", e)
    emptyList()
}

private fun writeList(prefs: Prefs, key: String, list: List<Map<String, Any?>>) {
    prefs.prefsNormal.edit(commit = true) { if (list.isEmpty()) remove(key) else putString(key, listAdapter.toJson(list)) }
}

/** The user a serial number from a backup stands for here; the main profile when it is 0 or unknown there. */
internal fun userForSerial(context: Context, serial: Long): UserHandle? {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    return userManager.getUserForSerialNumber(serial) ?: if (serial == 0L) Process.myUserHandle() else null
}

internal fun serialOf(context: Context, user: UserHandle): Long =
    (context.getSystemService(Context.USER_SERVICE) as UserManager).getSerialNumberForUser(user)

/**
 * Shortcuts from a backup that are not pinned (yet). Their settings stay (name, category, lock,
 * place, gesture) and are not pruned while they wait; each start of the app list tries to pin
 * them again, which only the default launcher can do and only for shortcuts the app still offers.
 * The rest wait until the user adds the same shortcut again or forgets it.
 */
object PendingShortcuts {

    fun list(prefs: Prefs): List<ShortcutEntry> = readList(prefs, PENDING_SHORTCUTS).mapNotNull {
        val pkg = it["package"] as? String ?: return@mapNotNull null
        val id = it["id"] as? String ?: return@mapNotNull null
        ShortcutEntry(pkg, id, it["label"] as? String ?: id, (it["user"] as? String)?.toLongOrNull() ?: 0L)
    }

    private fun save(prefs: Prefs, entries: List<ShortcutEntry>) = writeList(prefs, PENDING_SHORTCUTS, entries.distinctBy { keyOf(it) }.map {
        mapOf("package" to it.packageName, "id" to it.id, "label" to it.label, "user" to it.userSerial.toString())
    })

    fun keyOf(entry: ShortcutEntry): String = settingsKeyOf(entry.packageName, SHORTCUT_PREFIX + entry.id)

    /** Settings keys of the waiting shortcuts; their settings must not be pruned. */
    fun keys(prefs: Prefs): Set<String> = list(prefs).map { keyOf(it) }.toSet()

    fun add(prefs: Prefs, entries: List<ShortcutEntry>) = save(prefs, list(prefs) + entries)

    fun remove(prefs: Prefs, settingsKey: String) = save(prefs, list(prefs).filter { keyOf(it) != settingsKey })

    /** Pins what can be pinned now and keeps the rest waiting; true when one came back. */
    fun retry(context: Context): Boolean {
        val prefs = Prefs(context)
        val waiting = list(prefs)
        if (waiting.isEmpty() || !ShortcutHelper.hasPermission(context)) return false
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val back = mutableSetOf<String>()
        waiting.groupBy { it.packageName to it.userSerial }.forEach { (key, entries) ->
            val (pkg, serial) = key
            val user = userForSerial(context, serial) ?: return@forEach
            fun pinned() = ShortcutHelper.queryPinnedShortcuts(context, user).orEmpty()
                .filter { it.`package` == pkg }.map { it.id }.toSet()
            val already = pinned()
            // pinShortcuts replaces the package's pinned set, so the ones already there go in too
            runCatching { launcherApps.pinShortcuts(pkg, (already + entries.map { it.id }).toList(), user) }
                .onFailure { AppLogger.w("PendingShortcuts", "Could not pin shortcuts of $pkg: ${it.message}") }
            val now = pinned()
            entries.filter { it.id in now }.forEach { back.add(keyOf(it)) }
        }
        if (back.isEmpty()) return false
        save(prefs, waiting.filter { keyOf(it) !in back })
        AppLogger.i("PendingShortcuts", "Pinned again: ${back.size}")
        return true
    }
}

/** Widgets from a backup, each waiting on the widget page to be brought back with the user's consent. */
object PendingWidgets {

    fun list(prefs: Prefs): List<WidgetEntry> = readList(prefs, PENDING_WIDGETS).mapNotNull {
        val provider = it["provider"] as? String ?: return@mapNotNull null
        fun int(name: String) = (it[name] as? Number)?.toInt() ?: 0
        WidgetEntry(
            provider, it["label"] as? String ?: "", int("col"), int("row"),
            int("cellsW").coerceAtLeast(1), int("cellsH").coerceAtLeast(1), (it["user"] as? String)?.toLongOrNull() ?: 0L,
        )
    }

    fun save(prefs: Prefs, entries: List<WidgetEntry>) = writeList(prefs, PENDING_WIDGETS, entries.map {
        mapOf(
            "provider" to it.provider, "label" to it.label, "col" to it.col, "row" to it.row,
            "cellsW" to it.cellsW, "cellsH" to it.cellsH, "user" to it.userSerial.toString(),
        )
    })

    fun remove(prefs: Prefs, entry: WidgetEntry) = save(prefs, list(prefs).filter { it != entry })
}
