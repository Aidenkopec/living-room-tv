package tv.livingroom.home.screensaver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SaverTestSwitchTest {
    private val at = LocalTime.parse("23:29")

    @Test
    fun usedByTheNextSaver() {
        SaverTestSwitch.setForNextSaver(NextSaver(at, 1, christmas = true, weatherCode = 75), nowMs = 0)
        assertEquals(NextSaver(at, 1, christmas = true, weatherCode = 75), SaverTestSwitch.takeForNextSaver(nowMs = 14 * 60_000L))
    }

    @Test
    fun usedOnlyOnce() {
        SaverTestSwitch.setForNextSaver(NextSaver(at, 1), nowMs = 0)
        SaverTestSwitch.takeForNextSaver(nowMs = 1_000)
        assertEquals(NextSaver(), SaverTestSwitch.takeForNextSaver(nowMs = 2_000))
    }

    // Left armed with no screensaver soon after, it must not make a later one
    // think it's 23:29 and turn the TV off.
    @Test
    fun forgottenAfterFifteenMinutes() {
        SaverTestSwitch.setForNextSaver(NextSaver(at, 1, christmas = false), nowMs = 0)
        assertEquals(NextSaver(), SaverTestSwitch.takeForNextSaver(nowMs = 15 * 60_000L + 1))
    }
}
