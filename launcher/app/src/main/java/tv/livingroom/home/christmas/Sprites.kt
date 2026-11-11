package tv.livingroom.home.christmas

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Things that move are small ready-made pictures (sprites) cut from one shared
 * picture (a sheet) and placed on every frame. This file is the plain data:
 * where each sprite sits in its sheet, and the list of placed pieces for one
 * frame, kept in arrays that are reused from frame to frame, so a frame's
 * pieces cost no new objects. SpritePainter draws a list in one call.
 */

/**
 * One piece of a sheet: its box in the sheet's pixels ([x], [y], [w], [h]) and its anchor, the point
 * inside the box ([anchorX], [anchorY] from its top left) that lands on the place it's drawn at.
 */
data class Sprite(val x: Float, val y: Float, val w: Float, val h: Float, val anchorX: Float = w / 2, val anchorY: Float = h / 2)

/** Where pieces [sizes] big (width to height) go in a sheet: each one's box, and the sheet's own size. */
class SheetLayout(val width: Int, val height: Int, val cells: List<Area>)

/**
 * Packs pieces of the given [sizes] into rows no wider than [maxWidth], in order, [gap] pixels apart
 * and from the sheet's edge, so a piece drawn with smoothing never picks up its neighbour's pixels.
 * Sizes are rounded up to whole pixels.
 */
fun sheetLayout(sizes: List<Pair<Float, Float>>, maxWidth: Int, gap: Int = SHEET_GAP): SheetLayout {
    var x = gap
    var y = gap
    var rowHeight = 0
    var width = 0
    val cells = sizes.map { (w, h) ->
        val cellW = ceil(w).toInt()
        val cellH = ceil(h).toInt()
        if (x > gap && x + cellW + gap > maxWidth) {
            x = gap
            y += rowHeight + gap
            rowHeight = 0
        }
        Area(x.toFloat(), y.toFloat(), cellW.toFloat(), cellH.toFloat()).also {
            x += cellW + gap
            rowHeight = max(rowHeight, cellH)
            width = max(width, x)
        }
    }
    return SheetLayout(max(width, gap), y + rowHeight + gap, cells)
}

/** One piece to paint into a sheet: its [marks], [scale] times their own size, their own (0, 0) at ([x], [y]) of the sheet. */
class SheetPiece(val marks: List<Mark>, val x: Float, val y: Float, val scale: Float = 1f)

/** A sheet ready to paint: its size in pixels, what to paint where, and the [sprites] to cut from it, in the order they were added. */
class Sheet(val width: Int, val height: Int, val pieces: List<SheetPiece>, val sprites: List<Sprite>)

/** Collects the pieces of a sheet, then lays them out ([build]). */
class SheetMaker {
    private val drawings = ArrayList<List<Mark>>()
    private val boxes = ArrayList<Area>()
    private val scales = ArrayList<Float>()

    /**
     * Adds a piece drawn by [marks] around its anchor at (0, 0), painted [scale] times their size;
     * [box] is the part to keep, in the marks' units (frames of one figure share a box, so the figure
     * doesn't shift as they change). Gives back the piece's number in [Sheet.sprites].
     */
    fun add(marks: List<Mark>, box: Area, scale: Float = 1f): Int {
        drawings += marks
        boxes += box
        scales += scale
        return drawings.size - 1
    }

    /** Adds every one of [frames] as a piece, all in the one box that holds them all (and a pixel to spare). */
    fun addFrames(frames: List<List<Mark>>, scale: Float = 1f): List<Int> {
        val box = frames.map { marksBounds(it) }.reduce { a, b ->
            val left = min(a.x, b.x)
            val top = min(a.y, b.y)
            Area(left, top, max(a.x + a.w, b.x + b.w) - left, max(a.y + a.h, b.y + b.h) - top)
        }
        val spare = 1 / scale
        val padded = Area(box.x - spare, box.y - spare, box.w + 2 * spare, box.h + 2 * spare)
        return frames.map { add(it, padded, scale) }
    }

    /** Lays the pieces out, tallest first so rows waste little; each piece keeps its number. */
    fun build(maxWidth: Int): Sheet {
        val sizes = boxes.mapIndexed { i, box -> box.w * scales[i] to box.h * scales[i] }
        val order = sizes.indices.sortedByDescending { ceil(sizes[it].second) }
        val packed = sheetLayout(order.map { sizes[it] }, maxWidth)
        val cells = arrayOfNulls<Area>(sizes.size).also { cells -> order.forEachIndexed { place, i -> cells[i] = packed.cells[place] } }
        val layout = SheetLayout(packed.width, packed.height, cells.map { it!! })
        val pieces = drawings.mapIndexed { i, marks ->
            SheetPiece(marks, layout.cells[i].x - boxes[i].x * scales[i], layout.cells[i].y - boxes[i].y * scales[i], scales[i])
        }
        val sprites = layout.cells.mapIndexed { i, cell ->
            Sprite(
                cell.x,
                cell.y,
                cell.w,
                cell.h,
                -boxes[i].x * scales[i],
                -boxes[i].y * scales[i],
            )
        }
        return Sheet(layout.width, layout.height, pieces, sprites)
    }
}

/** How much wider and higher a piece is drawn than it is ([x] negative mirrors it): two numbers in one, so passing it makes no object. */
@JvmInline
value class Stretch private constructor(private val bits: Long) {
    constructor(x: Float, y: Float) : this((x.toRawBits().toLong() shl 32) or (y.toRawBits().toLong() and 0xFFFFFFFFL))

    val x: Float get() = Float.fromBits((bits ushr 32).toInt())
    val y: Float get() = Float.fromBits(bits.toInt())
}

/**
 * The pieces of one sheet to draw on a frame, in drawing order: for each, its four corners on screen
 * ([corners]), the matching corners in the sheet ([sheet]) and how strongly it shows ([strengths], a
 * white colour whose alpha is the strength, one per corner). Room for [capacity] pieces; more are dropped.
 */
class SpriteQuads(val capacity: Int) {
    val corners = FloatArray(capacity * CORNER_VALUES)
    val sheet = FloatArray(capacity * CORNER_VALUES)
    val strengths = IntArray(capacity * CORNERS)

    /** Two triangles for each piece, by corner number: the same for every frame. */
    val triangles = ShortArray(capacity * TRIANGLE_CORNERS).also {
        for (i in 0 until capacity) {
            val corner = i * CORNERS
            val at = i * TRIANGLE_CORNERS
            intArrayOf(0, 1, 2, 0, 2, 3).forEachIndexed { n, offset -> it[at + n] = (corner + offset).toShort() }
        }
    }

    var count = 0
        private set

    fun clear() {
        count = 0
    }

    /** [sprite] with its anchor at ([x], [y]), [scale] times its own size, [alpha] strong. */
    fun add(sprite: Sprite, x: Float, y: Float, scale: Float = 1f, alpha: Float = 1f) {
        if (alpha <= MIN_ALPHA || scale <= 0f) return
        put(sprite, upright(sprite, x, y, scale, scale), alpha)
    }

    /** [sprite] with its anchor at ([x], [y]), [scale] times its size, facing the other way when [flipped]: mirrored about the anchor. */
    fun addFacing(sprite: Sprite, x: Float, y: Float, flipped: Boolean, scale: Float = 1f) {
        put(sprite, upright(sprite, x, y, if (flipped) -scale else scale, scale), 1f)
    }

    /** [sprite] with its anchor at ([x], [y]), [scaleX] times as wide and [scaleY] times as high as it is. */
    fun addStretched(sprite: Sprite, x: Float, y: Float, scaleX: Float, scaleY: Float) {
        if (scaleX <= 0f || scaleY <= 0f) return
        put(sprite, upright(sprite, x, y, scaleX, scaleY), 1f)
    }

    /** The corners of an upright piece about its anchor; a negative [scaleX] mirrors it. */
    private fun upright(sprite: Sprite, x: Float, y: Float, scaleX: Float, scaleY: Float): FloatArray {
        val left = x - sprite.anchorX * scaleX
        val top = y - sprite.anchorY * scaleY
        return floatArrayOfCorners(left, top, left + sprite.w * scaleX, top + sprite.h * scaleY)
    }

    /** [sprite] with its anchor at ([x], [y]), [scale] times its own size and turned [turn] radians clockwise about the anchor. */
    fun addTurned(sprite: Sprite, x: Float, y: Float, scale: Float, turn: Float) {
        if (scale <= 0f) return
        addPlaced(sprite, x, y, Stretch(scale, scale), turn)
    }

    /**
     * [sprite] with its anchor at ([x], [y]), stretched by [stretch] (a negative width mirrors it), then
     * turned [turn] radians clockwise about the anchor, [alpha] strong.
     */
    fun addPlaced(sprite: Sprite, x: Float, y: Float, stretch: Stretch, turn: Float = 0f, alpha: Float = 1f) {
        val scaleX = stretch.x
        val scaleY = stretch.y
        if (scaleX == 0f || scaleY == 0f || alpha <= MIN_ALPHA) return
        val c = cos(turn)
        val s = sin(turn)
        val x0 = -sprite.anchorX * scaleX
        val y0 = -sprite.anchorY * scaleY
        val x1 = x0 + sprite.w * scaleX
        val y1 = y0 + sprite.h * scaleY
        val box = scratch
        box[0] = x + x0 * c - y0 * s
        box[1] = y + x0 * s + y0 * c
        box[2] = x + x1 * c - y0 * s
        box[3] = y + x1 * s + y0 * c
        box[4] = x + x1 * c - y1 * s
        box[5] = y + x1 * s + y1 * c
        box[6] = x + x0 * c - y1 * s
        box[7] = y + x0 * s + y1 * c
        put(sprite, box, alpha.coerceAtMost(1f))
    }

    /**
     * [sprite] placed through [at]: its anchor goes to [at]'s origin, and its box (painted [painted] times the size of the units [at]
     * works in) is carried through [at]'s turns, stretches and mirrors, shears included. [alpha] strong.
     */
    fun addThrough(sprite: Sprite, at: Turn2d, painted: Float = 1f, alpha: Float = 1f) {
        if (alpha <= MIN_ALPHA) return
        val x0 = -sprite.anchorX / painted
        val y0 = -sprite.anchorY / painted
        val x1 = x0 + sprite.w / painted
        val y1 = y0 + sprite.h / painted
        val box = scratch
        box[0] = at.mapX(x0, y0)
        box[1] = at.mapY(x0, y0)
        box[2] = at.mapX(x1, y0)
        box[3] = at.mapY(x1, y0)
        box[4] = at.mapX(x1, y1)
        box[5] = at.mapY(x1, y1)
        box[6] = at.mapX(x0, y1)
        box[7] = at.mapY(x0, y1)
        put(sprite, box, alpha.coerceAtMost(1f))
    }

    private val scratch = FloatArray(CORNER_VALUES)

    /** The corners of an upright box, clockwise from its top left, in the reused scratch array. */
    private fun floatArrayOfCorners(left: Float, top: Float, right: Float, bottom: Float): FloatArray {
        val box = scratch
        box[0] = left
        box[1] = top
        box[2] = right
        box[3] = top
        box[4] = right
        box[5] = bottom
        box[6] = left
        box[7] = bottom
        return box
    }

    private fun put(sprite: Sprite, box: FloatArray, alpha: Float) {
        if (count == capacity) return
        val at = count * CORNER_VALUES
        box.copyInto(corners, at, 0, CORNER_VALUES)
        val right = sprite.x + sprite.w
        val bottom = sprite.y + sprite.h
        sheet[at] = sprite.x
        sheet[at + 1] = sprite.y
        sheet[at + 2] = right
        sheet[at + 3] = sprite.y
        sheet[at + 4] = right
        sheet[at + 5] = bottom
        sheet[at + 6] = sprite.x
        sheet[at + 7] = bottom
        val strength = ink(0xFFFFFF, alpha)
        strengths.fill(strength, count * CORNERS, count * CORNERS + CORNERS)
        count++
    }

    companion object {
        const val CORNERS = 4
        const val CORNER_VALUES = 8 // An x and a y for each corner.
        const val TRIANGLE_CORNERS = 6
    }
}

/**
 * Darkens the pieces added since there were [from], keeping [keep] of their colour (their strength stays): the drawing
 * multiplies each piece by its corners' colour. For a piece drawn over the still picture where its own dark has been painted
 * into the picture under it.
 */
fun SpriteQuads.dimFrom(from: Int, keep: Float) {
    val k = (keep.coerceIn(0f, 1f) * 255).roundToInt()
    for (c in from * SpriteQuads.CORNERS until count * SpriteQuads.CORNERS) {
        strengths[c] = (strengths[c] and 0xFF000000.toInt()) or (k shl 16) or (k shl 8) or k
    }
}

private const val SHEET_GAP = 2
private const val MIN_ALPHA = 0.004f // Under one step of 255: nothing to see.
