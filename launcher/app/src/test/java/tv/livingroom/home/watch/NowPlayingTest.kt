package tv.livingroom.home.watch

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingTest {
    private val updated = 1_000_000L // When the app last reported, on the TV's uptime clock.
    private val minuteLater = updated + 60_000

    @Test
    fun pausedKeepsPosition() {
        val paused = PlayState(playing = false, positionMs = 780_000, updatedElapsedMs = updated, speed = 0f)
        assertEquals(780_000L, positionAt(paused, minuteLater, durationMs = 2_880_000))
    }

    @Test
    fun playingMovesOn() {
        val playing = PlayState(playing = true, positionMs = 780_000, updatedElapsedMs = updated, speed = 1f)
        assertEquals(840_000L, positionAt(playing, minuteLater, durationMs = 2_880_000))
    }

    @Test
    fun neverPastTheEnd() {
        val playing = PlayState(playing = true, positionMs = 780_000, updatedElapsedMs = updated, speed = 1f)
        assertEquals(800_000L, positionAt(playing, minuteLater, durationMs = 800_000))
    }

    @Test
    fun neverBelowZero() {
        val rewinding = PlayState(playing = true, positionMs = 30_000, updatedElapsedMs = updated, speed = -1f)
        assertEquals(0L, positionAt(rewinding, minuteLater, durationMs = 2_880_000))
    }

    @Test
    fun unknownLengthNotCapped() {
        val playing = PlayState(playing = true, positionMs = 780_000, updatedElapsedMs = updated, speed = 1f)
        assertEquals(840_000L, positionAt(playing, minuteLater, durationMs = null))
    }

    @Test
    fun unknownUpdateTimeDoesNotMove() {
        // 0 means the app didn't say when; moving on from 0 would add the TV's whole uptime.
        val playing = PlayState(playing = true, positionMs = 780_000, updatedElapsedMs = 0, speed = 1f)
        assertEquals(780_000L, positionAt(playing, minuteLater, durationMs = null))
    }

    @Test
    fun reportTimeOnTheWallClock() {
        // Reported a minute ago on the uptime clock: a minute before now on the wall clock.
        assertEquals(1_800_000_000_000L - 60_000, wallTimeOf(updated, nowElapsedMs = minuteLater, nowWallMs = 1_800_000_000_000L))
    }

    @Test
    fun unknownReportTimeStaysUnknown() {
        assertEquals(0L, wallTimeOf(0, nowElapsedMs = minuteLater, nowWallMs = 1_800_000_000_000L))
    }
}
