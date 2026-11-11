package tv.livingroom.home.christmas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import tv.livingroom.home.TAG
import tv.livingroom.home.screensaver.SkyWeather
import tv.livingroom.home.sky.DockSkyLayout
import tv.livingroom.home.sky.drawClouds
import tv.livingroom.home.sky.drawDockShades
import tv.livingroom.home.sky.drawRain
import tv.livingroom.home.sky.drawSkyAndBodies
import tv.livingroom.home.sky.paint
import tv.livingroom.home.sky.paintOn

/** One minute's village: the whole [picture], and the [strip] of its front that the train passes behind, cut from it. */
class VillagePicture(val picture: ImageBitmap, val strip: ImageBitmap) {
    fun recycle() {
        picture.asAndroidBitmap().recycle()
        strip.asAndroidBitmap().recycle()
    }
}

/**
 * The village's picture for [scene], null until the first is painted. As with the other home skies, [scene] changes at most once
 * a minute and each change is painted once, off the main thread, into one picture that is then drawn as is.
 */
@Composable
fun rememberVillagePicture(scene: VillageScene): VillagePicture? {
    var picture by remember { mutableStateOf<VillagePicture?>(null) }
    val density = LocalDensity.current.density // The screen's pixels per dp: the picture is painted at those.
    LaunchedEffect(scene, density) {
        val painted = withContext(Dispatchers.Default) {
            val job = currentCoroutineContext()
            paintVillage(scene, density) { job.isActive }
        }
        if (painted != null) {
            val replaced = picture
            picture = painted
            // Freed soon rather than whenever the garbage collector gets to it: a full-screen picture is 8 MB, and a new one comes
            // every minute while the light is changing. A moment's wait first, so no frame recorded before the swap still uses it.
            try {
                delay(RECYCLE_AFTER_MS)
            } finally {
                replaced?.recycle()
            }
        }
    }
    return picture
}

/** The Christmas village behind the home screen, filling it: [picture] drawn as it is. */
@Composable
fun VillageBackdrop(picture: VillagePicture?, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().drawBehind {
            picture?.let {
                drawImage(it.picture, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
            }
        },
    )
}

/**
 * [scene] over the whole screen, 960 x 540 dp at [pixelsPerDp]: the home screens' own sky (gradient and
 * glow in the village's colours and shape, stars, the northern lights, moon and sun), then the village's
 * marks, then clouds or rain if the weather has them, and the shades behind the clock and the dock; and
 * the strip the train passes behind ([paintStrip]). The time it took is logged. Null if [keepGoing]
 * stopped it part way (a newer minute came).
 */
fun paintVillage(scene: VillageScene, pixelsPerDp: Float = 1f, keepGoing: () -> Boolean = { true }): VillagePicture? {
    val start = SystemClock.elapsedRealtime()
    val width = DockSkyLayout.WIDTH.toInt()
    val height = DockSkyLayout.HEIGHT.toInt()
    val painter = InkPainter()
    val front = villageHill(scene.look) + villageFront(scene)
    val marks = villageFar(scene.look) + front
    try {
        val picture = paint(width, height, pixelsPerDp) {
            drawSkyAndBodies(scene.sky, VILLAGE_SKY) { painter.drawOn(this, villageAurora(scene)) }
        }
        val bitmap = picture.asAndroidBitmap()
        val canvas = Canvas(bitmap)
        canvas.scale(bitmap.width / VILLAGE_W, bitmap.height / VILLAGE_H)
        if (!keepGoing() || !painter.draw(canvas, marks, keepGoing)) {
            bitmap.recycle()
            return null
        }
        paintOn(picture, width, height, pixelsPerDp) {
            if (scene.sky.weather != SkyWeather.CLEAR) drawClouds()
            if (scene.sky.weather == SkyWeather.RAIN) drawRain()
            drawDockShades()
        }
        if (!keepGoing()) {
            bitmap.recycle()
            return null
        }
        val strip = paintStrip(bitmap, inTheStrip(front), painter)
        Log.i(TAG, "Village painted: ${marks.size} marks in ${SystemClock.elapsedRealtime() - start} ms")
        return VillagePicture(picture, strip)
    } finally {
        painter.recycle()
    }
}

/**
 * The band of [whole] (the finished picture) from [STRIP_TOP] to [STRIP_BOTTOM], kept only where [front] covers it and see-through
 * everywhere else: the village's front exactly as painted, window light, clouds and all, to draw again over the train.
 */
private fun paintStrip(whole: Bitmap, front: List<Mark>, painter: InkPainter): ImageBitmap {
    val scale = whole.height / VILLAGE_H
    val top = (STRIP_TOP * scale).toInt()
    val height = ((STRIP_BOTTOM - STRIP_TOP) * scale).toInt()
    val strip = Bitmap.createBitmap(whole.width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(strip)
    canvas.drawBitmap(whole, Rect(0, top, whole.width, top + height), Rect(0, 0, whole.width, height), null)
    // What the front's marks cover keeps its pixels; the rest is cleared.
    canvas.saveLayer(null, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
    canvas.scale(scale, scale)
    canvas.translate(0f, -top / scale)
    painter.draw(canvas, front)
    canvas.restore()
    // Sent to the GPU now, off the main thread, rather than on the first frame a train is drawn with it.
    strip.prepareToDraw()
    return strip.asImageBitmap()
}

/** [marks] (placed on the village's 1920 x 1080) painted into [scope], which covers the same picture in its own units. */
private fun InkPainter.drawOn(scope: DrawScope, marks: List<Mark>) {
    if (marks.isEmpty()) return
    val canvas = scope.drawContext.canvas.nativeCanvas
    canvas.save()
    canvas.scale(scope.size.width / VILLAGE_W, scope.size.height / VILLAGE_H)
    draw(canvas, marks)
    canvas.restore()
}

private const val RECYCLE_AFTER_MS = 200L // Several frames.
