package tv.livingroom.home.christmas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/*
 * The dressed dock on screen. Its snow, holly, bulbs and glow are painted once
 * into small pictures (again only when the light changes, at most once a
 * minute) and are only placed and scaled on each frame: this TV's GPU is slow
 * at gradients and curves drawn every frame. What changes on every frame (the
 * bulbs' twinkling, the snow's depth, loose snow and the rider) is drawn in
 * two layers of its own, under and over the tiles.
 */

/**
 * The dressing's ready-made pictures: the [glow], the whole [string] of lights (made for a bar of a
 * typical width and stretched the little it takes to fit), and one shared picture holding every
 * tile's snow cap, the holly and the loose snow, drawn through [atlas] as the sprites below.
 */
internal class DockSprites(
    val glow: ImageBitmap,
    val string: ImageBitmap,
    val atlas: SpritePainter,
    val pieces: DockPieces,
    /** Screen pixels per unit of the dressing's measures (which are pixels on a 1920 x 1080 screen). */
    val unit: Float,
    /** The bar width the string was made for (the dressing's units), and how bright its bulbs are at their fullest. */
    val stringWidth: Float,
    val brightness: Float,
)

/**
 * The shared picture's sprites. Each tile's snow ([caps]) is anchored on the tile's top left corner
 * and was made for a tile [capWidths] wide (the dressing's units); the holly for the bar's two ends
 * is anchored on its berries; the [clump] and the speck of [powder] on their middles.
 */
class DockPieces(
    val caps: List<Sprite>,
    val capWidths: List<Float>,
    val hollyLeft: Sprite,
    val hollyRight: Sprite,
    val clump: Sprite,
    val powder: Sprite,
)

/** The sprites of a shared picture laid out as [layout] for tiles [tileWidths] wide, on a screen with [unit] pixels to each unit. */
fun dockPieces(layout: DockAtlas, tileWidths: List<Float>, unit: Float): DockPieces {
    fun sprite(box: Area, anchorX: Float, anchorY: Float) =
        Sprite(box.x * unit, box.y * unit, box.w * unit, box.h * unit, anchorX * unit, anchorY * unit)
    return DockPieces(
        caps = layout.caps.map { sprite(it, CAP_PAD_X, CAP_PAD_TOP) },
        capWidths = tileWidths,
        hollyLeft = sprite(layout.hollyLeft, HOLLY_X, HOLLY_Y),
        hollyRight = sprite(layout.hollyRight, HOLLY_W - HOLLY_X, HOLLY_Y),
        clump = sprite(layout.clump, CLUMP_BOX / 2, CLUMP_BOX / 2),
        powder = sprite(layout.powder, POWDER_BOX / 2, POWDER_BOX / 2),
    )
}

/**
 * The dressing's pictures in [look]'s light, for tiles [tileWidths] wide at their tallest and a bar
 * about [barWidth] wide (both in the dressing's units). Null until they're painted, off the main thread.
 */
@Composable
internal fun rememberDockSprites(look: VillageLook, tileWidths: List<Float>, barWidth: Float): DockSprites? {
    var sprites by remember { mutableStateOf<DockSprites?>(null) }
    val unit = LocalDensity.current.density / 2 // The dressing is measured in pixels of a 1920 px wide screen, 2 to a dp.
    val colours = capColours(look)
    val brightness = dockLightsBrightness(look)
    LaunchedEffect(colours, brightness, tileWidths, barWidth, unit) {
        sprites = withContext(Dispatchers.Default) { paintDockSprites(colours, brightness, tileWidths, barWidth, unit) }
    }
    return sprites
}

private fun paintDockSprites(colours: CapColours, brightness: Float, tileWidths: List<Float>, barWidth: Float, unit: Float): DockSprites {
    fun pixels(units: Float) = ceil(units * unit).toInt()
    val layout = dockAtlas(tileWidths)
    val pieces = layout.caps.mapIndexed { i, box -> InkPiece(snowCapMarks(tileWidths[i], i, colours), box.x, box.y) } +
        InkPiece(hollyMarks(flip = false), layout.hollyLeft.x, layout.hollyLeft.y) +
        InkPiece(hollyMarks(flip = true), layout.hollyRight.x, layout.hollyRight.y) +
        InkPiece(clumpMarks(colours), layout.clump.x + CLUMP_BOX / 2, layout.clump.y + CLUMP_BOX / 2) +
        InkPiece(powderMarks(colours), layout.powder.x + POWDER_BOX / 2, layout.powder.y + POWDER_BOX / 2)
    val glow = listOf(InkPiece(listOf(GlowMark(GLOW_SIZE / 2, GLOW_SIZE / 2, GLOW_SIZE / 2, DOCK_GLOW, 1f))))
    return DockSprites(
        glow = inkPicture(pixels(GLOW_SIZE), pixels(GLOW_SIZE), glow, unit),
        string = inkPicture(pixels(barWidth), pixels(BULB_SIZE), listOf(InkPiece(dockStringMarks(barWidth, brightness))), unit),
        atlas = SpritePainter(inkPicture(pixels(layout.width), pixels(layout.height), pieces, unit)),
        pieces = dockPieces(layout, tileWidths, unit),
        unit = unit,
        stringWidth = barWidth,
        brightness = brightness,
    )
}

/** The warm glow under the selected tile, whose middle is ([x], [y]) and which is [tileWidth] wide: drawn before the bar. */
internal fun DrawScope.drawDockGlow(sprites: DockSprites, x: Float, y: Float, tileWidth: Float) {
    val reach = tileWidth * DOCK_GLOW_REACH
    drawImage(
        sprites.glow,
        dstOffset = IntOffset((x - reach).roundToInt(), (y - reach).roundToInt()),
        dstSize = IntSize((2 * reach).roundToInt(), (2 * reach).roundToInt()),
        alpha = DOCK_GLOW_ALPHA,
        blendMode = BlendMode.Plus,
        filterQuality = FilterQuality.Low,
    )
}

/** The string's picture's top edge on screen, on a whole pixel. */
private fun stringTop(sprites: DockSprites, bar: DockBar): Int =
    (bar.top + bar.height - (WIRE_UP - BULB_DROP + BULB_SIZE / 2) * sprites.unit).roundToInt()

/** The string of fairy lights along the bottom of [bar], under the tiles: drawn after the bar and before the tiles. */
internal fun DrawScope.drawDockLights(sprites: DockSprites, bar: DockBar) {
    drawImage(
        sprites.string,
        dstOffset = IntOffset(bar.left.roundToInt(), stringTop(sprites, bar)),
        dstSize = IntSize(bar.width.roundToInt(), sprites.string.height),
        filterQuality = FilterQuality.Low,
    )
}

/** What the layer of twinkling under the dock's tiles keeps from frame to frame. */
internal class DockTwinkle {
    val bar = DockBar()
    val place = StringPlace()
    val quads = SpriteQuads(BULBS_MOST)
}

/**
 * The string's bulbs twinkling at [t] seconds, over the string's picture along [twinkle]'s bar: lights
 * from the moving sheet ([motion]), drawn in one call.
 */
internal fun DrawScope.drawDockTwinkles(twinkle: DockTwinkle, sprites: DockSprites, motion: MotionPictures, t: Double) {
    val bar = twinkle.bar
    twinkle.place.apply {
        left = bar.left.roundToInt().toFloat()
        bulbsY = stringTop(sprites, bar) + BULB_SIZE / 2 * sprites.unit
        width = bar.width.roundToInt().toFloat()
        madeFor = sprites.stringWidth
        unit = sprites.unit
    }
    twinkle.quads.clear()
    dockTwinklesInto(twinkle.quads, motion.sprites.lights, twinkle.place, sprites.brightness, t)
    motion.painter.draw(drawContext.canvas.nativeCanvas, twinkle.quads, SpriteBlend.ADD)
}

/** What the layer over the dock's tiles keeps from frame to frame: where the tiles are, the rider and the snow, and its lists of pieces. */
internal class DockTop {
    val bar = DockBar()
    val spots = TileSpots()
    val ride = DockRide()
    val away = ReturnWatch()
    val target = RideTarget()
    val snow = SpriteQuads(SNOW_PIECES)
    val rider = SpriteQuads(2)

    /** Whether real snow is falling: set before each frame is drawn. */
    var snowing = false
}

/**
 * Everything on top of the dock's tiles (at [top]'s spots, on its bar) at [t] seconds, back to front: the snow on each tile, as deep
 * as the rider has left it, the holly on the bar's ends, loose snow in the air, and the rider. The
 * rider needs the moving sheet ([motion]); until that's painted the dock has its snow and holly only.
 */
internal fun DrawScope.drawDockTop(top: DockTop, sprites: DockSprites, motion: MotionPictures?, t: Double) {
    val canvas = drawContext.canvas.nativeCanvas
    val unit = sprites.unit
    val riding = top.target.aim(top.spots, unit)
    if (riding) top.ride.step(t, top.target, top.snowing)
    top.snow.clear()
    dockSnowInto(top.snow, sprites.pieces, top.spots, top.ride.caps)
    dockHollyInto(top.snow, sprites.pieces, top.bar, unit)
    top.ride.bits.into(top.snow, sprites.pieces.clump, sprites.pieces.powder, t)
    sprites.atlas.draw(canvas, top.snow)
    if (motion == null || !riding) return
    top.rider.clear()
    riderInto(top.rider, motion.sprites.rider, top.ride, t, unit)
    motion.painter.draw(canvas, top.rider)
}

private const val GLOW_SIZE = 128f
private const val WIRE_UP = 11.5f // The string's wire, this far above the bar's bottom edge.
private const val BULBS_MOST = 128 // A bulb every 44 px of the bar: a dock of about 40 tiles. Any more go without twinkling.
private const val SNOW_PIECES = 160 // More than the tiles, the holly and two landings' loose snow.
