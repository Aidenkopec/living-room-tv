package tv.livingroom.home.sky

import java.time.Instant
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan
import tv.livingroom.home.HomePlace

/*
 * Where the Moon is and how much of it is lit, worked out on the TV (no
 * internet). Meeus, "Astronomical Algorithms" (2nd ed.), chapter 47, with only
 * its largest terms: good to about 0.3°, plenty for a moon drawn on a screen.
 */

/** The Moon's phase: [ageDays] since the last new moon, [lit] (0 to 1) and whether it's [waxing] (lit on the right). */
data class MoonPhase(val ageDays: Double, val lit: Double, val waxing: Boolean)

/**
 * The Moon's height above the horizon as seen from the ground, and its hour angle (−180 to 180°:
 * negative in the east, 0 at its highest).
 */
data class MoonPlace(val elevationDeg: Double, val hourAngleDeg: Double)

/** The Moon's phase at [at], from how far it is from the Sun along the sky. */
fun moonPhase(at: Instant): MoonPhase {
    val moon = moonAt(at)
    val apart = (moon.longitudeDeg - sunLongitudeDeg(at)).mod(360.0) // 0 at new moon, 180 at full.
    val elongation = deg(kotlin.math.acos(cos(rad(moon.latitudeDeg)) * cos(rad(apart))))
    return MoonPhase(ageDays = apart / 360.0 * SYNODIC_MONTH_DAYS, lit = (1 - cos(rad(elongation))) / 2, waxing = apart < 180.0)
}

/** Where the Moon is in Calgary's sky (or at [lat], [lon]) at [at], allowing for its parallax. */
fun moonPlace(at: Instant, lat: Double = HomePlace.LATITUDE, lon: Double = HomePlace.LONGITUDE): MoonPlace {
    val moon = moonAt(at)
    val t = julianCenturies(at)
    val obliquity = rad(23.439291 - 0.0130042 * t)
    val lambda = rad(moon.longitudeDeg)
    val beta = rad(moon.latitudeDeg)
    val rightAscension = atan2(sin(lambda) * cos(obliquity) - tan(beta) * sin(obliquity), cos(lambda))
    val declination = asin(sin(beta) * cos(obliquity) + cos(beta) * sin(obliquity) * sin(lambda))
    val days = julianDay(at) - J2000
    val siderealDeg = 280.46061837 + 360.98564736629 * days + 0.000387933 * t * t + lon
    val hourAngle = (siderealDeg - deg(rightAscension)).mod(360.0).let { if (it > 180) it - 360 else it }
    val sinHeight = sin(rad(lat)) * sin(declination) + cos(rad(lat)) * cos(declination) * cos(rad(hourAngle))
    val geocentric = asin(sinHeight.coerceIn(-1.0, 1.0))
    // Seen from the ground rather than the Earth's centre, the Moon sits about a degree lower.
    val parallax = asin(EARTH_RADIUS_KM / moon.distanceKm)
    return MoonPlace(elevationDeg = deg(geocentric - parallax * cos(geocentric)), hourAngleDeg = hourAngle)
}

/** The Moon's ecliptic longitude and latitude (degrees) and its distance (km). */
private class MoonEcliptic(val longitudeDeg: Double, val latitudeDeg: Double, val distanceKm: Double)

private fun moonAt(at: Instant): MoonEcliptic {
    val t = julianCenturies(at)
    val meanLong = 218.3164477 + 481267.88123421 * t
    val d = rad(297.8501921 + 445267.1114034 * t) // Elongation.
    val m = rad(357.5291092 + 35999.0502909 * t) // The Sun's anomaly.
    val mp = rad(134.9633964 + 477198.8675055 * t) // The Moon's anomaly.
    val f = rad(93.2720950 + 483202.0175233 * t) // Its argument of latitude.
    val e = 1 - 0.002516 * t // The Earth's orbit, slowly getting rounder.
    val longitude = meanLong +
        6.288774 * sin(mp) + 1.274027 * sin(2 * d - mp) + 0.658314 * sin(2 * d) + 0.213618 * sin(2 * mp) -
        0.185116 * e * sin(m) - 0.114332 * sin(2 * f) + 0.058793 * sin(2 * d - 2 * mp) +
        0.057066 * e * sin(2 * d - m - mp) + 0.053322 * sin(2 * d + mp) + 0.045758 * e * sin(2 * d - m) -
        0.040923 * e * sin(m - mp) - 0.034720 * sin(d) - 0.030383 * e * sin(m + mp)
    val latitude = 5.128122 * sin(f) + 0.280602 * sin(mp + f) + 0.277693 * sin(mp - f) + 0.173237 * sin(2 * d - f) +
        0.055413 * sin(2 * d - mp + f) + 0.046271 * sin(2 * d - mp - f)
    val distance = 385000.56 - 20905.355 * cos(mp) - 3699.111 * cos(2 * d - mp) - 2955.968 * cos(2 * d) - 569.925 * cos(2 * mp)
    return MoonEcliptic(longitude.mod(360.0), latitude, distance)
}

private const val SYNODIC_MONTH_DAYS = 29.530588853
private const val EARTH_RADIUS_KM = 6378.14
