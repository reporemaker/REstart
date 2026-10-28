package ch.remaker.restart.ui.components

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * One line of the home notes block: the mark, the entry's text and, optionally, its day.
 *
 * With room enough all three stand on one line. Short of room the day moves under the text, in
 * line with it, so the text is not cut for the day: "Renew the passport / Overdue 23 Oct" instead
 * of "Renew the pass… · Overdue 23 Oct". Only a text too long for a line of its own ends in "…".
 * Right-to-left languages get the mirror image.
 *
 * Children, in this order: the mark (fixed size, followed by [gap]), the text, and optionally the
 * separator (" · ", dropped when the day moves under the text) and the day.
 */
class HomeNoteRow(context: Context, private val gap: Int) : ViewGroup(context) {

    private var stacked = false

    private fun mark(): View = getChildAt(0)
    private fun text(): View = getChildAt(1)
    private fun separator(): View? = if (childCount > 3) getChildAt(2) else null
    private fun day(): View? = if (childCount > 3) getChildAt(3) else null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        val mark = mark()
        mark.measure(
            MeasureSpec.makeMeasureSpec(mark.layoutParams.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(mark.layoutParams.height, MeasureSpec.EXACTLY)
        )
        val text = text()
        val separator = separator()
        val day = day()
        text.measure(free, free)
        separator?.measure(free, free)
        day?.measure(free, free)
        val lead = mark.measuredWidth + gap
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        val room = if (mode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE
        else MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight - lead
        val textWidth = text.measuredWidth
        val sepWidth = separator?.measuredWidth ?: 0
        val dayWidth = day?.measuredWidth ?: 0

        stacked = day != null && textWidth + sepWidth + dayWidth > room
        val s = if (stacked) 0 else sepWidth
        val t: Int
        val d: Int
        if (stacked) { t = minOf(textWidth, room); d = minOf(dayWidth, room) }
        // Without a day the text alone is capped, or it ran past the screen edge cut mid-letter, without "…"
        else { t = if (day == null) minOf(textWidth, room) else textWidth; d = dayWidth }
        text.measure(MeasureSpec.makeMeasureSpec(t.coerceAtLeast(0), MeasureSpec.EXACTLY), free)
        day?.measure(MeasureSpec.makeMeasureSpec(d.coerceAtLeast(0), MeasureSpec.EXACTLY), free)
        separator?.measure(MeasureSpec.makeMeasureSpec(s, MeasureSpec.EXACTLY), free)

        val dayHeight = day?.measuredHeight ?: 0
        val width = lead + if (stacked) maxOf(t, d) else t + s + d
        val height = if (stacked) text.measuredHeight + dayHeight
        else maxOf(mark.measuredHeight, text.measuredHeight, dayHeight)
        setMeasuredDimension(
            resolveSize(width + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(height + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        // Places a child at [x] from the start edge, mirrored for right-to-left
        fun place(v: View, x: Int, y: Int) {
            val left = if (rtl) width - paddingRight - x - v.measuredWidth else paddingLeft + x
            v.layout(left, y, left + v.measuredWidth, y + v.measuredHeight)
        }
        val mark = mark()
        val text = text()
        val separator = separator()
        val day = day()
        val lineHeight = if (stacked) text.measuredHeight
        else maxOf(mark.measuredHeight, text.measuredHeight, day?.measuredHeight ?: 0)
        val top = paddingTop
        place(mark, 0, top + (lineHeight - mark.measuredHeight) / 2)
        val lead = mark.measuredWidth + gap
        place(text, lead, top + (lineHeight - text.measuredHeight) / 2)
        if (day != null) {
            if (stacked) {
                separator?.layout(0, 0, 0, 0)
                place(day, lead, top + text.measuredHeight)
            } else {
                separator?.let { place(it, lead + text.measuredWidth, top + (lineHeight - it.measuredHeight) / 2) }
                place(day, lead + text.measuredWidth + (separator?.measuredWidth ?: 0), top + (lineHeight - day.measuredHeight) / 2)
            }
        }
    }
}
