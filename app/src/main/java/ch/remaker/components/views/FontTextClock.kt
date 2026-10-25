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

    /**
     * The view's own family at [weight]: the user's font (Doto, Montserrat, a custom file or the system one)
     * is never swapped for a platform family. A family without that weight stays itself — fake-bold for the
     * heavy line, the plain face for the light one.
     */
    private fun weighted(weight: Int): Typeface =
        Typeface.create(typeface ?: Typeface.DEFAULT, weight, false)

    override fun setText(text: CharSequence?, type: BufferType?) {
        if (twoLineWeights && text != null && text.contains('\n')) {
            val s = android.text.SpannableString(text.toString())   // plain copy: no spans from an earlier family
            val br = text.indexOf('\n')
            s.setSpan(android.text.style.TypefaceSpan(weighted(700)), 0, br, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            s.setSpan(android.text.style.TypefaceSpan(weighted(300)), br + 1, text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            super.setText(s, type)
        } else {
            super.setText(text, type)
        }
    }

    override fun setTypeface(tf: Typeface?) {
        super.setTypeface(tf)
        // The spans were derived from the old family; rebuild them from the new one
        if (twoLineWeights) text = text
    }

    override fun applyFont(typeface: Typeface?) {
        this.typeface = typeface
    }
}


