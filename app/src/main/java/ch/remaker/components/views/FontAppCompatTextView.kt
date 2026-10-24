package ch.remaker.components.views

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import ch.remaker.restart.helper.CustomFontView
import ch.remaker.restart.helper.FontManager


class FontAppCompatTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr), CustomFontView {

    init {
        FontManager.register(this)
    }

    override fun applyFont(typeface: Typeface?) {
        this.typeface = typeface
    }
}
