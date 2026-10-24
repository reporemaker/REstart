package ch.remaker.restart.helper.receivers

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import ch.remaker.common.AppLogger
import ch.remaker.common.showShortToast

class DeviceAdmin : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        Handler(Looper.getMainLooper()).post {
            AppLogger.d("DeviceAdminReceiver", "Device Admin Enabled")
            context.showShortToast("Device Admin Enabled")
        }
    }

    override fun onDisabled(context: Context, intent: Intent) {
        Handler(Looper.getMainLooper()).post {
            AppLogger.d("DeviceAdminReceiver", "Device Admin Disabled")
            context.showShortToast("Device Admin Disabled")
        }
    }
}