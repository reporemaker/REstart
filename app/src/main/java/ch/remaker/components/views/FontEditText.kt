package ch.remaker.components.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import ch.remaker.common.AppLogger
import ch.remaker.restart.helper.CustomFontView
import ch.remaker.restart.helper.FontManager

class FontEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatEditText(context, attrs), CustomFontView {

    init {
        try {
            FontManager.register(this)
            applyFont(FontManager.getTypeface(context))
        } catch (e: Exception) {
            AppLogger.e("FontEditText", "Font init failed", e)
        }
    }

    override fun applyFont(typeface: Typeface?) {
        try {
            if (typeface != null) {
                setTypeface(typeface, Typeface.NORMAL)
            }
        } catch (e: Exception) {
            AppLogger.e("FontEditText", "applyFont failed", e)
        }
    }
}