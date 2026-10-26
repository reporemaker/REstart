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
 * home screen draws them (the same layout and [ClockBlock]), at full size, so every setting shows
 * at once.
 */
class ClockPreview(context: Context, private val prefs: Prefs) : ViewGroup(context) {

    private val content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val battery = FontAppCompatTextView(context)
    private val block: ClockBlock

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
        setMeasuredDimension(width, content.measuredHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        content.layout(0, 0, content.measuredWidth, content.measuredHeight)
    }
}
