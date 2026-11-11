package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/*
 * Santa's visit drawn into one frame's layers, [FiresideNow.visit] seconds in:
 * what's seen through the fireplace's opening (grit, smoke, the sack, Santa
 * coming down and going up), Santa out on the hearth, what flies through the
 * room (soot and glitter, sparkles to the stockings, the parcel, the bones, the
 * flash as the fire relights) and the sleigh in the window. Ported from the
 * mockup's santaInside, santaOutside, santaFx and K.sleigh. Plain Kotlin.
 */

/** Where the stockings' sparkles fly to, left to right. */
private val STOCKING_AIMS = listOf(782f to 362f, 918f to 376f, 1042f to 376f, 1166f to 362f)

/** The visit at [FiresideNow.visit] seconds into [frame]'s layers; nothing when no visit is under way. */
fun visitInto(frame: FiresideFrame, sprites: FiresideSprites) {
    val st = frame.now.visit
    if (st.isNaN()) return
    val pose = frame.pose
    val shown = santaAt(st, frame.now.t, pose)
    insideInto(frame, sprites, st, shown)
    outsideInto(frame, sprites, st, shown)
    flyingInto(frame, sprites, st)
}

/** A sparkle: a soft gold light and a little star, [size] across its points, [alpha] strong, turned [turn]. */
private fun FiresideFrame.sparkle(sprites: FiresideSprites, x: Float, y: Float, size: Float, alpha: Float, turn: Float = 0f) {
    if (alpha <= 0.02f || size <= 0.3f) return
    light.add(sprites.glows.star, x, y, size * 5 / GLOW_REACH, alpha * 0.8f)
    light.addThrough(
        sprites.visit.sparkle,
        turns[3].reset().translate(x, y).rotate(turn + PI.toFloat() / 2).scale(size / SPARKLE_R),
        1f,
        alpha,
    )
}

private fun insideInto(frame: FiresideFrame, sprites: FiresideSprites, st: Double, shown: Boolean) {
    sootyChimneyInto(frame, sprites, st)
    val p = frame.pose
    if (shown && p.inside) {
        frame.insideLight.add(sprites.glows.star, p.x, p.y - 110, 260f / GLOW_REACH, 0.4f)
        santaInto(frame.inside, sprites.santa, p, frame.now.t, frame.turns)
    }
    if (st > 8.5 && st <= 9.3) { // His hat, floating down after him.
        val u = seg(st, 8.5, 9.3)
        val at = frame.turns[0].reset()
            .translate(1000 + sin(u * 7) * 34 * (1 - u), lerp(480f, 806 - 226 * 0.8f, smooth(u)))
            .scale(0.8f)
            .rotate(sin(u * 7 + 1) * 0.35f * (1 - u))
        frame.inside.addThrough(sprites.santa[SantaPart.HAT], at, SANTA_PAINTED)
    }
    if (st > 22.3 && st <= 23.1) { // Up the chimney in sparkles, gone with him.
        val t = frame.now.t
        for (i in 0 until 18) {
            val a = (t * 1.6 + i / 18.0).mod(1.0).toFloat()
            val x = 1000 + sin(i * 2.4 + t * 5).toFloat() * 90 * (1 - a * 0.5f)
            insideSparkle(frame, sprites, x, 810 - a * 300, 1 - a, (t + i).toFloat())
        }
    }
}

/** Before him: grit falling down the chimney, a wisp of smoke as the fire goes out, the sack dropping (and going back up). */
private fun sootyChimneyInto(frame: FiresideFrame, sprites: FiresideSprites, st: Double) {
    val t = frame.now.t
    val v = sprites.visit
    val q = frame.inside
    for (g in GRIT_STARTS.indices) {
        for (i in 0 until GRIT_SPECKS[g]) {
            val a = (st - GRIT_STARTS[g] - i * 0.09).toFloat()
            if (a > 0 && a < 0.7f) q.add(v.grit, 930f + (i * 53) % 140, 525 + 620 * a * a, (3 + (i % 3) * 1.5f) / GRIT_R)
        }
    }
    val wisp = seg(st, 3.2, 3.7) * (1 - seg(st, 5.2, 6.4))
    if (wisp > 0) {
        for (i in 0 until 6) {
            val age = (t * 0.35 + i / 6.0).mod(1.0).toFloat()
            val x = 1000 + sin(age * 6 + i * 2) * 26 + (i - 2.5f) * 22
            q.add(v.puffs[WISP], x, 780 - age * 230, (14 + age * 34) / PUFF_R, wisp * (1 - age) * 0.45f)
        }
    }
    if (st > 5.2 && st < 23.2) {
        val drop = seg(st, 5.2, 5.5)
        val up = if (st > 22.4) seg(st, 22.4, 23.1).pow(2) * 540 else 0f
        q.add(v.sack, 924f, lerp(470f, 802f, drop * drop) - sin(seg(st, 5.5, 5.8) * PI).toFloat() * 10 - up)
    }
}

/** A sparkle seen inside the fireplace: cut to its opening like everything else there. */
private fun insideSparkle(frame: FiresideFrame, sprites: FiresideSprites, x: Float, y: Float, alpha: Float, turn: Float) {
    if (alpha <= 0.02f) return
    frame.insideLight.add(sprites.glows.star, x, y, 25f / GLOW_REACH, alpha * 0.8f)
    frame.insideLight.addThrough(sprites.visit.sparkle, frame.turns[3].reset().translate(x, y).rotate(turn).scale(0.5f), 1f, alpha)
}

/** A parcel out of thin air in Santa's hands, in sparkles. */
private fun heldParcelInto(frame: FiresideFrame, sprites: FiresideSprites, st: Double) {
    val t = frame.now.t
    val p = frame.pose
    val v = sprites.visit
    val g = smooth(seg(st, 16.3, 16.55))
    if (g > 0.01f) {
        val at = frame.turns[0].reset().translate(p.x, p.y - 100).scale(g)
        frame.fx.addThrough(v.parcelBox, at) // In the room's own tint, as it will be under the tree.
        frame.fx.addThrough(v.parcelLid, at.translate(0f, -SANTAS_PARCEL.h))
    }
    for (i in 0 until 8) {
        val a = i * 0.8 + t * 3
        frame.sparkle(sprites, p.x + cos(a).toFloat() * 76 * g, p.y - 134 + sin(a).toFloat() * 54 * g, 5f, 1 - g * 0.5f, (t + i).toFloat())
    }
}

/** The three bursts of grit before he comes: when each starts and how many specks. */
private val GRIT_STARTS = doubleArrayOf(1.95, 4.2, 7.4)
private val GRIT_SPECKS = intArrayOf(4, 7, 5)

private fun outsideInto(frame: FiresideFrame, sprites: FiresideSprites, st: Double, shown: Boolean) {
    val t = frame.now.t
    val v = sprites.visit
    val q = frame.santa
    val prints = seg(st, 9.7, 10.0) * (1 - seg(st, 24.0, 30.0))
    if (prints > 0) q.add(v.footprints, 0f, 0f, 1f, 0.5f * prints)
    val p = frame.pose
    if (!shown || p.inside) return
    frame.glows.add(sprites.glows.star, p.x, p.y - 150 * p.s, 340f / GLOW_REACH, 0.32f)
    santaInto(q, sprites.santa, p, t, frame.turns)
    if (p.holding) heldParcelInto(frame, sprites, st)
    if (st > 10.6 && st < 11.9) { // The soot he shakes off.
        for (i in 0 until 7) {
            val a = ((st - 10.6 - i * 0.1) / 0.9).toFloat()
            if (a <= 0 || a >= 1) continue
            q.add(v.puffs[0], p.x + (i - 3) * 30, p.y - 180 + a * 150 + (i % 2) * 30, (14 + a * 22) / PUFF_R, 0.5f * (1 - a))
        }
    }
    if (st > 21.5 && st < 22.3) { // A ring of sparkles round him as he winks.
        val radius = 110 - seg(st, 21.5, 22.3) * 50
        for (i in 0 until 12) {
            val a = t * 3 + i / 12.0 * 2 * PI
            frame.sparkle(sprites, p.x + cos(a).toFloat() * radius, p.y - 130 + sin(a).toFloat() * radius * 0.9f, 5f, 0.9f, a.toFloat())
        }
    }
}

@Suppress("CyclomaticComplexMethod") // The visit's flying things, one after another as in the mockup.
private fun flyingInto(frame: FiresideFrame, sprites: FiresideSprites, st: Double) {
    val t = frame.now.t
    val v = sprites.visit
    for (i in STOCKING_AIMS.indices) {
        val (aimX, aimY) = STOCKING_AIMS[i]
        val a = st - stockingFlick(i)
        if (a > 0 && a < 0.55) { // A sparkle flicked from his hand to the stocking, trailing more.
            val u = (a / 0.55).toFloat()
            val x0 = 1000 + (if (i < 2) -125f else 125f)
            for (j in 0 until 9) {
                val w = maxOf(0f, u - j * 0.035f)
                val e = smooth(w)
                frame.sparkle(
                    sprites,
                    lerp(x0, aimX, e) + sin(w * 9 + j) * 5,
                    lerp(598f, aimY, e) - sin(w * PI).toFloat() * 70,
                    9 - j * 0.8f,
                    1 - j / 9f,
                    (t * 4 + j).toFloat(),
                )
            }
        }
        val b = ((st - stockingFilled(i)) / 0.7).toFloat()
        if (b >= 0 && b < 1) { // And a burst as it fills.
            for (j in 0 until 10) {
                val an = j / 10f * 2 * PI.toFloat()
                frame.sparkle(sprites, aimX + cos(an) * b * 74, aimY + sin(an) * b * 56, 6f, 1 - b, an + t.toFloat())
            }
        }
    }
    if (st >= 16.8 && st < PARCEL_LANDS) { // The parcel, lobbed under the tree.
        val u = seg(st, 16.8, PARCEL_LANDS)
        for (j in 1 until 9) {
            val w = maxOf(0f, u - j * 0.03f)
            frame.sparkle(sprites, parcelX(w), parcelY(w), 7 - j * 0.6f, 1 - j / 9f, (t * 3 + j).toFloat())
        }
        val at = frame.turns[0].reset().translate(parcelX(u), parcelY(u)).rotate(u * 2 * PI.toFloat() * 1.5f).translate(0f, 33f)
        frame.fx.addThrough(v.parcelBox, at)
        frame.fx.addThrough(v.parcelLid, at.translate(0f, -SANTAS_PARCEL.h))
    }
    for (i in BONES.indices) { // A bone each, tossed onto the rug.
        val u = ((st - (18.2 + i * 0.5)) / 0.7).toFloat()
        if (u < 0 || u >= 1) continue
        val (x, y) = BONES[i]
        val side = if (i == 0) -1 else 1
        frame.fx.addTurned(v.bone, lerp(1000f + side * 130, x, u), lerp(663f, y, u) - sin(u * PI).toFloat() * 150, 1f, u * 4 * PI.toFloat())
    }
    sootInto(frame, sprites, st - 4.6, 1000f, 720f, 1f)
    sootInto(frame, sprites, st - 8.22, 1000f, 800f, 0.5f)
    sootInto(frame, sprites, st - 22.7, 1000f, 760f, 0.4f)
    val flash = if (st > 23.4) 1 - seg(st, 23.4, 24.3) else 0f
    if (flash > 0) { // The fire relighting itself with a gold flash.
        frame.light.add(sprites.glows.star, 1000f, 740f, (300 + (1 - flash) * 500) / GLOW_REACH, flash)
        for (i in 0 until 16) {
            val an = i / 16f * 2 * PI.toFloat()
            frame.sparkle(sprites, 1000 + cos(an) * (1 - flash) * 320, 720 + sin(an) * (1 - flash) * 220, 8f, flash, an)
        }
    }
}

private fun parcelX(u: Float) = lerp(1030f, SANTAS_PARCEL.x, u)

private fun parcelY(u: Float) = lerp(690f, SANTAS_PARCEL.y - 33, u) - sin(u * PI).toFloat() * 250

/** A soot puff or a glitter speck of a burst: its direction, how far it goes, its size, its delay and (glitter) twinkle. */
private class Spray(val angle: FloatArray, val reach: FloatArray, val size: FloatArray, val delay: FloatArray, val phase: FloatArray)

private fun burst(seed: Int, n: Int, spread: Float, reach: Pair<Float, Float>, size: Pair<Float, Float>, delay: Float): Spray {
    val r = Mulberry(seed)
    val angle = FloatArray(n)
    val far = FloatArray(n)
    val big = FloatArray(n)
    val wait = FloatArray(n)
    val phase = FloatArray(n)
    for (i in 0 until n) {
        angle[i] = -spread + r.next() * (PI.toFloat() + 2 * spread)
        far[i] = reach.first + r.next() * reach.second
        big[i] = size.first + r.next() * size.second
        wait[i] = r.next() * delay
        phase[i] = r.next() * 2 * PI.toFloat()
    }
    return Spray(angle, far, big, wait, phase)
}

private val SOOT = burst(77, 34, 0.35f, 150f to 330f, 0.6f to 0.8f, 0.35f)
private val GLITTER = burst(78, 46, 0.6f, 120f to 430f, 2f to 4f, 0.5f)

/** A burst of soot and chimney dust with gold glitter in it, [a] seconds after it blew, from ([x], [y]), [size] big. */
private fun sootInto(frame: FiresideFrame, sprites: FiresideSprites, a: Double, x: Float, y: Float, size: Float) {
    if (a < 0 || a > 4) return
    val puffs = sprites.visit.puffs
    for (i in SOOT.angle.indices) {
        val age = (a - SOOT.delay[i]).toFloat()
        if (age <= 0 || age >= 2.9f) continue
        val u = age / 2.9f
        val e = 1 - (1 - u).pow(3)
        val px = x + cos(SOOT.angle[i]) * SOOT.reach[i] * e * size
        val py = y + sin(SOOT.angle[i]) * SOOT.reach[i] * 0.5f * e * size + age * 16 - 20 * e
        frame.fx.add(puffs[i % SOOT_GREYS.size], px, py, (30 + 95 * e) * SOOT.size[i] * size / PUFF_R, 0.8f * (1 - u).pow(1.3f))
    }
    for (i in GLITTER.angle.indices) {
        val age = (a - GLITTER.delay[i]).toFloat()
        if (age <= 0 || age >= 3.4f) continue
        val u = age / 3.4f
        val e = 1 - (1 - u).pow(2.5f)
        val twinkle = 0.5f + 0.5f * sin(age * 12 + GLITTER.phase[i])
        val px = x + cos(GLITTER.angle[i]) * GLITTER.reach[i] * e * size
        val py = y + sin(GLITTER.angle[i]) * GLITTER.reach[i] * 0.5f * e * size + age * age * 22 - 30 * e
        frame.sparkle(
            sprites,
            px,
            py,
            GLITTER.size[i] * size * (0.6f + twinkle),
            (1 - u) * (0.4f + 0.6f * twinkle),
            age * 3 + GLITTER.phase[i],
        )
    }
}

/**
 * Santa in pose [p] into [q]: his legs (kicking or striding), body, the left arm, his head (and hat) turned, the right arm,
 * then the soot on him; the whole squashed, stretched and leaning as [p] says.
 */
internal fun santaInto(q: SpriteQuads, parts: SantaSprites, p: SantaPose, t: Double, turns: Array<Turn2d>) {
    val body = turns[0].reset().translate(p.x, p.y).scale(p.s * (1 + p.squash * 0.5f), p.s * (1 - p.squash)).rotate(p.lean)
    val at = turns[1]
    legInto(q, parts[SantaPart.LEFT_LEG], body, at, -1, p, t)
    legInto(q, parts[SantaPart.RIGHT_LEG], body, at, 1, p, t)
    q.addThrough(parts[SantaPart.BODY], body, SANTA_PAINTED)
    armInto(q, parts, body, at, -1, p.handL, 0f)
    val face = when {
        p.wink -> SantaPart.HEAD_WINKING
        p.oh -> SantaPart.HEAD_OH
        else -> SantaPart.HEAD
    }
    at.set(body).translate(0f, -226f).rotate(p.head)
    q.addThrough(parts[face], at, SANTA_PAINTED)
    if (p.hat) q.addThrough(parts[SantaPart.HAT], at.rotate(sin(t * 3).toFloat() * 0.03f), SANTA_PAINTED)
    armInto(q, parts, body, at, 1, p.handR, p.finger)
    if (p.soot > 0) q.addThrough(parts[SantaPart.SOOT], body, SANTA_PAINTED, 0.5f * p.soot)
}

/** One leg, from its hip on [body] ([side] -1 his right, on the left as we see him), kicking or striding as [p] says. */
private fun legInto(q: SpriteQuads, leg: Sprite, body: Turn2d, at: Turn2d, side: Int, p: SantaPose, t: Double) {
    val kick = side * p.kick * 0.5f * sin(t * 11 + (if (side > 0) 0 else 2)).toFloat() + p.stride * side * 0.22f
    q.addThrough(leg, at.set(body).translate(side * 24f, -64f).rotate(kick), SANTA_PAINTED)
}

/** One arm, from its shoulder on [body] to [hand], bent at the elbow, with its white cuff and mitten; a [finger] raised. */
private fun armInto(q: SpriteQuads, parts: SantaSprites, body: Turn2d, at: Turn2d, side: Int, hand: FloatArray, finger: Float) {
    val sx = side * 60f
    val sy = -176f
    val hx = hand[0]
    val hy = hand[1]
    val ex = (sx + hx) / 2 + side * 16
    val ey = (sy + hy) / 2 + 8
    val joint = parts[SantaPart.JOINT]
    q.addThrough(joint, at.set(body).translate(sx, sy), SANTA_PAINTED)
    bandInto(q, parts[SantaPart.ARM], at.set(body), sx, sy, ex, ey)
    q.addThrough(joint, at.set(body).translate(ex, ey), SANTA_PAINTED)
    bandInto(q, parts[SantaPart.ARM], at.set(body), ex, ey, hx, hy)
    bandInto(q, parts[SantaPart.CUFF], at.set(body), lerp(ex, hx, 0.66f), lerp(ey, hy, 0.66f), lerp(ex, hx, 0.86f), lerp(ey, hy, 0.86f))
    q.addThrough(parts[SantaPart.MITTEN], at.set(body).translate(hx, hy), SANTA_PAINTED)
    if (finger > 0) q.addThrough(parts[SantaPart.FINGER], at.set(body).translate(hx, hy - 16 - 16 * finger), SANTA_PAINTED)
}

/** A band of Santa's (an arm, a cuff) from ([x0], [y0]) to ([x1], [y1]) in his units, through [at] (which this changes). */
private fun bandInto(q: SpriteQuads, band: Sprite, at: Turn2d, x0: Float, y0: Float, x1: Float, y1: Float) {
    val dx = x1 - x0
    val dy = y1 - y0
    q.addThrough(band, at.translate(x0, y0).rotate(atan2(-dx, dy)).scale(1f, hypot(dx, dy) / ARM_BAND), SANTA_PAINTED)
}

/** The roof's snow sliding past the window as the sleigh lands: where each clump starts across, its delay and its size. */
private val CLUMPS = Mulberry(5).let { r -> List(24) { Triple(1250 + r.next() * 570, r.next() * 0.7f, 8 + r.next() * 22) } }

/**
 * What the window shows of the visit, on the outdoor scene's units: the sleigh climbing toward the roof, the snow it shakes off
 * the roof, and the sleigh crossing the sky again as it leaves (over the moon, if the moon is in the window: [moonX], [moonY],
 * else NaN).
 */
fun windowVisitInto(frame: FiresideFrame, sprites: FiresideSprites, moonX: Float, moonY: Float) {
    val st = frame.now.visit
    if (st.isNaN()) return
    val flight = frame.flight
    if (st < CLIMB) sleighInto(frame, sprites, flight.set(1160f, 620f, 1740f, -140f, 30f, 1.4f), (st / CLIMB).toFloat(), seg(st, 0.0, 0.3))
    if (st > 1.9 && st < 4.4) {
        for (i in CLUMPS.indices) {
            val (x, delay, size) = CLUMPS[i]
            val age = (st - 1.9 - delay).toFloat()
            if (age <= 0) continue
            frame.snow.add(sprites.visit.puffs[CLUMP], x + sin(age * 5 + i) * 6, -20 + 620 * age * age + 60 * age, size / PUFF_R)
        }
    }
    val leaving = (st - LEAVES) / CROSSING
    if (leaving > 0 && leaving < 1) {
        var y = 320f
        // Over the moon if it's in the window and well above the mountains; low down, the flight would cross the land.
        if (moonToCross(moonX, moonY)) {
            val u = (moonX - LEAVE_FROM) / (LEAVE_TO - LEAVE_FROM)
            y = moonY + sin(u * PI).toFloat() * 70 // So the arc's middle crosses the moon.
        }
        sleighInto(frame, sprites, flight.set(LEAVE_FROM, y, LEAVE_TO, y, 70f, 1.5f), leaving.toFloat(), 1f)
    }
}

/** Whether the leaving sleigh can cross the moon at ([moonX], [moonY]): it's in the window, and high enough. */
private fun moonToCross(moonX: Float, moonY: Float): Boolean = moonX > LEAVE_FROM && moonX < LEAVE_TO && moonY < HIGH_MOON // False for NaN.

/** A moon higher than this (on the outdoor scene's units) is clear of the window's mountains for the sleigh to cross. */
private const val HIGH_MOON = 420f

private const val CLIMB = 2.7
private const val LEAVES = 24.4
private const val CROSSING = 9.0
private const val LEAVE_FROM = 1080f
private const val LEAVE_TO = 2000f

/** A sleigh's flight on the outdoor scene: from ([x0], [y0]) to ([x1], [y1]), its middle lifted [arc], drawn [s] big. Reused. */
class Flight {
    private var x0 = 0f
    private var y0 = 0f
    private var x1 = 0f
    private var y1 = 0f
    private var arc = 0f
    var s = 1f
        private set

    @Suppress("LongParameterList") // A flight is these six numbers.
    fun set(x0: Float, y0: Float, x1: Float, y1: Float, arc: Float, s: Float): Flight = apply {
        this.x0 = x0
        this.y0 = y0
        this.x1 = x1
        this.y1 = y1
        this.arc = arc
        this.s = s
    }

    fun x(u: Float) = lerp(x0, x1, u)

    fun y(u: Float) = lerp(y0, y1, u) - sin(u.coerceIn(0f, 1f) * PI).toFloat() * arc
}

/** The sleigh [u] of the way along [flight], with its glow and its trail of sparkles, [fade] strong. */
private fun sleighInto(frame: FiresideFrame, sprites: FiresideSprites, flight: Flight, u: Float, fade: Float) {
    val t = frame.now.t
    val s = flight.s
    val x = flight.x(u)
    val y = flight.y(u) + sin(u * 22) * 4 * s
    val ang = atan2(flight.y(u + 0.01f) - flight.y(u - 0.01f), abs(flight.x(u + 0.01f) - flight.x(u - 0.01f)))
    val v = sprites.visit
    for (i in 0 until 26) {
        val w = u - 0.0075f * (i + 1.5f) - 0.06f * s
        val al = (1 - i / 26f) * 0.95f * fade
        val tw = 0.6f + 0.4f * sin(t * 7 + i * 2.3).toFloat()
        val sx = flight.x(w) + sin(i * 2.1 + t * 2).toFloat() * 7 * s
        val sy = flight.y(w) + 14 * s + cos(i * 1.7 + t * 3).toFloat() * 9 * s + i * 0.9f * s
        frame.windowLight.add(sprites.glows.star, sx, sy, (22 - i * 0.4f) * s / GLOW_REACH, al * 0.8f * tw)
        frame.snow.add(v.spark, sx, sy, (2.6f - i * 0.05f) * s / 2, al)
    }
    frame.windowLight.add(sprites.glows.star, x - 10 * s, y, 250 * s / GLOW_REACH, 0.2f * fade)
    val gallop = floor((t * 9).mod(2 * PI) / (2 * PI) * GALLOP_FRAMES).toInt().coerceIn(0, GALLOP_FRAMES - 1)
    frame.snow.addThrough(v.sleighs[gallop], frame.turns[0].reset().translate(x, y).scale(s).rotate(ang * 0.85f - 0.03f), 1f, fade)
}
