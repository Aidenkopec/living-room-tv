package tv.livingroom.home.sky

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import tv.livingroom.home.TAG
import tv.livingroom.home.screensaver.SkyWeather
import tv.livingroom.home.sky.HomeSkyLayout.HEIGHT
import tv.livingroom.home.sky.HomeSkyLayout.WIDTH

/**
 * The top area's Living sky. [scene] changes at most once a minute (see
 * [homeSkyScene]); each change is painted once, off the main thread, into one
 * picture, which is then drawn as is. Nothing is painted per frame: this TV's
 * GPU is slow at gradients. One picture is held, two while the next is painted
 * (each 1920 x 760 pixels, about 6 MB).
 */
@Composable
fun HomeSky(scene: HomeSkyScene, modifier: Modifier = Modifier) {
    var picture by remember { mutableStateOf<ImageBitmap?>(null) }
    val density = LocalDensity.current.density // The screen's pixels per dp: the picture is painted at those.
    LaunchedEffect(scene, density) { picture = withContext(Dispatchers.Default) { paintHomeSky(scene, density) } }
    Box(
        modifier.fillMaxWidth().height(HEIGHT.dp).drawBehind {
            picture?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low) }
        },
    )
}

/**
 * [scene] painted 960 x 380 dp at [pixelsPerDp] (the home screen passes the screen's own, 2,
 * so the ridges, stars and moon are painted at full resolution, not stretched),
 * in [backdropAt]'s order: sky and glow, stars, the day veil (on the sky only,
 * so the sun stays bright), moon, sun, ridges, still weather, the shades behind
 * the words and the corner, and the fade into the background.
 */
fun paintHomeSky(scene: HomeSkyScene, pixelsPerDp: Float = 1f): ImageBitmap = paint(WIDTH.toInt(), HEIGHT.toInt(), pixelsPerDp) {
    drawSkyAndBodies(scene)
    drawRidges(scene.look)
    drawWeather(scene.weather)
    val shade = Color.Black.copy(alpha = HomeSkyLayout.WORDS_SHADE_ALPHA)
    drawRect(
        Brush.horizontalGradient(
            0f to shade,
            HomeSkyLayout.WORDS_SHADE_FULL_TO to shade,
            HomeSkyLayout.WORDS_SHADE_END to Color.Transparent,
        ),
    )
    drawCornerShade()
    val background = Color(HomeSkyLayout.BACKGROUND)
    val fadeFrom = HomeSkyLayout.FADE_TOP / HEIGHT
    drawRect(
        Brush.verticalGradient(
            fadeFrom to background.copy(alpha = 0f),
            fadeFrom + 0.6f * (1 - fadeFrom) to background.copy(alpha = HomeSkyLayout.FADE_MIDDLE_ALPHA),
            1f to background,
        ),
    )
}

/**
 * Mountains and dock's sky, filling the screen. Like [HomeSky]: [scene]
 * changes at most once a minute and each change is painted once, off the main
 * thread, into one picture that is then drawn as is. With the home photo in the
 * app, Mount Rundle is composed into it; otherwise the drawn ridges.
 */
@Composable
fun DockSky(scene: HomeSkyScene, modifier: Modifier = Modifier) {
    var picture by remember { mutableStateOf<ImageBitmap?>(null) }
    val density = LocalDensity.current.density // The screen's pixels per dp: the picture is painted at those.
    val assets = LocalContext.current.assets
    LaunchedEffect(scene, density) {
        val painted = withContext(Dispatchers.Default) { paintDock(scene, density, assets, hasPicture = picture != null) }
        if (painted != null) picture = painted
    }
    Box(
        modifier.fillMaxSize().drawBehind {
            picture?.let { drawImage(it, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low) }
        },
    )
}

/**
 * The dock's picture for [scene]: with the photo when it's in the app, its files decoded for this
 * paint only and let go straight after (so only the finished picture is kept). Null keeps the
 * picture there is: when the paint was stopped (a newer minute came), or the files couldn't be read
 * this time ([hasPicture]; the very first paint falls back to the ridges instead). Times are logged.
 */
private suspend fun paintDock(scene: HomeSkyScene, density: Float, assets: AssetManager, hasPicture: Boolean): ImageBitmap? {
    val photo = HomePhotoLoader.photo(assets) ?: return paintDockSky(scene, density)
    val start = SystemClock.elapsedRealtime()
    val files = HomePhotoLoader.decode(assets, photo) ?: return if (hasPicture) null else paintDockSky(scene, density)
    val decoded = SystemClock.elapsedRealtime()
    val job = currentCoroutineContext()
    return try {
        paintDockSky(scene, density, LandPhoto(photo, files)) { job.isActive }?.also {
            Log.i(TAG, "Home photo painted: read ${decoded - start} ms, composed ${SystemClock.elapsedRealtime() - decoded} ms")
        }
    } finally {
        files.recycle()
    }
}

/** The home photo's list entry and its decoded files, for one paint. */
class LandPhoto(val photo: HomePhoto, val files: HomePhotoFiles)

/**
 * [scene] over the whole screen, 960 x 540 dp at [pixelsPerDp] (the screen's own, as [paintHomeSky];
 * with a [land] photo, at the photo's own size), in [dockBackdropAt]'s order: sky and glow, stars,
 * the day veil, moon, sun, then the land (the [land] photo composed in, see [composeOnto], or the
 * drawn ridges in the screensaver's places), still weather, then the shades behind the clock and the
 * dock. Null if [keepGoing] stopped the photo part way.
 */
fun paintDockSky(scene: HomeSkyScene, pixelsPerDp: Float = 1f, land: LandPhoto? = null, keepGoing: () -> Boolean = { true }): ImageBitmap? {
    val width = DockSkyLayout.WIDTH.toInt()
    val height = DockSkyLayout.HEIGHT.toInt()
    val scale = land?.let { it.files.land.width / DockSkyLayout.WIDTH } ?: pixelsPerDp
    val picture = paint(width, height, scale) {
        drawSkyAndBodies(scene)
        if (land == null) drawRidges(scene.look)
    }
    if (land != null && !composeOnto(picture, land, scene, keepGoing)) return null
    paintOn(picture, width, height, scale) {
        drawWeather(scene.weather)
        drawClockShade()
        val shade = Color.Black.copy(alpha = DockSkyLayout.BOTTOM_SHADE_ALPHA)
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                1f to shade,
                startY = DockSkyLayout.BOTTOM_SHADE_TOP,
                endY = DockSkyLayout.HEIGHT,
            ),
        )
    }
    return picture
}

/**
 * The [land] photo composed into the sky [picture] (the same size) by [composeLand], lit for
 * [scene]'s sun and weather. False if the sizes differ or [keepGoing] stopped it.
 */
private fun composeOnto(picture: ImageBitmap, land: LandPhoto, scene: HomeSkyScene, keepGoing: () -> Boolean): Boolean {
    val bitmap = picture.asAndroidBitmap()
    val files = land.files
    if (files.land.width != bitmap.width || files.land.height != bitmap.height) return false
    val grade = landGrade(scene.sunElevationDeg, scene.weather, moonUp = scene.moon != null && scene.bodyAlpha > 0f)
    return composeLand(
        BitmapRows(bitmap),
        BitmapRows(files.land),
        BitmapRows(files.layers),
        bitmap.width,
        bitmap.height,
        land.photo.eyeRow,
        grade,
        firstRow = land.photo.landTop - 2,
        keepGoing,
    )
}

private class BitmapRows(private val bitmap: Bitmap) : PixelRows {
    override fun read(y: Int, into: IntArray) = bitmap.getPixels(into, 0, bitmap.width, 0, y, bitmap.width, 1)

    override fun write(y: Int, from: IntArray) = bitmap.setPixels(from, 0, bitmap.width, 0, y, bitmap.width, 1)
}

/** [DockSkyLayout]'s shade behind the clock: a fade across times a fade down ([clockShade]), as one masked layer. */
private fun DrawScope.drawClockShade() {
    val area = Rect(Offset.Zero, Size(DockSkyLayout.CLOCK_SHADE_END_ACROSS, DockSkyLayout.CLOCK_SHADE_END_DOWN))
    val shade = Color.Black.copy(alpha = DockSkyLayout.CLOCK_SHADE_ALPHA)
    drawContext.canvas.saveLayer(area, Paint())
    drawRect(
        Brush.horizontalGradient(
            0f to shade,
            DockSkyLayout.CLOCK_SHADE_FULL_ACROSS / DockSkyLayout.CLOCK_SHADE_END_ACROSS to shade,
            1f to Color.Transparent,
            startX = 0f,
            endX = area.width,
        ),
        size = area.size,
    )
    drawRect(
        Brush.verticalGradient(
            0f to Color.White,
            DockSkyLayout.CLOCK_SHADE_FULL_DOWN / DockSkyLayout.CLOCK_SHADE_END_DOWN to Color.White,
            1f to Color.Transparent,
            startY = 0f,
            endY = area.height,
        ),
        size = area.size,
        blendMode = BlendMode.DstIn,
    )
    drawContext.canvas.restore()
}

/** The sun's soft glow and its disc. */
private fun DrawScope.drawSun(at: SkyPoint, colour: Color, alpha: Float) {
    val centre = Offset(at.x, at.y)
    drawCircle(
        Brush.radialGradient(
            0f to colour.copy(alpha = HomeSkyLayout.SUN_GLOW_ALPHA * alpha),
            1f to colour.copy(alpha = 0f),
            center = centre,
            radius = HomeSkyLayout.SUN_GLOW_RADIUS,
        ),
        radius = HomeSkyLayout.SUN_GLOW_RADIUS,
        center = centre,
    )
    drawCircle(colour.copy(alpha = alpha), radius = HomeSkyLayout.SUN_RADIUS, center = centre)
}

/**
 * The moon: two faint rings, then its lit part. The lit edge is
 * a half circle; the line between light and dark is half an ellipse, bulging
 * toward the dark side while it's more than half lit. Lit on the right while waxing.
 */
private fun DrawScope.drawMoon(at: SkyPoint, phase: MoonPhase, alpha: Float) {
    val centre = Offset(at.x, at.y)
    val r = HomeSkyLayout.MOON_RADIUS
    val moon = Color(HomeSkyLayout.MOON)
    drawCircle(moon.copy(alpha = 0.07f * alpha), radius = r * 40f / 26f, center = centre)
    drawCircle(moon.copy(alpha = 0.10f * alpha), radius = r, center = centre)
    val lit = phase.lit.toFloat().coerceIn(0f, 1f)
    if (lit < 0.01f) return
    val rx = abs(1 - 2 * lit) * r
    val path = Path().apply {
        // Top, round the right, to the bottom.
        arcTo(Rect(centre, r), startAngleDegrees = -90f, sweepAngleDegrees = 180f, forceMoveTo = true)
        if (rx > 0.01f) {
            // Back up through the left (gibbous) or the right (crescent).
            arcTo(Rect(centre.x - rx, centre.y - r, centre.x + rx, centre.y + r), 90f, if (lit > 0.5f) 180f else -180f, forceMoveTo = false)
        }
        close()
    }
    scale(scaleX = if (phase.waxing) 1f else -1f, scaleY = 1f, pivot = centre) { drawPath(path, moon.copy(alpha = alpha)) }
}

/** [HomeSkyLayout]'s corner shade: a horizontal fade times a vertical one ([cornerShade]), as one masked layer. */
private fun DrawScope.drawCornerShade() {
    val left = WIDTH - HomeSkyLayout.CORNER_SHADE_IN
    val area = Rect(Offset(left, 0f), Size(HomeSkyLayout.CORNER_SHADE_IN, HomeSkyLayout.CORNER_SHADE_DOWN))
    val shade = Color.Black.copy(alpha = HomeSkyLayout.CORNER_SHADE_ALPHA)
    drawContext.canvas.saveLayer(area, Paint())
    drawRect(
        Brush.horizontalGradient(0f to Color.Transparent, 0.5f to shade, 1f to shade, startX = left, endX = WIDTH),
        topLeft = area.topLeft,
        size = area.size,
    )
    // Full strength down to 1 − 1/1.6 of the way, then gone at the bottom (as cornerShade's min(1, down × 1.6)).
    drawRect(
        Brush.verticalGradient(
            0f to Color.White,
            (1 - 1 / 1.6f) to Color.White,
            1f to Color.Transparent,
            startY = 0f,
            endY = HomeSkyLayout.CORNER_SHADE_DOWN,
        ),
        topLeft = area.topLeft,
        size = area.size,
        blendMode = BlendMode.DstIn,
    )
    drawContext.canvas.restore()
}

/** The sky both home skies share: gradient and glow, stars, the day veil (on the sky only, so the sun stays bright), moon and sun. */
private fun DrawScope.drawSkyAndBodies(scene: HomeSkyScene) {
    drawSkyGradient(scene.look, scene.glowX)
    drawStars(scene.look)
    if (scene.dim > 0f) drawRect(Color.Black.copy(alpha = scene.dim))
    if (scene.bodyAlpha > 0f) {
        scene.moon?.let { drawMoon(it, scene.phase, scene.bodyAlpha) }
        scene.sun?.let { drawSun(it, Color(sunColour(scene.sunElevationDeg)), scene.bodyAlpha) }
    }
}

/** Still weather over the land: clouds, and rain or snow under them. */
private fun DrawScope.drawWeather(weather: SkyWeather) {
    if (weather == SkyWeather.CLEAR) return
    drawClouds()
    when (weather) {
        SkyWeather.RAIN -> drawRain()
        SkyWeather.SNOW -> drawSnow()
        SkyWeather.CLEAR, SkyWeather.CLOUDY -> Unit
    }
}
