package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/*
 * The village's smaller moving things as marks, each in its own units with its
 * anchor at (0, 0), ported shape for shape from the mockup (skater, fox, train,
 * the sledge, the carollers' notes, the letter to Santa, a shooting star).
 * Plain daylight colours: the moving layer tints them for the hour.
 */

/** A skater gliding, facing right, feet at (0, 0), in [coat]: [kick] (-1 to 1) is where the free leg and arms are in their swing. */
fun skaterMarks(coat: Int, kick: Float): List<Mark> = Sketch().apply {
    oval(ink(0x141E50, 0.2f), Ellipse(0f, 2f, 16f, 4f))
    line(ink(0x2B2F44), 5f) { through(0f, -24f, 6f, 0f).through(-2f, -24f, -16 - kick * 6, -6 - abs(kick) * 5) }
    line(ink(coat), 13f) { through(-1f, -26f, 7f, -46f) }
    line(ink(coat), 5f) { through(6f, -42f, 20 + kick * 4, -34f).through(5f, -42f, -10f, -36 + kick * 4) }
    disc(ink(0xF1CFB4), 11f, -56f, 7.5f)
    fill(ink(0xD93A3A)) { arc(Ellipse(11f, -58f, 8f, 8f), PI.toFloat(), 2 * PI.toFloat()).close() }
    disc(ink(0xD93A3A), 11f, -67f, 3f)
    line(ink(0xF0B83A), 4f) { moveTo(6f, -49f).quadTo(-8f, -50 + kick * 3, -18f, -46 + kick * 6) }
}.marks

/** The fox trotting, facing left, feet at (0, 0): [phase] (radians) is where its legs are in their trot. */
fun foxMarks(phase: Float): List<Mark> = Sketch().apply {
    placed(0f, -abs(sin(phase)) * 2) {
        oval(ink(PERSON_SHADOW, 0.5f), Ellipse(4f, 1f, 40f, 5f))
        for ((x, k) in listOf(-18f to 0, -12f to 1, 16f to 1, 22f to 0)) {
            line(ink(0x3A2A22), 5f) { through(x, -18f, x + sin(phase + k * PI.toFloat()) * 9, -2f) }
        }
        val fur = ink(0xDD7A2E)
        val white = ink(0xFFF4E6)
        oval(fur, Ellipse(40f, -27f, 24f, 9.5f, -0.25f))
        oval(white, Ellipse(58f, -32f, 9f, 6.5f, -0.25f))
        oval(fur, Ellipse(0f, -24f, 30f, 11.5f))
        oval(white, Ellipse(-22f, -19f, 10f, 8f))
        disc(fur, -32f, -34f, 11f)
        shape(fur, -40f, -40f, -56f, -30f, -40f, -27f)
        shape(fur, -40f, -42f, -37f, -56f, -30f, -43f)
        shape(fur, -30f, -44f, -25f, -57f, -22f, -42f)
        shape(white, -42f, -33f, -55f, -29.5f, -41f, -27f)
        disc(ink(0x20140E), -56f, -30f, 2.2f)
        disc(ink(0x20140E), -37f, -36f, 1.8f)
    }
}.marks

/** How many coaches the train pulls, and how far apart they are. */
const val TRAIN_COACHES = 4
const val COACH_PITCH = 70f

/**
 * The little train, facing left: an engine and [TRAIN_COACHES] coaches with dark windows and snow on their roofs. (0, 0) is the
 * front of the engine, on the rails.
 */
fun trainMarks(): List<Mark> = Sketch().apply {
    val snow = ink(0xF6F9FF)
    val dark = ink(0x2A3A5C)
    for (i in 0 until TRAIN_COACHES) {
        val x = COACH_LEFT + i * COACH_PITCH
        roundBox(ink(if (i % 2 == 1) 0x2F6F8F else 0xA8323A), Area(x, -28f, 64f, 24f), 5f)
        box(ink(0xF1E6D6), x, -12f, 64f, 3f)
        for (j in 0 until 4) box(dark, x + 6 + j * 14, -24f, 9f, 9f)
        roundBox(snow, Area(x - 1, -32f, 66f, 6f), 3f)
    }
    roundBox(ink(0x2F5A48), Area(0f, -25f, 50f, 21f), 8f)
    box(ink(0x2F5A48), 44f, -35f, 24f, 31f)
    box(ink(0x1F2028), 9f, -37f, 9f, 14f)
    shape(ink(0x1F2028), -11f, -2f, 2f, -13f, 2f, -2f)
    box(ink(0xC9413F), 0f, -7f, 68f, 4f)
    box(dark, 50f, -31f, 12f, 9f)
    roundBox(snow, Area(42f, -40f, 28f, 6f), 3f)
    disc(ink(0x6A5A3A), 1f, -14f, 3.6f)
    for (i in 0 until 12) disc(ink(0x1F2028), 10 + i * 28.6f, -2f, 4.6f)
}.marks

/** The train's lit windows and lamp, to add over [trainMarks] in the same place, as strong as the village's lights. */
fun trainLightMarks(): List<Mark> = Sketch().apply {
    val glass = ink(0xFFD27A)
    for (i in 0 until TRAIN_COACHES) for (j in 0 until 4) box(glass, COACH_LEFT + i * COACH_PITCH + 6 + j * 14, -24f, 9f, 9f)
    box(glass, 50f, -31f, 12f, 9f)
    disc(ink(0xFFF0C0), 1f, -14f, 3.6f)
}.marks

/** Where the first coach starts behind the engine's front. */
const val COACH_LEFT = 74f

/** How long the train is, front of the engine to the end of the last coach. */
const val TRAIN_LENGTH = COACH_LEFT + (TRAIN_COACHES - 1) * COACH_PITCH + 64

/** A red sledge on its runners, (0, 0) the middle of its seat's foot, [long] times the mockup's length for a child its size. */
fun sledgeMarks(long: Float): List<Mark> = Sketch().apply {
    line(ink(0x5A3A26), 2.5f) { moveTo(-16 * long, 1f).lineTo(14 * long, 1f).quadTo(22 * long, 0f, 20 * long, -6f) }
    roundBox(ink(0xC9302C), Area(-15 * long, -7f, 28 * long, 5f), 2f)
}.marks

/** A length of the sledge's rope: [ROPE_LENGTH] long, its anchor at its left end, to be stretched and turned into place. */
fun ropeMarks(): List<Mark> = Sketch().apply { box(ink(0x5A3A26), 0f, -0.75f, ROPE_LENGTH, 1.5f) }.marks

const val ROPE_LENGTH = 10f

/** A sung note as the carollers' are drawn, about 24 px high: one quaver, or two beamed (with [two]); (0, 0) is the first head. */
fun noteMarks(two: Boolean): List<Mark> = Sketch().apply {
    val ink = ink(0xFFF4E6)
    oval(ink, Ellipse(0f, 0f, 5f, 3.8f, -0.35f))
    line(ink, 1.8f) { through(4.4f, -1f, 4.4f, -18f) }
    if (two) {
        oval(ink, Ellipse(11f, -3f, 5f, 3.8f, -0.35f))
        line(ink, 1.8f) { through(15.4f, -4f, 15.4f, -21f) }
        line(ink, 3.5f, round = false) { through(4.4f, -17f, 15.4f, -20f) }
    } else {
        line(ink, 2f) { moveTo(4.4f, -18f).quadTo(11f, -13f, 9.5f, -6f) }
    }
}.marks

/** A letter to Santa: a cream envelope with its red flap, 18 x 12, (0, 0) at its middle. */
fun letterMarks(): List<Mark> = Sketch().apply {
    box(ink(0xFFF8EA), -9f, -6f, 18f, 12f)
    line(ink(0xC9302C), 1.5f) { through(-9f, -6f, 0f, 1f, 9f, -6f) }
}.marks

/** A small gold star for sparkles, [SPARKLE_STAR] across its points' reach. */
fun sparkleStarMarks(): List<Mark> = Sketch().apply { fill(ink(0xFFF6CE)) { star(0f, 0f, SPARKLE_STAR) } }.marks

const val SPARKLE_STAR = 6f

/** A shooting star: a bright head at (0, 0) and a tail [STREAK_LENGTH] long toward +x, fading to nothing. */
fun streakMarks(): List<Mark> = Sketch().apply {
    fill(fade(0f, 0f, STREAK_LENGTH, 0f, Stop(0f, ink(0xFFFFFF, 0.9f)), Stop(1f, ink(0xFFFFFF, 0f)))) {
        polygon(0f, -1.4f, STREAK_LENGTH, -0.3f, STREAK_LENGTH, 0.3f, 0f, 1.4f)
    }
}.marks

const val STREAK_LENGTH = 160f

/** The brazier's flickering light: a soft orange glow [FIRE_GLOW] across, as strong as a glow mark can be at its middle. */
fun fireGlowMarks(): List<Mark> = Sketch().apply { glow(0f, 0f, FIRE_GLOW, 0xFF8A3A, 1f) }.marks

const val FIRE_GLOW = 40f
