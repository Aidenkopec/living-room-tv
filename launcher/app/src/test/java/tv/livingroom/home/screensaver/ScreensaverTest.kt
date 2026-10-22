package tv.livingroom.home.screensaver

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.livingroom.home.HomePlace
import tv.livingroom.home.sky.sunTimes

class ScreensaverTest {
    private fun at(date: String, time: String): ZonedDateTime =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.parse(time), HomePlace.ZONE)

    private val october = sunTimes(LocalDate.parse("2026-10-19")) // Sunset about 18:34.
    private val sky = SaverScene(SaverStyle.LIVING_SKY, PhotoMood.DUSK)
    private val dusk = SaverScene(SaverStyle.PLACES, PhotoMood.DUSK)
    private val day = SaverScene(SaverStyle.PLACES, PhotoMood.DAY)
    private val night = SaverScene(SaverStyle.PLACES, PhotoMood.NIGHT)
    private val lateNight = SaverScene(SaverStyle.LATE_NIGHT, PhotoMood.NIGHT)

    // --- which style when ---

    @Test
    fun thirtyMinutesIsSky() {
        assertEquals(sky, saverScene(october.sunset.minusMinutes(30)))
        assertEquals(sky, saverScene(october.sunset))
        assertEquals(sky, saverScene(october.sunset.plusMinutes(30)))
        assertEquals(sky, saverScene(october.sunrise.minusMinutes(30)))
        assertEquals(sky, saverScene(october.sunrise.plusMinutes(30)))
    }

    @Test
    fun thirtyOneMinutesIsDusk() {
        assertEquals(dusk, saverScene(october.sunset.minusMinutes(31)))
        assertEquals(dusk, saverScene(october.sunset.plusMinutes(31)))
        assertEquals(dusk, saverScene(october.sunrise.minusMinutes(31)))
        assertEquals(dusk, saverScene(october.sunset.minusMinutes(90)))
    }

    @Test
    fun ninetyOneMinutesIsDay() {
        assertEquals(day, saverScene(october.sunset.minusMinutes(91)))
        assertEquals(day, saverScene(october.sunrise.plusMinutes(91)))
        assertEquals(day, saverScene(at("2026-10-19", "11:15")))
    }

    @Test
    fun afterDuskIsNight() {
        assertEquals(night, saverScene(october.sunset.plusMinutes(91)))
        assertEquals(night, saverScene(at("2026-10-19", "20:30")))
    }

    @Test
    fun elevenPmIsLateNight() {
        assertEquals(night, saverScene(at("2026-10-19", "22:59")))
        assertEquals(lateNight, saverScene(at("2026-10-19", "23:00")))
        assertEquals(lateNight, saverScene(at("2026-10-20", "00:30")))
        assertTrue(isLateNight(LocalTime.parse("23:00")))
        assertFalse(isLateNight(LocalTime.parse("22:59")))
    }

    @Test
    fun fiveAmIsNotLateNight() {
        assertEquals(lateNight, saverScene(at("2026-10-19", "04:59")))
        assertEquals(night, saverScene(at("2026-10-19", "05:00")))
        assertTrue(isLateNight(LocalTime.parse("04:59")))
        assertFalse(isLateNight(LocalTime.parse("05:00")))
    }

    // June's sunrise is about 5:21, so its sky would start at 4:51.
    @Test
    fun lateNightBeatsJuneSunrise() {
        assertEquals(lateNight, saverScene(at("2026-06-21", "04:55")))
        assertEquals(sky, saverScene(at("2026-06-21", "05:00")))
    }

    @Test
    fun skyOffGivesDusk() {
        assertEquals(dusk, saverScene(october.sunset, skyOn = false))
    }

    @Test
    fun lateNightOffGivesNightPhotos() {
        assertEquals(night, saverScene(at("2026-10-19", "23:30"), lateNightOn = false))
    }

    // 21:30 in June is the sky (sunset about 21:54); with October's sun it would be night.
    @Test
    fun sceneUsesLocalDate() {
        assertEquals(sky, saverScene(at("2026-06-21", "21:30")))
        assertEquals(night, saverScene(at("2026-10-19", "21:30")))
    }

    // --- the late-night turn-off ---

    @Test
    fun offThirtyMinutesAfterStart() {
        val started = at("2026-10-19", "23:10")
        assertFalse(lateNightOffDue(started, at("2026-10-19", "23:39"), lateNightOn = true))
        assertTrue(lateNightOffDue(started, at("2026-10-19", "23:40"), lateNightOn = true))
    }

    @Test
    fun startedBeforeElevenGoesAtTwentyPast() {
        val started = at("2026-10-19", "22:50")
        assertFalse(lateNightOffDue(started, at("2026-10-19", "23:19"), lateNightOn = true))
        assertTrue(lateNightOffDue(started, at("2026-10-19", "23:20"), lateNightOn = true))
    }

    @Test
    fun startedLongBeforeGoesAtEleven() {
        val started = at("2026-10-19", "22:00")
        assertFalse(lateNightOffDue(started, at("2026-10-19", "22:59"), lateNightOn = true))
        assertTrue(lateNightOffDue(started, at("2026-10-19", "23:00"), lateNightOn = true))
    }

    @Test
    fun noOffWhenSwitchedOff() {
        assertFalse(lateNightOffDue(at("2026-10-19", "23:10"), at("2026-10-20", "00:30"), lateNightOn = false))
    }

    @Test
    fun noOffAfterFive() {
        assertFalse(lateNightOffDue(at("2026-10-20", "04:00"), at("2026-10-20", "05:00"), lateNightOn = true))
    }

    @Test
    fun testMinutesRespected() {
        val started = at("2026-10-19", "23:29")
        assertFalse(lateNightOffDue(started, at("2026-10-19", "23:29"), lateNightOn = true, offAfterMin = 1))
        assertTrue(lateNightOffDue(started, at("2026-10-19", "23:30"), lateNightOn = true, offAfterMin = 1))
    }

    // --- the sky's colours ---

    private fun rgb(hex: Long) = hex.toInt()

    @Test
    fun skyAtKeyframes() {
        assertEquals(SkyLook(rgb(0xFF2E64B0), rgb(0xFF6FA0D8), rgb(0xFFBCD6EE), rgb(0xFFFFD27A), 0f, 0f), skyLook(15.0))
        assertEquals(SkyLook(rgb(0xFF3D6FA8), rgb(0xFF8FB3D1), rgb(0xFFF2C27A), rgb(0xFFFFD27A), 0.45f, 0f), skyLook(6.0))
        assertEquals(SkyLook(rgb(0xFF24305E), rgb(0xFF6A5A8A), rgb(0xFFF08A5D), rgb(0xFFFF8C5A), 0.5f, 0f), skyLook(0.0))
        assertEquals(SkyLook(rgb(0xFF0D1A3D), rgb(0xFF33305E), rgb(0xFFD98A6A), rgb(0xFFFF8C5A), 0.42f, 0.5f), skyLook(-4.0))
        assertEquals(SkyLook(rgb(0xFF050A1F), rgb(0xFF0D1A3D), rgb(0xFF2A2F5E), rgb(0xFF6A4A7A), 0.15f, 0.9f), skyLook(-8.0))
    }

    private fun assertChannelsNear(expected: Long, actual: Int) {
        for (shift in listOf(16, 8, 0)) {
            val want = ((expected shr shift) and 0xFF).toInt()
            val got = (actual shr shift) and 0xFF
            assertTrue("channel $shift: expected $want, got $got", abs(want - got) <= 1)
        }
        assertEquals(0xFF, (actual ushr 24) and 0xFF)
    }

    @Test
    fun skyBlendsBetween() {
        val half = skyLook(-2.0)
        assertChannelsNear(0xFF19254E, half.top) // Halfway between #24305E and #0D1A3D, channel by channel.
        assertChannelsNear(0xFF4F4574, half.middle)
        assertChannelsNear(0xFFE58A64, half.horizon)
        assertEquals(0.46f, half.glowAlpha, 0.001f)
        assertEquals(0.25f, half.stars, 0.001f)
    }

    @Test
    fun skyClamps() {
        assertEquals(skyLook(15.0), skyLook(40.0))
        assertEquals(skyLook(-8.0), skyLook(-20.0))
    }

    // --- the weather in the sky ---

    @Test
    fun weatherKinds() {
        assertEquals(SkyWeather.CLEAR, skyWeather(0))
        assertEquals(SkyWeather.CLEAR, skyWeather(1))
        assertEquals(SkyWeather.CLOUDY, skyWeather(3))
        assertEquals(SkyWeather.CLOUDY, skyWeather(45))
        assertEquals(SkyWeather.RAIN, skyWeather(61))
        assertEquals(SkyWeather.RAIN, skyWeather(81))
        assertEquals(SkyWeather.RAIN, skyWeather(95))
        assertEquals(SkyWeather.SNOW, skyWeather(73))
        assertEquals(SkyWeather.SNOW, skyWeather(86))
    }

    @Test
    fun unknownCodeIsClear() = assertEquals(SkyWeather.CLEAR, skyWeather(42))

    @Test
    fun noWeatherIsClear() = assertEquals(SkyWeather.CLEAR, skyWeather(null))

    // --- the words on screen ---

    @Test
    fun clockTwelveHour() {
        assertEquals("7:48", saverClock(LocalTime.parse("19:48"), is24Hour = false))
        assertEquals("12:05", saverClock(LocalTime.parse("12:05"), is24Hour = false))
        assertEquals("12:30", saverClock(LocalTime.parse("00:30"), is24Hour = false))
    }

    @Test
    fun clockTwentyFourHour() {
        assertEquals("19:48", saverClock(LocalTime.parse("19:48"), is24Hour = true))
        assertEquals("7:05", saverClock(LocalTime.parse("07:05"), is24Hour = true)) // Like the corner clock (H:mm).
    }

    @Test
    fun dateLineWithWeather() {
        assertEquals("Monday, October 19 · ☁ 8° Cloudy", saverDateLine(LocalDate.parse("2026-10-19"), "☁ 8° Cloudy"))
    }

    @Test
    fun dateLineWithoutWeather() {
        assertEquals("Monday, October 19", saverDateLine(LocalDate.parse("2026-10-19"), null))
    }

    // --- the photos' drift ---

    @Test
    fun newPhotoEveryMinute() = assertEquals(60_000L, SAVER_PHOTO_MS)

    // At 1920 wide a photo moves a pixel or two a second, so 100 ms apart it has
    // moved a fraction of a pixel; whole-pixel steps showed as a twitch on the TV.
    @Test
    fun driftMovesByFractionsOfAPixel() {
        val a = slideDrift(id = 2, t = 0.5f, width = 1920f, height = 1080f)
        val b = slideDrift(id = 2, t = 0.5f + 100f / SAVER_PHOTO_MS, width = 1920f, height = 1080f)
        val moved = abs(b.left - a.left)
        assertTrue("moved $moved px", moved > 0f && moved < 0.5f)
        assertTrue(abs(b.scale - a.scale) > 0f)
    }

    @Test
    fun driftNeverShowsAnEdge() {
        for (id in 1..4) {
            for (step in 0..10) {
                val d = slideDrift(id, step / 10f, width = 1920f, height = 1080f)
                // At its far end the photo's edge sits exactly on the screen's (to within float error).
                assertTrue("id $id step $step: $d", d.left <= 0.01f && d.left + 1920f * d.scale >= 1919.99f)
                assertTrue("id $id step $step: $d", d.top <= 0.01f && d.top + 1080f * d.scale >= 1079.99f)
            }
        }
    }

    @Test
    fun driftZoomsInOrOut() {
        assertEquals(1.02f, slideDrift(2, 0f, 1920f, 1080f).scale, 0.0001f)
        assertEquals(1.08f, slideDrift(2, 1f, 1920f, 1080f).scale, 0.0001f)
        assertEquals(1.08f, slideDrift(1, 0f, 1920f, 1080f).scale, 0.0001f)
    }

    // The outgoing photo stays on screen through the next one's fade (and its loading),
    // so its drift must still be moving then, not stopped at the end.
    @Test
    fun driftStillMovingDuringTheNextFade() {
        assertEquals(0f, slideProgress(0L), 0f)
        val fadeDone = SAVER_PHOTO_MS + SAVER_FADE_MS
        assertTrue(slideProgress(fadeDone - 100) < slideProgress(fadeDone))
        assertTrue(slideProgress(fadeDone) < 1f)
    }

    @Test
    fun driftProgressStopsAtTheEnd() = assertEquals(1f, slideProgress(10 * SAVER_PHOTO_MS), 0f)
}
