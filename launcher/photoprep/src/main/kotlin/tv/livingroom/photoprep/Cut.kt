package tv.livingroom.photoprep

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/*
 * Cutting the sky out of a photo: find the skyline, refine it at full size,
 * take away clouds and leftover rims, then work out the layers the app paints
 * with: where the sky is, where the lake shows the sky, the water's shimmer,
 * and the high rock that catches the low sun.
 */

/**
 * A hand-set point (in the half-size copy for guides, the full size for floors); a guide keeps the
 * skyline within [radius] rows of [y].
 */
data class Guide(val x: Int, val y: Int, val radius: Int)

data class Point(val x: Int, val y: Int)

/** Everything one photo needs, in pixels of the 1920 × 1080 copy unless it says otherwise. */
data class CutSettings(
    /** Rows of the half-size copy the skyline may be on. */
    val band: IntRange,
    /** Hand-set guides on the half-size copy, left to right. */
    val guides: List<Guide>,
    /** Stretches where the photo's own clouds sit on dark forest and are cut away. */
    val cloudColumns: List<IntRange>,
    /** A line the edge may not be above, left to right. */
    val floor: List<Point>,
    /** A line the edge is set to exactly, left to right (where the cloud cut took too much). */
    val exact: List<Point>,
    /** Eye level: the row the lake mirrors about (null for a photo with no still water). */
    val eyeRow: Float?,
    /** Where the water starts; the lake shows the sky only below it. */
    val shoreRow: Float,
    /** No land may be above this row (the arc's top: the sun and moon must pass over the land). */
    val landNeverAbove: Int,
    /** Where the highest land must be, to catch a crop that came out differently. */
    val summitRows: IntRange,
)

/**
 * The skyline on [p] (meant for the half-size copy): one row per column, the first row of land.
 * The sky above is smooth and bright, the land below textured or darker; the best path is found
 * across all columns at once, moving at most [jump] rows per column, inside [band] and the [guides].
 */
fun skyline(p: Channels, band: IntRange, guides: List<Guide> = emptyList(), jump: Int = 4, smooth: Float = 0.5f): IntArray {
    val w = p.w
    val h = p.h
    val lo = band.first.coerceIn(0, h - 1)
    val hi = (band.last + 1).coerceIn(lo + 1, h)
    val lum = p.mixOf(0.3f, 0.59f, 0.11f)
    val (gx, gy) = slopes(lum, w, h)
    val tex = box(FloatArray(w * h) { hypot(gx[it], gy[it]) }, w, h, 1)
    val topRows = min(h, max(lo, 8) + 8)
    val t0 = percentile(tex.copyOfRange(0, topRows * w), 90f) * 2.5f + 0.004f
    val skyRows = min(h, max(8, lo + 4))
    val skyLum = FloatArray(w) { x -> median(FloatArray(skyRows) { lum[it * w + x] }) }
    val raw = FloatArray(w * h) {
        val texture = clip(tex[it] / t0 - 0.3f) * 0.75f
        val darker = clip((skyLum[it % w] - lum[it]) / 0.2f - 0.2f) * 0.6f
        clip(texture + darker)
    }
    val land = box(raw, w, h, 1)
    // Running totals down each column: land above the line and sky below it both cost.
    val up = Array(h + 1) { FloatArray(w) }
    val down = Array(h + 1) { FloatArray(w) }
    for (y in 0 until h) {
        for (x in 0 until w) {
            up[y + 1][x] = up[y][x] + land[y * w + x]
            down[y + 1][x] = down[y][x] + 1f - land[y * w + x]
        }
    }
    val n = hi - lo
    val gxs = guides.map { it.x.toFloat() }.toFloatArray()
    val gys = guides.map { it.y.toFloat() }.toFloatArray()
    val grs = guides.map { it.radius.toFloat() }.toFloatArray()
    fun score(i: Int, x: Int): Float {
        val y = lo + i
        var s = (up[y][x] - up[lo][x]) + (down[hi][x] - down[y][x])
        if (guides.isNotEmpty() && abs(y - interp(x.toFloat(), gxs, gys)) > interp(x.toFloat(), gxs, grs)) s += 1e6f
        return s
    }
    var cost = FloatArray(n) { score(it, 0) }
    val back = Array(w) { IntArray(n) }
    for (x in 1 until w) {
        val next = FloatArray(n)
        for (i in 0 until n) {
            var best = Float.POSITIVE_INFINITY
            var from = 0
            for (d in -jump..jump) {
                val src = (i - d).coerceIn(0, n - 1)
                val c = cost[src] + smooth * abs(d)
                if (c < best) {
                    best = c
                    from = src
                }
            }
            next[i] = best + score(i, x)
            back[x][i] = from
        }
        cost = next
    }
    val line = IntArray(w)
    var at = cost.indices.minBy { cost[it] }
    line[w - 1] = at
    for (x in w - 1 downTo 1) {
        at = back[x][at]
        line[x - 1] = at
    }
    return IntArray(w) { line[it] + lo }
}

/** The half-size [line] stretched to [w] columns of the full size (rows doubled, plus one). */
fun lineAtFullSize(line: IntArray, w: Int): FloatArray {
    val xs = FloatArray(line.size) { it.toFloat() }
    val ys = FloatArray(line.size) { line[it].toFloat() }
    return FloatArray(w) { interp((it + 0.5f) / 2f - 0.5f, xs, ys) * 2f + 1f }
}

/** The sky's colour just above the line in each column (a 7 × 7 average, 10 rows up). */
fun skyAbove(photo: Channels, line: FloatArray): Channels {
    val w = photo.w
    val h = photo.h
    val br = box(photo.r, w, h, 3)
    val bg = box(photo.g, w, h, 3)
    val bb = box(photo.b, w, h, 3)
    val rows = IntArray(w) { (line[it] - 10f).coerceIn(0f, h - 1f).toInt() }
    return Channels(
        w,
        1,
        FloatArray(w) { br[rows[it] * w + it] },
        FloatArray(w) { bg[rows[it] * w + it] },
        FloatArray(w) { bb[rows[it] * w + it] },
    )
}

/** How far each pixel's colour is from [sky] (the colour above the line in its column). */
fun distanceFromSky(photo: Channels, sky: Channels): FloatArray = FloatArray(photo.w * photo.h) {
    val x = it % photo.w
    val dr = photo.r[it] - sky.r[x]
    val dg = photo.g[it] - sky.g[x]
    val db = photo.b[it] - sky.b[x]
    sqrt(dr * dr + dg * dg + db * db)
}

/**
 * The exact edge in each column at full size: the first row near the [line] whose colour is clearly
 * not the sky's, smoothed over 5 columns, then 2 rows more, to drop the bright rim a photo's
 * sharpening leaves along ridges.
 */
fun refineEdge(line: FloatArray, distance: FloatArray, w: Int, h: Int): FloatArray {
    val first = FloatArray(w) { x ->
        val top = (line[x] - 8f).let { kotlin.math.ceil(it).toInt() }.coerceAtLeast(0)
        val bottom = floor(line[x] + 16f).toInt().coerceAtMost(h - 1)
        var hit = -1
        for (y in top..bottom) {
            if (distance[y * w + x] > 0.09f) {
                hit = y
                break
            }
        }
        val row = if (hit >= 0) hit.toFloat() else line[x].coerceIn(0f, h - 1f).toInt().toFloat()
        min(row, line[x] + 16f)
    }
    return FloatArray(w) { x ->
        var sum = 0f
        for (d in -2..2) sum += first[(x + d).coerceIn(0, w - 1)]
        min(sum / 5f, first[x] + 1f) + 2f
    }
}

/**
 * Where bright pixels hang straight under the edge over dark forest, they're the photo's own clouds:
 * the edge moves down past them, at most 40 rows. Only broad patches count (a 25-column running
 * median), so a gap between two trees isn't cut.
 */
fun cloudCut(edge: FloatArray, lum: FloatArray, w: Int, h: Int, columns: IntRange): FloatArray {
    val out = edge.copyOf()
    val from = columns.first.coerceAtLeast(0)
    val to = (columns.last + 1).coerceAtMost(w)
    if (from >= to) return out
    val push = FloatArray(to - from) { i ->
        val x = from + i
        val start = edge[x].toInt()
        val refRows = (min(h - 1, start + 30) until min(h, start + 60)).toList()
        val ref = median(FloatArray(refRows.size) { lum[refRows[it] * w + x] })
        var y = start
        while (y < min(h - 1, start + 40) && lum[y * w + x] > ref + 0.12f) y++
        (y - start).toFloat()
    }
    for (i in push.indices) {
        val window = FloatArray(25) { push[(i + it - 12).coerceIn(0, push.size - 1)] }
        val m = median(window)
        if (m > 0f) out[from + i] += m + 1.5f
    }
    return out
}

/** The edge held at or below [floor] (one row under the line), and set to [exact] where given. */
fun applyFloors(edge: FloatArray, floor: List<Point>, exact: List<Point>): FloatArray {
    val out = edge.copyOf()
    fun line(points: List<Point>, x: Int): Float =
        interp(x.toFloat(), points.map { it.x.toFloat() }.toFloatArray(), points.map { it.y.toFloat() }.toFloatArray()) + 1f
    if (floor.size >= 2) for (x in floor.first().x..floor.last().x) if (x in out.indices) out[x] = max(out[x], line(floor, x))
    if (exact.size >= 2) for (x in exact.first().x + 1..exact.last().x) if (x in out.indices) out[x] = line(exact, x)
    return out
}

/** How much of each pixel is sky: all of it above the edge (one soft row), plus sky-coloured gaps just under it (between tree tips). */
fun skyAlpha(edge: FloatArray, distance: FloatArray, w: Int, h: Int): FloatArray = FloatArray(w * h) {
    val x = it % w
    val y = it / w
    val above = clip(edge[x] - y)
    val gap = if (y >= edge[x] && y < edge[x] + 40f) clip(1f - (distance[it] - 0.03f) / 0.08f) else 0f
    max(above, gap)
}

/**
 * The land's own colour: partly-sky pixels have the old sky taken back out of them, and the sky
 * area is filled with the colour just under the edge, so nothing pale bleeds in when it's scaled.
 */
fun cleanLand(photo: Channels, sky: Channels, alpha: FloatArray, edge: FloatArray): Channels {
    val w = photo.w
    val h = photo.h
    val out = photo.copy()
    for (i in 0 until w * h) {
        val a = alpha[i]
        if (a > 0f && a < 0.98f) {
            val x = i % w
            val k = max(1f - a, 0.02f)
            out.r[i] = clip((photo.r[i] - a * sky.r[x]) / k)
            out.g[i] = clip((photo.g[i] - a * sky.g[x]) / k)
            out.b[i] = clip((photo.b[i] - a * sky.b[x]) / k)
        }
    }
    val fill = IntArray(w) { (edge[it] + 3f).coerceIn(0f, h - 1f).toInt() * w + it }
    val fr = FloatArray(w) { out.r[fill[it]] }
    val fg = FloatArray(w) { out.g[fill[it]] }
    val fb = FloatArray(w) { out.b[fill[it]] }
    for (i in 0 until w * h) {
        if (alpha[i] >= 0.98f) {
            val x = i % w
            out.r[i] = fr[x]
            out.g[i] = fg[x]
            out.b[i] = fb[x]
        }
    }
    return out
}

/** Where the warm low-sun glow lands: the light rock and snow near the top of the land, fading over 320 rows down. */
fun highRock(land: Channels, edge: FloatArray): FloatArray = FloatArray(land.w * land.h) {
    val x = it % land.w
    val y = it / land.w
    if (y <= edge[x] - 2f) return@FloatArray 0f
    val light = 0.3f * toLinear(land.r[it]) + 0.6f * toLinear(land.g[it]) + 0.1f * toLinear(land.b[it])
    clip(1f - (y - edge[x]) / 320f).pow(0.8f) * clip(light * 4f)
}

/**
 * Where the lake shows the sky, mirrored about [eyeRow]: below the mirrored skyline (6 rows early, to
 * cover the old sky's rim) and wherever the mirrored row is sky; softened over 3 × 3 and faded in over
 * 3 rows below [shoreRow].
 */
fun waterAlpha(edge: FloatArray, alpha: FloatArray, w: Int, h: Int, eyeRow: Float, shoreRow: Float): FloatArray {
    val raw = FloatArray(w * h) {
        val x = it % w
        val y = it / w
        val mirrored = clip(y - (2f * eyeRow - edge[x]) + 6f)
        val row = (2f * eyeRow - y).coerceIn(0f, h - 1f).toInt()
        max(mirrored, alpha[row * w + x])
    }
    val soft = box(raw, w, h, 1)
    return FloatArray(w * h) { soft[it] * clip((it / w - shoreRow) / 3f) }
}

/** The water's own shimmer: each pixel's light against its 29 × 29 neighbourhood's, between 0.6 and 1.5 (1 where it's flat). */
fun ripple(land: Channels): FloatArray {
    val lum =
        FloatArray(land.w * land.h) { 0.2126f * toLinear(land.r[it]) + 0.7152f * toLinear(land.g[it]) + 0.0722f * toLinear(land.b[it]) }
    val around = box(lum, land.w, land.h, 14)
    return FloatArray(lum.size) { clip(lum[it] / (around[it] + 1e-4f), 0.6f, 1.5f) }
}

/** What [cut] makes: the land photo (sky area filled) and the layers. */
class CutResult(
    val land: Channels,
    val sky: FloatArray,
    val water: FloatArray,
    val high: FloatArray,
    val ripple: FloatArray,
    val edge: FloatArray,
)

/** The whole cut for [photo] (the 1920 × 1080 copy) with [s]. */
fun cut(photo: Channels, s: CutSettings): CutResult {
    val w = photo.w
    val h = photo.h
    val line = lineAtFullSize(skyline(photo.half(), s.band, s.guides), w)
    val sky = skyAbove(photo, line)
    val distance = distanceFromSky(photo, sky)
    var edge = refineEdge(line, distance, w, h)
    val lum = photo.mixOf(0.3f, 0.59f, 0.11f)
    for (columns in s.cloudColumns) edge = cloudCut(edge, lum, w, h, columns)
    edge = applyFloors(edge, s.floor, s.exact)
    val alpha = skyAlpha(edge, distance, w, h)
    val land = cleanLand(photo, sky, alpha, edge)
    val water = s.eyeRow?.let { waterAlpha(edge, alpha, w, h, it, s.shoreRow) } ?: FloatArray(w * h)
    val shimmer = if (s.eyeRow != null) ripple(land) else FloatArray(w * h) { 1f }
    return CutResult(land, alpha, water, highRock(land, edge), shimmer, edge)
}

/** The ripple as stored: raised to 0.8 (the power the app applies) and scaled so 1.5 is the top. */
val RIPPLE_TOP = 1.5f.pow(0.8f)

/** One layers pixel as 0xAARRGGBB: R sky, G water shows sky, B high rock, A ripple^0.8 ÷ 1.5^0.8. */
fun layerPixel(sky: Float, water: Float, high: Float, ripple: Float): Int {
    fun byte(v: Float) = (clip(v) * 255f).roundToInt()
    return (byte(ripple.pow(0.8f) / RIPPLE_TOP) shl 24) or (byte(sky) shl 16) or (byte(water) shl 8) or byte(high)
}

/**
 * Reasons the cut can't be used (empty if it's fine): land above [CutSettings.landNeverAbove], or a
 * summit outside [CutSettings.summitRows].
 */
fun framingProblems(result: CutResult, s: CutSettings): List<String> {
    val w = result.land.w
    val problems = mutableListOf<String>()
    var tooHigh = 0
    for (y in 0 until s.landNeverAbove.coerceAtMost(result.land.h)) for (x in 0 until w) if (result.sky[y * w + x] < 0.5f) tooHigh++
    if (tooHigh > 0) problems += "$tooHigh land pixels are above row ${s.landNeverAbove}, where the sun and moon travel"
    val summit = result.edge.withIndex().minBy { it.value }
    if (summit.value.toInt() !in s.summitRows) {
        problems +=
            "the highest land is at row ${summit.value.toInt()} (column ${summit.index}), not in rows ${s.summitRows.first}–${s.summitRows.last}: the crop may be off"
    }
    return problems
}
