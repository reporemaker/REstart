package ch.remaker.restart.ui.widgets.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import ch.remaker.common.AppLogger
import ch.remaker.common.getLocalizedString
import ch.remaker.restart.R
import ch.remaker.restart.data.Prefs
import ch.remaker.restart.helper.ShortcutHelper
import ch.remaker.restart.helper.utils.AppReloader

class HomeAppUpdateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        AppLogger.d("HomeAppUpdateReceiver", "Received intent: action=${intent.action}, extras=${intent.extras}")

        if (intent.action == "HOME_APP_CLICK") {
            val extras = intent.extras
            if (extras == null) {
                AppLogger.d("HomeAppUpdateReceiver", "No extras found in intent")
                return
            }
            val packageName = extras.getString("PACKAGE_NAME")
            AppLogger.d("HomeAppUpdateReceiver", "PACKAGE_NAME extra: $packageName")

            if (packageName.isNullOrEmpty()) {
                AppLogger.d("HomeAppUpdateReceiver", "No package name provided, aborting launch")
                return
            }

            // Shortcuts share their creator's package, so start the shortcut itself
            val prefs = Prefs(context)
            val homeApp = prefs.getHomeAppModel(extras.getInt("HOME_SLOT", -1))
            if (homeApp.isShortcut && homeApp.activityPackage == packageName) {
                // A locked shortcut needs authentication, which only the launcher can ask for
                if (homeApp.settingsKey in prefs.lockedApps) {
                    AppReloader.startApp(context)
                    return
                }
                if (!ShortcutHelper.startShortcut(context, homeApp)) {
                    Toast.makeText(context, getLocalizedString(R.string.shortcut_launch_failed), Toast.LENGTH_SHORT).show()
                }
                return
            }

            // A locked app needs authentication, which only the launcher can ask for
            if (packageName in prefs.lockedApps) {
                AppReloader.startApp(context)
                return
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                AppLogger.d("HomeAppUpdateReceiver", "Launched app: $packageName")
            } else {
                AppLogger.d("HomeAppUpdateReceiver", "Cannot find app: $packageName")
                Toast.makeText(context, "Cannot find app: $packageName", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
