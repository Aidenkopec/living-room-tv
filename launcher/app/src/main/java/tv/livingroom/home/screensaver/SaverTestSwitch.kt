package tv.livingroom.home.screensaver

import android.os.SystemClock
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import tv.livingroom.home.christmas.AskedMoment

/**
 * What the next real screensaver is to pretend: its time, its late-night turn-off minutes, Fireside on or off, its weather
 * (null: the real ones), and Fireside's Santa coming [santa] seconds in or a dog [moment] soon.
 */
data class NextSaver(
    val at: LocalTime? = null,
    val offAfterMin: Int? = null,
    val christmas: Boolean? = null,
    val weatherCode: Int? = null,
    val santa: Int? = null,
    val moment: AskedMoment? = null,
)

/**
 * Test switches for the screensaver, set by [tv.livingroom.home.TestSwitchActivity] (see the
 * README's test switches): a pretend time, a shorter late-night turn-off and Fireside on or off,
 * for the next real screensaver only. Forgotten after 15 minutes, so a switch left set with
 * no screensaver soon after can't make a later one think it's late at night.
 */
object SaverTestSwitch {
    @Volatile private var next = NextSaver()

    @Volatile private var setAtMs = 0L

    fun setForNextSaver(next: NextSaver, nowMs: Long = SystemClock.elapsedRealtime()) {
        this.next = next
        setAtMs = nowMs
    }

    /** What the next screensaver is to pretend, used once. */
    fun takeForNextSaver(nowMs: Long = SystemClock.elapsedRealtime()): NextSaver {
        val taken = if (nowMs - setAtMs <= FORGET_AFTER_MS) next else NextSaver()
        next = NextSaver()
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
