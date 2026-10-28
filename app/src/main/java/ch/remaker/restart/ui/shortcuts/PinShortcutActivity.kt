package ch.remaker.restart.ui.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import ch.remaker.common.AppLogger
import ch.remaker.common.getLocalizedString
import ch.remaker.common.showShortToast
import ch.remaker.restart.R
import ch.remaker.restart.data.Prefs
import ch.remaker.restart.data.SHORTCUT_PREFIX
import ch.remaker.restart.data.backup.PendingShortcuts
import ch.remaker.restart.data.settingsKeyOf
import ch.remaker.restart.helper.ShortcutHelper
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ch.remaker.restart.helper.FontManager

/**
 * Receives "Add to home screen" requests (ACTION_CONFIRM_PIN_SHORTCUT),
 * e.g. from a browser, and lets the user pick the name before adding it.
 */
class PinShortcutActivity : AppCompatActivity() {

    private var dialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val request = runCatching { launcherApps.getPinItemRequest(intent) }.getOrNull()
        val shortcut = request?.shortcutInfo

        if (request == null || !request.isValid || request.requestType != LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT || shortcut == null) {
            AppLogger.w(TAG, "Ignoring invalid pin request")
            finish()
            return
        }

        val defaultLabel = (shortcut.shortLabel ?: shortcut.longLabel ?: shortcut.id).toString()

        val nameInput = EditText(this).apply {
            setText(defaultLabel)
            setSelection(text.length)
            hint = getLocalizedString(R.string.shortcut_name)
            isSingleLine = true
            typeface = FontManager.getTypeface(this@PinShortcutActivity)
        }
        val container = FrameLayout(this).apply {
            setPadding(50, 20, 50, 0)
            addView(nameInput)
        }

        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getLocalizedString(R.string.shortcut_add_title))
            .setMessage(ShortcutHelper.describe(this, shortcut.`package`))
            .setView(container)
            .setPositiveButton(getLocalizedString(R.string.shortcut_add)) { _, _ ->
                if (request.isValid && request.accept()) {
                    val name = nameInput.text.toString().trim()
                    val key = settingsKeyOf(shortcut.`package`, SHORTCUT_PREFIX + shortcut.id)
                    if (name.isNotEmpty() && name != defaultLabel) {
                        Prefs(this).setAppAlias(key, name)
                    }
                    showShortToast(getLocalizedString(R.string.shortcut_added))
                    if (matchWaiting(shortcut.`package`, key)) return@setPositiveButton
                } else {
                    AppLogger.e(TAG, "Pin request could not be accepted")
                }
                finish()
            }
            .setNegativeButton(getLocalizedString(R.string.cancel)) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
            .also { FontManager.applyToDialog(it) }
    }

    /**
     * A shortcut from a restored backup added again: under the same id its settings are already in
     * place; under a new id the user says which waiting shortcut it is, and that one's settings
     * (name, category, lock, place, gesture) move over. True while that question is open.
     */
    private fun matchWaiting(packageName: String, key: String): Boolean {
        val prefs = Prefs(this)
        val waiting = PendingShortcuts.list(prefs)
        if (waiting.any { PendingShortcuts.keyOf(it) == key }) {
            PendingShortcuts.remove(prefs, key)
            return false
        }
        val candidates = waiting.filter { it.packageName == packageName }
        if (candidates.isEmpty()) return false
        val labels = candidates.map { it.label } + getLocalizedString(R.string.pending_shortcut_match_none)
        dialog = MaterialAlertDialogBuilder(this)
            .setTitle(getLocalizedString(R.string.pending_shortcut_match_title))
            .setItems(labels.toTypedArray()) { _, index ->
                candidates.getOrNull(index)?.let { entry ->
                    val oldKey = PendingShortcuts.keyOf(entry)
                    prefs.moveShortcutSettings(oldKey, key)
                    PendingShortcuts.remove(prefs, oldKey)
                }
                finish()
            }
            .setOnCancelListener { finish() }
            .show()
            .also { FontManager.applyToDialog(it) }
        return true
    }

    override fun onDestroy() {
        // Avoid leaking the window when the activity is recreated, e.g. on rotation
        dialog?.dismiss()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PinShortcutActivity"
    }
}
