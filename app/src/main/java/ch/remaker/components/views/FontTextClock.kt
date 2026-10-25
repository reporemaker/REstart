package ch.remaker.components.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.TextClock
import ch.remaker.restart.helper.CustomFontView
import ch.remaker.restart.helper.FontManager

class FontTextClock @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextClock(context, attrs), CustomFontView {

    init {
        FontManager.register(this)
    }

    /** Style "Swiss": the first line (hours) bold, the second (minutes) light. */
    var twoLineWeights: Boolean = false
        set(value) { field = value; text = text }

    override fun setText(text: CharSequence?, type: BufferType?) {
        if (twoLineWeights && text != null && text.contains('\n')) {
            val s = android.text.SpannableString(text)
            val br = text.indexOf('\n')
            s.setSpan(android.text.style.StyleSpan(Typeface.BOLD), 0, br, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(android.text.style.TypefaceSpan(Typeface.create("sans-serif-light", Typeface.NORMAL)), br + 1, text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            super.setText(s, type)
        } else {
            super.setText(text, type)
        }
    }

    override fun applyFont(typeface: Typeface?) {
        this.typeface = typeface
    }
}


