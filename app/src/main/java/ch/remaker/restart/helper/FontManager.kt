package ch.remaker.restart.helper

import android.content.Context
import android.graphics.Typeface
import ch.remaker.common.AppLogger
import ch.remaker.restart.data.Constants
import ch.remaker.restart.data.Prefs
import java.io.File
import java.lang.ref.WeakReference

interface CustomFontView {
    fun applyFont(typeface: Typeface?)
}

object FontManager {

    private var cachedTypeface: Typeface? = null
    private var cachedFamily: Constants.FontFamily? = null
    private val registeredViews = mutableListOf<WeakReference<CustomFontView>>()

    /** The chosen family (not the typeface): cached with it, read from the prefs otherwise. */
    fun family(context: Context): Constants.FontFamily =
        cachedFamily ?: runCatching { Prefs(context).fontFamily }.getOrDefault(Constants.FontFamily.System).also { cachedFamily = it }

    /**
     * The user's font at [weight] (100–900), derived from the family rather than replaced by a platform one.
     * The shipped Doto and Montserrat files and most custom fonts carry a single Regular weight: a heavier
     * request comes out fake-bold, a lighter one as the file is; either way it stays the user's family.
     */
    fun weighted(context: Context, weight: Int, italic: Boolean = false): Typeface =
        Typeface.create(getTypeface(context) ?: Typeface.DEFAULT, weight, italic)

    /**
     * Tight (negative) tracking only suits the system sans. Doto is a dot-matrix font whose digits collide
     * when pulled together and a custom font can be anything, so they keep their own spacing (0).
     */
    fun tracking(context: Context, tight: Float): Float =
        if (tight >= 0f || family(context) == Constants.FontFamily.System) tight else 0f

    fun getTypeface(context: Context): Typeface? {
        if (cachedTypeface != null) return cachedTypeface

        return try {
            val prefs = Prefs(context)
            val fontFamily = prefs.fontFamily
            cachedFamily = fontFamily

            cachedTypeface = when (fontFamily) {
                Constants.FontFamily.Custom -> {
                    val file = File(context.filesDir, "CustomFont.ttf")
                    if (file.exists()) Typeface.createFromFile(file) else null
                }

                else -> fontFamily.getFont(context)
            }

            cachedTypeface
        } catch (e: Exception) {
            AppLogger.e("FontManager", "Error loading typeface", e)
            null
        }
    }

    fun register(view: CustomFontView) {
        registeredViews.add(WeakReference(view))
        view.applyFont(cachedTypeface)
    }

    fun reloadFont(context: Context) {
        cachedTypeface = null
        cachedFamily = null
        cachedTypeface = getTypeface(context)
        AppLogger.i("FontManager", "Reloading font and notifying views")

        val iterator = registeredViews.iterator()
        while (iterator.hasNext()) {
            val viewRef = iterator.next()
            val view = viewRef.get()
            if (view != null) {
                view.applyFont(cachedTypeface)
            } else {
                iterator.remove() // Clean up dead references
            }
        }
    }
}