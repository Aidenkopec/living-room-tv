package tv.livingroom.photoprep

import kotlin.math.pow

/*
 * Small picture helpers for the prep tool: a picture as three channels of
 * floats (0 to 1, the photo's own sRGB values), blurs, slopes and medians.
 * Pure Kotlin, so all of it is tested on the JVM.
 */

/** A picture [w] × [h], row by row; [r], [g] and [b] hold 0..1 sRGB values. */
class Channels(val w: Int, val h: Int, val r: FloatArray, val g: FloatArray, val b: FloatArray) {
    init {
        require(r.size == w * h && g.size == w * h && b.size == w * h) { "channels don't match $w × $h" }
    }

    /** A weighted mix of the channels at every pixel (e.g. brightness). */
    fun mixOf(kr: Float, kg: Float, kb: Float): FloatArray = FloatArray(w * h) { kr * r[it] + kg * g[it] + kb * b[it] }

    fun copy(): Channels = Channels(w, h, r.copyOf(), g.copyOf(), b.copyOf())

    /** Half the size each way, each pixel the average of four. */
    fun half(): Channels {
        val hw = w / 2
        val hh = h / 2
        fun shrink(a: FloatArray) = FloatArray(hw * hh) {
            val x = (it % hw) * 2
            val y = (it / hw) * 2
            (a[y * w + x] + a[y * w + x + 1] + a[(y + 1) * w + x] + a[(y + 1) * w + x + 1]) / 4f
        }
        return Channels(hw, hh, shrink(r), shrink(g), shrink(b))
    }

    companion object {
        /** From packed 0xRRGGBB pixels, as `BufferedImage.getRGB` gives them. */
        fun fromRgb(w: Int, h: Int, pixels: IntArray): Channels = Channels(
            w,
            h,
            FloatArray(w * h) { ((pixels[it] shr 16) and 0xFF) / 255f },
            FloatArray(w * h) { ((pixels[it] shr 8) and 0xFF) / 255f },
            FloatArray(w * h) { (pixels[it] and 0xFF) / 255f },
        )
    }
}

fun clip(v: Float, lo: Float = 0f, hi: Float = 1f): Float = v.coerceIn(lo, hi)

/** sRGB value to linear light, with the same plain 2.2 curve the app uses. */
fun toLinear(v: Float): Float = clip(v).pow(2.2f)

/** The average over a (2r+1) × (2r+1) square around each pixel; the edges repeat outwards. */
fun box(a: FloatArray, w: Int, h: Int, r: Int): FloatArray {
    if (r <= 0) return a.copyOf()
    val k = 2 * r + 1
    val across = FloatArray(w * h)
    for (y in 0 until h) {
        var sum = 0f
        for (d in -r..r) sum += a[y * w + d.coerceIn(0, w - 1)]
        for (x in 0 until w) {
            across[y * w + x] = sum / k
            sum += a[y * w + (x + r + 1).coerceAtMost(w - 1)] - a[y * w + (x - r).coerceAtLeast(0)]
        }
    }
    val out = FloatArray(w * h)
    for (x in 0 until w) {
        var sum = 0f
        for (d in -r..r) sum += across[d.coerceIn(0, h - 1) * w + x]
        for (y in 0 until h) {
            out[y * w + x] = sum / k
            sum += across[(y + r + 1).coerceAtMost(h - 1) * w + x] - across[(y - r).coerceAtLeast(0) * w + x]
        }
    }
    return out
}

/** The slope across and down at each pixel: the difference of the neighbours either side, halved; one-sided at the edges. */
fun slopes(a: FloatArray, w: Int, h: Int): Pair<FloatArray, FloatArray> {
    val gx = FloatArray(w * h)
    val gy = FloatArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            val i = y * w + x
            gx[i] = when {
                w == 1 -> 0f
                x == 0 -> a[i + 1] - a[i]
                x == w - 1 -> a[i] - a[i - 1]
                else -> (a[i + 1] - a[i - 1]) / 2f
            }
            gy[i] = when {
                h == 1 -> 0f
                y == 0 -> a[i + w] - a[i]
                y == h - 1 -> a[i] - a[i - w]
                else -> (a[i + w] - a[i - w]) / 2f
            }
        }
    }
    return gx to gy
}

fun median(values: FloatArray): Float {
    require(values.isNotEmpty())
    val s = values.sortedArray()
    val m = s.size / 2
    return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2f
}

/** The [p]th percentile (0..100), between the two nearest values, as a spreadsheet would. */
fun percentile(values: FloatArray, p: Float): Float {
    require(values.isNotEmpty())
    val s = values.sortedArray()
    val pos = (s.size - 1) * p / 100f
    val i = pos.toInt()
    val f = pos - i
    return if (i + 1 < s.size) s[i] + (s[i + 1] - s[i]) * f else s[i]
}

/** A straight-line reading between [xs] → [ys] points at [x], held flat past the ends. */
fun interp(x: Float, xs: FloatArray, ys: FloatArray): Float {
    if (x <= xs.first()) return ys.first()
    if (x >= xs.last()) return ys.last()
    var i = 1
    while (xs[i] < x) i++
    val t = (x - xs[i - 1]) / (xs[i] - xs[i - 1])
    return ys[i - 1] + (ys[i] - ys[i - 1]) * t
}
