package ch.remaker.restart.data

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.core.content.res.ResourcesCompat
import ch.remaker.common.AppLogger
import ch.remaker.common.getLocalizedString
import ch.remaker.restart.REstartApp
import ch.remaker.restart.R
import ch.remaker.restart.helper.IconCacheTarget
import ch.remaker.restart.helper.getTrueSystemFont
import java.io.File
import java.util.Locale

interface EnumOption {
    @Composable
    fun string(): String
}


object Constants {
    const val MIN_HOME_APPS = 0
    const val MAX_HOME_APPS = 8

    const val MIN_TEXT_SIZE = 10
    const val MAX_TEXT_SIZE = 100

    const val MIN_CLOCK_DATE_SIZE = 10
    const val MAX_CLOCK_DATE_SIZE = 120

    const val MIN_ALARM_SIZE = 10
    const val MAX_ALARM_SIZE = 120


    const val MIN_BATTERY_SIZE = 10
    const val MAX_BATTERY_SIZE = 75

    const val MIN_CATEGORY_SIZE = 8
    const val MAX_CATEGORY_SIZE = 40

    // Drawer search area, in dp
    const val MIN_SEARCH_SPACING = 0
    const val MAX_SEARCH_SPACING = 64
    const val MIN_SEARCH_HEIGHT = 0
    const val MAX_SEARCH_HEIGHT = 40

    const val MIN_TEXT_PADDING = 0
    const val MAX_TEXT_PADDING = 50

    const val MIN_RECENT_COUNTER = 1
    const val MAX_RECENT_COUNTER = 35

    const val MIN_FILTER_STRENGTH = 0
    const val MAX_FILTER_STRENGTH = 100

    const val MIN_OPACITY = 0
    const val MAX_OPACITY = 100

    const val DOUBLE_CLICK_TIME_DELTA = 300L // Adjust as needed
    const val SWIPE_VELOCITY_THRESHOLD = 450f // Adjust as needed

    // Update SWIPE_DISTANCE_THRESHOLD dynamically based on screen dimensions
    const val MIN_THRESHOLD = 0f
    const val MAX_THRESHOLD = 1f
    var SHORT_SWIPE_THRESHOLD = 0f  // pixels
    var LONG_SWIPE_THRESHOLD = 0f // pixels
    var USR_DPIX = 0f
    var USR_DPIY = 0f

    const val ACCESS_FINE_LOCATION = 666
    const val READ_CONTACTS = 777

    fun updateSwipeDistanceThreshold(context: Context, direction: String) {
        val prefs = Prefs(context)
        val metrics = context.resources.displayMetrics

        USR_DPIX = metrics.xdpi
        USR_DPIY = metrics.ydpi

        val screenWidthInches = metrics.widthPixels / USR_DPIX
        val screenHeightInches = metrics.heightPixels / USR_DPIY

        if (direction.equals("left", true) || direction.equals("right", true)) {
            LONG_SWIPE_THRESHOLD = screenWidthInches * prefs.longSwipeThreshold
            SHORT_SWIPE_THRESHOLD = screenWidthInches * prefs.shortSwipeThreshold
        } else {
            LONG_SWIPE_THRESHOLD = screenHeightInches * prefs.longSwipeThreshold
            SHORT_SWIPE_THRESHOLD = screenHeightInches * prefs.shortSwipeThreshold
        }

        AppLogger.d(
            "GestureThresholds",
            "Updated thresholds for $direction: SHORT = $SHORT_SWIPE_THRESHOLD inches, LONG = $LONG_SWIPE_THRESHOLD inches"
        )
    }

    enum class AppDrawerFlag {
        None,
        LaunchApp,
        HiddenApps,
        PrivateApps,
        SetHomeApp,
        SetShortSwipeUp,
        SetShortSwipeDown,
        SetShortSwipeLeft,
        SetShortSwipeRight,
        SetLongSwipeUp,
        SetLongSwipeDown,
        SetLongSwipeLeft,
        SetLongSwipeRight,
        SetClickClock,
        SetAppUsage,
        SetFloating,
        SetClickDate,
        SetDoubleTap,
    }

    enum class Language : EnumOption {
        System,
        Arabic,
        Czech,
        Dutch,
        Chinese,
        English,
        French,
        German,
        Hebrew,
        Italian,
        Japanese,
        Korean,
        Lithuanian,
        Polish,
        Portuguese,
        Romanian,
        Russian,
        Slovak,
        Spanish,
        Thai,
        Turkish;

        // Function to get a string from a context (for non-Composable use)
        fun getString(): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Arabic -> getLocalizedString(R.string.lang_arabic)
                Czech -> getLocalizedString(R.string.lang_czech)
                Dutch -> getLocalizedString(R.string.lang_dutch)
                Chinese -> getLocalizedString(R.string.lang_chinese)
                English -> getLocalizedString(R.string.lang_english)
                French -> getLocalizedString(R.string.lang_french)
                German -> getLocalizedString(R.string.lang_german)
                Hebrew -> getLocalizedString(R.string.lang_hebrew)
                Italian -> getLocalizedString(R.string.lang_italian)
                Japanese -> getLocalizedString(R.string.lang_japanese)
                Korean -> getLocalizedString(R.string.lang_korean)
                Lithuanian -> getLocalizedString(R.string.lang_lithuanian)
                Polish -> getLocalizedString(R.string.lang_polish)
                Portuguese -> getLocalizedString(R.string.lang_portuguese)
                Romanian -> getLocalizedString(R.string.lang_romanian)
                Russian -> getLocalizedString(R.string.lang_russian)
                Slovak -> getLocalizedString(R.string.lang_slovak)
                Spanish -> getLocalizedString(R.string.lang_spanish)
                Thai -> getLocalizedString(R.string.lang_thai)
                Turkish -> getLocalizedString(R.string.lang_turkish)
            }
        }

        @Composable
        override fun string(): String = getString()


        fun locale(): Locale {
            return Locale.forLanguageTag(zone())
        }

        private fun zone(): String {
            return when (this) {
                System -> Locale.getDefault().toLanguageTag()
                Arabic -> "ar-SA"
                Czech -> "cs-Cz"
                Dutch -> "nl-NL"
                Chinese -> "zh-CN"
                English -> "en-US"
                French -> "fr-FR"
                German -> "de-DE"
                Hebrew -> "he-IL"
                Italian -> "it-IT"
                Japanese -> "ja-JP"
                Korean -> "ko-KR"
                Lithuanian -> "lt-LT"
                Polish -> "pl-PL"
                Portuguese -> "pt-PT"
                Romanian -> "ro-RO"
                Russian -> "ru-RU"
                Slovak -> "sk-SK"
                Spanish -> "es-ES"
                Thai -> "th-TH"
                Turkish -> "tr-TR"
            }
        }
    }

    enum class Gravity : EnumOption {
        Left,
        Center,
        Right;

        @Composable
        override fun string(): String {
            return when (this) {
                Left -> getLocalizedString(R.string.left)
                Center -> getLocalizedString(R.string.center)
                Right -> getLocalizedString(R.string.right)
            }
        }

        @SuppressLint("RtlHardcoded")
        fun value(): Int {
            return when (this) {
                Left -> android.view.Gravity.LEFT
                Center -> android.view.Gravity.CENTER
                Right -> android.view.Gravity.RIGHT
            }
        }
    }

    fun getCustomIconPackName(target: String): String {
        return when (target) {
            IconCacheTarget.APP_LIST.name -> {
                val customPackageName = Prefs(REstartApp.getContext()).customIconPackAppList
                if (customPackageName.isEmpty()) {
                    getLocalizedString(R.string.system_custom)
                }
                try {
                    val pm = REstartApp.getContext().packageManager
                    val appInfo = pm.getApplicationInfo(customPackageName, 0)
                    val customName = pm.getApplicationLabel(appInfo).toString()
                    getLocalizedString(R.string.system_custom_plus, customName)
                } catch (_: NameNotFoundException) {
                    getLocalizedString(R.string.system_custom)
                }
            }

            IconCacheTarget.HOME.name -> {
                val customPackageName = Prefs(REstartApp.getContext()).customIconPackHome
                if (customPackageName.isEmpty()) {
                    getLocalizedString(R.string.system_custom)
                }
                try {
                    val pm = REstartApp.getContext().packageManager
                    val appInfo = pm.getApplicationInfo(customPackageName, 0)
                    val customName = pm.getApplicationLabel(appInfo).toString()
                    getLocalizedString(R.string.system_custom_plus, customName)
                } catch (_: NameNotFoundException) {
                    getLocalizedString(R.string.system_custom)
                }
            }

            else -> getLocalizedString(R.string.system_custom)
        }
    }

    enum class IconPacks : EnumOption {
        System,
        Custom,
        Star,
        Disabled;

        fun getString(target: String): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Custom -> getCustomIconPackName(target)
                Star -> getLocalizedString(R.string.app_icons_star)
                Disabled -> getLocalizedString(R.string.disabled)
            }
        }

        @Composable
        override fun string(): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Custom -> getLocalizedString(R.string.system_custom)
                Star -> getLocalizedString(R.string.app_icons_star)
                Disabled -> getLocalizedString(R.string.disabled)
            }
        }
    }

    enum class Action : EnumOption {
        OpenApp,
        TogglePrivateSpace,
        LockScreen,
        ShowNotification,
        ShowAppList,
        ShowWidgetPage,
        ShowNotesManager,
        ShowDigitalWellbeing,
        OpenQuickSettings,
        ShowRecents,
        OpenPowerDialog,
        TakeScreenShot,
        RestartApp,
        Disabled;

        fun getString(): String {
            return when (this) {
                OpenApp -> getLocalizedString(R.string.open_app)
                LockScreen -> getLocalizedString(R.string.lock_screen)
                TogglePrivateSpace -> getLocalizedString(R.string.private_space, "Toggle")
                ShowNotification -> getLocalizedString(R.string.show_notifications)
                ShowAppList -> getLocalizedString(R.string.show_app_list)
                ShowWidgetPage -> getLocalizedString(R.string.show_widget_page)
                ShowNotesManager -> getLocalizedString(R.string.show_notes_manager)
                ShowDigitalWellbeing -> getLocalizedString(R.string.show_digital_wellbeing)
                OpenQuickSettings -> getLocalizedString(R.string.open_quick_settings)
                ShowRecents -> getLocalizedString(R.string.show_recents)
                OpenPowerDialog -> getLocalizedString(R.string.open_power_dialog)
                TakeScreenShot -> getLocalizedString(R.string.take_a_screenshot)
                RestartApp -> getLocalizedString(R.string.restart_launcher)
                Disabled -> getLocalizedString(R.string.disabled)
            }
        }

        @Composable
        override fun string(): String {
            return when (this) {
                OpenApp -> getLocalizedString(R.string.open_app)
                LockScreen -> getLocalizedString(R.string.lock_screen)
                TogglePrivateSpace -> getLocalizedString(R.string.private_space)
                ShowNotification -> getLocalizedString(R.string.show_notifications)
                ShowAppList -> getLocalizedString(R.string.show_app_list)
                ShowWidgetPage -> getLocalizedString(R.string.show_widget_page)
                ShowNotesManager -> getLocalizedString(R.string.show_notes_manager)
                ShowDigitalWellbeing -> getLocalizedString(R.string.show_digital_wellbeing)
                OpenQuickSettings -> getLocalizedString(R.string.open_quick_settings)
                ShowRecents -> getLocalizedString(R.string.show_recents)
                OpenPowerDialog -> getLocalizedString(R.string.open_power_dialog)
                TakeScreenShot -> getLocalizedString(R.string.take_a_screenshot)
                RestartApp -> getLocalizedString(R.string.restart_launcher)
                Disabled -> getLocalizedString(R.string.disabled)
            }
        }
    }

    /** How the app drawer lists apps. */
    enum class DrawerView : EnumOption {
        AZ,
        Categories;

        @Composable
        override fun string(): String = getString()

        fun getString(): String = when (this) {
            AZ -> getLocalizedString(R.string.drawer_view_az)
            Categories -> getLocalizedString(R.string.drawer_view_categories)
        }
    }

    /** How the clock and date block on the home screen is drawn. */
    enum class ClockStyle : EnumOption {
        Classic,
        Swiss,
        Rings,
        OneLine;

        @Composable
        override fun string(): String = getString()

        fun getString(): String = when (this) {
            Classic -> getLocalizedString(R.string.clock_style_classic)
            Swiss -> getLocalizedString(R.string.clock_style_swiss)
            Rings -> getLocalizedString(R.string.clock_style_rings)
            OneLine -> getLocalizedString(R.string.clock_style_one_line)
        }
    }

    /** Style "Rings": where the ring sits. */
    enum class RingPlacement : EnumOption {
        Beside,
        Centred;

        @Composable
        override fun string(): String = getString()

        fun getString(): String = when (this) {
            Beside -> getLocalizedString(R.string.ring_beside)
            Centred -> getLocalizedString(R.string.ring_centred)
        }
    }

    /** Style "Rings": what the middle of the ring shows. */
    enum class RingInside : EnumOption {
        Day,
        Time,
        None;

        @Composable
        override fun string(): String = getString()

        fun getString(): String = when (this) {
            Day -> getLocalizedString(R.string.ring_inside_day)
            Time -> getLocalizedString(R.string.ring_inside_time)
            None -> getLocalizedString(R.string.ring_inside_none)
        }
    }

    /** Style "Rings": the outer ring as a 12-hour dial or the whole day. */
    enum class RingScale : EnumOption {
        Twelve,
        TwentyFour;

        @Composable
        override fun string(): String = getString()

        fun getString(): String = when (this) {
            Twelve -> getLocalizedString(R.string.ring_scale_12)
            TwentyFour -> getLocalizedString(R.string.ring_scale_24)
        }
    }

    /** Where the drawer search continues when no app matches. */
    enum class SearchScope : EnumOption {
        Apps,
        AppsFiles,
        AppsFilesContacts;

        val files: Boolean get() = this != Apps
        val contacts: Boolean get() = this == AppsFilesContacts

        @Composable
        override fun string(): String = getString()

        fun getString(): String = when (this) {
            Apps -> getLocalizedString(R.string.search_scope_apps)
            AppsFiles -> getLocalizedString(R.string.search_scope_files)
            AppsFilesContacts -> getLocalizedString(R.string.search_scope_all)
        }
    }

    enum class Theme : EnumOption {
        System,
        Dark,
        Light;

        fun getString(): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Dark -> getLocalizedString(R.string.dark)
                Light -> getLocalizedString(R.string.light)
            }
        }

        // Keep this for Composable usage
        @Composable
        override fun string(): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Dark -> getLocalizedString(R.string.dark)
                Light -> getLocalizedString(R.string.light)
            }
        }
    }

    enum class TempUnits : EnumOption {
        Celsius,
        Fahrenheit;

        // Keep this for Composable usage
        @Composable
        override fun string(): String {
            return when (this) {
                Celsius -> getLocalizedString(R.string.celsius)
                Fahrenheit -> getLocalizedString(R.string.fahrenheit)
            }
        }
    }


    enum class FontFamily : EnumOption {
        System,
        Custom,
        Doto,
        Montserrat;

        fun getFont(context: Context): Typeface? {
            return when (this) {
                System -> getTrueSystemFont()
                Doto -> ResourcesCompat.getFont(context, R.font.doto)
                Montserrat -> ResourcesCompat.getFont(context, R.font.montserrat)
                Custom -> {
                    val customFontFile = File(context.filesDir, "CustomFont.ttf")
                    if (customFontFile.exists()) {
                        try {
                            Typeface.createFromFile(customFontFile)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            getTrueSystemFont()
                        }
                    } else getTrueSystemFont()
                }
            }
        }

        fun getString(): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Doto -> getLocalizedString(R.string.settings_font_doto)
                Montserrat -> getLocalizedString(R.string.settings_font_montserrat)
                Custom -> getLocalizedString(R.string.system_custom)
            }
        }

        @Composable
        override fun string(): String {
            return when (this) {
                System -> getLocalizedString(R.string.system_default)
                Doto -> getLocalizedString(R.string.settings_font_doto)
                Montserrat -> getLocalizedString(R.string.settings_font_montserrat)
                Custom -> getLocalizedString(R.string.system_custom)
            }
        }
    }

}
