package tv.livingroom.home.christmas

import android.graphics.Canvas
import android.graphics.Path
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import tv.livingroom.home.TAG
import tv.livingroom.home.screensaver.SkyWeather
import tv.livingroom.home.sky.DockSkyLayout
import tv.livingroom.home.sky.drawClouds
import tv.livingroom.home.sky.drawRain
import tv.livingroom.home.sky.drawSkyAndBodies

/** Fireside's sheet painted, with its sprites and the painter that draws from it. */
internal class FiresidePictures(val sprites: FiresideSprites, val painter: SpritePainter, val sheet: ImageBitmap)

/**
 * The pictures Fireside holds while it shows: the still [room] and the [moving] pieces' sheet. Each replaced or
 * released picture is recycled at once rather than left for the garbage collector: the room is 8 MB, and the
 * home screen keeps its own pictures underneath. [broken] is set if drawing a frame failed. Changes redraw.
 */
@Stable
internal class FiresideHeld {
    var room by mutableStateOf<ImageBitmap?>(null)
        private set
    var moving by mutableStateOf<FiresidePictures?>(null)
    var broken by mutableStateOf(false)

    fun showRoom(picture: ImageBitmap) {
        val replaced = room
        room = picture
        replaced?.asAndroidBitmap()?.recycle() // A frame still being drawn keeps its own hold on it.
    }

    fun release() {
        room?.asAndroidBitmap()?.recycle()
        room = null
        moving?.sheet?.asAndroidBitmap()?.recycle()
        moving = null
    }
}

/**
 * Fireside's room, filling the screen: [scene]'s still picture, painted off the main thread whenever it would look
 * different ([paintKey]: about once a minute while the light changes, every few minutes otherwise), and everything
 * that moves, drawn on every frame (every other one late at night) from the screensaver's own clock, in a layer of its
 * own. [names] go on the stockings. [controls] hand it the remote's presses and what a test switch asks for (Santa or a dog
 * moment soon). Each showing plans its own evening (when Santa and the dogs' moments come). If the moving pieces or the
 * first picture can't be made, or drawing a frame fails, [onFailed] is called and the screensaver shows its everyday self;
 * a later repaint that fails keeps the last picture.
 */
@Composable
fun FiresideRoom(
    scene: FiresideScene,
    names: StockingNames,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
    controls: FiresideControls = FiresideControls(),
) {
    val failed by rememberUpdatedState(onFailed)
    val latest by rememberUpdatedState(scene)
    val held = remember { FiresideHeld() }
    DisposableEffect(held) { onDispose { held.release() } }
    val density = LocalDensity.current.density // The screen's pixels per dp: the picture is painted at those.
    val key = remember(scene) { paintKey(scene) }
    val oneAtATime = remember { Mutex() } // A paint started while another runs waits: never three full pictures at once.
    LaunchedEffect(key, density) {
        oneAtATime.withLock { if (!repaint(held, latest, density) && held.room == null) failed() }
    }
    LaunchedEffect(names) {
        var painted: FiresidePictures? = null
        try {
            withContext(Dispatchers.Default) { painted = paintFiresideSheet(names) }
            held.moving = painted
            painted = null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) { // Out of memory included: whatever it was, the everyday screensaver can take over.
            Log.e(TAG, "Fireside: couldn't paint the moving pieces", e)
            failed()
        } finally {
            painted?.sheet?.asAndroidBitmap()?.recycle()
        }
    }
    LaunchedEffect(held.broken) { if (held.broken) failed() }
    val clock = rememberMotionClock(frameStep = if (scene.late) LATE_FRAME_STEP else 1)
    val plan = remember { FiresidePlan(System.nanoTime(), controls.santaAt, controls.moment, controls.giftsLeft) }
    val giftsLeft by rememberUpdatedState(controls.onGiftsLeft)
    LaunchedEffect(plan) {
        // Tell, once, when this showing's visit has left its gifts, so they stay for the next showing.
        while (!plan.leftGifts(clock.seconds)) delay(GIFTS_CHECK_MS)
        giftsLeft()
    }
    val presses = remember { FiresidePresses() }
    val remote = controls.remote
    DisposableEffect(remote) {
        remote?.catcher = { key ->
            presses.press(key, clock.seconds)
            Log.i(TAG, "Fireside: $key at ${"%.1f".format(clock.seconds)} s")
        }
        onDispose { remote?.catcher = null }
    }
    val frame = remember { FiresideFrame() }
    val twinkles = remember(scene) {
        runCatching { roomTwinkles(scene) }.getOrElse {
            Log.e(TAG, "Fireside: couldn't place the twinkling lights", it)
            emptyList()
        }
    }
    val live = remember(scene, twinkles) { FiresideLive(scene, twinkles, plan, presses) }
    val tints = remember(scene.look.room, scene.look.roomK) {
        FiresideTints(tintTowards(scene.look.room, scene.look.roomK), tintTowards(scene.look.room, scene.look.roomK * SANTA_TINT))
    }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                // The thuds of Santa's visit shake the whole room a little: only the layer moves, nothing is drawn again.
                val st = plan.visitAt(clock.seconds)
                val shake = if (st.isNaN()) 0f else roomShake(st)
                // Drawn a hair bigger only while it shakes, so no edge shows; otherwise pixel for pixel, as sharp as painted.
                val grow = if (shake > 0f) SHAKE_ROOM else 1f
                translationX = sin(clock.seconds * 71).toFloat() * shake * size.width / VILLAGE_W
                translationY = cos(clock.seconds * 83).toFloat() * shake * size.height / VILLAGE_H
                scaleX = grow
                scaleY = grow
            }.drawBehind { held.room?.let { drawRoom(it) } },
    ) {
        Spacer(
            Modifier.fillMaxSize().graphicsLayer().drawBehind {
                val t = clock.seconds
                val ready = held.moving ?: return@drawBehind
                if (held.room == null || held.broken) return@drawBehind
                try {
                    firesideFrameAt(frame, ready.sprites, live, t)
                    drawFrame(frame, ready.painter, tints)
                } catch (e: Exception) { // Never take the whole app down with a screensaver.
                    Log.e(TAG, "Fireside: couldn't draw a frame", e)
                    held.broken = true
                }
            },
        )
    }
}

/**
 * What a showing of Fireside is handed: the [remote]'s presses; from a test switch, Santa at [santaAt] s or a dog [moment] soon;
 * whether Santa's gifts are still in the room from an earlier visit ([giftsLeft]), and what to do once this showing's visit has
 * left them ([onGiftsLeft], so the next showing has them too).
 */
data class FiresideControls(
    val remote: FiresideRemote? = null,
    val santaAt: Double? = null,
    val moment: AskedMoment? = null,
    val giftsLeft: Boolean = false,
    val onGiftsLeft: () -> Unit = {},
)

/** How often to look whether Santa's visit has left its gifts, ms. */
private const val GIFTS_CHECK_MS = 5_000L

/** The room's tint, and Santa's: lighter, as he's lit by his own glow. */
private class FiresideTints(val room: android.graphics.ColorFilter, val santa: android.graphics.ColorFilter)

/** How much of the room's tint Santa takes. */
private const val SANTA_TINT = 0.35f

/** The room is drawn a hair bigger than the screen, so its shake never shows an edge (the shake is at most about 10 px). */
private const val SHAKE_ROOM = 1.012f

/** The fireplace's opening, on the room's units: what's seen inside it is cut to it. */
private val ARCH = Path().apply {
    moveTo(858f, 822f)
    lineTo(858f, 610f)
    cubicTo(858f, 500f, 1142f, 500f, 1142f, 610f)
    lineTo(1142f, 822f)
    close()
}

/** The still room over the whole screen. */
private fun DrawScope.drawRoom(room: ImageBitmap) =
    drawImage(room, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)

/** Late at night what moves is drawn on every this many frames: 30 a second, which is plenty for embers. */
private const val LATE_FRAME_STEP = 2

/** Paints [scene] into a new picture and shows it. False if painting failed; a paint stopped part way or cancelled lets its picture go. */
private suspend fun repaint(held: FiresideHeld, scene: FiresideScene, density: Float): Boolean {
    var painted: ImageBitmap? = null
    try {
        withContext(Dispatchers.Default) {
            val job = currentCoroutineContext()
            painted = paintFireside(scene, density) { job.isActive }
        }
        painted?.let { held.showRoom(it) }
        painted = null
        return true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) { // Out of memory included.
        Log.e(TAG, "Fireside: couldn't paint the room", e)
        return false
    } finally {
        painted?.asAndroidBitmap()?.recycle() // Finished just as it was cancelled: not shown, so let it go now.
    }
}

/** [frame]'s pieces in their order (see [FiresideFrame]), on the room's 1920 x 1080 units stretched over the screen. */
private fun DrawScope.drawFrame(frame: FiresideFrame, painter: SpritePainter, tints: FiresideTints) {
    val canvas = drawContext.canvas.nativeCanvas
    canvas.save()
    canvas.scale(size.width / VILLAGE_W, size.height / VILLAGE_H)
    painter.draw(canvas, frame.glows, SpriteBlend.ADD)
    if (frame.tongues.count > 0) {
        canvas.save()
        canvas.clipPath(ARCH)
        painter.draw(canvas, frame.tongues)
        canvas.restore()
    }
    painter.draw(canvas, frame.flames)
    painter.draw(canvas, frame.room, tint = tints.room)
    painter.draw(canvas, frame.embers)
    if (frame.inside.count > 0 || frame.insideLight.count > 0) {
        canvas.save()
        canvas.clipPath(ARCH)
        painter.draw(canvas, frame.insideLight, SpriteBlend.ADD)
        painter.draw(canvas, frame.inside, tint = tints.santa)
        canvas.restore()
    }
    painter.draw(canvas, frame.santa, tint = tints.santa)
    painter.draw(canvas, frame.rug, tint = tints.room)
    painter.draw(canvas, frame.over)
    canvas.save()
    canvas.clipRect(WINDOW_SNOW.x, WINDOW_SNOW.y, WINDOW_SNOW.x + WINDOW_SNOW.w, WINDOW_SNOW.y + WINDOW_SNOW.h)
    canvas.translate(WINDOW_VIEW_X, WINDOW_VIEW_Y)
    canvas.scale(WINDOW_VIEW_SCALE, WINDOW_VIEW_SCALE)
    painter.draw(canvas, frame.snow)
    painter.draw(canvas, frame.windowLight, SpriteBlend.ADD)
    canvas.restore()
    painter.draw(canvas, frame.fx, tint = tints.room)
    painter.draw(canvas, frame.light, SpriteBlend.ADD)
    canvas.restore()
}

/** Fireside's sheet for [names], painted and sent to the GPU, off the main thread. The time it took is logged. */
internal fun paintFiresideSheet(names: StockingNames): FiresidePictures {
    val start = SystemClock.elapsedRealtime()
    val plan = firesideSheet(names)
    val picture = paintSheet(plan.sheet)
    picture.prepareToDraw()
    Log.i(
        TAG,
        "Fireside's sheet painted: ${plan.sheet.pieces.size} pieces, ${picture.width} x ${picture.height}, " +
            "in ${SystemClock.elapsedRealtime() - start} ms",
    )
    return FiresidePictures(plan.sprites, SpritePainter(picture), picture)
}

/**
 * [scene]'s still room over the whole screen, 960 x 540 dp at [pixelsPerDp]: the walls, the view through the window
 * (the shared sky painter in the village's colours, the land, then clouds or rain if the weather has them),
 * cut to the glass, then the room's marks. The time it took is logged. Null if [keepGoing] stopped it part way.
 */
fun paintFireside(scene: FiresideScene, pixelsPerDp: Float = 1f, keepGoing: () -> Boolean = { true }): ImageBitmap? {
    val start = SystemClock.elapsedRealtime()
    val picture = ImageBitmap((DockSkyLayout.WIDTH * pixelsPerDp).roundToInt(), (DockSkyLayout.HEIGHT * pixelsPerDp).roundToInt())
    val bitmap = picture.asAndroidBitmap()
    val canvas = Canvas(bitmap)
    canvas.scale(bitmap.width / VILLAGE_W, bitmap.height / VILLAGE_H)
    val painter = InkPainter()
    try {
        painter.draw(canvas, roomWallMarks(scene))
        canvas.save()
        canvas.clipRect(WINDOW.x, WINDOW.y, WINDOW.x + WINDOW.w, WINDOW.y + WINDOW.h)
        canvas.translate(WINDOW_VIEW_X, WINDOW_VIEW_Y)
        canvas.scale(WINDOW_VIEW_SCALE, WINDOW_VIEW_SCALE)
        drawInDp(canvas) { drawSkyAndBodies(scene.sky, VILLAGE_SKY) }
        val land = painter.draw(canvas, windowLandMarks(scene.look), keepGoing)
        drawInDp(canvas) {
            if (scene.weather != SkyWeather.CLEAR) drawClouds()
            if (scene.weather == SkyWeather.RAIN) drawRain()
        }
        canvas.restore()
        if (!land || !keepGoing()) {
            bitmap.recycle()
            return null
        }
        val marks = firesideMarks(scene)
        if (!painter.draw(canvas, marks, keepGoing)) {
            bitmap.recycle()
            return null
        }
        bitmap.setHasAlpha(false) // The walls cover it all: drawn without blending.
        Log.i(TAG, "Fireside painted: ${marks.size} marks in ${SystemClock.elapsedRealtime() - start} ms")
        return picture
    } catch (e: Throwable) {
        bitmap.recycle() // Not left for the garbage collector while the everyday screensaver loads its own pictures.
        throw e
    } finally {
        painter.recycle()
    }
}

/** [block] on [canvas], which is on the village's 1920 x 1080 units, as the shared sky painters draw: on 960 x 540 dp. */
private fun drawInDp(canvas: Canvas, block: DrawScope.() -> Unit) {
    canvas.save()
    canvas.scale(VILLAGE_W / DockSkyLayout.WIDTH, VILLAGE_H / DockSkyLayout.HEIGHT)
    CanvasDrawScope().draw(
        Density(1f),
        LayoutDirection.Ltr,
        androidx.compose.ui.graphics.Canvas(canvas),
        Size(DockSkyLayout.WIDTH, DockSkyLayout.HEIGHT),
        block,
    )
    canvas.restore()
}
