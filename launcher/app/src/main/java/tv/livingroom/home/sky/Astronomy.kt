package tv.livingroom.home.sky

import java.time.Instant

/** The Julian day at [at]: days since noon, 1 January 4713 BC (UTC), the astronomers' clock. */
internal fun julianDay(at: Instant): Double = at.epochSecond / 86_400.0 + 2_440_587.5

/** Julian centuries since [J2000], the time unit of the Sun and Moon series. */
internal fun julianCenturies(at: Instant): Double = (julianDay(at) - J2000) / 36_525.0

/** The Julian day of 1 January 2000, 12:00 TT. */
internal const val J2000 = 2_451_545.0

internal fun rad(degrees: Double) = Math.toRadians(degrees)

internal fun deg(radians: Double) = Math.toDegrees(radians)
