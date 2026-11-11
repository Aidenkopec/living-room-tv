package tv.livingroom.photoprep

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

/*
 * Making the 1920 × 1080 copy from the full-size photo, and the light sharpening
 * its land gets before it's saved. Pure Kotlin, so all of it is tested on the JVM.
 */

/** Lanczos with 3 lobes: sinc(x) · sinc(x / 3) inside ±3, 0 outside. */
fun lanczos3(x: Double): Double {
    val a = abs(x)
    if (a < 1e-9) return 1.0
    if (a >= 3.0) return 0.0
    val px = PI * x
    return 3.0 * sin(px) * sin(px / 3.0) / (px * px)
}

/** For each output pixel, the first input pixel it reads and the weights from there on (they sum to 1). */
class Taps(val first: IntArray, val weights: Array<FloatArray>)

/**
 * Lanczos-3 taps shrinking [inSize] pixels to [outSize]. The kernel is widened by the shrink factor,
 * so every input pixel counts and fine detail averages out instead of aliasing; pixel centres line
 * up as in any image scaler ((o + 0.5) × scale − 0.5).
 */
fun lanczosTaps(inSize: Int, outSize: Int): Taps {
    require(outSize in 1..inSize) { "only shrinking: $inSize to $outSize" }
    val scale = inSize.toDouble() / outSize
    val support = 3.0 * scale
    val first = IntArray(outSize)
    val weights = Array(outSize) { o ->
        val centre = (o + 0.5) * scale - 0.5
        val lo = ceil(centre - support).toInt()
        val hi = floor(centre + support).toInt()
        first[o] = lo
        val w = DoubleArray(hi - lo + 1) { lanczos3((lo + it - centre) / scale) }
        val sum = w.sum()
        FloatArray(w.size) { (w[it] / sum).toFloat() }
    }
    return Taps(first, weights)
}

/**
 * [pixels] (packed 0xRRGGBB, [w] × [h], as `BufferedImage.getRGB` gives them) shrunk to [outW] ×
 * [outH] with Lanczos-3, across then down, in the photo's own sRGB values (as photo tools do, so the
 * look doesn't shift). Past the edges the edge pixel repeats. One channel is worked at a time, so
 * a full-size photo never needs a full-size float copy.
 */
fun shrink(w: Int, h: Int, pixels: IntArray, outW: Int, outH: Int): Channels {
    require(pixels.size == w * h) { "pixels don't match $w × $h" }
    val across = lanczosTaps(w, outW)
    val down = lanczosTaps(h, outH)
    val (r, g, b) = intArrayOf(16, 8, 0).map { shift ->
        val rows = FloatArray(outW * h)
        val line = FloatArray(w)
        for (y in 0 until h) {
            for (x in 0 until w) line[x] = ((pixels[y * w + x] shr shift) and 0xFF) / 255f
            for (o in 0 until outW) {
                val taps = across.weights[o]
                val from = across.first[o]
                var sum = 0f
                for (k in taps.indices) sum += taps[k] * line[(from + k).coerceIn(0, w - 1)]
                rows[y * outW + o] = sum
            }
        }
        val out = FloatArray(outW * outH)
        for (o in 0 until outH) {
            val taps = down.weights[o]
            val from = down.first[o]
            for (k in taps.indices) {
                val weight = taps[k]
                val row = (from + k).coerceIn(0, h - 1) * outW
                for (x in 0 until outW) out[o * outW + x] += weight * rows[row + x]
            }
        }
        for (i in out.indices) out[i] = clip(out[i]) // Lanczos rings a little past 0 and 1 at hard edges.
        out
    }
    return Channels(outW, outH, r, g, b)
}

/** A Gaussian blur of [a] ([w] × [h]) with standard deviation [sigma] pixels, across then down; the edges repeat outwards. */
fun gaussian(a: FloatArray, w: Int, h: Int, sigma: Float): FloatArray {
    val r = ceil(3 * sigma).toInt().coerceAtLeast(1)
    val raw = FloatArray(2 * r + 1) { exp(-((it - r) * (it - r)) / (2f * sigma * sigma)) }
    val total = raw.sum()
    val kernel = FloatArray(raw.size) { raw[it] / total }
    val across = FloatArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            var sum = 0f
            for (k in kernel.indices) sum += kernel[k] * a[y * w + (x + k - r).coerceIn(0, w - 1)]
            across[y * w + x] = sum
        }
    }
    val out = FloatArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            var sum = 0f
            for (k in kernel.indices) sum += kernel[k] * across[(y + k - r).coerceIn(0, h - 1) * w + x]
            out[y * w + x] = sum
        }
    }
    return out
}

/** How the land is sharpened before it's saved (see [sharpen]). */
data class Sharpening(val amount: Float, val sigma: Float, val threshold: Float)

/**
 * Light enough not to halo along ridges or tree tops on the TV, strong enough to give back the
 * detail lost to shrinking and to the TV stretching its 1080p picture onto the 4K panel. The
 * threshold (2 of 255 levels) leaves flat water and the photo's grain alone.
 */
val LAND_SHARPENING = Sharpening(amount = 0.45f, sigma = 0.7f, threshold = 2f / 255f)

/**
 * [p] with its fine detail lifted (an unsharp mask on brightness only, so colours don't fringe):
 * each pixel moves by [s]'s amount times its difference from a Gaussian blur of itself, where that
 * difference is at least the threshold. [weight] (0..1 per pixel) scales it; 0 leaves a pixel exactly as it was.
 */
fun sharpen(p: Channels, s: Sharpening, weight: FloatArray? = null): Channels {
    require(weight == null || weight.size == p.w * p.h) { "weight doesn't match ${p.w} × ${p.h}" }
    val lum = p.mixOf(0.2126f, 0.7152f, 0.0722f)
    val soft = gaussian(lum, p.w, p.h, s.sigma)
    val out = p.copy()
    for (i in lum.indices) {
        val detail = lum[i] - soft[i]
        if (abs(detail) < s.threshold) continue
        val lift = s.amount * detail * (weight?.get(i) ?: 1f)
        out.r[i] = clip(p.r[i] + lift)
        out.g[i] = clip(p.g[i] + lift)
        out.b[i] = clip(p.b[i] + lift)
    }
    return out
}
