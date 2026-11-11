package tv.livingroom.home.christmas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.livingroom.home.TAG

/** The moving sheet's picture with its sprites, and the painter that draws from it. */
internal class MotionPictures(val sprites: MotionSprites, val painter: SpritePainter)

/** The moving sheet, painted once off the main thread; null until it's ready. */
@Composable
internal fun rememberMotionPictures(): MotionPictures? {
    var pictures by remember { mutableStateOf<MotionPictures?>(null) }
    LaunchedEffect(Unit) {
        pictures = withContext(Dispatchers.Default) {
            val start = SystemClock.elapsedRealtime()
            val plan = motionSheet()
            val picture = paintSheet(plan.sheet)
            // Sent to the GPU now, off the main thread, rather than on the first frame that draws it.
            picture.prepareToDraw()
            Log.i(
                TAG,
                "Moving sheet painted: ${plan.sheet.pieces.size} pieces, ${picture.width} x ${picture.height}, " +
                    "in ${SystemClock.elapsedRealtime() - start} ms",
            )
            MotionPictures(plan.sprites, SpritePainter(picture))
        }
    }
    return pictures
}

/** [sheet]'s pieces painted into a new see-through picture, each at its own scale. */
internal fun paintSheet(sheet: Sheet): ImageBitmap {
    val picture = ImageBitmap(sheet.width, sheet.height)
    val canvas = Canvas(picture.asAndroidBitmap())
    val painter = InkPainter()
    try {
        for (piece in sheet.pieces) {
            canvas.save()
            canvas.translate(piece.x, piece.y)
            canvas.scale(piece.scale, piece.scale)
            painter.draw(canvas, piece.marks)
            canvas.restore()
        }
    } finally {
        painter.recycle()
    }
    return picture
}

private const val LEAD_COLOUR = 0x2A2A30
private const val LEAD_WIDTH = 1.5f

/**
 * Everything that moves over the village, filling the screen, drawn on every frame from [clock] in its own layer, so a new frame
 * re-records these few draw calls and nothing else on the screen. Back to front: the train behind the village, with the
 * village's front ([backdrop]'s strip) over it again; the lights' twinkling and glows; chimney smoke, the people, children
 * and dogs, all in the hour's tint, back to front by where they stand; the dogs' leads; then sparkles, notes, the sleigh and
 * falling snow. [stress] adds that many extra pieces (a test switch).
 */
@Composable
internal fun VillageMotion(
    scene: VillageScene,
    backdrop: VillagePicture?,
    pictures: MotionPictures?,
    clock: MotionClock,
    stress: Int?,
    modifier: Modifier = Modifier,
) {
    val frame = remember { MotionFrame() }
    val tint = remember(scene.look.amb, scene.look.ambK) { tintTowards(scene.look.amb, scene.look.ambK) }
    val cast = remember { VillageCast() }
    val away = remember { ReturnWatch() }
    val leads = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = LEAD_WIDTH } }
    val strip = remember { StripPlace() }
    leads.color = scene.look.lit(LEAD_COLOUR)
    Spacer(
        modifier.fillMaxSize().graphicsLayer().drawBehind {
            val t = clock.seconds
            val ready = pictures ?: return@drawBehind
            val sprites = ready.sprites
            if (away.cameBack(clock)) cast.restart()
            val train = villageFrameInto(frame, sprites, scene, cast, t)
            if (stress != null) stressInto(frame, sprites, stress, t)
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(size.width / VILLAGE_W, size.height / VILLAGE_H)
            ready.painter.draw(canvas, frame.far, tint = tint)
            ready.painter.draw(canvas, frame.farGlass)
            ready.painter.draw(canvas, frame.farLights, SpriteBlend.ADD)
            if (!train.isNaN()) backdrop?.let { strip.draw(canvas, it.strip.asAndroidBitmap(), train) }
            ready.painter.draw(canvas, frame.lights, SpriteBlend.ADD)
            ready.painter.draw(canvas, frame.lit, tint = tint)
            if (frame.leadValues > 0) canvas.drawLines(frame.leads, 0, frame.leadValues, leads)
            ready.painter.draw(canvas, frame.plain)
            canvas.restore()
        },
    )
}

/** Draws the part of the village's front strip around the train, kept between frames so a frame makes nothing new. */
private class StripPlace {
    private val from = Rect()
    private val to = RectF()

    /** The strip [picture], on the village's units, from just ahead of a train whose front is at [trainX] to past its steam. */
    fun draw(canvas: Canvas, picture: Bitmap, trainX: Float) {
        val left = (trainX - STRIP_AHEAD).coerceIn(0f, VILLAGE_W)
        val right = (trainX + STRIP_BEHIND).coerceIn(0f, VILLAGE_W)
        if (right <= left) return
        val perUnit = picture.width / VILLAGE_W
        from.set((left * perUnit).toInt(), 0, (right * perUnit).toInt(), picture.height)
        to.set(from.left / perUnit, STRIP_TOP, from.right / perUnit, STRIP_BOTTOM)
        canvas.drawBitmap(picture, from, to, null)
    }
}
