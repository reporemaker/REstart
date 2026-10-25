package ch.remaker.restart.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.text.Spannable
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.SuperscriptSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.biometric.BiometricPrompt
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import ch.remaker.common.AppLogger
import ch.remaker.common.ColorIconsExtensions
import ch.remaker.common.ColorManager
import ch.remaker.common.CrashHandler
import ch.remaker.common.attachGestureManager
import ch.remaker.common.getLocalizedString
import ch.remaker.common.isGestureNavigationEnabled
import ch.remaker.common.launchCalendar
import ch.remaker.common.openAlarmApp
import ch.remaker.common.openBatteryManager
import ch.remaker.common.openCameraApp
import ch.remaker.common.openDeviceSettings
import ch.remaker.common.openDialerApp
import ch.remaker.common.openDigitalWellbeing
import ch.remaker.common.openPhotosApp
import ch.remaker.common.openTextMessagesApp
import ch.remaker.common.openWebBrowser
import ch.remaker.common.showShortToast
import ch.remaker.restart.MainViewModel
import ch.remaker.restart.R
import ch.remaker.restart.data.Constants
import ch.remaker.restart.data.Constants.Action
import ch.remaker.restart.data.Constants.AppDrawerFlag
import ch.remaker.restart.data.Prefs
import ch.remaker.restart.databinding.FragmentHomeBinding
import ch.remaker.restart.helper.FontManager
import ch.remaker.restart.helper.IconCacheTarget
import ch.remaker.restart.helper.IconPackHelper.getSafeAppIcon
import ch.remaker.restart.helper.ShortcutHelper
import ch.remaker.restart.helper.WeatherHelper
import ch.remaker.restart.helper.analytics.AppUsageMonitor
import ch.remaker.restart.helper.formatMillisToHMS
import ch.remaker.restart.helper.getHexForOpacity
import ch.remaker.restart.helper.getNextAlarm
import ch.remaker.restart.helper.getSystemIcons
import ch.remaker.restart.helper.hasUsageAccessPermission
import ch.remaker.restart.helper.initActionService
import ch.remaker.restart.helper.isREstartAppDefault
import ch.remaker.restart.helper.openFirstWeatherApp
import ch.remaker.restart.helper.receivers.BatteryReceiver
import ch.remaker.restart.helper.receivers.DeviceAdmin
import ch.remaker.restart.helper.receivers.PrivateSpaceReceiver
import ch.remaker.restart.helper.setTopPadding
import ch.remaker.restart.helper.showPermissionDialog
import ch.remaker.restart.helper.utils.AppReloader
import ch.remaker.restart.helper.utils.LockGuardContext
import ch.remaker.restart.helper.utils.BiometricHelper
import ch.remaker.restart.helper.utils.PrivateSpaceManager
import ch.remaker.restart.listener.GestureAdapter
import ch.remaker.restart.listener.NotificationDotManager
import ch.remaker.restart.services.ActionService
import ch.remaker.restart.ui.components.DialogManager
import ch.remaker.restart.ui.widgets.WidgetActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var dialogBuilder: DialogManager
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var batteryReceiver: BatteryReceiver
    private lateinit var biometricHelper: BiometricHelper
    private lateinit var weatherHelper: WeatherHelper
    private lateinit var privateSpaceReceiver: PrivateSpaceReceiver
    private lateinit var vibrator: Vibrator

    private var longPressToSelectApp: Int = 0
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private fun getDayOfYearText(): String {
        val cal = Calendar.getInstance()
        val day = cal.get(Calendar.DAY_OF_YEAR)
        val max = cal.getActualMaximum(Calendar.DAY_OF_YEAR)
        return "[$day/$max]"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        val view = binding.root
        prefs = Prefs(requireContext())
        batteryReceiver = BatteryReceiver(binding.battery, prefs)
        dialogBuilder = DialogManager(requireContext(), requireActivity())
        if (PrivateSpaceManager(requireContext()).isPrivateSpaceSupported()) {
            privateSpaceReceiver = PrivateSpaceReceiver()
        }

        longPressToSelectApp = if (prefs.homeLocked) {
            R.string.long_press_to_select_app_locked
        } else {
            R.string.long_press_to_select_app
        }

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        biometricHelper = BiometricHelper(this.requireActivity())

        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")

        viewModel.isREstartAppDefault()

        deviceManager =
            context?.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        @Suppress("DEPRECATION")
        vibrator = context?.getSystemService(VIBRATOR_SERVICE) as Vibrator

        FontManager.reloadFont(requireContext())

        initAppObservers()
        initClickListeners()
        initSwipeTouchListener()
        initPermissionCheck()
        initObservers()

        // Update view appearance/settings based on prefs
        updateUIFromPreferences()
    }

    override fun onStart() {
        super.onStart()

        // Handle status bar once per view creation
        setTopPadding(binding.mainLayout)

        weatherHelper = WeatherHelper(
            requireContext(),
            viewLifecycleOwner
        ) { weatherText ->
            binding.weather.textSize = prefs.batterySize.toFloat()
            binding.weather.setTextColor(prefs.batteryColor)
            binding.weather.text = weatherText
            binding.weather.isVisible = true
        }

        // Weather updates
        if (prefs.showWeather) {
            weatherHelper.getWeather()
        } else {
            binding.weather.isVisible = false
        }

        // Update dynamic UI elements
        updateTimeAndInfo()

        // Register battery receiver
        context?.let { ctx ->
            binding.battery.let { textView ->
                batteryReceiver = BatteryReceiver(textView, prefs)
                val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                // Register receiver
                val stickyIntent = ctx.registerReceiver(batteryReceiver, intentFilter)
                // Immediately update UI with sticky intent
                stickyIntent?.let { batteryReceiver.onReceive(ctx, it) }
            }

            // Register private space receiver if supported
            if (PrivateSpaceManager(ctx).isPrivateSpaceSupported()) {
                privateSpaceReceiver = PrivateSpaceReceiver()
                ctx.registerReceiver(privateSpaceReceiver, IntentFilter(Intent.ACTION_PROFILE_AVAILABLE))
            }
        }
    }

    override fun onStop() {
        super.onStop()

        context?.let { ctx ->
            try {
                batteryReceiver.let { ctx.unregisterReceiver(it) }
                if (PrivateSpaceManager(requireContext()).isPrivateSpaceSupported()) {
                    privateSpaceReceiver.let { ctx.unregisterReceiver(it) }
                }
            } catch (e: IllegalArgumentException) {
                // Receiver not registered — safe to ignore
                e.printStackTrace()
            }
        }

        dismissDialogs()
    }


    private fun updateUIFromPreferences() {
        val locale = prefs.appLanguage.locale()
        val is24HourFormat = DateFormat.is24HourFormat(requireContext())

        binding.apply {
            val best12Raw = DateFormat.getBestDateTimePattern(locale, "hm") // 12-hour with AM/PM
            val best12 = if (prefs.showClockFormat) {
                best12Raw // keep AM/PM
            } else {
                best12Raw.replace("a", "").trim() // strip AM/PM
            }

            val best24 = DateFormat.getBestDateTimePattern(locale, "Hm") // 24-hour

            val timePattern = clockPattern(if (is24HourFormat) best24 else best12)

            clock.format12Hour = timePattern
            clock.format24Hour = timePattern

            // Date format
            val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEddMMM")
            date.format12Hour = datePattern
            date.format24Hour = datePattern

            // Static UI setup
            date.textSize = prefs.dateSize.toFloat()
            clock.textSize = prefs.clockSize.toFloat()
            alarm.textSize = prefs.alarmSize.toFloat()
            battery.textSize = prefs.batterySize.toFloat()

            battery.isVisible = prefs.showBattery
            mainLayout.setBackgroundColor(getHexForOpacity(prefs))
            applyClockStyle()

            date.setTextColor(prefs.dateColor)
            clock.setTextColor(prefs.clockColor)
            alarm.setTextColor(prefs.alarmClockColor)
            battery.setTextColor(prefs.batteryColor)
            totalScreenTime.setTextColor(prefs.appColor)
            setDefaultLauncher.setTextColor(prefs.appColor)

            val fabList = listOf(fabPhone, fabMessages, fabCamera, fabPhotos, fabBrowser, fabSettings, fabAction)
            val fabFlags = prefs.getMenuFlags("HOME_BUTTON_FLAGS", "0000011") // Might return list of wrong size
            val colors = ColorManager.getRandomHueColors(prefs.shortcutIconsColor, fabList.size)

            for (i in fabList.indices) {
                val fab = fabList[i]

                val isVisible = if (i < fabFlags.size) fabFlags[i] else false
                val color = colors[i]

                fab.isVisible = isVisible

                // Skip recoloring for fabAction
                if (fab != fabAction) {
                    fab.setColorFilter(
                        if (prefs.iconRainbowColors) color else prefs.shortcutIconsColor
                    )
                }
            }
        }
    }

    private fun updateTimeAndInfo() {
        val locale = prefs.appLanguage.locale()
        val is24HourFormat = DateFormat.is24HourFormat(requireContext())

        binding.apply {

            val best12Raw = DateFormat.getBestDateTimePattern(locale, "hm") // 12-hour with AM/PM
            val best12 = if (prefs.showClockFormat) {
                best12Raw // keep AM/PM
            } else {
                best12Raw.replace("a", "").trim() // strip AM/PM
            }

            val best24 = DateFormat.getBestDateTimePattern(locale, "Hm") // 24-hour

            val timePattern = clockPattern(if (is24HourFormat) best24 else best12)

            clock.format12Hour = timePattern
            clock.format24Hour = timePattern

            // Date format
            // val datePattern = DateFormat.getBestDateTimePattern(locale, "EEEddMMM")
            // date.format12Hour = datePattern
            // date.format24Hour = datePattern

            val basePattern = DateFormat.getBestDateTimePattern(locale, "EEEddMMM")

            val finalPattern = if (prefs.showDayOfYear) {
                "$basePattern   ${getDayOfYearText()}"
            } else {
                basePattern
            }

            date.format12Hour = datePattern(finalPattern)
            date.format24Hour = datePattern(finalPattern)


            alarm.text = getNextAlarm(requireContext(), prefs)
            applyClockStyle()
        }
    }

    /** "One line" can put the alarm on its own line under the rule; every other style keeps it in the date column. */
    private fun moveAlarm(belowRule: Boolean) {
        val alarm = binding.alarm
        val column = binding.dateAlarmLayout
        val page = binding.clockRule.parent as ViewGroup
        if (belowRule && alarm.parent !== page) {
            (alarm.parent as? ViewGroup)?.removeView(alarm)
            page.addView(alarm, page.indexOfChild(binding.clockRule) + 1)
            (alarm.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = (8 * resources.displayMetrics.density).toInt()
        } else if (!belowRule && alarm.parent !== column) {
            (alarm.parent as? ViewGroup)?.removeView(alarm)
            column.addView(alarm)
            (alarm.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = 0
        }
    }

    /** The clock pattern for the chosen style: "Swiss" stacks hours over minutes. */
    private fun clockPattern(pattern: String): String {
        if (prefs.clockStyle != Constants.ClockStyle.Swiss) return pattern
        // Two digits on both lines; AM/PM has no place in the stack and moves to the date line
        val is24 = DateFormat.is24HourFormat(requireContext())
        return if (is24) "HH\nmm" else "hh\nmm"
    }

    /** "Swiss" with a 12-hour clock: the AM/PM marker joins the date line. */
    private fun datePattern(pattern: String): String =
        if (prefs.clockStyle == Constants.ClockStyle.Swiss && prefs.showClockFormat && !DateFormat.is24HourFormat(requireContext())) "$pattern · a" else pattern

    /** Draws the clock block the way the chosen style wants it; everything else stays as set by the preferences. */
    private fun applyClockStyle() {
        val style = prefs.clockStyle
        binding.apply {
            val capsOn = style != Constants.ClockStyle.Classic
            val density = resources.displayMetrics.density
            ringClock.isVisible = style == Constants.ClockStyle.Rings
            ringClock.color = prefs.clockColor
            val centred = style == Constants.ClockStyle.Rings && prefs.ringPlacement == Constants.RingPlacement.Centred
            (ringClock.layoutParams as LinearLayout.LayoutParams).apply {
                width = (prefs.ringSize * density).toInt()
                height = width
                marginEnd = if (centred) 0 else (18 * density).toInt()
                bottomMargin = if (centred) (12 * density).toInt() else 0
            }
            clockRow.orientation = if (centred) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            clockRow.gravity = if (centred) android.view.Gravity.CENTER_HORIZONTAL else android.view.Gravity.CENTER_VERTICAL
            (timeDateLayout.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (centred) ViewGroup.LayoutParams.WRAP_CONTENT else 0
                weight = if (centred) 0f else 1f
            }
            timeDateLayout.requestLayout()
            clockRule.isVisible = style == Constants.ClockStyle.OneLine && prefs.clockRule
            clockRule.setBackgroundColor(prefs.clockColor)
            ringClock.inside = prefs.ringInside
            ringClock.wholeDay = prefs.ringScale == Constants.RingScale.TwentyFour
            clock.twoLineWeights = style == Constants.ClockStyle.Swiss && prefs.swissWeights
            clock.setLineSpacing(0f, if (style == Constants.ClockStyle.Swiss) 0.82f else 1f)
            // "Rings" with the time inside: the numeric clock leaves the column
            clock.isVisible = prefs.showClock && !(style == Constants.ClockStyle.Rings && prefs.ringInside == Constants.RingInside.Time)

            // Where the date/alarm column sits: beside the clock (Swiss side column, One line) or under it
            val beside = (style == Constants.ClockStyle.Swiss && prefs.swissSideColumn) || style == Constants.ClockStyle.OneLine
            timeDateLayout.orientation = if (beside) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            timeDateLayout.gravity = if (beside) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.NO_GRAVITY
            (dateAlarmLayout.layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = if (beside) (14 * density).toInt() else 0
            }
            // One line: date and alarm share the row, unless the alarm goes under the rule
            val alarmBelow = style == Constants.ClockStyle.OneLine && prefs.oneLineAlarmBelow
            dateAlarmLayout.orientation = if (style == Constants.ClockStyle.OneLine && !alarmBelow) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            moveAlarm(belowRule = alarmBelow)

            // Date and alarm: small spaced capitals in every style but Classic
            listOf(date, alarm).forEach { v ->
                v.isAllCaps = capsOn
                v.letterSpacing = if (capsOn) 0.14f else 0f
                v.setPadding(if (style == Constants.ClockStyle.OneLine) (12 * density).toInt() else 0, 0, 0, 0)
            }
            date.textSize = when (style) {
                Constants.ClockStyle.Classic -> prefs.dateSize.toFloat()
                else -> prefs.dateSize * 0.72f
            }
            alarm.textSize = if (capsOn) prefs.alarmSize * 0.72f else prefs.alarmSize.toFloat()
            clock.textSize = when (style) {
                Constants.ClockStyle.Classic -> prefs.clockSize.toFloat()
                Constants.ClockStyle.Swiss -> prefs.clockSize * 1.5f
                Constants.ClockStyle.Rings -> prefs.clockSize * 0.7f
                Constants.ClockStyle.OneLine -> prefs.clockSize * 0.5f
            }
            // Weight from the user's family (a fresh base each time, so Classic really is Regular again);
            // the tight tracking only for the system sans — Doto's dots and unknown custom fonts keep their spacing
            clock.typeface = FontManager.weighted(requireContext(), if (style == Constants.ClockStyle.Classic) 400 else 700)
            clock.letterSpacing = if (style == Constants.ClockStyle.Classic) 0f else FontManager.tracking(requireContext(), -0.04f)
        }
    }


    override fun onClick(view: View) {
        when (view.id) {
            R.id.clock -> {
                when (val action = prefs.clickClockAction) {
                    Action.OpenApp -> openClickClockApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("Clock Clicked")
            }

            R.id.date -> {
                when (val action = prefs.clickDateAction) {
                    Action.OpenApp -> openClickDateApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("Date Clicked")
            }

            R.id.totalScreenTime -> {
                when (val action = prefs.clickAppUsageAction) {
                    Action.OpenApp -> openClickUsageApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("TotalScreenTime Clicked")
            }

            R.id.setDefaultLauncher -> {
                viewModel.resetDefaultLauncherApp(requireContext())
                CrashHandler.logUserAction("SetDefaultLauncher Clicked")
            }

            R.id.battery -> {
                context?.openBatteryManager()
                CrashHandler.logUserAction("Battery Clicked")
            }

            R.id.weather -> {
                lockGuard().openFirstWeatherApp()
                CrashHandler.logUserAction("Weather Clicked")
            }

            R.id.fabPhone -> {
                lockGuard().openDialerApp()
                CrashHandler.logUserAction("fabPhone Clicked")
            }

            R.id.fabMessages -> {
                lockGuard().openTextMessagesApp()
                CrashHandler.logUserAction("fabMessages Clicked")
            }

            R.id.fabCamera -> {
                lockGuard().openCameraApp()
                CrashHandler.logUserAction("fabCamera Clicked")
            }

            R.id.fabPhotos -> {
                lockGuard().openPhotosApp()
                CrashHandler.logUserAction("fabPhotos Clicked")
            }

            R.id.fabBrowser -> {
                lockGuard().openWebBrowser()
                CrashHandler.logUserAction("fabBrowser Clicked")
            }

            R.id.fabSettings -> {
                trySettings()
                CrashHandler.logUserAction("fabSettings Clicked")
            }

            R.id.fabAction -> {
                when (val action = prefs.clickFloatingAction) {
                    Action.OpenApp -> openFabActionApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("fabAction Clicked")
            }

            else -> {
                try { // Launch app
                    val appLocation = view.id
                    homeAppClicked(appLocation)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun onLongClick(view: View): Boolean {
        if (prefs.homeLocked) return true

        val n = view.id
        showAppList(AppDrawerFlag.SetHomeApp, includeHiddenApps = true, includeRecentApps = false, n)
        CrashHandler.logUserAction("Show App List")
        return true
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun initSwipeTouchListener() {
        binding.touchArea.getHomeScreenGestureListener()
    }

    private fun initPermissionCheck() {
        val context = requireContext()
        if (prefs.recentAppsDisplayed || prefs.appUsageStats) {
            // Check if the usage permission is not granted
            if (!hasUsageAccessPermission(context)) {
                // Postpone showing the dialog until the activity is running
                Handler(Looper.getMainLooper()).post {
                    // Instantiate MainActivity and pass it to showPermissionDialog
                    showPermissionDialog(context)
                }
            }
        }
    }

    private fun initClickListeners() {
        binding.apply {
            clock.setOnClickListener(this@HomeFragment)
            date.setOnClickListener(this@HomeFragment)
            totalScreenTime.setOnClickListener(this@HomeFragment)
            setDefaultLauncher.setOnClickListener(this@HomeFragment)
            battery.setOnClickListener(this@HomeFragment)
            weather.setOnClickListener(this@HomeFragment)

            fabPhone.setOnClickListener(this@HomeFragment)
            fabMessages.setOnClickListener(this@HomeFragment)
            fabCamera.setOnClickListener(this@HomeFragment)
            fabPhotos.setOnClickListener(this@HomeFragment)
            fabBrowser.setOnClickListener(this@HomeFragment)
            fabAction.setOnClickListener(this@HomeFragment)
            fabSettings.setOnClickListener(this@HomeFragment)
        }
    }


    private fun initAppObservers() {
        binding.apply {
            firstRunTips.isVisible = prefs.firstSettingsOpen

            setDefaultLauncher.isVisible = !isREstartAppDefault(requireContext())

            val changeLauncherText = if (isREstartAppDefault(requireContext())) {
                R.string.advanced_settings_change_default_launcher
            } else {
                R.string.advanced_settings_set_as_default_launcher
            }

            setDefaultLauncher.text = getLocalizedString(changeLauncherText)
        }

        with(viewModel) {
            homeAppsNum.observe(viewLifecycleOwner) {
                if (prefs.appUsageStats) {
                    updateAppCountWithUsageStats(it)
                } else {
                    updateAppCount(it)
                }
            }
            homeAppsChanged.observe(viewLifecycleOwner) {
                // Rebuild every slot, since the count did not change
                binding.homeAppsLayout.removeAllViews()
                if (prefs.appUsageStats) {
                    updateAppCountWithUsageStats(prefs.homeAppsNum)
                } else {
                    updateAppCount(prefs.homeAppsNum)
                }
            }
            launcherDefault.observe(viewLifecycleOwner) {
                binding.setDefaultLauncher.isVisible = it
            }
        }
    }

    private fun initObservers() {
        with(viewModel) {
            showDate.observe(viewLifecycleOwner) {
                binding.date.isVisible = it
            }
            showDayOfYear.observe(viewLifecycleOwner) {
                updateTimeAndInfo()
            }
            showClock.observe(viewLifecycleOwner) {
                binding.clock.isVisible = it
                applyClockStyle()   // "Rings" with the time inside keeps the numeric clock hidden
            }
            showAlarm.observe(viewLifecycleOwner) {
                binding.alarm.isVisible = it
            }

            clockAlignment.observe(viewLifecycleOwner) { clockGravity ->
                binding.clock.gravity = clockGravity.value()

                // Set layout_gravity to align the TextClock (clock) within the parent (LinearLayout)
                binding.clock.layoutParams =
                    (binding.clock.layoutParams as LinearLayout.LayoutParams).apply {
                        gravity = clockGravity.value()
                    }
            }

            dateAlignment.observe(viewLifecycleOwner) { dateGravity ->
                binding.date.gravity = dateGravity.value()

                // Set layout_gravity to align the TextClock (date) within the parent (LinearLayout)
                binding.date.layoutParams =
                    (binding.date.layoutParams as LinearLayout.LayoutParams).apply {
                        gravity = dateGravity.value()
                    }
            }

            alarmAlignment.observe(viewLifecycleOwner) { alarmGravity ->
                binding.alarm.gravity = alarmGravity.value()

                // Set layout_gravity to align the TextView (alarm) within the parent (LinearLayout)
                binding.alarm.layoutParams =
                    (binding.alarm.layoutParams as LinearLayout.LayoutParams).apply {
                        gravity = alarmGravity.value()
                    }
            }

            homeAppsAlignment.observe(viewLifecycleOwner) { (homeAppsGravity, onBottom) ->
                val horizontalAlignment = if (onBottom) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
                binding.homeAppsLayout.gravity = homeAppsGravity.value() or horizontalAlignment

                binding.homeAppsLayout.children.forEach { view ->
                    if (prefs.appUsageStats) {
                        (view as LinearLayout).gravity = homeAppsGravity.value()
                    } else {
                        (view as TextView).gravity = homeAppsGravity.value()
                    }
                }
            }
        }
    }

    private fun homeAppClicked(location: Int) {
        CrashHandler.logUserAction("Clicked Home App: $location")
        if (prefs.getAppName(location).isEmpty()) showLongPressToast()
        else viewModel.launchApp(prefs.getHomeAppModel(location), this)
    }

    private fun showAppList(flag: AppDrawerFlag, includeHiddenApps: Boolean = false, includeRecentApps: Boolean = true, n: Int = 0) {
        viewModel.getAppList(includeHiddenApps, includeRecentApps)
        CrashHandler.logUserAction("Display App List")
        try {
            if (findNavController().currentDestination?.id == R.id.mainFragment) {
                findNavController().navigate(
                    R.id.action_mainFragment_to_appListFragment,
                    Bundle().apply {
                        putString("flag", flag.toString())
                        putInt("n", n)
                        putString("profileType", "SYSTEM")
                    }
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showNotesManager() {
        CrashHandler.logUserAction("Display Notes Manager")
        try {
            if (findNavController().currentDestination?.id == R.id.mainFragment) {
                findNavController().navigate(R.id.action_mainFragment_to_notesManagerFragment)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("PrivateApi")
    private fun expandNotificationDrawer(context: Context) {
        try {
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(context.getSystemService("statusbar"))
        } catch (exception: Exception) {
            initActionService(requireContext())?.openNotifications()
            exception.printStackTrace()
        }
        CrashHandler.logUserAction("Expand Notification Drawer")
    }

    @SuppressLint("PrivateApi")
    private fun expandQuickSettings(context: Context) {
        try {
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandSettingsPanel")
                .invoke(context.getSystemService("statusbar"))
        } catch (exception: Exception) {
            initActionService(requireContext())?.openQuickSettings()
            exception.printStackTrace()
        }
        CrashHandler.logUserAction("Expand Quick Settings")
    }

    /** Context for quick actions that open an app indirectly: a locked app asks for authentication first. */
    private fun lockGuard() = LockGuardContext(requireContext()) { packageName, launch ->
        viewModel.authenticateForPackage(packageName, this, launch)
    }

    private fun openSwipeUpApp() {
        CrashHandler.logUserAction("Open Swipe Up App")
        if (prefs.appShortSwipeUp.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appShortSwipeUp, this)
        else
            requireContext().openDeviceSettings()
    }

    private fun openSwipeDownApp() {
        CrashHandler.logUserAction("Open Swipe Down App")
        if (prefs.appShortSwipeDown.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appShortSwipeDown, this)
        else
            lockGuard().openDialerApp()
    }

    private fun openSwipeLeftApp() {
        CrashHandler.logUserAction("Open Swipe Left App")
        if (prefs.appShortSwipeLeft.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appShortSwipeLeft, this)
        else
            requireContext().openDeviceSettings()
    }

    private fun openSwipeRightApp() {
        CrashHandler.logUserAction("Open Swipe Right App")
        if (prefs.appShortSwipeRight.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appShortSwipeRight, this)
        else
            lockGuard().openDialerApp()
    }

    private fun openLongSwipeUpApp() {
        CrashHandler.logUserAction("Open Swipe Long Up App")
        if (prefs.appLongSwipeUp.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appLongSwipeUp, this)
        else
            requireContext().openDeviceSettings()
    }

    private fun openLongSwipeDownApp() {
        CrashHandler.logUserAction("Open Swipe Long Down App")
        if (prefs.appLongSwipeDown.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appLongSwipeDown, this)
        else
            lockGuard().openDialerApp()
    }

    private fun openLongSwipeLeftApp() {
        CrashHandler.logUserAction("Open Swipe Long Left App")
        if (prefs.appLongSwipeLeft.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appLongSwipeLeft, this)
        else
            requireContext().openDeviceSettings()
    }

    private fun openLongSwipeRightApp() {
        CrashHandler.logUserAction("Open Swipe Long Right App")
        if (prefs.appLongSwipeRight.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appLongSwipeRight, this)
        else
            lockGuard().openDialerApp()
    }

    private fun openClickClockApp() {
        CrashHandler.logUserAction("Open Clock App")
        if (prefs.appClickClock.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appClickClock, this)
        else
            lockGuard().openAlarmApp()
    }

    private fun openClickUsageApp() {
        CrashHandler.logUserAction("Open Usage App")
        if (prefs.appClickUsage.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appClickUsage, this)
        else
            requireContext().openDigitalWellbeing()
    }

    private fun openClickDateApp() {
        CrashHandler.logUserAction("Open Date App")
        if (prefs.appClickDate.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appClickDate, this)
        else
            lockGuard().launchCalendar()
    }

    private fun openDoubleTapApp() {
        CrashHandler.logUserAction("Open Double Tap App")
        if (prefs.appDoubleTap.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appDoubleTap, this)
        else
            AppReloader.restartApp(requireContext())
    }

    private fun openFabActionApp() {
        CrashHandler.logUserAction("Open Fab App")
        if (prefs.appFloating.activityPackage.isNotEmpty())
            viewModel.launchApp(prefs.appFloating, this)
        else
            findNavController().navigate(R.id.action_mainFragment_to_notesManagerFragment)
    }

    // This function handles all swipe actions that an independent of the actual swipe direction
    @SuppressLint("NewApi")
    private fun handleOtherAction(action: Action) {
        when (action) {
            Action.ShowNotification -> expandNotificationDrawer(requireContext())
            Action.LockScreen -> lockPhone()
            Action.TogglePrivateSpace -> PrivateSpaceManager(requireContext()).togglePrivateSpaceLock(showToast = false, launchSettings = false)
            Action.ShowAppList -> showAppList(AppDrawerFlag.LaunchApp, includeHiddenApps = false)
            Action.ShowNotesManager -> showNotesManager()
            Action.ShowDigitalWellbeing -> requireContext().openDigitalWellbeing()
            Action.OpenQuickSettings -> expandQuickSettings(requireContext())
            Action.ShowRecents -> initActionService(requireContext())?.showRecents()
            Action.OpenPowerDialog -> initActionService(requireContext())?.openPowerDialog()
            Action.TakeScreenShot -> initActionService(requireContext())?.takeScreenShot()
            Action.RestartApp -> AppReloader.restartApp(requireContext())
            Action.ShowWidgetPage -> showWidgetPage()
            Action.OpenApp -> {
                // this should be handled in the respective onSwipe[Up,Down,Right,Left] functions
            }

            Action.Disabled -> {
                // Do nothing
            }

        }
    }

    private fun showWidgetPage() {
        val context = requireContext()
        val intent = Intent(context, WidgetActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }

    private fun lockPhone() {
        val context = requireContext()
        val deviceAdmin = ComponentName(context, DeviceAdmin::class.java)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val actionService = ActionService.instance()

        when {
            // Prefer the accessibility service: unlike Device Admin it keeps biometric unlock working
            actionService != null -> {
                actionService.lockScreen()
                CrashHandler.logUserAction("Lock Screen via ActionService")
            }
            // Fallback to Device Admin if active; the next unlock then needs the PIN or password
            dpm.isAdminActive(deviceAdmin) -> {
                dpm.lockNow()
                CrashHandler.logUserAction("Lock Screen via Device Admin")
            }
            // Otherwise prompt the user to enable Device Admin
            else -> {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                    putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin)
                }
                startActivity(intent)
            }
        }
    }


    private fun showLongPressToast() = showShortToast(getLocalizedString(longPressToSelectApp))

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)


    @SuppressLint("InflateParams", "ClickableViewAccessibility")
    private fun updateAppCountWithUsageStats(newAppsNum: Int) {
        applyHomeListStyle()
        val appUsageMonitor = AppUsageMonitor.getInstance(requireContext())
        val oldAppsNum = binding.homeAppsLayout.childCount // current number of apps
        val diff = newAppsNum - oldAppsNum

        if (diff > 0) {
            // Add new apps
            for (i in oldAppsNum until newAppsNum) {
                // Create a horizontal LinearLayout to hold both existingAppView and newAppView
                val parentLinearLayout = LinearLayout(context)
                parentLinearLayout.apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, // Use MATCH_PARENT for full width
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                // Create existingAppView
                val existingAppView =
                    layoutInflater.inflate(R.layout.home_app_button, null) as TextView
                existingAppView.apply {
                    // Set properties of existingAppView
                    textSize = prefs.appSize.toFloat()
                    id = i
                    text = indexed(i, prefs.getAppDisplayName(prefs.getHomeAppModel(i)))
                    getHomeAppsGestureListener()
                    setOnClickListener(this@HomeFragment)
                    if (!prefs.extendHomeAppsArea) {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    val padding: Int = prefs.textPaddingSize
                    setPadding(0, padding, 0, padding)
                    setTextColor(prefs.appColor)
                }

                // Create newAppView
                val newAppView = TextView(context)
                newAppView.apply {
                    // Set properties of newAppView
                    textSize = prefs.appSize.toFloat() / 1.5f
                    typeface = FontManager.getTypeface(context)
                    id = i
                    val homeApp = prefs.getHomeAppModel(i)
                    // A shortcut's package is its creator (e.g. the browser), whose usage would be misleading
                    text = if (homeApp.isShortcut) "" else formatMillisToHMS(
                        appUsageMonitor.getUsageStats(
                            context,
                            homeApp.activityPackage
                        ), false
                    )
                    getHomeAppsGestureListener()
                    setOnClickListener(this@HomeFragment)
                    if (!prefs.extendHomeAppsArea) {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }
                    val padding: Int = prefs.textPaddingSize
                    setPadding(0, padding, 0, padding)
                    setTextColor(prefs.appColor)
                }

                // Add a space between existingAppView and newAppView
                val space = Space(context)
                space.layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f // Weight to fill available space
                )

                // Add existingAppView to parentLinearLayout
                parentLinearLayout.addView(existingAppView)
                // Add space and newAppView to parentLinearLayout
                parentLinearLayout.addView(space)
                parentLinearLayout.addView(newAppView)

                // Add parentLinearLayout to homeAppsLayout
                binding.homeAppsLayout.addView(parentLinearLayout)
            }
        } else if (diff < 0) {
            // Remove extra apps
            binding.homeAppsLayout.removeViews(oldAppsNum + diff, -diff)
        }

        // Create a new TextView instance
        val totalText = getLocalizedString(R.string.total_screen_time)
        val totalTime = appUsageMonitor.getTotalScreenTime(requireContext())
        val totalScreenTime = formatMillisToHMS(totalTime, true)
        AppLogger.d("totalScreenTime", totalScreenTime)
        val totalScreenTimeJoin = "$totalText: $totalScreenTime"
        // Set properties for the TextView (optional)
        binding.totalScreenTime.apply {
            text = totalScreenTimeJoin
            if (totalTime > 300L) { // Checking if totalTime is greater than 5 minutes (300,000 milliseconds)
                isVisible = true
            }
        }

        if (prefs.showFloating) binding.fabLayout.isVisible = true
        adjustTextViewMargins()
    }

    private fun adjustTextViewMargins() {
        binding.apply {

            privateLayout.apply {
                // Set visibility based on both private space setup and hide logo preference
                isVisible = PrivateSpaceManager(requireContext()).isPrivateSpaceSetUp() && prefs.showPrivateSpaces

                // Initial icon
                fun updatePrivateFabIcon() {
                    val isLocked = PrivateSpaceManager(requireContext()).isPrivateSpaceLocked()
                    val iconRes = if (isLocked) R.drawable.private_profile_on
                    else R.drawable.private_profile_off
                    privateFab.setImageResource(iconRes)
                }

                updatePrivateFabIcon() // set initial icon

                privateFab.setOnClickListener {
                    // Toggle lock
                    PrivateSpaceManager(requireContext()).togglePrivateSpaceLock(
                        showToast = false,
                        launchSettings = false
                    )
                    // Update icon after toggle
                    updatePrivateFabIcon()
                }
            }

            val views = listOf(
                setDefaultLauncher,
                totalScreenTime,
                fabLayout,
                homeAppsLayout,
                privateLayout
            )

            // Check if device is using gesture navigation or 3-button navigation
            val isGestureNav = isGestureNavigationEnabled(requireContext())

            val numOfElements = 5
            val incrementBy = 35
            // Set margins based on navigation mode
            val margins = if (isGestureNav) {
                val startAt = resources.getDimensionPixelSize(R.dimen.bottom_margin_gesture_nav)
                List(numOfElements) { index -> startAt + (index * incrementBy) } // Adjusted margins for gesture navigation
            } else {
                val startAt = resources.getDimensionPixelSize(R.dimen.bottom_margin_3_button_nav)
                List(numOfElements) { index -> startAt + (index * incrementBy) } // Adjusted margins for 3-button navigation
            }

            val visibleViews = views.filter { it.isVisible }
            val visibleMargins =
                margins.take(visibleViews.size) // Trim margins list to match visible views

            // Reset margins for all views
            views.forEach { view ->
                val params = view.layoutParams as ViewGroup.MarginLayoutParams
                params.bottomMargin = 0
                view.layoutParams = params
            }

            // Apply correct spacing for visible views
            visibleViews.forEachIndexed { index, view ->
                val params = view.layoutParams as ViewGroup.MarginLayoutParams
                var bottomMargin = visibleMargins.getOrElse(index) { 0 }

                // Add extra space above fabLayout if it's visible
                if (prefs.homeAlignmentBottom) {
                    if (visibleViews.contains(fabLayout)) {
                        if (view == homeAppsLayout) {
                            bottomMargin += 65
                        }
                    }
                }

                params.bottomMargin = bottomMargin
                view.layoutParams = params
            }
        }
    }



    /** Swiss list: hairlines between the home rows and under the last one. */
    private fun applyHomeListStyle() {
        binding.homeAppsLayout.dividerDrawable = android.graphics.drawable.GradientDrawable().apply {
            setSize(0, (1 * resources.displayMetrics.density).toInt())
            setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(prefs.appColor, 40))
        }
        binding.homeAppsLayout.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE or LinearLayout.SHOW_DIVIDER_END
    }

    /** "01  Phone": a small, dimmed index in front of the label when the list is left-aligned. */
    private fun indexed(i: Int, label: CharSequence): CharSequence {
        if (prefs.homeAlignment != Constants.Gravity.Left) return label
        val index = (i + 1).toString().padStart(2, '0')
        return android.text.SpannableStringBuilder().apply {
            append(index)
            setSpan(android.text.style.RelativeSizeSpan(0.42f), 0, index.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(android.text.style.ForegroundColorSpan(androidx.core.graphics.ColorUtils.setAlphaComponent(prefs.appColor, 140)), 0, index.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            append("    ")
            append(label)
        }
    }

    @SuppressLint("InflateParams", "DiscouragedApi", "UseCompatLoadingForDrawables", "ClickableViewAccessibility")
    private fun updateAppCount(newAppsNum: Int) {
        applyHomeListStyle()
        val oldAppsNum = binding.homeAppsLayout.childCount // current number of apps
        val diff = newAppsNum - oldAppsNum

        if (diff > 0) {
            // Add new apps
            for (i in oldAppsNum until newAppsNum) {
                val homeAppLabel = layoutInflater.inflate(R.layout.home_app_button, null) as TextView
                homeAppLabel.apply {
                    val homeApp = prefs.getHomeAppModel(i)
                    textSize = prefs.appSize.toFloat()
                    id = i
                    text = indexed(i, if (homeApp.activityPackage.isBlank()) {
                        getLocalizedString(R.string.select_app)
                    } else {
                        prefs.getAppDisplayName(homeApp)
                    })

                    getHomeAppsGestureListener()
                    setOnClickListener(this@HomeFragment)

                    if (!prefs.extendHomeAppsArea) {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    }

                    gravity = prefs.homeAlignment.value()
                    isFocusable = true
                    isFocusableInTouchMode = true

                    val padding: Int = prefs.textPaddingSize
                    setPadding(0, padding, 0, padding)
                    setTextColor(prefs.appColor)
                    val appModel = prefs.getHomeAppModel(i)
                    val packageName = appModel.activityPackage

                    if (packageName.isNotBlank() && prefs.iconPackHome != Constants.IconPacks.Disabled) {
                        val iconPackPackage = prefs.customIconPackHome
                        // Try to get app icon, possibly using icon pack, with graceful fallback
                        val shortcutIcon = if (appModel.isShortcut) ShortcutHelper.getIcon(context, appModel) else null
                        val nonNullDrawable: Drawable = shortcutIcon ?: getSafeAppIcon(
                            context = context,
                            packageName = packageName,
                            useIconPack = (iconPackPackage.isNotEmpty() && prefs.iconPackHome == Constants.IconPacks.Custom),
                            iconPackTarget = IconCacheTarget.HOME
                        )

                        // Use the drawable
                        val recoloredDrawable: Drawable? = getSystemIcons(
                            context,
                            prefs,
                            IconCacheTarget.HOME,
                            nonNullDrawable
                        )

                        val drawableToUse = recoloredDrawable ?: nonNullDrawable

                        // Set the icon size to match text size and add padding
                        var iconSize = (prefs.appSize * 1.4f).toInt()
                        if (prefs.iconPackHome == Constants.IconPacks.System || prefs.iconPackHome == Constants.IconPacks.Custom) {
                            iconSize *= 2
                        }
                        val iconPadding = (iconSize / 1.2f).toInt() // padding next to icon

                        drawableToUse.setBounds(0, 0, iconSize, iconSize)

                        // Set drawable position based on alignment
                        when (prefs.homeAlignment) {
                            Constants.Gravity.Left -> {
                                setCompoundDrawables(
                                    drawableToUse,
                                    null,
                                    null,
                                    null
                                )
                                // Add padding between text and icon if an icon is set
                                compoundDrawablePadding = iconPadding
                            }

                            Constants.Gravity.Right -> {
                                setCompoundDrawables(
                                    null,
                                    null,
                                    drawableToUse,
                                    null
                                )
                                // Add padding between text and icon if an icon is set
                                compoundDrawablePadding = iconPadding
                            }

                            else -> setCompoundDrawables(null, null, null, null)
                        }

                        val nm = NotificationManagerCompat.getEnabledListenerPackages(context)

                        if (nm.contains(context.packageName)) {
                            fun getCircledDigit(number: Int): String {
                                return when {
                                    number in 1..9 -> ('\u278A' + (number - 1)).toString() // ➊…➒
                                    number >= 10 -> '\u2789'.toString() // always ➓ for 10 or more
                                    else -> "" // no badge if 0 or invalid
                                }
                            }

                            val listener: (Map<String, Int>) -> Unit = { counts ->
                                val count = counts[packageName] ?: 0

                                this.text = if (count > 0) {
                                    val circledNumber = getCircledDigit(count)
                                    val newText = "${prefs.getAppDisplayName(appModel)} $circledNumber"
                                    val spannable = SpannableString(newText)

                                    val start = newText.indexOf(circledNumber)
                                    val end = start + circledNumber.length

                                    // Change size of the circled digit
                                    spannable.setSpan(
                                        AbsoluteSizeSpan((this.textSize * 0.8f).toInt(), false),
                                        start, end,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )

                                    // Set color
                                    val customColor = ColorIconsExtensions.getDominantColor(nonNullDrawable)
                                    spannable.setSpan(
                                        ForegroundColorSpan(customColor),
                                        start, end,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )

                                    // Move emoji up slightly
                                    spannable.setSpan(
                                        SuperscriptSpan(),
                                        start, end,
                                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                                    )

                                    spannable
                                } else {
                                    prefs.getAppDisplayName(appModel)
                                }

                                AppLogger.d("HomeFragment", "Notification count updated for $packageName: $count")
                            }

                            // Register listener for this TextView
                            NotificationDotManager.registerListener(listener)

                            // Make sure we update immediately based on current counts
                            listener(NotificationDotManager.getAllCounts())

                            // Unregister when detached
                            this.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(v: View) {}
                                override fun onViewDetachedFromWindow(v: View) {
                                    NotificationDotManager.unregisterListener(listener)
                                }
                            })

                        } else {
                            AppLogger.d("HomeFragment", "Notification listener permission not enabled for ${context.packageName}")
                        }
                    }
                }
                // Add the view to the layout
                binding.homeAppsLayout.addView(homeAppLabel)
            }
        } else if (diff < 0) {
            // Remove extra apps
            binding.homeAppsLayout.removeViews(oldAppsNum + diff, -diff)
        }

        if (prefs.showFloating) binding.fabLayout.isVisible = true
        adjustTextViewMargins()
    }


    private fun trySettings() {
        lifecycleScope.launch(Dispatchers.Main) {
            if (prefs.settingsLocked) {
                biometricHelper.startBiometricSettingsAuth(object :
                    BiometricHelper.CallbackSettings {
                    override fun onAuthenticationSucceeded() {
                        sendToSettingFragment()
                    }

                    override fun onAuthenticationFailed() {
                        AppLogger.e(
                            "Authentication",
                            getLocalizedString(R.string.text_authentication_failed)
                        )
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errorMessage: CharSequence?
                    ) {
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED -> AppLogger.e(
                                "Authentication",
                                getLocalizedString(R.string.text_authentication_cancel)
                            )

                            else ->
                                AppLogger.e(
                                    "Authentication",
                                    getLocalizedString(R.string.text_authentication_error).format(
                                        errorMessage,
                                        errorCode
                                    )
                                )
                        }
                    }
                })
            } else {
                sendToSettingFragment()
            }
        }
    }

    private fun sendToSettingFragment() {
        try {
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            viewModel.firstOpen(false)
        } catch (e: java.lang.Exception) {
            AppLogger.d("onLongClick", e.toString())
        }
    }

    private fun View.getHomeScreenGestureListener() {
        this.attachGestureManager(requireContext(), object : GestureAdapter() {
            override fun onShortSwipeLeft() {
                when (val action = prefs.shortSwipeLeftAction) {
                    Action.OpenApp -> openSwipeLeftApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeLeft Short Gesture")
            }

            override fun onLongSwipeLeft() {
                when (val action = prefs.longSwipeLeftAction) {
                    Action.OpenApp -> openLongSwipeLeftApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeLeft Long Gesture")
            }

            override fun onShortSwipeRight() {
                when (val action = prefs.shortSwipeRightAction) {
                    Action.OpenApp -> openSwipeRightApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeRight Short Gesture")
            }

            override fun onLongSwipeRight() {
                when (val action = prefs.longSwipeRightAction) {
                    Action.OpenApp -> openLongSwipeRightApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeRight Long Gesture")
            }

            override fun onShortSwipeUp() {
                when (val action = prefs.shortSwipeUpAction) {
                    Action.OpenApp -> openSwipeUpApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeUp Short Gesture")
            }

            override fun onLongSwipeUp() {
                when (val action = prefs.longSwipeUpAction) {
                    Action.OpenApp -> openLongSwipeUpApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeUp Long Gesture")
            }

            override fun onShortSwipeDown() {
                when (val action = prefs.shortSwipeDownAction) {
                    Action.OpenApp -> openSwipeDownApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeDown Short Gesture")
            }

            override fun onLongSwipeDown() {
                when (val action = prefs.longSwipeDownAction) {
                    Action.OpenApp -> openLongSwipeDownApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeDown Long Gesture")
            }

            override fun onLongPress() {
                CrashHandler.logUserAction("LongPress Gesture")
                trySettings()
            }

            override fun onDoubleTap() {
                when (val action = prefs.doubleTapAction) {
                    Action.OpenApp -> openDoubleTapApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("DoubleTap Gesture")
            }
        })
    }

    private fun View.getHomeAppsGestureListener() {
        this.attachGestureManager(requireContext(), object : GestureAdapter() {

            override fun onLongPress() {
                textOnLongClick(this@getHomeAppsGestureListener)
            }

            override fun onSingleTap() {
                textOnClick(this@getHomeAppsGestureListener)
            }

            override fun onShortSwipeLeft() {
                when (val action = prefs.shortSwipeLeftAction) {
                    Action.OpenApp -> openSwipeLeftApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeLeft Short Gesture")
            }

            override fun onLongSwipeLeft() {
                when (val action = prefs.longSwipeLeftAction) {
                    Action.OpenApp -> openLongSwipeLeftApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeLeft Long Gesture")
            }

            override fun onShortSwipeRight() {
                when (val action = prefs.shortSwipeRightAction) {
                    Action.OpenApp -> openSwipeRightApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeRight Short Gesture")
            }

            override fun onLongSwipeRight() {
                when (val action = prefs.longSwipeRightAction) {
                    Action.OpenApp -> openLongSwipeRightApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeRight Long Gesture")
            }

            override fun onShortSwipeUp() {
                when (val action = prefs.shortSwipeUpAction) {
                    Action.OpenApp -> openSwipeUpApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeUp Short Gesture")
            }

            override fun onLongSwipeUp() {
                when (val action = prefs.longSwipeUpAction) {
                    Action.OpenApp -> openLongSwipeUpApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeUp Long Gesture")
            }

            override fun onShortSwipeDown() {
                when (val action = prefs.shortSwipeDownAction) {
                    Action.OpenApp -> openSwipeDownApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeDown Short Gesture")
            }

            override fun onLongSwipeDown() {
                when (val action = prefs.longSwipeDownAction) {
                    Action.OpenApp -> openLongSwipeDownApp()
                    else -> handleOtherAction(action)
                }
                CrashHandler.logUserAction("SwipeDown Long Gesture")
            }
        }, waitForDoubleTap = false)
    }

    private fun dismissDialogs() {
        dialogBuilder.backupRestoreBottomSheet?.dismiss()
        dialogBuilder.singleChoiceBottomSheetPill?.dismiss()
        dialogBuilder.singleChoiceBottomSheet?.dismiss()
        dialogBuilder.colorPickerBottomSheet?.dismiss()
        dialogBuilder.sliderBottomSheet?.dismiss()
        dialogBuilder.flagSettingsBottomSheet?.dismiss()
        dialogBuilder.showDeviceBottomSheet?.dismiss()
    }
}
