package ch.remaker.restart.listener

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * What one stroke on the home screen was, from the finger touching down to lifting: a swipe in one
 * of four directions, one of three shapes (circle, square, triangle) or nothing. Pure logic in
 * inches, no Android: every rule below is pinned by StrokeClassifierTest.
 *
 * The whole path counts, not only where it started and ended:
 *  - a swipe is (nearly) straight: the distance between the ends is at least [SWIPE_STRAIGHTNESS]
 *    of the length drawn. Its direction is the larger of the two moves; it needs the swipe distance.
 *  - anything that bends more is never a swipe. It is a shape when it closes (or nearly does) and
 *    is large enough, otherwise nothing at all. So a circle that stops short can no longer open the
 *    app of a swipe by accident.
 *
 * Shapes, with room for a human hand:
 *  - circle: no corner, about the same distance from the centre all round (spread up to
 *    [CIRCLE_SPREAD]), at least [CIRCLE_MIN_TURN] degrees of the turn drawn; it may stop short (a gap
 *    up to [MAX_GAP] of the length) or run past where it started;
 *  - square: four corners of about 90 degrees (a rectangle counts), the last side may stop short;
 *  - triangle: three corners of about 120 degrees.
 *  The open end of a square or triangle is at most [POLYGON_GAP] of a side: a U is not a square.
 * Either direction, any starting point, any rotation.
 */
object StrokeClassifier {

    data class Point(val x: Float, val y: Float)

    enum class Direction { Left, Right, Up, Down }
    enum class Shape { Circle, Square, Triangle }
    enum class Reason { TooShort, TooSmall, NotClosed, Unclear }

    sealed interface Result
    data class Swipe(val direction: Direction) : Result
    /** [score] 0..100: how close to the ideal shape (shown on the test page). */
    data class Figure(val shape: Shape, val score: Int) : Result
    data class Nothing(val reason: Reason) : Result

    /** Ends at least this share of the length apart: straight enough to be a swipe. */
    const val SWIPE_STRAIGHTNESS = 0.8f
    /** The smaller side of a shape, in inches (about 2 cm). */
    const val MIN_SHAPE_SIZE = 0.8f
    /** The open end of a shape, as a share of its length. */
    const val MAX_GAP = 0.3f
    const val CIRCLE_MIN_TURN = 270f
    const val CIRCLE_SPREAD = 0.24f
    /** The open end of a square or triangle, as a share of one side. */
    const val POLYGON_GAP = 0.7f

    private const val SAMPLES = 64
    private const val WINDOW = 3
    private const val CORNER = 55f

    /**
     * [points] in pixels; [dpiX]/[dpiY] pixels per inch; [swipeX]/[swipeY] the swipe distance in
     * inches for a horizontal and a vertical swipe.
     */
    fun classify(points: List<Point>, dpiX: Float, dpiY: Float, swipeX: Float, swipeY: Float): Result {
        val p = points.map { Point(it.x / dpiX, it.y / dpiY) }
        if (p.size < 2) return Nothing(Reason.TooShort)
        val length = length(p)
        if (length < 0.05f) return Nothing(Reason.TooShort)
        val first = p.first()
        val last = p.last()
        val dx = last.x - first.x
        val dy = last.y - first.y
        if (hypot(dx, dy) >= SWIPE_STRAIGHTNESS * length) {
            val horizontal = abs(dx) > abs(dy)
            val distance = if (horizontal) abs(dx) else abs(dy)
            if (distance < (if (horizontal) swipeX else swipeY)) return Nothing(Reason.TooShort)
            return Swipe(
                when {
                    horizontal && dx > 0 -> Direction.Right
                    horizontal -> Direction.Left
                    dy > 0 -> Direction.Down
                    else -> Direction.Up
                }
            )
        }
        return shape(p)
    }

    private fun shape(raw: List<Point>): Result {
        val minX = raw.minOf { it.x }; val maxX = raw.maxOf { it.x }
        val minY = raw.minOf { it.y }; val maxY = raw.maxOf { it.y }
        val w = maxX - minX
        val h = maxY - minY
        if (min(w, h) < MIN_SHAPE_SIZE) return Nothing(Reason.TooSmall)
        if (max(w, h) / min(w, h) > 3f) return Nothing(Reason.Unclear)

        // Where the loop closes: the point of the second half nearest to the start. A stroke that
        // runs past its start is cut there; one that stops short keeps its end.
        val path = resample(raw, SAMPLES)
        val start = path.first()
        var close = path.lastIndex
        var nearest = Float.MAX_VALUE
        for (j in path.size / 2 until path.size) {
            val d = dist(path[j], start)
            if (d < nearest) { nearest = d; close = j }
        }
        val open = path.subList(0, close + 1)
        val openLength = length(open)
        if (nearest > MAX_GAP * openLength) return Nothing(Reason.NotClosed)

        val turn = abs(totalTurn(open))
        val interior = corners(open, cyclic = false)
        val loop = resampleLoop(open, SAMPLES)
        val corners = corners(loop, cyclic = true)
        val spread = spread(loop)
        val aspect = max(w, h) / min(w, h)

        if (interior.isEmpty() && spread <= CIRCLE_SPREAD && turn >= CIRCLE_MIN_TURN && aspect <= 1.8f) {
            val gap = nearest / openLength
            val score = 100f - spread * 200f - max(0f, gap - 0.08f) * 100f - (aspect - 1f) * 30f
            return Figure(Shape.Circle, score.toInt().coerceIn(0, 100))
        }
        // A polygon may stop short by most of a side; a whole missing side is something else
        val sideGap = nearest / (openLength / max(corners.size, 1))
        if (sideGap > POLYGON_GAP) return Nothing(Reason.NotClosed)
        if (corners.size == 4 && corners.all { it in 50f..130f }) {
            val score = 100f - corners.map { abs(it - 90f) }.average().toFloat() * 1.5f
            return Figure(Shape.Square, score.toInt().coerceIn(0, 100))
        }
        if (corners.size == 3 && corners.all { it in 75f..165f }) {
            val score = 100f - corners.map { abs(it - 120f) }.average().toFloat() * 1.5f
            return Figure(Shape.Triangle, score.toInt().coerceIn(0, 100))
        }
        return Nothing(Reason.Unclear)
    }

    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    private fun length(p: List<Point>): Float {
        var sum = 0f
        for (i in 1 until p.size) sum += dist(p[i - 1], p[i])
        return sum
    }

    /** [n] points evenly spaced along the path. */
    private fun resample(p: List<Point>, n: Int): List<Point> {
        val step = length(p) / (n - 1)
        if (step <= 0f) return List(n) { p.first() }
        val out = ArrayList<Point>(n)
        out.add(p.first())
        var carried = 0f
        var prev = p.first()
        var i = 1
        while (i < p.size && out.size < n) {
            val cur = p[i]
            val d = dist(prev, cur)
            if (carried + d >= step && d > 0f) {
                val t = (step - carried) / d
                val q = Point(prev.x + t * (cur.x - prev.x), prev.y + t * (cur.y - prev.y))
                out.add(q)
                prev = q
                carried = 0f
            } else {
                carried += d
                prev = cur
                i++
            }
        }
        while (out.size < n) out.add(p.last())
        return out
    }

    /** The open path closed with a straight line back to its start, as [n] evenly spaced points (the start not repeated). */
    private fun resampleLoop(open: List<Point>, n: Int): List<Point> = resample(open + open.first(), n + 1).dropLast(1)

    private fun angle(a: Point, b: Point) = atan2(b.y - a.y, b.x - a.x)

    /** Signed turn from direction [a] to direction [b], in degrees within -180..180. */
    private fun turnBetween(a: Float, b: Float): Float {
        var d = Math.toDegrees((b - a).toDouble()).toFloat()
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    private fun totalTurn(p: List<Point>): Float {
        var sum = 0f
        for (i in 1 until p.size - 1) sum += turnBetween(angle(p[i - 1], p[i]), angle(p[i], p[i + 1]))
        return sum
    }

    /**
     * The corners: points where the direction over [WINDOW] steps before and after turns by more
     * than [CORNER] degrees, one per bend (the strongest point of it). Returns each corner's turn.
     */
    private fun corners(p: List<Point>, cyclic: Boolean): List<Float> {
        val n = p.size
        fun at(i: Int) = if (cyclic) p[(i % n + n) % n] else p[i]
        val range = if (cyclic) 0 until n else WINDOW until n - WINDOW
        val turn = FloatArray(n)
        for (i in range) turn[i] = abs(turnBetween(angle(at(i - WINDOW), at(i)), angle(at(i), at(i + WINDOW))))
        val found = mutableListOf<Float>()
        val taken = BooleanArray(n)
        // Strongest bends first; a bend claims the points around it
        for (i in range.sortedByDescending { turn[it] }) {
            if (turn[i] < CORNER) break
            if (taken[i]) continue
            found.add(turn[i])
            for (k in -2 * WINDOW..2 * WINDOW) {
                val j = i + k
                if (cyclic) taken[(j % n + n) % n] = true else if (j in 0 until n) taken[j] = true
            }
        }
        return found
    }

    /** How evenly the loop keeps its distance from its centre: standard deviation / mean. */
    private fun spread(p: List<Point>): Float {
        val cx = p.map { it.x }.average().toFloat()
        val cy = p.map { it.y }.average().toFloat()
        val r = p.map { hypot(it.x - cx, it.y - cy) }
        val mean = r.average().toFloat()
        if (mean <= 0f) return 1f
        val variance = r.map { (it - mean) * (it - mean) }.average().toFloat()
        return sqrt(variance) / mean
    }
}
