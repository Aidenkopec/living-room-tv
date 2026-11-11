package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import tv.livingroom.home.screensaver.mix

/*
 * Fireside's small things: the gingerbread person who walks the mantel, the
 * blue present's spring, star and confetti, a spark off the poked fire, and
 * what Santa leaves in the stockings. Each in its own units, ported from the
 * mockup (K.ginger, K.present's surprise, the stocking's stuffing).
 */

/**
 * A gingerbread person (the mockup's K.ginger), its feet at (0, 0): [walk] is its stride (two steps to a whole), [wave] 0 to 1
 * raises an arm, which swings by [waveSwing] (-1 to 1).
 */
fun gingerbreadMarks(walk: Float, wave: Float, waveSwing: Float): List<Mark> = Sketch().apply {
    val dough = ink(0xB9733C)
    val icing = ink(0xFFF7EA)
    val sw = sin(walk * PI.toFloat()) * 0.5f
    fun limb(x0: Float, y0: Float, a: Float, length: Float) {
        val x1 = x0 + sin(a) * length
        val y1 = y0 + cos(a) * length
        line(dough, 20f) { through(x0, y0, x1, y1) }
        line(icing, 3.5f) {
            through(x1 - cos(a) * 8 - sin(a) * 7, y1 + sin(a) * 8 - cos(a) * 7, x1 + cos(a) * 8 - sin(a) * 7, y1 - sin(a) * 8 - cos(a) * 7)
        }
    }
    limb(-9f, -44f, -0.18f + sw, 36f)
    limb(9f, -44f, 0.18f - sw, 36f)
    limb(-14f, -82f, -1.0f - sw * 0.8f, 34f)
    limb(14f, -82f, lerp(1.0f + sw * 0.8f, 2.5f + waveSwing * 0.35f, wave), 34f)
    roundBox(dough, Area(-19f, -92f, 38f, 54f), 16f)
    disc(dough, 0f, -110f, 23f)
    disc(ink(0x3A2418), -8f, -114f, 2.8f)
    disc(ink(0x3A2418), 8f, -114f, 2.8f)
    line(icing, 3f) { arc(Ellipse(0f, -108f, 10f, 10f), 0.25f, PI.toFloat() - 0.25f) }
    disc(ink(0xD93A3A), 0f, -78f, 4f)
    disc(ink(0x3FA25A), 0f, -62f, 4f)
}.marks

/** How many steps of the gingerbread person's walk are painted (over a whole stride), and of its wave. */
const val GINGER_WALK_FRAMES = 6
const val GINGER_WAVE_FRAMES = 5

/** The blue present's spring, its foot at (0, 0), [SPRING_H] tall as painted. */
fun springMarks(): List<Mark> = Sketch().apply {
    line(ink(0xC9C9D6), 4f) {
        moveTo(-10f, 0f)
        for (j in 1..8) lineTo(if (j % 2 == 1) 10f else -10f, -SPRING_H * j / 8)
    }
}.marks

const val SPRING_H = 96f

/** The star on top of the spring, around (0, 0), pointing up. */
fun popStarMarks(): List<Mark> = Sketch().apply { fill(ink(0xFFD84A)) { star(0f, 0f, 24f) } }.marks

/** One square of confetti, [colour], from (0, 0). */
fun confettiMarks(colour: Int): List<Mark> = Sketch().apply { box(ink(colour), 0f, 0f, 7f, 7f) }.marks

/** A spark's bright middle, around (0, 0). */
fun sparkMarks(): List<Mark> = Sketch().apply { disc(ink(0xFFE6A0), 0f, 0f, 2f) }.marks

/** A candy cane as it pokes out of a person's stocking, in the stocking's units at rest: white, with red stripes. */
fun candyCaneMarks(): List<Mark> = Sketch().apply {
    val straight = 20f
    val bend = 9f
    fun at(s: Float): Pair<Float, Float> {
        if (s <= straight) return -14f to 24 - s
        val a = PI.toFloat() + (s - straight) / bend
        return -5 + bend * cos(a) to 4 + bend * sin(a)
    }
    val length = straight + bend * PI.toFloat()
    line(ink(0xFFFAF2), 8f, round = false) {
        moveTo(-14f, 24f)
        for (k in 1..24) at(length * k / 24).let { (x, y) -> lineTo(x, y) }
    }
    var from = 0f
    while (from < length) { // Stripes 5 long, 6 apart.
        val to = minOf(from + 5, length)
        line(ink(0xD8342F), 8f, round = false) {
            at(from).let { (x, y) -> moveTo(x, y) }
            for (k in 1..4) at(lerp(from, to, k / 4f)).let { (x, y) -> lineTo(x, y) }
        }
        from += 11
    }
}.marks

/** The little green parcel beside the candy cane, at rest. */
fun stockingParcelMarks(): List<Mark> = Sketch().apply {
    box(ink(0x3FA25A), 6f, 6f, 20f, 20f)
    box(ink(0xF2C94A), 14f, 6f, 4f, 20f)
}.marks

/** [p]'s box (its paper, the shaded side, the ribbon), its foot's middle at (0, 0); colours through [shade]. */
internal fun Sketch.giftBox(p: Present, shade: (Int) -> Int) {
    val w = p.w
    val h = p.h
    box(shade(p.paper), -w / 2, -h, w, h)
    box(ink(0x000014, 0.14f), w * 0.15f, -h, w * 0.35f, h)
    box(shade(p.ribbon), -w * 0.08f, -h, w * 0.16f, h)
}

/** [p]'s lid and bow, the lid's bottom middle at (0, 0) (the box's top); colours through [shade]. */
internal fun Sketch.giftLid(p: Present, shade: (Int) -> Int) {
    val w = p.w
    val h = p.h
    box(shade(mix(p.paper, 0xFFFFFF, 0.12f)), -w / 2 - 5, -h * 0.2f, w + 10, h * 0.2f)
    box(shade(p.ribbon), -w * 0.08f, -h * 0.2f, w * 0.16f, h * 0.2f)
    oval(shade(p.ribbon), Ellipse(-w * 0.14f, -h * 0.3f, w * 0.15f, h * 0.1f, -0.5f))
    oval(shade(p.ribbon), Ellipse(w * 0.14f, -h * 0.3f, w * 0.15f, h * 0.1f, 0.5f))
    disc(shade(p.ribbon), 0f, -h * 0.24f, w * 0.06f)
}
