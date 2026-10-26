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
 *  - circle: about the same distance from the centre all round (spread up to [CIRCLE_SPREAD]),
 *    at least [CIRCLE_MIN_TURN] degrees round its centre, curving all along (little of it straight),
 *    no wider than [CIRCLE_MAX_ASPECT]; running past its start, only the first round is judged;
 *    a hand leaves a kink or two (up to [CIRCLE_KINKS], each softer than [CIRCLE_KINK]); it may stop
 *    short (a gap up to [MAX_GAP] of the length) or run past where it started;
 *  - square: any four-sided figure, the sides need not be equal (a rectangle, a trapezium, a
 *    slanted one): four corners between 35 and 145 degrees of turn, together about a full turn;
 *    the last side may stop short;
 *  - triangle: three corners (55 to 178 degrees of turn), the triangle through them covering
 *    nearly all of the shape.
 *  The open end of a square or triangle is at most [POLYGON_GAP] of a side: a U is not a square.
 *  A square has straight sides ([SQUARE_MIN_STRAIGHT]); a circle drawn with a few kinks does not,
 *  so it is not taken for a square. (A shaky triangle can be as little straight as such a circle,
 *  so the rule is the square's only.)
 *
 * The numbers come from real hands: 60 circles drawn on the try-out pad on 26 Oct 2026 (see
 * StrokeClassifierTest.realCircles).
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
    /** The smaller side of a shape, in inches (about 1.4 cm). */
    const val MIN_SHAPE_SIZE = 0.55f
    /** The open end of a shape, as a share of its length. */
    const val MAX_GAP = 0.3f
    /** Degrees round its centre a circle must go at least (a quarter may be missing). */
    const val CIRCLE_MIN_TURN = 270f
    /** Turned this much and the stroke ran on past its start: cut after the first round. */
    private const val OVERSHOOT_TURN = 380f
    /** Wider than this and it is an ellipse or a rounded rectangle, not a circle. */
    const val CIRCLE_MAX_ASPECT = 1.6f
    const val CIRCLE_SPREAD = 0.28f
    /** Kinks a circle may have, and how sharp each may be at most. */
    const val CIRCLE_KINKS = 2
    const val CIRCLE_KINK = 120
    /** Straight share of the loop: a circle at most, a square at least. */
    const val CIRCLE_MAX_STRAIGHT = 0.65f
    const val SQUARE_MIN_STRAIGHT = 0.55f
    /** Share of the loop a triangle through its three corners must cover. */
    const val TRIANGLE_FILL = 0.7f
    const val TRIANGLE_FILL_SURE = 0.8f
    const val TRIANGLE_MIN_STRAIGHT = 0.65f
    /** The open end of a square or triangle, as a share of one side. */
    const val POLYGON_GAP = 0.7f

    private const val SAMPLES = 64
    private const val WINDOW = 3
    private const val CORNER = 55f
    private const val STRAIGHT_TURN = 15f

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
        val m = metrics(p)
        // A stroke that ran well past its start is also judged by its first round alone
        val firstRound = if (abs(m.turnAll) >= OVERSHOOT_TURN) metrics(p, firstRound = true) else null
        return shape(m, firstRound)
    }

    /**
     * Everything the decision looks at, for the test page log (and the data to tune the rules by).
     * Sizes in inches; [gap] the open end as a share of the length, [sideGap] as a share of a side;
     * [turn] degrees turned; [corners] the turn at each corner of the closed loop.
     */
    data class Metrics(
        val length: Float, val straightness: Float, val width: Float, val height: Float,
        val gap: Float, val sideGap: Float, val turn: Float, val interiorCorners: Int,
        val corners: List<Int>, val spread: Float, val aspect: Float,
        /** The turn of each corner inside the drawn path (not where it closes). */
        val interior: List<Int> = emptyList(),
        /** Share of the closed loop that runs straight: high for the sides of a square or triangle, low all round a circle. */
        val straight: Float = 0f,
        /** Degrees the judged loop goes round its centre, seen from there (360 once round). */
        val sweep: Float = 0f,
        /** The pen's turn over the whole stroke. */
        val turnAll: Float = 0f,
        /** The turn between where the pen arrives at the end and where it left the start: a corner when the stroke began at one. */
        val startTurn: Int = 0,
        /** With three corners, the share of the loop's area the triangle through them covers (about 1 for a triangle). */
        val fill: Float = 0f,
        /** The same for the triangle through the start and two corners on the way. */
        val startFill: Float = 0f,
    )

    fun measure(points: List<Point>, dpiX: Float, dpiY: Float): Metrics? {
        val p = points.map { Point(it.x / dpiX, it.y / dpiY) }
        if (p.size < 2 || length(p) <= 0f) return null
        return metrics(p)
    }

    private fun metrics(raw: List<Point>, firstRound: Boolean = false): Metrics {
        val length = length(raw)
        val straightness = dist(raw.first(), raw.last()) / length
        val w = raw.maxOf { it.x } - raw.minOf { it.x }
        val h = raw.maxOf { it.y } - raw.minOf { it.y }
        // Where the loop closes: at the point of its second half nearest to the start or, with
        // [firstRound], where the pen has turned once round (a hand that runs on past its start may
        // draw the second round beside the first; then only the first round is judged).
        val path = resample(raw, SAMPLES)
        val start = path.first()
        val turns = turns(path)
        val once = if (firstRound) turns.indexOfFirst { abs(it) >= 360f } else -1
        val close = if (once > 0) once else {
            var best = path.lastIndex
            var bestD = Float.MAX_VALUE
            for (j in path.size / 2 until path.size) {
                val d = dist(path[j], start)
                if (d < bestD) { bestD = d; best = j }
            }
            best
        }
        val nearest = dist(path[close], start)
        val open = path.subList(0, close + 1)
        val openLength = length(open)
        val loop = resampleLoop(open, SAMPLES)
        val places = cornerPlaces(loop, cyclic = true)
        val corners = places.map { it.second }
        // With three corners: how much of the loop the triangle through them covers
        val fill = if (places.size == 3) {
            val area = abs(area(loop))
            if (area > 0f) abs(area(places.sortedBy { it.first }.map { loop[it.first] })) / area else 0f
        } else 0f
        val interiorPlaces = cornerPlaces(open, cyclic = false)
        val interior = interiorPlaces.map { it.second }
        // The same for a stroke begun at a corner: the start and two corners on the way
        val loopArea = abs(area(loop))
        val startFill = if (interiorPlaces.size == 2 && loopArea > 0f) {
            abs(area(listOf(open.first()) + interiorPlaces.sortedBy { it.first }.map { open[it.first] })) / loopArea
        } else 0f
        return Metrics(
            length = length,
            straightness = straightness,
            width = w,
            height = h,
            gap = if (openLength > 0f) nearest / openLength else 1f,
            sideGap = if (openLength > 0f) nearest / (openLength / max(corners.size, 1)) else 1f,
            turn = abs(totalTurn(open)),
            sweep = abs(sweeps(open).last()),
            turnAll = turns.last(),
            fill = fill,
            startFill = startFill,
            startTurn = if (open.size > 2 * WINDOW) abs(turnBetween(angle(open[open.size - 1 - WINDOW], open.last()), angle(open[0], open[WINDOW]))).toInt() else 0,
            interiorCorners = interior.size,
            interior = interior.map { it.toInt() },
            straight = straightShare(loop),
            corners = corners.map { it.toInt() },
            spread = spread(loop),
            aspect = if (min(w, h) > 0f) max(w, h) / min(w, h) else Float.MAX_VALUE,
        )
    }

    /** Lab only: where the interior corners of the open path are, as a share of its length (0 start, 1 end), and their turn. */
    internal fun interiorCornerPlaces(points: List<Point>, dpiX: Float, dpiY: Float): List<Pair<Float, Float>> {
        val raw = points.map { Point(it.x / dpiX, it.y / dpiY) }
        val path = resample(raw, SAMPLES)
        val start = path.first()
        var close = path.lastIndex
        var nearest = Float.MAX_VALUE
        for (j in path.size / 2 until path.size) {
            val d = dist(path[j], start)
            if (d < nearest) { nearest = d; close = j }
        }
        val open = path.subList(0, close + 1)
        val n = open.size
        val out = mutableListOf<Pair<Float, Float>>()
        val turn = FloatArray(n)
        for (i in WINDOW until n - WINDOW) turn[i] = abs(turnBetween(angle(open[i - WINDOW], open[i]), angle(open[i], open[i + WINDOW])))
        val taken = BooleanArray(n)
        for (i in (WINDOW until n - WINDOW).sortedByDescending { turn[it] }) {
            if (turn[i] < CORNER) break
            if (taken[i]) continue
            out.add(i.toFloat() / (n - 1) to turn[i])
            for (k in -2 * WINDOW..2 * WINDOW) if (i + k in 0 until n) taken[i + k] = true
        }
        return out
    }

    private fun circle(m: Metrics): Figure? {
        if (m.gap > MAX_GAP) return null
        // Begun at a clear corner, with a square corner on the way: a polygon whose other corners went soft
        if (m.startTurn >= 70 && m.interior.size >= 2 && m.interior.max() >= 90) return null
        val kinksOk = m.interior.size <= CIRCLE_KINKS && m.interior.all { it < CIRCLE_KINK }
        // Round enough: by the turn of the pen or, for a loop whose pen turned back on itself for a
        // moment, by the degrees it goes round its centre
        val round = max(m.turn, m.sweep) >= CIRCLE_MIN_TURN
        if (!(kinksOk && m.straight <= CIRCLE_MAX_STRAIGHT && m.spread <= CIRCLE_SPREAD && round && m.aspect <= CIRCLE_MAX_ASPECT)) return null
        val score = 100f - m.spread * 200f - max(0f, m.gap - 0.08f) * 100f - (m.aspect - 1f) * 30f
        return Figure(Shape.Circle, score.toInt().coerceIn(0, 100))
    }

    private fun shape(m: Metrics, firstRound: Metrics? = null): Result {
        if (min(m.width, m.height) < MIN_SHAPE_SIZE) return Nothing(Reason.TooSmall)
        if (m.aspect > 3f) return Nothing(Reason.Unclear)
        // Three clear corners make a triangle before anything else: a circle may forgive two kinks,
        // and a shaky triangle can look round enough otherwise
        if (m.gap <= MAX_GAP) triangle(m)?.let { return it }
        // The whole stroke, or (when it ran well past its start) its first round: whichever is a circle
        circle(m)?.let { return it }
        firstRound?.let { circle(it) }?.let { return it }
        if (m.gap > MAX_GAP) return Nothing(Reason.NotClosed)

        // A polygon may stop short by most of a side; a whole missing side is something else
        if (m.sideGap > POLYGON_GAP) return Nothing(Reason.NotClosed)
        square(m)?.let { return it }
        return Nothing(Reason.Unclear)
    }

    /**
     * Any four-sided figure (rectangle, trapezium, a slanted one): four corners between 30 and 165
     * degrees of turn, together about a full turn, and straight sides. Hands hide corners:
     *  - a stroke that began at a corner and stops just short of it (or just past it) hides that
     *    corner in its closing, or the closing makes a false one: three corners on the way and the
     *    one at the start make four;
     *  - a corner drawn round is no corner: two on the way and a clear one at the start, straight
     *    sides, and the triangle through those three covering too little to be a triangle.
     */
    private fun square(m: Metrics): Figure? {
        if (m.straight < SQUARE_MIN_STRAIGHT) return null
        fun quad(c: List<Int>) = c.size == 4 && c.all { it in 30..165 } && c.sum() in 250..450
        val corners = when {
            quad(m.corners) -> m.corners
            m.interior.size == 3 && m.startTurn >= CORNER && quad(m.interior + m.startTurn) -> m.interior + m.startTurn
            m.corners.size == 3 && m.interior.size == 2 && m.startTurn >= 70 && m.straight >= 0.6f && m.fill < 0.6f -> m.interior + m.startTurn + 90
            else -> return null
        }
        val score = 100f - corners.map { abs(it - 90) }.average().toFloat() * 1.5f
        return Figure(Shape.Square, score.toInt().coerceIn(0, 100))
    }

    /**
     * Three corners, and the triangle through them covering nearly all of the loop ([TRIANGLE_FILL]):
     * a four-sided figure whose fourth corner went soft covers half, a circle less. That cover is
     * what tells a triangle; the corners themselves vary widely in a hand (drawn round or sharp,
     * 55 to 178 degrees of turn). As for squares, a stroke begun at a corner may hide it in the
     * closing or make a false one there: two corners on the way and the one at the start make three.
     */
    private fun triangle(m: Metrics): Figure? {
        fun tri(c: List<Int>) = c.size == 3 && c.all { it in 55..178 }
        // The open end measured against a side of a triangle (a third of the length), not of the loop's corners
        if (m.gap * 3 > POLYGON_GAP) return null
        // Covering most of it is a triangle; covering a little less, its sides must also be straight
        // (a circle with a kink or two can cover that much: 0.75 measured, with 0.64 of it straight)
        fun covers(fill: Float) = fill >= TRIANGLE_FILL_SURE || (fill >= TRIANGLE_FILL && m.straight >= TRIANGLE_MIN_STRAIGHT)
        val corners = when {
            tri(m.corners) && covers(m.fill) -> m.corners
            m.interior.size == 2 && m.startTurn >= CORNER && covers(m.startFill) && tri(m.interior + m.startTurn) -> m.interior + m.startTurn
            else -> return null
        }
        val score = 100f - corners.map { abs(it - 120) }.average().toFloat() * 1.5f
        return Figure(Shape.Triangle, score.toInt().coerceIn(0, 100))
    }

    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)

    /** Signed area of the closed polygon through [p] (shoelace). */
    private fun area(p: List<Point>): Float {
        var sum = 0f
        for (i in p.indices) {
            val a = p[i]
            val b = p[(i + 1) % p.size]
            sum += a.x * b.y - b.x * a.y
        }
        return sum / 2f
    }

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

    /** The pen's turn so far at each point, signed by direction. */
    private fun turns(p: List<Point>): FloatArray {
        val out = FloatArray(p.size)
        var sum = 0f
        for (i in 1 until p.size - 1) {
            sum += turnBetween(angle(p[i - 1], p[i]), angle(p[i], p[i + 1]))
            out[i] = sum
        }
        out[p.size - 1] = sum
        return out
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
    private fun corners(p: List<Point>, cyclic: Boolean): List<Float> = cornerPlaces(p, cyclic).map { it.second }

    /** The corners as (index on [p], turn). */
    private fun cornerPlaces(p: List<Point>, cyclic: Boolean): List<Pair<Int, Float>> {
        val n = p.size
        fun at(i: Int) = if (cyclic) p[(i % n + n) % n] else p[i]
        val range = if (cyclic) 0 until n else WINDOW until n - WINDOW
        val turn = FloatArray(n)
        for (i in range) turn[i] = abs(turnBetween(angle(at(i - WINDOW), at(i)), angle(at(i), at(i + WINDOW))))
        val found = mutableListOf<Pair<Int, Float>>()
        val taken = BooleanArray(n)
        // Strongest bends first; a bend claims the points around it
        for (i in range.sortedByDescending { turn[it] }) {
            if (turn[i] < CORNER) break
            if (taken[i]) continue
            found.add(i to turn[i])
            for (k in -2 * WINDOW..2 * WINDOW) {
                val j = i + k
                if (cyclic) taken[(j % n + n) % n] = true else if (j in 0 until n) taken[j] = true
            }
        }
        return found
    }

    /**
     * For each point, the degrees the path has gone round its centre (the mean of its points) so far,
     * signed by direction: the angle seen from the centre, summed step by step.
     */
    private fun sweeps(p: List<Point>): FloatArray {
        val cx = p.map { it.x }.average().toFloat()
        val cy = p.map { it.y }.average().toFloat()
        val out = FloatArray(p.size)
        var prev = atan2(p[0].y - cy, p[0].x - cx)
        var sum = 0f
        for (i in 1 until p.size) {
            val a = atan2(p[i].y - cy, p[i].x - cx)
            sum += turnBetween(prev, a)
            out[i] = sum
            prev = a
        }
        return out
    }

    /** Share of the loop's points where the direction over [WINDOW] steps either side turns by less than [STRAIGHT_TURN]. */
    private fun straightShare(p: List<Point>): Float {
        val n = p.size
        fun at(i: Int) = p[(i % n + n) % n]
        var straight = 0
        for (i in 0 until n) if (abs(turnBetween(angle(at(i - WINDOW), at(i)), angle(at(i), at(i + WINDOW)))) < STRAIGHT_TURN) straight++
        return straight.toFloat() / n
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
