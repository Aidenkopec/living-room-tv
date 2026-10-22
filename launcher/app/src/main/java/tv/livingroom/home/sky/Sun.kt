package tv.livingroom.home.sky

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import tv.livingroom.home.HomePlace

data class SunTimes(val sunrise: ZonedDateTime, val sunset: ZonedDateTime)

/**
 * Sunrise and sunset on [date] (NOAA's sunrise equation, good to about a
 * minute): the moment the sun's top edge crosses the horizon, allowing for
 * the air bending its light (90.833° from straight up).
 */
fun sunTimes(
    date: LocalDate,
    zone: ZoneId = HomePlace.ZONE,
    lat: Double = HomePlace.LATITUDE,
    lon: Double = HomePlace.LONGITUDE,
): SunTimes {
    val midnightUtc = date.atStartOfDay(ZoneOffset.UTC).toInstant()

    // Each event is worked out twice: the second time with the sun's place at the first answer.
    fun event(sign: Int): ZonedDateTime {
        var minutes = 720.0 - 4 * lon // Solar noon, roughly, in minutes after midnight UTC.
        repeat(2) {
            val sun = sunAt(midnightUtc.plusSeconds((minutes * 60).toLong()))
            val cosHourAngle = (cos(rad(SUNRISE_ZENITH)) / (cos(rad(lat)) * cos(sun.declination))) - tan(rad(lat)) * tan(sun.declination)
            val hourAngle = deg(acos(cosHourAngle.coerceIn(-1.0, 1.0)))
            minutes = 720.0 - 4 * lon - sun.equationOfTimeMin + sign * 4 * hourAngle
        }
        return midnightUtc.plusSeconds((minutes * 60).toLong()).atZone(zone)
    }
    return SunTimes(sunrise = event(-1), sunset = event(1))
}

/** How high the sun's centre is above the horizon at [at], in degrees (negative below it). */
fun sunElevation(at: Instant, lat: Double = HomePlace.LATITUDE, lon: Double = HomePlace.LONGITUDE): Double {
    val sun = sunAt(at)
    val utcMinutes = (at.epochSecond.mod(86_400L)) / 60.0
    val trueSolarMinutes = (utcMinutes + sun.equationOfTimeMin + 4 * lon).mod(1440.0)
    val hourAngle = rad(trueSolarMinutes / 4 - 180)
    val cosZenith = sin(rad(lat)) * sin(sun.declination) + cos(rad(lat)) * cos(sun.declination) * cos(hourAngle)
    return 90 - deg(acos(cosZenith.coerceIn(-1.0, 1.0)))
}

/** Solar noon on [sun]'s day: halfway between sunrise and sunset, when the sun is highest. */
fun solarNoon(sun: SunTimes): ZonedDateTime = sun.sunrise.plusSeconds(Duration.between(sun.sunrise, sun.sunset).seconds / 2)

/** Where the sun is along the sky's yearly path at [at] (its apparent ecliptic longitude, degrees): for the Moon's phase. */
internal fun sunLongitudeDeg(at: Instant): Double = sunAt(at).apparentLongitudeDeg

/** The sun's declination (radians), the equation of time (minutes) and its apparent longitude (degrees) at [at]. */
private class SunPlace(val declination: Double, val equationOfTimeMin: Double, val apparentLongitudeDeg: Double)

private fun sunAt(at: Instant): SunPlace {
    val t = julianCenturies(at)
    val meanLong = (280.46646 + t * (36_000.76983 + t * 0.0003032)).mod(360.0)
    val meanAnomaly = 357.52911 + t * (35_999.05029 - 0.0001537 * t)
    val eccentricity = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)
    val centre = sin(rad(meanAnomaly)) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
        sin(rad(2 * meanAnomaly)) * (0.019993 - 0.000101 * t) + sin(rad(3 * meanAnomaly)) * 0.000289
    val omega = 125.04 - 1934.136 * t
    val apparentLong = meanLong + centre - 0.00569 - 0.00478 * sin(rad(omega))
    val meanObliquity = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
    val obliquity = meanObliquity + 0.00256 * cos(rad(omega))
    val declination = asin(sin(rad(obliquity)) * sin(rad(apparentLong)))
    val y = tan(rad(obliquity / 2)).let { it * it }
    val l0 = rad(meanLong)
    val m = rad(meanAnomaly)
    val equationOfTime = y * sin(2 * l0) - 2 * eccentricity * sin(m) + 4 * eccentricity * y * sin(m) * cos(2 * l0) -
        0.5 * y * y * sin(4 * l0) - 1.25 * eccentricity * eccentricity * sin(2 * m)
    return SunPlace(declination, 4 * deg(equationOfTime), apparentLong.mod(360.0))
}

private const val SUNRISE_ZENITH = 90.833
