package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/*
 * The village's now-and-then things: the little train across the viaduct, the
 * sleigh rising past the moon after dark, and shooting stars on clear nights.
 * Each comes once in every slot of the motion clock's time, at a seeded moment
 * within the slot's first part, so the gaps between them are as the design
 * says without any state: the same time always gives the same answer.
 */

/** A number from 0 up to 1 picked by [seed]: the first mulberry32 gives for it (`Mulberry(seed).next()`, made in place). */
fun seeded(seed: Int): Float = mulberryMix(seed + MULBERRY_STEP)

/** A rare event: one in every [slot] seconds, starting up to [spread] seconds into it, lasting [length]; [seed] sets the moments. */
class EventSlots(val slot: Double, val spread: Double, val length: Double, val seed: Int) {
    init {
        require(spread + length < slot) { "An event has to end before the next slot's can start" }
    }

    /** When the event in slot [k] starts, seconds. */
    fun start(k: Long): Double = k * slot + spread * seeded(seed xor (k * SLOT_MIX).toInt())

    /** How far into an event [t] seconds is, or NaN if none is under way. */
    fun age(t: Double): Double {
        if (t < 0) return Double.NaN
        val age = t - start(floor(t / slot).toLong())
        return if (age >= 0 && age < length) age else Double.NaN
    }

    /** Which slot the event under way at [t], [age] seconds in, belongs to (picks its own place or path). */
    fun slotOf(t: Double, age: Double): Long = floor((t - age) / slot).toLong()
}

/**
 * The train: every 6 to 10 minutes, across in 25.6 s. The sleigh: every 20 to 30 minutes after dark, 12 s. Shooting stars:
 * every 1.5 to 4.5 minutes.
 */
val TRAIN = EventSlots(slot = 480.0, spread = 120.0, length = (VILLAGE_W + 120 + 420) / TRAIN_PACE.toDouble(), seed = 11)
val SLEIGH = EventSlots(slot = 1500.0, spread = 300.0, length = 12.0, seed = 23)
val SHOOTING_STAR = EventSlots(slot = 180.0, spread = 90.0, length = 0.9, seed = 37)

/** The train's front at [age] seconds into its crossing: from just off the right edge, leftward at [TRAIN_PACE] px a second. */
fun trainX(age: Double): Float = (VILLAGE_W + 120 - age * TRAIN_PACE).toFloat()

/**
 * How far ahead of the train's front and behind it the village's front is drawn over it again: ahead, past its lamp's glow;
 * behind, past its last coach and the steam it leaves.
 */
const val STRIP_AHEAD = 52f
const val STRIP_BEHIND = 720f

/** The rails' height, and how fast the train goes. */
const val RAIL_Y = 512f
const val TRAIN_PACE = 96f

/**
 * Adds the train if it's crossing at [t] seconds, with its lit windows and lamp as strong as the village's [lights], and a puff
 * of steam every 0.4 s rising behind it. Gives back where its front is (NaN if it isn't out), so the village's front can be
 * painted over it there.
 */
fun trainInto(frame: MotionFrame, sprites: MotionSprites, lights: Float, t: Double): Float {
    val age = TRAIN.age(t)
    if (age.isNaN()) return Float.NaN
    val x = trainX(age)
    val things = sprites.life.things
    for (i in 0 until STEAM_PUFFS) {
        val made = (floor(age / STEAM_EVERY) - i) * STEAM_EVERY
        if (made < 0) continue
        val old = (age - made).toFloat()
        // Lighter and smaller than the mockup's (7 + 6 px a second, 0.5 strong): on the TV that read as a heavy row of discs.
        val strength = maxOf(0f, STEAM_STRENGTH - old * STEAM_FADE)
        frame.far.add(sprites.puff, trainX(made) + 12 + old * 6, RAIL_Y - 38 - old * 15, (6 + old * 4) / PUFF_DISC, strength)
    }
    frame.far.add(things.train, onPixel(x), RAIL_Y)
    frame.farGlass.add(things.trainLights, onPixel(x), RAIL_Y, alpha = (lights * 1.2f).coerceIn(0f, 1f))
    if (lights > 0.3f) {
        for (i in 0 until TRAIN_COACHES) {
            frame.farLights.add(
                sprites.lights[HALO_LIGHT],
                x + COACH_LEFT + i * COACH_PITCH + 32,
                RAIL_Y - 20,
                46f / HALO_REACH,
                0.25f * lights,
            )
        }
    }
    frame.farLights.add(sprites.lights[HALO_LIGHT], x - 6, RAIL_Y - 14, 40f / HALO_REACH, 0.85f * maxOf(0.3f, lights))
    return x
}

/** The sleigh's way up from behind Mount Rundle, past the moon and off the top right: the mockup's `RISE`. */
private const val RISE_X0 = 620f
private const val RISE_Y0 = 650f
private const val RISE_X1 = 2260f
private const val RISE_Y1 = -20f
private const val RISE_ARC = 70f
const val SLEIGH_SIZE = 1.15f

/** Where the sleigh is [u] of the way along its rise: x in [into]`[0]`, y in `[1]`. */
fun sleighAt(u: Float, into: FloatArray) {
    into[0] = lerp(RISE_X0, RISE_X1, u)
    into[1] = lerp(RISE_Y0, RISE_Y1, u) - sin(u.coerceIn(0f, 1f) * PI.toFloat()) * RISE_ARC
}

/**
 * How much of the sleigh (or a sparkle it left) shows [u] of the way along. In the design it rises from behind the village; drawn
 * over the still picture it can't go behind anything, so it fades in once it's clear of the church's steeple and over the
 * mountains, and its trail starts there too.
 */
fun sleighShows(u: Float): Float = smooth((u - SLEIGH_CLEARS) / SLEIGH_FADE)

/**
 * Adds the sleigh if it's [t] seconds into a rise and it was [dark] when it was first seen: its glow, its trail of sparkles, and
 * the sleigh itself.
 */
fun villageSleighInto(frame: MotionFrame, sprites: MotionSprites, dark: Boolean, t: Double) {
    val age = SLEIGH.age(t)
    if (age.isNaN()) return
    // Whether it flies is settled when it's first seen, so dusk or dawn coming on the minute can't make it vanish part way.
    val slot = SLEIGH.slotOf(t, age)
    if (slot != frame.sleighSlot) {
        frame.sleighSlot = slot
        frame.sleighFlies = dark
    }
    if (!frame.sleighFlies) return
    val u = (age / SLEIGH.length).toFloat()
    val fade = sleighShows(u)
    if (fade <= 0f) return
    val s = SLEIGH_SIZE
    val at = frame.scratch
    for (i in 0 until TRAIL) {
        val left = u - 0.0075f * (i + 1.5f) - 0.06f * s
        sleighAt(left, at)
        val alpha = (1 - i.toFloat() / TRAIL) * 0.95f * sleighShows(left)
        val twinkle = 0.6f + 0.4f * sin(t * 7 + i * 2.3).toFloat()
        val x = at[0] + sin(i * 2.1 + t * 2).toFloat() * 7 * s
        val y = at[1] + 14 * s + cos(i * 1.7 + t * 3).toFloat() * 9 * s + i * 0.9f * s
        frame.lights.add(sprites.lights[HALO_LIGHT], x, y, (22 - i * 0.4f) * s / HALO_REACH, alpha * 0.8f * twinkle)
        frame.plain.add(sprites.flakes[MIDDLE], x, y, (2.6f - i * 0.05f) * s / FLAKE_DISCS[MIDDLE], alpha)
    }
    sleighAt(u + 0.01f, at)
    val aheadX = at[0]
    val aheadY = at[1]
    sleighAt(u - 0.01f, at)
    val climb = atan2(aheadY - at[1], abs(aheadX - at[0]))
    sleighAt(u, at)
    val x = at[0]
    val y = at[1] + sin(u * 22) * 4 * s
    frame.lights.add(sprites.lights[HALO_LIGHT], x - 10 * s, y, 250 * s / HALO_REACH, 0.2f * fade)
    val gallop = step(t * 9 / (2 * PI), GALLOP_FRAMES)
    frame.plain.addThrough(sprites.life.sleigh[gallop], frame.turn.reset().translate(x, y).scale(s).rotate(climb * 0.85f - 0.03f), 1f, fade)
}

/**
 * Adds a shooting star if one is falling at [t] seconds and the night is [starry] (dark and clear): a streak across the top
 * right of the sky, clear of the clock, its place picked by its slot.
 */
fun shootingStarInto(frame: MotionFrame, sprites: MotionSprites, starry: Boolean, t: Double) {
    if (!starry) return
    val age = SHOOTING_STAR.age(t)
    if (age.isNaN()) return
    val a = (age / SHOOTING_STAR.length).toFloat()
    val slot = SHOOTING_STAR.slotOf(t, age).toInt()
    val x = STAR_FROM_X + STAR_SPREAD_X * seeded(slot * 2 + 1) - a * 420
    val y = STAR_FROM_Y + STAR_SPREAD_Y * seeded(slot * 2 + 2) + a * 150
    frame.lights.addPlaced(sprites.life.things.streak, x, y, Stretch(1f, 1f), STREAK_TURN, sin(a * PI.toFloat()))
}

/** Whether shooting stars can be seen in [look] with [clear] skies: only when the stars are out. */
fun starry(look: VillageLook, clear: Boolean): Boolean = clear && look.stars > 0.5f

/** Whether the sleigh can fly in [look]: after dark. */
fun sleighWeather(look: VillageLook): Boolean = look.stars > 0.5f

private const val SLOT_MIX = -0x61c88647 // Spreads one slot's seed far from the next one's.
private const val STEAM_PUFFS = 16
private const val STEAM_EVERY = 0.4
private const val STEAM_STRENGTH = 0.35f
private const val STEAM_FADE = 0.07f
private const val TRAIL = 26
private const val MIDDLE = 1 // The middle snow's disc: the sleigh's sparkles are drawn from it.
private const val SLEIGH_CLEARS = 0.42f
private const val SLEIGH_FADE = 0.1f
private const val STAR_FROM_X = 1100f
private const val STAR_SPREAD_X = 600f
private const val STAR_FROM_Y = 60f
private const val STAR_SPREAD_Y = 60f // So a star ends above Mount Rundle's peak (about y 300): it can't go behind it.
private val STREAK_TURN = atan2(-54f, 150f) // The tail points up and right, back along the way it came.
