package tv.livingroom.home.christmas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlin.math.min

/**
 * The time that moving things follow. It advances once per frame while the screen is started (Compose
 * stops handing out frames to a stopped activity, so nothing is drawn behind another app or the
 * screensaver) and picks up where it left off afterwards. Read it only while drawing: a read there
 * redraws that layer on the next frame without recomposing or laying anything out again.
 */
@Stable
class MotionClock internal constructor() {
    /** Seconds of motion so far. A long frame counts as [LONGEST_STEP] at most, so a stall slows things instead of making them jump. */
    var seconds by mutableDoubleStateOf(0.0)
        private set

    /**
     * How many times the screen has come back after being away more than [AWAY_SECONDS]. A count, not
     * the length of one frame, so a layer that missed the first frame back still sees it.
     */
    var returns = 0
        private set

    private var last = 0L

    internal fun frame(nanos: Long) {
        val passed = if (last == 0L) 0f else (nanos - last) / NANOS
        last = nanos
        if (passed > AWAY_SECONDS) returns++
        seconds += min(passed, LONGEST_STEP).toDouble()
    }
}

/**
 * Watches a [MotionClock] for the screen coming back after a while away: the people who were out
 * then and the snow the dog left aren't carried over into a different hour.
 */
class ReturnWatch {
    private var seen = 0

    /** True once for each return since this was last asked. */
    fun cameBack(clock: MotionClock): Boolean = (clock.returns != seen).also { seen = clock.returns }
}

/** How long the screen has to have been away, in seconds, for what moves to start afresh. */
const val AWAY_SECONDS = 30f

/** A [MotionClock] that runs for as long as the caller is on screen, moving on every [frameStep] frames (1: every frame). */
@Composable
fun rememberMotionClock(frameStep: Int = 1): MotionClock {
    val clock = remember { MotionClock() }
    LaunchedEffect(clock, frameStep) {
        var n = 0
        while (true) withFrameNanos { if (n++ % frameStep == 0) clock.frame(it) }
    }
    return clock
}

/** The longest a single frame may move things, in seconds. */
const val LONGEST_STEP = 0.1f
private const val NANOS = 1e9f
