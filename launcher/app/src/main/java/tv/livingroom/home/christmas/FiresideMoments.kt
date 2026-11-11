package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/*
 * The dogs' moments on Fireside's rug: every few minutes one of them does
 * something, then settles again. Each is a timeline: given how far into it we
 * are, where each dog is and how it stands. Plain Kotlin, so it runs in JVM tests.
 */

/** The three moments and how long each lasts, seconds. */
enum class Moment(val length: Double) {
    /** The small dog wakes, rolls onto his back, wriggles with his legs in the air, rolls back and settles. */
    ROLL(11.5),

    /**
     * The zoomies: the small dog stands and play-bows at the big one, who gets up and bows back, then tears round the rug twice
     * at a gallop while the small one bounces on the spot, skids to a stop, pants, and flops back down.
     */
    ZOOMIES(19.0),

    /** One of them runs in a dream: paws paddling, a muffled "wuf" or two. */
    DREAM(8.0),
}

/**
 * How a dog is at one instant: [awake] 0 asleep to 1 eyes open and head up; [up] 0 lying to 1 standing; [bow] its front end down;
 * [wag]; where it is ([x], [y]: the floor under its middle) and whether it faces right ([flip]); [walking] and how far along its
 * stride ([walk], in strides), at a [gallop]; [hop] up off the floor; [head] turned (radians); [pant]ing; [paddle], its paws
 * running in a dream; [roll], 0 lying to 1 on its back, and how hard it [wriggle]s there. Reused from frame to frame.
 */
class DogAct {
    var awake = 0f
    var up = 0f
    var bow = 0f
    var wag = 0f
    var x = 0f
    var y = 0f
    var flip = false
    var walking = false
    var walk = 0f
    var gallop = false
    var hop = 0f
    var head = 0f
    var pant = false
    var paddle = 0f
    var roll = 0f
    var wriggle = 0f

    /** Lying at [place], [awake] and wagging [wag], doing nothing else. */
    fun atHome(place: RugPlace, awake: Float, wag: Float) {
        this.awake = awake
        up = 0f
        bow = 0f
        this.wag = wag
        x = place.x
        y = place.y
        flip = place.facesRight
        walking = false
        walk = 0f
        gallop = false
        hop = 0f
        head = 0f
        pant = false
        paddle = 0f
        roll = 0f
        wriggle = 0f
    }
}

/** Where the ball is and how it's turned. */
class BallAct {
    var x = BALL_X
    var y = BALL_Y
    var turn = BALL_TURN

    fun atRest() {
        x = BALL_X
        y = BALL_Y
        turn = BALL_TURN
    }
}

/** How the ball lies at rest. */
const val BALL_TURN = 0.5f

/** Both dogs and the ball at one instant. */
class RugActs {
    val small = DogAct()
    val big = DogAct()
    val ball = BallAct()

    fun of(dog: Dog): DogAct = if (dog == Dog.BIG) big else small
}

/** 0 lying, 1 standing: up between [a] and [b], down again between [c] and [d], easing each way. */
internal fun getUp(v: Double, a: Double, b: Double, c: Double, d: Double): Float = smooth(seg(v, a, b)) * (1 - smooth(seg(v, c, d)))

private fun hump(v: Double, a: Double, b: Double): Float = sin(seg(v, a, b) * PI).toFloat()

/**
 * [moment] [v] seconds in, done by [dog] (the dream's dog; the others have their own), into [acts], whose dogs are already lying
 * at home ([DogAct.atHome]) and ball at rest.
 */
fun momentInto(moment: Moment, dog: Dog, v: Double, acts: RugActs) {
    when (moment) {
        Moment.ROLL -> rollOver(v, acts.small, acts.big)
        Moment.ZOOMIES -> zoomies(v, acts.small, acts.big)
        Moment.DREAM -> acts.of(dog).run {
            paddle = getUp(v, 0.8, 1.4, 5.6, 6.6)
            wag = getUp(v, 2.0, 2.4, 5.0, 5.6) * 0.8f
        }
    }
}

/** Which dog does [moment]: the small one rolls over, the big one has the zoomies; the dream is [either]'s. */
fun momentDog(moment: Moment, either: Dog): Dog = when (moment) {
    Moment.ROLL -> Dog.SMALL
    Moment.ZOOMIES -> Dog.BIG
    Moment.DREAM -> either
}

/** When the small dog goes over onto his back in [Moment.ROLL], and when he comes back, seconds in. */
const val ROLL_OVER_FROM = 1.5
const val ROLL_OVER_TO = 2.1
const val ROLL_BACK_FROM = 7.6
const val ROLL_BACK_TO = 8.2

private fun rollOver(v: Double, k: DogAct, m: DogAct) {
    k.awake = getUp(v, 0.0, 0.7, 9.2, 10.8)
    k.roll = getUp(v, ROLL_OVER_FROM, ROLL_OVER_TO, ROLL_BACK_FROM, ROLL_BACK_TO)
    k.wriggle = getUp(v, 2.0, 2.6, 6.8, 7.6)
    k.wag = getUp(v, 0.9, 1.3, 9.0, 10.0)
    k.head = 0.12f * hump(v, 0.7, 1.5) // A look up before he goes over.
    k.pant = v > ROLL_OVER_FROM && v < ROLL_BACK_TO
    // The big dog half wakes at the commotion, watches, and goes back to sleep.
    m.awake = getUp(v, 3.0, 3.6, 7.8, 9.4) * 0.8f
}

// The zoomies' laps: an oval round the rug (its middle, half-width and half-depth), wide enough to pass round the small dog's
// tail at the left and behind the presents at the right, run twice between ZOOM_FROM and ZOOM_TO.
private const val ZOOM_LAP_X = 935f
private const val ZOOM_LAP_Y = 966f
private const val ZOOM_LAP_RX = 375f
private const val ZOOM_LAP_RY = 62f
private const val ZOOM_LAPS = 2
const val ZOOM_FROM = 6.3
const val ZOOM_TO = 13.3

/** How far a galloping stride carries the big dog over the floor, in the room's units. */
private const val GALLOP_STRIDE = 250f

private fun zoomies(v: Double, k: DogAct, m: DogAct) {
    // The small dog: up, a play bow at the big one, then bouncing on the spot, turning to follow him round, and back to bed.
    k.awake = getUp(v, 0.0, 0.6, 16.6, 18.4)
    k.up = getUp(v, 0.7, 1.5, 15.0, 16.0)
    k.wag = getUp(v, 1.3, 1.7, 15.0, 15.8)
    k.bow = getUp(v, 1.9, 2.3, 3.4, 3.8) + getUp(v, 4.4, 4.7, 5.6, 6.0)
    k.hop = (if (k.bow > 0.9f) abs(sin(v * 9)).toFloat() * 6 else 0f) + bounces(v)
    k.pant = v > 7.0 && v < 15.0
    // The big one: wakes, gets up, bows back, then the laps, a skid, panting, and down.
    m.awake = getUp(v, 2.2, 2.8, 17.2, 18.8)
    m.up = getUp(v, 3.0, 3.9, 15.4, 16.4)
    m.wag = getUp(v, 3.6, 4.0, 15.6, 16.4)
    m.bow = getUp(v, 4.4, 4.8, 5.7, 6.1) + SKID_BRACE * getUp(v, ZOOM_TO - 0.5, ZOOM_TO - 0.2, ZOOM_TO + 0.2, ZOOM_TO + 0.6)
    m.pant = v > ZOOM_FROM && v < 16.2
    if (v > ZOOM_FROM && v < ZOOM_TO) lap(seg(v, ZOOM_FROM, ZOOM_TO), m)
    if (v > ZOOM_FROM - 0.2 && v < ZOOM_TO + 1.2) k.flip = m.x > k.x // Turning to keep him in sight.
}

/** How far the big dog braces into his skid at the end. */
private const val SKID_BRACE = 0.35f

/** The small dog's excited little bounces while the big one runs. */
private fun bounces(v: Double): Float {
    if (v <= ZOOM_FROM || v >= ZOOM_TO + 0.5) return 0f
    return abs(sin((v - ZOOM_FROM) * 7.5)).toFloat() * 9 * getUp(v, ZOOM_FROM, ZOOM_FROM + 0.4, ZOOM_TO, ZOOM_TO + 0.5)
}

/**
 * The big dog's laps, [u] of the way through them: from where he stands, easing into the oval, round it twice (behind the small
 * dog first, running left), and easing back to where he lies, slowing to a trot.
 */
internal fun lap(u: Float, m: DogAct) {
    val along = lapEase(u)
    val angle = START_ANGLE - along * ZOOM_LAPS * 2 * PI.toFloat()
    val ox = ZOOM_LAP_X + ZOOM_LAP_RX * cos(angle)
    val oy = ZOOM_LAP_Y + ZOOM_LAP_RY * sin(angle)
    // Into the oval from home, and out of it back home.
    val into = smooth((u / ZOOM_EDGE).coerceIn(0f, 1f))
    val out = smooth(((u - 1 + ZOOM_EDGE) / ZOOM_EDGE).coerceIn(0f, 1f))
    val hx = BIG_DOG_RUG.x
    val hy = BIG_DOG_RUG.y
    m.x = lerp(lerp(hx, ox, into), hx, out)
    m.y = lerp(lerp(hy, oy, into), hy, out)
    // Facing the way he's going round (running counter-clockwise, he heads right along the front), and home at the end.
    m.flip = out < 0.5f && sin(angle) > 0f
    m.walking = true
    m.gallop = out < 0.6f
    m.walk = ovalDistance(along * ZOOM_LAPS * 2 * PI.toFloat()) / GALLOP_STRIDE
    m.up = 1f
}

/** Where on the oval the laps start: a little up and right of the middle, near where the big dog lies. */
private const val START_ANGLE = -0.25f

/** The share of the laps spent getting into the oval at the start, and back out of it at the end. */
private const val ZOOM_EDGE = 0.12f

/**
 * How far along the oval he has run after turning through [swept] radians from where the laps start: added up in short
 * pieces, as he moves faster along the long sides than round the ends.
 */
private fun ovalDistance(swept: Float): Float {
    val pieces = (swept / OVAL_PIECE).toInt() + 1
    val step = swept / pieces
    var sum = 0f
    for (i in 0 until pieces) {
        val a = START_ANGLE - (i + 0.5f) * step
        sum += hypot(ZOOM_LAP_RX * sin(a), ZOOM_LAP_RY * cos(a)) * step
    }
    return sum
}

/** How finely [ovalDistance] adds up, radians: a lap is about a hundred pieces. */
private const val OVAL_PIECE = 0.06f

/** How far round the laps he is, [u] of the way through their time: slow off the mark, fast in the middle, slowing at the end. */
private fun lapEase(u: Float): Float = smooth(u.coerceIn(0f, 1f)) * 0.35f + u.coerceIn(0f, 1f) * 0.65f
