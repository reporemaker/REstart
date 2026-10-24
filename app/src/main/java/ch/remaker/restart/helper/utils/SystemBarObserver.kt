package ch.remaker.restart.helper.utils

import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ch.remaker.restart.data.Prefs
import ch.remaker.restart.helper.hideNavigationBar
import ch.remaker.restart.helper.hideStatusBar
import ch.remaker.restart.helper.showNavigationBar
import ch.remaker.restart.helper.showStatusBar

class SystemBarObserver(private val prefs: Prefs) : DefaultLifecycleObserver {

    override fun onResume(owner: LifecycleOwner) {
        val activity = owner as? AppCompatActivity ?: return
        val window = activity.window
        if (prefs.showStatusBar) showStatusBar(window) else hideStatusBar(window)
        if (prefs.showNavigationBar) showNavigationBar(window) else hideNavigationBar(window)
    }
}
