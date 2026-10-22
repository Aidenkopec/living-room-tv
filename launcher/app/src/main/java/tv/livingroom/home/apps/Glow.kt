package tv.livingroom.home.apps

import kotlin.math.abs
import kotlin.math.roundToInt

// Colours are ARGB ints. Plain Kotlin maths (no android.graphics.Color), so this runs in JVM tests.

/** An app's two colours, picked from its banner (opaque): for an icon tile's background and a card with no artwork at all. */
data class GlowColours(val primary: Int, val secondary: Int)

/** For banners with no real colour (all black, white or grey): a calm slate. */
val NEUTRAL_GLOW = GlowColours(0xFF5A6478.toInt(), 0xFF3A4150.toInt())

/**
 * An app's two colours from its banner's [pixels] (a small sample is
 * plenty). Only clearly coloured pixels count, and only if there's enough
 * of them; otherwise [NEUTRAL_GLOW]. [primary] is the strongest
 * hue, [secondary] the strongest one at least 60° away, else a darker
 * neighbour of [primary].
 */
fun pickGlowColours(pixels: IntArray): GlowColours {
    val weight = DoubleArray(BUCKETS)
    val red = DoubleArray(BUCKETS)
    val green = DoubleArray(BUCKETS)
    val blue = DoubleArray(BUCKETS)
    var opaque = 0
    for (p in pixels) {
        if (alphaOf(p) < 128) continue
        opaque++
        val (h, s, v) = toHsv(p)
        if (s < 0.35f || v < 0.25f) continue
        val bucket = (h / (360f / BUCKETS)).toInt() % BUCKETS
        val w = (s * v).toDouble()
        weight[bucket] += w
        red[bucket] += redOf(p) * w
        green[bucket] += greenOf(p) * w
        blue[bucket] += blueOf(p) * w
    }
    val top = weight.indices.maxBy { weight[it] }
    // A small coloured logo on a grey banner (Apple TV's is 0.5%) shouldn't colour the whole glow.
    if (weight[top] == 0.0 || weight.sum() < MIN_COLOUR_SHARE * opaque) return NEUTRAL_GLOW

    fun average(b: Int) = argb(red[b] / weight[b], green[b] / weight[b], blue[b] / weight[b])
    val primary = average(top)
    val second = weight.indices
        .filter { bucketDistance(it, top) >= 2 && weight[it] >= 0.15 * weight[top] }
        .maxByOrNull { weight[it] }
    val secondary = if (second != null) {
        average(second)
    } else {
        val (h, s, v) = toHsv(primary)
        fromHsv((h + 40f) % 360f, s, v * 0.6f)
    }
    return GlowColours(primary, secondary)
}

/** [colour] with its brightness (HSV value) times [factor], capped at full. */
fun scaleValue(colour: Int, factor: Float): Int {
    val (h, s, v) = toHsv(colour)
    return withAlpha(fromHsv(h, s, (v * factor).coerceAtMost(1f)), alphaOf(colour) / 255f)
}

private const val BUCKETS = 12 // 30° each.

// Coloured share (saturation x value, per opaque pixel) below which a banner counts as grey.
// Measured on the TV's banners: Apple TV 0.5%, YouTube 5.6%, Netflix 8.8%, the rest 31–92%.
private const val MIN_COLOUR_SHARE = 0.02

private fun bucketDistance(a: Int, b: Int): Int = abs(a - b).let { minOf(it, BUCKETS - it) }

private fun alphaOf(c: Int) = (c ushr 24) and 0xFF
private fun redOf(c: Int) = (c shr 16) and 0xFF
private fun greenOf(c: Int) = (c shr 8) and 0xFF
private fun blueOf(c: Int) = c and 0xFF

private fun argb(r: Double, g: Double, b: Double, a: Int = 0xFF): Int =
    (a shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or (g.roundToInt().coerceIn(0, 255) shl 8) or
        b.roundToInt().coerceIn(0, 255)

private fun withAlpha(c: Int, alpha: Float): Int = (c and 0x00FFFFFF) or ((alpha * 255).roundToInt() shl 24)

/** Hue (0–360), saturation and value (0–1). */
private fun toHsv(c: Int): Triple<Float, Float, Float> {
    val r = redOf(c) / 255f
    val g = greenOf(c) / 255f
    val b = blueOf(c) / 255f
    val max = maxOf(r, g, b)
    val delta = max - minOf(r, g, b)
    val h = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return Triple(h, if (max == 0f) 0f else delta / max, max)
}

private fun fromHsv(h: Float, s: Float, v: Float): Int {
    val c = v * s
    val x = c * (1 - abs((h / 60f) % 2f - 1))
    val m = v - c
    val (r, g, b) = when ((h / 60f).toInt() % 6) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return argb((r + m) * 255.0, (g + m) * 255.0, (b + m) * 255.0)
}
