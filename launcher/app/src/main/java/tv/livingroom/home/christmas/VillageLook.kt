package tv.livingroom.home.christmas

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import tv.livingroom.home.HomePlace
import tv.livingroom.home.screensaver.SkyLook
import tv.livingroom.home.screensaver.SkyWeather
import tv.livingroom.home.screensaver.mix
import tv.livingroom.home.sky.DockSkyLayout
import tv.livingroom.home.sky.HomeSkyScene
import tv.livingroom.home.sky.SkyShape
import tv.livingroom.home.sky.homeSkyScene
import tv.livingroom.home.sky.sunTimes
import tv.livingroom.home.sky.weatherSky

/*
 * The Christmas village's light. Four looks (a snowy morning, a golden
 * afternoon, a twinkling evening, a quiet midnight) are the key frames; the
 * look at any minute is a blend of them, from the sun's height, how far
 * through the daylight it is, and how late in the evening. Colours are
 * 0xRRGGBB ints. Plain Kotlin, so it runs in JVM tests.
 */

/**
 * How the village is lit: the sky's three colours and its horizon [glow], [stars] and [aurora]
 * (0 to 1), the light's tint [amb] and how strongly it colours things ([ambK]), the snow on the
 * ground ([snow], [snowLow] in the dips), the far mountains ([far], [farSnow]), and how bright
 * the windows, lamps and fairy lights are ([lights], 0 to 1). [quiet] is how far into the quiet
 * night it is (0 to 1): fires burn low and nobody is out. Inside Fireside's room, things are tinted
 * [roomK] of the way to [room], the fire burns [fire] strong (1 is a roaring evening fire), and
 * [dark] (0 by day, 1 at night) says how much the room is lit by the fire rather than the window.
 */
data class VillageLook(
    val skyTop: Int,
    val skyMid: Int,
    val skyHorizon: Int,
    val glow: Int,
    val glowAlpha: Float,
    val stars: Float,
    val amb: Int,
    val ambK: Float,
    val snow: Int,
    val snowLow: Int,
    val far: Int,
    val farSnow: Int,
    val lights: Float,
    val aurora: Float,
    val quiet: Float,
    val room: Int = 0,
    val roomK: Float = 0f,
    val fire: Float = 0f,
    val dark: Float = 0f,
) {
    /** [rgb] as it looks in this light. */
    fun lit(rgb: Int): Int = mix(rgb, amb, ambK)

    /** [rgb] as it looks inside Fireside's room at this hour. */
    fun roomLit(rgb: Int): Int = mix(rgb, room, roomK)

    /** Snow lying on roofs, branches and sills: a touch brighter than the ground. */
    val lyingSnow: Int get() = mix(snow, WHITE, 0.22f)
}

val MORNING = VillageLook(
    skyTop = 0x4F76B4, skyMid = 0xA3BCE0, skyHorizon = 0xF7CFB6, glow = 0xFFC39A, glowAlpha = 0.6f, stars = 0f,
    amb = 0xB9C8EA, ambK = 0.14f, snow = 0xF6F9FF, snowLow = 0xC2D0EE, far = 0x8296C2, farSnow = 0xFFE6DC,
    lights = 0.45f, aurora = 0f, quiet = 0f,
    room = 0xDFE6F5, roomK = 0.20f, fire = 0.4f, dark = 0f,
).opaque()
val AFTERNOON = VillageLook(
    skyTop = 0x4A80C4, skyMid = 0xA6C8E6, skyHorizon = 0xFFE3B3, glow = 0xFFC56A, glowAlpha = 0.7f, stars = 0f,
    amb = 0xFFE3BD, ambK = 0.10f, snow = 0xFFFAF1, snowLow = 0xB6C8EA, far = 0x8598C4, farSnow = 0xFFF1D2,
    lights = 0.25f, aurora = 0f, quiet = 0f,
    room = 0xFFE2B8, roomK = 0.18f, fire = 0.5f, dark = 0f,
).opaque()
val EVENING = VillageLook(
    skyTop = 0x0A1134, skyMid = 0x222B66, skyHorizon = 0x5D4788, glow = 0xB86A9C, glowAlpha = 0.3f, stars = 0.8f,
    amb = 0x1A2560, ambK = 0.58f, snow = 0x93A5E0, snowLow = 0x4A5A9F, far = 0x2A356F, farSnow = 0x7083C8,
    lights = 1f, aurora = 0f, quiet = 0f,
    room = 0x2A1410, roomK = 0.42f, fire = 1f, dark = 1f,
).opaque()
val MIDNIGHT = VillageLook(
    skyTop = 0x030614, skyMid = 0x0A1232, skyHorizon = 0x1B2858, glow = 0x2C3D80, glowAlpha = 0.25f, stars = 1f,
    amb = 0x0C133E, ambK = 0.72f, snow = 0x6577BA, snowLow = 0x303C7C, far = 0x151D4C, farSnow = 0x4558A6,
    lights = 0.5f, aurora = 1f, quiet = 1f,
    room = 0x0B0916, roomK = 0.66f, fire = 0.28f, dark = 1f,
).opaque()

/** Where the village sky's colours and glow sit: the horizon colour is reached 60% down, behind the mountains, with the glow on it. */
val VILLAGE_SKY = SkyShape(middleAt = 0.348f, horizonAt = 0.6f, glowY = 0.6f, glowRadius = 0.5f, glowSquash = 0.5f)

/** The sun's height (degrees) at and above which it's fully day, and at and below which it's fully night. */
const val DAY_FROM_DEG = -1.0
const val NIGHT_BELOW_DEG = -7.0

/**
 * The look with the sun at [sunElevationDeg]. By day it's the morning look turning into the
 * afternoon's across the middle of the daylight ([dayThrough], 0 at sunrise to 1 at sunset); by
 * night the evening look, or midnight's when it's late ([lateNight], 0 to 1). Dusk and dawn
 * blend the two while the sun is between [DAY_FROM_DEG] and [NIGHT_BELOW_DEG].
 */
fun villageLook(sunElevationDeg: Double, dayThrough: Float, lateNight: Float): VillageLook {
    val day = blend(MORNING, AFTERNOON, smooth((dayThrough - 0.35f) / 0.3f))
    val night = blend(EVENING, MIDNIGHT, lateNight.coerceIn(0f, 1f))
    return blend(night, day, smooth(((sunElevationDeg - NIGHT_BELOW_DEG) / (DAY_FROM_DEG - NIGHT_BELOW_DEG)).toFloat()))
}

/**
 * How late it is at [time], 0 to 1: the evening turns into the quiet night between 9:30 and
 * 10:30 PM and stays that way until the sun comes up (the day's own look takes over then).
 */
fun lateNight(time: LocalTime): Float {
    val minutes = time.hour * 60 + time.minute
    return when {
        minutes < 12 * 60 -> 1f
        else -> smooth((minutes - LATE_FROM_MIN) / LATE_BLEND_MIN)
    }
}

/**
 * Everything the village picture shows at one minute: equal all minute, so it's painted once.
 * [sky] is the home screen's own sky for that minute (sun, moon, phase, weather) in the village's colours.
 */
data class VillageScene(
    val look: VillageLook,
    val sky: HomeSkyScene,
    val hour: Int,
    val minute: Int,
    /** Which of the village's hours it is, by the same place's clock as the light: who is out depends on it. */
    val hours: VillageHours,
    /** How far through Advent it is ([adventDay]): how many windows are lit and how deep the roofs' snow is. */
    val advent: Int = ADVENT_DAYS,
)

/** Advent's days: from December 1 to 24 one more window is lit each day. */
const val ADVENT_DAYS = 24

/** The day of Advent on [date], 1 to 24 from December 1 to 24; on any other date the whole of it, 24 (every window lit). */
fun adventDay(date: LocalDate): Int = if (date.monthValue == DECEMBER && date.dayOfMonth <= ADVENT_DAYS) date.dayOfMonth else ADVENT_DAYS

private const val DECEMBER = 12

/**
 * The village at [at] (to the minute), with the weather [weatherCode] (null or unknown: clear).
 * Its light keeps the hours of the place whose sun it shows ([zone], with [lat] and [lon]): the
 * day's sunrise and sunset and "late in the evening" are read from that place's own date and
 * clock, so a screen set to another time zone can't pair one day's sun with another day's times.
 * The church clock ([VillageScene.hour], [VillageScene.minute]) and the day of Advent show [at]'s own time and date.
 */
fun villageScene(
    at: ZonedDateTime,
    weatherCode: Int?,
    lat: Double = HomePlace.LATITUDE,
    lon: Double = HomePlace.LONGITUDE,
    zone: ZoneId = HomePlace.ZONE,
): VillageScene {
    val minute = at.truncatedTo(ChronoUnit.MINUTES)
    val sky = homeSkyScene(minute, weatherCode, lat, lon, arc = DockSkyLayout.ARC)
    val there = minute.withZoneSameInstant(zone)
    val sun = sunTimes(there.toLocalDate(), zone, lat, lon)
    val daylight = Duration.between(sun.sunrise, sun.sunset).seconds.coerceAtLeast(1L)
    val through = (Duration.between(sun.sunrise, there).seconds.toFloat() / daylight).coerceIn(0f, 1f)
    val look = villageLook(sky.sunElevationDeg, through, lateNight(there.toLocalTime()))
    val hours = villageHours(there.toLocalTime(), sun.sunrise.toLocalTime(), sun.sunset.toLocalTime())
    val advent = adventDay(minute.toLocalDate())
    return VillageScene(look, sky.copy(look = villageSky(look, sky.weather), dim = 0f), minute.hour, minute.minute, hours, advent)
}

/** The village's sky colours for the shared sky painter, greyed by the weather as the home sky is. */
fun villageSky(look: VillageLook, weather: SkyWeather): SkyLook =
    weatherSky(SkyLook(look.skyTop, look.skyMid, look.skyHorizon, look.glow, look.glowAlpha, look.stars), weather)

/** [t] of the way from [a] to [b], colour by colour and number by number. */
internal fun blend(a: VillageLook, b: VillageLook, t: Float): VillageLook {
    if (t <= 0f) return a
    if (t >= 1f) return b

    fun number(from: Float, to: Float) = from + (to - from) * t
    return VillageLook(
        skyTop = mix(a.skyTop, b.skyTop, t),
        skyMid = mix(a.skyMid, b.skyMid, t),
        skyHorizon = mix(a.skyHorizon, b.skyHorizon, t),
        glow = mix(a.glow, b.glow, t),
        glowAlpha = number(a.glowAlpha, b.glowAlpha),
        stars = number(a.stars, b.stars),
        amb = mix(a.amb, b.amb, t),
        ambK = number(a.ambK, b.ambK),
        snow = mix(a.snow, b.snow, t),
        snowLow = mix(a.snowLow, b.snowLow, t),
        far = mix(a.far, b.far, t),
        farSnow = mix(a.farSnow, b.farSnow, t),
        lights = number(a.lights, b.lights),
        aurora = number(a.aurora, b.aurora),
        quiet = number(a.quiet, b.quiet),
        room = mix(a.room, b.room, t),
        roomK = number(a.roomK, b.roomK),
        fire = number(a.fire, b.fire),
        dark = number(a.dark, b.dark),
    )
}

/** 0 to 1, easing in and out. */
internal fun smooth(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return c * c * (3 - 2 * c)
}

internal const val WHITE = 0xFFFFFFFF.toInt()
private const val OPAQUE = 0xFF000000.toInt()
private const val LATE_FROM_MIN = 21 * 60 + 30
private const val LATE_BLEND_MIN = 60f

/** The same look with every colour opaque (0xFFRRGGBB), as the colour mixing gives back. */
private fun VillageLook.opaque(): VillageLook = copy(
    skyTop = skyTop or OPAQUE,
    skyMid = skyMid or OPAQUE,
    skyHorizon = skyHorizon or OPAQUE,
    glow = glow or OPAQUE,
    amb = amb or OPAQUE,
    snow = snow or OPAQUE,
    snowLow = snowLow or OPAQUE,
    far = far or OPAQUE,
    farSnow = farSnow or OPAQUE,
    room = room or OPAQUE,
)
