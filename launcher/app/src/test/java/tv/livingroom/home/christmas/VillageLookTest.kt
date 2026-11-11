package tv.livingroom.home.christmas

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.livingroom.home.HomePlace
import tv.livingroom.home.screensaver.SkyWeather
import tv.livingroom.home.sky.DOCK_LINE_ALPHA
import tv.livingroom.home.sky.contrast
import tv.livingroom.home.sky.dockBackdropAt

class VillageLookTest {
    private val christmasEve = LocalDate.of(2026, 12, 24)
    private fun at(date: LocalDate, h: Int, m: Int = 0) = ZonedDateTime.of(date, LocalTime.of(h, m), HomePlace.ZONE)

    @Test
    fun theFourLooksShowAtTheirTimesOfDay() {
        // Calgary keeps UTC−6 all year: a December sunrise is about 9:37 by the clock, sunset about 5:33.
        assertEquals(MORNING, villageScene(at(christmasEve, 10), null).look)
        assertEquals(AFTERNOON, villageScene(at(christmasEve, 16, 10), null).look)
        assertEquals(EVENING, villageScene(at(christmasEve, 19, 30), null).look)
        assertEquals(MIDNIGHT, villageScene(at(christmasEve, 0, 5), null).look)
    }

    @Test
    fun theSameFourShowOnAnOrdinaryNovemberDay() {
        val november = LocalDate.of(2026, 11, 10)
        assertEquals(MORNING, villageScene(at(november, 9, 40), null).look)
        assertEquals(AFTERNOON, villageScene(at(november, 16, 10), null).look)
        assertEquals(EVENING, villageScene(at(november, 19, 30), null).look)
        assertEquals(MIDNIGHT, villageScene(at(november, 0, 5), null).look)
    }

    @Test
    fun duskSitsBetweenAfternoonAndEvening() {
        val dusk = villageLook((DAY_FROM_DEG + NIGHT_BELOW_DEG) / 2, dayThrough = 1f, lateNight = 0f)
        assertEquals(blend(EVENING, AFTERNOON, 0.5f), dusk)
        assertEquals((AFTERNOON.lights + EVENING.lights) / 2, dusk.lights, 0.001f)
        assertEquals(AFTERNOON, villageLook(DAY_FROM_DEG, 1f, 0f))
        assertEquals(EVENING, villageLook(NIGHT_BELOW_DEG, 1f, 0f))
    }

    @Test
    fun theEveningTurnsQuietBetweenHalfNineAndHalfTen() {
        assertEquals(0f, lateNight(LocalTime.of(17, 0)), 0f)
        assertEquals(0f, lateNight(LocalTime.of(21, 30)), 0f)
        assertEquals(0.5f, lateNight(LocalTime.of(22, 0)), 0.001f)
        assertEquals(1f, lateNight(LocalTime.of(22, 30)), 0f)
        assertEquals(1f, lateNight(LocalTime.of(23, 59)), 0f)
        assertEquals(1f, lateNight(LocalTime.of(0, 0)), 0f)
        assertEquals(1f, lateNight(LocalTime.of(7, 0)), 0f)
    }

    @Test
    fun nothingJumpsFromOneMinuteToTheNext() {
        fun parts(look: VillageLook) =
            listOf(look.skyTop, look.skyMid, look.skyHorizon, look.glow, look.amb, look.snow, look.snowLow, look.far, look.farSnow)
                .flatMap { c -> listOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF) }
        for (date in listOf(christmasEve, LocalDate.of(2026, 3, 20), LocalDate.of(2026, 6, 21), LocalDate.of(2026, 11, 10))) {
            var before = villageScene(at(date, 0), null).look
            for (minute in 1 until 24 * 60) {
                val look = villageScene(at(date, minute / 60, minute % 60), null).look
                val step = parts(before).zip(parts(look)).maxOf { (a, b) -> abs(a - b) }
                assertTrue("$date ${minute / 60}:${minute % 60} a colour moved $step", step <= 14)
                assertTrue(abs(look.lights - before.lights) <= 0.05f)
                assertTrue(abs(look.ambK - before.ambK) <= 0.05f)
                assertTrue(abs(look.stars - before.stars) <= 0.06f)
                assertTrue(abs(look.aurora - before.aurora) <= 0.06f)
                assertTrue(abs(look.quiet - before.quiet) <= 0.06f)
                before = look
            }
        }
    }

    @Test
    fun lightKeepsThePlacesHoursWhateverZoneTheScreenIsIn() {
        // Dusk in Calgary on Christmas Eve is already past midnight, and the next day, in UTC.
        val dusk = at(christmasEve, 18, 10)
        val elsewhere = dusk.withZoneSameInstant(java.time.ZoneId.of("UTC"))
        assertEquals(LocalDate.of(2026, 12, 25), elsewhere.toLocalDate())
        assertEquals(villageScene(dusk, null).look, villageScene(elsewhere, null).look)
        assertEquals(0f, villageScene(elsewhere, null).look.quiet, 0f)
        // The church clock still shows the screen's own time.
        assertEquals(0, villageScene(elsewhere, null).hour)
        assertEquals(10, villageScene(elsewhere, null).minute)
        // And a whole day seen from far away has no jump from one minute to the next.
        var before = villageScene(elsewhere, null).look
        for (minute in 1 until 24 * 60) {
            val look = villageScene(elsewhere.plusMinutes(minute.toLong()), null).look
            assertTrue(abs(look.quiet - before.quiet) <= 0.06f && abs(look.lights - before.lights) <= 0.05f)
            before = look
        }
    }

    @Test
    fun sceneIsTheSameAllMinute() {
        val time = at(christmasEve, 16, 20)
        assertEquals(villageScene(time, 73), villageScene(time.plusSeconds(59), 73))
    }

    @Test
    fun churchClockFollowsTheScenesTime() {
        val scene = villageScene(at(christmasEve, 15, 10).plusSeconds(30), null)
        assertEquals(15, scene.hour)
        assertEquals(10, scene.minute)
    }

    @Test
    fun cloudHidesTheStarsAndGreysTheSky() {
        val clear = villageSky(EVENING, SkyWeather.CLEAR)
        val cloudy = villageSky(EVENING, SkyWeather.CLOUDY)
        assertEquals(EVENING.stars, clear.stars, 0f)
        assertEquals(EVENING.skyTop, clear.top)
        assertEquals(0f, cloudy.stars, 0f)
        assertTrue(cloudy.top != clear.top)
        assertTrue(cloudy.glowAlpha < clear.glowAlpha)
    }

    @Test
    fun snowWeatherBringsMoreSnow() {
        assertTrue(snowAmount(SkyWeather.SNOW) > 2 * snowAmount(SkyWeather.CLEAR))
        assertEquals(snowAmount(SkyWeather.CLEAR), snowAmount(SkyWeather.RAIN), 0f)
    }

    // --- Readability: the clock and its line over every village sky -------------------------

    /**
     * The lowest contrast of white words in the box ([xs], [ys], dp) over the village's sky: every 10 minutes
     * of every 7th day of 2026, in all four weathers.
     */
    private fun worstContrast(
        xs: ClosedFloatingPointRange<Float>,
        ys: ClosedFloatingPointRange<Float>,
        alpha: Float,
    ): Pair<Double, String> {
        var worst = Double.MAX_VALUE to ""
        for (day in 0 until 365 step 7) {
            for (minute in 0 until 24 * 60 step 10) {
                for (code in listOf(0, 3, 61, 73)) {
                    val time = at(LocalDate.of(2026, 1, 1).plusDays(day.toLong()), minute / 60, minute % 60)
                    val sky = villageScene(time, code).sky
                    var x = xs.start
                    while (x <= xs.endInclusive) {
                        var y = ys.start
                        while (y <= ys.endInclusive) {
                            val c = contrast(WHITE, alpha, dockBackdropAt(sky, x, y, VILLAGE_SKY))
                            if (c < worst.first) worst = c to "$time code $code at ($x, $y)"
                            y += 6f
                        }
                        x += 10f
                    }
                }
            }
        }
        return worst
    }

    @Test
    fun wordsStayReadableUnderTheNorthernLights() {
        // The lights add their brightest green (see auroraMarks: at most 14% of it) to the darkest sky, under the clock's shade.
        val sky = villageScene(at(christmasEve, 0, 5), null).sky
        val green = 0x5AFFB4
        val under = dockBackdropAt(sky, 300f, 110f, VILLAGE_SKY)
        fun lit(shift: Int): Int = minOf(255, ((under shr shift) and 0xFF) + (((green shr shift) and 0xFF) * 0.14f).toInt())
        val backdrop = (0xFF shl 24) or (lit(16) shl 16) or (lit(8) shl 8) or lit(0)
        assertTrue(contrast(WHITE, DOCK_LINE_ALPHA, backdrop) >= 4.5)
    }

    // Positions from DockHomeView: the clock 30 dp down and 48 dp in, its line under it.
    @Test
    fun clockReadableOverEveryVillageSky() {
        val (c, where) = worstContrast(48f..240f, 38f..92f, 1f)
        assertTrue("clock: $c at $where", c >= 3.0) // 68 sp: large text needs 3:1.
    }

    @Test
    fun lineUnderTheClockReadableOverEveryVillageSky() {
        val (c, where) = worstContrast(48f..560f, 104f..122f, DOCK_LINE_ALPHA)
        assertTrue("line under the clock: $c at $where", c >= 4.5)
    }
}
