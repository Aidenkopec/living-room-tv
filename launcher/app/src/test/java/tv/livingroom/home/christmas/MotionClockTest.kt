package tv.livingroom.home.christmas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionClockTest {
    private val second = 1_000_000_000L

    @Test
    fun theFirstFrameIsTimeZeroAndEachFrameAddsWhatPassed() {
        val clock = MotionClock()
        clock.frame(5 * second)
        assertEquals(0.0, clock.seconds, 0.0)
        clock.frame(5 * second + second / 60)
        assertEquals(1 / 60.0, clock.seconds, 1e-6)
    }

    @Test
    fun aStallMovesThingsOnATenthOfASecondAtMost() {
        val clock = MotionClock()
        clock.frame(second)
        clock.frame(3 * second)
        assertEquals(LONGEST_STEP.toDouble(), clock.seconds, 1e-6)
        assertEquals("Two seconds isn't away", 0, clock.returns)
    }

    @Test
    fun comingBackAfterAWhileIsCountedAndSeenOnceByEachWatcher() {
        val clock = MotionClock()
        val village = ReturnWatch()
        val dock = ReturnWatch()
        clock.frame(second)
        clock.frame(second + second / 60)
        assertFalse(village.cameBack(clock))
        clock.frame(second + (AWAY_SECONDS.toLong() + 5) * second)
        clock.frame(second + (AWAY_SECONDS.toLong() + 5) * second + second / 60)
        assertEquals(1, clock.returns)
        // A watcher that only looks a frame later still sees it, once.
        assertTrue(village.cameBack(clock))
        assertFalse(village.cameBack(clock))
        assertTrue(dock.cameBack(clock))
    }

    @Test
    fun aRestartedRideStandsOnTheSelectedTileWithTheOthersSnowy() {
        val ride = DockRide()
        val target = RideTarget().apply {
            focus = 1
            tiles = 3
            left = 300f
            top = 900f
            width = 200f
        }
        ride.step(0.0, target, false)
        target.focus = 2
        target.left = 520f
        for (frame in 1..120) ride.step(frame / 60.0, target, false)
        assertTrue("The tile it left is still bare", ride.caps[1] < 0.2f)
        ride.restart()
        ride.step(3600.0, target, false)
        assertEquals(listOf(1f, 1f, 0f), ride.caps.toList())
        assertFalse(ride.inAir)
        assertEquals(620f, ride.x, 0f)
    }
}
