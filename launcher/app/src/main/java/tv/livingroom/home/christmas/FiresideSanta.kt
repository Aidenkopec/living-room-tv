package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/*
 * Santa's visit to Fireside: about half a minute from the sleigh climbing past
 * the window to the fire relighting. Here are his pieces, drawn front on in his
 * own units (about 300 tall, (0, 0) between his boots), and the timeline of
 * where he is and how he stands at each instant, ported number for number from
 * the mockup's santaPose. Plain Kotlin, so it runs in JVM tests.
 */

/** The scale Santa's pieces are painted at: his size on the hearth, the biggest he's drawn. */
const val SANTA_PAINTED = 1.12f

/** How long a visit lasts, seconds, from the sleigh's climb to its crossing the window again. */
const val VISIT_LENGTH = 34.0

/** Where Santa stands and how, at one instant of the visit. Reused from frame to frame. */
class SantaPose {
    var x = 0f
    var y = 0f
    var s = 0f
    var inside = true
    var hat = true
    var soot = 0f
    var squash = 0f
    var lean = 0f
    var kick = 0f
    var stride = 0f
    var head = 0f
    var wink = false
    var oh = false
    var finger = 0f
    var holding = false
    val handL = FloatArray(2)
    val handR = FloatArray(2)
}

/** [v]'s share of the way from [a] to [b], 0 before and 1 after. */
internal fun seg(v: Double, a: Double, b: Double): Float = ((v - a) / (b - a)).coerceIn(0.0, 1.0).toFloat()

private fun FloatArray.put(x: Float, y: Float) {
    this[0] = x
    this[1] = y
}

/** The hand at rest: hanging by his sides. */
private const val REST_X = 96f
private const val REST_Y = -100f

/** [into] [u] of the way from where it is to ([x], [y]). */
private fun FloatArray.toward(x: Float, y: Float, u: Float) = put(lerp(this[0], x, u), lerp(this[1], y, u))

/**
 * Santa [st] seconds into the visit ([t] is the screensaver's clock, for his wriggles and waves), into [p]. False while
 * he isn't in the room (before 6 s, when his boots appear in the chimney, and after 23.1 s, when he's gone up it).
 */
@Suppress("CyclomaticComplexMethod", "LongMethod") // One timeline, read top to bottom as in the mockup.
fun santaAt(st: Double, t: Double, p: SantaPose): Boolean {
    if (st < 6.0 || st > 23.1) return false
    p.x = 1000f
    p.y = 806f
    p.s = 0.8f
    p.inside = true
    p.hat = true
    p.soot = 0.3f
    p.squash = 0f
    p.lean = 0f
    p.kick = 0f
    p.stride = 0f
    p.head = 0f
    p.wink = false
    p.oh = false
    p.finger = 0f
    p.holding = false
    p.handL.put(-REST_X, REST_Y)
    p.handR.put(REST_X, REST_Y)
    when {
        st < 8.0 -> { // Boots first, kicking; then stuck at the belly.
            p.y = if (st < 7.0) lerp(470f, 640f, smooth(seg(st, 6.0, 6.7))) else lerp(640f, 694f, smooth(seg(st, 7.0, 7.4)))
            p.kick = 1f
            p.hat = false
            p.soot = 1f
            p.handL.put(-70f, -260f)
            p.handR.put(70f, -260f)
            if (st > 7.4) p.x += sin(t * 26).toFloat() * 5
        }
        st < 8.22 -> { // Pop: he drops the rest of the way.
            val u = seg(st, 8.0, 8.22)
            p.y = lerp(694f, 806f, u * u)
            p.kick = 0.6f
            p.hat = false
            p.soot = 1f
            p.handL.put(-112f, -210f)
            p.handR.put(112f, -210f)
            p.oh = true
        }
        st < 9.4 -> { // Landed in a heap, bare-headed; the hat arrives a moment later.
            p.squash = 0.26f * (1 - seg(st, 8.22, 8.6)) + (if (st > 9.3) 0.07f * (1 - seg(st, 9.3, 9.4)) else 0f)
            p.hat = st > 9.3
            p.oh = st < 8.7
            p.soot = 1f
            p.head = sin((st - 8.5) * 5.5).toFloat() * 0.16f * seg(st, 8.5, 8.7) * (1 - seg(st, 9.1, 9.3))
            p.handL.put(-112f, -140f)
            p.handR.put(112f, -140f)
        }
        st < 10.6 -> { // Out onto the hearth, ducking under the arch.
            val u = smooth(seg(st, 9.4, 10.6))
            p.inside = false
            p.soot = 1f
            p.y = lerp(806f, 898f, u) - abs(sin(u * PI * 3)).toFloat() * 9
            p.s = lerp(0.8f, SANTA_PAINTED, u)
            p.squash = 0.1f * sin(u * PI).toFloat()
            p.stride = sin(u * PI * 3).toFloat()
        }
        st < 20.4 -> onTheHearth(st, t, p)
        st < 21.4 -> { // Back into the fireplace.
            val u = smooth(seg(st, 20.4, 21.4))
            p.inside = false
            p.y = lerp(898f, 806f, u) - abs(sin(u * PI * 3)).toFloat() * 9
            p.s = lerp(SANTA_PAINTED, 0.8f, u)
            p.stride = sin(u * PI * 3).toFloat()
        }
        st < 22.3 -> { // A finger beside his nose, and a wink.
            val e = smooth(seg(st, 21.4, 21.7))
            p.inside = false
            p.handR.toward(22f, -214f, e)
            p.finger = e
            p.wink = st > 21.8
        }
        else -> { // And up the chimney he goes.
            val u = seg(st, 22.3, 23.0)
            p.y = 806 - 540 * u * u
            p.squash = -0.28f * min(1f, u * 4)
            p.handL.put(-60f, -280f)
            p.handR.put(60f, -280f)
            p.wink = true
        }
    }
    return true
}

/** From 10.6 to 20.4 s, out on the hearth: soot shaken off, "Shh", the stockings, the parcel, the bones, a wave. */
private fun onTheHearth(st: Double, t: Double, p: SantaPose) {
    p.inside = false
    p.y = 898f
    p.s = SANTA_PAINTED
    when {
        st < 11.6 -> { // Shakes the soot off.
            val e = sin(seg(st, 10.6, 11.6) * PI).toFloat()
            val shake = sin(t * 20).toFloat()
            p.lean = shake * 0.05f * e
            p.soot = 1 - 0.7f * seg(st, 10.6, 11.6)
            p.handL.put(-74 + shake * 14 * e, -150f)
            p.handR.put(74 + shake * 14 * e, -150f)
        }
        st < 12.9 -> { // "Shh", to the small dog and then to the big one.
            val e = smooth(seg(st, 11.6, 11.9)) * (1 - smooth(seg(st, 12.6, 12.9)))
            val side = if (st < 12.25) -1f else 1f
            p.handR.toward(12f, -206f, e)
            p.finger = e
            p.head = side * 0.14f * e
            p.lean = side * 0.05f * e
        }
        st < 16.3 -> for (i in 0 until 4) { // A flick of the hand for each stocking.
            val a = st - stockingFlick(i)
            if (a > -0.25 && a < 0.45) {
                val e = sin(seg(a, -0.25, 0.45) * PI).toFloat()
                val side = if (i < 2) -1f else 1f
                (if (i < 2) p.handL else p.handR).put(side * lerp(96f, 112f, e), lerp(-100f, -268f, e))
                p.head = side * 0.1f * e
            }
        }
        st < 17.9 -> { // A parcel out of thin air, and over to the tree with it.
            val w = smooth(seg(st, 16.3, 16.6))
            val th = smooth(seg(st, 16.8, 16.95))
            val back = smooth(seg(st, 17.4, 17.9))
            p.squash = 0.1f * w * (1 - th)
            p.holding = st < 16.8
            p.head = 0.12f * th * (1 - back)
            p.handL.toward(-40f, -122f, w)
            p.handL.toward(-6f, -256f, th)
            p.handL.toward(-REST_X, REST_Y, back)
            p.handR.toward(40f, -122f, w)
            p.handR.toward(112f, -250f, th)
            p.handR.toward(REST_X, REST_Y, back)
        }
        st < 19.6 -> { // A bone each.
            val a = sin(seg(st, 17.95, 18.45) * PI).toFloat()
            val b = sin(seg(st, 18.45, 18.95) * PI).toFloat()
            p.handL.put(-96 - 20 * a, -100 - 110 * a)
            p.handR.put(96 + 20 * b, -100 - 110 * b)
            p.head = -0.12f * a + 0.12f * b
        }
        else -> p.handR.toward(104 + sin(t * 13).toFloat() * 18, -270f, smooth(seg(st, 19.6, 19.8))) // A wave goodnight.
    }
}

/** When Santa flicks a sparkle at stocking [i], seconds into the visit. */
fun stockingFlick(i: Int): Double = 13.0 + i * 0.75

/** When stocking [i] fills, seconds into the visit: its sparkle has flown there. */
fun stockingFilled(i: Int): Double = stockingFlick(i) + 0.55

/** When the parcel lands under the tree, and when bone [i] lands on the rug, seconds into the visit. */
const val PARCEL_LANDS = 17.7
fun boneLands(i: Int): Double = 18.2 + i * 0.5 + 0.7

/** Where each dog's bone lands on the rug (the small dog's, then the big dog's), and how it lies. */
val BONES = listOf(Triple(894f, 1014f, 0.25f), Triple(1068f, 1016f, -0.3f))

/**
 * How the fire burns [st] seconds into the visit, as a share of its own strength: it gutters and goes out as Santa arrives,
 * stays out while he's here, and comes back with a swell when he's gone.
 */
fun visitFire(st: Double): Float = when {
    st < 2.6 -> 1f
    st < 3.5 -> (1 - seg(st, 2.6, 3.5)) * (0.5f + 0.5f * abs(sin(st * 23)).toFloat())
    st < 23.4 -> 0f
    else -> smooth(seg(st, 23.4, 24.4)) * (1 + 0.45f * sin(seg(st, 23.4, 26.0) * PI).toFloat())
}

/** The gold flash of the fire relighting itself, 1 down to 0 from 23.4 s. */
fun relightFlash(st: Double): Float = if (st < 23.4) 0f else 1 - seg(st, 23.4, 24.6)

/** How awake both dogs are [st] seconds into the visit: they wake in the soot and settle once he's gone. */
fun dogsAwake(st: Double): Float = smooth(seg(st, 4.6, 5.0)) * (1 - smooth(seg(st, 26.5, 28.5)))

/** The small dog lifts his head first, at the sleigh's thump on the roof. */
fun smallDogAwake(st: Double): Float = maxOf(dogsAwake(st), 0.5f * smooth(seg(st, 2.0, 2.3)) * (1 - seg(st, 4.0, 4.6)))

/** Both tails wag from Santa's "Shh" until he goes. */
fun dogsWag(st: Double): Float = seg(st, 11.6, 12.2) * (1 - seg(st, 21.5, 23.0))

/** How far the room shakes [st] seconds in: the sleigh landing on the roof, the sack dropping, Santa landing. */
fun roomShake(st: Double): Float {
    fun thud(at: Double, length: Double, amount: Float): Float {
        val into = st - at
        return if (into > 0 && into < length) (1 - into / length).toFloat() * amount else 0f
    }
    return thud(1.9, 0.8, 5f) + thud(5.2, 0.3, 3f) + thud(8.22, 0.4, 5f)
}

/** The pieces Santa is drawn from: see [santaMarks]. */
enum class SantaPart { LEFT_LEG, RIGHT_LEG, BODY, HEAD, HEAD_WINKING, HEAD_OH, HAT, SOOT, ARM, CUFF, JOINT, MITTEN, FINGER }

/** The red of his suit, its white trim, his boots and belt, and his mittens. */
private const val SUIT = 0xD8342F
private const val TRIM = 0xFFFFFF
private const val BLACK = 0x1D1B1E
private const val MITTEN = 0x2F6B45

/** How long an arm's band is painted ([SantaPart.ARM], [SantaPart.CUFF]); it's stretched to each part of the arm. */
const val ARM_BAND = 32f

/**
 * [part] of Santa, in his own units. A leg hangs from its hip at (0, 0); the head (and the hat, which goes on it) is around
 * its middle at (0, 0); the arm's band and the cuff hang down [ARM_BAND] from (0, 0); a joint, the mitten and the finger's
 * root are at (0, 0); the rest are where they are on him.
 */
fun santaMarks(part: SantaPart): List<Mark> = Sketch().apply {
    when (part) {
        SantaPart.LEFT_LEG, SantaPart.RIGHT_LEG -> {
            val side = if (part == SantaPart.LEFT_LEG) -1f else 1f
            roundBox(ink(SUIT), Area(-15f, 0f, 30f, 46f), 8f)
            roundBox(ink(BLACK), Area(-14f, 34f, 28f, 22f), 6f)
            oval(ink(BLACK), Ellipse(side * 4, 56f, 23f, 12f))
            roundBox(ink(TRIM), Area(-18f, 29f, 36f, 12f), 6f)
        }
        SantaPart.BODY -> {
            val belly = Ellipse(0f, -130f, 74f, 78f)
            oval(ink(SUIT), belly)
            clipped({ oval(belly) }) {
                box(ink(TRIM), -9f, -210f, 18f, 160f)
                box(ink(TRIM), -80f, -72f, 160f, 24f)
                box(ink(BLACK), -80f, -128f, 160f, 22f)
            }
            line(ink(0xF2C94A), 5f) { roundRect(Area(-17f, -132f, 34f, 30f), 5f) }
        }
        SantaPart.HEAD, SantaPart.HEAD_WINKING, SantaPart.HEAD_OH -> {
            val wink = part == SantaPart.HEAD_WINKING
            santaHead(wink, oh = part == SantaPart.HEAD_OH)
        }
        SantaPart.HAT -> {
            fill(ink(SUIT)) {
                moveTo(-38f, -30f).cubicTo(-36f to -84f, 16f to -98f, 54f, -58f)
                lineTo(44f, -44f).cubicTo(30f to -62f, 34f to -40f, 38f, -30f).close()
            }
            roundBox(ink(TRIM), Area(-45f, -40f, 90f, 20f), 10f)
            disc(ink(TRIM), 54f, -50f, 12f)
        }
        SantaPart.SOOT -> {
            val soot = ink(0x1E1A18)
            oval(soot, Ellipse(-36f, -152f, 17f, 10f, 0.5f))
            oval(soot, Ellipse(32f, -92f, 15f, 9f, -0.3f))
            oval(soot, Ellipse(40f, -180f, 10f, 7f, 0.2f))
        }
        SantaPart.ARM -> box(ink(SUIT), -16f, 0f, 32f, ARM_BAND)
        SantaPart.CUFF -> box(ink(TRIM), -17f, 0f, 34f, ARM_BAND)
        SantaPart.JOINT -> disc(ink(SUIT), 0f, 0f, 16f)
        SantaPart.MITTEN -> disc(ink(MITTEN), 0f, 0f, 18f)
        SantaPart.FINGER -> roundBox(ink(MITTEN), Area(-5f, 0f, 10f, 26f), 5f)
    }
}.marks

/** His face and beard, around the middle of his head: plain, winking, or "oh" (mouth open, brows up). */
private fun Sketch.santaHead(wink: Boolean, oh: Boolean) {
    oval(ink(TRIM), Ellipse(0f, 24f, 50f, 48f))
    oval(ink(0xF5CDB0), Ellipse(0f, -4f, 34f, 28f))
    disc(ink(0xEE8F7C, 0.55f), -22f, 2f, 8f)
    disc(ink(0xEE8F7C, 0.55f), 22f, 2f, 8f)
    oval(ink(TRIM), Ellipse(-15f, 15f, 19f, 9f, 0.25f))
    oval(ink(TRIM), Ellipse(15f, 15f, 19f, 9f, -0.25f))
    disc(ink(0xEE8F7C), 0f, 7f, 8f)
    val eye = ink(0x20140E)
    disc(eye, -13f, -10f, 3.6f)
    if (wink) {
        line(eye, 3f) {
            arc(Ellipse(13f, -9f, 5.5f, 5.5f), PI.toFloat() + 0.3f, 2 * PI.toFloat() - 0.3f)
        }
    } else {
        disc(eye, 13f, -10f, 3.6f)
    }
    val up = if (oh) 4f else 0f
    line(ink(TRIM), 5.5f) { through(-22f, -20 - up, -7f, -23 - up) }
    line(ink(TRIM), 5.5f) { through(7f, -23 - up, 22f, -20 - up) }
    if (oh) oval(ink(0x5A2A22), Ellipse(0f, 27f, 6.5f, 8f))
}

/** Santa's sack, its foot's middle at (0, 0), presents peeking out of the top. */
fun sackMarks(): List<Mark> = Sketch().apply {
    box(ink(0x3F8FD8), -22f, -112f, 24f, 28f)
    box(ink(0xF2C94A), -12f, -112f, 4f, 28f)
    placed(16f, -100f, turn = 0.3f) { box(ink(SUIT), -9f, -12f, 20f, 24f) }
    fill(ink(0x8F6638)) {
        moveTo(
            -44f,
            0f,
        ).cubicTo(-66f to -40f, -42f to -86f, -14f, -90f).lineTo(16f, -90f).cubicTo(46f to -86f, 64f to -38f, 44f, 0f).close()
    }
    oval(ink(0xA57A46), Ellipse(-16f, -42f, 15f, 26f, 0.2f))
    line(ink(0xF2C94A), 5f) { through(-18f, -88f, 20f, -88f) }
}.marks

/** A sparkle's star, [SPARKLE_R] across its points, around (0, 0) (its glow is a separate soft light). */
fun sparkleMarks(): List<Mark> = Sketch().apply { fill(ink(0xFFF6CE)) { star(0f, 0f, SPARKLE_R) } }.marks

const val SPARKLE_R = 10f

/** The four greys of the soot that billows out over the hearth. */
val SOOT_GREYS = intArrayOf(0x4A423D, 0x6B625B, 0x353030, 0x857B72)

/** The radius soot puffs, smoke wisps and roof clumps are painted at; they're drawn bigger or smaller from that. */
const val PUFF_R = 40f

/** Santa's sooty footprints on the hearth, where they are in the room. */
fun footprintMarks(): List<Mark> = Sketch().apply {
    val soot = ink(0x181412)
    oval(soot, Ellipse(972f, 836f, 20f, 8f, -0.15f))
    oval(soot, Ellipse(1030f, 840f, 20f, 8f, 0.15f))
    oval(soot, Ellipse(974f, 884f, 23f, 9f, -0.1f))
    oval(soot, Ellipse(1030f, 888f, 23f, 9f, 0.1f))
}.marks

/**
 * The sleigh crossing the sky (from the mockup's K.sleigh): three reindeer and the sleigh with Santa in it, facing right, in its
 * own units, its middle at (0, 0). [gallop] is where the reindeer's legs are in their stride, radians.
 */
@Suppress("LongMethod") // One drawing, part by part as in the mockup.
fun sleighMarks(gallop: Float): List<Mark> = Sketch().apply {
    line(ink(0xF2D27E), 1.8f) { through(-88f, -12f, -14f, -8f, 132f, -16f) }
    for (i in 0 until 3) {
        val rx = -20f + i * 62
        val ry = 2f - i * 3
        val g = sin(gallop + i * 1.3f)
        val leg = ink(0x6E4728)
        line(leg, 3.4f) { through(rx - 15, ry + 4, rx - 24 - g * 10, ry + 24) }
        line(leg, 3.4f) { through(rx - 9, ry + 5, rx - 12 - g * 8, ry + 26) }
        line(leg, 3.4f) { through(rx + 13, ry + 5, rx + 22 + g * 10, ry + 24) }
        line(leg, 3.4f) { through(rx + 17, ry + 4, rx + 30 + g * 8, ry + 20) }
        disc(ink(0xFFF4E6), rx - 22, ry - 4, 4.2f)
        val coat = ink(0xB27A45)
        oval(coat, Ellipse(rx, ry, 22f, 9.5f))
        oval(coat, Ellipse(rx + 24, ry - 12, 9f, 5.6f, -0.6f))
        oval(coat, Ellipse(rx + 32, ry - 19, 8.6f, 5.3f, 0.1f))
        oval(ink(0xD6A874), Ellipse(rx + 2, ry + 4.5f, 15f, 4.2f))
        line(ink(0xE0352F), 3.2f) { through(rx + 15, ry - 13, rx + 21, ry - 3) }
        disc(ink(0xFFD24A), rx + 21, ry - 1.5f, 2.4f)
        val antler = ink(0xECD3A6)
        line(antler, 2.1f) { through(rx + 30, ry - 23, rx + 26, ry - 37) }
        line(antler, 2.1f) { through(rx + 28, ry - 30, rx + 35, ry - 36) }
        line(antler, 2.1f) { through(rx + 27, ry - 33, rx + 20, ry - 38) }
        disc(ink(0x20140E), rx + 34.5f, ry - 21, 1.1f)
        if (i == 2) {
            glow(rx + 40.5f, ry - 18.5f, 18f, 0xFF4A3A, 1f)
            disc(ink(0xFF5A48), rx + 40.5f, ry - 18.5f, 3f)
        } else {
            disc(ink(0x20140E), rx + 40, ry - 18.5f, 1.8f)
        }
    }
    val gold = ink(0xF2D27E)
    line(gold, 3.6f) { moveTo(-160f, 33f).lineTo(-66f, 33f).quadTo(-46f, 33f, -47f, 14f) }
    line(gold, 3.6f) { through(-140f, 24f, -140f, 33f) }
    line(gold, 3.6f) { through(-84f, 24f, -84f, 33f) }
    disc(ink(0x8D6438), -135f, -17f, 17f)
    box(ink(0x3FA25A), -152f, -42f, 15f, 13f)
    box(ink(0xFFD24A), -146f, -42f, 3.5f, 13f)
    val red = ink(0xE0352F)
    oval(red, Ellipse(-101f, -9f, 14.5f, 12.5f))
    line(red, 6.5f) { through(-99f, -13f, -86f, -11f) }
    disc(ink(0xFFFFFF), -84f, -11f, 3.6f)
    disc(ink(0xF5D0B4), -99f, -26f, 7.6f)
    oval(ink(0xFFFFFF), Ellipse(-95f, -20.5f, 8f, 6.2f))
    shape(red, -107f, -30f, -91f, -32f, -111f, -46f)
    line(ink(0xFFFFFF), 3.8f) { through(-107f, -30f, -91f, -32f) }
    disc(ink(0xFFFFFF), -112f, -47f, 3.4f)
    fun body(): PathMaker.() -> Unit = {
        moveTo(-152f, -6f).quadTo(-160f, 22f, -128f, 25f).lineTo(-74f, 25f).quadTo(-56f, 23f, -58f, 0f)
            .lineTo(-82f, 2f).quadTo(-100f, 6f, -120f, -2f).close()
    }
    fill(red, build = body())
    line(gold, 2.4f, build = body())
}.marks

/** How many steps of the reindeer's gallop are painted. */
const val GALLOP_FRAMES = 3
