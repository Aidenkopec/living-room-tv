package tv.livingroom.home.christmas

import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import tv.livingroom.home.HomePlace
import tv.livingroom.home.screensaver.SkyWeather
import tv.livingroom.home.screensaver.isLateNight
import tv.livingroom.home.sky.HomeSkyScene
import tv.livingroom.home.sky.SkyArc
import tv.livingroom.home.sky.homeSkyScene

/*
 * Fireside, the Christmas screensaver: inside the cabin, by the fire. Its light
 * is the village's at the same minute (the same four looks, from Calgary's
 * sun), seen from indoors: the window shows that sky and weather, and the room
 * takes the look's indoor tint and fire. Plain Kotlin, so it runs in JVM tests.
 */

/**
 * Everything Fireside's still picture shows at one minute: equal all minute, so it's painted once.
 * [sky] is the sky in the window (the sun and moon on [WINDOW_ARC]); [late] is the late night,
 * when the screensaver dims, the fire burns down to embers and the tree's lights breathe slowly.
 */
data class FiresideScene(val look: VillageLook, val sky: HomeSkyScene, val late: Boolean) {
    val weather: SkyWeather get() = sky.weather

    /** How strongly the fire burns: the look's own fire, or embers late at night. */
    val fire: Float get() = if (late) EMBERS else look.fire
}

/** The fire late at night: a glow and a few low tongues. */
const val EMBERS = 0.16f

/**
 * The sun and moon's path for the window, in the sky painter's 960 x 540 dp. The window shows dp 516 to 895
 * across and 8 to 411 down of that sky, so the path runs inside it and sets behind the window's mountains.
 */
val WINDOW_ARC = SkyArc(left = 560f, right = 860f, horizon = 330f, top = 120f, morningGlow = 0.62f, eveningGlow = 0.84f)

/**
 * What Fireside's still picture depends on, rounded to what can be seen: two scenes with the same key would paint
 * the same picture to the eye. The sun and moon move about half a dp a minute along the window's path and the moon's
 * shape changes by a hair, so without this the room would be painted afresh every minute all night for nothing.
 */
data class FiresidePaintKey(
    val look: VillageLook,
    val weather: SkyWeather,
    val late: Boolean,
    val sun: Pair<Int, Int>?,
    val sunColour: Int,
    val moon: Pair<Int, Int>?,
    val moonLit: Int,
    val waxing: Boolean,
    val glow: Int,
    val bodies: Int,
)

/** [scene]'s [FiresidePaintKey]: places to [BODY_STEP] dp, the sun's colour to a degree of its height, the moon's shape to 2%. */
fun paintKey(scene: FiresideScene): FiresidePaintKey {
    val sky = scene.sky
    fun step(v: Float) = (v / BODY_STEP).roundToInt()
    return FiresidePaintKey(
        look = scene.look,
        weather = scene.weather,
        late = scene.late,
        sun = sky.sun?.let { step(it.x) to step(it.y) },
        sunColour = sky.sunElevationDeg.roundToInt(),
        moon = sky.moon?.let { step(it.x) to step(it.y) },
        moonLit = (sky.phase.lit * 50).roundToInt(),
        waxing = sky.phase.waxing,
        glow = (sky.glowX * 200).roundToInt(),
        bodies = (sky.bodyAlpha * 20).roundToInt(),
    )
}

/** How far, in the sky painter's dp, the sun or moon moves before the room is painted again (about 2.5 px in the window). */
const val BODY_STEP = 2f

/**
 * Fireside at [at] (to the minute) with the weather [weatherCode] (null or unknown: clear). Late night is
 * the screensaver's own (11 PM to 5 AM on [at]'s clock), and only when [lateNightOn].
 */
fun firesideScene(at: ZonedDateTime, weatherCode: Int?, lateNightOn: Boolean = true): FiresideScene {
    val minute = at.truncatedTo(ChronoUnit.MINUTES)
    val look = villageScene(minute, weatherCode).look
    val sky = homeSkyScene(minute, weatherCode, HomePlace.LATITUDE, HomePlace.LONGITUDE, arc = WINDOW_ARC)
    return FiresideScene(look, sky.copy(look = villageSky(look, sky.weather), dim = 0f), lateNightOn && isLateNight(minute.toLocalTime()))
}
