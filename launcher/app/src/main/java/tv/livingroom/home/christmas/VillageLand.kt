package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import tv.livingroom.home.screensaver.mix

/*
 * The village's land, drawn as marks: mountains, snowy ground, pines, the
 * snowman and the northern lights. Everything is placed on a 1920 x 1080
 * picture and coloured through the look's light.
 */

/** The picture's size: the TV's own 1920 x 1080. */
const val VILLAGE_W = 1920f
const val VILLAGE_H = 1080f

/** What the village is drawn with: a [look] to colour things by, and the [sketch] the marks go to. */
class Easel(val look: VillageLook, val sketch: Sketch = Sketch()) {
    /** [rgb] in the look's light, opaque. */
    fun c(rgb: Int): Int = look.lit(rgb)

    /** Snow lying on things. */
    val snow: Int get() = look.lyingSnow
}

/** A small seeded random number source (mulberry32), so a seed always lays things out the same way. */
class Mulberry(seed: Int) {
    private var a = seed

    /** The next number, 0 up to (not including) 1. */
    fun next(): Float {
        a += MULBERRY_STEP
        return mulberryMix(a)
    }
}

/** mulberry32's step between states, and its mixing of one state into a number from 0 up to 1. */
internal const val MULBERRY_STEP = 0x6D2B79F5

internal fun mulberryMix(a: Int): Float {
    var t = (a xor (a ushr 15)) * (1 or a)
    t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
    // Rounding to a Float can reach 1 for the largest few values; keep it below, as promised.
    return ((t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0).toFloat().coerceAtMost(BELOW_ONE)
}

private val BELOW_ONE = Math.nextDown(1f)

internal fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** The height of a ground line at [x]: [ys] are its heights at evenly spaced points across the picture. */
fun groundY(ys: FloatArray, x: Float): Float {
    val n = ys.size - 1
    val u = (x / VILLAGE_W).coerceIn(0f, 1f) * n
    val i = min(n - 1, floor(u).toInt())
    return lerp(ys[i], ys[i + 1], smooth(u - i))
}

/** Where a piece of land runs: from [from] to [to] across, down to [foot]. The whole picture's width unless said. */
data class Span(val from: Float = 0f, val to: Float = VILLAGE_W, val foot: Float = VILLAGE_H)

/** Snowy ground under the line through [ys] (evenly spaced over [span]), shaded from [top] down to [bottom] at its foot. */
internal fun Easel.ground(ys: FloatArray, top: Int, bottom: Int, span: Span = Span()) {
    val n = ys.size - 1
    sketch.fill(sketch.fade(0f, ys.min(), 0f, span.foot, Stop(0f, top), Stop(1f, bottom))) {
        moveTo(span.from, span.foot).lineTo(span.from, ys[0])
        for (i in 0 until n) {
            val xa = lerp(span.from, span.to, i.toFloat() / n)
            val xb = lerp(span.from, span.to, (i + 1f) / n)
            val xm = (xa + xb) / 2
            cubicTo(xm to ys[i], xm to ys[i + 1], xb, ys[i + 1])
        }
        lineTo(span.to, span.foot).close()
    }
}

/** A pine with snow on each tier; ([x], [y]) is the foot of its trunk. Three-tier ones are the small far-away kind. */
internal fun Easel.pine(x: Float, y: Float, h: Float, tiers: Int = 4, colour: Int = c(0x24523F)) {
    val w = h * (if (tiers == 3) 0.5f else 0.52f)
    sketch.box(c(0x4B3324), x - h * 0.03f, y - h * 0.1f, h * 0.06f, h * 0.1f)
    for (i in 0 until tiers) {
        val f = i.toFloat() / tiers
        val by = y - h * 0.07f - h * 0.93f * f
        val top = y - h * 0.07f - h * 0.93f * min(1f, f + 1.6f / tiers)
        val hw = w * 0.5f * (1 - f * 0.74f)
        sketch.fill(colour) { moveTo(x, top).lineTo(x + hw, by).quadTo(x, by + h * 0.04f, x - hw, by).close() }
        sketch.fill(ink(0x000014, 0.16f)) {
            moveTo(x, top).lineTo(x + hw, by).quadTo(x + hw * 0.5f, by + h * 0.03f, x + hw * 0.1f, by + h * 0.018f).close()
        }
        snowOnTier(x, top - h * 0.004f, lerp(top, by, 0.58f), hw * 0.58f, h * 0.028f)
    }
}

/** The scalloped snow on one tier of a tree: from its tip at ([x], [top]) down to a wavy edge [sw] either side at [sy]. */
internal fun Easel.snowOnTier(x: Float, top: Float, sy: Float, sw: Float, d: Float) {
    sketch.fill(snow) {
        moveTo(x, top).lineTo(x + sw, sy)
        quadTo(x + sw * 0.7f, sy + d, x + sw * 0.42f, sy - d * 0.6f)
        quadTo(x + sw * 0.1f, sy + d * 1.5f, x - sw * 0.25f, sy - d * 0.4f)
        quadTo(x - sw * 0.6f, sy + d * 1.3f, x - sw, sy).close()
    }
}

/** A row of small far-away pines along the hill line [ys], the further ones drawn first. */
internal fun Easel.forest(ys: FloatArray) {
    val r = Mulberry(21)
    val trees = List(54) { Triple(lerp(-20f, VILLAGE_W + 20, r.next()), lerp(56f, 112f, r.next()), r.next()) }.sortedBy { it.third }
    val green = c(0x1E4438)
    for ((x, h, depth) in trees) pine(x, groundY(ys, x) + 10 + depth * 16, h, tiers = 3, colour = mix(green, look.amb, depth * 0.12f))
}

/**
 * [count] small far-away pines from [seed], [heights] tall, along [span] with their feet about [y] (the further ones lower, drawn
 * first).
 */
internal fun Easel.grove(seed: Int, count: Int, span: Span, y: Float, heights: ClosedFloatingPointRange<Float>) {
    val r = Mulberry(seed)
    val trees = List(count) {
        Triple(lerp(span.from, span.to, r.next()), lerp(heights.start, heights.endInclusive, r.next()), r.next())
    }.sortedBy { it.third }
    val green = c(0x1E4438)
    for ((x, h, depth) in trees) pine(x, y + depth * 16, h, tiers = 3, colour = mix(green, look.amb, depth * 0.12f))
}

/** A jagged far mountain range with snow on its peaks, from [seed], from [x0] to [x1] across. */
internal fun Easel.range(seed: Int, baseY: Float, amp: Float, x0: Float = -40f, x1: Float = VILLAGE_W + 40) {
    val r = Mulberry(seed)
    val peaks = ArrayList<Pair<Float, Float>>()
    var x = x0
    while (x <= x1) {
        peaks += x to baseY - amp * (0.35f + r.next() * 0.65f) * (if (peaks.size % 2 == 1) 1f else 0.45f)
        x += 150f * (0.6f + r.next() * 0.7f)
    }
    sketch.fill(sketch.fade(0f, baseY - amp, 0f, baseY + 60, Stop(0f, look.far), Stop(1f, mix(look.far, look.skyHorizon, 0.35f)))) {
        moveTo(x0, baseY + 80)
        for ((px, py) in peaks) lineTo(px, py)
        lineTo(x1, baseY + 80).close()
    }
    for (i in 1 until peaks.size - 1) {
        val (px, py) = peaks[i]
        val before = peaks[i - 1]
        val after = peaks[i + 1]
        if (py > before.second || py > after.second) continue
        val ax = lerp(px, before.first, 0.36f)
        val ay = lerp(py, before.second, 0.36f)
        val bx = lerp(px, after.first, 0.36f)
        val by = lerp(py, after.second, 0.36f)
        sketch.shape(
            look.farSnow, px, py, bx, by,
            lerp(
                bx,
                px,
                0.35f,
            ),
            by - 6, px + 4, lerp(py, by, 0.8f), lerp(ax, px, 0.4f), ay - 10, ax, ay,
        )
    }
}

// Mount Rundle as a storybook shape: the steep face on the left, the long tilted slab running down to the right.
private val RUNDLE = floatArrayOf(
    0f, 0f, 70f, -46f, 150f, -190f, 192f, -300f, 226f, -332f, 252f, -316f, 276f, -326f, 322f, -290f, 384f, -268f,
    412f, -282f, 470f, -250f, 560f, -234f, 602f, -246f, 684f, -200f, 784f, -150f, 900f, -70f, 1000f, 0f,
)
private const val RUNDLE_TOP_FROM = 4 // The slab's top edge: the outline's points from the summit...
private const val RUNDLE_TOP_TO = 14 // ...to where it meets the valley.

/** Mount Rundle, [scale] times its outline, with its foot's left end at ([x], [y]). */
internal fun Easel.rundle(x: Float, y: Float, scale: Float) = sketch.placed(x, y, scale) {
    val rock = sketch.fade(0f, -332f, 0f, 0f, Stop(0f, mix(look.far, 0x000020, 0.12f)), Stop(1f, mix(look.far, look.skyHorizon, 0.3f)))
    sketch.fill(rock) { polygon(*RUNDLE) }
    // The lit left face.
    sketch.shape(
        mix(
            look.far,
            look.farSnow,
            0.42f,
        ),
        226f, -332f, 192f, -300f, 150f, -190f, 70f, -46f, 0f, 0f, 150f, 0f, 215f, -150f, 240f, -260f,
    )
    // Snow along the top of the slab, with a ragged lower edge.
    val edge = ArrayList<Float>()
    for (i in RUNDLE_TOP_FROM..RUNDLE_TOP_TO) edge += listOf(RUNDLE[i * 2], RUNDLE[i * 2 + 1])
    for (i in RUNDLE_TOP_TO downTo RUNDLE_TOP_FROM) {
        val n = i - RUNDLE_TOP_FROM
        val px = RUNDLE[i * 2]
        val py = RUNDLE[i * 2 + 1]
        edge += listOf(px - 14, py + 26 + (n % 2) * 30 + (if (n == 0) 20 else 0))
        if (n > 0) edge += listOf((px + RUNDLE[i * 2 - 2]) / 2, (py + RUNDLE[i * 2 - 1]) / 2 + 18)
    }
    sketch.shape(look.farSnow, *edge.toFloatArray())
    // The slab's layers.
    for (i in 0 until 5) {
        sketch.line(ink(look.farSnow, 0.35f), 3f) { through(300f + i * 70, -210f + i * 34, 520f + i * 80, -168f + i * 34) }
    }
}

/** A snowman, [scale] times life size, with ([x], [y]) at the bottom; its scarf's tail hangs out to the right. */
internal fun Easel.snowman(x: Float, y: Float, scale: Float) = sketch.placed(x, y, scale) {
    sketch.oval(ink(0x141E50, 0.18f), Ellipse(30f, 0f, 95f, 14f))
    for ((cy, r) in listOf(-52f to 58f, -128f to 42f, -190f to 30f)) {
        val shade = sketch.burst(-r * 0.35f, cy - r * 0.4f, r * 1.4f, Stop(0.07f, c(0xFFFFFF)), Stop(1f, c(0xC4D2EE)))
        sketch.fill(shade) { circle(0f, cy, r) }
    }
    sketch.line(c(0x4A3020), 5f) { through(-36f, -138f, -86f, -172f).through(-70f, -161f, -80f, -146f).through(36f, -138f, 84f, -176f) }
    val coal = c(0x1B1B22)
    for ((cx, cy, r) in listOf(Triple(-10f, -197f, 3.6f), Triple(10f, -197f, 3.6f), Triple(0f, -138f, 4f), Triple(0f, -118f, 4f))) {
        sketch.disc(coal, cx, cy, r)
    }
    for (i in -2..2) sketch.disc(coal, i * 5.5f, -180f - abs(i) * 1.8f, 2f)
    sketch.shape(c(0xF08A2C), 0f, -192f, 26f, -187f, 0f, -184f)
    sketch.box(c(0x23232C), -30f, -221f, 60f, 7f)
    sketch.roundBox(c(0x23232C), Area(-20f, -258f, 40f, 40f), 3f)
    sketch.box(c(0xC9302C), -20f, -232f, 40f, 8f)
    scarf()
}

/** The snowman's scarf: a band round the neck and a striped tail. */
private fun Easel.scarf() {
    val red = c(0xC9302C)
    sketch.line(red, 15f) { moveTo(-27f, -163f).quadTo(0f, -152f, 27f, -163f) }
    val tail = FloatArray(16)
    for (i in 0..7) {
        val u = i / 7f
        tail[i * 2] = 18 + u * 84 * (0.6f + 0.4f * SCARF_WIND)
        tail[i * 2 + 1] = -160 + u * 26 * (1.4f - SCARF_WIND * 0.6f) + sin(-i * 0.9f) * 9 * u * (0.4f + SCARF_WIND)
    }
    sketch.line(red, 14f) { through(*tail) }
    for (i in listOf(2, 4, 6)) {
        sketch.line(c(0xF4EFE4), 14f, round = false) {
            through(
                lerp(tail[i * 2], tail[i * 2 + 2], 0.2f),
                lerp(tail[i * 2 + 1], tail[i * 2 + 3], 0.2f),
                lerp(tail[i * 2], tail[i * 2 + 2], 0.6f),
                lerp(tail[i * 2 + 1], tail[i * 2 + 3], 0.6f),
            )
        }
    }
}

/** The northern lights, [strength] 0 to 1: two bands of thin green curtains fading to violet, adding their light to the sky. */
fun auroraMarks(strength: Float): List<Mark> {
    val sketch = Sketch()
    if (strength <= 0f) return sketch.marks
    for (band in 0..1) {
        var x = -20f
        while (x < VILLAGE_W) {
            val u = x / VILLAGE_W
            val top = VILLAGE_H * (0.1f + 0.09f * band) + sin(u * 5 + band * 2) * 60 + sin(u * 11) * 22
            val height = 190 + 90 * sin(u * 7 + band)
            val alpha = strength * (0.05f + 0.09f * sin(u * 9 + band * 3).pow(2)) * (0.25f + 0.75f * sin(u * PI.toFloat()))
            if (alpha > 0f) {
                val curtain =
                    Fade(
                        0f,
                        top,
                        0f,
                        top + height,
                        listOf(Stop(0f, ink(0x5AFFAA, 0f)), Stop(0.3f, ink(0x5AFFB4, alpha)), Stop(1f, ink(0x785AFF, 0f))),
                    )
                sketch.fill(curtain, additive = true) { rect(x, top, 27f, height) }
            }
            x += 26f
        }
    }
    return sketch.marks
}

private const val SCARF_WIND = 0.6f
