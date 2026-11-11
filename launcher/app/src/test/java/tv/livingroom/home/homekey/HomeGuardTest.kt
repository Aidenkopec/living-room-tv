package tv.livingroom.home.homekey

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGuardTest {
    private val guard = HomeGuard(windowMs = 5_000, maxReopens = 3)

    @Test
    fun idleGuardNeverReopens() {
        // Google's home opened with no Home press before it (Explore, a test, the TV turning on).
        assertFalse(guard.shouldReopen(now = 1_000))
    }

    @Test
    fun googlesHomeRightAfterOurOpenIsSentBack() {
        // As logged on the TV: Google's launcher started itself again 430–800 ms after the press.
        guard.opened(now = 10_000)
        assertTrue(guard.shouldReopen(now = 10_430))
    }

    @Test
    fun guardEndsAfterItsWindow() {
        guard.opened(now = 10_000)
        assertTrue(guard.shouldReopen(now = 15_000))
        guard.opened(now = 20_000)
        assertFalse(guard.shouldReopen(now = 25_001))
    }

    @Test
    fun anyKeyEndsTheGuard() {
        // The user chose something (Explore opens Google's home on purpose, a long Home opens its side panel).
        guard.opened(now = 10_000)
        guard.keyPressed()
        assertFalse(guard.shouldReopen(now = 10_500))
    }

    @Test
    fun reopensAreCappedSoItNeverFightsForever() {
        guard.opened(now = 0)
        val answers = (1..5).map { guard.shouldReopen(now = it * 100L) }
        assertEquals(listOf(true, true, true, false, false), answers)
    }

    @Test
    fun aNewHomePressStartsAFreshGuard() {
        guard.opened(now = 0)
        repeat(3) { guard.shouldReopen(now = 100) }
        assertFalse(guard.shouldReopen(now = 200))
        guard.opened(now = 1_000)
        assertTrue(guard.shouldReopen(now = 1_400))
    }
}
