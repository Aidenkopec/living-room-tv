package tv.livingroom.home.sky

import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import tv.livingroom.home.screensaver.SkyWeather
import tv.livingroom.home.screensaver.mix

/*
 * Mount Rundle behind the Mountains and dock screen: a real photo with the live
 * sky showing through where its own sky was cut away (scripts/fetch-home-photo.sh
 * and launcher/photoprep make its files). The land's light follows the sun's
 * height and the lake mirrors the sky. These are the rules; HomeSkyView paints
 * them. Plain Kotlin, so this runs in JVM tests.
 */

/**
 * The photo's entry in `home-photo.tsv`: its two files, its credit, and the lake's eye level and where
 * the land starts (rows of the 1920 × 1080 files).
 */
data class HomePhoto(
    val land: String,
    val layers: String,
    val place: String,
    val region: String,
    val credit: String,
    val licence: String,
    val page: String,
    val changes: String,
    val eyeRow: Float,
    val landTop: Int,
)

/** `home-photo.tsv` (a header row, then one photo); null if it doesn't read cleanly. Never throws. */
fun parseHomePhoto(tsv: String): HomePhoto? {
    val rows = tsv.lines().filter { it.isNotBlank() }
    if (rows.size != 2 || !rows[0].startsWith("land\t")) return null
    val c = rows[1].split('\t')
    if (c.size != 10) return null
    val eye = c[8].toFloatOrNull() ?: return null
    val top = c[9].toIntOrNull() ?: return null
    if (c[0].isBlank() || c[1].isBlank()) return null
    return HomePhoto(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7], eye, top)
}

/**
 * How the photo's land looks at one moment: overall light, colour, saturation, warm glow on the high
 * rock, moonlight on the lightest rock.
 */
data class LandGrade(
    val gain: Float,
    val tintR: Float,
    val tintG: Float,
    val tintB: Float,
    val saturation: Float,
    val glow: Float,
    val moonlight: Float,
)

/**
 * The land's light by the sun's height, in degrees: full day from 12° up, warm low sun at 5° and 1°,
 * blue dusk at −3°, night by −7° and −12°. Straight lines between, held flat outside.
 */
private val GRADES = listOf(
    12.0 to LandGrade(1.08f, 0.99f, 1.0f, 1.03f, 1.05f, 0f, 0f),
    5.0 to LandGrade(0.86f, 1.14f, 0.98f, 0.86f, 1.0f, 0.38f, 0f),
    1.0 to LandGrade(0.70f, 1.22f, 0.93f, 0.78f, 1.05f, 0.5f, 0f),
    -3.0 to LandGrade(0.36f, 0.95f, 0.85f, 1.10f, 0.8f, 0.08f, 0f),
    -7.0 to LandGrade(0.22f, 0.82f, 0.85f, 1.25f, 0.6f, 0f, 0.5f),
    -12.0 to LandGrade(0.15f, 0.72f, 0.85f, 1.35f, 0.45f, 0f, 1f),
)

/**
 * The land's light with the sun [elevationDeg] up. Under cloud, rain or snow there's no warm glow and
 * the land is a little darker; with the moon down, its light on the rock is weaker.
 */
fun landGrade(elevationDeg: Double, weather: SkyWeather, moonUp: Boolean): LandGrade {
    val e = elevationDeg
    val base = when {
        e >= GRADES.first().first -> GRADES.first().second
        e <= GRADES.last().first -> GRADES.last().second
        else -> {
            val i = GRADES.indices.first { e <= GRADES[it].first && e >= GRADES[it + 1].first }
            val (e0, a) = GRADES[i]
            val (e1, b) = GRADES[i + 1]
            val t = ((e0 - e) / (e0 - e1)).toFloat()
            fun mix(x: Float, y: Float) = x + (y - x) * t
            LandGrade(
                mix(a.gain, b.gain),
                mix(a.tintR, b.tintR),
                mix(a.tintG, b.tintG),
                mix(a.tintB, b.tintB),
                mix(a.saturation, b.saturation),
                mix(a.glow, b.glow),
                mix(a.moonlight, b.moonlight),
            )
        }
    }
    val clouded = weather != SkyWeather.CLEAR
    return base.copy(
        gain = if (clouded) base.gain * CLOUD_GAIN else base.gain,
        glow = if (clouded) 0f else base.glow,
        moonlight = base.moonlight * if (moonUp && !clouded) 1f else MOON_DOWN,
    )
}

/** sRGB codes to linear light and back, by lookup table: `pow` on every pixel is too slow. */
object Light {
    /** Linear light (0..1) for each sRGB code, by a plain 2.2 gamma curve (not the piecewise sRGB one). */
    val toLinear = FloatArray(256) { (it / 255f).pow(2.2f) }

    private const val STEPS = 8192

    /**
     * Back to codes, looked up by the square root of the light: that spreads the night's darkest
     * shades out (they're crowded near 0 in linear light), so every code survives the trip.
     */
    private val toCode = ByteArray(STEPS + 1) { (it.toFloat() / STEPS).pow(2f / 2.2f).times(255f).roundToInt().toByte() }

    /** The sRGB code (0..255) for linear light [v], clamped. */
    fun code(v: Float): Int {
        if (v <= 0f) return 0
        val i = (sqrt(v) * STEPS + 0.5f).toInt()
        return toCode[if (i > STEPS) STEPS else i].toInt() and 0xFF
    }
}

/** The water's ripple as stored in the layers file (A = ripple^0.8 ÷ 1.5^0.8; see photoprep's layerPixel). */
private val RIPPLE_TOP = 1.5f.pow(0.8f)

// Moonlight's colour: sRGB (0.55, 0.62, 0.8).
private val MOON_LIGHT = floatArrayOf(Light.toLinear[140], Light.toLinear[158], Light.toLinear[204])

/**
 * One pixel of the screen: the photo's [land] pixel graded by [grade], with the [layers] pixel's sky
 * ([sky] through it), and its lake showing [mirrorSky] (the sky mirrored about the eye level). All are
 * 0xAARRGGBB; the layers pixel holds R sky, G water shows sky, B high rock, A ripple.
 */
fun composePixel(sky: Int, mirrorSky: Int, land: Int, layers: Int, grade: LandGrade): Int {
    val skyA = (layers shr 16) and 0xFF
    if (skyA == 255) return sky
    val lin = Light.toLinear
    val r = lin[(land shr 16) and 0xFF]
    val g = lin[(land shr 8) and 0xFF]
    val b = lin[land and 0xFF]
    val lum = 0.2126f * r + 0.7152f * g + 0.0722f * b
    val s = grade.saturation
    var or = (lum + (r - lum) * s) * grade.tintR * grade.gain
    var og = (lum + (g - lum) * s) * grade.tintG * grade.gain
    var ob = (lum + (b - lum) * s) * grade.tintB * grade.gain
    val k = grade.glow * ((layers and 0xFF) / 255f)
    if (k > 0f) {
        or *= 1f + k * 1.1f
        og *= 1f + k * 0.05f
        ob *= 1f - k * 0.38f
    } // Alpenglow: × (2.1, 1.05, 0.62).
    if (grade.moonlight > 0f) {
        val bright = 0.3f * r + 0.6f * g + 0.1f * b - 0.25f
        if (bright > 0f) {
            val m = bright.coerceAtMost(1f) * 0.12f * grade.moonlight
            or += MOON_LIGHT[0] * m
            og += MOON_LIGHT[1] * m
            ob += MOON_LIGHT[2] * m
        }
    }
    val waterA = ((layers shr 8) and 0xFF) / 255f
    if (waterA > 0f) {
        val ripple = ((layers ushr 24) and 0xFF) / 255f * RIPPLE_TOP * 0.78f
        or += (lin[(mirrorSky shr 16) and 0xFF] * ripple - or) * waterA
        og += (lin[(mirrorSky shr 8) and 0xFF] * ripple - og) * waterA
        ob += (lin[mirrorSky and 0xFF] * ripple - ob) * waterA
    }
    if (skyA > 0) {
        val a = skyA / 255f
        or += (lin[(sky shr 16) and 0xFF] - or) * a
        og += (lin[(sky shr 8) and 0xFF] - og) * a
        ob += (lin[sky and 0xFF] - ob) * a
    }
    return (0xFF shl 24) or (Light.code(or) shl 16) or (Light.code(og) shl 8) or Light.code(ob)
}

/**
 * The sky row that row [y]'s water mirrors, about [eyeRow] (measured between pixel centres); null if
 * it isn't above [y], since only rows above are still pure sky when the land is composed bottom-up.
 */
fun mirrorRow(y: Int, eyeRow: Float, height: Int): Int? {
    val m = floor(2f * eyeRow - y - 0.5f).toInt().coerceIn(0, height - 1)
    return if (m < y) m else null
}

/**
 * One row of the screen: [sky] (that row of the sky picture) becomes the composed row, in place.
 * [mirror] is the mirrored sky row (null where there's no water).
 */
fun composeRow(sky: IntArray, mirror: IntArray?, land: IntArray, layers: IntArray, grade: LandGrade) {
    for (x in sky.indices) sky[x] = composePixel(sky[x], mirror?.get(x) ?: sky[x], land[x], layers[x], grade)
}

/** A picture's pixel rows (0xAARRGGBB), read and written one at a time. On the TV it's a bitmap. */
interface PixelRows {
    fun read(y: Int, into: IntArray)

    fun write(y: Int, from: IntArray)
}

/**
 * The whole land composed into [sky] ([w] × [h], in place), from the bottom up so the lake can still
 * read pure sky above it. Rows above [firstRow] are only sky and stay as they are. Only one row of
 * each picture is held at a time. Checks [keepGoing] every 32 rows; false if it stopped early.
 */
fun composeLand(
    sky: PixelRows,
    land: PixelRows,
    layers: PixelRows,
    w: Int,
    h: Int,
    eyeRow: Float,
    grade: LandGrade,
    firstRow: Int,
    keepGoing: () -> Boolean = { true },
): Boolean {
    val row = IntArray(w)
    val mirror = IntArray(w)
    val landRow = IntArray(w)
    val layersRow = IntArray(w)
    for (y in h - 1 downTo firstRow.coerceIn(0, h)) {
        if ((h - 1 - y) % 32 == 0 && !keepGoing()) return false
        sky.read(y, row)
        land.read(y, landRow)
        layers.read(y, layersRow)
        val m = mirrorRow(y, eyeRow, h)
        if (m != null) sky.read(m, mirror)
        composeRow(row, if (m != null) mirror else null, landRow, layersRow, grade)
        sky.write(y, row)
    }
    return true
}

private const val CLOUD_GAIN = 0.9f
private const val MOON_DOWN = 0.4f
