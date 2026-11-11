package tv.livingroom.home.christmas

import android.view.KeyEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/** What a button on the remote does in Fireside. */
enum class FiresideKey {
    /** OK: the tree's next light show. */
    TREE,

    /** Up: harder snow outside. */
    SNOW,

    /** Down: a poke of the fire. */
    FIRE,

    /** Left: the blue present pops open. */
    PRESENT,

    /** Right: a gingerbread person walks along the mantel. */
    GINGERBREAD,
}

/** The Fireside action for [keyCode], or null for a key that wakes the TV (Back, Home, volume and everything else). */
fun firesideKey(keyCode: Int): FiresideKey? = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> FiresideKey.TREE
    KeyEvent.KEYCODE_DPAD_UP -> FiresideKey.SNOW
    KeyEvent.KEYCODE_DPAD_DOWN -> FiresideKey.FIRE
    KeyEvent.KEYCODE_DPAD_LEFT -> FiresideKey.PRESENT
    KeyEvent.KEYCODE_DPAD_RIGHT -> FiresideKey.GINGERBREAD
    else -> null
}

/**
 * Hands the remote's presses from the screensaver's window to Fireside while it's drawing. Fireside sets [catcher] while it
 * shows and clears it when it goes (including when it fails and the everyday screensaver takes over), so with no catcher every
 * key wakes the TV as it always has. Used on the main thread only.
 */
class FiresideRemote {
    var catcher: ((FiresideKey) -> Unit)? = null

    /** When Fireside last took a press ([android.os.SystemClock.elapsedRealtime]), or null if it hasn't. */
    var lastTakenMs: Long? = null
        private set

    /**
     * Whether the key [keyCode] is Fireside's to take (true: consumed; false: it should wake the TV). A press reaches the
     * catcher once, on its first key-down (at [nowMs], `SystemClock.elapsedRealtime`); the key's repeats and its key-up are taken quietly.
     */
    fun take(keyCode: Int, down: Boolean, repeat: Int, nowMs: Long): Boolean {
        val key = firesideKey(keyCode) ?: return false
        val catching = catcher ?: return false
        if (down && repeat == 0) {
            lastTakenMs = nowMs
            catching(key)
        }
        return true
    }
}

/**
 * The remote's presses in one showing of Fireside, by kind, stamped with the screensaver's clock: the last [KEPT] of each
 * (older ones no longer show). Written and read on the main thread.
 */
class FiresidePresses {
    private val times = Array(FiresideKey.entries.size) { DoubleArray(KEPT) }
    private val counts = IntArray(FiresideKey.entries.size)

    fun press(key: FiresideKey, t: Double) {
        val k = key.ordinal
        times[k][counts[k] % KEPT] = t
        counts[k]++
    }

    /** How many times [key] has been pressed. */
    fun count(key: FiresideKey): Int = counts[key.ordinal]

    /** When [key] was pressed [back] presses ago (0: the latest), or NaN if it wasn't (or that's too far back to keep). */
    fun at(key: FiresideKey, back: Int = 0): Double {
        val n = counts[key.ordinal]
        if (back >= n || back >= KEPT) return Double.NaN
        return times[key.ordinal][(n - 1 - back) % KEPT]
    }

    companion object {
        const val KEPT = 8
    }
}

/** The tree's light show after [presses] of OK: 1 (coloured twinkling, as it starts), then 2 (colours chasing up), then 0 (warm white). */
fun treeShow(presses: Int): Int = (1 + presses) % 3

/**
 * How much harder it snows outside at [t] after Up's [presses]: each press adds 1, up to 3, and it eases off by 0.07 a second.
 * The window's snow is [snowAmount] times `1 + 1.15 × this`.
 */
fun snowBoost(presses: FiresidePresses, t: Double): Float {
    var boost = 0.0
    var since = Double.NaN
    for (back in minOf(presses.count(FiresideKey.SNOW), FiresidePresses.KEPT) - 1 downTo 0) {
        val at = presses.at(FiresideKey.SNOW, back)
        if (at > t) break
        if (!since.isNaN()) boost = maxOf(0.0, boost - (at - since) * BOOST_EASE)
        boost = minOf(BOOST_MOST, boost + 1)
        since = at
    }
    if (since.isNaN()) return 0f
    return maxOf(0.0, boost - (t - since) * BOOST_EASE).toFloat()
}

/**
 * How much extra snow has fallen by [t] because of Up's [presses]: [snowBoost] added up over time (exactly: it eases off in
 * straight lines). The window's flakes fall this much further ahead of the clock, times [BOOST_FALL], so harder snow is also
 * faster snow, with nothing carried from frame to frame.
 */
fun snowBoostSoFar(presses: FiresidePresses, t: Double): Double {
    var boost = 0.0
    var since = Double.NaN
    var sum = 0.0
    for (back in minOf(presses.count(FiresideKey.SNOW), FiresidePresses.KEPT) - 1 downTo 0) {
        val at = presses.at(FiresideKey.SNOW, back)
        if (at > t) break
        if (!since.isNaN()) {
            sum += easedSum(boost, at - since)
            boost = maxOf(0.0, boost - (at - since) * BOOST_EASE)
        }
        boost = minOf(BOOST_MOST, boost + 1)
        since = at
    }
    if (!since.isNaN()) sum += easedSum(boost, t - since)
    return sum
}

/** The sum over [span] seconds of a boost starting at [boost] and easing off in a straight line to nothing. */
private fun easedSum(boost: Double, span: Double): Double {
    val left = boost - span * BOOST_EASE
    return if (left >= 0) (boost + left) / 2 * span else boost * boost / (2 * BOOST_EASE)
}

/** How much faster each step of [snowBoost] makes the snow fall, as the mockup has it. */
const val BOOST_FALL = 0.75

private const val BOOST_EASE = 0.07
private const val BOOST_MOST = 3.0

/** How the poked fire flares, [a] seconds after Down: up and back down over 3 s. The fire burns `1 + 0.9 × this` as strong. */
fun fireFlare(a: Double): Float = if (a >= 0 && a < FLARE_LENGTH) sin(a / FLARE_LENGTH * PI).toFloat().pow(0.6f) else 0f

const val FLARE_LENGTH = 3.0

/** The fire's flare at [t] after Down's [presses]: the strongest of the last few pokes', so a second poke never dims it. */
fun fireFlareAt(presses: FiresidePresses, t: Double): Float {
    var flare = 0f
    for (back in 0 until FLARES_KEPT) flare = maxOf(flare, fireFlare(t - presses.at(FiresideKey.FIRE, back)))
    return flare
}

/** How many of the latest pokes still count: three are as many as fit in a flare's three seconds by hand. */
const val FLARES_KEPT = 3

/**
 * When the run under way at [t] (or the latest one before it) of something that lasts [length] seconds started: [key]'s
 * presses, and [also] (another start, or NaN), each starting a run unless one is still going, so pressing again doesn't make
 * it jump back to its start. NaN if nothing has started.
 */
fun runStart(presses: FiresidePresses, key: FiresideKey, length: Double, t: Double, also: Double = Double.NaN): Double {
    var start = Double.NaN
    var extra = also
    var back = minOf(presses.count(key), FiresidePresses.KEPT) - 1
    while (true) {
        val pressed = if (back >= 0) presses.at(key, back) else Double.NaN
        // The next start in time order: the extra one if it comes first, else the next press.
        val at = if (!extra.isNaN() && (pressed.isNaN() || extra < pressed)) extra else pressed
        if (at.isNaN() || at > t) return start
        if (at == extra) extra = Double.NaN else back--
        if (start.isNaN() || at - start >= length) start = at
    }
}

/** How far open the blue present is [a] seconds after it popped: open in a quarter second, shut again by 3.4 s. */
fun presentOpen(a: Double): Float =
    if (a >= 0 && a < POP_LENGTH) smooth((a / 0.25).toFloat()) * smooth(((POP_LENGTH - a) / 0.4).toFloat()) else 0f

const val POP_LENGTH = 3.4

/** How long the gingerbread person takes along the mantel, seconds. */
const val GINGER_LENGTH = 9.0

/**
 * Where the gingerbread person is [a] seconds after setting off along the mantel (0 to [GINGER_LENGTH]): its x, its stride, and
 * whether it's stopped to wave (in the middle), into [into] as x, walk, wave (0 or 1). False when it isn't on the mantel.
 */
fun gingerbreadAt(a: Double, into: FloatArray): Boolean {
    if (!(a >= 0 && a < GINGER_LENGTH)) return false // NaN (never pressed) included.
    val u = (a / GINGER_LENGTH).toFloat()
    val waving = abs(u - 0.5f) < 0.1f
    val along = if (waving) {
        0.5f
    } else if (u < 0.5f) {
        u / 0.8f
    } else {
        (u - 0.2f) / 0.8f
    }
    into[0] = lerp(770f, 1232f, along)
    into[1] = if (waving) 0f else (a * 3.2).toFloat()
    into[2] = if (waving) 1f else 0f
    return true
}
