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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import ch.remaker.common.getCpuBatteryInfo
import ch.remaker.common.getLocalizedString
import ch.remaker.common.getRamInfo
import ch.remaker.common.getSdCardInfo
import ch.remaker.common.getStorageInfo
import ch.remaker.common.requestRuntimePermission
import ch.remaker.restart.MainActivity
import ch.remaker.restart.R
import ch.remaker.restart.data.backup.BackupCrypto
import ch.remaker.restart.data.Constants
import ch.remaker.restart.data.Prefs
import ch.remaker.common.showLongToast
import ch.remaker.restart.helper.FontManager
import ch.remaker.restart.helper.getDeviceInfo
import ch.remaker.restart.helper.hasContactsPermission
import ch.remaker.restart.helper.utils.AppReloader
import ch.remaker.restart.services.HapticFeedbackService
import ch.remaker.components.views.CappedScrollView
import ch.remaker.restart.data.EnumOption
import ch.remaker.components.views.FontBottomSheetDialogLocked

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
            this.text = text.uppercase(java.util.Locale.getDefault())
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

    /** A caps text button for the sheet's action row. */
    private fun sheetButton(text: String, strong: Boolean, onClick: () -> Unit): TextView = TextView(context).apply {
        this.text = text.uppercase(java.util.Locale.getDefault())
        textSize = 13f
        letterSpacing = 0.14f
        typeface = FontManager.weighted(context, if (strong) 700 else 400)
        setTextColor(ink)
        setPadding(px(18), px(14), 0, px(6))
        setOnClickListener { onClick() }
    }

    /**
     * Swiss dialog: a sheet with a caps title and a 2 dp rule, [content] under it, and the actions
     * as caps text buttons on the right. Replaces the Material alert dialogs.
     */
    private fun swissSheet(
        title: String,
        content: View?,
        positive: String?,
        onPositive: (() -> Unit)?,
        negative: String? = getLocalizedString(R.string.cancel),
        cancelable: Boolean = true,
        /** Runs before the sheet closes on [positive]; false keeps it open (e.g. to show an error). */
        validate: (() -> Boolean)? = null
    ): FontBottomSheetDialogLocked {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(24), px(8), px(24), px(20))
        }
        layout.addView(sheetTitle(title))
        // Only the content scrolls; the title and the action row stay in view
        content?.let {
            layout.addView(CappedScrollView(context, reservePx = px(if (positive != null || negative != null) 190 else 110)).apply {
                addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            })
        }
        val sheet = FontBottomSheetDialogLocked(context)
        sheet.setCancelable(cancelable)
        if (positive != null || negative != null) {
            layout.addView(hairline().apply { (layoutParams as LinearLayout.LayoutParams).topMargin = px(16) })
            layout.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                negative?.let { addView(sheetButton(it, false) { sheet.dismiss() }) }
                positive?.let {
                    addView(sheetButton(it, true) {
                        if (validate?.invoke() == false) return@sheetButton
                        sheet.dismiss(); onPositive?.invoke()
                    })
                }
            })
        }
        sheet.setContentView(layout, scrollable = false)
        sheet.show()
        return sheet
    }

    /** Message under the title, as a paragraph. */
    private fun sheetMessage(text: String): TextView = TextView(context).apply {
        this.text = text
        textSize = 16f
        setTextColor(ink)
        setPadding(0, px(16), 0, 0)
    }

    /** Yes/no question in the Swiss sheet. */
    fun confirm(title: String, message: String, positive: String, negative: String? = getLocalizedString(R.string.cancel), onPositive: () -> Unit) {
        swissSheet(title, sheetMessage(message), positive, onPositive, negative)
    }

    /** A plain list of choices in the Swiss sheet; tapping a row closes the sheet. */
    fun chooser(title: String, labels: List<String>, onChosen: (Int) -> Unit) {
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, px(6), 0, 0) }
        var sheet: FontBottomSheetDialogLocked? = null
        labels.forEachIndexed { index, label ->
            list.addView(TextView(context).apply {
                text = label
                textSize = 18f
                setTextColor(ink)
                setPadding(0, px(14), 0, px(14))
                setOnClickListener { sheet?.dismiss(); onChosen(index) }
            })
            if (index < labels.lastIndex) list.addView(hairline())
        }
        sheet = swissSheet(title, list, null, null)
    }

    /** One group of [groupedChooser]: a row with an icon and a name that opens to its [items] (label, note). */
    data class ChooserGroup(val label: String, val icon: android.graphics.drawable.Drawable?, val items: List<Pair<String, String>>)

    /**
     * A Swiss list of groups that open on tap, e.g. apps and their widgets: icon and name per
     * group with a thin + / − on the right, the items indented under it with a small note (a size)
     * in spaced capitals. Tapping an item closes the sheet and reports (group, item).
     */
    fun groupedChooser(title: String, groups: List<ChooserGroup>, onChosen: (Int, Int) -> Unit) {
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, px(6), 0, 0) }
        var sheet: FontBottomSheetDialogLocked? = null
        groups.forEachIndexed { g, group ->
            val sign = TextView(context).apply { text = "+"; textSize = 20f; setTextColor(ink) }
            val items = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
            list.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(12), 0, px(12))
                addView(android.widget.ImageView(context).apply {
                    group.icon?.let { setImageDrawable(it) }
                    layoutParams = LinearLayout.LayoutParams(px(24), px(24)).apply { marginEnd = px(14) }
                })
                addView(TextView(context).apply { text = group.label; textSize = 18f; setTextColor(ink) },
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(sign)
                setOnClickListener {
                    val open = items.visibility != View.VISIBLE
                    items.visibility = if (open) View.VISIBLE else View.GONE
                    sign.text = if (open) "\u2212" else "+"
                }
            })
            group.items.forEachIndexed { i, (label, note) ->
                items.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(px(38), px(10), 0, px(10))
                    addView(TextView(context).apply { text = label; textSize = 16f; setTextColor(ink) },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(TextView(context).apply {
                        text = note.uppercase(java.util.Locale.ROOT)
                        textSize = 12f
                        letterSpacing = 0.14f
                        setTextColor(ink)
                    })
                    setOnClickListener { sheet?.dismiss(); onChosen(g, i) }
                })
            }
            list.addView(items)
            if (g < groups.lastIndex) list.addView(hairline())
        }
        sheet = swissSheet(title, list, null, null)
    }

    var backupRestoreBottomSheet: FontBottomSheetDialogLocked? = null

    /** Asks for one line of text; [onDone] gets the trimmed text, also when it is empty. */
    fun showTextInputDialog(context: Context, title: String, current: String, hint: String = "", onDone: (String) -> Unit) {
        // Swiss input: no box, a 2 dp rule under the text
        val input = EditText(context).apply {
            setText(current)
            this.hint = hint
            setSelection(text.length)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            textSize = 22f
            setTextColor(ink)
            setHintTextColor(ColorUtils.setAlphaComponent(ink, 150))
            background = ContextCompat.getDrawable(context, R.drawable.swiss_rule_bottom)
            typeface = FontManager.weighted(context, 400)
            setPadding(0, px(18), 0, px(10))
        }
        val sheet = swissSheet(title, input, getLocalizedString(R.string.okay), { onDone(input.text.toString().trim()) })
        sheet.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        input.requestFocus()
        // The state flag alone does not open the keyboard once the sheet is showing: ask for it
        input.postDelayed({
            sheet.window?.let { WindowCompat.getInsetsController(it, input).show(WindowInsetsCompat.Type.ime()) }
        }, 120)
    }

    /**
     * Asks for a backup password: once to open a backup, twice ([repeat]) to make one. At least
     * [BackupCrypto.MIN_PASSWORD] characters; [onDone] gets the password, which the caller clears after use.
     */
    fun showPasswordDialog(title: String, message: String, repeat: Boolean, onDone: (CharArray) -> Unit) {
        fun field(hint: String) = EditText(context).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 20f
            setTextColor(ink)
            setHintTextColor(ColorUtils.setAlphaComponent(ink, 150))
            background = ContextCompat.getDrawable(context, R.drawable.swiss_rule_bottom)
            typeface = FontManager.weighted(context, 400)
            setPadding(0, px(18), 0, px(10))
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO
        }
        val first = field(getLocalizedString(R.string.backup_password_hint))
        val second = field(getLocalizedString(R.string.backup_password_repeat_hint))
        val error = TextView(context).apply {
            textSize = 15f
            setTextColor(ink)
            typeface = FontManager.weighted(context, 700)
            setPadding(0, px(12), 0, 0)
            visibility = View.GONE
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(sheetMessage(message))
            addView(first)
            if (repeat) addView(second)
            addView(error)
        }
        fun password(field: EditText) = CharArray(field.text.length) { field.text[it] }
        val sheet = swissSheet(title, content, getLocalizedString(R.string.okay), {
            onDone(password(first))
            first.text.clear(); second.text.clear()
        }, validate = {
            val problem = when {
                first.text.length < BackupCrypto.MIN_PASSWORD && repeat -> R.string.backup_password_too_short
                first.text.isEmpty() -> R.string.backup_password_too_short
                repeat && first.text.toString() != second.text.toString() -> R.string.backup_password_mismatch
                else -> null
            }
            problem?.let { error.text = getLocalizedString(it); error.visibility = View.VISIBLE }
            problem == null
        })
        sheet.window?.setSoftInputMode(
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        )
        first.requestFocus()
        first.postDelayed({
            sheet.window?.let { WindowCompat.getInsetsController(it, first).show(WindowInsetsCompat.Type.ime()) }
        }, 120)
    }

    /** Lets the user tick any number of [labels]; [onDone] gets the indices that ended up ticked. */
    fun showMultiChoiceDialog(context: Context, title: String, labels: List<String>, checked: Set<Int>, onDone: (Set<Int>) -> Unit) {
        val state = BooleanArray(labels.size) { it in checked }
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, px(6), 0, 0) }
        labels.forEachIndexed { index, label ->
            // Square box, 1 dp outline, filled with the text colour when ticked
            val box = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(px(18), px(18)).apply { marginEnd = px(16) }
            }
            fun paint() {
                box.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(if (state[index]) ink else Color.TRANSPARENT)
                    setStroke(px(1), ink)
                }
            }
            paint()
            list.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, px(13), 0, px(13))
                addView(box)
                addView(TextView(context).apply { text = label; textSize = 18f; setTextColor(ink) })
                setOnClickListener { state[index] = !state[index]; paint() }
            })
            if (index < labels.lastIndex) list.addView(hairline())
        }
        swissSheet(title, list, getLocalizedString(R.string.okay), { onDone(state.indices.filter { state[it] }.toSet()) })
    }

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

        val flow = (activity as MainActivity).backupFlow
        layout.addView(createItem(getLocalizedString(R.string.backup_create)) { flow.create() })
        layout.addView(createItem(getLocalizedString(R.string.backup_restore)) { flow.restore() })
        layout.addView(createItem(flow.autoBackupState()) { flow.autoBackup() })

        // Shortcuts from a restored backup that wait to be added again
        if (flow.hasPendingShortcuts()) {
            layout.addView(createItem(getLocalizedString(R.string.pending_shortcuts_title)) { flow.pendingShortcuts() })
        }

        // Copies taken automatically before app updates and restores
        if (flow.hasSnapshots()) {
            layout.addView(createItem(getLocalizedString(R.string.backup_snapshots)) { flow.snapshots() })
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
        confirm(
            getLocalizedString(R.string.advanced_settings_backup_restore_clear_title),
            getLocalizedString(R.string.advanced_settings_backup_restore_clear_description),
            getLocalizedString(R.string.advanced_settings_clear_yes),
            getLocalizedString(R.string.advanced_settings_clear_no)
        ) { clearData() }
    }

    private fun clearData() {
        prefs = Prefs(context)
        prefs.clear()

        AppReloader.restartApp(context)
    }


    var sliderBottomSheet: FontBottomSheetDialogLocked? = null

    fun showSliderBottomSheet(
        context: Context,
        title: String,
        minValue: Number,
        maxValue: Number,
        currentValue: Number,
        steps: Int = 100,
        live: Boolean = false,
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
            typeface = FontManager.weighted(context, 300)   // the user's family, light where it has one
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
                    // Live: the value goes out while dragging, so a preview behind the sheet follows the finger
                    if (live && fromUser) onValueSelected(value)
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
            // Live: no dimming, the preview behind the sheet is what the value is for
            if (live) window?.setDimAmount(0f)
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
                // The translated name; Enum.name is the code identifier ("Left", "Beside")
                is EnumOption -> it.string()
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
                    // Each font row shows its own face; every other list keeps the user's font
                    typeface = fonts?.getOrNull(position) ?: FontManager.getTypeface(context) ?: Typeface.DEFAULT
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

    private fun statTitle(context: Context, title: String) = TextView(context).apply {
        text = title
        setTextColor(ink)
        setTypeface(null, Typeface.BOLD)
        textSize = 18f
    }

    // The values in a plain monospaced face whatever the chosen font: a dotted or display font
    // makes model names and numbers hard to read, and this sheet is there to be read and sent on
    private fun statDetails(context: Context, details: String) = TextView(context).apply {
        text = details
        setTextColor(ink)
        typeface = Typeface.MONOSPACE
        tag = FontBottomSheetDialogLocked.KEEP_TYPEFACE
        textSize = 13f
        setPadding(0, 8, 0, 0)
    }

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
                leftContainer.addView(statTitle(context, leftTitle))
                leftContainer.addView(statDetails(context, leftDetails))

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
                    card.addView(statTitle(context, title))
                    card.addView(statDetails(context, details))

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

                    card.addView(statTitle(context, title))

                    card.addView(statDetails(context, details))

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