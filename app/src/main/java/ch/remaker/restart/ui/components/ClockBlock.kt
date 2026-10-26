package ch.remaker.restart.ui.components

import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import ch.remaker.common.getLocalizedString
import ch.remaker.restart.R
import ch.remaker.restart.data.Constants
import ch.remaker.restart.data.NotesHome
import ch.remaker.restart.data.NotesStore
import ch.remaker.restart.data.Prefs
import ch.remaker.restart.databinding.ClockBlockBinding
import ch.remaker.restart.helper.FontManager
import java.text.NumberFormat
import java.util.Calendar

/**
 * The clock block of the home screen: ring, clock, date, notes and the "One line" rule, drawn the
 * way the chosen clock style and the settings want. One drawing for two places, the home screen and
 * the live preview in Settings > Clock area, so the preview is the home screen and not a sketch of it.
 *
 * [onNotes] opens the notes page (0) or one entry (its id); null leaves the notes untouchable, as in
 * the preview.
 */
class ClockBlock(
    private val b: ClockBlockBinding,
    private val prefs: Prefs,
    private val onNotes: ((Long) -> Unit)? = null,
) {
    private val context get() = b.root.context
    private val density get() = context.resources.displayMetrics.density

    /** The day the notes were drawn for; [notesStale] tells the home screen to redraw them. */
    private var notesDay = 0L

    val notesStale: Boolean get() = NotesHome.startOfDay(System.currentTimeMillis()) != notesDay

    /** Everything from the settings: patterns, what is shown, alignment, colour, size, style and notes. */
    fun render() {
        patterns()
        b.clock.setTextColor(prefs.clockColor)
        b.date.setTextColor(prefs.dateColor)
        b.date.isVisible = prefs.showDate
        align(b.clock, prefs.clockAlignment)
        align(b.date, prefs.dateAlignment)
        style()
        renderNotes()
    }

    private fun align(view: TextView, alignment: Constants.Gravity) {
        view.gravity = alignment.value()
        view.layoutParams = (view.layoutParams as LinearLayout.LayoutParams).apply { gravity = alignment.value() }
    }

    private fun dayOfYear(): String {
        val cal = Calendar.getInstance()
        return "[${cal.get(Calendar.DAY_OF_YEAR)}/${cal.getActualMaximum(Calendar.DAY_OF_YEAR)}]"
    }

    /** Time and date patterns in the app language, 12 or 24 hours as the phone is set. */
    private fun patterns() {
        val locale = prefs.appLanguage.formatLocale()
        val is24 = DateFormat.is24HourFormat(context)
        val best12Raw = DateFormat.getBestDateTimePattern(locale, "hm")
        val best12 = if (prefs.showClockFormat) best12Raw else best12Raw.replace("a", "").trim()
        val best24 = DateFormat.getBestDateTimePattern(locale, "Hm")
        val timePattern = clockPattern(if (is24) best24 else best12)
        b.clock.format12Hour = timePattern
        b.clock.format24Hour = timePattern

        val base = DateFormat.getBestDateTimePattern(locale, "EEEddMMM")
        val date = datePattern(if (prefs.showDayOfYear) "$base   ${dayOfYear()}" else base)
        b.date.format12Hour = date
        b.date.format24Hour = date
    }

    /** The clock pattern for the chosen style: "Swiss" stacks hours over minutes. */
    private fun clockPattern(pattern: String): String {
        if (prefs.clockStyle != Constants.ClockStyle.Swiss) return pattern
        // Two digits on both lines; AM/PM has no place in the stack and moves to the date line
        return if (DateFormat.is24HourFormat(context)) "HH\nmm" else "hh\nmm"
    }

    /** "Swiss" with a 12-hour clock: the AM/PM marker joins the date line. */
    private fun datePattern(pattern: String): String =
        if (prefs.clockStyle == Constants.ClockStyle.Swiss && prefs.showClockFormat && !DateFormat.is24HourFormat(context)) "$pattern · a" else pattern

    /**
     * Whether the date/notes column can be as wide as the room it stands in: yes under the clock or
     * beside the ring, so right and centre alignment mean that room. Not beside the clock (Swiss side
     * column, One line) nor under a centred ring, where the column is only as wide as what it holds.
     */
    private fun columnHasWidth(): Boolean = when (prefs.clockStyle) {
        Constants.ClockStyle.Classic -> true
        Constants.ClockStyle.Swiss -> !prefs.swissSideColumn
        Constants.ClockStyle.Rings -> prefs.ringPlacement != Constants.RingPlacement.Centred
        Constants.ClockStyle.OneLine -> false
    }

    /** "One line" puts the notes under the rule (the row has no room for them); every other style keeps them in the date column. */
    private fun moveNotes(belowRule: Boolean) {
        val notes = b.homeNotes
        val column = b.dateNotesLayout
        val page = b.clockRule.parent as ViewGroup
        if (belowRule && notes.parent !== page) {
            (notes.parent as? ViewGroup)?.removeView(notes)
            page.addView(notes, page.indexOfChild(b.clockRule) + 1)
        } else if (!belowRule && notes.parent !== column) {
            (notes.parent as? ViewGroup)?.removeView(notes)
            column.addView(notes)
        }
    }

    /** Draws the block the way the chosen style wants it; everything else stays as set by the preferences. */
    private fun style() {
        val style = prefs.clockStyle
        val capsOn = style != Constants.ClockStyle.Classic
        b.apply {
            ringClock.isVisible = style == Constants.ClockStyle.Rings
            ringClock.color = prefs.clockColor
            val centred = style == Constants.ClockStyle.Rings && prefs.ringPlacement == Constants.RingPlacement.Centred
            // Set again, not only changed: a start/end margin changed after the first measure is
            // never resolved for the layout direction otherwise (the preview redraws a measured block)
            ringClock.layoutParams = (ringClock.layoutParams as LinearLayout.LayoutParams).apply {
                width = (prefs.ringSize * density).toInt()
                height = width
                marginEnd = if (centred) 0 else (18 * density).toInt()
                bottomMargin = if (centred) (12 * density).toInt() else 0
            }
            clockRow.orientation = if (centred) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            clockRow.gravity = if (centred) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
            timeDateLayout.layoutParams = (timeDateLayout.layoutParams as LinearLayout.LayoutParams).apply {
                width = if (centred) ViewGroup.LayoutParams.WRAP_CONTENT else 0
                weight = if (centred) 0f else 1f
            }
            clockRule.isVisible = style == Constants.ClockStyle.OneLine && prefs.clockRule
            clockRule.setBackgroundColor(prefs.clockColor)
            ringClock.inside = prefs.ringInside
            ringClock.wholeDay = prefs.ringScale == Constants.RingScale.TwentyFour
            clock.twoLineWeights = style == Constants.ClockStyle.Swiss && prefs.swissWeights
            clock.setLineSpacing(0f, if (style == Constants.ClockStyle.Swiss) 0.82f else 1f)
            // "Rings" with the time inside: the numeric clock leaves the column
            clock.isVisible = prefs.showClock && !(style == Constants.ClockStyle.Rings && prefs.ringInside == Constants.RingInside.Time)

            // Where the date/notes column sits: beside the clock (Swiss side column, One line) or under it
            val beside = (style == Constants.ClockStyle.Swiss && prefs.swissSideColumn) || style == Constants.ClockStyle.OneLine
            timeDateLayout.orientation = if (beside) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            timeDateLayout.gravity = if (beside) Gravity.CENTER_VERTICAL else Gravity.NO_GRAVITY
            dateNotesLayout.layoutParams = (dateNotesLayout.layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = if (beside) (14 * density).toInt() else 0
                width = if (columnHasWidth()) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
            }
            // One line keeps clock and date on one row; its notes go under the rule
            dateNotesLayout.orientation = LinearLayout.VERTICAL
            moveNotes(belowRule = style == Constants.ClockStyle.OneLine)

            // Date: small spaced capitals in every style but Classic (the notes follow in renderNotes)
            date.isAllCaps = capsOn
            date.letterSpacing = if (capsOn) 0.14f else 0f
            date.setPadding(if (style == Constants.ClockStyle.OneLine) (12 * density).toInt() else 0, 0, 0, 0)
            date.textSize = when (style) {
                Constants.ClockStyle.Classic -> prefs.dateSize.toFloat()
                else -> prefs.dateSize * 0.72f
            }
            clock.textSize = when (style) {
                Constants.ClockStyle.Classic -> prefs.clockSize.toFloat()
                Constants.ClockStyle.Swiss -> prefs.clockSize * 1.5f
                Constants.ClockStyle.Rings -> prefs.clockSize * 0.7f
                Constants.ClockStyle.OneLine -> prefs.clockSize * 0.5f
            }
            // Weight from the user's family (a fresh base each time, so Classic really is Regular again);
            // the tight tracking only for the system sans: Doto's dots and unknown custom fonts keep their spacing
            clock.typeface = FontManager.weighted(context, if (style == Constants.ClockStyle.Classic) 400 else 700)
            clock.letterSpacing = if (style == Constants.ClockStyle.Classic) 0f else FontManager.tracking(context, -0.04f)
        }
    }

    /**
     * The notes block: a count line (only the counts above zero) and up to three important entries,
     * chosen by [NotesHome]. It has the look the alarm had: small spaced capitals in every style but
     * Classic, the notes colour, size and alignment. Nothing to show hides the whole block.
     */
    fun renderNotes() {
        val block = b.homeNotes
        block.removeAllViews()
        val now = System.currentTimeMillis()
        notesDay = NotesHome.startOfDay(now)
        if (!prefs.notesOnHome) {
            block.isVisible = false
            return
        }
        val summary = NotesHome.summarize(NotesStore(prefs).load(), now)
        val locale = prefs.appLanguage.formatLocale()
        // The language's own digits, as in the date beside it (Arabic writes ٣, not 3)
        val number = NumberFormat.getIntegerInstance(locale)
        val counts = listOf(
            R.string.settings_notes_title to summary.notes,
            R.string.notes_kind_todo to summary.openTodos,
            R.string.notes_overdue to summary.overdue,
        ).filter { it.second > 0 }.joinToString(" · ") { (label, n) -> "${getLocalizedString(label)} ${number.format(n)}" }
        if (counts.isEmpty() && summary.featured.isEmpty()) {
            block.isVisible = false
            return
        }

        val style = prefs.clockStyle
        val capsOn = style != Constants.ClockStyle.Classic
        val size = prefs.notesHomeSize.coerceIn(Constants.MIN_NOTES_HOME_SIZE, Constants.MAX_NOTES_HOME_SIZE)
        val textSize = if (capsOn) size * 0.72f else size.toFloat()
        val color = prefs.notesHomeColor
        val gravity = prefs.notesHomeAlignment.value()
        val belowRule = style == Constants.ClockStyle.OneLine
        // Under the rule the block sits in the block's own column, otherwise in the date column.
        // Where that column has a width of its own (see style) the block takes it, so its rows line
        // up by the notes alignment; in a column only as wide as its content, matching it would
        // squeeze the rows to the date's width
        val lp = block.layoutParams
        lp.width = if (belowRule || columnHasWidth()) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
        when (lp) {
            is LinearLayout.LayoutParams -> lp.gravity = gravity
            is LinearLayoutCompat.LayoutParams -> lp.gravity = gravity
        }
        (lp as? ViewGroup.MarginLayoutParams)?.topMargin = ((if (belowRule) 8 else 4) * density).toInt()
        block.layoutParams = lp
        block.gravity = gravity

        fun styled(view: TextView, lines: Int = 1) = view.apply {
            this.textSize = textSize
            typeface = FontManager.getTypeface(context)
            isAllCaps = capsOn
            letterSpacing = if (capsOn) 0.14f else 0f
            maxLines = lines
            ellipsize = TextUtils.TruncateAt.END
            includeFontPadding = false
        }

        val pad = (4 * density).toInt()
        if (counts.isNotEmpty()) {
            // The counts may take more lines rather than lose the last number
            block.addView(styled(TextView(context), lines = 3).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                this.gravity = gravity
                text = counts
                setTextColor(ColorUtils.setAlphaComponent(color, 184))
                setPadding(0, pad, 0, pad)
                onNotes?.let { open -> setOnClickListener { open(0L) } }
            })
        }
        val dayPattern = DateFormat.getBestDateTimePattern(locale, "dMMM")
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSize, context.resources.displayMetrics)
        summary.featured.forEach { (item, reason) ->
            val day = item.due?.let { DateFormat.format(dayPattern, it).toString() }
            val meta = when (reason) {
                NotesHome.Reason.OVERDUE -> "${getLocalizedString(R.string.notes_overdue)} $day"
                NotesHome.Reason.TODAY -> getLocalizedString(R.string.notes_today)
                NotesHome.Reason.UPCOMING -> day
                NotesHome.Reason.IMPORTANT -> null
            }
            val first = item.text.lineSequence().first().trim()
            val row = HomeNoteRow(context, gap = (px * 0.6f).toInt()).apply {
                // Wrapped, not the column's default match_parent: a stretched row would push the day to the far edge
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, pad, 0, pad)
                onNotes?.let { open -> setOnClickListener { open(item.id) } }
            }
            // The mark in front: an empty square for a to-do, a short bar for a note
            val mark = View(context).apply {
                val side = (px * 0.62f).toInt()
                layoutParams = ViewGroup.LayoutParams(side, if (item.todo) side else (2 * density).toInt())
                background = GradientDrawable().apply {
                    if (item.todo) setStroke((1.5f * density).toInt().coerceAtLeast(1), color) else setColor(color)
                }
            }
            row.addView(mark)
            // Text, separator and day: HomeNoteRow shortens the text or moves the day under it when short of room
            fun part(value: String) = styled(TextView(context)).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                text = value
                setTextColor(color)
            }
            row.addView(part(first))
            if (meta != null) {
                row.addView(part(" · "))
                row.addView(part(meta))
            }
            block.addView(row)
        }
        block.isVisible = true
    }
}
