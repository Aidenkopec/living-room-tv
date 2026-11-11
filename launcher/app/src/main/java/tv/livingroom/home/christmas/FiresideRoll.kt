package tv.livingroom.home.christmas

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import tv.livingroom.home.screensaver.mix

/*
 * The small dog rolling onto his back on Fireside's rug. On his back he's a
 * set of pieces of his own (the body belly up, the head upside down with the
 * ears flopped on the floor, four legs bent in the air that paddle about where
 * they join him); going over and coming back, the lying dog flattens as he
 * turns, then the belly-up dog fills out, which reads as a roll seen side on.
 * In the dog's own units, facing left, (0, 0) on the floor under his middle.
 */

/** The pieces of the small dog on his back, back to front. */
enum class BellyPart { FAR_HIND, FAR_FRONT, BODY, HEAD, NEAR_HIND, NEAR_FRONT }

/** Where each leg joins the body on its back (x, y), by [BellyPart] ordinal; the head's joint. */
private val LEG_ROOTS = mapOf(
    BellyPart.FAR_HIND to (72f to -54f),
    BellyPart.FAR_FRONT to (-28f to -62f),
    BellyPart.NEAR_HIND to (62f to -48f),
    BellyPart.NEAR_FRONT to (-40f to -56f),
)
private const val HEAD_X = -58f

/** The small dog's [part] on his back, each leg drawn rising from its joint at (0, 0), the head round its neck joint. */
fun bellyMarks(part: BellyPart): List<Mark> = Sketch().apply {
    val coat = SMALL_COAT
    when (part) {
        BellyPart.BODY -> bellyBody(coat)
        BellyPart.HEAD -> upsideDownSmallHead()
        BellyPart.FAR_HIND, BellyPart.NEAR_HIND -> bellyHindLeg(coat, far = part == BellyPart.FAR_HIND)
        BellyPart.FAR_FRONT, BellyPart.NEAR_FRONT -> bellyFrontLeg(coat, far = part == BellyPart.FAR_FRONT)
    }
}.marks

/** The body belly up: the back on the floor, the white chest and pale belly on top. */
private fun Sketch.bellyBody(coat: Coat) {
    val outline: PathMaker.() -> Unit = {
        moveTo(-68f, -30f)
        quadTo(-64f, -68f, -34f, -72f)
        quadTo(10f, -70f, 50f, -62f)
        quadTo(92f, -58f, 104f, -34f)
        quadTo(110f, -10f, 92f, -2f)
        lineTo(-46f, -1f)
        quadTo(-70f, -4f, -68f, -30f)
        close()
    }
    fill(ink(coat.fur), build = outline)
    clipped(outline) {
        oval(ink(coat.pale), Ellipse(-34f, -66f, 34f, 14f, 0.04f))
        oval(ink(0xF3DCCB), Ellipse(30f, -62f, 48f, 10f, 0.1f)) // The belly, pinker where the fur is thin.
        oval(ink(coat.shade, 0.35f), Ellipse(20f, -6f, 80f, 7f))
    }
}

private fun farOr(rgb: Int, far: Boolean, shade: Int = 0x9E8A76): Int = if (far) mix(rgb, shade, 0.32f) else ink(rgb)

/** A foreleg in the air: the forearm up, white, the paw flopping forward at the wrist. */
private fun Sketch.bellyFrontLeg(coat: Coat, far: Boolean) {
    taper(floatArrayOf(0f, 0f, -1f, -8f, -2f, -16f), 17f, 13f, if (far) mix(coat.fur, coat.shade, 0.75f) else ink(coat.fur))
    taper(floatArrayOf(-2f, -12f, -3f, -28f, -4f, -44f), 11f, 10f, farOr(coat.pale, far))
    placed(-4f, -44f, turn = -0.5f) {
        fill(farOr(coat.pale, far)) { oval(Ellipse(-7f, -2f, 9f, 5.5f)) }
        line(ink(0xC9C0B4, 0.7f), 1f) { through(-12f, -5f, -13f, -1f) }
    }
}

/** A hind leg in the air: the thigh up and forward to the knee, the white sock back up to the paw. */
private fun Sketch.bellyHindLeg(coat: Coat, far: Boolean) {
    taper(floatArrayOf(0f, 0f, -12f, -12f, -20f, -30f), 26f, 14f, if (far) mix(coat.fur, coat.shade, 0.75f) else ink(coat.fur))
    taper(floatArrayOf(-20f, -30f, -15f, -44f, -8f, -54f), 11f, 9.5f, farOr(coat.pale, far))
    placed(-7f, -56f, turn = -0.8f) { fill(farOr(coat.pale, far)) { oval(Ellipse(-5f, -2f, 8.5f, 5f)) } }
}

/**
 * The small dog going over onto his back and wriggling there ([DogAct.roll] above 0): flattening as a lying dog up to half way,
 * then filling out belly up, rocking and paddling his legs as hard as he [DogAct.wriggle]s, his tail sweeping the floor.
 */
internal fun rollInto(frame: FiresideFrame, sprites: FiresideSprites, act: DogAct) {
    val lying = sprites.smallDog
    val belly = sprites.belly
    val r = act.roll
    if (r < 0.5f) {
        lyingInto(frame, lying, act, sprites, flatten = 1 - r * 2 * (1 - FLATTEST))
        return
    }
    val s = lying.at.scale
    val t = frame.now.t
    val fill = FLATTEST + (1 - FLATTEST) * smooth((r - 0.5f) * 2)
    val w = act.wriggle
    val rock = sin(t * ROCK_RATE).toFloat() * ROCK * w
    val base = frame.turns[0].reset().translate(act.x, act.y).scale(if (act.flip) -s else s, s * fill)
    val at = frame.turns[1]
    val q = frame.rug
    q.addThrough(lying.shadow, base, s)
    val sweep = abs(sin(t * 6)).toFloat() * (0.4f + 0.6f * w)
    q.addThrough(lying.tails[(sweep * (lying.tails.size - 1)).roundToInt()], base, s)
    // Everything rocks together about the middle of his back; the legs swing in from the floor as he comes over.
    val body = frame.turns[2].set(base).translate(BELLY_MIDDLE, 0f).rotate(rock).translate(-BELLY_MIDDLE, 0f)
    val swingIn = (1 - smooth((r - 0.5f) * 2)) * 1.1f
    fun leg(part: BellyPart, i: Int) {
        val (x, y) = LEG_ROOTS.getValue(part)
        val paddle = sin(t * PADDLE_RATE + i * 1.7).toFloat() * (PADDLE_IDLE + PADDLE * w)
        q.addThrough(belly[part.ordinal], at.set(body).translate(x, y).rotate(paddle - swingIn), s)
    }
    leg(BellyPart.FAR_HIND, 0)
    leg(BellyPart.FAR_FRONT, 1)
    q.addThrough(belly[BellyPart.BODY.ordinal], body, s)
    val toss = sin(t * ROCK_RATE + 0.7).toFloat() * HEAD_TOSS * w
    q.addThrough(belly[BellyPart.HEAD.ordinal], at.set(body).translate(HEAD_X, -UPSIDE_DOWN_JOINT).rotate(toss), s)
    leg(BellyPart.NEAR_HIND, 2)
    leg(BellyPart.NEAR_FRONT, 3)
}

/** How flat he looks at the moment he's half way over (as a share of his height). */
private const val FLATTEST = 0.45f
private const val BELLY_MIDDLE = 18f
private const val ROCK = 0.09f // Radians either way, wriggling hardest.
private const val ROCK_RATE = 7.0
private const val HEAD_TOSS = 0.1f
private const val PADDLE = 0.4f
private const val PADDLE_IDLE = 0.08f
private const val PADDLE_RATE = 9.0
