package ch.remaker.restart.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import ch.remaker.restart.helper.FontManager
import java.util.Calendar
import java.util.Locale

/**
 * The "Rings" clock style: a thick outer ring for the hours (12-hour dial), a thin inner ring
 * for the minutes, both starting at 12 o'clock and flowing with the time; the day of the month
 * and the weekday sit in the middle. Nothing is filled, only the text colour is used.
 */
class RingClockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var color: Int = 0xFF000000.toInt()
        set(value) { field = value; invalidate() }

    /** What the middle shows: the day of the month, the time, or nothing. */
    var inside: ch.remaker.restart.data.Constants.RingInside = ch.remaker.restart.data.Constants.RingInside.Day
        set(value) { field = value; invalidate() }

    /** Outer ring as a 12-hour dial, or the whole day from midnight. */
    var wholeDay: Boolean = false
        set(value) { field = value; invalidate() }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val density = resources.displayMetrics.density

    private val ticker = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.registerReceiver(ticker, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
    }

    override fun onDetachedFromWindow() {
        runCatching { context.unregisterReceiver(ticker) }
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        val cx = width / 2f
        val cy = height / 2f
        val outerWidth = 7f * density
        val innerWidth = 1.5f * density
        val outerR = size / 2f - outerWidth / 2f - density
        val innerR = outerR - outerWidth / 2f - 9f * density

        val now = Calendar.getInstance()
        val minutes = now.get(Calendar.MINUTE)
        val hourSweep = if (wholeDay) 360f * (now.get(Calendar.HOUR_OF_DAY) + minutes / 60f) / 24f
                        else 360f * (now.get(Calendar.HOUR) + minutes / 60f) / 12f   // flowing
        val minuteSweep = 360f * minutes / 60f

        // Faint full tracks, then the elapsed arcs; both start at 12 o'clock (-90°)
        ringPaint.color = ColorUtils.setAlphaComponent(color, 36)
        ringPaint.strokeWidth = outerWidth
        canvas.drawCircle(cx, cy, outerR, ringPaint)
        ringPaint.strokeWidth = innerWidth
        canvas.drawCircle(cx, cy, innerR, ringPaint)

        ringPaint.color = color
        ringPaint.strokeWidth = outerWidth
        rect.set(cx - outerR, cy - outerR, cx + outerR, cy + outerR)
        canvas.drawArc(rect, -90f, hourSweep, false, ringPaint)
        ringPaint.strokeWidth = innerWidth
        rect.set(cx - innerR, cy - innerR, cx + innerR, cy + innerR)
        canvas.drawArc(rect, -90f, minuteSweep, false, ringPaint)

        if (inside == ch.remaker.restart.data.Constants.RingInside.None) return

        // Middle: the day of the month (regular, large) or the time (bold), with a small caps line under it.
        // Weights derive from the user's family (fake-bold where it has only one); tight tracking only for the system sans.
        val weekday = now.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault())
            ?.uppercase(Locale.getDefault()) ?: ""
        val big: String
        val small: String
        if (inside == ch.remaker.restart.data.Constants.RingInside.Time) {
            val is24 = android.text.format.DateFormat.is24HourFormat(context)
            big = android.text.format.DateFormat.format(if (is24) "HH:mm" else "h:mm", now).toString()
            small = "${now.get(Calendar.DAY_OF_MONTH)} $weekday"
            textPaint.typeface = FontManager.weighted(context, 700)
            textPaint.textSize = size * 0.19f
            textPaint.letterSpacing = FontManager.tracking(context, -0.03f)
        } else {
            big = now.get(Calendar.DAY_OF_MONTH).toString()
            small = weekday
            textPaint.typeface = FontManager.weighted(context, 400)
            textPaint.textSize = size * 0.30f
            textPaint.letterSpacing = FontManager.tracking(context, -0.04f)
        }
        textPaint.color = color
        canvas.drawText(big, cx, cy + textPaint.textSize * 0.30f, textPaint)

        textPaint.color = ColorUtils.setAlphaComponent(color, 150)
        textPaint.typeface = FontManager.weighted(context, 400)
        textPaint.textSize = size * 0.075f
        textPaint.letterSpacing = 0.25f
        canvas.drawText(small, cx, cy + textPaint.textSize * 0.30f + size * 0.16f, textPaint)
    }
}
