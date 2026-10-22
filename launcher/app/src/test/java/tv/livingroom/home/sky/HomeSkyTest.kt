package tv.livingroom.home.sky

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.livingroom.home.HomePlace
import tv.livingroom.home.screensaver.SkyWeather
import tv.livingroom.home.screensaver.skyLook
import tv.livingroom.home.sky.HomeSkyLayout.ARC_LEFT
import tv.livingroom.home.sky.HomeSkyLayout.ARC_RIGHT
import tv.livingroom.home.sky.HomeSkyLayout.ARC_TOP
import tv.livingroom.home.sky.HomeSkyLayout.HEIGHT
import tv.livingroom.home.sky.HomeSkyLayout.HORIZON
import tv.livingroom.home.sky.HomeSkyLayout.WIDTH

class HomeSkyTest {
    private val zone = HomePlace.ZONE
    private val oct20 = LocalDate.of(2026, 10, 20)
    private fun at(date: LocalDate, h: Int, m: Int = 0) = ZonedDateTime.of(date, LocalTime.of(h, m), zone)

    // Svalbard: the sun never rises in December and never sets in June.
    private val polarLat = 78.0
    private val polarLon = 15.0
    private val polarZone = ZoneId.of("Arctic/Longyearbyen")

    // --- The sun on screen ------------------------------------------------------

    @Test
    fun sunAtSunriseLowOnTheLeft() {
        val sun = sunTimes(oct20)
        val day = Duration.between(sun.sunrise, sun.sunset).toMinutes() + 60.0
        val p = sunOnScreen(sun.sunrise)!!
        assertEquals(ARC_LEFT + (ARC_RIGHT - ARC_LEFT) * (30 / day).toFloat(), p.x, 1f)
        assertEquals(HORIZON, p.y, 8f)
    }

    @Test
    fun sunAtNoonAtTheTop() {
        for (date in listOf(LocalDate.of(2026, 6, 21), LocalDate.of(2026, 12, 21), oct20)) {
            val p = sunOnScreen(solarNoon(sunTimes(date)))!!
            assertEquals("$date", ARC_TOP, p.y, 1f)
            assertEquals("$date", (ARC_LEFT + ARC_RIGHT) / 2, p.x, 5f)
        }
    }

    @Test
    fun sunAtSunsetLowOnTheRight() {
        val sun = sunTimes(oct20)
        val day = Duration.between(sun.sunrise, sun.sunset).toMinutes() + 60.0
        val p = sunOnScreen(sun.sunset)!!
        assertEquals(ARC_RIGHT - (ARC_RIGHT - ARC_LEFT) * (30 / day).toFloat(), p.x, 1f)
        assertEquals(HORIZON, p.y, 8f)
    }

    @Test
    fun sunHiddenBelowMinus2point5() {
        var time = sunTimes(oct20).sunset
        while (sunElevation(time.toInstant()) > -2.4) {
            assertNotNull("$time", sunOnScreen(time))
            time = time.plusMinutes(1)
        }
        while (sunElevation(time.toInstant()) > -2.6) time = time.plusMinutes(1)
        assertNull(sunOnScreen(time))
        assertNull(sunOnScreen(at(oct20, 23, 0)))
    }

    @Test
    fun sunAlwaysOnTheArc() {
        for (minute in 0 until 24 * 60) {
            val p = sunOnScreen(at(oct20, minute / 60, minute % 60)) ?: continue
            assertTrue("$minute: ${p.x}", p.x in ARC_LEFT..ARC_RIGHT)
            // Down to 2.5° under the horizon (a tenth of the arc's height at most) as it sets behind the ridges.
            assertTrue("$minute: ${p.y}", p.y in ARC_TOP..(HORIZON + 0.1f * (HORIZON - ARC_TOP)))
        }
    }

    @Test
    fun polarNightHasNoSun() {
        for (hour in 0 until 24) {
            assertNull(sunOnScreen(ZonedDateTime.of(2026, 12, 21, hour, 0, 0, 0, polarZone), polarLat, polarLon))
        }
    }

    @Test
    fun midnightSunStaysOnArc() {
        for (minute in 0 until 24 * 60 step 10) {
            val p = sunOnScreen(ZonedDateTime.of(2026, 6, 21, minute / 60, minute % 60, 0, 0, polarZone), polarLat, polarLon)!!
            assertTrue("$minute: $p", p.x in ARC_LEFT..ARC_RIGHT && p.y in ARC_TOP..HORIZON)
        }
    }

    @Test
    fun noNaNAllYear() {
        for ((lat, lon, z) in listOf(Triple(HomePlace.LATITUDE, HomePlace.LONGITUDE, zone), Triple(polarLat, polarLon, polarZone))) {
            var time = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, z)
            while (time.year == 2026) {
                val sun = sunOnScreen(time, lat, lon)
                val moon = moonOnScreen(moonPlace(time.toInstant(), lat, lon), sunElevation(time.toInstant(), lat, lon))
                for (p in listOfNotNull(sun, moon)) {
                    assertTrue("$lat $time $p", p.x.isFinite() && p.y.isFinite() && p.x in 0f..WIDTH && p.y in 0f..HEIGHT)
                }
                time = time.plusMinutes(10)
            }
        }
    }

    // --- The moon on screen -----------------------------------------------------

    @Test
    fun moonHiddenInDaylight() {
        assertNull(moonOnScreen(MoonPlace(30.0, 0.0), sunElevationDeg = 5.0))
        assertNotNull(moonOnScreen(MoonPlace(30.0, 0.0), sunElevationDeg = 1.0))
    }

    @Test
    fun moonBelowHorizonHidden() {
        assertNull(moonOnScreen(MoonPlace(-1.0, 0.0), sunElevationDeg = -10.0))
    }

    @Test
    fun moonOnMeridianInTheMiddle() {
        val p = moonOnScreen(MoonPlace(20.0, 0.0), sunElevationDeg = -10.0)!!
        assertEquals((ARC_LEFT + ARC_RIGHT) / 2, p.x, 0.01f)
        assertEquals(HORIZON - (HORIZON - ARC_TOP) * 20f / 30f, p.y, 0.01f)
        assertEquals(ARC_TOP, moonOnScreen(MoonPlace(55.0, 0.0), -10.0)!!.y, 0.01f) // Held at the top.
    }

    @Test
    fun moonFarEastHeldAtArcLeft() {
        assertEquals(ARC_LEFT, moonOnScreen(MoonPlace(5.0, -150.0), -10.0)!!.x, 0.01f)
        assertEquals(ARC_RIGHT, moonOnScreen(MoonPlace(5.0, 150.0), -10.0)!!.x, 0.01f)
    }

    // --- The sky's look ---------------------------------------------------------

    @Test
    fun clearSkyIsTheScreensaverSky() {
        for (e in -20..70 step 5) assertEquals(skyLook(e.toDouble()), homeSkyLook(e.toDouble(), SkyWeather.CLEAR))
    }

    @Test
    fun cloudsGreyTheSkyAndHideStars() {
        val night = homeSkyLook(-10.0, SkyWeather.CLOUDY)
        assertEquals(0f, night.stars)
        assertTrue(night.top != skyLook(-10.0).top)
        assertEquals(skyLook(0.0).glowAlpha * 0.45f, homeSkyLook(0.0, SkyWeather.RAIN).glowAlpha, 0.001f)
    }

    @Test
    fun snowDimsSunAndMoon() {
        assertEquals(1f, bodyAlpha(SkyWeather.CLEAR))
        assertEquals(0.6f, bodyAlpha(SkyWeather.SNOW))
    }

    @Test
    fun rainHidesSunAndMoon() {
        assertEquals(0f, bodyAlpha(SkyWeather.RAIN))
        assertEquals(0f, bodyAlpha(SkyWeather.CLOUDY))
    }

    @Test
    fun dayDimGrowsWithTheSun() {
        assertEquals(0f, dayDim(-5.0))
        assertEquals(0f, dayDim(0.0))
        assertEquals(DAY_DIM_MAX / 2, dayDim(DAY_DIM_FULL_AT / 2), 0.001f)
        assertEquals(DAY_DIM_MAX, dayDim(DAY_DIM_FULL_AT))
        assertEquals(DAY_DIM_MAX, dayDim(60.0))
    }

    @Test
    fun noWeatherIsClearSky() {
        val scene = homeSkyScene(at(oct20, 13, 0), weatherCode = null)
        assertEquals(SkyWeather.CLEAR, scene.weather)
        assertNotNull(scene.sun)
        assertEquals(1f, scene.bodyAlpha)
    }

    @Test
    fun unknownCodeIsClearSky() {
        assertEquals(SkyWeather.CLEAR, homeSkyScene(at(oct20, 13, 0), weatherCode = 42).weather)
    }

    @Test
    fun glowSitsAtTheSunOrTowardItsSide() {
        val noon = homeSkyScene(at(oct20, 13, 0), null)
        assertEquals(noon.sun!!.x / WIDTH, noon.glowX, 0.0001f)
        assertEquals(0.52f, homeSkyScene(at(oct20, 2, 0), null).glowX) // Night, before solar noon: toward the morning side.
        assertEquals(0.83f, homeSkyScene(at(oct20, 22, 0), null).glowX) // After: toward the evening side.
    }

    @Test
    fun sceneIsTheSameAllMinute() {
        assertEquals(homeSkyScene(at(oct20, 13, 0), 3), homeSkyScene(at(oct20, 13, 0).plusSeconds(59), 3))
    }

    @Test
    fun skyUsesTheNewDayAfterMidnight() {
        val oct21 = oct20.plusDays(1)
        assertNull(homeSkyScene(at(oct21, 0, 1), null).sun)
        // At the next sunrise, the sun sits where Oct 21's day length puts it.
        val sun = sunTimes(oct21)
        val day = Duration.between(sun.sunrise, sun.sunset).toMinutes() + 60.0
        assertEquals(ARC_LEFT + (ARC_RIGHT - ARC_LEFT) * (30 / day).toFloat(), sunOnScreen(sun.sunrise)!!.x, 1f)
    }

    // --- Readable over every sky ---------------------------------------------------

    /** A box of text: where it sits (dp), how white it is, and the contrast it needs. */
    private class Words(
        val name: String,
        val x: ClosedFloatingPointRange<Float>,
        val y: ClosedFloatingPointRange<Float>,
        val alpha: Float,
        val needs: Double,
    )

    // Positions from the layout (HomeScreen, TopAreaView); alphas are the dimmest text in each box.
    private val words = listOf(
        Words("lead", 43f..583f, 56f..76f, 0.8f, 4.5),
        Words("meta", 43f..583f, 176f..197f, 0.82f, 4.5), // Episode and time, one line under the title.
        Words("corner", 642f..917f, 25f..75f, CORNER_TEXT_ALPHA, 4.5), // Both lines: the clock row and the sky line.
        Words("row heading", 43f..340f, 230f..246f, HEADING_TEXT_ALPHA, 4.5),
        Words("card titles", 43f..917f, 384f..418f, 1f, 4.5), // Below the sky, on the plain background.
    )
    private val title = Words("title", 43f..583f, 85f..175f, 1f, 3.0)

    /**
     * The lowest contrast of [w] over the sky, across 2026 (every 15 minutes on the 21st of each month) and all four weathers.
     * [arc] and [backdrop] pick the screen: Living sky's (the default) or the dock's.
     */
    private fun worstContrast(
        w: Words,
        arc: SkyArc = HomeSkyLayout.ARC,
        backdrop: (HomeSkyScene, Float, Float) -> Int = ::backdropAt,
    ): Pair<Double, String> {
        var worst = Double.MAX_VALUE to ""
        for (month in 1..12) {
            for (minute in 0 until 24 * 60 step 15) {
                for (code in listOf(0, 3, 61, 73)) {
                    val time = at(LocalDate.of(2026, month, 21), minute / 60, minute % 60)
                    val scene = homeSkyScene(time, code, arc = arc)
                    var x = w.x.start
                    while (x <= w.x.endInclusive) {
                        var y = w.y.start
                        while (y <= w.y.endInclusive) {
                            val c = contrast(WHITE, w.alpha, backdrop(scene, x, y))
                            if (c < worst.first) worst = c to "$time code $code at ($x, $y)"
                            y += 10f
                        }
                        x += 10f
                    }
                }
            }
        }
        return worst
    }

    @Test
    fun wordsReadableOverEverySky() {
        for (w in words) {
            val (c, where) = worstContrast(w)
            assertTrue("${w.name}: $c at $where", c >= w.needs)
        }
    }

    @Test
    fun titleReadableOverEverySky() {
        val (c, where) = worstContrast(title)
        assertTrue("title: $c at $where", c >= title.needs)
    }

    // --- The dock screen (Mountains and dock): the whole screen is sky -------------------------

    private val dockArc = DockSkyLayout.ARC

    @Test
    fun dockSunCrossesTheWholeSky() {
        val sun = sunTimes(oct20)
        val day = Duration.between(sun.sunrise, sun.sunset).toMinutes() + 60.0
        val rise = sunOnScreen(sun.sunrise, arc = dockArc)!!
        assertEquals(dockArc.left + (dockArc.right - dockArc.left) * (30 / day).toFloat(), rise.x, 1f)
        assertEquals(dockArc.horizon, rise.y, 8f)
        val noon = sunOnScreen(solarNoon(sun), arc = dockArc)!!
        assertEquals(dockArc.top, noon.y, 1f)
        assertEquals((dockArc.left + dockArc.right) / 2, noon.x, 5f)
        val set = sunOnScreen(sun.sunset, arc = dockArc)!!
        assertEquals(dockArc.right - (dockArc.right - dockArc.left) * (30 / day).toFloat(), set.x, 1f)
    }

    @Test
    fun dockMoonStaysOnItsArc() {
        val high = moonOnScreen(MoonPlace(elevationDeg = 40.0, hourAngleDeg = 0.0), sunElevationDeg = -20.0, arc = dockArc)!!
        assertEquals((dockArc.left + dockArc.right) / 2, high.x, 0.01f)
        assertEquals(dockArc.top, high.y, 0.01f)
        val rising = moonOnScreen(MoonPlace(elevationDeg = 1.0, hourAngleDeg = -90.0), sunElevationDeg = -20.0, arc = dockArc)!!
        assertEquals(dockArc.left, rising.x, 0.01f)
    }

    @Test
    fun dockSkyNoNaNAllYear() {
        var time = ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone)
        while (time.year == 2026) {
            val sun = sunOnScreen(time, arc = dockArc)
            val moon =
                moonOnScreen(
                    moonPlace(time.toInstant(), HomePlace.LATITUDE, HomePlace.LONGITUDE),
                    sunElevation(time.toInstant(), HomePlace.LATITUDE, HomePlace.LONGITUDE),
                    arc = dockArc,
                )
            for (p in listOfNotNull(sun, moon)) {
                assertTrue(
                    "$time $p",
                    p.x.isFinite() && p.y.isFinite() && p.x in 0f..DockSkyLayout.WIDTH && p.y in 0f..DockSkyLayout.HEIGHT,
                )
            }
            time = time.plusMinutes(10)
        }
    }

    // Positions from DockHomeView: the clock 30 dp down and 48 dp in, its line under it.
    private val dockClock = Words("clock", 48f..240f, 38f..92f, 1f, 3.0) // 68 sp: large text needs 3:1.
    private val dockLine = Words("line under the clock", 48f..560f, 104f..122f, DOCK_LINE_ALPHA, 4.5)

    @Test
    fun dockClockReadableOverEverySky() {
        for (w in listOf(dockClock, dockLine)) {
            val (c, where) = worstContrast(w, dockArc, ::dockBackdropAt)
            assertTrue("${w.name}: $c at $where", c >= w.needs)
        }
    }

    @Test
    fun dockTileWordsReadableOverEverySky() {
        // A selected square tile's words ("PS5", "Explore") sit on the dock's bar, near the bottom of the screen.
        val words = Words("tile words", 120f..840f, 480f..520f, 0.9f, 4.5)
        val (c, where) = worstContrast(words, dockArc, ::dockTileAt)
        assertTrue("${words.name}: $c at $where", c >= words.needs)
    }

    @Test
    fun contrastMatchesWcag() {
        assertEquals(21.0, contrast(WHITE, 1f, 0xFF000000.toInt()), 0.01)
        assertEquals(1.0, contrast(WHITE, 1f, WHITE), 0.01)
        assertEquals(4.54, contrast(WHITE, 1f, 0xFF767676.toInt()), 0.02)
    }

    // --- The corner's second line ------------------------------------------------------

    private val clock12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    @Test
    fun cornerLineSunrise() {
        assertEquals("Sunrise ${sunTimes(oct20).sunrise.format(clock12)}", cornerSkyLine(at(oct20, 7, 0), is24Hour = false))
    }

    @Test
    fun cornerLineSunset() {
        assertEquals("Sunset ${sunTimes(oct20).sunset.format(clock12)}", cornerSkyLine(at(oct20, 12, 0), is24Hour = false))
    }

    @Test
    fun cornerLineMoon() {
        val time = at(oct20, 21, 30)
        val percent = Math.round(moonPhase(time.toInstant()).lit * 100)
        assertEquals("Moon $percent% full", cornerSkyLine(time, is24Hour = false))
    }

    @Test
    fun cornerLineFullMoon() {
        // The full moon is at 04:12 UT on Oct 26: 22:12 on Oct 25 in Calgary, with the moon up.
        assertEquals("Full moon", cornerSkyLine(at(LocalDate.of(2026, 10, 25), 22, 12), is24Hour = false))
    }

    @Test
    fun cornerLineAfterMidnight() {
        val oct21 = oct20.plusDays(1)
        assertEquals("Sunrise ${sunTimes(oct21).sunrise.format(clock12)}", cornerSkyLine(at(oct21, 0, 30), is24Hour = false))
    }

    @Test
    fun cornerLineTomorrowsSunriseWithoutTheMoon() {
        // Two days after the new moon of Oct 10: a thin moon that has already set by 22:00.
        val oct12 = LocalDate.of(2026, 10, 12)
        assertEquals("Sunrise ${sunTimes(oct12.plusDays(1)).sunrise.format(clock12)}", cornerSkyLine(at(oct12, 22, 0), is24Hour = false))
    }

    @Test
    fun cornerLinePolarIsNull() {
        assertNull(cornerSkyLine(ZonedDateTime.of(2026, 12, 21, 12, 0, 0, 0, polarZone), false, polarLat, polarLon))
        assertNull(cornerSkyLine(ZonedDateTime.of(2026, 6, 21, 12, 0, 0, 0, polarZone), false, polarLat, polarLon))
    }

    @Test
    fun cornerLine24Hour() {
        val sunset = sunTimes(oct20).sunset.format(DateTimeFormatter.ofPattern("H:mm", Locale.US))
        assertEquals("Sunset $sunset", cornerSkyLine(at(oct20, 12, 0), is24Hour = true))
    }

    private companion object {
        val WHITE = 0xFFFFFFFF.toInt()
    }
}
