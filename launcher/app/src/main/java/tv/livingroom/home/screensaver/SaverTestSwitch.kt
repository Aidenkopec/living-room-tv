package tv.livingroom.home.screensaver

import android.os.SystemClock
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Test switches for the screensaver, set by [tv.livingroom.home.TestSwitchActivity] (see the
 * README's test switches): a pretend time and a shorter late-night turn-off, for the next
 * real screensaver only. Forgotten after 15 minutes, so a switch left set with
 * no screensaver soon after can't make a later one think it's late at night.
 */
object SaverTestSwitch {
    @Volatile private var nextAt: LocalTime? = null

    @Volatile private var nextOffAfterMin: Int? = null

    @Volatile private var setAtMs = 0L

    fun setForNextSaver(at: LocalTime?, offAfterMin: Int?, nowMs: Long = SystemClock.elapsedRealtime()) {
        nextAt = at
        nextOffAfterMin = offAfterMin
        setAtMs = nowMs
    }

    /** The next screensaver's pretend time and turn-off minutes (null: the real ones), used once. */
    fun takeForNextSaver(nowMs: Long = SystemClock.elapsedRealtime()): Pair<LocalTime?, Int?> {
        val taken = if (nowMs - setAtMs <= FORGET_AFTER_MS) nextAt to nextOffAfterMin else null to null
        nextAt = null
        nextOffAfterMin = null
        return taken
    }

    private const val FORGET_AFTER_MS = 15 * 60_000L
}

/** The screensaver's clock: the real time, or today at [at] and running on from there. */
fun saverClockFrom(at: LocalTime?): () -> ZonedDateTime {
    if (at == null) return { ZonedDateTime.now() }
    val start = ZonedDateTime.now().with(at)
    val startedMs = SystemClock.elapsedRealtime()
    return { start.plus(Duration.ofMillis(SystemClock.elapsedRealtime() - startedMs)) }
}
