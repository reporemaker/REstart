package ch.remaker.components.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import ch.remaker.common.AppLogger
import ch.remaker.restart.helper.CustomFontView
import ch.remaker.restart.helper.FontManager

class FontAutoCompleteTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.autoCompleteTextViewStyle
) : AppCompatAutoCompleteTextView(context, attrs, defStyleAttr), CustomFontView {

    init {
        try {
            FontManager.register(this)
            applyFont(FontManager.getTypeface(context))
        } catch (e: Exception) {
            AppLogger.e("FontAutoCompleteTextView", "Font application failed", e)
        }
    }

    override fun applyFont(typeface: Typeface?) {
        try {
            if (typeface != null) {
                setTypeface(typeface, Typeface.NORMAL)
            }
        } catch (e: Exception) {
            AppLogger.e("FontAutoCompleteTextView", "Failed to apply typeface", e)
        }
    }
}
