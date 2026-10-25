package ch.remaker.restart

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ch.remaker.common.getLocalizedString
import ch.remaker.restart.helper.emptyString
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ch.remaker.restart.helper.FontManager

/**
 * Shown after a crash. Nothing is sent anywhere: the dialog names the contact address as plain
 * text and the log file, which stays in the app's private storage.
 */
class CrashReportActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appName = getLocalizedString(R.string.app_name)
        val logName = intent.getStringExtra("crash_log_name") ?: emptyString()
        val message = getLocalizedString(R.string.acra_dialog_text, appName) + "\n\n" +
            getLocalizedString(R.string.acra_report_hint, getLocalizedString(R.string.acra_email), logName)

        MaterialAlertDialogBuilder(this)
            .setTitle(getLocalizedString(R.string.acra_crash))
            .setMessage(message)
            .setPositiveButton(getLocalizedString(R.string.acra_restart)) { _, _ -> restartApp() }
            .setCancelable(false)
            .show()
            .also { FontManager.applyToDialog(it) }
    }

    private fun restartApp() {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        finish()
    }
}
