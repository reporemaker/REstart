package ch.remaker.components.views

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import ch.remaker.common.isGestureNavigationEnabled
import ch.remaker.restart.R
import ch.remaker.restart.data.Prefs
import ch.remaker.restart.helper.hideStatusBar
import ch.remaker.restart.helper.showStatusBar
import ch.remaker.restart.helper.CustomFontView
import ch.remaker.restart.helper.FontManager
import com.google.android.material.card.MaterialCardView

/**
 * BottomSheetDialog using CardView with a grab line at the top.
 *
 * The sheet stays above the keyboard (it follows the IME insets) and never grows past the
 * screen: content taller than the space left is scrolled inside the sheet.
 */
class FontBottomSheetDialogLocked(context: Context) : AppCompatDialog(context), CustomFontView {

    private val coordinator: CoordinatorLayout
    private val sheet: MaterialCardView
    private val contentWrapper: FrameLayout
    private var cancelableFlag = true
    private var navBottomMargin = 0
    private var imeBottom = 0

    override fun setCancelable(flag: Boolean) {
        super.setCancelable(flag)
        cancelableFlag = flag
    }

    init {
        val density = context.resources.displayMetrics.density

        // Coordinator layout
        coordinator = CoordinatorLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setOnClickListener { if (cancelableFlag) dismiss() }
        }

        // Sheet wrapped in MaterialCardView
        sheet = MaterialCardView(context).apply {
            layoutParams = CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM }

            // Swiss sheet: square corners, no shadow, a 2 dp rule at the top
            cardElevation = 0f
            radius = 0f
            // Taps on the sheet's empty parts are not taps outside it
            isClickable = true
            // The colour goes on the content, not the card: a card the colour of the theme's surface
            // gets Material's elevation overlay in the dark theme (black came out as #262626)
            setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        // Grab line at top
        val grabLine = View(context).apply {
            val heightPx = (2 * density).toInt()
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx).apply {
                gravity = Gravity.TOP
            }
            val ink = android.util.TypedValue().let { v ->
                context.theme.resolveAttribute(android.R.attr.textColorPrimary, v, true)
                ContextCompat.getColor(context, v.resourceId)
            }
            setBackgroundColor(ink)
        }

        // Content wrapper inside sheet
        contentWrapper = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimaryBackground))
            addView(grabLine) // always at index 0
        }

        sheet.addView(contentWrapper)
        coordinator.addView(sheet)
        super.setContentView(coordinator)

        // Window setup
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            // Full-screen transparent window drawn edge to edge: the sheet sits at the bottom of
            // the coordinator and is lifted by the IME insets itself (a wrap-content dialog
            // window gets no IME insets, so the keyboard used to cover it)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.BOTTOM)
            WindowCompat.setDecorFitsSystemWindows(this, false)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            attributes = attributes.apply {
                dimAmount = 0.5f
                flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            }
        }

        // Keyboard: lift the sheet by the part of the window the IME covers
        ViewCompat.setOnApplyWindowInsetsListener(coordinator) { _, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val lifted = if (ime > nav) ime else 0
            if (lifted != imeBottom) {
                imeBottom = lifted
                applyBottomMargin()
            }
            insets
        }

        // Register font manager
        FontManager.register(this)
    }

    override fun setContentView(view: View) = setContentView(view, scrollable = true)

    /**
     * [scrollable]: wrap [view] in a scroll view capped to the space on screen. A view that
     * scrolls itself (a list) or caps its own parts ([CappedScrollView] inside) passes false.
     */
    fun setContentView(view: View, scrollable: Boolean) {
        // Remove old content except the grab line (index 0)
        for (i in contentWrapper.childCount - 1 downTo 1) {
            contentWrapper.removeViewAt(i)
        }

        // Remove parent if the view already has one
        (view.parent as? ViewGroup)?.removeView(view)

        val density = context.resources.displayMetrics.density
        val content = if (scrollable && !containsList(view)) {
            CappedScrollView(context, reservePx = (40 * density).toInt()).apply {
                addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        } else view
        contentWrapper.addView(
            content, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (22 * density).toInt() // push below grab line
            })
    }

    override fun onStart() {
        super.onStart()

        // A full-screen window shows the status bar again: follow the launcher's setting
        window?.let { if (Prefs(context).showStatusBar) showStatusBar(it) else hideStatusBar(it) }

        // Apply font recursively
        window?.decorView?.let { rootView ->
            FontManager.getTypeface(context)?.let { applyFontRecursively(rootView, it) }
        }

        // Add padding for gesture navigation
        navBottomMargin = when (isGestureNavigationEnabled(context)) {
            true -> context.resources.getDimensionPixelSize(R.dimen.bottom_margin_gesture_nav)
            false -> context.resources.getDimensionPixelSize(R.dimen.bottom_margin_3_button_nav)
        }
        applyBottomMargin()

        // Slide up animation from bottom
        sheet.translationY = -sheet.height.toFloat()
        sheet.animate().translationY(0f).setDuration(250).start()
    }

    private fun applyBottomMargin() {
        val sideMargin = context.resources.getDimensionPixelSize(R.dimen.bottom_sheet_side_margin)
        val lp = sheet.layoutParams as CoordinatorLayout.LayoutParams
        // Above the keyboard when it is open, above the navigation bar otherwise
        lp.setMargins(sideMargin, lp.topMargin, sideMargin, if (imeBottom > 0) imeBottom + navBottomMargin / 3 else navBottomMargin)
        sheet.layoutParams = lp
    }

    private fun containsList(view: View): Boolean = when (view) {
        is AbsListView, is ScrollView -> true
        is ViewGroup -> (0 until view.childCount).any { containsList(view.getChildAt(it)) }
        else -> false
    }

    override fun applyFont(typeface: Typeface?) {
        window?.decorView?.let { rootView ->
            applyFontRecursively(rootView, typeface)
        }
    }

    private fun applyFontRecursively(view: View, typeface: Typeface?) {
        if (typeface == null) return
        when (view) {
            // Keep each text's own weight (a bold label, a light value) and only swap the family;
            // Doto stays on its emboldened cut, as everywhere else (its thin dots wash out to grey)
            is TextView -> if (view.tag != KEEP_TYPEFACE) {
                view.typeface = FontManager.keepingWeight(typeface, view.typeface)
            }
            is ViewGroup -> (0 until view.childCount).forEach {
                applyFontRecursively(view.getChildAt(it), typeface)
            }
        }
    }

    companion object {
        /** Tag for a text that keeps its own typeface instead of the chosen font. */
        const val KEEP_TYPEFACE = "keepTypeface"
    }
}

/**
 * A ScrollView that is never taller than the screen space left for a bottom sheet: the window
 * height minus the status bar, the keyboard or navigation bar, and [reservePx] for whatever
 * else the sheet shows (title, buttons, margins). Shorter content keeps its own height.
 */
class CappedScrollView(context: Context, private val reservePx: Int) : ScrollView(context) {

    init {
        isFillViewport = false
        isVerticalScrollBarEnabled = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val screen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getSystemService(WindowManager::class.java).currentWindowMetrics.bounds.height()
        } else {
            resources.displayMetrics.heightPixels
        }
        val insets = ViewCompat.getRootWindowInsets(this)
        val top = insets?.getInsets(WindowInsetsCompat.Type.systemBars())?.top ?: 0
        val bottom = insets?.let {
            maxOf(it.getInsets(WindowInsetsCompat.Type.ime()).bottom, it.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
        } ?: 0
        val gap = (48 * resources.displayMetrics.density).toInt() // keep a strip of the page visible above the sheet
        val max = (screen - top - bottom - gap - reservePx).coerceAtLeast((120 * resources.displayMetrics.density).toInt())
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST))
    }
}
