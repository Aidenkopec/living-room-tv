package tv.livingroom.home.christmas

import tv.livingroom.home.screensaver.mix

/*
 * The two dogs asleep on Fireside's rug, head on paws, drawn in parts so the
 * moving layer can make them breathe, wag, twitch a paw and flick an ear
 * without painting anything per frame. Each part is in the dog's own units,
 * facing left, with (0, 0) on the floor under its middle; drawn in the order of
 * [RestingPart] they make the whole dog.
 */

/**
 * A lying dog's parts, back to front; [BACK_LIGHT], the fire's light along its back, is added over the whole dog at the fire's
 * strength.
 */
enum class RestingPart { SHADOW, TAIL, BODY, FRONT, HIND_PAW, FRONT_PAW, HEAD, COLLAR, BACK_LIGHT }

/**
 * Where a dog lies on the rug: the floor under its middle, its size, and whether it faces right (the small one does, toward the big
 * one).
 */
data class RugPlace(val x: Float, val y: Float, val scale: Float, val facesRight: Boolean)

val BIG_DOG_RUG = RugPlace(1176f, 954f, 1.14f, facesRight = false)
val SMALL_DOG_RUG = RugPlace(790f, 966f, 0.94f, facesRight = true)

/** The big dog's red ball on the rug, between the two noses: its middle and radius. */
const val BALL_X = 972f
const val BALL_Y = 992f
const val BALL_R = 27f

/** The tail's wag, from lying still (0) to fully up (1), in [TAIL_FRAMES] steps. */
const val TAIL_FRAMES = 4

/**
 * A lying dog's measures: where its chest is ([front], x), where its rump is ([rump], x), and how high its back is ([back]),
 * in its own units. The big dog is longer and lower.
 */
private class LyingBuild(val front: Float, val rump: Float, val back: Float)

private val SMALL_LYING = LyingBuild(front = -62f, rump = 110f, back = 86f)
private val BIG_LYING = LyingBuild(front = -72f, rump = 130f, back = 80f)

/** [dog]'s [part], with the tail at [wag] (0 to 1) or the head's ear at [ear] (0 to 1). */
fun restingDogMarks(dog: Dog, part: RestingPart, wag: Float = 0f, ear: Float = 0f): List<Mark> {
    val sketch = Sketch()
    val coat = coatOf(dog)
    val big = dog == Dog.BIG
    val b = if (big) BIG_LYING else SMALL_LYING
    when (part) {
        RestingPart.SHADOW -> sketch.oval(ink(0x000000, 0.26f), Ellipse((b.front + b.rump) / 2, 2f, (b.rump - b.front) / 2 + 34, 15f))
        RestingPart.TAIL -> sketch.restingTail(dog, b, wag)
        RestingPart.BODY -> sketch.lyingBody(coat, big, b)
        // Firelight along the back, stopping short of the head and its ears, which it's drawn over.
        RestingPart.BACK_LIGHT -> sketch.line(ink(0xFFB060), 4f) {
            moveTo(b.front + 14, -b.back * 0.84f).quadTo(b.front + 70, -b.back * 1.0f, b.rump - 26, -b.back * 0.86f)
        }
        RestingPart.FRONT -> sketch.lyingFront(coat, big, b)
        // The paws that twitch in a dream, each its own piece as they move opposite ways: the hind foot tucked forward under the
        // haunch, and the front paw.
        RestingPart.HIND_PAW -> {
            sketch.taper(floatArrayOf(b.rump - 18, -9f, b.rump - 30, -7f, b.rump - 42, -7f), 11f, 10f, ink(coat.pale))
            sketch.pawPad(coat, b.rump - 46, big)
        }
        RestingPart.FRONT_PAW -> sketch.pawPad(coat, b.front - 30, big)
        RestingPart.HEAD -> sketch.restingHead(dog, DogPose(open = false, ear = ear))
        RestingPart.COLLAR -> sketch.collarBand(dog, if (big) BIG_LYING_COLLAR else SMALL_LYING_COLLAR)
    }
    return sketch.marks
}

// A lying dog's collar: the back of the neck, the throat and the width. Its neck runs down and forward to the head on its paws,
// so the collar slants across it from just behind the head's top down toward the chest.
private val SMALL_LYING_COLLAR = floatArrayOf(-58f, -56f, -34f, -40f, 8f)
private val BIG_LYING_COLLAR = floatArrayOf(-60f, -60f, -33f, -44f, 9f)

/** A paw resting flat on the floor, its toes forward (toward -x) from [x]. */
private fun Sketch.pawPad(coat: Coat, x: Float, big: Boolean) {
    val k = if (big) 1.1f else 1f
    fill(ink(coat.pale)) {
        moveTo(x + 8 * k, 0.5f).lineTo(x + 8 * k, -10f * k).quadTo(x - 4 * k, -12f * k, x - 12 * k, -6f * k)
        quadTo(x - 15 * k, 0.5f, x - 10 * k, 0.5f).close()
    }
    val toe = ink(if (big) 0x7A4622 else 0xC9C0B4, 0.7f)
    for (dx in listOf(-4f, -8f)) line(toe, 1.1f * k) { through(x + dx * k, 0f, x + dx * k + 0.6f, -3.5f * k) }
}

/** The lying body: back curving up over the shoulders and down over the hips to the rump, the far foreleg just showing. */
private fun Sketch.lyingBody(coat: Coat, big: Boolean, b: LyingBuild) {
    val f = b.front
    val r = b.rump
    val h = b.back
    // The far foreleg, a little behind and above the near one.
    val farLeg = mix(if (big) coat.fur else coat.pale, if (big) 0x000000 else 0x9E8A76, if (big) 0.3f else 0.32f)
    taper(floatArrayOf(f + 16, -14f, f - 6, -13f, f - 26, -12f), 12f, 10f, farLeg)
    if (big) taper(floatArrayOf(f + 2, -13.5f, f - 12, -12.5f, f - 26, -12f), 9f, 10f, mix(coat.pale, 0x000000, 0.25f))
    val outline: PathMaker.() -> Unit = {
        moveTo(f + 4, -h * 0.62f)
        quadTo(f + 30, -h * 0.98f, f + 70, -h)
        quadTo(r - 50, -h * 1.02f, r - 18, -h * 0.8f)
        cubicTo(r + 2 to -h * 0.62f, r + 4 to -h * 0.2f, r - 10, -2f)
        lineTo(f + 22, -1f)
        quadTo(f - 4, -3f, f - 8, -h * 0.3f)
        quadTo(f - 8, -h * 0.52f, f + 4, -h * 0.62f)
        close()
    }
    fill(ink(coat.fur), build = outline)
    clipped(outline) {
        if (big) {
            oval(ink(coat.pale), Ellipse(f + 2, -h * 0.4f, 8f, 11f, 0.2f))
        } else {
            oval(ink(coat.pale), Ellipse(f + 6, -h * 0.3f, 19f, 24f))
        }
    }
    line(ink(coat.sheen, 0.55f), 4f) { moveTo(f + 18, -h * 0.9f).quadTo(f + 76, -h * 1.03f, r - 34, -h * 0.9f) }
}

/** Over the body: the folded hind leg's haunch, and the near foreleg, upper arm down to the elbow and forearm along the floor. */
private fun Sketch.lyingFront(coat: Coat, big: Boolean, b: LyingBuild) {
    val f = b.front
    val r = b.rump
    val h = b.back
    val k = if (big) 1.1f else 1f
    val haunch = Ellipse(r - 42, -h * 0.4f, 40f * k, 32f * k, -0.25f)
    oval(ink(coat.fur), haunch)
    line(ink(coat.shade, 0.6f), 2.6f) { arc(Ellipse(haunch.x, haunch.y, haunch.rx - 2, haunch.ry - 2, haunch.turn), 1.9f, 3.6f) }
    line(ink(coat.sheen, 0.5f), 3f) { arc(Ellipse(haunch.x, haunch.y, haunch.rx - 5, haunch.ry - 5, haunch.turn), 4.2f, 5.3f) }
    // The upper arm to the elbow, then the forearm lying forward.
    taper(floatArrayOf(f + 34, -h * 0.6f, f + 36, -h * 0.3f, f + 24, -12f), 22f * k, 15f * k, ink(coat.fur))
    if (big) {
        taper(floatArrayOf(f + 24, -10f, f, -9f, f - 22, -8f), 13f * k, 11f * k, ink(coat.fur))
        taper(floatArrayOf(f - 6, -8.5f, f - 14, -8f, f - 22, -8f), 9f * k, 11f * k, ink(coat.pale))
    } else {
        taper(floatArrayOf(f + 24, -10f, f, -9f, f - 22, -8f), 13f, 11f, ink(coat.pale))
    }
}

/** The tail lying out behind on the floor, lifted [wag] of the way: the big dog's short stub, the small one's long one. */
private fun Sketch.restingTail(dog: Dog, b: LyingBuild, wag: Float) {
    val coat = coatOf(dog)
    val r = b.rump
    if (dog == Dog.BIG) {
        taper(floatArrayOf(r - 8, -32f, r + 4, -32f - wag * 6, r + 14, -30f - wag * 18), 14f, 10f, ink(coat.fur))
        return
    }
    val curve = floatArrayOf(r - 8, -22f, r + 30, -6f - wag * 10, r + 44, -12f - wag * 46)
    taper(curve, 11f, 6f, ink(coat.fur), to = 0.6f)
    taper(curve, 11f, 6f, ink(0x5E4029), from = 0.56f, to = 0.86f)
    taper(curve, 11f, 6f, ink(coat.pale), from = 0.84f)
}

/** The red ball's shadow on the rug, for the ball resting with its middle at (0, 0). */
fun rugBallShadowMarks(): List<Mark> = Sketch().apply {
    oval(ink(0x000000, 0.25f), Ellipse(3f, BALL_R - 2, BALL_R * 0.95f, BALL_R * 0.24f))
}.marks

/** The red ball, around its middle at (0, 0) (it rolls and bounces, so its shadow is a piece of its own). */
fun rugBallMarks(): List<Mark> = Sketch().apply { ball(0f, 0f, BALL_R, BALL_TURN) }.marks

/** A sleeping dog's z, painted this big in the sheet and scaled as it rises. */
const val Z_SIZE = 34f
