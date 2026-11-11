package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import tv.livingroom.home.screensaver.mix

/*
 * Fireside's still picture: the cabin's room as marks on 1920 x 1080, back to
 * front. A stone fireplace in the middle with the mantel, its garland and the
 * stockings' hooks; a window onto the snow on the left, over a chest with books
 * and a mug; the lit tree and its presents on the right; a rug in front of the
 * fire. What moves (the fire, the candles' flames, the steam, the stockings, the
 * dogs, the twinkling, the snow outside) is drawn over it every frame. The view
 * through the window is painted first, by the screensaver's painter.
 */

/** The window's glass. */
const val WINDOW_X = 150f
const val WINDOW_Y = 130f
const val WINDOW_W = 470f
const val WINDOW_H = 500f
val WINDOW = Area(WINDOW_X, WINDOW_Y, WINDOW_W, WINDOW_H)

/**
 * Where the view through the window is drawn from: the outdoor scene (on the village's 1920 x 1080) is
 * moved by ([WINDOW_VIEW_X], [WINDOW_VIEW_Y]) and drawn [WINDOW_VIEW_SCALE] times its size.
 */
const val WINDOW_VIEW_X = WINDOW_X - 640f
const val WINDOW_VIEW_Y = WINDOW_Y - 10f
const val WINDOW_VIEW_SCALE = 0.62f

/** The part of the glass the snow outside is seen through: the curtains cover the edges. */
val WINDOW_SNOW = Area(WINDOW_X + 40f, WINDOW_Y, WINDOW_W - 80f, WINDOW_H - 42f) // Down to the snow on the sill.

/** The fireplace's opening, an arch from (858, 822) up to 610 and over, as an outline. */
fun PathMaker.fireplaceArch(): PathMaker =
    moveTo(858f, 822f).lineTo(858f, 610f).cubicTo(858f to 500f, 1142f to 500f, 1142f, 610f).lineTo(1142f, 822f).close()

/** The big tree: its foot and height. */
const val TREE_FOOT_X = 1600f
const val TREE_FOOT_Y = 952f
const val TREE_HEIGHT = 770f

/** Where the dark at the room's edges begins and is full, around ([EDGE_X], [EDGE_Y]). */
private const val EDGE_X = 1000f
private const val EDGE_Y = 640f
private const val EDGE_FROM = 380f
private const val EDGE_TO = 1320f

/** How dark the room's edges are at ([x], [y]) in [look]: 0 near the fire, the full shade at the corners. */
fun edgeDark(look: VillageLook, x: Float, y: Float): Float {
    val d = sqrt((x - EDGE_X) * (x - EDGE_X) + (y - EDGE_Y) * (y - EDGE_Y))
    return ((d - EDGE_FROM) / (EDGE_TO - EDGE_FROM)).coerceIn(0f, 1f) * edgeShade(look)
}

private fun edgeShade(look: VillageLook) = lerp(0.22f, 0.62f, look.dark)

/** The strings of fairy lights along the top of the wall and along the garland. */
val WALL_STRINGS = listOf(
    LightString(-10f, 52f, 400f, 60f, 30f, 10, phase = 1f),
    LightString(400f, 60f, 800f, 52f, 30f, 10, phase = 3f),
    LightString(1200f, 52f, 1570f, 60f, 30f, 9, phase = 5f),
    LightString(1570f, 60f, 1930f, 52f, 30f, 9, phase = 7f),
)
val GARLAND_STRINGS = List(4) { i -> LightString(706f + i * 147f, 340f, 853f + i * 147f, 340f, 27f, 5, phase = i.toFloat(), radius = 4f) }

/** The room's fairy lights are all one warm colour. */
const val ROOM_BULB = 0xFFD27A

/** How bright the wall and garland strings are: full by the fire's light, about half by day. */
fun roomStringBrightness(look: VillageLook, wall: Boolean): Float = lerp(if (wall) 0.45f else 0.5f, 1f, look.dark)

/** The wreath above the mantel, and its ten glowing berries' places and colours. */
const val WREATH_X = 1000f
const val WREATH_Y = 150f
const val WREATH_R = 66f
fun wreathBerry(i: Int): Pair<Float, Float> {
    val a = i / 10f * 2 * PI.toFloat() + 0.3f
    return WREATH_X + cos(a) * WREATH_R to WREATH_Y + sin(a) * WREATH_R
}
fun wreathBerryColour(i: Int): Int = if (i % 2 == 1) 0xFFD27A else 0xFF5A4F

/** How strongly a wreath berry glows at its brightest: more by the fire's light. */
fun wreathGlow(look: VillageLook): Float = lerp(0.25f, 0.6f, look.dark)

/** The share of a berry's, a string bulb's, the star's glow that is in the still picture: they never go darker. */
const val WREATH_DIMMEST = 0.2f
const val ROOM_STRING_DIMMEST = 0.1f

/**
 * How bright the tree's lights are: full by the fire's light, 0.6 by day, low late at night
 * (where the moving layer adds a slow breathing on top).
 */
fun roomTreeBrightness(scene: FiresideScene): Float = if (scene.late) LATE_TREE else lerp(0.6f, 1f, scene.look.dark)

/** The tree's lights late at night, and how far their slow breathing swings either side of it. */
const val LATE_TREE = 0.45f
const val LATE_TREE_SWING = 0.2f

/** A candle: the middle of its foot and how tall it is. */
data class Candle(val x: Float, val y: Float, val h: Float)

val CANDLES = listOf(Candle(WINDOW.x + WINDOW.w * 0.24f, WINDOW.y + WINDOW.h + 6, 46f), Candle(728f, 286f, 70f), Candle(1272f, 286f, 54f))

/** How brightly the candles burn: more by the fire's light. */
fun candleBrightness(look: VillageLook): Float = lerp(0.4f, 1f, look.dark)

/** A present: the middle of its foot, its size, its paper and its ribbon. */
data class Present(val x: Float, val y: Float, val w: Float, val h: Float, val paper: Int, val ribbon: Int)

val PRESENTS = listOf(
    Present(1420f, 992f, 96f, 74f, 0xC9302C, 0xF0C24A),
    Present(1504f, 1008f, 70f, 54f, 0x2F7A4A, 0xFFF4E6),
    Present(1806f, 1002f, 72f, 64f, 0xE0B34F, 0xC9302C),
    Present(1352f, 1018f, 60f, 46f, 0x7A4F9A, 0xFFF4E6),
    Present(1706f, 1000f, 104f, 80f, 0x3F7FD8, 0xFFF4E6), // The blue one.
)

/** The blue present, which pops open: it moves, so it isn't in the still picture. */
val BLUE_PRESENT = PRESENTS.last()

/** The parcel Santa leaves under the tree (cream with a red ribbon), and where it lands: its foot's middle. */
val SANTAS_PARCEL = Present(1596f, 1044f, 86f, 66f, 0xF4EFE6, 0xC9302C)

/** The room's walls and floor for [scene]: painted first, then the view through the window, then [firesideMarks]. */
fun roomWallMarks(scene: FiresideScene): List<Mark> = Room(scene).apply { walls() }.sketch.marks

/** The rest of the still room for [scene], back to front, over the walls and the window's view. */
fun firesideMarks(scene: FiresideScene): List<Mark> = Room(scene).apply {
    window()
    curtains()
    chest()
    fireplace()
    mantel()
    wreath()
    for (s in WALL_STRINGS) string(s, roomStringBrightness(look, wall = true))
    rug()
    tree()
    for (p in PRESENTS) if (p != BLUE_PRESENT) present(p)
    sketch.fill(
        Burst(EDGE_X, EDGE_Y, EDGE_TO, listOf(Stop(EDGE_FROM / EDGE_TO, ink(0x04020A, 0f)), Stop(1f, ink(0x04020A, edgeShade(look))))),
    ) {
        rect(0f, 0f, VILLAGE_W, VILLAGE_H)
    }
}.sketch.marks

/** What's seen through the window, on the village's 1920 x 1080 (drawn at [WINDOW_VIEW_SCALE]): mountains, Rundle, pines, snow. */
fun windowLandMarks(look: VillageLook): List<Mark> = Easel(look).apply {
    range(9, 640f, 170f, x0 = 1000f, x1 = 2000f)
    rundle(1150f, 668f, 0.66f)
    grove(8, 12, Span(1240f, 1830f), 716f, 90f..150f)
    ground(floatArrayOf(706f, 690f, 700f, 694f, 704f), look.snow, look.snowLow, Span(1000f, 1860f, 900f))
    pine(1330f, 800f, 250f)
    pine(1720f, 820f, 320f)
}.sketch.marks

/** The room being drawn: [scene]'s light on everything, and the [sketch] the marks go to. */
private class Room(val scene: FiresideScene) {
    val look = scene.look
    val sketch = Sketch()

    /** [rgb] in the room's light. */
    fun r(rgb: Int): Int = look.roomLit(rgb)
}

private fun Room.walls() {
    sketch.box(r(0x7D5542), 0f, 0f, VILLAGE_W, 822f)
    var x = 60f
    while (x < VILLAGE_W) {
        sketch.box(r(0x6C4737), x, 0f, 4f, 822f)
        x += 132f
    }
    sketch.fill(sketch.fade(0f, 820f, 0f, VILLAGE_H, Stop(0f, r(0x5C3B2A)), Stop(1f, r(0x3B261B)))) { rect(0f, 820f, VILLAGE_W, 260f) }
    for (y in floatArrayOf(866f, 918f, 984f, 1056f)) sketch.box(r(0x33201A), 0f, y, VILLAGE_W, 3f)
    sketch.box(r(0x4A2F22), 0f, 804f, VILLAGE_W, 20f)
}

private fun Room.window() {
    val wx = WINDOW.x
    val wy = WINDOW.y
    val ww = WINDOW.w
    val wh = WINDOW.h
    // Frost in the corners of the glass.
    sketch.clipped({ rect(wx, wy, ww, wh) }) {
        for (fx in floatArrayOf(wx, wx + ww)) {
            for (fy in floatArrayOf(wy, wy + wh)) {
                sketch.disc(ink(0xFFFFFF, 0.16f), fx, fy, 74f)
                sketch.disc(ink(0xFFFFFF, 0.16f), fx, fy, 46f)
            }
        }
    }
    // Snow piled on the sill outside.
    sketch.fill(look.lyingSnow) {
        moveTo(wx, wy + wh).lineTo(wx, wy + wh - 16)
        quadTo(wx + ww * 0.3f, wy + wh - 40, wx + ww * 0.55f, wy + wh - 22)
        quadTo(wx + ww * 0.8f, wy + wh - 34, wx + ww, wy + wh - 14)
        lineTo(wx + ww, wy + wh).close()
    }
    val frame = r(0xEADCC4)
    sketch.box(frame, wx - 9, wy - 9, ww + 18, 18f)
    sketch.box(frame, wx - 9, wy + wh - 9, ww + 18, 18f)
    sketch.box(frame, wx - 9, wy - 9, 18f, wh + 18)
    sketch.box(frame, wx + ww - 9, wy - 9, 18f, wh + 18)
    sketch.box(frame, wx + ww / 2 - 6, wy, 12f, wh)
    sketch.box(frame, wx, wy + wh / 2 - 6, ww, 12f)
    sketch.roundBox(frame, Area(wx - 30, wy + wh + 6, ww + 60, 22f), 5f)
    // Daylight falling through it onto the floor.
    val day = 0.085f * (1 - look.dark)
    if (day > 0.005f) sketch.shape(ink(0xFFF4D6, day), wx, wy + wh, wx + ww, wy + wh, wx + ww + 420, VILLAGE_H, wx + 160, VILLAGE_H)
}

private fun Room.curtains() {
    val wx = WINDOW.x
    val wy = WINDOW.y
    val ww = WINDOW.w
    val wh = WINDOW.h
    sketch.roundBox(r(0x3A2A20), Area(wx - 90, wy - 52, ww + 180, 12f), 6f)
    sketch.disc(r(0x3A2A20), wx - 90, wy - 46, 11f)
    sketch.disc(r(0x3A2A20), wx + ww + 90, wy - 46, 11f)
    for (side in intArrayOf(-1, 1)) {
        val ex = if (side < 0) wx - 66 else wx + ww + 66
        val ix = if (side < 0) wx + 46 else wx + ww - 46
        sketch.fill(r(0x93263A)) {
            moveTo(ex, wy - 44).lineTo(ix, wy - 44)
            quadTo(ix + side * 4, wy + wh * 0.5f, lerp(ex, ix, 0.42f), wy + wh * 0.62f)
            quadTo(lerp(ex, ix, 0.75f), wy + wh * 0.8f, lerp(ex, ix, 0.62f), wy + wh + 60)
            lineTo(ex, wy + wh + 60).close()
        }
        for (j in 1..3) {
            val x0 = lerp(ex, ix, j / 4f)
            sketch.line(r(0x6F1A2C), 5f) { moveTo(x0, wy - 40).quadTo(x0, wy + wh * 0.4f, lerp(ex, ix, 0.2f + j * 0.08f), wy + wh * 0.62f) }
        }
        sketch.roundBox(r(0xE0B34F), Area(lerp(ex, ix, 0.02f), wy + wh * 0.6f, kotlin.math.abs(ix - ex) * 0.46f, 12f), 6f)
    }
}

private fun Room.chest() {
    sketch.roundBox(r(0x6D4831), Area(170f, 700f, 430f, 122f), 8f)
    sketch.box(r(0x5A3A26), 170f, 716f, 430f, 6f)
    sketch.box(r(0x5A3A26), 378f, 722f, 6f, 100f)
    sketch.roundBox(r(0xE0B34F), Area(372f, 752f, 18f, 24f), 4f)
    val books = listOf(Triple(0x3F6F9A, 96f, 22f), Triple(0xA8323A, 84f, 20f), Triple(0x4F8F6A, 70f, 18f))
    books.forEachIndexed { i, (colour, w, h) -> sketch.roundBox(r(colour), Area(214f + i * 6, 700f - (i + 1) * 21 + (2 - i), w, h), 3f) }
    sketch.roundBox(r(0xF1E6D6), Area(486f, 656f, 44f, 44f), 6f)
    sketch.line(r(0xF1E6D6), 7f) { arc(Ellipse(532f, 678f, 12f, 12f), -1.2f, 1.2f) }
    sketch.box(r(0xC9302C), 486f, 672f, 44f, 8f)
    candleBody(CANDLES[0])
}

private fun Room.candleBody(c: Candle) = sketch.roundBox(r(0xF1E6D0), Area(c.x - 9, c.y - c.h, 18f, c.h), 4f)

/** Rows of rounded stones filling the box from [x], [y], [w] wide and [h] high, laid out by [seed]. */
private fun Room.stones(box: Area, seed: Int) {
    val random = Mulberry(seed)
    val colours = intArrayOf(0x8D867E, 0x7B746D, 0x9A938A, 0x857A70)
    sketch.box(r(0x4A433D), box.x, box.y, box.w, box.h)
    sketch.clipped({ rect(box.x, box.y, box.w, box.h) }) {
        var y = box.y + 4
        while (y < box.y + box.h) {
            var x = box.x - random.next() * 60
            while (x < box.x + box.w) {
                val w = 64 + random.next() * 70
                val colour = colours[floor(random.next() * 4).toInt().coerceIn(0, 3)]
                sketch.roundBox(r(colour), Area(x + 3, y, w - 6, 52f), 13f)
                x += w
            }
            y += 58
        }
    }
}

private fun Room.fireplace() {
    stones(Area(800f, 0f, 400f, 300f), 4)
    stones(Area(730f, 300f, 540f, 522f), 6)
    sketch.fill(ink(0x120A08)) { fireplaceArch() }
    sketch.roundBox(r(0x8A837B), Area(690f, 820f, 620f, 28f), 4f)
}

private fun Room.mantel() {
    sketch.roundBox(r(0x5C3A24), Area(690f, 286f, 620f, 44f), 5f)
    sketch.box(r(0x77492C), 690f, 286f, 620f, 9f)
    for (i in 0 until 4) {
        val x0 = 706f + i * 147
        val x1 = x0 + 147
        sketch.line(r(0x2C6A44), 26f) { moveTo(x0, 338f).quadTo((x0 + x1) / 2, 396f, x1, 338f) }
        sketch.line(r(0x3D8556), 9f) { moveTo(x0 + 8, 334f).quadTo((x0 + x1) / 2, 384f, x1 - 8, 334f) }
        string(GARLAND_STRINGS[i], roomStringBrightness(look, wall = false))
        sketch.disc(r(0xD93A3A), x0, 340f, 8f)
        sketch.disc(r(0xD93A3A), x0 + 12, 348f, 6f)
    }
    sketch.disc(r(0xD93A3A), 1294f, 340f, 8f)
    candleBody(CANDLES[1])
    candleBody(CANDLES[2])
}

/** A string of the room's warm fairy lights at their dimmest ([brightness] is how bright the string is). */
private fun Room.string(s: LightString, brightness: Float) {
    sketch.line(ink(0x0F121E, 0.8f), 2f) { moveTo(s.x0, s.y0).quadTo(s.pullX, s.pullY, s.x1, s.y1) }
    for (i in 0 until s.count) {
        val (x, y) = s.bulbAt(i)
        val dimmest = brightness * ROOM_STRING_DIMMEST
        sketch.glow(x, y, s.radius * 4.5f, ROOM_BULB, 0.55f * dimmest * (1 - edgeDark(look, x, y)))
        sketch.disc(bulbColour(ROOM_BULB, dimmest), x, y, s.radius)
    }
}

private fun Room.wreath() {
    sketch.line(r(0x2C6A44), 30f) { arc(Ellipse(WREATH_X, WREATH_Y, WREATH_R, WREATH_R), 0f, 2 * PI.toFloat()) }
    sketch.line(r(0x3D8556), 8f) { arc(Ellipse(WREATH_X, WREATH_Y, 72f, 72f), 0f, 2 * PI.toFloat()) }
    for (i in 0 until 10) {
        val (x, y) = wreathBerry(i)
        sketch.glow(x, y, 20f, wreathBerryColour(i), wreathGlow(look) * WREATH_DIMMEST)
        sketch.disc(r(wreathBerryColour(i)), x, y, 4.5f)
    }
    sketch.oval(r(0xC9302C), Ellipse(982f, 212f, 22f, 12f, 0.5f))
    sketch.oval(r(0xC9302C), Ellipse(1018f, 212f, 22f, 12f, -0.5f))
    sketch.disc(r(0xC9302C), 1000f, 210f, 9f)
}

private fun Room.rug() {
    sketch.oval(r(0x7C2A30), Ellipse(1000f, 950f, 430f, 92f))
    sketch.line(r(0xE6D2B0), 6f) { arc(Ellipse(1000f, 950f, 394f, 76f), 0f, 2 * PI.toFloat()) }
    sketch.oval(r(0x8F363C), Ellipse(1000f, 950f, 330f, 56f))
}

/** The lit tree: five tiers, ornaments, seven rows of bulbs (dark glass here; they light in the moving layer) and the star. */
private fun Room.tree() {
    val x = TREE_FOOT_X
    val y = TREE_FOOT_Y
    val h = TREE_HEIGHT
    val k = roomTreeBrightness(scene)
    sketch.oval(r(0xA8323A), Ellipse(1600f, 950f, 170f, 30f))
    sketch.glow(1600f, 600f, 520f, 0xFFCF8A, 0.16f * k)
    val w = h * 0.62f
    sketch.box(r(0x5A3A26), x - h * 0.035f, y - h * 0.09f, h * 0.07f, h * 0.09f)
    val green = r(0x1F5A3C)
    for (i in 0 until 5) {
        val f = i / 5f
        val by = y - h * 0.07f - h * 0.86f * f
        val top = y - h * 0.07f - h * 0.86f * minOf(1f, f + 1.7f / 5)
        val hw = w * 0.5f * (1 - f * 0.72f)
        sketch.fill(mix(green, 0x000000, 0.06f * (5 - i))) {
            moveTo(x, top).lineTo(x + hw, by).quadTo(x + hw * 0.5f, by + h * 0.03f, x, by + h * 0.012f)
            quadTo(x - hw * 0.5f, by + h * 0.03f, x - hw, by).close()
        }
    }
    val random = Mulberry(23)
    val top = y - h * 0.93f
    val ornaments = intArrayOf(0xD93A3A, 0xF0B83A, 0x3F8FD8, 0xE9E2D0)
    for (i in 0 until 16) {
        val v = 0.12f + random.next() * 0.8f
        val reach = w * 0.5f * (0.1f + v * 0.8f) * 0.8f
        sketch.disc(r(ornaments[i % 4]), x + (random.next() * 2 - 1) * reach, lerp(top, y - h * 0.1f, v), h * 0.016f)
    }
    treeBulbs(x, y, h).forEachIndexed { n, bulb -> sketch.disc(mix(BULBS[n % BULBS.size], 0x18222A, 0.7f), bulb.x, bulb.y, h * 0.0095f) }
    val starY = top - h * 0.02f
    sketch.glow(x, starY, h * 0.13f, 0xFFE08A, 0.7f * STAR_DIMMEST * k)
    sketch.fill(mix(0x8A7A40, 0xFFF2B0, (k + 0.25f).coerceIn(0f, 1f))) { star(x, starY, h * 0.05f) }
}

private fun Room.present(p: Present) = sketch.placed(p.x, p.y) {
    sketch.giftBox(p, ::r)
    sketch.placed(0f, -p.h) { sketch.giftLid(p, ::r) }
}

/** Where each stocking hangs from, left to right. */
val STOCKING_HOOKS = listOf(782f to 350f, 918f to 366f, 1042f to 366f, 1166f to 350f)
