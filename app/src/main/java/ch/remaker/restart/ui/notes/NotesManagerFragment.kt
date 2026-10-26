package ch.remaker.restart.ui.notes

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigation.fragment.findNavController
import ch.remaker.common.CrashHandler
import ch.remaker.common.getLocalizedString
import ch.remaker.restart.BuildConfig
import ch.remaker.restart.R
import ch.remaker.restart.data.Constants
import ch.remaker.restart.data.NotesStore
import ch.remaker.restart.data.Prefs
import ch.remaker.restart.helper.isSystemInDarkMode
import ch.remaker.restart.style.SettingsTheme
import ch.remaker.restart.ui.BaseFragment
import ch.remaker.restart.ui.components.DialogManager

/** The notes page (opened from the RE* mark by default): notes and to-dos, see [NotesScreen]. */
class NotesManagerFragment : BaseFragment() {

    private lateinit var state: NotesState

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val prefs = Prefs(requireContext())
        state = NotesState(NotesStore(prefs))
        val dialogs = DialogManager(requireContext(), requireActivity())
        val isDark = when (prefs.appTheme) {
            Constants.Theme.Light -> false
            Constants.Theme.Dark -> true
            Constants.Theme.System -> isSystemInDarkMode(requireContext())
        }
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                SettingsTheme(isDark) {
                    NotesScreen(state, isDark, dialogs, onBack = { findNavController().popBackStack() }, onShare = ::share)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Back closes an open entry first (it is saved on the way), then leaves the page
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (state.editingId != null) {
                    state.closeEditor()
                } else {
                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun share(text: String) {
        CrashHandler.logUserAction("Notes share")
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        startActivity(Intent.createChooser(send, getLocalizedString(R.string.share_note)))
    }

    // Notes are kept out of screenshots and the recent-apps preview while they are on screen
    // (debug builds excepted, so the page can be checked on an emulator)
    override fun onResume() {
        super.onResume()
        if (!BuildConfig.DEBUG) requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onPause() {
        state.flush()
        requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onPause()
    }
}
