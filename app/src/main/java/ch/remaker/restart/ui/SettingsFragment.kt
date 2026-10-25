package ch.remaker.restart.ui

import ch.remaker.common.openUrl
import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.compose.BackHandler
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Column
import ch.remaker.restart.ui.compose.SettingsComposable.FontText
import ch.remaker.restart.ui.compose.SettingsComposable.SettingsTile
import ch.remaker.restart.ui.compose.SettingsComposable.SectionCard
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import ch.remaker.restart.ui.compose.SettingsComposable.outlineColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import ch.remaker.common.AppLogger
import ch.remaker.common.LauncherLocaleManager
import ch.remaker.common.LocalizedResources
import ch.remaker.common.getLocalizedString
import ch.remaker.common.isBiometricEnabled
import ch.remaker.common.isGestureNavigationEnabled
import ch.remaker.common.requestRuntimePermission
import ch.remaker.common.showInstantToast
import ch.remaker.common.showShortToast
import ch.remaker.restart.BuildConfig
import ch.remaker.restart.MainActivity
import ch.remaker.restart.MainViewModel
import ch.remaker.restart.R
import ch.remaker.restart.data.AppCategory
import ch.remaker.restart.data.AppListItem
import ch.remaker.restart.data.Constants
import ch.remaker.restart.data.Constants.Action
import ch.remaker.restart.data.Constants.AppDrawerFlag
import ch.remaker.restart.data.Prefs
import ch.remaker.restart.databinding.FragmentSettingsBinding
import ch.remaker.restart.helper.FontManager
import ch.remaker.restart.helper.IconCacheTarget
import ch.remaker.restart.helper.emptyString
import ch.remaker.restart.helper.getTrueSystemFont
import ch.remaker.restart.helper.hasLocationPermission
import ch.remaker.restart.helper.helpFeedbackButton
import ch.remaker.restart.helper.hideNavigationBar
import ch.remaker.restart.helper.hideStatusBar
import ch.remaker.restart.helper.isSystemInDarkMode
import ch.remaker.restart.helper.isREstartAppDefault
import ch.remaker.restart.helper.openAppInfo
import ch.remaker.restart.helper.reloadLauncher
import ch.remaker.restart.helper.setThemeMode
import ch.remaker.restart.helper.setTopPadding
import ch.remaker.restart.helper.showNavigationBar
import ch.remaker.restart.helper.showStatusBar
import ch.remaker.restart.helper.updateHomeWidget
import ch.remaker.restart.helper.utils.AppReloader
import ch.remaker.restart.helper.utils.PrivateSpaceManager
import ch.remaker.restart.style.SettingsTheme
import ch.remaker.restart.ui.components.DialogManager
import ch.remaker.restart.ui.compose.CategoryManager
import ch.remaker.restart.ui.compose.SettingsComposable.PageHeader
import ch.remaker.restart.ui.compose.SettingsComposable.SettingsHomeCard
import ch.remaker.restart.ui.compose.SettingsComposable.SettingsHomeItem
import ch.remaker.restart.ui.compose.SettingsComposable.SettingsLink
import ch.remaker.restart.ui.compose.SettingsComposable.SettingsSelect
import ch.remaker.restart.ui.compose.SettingsComposable.SettingsSwitch
import ch.remaker.restart.ui.compose.SettingsComposable.SettingsTitle
import ch.remaker.restart.ui.compose.SettingsComposable.TitleWithHtmlLinks
import ch.remaker.restart.ui.compose.SettingsComposable.TopMainHeader
import ch.remaker.restart.ui.iconpack.CustomIconSelectionActivity
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat


class SettingsFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    /** Apps for the category manager; kept as state so the screen follows tag changes. */
    private var appsForCategories by mutableStateOf<List<AppListItem>>(emptyList())
    private lateinit var dialogBuilder: DialogManager

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        dialogBuilder = DialogManager(requireContext(), requireActivity())
        prefs = Prefs(requireContext())
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (prefs.firstSettingsOpen) {
            prefs.firstSettingsOpen = false
        }

        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        viewModel.isREstartAppDefault()
        viewModel.appList.observe(viewLifecycleOwner) { appsForCategories = it.orEmpty() }

        resetThemeColors()

        setTopPadding(binding.settingsView, true)
    }

    private fun resetThemeColors() {
        binding.settingsView.setContent {

            val isDark = when (prefs.appTheme) {
                Constants.Theme.Light -> false
                Constants.Theme.Dark -> true
                Constants.Theme.System -> isSystemInDarkMode(requireContext())
            }

            setThemeMode(requireContext(), isDark, binding.settingsView)
            SettingsTheme(isDark) {
                Settings()
            }
        }
    }

    @SuppressLint("DefaultLocale")
    @Composable
    private fun Settings() {
        var selectedSettingsSize by remember { mutableIntStateOf(prefs.settingsSize) }
        var toggledPrivateSpaces by remember { mutableStateOf(PrivateSpaceManager(requireContext()).isPrivateSpaceLocked()) }

        // Derived states that recompute whenever selectedSize changes
        val titleFontSize by remember {
            derivedStateOf { (selectedSettingsSize.sp) }
        }

        val descriptionFontSize by remember {
            derivedStateOf { (selectedSettingsSize.sp * 0.8f) }
        }

        val density = LocalDensity.current

        val iconSize by remember(selectedSettingsSize) {
            derivedStateOf {
                (selectedSettingsSize.sp * 1.8f).toDp(density)
            }
        }

        val context = LocalContext.current
        val scrollState = rememberScrollState()

        var currentScreen by remember { mutableStateOf("main") }

        // Features Settings
        var selectedTheme by remember { mutableStateOf(prefs.appTheme) }
        var selectedLanguage by remember { mutableStateOf(prefs.appLanguage) }
        var selectedFontFamily by remember { mutableStateOf(prefs.fontFamily) }
        var toggledHideSearchView by remember { mutableStateOf(prefs.hideSearchView) }
        var toggledFloating by remember { mutableStateOf(prefs.showFloating) }

        var toggledShowAZSidebar by remember { mutableStateOf(prefs.showAZSidebar) }
        var toggledAutoShowKeyboard by remember { mutableStateOf(prefs.autoShowKeyboard) }
        var toggledSearchFromStart by remember { mutableStateOf(prefs.searchFromStart) }
        var toggledEnableFilterStrength by remember { mutableStateOf(prefs.enableFilterStrength) }
        var toggledSmartSearch by remember { mutableStateOf(prefs.smartSearch) }
        var selectedDrawerView by remember { mutableStateOf(prefs.drawerView) }
        var selectedSearchScope by remember { mutableStateOf(prefs.searchScope) }
        var toggledCategoryUppercase by remember { mutableStateOf(prefs.categoryUppercase) }
        var toggledCategoryShowCount by remember { mutableStateOf(prefs.categoryShowCount) }
        var selectedCategorySize by remember { mutableIntStateOf(prefs.categorySize) }
        var selectedCategoryColor by remember { mutableIntStateOf(prefs.categoryColor) }
        var selectedSearchSpacing by remember { mutableIntStateOf(prefs.searchSpacing) }
        var selectedSearchHeight by remember { mutableIntStateOf(prefs.searchHeight) }
        var selectedFilterStrength by remember { mutableIntStateOf(prefs.filterStrength) }

        var toggledAutoOpenApp by remember { mutableStateOf(prefs.autoOpenApp) }
        var toggledOpenAppOnEnter by remember { mutableStateOf(prefs.openAppOnEnter) }
        var toggledAppsLocked by remember { mutableStateOf(prefs.homeLocked) }
        var selectedHomeAppsNum by remember { mutableIntStateOf(prefs.homeAppsNum) }

        var toggledShowDate by remember { mutableStateOf(prefs.showDate) }
        var toggledShowDayOfYear by remember { mutableStateOf(prefs.showDayOfYear) }
        var toggledShowClock by remember { mutableStateOf(prefs.showClock) }
        var toggledShowClockFormat by remember { mutableStateOf(prefs.showClockFormat) }
        var toggledShowAlarm by remember { mutableStateOf(prefs.showAlarm) }
        var bedtimeStartHour by remember { mutableIntStateOf(prefs.bedtimeStartHour) }
        var bedtimeStartMinute by remember { mutableIntStateOf(prefs.bedtimeStartMinute) }
        var bedtimeEndHour by remember { mutableIntStateOf(prefs.bedtimeEndHour) }
        var bedtimeEndMinute by remember { mutableIntStateOf(prefs.bedtimeEndMinute) }
        var toggledShowBattery by remember { mutableStateOf(prefs.showBattery) }
        var toggledShowBatteryIcon by remember { mutableStateOf(prefs.showBatteryIcon) }
        var toggledShowWeather by remember { mutableStateOf(prefs.showWeather) }
        var toggledShowPrivateSpaces by remember { mutableStateOf(prefs.showPrivateSpaces) }
        var toggledGPSLocation by remember { mutableStateOf(prefs.gpsLocation) }
        var selectedTempUnits by remember { mutableStateOf(prefs.tempUnit) }
        var selectedWeatherLocation by remember { mutableStateOf(prefs.loadLocationName()) }

        val contextMenuOptionLabels = listOf(
            getLocalizedString(R.string.pin),
            getLocalizedString(R.string.lock),
            getLocalizedString(R.string.hide),
            getLocalizedString(R.string.rename),
            getLocalizedString(R.string.tag),
            getLocalizedString(R.string.info),
            getLocalizedString(R.string.delete)
        )

        val homeButtonOptionLabels = listOf(
            getLocalizedString(R.string.home_button_phone),
            getLocalizedString(R.string.home_button_messages),
            getLocalizedString(R.string.home_button_camera),
            getLocalizedString(R.string.home_button_photos),
            getLocalizedString(R.string.home_button_web),
            getLocalizedString(R.string.home_button_settings),
            getLocalizedString(R.string.home_button_logo)
        )

        val appListButtonOptionLabels = listOf(
            getLocalizedString(R.string.applist_button_contacts)
        )

        // Look & Feel Settings
        var selectedAppSize by remember { mutableIntStateOf(prefs.appSize) }
        var selectedDateSize by remember { mutableIntStateOf(prefs.dateSize) }
        var selectedClockSize by remember { mutableIntStateOf(prefs.clockSize) }
        var selectedAlarmSize by remember { mutableIntStateOf(prefs.alarmSize) }
        var selectedBatterySize by remember { mutableIntStateOf(prefs.batterySize) }

        var selectedPaddingSize by remember { mutableIntStateOf(prefs.textPaddingSize) }
        var toggledExtendHomeAppsArea by remember { mutableStateOf(prefs.extendHomeAppsArea) }
        var toggledHomeAlignmentBottom by remember { mutableStateOf(prefs.homeAlignmentBottom) }

        var toggledShowStatusBar by remember { mutableStateOf(prefs.showStatusBar) }
        var toggledShowNavigationBar by remember { mutableStateOf(prefs.showNavigationBar) }
        var toggledRecentAppsDisplayed by remember { mutableStateOf(prefs.recentAppsDisplayed) }
        var selectedRecentCounter by remember { mutableIntStateOf(prefs.recentCounter) }
        var toggledRecentAppUsageStats by remember { mutableStateOf(prefs.appUsageStats) }
        var selectedIconPackHome by remember { mutableStateOf(prefs.iconPackHome) }
        var selectedIconPackAppList by remember { mutableStateOf(prefs.iconPackAppList) }
        var toggledShowBackground by remember { mutableStateOf(prefs.showBackground) }
        var selectedBackgroundOpacity by remember { mutableIntStateOf(prefs.opacityNum) }

        var selectedHomeAlignment by remember { mutableStateOf(prefs.homeAlignment) }
        var selectedClockAlignment by remember { mutableStateOf(prefs.clockAlignment) }
        var selectedDateAlignment by remember { mutableStateOf(prefs.dateAlignment) }
        var selectedAlarmAlignment by remember { mutableStateOf(prefs.alarmAlignment) }
        var selectedDrawAlignment by remember { mutableStateOf(prefs.drawerAlignment) }

        var selectedBackgroundColor by remember { mutableIntStateOf(prefs.backgroundColor) }
        var selectedAppColor by remember { mutableIntStateOf(prefs.appColor) }
        var selectedDateColor by remember { mutableIntStateOf(prefs.dateColor) }
        var selectedClockColor by remember { mutableIntStateOf(prefs.clockColor) }
        var selectedAlarmColor by remember { mutableIntStateOf(prefs.alarmClockColor) }
        var selectedBatteryColor by remember { mutableIntStateOf(prefs.batteryColor) }
        var toggledIconRainbowColors by remember { mutableStateOf(prefs.iconRainbowColors) }
        var selectedShortcutIconsColor by remember { mutableIntStateOf(prefs.shortcutIconsColor) }

        // Gestures Settings
        var selectedDoubleTapAction by remember { mutableStateOf(prefs.doubleTapAction) }
        var selectedClickClockAction by remember { mutableStateOf(prefs.clickClockAction) }
        var selectedClickDateAction by remember { mutableStateOf(prefs.clickDateAction) }
        var selectedClickAppUsageAction by remember { mutableStateOf(prefs.clickAppUsageAction) }
        var selectedClickFloatingAction by remember { mutableStateOf(prefs.clickFloatingAction) }

        var selectedShortSwipeUpAction by remember { mutableStateOf(prefs.shortSwipeUpAction) }
        var selectedShortSwipeDownAction by remember { mutableStateOf(prefs.shortSwipeDownAction) }
        var selectedShortSwipeLeftAction by remember { mutableStateOf(prefs.shortSwipeLeftAction) }
        var selectedShortSwipeRightAction by remember { mutableStateOf(prefs.shortSwipeRightAction) }

        var selectedLongSwipeUpAction by remember { mutableStateOf(prefs.longSwipeUpAction) }
        var selectedLongSwipeDownAction by remember { mutableStateOf(prefs.longSwipeDownAction) }
        var selectedLongSwipeLeftAction by remember { mutableStateOf(prefs.longSwipeLeftAction) }
        var selectedLongSwipeRightAction by remember { mutableStateOf(prefs.longSwipeRightAction) }

        val actions = Action.entries

        // Filter out 'TogglePrivateSpace' if private space is not supported
        val filteredActions =
            if (!PrivateSpaceManager(requireContext()).isPrivateSpaceSetUp() || !isREstartAppDefault(
                    requireContext()
                )
            ) {
                actions.filter { it != Action.TogglePrivateSpace }
            } else {
                actions
            }

        // Convert enums to their string representations
        val actionStrings = filteredActions.map { it.getString() }.toTypedArray()

        var selectedShortSwipeThreshold by remember { mutableFloatStateOf(prefs.shortSwipeThreshold) }
        var selectedLongSwipeThreshold by remember { mutableFloatStateOf(prefs.longSwipeThreshold) }

        // Notes Settings
        var toggledAutoExpandNotes by remember { mutableStateOf(prefs.autoExpandNotes) }
        var toggledClickToEditDelete by remember { mutableStateOf(prefs.clickToEditDelete) }

        var selectedNotesBackgroundColor by remember { mutableIntStateOf(prefs.notesBackgroundColor) }
        var selectedBubbleBackgroundColor by remember { mutableIntStateOf(prefs.bubbleBackgroundColor) }
        var selectedBubbleMessageTextColor by remember { mutableIntStateOf(prefs.bubbleMessageTextColor) }
        var selectedBubbleTimeDateColor by remember { mutableIntStateOf(prefs.bubbleTimeDateColor) }
        var selectedBubbleCategoryColor by remember { mutableIntStateOf(prefs.bubbleCategoryColor) }

        var selectedInputMessageColor by remember { mutableIntStateOf(prefs.inputMessageColor) }
        var selectedInputMessageHintColor by remember { mutableIntStateOf(prefs.inputMessageHintColor) }

        // Private Spaces Settings
        val (setPrivateSpacesIcon, setPrivateSpacesStatus) = if (toggledPrivateSpaces) {
            R.drawable.private_profile_on to R.string.locked
        } else {
            R.drawable.private_profile_off to R.string.unlocked
        }

        // Advanced Settings
        val (changeLauncherText, changeLauncherTextDescription) = if (isREstartAppDefault(
                requireContext()
            )
        ) {
            R.string.advanced_settings_change_default_launcher to
                    R.string.advanced_settings_change_default_launcher_description
        } else {
            R.string.advanced_settings_set_as_default_launcher to
                    R.string.advanced_settings_set_as_default_launcher_description
        }

        // Experimental Settings
        var toggledExpertOptions by remember { mutableStateOf(prefs.enableExpertOptions) }
        var toggledForceWallpaper by remember { mutableStateOf(prefs.forceWallpaper) }
        var toggledSettingsLocked by remember { mutableStateOf(prefs.settingsLocked) }
        var toggledLockOrientation by remember { mutableStateOf(prefs.lockOrientation) }
        var toggledHapticFeedback by remember { mutableStateOf(prefs.hapticFeedback) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (currentScreen == "main" || currentScreen == "about") Modifier else Modifier.verticalScroll(scrollState))
        ) {
            when (currentScreen) {
                "main" -> {
                    val titleColor = SettingsTheme.typography.title.color
                    val mutedColor = SettingsTheme.typography.option.color
                    val capsStyle = TextStyle(letterSpacing = 0.14.em)
                    val privateSpaceSetUp = PrivateSpaceManager(context).isPrivateSpaceSetUp()

                    // Swiss head: the mark top left, the title large and bold, the app and version
                    // as small capitals on the right, a 2 dp rule under all of it.
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 28.dp, start = 24.dp, end = 24.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        FontText(
                            text = getLocalizedString(R.string.settings_heading),
                            fontSize = (titleFontSize.value * 2.1f).sp,
                            color = titleColor,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(letterSpacing = (-0.03).em),
                            modifier = Modifier.weight(1f)
                        )
                        FontText(
                            text = (getLocalizedString(R.string.about_app_title) + "\n" + BuildConfig.VERSION_NAME.replace(" Build ", " · Build ")).uppercase(),
                            fontSize = (descriptionFontSize.value * 0.8f).sp,
                            color = mutedColor,
                            style = capsStyle,
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 14.dp, start = 24.dp, end = 24.dp).height(2.dp).background(titleColor))

                    // The numbered list
                    @Composable
                    fun Entry(index: Int, title: String, subtitle: String, onClick: () -> Unit) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onClick)
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FontText(
                                text = index.toString().padStart(2, '0'),
                                fontSize = (descriptionFontSize.value * 0.8f).sp,
                                color = mutedColor,
                                fontWeight = FontWeight.Medium,
                                style = capsStyle,
                                modifier = Modifier.width(44.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                FontText(text = title, fontSize = titleFontSize, color = titleColor, fontWeight = FontWeight.Medium)
                                FontText(
                                    text = subtitle,
                                    fontSize = (descriptionFontSize.value * 0.9f).sp,
                                    color = mutedColor,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            FontText(text = "→", fontSize = titleFontSize, color = titleColor)
                        }
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(1.dp).background(titleColor.copy(alpha = 0.14f)))
                    }

                    Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                        var n = 1
                        Entry(n++, getLocalizedString(R.string.settings_features_title), getLocalizedString(R.string.settings_features_sub)) { currentScreen = "features" }
                        Entry(n++, getLocalizedString(R.string.settings_look_feel_title), getLocalizedString(R.string.settings_look_feel_sub)) { currentScreen = "look_feel" }
                        Entry(n++, getLocalizedString(R.string.settings_gestures_title), getLocalizedString(R.string.settings_gestures_sub)) { currentScreen = "gestures" }
                        Entry(n++, getLocalizedString(R.string.settings_favorite_apps_title), getLocalizedString(R.string.settings_favorite_apps_sub)) { showFavoriteApps() }
                        Entry(n++, getLocalizedString(R.string.settings_hidden_apps_title), getLocalizedString(R.string.settings_hidden_apps_sub)) { showHiddenApps() }
                        Entry(n++, getLocalizedString(R.string.settings_notes_title), getLocalizedString(R.string.settings_notes_sub)) { currentScreen = "notes" }
                        if (privateSpaceSetUp) {
                            Entry(n++, getLocalizedString(R.string.private_space_title), getLocalizedString(setPrivateSpacesStatus)) {
                                if (PrivateSpaceManager(context).isPrivateSpaceSetUp(showToast = false, launchSettings = false)) {
                                    PrivateSpaceManager(context).togglePrivateSpaceLock(showToast = false, launchSettings = false)
                                    toggledPrivateSpaces = !toggledPrivateSpaces
                                }
                            }
                        }
                        Entry(n, getLocalizedString(R.string.settings_advanced_title), getLocalizedString(R.string.settings_advanced_sub)) { currentScreen = "advanced" }
                    }

                    // Bottom: the way to the About screen and the signature on the left; the RE* mark on the
                    // right opens the device summary (what to paste into a bug report).
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            FontText(
                                text = (getLocalizedString(R.string.about_settings_title, getLocalizedString(R.string.about_app_title)) + " →").uppercase(),
                                fontSize = (descriptionFontSize.value * 0.8f).sp,
                                color = SettingsTheme.typography.title.color,
                                fontWeight = FontWeight.Medium,
                                style = TextStyle(letterSpacing = 0.14.em),
                                modifier = Modifier.clickable { currentScreen = "about" }.padding(bottom = 4.dp)
                            )
                            FontText(
                                text = getLocalizedString(R.string.about_developer).uppercase(),
                                fontSize = (descriptionFontSize.value * 0.8f).sp,
                                color = SettingsTheme.typography.option.color,
                                style = TextStyle(letterSpacing = 0.14.em)
                            )
                        }
                        Image(
                            painter = painterResource(R.drawable.branding_image),
                            contentDescription = getLocalizedString(R.string.device_summary),
                            modifier = Modifier
                                .height(30.dp)
                                .clickable { dialogBuilder.showDeviceStatsBottomSheet(context = context) }
                        )
                    }

                    if (isGestureNavigationEnabled(context)) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_gesture_nav)))
                    } else {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_3_button_nav)))
                    }
                }

                "categories" -> {
                    CategoryManager.Screen(
                        context = requireContext(),
                        prefs = prefs,
                        dialogBuilder = dialogBuilder,
                        apps = appsForCategories,
                        titleFontSize = titleFontSize,
                        onBack = { currentScreen = "features" },
                        onChanged = { viewModel.getAppList(includeHiddenApps = false) }
                    )
                }

                "features" -> {
                    BackHandler { currentScreen = "main" }
                    PageHeader(
                        iconRes = R.drawable.ic_back,
                        title = getLocalizedString(R.string.features_settings_title),
                        onClick = { currentScreen = "main" }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsTitle(
                        text = getLocalizedString(R.string.settings_search_section),
                        fontSize = titleFontSize
                    )
                    SectionCard {

                    SettingsSwitch(
                        text = getLocalizedString(R.string.hide_search_view),
                        fontSize = titleFontSize,
                        defaultState = toggledHideSearchView,
                        onCheckedChange = {
                            toggledHideSearchView = !prefs.hideSearchView
                            prefs.hideSearchView = toggledHideSearchView

                            if (toggledHideSearchView) {
                                toggledAutoShowKeyboard = false
                                prefs.autoShowKeyboard = toggledAutoShowKeyboard
                            }
                        }
                    )

                    if (!toggledHideSearchView) {
                        SettingsSwitch(
                            text = getLocalizedString(R.string.auto_show_keyboard),
                            fontSize = titleFontSize,
                            defaultState = toggledAutoShowKeyboard,
                            onCheckedChange = {
                                toggledAutoShowKeyboard = !prefs.autoShowKeyboard
                                prefs.autoShowKeyboard = toggledAutoShowKeyboard
                            }
                        )
                    }

                    SettingsSelect(
                        title = getLocalizedString(R.string.search_scope),
                        option = selectedSearchScope.string(),
                        fontSize = titleFontSize,
                        onClick = {
                            val entries = Constants.SearchScope.entries
                            val options = entries.map { it.getString() }
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = options.toTypedArray(),
                                title = getLocalizedString(R.string.search_scope),
                                selectedIndex = entries.indexOf(selectedSearchScope).coerceAtLeast(0),
                                onItemSelected = { chosen ->
                                    entries.getOrNull(options.indexOf(chosen))?.let {
                                        selectedSearchScope = it
                                        prefs.searchScope = it
                                    }
                                }
                            )
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.smart_search),
                        fontSize = titleFontSize,
                        defaultState = toggledSmartSearch,
                        onCheckedChange = {
                            toggledSmartSearch = !prefs.smartSearch
                            prefs.smartSearch = toggledSmartSearch
                        }
                    )

                    // The fuzzy finder options only apply to the classic search.
                    if (!toggledSmartSearch) {
                        SettingsSwitch(
                            text = getLocalizedString(R.string.enable_filter_strength),
                            fontSize = titleFontSize,
                            defaultState = toggledEnableFilterStrength,
                            onCheckedChange = {
                                toggledEnableFilterStrength = !prefs.enableFilterStrength
                                prefs.enableFilterStrength = toggledEnableFilterStrength
                            }
                        )

                        if (toggledEnableFilterStrength) {
                            SettingsSelect(
                                title = getLocalizedString(R.string.filter_strength),
                                option = selectedFilterStrength.toString(),
                                fontSize = titleFontSize,
                                onClick = {
                                    dialogBuilder.showSliderBottomSheet(
                                        context = requireContext(),
                                        title = getLocalizedString(R.string.filter_strength),
                                        minValue = Constants.MIN_FILTER_STRENGTH,
                                        maxValue = Constants.MAX_FILTER_STRENGTH,
                                        currentValue = prefs.filterStrength,
                                        onValueSelected = { newFilterStrength ->
                                            selectedFilterStrength =
                                                newFilterStrength.toInt() // Update state
                                            prefs.filterStrength =
                                                newFilterStrength.toInt() // Persist selection in preferences
                                            viewModel.filterStrength.value = newFilterStrength.toInt()
                                        }
                                    )
                                }
                            )
                        }

                        if (toggledEnableFilterStrength) {
                            SettingsSwitch(
                                text = getLocalizedString(R.string.search_from_start),
                                fontSize = titleFontSize,
                                defaultState = toggledSearchFromStart,
                                onCheckedChange = {
                                    toggledSearchFromStart = !prefs.searchFromStart
                                    prefs.searchFromStart = toggledSearchFromStart
                                }
                            )
                        }
                    }

                    SettingsSwitch(
                        text = getLocalizedString(R.string.auto_open_apps),
                        fontSize = titleFontSize,
                        defaultState = toggledAutoOpenApp,
                        onCheckedChange = {
                            toggledAutoOpenApp = !prefs.autoOpenApp
                            prefs.autoOpenApp = toggledAutoOpenApp
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.open_apps_on_enter),
                        fontSize = titleFontSize,
                        defaultState = toggledOpenAppOnEnter,
                        onCheckedChange = {
                            toggledOpenAppOnEnter = !prefs.openAppOnEnter
                            prefs.openAppOnEnter = toggledOpenAppOnEnter
                        }
                    )

                    }
                    SettingsTitle(
                        text = getLocalizedString(R.string.settings_drawer_section),
                        fontSize = titleFontSize
                    )
                    SectionCard {

                    SettingsSelect(
                        title = getLocalizedString(R.string.drawer_view),
                        option = selectedDrawerView.string(),
                        fontSize = titleFontSize,
                        onClick = {
                            val entries = Constants.DrawerView.entries
                            val options = entries.map { it.getString() }
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = options.toTypedArray(),
                                title = getLocalizedString(R.string.drawer_view),
                                selectedIndex = entries.indexOf(selectedDrawerView).coerceAtLeast(0),
                                onItemSelected = { chosen ->
                                    entries.getOrNull(options.indexOf(chosen))?.let {
                                        selectedDrawerView = it
                                        prefs.drawerView = it
                                    }
                                }
                            )
                        }
                    )

                    if (selectedDrawerView == Constants.DrawerView.Categories) {
                        SettingsLink(
                            title = getLocalizedString(R.string.manage_categories),
                            fontSize = titleFontSize,
                            onClick = {
                                // Hidden apps are not part of the drawer, so they are not part of its categories
                                viewModel.getAppList(includeHiddenApps = false)
                                currentScreen = "categories"
                            }
                        )
                    }

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_az_sidebar),
                        fontSize = titleFontSize,
                        defaultState = toggledShowAZSidebar,
                        onCheckedChange = {
                            toggledShowAZSidebar = !prefs.showAZSidebar
                            prefs.showAZSidebar = toggledShowAZSidebar
                        }
                    )

                    val currentContextMenuFlags = remember {
                        mutableStateListOf<Boolean>().apply {
                            addAll(prefs.getMenuFlags("CONTEXT_MENU_FLAGS", "0011111"))
                        }
                    }

                    val enabledContextMenuOptions by remember {
                        derivedStateOf {
                            contextMenuOptionLabels
                                .take(currentContextMenuFlags.size)
                                .zip(currentContextMenuFlags)
                                .filter { it.second }
                                .joinToString(", ") { it.first }
                                .ifEmpty { getLocalizedString(R.string.none) }
                        }
                    }

                    SettingsSelect(
                        title = getLocalizedString(R.string.settings_context_menu_title),
                        option = enabledContextMenuOptions,
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showFlagSettingsBottomSheet(
                                context,
                                contextMenuOptionLabels,
                                "CONTEXT_MENU_FLAGS",
                                "0011111"
                            ) { updatedFlags: List<Boolean> ->
                                currentContextMenuFlags.clear()
                                currentContextMenuFlags.addAll(updatedFlags)
                            }
                        }
                    )

                    val currentAppListFlags = remember {
                        mutableStateListOf<Boolean>().apply {
                            addAll(prefs.getMenuFlags("APPLIST_BUTTON_FLAGS", "0").takeLast(1))
                        }
                    }

                    val enabledAppListOptions by remember {
                        derivedStateOf {
                            appListButtonOptionLabels
                                .take(currentAppListFlags.size)
                                .zip(currentAppListFlags)
                                .filter { it.second }
                                .joinToString(", ") { it.first }
                                .ifEmpty { getLocalizedString(R.string.none) }
                        }
                    }

                    SettingsSelect(
                        title = getLocalizedString(R.string.settings_applist_buttons_title),
                        option = enabledAppListOptions,
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showFlagSettingsBottomSheet(
                                context,
                                appListButtonOptionLabels,
                                "APPLIST_BUTTON_FLAGS",
                                "0"
                            ) { updatedFlags: List<Boolean> ->
                                currentAppListFlags.clear()
                                currentAppListFlags.addAll(updatedFlags)
                            }
                        }
                    )

                    }
                    SettingsTitle(
                        text = getLocalizedString(R.string.weather),
                        fontSize = titleFontSize,
                    )
                    SectionCard {

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_weather),
                        fontSize = titleFontSize,
                        defaultState = toggledShowWeather,
                        onCheckedChange = {
                            toggledShowWeather = !prefs.showWeather
                            prefs.showWeather = toggledShowWeather
                        }
                    )

                    if (toggledShowWeather) {
                        SettingsSwitch(
                            text = getLocalizedString(R.string.gps_location),
                            fontSize = titleFontSize,
                            defaultState = toggledGPSLocation,
                            onCheckedChange = {
                                toggledGPSLocation = !prefs.gpsLocation
                                prefs.gpsLocation = toggledGPSLocation

                                if (toggledGPSLocation && !hasLocationPermission(context)) {
                                    context.requestRuntimePermission(
                                        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                                        Constants.ACCESS_FINE_LOCATION,
                                        "Location"
                                    )
                                }
                            }
                        )
                    }

                    if (toggledShowWeather) {
                        SettingsSelect(
                            title = getLocalizedString(R.string.temp_unit),
                            option = selectedTempUnits.string(),
                            fontSize = titleFontSize,
                            onClick = {
                                val tempUnitsOptions = Constants.TempUnits.entries.toTypedArray()
                                val selectedIndex =
                                    tempUnitsOptions.indexOf(selectedTempUnits).takeIf { it >= 0 }
                                        ?: 1

                                dialogBuilder.showSingleChoiceBottomSheetPill(
                                    context = requireContext(),
                                    options = tempUnitsOptions,
                                    title = getLocalizedString(R.string.temp_unit),
                                    selectedIndex = selectedIndex,
                                    onItemSelected = { newTempUnit ->
                                        selectedTempUnits = newTempUnit // Update state
                                        prefs.tempUnit =
                                            selectedTempUnits // Persist selection in preferences
                                    }
                                )
                            }
                        )
                    }

                    if (toggledShowWeather && !toggledGPSLocation) {
                        SettingsSelect(
                            title = getLocalizedString(R.string.manual_location),
                            option = selectedWeatherLocation,
                            fontSize = titleFontSize,
                            onClick = {
                                showLocationSearch()
                            }
                        )
                    }

                    }
                    if (PrivateSpaceManager(requireContext()).isPrivateSpaceSetUp()) {
                    SettingsTitle(
                        text = getLocalizedString(R.string.settings_private_space_section),
                        fontSize = titleFontSize
                    )
                    SectionCard {

                        SettingsSwitch(
                            text = getLocalizedString(R.string.show_private_spaces),
                            fontSize = titleFontSize,
                            defaultState = toggledShowPrivateSpaces,
                            onCheckedChange = {
                                toggledShowPrivateSpaces = !prefs.showPrivateSpaces
                                prefs.showPrivateSpaces = toggledShowPrivateSpaces
                            }
                        )

                    }
                    }
                    if (isGestureNavigationEnabled(context)) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_gesture_nav)))
                    } else {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_3_button_nav)))
                    }
                
                }

                "look_feel" -> {
                    BackHandler {
                        currentScreen = "main"
                    }

                    PageHeader(
                        iconRes = R.drawable.ic_back,
                        title = getLocalizedString(R.string.look_feel_settings_title),
                        onClick = {
                            currentScreen = "main"
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsTitle(
                        text = getLocalizedString(R.string.settings_appearance_section),
                        fontSize = titleFontSize
                    )
                    SectionCard {

                    SettingsSelect(
                        title = getLocalizedString(R.string.theme_mode),
                        option = selectedTheme.getString(),
                        fontSize = titleFontSize,
                        onClick = {
                            val themeEntries = Constants.Theme.entries

                            val themeOptions = themeEntries.map { it.getString() }

                            val selectedIndex =
                                themeEntries.indexOf(selectedTheme).takeIf { it >= 0 } ?: 1

                            dialogBuilder.showSingleChoiceBottomSheetPill(
                                context = requireContext(),
                                options = themeOptions.toTypedArray(),
                                title = getLocalizedString(R.string.theme_mode),
                                selectedIndex = selectedIndex,
                                onItemSelected = { newThemeName ->
                                    val newThemeIndex =
                                        themeOptions.indexOfFirst { it == newThemeName }
                                    if (newThemeIndex != -1) {
                                        val newTheme = themeEntries[newThemeIndex]
                                        selectedTheme = newTheme
                                        prefs.appTheme = newTheme
                                        resetThemeColors()
                                    }
                                }
                            )
                        }
                    )

                    SettingsSelect(
                        title = getLocalizedString(R.string.font_family),
                        option = selectedFontFamily.string(),
                        fontSize = titleFontSize,
                        onClick = {
                            // Generate options and fonts
                            val fontFamilyEntries = Constants.FontFamily.entries

                            val fontFamilyOptions = fontFamilyEntries.map { it.getString() }
                            val fontFamilyFonts = fontFamilyEntries.map {
                                it.getFont(requireContext()) ?: getTrueSystemFont()
                            }

                            // Determine selected index based on current prefs value
                            val selectedIndex =
                                fontFamilyEntries.indexOf(selectedFontFamily).takeIf { it >= 0 }
                                    ?: 1

                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = fontFamilyOptions.toTypedArray(),
                                fonts = fontFamilyFonts,
                                title = getLocalizedString(R.string.font_family),
                                selectedIndex = selectedIndex,
                                onItemSelected = { newFontFamilyName ->
                                    val newFontFamilyIndex =
                                        fontFamilyOptions.indexOfFirst { it == newFontFamilyName }
                                    if (newFontFamilyIndex != -1) {
                                        val newFontFamily = fontFamilyEntries[newFontFamilyIndex]
                                        if (newFontFamily == Constants.FontFamily.Custom) {
                                            // Show file picker and handle upload
                                            launchFontPicker()
                                        } else {
                                            selectedFontFamily = newFontFamily
                                            prefs.fontFamily = newFontFamily
                                            AppReloader.restartApp(requireContext())
                                        }
                                    }
                                }
                            )
                        }
                    )

                    val hexBackgroundColor =
                        String.format("#%06X", (0xFFFFFF and selectedBackgroundColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.background_color),
                        option = hexBackgroundColor,
                        fontSize = titleFontSize,
                        optionColor = Color(selectedBackgroundColor),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedBackgroundColor,
                                title = getLocalizedString(R.string.background_color),
                                onItemSelected = { selectedColor ->
                                    selectedBackgroundColor = selectedColor
                                    prefs.backgroundColor = selectedColor
                                })
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_background),
                        fontSize = titleFontSize,
                        defaultState = toggledShowBackground,
                        onCheckedChange = {
                            toggledShowBackground = !prefs.showBackground
                            prefs.showBackground = toggledShowBackground
                        }
                    )

                    if (!toggledShowBackground) {
                        SettingsSelect(
                            title = getLocalizedString(R.string.background_opacity),
                            option = selectedBackgroundOpacity.toString(),
                            fontSize = titleFontSize,
                            onClick = {
                                dialogBuilder.showSliderBottomSheet(
                                    context = requireContext(),
                                    title = getLocalizedString(R.string.background_opacity),
                                    minValue = Constants.MIN_OPACITY,
                                    maxValue = Constants.MAX_OPACITY,
                                    currentValue = prefs.opacityNum,
                                    onValueSelected = { newBackgroundOpacity ->
                                        selectedBackgroundOpacity =
                                            newBackgroundOpacity.toInt() // Update state
                                        prefs.opacityNum =
                                            newBackgroundOpacity.toInt() // Persist selection in preferences
                                        viewModel.opacityNum.value = newBackgroundOpacity.toInt()
                                    }
                                )
                            }
                        )
                    }

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_status_bar),
                        fontSize = titleFontSize,
                        defaultState = toggledShowStatusBar,
                        onCheckedChange = {
                            toggledShowStatusBar = !prefs.showStatusBar
                            prefs.showStatusBar = toggledShowStatusBar
                            if (toggledShowStatusBar) showStatusBar(requireActivity().window)
                            else hideStatusBar(requireActivity().window)
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_navigation_bar),
                        fontSize = titleFontSize,
                        defaultState = toggledShowNavigationBar,
                        onCheckedChange = {
                            toggledShowNavigationBar = !prefs.showNavigationBar
                            prefs.showNavigationBar = toggledShowNavigationBar
                            if (toggledShowNavigationBar) showNavigationBar(requireActivity().window)
                            else hideNavigationBar(requireActivity().window)
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.force_colored_wallpaper),
                        fontSize = titleFontSize,
                        defaultState = toggledForceWallpaper,

                        onCheckedChange = {
                            toggledForceWallpaper = !prefs.forceWallpaper
                            prefs.forceWallpaper = toggledForceWallpaper
                        }
                    )

                    }
                    // Clock and date live on their own screen; here only the way in
                    SettingsTitle(
                        text = getLocalizedString(R.string.clock_and_date),
                        fontSize = titleFontSize
                    )
                    SectionCard {
                        SettingsLink(
                            title = getLocalizedString(R.string.clock_display),
                            fontSize = titleFontSize,
                            onClick = { currentScreen = "clock" }
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    SettingsTitle(
                        text = getLocalizedString(R.string.settings_home_list_section),
                        fontSize = titleFontSize
                    )
                    SectionCard {

                    SettingsSelect(
                        title = getLocalizedString(R.string.home_alignment),
                        option = selectedHomeAlignment.string(),
                        fontSize = titleFontSize,
                        onClick = {
                            val gravityOptions = Constants.Gravity.entries.toTypedArray()
                            val selectedIndex =
                                gravityOptions.indexOf(selectedHomeAlignment).takeIf { it >= 0 }
                                    ?: 1

                            dialogBuilder.showSingleChoiceBottomSheetPill(
                                context = requireContext(),
                                options = gravityOptions,
                                title = getLocalizedString(R.string.home_alignment),
                                selectedIndex = selectedIndex,
                                onItemSelected = { newGravity ->
                                    selectedHomeAlignment = newGravity // Update state
                                    prefs.homeAlignment =
                                        newGravity // Persist selection in preferences
                                    viewModel.updateHomeAppsAlignment(
                                        prefs.homeAlignment,
                                        prefs.homeAlignmentBottom
                                    )
                                }
                            )
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.alignment_to_bottom),
                        fontSize = titleFontSize,
                        defaultState = toggledHomeAlignmentBottom,
                        onCheckedChange = {
                            toggledHomeAlignmentBottom = !prefs.homeAlignmentBottom
                            prefs.homeAlignmentBottom = toggledHomeAlignmentBottom
                            viewModel.updateHomeAppsAlignment(
                                prefs.homeAlignment,
                                prefs.homeAlignmentBottom
                            )
                        }
                    )

                    SettingsSelect(
                        title = getLocalizedString(R.string.app_padding_size),
                        option = selectedPaddingSize.toString(),
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSliderBottomSheet(
                                context = requireContext(),
                                title = getLocalizedString(R.string.app_padding_size),
                                minValue = Constants.MIN_TEXT_PADDING,
                                maxValue = Constants.MAX_TEXT_PADDING,
                                currentValue = prefs.textPaddingSize,
                                onValueSelected = { newPaddingSize ->
                                    selectedPaddingSize = newPaddingSize.toInt() // Update state
                                    prefs.textPaddingSize =
                                        newPaddingSize.toInt() // Persist selection in preferences
                                }
                            )
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.extend_home_apps_area),
                        fontSize = titleFontSize,
                        defaultState = toggledExtendHomeAppsArea,
                        onCheckedChange = {
                            toggledExtendHomeAppsArea = !prefs.extendHomeAppsArea
                            prefs.extendHomeAppsArea = toggledExtendHomeAppsArea
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_app_usage_stats),
                        fontSize = titleFontSize,
                        defaultState = toggledRecentAppUsageStats,
                        onCheckedChange = {
                            toggledRecentAppUsageStats = !prefs.appUsageStats
                            prefs.appUsageStats = toggledRecentAppUsageStats
                        }
                    )

                    SettingsSelect(
                        title = getLocalizedString(R.string.apps_on_home_screen),
                        option = selectedHomeAppsNum.toString(),
                        fontSize = titleFontSize,
                        onClick = {
                            val oldHomeAppsNum = selectedHomeAppsNum + 1
                            dialogBuilder.showSliderBottomSheet(
                                context = requireContext(),
                                title = getLocalizedString(R.string.apps_on_home_screen),
                                minValue = Constants.MIN_HOME_APPS,
                                maxValue = Constants.MAX_HOME_APPS,
                                currentValue = prefs.homeAppsNum,
                                onValueSelected = { newHomeAppsNum ->
                                    selectedHomeAppsNum = newHomeAppsNum.toInt() // Update state
                                    prefs.homeAppsNum =
                                        newHomeAppsNum.toInt() // Persist selection in preferences
                                    viewModel.homeAppsNum.value = newHomeAppsNum.toInt()

                                    val userManager =
                                        requireContext().getSystemService(Context.USER_SERVICE) as UserManager

                                    val clearApp = AppListItem(
                                        activityLabel = "Clear",
                                        activityPackage = emptyString(),
                                        activityClass = emptyString(),
                                        user = userManager.userProfiles[0], // or use Process.myUserHandle() if it makes more sense
                                        profileType = "SYSTEM",
                                        customTag = emptyString(),
                                        category = AppCategory.REGULAR
                                    )

                                    for (n in newHomeAppsNum.toInt()..oldHomeAppsNum) {
                                        // n is outside the range between oldHomeAppsNum and newHomeAppsNum
                                        // Do something with n
                                        prefs.setHomeAppModel(n, clearApp)
                                    }

                                    // --- Trigger widget update ---
                                    updateHomeWidget(context)
                                }
                            )
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.lock_home_apps),
                        fontSize = titleFontSize,
                        defaultState = toggledAppsLocked,
                        onCheckedChange = {
                            toggledAppsLocked = !prefs.homeLocked
                            prefs.homeLocked = toggledAppsLocked
                        }
                    )

                    SettingsSelect(
                        title = getLocalizedString(R.string.app_text_size),
                        option = selectedAppSize.toString(),
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSliderBottomSheet(
                                context = requireContext(),
                                title = getLocalizedString(R.string.app_text_size),
                                minValue = Constants.MIN_TEXT_SIZE,
                                maxValue = Constants.MAX_TEXT_SIZE,
                                currentValue = prefs.appSize,
                                onValueSelected = { newAppSize ->
                                    selectedAppSize = newAppSize.toInt() // Update state
                                    prefs.appSize =
                                        newAppSize.toInt() // Persist selection in preferences
                                }
                            )
                        }
                    )

                    val hexAppColor = String.format("#%06X", (0xFFFFFF and selectedAppColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.app_color),
                        option = hexAppColor,
                        fontSize = titleFontSize,
                        optionColor = Color(hexAppColor.toColorInt()),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedAppColor,
                                title = getLocalizedString(R.string.app_color),
                                onItemSelected = { selectedColor ->
                                    selectedAppColor = selectedColor
                                    prefs.appColor = selectedColor

                                    // --- Trigger widget update ---
                                    updateHomeWidget(context)
                                })
                        }
                    )

                    SettingsSelect(
                        title = getLocalizedString(R.string.select_home_icons),
                        option = selectedIconPackHome.getString(IconCacheTarget.HOME.name),
                        fontSize = titleFontSize,
                        onClick = {
                            // Generate options and icons
                            val iconPacksEntries = Constants.IconPacks.entries

                            val iconPacksOptions =
                                iconPacksEntries.map { it.getString(emptyString()) }

                            // Determine selected index based on current prefs value
                            val selectedIndex =
                                iconPacksEntries.indexOf(selectedIconPackHome).takeIf { it >= 0 }
                                    ?: 1

                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = iconPacksOptions.map { it }.toTypedArray(),
                                title = getLocalizedString(R.string.select_home_icons),
                                selectedIndex = selectedIndex,
                                onItemSelected = { newAppIconsName ->
                                    val newIconPacksIndex =
                                        iconPacksOptions.indexOfFirst { it == newAppIconsName }
                                    if (newIconPacksIndex != -1) {
                                        val newAppIcons =
                                            iconPacksEntries[newIconPacksIndex] // Get the selected FontFamily enum
                                        if (newAppIcons == Constants.IconPacks.Custom) {
                                            openCustomIconSelectionDialog(IconCacheTarget.HOME)
                                        } else {
                                            prefs.customIconPackHome = emptyString()
                                            selectedIconPackHome = newAppIcons // Update state
                                            prefs.iconPackHome =
                                                newAppIcons // Persist selection in preferences
                                            viewModel.iconPackHome.value = newAppIcons

                                            // --- Trigger widget update ---
                                            updateHomeWidget(context)
                                        }
                                    }
                                }
                            )
                        }
                    )

                    }
                    SettingsTitle(
                        text = getLocalizedString(R.string.settings_quick_actions_section),
                        fontSize = titleFontSize
                    )
                    SectionCard {

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_floating_button),
                        fontSize = titleFontSize,
                        defaultState = toggledFloating,
                        onCheckedChange = {
                            toggledFloating = !prefs.showFloating
                            prefs.showFloating = toggledFloating
                        }
                    )

                    val currentHomeButtonFlags = remember {
                        mutableStateListOf<Boolean>().apply {
                            addAll(prefs.getMenuFlags("HOME_BUTTON_FLAGS", "0000011"))
                        }
                    }

                    val enabledHomeButtonOptions by remember {
                        derivedStateOf {
                            homeButtonOptionLabels
                                .take(currentHomeButtonFlags.size)
                                .zip(currentHomeButtonFlags)
                                .filter { it.second }
                                .joinToString(", ") { it.first }
                                .ifEmpty { getLocalizedString(R.string.none) }
                        }
                    }
                    SettingsSelect(
                        title = getLocalizedString(R.string.settings_home_buttons_title),
                        option = enabledHomeButtonOptions,
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showFlagSettingsBottomSheet(
                                context,
                                homeButtonOptionLabels,
                                "HOME_BUTTON_FLAGS",
                                "0000011"
                            ) { updatedFlags: List<Boolean> ->
                                currentHomeButtonFlags.clear()
                                currentHomeButtonFlags.addAll(updatedFlags)
                            }
                        }
                    )

                    SettingsSelect(
                        title = getLocalizedString(R.string.shortcuts_color),
                        option = String.format("#%06X", (0xFFFFFF and selectedShortcutIconsColor)),
                        fontSize = titleFontSize,
                        optionColor = Color(
                            String.format(
                                "#%06X",
                                (0xFFFFFF and selectedShortcutIconsColor)
                            ).toColorInt()
                        ),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedShortcutIconsColor,
                                title = getLocalizedString(R.string.shortcuts_color),
                                onItemSelected = { selectedColor ->
                                    selectedShortcutIconsColor = selectedColor
                                    prefs.shortcutIconsColor = selectedColor
                                })
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.rainbow_shortcuts),
                        fontSize = titleFontSize,
                        defaultState = toggledIconRainbowColors,
                        onCheckedChange = {
                            toggledIconRainbowColors = !prefs.iconRainbowColors
                            prefs.iconRainbowColors = toggledIconRainbowColors
                        }
                    )

                    }
                    SettingsTitle(
                        text = getLocalizedString(R.string.settings_drawer_section),
                        fontSize = titleFontSize
                    )
                    SectionCard {

                    SettingsSelect(
                        title = getLocalizedString(R.string.drawer_alignment),
                        option = selectedDrawAlignment.string(),
                        fontSize = titleFontSize,
                        onClick = {
                            val gravityOptions = Constants.Gravity.entries.toTypedArray()
                            val selectedIndex =
                                gravityOptions.indexOf(selectedDrawAlignment).takeIf { it >= 0 }
                                    ?: 1

                            dialogBuilder.showSingleChoiceBottomSheetPill(
                                context = requireContext(),
                                options = Constants.Gravity.entries.toTypedArray(),
                                title = getLocalizedString(R.string.drawer_alignment),
                                selectedIndex = selectedIndex,
                                onItemSelected = { newGravity ->
                                    selectedDrawAlignment = newGravity // Update state
                                    prefs.drawerAlignment =
                                        newGravity // Persist selection in preferences
                                    viewModel.updateDrawerAlignment(newGravity)
                                }
                            )
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_recent_apps),
                        fontSize = titleFontSize,
                        defaultState = toggledRecentAppsDisplayed,
                        onCheckedChange = {
                            toggledRecentAppsDisplayed = !prefs.recentAppsDisplayed
                            prefs.recentAppsDisplayed = toggledRecentAppsDisplayed
                        }
                    )

                    if (toggledRecentAppsDisplayed) {
                        SettingsSelect(
                            title = getLocalizedString(R.string.number_of_recents),
                            option = selectedRecentCounter.toString(),
                            fontSize = titleFontSize,
                            onClick = {
                                dialogBuilder.showSliderBottomSheet(
                                    context = requireContext(),
                                    title = getLocalizedString(R.string.number_of_recents),
                                    minValue = Constants.MIN_RECENT_COUNTER,
                                    maxValue = Constants.MAX_RECENT_COUNTER,
                                    currentValue = prefs.recentCounter,
                                    onValueSelected = { newRecentCounter ->
                                        selectedRecentCounter =
                                            newRecentCounter.toInt() // Update state
                                        prefs.recentCounter =
                                            newRecentCounter.toInt() // Persist selection in preferences
                                        viewModel.recentCounter.value = newRecentCounter.toInt()
                                    }
                                )
                            }
                        )
                    }

                    SettingsSelect(
                        title = getLocalizedString(R.string.drawer_search_height),
                        option = selectedSearchHeight.toString(),
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSliderBottomSheet(
                                context = requireContext(),
                                title = getLocalizedString(R.string.drawer_search_height),
                                minValue = Constants.MIN_SEARCH_HEIGHT,
                                maxValue = Constants.MAX_SEARCH_HEIGHT,
                                currentValue = prefs.searchHeight,
                                onValueSelected = { newValue ->
                                    selectedSearchHeight = newValue.toInt()
                                    prefs.searchHeight = newValue.toInt()
                                }
                            )
                        }
                    )

                    SettingsSelect(
                        title = getLocalizedString(R.string.drawer_search_spacing),
                        option = selectedSearchSpacing.toString(),
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSliderBottomSheet(
                                context = requireContext(),
                                title = getLocalizedString(R.string.drawer_search_spacing),
                                minValue = Constants.MIN_SEARCH_SPACING,
                                maxValue = Constants.MAX_SEARCH_SPACING,
                                currentValue = prefs.searchSpacing,
                                onValueSelected = { newValue ->
                                    selectedSearchSpacing = newValue.toInt()
                                    prefs.searchSpacing = newValue.toInt()
                                }
                            )
                        }
                    )

                    SettingsSelect(
                        title = getLocalizedString(R.string.category_text_size),
                        option = selectedCategorySize.toString(),
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSliderBottomSheet(
                                context = requireContext(),
                                title = getLocalizedString(R.string.category_text_size),
                                minValue = Constants.MIN_CATEGORY_SIZE,
                                maxValue = Constants.MAX_CATEGORY_SIZE,
                                currentValue = prefs.categorySize,
                                onValueSelected = { newValue ->
                                    selectedCategorySize = newValue.toInt()
                                    prefs.categorySize = newValue.toInt()
                                }
                            )
                        }
                    )

                    val hexCategoryColor =
                        String.format("#%06X", (0xFFFFFF and selectedCategoryColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.category_color),
                        option = hexCategoryColor,
                        fontSize = titleFontSize,
                        optionColor = Color(hexCategoryColor.toColorInt()),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedCategoryColor,
                                title = getLocalizedString(R.string.category_color),
                                onItemSelected = { selectedColor ->
                                    selectedCategoryColor = selectedColor
                                    prefs.categoryColor = selectedColor
                                })
                        }
                    )

                    if (selectedDrawerView == Constants.DrawerView.Categories) {
                        SettingsSwitch(
                            text = getLocalizedString(R.string.category_uppercase),
                            fontSize = titleFontSize,
                            defaultState = toggledCategoryUppercase,
                            onCheckedChange = {
                                toggledCategoryUppercase = !prefs.categoryUppercase
                                prefs.categoryUppercase = toggledCategoryUppercase
                            }
                        )

                        SettingsSwitch(
                            text = getLocalizedString(R.string.category_show_count),
                            fontSize = titleFontSize,
                            defaultState = toggledCategoryShowCount,
                            onCheckedChange = {
                                toggledCategoryShowCount = !prefs.categoryShowCount
                                prefs.categoryShowCount = toggledCategoryShowCount
                            }
                        )
                    }

                    SettingsSelect(
                        title = getLocalizedString(R.string.select_app_list_icons),
                        option = selectedIconPackAppList.getString(IconCacheTarget.APP_LIST.name),
                        fontSize = titleFontSize,
                        onClick = {
                            // Generate options and icons
                            val iconPacksEntries = Constants.IconPacks.entries

                            val iconPacksOptions =
                                iconPacksEntries.map { it.getString(emptyString()) }

                            // Determine selected index based on current prefs value
                            val selectedIndex =
                                iconPacksEntries.indexOf(selectedIconPackAppList).takeIf { it >= 0 }
                                    ?: 1

                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = iconPacksOptions.map { it }.toTypedArray(),
                                title = getLocalizedString(R.string.select_app_list_icons),
                                selectedIndex = selectedIndex,
                                onItemSelected = { newAppIconsName ->
                                    val newIconPacksIndex =
                                        iconPacksOptions.indexOfFirst { it == newAppIconsName }
                                    if (newIconPacksIndex != -1) {
                                        val newAppIcons =
                                            iconPacksEntries[newIconPacksIndex] // Get the selected FontFamily enum
                                        if (newAppIcons == Constants.IconPacks.Custom) {
                                            openCustomIconSelectionDialog(IconCacheTarget.APP_LIST)
                                        } else {
                                            prefs.customIconPackAppList = emptyString()
                                            selectedIconPackAppList = newAppIcons // Update state
                                            prefs.iconPackAppList =
                                                newAppIcons // Persist selection in preferences
                                            viewModel.iconPackAppList.value = newAppIcons
                                        }
                                    }
                                }
                            )
                        }
                    )

                    }
                    if (isGestureNavigationEnabled(context)) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_gesture_nav)))
                    } else {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_3_button_nav)))
                    }
                
                }

                "clock" -> {
                    BackHandler { currentScreen = "look_feel" }
                    PageHeader(
                        iconRes = R.drawable.ic_back,
                        title = getLocalizedString(R.string.clock_display),
                        onClick = { currentScreen = "look_feel" }
                    )
                    var selectedClockStyle by remember { mutableStateOf(prefs.clockStyle) }
                    SettingsTitle(
                        text = getLocalizedString(R.string.clock_style),
                        fontSize = titleFontSize
                    )
                    ClockStylePicker(
                        selected = selectedClockStyle,
                        clockColor = selectedClockColor,
                        dateColor = selectedDateColor,
                        clockSize = selectedClockSize,
                        dateSize = selectedDateSize,
                        alignment = selectedClockAlignment,
                        titleFontSize = titleFontSize,
                        descriptionFontSize = descriptionFontSize
                    ) { style ->
                        selectedClockStyle = style
                        prefs.clockStyle = style
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsTitle(
                        text = getLocalizedString(R.string.shown_info),
                        fontSize = titleFontSize,
                    )
                    SectionCard {

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_date),
                        fontSize = titleFontSize,
                        defaultState = toggledShowDate,
                        onCheckedChange = {
                            toggledShowDate = !prefs.showDate
                            prefs.showDate = toggledShowDate
                            viewModel.setShowDate(prefs.showDate)
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_day_of_year),
                        fontSize = titleFontSize,
                        defaultState = toggledShowDayOfYear,
                        onCheckedChange = {
                            toggledShowDayOfYear = !prefs.showDayOfYear
                            prefs.showDayOfYear = toggledShowDayOfYear
                            viewModel.setShowDayOfYear(prefs.showDayOfYear)
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_clock),
                        fontSize = titleFontSize,
                        defaultState = toggledShowClock,
                        onCheckedChange = {
                            toggledShowClock = !prefs.showClock
                            prefs.showClock = toggledShowClock
                            viewModel.setShowClock(prefs.showClock)
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_clock_format),
                        fontSize = titleFontSize,
                        defaultState = toggledShowClockFormat,
                        onCheckedChange = {
                            toggledShowClockFormat = !prefs.showClockFormat
                            prefs.showClockFormat = toggledShowClockFormat
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_alarm),
                        fontSize = titleFontSize,
                        defaultState = toggledShowAlarm,
                        onCheckedChange = {
                            toggledShowAlarm = !prefs.showAlarm
                            prefs.showAlarm = toggledShowAlarm
                            viewModel.setShowAlarm(prefs.showAlarm)
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_battery),
                        fontSize = titleFontSize,
                        defaultState = toggledShowBattery,
                        onCheckedChange = {
                            toggledShowBattery = !prefs.showBattery
                            prefs.showBattery = toggledShowBattery
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.show_battery_icon),
                        fontSize = titleFontSize,
                        defaultState = toggledShowBatteryIcon,
                        onCheckedChange = {
                            toggledShowBatteryIcon = !prefs.showBatteryIcon
                            prefs.showBatteryIcon = toggledShowBatteryIcon
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (toggledShowAlarm) {

                        // Alarm
                        SettingsTitle(
                            text = getLocalizedString(R.string.alarm_settings),
                            fontSize = titleFontSize
                        )


                        SettingsSelect(
                            title = getLocalizedString(R.string.bedtime_start_time),
                            option = String.format(
                                "%02d:%02d",
                                bedtimeStartHour,
                                bedtimeStartMinute
                            ),
                            fontSize = titleFontSize,
                            onClick = {
                                showBedtimeTimePicker(
                                    bedtimeStartHour,
                                    bedtimeStartMinute
                                ) { hour, minute ->
                                    bedtimeStartHour = hour
                                    bedtimeStartMinute = minute
                                    prefs.bedtimeStartHour = hour
                                    prefs.bedtimeStartMinute = minute
                                }
                            }
                        )

                        SettingsSelect(
                            title = getLocalizedString(R.string.bedtime_end_time),
                            option = String.format("%02d:%02d", bedtimeEndHour, bedtimeEndMinute),
                            fontSize = titleFontSize,
                            onClick = {
                                showBedtimeTimePicker(
                                    bedtimeEndHour,
                                    bedtimeEndMinute
                                ) { hour, minute ->
                                    bedtimeEndHour = hour
                                    bedtimeEndMinute = minute
                                    prefs.bedtimeEndHour = hour
                                    prefs.bedtimeEndMinute = minute
                                }
                            }
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                    }


                    }
                    SettingsTitle(
                        text = getLocalizedString(R.string.clock_layout),
                        fontSize = titleFontSize
                    )
                    val st = selectedClockStyle
                    var selectedRingPlacement by remember { mutableStateOf(prefs.ringPlacement) }
                    var selectedRingSize by remember { mutableStateOf(prefs.ringSize) }
                    var selectedRingInside by remember { mutableStateOf(prefs.ringInside) }
                    var selectedRingScale by remember { mutableStateOf(prefs.ringScale) }
                    var toggledClockRule by remember { mutableStateOf(prefs.clockRule) }
                    var toggledSwissSide by remember { mutableStateOf(prefs.swissSideColumn) }
                    var toggledSwissWeights by remember { mutableStateOf(prefs.swissWeights) }
                    var toggledAlarmBelow by remember { mutableStateOf(prefs.oneLineAlarmBelow) }
                    SectionCard {
                    if (st != Constants.ClockStyle.Rings) {
                        SettingsSelect(
                            title = getLocalizedString(R.string.clock_alignment),
                            option = selectedClockAlignment.string(),
                            fontSize = titleFontSize,
                            onClick = {
                                val gravityOptions = Constants.Gravity.entries.toTypedArray()
                                val selectedIndex =
                                    gravityOptions.indexOf(selectedClockAlignment).takeIf { it >= 0 }
                                        ?: 1

                                dialogBuilder.showSingleChoiceBottomSheetPill(
                                    context = requireContext(),
                                    options = gravityOptions,
                                    title = getLocalizedString(R.string.clock_alignment),
                                    selectedIndex = selectedIndex,
                                    onItemSelected = { newGravity ->
                                        selectedClockAlignment = newGravity // Update state
                                        prefs.clockAlignment =
                                            newGravity // Persist selection in preferences
                                        viewModel.updateClockAlignment(newGravity)
                                    }
                                )
                            }
                        )

                    }
                    if (st == Constants.ClockStyle.Classic) {
                        SettingsSelect(
                            title = getLocalizedString(R.string.date_alignment),
                            option = selectedDateAlignment.string(),
                            fontSize = titleFontSize,
                            onClick = {
                                val gravityOptions = Constants.Gravity.entries.toTypedArray()
                                val selectedIndex =
                                    gravityOptions.indexOf(selectedDateAlignment).takeIf { it >= 0 }
                                        ?: 1

                                dialogBuilder.showSingleChoiceBottomSheetPill(
                                    context = requireContext(),
                                    options = gravityOptions,
                                    title = getLocalizedString(R.string.date_alignment),
                                    selectedIndex = selectedIndex,
                                    onItemSelected = { newGravity ->
                                        selectedDateAlignment = newGravity // Update state
                                        prefs.dateAlignment =
                                            newGravity // Persist selection in preferences
                                        viewModel.updateDateAlignment(newGravity)
                                    }
                                )
                            }
                        )

                        SettingsSelect(
                            title = getLocalizedString(R.string.alarm_alignment),
                            option = selectedAlarmAlignment.string(),
                            fontSize = titleFontSize,
                            onClick = {
                                val gravityOptions = Constants.Gravity.entries.toTypedArray()
                                val selectedIndex =
                                    gravityOptions.indexOf(selectedAlarmAlignment).takeIf { it >= 0 }
                                        ?: 1

                                dialogBuilder.showSingleChoiceBottomSheetPill(
                                    context = requireContext(),
                                    options = Constants.Gravity.entries.toTypedArray(),
                                    title = getLocalizedString(R.string.alarm_alignment),
                                    selectedIndex = selectedIndex,
                                    onItemSelected = { newGravity ->
                                        selectedAlarmAlignment = newGravity // Update state
                                        prefs.alarmAlignment =
                                            newGravity // Persist selection in preferences
                                        viewModel.updateAlarmAlignment(newGravity)
                                    }
                                )
                            }
                        )

                    }
                    if (st == Constants.ClockStyle.Swiss) {
                        SettingsSwitch(
                            text = getLocalizedString(R.string.swiss_side_column),
                            fontSize = titleFontSize,
                            defaultState = toggledSwissSide,
                            onCheckedChange = { toggledSwissSide = it; prefs.swissSideColumn = it }
                        )
                        SettingsSwitch(
                            text = getLocalizedString(R.string.swiss_weights),
                            fontSize = titleFontSize,
                            defaultState = toggledSwissWeights,
                            onCheckedChange = { toggledSwissWeights = it; prefs.swissWeights = it }
                        )
                    }
                    if (st == Constants.ClockStyle.Rings) {
                        SettingsSelect(
                            title = getLocalizedString(R.string.ring_placement),
                            option = selectedRingPlacement.string(),
                            fontSize = titleFontSize,
                            onClick = {
                                val options = Constants.RingPlacement.entries.toTypedArray()
                                dialogBuilder.showSingleChoiceBottomSheet(
                                    context = requireContext(),
                                    options = options,
                                    title = getLocalizedString(R.string.ring_placement),
                                    selectedIndex = options.indexOf(selectedRingPlacement),
                                    onItemSelected = { v -> selectedRingPlacement = v; prefs.ringPlacement = v }
                                )
                            }
                        )
                        SettingsSelect(
                            title = getLocalizedString(R.string.ring_size),
                            option = selectedRingSize.toString(),
                            fontSize = titleFontSize,
                            onClick = {
                                dialogBuilder.showSliderBottomSheet(
                                    context = requireContext(),
                                    title = getLocalizedString(R.string.ring_size),
                                    minValue = 100,
                                    maxValue = 240,
                                    currentValue = prefs.ringSize,
                                    onValueSelected = { v -> selectedRingSize = v.toInt(); prefs.ringSize = v.toInt() }
                                )
                            }
                        )
                        SettingsSelect(
                            title = getLocalizedString(R.string.ring_inside),
                            option = selectedRingInside.string(),
                            fontSize = titleFontSize,
                            onClick = {
                                val options = Constants.RingInside.entries.toTypedArray()
                                dialogBuilder.showSingleChoiceBottomSheet(
                                    context = requireContext(),
                                    options = options,
                                    title = getLocalizedString(R.string.ring_inside),
                                    selectedIndex = options.indexOf(selectedRingInside),
                                    onItemSelected = { v -> selectedRingInside = v; prefs.ringInside = v }
                                )
                            }
                        )
                        SettingsSelect(
                            title = getLocalizedString(R.string.ring_scale),
                            option = selectedRingScale.string(),
                            fontSize = titleFontSize,
                            onClick = {
                                val options = Constants.RingScale.entries.toTypedArray()
                                dialogBuilder.showSingleChoiceBottomSheet(
                                    context = requireContext(),
                                    options = options,
                                    title = getLocalizedString(R.string.ring_scale),
                                    selectedIndex = options.indexOf(selectedRingScale),
                                    onItemSelected = { v -> selectedRingScale = v; prefs.ringScale = v }
                                )
                            }
                        )
                    }
                    if (st == Constants.ClockStyle.OneLine) {
                        SettingsSwitch(
                            text = getLocalizedString(R.string.clock_rule),
                            fontSize = titleFontSize,
                            defaultState = toggledClockRule,
                            onCheckedChange = { toggledClockRule = it; prefs.clockRule = it }
                        )
                        SettingsSwitch(
                            text = getLocalizedString(R.string.one_line_alarm_below),
                            fontSize = titleFontSize,
                            defaultState = toggledAlarmBelow,
                            onCheckedChange = { toggledAlarmBelow = it; prefs.oneLineAlarmBelow = it }
                        )
                    }
                    }
                    SettingsTitle(
                        text = getLocalizedString(R.string.colour_and_size),
                        fontSize = titleFontSize
                    )
                    SectionCard {
                    val hexClockColor = String.format("#%06X", (0xFFFFFF and selectedClockColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.clock_color),
                        option = hexClockColor,
                        fontSize = titleFontSize,
                        optionColor = Color(hexClockColor.toColorInt()),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedClockColor,
                                title = getLocalizedString(R.string.clock_color),
                                onItemSelected = { selectedColor ->
                                    selectedClockColor = selectedColor
                                    prefs.clockColor = selectedColor
                                })
                        }
                    )

                    val hexDateColor = String.format("#%06X", (0xFFFFFF and selectedDateColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.date_color),
                        option = hexDateColor,
                        fontSize = titleFontSize,
                        optionColor = Color(hexDateColor.toColorInt()),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedDateColor,
                                title = getLocalizedString(R.string.date_color),
                                onItemSelected = { selectedColor ->
                                    selectedDateColor = selectedColor
                                    prefs.dateColor = selectedColor
                                })
                        }
                    )

                    val hexAlarmColor = String.format("#%06X", (0xFFFFFF and selectedAlarmColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.alarm_color),
                        option = hexAlarmColor,
                        fontSize = titleFontSize,
                        optionColor = Color(hexAlarmColor.toColorInt()),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedAlarmColor,
                                title = getLocalizedString(R.string.alarm_color),
                                onItemSelected = { selectedColor ->
                                    selectedAlarmColor = selectedColor
                                    prefs.alarmClockColor = selectedColor
                                })
                        }
                    )

                    val hexBatteryColor =
                        String.format("#%06X", (0xFFFFFF and selectedBatteryColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.battery_color),
                        option = hexBatteryColor,
                        fontSize = titleFontSize,
                        optionColor = Color(hexBatteryColor.toColorInt()),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedBatteryColor,
                                title = getLocalizedString(R.string.battery_color),
                                onItemSelected = { selectedColor ->
                                    selectedBatteryColor = selectedColor
                                    prefs.batteryColor = selectedColor
                                })
                        }
                    )

                    SettingsSelect(
                        title = getLocalizedString(R.string.clock_text_size),
                        option = selectedClockSize.toString(),
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSliderBottomSheet(
                                context = requireContext(),
                                title = getLocalizedString(R.string.clock_text_size),
                                minValue = Constants.MIN_CLOCK_DATE_SIZE,
                                maxValue = Constants.MAX_CLOCK_DATE_SIZE,
                                currentValue = prefs.clockSize,
                                onValueSelected = { newClockSize ->
                                    selectedClockSize = newClockSize.toInt() // Update state
                                    prefs.clockSize =
                                        newClockSize.toInt() // Persist selection in preferences
                                }
                            )
                        }
                    )

                    if (st != Constants.ClockStyle.OneLine) {
                        SettingsSelect(
                            title = getLocalizedString(R.string.date_text_size),
                            option = selectedDateSize.toString(),
                            fontSize = titleFontSize,
                            onClick = {
                                dialogBuilder.showSliderBottomSheet(
                                    context = requireContext(),
                                    title = getLocalizedString(R.string.date_text_size),
                                    minValue = Constants.MIN_CLOCK_DATE_SIZE,
                                    maxValue = Constants.MAX_CLOCK_DATE_SIZE,
                                    currentValue = prefs.dateSize,
                                    onValueSelected = { newDateSize ->
                                        selectedDateSize = newDateSize.toInt() // Update state
                                        prefs.dateSize =
                                            newDateSize.toInt() // Persist selection in preferences
                                    }
                                )
                            }
                        )

                    }

                    SettingsSelect(
                        title = getLocalizedString(R.string.alarm_text_size),
                        option = selectedAlarmSize.toString(),
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSliderBottomSheet(
                                context = requireContext(),
                                title = getLocalizedString(R.string.alarm_text_size),
                                minValue = Constants.MIN_ALARM_SIZE,
                                maxValue = Constants.MAX_ALARM_SIZE,
                                currentValue = prefs.alarmSize,
                                onValueSelected = { newDateSize ->
                                    selectedAlarmSize = newDateSize.toInt() // Update state
                                    prefs.alarmSize =
                                        newDateSize.toInt() // Persist selection in preferences
                                }
                            )
                        }
                    )

                    SettingsSelect(
                        title = getLocalizedString(R.string.battery_text_size),
                        option = selectedBatterySize.toString(),
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSliderBottomSheet(
                                context = requireContext(),
                                title = getLocalizedString(R.string.battery_text_size),
                                minValue = Constants.MIN_BATTERY_SIZE,
                                maxValue = Constants.MAX_BATTERY_SIZE,
                                currentValue = prefs.batterySize,
                                onValueSelected = { newBatterySize ->
                                    selectedBatterySize = newBatterySize.toInt() // Update state
                                    prefs.batterySize =
                                        newBatterySize.toInt() // Persist selection in preferences
                                }
                            )
                        }
                    )

                    }
                    val appLabelClickClockAction =
                        prefs.appClickClock.activityLabel.ifEmpty { "Clock" }
                    val appLabelClickDateAction =
                        prefs.appClickDate.activityLabel.ifEmpty { "Calendar" }
                    SettingsTitle(
                        text = getLocalizedString(R.string.clock_area_tap),
                        fontSize = titleFontSize
                    )
                    SectionCard {
                    SettingsSelect(
                        title = getLocalizedString(R.string.clock_click_app),
                        option = if (selectedClickClockAction == Action.OpenApp) {
                            "${getLocalizedString(R.string.open)} $appLabelClickClockAction"
                        } else {
                            selectedClickClockAction.string()
                        },
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = actionStrings,
                                title = getLocalizedString(R.string.clock_click_app),
                                onItemSelected = { newClickClock ->
                                    val selectedAction =
                                        actions.firstOrNull { it.getString() == newClickClock }
                                    if (selectedAction != null) {
                                        selectedClickClockAction =
                                            selectedAction // Store the enum itself
                                        setGesture(
                                            AppDrawerFlag.SetClickClock,
                                            selectedAction
                                        ) // Persist selection in preferences
                                    }
                                }
                            )
                        }
                    )

                    SettingsSelect(
                        title = getLocalizedString(R.string.date_click_app),
                        option = if (selectedClickDateAction == Action.OpenApp) {
                            "${getLocalizedString(R.string.open)} $appLabelClickDateAction"
                        } else {
                            selectedClickDateAction.string()
                        },
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = actionStrings,
                                title = getLocalizedString(R.string.date_click_app),
                                onItemSelected = { newClickDate ->
                                    val selectedAction =
                                        actions.firstOrNull { it.getString() == newClickDate }
                                    if (selectedAction != null) {
                                        selectedClickDateAction =
                                            selectedAction // Store the enum itself
                                        setGesture(
                                            AppDrawerFlag.SetClickDate,
                                            selectedAction
                                        ) // Persist selection in preferences
                                    }
                                }
                            )
                        }
                    )

                    }
                    if (isGestureNavigationEnabled(context)) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_gesture_nav)))
                    } else {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_3_button_nav)))
                    }
                }

                "gestures" -> {
                    BackHandler { currentScreen = "main" }
                    PageHeader(
                        iconRes = R.drawable.ic_back,
                        title = getLocalizedString(R.string.gestures_settings_title),
                        onClick = { currentScreen = "main" }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Tap & Click Actions
                    SettingsTitle(
                        text = getLocalizedString(R.string.tap_click_actions),
                        fontSize = titleFontSize
                    )
                    SectionCard {

                    val appLabelDoubleTapAction = prefs.appDoubleTap.activityLabel
                    SettingsSelect(
                        title = getLocalizedString(R.string.double_tap),
                        option = if (selectedDoubleTapAction == Action.OpenApp) {
                            "${getLocalizedString(R.string.open)} $appLabelDoubleTapAction"
                        } else {
                            selectedDoubleTapAction.string()
                        },
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = actionStrings,
                                title = getLocalizedString(R.string.double_tap),
                                onItemSelected = { newDoubleTapAction ->
                                    val selectedAction =
                                        actions.firstOrNull { it.getString() == newDoubleTapAction }
                                    if (selectedAction != null) {
                                        selectedDoubleTapAction =
                                            selectedAction // Store the enum itself
                                        setGesture(
                                            AppDrawerFlag.SetDoubleTap,
                                            selectedAction
                                        ) // Persist selection in preferences
                                    }
                                }
                            )
                        }
                    )

                    val appLabelClickClockAction =
                        prefs.appClickClock.activityLabel.ifEmpty { "Clock" }
                    val appLabelClickDateAction =
                        prefs.appClickDate.activityLabel.ifEmpty { "Calendar" }
                    val appLabelClickAppUsageAction =
                        prefs.appClickUsage.activityLabel.ifEmpty { "Digital Wellbeing" }
                    SettingsSelect(
                        title = getLocalizedString(R.string.usage_click_app),
                        option = if (selectedClickAppUsageAction == Action.OpenApp) {
                            "${getLocalizedString(R.string.open)} $appLabelClickAppUsageAction"
                        } else {
                            selectedClickAppUsageAction.string()
                        },
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = actionStrings,
                                title = getLocalizedString(R.string.usage_click_app),
                                onItemSelected = { newClickAppUsage ->
                                    val selectedAction =
                                        actions.firstOrNull { it.getString() == newClickAppUsage }
                                    if (selectedAction != null) {
                                        selectedClickAppUsageAction =
                                            selectedAction // Store the enum itself
                                        setGesture(
                                            AppDrawerFlag.SetAppUsage,
                                            selectedAction
                                        ) // Persist selection in preferences
                                    }
                                }
                            )
                        }
                    )

                    val appLabelClickFloatingAction =
                        prefs.appFloating.activityLabel.ifEmpty { "Notes" }
                    SettingsSelect(
                        title = getLocalizedString(R.string.floating_click_app),
                        option = if (selectedClickFloatingAction == Action.OpenApp) {
                            "${getLocalizedString(R.string.open)} $appLabelClickFloatingAction"
                        } else {
                            selectedClickFloatingAction.string()
                        },
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = actionStrings,
                                title = getLocalizedString(R.string.floating_click_app),
                                onItemSelected = { newClickFloating ->
                                    val selectedAction =
                                        actions.firstOrNull { it.getString() == newClickFloating }
                                    if (selectedAction != null) {
                                        selectedClickFloatingAction =
                                            selectedAction // Store the enum itself
                                        setGesture(
                                            AppDrawerFlag.SetFloating,
                                            selectedAction
                                        ) // Persist selection in preferences
                                    }
                                }
                            )
                        }
                    )

                    }
                    // Swipe Actions
                    SettingsTitle(
                        text = getLocalizedString(R.string.swipe_movement),
                        fontSize = titleFontSize,
                    )
                    SectionCard {

                    val appLabelShortSwipeUpAction =
                        prefs.appShortSwipeUp.activityLabel.ifEmpty { "Settings" }
                    SettingsSelect(
                        title = getLocalizedString(R.string.short_swipe_up_app),
                        option = if (selectedShortSwipeUpAction == Action.OpenApp) {
                            "${getLocalizedString(R.string.open)} $appLabelShortSwipeUpAction"
                        } else {
                            selectedShortSwipeUpAction.string()
                        },
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = actionStrings,
                                title = getLocalizedString(R.string.short_swipe_up_app),
                                onItemSelected = { newShortSwipeUpAction ->

                                    val selectedAction =
                                        actions.firstOrNull { it.getString() == newShortSwipeUpAction }
                                    if (selectedAction != null) {
                                        selectedShortSwipeUpAction =
                                            selectedAction // Store the enum itself
                                        setGesture(
                                            AppDrawerFlag.SetShortSwipeUp,
                                            selectedAction
                                        ) // Persist selection in preferences
                                    }
                                }
                            )
                        }
                    )

                    val appLabelShortSwipeDownAction =
                        prefs.appShortSwipeDown.activityLabel.ifEmpty { "Phone" }
                    SettingsSelect(
                        title = getLocalizedString(R.string.short_swipe_down_app),
                        option = if (selectedShortSwipeDownAction == Action.OpenApp) {
                            "${getLocalizedString(R.string.open)} $appLabelShortSwipeDownAction"
                        } else {
                            selectedShortSwipeDownAction.string()
                        },
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = actionStrings,
                                title = getLocalizedString(R.string.short_swipe_down_app),
                                onItemSelected = { newShortSwipeDownAction ->
                                    val selectedAction =
                                        actions.firstOrNull { it.getString() == newShortSwipeDownAction }
                                    if (selectedAction != null) {
                                        selectedShortSwipeDownAction =
                                            selectedAction // Store the enum itself
                                        setGesture(
                                            AppDrawerFlag.SetShortSwipeDown,
                                            selectedAction
                                        ) // Persist selection in preferences
                                    }
                                }
                            )
                        }
                    )

                    val appLabelShortSwipeLeftAction =
                        prefs.appShortSwipeLeft.activityLabel.ifEmpty { "Settings" }
                    SettingsSelect(
                        title = getLocalizedString(R.string.short_swipe_left_app),
                        option = if (selectedShortSwipeLeftAction == Action.OpenApp) {
                            "${getLocalizedString(R.string.open)} $appLabelShortSwipeLeftAction"
                        } else {
                            selectedShortSwipeLeftAction.string()
                        },
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = actionStrings,
                                title = getLocalizedString(R.string.short_swipe_left_app),
                                onItemSelected = { newShortSwipeLeftAction ->
                                    val selectedAction =
                                        actions.firstOrNull { it.getString() == newShortSwipeLeftAction }
                                    if (selectedAction != null) {
                                        selectedShortSwipeLeftAction =
                                            selectedAction // Store the enum itself
                                        setGesture(
                                            AppDrawerFlag.SetShortSwipeLeft,
                                            selectedAction
                                        ) // Persist selection in preferences
                                    }
                                }
                            )
                        }
                    )

                    val appLabelShortSwipeRightAction =
                        prefs.appShortSwipeRight.activityLabel.ifEmpty { "Phone" }
                    SettingsSelect(
                        title = getLocalizedString(R.string.short_swipe_right_app),
                        option = if (selectedShortSwipeRightAction == Action.OpenApp) {
                            "${getLocalizedString(R.string.open)} $appLabelShortSwipeRightAction"
                        } else {
                            selectedShortSwipeRightAction.string()
                        },
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = actionStrings,
                                title = getLocalizedString(R.string.short_swipe_right_app),
                                onItemSelected = { newShortSwipeRightAction ->
                                    val selectedAction =
                                        actions.firstOrNull { it.getString() == newShortSwipeRightAction }
                                    if (selectedAction != null) {
                                        selectedShortSwipeRightAction =
                                            selectedAction // Store the enum itself
                                        setGesture(
                                            AppDrawerFlag.SetShortSwipeRight,
                                            selectedAction
                                        ) // Persist selection in preferences
                                    }
                                }
                            )
                        }
                    )

                    val appLabelLongSwipeUpAction =
                        prefs.appLongSwipeUp.activityLabel.ifEmpty { "Settings" }
                    SettingsSelect(
                        title = getLocalizedString(R.string.long_swipe_up_app),
                        option = if (selectedLongSwipeUpAction == Action.OpenApp) {
                            "${getLocalizedString(R.string.open)} $appLabelLongSwipeUpAction"
                        } else {
                            selectedLongSwipeUpAction.string()
                        },
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = actionStrings,
                                title = getLocalizedString(R.string.long_swipe_up_app),
                                onItemSelected = { newLongSwipeUpAction ->
                                    val selectedAction =
                                        actions.firstOrNull { it.getString() == newLongSwipeUpAction }
                                    if (selectedAction != null) {
                                        selectedLongSwipeUpAction =
                                            selectedAction // Store the enum itself
                                        setGesture(
                                            AppDrawerFlag.SetLongSwipeUp,
                                            selectedAction
                                        ) // Persist selection in preferences
                                    }
                                }
                            )
                        }
                    )

                    val appLabelLongSwipeDownAction =
                        prefs.appLongSwipeDown.activityLabel.ifEmpty { "Phone" }
                    SettingsSelect(
                        title = getLocalizedString(R.string.long_swipe_down_app),
                        option = if (selectedLongSwipeDownAction == Action.OpenApp) {
                            "${getLocalizedString(R.string.open)} $appLabelLongSwipeDownAction"
                        } else {
                            selectedLongSwipeDownAction.string()
                        },
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = actionStrings,
                                title = getLocalizedString(R.string.long_swipe_down_app),
                                onItemSelected = { newLongSwipeDownAction ->
                                    val selectedAction =
                                        actions.firstOrNull { it.getString() == newLongSwipeDownAction }
                                    if (selectedAction != null) {
                                        selectedLongSwipeDownAction =
                                            selectedAction // Store the enum itself
                                        setGesture(
                                            AppDrawerFlag.SetLongSwipeDown,
                                            selectedAction
                                        ) // Persist selection in preferences
                                    }
                                }
                            )
                        }
                    )

                    val appLabelLongSwipeLeftAction =
                        prefs.appLongSwipeLeft.activityLabel.ifEmpty { "Settings" }
                    SettingsSelect(
                        title = getLocalizedString(R.string.long_swipe_left_app),
                        option = if (selectedLongSwipeLeftAction == Action.OpenApp) {
                            "${getLocalizedString(R.string.open)} $appLabelLongSwipeLeftAction"
                        } else {
                            selectedLongSwipeLeftAction.string()
                        },
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = actionStrings, // Pass the list of localized strings
                                title = getLocalizedString(R.string.long_swipe_left_app),
                                onItemSelected = { newLongSwipeLeftAction ->
                                    val selectedAction =
                                        actions.firstOrNull { it.getString() == newLongSwipeLeftAction }
                                    if (selectedAction != null) {
                                        selectedLongSwipeLeftAction =
                                            selectedAction // Store the enum itself
                                        setGesture(
                                            AppDrawerFlag.SetLongSwipeLeft,
                                            selectedAction
                                        ) // Persist selection in preferences
                                    }
                                }
                            )
                        }
                    )

                    val appLabelLongSwipeRightAction =
                        prefs.appLongSwipeRight.activityLabel.ifEmpty { "Phone" }
                    SettingsSelect(
                        title = getLocalizedString(R.string.long_swipe_right_app),
                        option = if (selectedLongSwipeRightAction == Action.OpenApp) {
                            "${getLocalizedString(R.string.open)} $appLabelLongSwipeRightAction"
                        } else {
                            selectedLongSwipeRightAction.string()
                        },
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = actionStrings,
                                title = getLocalizedString(R.string.long_swipe_right_app),
                                onItemSelected = { newLongSwipeRightAction ->
                                    val selectedAction =
                                        actions.firstOrNull { it.getString() == newLongSwipeRightAction }
                                    if (selectedAction != null) {
                                        selectedLongSwipeRightAction =
                                            selectedAction // Store the enum itself
                                        setGesture(
                                            AppDrawerFlag.SetLongSwipeRight,
                                            selectedAction
                                        ) // Persist selection in preferences
                                    }
                                }
                            )
                        }
                    )

                    }
                    // Thresholds
                    SettingsTitle(
                        text = getLocalizedString(R.string.threshold),
                        fontSize = titleFontSize
                    )
                    SectionCard {

                    SettingsSelect(
                        title = getLocalizedString(R.string.settings_short_threshold),
                        option = selectedShortSwipeThreshold.toString(),
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSliderBottomSheet(
                                context = context,
                                title = getLocalizedString(R.string.settings_short_threshold),
                                minValue = Constants.MIN_THRESHOLD,
                                maxValue = selectedLongSwipeThreshold,
                                currentValue = prefs.shortSwipeThreshold,
                                onValueSelected = { newSettingsSize ->
                                    selectedShortSwipeThreshold = newSettingsSize.toFloat()
                                    prefs.shortSwipeThreshold = newSettingsSize.toFloat()
                                }
                            )
                        }
                    )

                    SettingsSelect(
                        title = getLocalizedString(R.string.settings_long_threshold),
                        option = selectedLongSwipeThreshold.toString(),
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSliderBottomSheet(
                                context = context,
                                title = getLocalizedString(R.string.settings_long_threshold),
                                minValue = selectedShortSwipeThreshold,
                                maxValue = Constants.MAX_THRESHOLD,
                                currentValue = prefs.longSwipeThreshold,
                                onValueSelected = { newSettingsSize ->
                                    selectedLongSwipeThreshold = newSettingsSize.toFloat()
                                    prefs.longSwipeThreshold = newSettingsSize.toFloat()
                                }
                            )
                        }
                    )

                    }
                    if (isGestureNavigationEnabled(context)) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_gesture_nav)))
                    } else {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_3_button_nav)))
                    }
                
                }

                "notes" -> {
                    BackHandler { currentScreen = "main" }
                    PageHeader(
                        iconRes = R.drawable.ic_back,
                        title = getLocalizedString(R.string.notes_settings_title),
                        onClick = { currentScreen = "main" }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    // Display
                    SettingsTitle(
                        text = getLocalizedString(R.string.display_options),
                        fontSize = titleFontSize
                    )
                    SectionCard {

                    SettingsSwitch(
                        text = getLocalizedString(R.string.auto_expand_notes),
                        fontSize = titleFontSize,
                        defaultState = toggledAutoExpandNotes,
                        onCheckedChange = {
                            toggledAutoExpandNotes = !prefs.autoExpandNotes
                            prefs.autoExpandNotes = toggledAutoExpandNotes
                        }
                    )

                    SettingsSwitch(
                        text = getLocalizedString(R.string.click_to_edit_delete),
                        fontSize = titleFontSize,
                        defaultState = toggledClickToEditDelete,
                        onCheckedChange = {
                            toggledClickToEditDelete = !prefs.clickToEditDelete
                            prefs.clickToEditDelete = toggledClickToEditDelete
                        }
                    )

                    }
                    // Notes Colors
                    SettingsTitle(
                        text = getLocalizedString(R.string.notes_colors),
                        fontSize = titleFontSize
                    )
                    SectionCard {

                    val hexBackgroundColor =
                        String.format("#%06X", (0xFFFFFF and selectedNotesBackgroundColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.notes_background_color),
                        option = hexBackgroundColor,
                        fontSize = titleFontSize,
                        optionColor = Color(hexBackgroundColor.toColorInt()),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedNotesBackgroundColor,
                                title = getLocalizedString(R.string.notes_background_color),
                                onItemSelected = { selectedColor ->
                                    selectedNotesBackgroundColor = selectedColor
                                    prefs.notesBackgroundColor = selectedColor
                                })
                        }
                    )

                    val hexBubbleBackgroundColor =
                        String.format("#%06X", (0xFFFFFF and selectedBubbleBackgroundColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.bubble_background_color),
                        option = hexBubbleBackgroundColor,
                        fontSize = titleFontSize,
                        optionColor = Color(hexBubbleBackgroundColor.toColorInt()),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedBubbleBackgroundColor,
                                title = getLocalizedString(R.string.bubble_background_color),
                                onItemSelected = { selectedColor ->
                                    selectedBubbleBackgroundColor = selectedColor
                                    prefs.bubbleBackgroundColor = selectedColor
                                })
                        }
                    )

                    val hexMessageTextColor =
                        String.format("#%06X", (0xFFFFFF and selectedBubbleMessageTextColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.bubble_message_color),
                        option = hexMessageTextColor,
                        fontSize = titleFontSize,
                        optionColor = Color(hexMessageTextColor.toColorInt()),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedBubbleMessageTextColor,
                                title = getLocalizedString(R.string.bubble_message_color),
                                onItemSelected = { selectedColor ->
                                    selectedBubbleMessageTextColor = selectedColor
                                    prefs.bubbleMessageTextColor = selectedColor
                                })
                        }
                    )

                    val hexBubbleTimeDateColor =
                        String.format("#%06X", (0xFFFFFF and selectedBubbleTimeDateColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.bubble_date_time_color),
                        option = hexBubbleTimeDateColor,
                        fontSize = titleFontSize,
                        optionColor = Color(hexBubbleTimeDateColor.toColorInt()),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedBubbleTimeDateColor,
                                title = getLocalizedString(R.string.bubble_date_time_color),
                                onItemSelected = { selectedColor ->
                                    selectedBubbleTimeDateColor = selectedColor
                                    prefs.bubbleTimeDateColor = selectedColor
                                })
                        }
                    )

                    val hexBubbleCategoryColor =
                        String.format("#%06X", (0xFFFFFF and selectedBubbleCategoryColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.bubble_category_color),
                        option = hexBubbleCategoryColor,
                        fontSize = titleFontSize,
                        optionColor = Color(hexBubbleCategoryColor.toColorInt()),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedBubbleCategoryColor,
                                title = getLocalizedString(R.string.bubble_category_color),
                                onItemSelected = { selectedColor ->
                                    selectedBubbleCategoryColor = selectedColor
                                    prefs.bubbleCategoryColor = selectedColor
                                })
                        }
                    )

                    }
                    // Input Colors
                    SettingsTitle(
                        text = getLocalizedString(R.string.input_colors),
                        fontSize = titleFontSize
                    )
                    SectionCard {

                    val hexBubbleInputMessageColor =
                        String.format("#%06X", (0xFFFFFF and selectedInputMessageColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.message_input_color),
                        option = hexBubbleInputMessageColor,
                        fontSize = titleFontSize,
                        optionColor = Color(hexBubbleInputMessageColor.toColorInt()),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedInputMessageColor,
                                title = getLocalizedString(R.string.message_input_color),
                                onItemSelected = { selectedColor ->
                                    selectedInputMessageColor = selectedColor
                                    prefs.inputMessageColor = selectedColor
                                })
                        }
                    )

                    val hexBubbleInputMessageHintColor =
                        String.format("#%06X", (0xFFFFFF and selectedInputMessageHintColor))
                    SettingsSelect(
                        title = getLocalizedString(R.string.message_input_hint_color),
                        option = hexBubbleInputMessageHintColor,
                        fontSize = titleFontSize,
                        optionColor = Color(hexBubbleInputMessageHintColor.toColorInt()),
                        onClick = {
                            dialogBuilder.showColorPickerBottomSheet(
                                context = requireContext(),
                                color = selectedInputMessageHintColor,
                                title = getLocalizedString(R.string.message_input_hint_color),
                                onItemSelected = { selectedColor ->
                                    selectedInputMessageHintColor = selectedColor
                                    prefs.inputMessageHintColor = selectedColor
                                })
                        }
                    )

                    }
                    if (isGestureNavigationEnabled(context)) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_gesture_nav)))
                    } else {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_3_button_nav)))
                    }
                
                }

                "advanced" -> {
                    BackHandler { currentScreen = "main" }
                    PageHeader(
                        iconRes = R.drawable.ic_back,
                        title = getLocalizedString(R.string.advanced_settings_title),
                        onClick = {
                            currentScreen = "main"
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val versionName = getLocalizedString(R.string.app_version)

                    // Actions as a ruled list
                    SettingsTitle(
                        text = getLocalizedString(R.string.settings_actions_section),
                        fontSize = titleFontSize
                    )
                    SectionCard {
                        SettingsLink(title = getLocalizedString(R.string.advanced_settings_app_info_title), fontSize = titleFontSize) {
                            openAppInfo(requireContext(), Process.myUserHandle(), BuildConfig.APPLICATION_ID)
                        }
                        SettingsLink(title = getLocalizedString(changeLauncherText), fontSize = titleFontSize) {
                            viewModel.resetDefaultLauncherApp(requireContext())
                        }
                        SettingsLink(title = getLocalizedString(R.string.advanced_settings_restart_title), fontSize = titleFontSize) {
                            AppReloader.restartApp(requireContext())
                        }
                        SettingsLink(title = getLocalizedString(R.string.settings_exit_launcher_title), fontSize = titleFontSize) {
                            exitLauncher(requireContext())
                        }
                        SettingsLink(title = getLocalizedString(R.string.advanced_settings_backup_restore_title), fontSize = titleFontSize) {
                            dialogBuilder.showBackupRestoreBottomSheet()
                        }
                        SettingsLink(title = getLocalizedString(R.string.advanced_settings_theme_title), fontSize = titleFontSize) {
                            dialogBuilder.showSaveLoadThemeBottomSheet()
                        }
                        SettingsLink(title = getLocalizedString(R.string.advanced_settings_help_feedback_title), fontSize = titleFontSize) {
                            currentScreen = "help"
                        }
                    }

                    SectionCard {



                    // Personalization
                    }
                    SettingsTitle(
                        text = getLocalizedString(R.string.settings_general_section),
                        fontSize = titleFontSize,
                    )
                    SectionCard {
                    SettingsSelect(
                        title = getLocalizedString(R.string.app_language),
                        option = selectedLanguage.getString(), // assuming selectedLanguage: Constants.Language
                        fontSize = titleFontSize,
                        onClick = {
                            // Generate options
                            val languageEntries = Constants.Language.entries

                            val languageOptions =
                                languageEntries.map { it.getString() } // get localized names to display

                            // Determine selected index based on current prefs value
                            val selectedIndex =
                                languageEntries.indexOf(selectedLanguage).takeIf { it >= 0 } ?: 1

                            dialogBuilder.showSingleChoiceBottomSheet(
                                context = requireContext(),
                                options = languageOptions.toTypedArray(),
                                title = getLocalizedString(R.string.app_language),
                                selectedIndex = selectedIndex,
                                onItemSelected = { newLanguageName ->
                                    // Find the actual enum by matching its localized name
                                    val newLanguageIndex =
                                        languageOptions.indexOfFirst { it == newLanguageName }
                                    if (newLanguageIndex != -1) {
                                        val newLanguage = languageEntries[newLanguageIndex]
                                        selectedLanguage = newLanguage // Update state
                                        prefs.appLanguage = newLanguage // Persist in preferences
                                        LauncherLocaleManager.updateLanguage(
                                            requireContext(),
                                            newLanguage
                                        )
                                        LocalizedResources.invalidate()
                                        reloadLauncher() // force reload with new language
                                    }
                                }
                            )
                        }
                    )

                    SettingsSelect(
                        title = getLocalizedString(R.string.settings_text_size),
                        option = selectedSettingsSize.toString(),
                        fontSize = titleFontSize,
                        onClick = {
                            dialogBuilder.showSliderBottomSheet(
                                context = context,
                                title = getLocalizedString(R.string.settings_text_size),
                                minValue = Constants.MIN_TEXT_SIZE,
                                maxValue = Constants.MAX_TEXT_SIZE,
                                currentValue = prefs.settingsSize,
                                onValueSelected = { newSettingsSize ->
                                    selectedSettingsSize = newSettingsSize.toInt()
                                    prefs.settingsSize = newSettingsSize.toInt()
                                }
                            )
                        }
                    )
                    SettingsSwitch(
                        text = getLocalizedString(R.string.lock_orientation),
                        fontSize = titleFontSize,
                        defaultState = toggledLockOrientation,
                        onCheckedChange = {
                            toggledLockOrientation = !prefs.lockOrientation
                            prefs.lockOrientation = toggledLockOrientation

                            val currentOrientation = resources.configuration.orientation
                            prefs.lockOrientationPortrait =
                                currentOrientation == Configuration.ORIENTATION_PORTRAIT
                            AppReloader.restartApp(requireContext())
                        }
                    )

                    if (requireContext().isBiometricEnabled()) {
                        SettingsSwitch(
                            text = getLocalizedString(R.string.lock_settings),
                            fontSize = titleFontSize,
                            defaultState = toggledSettingsLocked,

                            onCheckedChange = {
                                toggledSettingsLocked = !prefs.settingsLocked
                                prefs.settingsLocked = toggledSettingsLocked
                            }
                        )
                    }

                    SettingsSwitch(
                        text = getLocalizedString(R.string.haptic_feedback),
                        fontSize = titleFontSize,
                        defaultState = toggledHapticFeedback,
                        onCheckedChange = {
                            toggledHapticFeedback = !prefs.hapticFeedback
                            prefs.hapticFeedback = toggledHapticFeedback
                        }
                    )


                    }
                

                    if (isGestureNavigationEnabled(context)) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_gesture_nav)))
                    } else {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_3_button_nav)))
                    }
                }

                "help" -> {
                    BackHandler { currentScreen = "advanced" }
                    PageHeader(
                        iconRes = R.drawable.ic_back,
                        title = getLocalizedString(R.string.advanced_settings_help_feedback_title),
                        onClick = { currentScreen = "advanced" }
                    )
                    SettingsTitle(text = getLocalizedString(R.string.help_title), fontSize = titleFontSize)
                    SectionCard {
                        AboutRow(title = "", lines = listOf(getLocalizedString(R.string.help_text)), titleFontSize = titleFontSize, descriptionFontSize = descriptionFontSize)
                        SettingsLink(title = getLocalizedString(R.string.about_github) + " ↗", fontSize = (titleFontSize.value * 0.85f).sp) {
                            requireContext().openUrl(getLocalizedString(R.string.about_github_url))
                        }
                        SettingsLink(title = "remaker.ch ↗", fontSize = (titleFontSize.value * 0.85f).sp) {
                            requireContext().openUrl("https://remaker.ch")
                        }
                    }
                    SettingsTitle(text = getLocalizedString(R.string.feedback_title), fontSize = titleFontSize)
                    SectionCard {
                        AboutRow(title = "", lines = listOf(getLocalizedString(R.string.feedback_text)), titleFontSize = titleFontSize, descriptionFontSize = descriptionFontSize)
                        SettingsLink(title = getLocalizedString(R.string.feedback_open_issue) + " ↗", fontSize = (titleFontSize.value * 0.85f).sp) {
                            requireContext().openUrl(getLocalizedString(R.string.about_issues_url))
                        }
                        // The address is plain text on purpose: nothing in the app sends mail by itself
                        AboutRow(title = getLocalizedString(R.string.feedback_by_mail), lines = listOf(getLocalizedString(R.string.acra_email)), titleFontSize = (titleFontSize.value * 0.85f).sp, descriptionFontSize = descriptionFontSize)
                    }
                    SettingsTitle(text = getLocalizedString(R.string.security_title), fontSize = titleFontSize)
                    SectionCard {
                        AboutRow(title = "", lines = listOf(getLocalizedString(R.string.security_text)), titleFontSize = titleFontSize, descriptionFontSize = descriptionFontSize)
                    }
                    if (isGestureNavigationEnabled(context)) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_gesture_nav)))
                    } else {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_3_button_nav)))
                    }
                }

                "about" -> {
                    BackHandler {
                        currentScreen = "main"
                    }

                    PageHeader(
                        iconRes = R.drawable.ic_back,
                        title = getLocalizedString(R.string.about_short),
                        onClick = {
                            currentScreen = "main"
                        }
                    )

                    val titleColor = SettingsTheme.typography.title.color
                    val mutedColor = SettingsTheme.typography.option.color
                    val capsStyle = TextStyle(letterSpacing = 0.16.em)
                    val versionLine = BuildConfig.VERSION_NAME.replace(" Build ", " · Build ") + if (BuildConfig.DEBUG) " · debug" else ""
                    val contributorCount = remember { readAsset("contributors.txt").lines().count { it.isNotBlank() } }

                    // Logo rule: the RE* mark on a white tile in the light theme, on a dark tile in
                    // the dark theme (drawable / drawable-night), the same everywhere in the app.
                    Spacer(modifier = Modifier.height(20.dp))
                    Image(
                        painter = painterResource(R.drawable.app_launcher),
                        contentDescription = null,
                        modifier = Modifier
                            .size(160.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    // Name: light "RE", red star, medium "start" — the largest text on the page
                    FontText(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append("RE") }
                            withStyle(SpanStyle(fontWeight = FontWeight.Light, color = Color(0xFFE2001A))) { append("*") }
                            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append("start") }
                        },
                        fontSize = (titleFontSize.value * 1.7f).sp,
                        color = titleColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                    )
                    FontText(
                        text = getLocalizedString(R.string.about_developer),
                        fontSize = (descriptionFontSize.value * 0.9f).sp,
                        color = titleColor,
                        fontWeight = FontWeight.Medium,
                        style = capsStyle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                    FontText(
                        text = versionLine.uppercase(),
                        fontSize = (descriptionFontSize.value * 0.8f).sp,
                        color = mutedColor,
                        style = capsStyle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )

                    // Centred between the version line and the colophon at the bottom
                    Spacer(modifier = Modifier.weight(1f))
                    FontText(
                        text = getLocalizedString(R.string.about_foundation_text),
                        fontSize = descriptionFontSize,
                        color = mutedColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 0.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AboutCard(
                            number = contributorCount.toString(),
                            star = true,
                            label = getLocalizedString(R.string.about_contributors),
                            sub = "Olauncher → OlauncherCF → mLauncher → RE*start",
                            go = getLocalizedString(R.string.credits_thanks),
                            titleFontSize = titleFontSize,
                            descriptionFontSize = descriptionFontSize,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) { currentScreen = "credits" }
                        AboutCard(
                            number = "4",
                            star = false,
                            label = getLocalizedString(R.string.credits_licences),
                            sub = getLocalizedString(R.string.about_licence_list),
                            go = getLocalizedString(R.string.about_full_texts),
                            titleFontSize = titleFontSize,
                            descriptionFontSize = descriptionFontSize,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) { currentScreen = "licences" }
                    }
                    Spacer(modifier = Modifier.weight(1f))

                    // Colophon: a short rule, the repository (opens the browser), the address as plain text.
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(40.dp)
                            .height(1.dp)
                            .background(outlineColor())
                    )
                    FontText(
                        text = getLocalizedString(R.string.about_github) + " ↗",
                        fontSize = titleFontSize,
                        color = titleColor,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { requireContext().openUrl(getLocalizedString(R.string.about_github_url)) }
                            .padding(top = 14.dp, bottom = 6.dp)
                    )
                    FontText(
                        text = getLocalizedString(R.string.acra_email),
                        fontSize = (titleFontSize.value * 0.9f).sp,
                        color = titleColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 18.dp)
                    )
                    if (isGestureNavigationEnabled(context)) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_gesture_nav)))
                    } else {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_3_button_nav)))
                    }
                }

                "credits" -> {
                    BackHandler { currentScreen = "about" }
                    PageHeader(
                        iconRes = R.drawable.ic_back,
                        title = getLocalizedString(R.string.about_contributors),
                        onClick = { currentScreen = "about" }
                    )
                    val contributors = remember { readAsset("contributors.txt").lines().filter { it.isNotBlank() } }

                    SettingsTitle(text = "Multi Launcher", fontSize = titleFontSize)
                    SectionCard {
                        AboutRow(
                            title = "",
                            lines = listOf(getLocalizedString(R.string.credits_lineage)),
                            titleFontSize = titleFontSize,
                            descriptionFontSize = descriptionFontSize
                        )
                    }
                    SettingsTitle(text = getLocalizedString(R.string.credits_thanks), fontSize = titleFontSize)
                    SectionCard {
                        AboutRow(
                            title = "",
                            lines = listOf(getLocalizedString(R.string.credits_thanks_intro)),
                            titleFontSize = titleFontSize,
                            descriptionFontSize = descriptionFontSize
                        )
                        // One name per row, numbered: the register of everyone up to the fork point
                        contributors.forEachIndexed { i, name ->
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
                                FontText(
                                    text = (i + 1).toString().padStart(2, '0'),
                                    fontSize = (descriptionFontSize.value * 0.8f).sp,
                                    color = SettingsTheme.typography.option.color,
                                    style = TextStyle(letterSpacing = 0.1.em),
                                    modifier = Modifier.width(40.dp)
                                )
                                FontText(text = name, fontSize = descriptionFontSize, color = SettingsTheme.typography.title.color)
                            }
                        }
                        AboutRow(
                            title = "",
                            lines = listOf(getLocalizedString(R.string.credits_thanks_outro)),
                            titleFontSize = titleFontSize,
                            descriptionFontSize = descriptionFontSize
                        )
                    }
                    if (isGestureNavigationEnabled(context)) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_gesture_nav)))
                    } else {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_3_button_nav)))
                    }
                }

                "licences" -> {
                    BackHandler { currentScreen = "about" }
                    PageHeader(
                        iconRes = R.drawable.ic_back,
                        title = getLocalizedString(R.string.credits_licences),
                        onClick = { currentScreen = "about" }
                    )
                    val licences = listOf(
                        R.string.licence_gpl to "licenses/GPL-3.0.txt",
                        R.string.licence_ofl to "fonts/OFL-Montserrat.txt",
                        R.string.licence_ccby to "licenses/CC-BY-4.0.txt",
                        R.string.licence_apache to "licenses/Apache-2.0.txt"
                    )
                    var openLicence by remember { mutableStateOf<String?>(null) }

                    Spacer(modifier = Modifier.height(10.dp))
                    SectionCard {
                        licences.forEach { (nameRes, asset) ->
                            SettingsLink(
                                title = getLocalizedString(nameRes),
                                fontSize = (titleFontSize.value * 0.85f).sp,
                                onClick = { openLicence = if (openLicence == asset) null else asset }
                            )
                            if (openLicence == asset) {
                                val text = remember(asset) {
                                    if (asset == "fonts/OFL-Montserrat.txt") readAsset(asset) + "\n\n" + readAsset("fonts/OFL-Doto.txt") else readAsset(asset)
                                }
                                FontText(
                                    text = text,
                                    fontSize = (descriptionFontSize.value * 0.85f).sp,
                                    color = SettingsTheme.typography.option.color,
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                    if (isGestureNavigationEnabled(context)) {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_gesture_nav)))
                    } else {
                        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_margin_3_button_nav)))
                    }
                }


            }
        }
    }

    /** Four clock styles stacked, each drawn with the current time; the chosen one carries a 2 dp frame. */
    @Composable
    private fun ClockStylePicker(
        selected: Constants.ClockStyle,
        clockColor: Int,
        dateColor: Int,
        clockSize: Int,
        dateSize: Int,
        alignment: Constants.Gravity,
        titleFontSize: TextUnit,
        descriptionFontSize: TextUnit,
        onSelect: (Constants.ClockStyle) -> Unit
    ) {
        // The previews follow the live settings below them: colour, size and alignment change at once
        val ink = Color(clockColor)
        val muted = Color(dateColor)
        val frame = SettingsTheme.typography.title.color
        val label = SettingsTheme.typography.title.color
        val labelMuted = SettingsTheme.typography.option.color
        val clockSp = (clockSize * 0.55f).sp          // the home clock scaled to the card
        val dateSp = (dateSize * 0.55f).sp
        val align = when (alignment) {
            Constants.Gravity.Left -> Alignment.Start
            Constants.Gravity.Center -> Alignment.CenterHorizontally
            Constants.Gravity.Right -> Alignment.End
        }
        val now = remember { java.util.Calendar.getInstance() }
        val h = String.format("%02d", now.get(java.util.Calendar.HOUR_OF_DAY))
        val m = String.format("%02d", now.get(java.util.Calendar.MINUTE))
        val locale = java.util.Locale.getDefault()
        val weekday = now.getDisplayName(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.LONG, locale) ?: ""
        val weekdayShort = now.getDisplayName(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SHORT, locale) ?: ""
        val month = now.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.LONG, locale) ?: ""
        val monthShort = now.getDisplayName(java.util.Calendar.MONTH, java.util.Calendar.SHORT, locale) ?: ""
        val day = now.get(java.util.Calendar.DAY_OF_MONTH)
        val caps = TextStyle(letterSpacing = 0.14.em)
        val capsSize = (dateSp.value * 0.7f).sp
        // Tight tracking only for the system sans, as on the home screen (Doto and custom fonts keep 0)
        val tight = FontManager.tracking(LocalContext.current, -0.06f).em
        val options = listOf(
            Constants.ClockStyle.Classic to R.string.clock_style_classic_sub,
            Constants.ClockStyle.Swiss to R.string.clock_style_swiss_sub,
            Constants.ClockStyle.Rings to R.string.clock_style_rings_sub,
            Constants.ClockStyle.OneLine to R.string.clock_style_one_line_sub
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            options.forEachIndexed { index, (style, subRes) ->
                val isSelected = style == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(if (isSelected) 2.dp else 1.dp, if (isSelected) frame else outlineColor())
                        .clickable { onSelect(style) }
                        .padding(top = 14.dp, bottom = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FontText(
                        text = (index + 1).toString().padStart(2, '0') + if (isSelected) " →" else "",
                        fontSize = capsSize,
                        color = if (isSelected) label else labelMuted,
                        style = caps,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(48.dp)
                    )
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = align) {
                        when (style) {
                            Constants.ClockStyle.Classic -> Column(horizontalAlignment = align) {
                                FontText(text = "$h:$m", fontSize = clockSp, color = ink)
                                FontText(text = "$weekdayShort, $monthShort $day", fontSize = dateSp, color = muted, modifier = Modifier.padding(top = 2.dp))
                            }
                            Constants.ClockStyle.Swiss -> Row(verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    FontText(text = h, fontSize = (clockSp.value * 1.5f).sp, fontWeight = FontWeight.Bold, color = ink, style = TextStyle(letterSpacing = tight, lineHeight = (clockSp.value * 1.35f).sp))
                                    FontText(text = m, fontSize = (clockSp.value * 1.5f).sp, fontWeight = FontWeight.Light, color = ink, style = TextStyle(letterSpacing = tight, lineHeight = (clockSp.value * 1.35f).sp))
                                }
                                Column(modifier = Modifier.padding(start = 18.dp)) {
                                    FontText(text = weekday.uppercase(locale), fontSize = capsSize, color = muted, style = caps)
                                    FontText(text = "$day ${month.uppercase(locale)}", fontSize = capsSize, color = muted, style = caps, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            Constants.ClockStyle.Rings -> Row(verticalAlignment = Alignment.CenterVertically) {
                                val hours = now.get(java.util.Calendar.HOUR) + now.get(java.util.Calendar.MINUTE) / 60f
                                val minuteSweep = 360f * now.get(java.util.Calendar.MINUTE) / 60f
                                val hourSweep = 360f * hours / 12f
                                androidx.compose.foundation.Canvas(modifier = Modifier.size(96.dp)) {
                                    val outer = 6.dp.toPx(); val inner = 1.5.dp.toPx()
                                    val r1 = size.minDimension / 2 - outer / 2 - 1.dp.toPx()
                                    val r2 = r1 - outer / 2 - 8.dp.toPx()
                                    val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                                    drawCircle(ink.copy(alpha = 0.14f), r1, c, style = androidx.compose.ui.graphics.drawscope.Stroke(outer))
                                    drawCircle(ink.copy(alpha = 0.14f), r2, c, style = androidx.compose.ui.graphics.drawscope.Stroke(inner))
                                    drawArc(ink, -90f, hourSweep, false, topLeft = androidx.compose.ui.geometry.Offset(c.x - r1, c.y - r1), size = androidx.compose.ui.geometry.Size(r1 * 2, r1 * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(outer))
                                    drawArc(ink, -90f, minuteSweep, false, topLeft = androidx.compose.ui.geometry.Offset(c.x - r2, c.y - r2), size = androidx.compose.ui.geometry.Size(r2 * 2, r2 * 2), style = androidx.compose.ui.graphics.drawscope.Stroke(inner))
                                }
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    FontText(text = "$h:$m", fontSize = (clockSp.value * 0.7f).sp, fontWeight = FontWeight.Bold, color = ink)
                                    FontText(text = weekday.uppercase(locale), fontSize = capsSize, color = muted, style = caps, modifier = Modifier.padding(top = 4.dp))
                                    FontText(text = "$day ${month.uppercase(locale)}", fontSize = capsSize, color = muted, style = caps)
                                }
                            }
                            Constants.ClockStyle.OneLine -> Column {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    FontText(text = "$h:$m", fontSize = (clockSp.value * 0.5f).sp, fontWeight = FontWeight.Bold, color = ink)
                                    FontText(text = "${weekdayShort.uppercase(locale)} $day ${monthShort.uppercase(locale)}", fontSize = capsSize, color = muted, style = caps, modifier = Modifier.padding(start = 12.dp, bottom = 3.dp))
                                }
                                Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(2.dp).background(ink))
                            }
                        }
                        Row(modifier = Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
                            FontText(text = style.getString(), fontSize = (descriptionFontSize.value * 0.95f).sp, fontWeight = FontWeight.Medium, color = label)
                            FontText(text = getLocalizedString(subRes).uppercase(locale), fontSize = (capsSize.value * 0.9f).sp, color = labelMuted, style = caps, modifier = Modifier.padding(start = 8.dp, bottom = 1.dp))
                        }
                    }
                }
            }
        }
    }

    /** About page card: a large number (with the red star for the contributors), a label, one line of detail and a small direction at the bottom. */
    @Composable
    private fun AboutCard(
        number: String,
        star: Boolean,
        label: String,
        sub: String,
        go: String,
        titleFontSize: TextUnit,
        descriptionFontSize: TextUnit,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        val shape = RoundedCornerShape(0.dp)
        Column(
            modifier = modifier
                .clip(shape)
                .border(1.dp, outlineColor(), shape)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                FontText(
                    text = buildAnnotatedString {
                        append(number)
                        if (star) withStyle(SpanStyle(color = Color(0xFFE2001A))) { append("*") }
                    },
                    fontSize = (titleFontSize.value * 2f).sp,
                    fontWeight = FontWeight.Light,
                    color = SettingsTheme.typography.title.color
                )
                FontText(
                    text = label,
                    fontSize = (titleFontSize.value * 0.8f).sp,
                    fontWeight = FontWeight.Medium,
                    color = SettingsTheme.typography.title.color,
                    modifier = Modifier.padding(top = 6.dp)
                )
                FontText(
                    text = sub,
                    fontSize = (descriptionFontSize.value * 0.85f).sp,
                    color = SettingsTheme.typography.option.color,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            FontText(
                text = go.uppercase() + " →",
                fontSize = (descriptionFontSize.value * 0.75f).sp,
                color = SettingsTheme.typography.option.color,
                style = TextStyle(letterSpacing = 0.14.em),
                modifier = Modifier.padding(top = 14.dp)
            )
        }
    }

    /** One row of the About card: a title and plain lines under it; optionally the whole row opens something. */
    @Composable
    private fun AboutRow(
        title: String,
        lines: List<String>,
        titleFontSize: TextUnit,
        descriptionFontSize: TextUnit,
        onClick: (() -> Unit)? = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(vertical = 12.dp, horizontal = 16.dp)
        ) {
            if (title.isNotEmpty()) {
                FontText(text = title, fontSize = titleFontSize, color = SettingsTheme.typography.title.color)
            }
            lines.forEach { line ->
                FontText(
                    text = line,
                    fontSize = descriptionFontSize,
                    color = SettingsTheme.typography.option.color,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }

    private fun readAsset(path: String): String =
        requireContext().assets.open(path).bufferedReader().use { it.readText() }

    private fun dismissDialogs() {
        dialogBuilder.backupRestoreBottomSheet?.dismiss()
        dialogBuilder.saveLoadThemeBottomSheet?.dismiss()
        dialogBuilder.singleChoiceBottomSheetPill?.dismiss()
        dialogBuilder.singleChoiceBottomSheet?.dismiss()
        dialogBuilder.colorPickerBottomSheet?.dismiss()
        dialogBuilder.sliderBottomSheet?.dismiss()
        dialogBuilder.flagSettingsBottomSheet?.dismiss()
        dialogBuilder.showDeviceBottomSheet?.dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onStop() {
        super.onStop()
        dismissDialogs()
    }

    private fun showHiddenApps() {
        viewModel.getHiddenApps()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            Bundle().apply {
                putString("flag", AppDrawerFlag.HiddenApps.toString())
            }
        )
    }

    private fun showFavoriteApps() {
        findNavController().navigate(
            R.id.action_settingsFragment_to_appFavoriteFragment,
            Bundle().apply {
                putString("flag", AppDrawerFlag.SetHomeApp.toString())
            }
        )
    }

    private fun showLocationSearch() {
        findNavController().navigate(
            R.id.action_settingsFragment_to_locationSearchFragment
        )
    }

    private fun launchFontPicker() {
        AppLogger.d("FontPicker", "Launching picker...")
        (activity as MainActivity).pickCustomFont()
    }

    private fun openCustomIconSelectionDialog(target: IconCacheTarget) {
        val intent = Intent(requireActivity(), CustomIconSelectionActivity::class.java).apply {
            putExtra("IconCacheTarget", "$target")
        }
        startActivity(intent)
    }

    private fun setGesture(flag: AppDrawerFlag, action: Action) {
        when (flag) {
            AppDrawerFlag.SetShortSwipeUp -> prefs.shortSwipeUpAction = action
            AppDrawerFlag.SetShortSwipeDown -> prefs.shortSwipeDownAction = action
            AppDrawerFlag.SetShortSwipeLeft -> prefs.shortSwipeLeftAction = action
            AppDrawerFlag.SetShortSwipeRight -> prefs.shortSwipeRightAction = action
            AppDrawerFlag.SetClickClock -> prefs.clickClockAction = action
            AppDrawerFlag.SetAppUsage -> prefs.clickAppUsageAction = action
            AppDrawerFlag.SetClickDate -> prefs.clickDateAction = action
            AppDrawerFlag.SetDoubleTap -> prefs.doubleTapAction = action
            AppDrawerFlag.SetLongSwipeUp -> prefs.longSwipeUpAction = action
            AppDrawerFlag.SetLongSwipeDown -> prefs.longSwipeDownAction = action
            AppDrawerFlag.SetLongSwipeLeft -> prefs.longSwipeLeftAction = action
            AppDrawerFlag.SetLongSwipeRight -> prefs.longSwipeRightAction = action
            AppDrawerFlag.SetFloating -> prefs.clickFloatingAction = action
            AppDrawerFlag.None,
            AppDrawerFlag.SetHomeApp,
            AppDrawerFlag.HiddenApps,
            AppDrawerFlag.PrivateApps,
            AppDrawerFlag.LaunchApp -> {
            }

        }

        when (action) {
            Action.OpenApp -> {
                viewModel.getAppList(true)
                findNavController().navigate(
                    R.id.action_settingsFragment_to_appListFragment,
                    Bundle().apply {
                        putString("flag", flag.toString())
                    }
                )
            }

            else -> {
            }
        }
    }

    private fun exitLauncher(context: Context) {
        val pm = context.packageManager

        // Query all launchers (excluding Settings)
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .filter {
                val pkg = it.activityInfo.packageName
                it.activityInfo.enabled && !pkg.contains("settings", ignoreCase = true)
            }

        if (resolveInfos.isEmpty()) {
            showShortToast("No launchers found")
            return
        }

        val labels = resolveInfos.map { it.loadLabel(pm).toString() }

        // Swiss sheet: a plain list of the installed home apps
        dialogBuilder.chooser(getLocalizedString(R.string.settings_exit_launcher_dialog), labels) { which ->
            val info = resolveInfos[which]
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                component = ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(launchIntent)
        }
    }

    fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    private fun showBedtimeTimePicker(
        initialHour: Int,
        initialMinute: Int,
        onTimeSet: (hour: Int, minute: Int) -> Unit
    ) {
        // Use Material TimePicker (Material Design picker)
        val materialTimePicker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(initialHour)
            .setMinute(initialMinute)
            .build()

        materialTimePicker.addOnPositiveButtonClickListener {
            onTimeSet(materialTimePicker.hour, materialTimePicker.minute)
        }

        materialTimePicker.show(requireActivity().supportFragmentManager, "TIME_PICKER")
    }
}

private fun TextUnit.toDp(density: Density): Dp = with(density) { this@toDp.toDp() }
