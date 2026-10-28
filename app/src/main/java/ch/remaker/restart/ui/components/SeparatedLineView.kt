package ch.remaker.restart.ui.components

import android.content.Context
import androidx.appcompat.widget.AppCompatTextView

/**
 * Short items joined by " · " that wrap only between items: "Notes 4 · To-do 3 / Overdue 2", never
 * "Notes 4 · To-do 3 · / Overdue 2" with the dot left at a line's end, nor a label torn from its
 * number. Where a line breaks the separator is dropped.
 */
class SeparatedLineView(context: Context) : AppCompatTextView(context) {

    var items: List<String> = emptyList()
        set(value) {
            field = value
            text = value.joinToString(SEPARATOR)
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val room = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && room > 0) {
            // Measured as drawn: capitals and letter spacing included
            val wrapped = wrap(items, room.toFloat()) { s ->
                paint.measureText((transformationMethod?.getTransformation(s, this) ?: s).toString())
            }
            if (wrapped != text.toString()) text = wrapped
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    companion object {
        const val SEPARATOR = " · "

        /** Fills each line with as many whole items as fit in [room]; an item wider than [room] stands alone. */
        fun wrap(items: List<String>, room: Float, measure: (String) -> Float): String {
            val lines = mutableListOf<String>()
            var line = ""
            for (item in items) {
                val joined = if (line.isEmpty()) item else line + SEPARATOR + item
                if (line.isEmpty() || measure(joined) <= room) {
                    line = joined
                } else {
                    lines += line
                    line = item
                }
            }
            if (line.isNotEmpty()) lines += line
            return lines.joinToString("\n")
        }
    }
}
