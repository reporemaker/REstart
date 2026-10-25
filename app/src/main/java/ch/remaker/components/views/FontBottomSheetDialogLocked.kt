package ch.remaker.components.views

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import ch.remaker.common.isGestureNavigationEnabled
import ch.remaker.restart.R
import ch.remaker.restart.helper.CustomFontView
import ch.remaker.restart.helper.FontManager
import com.google.android.material.card.MaterialCardView

/**
 * BottomSheetDialog using CardView with a grab line at the top.
 */
class FontBottomSheetDialogLocked(context: Context) : AppCompatDialog(context), CustomFontView {

    private val coordinator: CoordinatorLayout
    private val sheet: MaterialCardView
    private val contentWrapper: FrameLayout
    private var cancelableFlag = true

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
            setCardBackgroundColor(ContextCompat.getColor(context, R.color.colorPrimaryBackground))
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
            addView(grabLine) // always at index 0
        }

        sheet.addView(contentWrapper)
        coordinator.addView(sheet)
        super.setContentView(coordinator)

        // Window setup
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
            attributes = attributes.apply {
                dimAmount = 0.5f
                flags = flags or WindowManager.LayoutParams.FLAG_DIM_BEHIND
            }
        }

        // Register font manager
        FontManager.register(this)
    }

    override fun setContentView(view: View) {
        // Remove old content except the grab line (index 0)
        for (i in contentWrapper.childCount - 1 downTo 1) {
            contentWrapper.removeViewAt(i)
        }

        // Remove parent if the view already has one
        (view.parent as? ViewGroup)?.removeView(view)

        val density = context.resources.displayMetrics.density
        contentWrapper.addView(
            view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (22 * density).toInt() // push below grab line
            })
    }

    override fun onStart() {
        super.onStart()

        // Apply font recursively
        window?.decorView?.let { rootView ->
            FontManager.getTypeface(context)?.let { applyFontRecursively(rootView, it) }
        }

        // Add padding for gesture navigation
        val bottomMargin = when (isGestureNavigationEnabled(context)) {
            true -> context.resources.getDimensionPixelSize(R.dimen.bottom_margin_gesture_nav)
            false -> context.resources.getDimensionPixelSize(R.dimen.bottom_margin_3_button_nav)
        }

        val sideMargin = context.resources.getDimensionPixelSize(R.dimen.bottom_sheet_side_margin)
        val lp = sheet.layoutParams as CoordinatorLayout.LayoutParams
        lp.setMargins(sideMargin, lp.topMargin, sideMargin, bottomMargin)
        sheet.layoutParams = lp

        // Slide up animation from bottom
        sheet.translationY = -sheet.height.toFloat()
        sheet.animate().translationY(0f).setDuration(250).start()
    }

    override fun applyFont(typeface: Typeface?) {
        window?.decorView?.let { rootView ->
            applyFontRecursively(rootView, typeface)
        }
    }

    private fun applyFontRecursively(view: View, typeface: Typeface?) {
        if (typeface == null) return
        when (view) {
            // Keep each text's own weight (a bold label, a light value) and only swap the family
            is TextView -> view.typeface = view.typeface?.let { Typeface.create(typeface, it.weight, it.isItalic) } ?: typeface
            is ViewGroup -> (0 until view.childCount).forEach {
                applyFontRecursively(view.getChildAt(it), typeface)
            }
        }
    }
}
