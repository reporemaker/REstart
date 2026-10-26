package ch.remaker.restart.listener

import ch.remaker.restart.listener.StrokeClassifier.Direction
import ch.remaker.restart.listener.StrokeClassifier.Figure
import ch.remaker.restart.listener.StrokeClassifier.Nothing
import ch.remaker.restart.listener.StrokeClassifier.Point
import ch.remaker.restart.listener.StrokeClassifier.Reason
import ch.remaker.restart.listener.StrokeClassifier.Shape
import ch.remaker.restart.listener.StrokeClassifier.Swipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Strokes on a 420 dpi phone (1080 x 2424 px); a swipe needs 0.4 in across or 0.9 in up/down. */
class StrokeClassifierTest {

    private val dpi = 420f
    private val swipeX = 0.39f
    private val swipeY = 0.87f

    private fun classify(p: List<Point>) = StrokeClassifier.classify(p, dpi, dpi, swipeX, swipeY)

    /** A hand is not a plotter: every point moved by up to [amount] px, the same way for each seed. */
    private fun shaky(p: List<Point>, amount: Float, seed: Int = 7): List<Point> {
        val r = Random(seed)
        return p.map { Point(it.x + (r.nextFloat() * 2 - 1) * amount, it.y + (r.nextFloat() * 2 - 1) * amount) }
    }

    private fun line(x1: Float, y1: Float, x2: Float, y2: Float, n: Int = 20) =
        (0..n).map { i -> Point(x1 + (x2 - x1) * i / n, y1 + (y2 - y1) * i / n) }

    /** [turns] of a circle (1.0 closed, 0.8 stops short, 1.1 runs past), starting at [startDeg]. */
    private fun circle(r: Float = 260f, turns: Float = 1f, startDeg: Float = -90f, clockwise: Boolean = true, rx: Float = 1f, n: Int = 60) =
        (0..n).map { i ->
            val a = Math.toRadians((startDeg + (if (clockwise) 1 else -1) * 360f * turns * i / n).toDouble())
            Point(540f + r * rx * cos(a).toFloat(), 1200f + r * sin(a).toFloat())
        }

    /** Walks the corners in order; [cut] leaves that share of the last side undrawn. */
    private fun polygon(corners: List<Point>, cut: Float = 0f, perSide: Int = 15): List<Point> {
        val out = mutableListOf<Point>()
        val sides = corners.size
        for (k in 0 until sides) {
            val a = corners[k]
            val b = corners[(k + 1) % sides]
            val steps = if (k == sides - 1) (perSide * (1 - cut)).toInt() else perSide
            for (i in 0 until steps) out.add(Point(a.x + (b.x - a.x) * i / perSide, a.y + (b.y - a.y) * i / perSide))
        }
        // The last point exactly where the drawing stops: (1 - cut) along the last side
        val a = corners[sides - 1]
        val b = corners[0]
        out.add(Point(a.x + (b.x - a.x) * (1 - cut), a.y + (b.y - a.y) * (1 - cut)))
        return out
    }

    private fun square(s: Float = 500f, rotateDeg: Float = 0f): List<Point> {
        val c = Point(540f, 1200f)
        val a = Math.toRadians(rotateDeg.toDouble())
        return listOf(-1 to -1, 1 to -1, 1 to 1, -1 to 1).map { (sx, sy) ->
            val x = sx * s / 2; val y = sy * s / 2
            Point(c.x + (x * cos(a) - y * sin(a)).toFloat(), c.y + (x * sin(a) + y * cos(a)).toFloat())
        }
    }

    private fun triangle(r: Float = 300f) = listOf(Point(540f, 1200f - r), Point(540f + r * 0.87f, 1200f + r * 0.5f), Point(540f - r * 0.87f, 1200f + r * 0.5f))

    private fun shapeOf(result: StrokeClassifier.Result) = (result as? Figure)?.shape

    // Swipes

    @Test
    fun straightSwipesGoByDirectionWhateverTheirLength() {
        assertEquals(Swipe(Direction.Right), classify(line(300f, 1200f, 600f, 1200f)))     // 0.7 in
        assertEquals(Swipe(Direction.Right), classify(line(100f, 1200f, 1000f, 1210f)))    // across the screen
        assertEquals(Swipe(Direction.Left), classify(line(900f, 1200f, 300f, 1180f)))
        assertEquals(Swipe(Direction.Up), classify(line(540f, 1800f, 560f, 900f)))
        assertEquals(Swipe(Direction.Down), classify(line(540f, 600f, 520f, 1900f)))
    }

    @Test
    fun aShakySlightlyCurvedSwipeIsStillASwipe() {
        val arc = (0..20).map { i -> Point(200f + 30f * i, 1200f + 0.4f * (i - 10) * (i - 10)) }
        assertEquals(Swipe(Direction.Right), classify(shaky(arc, 4f)))
    }

    @Test
    fun tooShortIsNothing() {
        assertEquals(Nothing(Reason.TooShort), classify(line(500f, 1200f, 600f, 1200f)))    // 0.24 in
        assertEquals(Nothing(Reason.TooShort), classify(line(540f, 1200f, 540f, 1500f)))    // 0.7 in up/down
    }

    // Circles

    @Test
    fun closedCirclesEitherWayFromAnyStart() {
        for (start in listOf(-90f, 0f, 137f)) for (cw in listOf(true, false)) {
            assertEquals("start $start cw $cw", Shape.Circle, shapeOf(classify(circle(startDeg = start, clockwise = cw))))
        }
    }

    @Test
    fun aCircleThatStopsShortOrRunsPastIsStillACircle() {
        assertEquals(Shape.Circle, shapeOf(classify(circle(turns = 0.9f))))    // 36 degrees open
        assertEquals(Shape.Circle, shapeOf(classify(circle(turns = 0.8f))))    // 72 degrees open
        assertEquals(Shape.Circle, shapeOf(classify(circle(turns = 1.15f))))   // 54 degrees past the start
    }

    @Test
    fun aCircleOpenByHalfIsNothingAndNeverASwipe() {
        val result = classify(circle(turns = 0.55f))
        assertTrue(result.toString(), result is Nothing)
    }

    @Test
    fun aShakyEggIsACircle() {
        assertEquals(Shape.Circle, shapeOf(classify(shaky(circle(rx = 1.3f), 12f))))
        assertEquals(Shape.Circle, shapeOf(classify(shaky(circle(turns = 0.85f), 10f, seed = 3))))
    }

    @Test
    fun aSmallCircleIsTooSmall() {
        assertEquals(Nothing(Reason.TooSmall), classify(circle(r = 120f)))   // 0.57 in across
    }

    // Squares and triangles

    @Test
    fun squaresClosedOpenRotatedShaky() {
        assertEquals(Shape.Square, shapeOf(classify(polygon(square()))))
        assertEquals(Shape.Square, shapeOf(classify(polygon(square(), cut = 0.3f))))
        assertEquals(Shape.Square, shapeOf(classify(polygon(square(rotateDeg = 20f)))))
        assertEquals(Shape.Square, shapeOf(classify(polygon(square().reversed()))))
        assertEquals(Shape.Square, shapeOf(classify(shaky(polygon(square()), 10f))))
    }

    @Test
    fun aRectangleCountsAsASquare() {
        val rect = listOf(Point(240f, 1000f), Point(840f, 1000f), Point(840f, 1380f), Point(240f, 1380f))   // 1.4 x 0.9 in
        assertEquals(Shape.Square, shapeOf(classify(polygon(rect))))
    }

    @Test
    fun trianglesClosedOpenShaky() {
        assertEquals(Shape.Triangle, shapeOf(classify(polygon(triangle()))))
        assertEquals(Shape.Triangle, shapeOf(classify(polygon(triangle(), cut = 0.3f))))
        classify(polygon(triangle(), cut = 0.55f)).let { assertEquals(it.toString(), Shape.Triangle, shapeOf(it)) }   // half a side missing
        assertEquals(Shape.Triangle, shapeOf(classify(polygon(triangle().reversed()))))
        assertEquals(Shape.Triangle, shapeOf(classify(shaky(polygon(triangle()), 10f))))
    }

    @Test
    fun aSquareIsNotACircleAndACircleNotASquare() {
        assertEquals(Shape.Square, shapeOf(classify(polygon(square()))))
        assertEquals(Shape.Circle, shapeOf(classify(circle())))
    }

    @Test
    fun twentyShakyHandsEachNeverTurnIntoAnotherShapeOrASwipe() {
        for (seed in 1..20) {
            assertEquals("circle $seed", Shape.Circle, shapeOf(classify(shaky(circle(turns = 0.88f + seed % 4 * 0.06f), 12f, seed))))
            assertEquals("square $seed", Shape.Square, shapeOf(classify(shaky(polygon(square(rotateDeg = seed * 3f), cut = seed % 3 * 0.15f), 12f, seed))))
            assertEquals("triangle $seed", Shape.Triangle, shapeOf(classify(shaky(polygon(triangle(), cut = seed % 3 * 0.15f), 12f, seed))))
        }
    }

    // Neither

    @Test
    fun anLOrAZigzagIsNothing() {
        val l = line(300f, 900f, 300f, 1500f) + line(300f, 1500f, 800f, 1500f)
        assertTrue(classify(l) is Nothing)
        val zigzag = line(200f, 1000f, 500f, 1400f) + line(500f, 1400f, 800f, 1000f) + line(800f, 1000f, 1000f, 1400f)
        assertTrue(classify(zigzag) is Nothing)
    }

    @Test
    fun anOpenUIsNothing() {
        val u = line(300f, 900f, 300f, 1500f) + line(300f, 1500f, 800f, 1500f) + line(800f, 1500f, 800f, 900f)
        assertEquals(Nothing(Reason.NotClosed), classify(u))
    }
}
