package tv.livingroom.home.sky

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reference values from the US Naval Observatory (aa.usno.navy.mil/api, read
 * 2026-10-20): Moon phases in UT, and Calgary's moonrise and moonset (51.0447 N, 114.0719 W).
 */
class MoonTest {
    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int): Instant = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC).toInstant()
    private fun mdt(mo: Int, d: Int, h: Int, mi: Int): Instant =
        ZonedDateTime.of(2026, mo, d, h, mi, 0, 0, ZoneOffset.ofHours(-6)).toInstant()
    private fun mst(mo: Int, d: Int, h: Int, mi: Int): Instant =
        ZonedDateTime.of(2026, mo, d, h, mi, 0, 0, ZoneOffset.ofHours(-7)).toInstant()

    private val newMoon = utc(2026, 10, 10, 15, 50)
    private val firstQuarter = utc(2026, 10, 18, 16, 12)
    private val fullMoon = utc(2026, 10, 26, 4, 12)

    @Test
    fun newMoonIsDark() {
        assertTrue(moonPhase(newMoon).lit < 0.02)
    }

    @Test
    fun fullMoonIsFull() {
        assertTrue(moonPhase(fullMoon).lit > 0.98)
    }

    @Test
    fun firstQuarterIsHalfAndWaxing() {
        val phase = moonPhase(firstQuarter)
        assertEquals(0.5, phase.lit, 0.05)
        assertTrue(phase.waxing)
    }

    @Test
    fun tonightIsWaxing() {
        // USNO: 70% lit on Oct 20.
        val phase = moonPhase(mdt(10, 20, 21, 30))
        assertTrue("${phase.lit}", phase.lit in 0.65..0.85)
        assertTrue(phase.waxing)
        assertTrue(!moonPhase(mdt(10, 30, 21, 30)).waxing) // After the full moon.
    }

    @Test
    fun ageWrapsAtNewMoon() {
        assertTrue(moonPhase(newMoon.minusSeconds(6 * 3600)).ageDays > 29.0)
        assertTrue(moonPhase(newMoon.plusSeconds(6 * 3600)).ageDays < 1.0)
        assertEquals(14.8, moonPhase(fullMoon).ageDays, 1.0)
    }

    // At moonrise and moonset the Moon's centre is a little under the horizon (refraction, its size).

    @Test
    fun elevationNearZeroAtMoonrise() {
        assertEquals(0.0, moonPlace(mdt(10, 20, 16, 34)).elevationDeg, 1.0)
        assertEquals(0.0, moonPlace(mst(12, 24, 17, 15)).elevationDeg, 1.0)
    }

    @Test
    fun elevationNearZeroAtMoonset() {
        assertEquals(0.0, moonPlace(mdt(10, 20, 1, 20)).elevationDeg, 1.0)
        assertEquals(0.0, moonPlace(mst(12, 24, 9, 45)).elevationDeg, 1.0)
    }

    @Test
    fun hourAngleZeroAtItsHighest() {
        assertEquals(0.0, moonPlace(mdt(10, 20, 21, 28)).hourAngleDeg, 1.5)
        assertEquals(0.0, moonPlace(mst(12, 24, 0, 53)).hourAngleDeg, 1.5)
        assertTrue(moonPlace(mdt(10, 20, 18, 0)).hourAngleDeg < 0) // Rising, in the east.
        assertTrue(moonPlace(mdt(10, 20, 23, 30)).hourAngleDeg > 0)
    }
}
