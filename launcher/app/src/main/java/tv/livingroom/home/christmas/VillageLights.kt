package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import tv.livingroom.home.screensaver.mix

/*
 * The village's buildings and everything with a light in it: houses and their
 * windows, strings of fairy lights, the big tree, the market stalls and the
 * lamps. How bright they are comes from the look ([VillageLook.lights]).
 */

/** The five fairy-light colours, in the order they repeat along a string. */
val BULBS = intArrayOf(0xFF5A4F, 0xFFCF4A, 0x58E08A, 0x5AB4FF, 0xFF86CF)

/** A bulb's own colour, from dark glass when off to its full [rgb] at [brightness] 1. */
fun bulbColour(rgb: Int, brightness: Float): Int = mix(rgb, 0x1A1A2A, 0.65f - 0.65f * (brightness * 1.4f).coerceIn(0f, 1f))

/**
 * A string of [count] fairy lights from ([x0], [y0]) to ([x1], [y1]), sagging [sag] in the middle. [shift] and
 * [phase] start the colours and the brightness pattern somewhere else, so neighbouring strings differ.
 */
data class LightString(
    val x0: Float,
    val y0: Float,
    val x1: Float,
    val y1: Float,
    val sag: Float,
    val count: Int,
    val shift: Int = 0,
    val phase: Float = 0f,
    val radius: Float = 5f,
) {
    /** The point the wire sags towards. */
    internal val pullX: Float get() = (x0 + x1) / 2
    internal val pullY: Float get() = (y0 + y1) / 2 + sag * 2

    /** Where bulb [i] hangs: just under the wire. */
    fun bulbAt(i: Int): Pair<Float, Float> {
        val u = (i + 0.5f) / count
        val x = (1 - u) * (1 - u) * x0 + 2 * u * (1 - u) * pullX + u * u * x1
        return x to (1 - u) * (1 - u) * y0 + 2 * u * (1 - u) * pullY + u * u * y1 + 5
    }

    fun colourOf(i: Int): Int = (i + shift) % BULBS.size
}

/**
 * A light that twinkles. The still picture shows it at its dimmest; the moving layer adds the rest on
 * each frame as one soft light from the moving sheet ([light], a number in [MotionSprites.lights]),
 * [scale] times its size, centred on ([x], [y]). Its strength swings as a sine, [rate] radians a second
 * from [offset]: between nothing and 2 x [gain], times how bright the village's lights are.
 * [onTree] lights follow the big tree's brightness, which never goes right down.
 */
data class Twinkle(
    val x: Float,
    val y: Float,
    val light: Int,
    val scale: Float,
    val rate: Float,
    val offset: Float,
    val gain: Float,
    val onTree: Boolean = false,
) {
    /** How strongly the added light shows at [t] seconds, before the village's brightness. */
    fun strength(t: Double): Float = gain * (1 + sin(t * rate + offset).toFloat())
}

/** A fairy light swings between [STRING_DIMMEST] and 1 of its full brightness, a tree bulb between [TREE_DIMMEST] and 1. */
const val STRING_DIMMEST = 0.1f
const val TREE_DIMMEST = 0.2f

/** The tree's star pulses between this and 1. */
const val STAR_DIMMEST = 0.5f

/** How bright the big tree's lights are when the village's are [lights]: never right down, so it's a lit tree by day too. */
fun treeBrightness(lights: Float): Float = max(lights, 0.35f)

/** The twinkling of every bulb on [s]. */
fun stringTwinkles(s: LightString): List<Twinkle> = List(s.count) { i ->
    val (x, y) = s.bulbAt(i)
    Twinkle(x, y, s.colourOf(i), s.radius / BULB_CORE, STRING_RATE, i * 1.9f + s.phase, (1 - STRING_DIMMEST) / 2)
}

internal fun Easel.lights(s: LightString) {
    sketch.line(ink(0x0F121E, 0.8f), 2f) { moveTo(s.x0, s.y0).quadTo(s.pullX, s.pullY, s.x1, s.y1) }
    for (i in 0 until s.count) {
        val (x, y) = s.bulbAt(i)
        val colour = BULBS[s.colourOf(i)]
        sketch.glow(x, y, s.radius * 4.5f, colour, 0.55f * STRING_DIMMEST * look.lights)
        sketch.disc(bulbColour(colour, look.lights * STRING_DIMMEST), x, y, s.radius)
    }
}

/** The warm light a lit window throws around itself. */
const val WINDOW_GLOW = 0xFFB14A

/** How bright a window [lit] 0 to 1 shows in the still picture: a fire inside flickers around this. */
fun windowLight(lit: Float): Float = lit * 0.86f

/** The glass of a window in [look]'s light, from dark blue to warm yellow as it's [lit]. */
fun windowGlass(look: VillageLook, lit: Float): Int = mix(look.lit(0x2A3A5C), 0xFFD27A, windowLight(lit).coerceIn(0f, 1f))

/** A window in its frame, with snow on the sill; [lit] is 0 to 1. */
internal fun Easel.window(box: Area, lit: Float) {
    val x = box.x
    val y = box.y
    val w = box.w
    val h = box.h
    val f = windowLight(lit)
    if (f > 0.02f) sketch.glow(x + w / 2, y + h / 2, max(w, h) * 1.9f, WINDOW_GLOW, 0.5f * f)
    val frame = c(0x3A2A20)
    sketch.box(frame, x - 4, y - 4, w + 8, h + 8)
    sketch.box(windowGlass(look, lit), x, y, w, h)
    if (f > 0.02f) sketch.box(ink(0xFFF0BE, 0.5f * f), x, y + h * 0.45f, w, h * 0.55f)
    sketch.box(frame, x + w / 2 - 2, y, 4f, h)
    sketch.box(frame, x, y + h / 2 - 2, w, 4f)
    sketch.roundBox(snow, Area(x - 7, y + h + 1, w + 14, 7f), 3f)
}

/** A village house: the middle of its base is [x] across, and it's [w] wide and [h] high to the eaves. */
data class House(val x: Float, val w: Float, val h: Float, val colour: Int, val door: Int = 0x5A3324, val chimney: Boolean = true)

/**
 * [house] standing on [y], its two front windows and its attic window [lit] 0 to 1 each, the snow on
 * its roof [depth] thick (1 is a normal fall).
 */
internal fun Easel.house(house: House, y: Float, lit: FloatArray, depth: Float) {
    val (x, w, h) = house
    val roof = h * 0.62f
    val wall = c(house.colour)
    sketch.box(wall, x - w / 2, y - h, w, h)
    sketch.box(ink(0x00001E, 0.13f), x + w * 0.18f, y - h, w * 0.32f, h)
    if (house.chimney) {
        sketch.box(c(0x7A5248), x + w * 0.2f, y - h - roof * 0.95f, w * 0.13f, roof * 0.7f)
        sketch.roundBox(snow, Area(x + w * 0.17f, y - h - roof * 0.95f - 7, w * 0.19f, 11f), 5f)
    }
    sketch.shape(wall, x - w / 2, y - h, x + w / 2, y - h, x, y - h - roof)
    sketch.line(c(0x5A342A), w * 0.07f) { through(x - w * 0.58f, y - h + 4, x, y - h - roof, x + w * 0.58f, y - h + 4) }
    sketch.line(snow, w * 0.1f * depth) { through(x - w * 0.6f, y - h - 2, x, y - h - roof - w * 0.05f, x + w * 0.6f, y - h - 2) }
    val ww = w * 0.2f
    window(Area(x - w * 0.34f, y - h * 0.82f, ww, h * 0.34f), lit[0])
    window(Area(x + w * 0.14f, y - h * 0.82f, ww, h * 0.34f), lit[1])
    window(Area(x - ww * 0.4f, y - h - roof * 0.5f, ww * 0.8f, ww * 0.8f), lit[2])
    sketch.roundBox(c(house.door), Area(x - w * 0.09f, y - h * 0.4f, w * 0.18f, h * 0.4f), 6f, bottomRadius = 0f)
}

/** Where the top of [house]'s chimney is when it stands on [y]: where its smoke starts. */
fun chimneyTop(house: House, y: Float): Pair<Float, Float> = (house.x + house.w * 0.265f) to (y - house.h - house.h * 0.62f * 0.95f - 7)

/** A five-pointed star, its first point straight up unless [turn] (radians) says otherwise. */
internal fun PathMaker.star(x: Float, y: Float, radius: Float, turn: Float = -PI.toFloat() / 2): PathMaker {
    for (i in 0 until 10) {
        val angle = turn + i * 2 * PI.toFloat() / 10
        val r = if (i % 2 == 1) radius * 0.45f else radius
        if (i == 0) moveTo(x + cos(angle) * r, y + sin(angle) * r) else lineTo(x + cos(angle) * r, y + sin(angle) * r)
    }
    return close()
}

/**
 * The big decorated tree in the square, [h] tall with its foot at ([x], [y]): five snowy tiers, ornaments,
 * seven rows of warm lights and the star. [brightness] is how strongly its lights show (0 to 1).
 */
internal fun Easel.bigTree(x: Float, y: Float, h: Float, brightness: Float) {
    val w = h * 0.62f
    val green = c(0x1F5A3C)
    sketch.box(c(0x5A3A26), x - h * 0.035f, y - h * 0.09f, h * 0.07f, h * 0.09f)
    for (i in 0 until TREE_TIERS) {
        val f = i.toFloat() / TREE_TIERS
        val by = y - h * 0.07f - h * 0.86f * f
        val top = y - h * 0.07f - h * 0.86f * min(1f, f + 1.7f / TREE_TIERS)
        val hw = w * 0.5f * (1 - f * 0.72f)
        sketch.fill(mix(green, 0x000000, 0.06f * (TREE_TIERS - i))) {
            moveTo(x, top).lineTo(x + hw, by).quadTo(x + hw * 0.5f, by + h * 0.03f, x, by + h * 0.012f)
            quadTo(x - hw * 0.5f, by + h * 0.03f, x - hw, by).close()
        }
        snowOnTier(x, top, lerp(top, by, 0.56f), hw * 0.56f, h * 0.02f)
    }
    val r = Mulberry(14)
    val top = y - h * 0.93f
    val ornaments = intArrayOf(0xD93A3A, 0xF0B83A, 0x3F8FD8, 0xE9E2D0)
    for (i in 0 until 16) {
        val v = 0.12f + r.next() * 0.8f
        val reach = w * 0.5f * (0.1f + v * 0.8f) * 0.8f
        sketch.disc(c(ornaments[i % 4]), x + (r.next() * 2 - 1) * reach, lerp(top, y - h * 0.1f, v), h * 0.016f)
    }
    treeLights(x, y, h, brightness)
    sketch.glow(x, top - h * 0.02f, h * STAR_GLOW, 0xFFE08A, 0.7f * STAR_DIMMEST * brightness)
    sketch.fill(mix(0x8A7A40, 0xFFF2B0, (brightness + 0.25f).coerceIn(0f, 1f))) { star(x, top - h * 0.02f, h * 0.05f) }
}

/** One of the big tree's bulbs: where it is, and how far on it is (the lights come on from the bottom up; all the way on here). */
internal class TreeBulb(val x: Float, val y: Float, val on: Float)

/** The big tree's lights, for a tree [h] tall with its foot at ([x], [y]): seven rows of warm bulbs, more in each row down. */
internal fun treeBulbs(x: Float, y: Float, h: Float): List<TreeBulb> {
    val w = h * 0.62f
    val top = y - h * 0.93f
    val bulbs = ArrayList<TreeBulb>()
    for (row in 0 until 7) {
        val v = 0.14f + row * 0.122f
        val reach = w * 0.5f * (0.12f + v * 0.84f)
        val count = 3 + row
        for (j in 0 until count) {
            val u = (j + 0.5f) / count
            val by = lerp(top, y - h * 0.08f, v) + sin(u * PI.toFloat()) * h * 0.022f + (if (row % 2 == 1) u else 1 - u) * h * 0.03f
            bulbs += TreeBulb(x - reach * 0.9f + u * reach * 1.8f, by, (v * 6).coerceIn(0f, 1f))
        }
    }
    return bulbs
}

private fun Easel.treeLights(x: Float, y: Float, h: Float, brightness: Float) {
    for (bulb in treeBulbs(x, y, h)) {
        sketch.glow(bulb.x, bulb.y, h * TREE_GLOW, TREE_BULB, 0.6f * TREE_DIMMEST * bulb.on * brightness)
        val glass = mix(TREE_BULB, 0x18222A, 0.7f - 0.7f * (bulb.on * brightness * TREE_DIMMEST * 1.5f).coerceIn(0f, 1f))
        sketch.disc(glass, bulb.x, bulb.y, h * 0.0095f)
    }
}

/**
 * The twinkling of the big tree's bulbs and the pulse of its star, for a tree [h] tall with its foot
 * at ([x], [y]). The sheet's lights are made 0.55 strong at their middle; the gains make up the difference.
 */
fun treeTwinkles(x: Float, y: Float, h: Float): List<Twinkle> {
    val bulbs = treeBulbs(x, y, h).mapIndexed { n, bulb ->
        Twinkle(
            bulb.x,
            bulb.y,
            WARM_LIGHT,
            h * TREE_GLOW / BULB_REACH,
            TREE_RATE,
            n * 2.3f,
            (1 - TREE_DIMMEST) / 2 * bulb.on * 0.6f / 0.55f,
            true,
        )
    }
    val star =
        Twinkle(x, y - h * 0.95f, HALO_LIGHT, h * STAR_GLOW / HALO_REACH, STRING_RATE, 0f, (1 - STAR_DIMMEST) / 2 * 0.7f / 0.55f, true)
    return bulbs + star
}

/**
 * A market stall [w] wide with a striped awning in [stripes] (two colours) and a lantern; ([x], [y]) is the middle of its foot.
 */
internal fun Easel.stall(x: Float, y: Float, w: Float, stripes: IntArray) {
    val h = w * 0.62f
    val post = c(0x6A4630)
    sketch.box(post, x - w / 2 + 6, y - h, 7f, h)
    sketch.box(post, x + w / 2 - 13, y - h, 7f, h)
    sketch.box(mix(c(0x3A2A22), 0xFFB14A, 0.55f * look.lights), x - w / 2 + 10, y - h * 0.86f, w - 20, h * 0.5f)
    sketch.box(c(0x8A5A3A), x - w / 2, y - h * 0.42f, w, h * 0.42f)
    sketch.box(c(0x74482C), x - w / 2, y - h * 0.42f, w, 7f)
    val goods = intArrayOf(0xD93A3A, 0xF0B83A, 0x3FA25A, 0xE9E2D0, 0xD93A3A, 0x3F8FD8)
    goods.forEachIndexed { i, colour -> sketch.disc(c(colour), x - w * 0.36f + i * w * 0.145f, y - h * 0.42f - 8, 8f) }
    val topLeft = x - w / 2 + 4
    val topRight = x + w / 2 - 4
    val lowLeft = x - w / 2 - 16
    val lowRight = x + w / 2 + 16
    for (i in 0 until AWNING_STRIPES) {
        val from = i.toFloat() / AWNING_STRIPES
        val to = (i + 1f) / AWNING_STRIPES
        val la = lerp(lowLeft, lowRight, from)
        val lb = lerp(lowLeft, lowRight, to)
        sketch.fill(c(stripes[i % 2])) {
            moveTo(lerp(topLeft, topRight, from), y - h * 1.22f).lineTo(lerp(topLeft, topRight, to) + 0.6f, y - h * 1.22f)
            lineTo(lb + 0.6f, y - h * 0.9f).quadTo((la + lb) / 2, y - h * 0.9f + 16, la, y - h * 0.9f).close()
        }
    }
    sketch.roundBox(snow, Area(x - w / 2 - 6, y - h * 1.22f - 12, w + 12, 16f), 8f)
    sketch.glow(x, y - h * 0.72f, 70f, 0xFFB14A, 0.6f * look.lights * 0.9f)
    sketch.roundBox(mix(0x6A5A3A, 0xFFE2A0, look.lights), Area(x - 6, y - h * 0.8f, 12f, 16f), 3f)
}

/** A street lamp [h] tall with its foot at ([x], [y]). */
internal fun Easel.lamp(x: Float, y: Float, h: Float) {
    val iron = c(0x22252E)
    sketch.box(iron, x - 4, y - h, 8f, h)
    sketch.roundBox(iron, Area(x - 9, y - 10, 18f, 10f), 3f)
    sketch.glow(x, y - h - 10, 120f, 0xFFC56A, 0.55f * look.lights)
    sketch.roundBox(mix(0x5A5648, 0xFFF0C0, (look.lights * 1.2f).coerceIn(0f, 1f)), Area(x - 11, y - h - 26, 22f, 28f), 5f)
    sketch.shape(iron, x - 15, y - h - 26, x + 15, y - h - 26, x, y - h - 40)
    sketch.roundBox(snow, Area(x - 13, y - h - 44, 26f, 8f), 4f)
}

private const val TREE_TIERS = 5
private const val TREE_BULB = WARM_BULB
private const val TREE_GLOW = 0.05f // A tree bulb's light reaches this share of the tree's height...
private const val STAR_GLOW = 0.13f // ...and the star's this.
private const val STRING_RATE = 2.2f // How fast a fairy light and the star swing, radians a second...
private const val TREE_RATE = 1.6f // ...and a tree bulb.
private const val AWNING_STRIPES = 6
