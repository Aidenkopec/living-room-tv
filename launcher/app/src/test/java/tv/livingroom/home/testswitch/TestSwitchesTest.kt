package tv.livingroom.home.testswitch

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.livingroom.home.christmas.AskedMoment
import tv.livingroom.home.christmas.Dog
import tv.livingroom.home.christmas.Moment
import tv.livingroom.home.settings.HomeLayout

class TestSwitchesTest {
    @Test
    fun noExtrasIsNoSwitch() {
        assertEquals(TestSwitches(home = null, saver = null), parseTestSwitches(emptyMap()))
    }

    @Test
    fun homeSwitchesAreRead() {
        val switches = parseTestSwitches(
            mapOf(EXTRA_AT to "18:04", EXTRA_WEATHER to 71, EXTRA_SAMPLE_SHOWS to 12, EXTRA_LAYOUT to "sky"),
        )
        assertEquals(
            HomeOverrides(at = LocalTime.of(18, 4), weatherCode = 71, sampleShows = 12, layout = HomeLayout.LIVING_SKY),
            switches.home,
        )
        assertNull(switches.saver)
    }

    @Test
    fun zeroSampleShowsIsASwitchToo() {
        assertEquals(HomeOverrides(sampleShows = 0), parseTestSwitches(mapOf(EXTRA_SAMPLE_SHOWS to 0)).home)
    }

    @Test
    fun outOfRangeOrWrongTypeIsIgnored() {
        val switches = parseTestSwitches(
            mapOf(EXTRA_HOUR to 24, EXTRA_AT to "6 PM", EXTRA_WEATHER to "71", EXTRA_SAMPLE_SHOWS to -1, EXTRA_LAYOUT to "shelf"),
        )
        assertNull(switches.home)
    }

    @Test
    fun christmasSwitchesAreRead() {
        val switches = parseTestSwitches(mapOf(EXTRA_CHRISTMAS to "on", EXTRA_DATE to "2026-12-24"))
        assertEquals(HomeOverrides(christmas = true, date = LocalDate.of(2026, 12, 24)), switches.home)
        assertEquals(HomeOverrides(christmas = false), parseTestSwitches(mapOf(EXTRA_CHRISTMAS to "off")).home)
    }

    @Test
    fun extraMovingPiecesAreRead() {
        assertEquals(HomeOverrides(sprites = 200), parseTestSwitches(mapOf(EXTRA_SPRITES to 200)).home)
        assertEquals(HomeOverrides(sprites = 0), parseTestSwitches(mapOf(EXTRA_SPRITES to 0)).home)
        assertNull(parseTestSwitches(mapOf(EXTRA_SPRITES to MAX_TEST_SPRITES + 1)).home)
        assertNull(parseTestSwitches(mapOf(EXTRA_SPRITES to "200")).home)
    }

    @Test
    fun christmasSwitchesThatCantBeReadAreIgnored() {
        assertNull(parseTestSwitches(mapOf(EXTRA_CHRISTMAS to "yes", EXTRA_DATE to "December 24")).home)
        assertNull(parseTestSwitches(mapOf(EXTRA_CHRISTMAS to true, EXTRA_DATE to 20261224)).home)
        assertNull(onOffSwitch(null))
    }

    @Test
    fun villageShowsWhenTheSettingOrASwitchSaysSo() {
        assertEquals(true, showsChristmas(null, saved = true))
        assertEquals(false, showsChristmas(null, saved = false))
        assertEquals(true, showsChristmas(HomeOverrides(hour = 11), saved = true)) // A pretend time alone changes nothing.
        assertEquals(true, showsChristmas(HomeOverrides(christmas = true), saved = false))
        assertEquals(false, showsChristmas(HomeOverrides(christmas = false), saved = true))
    }

    @Test
    fun aSwitchThatNamesALayoutShowsThatLayout() {
        assertEquals(false, showsChristmas(HomeOverrides(layout = HomeLayout.LIVING_SKY), saved = true))
        assertEquals(false, showsChristmas(HomeOverrides(layout = HomeLayout.MOUNTAINS), saved = true))
        assertEquals(true, showsChristmas(HomeOverrides(layout = HomeLayout.MOUNTAINS, christmas = true), saved = false))
    }

    @Test
    fun layoutNamesEitherScreen() {
        assertEquals(HomeLayout.MOUNTAINS, layoutSwitch("dock"))
        assertEquals(HomeLayout.LIVING_SKY, layoutSwitch("sky"))
        assertNull(layoutSwitch(null))
        assertNull(layoutSwitch("shelf"))
    }

    @Test
    fun screensaverPreview() {
        val switches = parseTestSwitches(mapOf(EXTRA_SAVER_AT to "23:29", EXTRA_SAVER_WEATHER to 73))
        assertEquals(SaverOverrides(at = LocalTime.of(23, 29), weatherCode = 73), switches.saver)
        assertNull(switches.home)
    }

    @Test
    fun screensaverNextOnlyWithShortTurnOff() {
        val saver = parseTestSwitches(mapOf(EXTRA_SAVER_AT to "23:29", EXTRA_SAVER_OFF_MIN to 1, EXTRA_SAVER_NEXT to true)).saver
        assertEquals(SaverOverrides(at = LocalTime.of(23, 29), offAfterMin = 1, nextOnly = true), saver)
    }

    @Test
    fun santaAndADogMomentCanBeAskedFor() {
        val santa = parseTestSwitches(mapOf(EXTRA_SAVER_AT to "18:30", EXTRA_SAVER_SANTA to 5)).saver
        assertEquals(SaverOverrides(at = LocalTime.of(18, 30), santa = 5), santa)
        val dream = parseTestSwitches(mapOf(EXTRA_SAVER_AT to "18:30", EXTRA_SAVER_MOMENT to "dream", EXTRA_SAVER_DOG to "big")).saver
        assertEquals(AskedMoment(Moment.DREAM, Dog.BIG), dream?.moment)
        assertEquals(
            AskedMoment(Moment.ZOOMIES, null),
            parseTestSwitches(mapOf(EXTRA_SAVER_AT to "18:30", EXTRA_SAVER_MOMENT to "Zoomies")).saver?.moment,
        )
        assertNull(parseTestSwitches(mapOf(EXTRA_SAVER_AT to "18:30", EXTRA_SAVER_MOMENT to "nap")).saver?.moment)
        assertNull(parseTestSwitches(mapOf(EXTRA_SAVER_AT to "18:30", EXTRA_SAVER_SANTA to 4000)).saver?.santa)
        assertEquals("dream" to "small", momentValues(AskedMoment(Moment.DREAM, Dog.SMALL)))
        assertEquals(AskedMoment(Moment.DREAM, Dog.SMALL), momentSwitch("dream", "small"))
        assertEquals(AskedMoment(Moment.ROLL, null), momentSwitch("roll", null))
        assertEquals(null to null, momentValues(null))
    }

    @Test
    fun screensaverChristmasOnOrOff() {
        val on = parseTestSwitches(mapOf(EXTRA_SAVER_AT to "18:30", EXTRA_SAVER_CHRISTMAS to "on")).saver
        assertEquals(SaverOverrides(at = LocalTime.of(18, 30), christmas = true), on)
        assertEquals(false, parseTestSwitches(mapOf(EXTRA_SAVER_AT to "18:30", EXTRA_SAVER_CHRISTMAS to "off")).saver?.christmas)
        assertNull(parseTestSwitches(mapOf(EXTRA_SAVER_AT to "18:30", EXTRA_SAVER_CHRISTMAS to "yes")).saver?.christmas)
        assertEquals("on", onOffValue(true))
        assertEquals("off", onOffValue(false))
        assertNull(onOffValue(null))
    }

    @Test
    fun screensaverSwitchesNeedItsTime() {
        assertNull(parseTestSwitches(mapOf(EXTRA_SAVER_WEATHER to 73, EXTRA_SAVER_NEXT to true)).saver)
        // A time that can't be read still opens the preview, showing now.
        assertEquals(SaverOverrides(at = null), parseTestSwitches(mapOf(EXTRA_SAVER_AT to "late")).saver)
    }

    @Test
    fun homeSwitchIsTakenOnce() {
        HomeTestSwitch.arm(HomeOverrides(hour = 11))
        assertEquals(HomeOverrides(hour = 11), HomeTestSwitch.take())
        assertNull(HomeTestSwitch.take())
    }
}
