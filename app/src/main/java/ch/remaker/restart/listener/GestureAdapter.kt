package ch.remaker.restart.listener

/**
 * GestureAdapter provides empty implementations for all GestureListener methods.
 * Override only what you need.
 */
abstract class GestureAdapter : GestureManager.GestureListener {
    override fun onSwipeLeft() {}
    override fun onSwipeRight() {}
    override fun onSwipeUp() {}
    override fun onSwipeDown() {}
    override fun onShape(shape: StrokeClassifier.Shape) {}
    override fun onLongPress() {}
    override fun onDoubleTap() {}
    override fun onSingleTap() {}
}
