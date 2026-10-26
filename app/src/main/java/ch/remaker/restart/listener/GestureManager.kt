package ch.remaker.restart.listener

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewConfiguration
import ch.remaker.common.AppLogger
import ch.remaker.restart.data.Prefs
import ch.remaker.restart.listener.StrokeClassifier.Point
import kotlin.math.hypot

/**
 * Touches on the home screen. Taps, double taps and long presses come from Android's
 * GestureDetector; every stroke (finger down, move, up) is read whole by [StrokeClassifier]:
 * a straight one is a swipe in its direction (one action per direction, however long), a closed
 * one a circle, square or triangle, and anything else does nothing.
 */
class GestureManager(
    private val context: Context,
    private val listener: GestureListener,
    // When false a tap is reported as soon as the finger lifts instead of after the double-tap timeout.
    private val waitForDoubleTap: Boolean = true
) : GestureDetector.SimpleOnGestureListener() {

    private val gestureDetector = GestureDetector(context, this)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val stroke = ArrayList<Point>()
    private var moved = false
    private var longPressed = false
    private var twoFingers = false

    companion object {
        private const val TAG = "GestureManager"

        /** The swipe distance in inches: [Prefs.swipeDistance] of the screen width (across) or height (up/down). */
        fun swipeInches(context: Context): Pair<Float, Float> {
            val m = context.resources.displayMetrics
            val share = Prefs(context).swipeDistance
            return (m.widthPixels / m.xdpi * share) to (m.heightPixels / m.ydpi * share)
        }

        fun classify(context: Context, points: List<Point>): StrokeClassifier.Result {
            val m = context.resources.displayMetrics
            val (x, y) = swipeInches(context)
            return StrokeClassifier.classify(points, m.xdpi, m.ydpi, x, y)
        }
    }

    interface GestureListener {
        fun onSwipeLeft()
        fun onSwipeRight()
        fun onSwipeUp()
        fun onSwipeDown()
        fun onShape(shape: StrokeClassifier.Shape)
        fun onLongPress()
        fun onSingleTap()
        fun onDoubleTap()
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                stroke.clear()
                stroke.add(Point(event.x, event.y))
                moved = false
                longPressed = false
                twoFingers = false
            }

            MotionEvent.ACTION_POINTER_DOWN -> twoFingers = true

            MotionEvent.ACTION_MOVE -> {
                // Every point the screen reported, not only the last of each frame: shapes need the path
                for (h in 0 until event.historySize) stroke.add(Point(event.getHistoricalX(h), event.getHistoricalY(h)))
                stroke.add(Point(event.x, event.y))
                if (!moved && hypot(event.x - stroke.first().x, event.y - stroke.first().y) > slop) moved = true
            }

            MotionEvent.ACTION_UP -> {
                stroke.add(Point(event.x, event.y))
                // A long press or a two-finger touch already decided what this touch is
                if (moved && !longPressed && !twoFingers) dispatch(classify(context, stroke))
                stroke.clear()
            }

            MotionEvent.ACTION_CANCEL -> stroke.clear()
        }
        return gestureDetector.onTouchEvent(event)
    }

    private fun dispatch(result: StrokeClassifier.Result) {
        AppLogger.d(TAG, "stroke: $result")
        when (result) {
            is StrokeClassifier.Swipe -> when (result.direction) {
                StrokeClassifier.Direction.Left -> listener.onSwipeLeft()
                StrokeClassifier.Direction.Right -> listener.onSwipeRight()
                StrokeClassifier.Direction.Up -> listener.onSwipeUp()
                StrokeClassifier.Direction.Down -> listener.onSwipeDown()
            }
            is StrokeClassifier.Figure -> listener.onShape(result.shape)
            is StrokeClassifier.Nothing -> {}
        }
    }

    override fun onDown(event: MotionEvent): Boolean = true

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        if (waitForDoubleTap) return false
        listener.onSingleTap()
        return true
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        if (!waitForDoubleTap) return false
        listener.onSingleTap()
        return true
    }

    override fun onDoubleTap(e: MotionEvent): Boolean {
        listener.onDoubleTap()
        return true
    }

    override fun onLongPress(e: MotionEvent) {
        longPressed = true
        listener.onLongPress()
    }
}
