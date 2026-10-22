package tv.livingroom.home.screensaver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import tv.livingroom.home.sky.sunTimes

/** Which photos suit the time of day (the list tags each one). */
enum class PhotoMood { DAY, DUSK, NIGHT }

/** Living sky, Beautiful places, or the dimmed late-night photos. */
enum class SaverStyle { LIVING_SKY, PLACES, LATE_NIGHT }

/** What the screensaver shows now. Living sky carries [PhotoMood.DUSK], the photos it stands in for. */
data class SaverScene(val style: SaverStyle, val mood: PhotoMood)

/** The screensaver switches in Settings: Living sky at sunrise and sunset, and the dimmed late night with its turn-off. */
data class SaverSettings(val skyOn: Boolean = true, val lateNightOn: Boolean = true)

/** Late night is 11 PM to 5 AM. */
fun isLateNight(time: LocalTime): Boolean = time.hour >= 23 || time.hour < 5

/**
 * What to show at [now], from Calgary's sun on [now]'s own date. Late night
 * comes first, then Living sky within 30 minutes of sunrise or sunset, dusk
 * photos within 90, then day photos while the sun is up and night photos
 * after dark. [skyOn] off shows dusk photos instead of the sky; [lateNightOn]
 * off shows ordinary night photos instead of the dimmed ones.
 */
fun saverScene(now: ZonedDateTime, skyOn: Boolean = true, lateNightOn: Boolean = true): SaverScene {
    if (lateNightOn && isLateNight(now.toLocalTime())) return SaverScene(SaverStyle.LATE_NIGHT, PhotoMood.NIGHT)
    val sun = sunTimes(now.toLocalDate(), now.zone)
    val fromSun = minOf(Duration.between(sun.sunrise, now).abs(), Duration.between(sun.sunset, now).abs())
    return when {
        fromSun <= SKY_WINDOW -> SaverScene(if (skyOn) SaverStyle.LIVING_SKY else SaverStyle.PLACES, PhotoMood.DUSK)
        fromSun <= DUSK_WINDOW -> SaverScene(SaverStyle.PLACES, PhotoMood.DUSK)
        now.isAfter(sun.sunrise) && now.isBefore(sun.sunset) -> SaverScene(SaverStyle.PLACES, PhotoMood.DAY)
        else -> SaverScene(SaverStyle.PLACES, PhotoMood.NIGHT)
    }
}

/** True once the screensaver has shown for [offAfterMin] minutes and it's late night: time to turn the TV off. */
fun lateNightOffDue(startedAt: ZonedDateTime, now: ZonedDateTime, lateNightOn: Boolean, offAfterMin: Int = 30): Boolean =
    lateNightOn && isLateNight(now.toLocalTime()) && Duration.between(startedAt, now) >= Duration.ofMinutes(offAfterMin.toLong())

/** Living sky's colours: a gradient from [top] through [middle] to [horizon], a [glow] on the horizon, and [stars] (0 to 1). */
data class SkyLook(val top: Int, val middle: Int, val horizon: Int, val glow: Int, val glowAlpha: Float, val stars: Float)

/** The sky's colours for the sun at [elevationDeg] degrees: gold, then pink, then deep blue with stars. */
fun skyLook(elevationDeg: Double): SkyLook {
    val (upper, lower) = SKY_KEYFRAMES.zipWithNext().firstOrNull { (a, b) -> elevationDeg <= a.first && elevationDeg >= b.first }
        ?: return (if (elevationDeg > SKY_KEYFRAMES.first().first) SKY_KEYFRAMES.first() else SKY_KEYFRAMES.last()).second
    val t = ((upper.first - elevationDeg) / (upper.first - lower.first)).toFloat()
    val a = upper.second
    val b = lower.second
    return SkyLook(
        top = mix(a.top, b.top, t),
        middle = mix(a.middle, b.middle, t),
        horizon = mix(a.horizon, b.horizon, t),
        glow = mix(a.glow, b.glow, t),
        glowAlpha = a.glowAlpha + (b.glowAlpha - a.glowAlpha) * t,
        stars = a.stars + (b.stars - a.stars) * t,
    )
}

/** What the weather adds to the sky. */
enum class SkyWeather { CLEAR, CLOUDY, RAIN, SNOW }

/** The sky's weather for a WMO [code]; no weather, or an unknown code, is clear. */
fun skyWeather(code: Int?): SkyWeather = when (code) {
    2, 3, 45, 48 -> SkyWeather.CLOUDY
    in 51..67, in 80..82, 95, 96, 99 -> SkyWeather.RAIN
    in 71..77, 85, 86 -> SkyWeather.SNOW
    else -> SkyWeather.CLEAR
}

/** The big clock: "7:48" (no AM/PM), or "19:48" when the TV uses 24-hour time. */
fun saverClock(time: LocalTime, is24Hour: Boolean): String = (if (is24Hour) CLOCK_24 else CLOCK_12).format(time)

/** The line under the clock: "Monday, October 19 · ☁ 8° Cloudy", or just the date with no [weather]. */
fun saverDateLine(date: LocalDate, weather: String?): String = listOfNotNull(DATE_LINE.format(date), weather).joinToString(" · ")

private val SKY_WINDOW = Duration.ofMinutes(30)
private val DUSK_WINDOW = Duration.ofMinutes(90)

private fun rgb(hex: Long) = hex.toInt()

// Sun's height (degrees), highest first. The +15° glow is the gold one, faded out, so the gold fades in by itself.
private val SKY_KEYFRAMES = listOf(
    15.0 to SkyLook(rgb(0xFF2E64B0), rgb(0xFF6FA0D8), rgb(0xFFBCD6EE), rgb(0xFFFFD27A), 0f, 0f),
    6.0 to SkyLook(rgb(0xFF3D6FA8), rgb(0xFF8FB3D1), rgb(0xFFF2C27A), rgb(0xFFFFD27A), 0.45f, 0f),
    0.0 to SkyLook(rgb(0xFF24305E), rgb(0xFF6A5A8A), rgb(0xFFF08A5D), rgb(0xFFFF8C5A), 0.5f, 0f),
    -4.0 to SkyLook(rgb(0xFF0D1A3D), rgb(0xFF33305E), rgb(0xFFD98A6A), rgb(0xFFFF8C5A), 0.42f, 0.5f),
    -8.0 to SkyLook(rgb(0xFF050A1F), rgb(0xFF0D1A3D), rgb(0xFF2A2F5E), rgb(0xFF6A4A7A), 0.15f, 0.9f),
)

/** Opaque colour [t] of the way from [a] to [b], channel by channel. */
internal fun mix(a: Int, b: Int, t: Float): Int {
    fun channel(shift: Int): Int {
        val from = (a shr shift) and 0xFF
        val to = (b shr shift) and 0xFF
        return (from + (to - from) * t).roundToInt()
    }
    return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

private val CLOCK_12 = DateTimeFormatter.ofPattern("h:mm", Locale.US)
private val CLOCK_24 = DateTimeFormatter.ofPattern("H:mm", Locale.US)
private val DATE_LINE = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US)

/** How long each photo shows before the next fades in. */
const val SAVER_PHOTO_MS = 60_000L

/** How long a change of photo or style takes to fade. */
const val SAVER_FADE_MS = 3_000

/**
 * How far through its drift (0 to 1) a photo is, [elapsedMs] after it appeared. The drift
 * runs past the photo's minute, through the next photo's loading and fade-in, so the
 * outgoing photo keeps moving until it's gone.
 */
fun slideProgress(elapsedMs: Long): Float = (elapsedMs.toFloat() / (SAVER_PHOTO_MS + SAVER_FADE_MS + LOADING_ALLOWANCE_MS)).coerceIn(0f, 1f)

private const val LOADING_ALLOWANCE_MS = 1_000L // Decoding the next photo takes well under this.

/** Where a drifting photo sits: its top-left corner (in pixels, not rounded) and its scale. */
data class SlideDrift(val left: Float, val top: Float, val scale: Float)

/**
 * Photo [id]'s drift at [t] (0 to 1 through its time on screen) on a [width] x [height]
 * screen: scaled from 1.08 to 1.02 (or back) and moved 2% of the width sideways,
 * so its edges never show. Even ids zoom in, odd ones out; the sideways direction
 * alternates every two photos. Kept in fractions of a pixel, so it glides.
 */
fun slideDrift(id: Int, t: Float, width: Float, height: Float): SlideDrift {
    val scale = if (id % 2 == 0) 1.02f + 0.06f * t else 1.08f - 0.06f * t
    val direction = if (id % 4 < 2) 1f else -1f
    val left = (width - width * scale) / 2 + direction * (-0.01f + 0.02f * t) * width
    val top = (height - height * scale) / 2
    return SlideDrift(left, top, scale)
}
