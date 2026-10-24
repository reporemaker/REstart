package ch.remaker.restart.helper.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ch.remaker.common.getLocalizedString
import ch.remaker.common.showLongToast
import ch.remaker.restart.R
import ch.remaker.restart.helper.isREstartAppDefault

class PrivateSpaceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_USER_UNLOCKED) {
            // Check if RE*start is the default launcher
            if (isREstartAppDefault(context)) {
                // Notify the user that RE*start is now accessible in the private space
                val message = getLocalizedString(R.string.toast_private_space_unlocked)
                context.showLongToast(message)
            }
        }
    }
}