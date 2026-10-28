package ch.remaker.restart.ui.backup

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import ch.remaker.common.AppLogger
import ch.remaker.common.getLocalizedString
import ch.remaker.common.showLongToast
import ch.remaker.restart.MainActivity
import ch.remaker.restart.R
import ch.remaker.restart.data.Prefs
import ch.remaker.restart.data.SettingsSnapshots
import ch.remaker.restart.data.backup.AutoBackup
import ch.remaker.restart.data.backup.BackupCodec
import ch.remaker.restart.data.backup.BackupContent
import ch.remaker.restart.data.backup.BackupCrypto
import ch.remaker.restart.data.backup.BackupManager
import ch.remaker.restart.data.backup.BackupSection
import ch.remaker.restart.data.backup.PendingShortcuts
import ch.remaker.restart.helper.utils.AppReloader
import ch.remaker.restart.ui.components.DialogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings > Advanced > Backup/Restore: making a password-protected backup, restoring one (or a
 * file from an earlier version), the automatic backup, the shortcuts waiting after a restore and
 * the copies taken before updates. Created in the activity's onCreate (it registers the pickers).
 */
class BackupFlow(private val activity: MainActivity) {

    private val dialogs get() = DialogManager(activity, activity)

    private var toCreate: Pair<Set<BackupSection>, CharArray>? = null

    private val createLauncher = activity.registerForActivityResult(ActivityResultContracts.CreateDocument(MIME)) { uri ->
        val (sections, password) = toCreate ?: return@registerForActivityResult
        toCreate = null
        if (uri == null) password.fill(' ') else write(uri, sections, password)
    }

    private val openLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { open(it) }
    }

    private val folderLauncher = activity.registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { setUpAutoBackup(it) }
    }

    private fun label(section: BackupSection): String = getLocalizedString(
        when (section) {
            BackupSection.SETTINGS -> R.string.backup_section_settings
            BackupSection.HOME -> R.string.backup_section_home
            BackupSection.DRAWER -> R.string.backup_section_drawer
            BackupSection.SHORTCUTS -> R.string.backup_section_shortcuts
            BackupSection.NOTES -> R.string.backup_section_notes
            BackupSection.WIDGETS -> R.string.backup_section_widgets
            BackupSection.FONT -> R.string.backup_section_font
        }
    )

    /** Ticks for [sections], all ticked to start with; [onDone] gets the ticked ones (none: nothing happens). */
    private fun chooseSections(title: String, sections: List<BackupSection>, onDone: (Set<BackupSection>) -> Unit) {
        dialogs.showMultiChoiceDialog(activity, title, sections.map(::label), sections.indices.toSet()) { ticked ->
            if (ticked.isNotEmpty()) onDone(ticked.map { sections[it] }.toSet())
        }
    }

    // ---- Making a backup ----

    fun create() {
        chooseSections(getLocalizedString(R.string.backup_choose_sections), BackupSection.entries) { sections ->
            dialogs.showPasswordDialog(
                getLocalizedString(R.string.backup_password_title),
                getLocalizedString(R.string.backup_password_note), repeat = true
            ) { password ->
                toCreate = sections to password
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                createLauncher.launch("RE-start-backup-$day.restart")
            }
        }
    }

    private fun write(uri: Uri, sections: Set<BackupSection>, password: CharArray) {
        activity.showLongToast(getLocalizedString(R.string.backup_working))
        activity.lifecycleScope.launch {
            val written = withContext(Dispatchers.IO) {
                try {
                    val content = BackupManager.collect(activity.applicationContext, sections)
                    val bytes = BackupCrypto.encrypt(BackupCodec.toJson(content).toByteArray(), password)
                    activity.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                    content
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Could not write the backup", e)
                    null
                } finally {
                    password.fill(' ')
                }
            }
            if (written == null) {
                activity.showLongToast(getLocalizedString(R.string.backup_save_failed))
            } else {
                val parts = BackupSection.entries.filter { it in written.sections }.joinToString(", ", transform = ::label)
                activity.showLongToast(getLocalizedString(R.string.backup_saved, parts))
            }
        }
    }

    // ---- Restoring ----

    fun restore() = openLauncher.launch(arrayOf("*/*"))

    private fun open(uri: Uri) {
        activity.lifecycleScope.launch {
            val bytes = withContext(Dispatchers.IO) {
                runCatching { activity.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            }
            when {
                bytes == null -> activity.showLongToast(getLocalizedString(R.string.backup_read_failed))
                BackupCrypto.isEncrypted(bytes) -> askPassword(bytes)
                else -> BackupCodec.fromJson(String(bytes))?.let { chooseWhatToRestore(it) }
                    ?: activity.showLongToast(getLocalizedString(R.string.backup_read_failed))
            }
        }
    }

    private fun askPassword(bytes: ByteArray) {
        dialogs.showPasswordDialog(
            getLocalizedString(R.string.backup_password_title),
            getLocalizedString(R.string.backup_password_enter), repeat = false
        ) { password ->
            activity.showLongToast(getLocalizedString(R.string.backup_opening))
            activity.lifecycleScope.launch {
                val content = withContext(Dispatchers.IO) {
                    try {
                        BackupCodec.fromJson(String(BackupCrypto.decrypt(bytes, password)))
                    } catch (_: BackupCrypto.WrongPasswordException) {
                        null
                    } finally {
                        password.fill(' ')
                    }
                }
                if (content == null) {
                    activity.showLongToast(getLocalizedString(R.string.backup_password_wrong))
                    askPassword(bytes)
                } else {
                    chooseWhatToRestore(content)
                }
            }
        }
    }

    private fun chooseWhatToRestore(content: BackupContent) {
        val present = BackupSection.entries.filter { it in content.sections }
        chooseSections(getLocalizedString(R.string.backup_restore_choose_sections), present) { sections ->
            if (BackupSection.NOTES in sections) {
                dialogs.chooser(
                    getLocalizedString(R.string.backup_section_notes),
                    listOf(getLocalizedString(R.string.backup_notes_merge), getLocalizedString(R.string.backup_notes_replace))
                ) { choice -> confirmRestore(content, sections, replaceNotes = choice == 1) }
            } else {
                confirmRestore(content, sections, replaceNotes = false)
            }
        }
    }

    private fun confirmRestore(content: BackupContent, sections: Set<BackupSection>, replaceNotes: Boolean) {
        val from = if (content.created > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(content.created)) else null
        val message = listOfNotNull(
            from?.let { getLocalizedString(R.string.backup_made_on, it) },
            getLocalizedString(R.string.backup_old_format).takeIf { content.legacy },
            getLocalizedString(R.string.backup_restore_confirm),
        ).joinToString("\n\n")
        dialogs.confirm(getLocalizedString(R.string.backup_restore), message, getLocalizedString(R.string.backup_restore)) {
            activity.lifecycleScope.launch {
                val done = withContext(Dispatchers.IO) {
                    runCatching { BackupManager.apply(activity.applicationContext, content, sections, replaceNotes) }
                        .onFailure { AppLogger.e(TAG, "Could not restore", it) }.isSuccess
                }
                if (done) AppReloader.restartApp(activity.applicationContext)
                else activity.showLongToast(getLocalizedString(R.string.backup_read_failed))
            }
        }
    }

    // ---- Automatic backup ----

    fun autoBackup() {
        val prefs = Prefs(activity)
        if (!AutoBackup.isOn(prefs)) {
            dialogs.confirm(
                getLocalizedString(R.string.auto_backup_title), getLocalizedString(R.string.auto_backup_explain),
                getLocalizedString(R.string.auto_backup_turn_on)
            ) { folderLauncher.launch(null) }
            return
        }
        dialogs.chooser(
            autoBackupState(),
            listOf(getLocalizedString(R.string.auto_backup_now), getLocalizedString(R.string.auto_backup_turn_off))
        ) { choice ->
            if (choice == 0) runAutoBackupNow()
            else dialogs.confirm(
                getLocalizedString(R.string.auto_backup_title), getLocalizedString(R.string.auto_backup_off_confirm),
                getLocalizedString(R.string.auto_backup_turn_off)
            ) {
                AutoBackup.turnOff(activity.applicationContext)
                activity.showLongToast(getLocalizedString(R.string.auto_backup_is_off))
            }
        }
    }

    /** "Automatic backup: on, Documents", "off" or "stopped". */
    fun autoBackupState(): String {
        val prefs = Prefs(activity)
        return when {
            !AutoBackup.isOn(prefs) -> getLocalizedString(R.string.auto_backup_state_off)
            AutoBackup.failed(prefs) -> getLocalizedString(R.string.auto_backup_state_failed)
            else -> getLocalizedString(R.string.auto_backup_state_on, AutoBackup.folderName(activity).orEmpty())
        }
    }

    private fun runAutoBackupNow() {
        activity.lifecycleScope.launch {
            val written = withContext(Dispatchers.IO) { AutoBackup.run(activity.applicationContext, force = true) }
            activity.showLongToast(getLocalizedString(if (written) R.string.auto_backup_written else R.string.auto_backup_state_failed))
        }
    }

    private fun setUpAutoBackup(folder: Uri) {
        chooseSections(getLocalizedString(R.string.backup_choose_sections), BackupSection.entries) { sections ->
            dialogs.showPasswordDialog(
                getLocalizedString(R.string.backup_password_title),
                getLocalizedString(R.string.auto_backup_password_note), repeat = true
            ) { password ->
                activity.showLongToast(getLocalizedString(R.string.backup_working))
                activity.lifecycleScope.launch {
                    val on = withContext(Dispatchers.IO) {
                        try {
                            AutoBackup.turnOn(activity.applicationContext, folder, sections, password)
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Could not turn on the automatic backup", e)
                            AutoBackup.turnOff(activity.applicationContext)
                            false
                        } finally {
                            password.fill(' ')
                        }
                    }
                    activity.showLongToast(getLocalizedString(if (on) R.string.auto_backup_is_on else R.string.auto_backup_state_failed))
                }
            }
        }
    }

    // ---- Shortcuts waiting after a restore ----

    fun hasPendingShortcuts(): Boolean = PendingShortcuts.list(Prefs(activity)).isNotEmpty()

    fun pendingShortcuts() {
        val prefs = Prefs(activity)
        val waiting = PendingShortcuts.list(prefs)
        if (waiting.isEmpty()) return
        val labels = waiting.map { "${it.label} · ${appLabel(it.packageName)}" }
        dialogs.chooser(getLocalizedString(R.string.pending_shortcuts_title), labels) { index ->
            val entry = waiting[index]
            dialogs.confirm(
                labels[index],
                getLocalizedString(R.string.pending_shortcuts_explain, appLabel(entry.packageName)),
                getLocalizedString(R.string.pending_shortcuts_forget),
                getLocalizedString(R.string.close)
            ) {
                val key = PendingShortcuts.keyOf(entry)
                PendingShortcuts.remove(prefs, key)
                prefs.forgetShortcutSettings(key)
            }
        }
    }

    private fun appLabel(packageName: String): String = runCatching {
        activity.packageManager.getApplicationLabel(activity.packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    // ---- Copies taken before updates and restores ----

    fun hasSnapshots(): Boolean = SettingsSnapshots.list(activity).isNotEmpty()

    fun snapshots() {
        val snapshots = SettingsSnapshots.list(activity)
        if (snapshots.isEmpty()) return
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val labels = snapshots.map {
            getLocalizedString(R.string.advanced_settings_backup_restore_snapshot_item, versionNameOf(it.versionCode), dateFormat.format(it.takenAt))
        }
        dialogs.chooser(getLocalizedString(R.string.backup_snapshots), labels) { index ->
            dialogs.confirm(
                labels[index], getLocalizedString(R.string.advanced_settings_backup_restore_snapshot_confirm),
                getLocalizedString(R.string.backup_restore)
            ) {
                activity.lifecycleScope.launch {
                    val done = withContext(Dispatchers.IO) {
                        runCatching {
                            val content = BackupCodec.fromJson(snapshots[index].file.readText()) ?: error("Unreadable copy")
                            BackupManager.apply(activity.applicationContext, content, content.sections, replaceNotes = true)
                        }.onFailure { AppLogger.e(TAG, "Could not restore ${snapshots[index].file.name}", it) }.isSuccess
                    }
                    if (done) AppReloader.restartApp(activity.applicationContext)
                    else activity.showLongToast(getLocalizedString(R.string.advanced_settings_backup_restore_snapshot_failed))
                }
            }
        }
    }

    /** "1.2.0 Build 1" for a version code of the form MMmmppbb, as the app names its versions. */
    private fun versionNameOf(code: Int): String =
        "${code / 1_000_000}.${code / 10_000 % 100}.${code / 100 % 100} Build ${code % 100}"

    companion object {
        private const val TAG = "BackupFlow"
        private const val MIME = "application/octet-stream"
    }
}
