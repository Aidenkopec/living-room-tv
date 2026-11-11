package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The dog that rides the dock, and the snow under it. It bounds to whichever
 * tile is selected, lands, and stands there wagging; now and then it bows or
 * looks round. Where it lands, the tile's snow breaks off in clumps and
 * powder, and settles back slowly on the tile it left. Plain Kotlin, advanced
 * once per frame; lengths are in screen pixels.
 */

/**
 * Where every tile is on a frame: its top left corner in screen pixels ([left], [top]), how much of
 * its tallest size it is ([scale]) and how wide it is at its tallest ([tallest], the dressing's
 * units); and which tile is selected.
 */
class TileSpots {
    var count = 0
        private set
    var left = FloatArray(0)
        private set
    var top = FloatArray(0)
        private set
    var scale = FloatArray(0)
        private set
    var tallest = FloatArray(0)
        private set
    var focused = -1

    /** Makes room for [tiles] tiles; the caller then fills in each one's numbers. */
    fun resize(tiles: Int) {
        if (left.size < tiles) {
            left = FloatArray(tiles)
            top = FloatArray(tiles)
            scale = FloatArray(tiles)
            tallest = FloatArray(tiles)
        }
        count = tiles
    }
}

/** The selected tile on this frame: which one it is of how many, and its top edge on screen ([left], [top], [width]). */
class RideTarget {
    var focus = -1
    var tiles = 0
    var left = 0f
    var top = 0f
    var width = 0f

    /** Screen pixels to each of the dressing's units (pixels of a 1920 px wide screen). */
    var unit = 1f

    /** Points this at the selected tile of [spots], on a screen with [unit] pixels to a unit. False, and unchanged, if none is selected. */
    fun aim(spots: TileSpots, unit: Float): Boolean {
        val focused = spots.focused
        if (focused !in 0 until spots.count) return false
        focus = focused
        tiles = spots.count
        left = spots.left[focused]
        top = spots.top[focused]
        width = spots.tallest[focused] * unit * spots.scale[focused]
        this.unit = unit
        return true
    }
}

/** The rider and the dock's snow from frame to frame. */
class DockRide(seed: Int = 5) {
    /** Where its feet are (when not in the air), and how high above that it is in a bound. */
    var x = 0f
        private set
    var y = 0f
        private set
    var height = 0f
        private set
    var facingRight = true
        private set

    /** Whether it's between tiles; [sinceLanding] is seconds since it last landed (negative until it has). */
    var inAir = false
        private set
    var sinceLanding = 0f
        private set

    /** How deep the snow lies on each tile, 0 to 1. */
    var caps = FloatArray(0)
        private set

    /** The clumps and powder thrown up by landings. */
    val bits = SnowBits()

    private val random = Mulberry(seed)
    private var focus = -1
    private var fromX = 0f
    private var fromY = 0f
    private var leftAt = 0.0
    private var fromHeight = 0f // How high it was when a new tile was picked mid-bound: it comes down from there.
    private var landed = true
    private var last = Double.NaN

    /** Moves everything on to [t] seconds, with the selected tile at [target]; the snow settles back faster while it's [snowing]. */
    fun step(t: Double, target: RideTarget, snowing: Boolean) {
        val toX = target.left + target.width / 2
        val toY = target.top - RING * target.unit
        if (focus < 0 || caps.size != target.tiles) arrive(t, target, toX, toY)
        if (target.focus != focus) {
            // Off again, from wherever it is. Already in the air, it carries on from its height without
            // crouching: with quick presses it would otherwise drop to the dock between tiles each time.
            val airborne = inAir
            fromX = x
            fromY = y
            fromHeight = if (airborne) height else 0f
            leftAt = if (airborne) t - CROUCH_SECONDS else t
            facingRight = toX >= fromX
            focus = target.focus
            landed = false
        }
        val dt = if (last.isNaN()) 0f else (t - last).toFloat().coerceIn(0f, LONGEST_STEP)
        last = t
        val u = (((t - leftAt) - CROUCH_SECONDS) / (HOP_SECONDS - CROUCH_SECONDS)).toFloat().coerceIn(0f, 1f)
        val eased = smooth(u)
        x = lerp(fromX, toX, eased)
        y = lerp(fromY, toY, eased)
        inAir = u < 1f && (u > 0f || fromHeight > 0f)
        val arc = sin(u * PI.toFloat()) * (HOP_HEIGHT * target.unit + abs(toX - fromX) * 0.08f)
        height = if (inAir) arc + fromHeight * (1 - eased) else 0f
        sinceLanding = (t - leftAt - HOP_SECONDS).toFloat()
        if (u >= 1f && !landed) {
            landed = true
            bits.burst(random, t, target, caps.getOrElse(focus) { 0f }, facingRight)
        }
        settle(dt, snowing)
    }

    /** Starts afresh on the next step (the screen was away a while): standing on the selected tile, the others snowy again. */
    fun restart() {
        focus = -1
    }

    /** The first frame, or the row changed: it's simply standing on the selected tile, whose snow is gone. */
    private fun arrive(t: Double, target: RideTarget, toX: Float, toY: Float) {
        focus = target.focus
        caps = FloatArray(target.tiles) { if (it == focus) 0f else 1f }
        x = toX
        y = toY
        fromX = toX
        fromY = toY
        fromHeight = 0f
        leftAt = t - HOP_SECONDS - 1
        landed = true
    }

    /** Snow goes from under its feet in a tenth of a second, and settles back everywhere else over half a minute. */
    private fun settle(dt: Float, snowing: Boolean) {
        val back = dt * (if (snowing) 2 * SETTLE_RATE else SETTLE_RATE)
        for (i in caps.indices) {
            caps[i] = if (i == focus && landed) max(0f, caps[i] - dt * MELT_RATE) else min(1f, caps[i] + back)
        }
    }
}

/** How much of its full height a tile's snow is drawn at when it's [amount] deep (0 to 1); under [CAP_LEAST] it isn't drawn. */
fun capDepth(amount: Float): Float = 0.21f + 0.79f * amount

const val CAP_LEAST = 0.04f

/**
 * Bits of snow in the air: clumps that tumble and fall, and powder that puffs up and hangs. Each is where
 * it started, how fast it left, its size (in the dressing's units) and when; where it is now follows
 * from the time ([into]).
 */
class SnowBits(val capacity: Int = 96) {
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val size = FloatArray(capacity)
    private val spin = FloatArray(capacity)
    private val unit = FloatArray(capacity) // Screen pixels to each of the dressing's units, when it was thrown up.
    private val life = FloatArray(capacity)
    private val born = DoubleArray(capacity) { Double.NEGATIVE_INFINITY }
    private val powder = BooleanArray(capacity)
    private var next = 0

    /** How many bits are still in the air at [t]. */
    fun alive(t: Double): Int = (0 until capacity).count { t - born[it] < life[it] }

    /**
     * A landing at [t] on [target]'s tile, whose snow was [amount] deep: the deeper, the more and bigger
     * the clumps. They break off toward both ends of the tile, thrown a little the way the rider was going.
     */
    fun burst(random: Mulberry, t: Double, target: RideTarget, amount: Float, movingRight: Boolean) {
        val unit = target.unit
        repeat((3 + 7 * amount).roundToInt()) {
            val across = random.next()
            val i = take(t, powder = false, unit)
            x[i] = target.left + target.width * across
            y[i] = target.top - (6 + random.next() * 8 * amount) * unit
            vx[i] = ((if (across < 0.5f) -1 else 1) * (30 + random.next() * 90) + (if (movingRight) 30 else -30)) * unit
            vy[i] = (-70 - random.next() * 120) * unit
            size[i] = 3 + random.next() * 5 * (0.4f + amount)
            spin[i] = (random.next() - 0.5f) * 9
            life[i] = CLUMP_LIFE
        }
        repeat((10 + 14 * amount).roundToInt()) {
            val i = take(t, powder = true, unit)
            x[i] = target.left + target.width * random.next()
            y[i] = target.top - 4 * unit
            vx[i] = (random.next() - 0.5f) * 150 * unit
            vy[i] = (-30 - random.next() * 110) * unit
            size[i] = 1.2f + random.next() * 1.8f
            life[i] = 0.9f + random.next() * 0.5f
        }
    }

    /** Adds every bit still in the air at [t] to [quads]: clumps as turned copies of [clump], powder as fading copies of [dust]. */
    fun into(quads: SpriteQuads, clump: Sprite, dust: Sprite, t: Double) {
        for (i in 0 until capacity) {
            val age = (t - born[i]).toFloat()
            if (age < 0f || age >= life[i]) continue
            if (powder[i]) {
                // It shoots out, slows as if through air, and sinks a little.
                val reach = (1 - exp(-age * POWDER_DRAG)) / POWDER_DRAG * 1.6f
                val alpha = 0.85f * (1 - age / life[i])
                quads.add(
                    dust,
                    x[i] + vx[i] * reach,
                    y[i] + vy[i] * reach + POWDER_SINK * unit[i] * age * age,
                    size[i] / POWDER_PAINTED,
                    alpha,
                )
            } else {
                quads.addTurned(
                    clump,
                    x[i] + vx[i] * age,
                    y[i] + vy[i] * age + GRAVITY * unit[i] * age * age,
                    size[i] / CLUMP_PAINTED,
                    spin[i] * age,
                )
            }
        }
    }

    private fun take(t: Double, powder: Boolean, unit: Float): Int {
        val i = next
        next = (next + 1) % capacity
        born[i] = t
        this.powder[i] = powder
        this.unit[i] = unit
        return i
    }
}

/** A clump's picture is painted for a clump this big (its radius before it's squashed into an oval), and powder's disc this big. */
const val CLUMP_PAINTED = 10f
const val POWDER_PAINTED = 3f

/** A clump of snow for the dock's sheet, about (0, 0): an oval with its shadow a little below and to the right. */
fun clumpMarks(colours: CapColours): List<Mark> = Sketch().apply {
    oval(colours.shadow, Ellipse(1f, 1.5f, CLUMP_PAINTED * 1.25f, CLUMP_PAINTED * 0.85f))
    oval(colours.snow, Ellipse(0f, 0f, CLUMP_PAINTED * 1.25f, CLUMP_PAINTED * 0.85f))
}.marks

/** A speck of powder for the dock's sheet, about (0, 0). */
fun powderMarks(colours: CapColours): List<Mark> = Sketch().apply { disc(colours.snow, 0f, 0f, POWDER_PAINTED) }.marks

/** Adds the snow on every tile of [spots] to [quads], each [amounts] deep (1 if it has no number yet), squashed toward the tile's edge. */
fun dockSnowInto(quads: SpriteQuads, pieces: DockPieces, spots: TileSpots, amounts: FloatArray) {
    for (i in 0 until spots.count) {
        val amount = amounts.getOrElse(i) { 1f }
        val cap = pieces.caps.getOrNull(i)
        if (cap == null || amount < CAP_LEAST || !capFits(pieces.capWidths[i], spots.tallest[i])) continue
        quads.addStretched(cap, spots.left[i], spots.top[i], spots.scale[i], capHeightScale(spots.scale[i]) * capDepth(amount))
    }
}

/** Adds the sprig of holly on each top corner of [bar], on whole pixels. */
fun dockHollyInto(quads: SpriteQuads, pieces: DockPieces, bar: DockBar, unit: Float) {
    val y = onPixel(bar.top + HOLLY_DOWN * unit)
    quads.add(pieces.hollyLeft, onPixel(bar.left + HOLLY_IN * unit), y)
    quads.add(pieces.hollyRight, onPixel(bar.left + bar.width - HOLLY_IN * unit), y)
}

/** Adds the rider at [t] seconds to [quads]: its shadow while it stands, and the frame it shows, facing the way it last went. */
fun riderInto(quads: SpriteQuads, frames: RiderFrames<Sprite>, ride: DockRide, t: Double, unit: Float) {
    val feet = ride.y + RIDER_SINK * unit
    if (!ride.inAir) quads.add(frames.shadow, ride.x, feet, unit)
    quads.addFacing(riderSprite(frames, ride, t), onPixel(ride.x), onPixel(feet - ride.height), flipped = ride.facingRight, scale = unit)
}

/**
 * Which of the rider's frames shows at [t] seconds: bounding through the air, landing from a crouch,
 * then standing and wagging, with a bow and a look round at set moments of every [IDLE_LOOP] seconds.
 */
fun riderSprite(frames: RiderFrames<Sprite>, ride: DockRide, t: Double): Sprite {
    val since = ride.sinceLanding
    if (ride.inAir) return frames.air[step(t * AIR_STRIDES, frames.air.size)]
    if (since < LANDING_SECONDS) {
        return frames.landing[(since / LANDING_SECONDS * frames.landing.size).toInt().coerceIn(0, frames.landing.size - 1)]
    }
    val settled = since > SETTLED_AFTER
    val wag = sin(t * WAG_RATE + 2).toFloat() * (if (settled) 1f else 0.3f)
    val side = if (wag < 0f) 0 else 1
    val loop = since.mod(IDLE_LOOP)
    if (settled && loop > BOW_FROM && loop < BOW_FROM + BOW_FOR) {
        val depth = (sin((loop - BOW_FROM) / BOW_FOR * PI.toFloat()) * RIDER_DEPTHS).roundToInt()
        if (depth > 0) return frames.bows[depth - 1][side]
    }
    if (settled && loop > LOOK_FROM && loop < LOOK_FROM + LOOK_FOR) {
        val depth = (sin((loop - LOOK_FROM) / LOOK_FOR * PI.toFloat()) * RIDER_DEPTHS).roundToInt()
        if (depth > 0) return frames.looks[depth - 1][side]
    }
    return frames.wag[((wag + 1) * 2).roundToInt().coerceIn(0, frames.wag.size - 1)]
}

/** A bound takes this long from the selection changing: a short crouch, then through the air. */
const val HOP_SECONDS = 0.36
private const val CROUCH_SECONDS = 0.05
private const val HOP_HEIGHT = 40f // How high a bound goes, plus a little for a long one.
private const val RING = 5f // The selected tile's ring stands this far above the tile: the rider stands on it.
private const val MELT_RATE = 9f // Snow under its feet: gone in a ninth of a second.
private const val SETTLE_RATE = 0.03f // Snow settling back: a full cap in about half a minute, twice as fast in real snow.
private const val CLUMP_LIFE = 1.3f
private const val GRAVITY = 560f
private const val POWDER_DRAG = 3.2f
private const val POWDER_SINK = 46f
private const val LANDING_SECONDS = 0.18f
private const val SETTLED_AFTER = 0.3f
private const val IDLE_LOOP = 9.5f
private const val LOOK_FROM = 2f
private const val LOOK_FOR = 1.2f
private const val BOW_FROM = 5.2f
private const val BOW_FOR = 1.4f
private const val AIR_STRIDES = 4.0 // Leg cycles a second while bounding.
private const val HOLLY_IN = 7f // The holly's berries, this far in from the bar's end...
private const val HOLLY_DOWN = 5f // ...and this far below its top.
private const val RIDER_SINK = 3f // The rider's feet sit this far into what it stands on.
