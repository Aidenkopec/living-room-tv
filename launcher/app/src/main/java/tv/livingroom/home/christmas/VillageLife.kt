package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The rest of the village's life for one frame: the snowball fight and the
 * sledgers in the morning and afternoon, carollers in the evening, skaters
 * from noon, the brazier's flicker, steam off the stalls, the fox at night,
 * the gingerbread people and the letter to Santa. Each is the mockup's
 * timeline (villageWorld), number for number, as a function of the time.
 * Plain Kotlin: the same functions run in JVM tests.
 */

// Where each of them is kept in VillageCast, after the walkers and the dog walk.
internal val SNOWBALL_CAST = WALKERS.size + 1
internal val SLEDGE_CAST = SNOWBALL_CAST + 1
internal val CAROL_CAST = SLEDGE_CAST + 1
internal val POND_CAST = CAROL_CAST + 1
internal val FOX_CAST = POND_CAST + 1
internal val GINGER_CAST = FOX_CAST + 1
internal val STALL_CAST = GINGER_CAST + 3 // The gingerbread people take three.
internal val WARMER_CAST = STALL_CAST + 1

/** The gingerbread people who scamper through (not at night): where on their lap they start, their pace, and how big they are. */
class Gingerbread(val start: Float, val speed: Float, val lift: Float, val size: Float, val phase: Float, val waves: Boolean = false)

val GINGERBREAD = listOf(
    Gingerbread(200f, 30f, 0f, 0.34f, 0f, waves = true),
    Gingerbread(260f, 30f, -3f, 0.3f, 0.6f),
    Gingerbread(1300f, -24f, -6f, 0.32f, 1.3f),
)

/** How many in the cast: the walkers, the dog walk and these. */
val CAST_SIZE = WARMER_CAST + 1

private val DAY = setOf(VillageHours.MORNING, VillageHours.AFTERNOON)
private val LATER = setOf(VillageHours.AFTERNOON, VillageHours.EVENING)

/**
 * The snowball fight at [t] seconds, in [into]: which child throws (0 the left, 1 the right) in `[0]`, and how far into their
 * throw it is in `[1]`, seconds. They take turns, 1.7 s each.
 */
fun snowballRound(t: Double, into: FloatArray) {
    val round = t.mod(SNOWBALL_ROUND)
    val second = round >= SNOWBALL_ROUND / 2
    into[0] = if (second) 1f else 0f
    into[1] = (if (second) round - SNOWBALL_ROUND / 2 else round).toFloat()
}

/** Which of a snowball child's frames shows: throwing ([THROW_ARMS], over 0.3 s), resting, or ducking as the ball comes. */
fun snowballFrame(throwing: Boolean, u: Float): Int = when {
    throwing -> ((u / 0.3f).coerceIn(0f, 1f) * (THROW_ARMS.size - 1)).roundToInt()
    u > 0.75f && u < 1.25f -> THROW_ARMS.size + 1
    else -> THROW_ARMS.size
}

/** Adds the snowball fight, if it's out (morning and afternoon): the two children, the ball in flight and its burst of snow. */
fun snowballInto(frame: MotionFrame, sprites: MotionSprites, cast: VillageCast, hours: VillageHours, t: Double) {
    if (!cast.settleInPlace(SNOWBALL_CAST, hours in DAY)) return
    val at = frame.scratch
    snowballRound(t, at)
    val thrower = at[0].toInt()
    val u = at[1]
    val kids = sprites.life.snowballers
    for (i in SNOWBALLERS.indices) {
        val feet = SNOWBALLERS[i].second
        frame.lit.addFacing(kids[i][snowballFrame(i == thrower, u)], feet.first, feet.second, flipped = i == 1)
    }
    val left = SNOWBALLERS[0].second.first
    val right = SNOWBALLERS[1].second.first
    val from = if (thrower == 1) right - 12 else left + 12
    val to = if (thrower == 1) left + 22 else right - 6
    val ball = sprites.flakes[NEAR]
    val y = SNOWBALLERS[0].second.second
    if (u > 0.3f && u < 1f) {
        val f = ((u - 0.3f) / 0.7f).coerceIn(0f, 1f)
        frame.plain.add(ball, lerp(from, to, f), y - 36 - sin(f * PI.toFloat()) * 44, 4.5f / FLAKE_DISCS[NEAR])
    }
    if (u >= 1f && u < 1.45f) {
        val q = (u - 1f) / 0.45f
        for (i in 0 until 7) {
            frame.plain.add(
                ball,
                to + kotlin.math.cos(i * 0.9f) * q * 26,
                y - 34 + sin(i * 0.9f) * q * 20 + q * q * 14,
                (3 - q * 1.5f) / FLAKE_DISCS[NEAR],
                1 - q,
            )
        }
    }
}

/**
 * A sledger at [t] seconds, starting [offset] seconds into their round, in [into]: x, y (the sledge's runners), how big
 * they're drawn, 1 if they're on the sledge, 1 if going down, and how far down the hill (0 top, 1 bottom). A 9.2 s round:
 * down in 2 s, getting faster; 0.7 s at the bottom; 6.5 s back up.
 */
fun sledgerAt(t: Double, offset: Double, into: FloatArray) {
    val tau = (t + offset).mod(SLEDGE_ROUND).toFloat()
    val down = tau < 2f
    val u = when {
        down -> (tau / 2f).pow(1.7f)
        tau < 2.7f -> 1f
        else -> 1 - (tau - 2.7f) / 6.5f
    }
    into[0] = lerp(44f, 208f, u)
    into[1] = lerp(752f, 874f, u)
    into[2] = personScale(into[1]) * 0.95f
    into[3] = if (tau < 2.7f || tau > 8.9f) 1f else 0f
    into[4] = if (down) 1f else 0f
    into[5] = u
}

/** Where sledger [i] is standing (their sledge's runners) at [t] seconds: where they come in the order things are drawn. */
fun sledgerY(i: Int, t: Double, into: FloatArray): Float {
    sledgerAt(t, i * SLEDGE_SECOND, into)
    return into[1]
}

/**
 * Adds sledger [i] if the sledgers are out (morning and afternoon): riding down with snow flying, or walking up towing the
 * sledge.
 */
fun sledgerInto(frame: MotionFrame, sprites: MotionSprites, cast: VillageCast, hours: VillageHours, t: Double, i: Int) {
    if (!cast.settleInPlace(SLEDGE_CAST, hours in DAY)) return
    val at = frame.scratch
    val life = sprites.life
    sledgerAt(t, i * SLEDGE_SECOND, at)
    val x = at[0]
    val y = at[1]
    val sc = at[2]
    val k = sc / SLEDGER_SCALE
    val riding = at[3] > 0f
    val sx = if (riding) x else x + 20
    val sy = if (riding) y else y + 14
    frame.lit.add(life.things.sledge, onPixel(sx), sy, k)
    val frames = life.sledgers[i]
    if (riding) {
        val pose = if (at[4] > 0f) 1 + tailFrame(sin(t * WAVE_RATE).toFloat()) else 0
        frame.lit.add(frames.riding[pose], onPixel(x - 2), y + 9 * sc, k)
        if (at[4] > 0f && at[5] > 0.2f) {
            for (j in 0 until 5) {
                frame.plain.add(
                    sprites.flakes[NEAR],
                    x - 20 - j * 9,
                    y - 4 - j * 3 + sin(t * 20 + j).toFloat() * 2,
                    (4 - j * 0.5f) / FLAKE_DISCS[NEAR],
                    0.6f - j * 0.1f,
                )
            }
        }
    } else {
        frame.lit.addFacing(frames.climbing[strideFrame(t * 1.4)], onPixel(x), y, flipped = true, scale = k)
        rope(frame.lit, life.things.rope, x + 6, y - 26 * sc, sx + 18 * sc * SLEDGE_LONG, sy - 5)
    }
}

/** [rope] stretched and turned from ([x0], [y0]) to ([x1], [y1]). */
private fun rope(quads: SpriteQuads, rope: Sprite, x0: Float, y0: Float, x1: Float, y1: Float) {
    quads.addPlaced(rope, x0, y0, Stretch(hypot(x1 - x0, y1 - y0) / ROPE_LENGTH, 1f), atan2(y1 - y0, x1 - x0))
}

/** How wide caroller [i] (at [x]) is singing at [t] seconds, 0 to 1, and how high they bob, px. */
fun carolMouth(t: Double, x: Float): Float = abs(sin(t * 4 + x)).toFloat()

fun carolHop(t: Double, i: Int): Float = abs(sin(t * 1.6 + i)).toFloat() * 2

/** Adds the carollers under the lamp, if they're out (the evening), and their notes drifting up. */
fun carolsInto(frame: MotionFrame, sprites: MotionSprites, cast: VillageCast, hours: VillageHours, t: Double) {
    if (!cast.settleInPlace(CAROL_CAST, hours == VillageHours.EVENING)) return
    val singers = sprites.life.carollers
    for (i in CAROLLERS.indices) {
        val feet = CAROLLERS[i].second
        val mouth = (carolMouth(t, feet.first) * (SINGING.size - 1)).roundToInt()
        frame.lit.add(singers[i][mouth], feet.first, feet.second - carolHop(t, i))
    }
    val notes = sprites.life.things.notes
    for (i in 0 until NOTES) {
        val a = (t * 0.22 + i.toDouble() / NOTES).mod(1.0).toFloat()
        val x = 470 + i * 22 + sin(a * 5 + i) * 10
        frame.plain.add(notes[if (i % 2 == 1) 0 else 1], x, 782 - a * 86, if (i % 2 == 1) 1f else 0.75f, 0.9f * sin(a * PI.toFloat()))
    }
}

/** Where skater [i] is on the pond at [t] seconds: x, y (feet) and 1 facing right or -1 left, in [into]. */
fun skaterAt(i: Int, t: Double, into: FloatArray) {
    val a = if (i == 0) t * 0.42 else t * 0.31 + 2.4
    into[0] = POND_X + POND_RX * 0.72f * sin(a).toFloat()
    into[1] = POND_Y + POND_RY * 0.5f * sin(2 * a).toFloat() + 6
    into[2] = if (kotlin.math.cos(a) >= 0) 1f else -1f
}

/** Whether the learning skater is sitting on the ice at [t] seconds: from 8.2 to 10.4 s of every 12. */
fun learnerDown(t: Double): Boolean = t.mod(LEARNER_ROUND) in 8.2..10.4

/** Adds the skaters and the child learning, if they're out (afternoon and evening). The farther skater first. */
fun pondInto(frame: MotionFrame, sprites: MotionSprites, cast: VillageCast, hours: VillageHours, t: Double) {
    if (!cast.settleInPlace(POND_CAST, hours in LATER)) return
    val life = sprites.life
    val at = frame.scratch
    val first = if (sin(2 * t * 0.42) <= sin(2 * (t * 0.31 + 2.4))) 0 else 1
    for (n in 0 until 2) {
        val i = if (n == 0) first else 1 - first
        skaterAt(i, t, at)
        val phase = (t * 3 + i * 2) / (2 * PI)
        frame.lit.addFacing(life.skaters[i][step(phase, SKATER_STEPS)], onPixel(at[0]), at[1], flipped = at[2] < 0)
    }
    val x = POND_X + sin(t * 0.17 + 1).toFloat() * 60
    val y = POND_Y + 14
    if (learnerDown(t)) {
        frame.lit.add(life.learner[STRIDE_SWINGS.size + tailFrame(sin(t * WAVE_RATE).toFloat())], onPixel(x), y + 2)
        val q = ((t.mod(LEARNER_ROUND) - 8.2) / 0.5).toFloat()
        if (q <
            1f
        ) {
            for (j in 0 until 6) {
                frame.plain.add(
                    sprites.flakes[NEAR],
                    x + (j - 2.5f) * 9 * (0.5f + q),
                    y - q * 12,
                    3f / FLAKE_DISCS[NEAR],
                    1 - q,
                )
            }
        }
    } else {
        frame.lit.add(life.learner[strideFrame(t * 0.8)], onPixel(x + sin(t * 7).toFloat() * 2), y - abs(sin(t * 5)).toFloat() * 1.5f)
    }
}

/** Adds the brazier's flicker over its still glow (see [BRAZIER_LOW]) and, unless it's burning low at night, its rising sparks. */
fun brazierInto(frame: MotionFrame, sprites: MotionSprites, look: VillageLook, t: Double) {
    val fire = brazierFire(look)
    val flicker = 0.8f + 0.2f * (sin(t * 9) * sin(t * 3.1)).toFloat()
    val glow = sprites.life.things.fireGlow
    frame.lights.add(glow, BRAZIER_X, BRAZIER_Y - 54, 120f / FIRE_GLOW, 0.6f * fire * (flicker - BRAZIER_LOW))
    if (fire <= 0.5f) return
    for (i in 0 until 4) {
        val a = (t * 0.6 + i / 4.0).mod(1.0).toFloat()
        frame.lights.add(sprites.lights[WARM_LIGHT], BRAZIER_X + sin(a * 7 + i * 2) * 10, BRAZIER_Y - 58 - a * 50, 7f / BULB_REACH, 1 - a)
    }
}

/**
 * Adds the stallholders behind their counters with steam rising off the hot drinks, while the market is open (not at night),
 * and back to front with the rest. The brazier's keeper is one of them too, but stands nearer: see [brazierPeopleInto].
 */
fun stallsInto(frame: MotionFrame, sprites: MotionSprites, cast: VillageCast, hours: VillageHours, t: Double) {
    if (!cast.settleInPlace(STALL_CAST, hours != VillageHours.NIGHT)) return
    for (i in 0 until 2) bystanderInto(frame, sprites, i)
    for (x in STALLS) {
        for (i in 0 until 3) {
            val a = (t * 0.3 + i / 3.0).mod(1.0).toFloat()
            frame.lit.add(sprites.puff, x + 40 + sin(a * 5 + i) * 6, 768 - a * 44, (4 + a * 7) / PUFF_DISC, 0.45f * sin(a * PI.toFloat()))
        }
    }
}

/** Adds the brazier's keeper behind it (not at night) and the two people warming their hands at it (afternoon and evening). */
fun brazierPeopleInto(frame: MotionFrame, sprites: MotionSprites, cast: VillageCast, hours: VillageHours) {
    if (cast.settleInPlace(STALL_CAST, hours != VillageHours.NIGHT)) bystanderInto(frame, sprites, KEEPERS - 1)
    if (cast.settleInPlace(WARMER_CAST, hours in LATER)) for (i in KEEPERS until BYSTANDERS.size) bystanderInto(frame, sprites, i)
}

private fun bystanderInto(frame: MotionFrame, sprites: MotionSprites, i: Int) {
    val b = BYSTANDERS[i]
    frame.lit.addFacing(sprites.life.standing[i], b.x, b.y, flipped = b.flip)
}

/** Where the fox is at [t] seconds: trotting left across the square at 44 px a second, a lap every minute or so. */
fun foxX(t: Double): Float = (VILLAGE_W + 120 - (t * FOX_PACE + 700).mod((VILLAGE_W + 700).toDouble())).toFloat()

/** Adds the fox, if it's out (at night). */
fun foxInto(frame: MotionFrame, sprites: MotionSprites, cast: VillageCast, hours: VillageHours, t: Double) {
    val x = foxX(t)
    if (!cast.settle(FOX_CAST, offPicture(x, FOX_REACH), hours == VillageHours.NIGHT)) return
    frame.lit.add(sprites.life.fox[step(t * 9 / (2 * PI), FOX_STEPS)], onPixel(x), FOX_Y)
}

/** Adds the gingerbread people scampering through, when they're out (not at night). */
fun gingerbreadInto(frame: MotionFrame, sprites: MotionSprites, cast: VillageCast, hours: VillageHours, t: Double, i: Int) {
    val g = GINGERBREAD[i]
    val x = ((g.start + t * g.speed).mod(LAP_SPAN.toDouble()) - LAP_EDGE).toFloat()
    if (!cast.settle(GINGER_CAST + i, offPicture(x, GINGER_REACH), hours != VillageHours.NIGHT)) return
    val walk = t * abs(g.speed) * 0.045 + g.phase
    val waving = g.waves && sin(t * 0.5 + g.phase) > 0.75
    val sprite = sprites.life.gingerbread[step(walk / 2, GINGER_WALK_FRAMES) + if (waving) GINGER_WALK_FRAMES else 0]
    frame.lit.addFacing(sprite, onPixel(x), gingerbreadY(i), flipped = g.speed < 0, scale = g.size / VILLAGE_GINGER_SCALE)
}

/** Where gingerbread person [i]'s feet are. */
fun gingerbreadY(i: Int): Float = 888 + GINGERBREAD[i].lift

/**
 * The letter to Santa [a] seconds after it's posted, in [into]: x, y, and how strongly it shows. It flies up and away to the
 * north in 3.2 s, fading at the end.
 */
fun letterAt(a: Float, into: FloatArray) {
    into[0] = 852 + 70 * a * a
    into[1] = 786 - 34 * a - 150 * a * a
    into[2] = (LETTER_FLIGHT - a).coerceIn(0f, 1f)
}

/** Adds the letter to Santa, from 1.2 s into its child's stop at the post box: the envelope and its trail of sparkles. */
fun letterInto(frame: MotionFrame, sprites: MotionSprites, cast: VillageCast, t: Double) {
    val at = frame.scratch
    walkerAt(WALKERS[LETTER_WALKER], t, at)
    val a = at[1] - 1.2f
    if (!cast.out[LETTER_WALKER] || a <= 0f || a >= LETTER_FLIGHT) return // Not stopped yet, or done.
    val things = sprites.life.things
    for (j in 1 until 7) {
        val b = maxOf(0f, a - j * 0.05f)
        letterAt(b, at)
        val size = 5 - j * 0.5f
        val alpha = (1 - j / 7f) * (LETTER_FLIGHT - a).coerceIn(0f, 1f)
        frame.lights.add(sprites.lights[HALO_LIGHT], at[0], at[1], size * 5 / HALO_REACH, alpha * 0.8f)
        frame.plain.addPlaced(things.star, at[0], at[1], Stretch(size / SPARKLE_STAR, size / SPARKLE_STAR), (j + t * 3).toFloat(), alpha)
    }
    letterAt(a, at)
    frame.plain.addPlaced(things.letter, at[0], at[1], Stretch(1f, 1f), -0.4f + sin(a * 6) * 0.2f, at[2])
}

/** The walker who posts the letter. */
val LETTER_WALKER = WALKERS.indexOfFirst { it.letter }

private const val SNOWBALL_ROUND = 3.4
private const val SLEDGE_ROUND = 9.2
private const val SLEDGE_SECOND = 4.6 // The second sledger's round starts this far into the first's.
private const val LEARNER_ROUND = 12.0
private const val LETTER_FLIGHT = 3.2f
private const val NOTES = 5
private const val NEAR = 2 // The near snow's disc: a snowball and its burst are drawn from it.
private const val WAVE_RATE = 9.0
private const val FOX_PACE = 44.0
private const val FOX_Y = 874f
private const val FOX_REACH = 70f
private const val GINGER_REACH = 30f
private const val LAP_SPAN = VILLAGE_W + 2 * LAP_EDGE
private val STALLS = floatArrayOf(730f, 1240f)

/** The skating pond: its middle and its ice's half width and height. */
const val POND_X = 142f
const val POND_Y = 990f
const val POND_RX = 118f
const val POND_RY = 40f
