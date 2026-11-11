package tv.livingroom.home.christmas

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import tv.livingroom.home.screensaver.mix

/*
 * How the two dogs look standing: each of [RigPart]'s pieces drawn in its own
 * units (see DogRig.kt), and the whole dog put together from them. Drawn from
 * photos of the real dogs, flat and soft-edged like the rest of the village,
 * with a few tones a piece: the far legs a shade darker, the back catching the
 * light.
 */

internal class Coat(
    val fur: Int,
    /** Fur in shadow: the far legs, the edges of a muscle. */
    val shade: Int,
    /** Fur catching the light, along the back. */
    val sheen: Int,
    /** The second colour: the small dog's white, the big dog's tan. */
    val pale: Int,
    val collar: Int,
    val metal: Int,
)

internal val SMALL_COAT = Coat(fur = 0xC88A4E, shade = 0xA36A36, sheen = 0xDCA367, pale = 0xFBF6EE, collar = 0x2F62C8, metal = 0xD8DDE6)
internal val BIG_COAT = Coat(fur = 0x2B2421, shade = 0x1B1615, sheen = 0x4A403B, pale = 0xB8723C, collar = 0x161211, metal = 0xD4D8DF)

/** [dog]'s colours. */
internal fun coatOf(dog: Dog): Coat = if (dog == Dog.BIG) BIG_COAT else SMALL_COAT

/**
 * [dog] on its feet in [pose], facing left, with (0, 0) on the floor under its middle. Its shadow is separate
 * ([dogShadowMarks]): the shadow stays on the ground when the dog hops.
 */
fun dogMarks(dog: Dog, pose: DogPose): List<Mark> {
    val rig = DogRig().apply { pose(dog, pose) }
    val sketch = Sketch()
    for (part in RigPart.entries) {
        sketch.placed(rig.x(part), rig.y(part), turn = rig.turn(part)) { sketch.rigPart(dog, part, pose) }
    }
    return sketch.marks
}

/** [dog]'s [part] in its own units (see [DogBuild]), as [dogMarks] places it: [pose] gives the tail's wag and the head's look. */
fun rigPartMarks(dog: Dog, part: RigPart, pose: DogPose = DogPose()): List<Mark> = Sketch().apply { rigPart(dog, part, pose) }.marks

/** [dog]'s shadow on the ground, in [dogMarks]' units. */
fun dogShadowMarks(dog: Dog): List<Mark> = Sketch().apply {
    val big = dog == Dog.BIG
    oval(ink(0x000000, 0.22f), Ellipse(0f, 1f, if (big) 104f else 86f, if (big) 12f else 10f))
}.marks

internal fun Sketch.rigPart(dog: Dog, part: RigPart, pose: DogPose) {
    val big = dog == Dog.BIG
    val coat = coatOf(dog)
    val b = buildOf(dog)
    val far = part.ordinal < RigPart.TAIL.ordinal
    when (part) {
        RigPart.FAR_THIGH, RigPart.NEAR_THIGH -> thigh(coat, big, boneLength(b.hind, 0), far)
        RigPart.FAR_GASKIN, RigPart.NEAR_GASKIN -> gaskin(coat, big, boneLength(b.hind, 1), far)
        RigPart.FAR_HOCK, RigPart.NEAR_HOCK -> hock(coat, big, boneLength(b.hind, 2), far)
        RigPart.FAR_HIND_PAW, RigPart.NEAR_HIND_PAW -> paw(coat, big, -b.hind[7], far)
        RigPart.FAR_UPPER_ARM, RigPart.NEAR_UPPER_ARM -> upperArm(coat, big, boneLength(b.front, 0), far)
        RigPart.FAR_FOREARM, RigPart.NEAR_FOREARM -> forearm(coat, big, boneLength(b.front, 1), far)
        RigPart.FAR_FRONT_PAW, RigPart.NEAR_FRONT_PAW -> paw(coat, big, -b.front[5], far)
        RigPart.TAIL -> dogTail(dog, pose.tail.coerceIn(-1f, 1f))
        RigPart.TORSO -> if (big) bigTorso(coat) else smallTorso(coat)
        RigPart.NECK -> if (big) bigNeck(coat) else smallNeck(coat)
        RigPart.COLLAR -> dogCollar(dog)
        RigPart.HEAD -> dogHead(dog, pose)
    }
}

/** A colour for a far leg: the same fur a shade darker. */
private fun Coat.far(rgb: Int, far: Boolean): Int = when {
    !far -> ink(rgb)
    rgb == fur -> mix(rgb, shade, 0.75f)
    else -> mix(rgb, if (this === SMALL_COAT) 0x9E8A76 else 0x000000, if (this === SMALL_COAT) 0.32f else 0.22f)
}

/**
 * A filled band along the curve from ([x0], [y0]) bending toward ([cx], [cy]) to ([x1], [y1]), [w0] wide at its start and [w1]
 * at its end, from [from] to [to] of the way along, its ends rounded.
 */
internal fun Sketch.taper(curve: FloatArray, w0: Float, w1: Float, argb: Int, from: Float = 0f, to: Float = 1f) {
    val (x0, y0) = curve[0] to curve[1]
    val (cx, cy) = curve[2] to curve[3]
    val (x1, y1) = curve[4] to curve[5]
    val steps = TAPER_STEPS
    val left = FloatArray((steps + 1) * 2)
    val right = FloatArray((steps + 1) * 2)
    for (i in 0..steps) {
        val t = lerp(from, to, i.toFloat() / steps)
        val u = 1 - t
        val px = u * u * x0 + 2 * u * t * cx + t * t * x1
        val py = u * u * y0 + 2 * u * t * cy + t * t * y1
        val tx = 2 * u * (cx - x0) + 2 * t * (x1 - cx)
        val ty = 2 * u * (cy - y0) + 2 * t * (y1 - cy)
        val len = hypot(tx, ty).coerceAtLeast(1e-4f)
        val half = lerp(w0, w1, t) / 2
        left[i * 2] = px - ty / len * half
        left[i * 2 + 1] = py + tx / len * half
        right[i * 2] = px + ty / len * half
        right[i * 2 + 1] = py - tx / len * half
    }
    fill(argb) {
        moveTo(left[0], left[1])
        for (i in 1..steps) lineTo(left[i * 2], left[i * 2 + 1])
        for (i in steps downTo 0) lineTo(right[i * 2], right[i * 2 + 1])
        close()
    }
    for (i in listOf(0, steps)) {
        val t = lerp(from, to, i.toFloat() / steps)
        disc(argb, (left[i * 2] + right[i * 2]) / 2, (left[i * 2 + 1] + right[i * 2 + 1]) / 2, lerp(w0, w1, t) / 2)
    }
}

private const val TAPER_STEPS = 10

/** The thigh, hanging from the hip: the haunch's round muscle behind, narrowing to the stifle. */
private fun Sketch.thigh(coat: Coat, big: Boolean, length: Float, far: Boolean) {
    val k = if (big) 1.12f else 1f
    val fur = coat.far(coat.fur, far)
    fill(fur) {
        moveTo(-8f * k, length)
        quadTo(-17f * k, length * 0.5f, -13f * k, -4f * k)
        quadTo(-2f * k, -22f * k, 15f * k, -16f * k)
        cubicTo(29f * k to -6f * k, 28f * k to length * 0.55f, 9f * k, length + 1)
        quadTo(0f, length + 7, -8f * k, length)
        close()
    }
    if (!far) { // The front of the thigh in shadow, so it reads as a muscle over the belly.
        line(ink(coat.shade, 0.55f), 2.6f * k) { moveTo(-12f * k, 2f * k).quadTo(-15f * k, length * 0.5f, -8f * k, length - 2) }
        line(ink(coat.sheen, 0.5f), 3f * k) { moveTo(-2f * k, -15f * k).quadTo(14f * k, -16f * k, 20f * k, -2f * k) }
    }
}

/** The lower thigh, stifle to hock, a little fuller behind. */
private fun Sketch.gaskin(coat: Coat, big: Boolean, length: Float, far: Boolean) {
    val k = if (big) 1.12f else 1f
    taper(floatArrayOf(0f, 0f, 4f * k, length * 0.5f, 0f, length), 15f * k, 9f * k, coat.far(coat.fur, far))
}

/** Hock to ankle: the small dog's white sock, the big dog's tan running up the front. */
private fun Sketch.hock(coat: Coat, big: Boolean, length: Float, far: Boolean) {
    val k = if (big) 1.12f else 1f
    if (big) {
        taper(floatArrayOf(0f, 0f, 0f, length / 2, 0f, length), 9.5f * k, 9f * k, coat.far(coat.fur, far))
        taper(floatArrayOf(0f, length * 0.2f, -1f, length * 0.6f, 0f, length), 6f * k, 9f * k, coat.far(coat.pale, far))
    } else {
        taper(floatArrayOf(0f, 0f, 0f, length / 2, 0f, length), 9.5f, 9f, coat.far(coat.fur, far))
        taper(floatArrayOf(0f, length * 0.35f, 0f, length * 0.7f, 0f, length), 9f, 9f, coat.far(coat.pale, far))
    }
}

/** The upper arm, shoulder to elbow, mostly over the chest. */
private fun Sketch.upperArm(coat: Coat, big: Boolean, length: Float, far: Boolean) {
    val k = if (big) 1.12f else 1f
    taper(floatArrayOf(0f, 0f, 5f * k, length * 0.5f, 0f, length), 21f * k, 14f * k, coat.far(coat.fur, far))
    if (!far) line(ink(coat.shade, 0.45f), 2.4f * k) { moveTo(9f * k, 2f).quadTo(11f * k, length * 0.6f, 6f * k, length + 3) }
}

/** The forearm, elbow to wrist, with the point of the elbow behind: white on the small dog; black going to tan on the big one. */
private fun Sketch.forearm(coat: Coat, big: Boolean, length: Float, far: Boolean) {
    val k = if (big) 1.12f else 1f
    val fur = coat.far(coat.fur, far)
    val pale = coat.far(coat.pale, far)
    disc(fur, 4f * k, 2f, 7.5f * k) // The elbow.
    if (big) {
        taper(floatArrayOf(0f, 0f, 0f, length / 2, 0f, length), 13f * k, 10f * k, fur)
        taper(floatArrayOf(0f, length * 0.55f, -1f, length * 0.8f, 0f, length), 8f * k, 10f * k, pale)
    } else {
        taper(floatArrayOf(0f, 0f, 0f, length / 2, 0f, length), 13f, 10.5f, fur)
        taper(floatArrayOf(-1.5f, length * 0.1f, -1.5f, length * 0.55f, 0f, length), 9f, 10.5f, pale)
    }
}

/**
 * A paw on its pastern, hanging from the wrist or ankle at (0, 0), its pad on the floor [height] below when flat, toes
 * forward (toward -x): the small dog's white, the big dog's tan.
 */
private fun Sketch.paw(coat: Coat, big: Boolean, height: Float, far: Boolean) {
    val k = if (big) 1.1f else 1f
    val floor = height
    val colour = coat.far(coat.pale, far)
    taper(floatArrayOf(0f, 0f, -1f, floor * 0.5f, -3f * k, floor - 5f * k), 9.5f * k, 9f * k, colour)
    fill(colour) {
        moveTo(2f * k, floor)
        quadTo(3f * k, floor - 8f * k, -4f * k, floor - 8.5f * k)
        quadTo(-13f * k, floor - 8.5f * k, -15f * k, floor - 2f * k)
        quadTo(-15f * k, floor + 0.5f, -12f * k, floor + 0.5f)
        lineTo(1f * k, floor + 0.5f)
        close()
    }
    val toe = ink(if (big) 0x7A4622 else 0xC9C0B4, 0.7f)
    for (x in listOf(-6f, -10f)) line(toe, 1.1f * k) { through(x * k, floor - 0.5f, x * k + 0.6f, floor - 3.5f * k) }
}

/**
 * The tail at [wag] of the way to one side: the small dog's long and carried up, tan, then a dark band, then a white tip; the
 * big dog's a short stub.
 */
internal fun Sketch.dogTail(dog: Dog, wag: Float) {
    val coat = coatOf(dog)
    if (dog == Dog.BIG) {
        placed(78f, -142f, turn = -0.5f + wag * 0.28f) {
            taper(floatArrayOf(0f, 0f, 8f, -2f, 18f, -2f), 14f, 10f, ink(coat.fur))
        }
        return
    }
    // Seen from the side, a wag swings the raised tail's tip forward and back.
    val curve = floatArrayOf(62f, -106f, 104f - wag * 4, -110f, 94f + wag * 18, -166f + abs(wag) * 6)
    taper(curve, 11f, 6f, ink(coat.fur), to = 0.62f)
    taper(curve, 11f, 6f, ink(0x5E4029), from = 0.58f, to = 0.86f)
    taper(curve, 11f, 6f, ink(coat.pale), from = 0.84f)
}

/** The small dog's body: a lean, deep-chested terrier mix, tan with a white chest and belly. */
private fun Sketch.smallTorso(coat: Coat) {
    val outline: PathMaker.() -> Unit = {
        moveTo(-46f, -121f)
        quadTo(-12f, -116f, 22f, -116f)
        quadTo(52f, -120f, 68f, -110f)
        cubicTo(83f to -101f, 82f to -84f, 70f, -74f)
        quadTo(56f, -64f, 40f, -68f)
        quadTo(22f, -74f, 6f, -67f)
        quadTo(-14f, -56f, -38f, -53f)
        cubicTo(-60f to -51f, -78f to -64f, -79f, -84f)
        quadTo(-79f, -102f, -67f, -110f)
        quadTo(-58f, -120f, -46f, -121f)
        close()
    }
    fill(ink(coat.fur), build = outline)
    clipped(outline) {
        oval(ink(coat.pale), Ellipse(-66f, -76f, 22f, 29f, 0.2f))
        oval(ink(coat.pale), Ellipse(-24f, -53f, 34f, 7f, -0.12f))
        oval(ink(coat.shade, 0.35f), Ellipse(20f, -68f, 30f, 6f, -0.15f)) // The flank's tuck in shadow.
    }
    line(ink(coat.sheen, 0.55f), 4f) { moveTo(-38f, -115f).quadTo(10f, -110f, 52f, -113f) }
}

/** The big dog's body: a Dobermann's, square, deep in the chest and tucked up at the loin, with a tan patch on the chest. */
private fun Sketch.bigTorso(coat: Coat) {
    val outline: PathMaker.() -> Unit = {
        moveTo(-52f, -155f)
        quadTo(-6f, -149f, 34f, -148f)
        quadTo(62f, -152f, 80f, -141f)
        cubicTo(94f to -131f, 92f to -112f, 82f, -102f)
        quadTo(66f, -90f, 46f, -96f)
        quadTo(26f, -106f, 10f, -98f)
        quadTo(-16f, -82f, -40f, -76f)
        cubicTo(-62f to -73f, -82f to -90f, -84f, -112f)
        quadTo(-82f, -130f, -72f, -142f)
        quadTo(-62f, -154f, -52f, -155f)
        close()
    }
    fill(ink(coat.fur), build = outline)
    clipped(outline) {
        oval(ink(coat.pale), Ellipse(-78f, -104f, 9f, 13f, 0.25f))
        oval(ink(coat.shade, 0.6f), Ellipse(24f, -96f, 34f, 8f, -0.3f))
    }
    line(ink(coat.sheen, 0.6f), 5f) { moveTo(-44f, -151f).quadTo(10f, -145f, 66f, -147f) }
}

/** The small dog's neck, from the chest up into the back of the head, its throat white. */
private fun Sketch.smallNeck(coat: Coat) {
    val outline: PathMaker.() -> Unit = {
        moveTo(-42f, -116f)
        quadTo(-58f, -142f, -76f, -160f)
        lineTo(-104f, -136f)
        quadTo(-95f, -110f, -80f, -86f)
        lineTo(-52f, -98f)
        close()
    }
    fill(ink(coat.fur), build = outline)
    clipped(outline) { oval(ink(coat.pale), Ellipse(-92f, -110f, 13f, 32f, -0.45f)) }
}

/** The big dog's neck: long and arched, black. */
private fun Sketch.bigNeck(coat: Coat) {
    val outline: PathMaker.() -> Unit = {
        moveTo(-50f, -152f)
        quadTo(-64f, -180f, -86f, -204f)
        lineTo(-116f, -180f)
        quadTo(-102f, -142f, -84f, -112f)
        lineTo(-58f, -128f)
        close()
    }
    fill(ink(coat.fur), build = outline)
    line(ink(coat.sheen, 0.6f), 4.5f) { moveTo(-54f, -156f).quadTo(-66f, -180f, -82f, -198f) }
}

/**
 * The collar round the neck, across it the way the neck runs, so it wraps round rather than lying along it: a band
 * from the back of the neck to the throat, bowed a little toward the body as
 * a ring on a slanting neck is, with its lower edge in shadow. The small dog's is blue with a silver buckle and a round
 * tag; the big dog's black leather with silver studs and a ring.
 */
internal fun Sketch.dogCollar(dog: Dog) = collarBand(dog, if (dog == Dog.BIG) BIG_COLLAR else SMALL_COLLAR)

/** [dog]'s collar from the back of the neck ([p]'s first point) to the throat (its second), [p]'s fifth number wide. */
internal fun Sketch.collarBand(dog: Dog, p: FloatArray) {
    val big = dog == Dog.BIG
    val coat = coatOf(dog)
    val bx = p[0]
    val by = p[1]
    val fx = p[2]
    val fy = p[3]
    val width = p[4]
    // The neck's direction, toward the body: across the band.
    val across = atan2(fy - by, fx - bx)
    val nx = -sin(across)
    val ny = cos(across)
    val down = if (ny < 0) -1f else 1f // Toward the body, which is below and behind.
    val ux = nx * down * width / 2
    val uy = ny * down * width / 2
    val mx = (bx + fx) / 2 + ux * 0.9f
    val my = (by + fy) / 2 + uy * 0.9f
    fill(ink(coat.collar)) {
        moveTo(bx - ux, by - uy)
        quadTo(mx - ux, my - uy, fx - ux, fy - uy)
        lineTo(fx + ux, fy + uy)
        quadTo(mx + ux, my + uy, bx + ux, by + uy)
        close()
    }
    line(ink(0x000000, 0.25f), width * 0.25f) {
        moveTo(bx + ux * 0.75f, by + uy * 0.75f).quadTo(
            mx + ux * 0.75f,
            my + uy * 0.75f,
            fx + ux * 0.75f,
            fy + uy * 0.75f,
        )
    }
    if (big) {
        for (q in listOf(0.18f, 0.38f, 0.58f, 0.78f)) {
            val t = q
            val u = 1 - t
            disc(ink(coat.metal), u * u * bx + 2 * u * t * mx + t * t * fx, u * u * by + 2 * u * t * my + t * t * fy, width * 0.2f)
        }
        line(ink(coat.metal), 2f) { arc(Ellipse(fx + 1f, fy + 5f, 4f, 4.5f), 0f, 6.3f) }
    } else {
        val t = 0.3f
        val u = 1 - t
        val sx = u * u * bx + 2 * u * t * mx + t * t * fx
        val sy = u * u * by + 2 * u * t * my + t * t * fy
        placed(sx, sy, turn = across) { roundBox(ink(coat.metal), Area(-3.5f, -width * 0.62f, 7f, width * 1.24f), 1.5f) }
        line(ink(coat.metal), 1.6f) { arc(Ellipse(fx + 0.5f, fy + 3.5f, 2.6f, 3f), 0f, 6.3f) }
        disc(ink(coat.metal), fx + 0.5f, fy + 10.5f, 5f)
        disc(ink(0xFFFFFF, 0.5f), fx - 1f, fy + 9f, 1.6f)
    }
}

// The collar's two ends (the back of the neck, then the throat) and its width, in the dogs' own units.
private val SMALL_COLLAR = floatArrayOf(-63f, -144f, -96f, -122f, 8f)
private val BIG_COLLAR = floatArrayOf(-72f, -184f, -110f, -164f, 9f)

/** Where a lead meets [dog], in [dogMarks]' units (the dog facing left), standing square: at the collar's ring, under the throat. */
fun leadPoint(dog: Dog): Pair<Float, Float> = if (dog ==
    Dog.BIG
) {
    BIG_COLLAR[2] + 1f to BIG_COLLAR[3] + 5f
} else {
    SMALL_COLLAR[2] + 0.5f to SMALL_COLLAR[3] + 3.5f
}

/** A bone, [scale] times its size and turned [turn], around ([x], [y]). */
internal fun Sketch.bone(x: Float, y: Float, scale: Float, turn: Float) = placed(x, y, scale = scale, turn = turn) {
    val colour = ink(0xF6EEDD)
    roundBox(colour, Area(-20f, -5f, 40f, 10f), 4f)
    for ((bx, by) in listOf(-21f to -5.5f, -21f to 5.5f, 21f to -5.5f, 21f to 5.5f)) disc(colour, bx, by, 7.5f)
}

/** The fire's light along a standing dog's back, in the torso's units, added over the dog at the fire's strength. */
internal fun standingBackLightMarks(dog: Dog): List<Mark> = Sketch().apply {
    if (dog == Dog.BIG) {
        line(ink(0xFFB060), 4f) { moveTo(-50f, -153f).quadTo(10f, -147f, 76f, -143f) }
    } else {
        line(ink(0xFFB060), 4f) { moveTo(-44f, -116f).quadTo(12f, -111f, 64f, -109f) }
    }
}.marks
