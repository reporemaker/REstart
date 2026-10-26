package ch.remaker.restart.ui.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isVisible
import ch.remaker.components.views.FontAppCompatTextView
import ch.remaker.restart.data.Prefs
import ch.remaker.restart.databinding.ClockBlockBinding
import ch.remaker.restart.helper.receivers.BatteryReceiver

/**
 * The live preview in Settings > Clock area: the battery line and the clock block exactly as the
 * home screen draws them (the same layout and [ClockBlock]), so every setting shows at once.
 * Taller than [maxHeightFraction] of the screen, it is scaled down evenly and [onScaled] says so.
 */
class ClockPreview(context: Context, private val prefs: Prefs) : ViewGroup(context) {

    var onScaled: ((Boolean) -> Unit)? = null
    /** Up to 40% of the screen; on a short screen (under 760 dp) 28%, so the settings keep room. */
    var maxHeightFraction = if (context.resources.configuration.screenHeightDp < 760) 0.28f else 0.4f

    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val battery = FontAppCompatTextView(context)
    private val block: ClockBlock
    private var scaled = false

    init {
        content.addView(battery, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END
        })
        block = ClockBlock(ClockBlockBinding.inflate(LayoutInflater.from(context), content, true), prefs)
        addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    /** Redraws from the settings as they are now. */
    fun refresh() {
        battery.isVisible = prefs.showBattery
        battery.textSize = prefs.batterySize.toFloat()
        battery.setTextColor(prefs.batteryColor)
        // The sticky battery broadcast, read once: no receiver stays registered
        context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let {
            BatteryReceiver(battery, prefs).onReceive(context, it)
        }
        block.render()
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        content.measure(
            MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        val natural = content.measuredHeight
        val limit = (resources.displayMetrics.heightPixels * maxHeightFraction).toInt()
        val scale = if (natural > limit && natural > 0) limit.toFloat() / natural else 1f
        // Scaled from the top centre, so a left, centred or right block keeps its place roughly
        content.pivotX = width / 2f
        content.pivotY = 0f
        content.scaleX = scale
        content.scaleY = scale
        setMeasuredDimension(width, (natural * scale).toInt())
        if ((scale < 1f) != scaled) {
            scaled = scale < 1f
            post { onScaled?.invoke(scaled) }
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
    }
}
