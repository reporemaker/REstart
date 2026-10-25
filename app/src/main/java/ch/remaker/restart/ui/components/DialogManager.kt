package ch.remaker.restart.ui.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.toColorInt
import ch.remaker.common.getCpuBatteryInfo
import ch.remaker.common.getLocalizedString
import ch.remaker.common.getRamInfo
import ch.remaker.common.getSdCardInfo
import ch.remaker.common.getStorageInfo
import ch.remaker.common.requestRuntimePermission
import ch.remaker.restart.MainActivity
import ch.remaker.restart.BuildConfig
import ch.remaker.restart.R
import ch.remaker.restart.data.Constants
import ch.remaker.restart.data.Prefs
import ch.remaker.common.AppLogger
import ch.remaker.common.showLongToast
import java.text.DateFormat
import ch.remaker.restart.data.SettingsSnapshots
import ch.remaker.restart.helper.getDeviceInfo
import ch.remaker.restart.helper.hasContactsPermission
import ch.remaker.restart.helper.utils.AppReloader
import ch.remaker.restart.services.HapticFeedbackService
import ch.remaker.components.views.FontBottomSheetDialogLocked
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DialogManager(val context: Context, val activity: Activity) {

    private lateinit var prefs: Prefs
    val selectedColor: Int = ContextCompat.getColor(context, R.color.colorSelected)

    // ---- Swiss sheet vocabulary: flush left, small capitals, a 2 dp rule, hairlines, no rounding ----
    private val ink: Int
        get() = TypedValue().let { v ->
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, v, true)
            ContextCompat.getColor(context, v.resourceId)
        }
    private val paper: Int get() = ContextCompat.getColor(context, R.color.colorPrimaryBackground)
    private fun px(dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    /** Sheet title: spaced capitals with a 2 dp rule under them. */
    private fun sheetTitle(text: String): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(TextView(context).apply {
            this.text = text.uppercase()
            textSize = 12f
            letterSpacing = 0.14f
            setTextColor(ink)
            setPadding(0, px(12), 0, px(10))
        })
        addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(2))
            setBackgroundColor(ink)
        })
    }

    private fun hairline(): View = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(1))
        setBackgroundColor(ColorUtils.setAlphaComponent(ink, 36))
    }

    var backupRestoreBottomSheet: FontBottomSheetDialogLocked? = null

    /** Lists the automatic settings copies, newest first, and restores the chosen one after confirmation. */
    private fun showSnapshotPicker() {
        val snapshots = SettingsSnapshots.list(context)
        if (snapshots.isEmpty()) return
        val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        val labels = snapshots.map { snapshot ->
            getLocalizedString(R.string.advanced_settings_backup_restore_snapshot_item, versionNameOf(snapshot.versionCode), dateFormat.format(snapshot.takenAt))
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(getLocalizedString(R.string.advanced_settings_backup_restore_snapshot))
            .setItems(labels.toTypedArray()) { _, index ->
                val snapshot = snapshots[index]
                MaterialAlertDialogBuilder(context)
                    .setTitle(labels[index])
                    .setMessage(getLocalizedString(R.string.advanced_settings_backup_restore_snapshot_confirm))
                    .setPositiveButton(getLocalizedString(R.string.advanced_settings_backup_restore_restore)) { _, _ ->
                        val restored = try {
                            Prefs(context).loadFromString(snapshot.file.readText())
                        } catch (e: Exception) {
                            AppLogger.e("SettingsSnapshots", "Could not read ${snapshot.file.name}", e)
                            false
                        }
                        if (restored) {
                            // The copy carries the old version number; the update it came from is already done
                            Prefs(context).appVersion = BuildConfig.VERSION_CODE
                            AppReloader.restartApp(context)
                        } else {
                            context.showLongToast(getLocalizedString(R.string.advanced_settings_backup_restore_snapshot_failed))
                        }
                    }
                    .setNegativeButton(getLocalizedString(R.string.cancel), null)
                    .show()
            }
            .setNegativeButton(getLocalizedString(R.string.cancel), null)
            .show()
    }

    /** Asks for one line of text; [onDone] gets the trimmed text, also when it is empty. */
    fun showTextInputDialog(context: Context, title: String, current: String, hint: String = "", onDone: (String) -> Unit) {
        val input = EditText(context).apply {
            setText(current)
            this.hint = hint
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val padding = (24 * context.resources.displayMetrics.density).toInt()
        val frame = FrameLayout(context).apply {
            setPadding(padding, 0, padding, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(frame)
            .setPositiveButton(getLocalizedString(R.string.okay)) { _, _ -> onDone(input.text.toString().trim()) }
            .setNegativeButton(getLocalizedString(R.string.cancel), null)
            .show()
        input.requestFocus()
    }

    /** Lets the user tick any number of [labels]; [onDone] gets the indices that ended up ticked. */
    fun showMultiChoiceDialog(context: Context, title: String, labels: List<String>, checked: Set<Int>, onDone: (Set<Int>) -> Unit) {
        val state = BooleanArray(labels.size) { it in checked }
        MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setMultiChoiceItems(labels.toTypedArray(), state) { _, index, isChecked -> state[index] = isChecked }
            .setPositiveButton(getLocalizedString(R.string.okay)) { _, _ -> onDone(state.indices.filter { state[it] }.toSet()) }
            .setNegativeButton(getLocalizedString(R.string.cancel), null)
            .show()
    }

    /** "1.12.2.0" for a version code of the form MMmmppbb. */
    private fun versionNameOf(code: Int): String =
        listOf(code / 1_000_000, code / 10_000 % 100, code / 100 % 100, code % 100).joinToString(".")

    fun showBackupRestoreBottomSheet() {
        // Dismiss existing bottom sheet if it's showing
        backupRestoreBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        // Create layout programmatically
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(8), px(24), px(20))
            gravity = Gravity.START
        }

        // Utility function to create a clickable item
        fun createItem(text: String, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                this.text = text
                textSize = 18f
                setTextColor(ink)
                setPadding(0, px(14), 0, px(14))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onClick()
                    HapticFeedbackService.trigger(
                        context,
                        HapticFeedbackService.EffectType.SAVE
                    )
                    backupRestoreBottomSheet?.dismiss()
                }
            }
        }

        // Add the three items
        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_backup_restore_backup)) {
            (activity as MainActivity).createFullBackup()
        })

        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_backup_restore_restore)) {
            (activity as MainActivity).restoreFullBackup()
        })

        // Copies taken automatically before app updates
        if (SettingsSnapshots.list(context).isNotEmpty()) {
            layout.addView(createItem(getLocalizedString(R.string.advanced_settings_backup_restore_snapshot)) {
                showSnapshotPicker()
            })
        }

        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_backup_restore_clear)) {
            confirmClearData()
        })

        // Create and show the bottom sheet
        backupRestoreBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(layout)
        }
        backupRestoreBottomSheet?.show() // ✅ Correct method call
    }

    // Function to handle the Clear Data action, with a confirmation dialog
    private fun confirmClearData() {
        MaterialAlertDialogBuilder(context)
            .setTitle(getLocalizedString(R.string.advanced_settings_backup_restore_clear_title))
            .setMessage(getLocalizedString(R.string.advanced_settings_backup_restore_clear_description))
            .setPositiveButton(getLocalizedString(R.string.advanced_settings_clear_yes)) { _, _ ->
                clearData()
            }
            .setNegativeButton(getLocalizedString(R.string.advanced_settings_clear_no), null)
            .show()
    }

    private fun clearData() {
        prefs = Prefs(context)
        prefs.clear()

        AppReloader.restartApp(context)
    }

    var saveLoadThemeBottomSheet: FontBottomSheetDialogLocked? = null

    fun showSaveLoadThemeBottomSheet() {
        // Dismiss any existing bottom sheet
        saveLoadThemeBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        // Create vertical layout
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(8), px(24), px(20))
            gravity = Gravity.START
        }

        // Utility function to create clickable items
        fun createItem(text: String, onClick: () -> Unit): TextView {
            return TextView(context).apply {
                this.text = text
                textSize = 18f
                setTextColor(ink)
                setPadding(0, px(14), 0, px(14))
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    onClick()
                    HapticFeedbackService.trigger(
                        context,
                        HapticFeedbackService.EffectType.SAVE
                    )
                    saveLoadThemeBottomSheet?.dismiss()
                }
            }
        }

        // Add Export and Import options
        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_theme_export)) {
            (activity as MainActivity).createThemeBackup()
        })

        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_theme_import)) {
            (activity as MainActivity).restoreThemeBackup()
        })

        // Add Clear Option
        layout.addView(createItem(getLocalizedString(R.string.advanced_settings_theme_clear)) {
            confirmClearTheme()
        })

        // Create and show the LockedBottomSheetDialog
        saveLoadThemeBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(layout)
        }
        saveLoadThemeBottomSheet?.show()
    }

    private fun confirmClearTheme() {
        MaterialAlertDialogBuilder(context)
            .setTitle(getLocalizedString(R.string.advanced_settings_theme_clear_title))
            .setMessage(getLocalizedString(R.string.advanced_settings_theme_clear_description))
            .setPositiveButton(getLocalizedString(R.string.advanced_settings_clear_yes)) { _, _ ->
                clearTheme()
            }
            .setNegativeButton(getLocalizedString(R.string.advanced_settings_clear_no), null)
            .show()
    }

    fun clearTheme() {
        val prefs = Prefs(context)
        val keys = listOf(
            "BACKGROUND_COLOR",
            "APP_COLOR",
            "DATE_COLOR",
            "ALARM_CLOCK_COLOR",
            "CLOCK_COLOR",
            "BATTERY_COLOR",
            "CATEGORY_COLOR",
            "NOTES_BACKGROUND_COLOR",
            "BUBBLE_BACKGROUND_COLOR",
            "BUBBLE_MESSAGE_COLOR",
            "BUBBLE_TIMEDATE_COLOR",
            "BUBBLE_CATEGORY_COLOR",
            "INPUT_MESSAGE_COLOR",
            "INPUT_MESSAGEHINT_COLOR"

        )
        keys.forEach { prefs.remove(it) }
        if (context is Activity) {
            context.recreate()
        }
    }

    var sliderBottomSheet: FontBottomSheetDialogLocked? = null

    fun showSliderBottomSheet(
        context: Context,
        title: String,
        minValue: Number,
        maxValue: Number,
        currentValue: Number,
        steps: Int = 100,
        onValueSelected: (Number) -> Unit
    ) {
        // Dismiss any existing sheet
        sliderBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        // Determine float mode
        val isFloat = minValue is Float || maxValue is Float || currentValue is Float
        val scaleFactor = if (isFloat) steps else 1

        val scaledMin = (minValue.toFloat() * scaleFactor).toInt()
        val scaledMax = (maxValue.toFloat() * scaleFactor).toInt()
        var scaledCurrent = (currentValue.toFloat() * scaleFactor).toInt()

        // Outer layout
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(8), px(24), px(20))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Title
        val titleText = sheetTitle(title)

        // Value display: large and light, flush left
        val valueText = TextView(context).apply {
            text = if (isFloat) currentValue.toFloat().toString() else currentValue.toInt().toString()
            textSize = 34f
            setTextColor(ink)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            gravity = Gravity.START
            setPadding(0, px(18), 0, px(6))
        }

        fun commitValue() {
            val value = if (isFloat) {
                scaledCurrent.toFloat() / scaleFactor
            } else {
                scaledCurrent
            }
            valueText.text = value.toString()
            onValueSelected(value)
        }

        // SeekBar
        val seekBar = SeekBar(context).apply {
            min = scaledMin
            max = scaledMax
            progress = scaledCurrent
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    scaledCurrent = progress
                    val value = if (isFloat) scaledCurrent.toFloat() / scaleFactor else scaledCurrent
                    valueText.text = value.toString()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    commitValue()
                    HapticFeedbackService.trigger(
                        context,
                        HapticFeedbackService.EffectType.SELECT
                    )
                }
            })
        }

        fun createCircleButton(
            context: Context,
            iconRes: Int
        ): AppCompatImageButton {

            val sizeDp = 32
            val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt()
            val paddingDp = 7
            val paddingPx = (paddingDp * context.resources.displayMetrics.density).toInt()

            return AppCompatImageButton(context).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.CENTER_INSIDE

                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    marginStart = 4
                    marginEnd = 4
                }

                // Square, outlined, no fill
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.TRANSPARENT)
                    setStroke(px(1), ink)
                }
                imageTintList = android.content.res.ColorStateList.valueOf(ink)

                // 👇 space around the icon
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

                // 👇 required for tiny buttons
                minimumWidth = 0
                minimumHeight = 0
            }
        }

        // Usage
        val minusButton = createCircleButton(context, R.drawable.ic_minus).apply {
            setOnClickListener {
                if (scaledCurrent > scaledMin) {
                    scaledCurrent--
                    seekBar.progress = scaledCurrent
                    commitValue()
                }
            }
        }

        val plusButton = createCircleButton(context, R.drawable.ic_plus).apply {
            setOnClickListener {
                if (scaledCurrent < scaledMax) {
                    scaledCurrent++
                    seekBar.progress = scaledCurrent
                    commitValue()
                }
            }
        }


        // Horizontal layout for buttons + slider
        val sliderLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(minusButton)
            addView(seekBar)
            addView(plusButton)
        }

        // Build layout
        container.addView(titleText)
        container.addView(valueText)
        container.addView(sliderLayout)

        // Show bottom sheet
        sliderBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(container)
            show()
        }
    }

    var singleChoiceBottomSheet: FontBottomSheetDialogLocked? = null

    fun <T> showSingleChoiceBottomSheet(
        context: Context,
        options: Array<T>,
        title: String,
        fonts: List<Typeface>? = null,
        fontSize: Float = 18f,
        selectedIndex: Int = -1,            // <- NEW
        onItemSelected: (T) -> Unit
    ) {
        singleChoiceBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        val itemStrings = options.map {
            when (it) {
                is Enum<*> -> it.name
                else -> it.toString()
            }
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(8), px(24), px(20))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val titleView = sheetTitle(title)

        val listView = ListView(context).apply {
            divider = android.graphics.drawable.ColorDrawable(ColorUtils.setAlphaComponent(ink, 36))
            dividerHeight = px(1)
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            choiceMode = ListView.CHOICE_MODE_SINGLE
        }

        // Mutable var so we can update highlight after a tap (even though we dismiss right away)
        var currentSelected = selectedIndex
        val defaultColor = TypedValue().let { value ->
            context.theme.resolveAttribute(android.R.attr.textColorPrimary, value, true)
            ContextCompat.getColor(context, value.resourceId)
        }

        val adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, itemStrings) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: TextView(context).apply {
                    setPadding(0, px(14), 0, px(14))
                    layoutParams = AbsListView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                (view as TextView).apply {
                    text = if (position == currentSelected) "→  " + itemStrings[position] else itemStrings[position]
                    typeface = fonts?.getOrNull(position) ?: Typeface.DEFAULT
                    textSize = fontSize
                    setTextColor(ink)
                }
                return view
            }
        }

        listView.adapter = adapter
        if (currentSelected in itemStrings.indices) listView.setItemChecked(currentSelected, true)

        container.addView(titleView)
        container.addView(listView)

        singleChoiceBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(container)
            show()
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            currentSelected = position          // update highlight if you keep dialog open
            adapter.notifyDataSetChanged()
            onItemSelected(options[position])   // callback
            HapticFeedbackService.trigger(
                context,
                HapticFeedbackService.EffectType.SELECT
            )
            singleChoiceBottomSheet?.dismiss()  // close sheet
        }

        // Limit visible height to 7 items
        listView.post {
            val itemHeight = listView.getChildAt(0)?.height ?: 0
            if (itemHeight > 0) {
                val maxHeight = itemHeight * 7
                listView.layoutParams.height = minOf(maxHeight, itemHeight * options.size)
                listView.requestLayout()
            }
        }
    }

    var singleChoiceBottomSheetPill: FontBottomSheetDialogLocked? = null

    fun <T> showSingleChoiceBottomSheetPill(
        context: Context,
        options: Array<T>,
        title: String,
        fonts: List<Typeface>? = null,
        fontSize: Float = 18f,
        selectedIndex: Int = -1,
        onItemSelected: (T) -> Unit
    ) {
        // Segments side by side are gone: every choice is a list (Swiss, one column).
        showSingleChoiceBottomSheet(context, options, title, fonts, fontSize, selectedIndex, onItemSelected)
    }

    var flagSettingsBottomSheet: FontBottomSheetDialogLocked? = null

    fun showFlagSettingsBottomSheet(
        context: Context,
        optionLabels: List<String>,
        settingFlags: String,
        default: String = "0",
        onFlagsChanged: (List<Boolean>) -> Unit
    ) {
        flagSettingsBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        val prefs = Prefs(context)
        val currentFlags = prefs.getMenuFlags(settingFlags, default).toMutableList()

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(8), px(24), px(20))
        }

        // Adjust the size of currentFlags to match optionLabels
        if (currentFlags.size < optionLabels.size) {
            currentFlags.addAll(List(optionLabels.size - currentFlags.size) { false })
        } else if (currentFlags.size > optionLabels.size) {
            currentFlags.subList(optionLabels.size, currentFlags.size).clear()
        }

        optionLabels.forEachIndexed { index, label ->
            val checkBox = CheckBox(context).apply {
                text = label
                textSize = 18f
                setTextColor(ink)
                isChecked = currentFlags[index]
                setOnCheckedChangeListener { _, isChecked ->
                    currentFlags[index] = isChecked
                    prefs.saveMenuFlags(settingFlags, currentFlags)
                    onFlagsChanged(currentFlags.toList()) // ✅ notify Compose with new data
                    HapticFeedbackService.trigger(
                        context,
                        HapticFeedbackService.EffectType.SELECT
                    )
                    if (label == context.getString(R.string.applist_button_contacts) && isChecked) {
                        if (!hasContactsPermission(context)) {
                            context.requestRuntimePermission(
                                arrayOf(Manifest.permission.READ_CONTACTS),
                                Constants.READ_CONTACTS,
                                "Contacts"
                            )
                        }
                    }
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 16
                }
            }
            layout.addView(checkBox)
        }


        flagSettingsBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(layout)
        }
        flagSettingsBottomSheet?.show()
    }


    var colorPickerBottomSheet: FontBottomSheetDialogLocked? = null

    fun showColorPickerBottomSheet(
        context: Context,
        title: String,
        color: Int,
        onItemSelected: (Int) -> Unit
    ) {
        colorPickerBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.CLICK
        )

        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)

        var isUpdatingText = false

        // Title
        val titleTextView = TextView(context).apply {
            text = title
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 16)
        }

        // SeekBars
        val redSeekBar = createColorSeekBar(context, red)
        val greenSeekBar = createColorSeekBar(context, green)
        val blueSeekBar = createColorSeekBar(context, blue)

        // Color preview
        val colorPreviewBox = createColorPreviewBox(context, color)

        // Hex input
        val rgbText = createRgbTextField(context, red, green, blue)

        // Layout
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Horizontal layout for text and preview
        val horizontalLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 32)

            val rgbParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 16
            }
            rgbText.layoutParams = rgbParams
            addView(rgbText)

            val previewParams = LinearLayout.LayoutParams(150, 50)
            colorPreviewBox.layoutParams = previewParams
            addView(colorPreviewBox)
        }

        // Add all views to layout
        layout.addView(titleTextView)
        layout.addView(redSeekBar)
        layout.addView(greenSeekBar)
        layout.addView(blueSeekBar)
        layout.addView(horizontalLayout)

        // Shared update function
        val updateColor = {
            val updatedColor = Color.rgb(redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress)
            colorPreviewBox.setBackgroundColor(updatedColor)

            if (!isUpdatingText) {
                isUpdatingText = true
                rgbText.setText(String.format("#%02X%02X%02X", redSeekBar.progress, greenSeekBar.progress, blueSeekBar.progress))
                isUpdatingText = false
            }

            // Auto-save
            onItemSelected(updatedColor)
            HapticFeedbackService.trigger(
                context,
                HapticFeedbackService.EffectType.SELECT
            )
        }

        // Listeners
        redSeekBar.setOnSeekBarChangeListener(createSeekBarChangeListener(updateColor))
        greenSeekBar.setOnSeekBarChangeListener(createSeekBarChangeListener(updateColor))
        blueSeekBar.setOnSeekBarChangeListener(createSeekBarChangeListener(updateColor))

        rgbText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (isUpdatingText) return
                s?.toString()?.trim()?.let { colorString ->
                    if (colorString.matches(Regex("^#[0-9A-Fa-f]{6}$"))) {
                        val hexColor = try {
                            colorString.toColorInt()
                        } catch (_: Exception) {
                            return
                        }
                        redSeekBar.progress = Color.red(hexColor)
                        greenSeekBar.progress = Color.green(hexColor)
                        blueSeekBar.progress = Color.blue(hexColor)
                        updateColor()
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // Show bottom sheet
        colorPickerBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(layout)
            show()
        }
    }

    private fun createColorSeekBar(context: Context, initialValue: Int): SeekBar {
        return SeekBar(context).apply {
            max = 255
            progress = initialValue
        }
    }

    private fun createColorPreviewBox(context: Context, color: Int): View {
        return View(context).apply {
            setBackgroundColor(color)
        }
    }

    private fun createRgbTextField(context: Context, red: Int, green: Int, blue: Int): EditText {
        return EditText(context).apply {
            setText(String.format("#%02X%02X%02X", red, green, blue))
            inputType = InputType.TYPE_CLASS_TEXT

            // Remove the bottom line (underline) from the EditText
            background = null
        }
    }

    private fun createSeekBarChangeListener(updateColorPreview: () -> Unit): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                updateColorPreview()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }
    }

    var showDeviceBottomSheet: FontBottomSheetDialogLocked? = null
    fun showDeviceStatsBottomSheet(context: Context) {
        showDeviceBottomSheet?.dismiss()

        HapticFeedbackService.trigger(
            context,
            HapticFeedbackService.EffectType.DEFAULT
        )

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val infoCards = listOf(
            "Device Info" to getDeviceInfo(context),
            "Storage" to getStorageInfo(),
            "RAM" to context.getRamInfo(),
            "CPU & Battery" to context.getCpuBatteryInfo(),
            "SD Card" to context.getSdCardInfo()
        )

        var i = 0
        while (i < infoCards.size) {
            val remaining = infoCards.size - i

            if (remaining >= 3) {
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 24
                    }
                }

                // Left big card container
                val leftContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(24, 24, 24, 24)
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        rightMargin = 16
                    }
                }
                val (leftTitle, leftDetails) = infoCards[i]
                leftContainer.addView(TextView(context).apply {
                    text = leftTitle
                    setTextColor(Color.WHITE)
                    setTypeface(null, Typeface.BOLD)
                    textSize = 18f
                })
                leftContainer.addView(TextView(context).apply {
                    text = leftDetails
                    setTextColor(Color.LTGRAY)
                    textSize = 14f
                    setPadding(0, 8, 0, 0)
                })

                // Right stacked cards container
                val rightContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                }
                for (j in 1..2) {
                    val (title, details) = infoCards[i + j]
                    val card = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(24, 24, 24, 24)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            if (j == 1) bottomMargin = 16
                        }
                    }
                    card.addView(TextView(context).apply {
                        text = title
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        textSize = 18f
                    })
                    card.addView(TextView(context).apply {
                        text = details
                        setTextColor(Color.LTGRAY)
                        textSize = 14f
                        setPadding(0, 8, 0, 0)
                    })

                    rightContainer.addView(card)
                }

                row.addView(leftContainer)
                row.addView(rightContainer)
                rootLayout.addView(row)

                // Equalize heights of left and right containers
                row.post {
                    val leftHeight = leftContainer.height
                    val rightHeight = rightContainer.height
                    val maxHeight = maxOf(leftHeight, rightHeight)

                    if (leftHeight != maxHeight || rightHeight != maxHeight) {
                        leftContainer.layoutParams.height = maxHeight
                        rightContainer.layoutParams.height = maxHeight
                        row.requestLayout()
                    }
                }

                i += 3
            } else {
                // Handle fewer than 3 cards normally
                val rowItems = infoCards.subList(i, infoCards.size)
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 24
                    }
                }

                rowItems.forEachIndexed { index, (title, details) ->
                    val isSingleCard = rowItems.size == 1

                    val cardLayoutParams = if (isSingleCard) {
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                    } else {
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        ).apply {
                            if (index == 0) rightMargin = 16
                        }
                    }

                    val card = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(24, 24, 24, 24)
                        layoutParams = cardLayoutParams
                    }

                    card.addView(TextView(context).apply {
                        text = title
                        setTextColor(Color.WHITE)
                        setTypeface(null, Typeface.BOLD)
                        textSize = 18f
                    })

                    card.addView(TextView(context).apply {
                        text = details
                        setTextColor(Color.LTGRAY)
                        textSize = 14f
                        setPadding(0, 8, 0, 0)
                    })

                    row.addView(card)
                }

                rootLayout.addView(row)
                break
            }
        }

        showDeviceBottomSheet = FontBottomSheetDialogLocked(context).apply {
            setContentView(rootLayout)
            show()
        }
    }
}