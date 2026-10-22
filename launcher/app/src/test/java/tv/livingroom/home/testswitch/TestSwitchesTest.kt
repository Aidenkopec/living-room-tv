package tv.livingroom.home.testswitch

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
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
