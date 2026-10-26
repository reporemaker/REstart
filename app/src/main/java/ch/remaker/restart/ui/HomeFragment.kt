package ch.remaker.restart.ui

import android.annotation.SuppressLint
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Context.VIBRATOR_SERVICE
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.text.Spannable
import android.text.SpannableString
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
import ch.remaker.restart.helper.reMarkFor
import ch.remaker.restart.helper.FontManager
import ch.remaker.restart.helper.IconCacheTarget
import ch.remaker.restart.helper.IconPackHelper.getSafeAppIcon
import ch.remaker.restart.helper.ShortcutHelper
import ch.remaker.restart.helper.WeatherHelper
import ch.remaker.restart.helper.analytics.AppUsageMonitor
import ch.remaker.restart.helper.formatMillisToHMS
import ch.remaker.restart.helper.applyPageBackground
import ch.remaker.restart.helper.getHexForOpacity
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
import ch.remaker.restart.listener.StrokeClassifier
import ch.remaker.restart.listener.NotificationDotManager
import ch.remaker.restart.services.ActionService
import ch.remaker.restart.ui.components.DialogManager
import ch.remaker.restart.ui.components.ClockBlock
import ch.remaker.restart.ui.notes.NotesManagerFragment
import ch.remaker.restart.ui.widgets.WidgetActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    /** Clock, date, ring and notes; the same drawing is the preview in the clock settings. */
    private var clockBlock: ClockBlock? = null

    /** A new day (midnight, a clock or time zone change) redraws the notes. */
    private val dayReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val block = clockBlock ?: return
            if (intent.action != Intent.ACTION_TIME_TICK || block.notesStale) block.renderNotes()
        }
    }
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

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
        clockBlock = ClockBlock(binding.clockBlock, prefs) { id -> showNotesManager(id) }

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

            // System broadcasts only: the minute tick catches midnight, the others a changed clock or zone
            ctx.registerReceiver(dayReceiver, IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_DATE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            })
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the notes page or another app: the entries may have changed
        clockBlock?.renderNotes()
    }

    override fun onStop() {
        super.onStop()

        context?.let { ctx ->
            try {
                ctx.unregisterReceiver(dayReceiver)
            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }
            try {
                batteryReceiver.let { ctx.unregisterReceiver(it) }
                if (PrivateSpaceManager(requireContext()).isPrivateSpaceSupported()) {
                    privateSpaceReceiver.let { ctx.unregisterReceiver(it) }
                }
            } catch (e: IllegalArgumentException) {
                // Receiver not registered: safe to ignore
                e.printStackTrace()
            }
        }

        dismissDialogs()
    }


    private fun updateUIFromPreferences() {
        binding.apply {
            // Static UI setup
            battery.textSize = prefs.batterySize.toFloat()

            battery.isVisible = prefs.showBattery
            applyPageBackground(mainLayout, prefs)
            this@HomeFragment.clockBlock?.render()

            battery.setTextColor(prefs.batteryColor)
            totalScreenTime.setTextColor(prefs.appColor)
            setDefaultLauncher.setTextColor(prefs.appColor)
            // The rule over the quick actions follows the text colour (a theme change does not
            // recreate the view, so the theme attribute alone kept the old colour)
            quickActionsRule.setBackgroundColor(prefs.appColor)

            val fabList = listOf(fabPhone, fabMessages, fabCamera, fabPhotos, fabBrowser, fabSettings, fabAction)
            val fabFlags = prefs.getMenuFlags("HOME_BUTTON_FLAGS", "0000011") // Might return list of wrong size
            val colors = ColorManager.getRandomHueColors(prefs.shortcutIconsColor, fabList.size)

            for (i in fabList.indices) {
                val fab = fabList[i]

                val isVisible = if (i < fabFlags.size) fabFlags[i] else false
                val color = colors[i]

                fab.isVisible = isVisible

                // The RE* mark keeps its red star; its "RE" is black or white for the background
                if (fab == fabAction) fabAction.setImageResource(reMarkFor(prefs.backgroundColor))
                // Skip recoloring for fabAction
                if (fab != fabAction) {
                    fab.setColorFilter(
                        if (prefs.iconRainbowColors) color else prefs.shortcutIconsColor
                    )
                }
            }
        }
    }

    /** Time, date and notes again (on start: the day, the 12/24-hour setting or the notes may have changed). */
    private fun updateTimeAndInfo() {
        clockBlock?.render()
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
        keepEdgesForSwipes(binding.mainLayout)
    }

    /**
     * With gesture navigation Android takes a strip at the left and right edge for "back", which
     * does nothing on the home screen; a swipe started there never reached us. Android gives an
     * app up to 200 dp of each edge back: here the middle of the screen, where swipes start.
     */
    private fun keepEdgesForSwipes(page: View) {
        page.addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
            val edge = (32 * resources.displayMetrics.density).toInt()
            val band = (200 * resources.displayMetrics.density).toInt()
            val width = right - left
            val middle = (bottom - top) / 2
            v.systemGestureExclusionRects = listOf(
                android.graphics.Rect(0, middle - band / 2, edge, middle + band / 2),
                android.graphics.Rect(width - edge, middle - band / 2, width, middle + band / 2),
            )
        }
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
            clockBlock.clock.setOnClickListener(this@HomeFragment)
            clockBlock.date.setOnClickListener(this@HomeFragment)
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
            // The block reads every clock setting from the preferences itself
            listOf(showDate, showDayOfYear, showClock, clockAlignment, dateAlignment).forEach { setting ->
                setting.observe(viewLifecycleOwner) { clockBlock?.render() }
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

    /** The notes page; with [noteId] it opens that entry straight away. */
    private fun showNotesManager(noteId: Long = 0L) {
        CrashHandler.logUserAction("Display Notes Manager")
        try {
            if (findNavController().currentDestination?.id == R.id.mainFragment) {
                findNavController().navigate(
                    R.id.action_mainFragment_to_notesManagerFragment,
                    Bundle().apply { putLong(NotesManagerFragment.ARG_NOTE_ID, noteId) }
                )
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

    /**
     * Runs what the user set for a gesture: [action], or for "open app" the [app] chosen for it.
     * No app chosen yet: [noApp] (the old defaults of the swipes: settings to the left, phone to the right).
     */
    private fun runGesture(name: String, action: Action, app: ch.remaker.restart.data.AppListItem, noApp: () -> Unit = {}) {
        CrashHandler.logUserAction("$name Gesture")
        when (action) {
            Action.OpenApp -> if (app.activityPackage.isNotEmpty()) viewModel.launchApp(app, this) else noApp()
            else -> handleOtherAction(action)
        }
    }

    private fun swipeLeft() = runGesture("SwipeLeft", prefs.swipeLeftAction, prefs.appSwipeLeft) { requireContext().openDeviceSettings() }
    private fun swipeRight() = runGesture("SwipeRight", prefs.swipeRightAction, prefs.appSwipeRight) { lockGuard().openDialerApp() }

    /** Up always opens the app drawer, down always the notifications with the quick settings. */
    private fun swipeUp() {
        CrashHandler.logUserAction("SwipeUp Gesture")
        showAppList(AppDrawerFlag.LaunchApp, includeHiddenApps = false)
    }

    private fun swipeDown() {
        CrashHandler.logUserAction("SwipeDown Gesture")
        expandNotificationDrawer(requireContext())
    }

    private fun shapeDrawn(shape: StrokeClassifier.Shape) = when (shape) {
        StrokeClassifier.Shape.Circle -> runGesture("Circle", prefs.circleAction, prefs.appCircle)
        StrokeClassifier.Shape.Square -> runGesture("Square", prefs.squareAction, prefs.appSquare)
        StrokeClassifier.Shape.Triangle -> runGesture("Triangle", prefs.triangleAction, prefs.appTriangle)
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
            Action.OpenSettings -> trySettings()
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
            setSpan(android.text.style.ForegroundColorSpan(androidx.core.graphics.ColorUtils.setAlphaComponent(prefs.appColor, 190)), 0, index.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
            override fun onSwipeLeft() = swipeLeft()
            override fun onSwipeRight() = swipeRight()
            override fun onSwipeUp() = swipeUp()
            override fun onSwipeDown() = swipeDown()
            override fun onShape(shape: StrokeClassifier.Shape) = shapeDrawn(shape)

            override fun onLongPress() {
                runGesture("LongPress", prefs.longPressAction, prefs.appLongPress)
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

    /** A home app row: tap opens it, long press picks its app; strokes started on it count as on the screen. */
    private fun View.getHomeAppsGestureListener() {
        this.attachGestureManager(requireContext(), object : GestureAdapter() {

            override fun onLongPress() {
                textOnLongClick(this@getHomeAppsGestureListener)
            }

            override fun onSingleTap() {
                textOnClick(this@getHomeAppsGestureListener)
            }

            override fun onSwipeLeft() = swipeLeft()
            override fun onSwipeRight() = swipeRight()
            override fun onSwipeUp() = swipeUp()
            override fun onSwipeDown() = swipeDown()
            override fun onShape(shape: StrokeClassifier.Shape) = shapeDrawn(shape)
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
