package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The two dogs' heads in profile, facing left, in the heads' own units: the neck
 * joins at (HEAD_JOINT_X, HEAD_JOINT_Y), and a head lying on its paws rests
 * there too. Each head is one outline with its markings cut to it, so they
 * never spill past the edge, then the eye, nose and ears.
 */

/** [dog]'s head in [pose]: the eyes open or shut, an ear's twitch, the mouth panting or carrying the ball or a bone. */
internal fun Sketch.dogHead(dog: Dog, pose: DogPose) {
    if (dog == Dog.BIG) {
        bigHead(pose)
    } else {
        // The small dog's head is broad for his size: drawn a little bigger about the neck's joint.
        placed(HEAD_JOINT_X, HEAD_JOINT_Y, scale = SMALL_HEAD_SCALE) { placed(-HEAD_JOINT_X, -HEAD_JOINT_Y) { smallHead(pose) } }
    }
}

private const val SMALL_HEAD_SCALE = 1.1f

/**
 * The small dog's head upside down, as he lies on his back: the top of his skull on the floor, chin up, eyes open, and his big
 * ears flopped out on the floor, one each side. Drawn round the neck's joint at (0, 0), which sits [UPSIDE_DOWN_JOINT] above the
 * floor.
 */
internal fun Sketch.upsideDownSmallHead() {
    val coat = SMALL_COAT
    val floor = UPSIDE_DOWN_JOINT
    // The far ear, splayed out behind on the floor.
    fill(ink(coat.shade)) {
        moveTo(-20f, floor - 8f).quadTo(-2f, floor - 10f, 22f, floor - 2f).quadTo(4f, floor + 1f, -18f, floor).close()
    }
    // A mirror image top to bottom about the joint: the head's own drawing, with the joint where it joins the neck.
    placed(-HEAD_JOINT_X, HEAD_JOINT_Y, turn = PI.toFloat(), flip = true) {
        placed(HEAD_JOINT_X, HEAD_JOINT_Y, scale = SMALL_HEAD_SCALE) {
            placed(-HEAD_JOINT_X, -HEAD_JOINT_Y) { smallHead(DogPose(), ears = false) }
        }
    }
    // The near ear, flopped forward over the floor, pale inside up, its folded tip lying open.
    fill(ink(coat.fur)) {
        moveTo(-18f, floor - 14f).cubicTo(-34f to floor - 20f, -58f to floor - 12f, -58f, floor - 4f)
        cubicTo(-58f to floor + 1f, -34f to floor + 1f, -14f, floor).close()
    }
    fill(ink(0xEBC6A6)) {
        moveTo(-24f, floor - 9f).cubicTo(-34f to floor - 13f, -48f to floor - 9f, -50f, floor - 5f)
        cubicTo(-44f to floor - 2f, -32f to floor - 3f, -22f, floor - 4f).close()
    }
}

/** How far above the floor the upside-down head's neck joint is: the skull's height above the joint, at the small dog's size. */
internal const val UPSIDE_DOWN_JOINT = 42f

/** [dog]'s head as [dogHead] draws it, for a dog lying with its head on its paws. */
internal fun Sketch.restingHead(dog: Dog, pose: DogPose) = dogHead(dog, pose)

/** [dog]'s head in [pose], in the heads' own units. */
internal fun headMarks(dog: Dog, pose: DogPose): List<Mark> = Sketch().apply { dogHead(dog, pose) }.marks

/**
 * The small dog's head: a broad tan skull, a white blaze running up from a white muzzle, dark lips, a big black nose, dark
 * brown eyes, and big ears standing up with their tips folded forward.
 */
private fun Sketch.smallHead(pose: DogPose, ears: Boolean = true) {
    val coat = SMALL_COAT
    if (ears) smallEar(coat, pose.ear, far = true)
    val outline: PathMaker.() -> Unit = {
        moveTo(-58f, -30f)
        quadTo(-54f, -50f, -66f, -60f)
        quadTo(-84f, -70f, -100f, -62f)
        quadTo(-108f, -58f, -112f, -50f)
        quadTo(-122f, -47f, -131f, -45f)
        quadTo(-139f, -44f, -141f, -38f)
        quadTo(-142f, -31f, -137f, -28f)
        quadTo(-137f, -21f, -131f, -17f)
        quadTo(-122f, -12f, -106f, -13f)
        quadTo(-84f, -12f, -68f, -18f)
        quadTo(-60f, -22f, -58f, -30f)
        close()
    }
    fill(ink(coat.fur), build = outline)
    clipped(outline) {
        // The blaze: a white stripe up the top of the muzzle, narrowing between the eyes onto the forehead.
        fill(ink(coat.pale)) {
            moveTo(-144f, -48f).lineTo(-128f, -48f).quadTo(-114f, -54f, -104f, -64f).quadTo(-96f, -70f, -86f, -72f)
            lineTo(-96f, -76f).lineTo(-144f, -60f).close()
        }
        // The white muzzle and chin.
        fill(ink(coat.pale)) {
            moveTo(-146f, -46f).quadTo(-126f, -45f, -118f, -38f).quadTo(-112f, -28f, -114f, -18f).quadTo(-110f, -12f, -96f, -8f)
            lineTo(-146f, -6f).close()
        }
        oval(ink(coat.pale), Ellipse(-92f, -12f, 22f, 6f))
        // Dark shading by the mouth, and a soft grey on the side of the muzzle where the whiskers are.
        oval(ink(0x9C8676, 0.2f), Ellipse(-126f, -32f, 7f, 4f, -0.2f))
    }
    if (pose.pant) pantingMouth(coat.pale, big = false) else lips(big = false)
    dogEye(-106f, -46f, pose.open, big = false)
    // The brow over the eye.
    line(ink(coat.shade, 0.7f), 2.4f) { moveTo(-112f, -54f).quadTo(-105f, -58f, -97f, -55f) }
    nose(-137f, -38f, big = false)
    if (ears) smallEar(coat, pose.ear, far = false)
    if (pose.bone) bone(-128f, -16f, 0.85f, 0.12f)
}

/**
 * The big dog's head: a Dobermann's long wedge, black with tan on the muzzle, the cheek, the throat and a spot over the eye,
 * and long ears standing up to a point.
 */
private fun Sketch.bigHead(pose: DogPose) {
    val coat = BIG_COAT
    bigEar(coat, pose.ear, far = true)
    val outline: PathMaker.() -> Unit = {
        moveTo(-58f, -30f)
        quadTo(-52f, -52f, -66f, -60f)
        quadTo(-88f, -68f, -108f, -61f)
        quadTo(-116f, -58f, -121f, -53f)
        lineTo(-151f, -45f)
        quadTo(-159f, -44f, -161f, -38f)
        quadTo(-162f, -31f, -157f, -28f)
        quadTo(-155f, -21f, -150f, -18f)
        quadTo(-142f, -14f, -126f, -15f)
        quadTo(-96f, -12f, -70f, -18f)
        quadTo(-60f, -22f, -58f, -30f)
        close()
    }
    fill(ink(coat.fur), build = outline)
    clipped(outline) {
        // Tan on the lower muzzle, round the lip's corner onto the cheek, and along the throat.
        fill(ink(coat.pale)) {
            moveTo(-164f, -33f).quadTo(-142f, -33f, -122f, -30f).quadTo(-104f, -34f, -96f, -24f).quadTo(-96f, -14f, -106f, -10f)
            lineTo(-164f, -8f).close()
        }
        oval(ink(coat.pale), Ellipse(-90f, -12f, 18f, 4.5f))
        oval(ink(coat.sheen, 0.45f), Ellipse(-92f, -58f, 18f, 4f, -0.12f)) // Light along the top of the skull.
    }
    if (pose.pant) pantingMouth(coat.pale, big = true) else lips(big = true)
    disc(ink(coat.pale), -108f, -58f, 4.6f) // The tan spot over the eye.
    dogEye(-110f, -48f, pose.open, big = true)
    nose(-157f, -38f, big = true)
    bigEar(coat, pose.ear, far = false)
    if (pose.ball) ball(-147f, -15f, 22f, 0.4f)
    if (pose.bone) bone(-146f, -18f, 0.95f, 0.12f)
}

/** The mouth shut: a dark lip line from under the nose back to its corner, the small dog's with dark lips along it. */
private fun Sketch.lips(big: Boolean) {
    if (big) {
        line(ink(0x0E0B0A), 2.4f) { moveTo(-155f, -27f).quadTo(-140f, -24f, -124f, -26f) }
    } else {
        oval(ink(0x3A2A22, 0.55f), Ellipse(-122f, -24f, 9f, 2.4f, -0.06f))
        line(ink(0x2A1C16), 2.1f) { moveTo(-135f, -27f).quadTo(-126f, -24f, -113f, -25.5f) }
    }
}

/**
 * The mouth open, panting: the lower jaw dropped from the lip's corner, the dark of the mouth between, and the tongue
 * hanging over the lower lip. [chin] is the jaw's colour (the small dog's white, the big dog's tan).
 */
private fun Sketch.pantingMouth(chin: Int, big: Boolean) {
    val front = if (big) -154f else -135f
    val corner = if (big) -120f else -110f
    fill(ink(0x3A1A1C)) {
        moveTo(front, -27f).quadTo((front + corner) / 2, -25f, corner, -25f).quadTo(corner - 6, -17f, front + 5, -11f).close()
    }
    fill(ink(chin)) {
        // The lower jaw.
        moveTo(corner, -24f).quadTo(corner - 8, -15f, front + 4, -11f).quadTo(front + 2, -6f, front + 9, -4f)
        quadTo((front + corner) / 2, -5f, corner + 6, -13f).close()
    }
    fill(ink(0xE27D88)) {
        moveTo(front + 9, -17f).quadTo(front + 4, -8f, front + 8, -1f).quadTo(front + 13, 4f, front + 18, -2f)
        quadTo(front + 20, -10f, front + 19, -17f).close()
    }
    line(ink(0xB85A66), 1.3f) { through(front + 13.5f, -15f, front + 13f, -3f) }
}

/** A dog's eye at ([x], [y]): dark brown with a catch of light, or shut in a curve when not [open]. */
private fun Sketch.dogEye(x: Float, y: Float, open: Boolean, big: Boolean) {
    if (!open) {
        line(ink(if (big) 0x8A6A52 else 0x3A2418), 2.6f) { arc(Ellipse(x, y - 1f, 6f, 4f), 0.25f, PI.toFloat() - 0.25f) }
        return
    }
    fill(ink(if (big) 0x120D0B else 0x2A1A12)) { oval(Ellipse(x, y, 6.6f, 5.4f, -0.15f)) }
    disc(ink(if (big) 0x4A2A18 else 0x5A3418), x - 0.8f, y, 4.2f)
    disc(ink(0x0C0807), x - 1.2f, y, 2.4f)
    disc(ink(0xFFFFFF, 0.9f), x - 2.6f, y - 1.8f, 1.4f)
}

/** The nose's tip at ([x], [y]): black, with a nostril and a soft shine. */
private fun Sketch.nose(x: Float, y: Float, big: Boolean) {
    val r = if (big) 6.5f else 7f
    fill(ink(0x14100E)) {
        moveTo(x + r, y - r * 0.7f).quadTo(x - r * 0.4f, y - r * 1.15f, x - r * 0.9f, y - r * 0.2f)
        quadTo(x - r * 1.1f, y + r * 0.7f, x - r * 0.1f, y + r * 0.9f).quadTo(x + r * 1.1f, y + r * 0.6f, x + r, y - r * 0.7f).close()
    }
    oval(ink(0x000000), Ellipse(x - r * 0.45f, y + r * 0.25f, r * 0.28f, r * 0.2f, 0.4f))
    oval(ink(0xFFFFFF, 0.28f), Ellipse(x - r * 0.1f, y - r * 0.55f, r * 0.45f, r * 0.18f, -0.2f))
}

/**
 * The small dog's big ear: standing up from the back of the skull, pale inside, its tip folded forward and down. An ear's
 * twitch ([ear]) lifts the folded tip. The [far] ear peeps out ahead of the near one, darker.
 */
private fun Sketch.smallEar(coat: Coat, ear: Float, far: Boolean) {
    val dx = if (far) -15f else 0f
    val dy = if (far) 5f else 0f
    placed(dx, dy) {
        val outside = if (far) ink(coat.shade) else ink(coat.fur)
        // Where the ear folds, and the folded tip, which lifts with a twitch.
        val foldBackX = -68f
        val foldBackY = -100f
        val foldFrontX = -92f
        val foldFrontY = -97f
        val tipX = -101f + ear * 4
        val tipY = -80f - ear * 15
        fill(outside) {
            moveTo(-58f, -50f)
            quadTo(-60f, -80f, foldBackX, foldBackY)
            quadTo(-80f, -106f, foldFrontX, foldFrontY)
            quadTo(-96f, -74f, -92f, -58f)
            close()
        }
        if (!far) {
            fill(ink(0xEBC6A6)) {
                moveTo(-74f, -62f).quadTo(-80f, -82f, -83f, -98f).lineTo(-90f, -96f).quadTo(-92f, -76f, -88f, -60f).close()
            }
            line(ink(coat.shade, 0.6f), 2f) { moveTo(-61f, -56f).quadTo(-62f, -80f, foldBackX + 1, foldBackY + 2) }
        }
        fill(if (far) ink(0x7A4A24) else ink(coat.shade)) {
            moveTo(foldBackX, foldBackY)
            quadTo(-80f, -107f, foldFrontX, foldFrontY)
            quadTo(-98f, -90f, tipX, tipY)
            quadTo(-86f, -88f, foldBackX, foldBackY)
            close()
        }
        line(ink(0x6E4320, if (far) 0.4f else 0.7f), 1.5f) { moveTo(foldBackX, foldBackY).quadTo(-86f, -94f, tipX, tipY) }
    }
}

/**
 * The big dog's long ear, standing up from the top of the skull to a point, its inside showing at the front edge. An ear's
 * twitch ([ear]) tips the point back. The [far] ear stands a little ahead of the near one.
 */
private fun Sketch.bigEar(coat: Coat, ear: Float, far: Boolean) {
    val dx = if (far) -18f else 0f
    val dy = if (far) 4f else 0f
    placed(dx, dy) {
        val tipX = -72f + ear * 8
        val tipY = -126f + ear * 3
        fill(ink(if (far) 0x141110 else coat.fur)) {
            moveTo(-62f, -54f).quadTo(-60f, -94f, tipX, tipY).quadTo(-88f, -96f, -92f, -62f).close()
        }
        if (!far) {
            fill(ink(0x6A5552)) { moveTo(-86f, -64f).quadTo(-82f, -96f, tipX - 1f, tipY + 9f).quadTo(-88f, -94f, -91f, -64f).close() }
            line(ink(coat.sheen, 0.7f), 2f) { moveTo(-64f, -60f).quadTo(-63f, -94f, tipX + 1f, tipY + 4f) }
        }
    }
}

/** The big dog's red ball, a hollow lattice: seven six-sided holes, turned by [turn], and a highlight. */
internal fun Sketch.ball(x: Float, y: Float, radius: Float, turn: Float) {
    disc(ink(0xDC3A40), x, y, radius)
    val sixth = PI.toFloat() / 3

    fun hole(cx: Float, cy: Float, r: Float) = fill(ink(0x6A1B23)) {
        for (k in 0 until 6) {
            val px = cx + cos(k * sixth + turn) * r
            val py = cy + sin(k * sixth + turn) * r
            if (k == 0) moveTo(px, py) else lineTo(px, py)
        }
        close()
    }
    hole(x, y, radius * 0.3f)
    for (k in 0 until 6) {
        val angle = k * sixth + turn + sixth / 2
        hole(x + cos(angle) * radius * 0.66f, y + sin(angle) * radius * 0.66f, radius * 0.25f)
    }
    oval(ink(0xFFFFFF, 0.3f), Ellipse(x - radius * 0.35f, y - radius * 0.5f, radius * 0.3f, radius * 0.14f, -0.6f))
}
