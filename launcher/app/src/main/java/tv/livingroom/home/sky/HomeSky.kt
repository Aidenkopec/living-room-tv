package tv.livingroom.home.sky

import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt
import tv.livingroom.home.HomePlace
import tv.livingroom.home.screensaver.SkyLook
import tv.livingroom.home.screensaver.SkyWeather
import tv.livingroom.home.screensaver.mix
import tv.livingroom.home.screensaver.skyLook
import tv.livingroom.home.screensaver.skyWeather

/*
 * Living sky on the home screen: Calgary's sky right now, in the
 * screensaver's colours, with the sun and moon on an arc kept to the right of
 * the words. These are the rules; HomeSkyView paints them. Colours are ARGB ints
 * and positions dp in the 960 x 380 dp sky (plain Kotlin, so this runs in JVM tests).
 */

/** Sizes and places shared by the painter ([paintHomeSky]) and [backdropAt], in dp (960 dp across the 1920 px screen). */
object HomeSkyLayout {
    const val WIDTH = 960f
    const val HEIGHT = 380f // The top 760 px of the 1080 px screen.
    const val ARC_LEFT = 600f // Right of the words, which end at 583 dp.
    const val ARC_RIGHT = 900f
    const val HORIZON = 296f
    const val ARC_TOP = 110f
    const val SUN_RADIUS = 17f
    const val SUN_GLOW_RADIUS = 80f
    const val SUN_GLOW_ALPHA = 0.55f
    const val MOON_RADIUS = 13f
    const val MOON = 0xFFEEE9DC.toInt()

    /** The sky's gradient stops (top, middle, horizon), as the screensaver's. */
    const val MIDDLE_AT = 0.45f
    const val HORIZON_AT = 0.78f

    /** The horizon glow: centred at the glow's x and 72% down, 0.6 of the width across, squashed to 0.66 high. */
    const val GLOW_Y = 0.72f
    const val GLOW_RADIUS = 0.6f
    const val GLOW_SQUASH = 0.66f

    /**
     * The shade behind the words: black at [WORDS_SHADE_ALPHA] up to [WORDS_SHADE_FULL_TO] of the width, gone by
     * [WORDS_SHADE_END]. Set by the contrast test, whose worst sky is June's just after sunrise, with the
     * gold glow beside the words.
     */
    const val WORDS_SHADE_ALPHA = 0.6f
    const val WORDS_SHADE_FULL_TO = 0.55f
    const val WORDS_SHADE_END = 0.85f

    /**
     * A soft shade behind the corner's words: black at [CORNER_SHADE_ALPHA] at the top right, gone
     * [CORNER_SHADE_DOWN] dp down and [CORNER_SHADE_IN] dp in.
     */
    const val CORNER_SHADE_ALPHA = 0.35f
    const val CORNER_SHADE_DOWN = 120f
    const val CORNER_SHADE_IN = 420f

    /** The sky fades into the background from [FADE_TOP] down to its bottom ([FADE_MIDDLE_ALPHA] at 60% of the way). */
    const val FADE_TOP = 280f
    const val FADE_MIDDLE_ALPHA = 0.55f

    /** The screen's background, under and below the sky. */
    const val BACKGROUND = 0xFF05070A.toInt()

    /** Clouds: a soft white at their middle, fading out. */
    const val CLOUD_ALPHA = 0.22f

    /** Living sky's arc, kept to the right of the words; with the sun down, the glow sits toward the morning or evening side. */
    val ARC = SkyArc(ARC_LEFT, ARC_RIGHT, HORIZON, ARC_TOP, morningGlow = 0.52f, eveningGlow = 0.83f)
}

/**
 * Where the sun and moon travel, in dp: from [left] to [right], rising from
 * [horizon] to [top]. With the sun down, the horizon glow sits [morningGlow]
 * (before noon) or [eveningGlow] of the way across.
 */
data class SkyArc(val left: Float, val right: Float, val horizon: Float, val top: Float, val morningGlow: Float, val eveningGlow: Float)

/**
 * Mountains and dock: the whole 960 x 540 dp screen (1920 x 1080 px) is sky, the
 * ridges in the screensaver's places, and the sun and moon cross the whole width.
 * Shades sit behind the clock and the dock only.
 */
object DockSkyLayout {
    const val WIDTH = 960f
    const val HEIGHT = 540f

    /**
     * The arc spans 200 to 1720 px, rising from the horizon at 650 px only to 150 dp (300 px): a higher moon
     * at the left would sit behind the clock (the contrast test). The glow sits at the screensaver's sides when
     * the sun is down.
     */
    val ARC = SkyArc(left = 100f, right = 860f, horizon = 325f, top = 150f, morningGlow = 0.27f, eveningGlow = 0.78f)

    /**
     * The shade behind the clock and its line: black at [CLOCK_SHADE_ALPHA] over them, fading out from
     * [CLOCK_SHADE_FULL_ACROSS] to [CLOCK_SHADE_END_ACROSS] dp across and from [CLOCK_SHADE_FULL_DOWN] to
     * [CLOCK_SHADE_END_DOWN] dp down. Set by the contrast test: a round shade in the corner left the end of
     * a long line unreadable on a snowy midday sky, by the sun.
     */
    const val CLOCK_SHADE_ALPHA = 0.48f
    const val CLOCK_SHADE_FULL_ACROSS = 560f
    const val CLOCK_SHADE_END_ACROSS = 760f
    const val CLOCK_SHADE_FULL_DOWN = 130f
    const val CLOCK_SHADE_END_DOWN = 220f

    /** The darkening behind the dock: none above [BOTTOM_SHADE_TOP] dp, [BOTTOM_SHADE_ALPHA] at the bottom. */
    const val BOTTOM_SHADE_TOP = 410f
    const val BOTTOM_SHADE_ALPHA = 0.5f

    /**
     * The dock's bar behind the tiles, and the fill of a square tile (Explore's lightest). The bar's alpha
     * is set by HomePhotoTest's contrast test: over the photo's lake, a low sun's reflection sits right
     * behind the dock.
     */
    const val BAR = 0xFF0C0B18.toInt()
    const val BAR_ALPHA = 0.6f
    const val SQUARE_TILE_FILL_ALPHA = 0.16f
}

/** The line under the dock screen's clock: the screensaver's 85% white was too faint on the brightest skies, so a little more. */
const val DOCK_LINE_ALPHA = 0.82f

/** A cloud: its middle (fractions of the width and height) and its radius (of the width); drawn 0.35 as high as wide. */
data class Cloud(val x: Float, val y: Float, val radius: Float)

/** The screensaver's three clouds, also used on the home screen. */
val CLOUDS = listOf(Cloud(0.22f, 0.22f, 0.18f), Cloud(0.55f, 0.20f, 0.24f), Cloud(0.84f, 0.30f, 0.14f))
const val CLOUD_SQUASH = 0.35f

/** How dark the bright daytime sky gets so the words stay readable: up to 22%. */
const val DAY_DIM_MAX = 0.22f

/** The sun's height at which the veil is full: 6°, as the gold sky just after sunrise is already bright. */
const val DAY_DIM_FULL_AT = 6.0

/** The corner's dimmest words (weather, date, the sky line): 80% white, for contrast. */
const val CORNER_TEXT_ALPHA = 0.8f

/** The row heading ("CONTINUE WATCHING"). */
const val HEADING_TEXT_ALPHA = 0.66f

/** A spot in the sky, in dp. */
data class SkyPoint(val x: Float, val y: Float)

/**
 * The sun on the arc at [at], or null when it's more than 2.5° below the
 * horizon (or never rises that day). Its x follows the time from 30 minutes
 * before sunrise to 30 minutes after sunset; its y, its height over the day's
 * highest, so at noon it's at the arc's top in every season.
 */
fun sunOnScreen(
    at: ZonedDateTime,
    lat: Double = HomePlace.LATITUDE,
    lon: Double = HomePlace.LONGITUDE,
    arc: SkyArc = HomeSkyLayout.ARC,
): SkyPoint? {
    val elevation = sunElevation(at.toInstant(), lat, lon)
    if (elevation < SUN_HIDDEN_BELOW) return null
    val sun = sunTimes(at.toLocalDate(), at.zone, lat, lon)
    val highest = sunElevation(solarNoon(sun).toInstant(), lat, lon)
    if (highest <= 0) return null
    val start = sun.sunrise.minusMinutes(30)
    val length = Duration.between(start, sun.sunset.plusMinutes(30)).seconds.toDouble()
    val through = (Duration.between(start, at).seconds / length).coerceIn(0.0, 1.0).toFloat()
    val up = (elevation / highest).coerceIn(-0.1, 1.0).toFloat()
    return SkyPoint(x = arc.left + through * (arc.right - arc.left), y = arc.horizon - up * (arc.horizon - arc.top))
}

/**
 * The moon on the arc, or null when it's below the horizon or the sun is 2°
 * or more up. Its x follows its hour angle from −90° to
 * +90°; its y, its height over 30°, both held within the arc.
 */
fun moonOnScreen(moon: MoonPlace, sunElevationDeg: Double, arc: SkyArc = HomeSkyLayout.ARC): SkyPoint? {
    if (moon.elevationDeg <= 0 || sunElevationDeg >= MOON_ONLY_BELOW_SUN) return null
    val across = ((moon.hourAngleDeg + 90) / 180).coerceIn(0.0, 1.0).toFloat()
    val up = (moon.elevationDeg / 30).coerceIn(0.0, 1.0).toFloat()
    return SkyPoint(x = arc.left + across * (arc.right - arc.left), y = arc.horizon - up * (arc.horizon - arc.top))
}

/** The screensaver's sky ([skyLook]), greyed by the [weather]: no stars and a fainter glow under cloud, rain or snow. */
fun homeSkyLook(elevationDeg: Double, weather: SkyWeather): SkyLook {
    val clear = skyLook(elevationDeg)
    val (grey, amount) = when (weather) {
        SkyWeather.CLEAR -> return clear
        SkyWeather.CLOUDY -> 0xFF606676.toInt() to 0.32f
        SkyWeather.RAIN -> 0xFF3A4050.toInt() to 0.42f
        SkyWeather.SNOW -> 0xFF8C92A2.toInt() to 0.26f
    }
    return clear.copy(
        top = mix(clear.top, grey, amount),
        middle = mix(clear.middle, grey, amount),
        horizon = mix(clear.horizon, grey, amount),
        glowAlpha = clear.glowAlpha * 0.45f,
        stars = 0f,
    )
}

/** How much the sun and moon show: fully when clear, dimmed through snow, not at all under cloud or rain. */
fun bodyAlpha(weather: SkyWeather): Float = when (weather) {
    SkyWeather.CLEAR -> 1f
    SkyWeather.SNOW -> 0.6f
    SkyWeather.CLOUDY, SkyWeather.RAIN -> 0f
}

/** The dark veil over the daytime sky: none with the sun on the horizon, [DAY_DIM_MAX] with it [DAY_DIM_FULL_AT] or higher. */
fun dayDim(elevationDeg: Double): Float = (elevationDeg / DAY_DIM_FULL_AT).coerceIn(0.0, 1.0).toFloat() * DAY_DIM_MAX

/** The sun's colour: pale near midday, warmer as it gets low. */
fun sunColour(elevationDeg: Double): Int = mix(0xFFFFF0C8.toInt(), 0xFFFFA868.toInt(), (1 - elevationDeg / 12).coerceIn(0.0, 1.0).toFloat())

/**
 * Everything the home sky shows at one minute: equal all minute, so it's
 * repainted only when something changes. [glowX] is where the horizon glow sits
 * (the sun's x, or toward the morning or evening side when the sun is down).
 */
data class HomeSkyScene(
    val look: SkyLook,
    val weather: SkyWeather,
    val dim: Float,
    val sunElevationDeg: Double,
    val sun: SkyPoint?,
    val moon: SkyPoint?,
    val phase: MoonPhase,
    val bodyAlpha: Float,
    val glowX: Float,
)

/** The home sky at [at] (to the minute), with the weather [weatherCode] (null or unknown: clear), the sun and moon on [arc]. */
fun homeSkyScene(
    at: ZonedDateTime,
    weatherCode: Int?,
    lat: Double = HomePlace.LATITUDE,
    lon: Double = HomePlace.LONGITUDE,
    arc: SkyArc = HomeSkyLayout.ARC,
): HomeSkyScene {
    val minute = at.truncatedTo(ChronoUnit.MINUTES)
    val instant = minute.toInstant()
    val elevation = sunElevation(instant, lat, lon)
    val weather = skyWeather(weatherCode)
    val sun = sunOnScreen(minute, lat, lon, arc)
    val beforeNoon = minute.isBefore(solarNoon(sunTimes(minute.toLocalDate(), minute.zone, lat, lon)))
    return HomeSkyScene(
        look = homeSkyLook(elevation, weather),
        weather = weather,
        dim = dayDim(elevation),
        sunElevationDeg = elevation,
        sun = sun,
        moon = moonOnScreen(moonPlace(instant, lat, lon), elevation, arc),
        phase = moonPhase(instant),
        bodyAlpha = bodyAlpha(weather),
        glowX = sun?.x?.div(HomeSkyLayout.WIDTH) ?: if (beforeNoon) arc.morningGlow else arc.eveningGlow,
    )
}

/**
 * The colour [paintHomeSky] puts at ([x], [y]) dp, from its layers: the sky's
 * gradient and glow, the day veil, the moon, the sun and its glow, clouds at
 * their brightest, the shades behind the words and the corner, and the fade
 * into the background.
 * Left out, as they only make it darker or are too small to matter for reading:
 * the ridges, stars, rain and snowflakes.
 */
fun backdropAt(scene: HomeSkyScene, x: Float, y: Float): Int {
    val w = HomeSkyLayout.WIDTH
    val h = HomeSkyLayout.HEIGHT
    if (y >= h) return HomeSkyLayout.BACKGROUND
    var c = skyAt(scene, x, y, h)
    c = over(c, BLACK, wordsShade(x / w))
    c = over(c, BLACK, cornerShade(x, y))
    if (y > HomeSkyLayout.FADE_TOP) {
        val t = (y - HomeSkyLayout.FADE_TOP) / (h - HomeSkyLayout.FADE_TOP)
        val middle = HomeSkyLayout.FADE_MIDDLE_ALPHA
        val fade = if (t < 0.6f) middle * t / 0.6f else middle + (1 - middle) * (t - 0.6f) / 0.4f
        c = over(c, HomeSkyLayout.BACKGROUND, fade)
    }
    return c
}

/**
 * The colour paintDockSky puts at ([x], [y]) dp on the dock screen, as
 * [backdropAt] does for Living sky: the sky's layers, then the shades behind
 * the clock and the dock. Left out, as before: the ridges or the photo (the
 * land never reaches above the sun's arc, so not behind the clock), stars,
 * rain and snow. The photo under the dock is checked with [dockTileOver] in
 * HomePhotoTest, from the real files.
 */
fun dockBackdropAt(scene: HomeSkyScene, x: Float, y: Float): Int = dockShadesOver(dockSkyAt(scene, x, y), x, y)

/** The dock screen's sky alone at ([x], [y]) dp (what the photo's lake mirrors). */
fun dockSkyAt(scene: HomeSkyScene, x: Float, y: Float): Int = skyAt(scene, x, y, DockSkyLayout.HEIGHT)

/** [colour] at ([x], [y]) dp with the shades behind the clock and the dock over it. */
fun dockShadesOver(colour: Int, x: Float, y: Float): Int = over(over(colour, BLACK, clockShade(x, y)), BLACK, bottomShade(y))

/** The colour behind a selected square tile's words: the dock's bar over [dockBackdropAt], then the tile's own light fill. */
fun dockTileAt(scene: HomeSkyScene, x: Float, y: Float): Int = dockTileOver(dockBackdropAt(scene, x, y))

/** A selected square tile's colour over [backdrop] (with the screen's shades already in it): the dock's bar, then the tile's light fill. */
fun dockTileOver(backdrop: Int): Int =
    over(over(backdrop, DockSkyLayout.BAR, DockSkyLayout.BAR_ALPHA), WHITE, DockSkyLayout.SQUARE_TILE_FILL_ALPHA)

/** The shade behind the dock screen's clock and its line at ([x], [y]) dp: full over them, fading out to the right and below. */
fun clockShade(x: Float, y: Float): Float {
    fun fading(at: Float, full: Float, end: Float) = 1 - ((at - full) / (end - full)).coerceIn(0f, 1f)
    val across = fading(x, DockSkyLayout.CLOCK_SHADE_FULL_ACROSS, DockSkyLayout.CLOCK_SHADE_END_ACROSS)
    val down = fading(y, DockSkyLayout.CLOCK_SHADE_FULL_DOWN, DockSkyLayout.CLOCK_SHADE_END_DOWN)
    return DockSkyLayout.CLOCK_SHADE_ALPHA * across * down
}

/** The darkening behind the dock at [y] dp. */
fun bottomShade(y: Float): Float = DockSkyLayout.BOTTOM_SHADE_ALPHA *
    ((y - DockSkyLayout.BOTTOM_SHADE_TOP) / (DockSkyLayout.HEIGHT - DockSkyLayout.BOTTOM_SHADE_TOP)).coerceIn(0f, 1f)

/** The sky's own layers at ([x], [y]) dp in a sky [h] dp tall: gradient and glow, day veil, moon, sun and its glow, clouds. */
private fun skyAt(scene: HomeSkyScene, x: Float, y: Float, h: Float): Int {
    val w = HomeSkyLayout.WIDTH
    val look = scene.look
    val f = y / h
    var c = when {
        f <= HomeSkyLayout.MIDDLE_AT -> mix(look.top, look.middle, f / HomeSkyLayout.MIDDLE_AT)
        f <= HomeSkyLayout.HORIZON_AT -> mix(
            look.middle,
            look.horizon,
            (f - HomeSkyLayout.MIDDLE_AT) / (HomeSkyLayout.HORIZON_AT - HomeSkyLayout.MIDDLE_AT),
        )
        else -> look.horizon
    }
    if (look.glowAlpha > 0f) {
        val rx = w * HomeSkyLayout.GLOW_RADIUS
        val d = distance(x - scene.glowX * w, (y - h * HomeSkyLayout.GLOW_Y) / HomeSkyLayout.GLOW_SQUASH) / rx
        c = over(c, look.glow, look.glowAlpha * (1 - d).coerceAtLeast(0f))
    }
    c = over(c, BLACK, scene.dim)
    if (scene.bodyAlpha > 0f) {
        scene.moon?.let { m ->
            if (distance(x - m.x, y - m.y) <= HomeSkyLayout.MOON_RADIUS) c = over(c, HomeSkyLayout.MOON, scene.bodyAlpha)
        }
        scene.sun?.let { s ->
            val colour = sunColour(scene.sunElevationDeg)
            val d = distance(x - s.x, y - s.y)
            c = over(c, colour, HomeSkyLayout.SUN_GLOW_ALPHA * scene.bodyAlpha * (1 - d / HomeSkyLayout.SUN_GLOW_RADIUS).coerceAtLeast(0f))
            if (d <= HomeSkyLayout.SUN_RADIUS) c = over(c, colour, scene.bodyAlpha)
        }
    }
    if (scene.weather != SkyWeather.CLEAR) {
        for (cloud in CLOUDS) {
            val r = w * cloud.radius
            val d = distance(x - w * cloud.x, (y - h * cloud.y) / CLOUD_SQUASH) / r
            c = over(c, WHITE, HomeSkyLayout.CLOUD_ALPHA * (1 - d).coerceAtLeast(0f))
        }
    }
    return c
}

/** The shade behind the words at [x] (a fraction of the width). */
fun wordsShade(x: Float): Float {
    val fading = (x - HomeSkyLayout.WORDS_SHADE_FULL_TO) / (HomeSkyLayout.WORDS_SHADE_END - HomeSkyLayout.WORDS_SHADE_FULL_TO)
    return HomeSkyLayout.WORDS_SHADE_ALPHA * (1 - fading).coerceIn(0f, 1f)
}

/** The shade behind the corner's words at ([x], [y]) dp: strongest at the top right. */
fun cornerShade(x: Float, y: Float): Float {
    val across = ((x - (HomeSkyLayout.WIDTH - HomeSkyLayout.CORNER_SHADE_IN)) / HomeSkyLayout.CORNER_SHADE_IN).coerceIn(0f, 1f)
    val down = (1 - y / HomeSkyLayout.CORNER_SHADE_DOWN).coerceIn(0f, 1f)
    return HomeSkyLayout.CORNER_SHADE_ALPHA * minOf(1f, across * 2f) * minOf(1f, down * 1.6f)
}

/** WCAG contrast of [text] drawn at [textAlpha] over the opaque [backdrop]: 1 (none) to 21 (black on white). */
fun contrast(text: Int, textAlpha: Float, backdrop: Int): Double {
    val a = luminance(over(backdrop, text, textAlpha))
    val b = luminance(backdrop)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

/**
 * The corner's small second line at [at]: "Sunrise 8:09 AM" before sunrise,
 * "Sunset 6:33 PM" before sunset, then "Moon 74% full" (or "Full moon") while
 * the moon is up, else tomorrow's sunrise. Null where the sun doesn't rise or set that day.
 */
fun cornerSkyLine(at: ZonedDateTime, is24Hour: Boolean, lat: Double = HomePlace.LATITUDE, lon: Double = HomePlace.LONGITUDE): String? {
    val sun = sunTimes(at.toLocalDate(), at.zone, lat, lon)
    val noon = solarNoon(sun)
    val risesAndSets =
        sunElevation(noon.toInstant(), lat, lon) > HORIZON_DEG && sunElevation(noon.minusHours(12).toInstant(), lat, lon) < HORIZON_DEG
    if (!risesAndSets) return null
    val clock = if (is24Hour) CLOCK_24 else CLOCK_12
    return when {
        at.isBefore(sun.sunrise) -> "Sunrise ${sun.sunrise.format(clock)}"
        at.isBefore(sun.sunset) -> "Sunset ${sun.sunset.format(clock)}"
        else -> {
            val phase = moonPhase(at.toInstant())
            val moonUp = moonPlace(at.toInstant(), lat, lon).elevationDeg > 0
            when {
                moonUp && phase.lit >= FULL -> "Full moon"
                moonUp && phase.lit >= THIN -> "Moon ${(phase.lit * 100).roundToInt()}% full"
                else -> "Sunrise ${sunTimes(at.toLocalDate().plusDays(1), at.zone, lat, lon).sunrise.format(clock)}"
            }
        }
    }
}

private const val SUN_HIDDEN_BELOW = -2.5
private const val MOON_ONLY_BELOW_SUN = 2.0
private const val HORIZON_DEG = -0.833 // Sunrise and sunset: the sun's top edge on the horizon.
private const val FULL = 0.97
private const val THIN = 0.03 // A moon thinner than this isn't worth a line.
private val WHITE = 0xFFFFFFFF.toInt()
private val BLACK = 0xFF000000.toInt()
private val CLOCK_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val CLOCK_24 = DateTimeFormatter.ofPattern("H:mm", Locale.US)

private fun distance(dx: Float, dy: Float) = sqrt(dx * dx + dy * dy)

/** [layer] at [alpha] over the opaque [base]. */
private fun over(base: Int, layer: Int, alpha: Float): Int = if (alpha <= 0f) base else mix(base, layer, alpha.coerceAtMost(1f))

/** WCAG relative luminance of an opaque colour. */
private fun luminance(c: Int): Double {
    fun channel(shift: Int): Double {
        val v = ((c shr shift) and 0xFF) / 255.0
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}
