package tv.livingroom.home.christmas

import java.time.LocalDate
import java.time.Month
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import tv.livingroom.home.screensaver.mix

/*
 * The dock dressed for Christmas: snow lying on each tile, holly on the bar's
 * ends, a string of fairy lights along its bottom edge and a warm glow under
 * the selected app. These are the rules and the shapes, in pixels on the
 * 1920 x 1080 screen; DockDressingView paints them once into small pictures
 * and places those each frame.
 */

/** The countdown that ends the line under the clock from December 1 to 25: "12 sleeps until Christmas"; null on any other date. */
fun christmasLine(date: LocalDate): String? {
    if (date.month != Month.DECEMBER || date.dayOfMonth > CHRISTMAS_DAY) return null
    return when (val sleeps = CHRISTMAS_DAY - date.dayOfMonth) {
        0 -> "Merry Christmas"
        1 -> "1 sleep until Christmas"
        else -> "$sleeps sleeps until Christmas"
    }
}

/** The colours of snow lying on the dock in [look]'s light: the snow, and the blue shadow under its lip. */
data class CapColours(val snow: Int, val shadow: Int)

fun capColours(look: VillageLook): CapColours =
    CapColours(mix(WHITE, look.amb, look.ambK * 0.16f), mix(0x9DB4E8, look.amb, look.ambK * 0.45f))

/** A snow cap's picture: it overhangs its tile by [CAP_PAD_X] each side, with the tile's top edge [CAP_PAD_TOP] down it. */
const val CAP_PAD_X = 8f
const val CAP_PAD_TOP = 20f
const val CAP_HEIGHT = 46f

/**
 * Snow lying on a tile [width] wide: a mound with uneven drips over the tile's top edge, a shadow
 * under its lip, a soft highlight and a glint or two. [seed] varies the drips and glints from tile
 * to tile. Drawn in its own picture's pixels ([width] + 2 × [CAP_PAD_X] by [CAP_HEIGHT]).
 */
fun snowCapMarks(width: Float, seed: Int, colours: CapColours): List<Mark> {
    val sketch = Sketch()
    val x = CAP_PAD_X
    val y = CAP_PAD_TOP
    val drips = max(2, (width / 34).roundToInt())
    val dripWidth = (width + 8) / drips

    fun mound(colour: Int, down: Float) = sketch.fill(colour) {
        val edge = y + CAP_LIP + down
        moveTo(x - 4, edge).quadTo(x - 7, y - CAP_RISE * 0.36f, x + width * 0.14f, y - CAP_RISE * 0.52f)
        quadTo(x + width * 0.5f, y - CAP_RISE, x + width * 0.86f, y - CAP_RISE * 0.52f)
        quadTo(x + width + 7, y - CAP_RISE * 0.36f, x + width + 4, edge)
        for (j in drips downTo 1) {
            val from = x - 4 + dripWidth * j
            val to = x - 4 + dripWidth * (j - 1)
            val depth = 5 + ((j * 7 + seed * 3) % 4) * 3.5f
            quadTo((from + to) / 2, edge + depth * 2, to, edge)
        }
        close()
    }
    mound(colours.shadow, 3.5f)
    mound(colours.snow, 0f)
    sketch.oval(ink(0xFFFFFF, 0.75f), Ellipse(x + width * 0.42f, y - CAP_RISE * 0.56f, width * 0.24f, 3f))
    for ((across, phase) in listOf(0.24f to 0f, 0.74f to 2.4f)) {
        val glint = sin(seed * 1.7f + phase)
        if (glint > 0.05f) sketch.fill(ink(0xFFFFFF, glint)) { star(x + width * across, y - 8, 2 + 5 * glint, turn = 0f) }
    }
    return sketch.marks
}

/** The holly picture's size, and where in it the sprig's berries sit (its other end mirrored for the bar's right end). */
const val HOLLY_W = 48f
const val HOLLY_H = 40f
const val HOLLY_X = 10f
const val HOLLY_Y = 24f

/** A sprig of holly, two leaves and three berries; [flip] mirrors it for the bar's right end. */
fun hollyMarks(flip: Boolean): List<Mark> {
    val sketch = Sketch()
    sketch.placed(if (flip) HOLLY_W - HOLLY_X else HOLLY_X, HOLLY_Y, flip = flip) {
        for ((turn, green) in listOf(-0.5f to 0x2F8A52, 0.35f to 0x256F42)) {
            sketch.placed(0f, 0f, turn = turn) {
                sketch.fill(ink(green)) {
                    moveTo(
                        0f,
                        0f,
                    ).quadTo(8f, -13f, 17f, -7f).quadTo(23f, -12f, 31f, -2f).quadTo(22f, 5f, 16f, 2f).quadTo(8f, 8f, 0f, 0f).close()
                }
            }
        }
        sketch.disc(ink(0xE0352F), 0f, 0f, 5f)
        sketch.disc(ink(0xE0352F), 7f, 6f, 4.4f)
        sketch.disc(ink(0xE0352F), -3f, 8f, 4.2f)
        sketch.disc(ink(0xFFFFFF, 0.7f), -1.5f, -1.5f, 1.3f)
    }
    return sketch.marks
}

/** The dock's fairy lights: one about every [BULB_SPACING] px of the bar, starting and ending [STRING_INSET] px in. */
const val BULB_SPACING = 44f
const val STRING_INSET = 26f

/** The string's picture is as wide as the bar and [BULB_SIZE] px high: the bulbs along its middle, the wire [BULB_DROP] px above them. */
const val BULB_SIZE = 32f
const val BULB_DROP = 5f

fun dockBulbCount(barWidth: Float): Int = (barWidth / BULB_SPACING).roundToInt().coerceAtLeast(0)

/** How far along the bar (px from its left end) bulb [index] of [count] sits. */
fun dockBulbX(index: Int, count: Int, barWidth: Float): Float = STRING_INSET + (index + 0.5f) / count * (barWidth - 2 * STRING_INSET)

/** How bright the dock's string is in [look]'s light: never off, brightest when the village's lights are. */
fun dockLightsBrightness(look: VillageLook): Float = 0.6f + 0.4f * look.lights

/**
 * The dock's whole string of lights for a bar [barWidth] wide, [brightness] bright, in one picture
 * ([barWidth] by [BULB_SIZE]): the wire, and each bulb at its dimmest, the five colours in turn.
 * [dockTwinklesInto] adds each bulb's twinkling on every frame.
 */
fun dockStringMarks(barWidth: Float, brightness: Float): List<Mark> {
    val sketch = Sketch()
    val middle = BULB_SIZE / 2
    sketch.line(ink(0x0F121E, 0.8f), 2f) { through(STRING_INSET, middle - BULB_DROP, barWidth - STRING_INSET, middle - BULB_DROP) }
    val count = dockBulbCount(barWidth)
    for (i in 0 until count) {
        val x = dockBulbX(i, count, barWidth)
        val colour = BULBS[i % BULBS.size]
        sketch.glow(x, middle, BULB_RADIUS * 4.5f, colour, 0.55f * STRING_DIMMEST * brightness)
        sketch.disc(bulbColour(colour, brightness * STRING_DIMMEST), x, middle, BULB_RADIUS)
    }
    return sketch.marks
}

/** Where the dock's bar is on a frame, in screen pixels. One is kept and refilled on each frame. */
class DockBar(var left: Float = 0f, var top: Float = 0f, var width: Float = 0f, var height: Float = 0f)

/**
 * Where the dock's string of lights is on screen: its picture's left end and its bulbs' height (px), how
 * wide it's drawn, the width it was made for (the dressing's units) and the screen's pixels to each of
 * those units. One is kept and refilled on each frame.
 */
class StringPlace(var left: Float = 0f, var bulbsY: Float = 0f, var width: Float = 0f, var madeFor: Float = 0f, var unit: Float = 1f)

/**
 * Adds the twinkling of the dock's bulbs at [t] seconds: over each bulb of the string's picture, a
 * light from the moving sheet ([lights]), as the village's strings twinkle.
 */
fun dockTwinklesInto(quads: SpriteQuads, lights: List<Sprite>, place: StringPlace, brightness: Float, t: Double) {
    val count = dockBulbCount(place.madeFor)
    val gain = (1 - STRING_DIMMEST) / 2 * brightness
    for (i in 0 until count) {
        val x = place.left + dockBulbX(i, count, place.madeFor) / place.madeFor * place.width
        val strength = gain * (1 + sin(t * DOCK_TWINKLE_RATE + i * 1.9).toFloat())
        quads.add(lights[i % BULBS.size], x, place.bulbsY, BULB_RADIUS / BULB_CORE * place.unit, strength)
    }
}

/**
 * Where the dock's pieces sit in the one picture they share ([width] by [height]): every tile's snow
 * cap, the two sprigs of holly, and a clump and a speck of loose snow. Pieces drawn from one picture
 * cost this TV's GPU far less than one picture each.
 */
data class DockAtlas(
    val width: Float,
    val height: Float,
    val caps: List<Area>,
    val hollyLeft: Area,
    val hollyRight: Area,
    val clump: Area,
    val powder: Area,
)

/**
 * The shared picture's layout for tiles [tileWidths] wide: the caps one under another, then the holly
 * and the loose snow in a row, with a gap between them all so none bleeds into the next.
 */
fun dockAtlas(tileWidths: List<Float>): DockAtlas {
    var y = 0f
    val caps = tileWidths.map { width -> Area(0f, y, width + 2 * CAP_PAD_X, CAP_HEIGHT).also { y += CAP_HEIGHT + ATLAS_GAP } }
    val hollyLeft = Area(0f, y, HOLLY_W, HOLLY_H)
    val hollyRight = Area(HOLLY_W + ATLAS_GAP, y, HOLLY_W, HOLLY_H)
    val clump = Area(hollyRight.x + HOLLY_W + ATLAS_GAP, y, CLUMP_BOX, CLUMP_BOX)
    val powder = Area(clump.x + CLUMP_BOX + ATLAS_GAP, y, POWDER_BOX, POWDER_BOX)
    val width = max(caps.maxOfOrNull { it.w } ?: 0f, powder.x + POWDER_BOX)
    return DockAtlas(width, y + HOLLY_H, caps, hollyLeft, hollyRight, clump, powder)
}

/** The boxes the clump and the speck of powder are painted in the middle of. */
const val CLUMP_BOX = 30f
const val POWDER_BOX = 8f

/** The glow under the selected app: this colour, [DOCK_GLOW_ALPHA] strong, reaching 1.05 tile widths from its middle. */
const val DOCK_GLOW = 0xFFD9A0
const val DOCK_GLOW_ALPHA = 0.42f
const val DOCK_GLOW_REACH = 1.05f

/**
 * Whether a snow cap made for a tile [madeFor] wide suits a tile that is [tileWidth] wide at its
 * tallest. After the row changes (a console turns on), the old caps are still in use until the new
 * ones are painted; one of the wrong width is left off.
 */
fun capFits(madeFor: Float, tileWidth: Float): Boolean = abs(madeFor - tileWidth) < 0.5f

/** How much of its full height a tile's snow keeps when the tile is [scale] of the tallest: thinner on small tiles, but never a sliver. */
fun capHeightScale(scale: Float): Float = 0.55f + 0.45f * scale

private const val CHRISTMAS_DAY = 25
private const val CAP_RISE = 19f // How high the mound stands over the tile's top edge.
private const val CAP_LIP = 4f // How far its lip hangs below that edge.
private const val BULB_RADIUS = 3.4f
private const val ATLAS_GAP = 2f
private const val DOCK_TWINKLE_RATE = 2.2 // Radians a second, as the village's strings.
