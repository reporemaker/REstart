package ch.remaker.restart.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import ch.remaker.restart.listener.GestureManager
import ch.remaker.restart.listener.StrokeClassifier

/**
 * The try-out pad in Settings > Gestures: draw or swipe on it and [onResult] gets what the home
 * screen would make of the same stroke (the same classifier, the same swipe distance). The stroke
 * stays drawn until the next one starts.
 */
class GesturePad(context: Context) : View(context) {

    var onResult: ((StrokeClassifier.Result) -> Unit)? = null
    var ink: Int = 0
        set(value) { field = value; paint.color = value; invalidate() }

    private val path = Path()
    private val points = ArrayList<StrokeClassifier.Point>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // The page must not scroll or take the stroke while drawing
                parent?.requestDisallowInterceptTouchEvent(true)
                path.reset()
                points.clear()
                path.moveTo(event.x, event.y)
                points.add(StrokeClassifier.Point(event.x, event.y))
            }
            MotionEvent.ACTION_MOVE -> {
                for (h in 0 until event.historySize) add(event.getHistoricalX(h), event.getHistoricalY(h))
                add(event.x, event.y)
            }
            MotionEvent.ACTION_UP -> {
                add(event.x, event.y)
                onResult?.invoke(GestureManager.classify(context, points))
            }
        }
        invalidate()
        return true
    }

    private fun add(x: Float, y: Float) {
        path.lineTo(x, y)
        points.add(StrokeClassifier.Point(x, y))
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }
}
