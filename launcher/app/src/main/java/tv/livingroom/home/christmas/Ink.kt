package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * A drawing as plain data. The Christmas scenes are built as lists of marks
 * (filled outlines, lines and soft glows) by plain Kotlin, so they can be
 * checked in JVM tests and painted by any canvas: InkPainter on the TV, Java2D
 * in the preview test. Positions are in the picture's own units.
 */

/** A colour with its alpha as 0xAARRGGBB, from an 0xRRGGBB (any alpha bits in [rgb] are dropped). */
fun ink(rgb: Int, alpha: Float = 1f): Int = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (rgb and 0xFFFFFF)

/** One colour of a gradient, [at] 0 to 1 along it. */
data class Stop(val at: Float, val argb: Int)

/** How an outline is filled. */
sealed interface Tone

data class Flat(val argb: Int) : Tone

/** A gradient along the line from ([x0], [y0]) to ([x1], [y1]), holding its end colours beyond it. */
data class Fade(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val stops: List<Stop>) : Tone

/** A gradient outward from ([x], [y]) to [radius]. */
data class Burst(val x: Float, val y: Float, val radius: Float, val stops: List<Stop>) : Tone

/** A path: one verb per segment, and the points the verbs use, in order (x, y pairs). */
class Outline(val verbs: ByteArray, val points: FloatArray) {
    /** Left, top, right, bottom of every point, control points included (so never smaller than the shape). */
    fun bounds(): FloatArray {
        val box = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)
        for (i in points.indices step 2) {
            box[0] = min(box[0], points[i])
            box[1] = min(box[1], points[i + 1])
            box[2] = maxOf(box[2], points[i])
            box[3] = maxOf(box[3], points[i + 1])
        }
        return box
    }

    companion object {
        const val MOVE: Byte = 0 // One point.
        const val LINE: Byte = 1 // One point.
        const val QUAD: Byte = 2 // A control point and the end.
        const val CUBIC: Byte = 3 // Two control points and the end.
        const val CLOSE: Byte = 4 // No points.
    }
}

sealed interface Mark

/**
 * An outline filled with [tone]; [additive] adds its light to what's under it instead of covering it.
 * With a [clip], only the part of the fill inside that outline shows.
 */
class FillMark(val outline: Outline, val tone: Tone, val additive: Boolean = false, val clip: Outline? = null) : Mark

/** An outline drawn as a line [width] thick, with round ends and corners unless not [round] (then cut square). */
class LineMark(val outline: Outline, val argb: Int, val width: Float, val round: Boolean = true, val clip: Outline? = null) : Mark

/** A soft round light added to what's under it: [rgb] at [alpha] in the middle, gone at [radius]. */
class GlowMark(val x: Float, val y: Float, val radius: Float, val rgb: Int, val alpha: Float) : Mark

/** The two faces words are drawn in. */
enum class WordFace { SERIF_BOLD, SANS }

/** How a word is drawn: [size] high in [argb] and [face], and never wider than [widest] (it's drawn smaller instead). */
data class WordStyle(val size: Float, val argb: Int, val face: WordFace = WordFace.SERIF_BOLD, val widest: Float = Float.MAX_VALUE)

/**
 * A word centred on ([x], [y]) both ways, drawn as [style] says: names stitched on a stocking, a sleeping
 * dog's "z". Its box for [marksBounds] is a generous estimate, [WORD_WIDTH] of the size a letter, held to its widest.
 */
data class WordMark(val text: String, val x: Float, val y: Float, val style: WordStyle) : Mark {
    val size: Float get() = style.size
}

/** A letter's width as a share of its size, generously: no letter of a bold serif face is wider. */
const val WORD_WIDTH = 0.75f

/** Builds one [Outline]; every point goes through the sketch's current placement as it's added. */
class PathMaker internal constructor(private val m: FloatArray) {
    private var verbs = ByteArray(16)
    private var verbCount = 0
    private var points = FloatArray(64)
    private var pointCount = 0

    fun moveTo(x: Float, y: Float): PathMaker = add(Outline.MOVE) { point(x, y) }

    fun lineTo(x: Float, y: Float): PathMaker = add(Outline.LINE) { point(x, y) }

    fun quadTo(cx: Float, cy: Float, x: Float, y: Float): PathMaker = add(Outline.QUAD) {
        point(cx, cy)
        point(x, y)
    }

    fun cubicTo(c1: Pair<Float, Float>, c2: Pair<Float, Float>, x: Float, y: Float): PathMaker = add(Outline.CUBIC) {
        point(c1.first, c1.second)
        point(c2.first, c2.second)
        point(x, y)
    }

    fun close(): PathMaker = add(Outline.CLOSE) {}

    internal fun outline() = Outline(verbs.copyOf(verbCount), points.copyOf(pointCount))

    private inline fun add(verb: Byte, pointsOf: () -> Unit): PathMaker {
        if (verbCount == verbs.size) verbs = verbs.copyOf(verbCount * 2)
        verbs[verbCount++] = verb
        pointsOf()
        return this
    }

    private fun point(x: Float, y: Float) {
        if (pointCount + 2 > points.size) points = points.copyOf(points.size * 2)
        points[pointCount++] = m[A] * x + m[C] * y + m[E]
        points[pointCount++] = m[B] * x + m[D] * y + m[F]
    }
}

/** A closed shape through [xy] (x, y pairs). */
fun PathMaker.polygon(vararg xy: Float): PathMaker {
    for (i in xy.indices step 2) if (i == 0) moveTo(xy[0], xy[1]) else lineTo(xy[i], xy[i + 1])
    return close()
}

/** An open line through [xy] (x, y pairs). */
fun PathMaker.through(vararg xy: Float): PathMaker {
    for (i in xy.indices step 2) if (i == 0) moveTo(xy[0], xy[1]) else lineTo(xy[i], xy[i + 1])
    return this
}

fun PathMaker.rect(x: Float, y: Float, w: Float, h: Float): PathMaker = polygon(x, y, x + w, y, x + w, y + h, x, y + h)

/** A rectangle with rounded corners; [bottomRadius] rounds the bottom two differently (a door has square feet). */
fun PathMaker.roundRect(box: Area, radius: Float, bottomRadius: Float = radius): PathMaker {
    val limit = min(abs(box.w), abs(box.h)) / 2
    val top = radius.coerceIn(0f, limit)
    val bottom = bottomRadius.coerceIn(0f, limit)
    val right = box.x + box.w
    val foot = box.y + box.h
    moveTo(box.x + top, box.y).lineTo(right - top, box.y)
    corner(right - top, box.y + top, top, -QUARTER)
    lineTo(right, foot - bottom)
    corner(right - bottom, foot - bottom, bottom, 0f)
    lineTo(box.x + bottom, foot)
    corner(box.x + bottom, foot - bottom, bottom, QUARTER)
    lineTo(box.x, box.y + top)
    corner(box.x + top, box.y + top, top, 2 * QUARTER)
    return close()
}

/** A quarter turn of a circle at ([cx], [cy]), starting at angle [from] and joined to the path. */
private fun PathMaker.corner(cx: Float, cy: Float, radius: Float, from: Float) {
    if (radius > 0f) arc(Ellipse(cx, cy, radius, radius), from, from + QUARTER, joined = true)
}

fun PathMaker.circle(x: Float, y: Float, radius: Float): PathMaker = oval(Ellipse(x, y, radius, radius))

/** A whole ellipse. */
fun PathMaker.oval(e: Ellipse): PathMaker = arc(e, 0f, 2 * PI.toFloat(), joined = false).close()

/**
 * Part of [e]'s edge from angle [from] to [to] (radians, clockwise from 3 o'clock, as on an HTML canvas).
 * It starts a new piece unless [joined], which draws a line to its start first.
 */
fun PathMaker.arc(e: Ellipse, from: Float, to: Float, joined: Boolean = false): PathMaker {
    val pieces = ceil(abs(to - from) / QUARTER - 1e-4f).toInt().coerceAtLeast(1)
    val step = (to - from) / pieces
    val k = 4f / 3f * tan(step / 4)
    val cosT = cos(e.turn)
    val sinT = sin(e.turn)

    fun at(ux: Float, uy: Float) = (e.x + e.rx * ux * cosT - e.ry * uy * sinT) to (e.y + e.rx * ux * sinT + e.ry * uy * cosT)
    val start = at(cos(from), sin(from))
    if (joined) lineTo(start.first, start.second) else moveTo(start.first, start.second)
    for (i in 0 until pieces) {
        val a0 = from + step * i
        val a1 = a0 + step
        val end = at(cos(a1), sin(a1))
        cubicTo(at(cos(a0) - k * sin(a0), sin(a0) + k * cos(a0)), at(cos(a1) + k * sin(a1), sin(a1) - k * cos(a1)), end.first, end.second)
    }
    return this
}

/** A rectangle: its top left corner and its size. */
data class Area(val x: Float, val y: Float, val w: Float, val h: Float)

/** An ellipse's place and size: its middle, its two radii, and how far it's turned (radians, clockwise). */
data class Ellipse(val x: Float, val y: Float, val rx: Float, val ry: Float, val turn: Float = 0f)

/**
 * Collects marks in drawing order. [placed] moves, scales and turns everything drawn inside its
 * block, as an HTML canvas's translate, scale and rotate do (in that order).
 */
class Sketch {
    private val list = ArrayList<Mark>()
    private var m = floatArrayOf(1f, 0f, 0f, 1f, 0f, 0f)
    private var clip: Outline? = null

    val marks: List<Mark> get() = list

    /** How much lengths grow under the current placement. */
    private val stretch: Float get() = sqrt(abs(m[A] * m[D] - m[B] * m[C]))

    fun placed(x: Float, y: Float, scale: Float = 1f, turn: Float = 0f, flip: Boolean = false, block: () -> Unit) {
        val before = m
        val sx = if (flip) -scale else scale
        val a = sx * cos(turn)
        val b = scale * sin(turn)
        val c = -sx * sin(turn)
        val d = scale * cos(turn)
        m = floatArrayOf(
            before[A] * a + before[C] * b,
            before[B] * a + before[D] * b,
            before[A] * c + before[C] * d,
            before[B] * c + before[D] * d,
            before[A] * x + before[C] * y + before[E],
            before[B] * x + before[D] * y + before[F],
        )
        try {
            block()
        } finally {
            m = before
        }
    }

    /** Squashes everything drawn inside its block toward the current placement's x axis: heights become [by] of themselves. */
    fun squashed(by: Float, block: () -> Unit) {
        val before = m
        m = floatArrayOf(before[A], before[B], before[C] * by, before[D] * by, before[E], before[F])
        try {
            block()
        } finally {
            m = before
        }
    }

    /**
     * Fills and lines made inside [block] show only inside [shape], which is placed as things are when this is
     * called. Glows aren't clipped, and one clip doesn't nest inside another (the inner one wins).
     */
    fun clipped(shape: PathMaker.() -> Unit, block: () -> Unit) {
        val before = clip
        clip = PathMaker(m).also { it.shape() }.outline()
        try {
            block()
        } finally {
            clip = before
        }
    }

    fun fill(argb: Int, build: PathMaker.() -> Unit) = fill(Flat(argb), build = build)

    fun fill(tone: Tone, additive: Boolean = false, build: PathMaker.() -> Unit) {
        list += FillMark(PathMaker(m).also { it.build() }.outline(), tone, additive, clip)
    }

    fun line(argb: Int, width: Float, round: Boolean = true, build: PathMaker.() -> Unit) {
        list += LineMark(PathMaker(m).also { it.build() }.outline(), argb, width * stretch, round, clip)
    }

    fun glow(x: Float, y: Float, radius: Float, rgb: Int, alpha: Float) {
        if (alpha <= 0.005f) return
        list += GlowMark(m[A] * x + m[C] * y + m[E], m[B] * x + m[D] * y + m[F], radius * stretch, rgb, alpha.coerceAtMost(1f))
    }

    /**
     * [text] centred on ([x], [y]) in the current placement, drawn as [style] says (see [WordMark]). Turns aren't followed:
     * words stay upright.
     */
    fun word(text: String, x: Float, y: Float, style: WordStyle) {
        val grow = stretch
        list +=
            WordMark(
                text,
                m[A] * x + m[C] * y + m[E],
                m[B] * x + m[D] * y + m[F],
                style.copy(
                    size = style.size * grow,
                    widest =
                        style.widest * grow,
                ),
            )
    }

    /** A gradient between two points given in the current placement's own units. */
    fun fade(x0: Float, y0: Float, x1: Float, y1: Float, vararg stops: Stop): Fade = Fade(
        m[A] * x0 + m[C] * y0 + m[E],
        m[B] * x0 + m[D] * y0 + m[F],
        m[A] * x1 + m[C] * y1 + m[E],
        m[B] * x1 + m[D] * y1 + m[F],
        stops.toList(),
    )

    /** A round gradient given in the current placement's own units. */
    fun burst(x: Float, y: Float, radius: Float, vararg stops: Stop): Burst =
        Burst(m[A] * x + m[C] * y + m[E], m[B] * x + m[D] * y + m[F], radius * stretch, stops.toList())
}

fun Sketch.disc(argb: Int, x: Float, y: Float, radius: Float) = fill(argb) { circle(x, y, radius) }

fun Sketch.oval(argb: Int, e: Ellipse) = fill(argb) { oval(e) }

fun Sketch.box(argb: Int, x: Float, y: Float, w: Float, h: Float) = fill(argb) { rect(x, y, w, h) }

fun Sketch.roundBox(argb: Int, box: Area, radius: Float, bottomRadius: Float = radius) = fill(argb) { roundRect(box, radius, bottomRadius) }

fun Sketch.shape(argb: Int, vararg xy: Float) = fill(argb) { polygon(*xy) }

/**
 * The box around everything [marks] can touch: each outline's points (a clipped fill or line no further than
 * its clip), a line with half its width around it, a glow out to its radius. Empty for no marks.
 */
fun marksBounds(marks: List<Mark>): Area {
    val box = floatArrayOf(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE)

    fun grow(b: FloatArray, pad: Float = 0f) {
        box[0] = min(box[0], b[0] - pad)
        box[1] = min(box[1], b[1] - pad)
        box[2] = maxOf(box[2], b[2] + pad)
        box[3] = maxOf(box[3], b[3] + pad)
    }
    for (mark in marks) {
        when (mark) {
            is FillMark -> fillBounds(mark)?.let { grow(it) }
            is LineMark -> cut(padded(mark.outline.bounds(), mark.width / 2), mark.clip)?.let { grow(it) }
            is GlowMark -> grow(floatArrayOf(mark.x, mark.y, mark.x, mark.y), mark.radius)
            is WordMark -> {
                val half = minOf(mark.text.length * mark.size * WORD_WIDTH, mark.style.widest) / 2
                grow(floatArrayOf(mark.x - half, mark.y - mark.size / 2, mark.x + half, mark.y + mark.size / 2))
            }
        }
    }
    return if (box[0] > box[2]) Area(0f, 0f, 0f, 0f) else Area(box[0], box[1], box[2] - box[0], box[3] - box[1])
}

/** Left, top, right, bottom of what [mark] can paint: its outline's box, cut to its clip's; null when the clip leaves nothing. */
private fun fillBounds(mark: FillMark): FloatArray? = cut(mark.outline.bounds(), mark.clip)

/** The box [b] cut to [clip]'s box (none: as it is); null when nothing is left. */
private fun cut(b: FloatArray, clip: Outline?): FloatArray? {
    val c = clip?.bounds() ?: return b
    val cut = floatArrayOf(maxOf(b[0], c[0]), maxOf(b[1], c[1]), min(b[2], c[2]), min(b[3], c[3]))
    return cut.takeIf { it[0] <= it[2] && it[1] <= it[3] }
}

private fun padded(b: FloatArray, pad: Float) = floatArrayOf(b[0] - pad, b[1] - pad, b[2] + pad, b[3] + pad)

private const val QUARTER = (PI / 2).toFloat()

// The placement's six numbers: x' = A·x + C·y + E, y' = B·x + D·y + F.
private const val A = 0
private const val B = 1
private const val C = 2
private const val D = 3
private const val E = 4
private const val F = 5
