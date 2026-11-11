package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import tv.livingroom.home.screensaver.mix

/*
 * The village's people, drawn as marks at life size (a grown-up
 * is about 120 units tall) with the origin on the ground under them. Colours are
 * the plain daylight ones: whoever draws a figure tints it for the hour's light,
 * so one set of pictures serves every look. Poses are plain numbers, so a walk or
 * a wag is a handful of ready-drawn frames.
 */

/** What a villager has in the free hand, or is doing with it. */
enum class Carry { NONE, BAG, PARCELS, TREE, CUP, BOOK, WAVE, THROW, WARM, LEAD }

/**
 * What a villager wears and looks like, as 0xRRGGBB colours; a [kid] is shorter, and one who [run]s takes longer strides with
 * an arm pumping. Most wear a woolly hat; with a [hair] colour the hair shows under it, falling down the back if [longHair], or
 * with a [bareHead] short hair instead of the hat. [boots] are the footwear's colour, up to the knee if [tallBoots]; the coat
 * reaches [coatLength] below the hips; and [tall] stretches the whole figure (1 is everyone's height).
 */
data class Villager(
    val coat: Int = 0xC9413F,
    val hat: Int = 0x2F6F8F,
    val scarf: Int = 0xF0B83A,
    val skin: Int = 0xF3CDB0,
    val legs: Int = 0x2B2F44,
    val mitt: Int = 0xF4EFE6,
    val bag: Int = 0xF0B83A,
    val kid: Boolean = false,
    val run: Boolean = false,
    val hair: Int? = null,
    val longHair: Boolean = false,
    val bareHead: Boolean = false,
    val boots: Int = 0x1F2028,
    val tallBoots: Boolean = false,
    val coatLength: Float = 9f,
    val tall: Float = 1f,
)

/**
 * How a villager stands at one instant. [swing] is the stride: the sine of the walk's phase, -1 to 1,
 * and 0 standing still. [arm] is the free arm's angle (radians from straight down, forward positive)
 * for [Carry.WAVE] and [Carry.THROW], and a small offset from the resting angle for [Carry.WARM].
 * [sing] opens the mouth 0 to 1 (null: closed). [duck] crouches (1 is a full duck). [flutter], -1 to 1,
 * is where the scarf's tail is in its flapping.
 */
data class PersonPose(
    val swing: Float = 0f,
    val carry: Carry = Carry.NONE,
    val arm: Float = 0f,
    val sing: Float? = null,
    val duck: Float = 0f,
    val flutter: Float = 0f,
)

/** The colour of a villager's shadow on the snow before it's tinted: tinted for the hour, it comes out near the snow's own shade colour. */
const val PERSON_SHADOW = 0xB6C8EA

/**
 * [who] facing right, with (0, 0) on the ground between the feet and the shadow under them. A stride
 * lifts the whole figure a little at its ends, which is included.
 */
fun personMarks(who: Villager, pose: PersonPose): List<Mark> = Sketch().apply { person(who, pose) }.marks

/** [who] in [pose] as [personMarks] draws them, into this sketch under its current placement. */
fun Sketch.person(who: Villager, pose: PersonPose) {
    val build = Build(who.kid)
    placed(0f, -abs(pose.swing) * STRIDE_BOB) {
        squashed((1 - pose.duck * DUCK_SQUASH) * who.tall) {
            oval(ink(PERSON_SHADOW, 0.55f), Ellipse(0f, 1f, 19f, 5f))
            legs(who, build, pose.swing)
            if (pose.carry == Carry.TREE) shoulderTree(build, pose.swing)
            coatAndScarf(who, build, pose.flutter)
            personHead(who, build, pose.sing)
            armAndLoad(who, build, pose)
        }
    }
}

/** Where [who]'s free hand is in [pose], in [personMarks]' units: where a lead or a sledge's rope starts. */
fun personHand(who: Villager, pose: PersonPose): Pair<Float, Float> {
    val (x, y) = Build(who.kid).hand(freeArm(who, pose))
    return x to y * (1 - pose.duck * DUCK_SQUASH) * who.tall - abs(pose.swing) * STRIDE_BOB
}

/** A villager's proportions: a kid's or a grown-up's. */
private class Build(val kid: Boolean) {
    val hipY = if (kid) -30f else -48f
    val shoulderY = if (kid) -56f else -90f
    val headY = if (kid) -71f else -104f
    val headRadius = if (kid) 12.5f else 12f
    val armLength = if (kid) 22f else 30f

    /** The hand of an [arm] (its angle, and how much of its length is stretched out) hanging from the shoulder. */
    fun hand(arm: Pair<Float, Float>): Pair<Float, Float> =
        (2 + sin(arm.first) * armLength * arm.second) to (shoulderY + 7 + cos(arm.first) * armLength * arm.second)
}

/** The free arm's angle and how far it's stretched (0 to 1) for what the hand is doing; an empty hand swings against the stride. */
private fun freeArm(who: Villager, pose: PersonPose): Pair<Float, Float> = when (pose.carry) {
    Carry.CUP -> 1.75f to 0.8f
    Carry.BOOK -> 1.45f to 0.75f
    Carry.PARCELS -> 1.25f to 0.8f
    Carry.WAVE, Carry.THROW -> pose.arm to 1f
    Carry.WARM -> 1.3f + pose.arm to 0.85f
    Carry.LEAD -> 0.95f to 0.9f
    Carry.NONE, Carry.BAG, Carry.TREE -> (if (who.run) 1.2f + pose.swing * 0.9f else -pose.swing * 0.55f) to 1f
}

private fun Sketch.legs(who: Villager, build: Build, swing: Float) {
    val stride = (if (build.kid) 9f else 13f) * (if (who.run) 1.5f else 1f)
    for (side in listOf(-1f, 1f)) {
        line(ink(who.legs), if (build.kid) 8f else 9f) { through(side * 4, build.hipY, side * 4 + side * swing * stride, -3f) }
    }
    if (who.tallBoots) {
        for (side in listOf(-1f, 1f)) {
            val footX = side * 4 + side * swing * stride
            line(ink(who.boots), 10f) { through(lerp(side * 4, footX, 0.5f), lerp(build.hipY, -3f, 0.5f), footX, -3f) }
        }
    }
    for (side in listOf(-1f, 1f)) oval(ink(who.boots), Ellipse(side * 4 + side * swing * stride + 3, -2f, 7f, 4f))
}

/** A cut tree over the shoulder, its top trailing behind; drawn before the coat so the body hides its middle. */
private fun Sketch.shoulderTree(build: Build, swing: Float) = placed(-4f, build.shoulderY - 6, turn = -0.12f + swing * 0.03f) {
    shape(ink(0x2C6A44), 10f, -6f, -62f, -2f, 10f, 14f)
    shape(ink(0x2C6A44), -6f, -10f, -48f, -3f, -6f, 16f)
    box(ink(0x5A3A26), 10f, 1f, 12f, 5f)
}

private fun Sketch.coatAndScarf(who: Villager, build: Build, flutter: Float) {
    val y = build.shoulderY
    val hem = if (build.kid) 12f else 14f
    fill(ink(who.coat)) {
        moveTo(-9f, y).quadTo(0f, y - 5, 9f, y).lineTo(hem, build.hipY + who.coatLength).lineTo(
            -hem,
            build.hipY + who.coatLength,
        ).close()
    }
    // The tail's end runs a little ahead of its middle, as cloth does: a fixed lead on the same flapping.
    val f = flutter.coerceIn(-1f, 1f)
    val tip = f * cos(SCARF_LEAD) + sqrt(1 - f * f) * sin(SCARF_LEAD)
    val reach = if (who.run) 9f else 6f
    line(ink(who.scarf), 6f) { moveTo(-5f, y + 4).quadTo(-17f, y + 7 + f * 3, -25 - reach, y + 12 + tip * 5) }
    line(ink(who.scarf), 7f) { through(-7f, y + 3, 8f, y + 3) }
}

/** The head with its hair, a woolly hat with its bobble (unless bare-headed), the eye, and a mouth when [sing]ing. */
private fun Sketch.personHead(who: Villager, build: Build, sing: Float?) {
    val y = build.headY
    val r = build.headRadius
    val hair = who.hair
    // Long hair falls behind the head and down over the coat's back.
    if (hair != null && who.longHair) {
        fill(ink(hair)) {
            moveTo(-4f, y - r).quadTo(-15f, y - 6, -14f, y + 10).quadTo(-17f, y + 24, -11f, y + 34)
            quadTo(-6f, y + 31, -3f, y + 24).quadTo(-1f, y + 14, 6f, y + 8).lineTo(10f, y - 4).close()
        }
        line(ink(mix(hair, 0x000000, 0.3f)), 1.2f) { moveTo(-10f, y + 4).quadTo(-13f, y + 18, -9f, y + 29) }
    }
    disc(ink(who.skin), 2f, y, r)
    if (hair != null) fill(ink(hair)) { arc(Ellipse(2f, y, r + 0.6f, r + 0.6f), PI.toFloat() * 0.62f, PI.toFloat() * 1.5f).close() }
    if (who.bareHead && hair != null) {
        // Short hair: over the top, a little fuller at the front.
        fill(ink(hair)) { arc(Ellipse(2f, y - 1, r + 1.4f, r + 1.6f), PI.toFloat() * 0.98f, PI.toFloat() * 1.88f).close() }
        oval(ink(hair), Ellipse(8f, y - r + 1, 5.5f, 3.2f, 0.3f))
        return personFace(y, sing)
    }
    fill(ink(who.hat)) { arc(Ellipse(2f, y - 1, r + 1.2f, r + 1.2f), PI.toFloat() * 1.04f, PI.toFloat() * 1.96f).close() }
    disc(ink(0xFFF4E6), 1f, y - r - 4, 4.4f)
    personFace(y, sing)
}

/** The eye, and the mouth when [sing]ing, for a head at height [y]. */
private fun Sketch.personFace(y: Float, sing: Float?) {
    disc(ink(0x20140E), 8.5f, y + 2, 1.7f)
    if (sing != null) oval(ink(0x5A2A22), Ellipse(10f, y + 7, 2.4f, 2.2f + 1.8f * sing.coerceIn(0f, 1f)))
}

/** The free arm with its mitten, and whatever it holds. */
private fun Sketch.armAndLoad(who: Villager, build: Build, pose: PersonPose) {
    val (hx, hy) = build.hand(freeArm(who, pose))
    line(ink(who.coat), if (build.kid) 7f else 8f) { through(2f, build.shoulderY + 7, hx, hy) }
    disc(ink(who.mitt), hx, hy, 4.6f)
    when (pose.carry) {
        Carry.CUP -> box(ink(0xF4EFE6), hx + 1, hy - 9, 8f, 9f)
        Carry.BOOK -> {
            box(ink(0xF4EFE6), hx - 2, hy - 12, 13f, 15f)
            box(ink(0xA8323A), hx - 3, hy - 12, 3f, 15f)
        }
        Carry.PARCELS -> {
            present(Area(hx + 8, hy + 5, 24f, 18f), 0xC9302C, 0xF0C24A)
            present(Area(hx + 8, hy - 13, 18f, 14f), 0x3F7FD8, 0xFFF4E6)
        }
        Carry.BAG -> if (!who.run) {
            box(ink(who.bag), hx - 8, hy + 3, 16f, 17f)
            line(ink(0x5A3A26), 2f) { arc(Ellipse(hx, hy + 4, 5f, 5f), PI.toFloat(), 2 * PI.toFloat()) }
        }
        else -> {}
    }
}

/** A wrapped present with its lid on, [size]'s w wide and h high: its x and y are the middle of its foot. */
private fun Sketch.present(size: Area, colour: Int, ribbon: Int) = placed(size.x, size.y) {
    val w = size.w
    val h = size.h
    box(ink(colour), -w / 2, -h, w, h)
    box(ink(0x000014, 0.14f), w * 0.15f, -h, w * 0.35f, h)
    box(ink(ribbon), -w * 0.08f, -h, w * 0.16f, h)
    box(mix(colour, WHITE, 0.12f), -w / 2 - 5, -h * 1.2f, w + 10, h * 0.2f)
    box(ink(ribbon), -w * 0.08f, -h * 1.2f, w * 0.16f, h * 0.2f)
    oval(ink(ribbon), Ellipse(-w * 0.14f, -h * 1.3f, w * 0.15f, h * 0.1f, -0.5f))
    oval(ink(ribbon), Ellipse(w * 0.14f, -h * 1.3f, w * 0.15f, h * 0.1f, 0.5f))
    disc(ink(ribbon), 0f, -h * 1.24f, w * 0.06f)
}

private const val STRIDE_BOB = 2.5f // How far a full stride lifts the figure.
private const val DUCK_SQUASH = 0.35f // How much of its height a full duck takes off.
private const val SCARF_LEAD = 1f // The scarf tip's lead over its middle, in radians of the flapping.
