package tv.livingroom.home.sky

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.livingroom.home.HomePlace

class SunTest {
    // Expected times: Open-Meteo's sunrise and sunset for Calgary, read 2026-10-19.
    private fun assertNear(expected: String, actual: ZonedDateTime) {
        val want = actual.with(LocalTime.parse(expected))
        val off = Duration.between(want, actual).abs()
        assertTrue("expected about $expected, got ${actual.toLocalTime()}", off <= Duration.ofMinutes(2))
    }

    private fun assertSun(date: String, sunrise: String, sunset: String) {
        val sun = sunTimes(LocalDate.parse(date))
        assertEquals(LocalDate.parse(date), sun.sunrise.toLocalDate())
        assertEquals(LocalDate.parse(date), sun.sunset.toLocalDate())
        assertNear(sunrise, sun.sunrise)
        assertNear(sunset, sun.sunset)
    }

    @Test
    fun decemberSunTimes() = assertSun("2025-12-21", "08:37", "16:31")

    @Test
    fun juneSunTimes() = assertSun("2026-06-21", "05:21", "21:54")

    @Test
    fun octoberSunTimes() = assertSun("2026-10-19", "08:06", "18:34")

    // The clocks went forward at 2 AM that morning.
    @Test
    fun dstDaySunTimes() = assertSun("2026-03-08", "08:05", "19:29")

    @Test
    fun elevationAtSunriseIsJustBelowHorizon() {
        val sunrise = sunTimes(LocalDate.parse("2026-10-19")).sunrise.toInstant()
        assertEquals(-0.83, sunElevation(sunrise), 0.3)
    }

    @Test
    fun juneNoonElevation() {
        val start = ZonedDateTime.of(LocalDate.parse("2026-06-21"), LocalTime.NOON, HomePlace.ZONE)
        val highest = (0..180).maxOf { sunElevation(start.plusMinutes(it.toLong()).toInstant()) }
        assertTrue("got $highest", highest in 62.1..62.7)
    }
}
