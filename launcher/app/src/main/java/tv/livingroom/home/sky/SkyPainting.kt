package tv.livingroom.home.sky

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.roundToInt
import kotlin.random.Random
import tv.livingroom.home.screensaver.SkyLook

/*
 * Living sky: a sky in the sun's real colours over two Rockies ridges, with
 * stars as it gets dark, and clouds, rain or snow when Calgary has them. Everything is painted once into pictures and only moved
 * on screen (this TV's GPU is slow at painting gradients every frame): the
 * sky once a minute, each weather layer once.
 */

/**
 * The sky, stars and ridges for [look], at half the screen's pixels. The glow
 * sits at [glowX] across (the screensaver: 0.28 for sunrise, 0.72 for sunset).
 */
fun paintSky(look: SkyLook, glowX: Float): ImageBitmap = paint {
    drawSkyGradient(look, glowX)
    drawStars(look)
    drawRidges(look)
}

/** Three soft clouds across the top of the sky; drawn twice side by side as they drift. */
fun paintClouds(): ImageBitmap = paint { drawClouds() }

/** Rain streaks, slanted; drawn twice, one above the other, as they fall. */
fun paintRain(): ImageBitmap = paint { drawRain() }

/** Snowflakes; drawn twice, one above the other, as they fall slowly. */
fun paintSnow(): ImageBitmap = paint { drawSnow() }

// Each part on its own, sized from the scope: the screensaver's pictures and the home sky (HomeSkyView) share them.

/** The sky's gradient (top, middle, horizon) and the glow on the horizon at [glowX] across. */
fun DrawScope.drawSkyGradient(look: SkyLook, glowX: Float) {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.verticalGradient(
            0f to Color(look.top),
            HomeSkyLayout.MIDDLE_AT to Color(look.middle),
            HomeSkyLayout.HORIZON_AT to Color(look.horizon),
        ),
    )
    if (look.glowAlpha > 0f) {
        val centre = Offset(w * glowX, h * HomeSkyLayout.GLOW_Y)
        withTransform({ scale(1f, HomeSkyLayout.GLOW_SQUASH, centre) }) {
            drawCircle(
                Brush.radialGradient(
                    0f to Color(look.glow).copy(alpha = look.glowAlpha),
                    1f to Color.Transparent,
                    center = centre,
                    radius = w * HomeSkyLayout.GLOW_RADIUS,
                ),
                radius = w * HomeSkyLayout.GLOW_RADIUS,
                center = centre,
            )
        }
    }
}

/** Stars in the top 55%, as bright as [look] says (none in daylight). */
fun DrawScope.drawStars(look: SkyLook) {
    if (look.stars <= 0f) return
    val random = Random(STARS_SEED)
    repeat(STAR_COUNT) {
        val x = random.nextFloat() * size.width
        val y = random.nextFloat() * size.height * 0.55f
        val brightness = 0.5f + random.nextFloat() * 0.5f
        drawCircle(Color.White.copy(alpha = look.stars * brightness), radius = 0.6f + random.nextFloat() * 0.6f, center = Offset(x, y))
    }
}

/** Two Rockies ridges, tinted 30% towards the horizon's colour, with snow on the high peaks. */
fun DrawScope.drawRidges(look: SkyLook) {
    val w = size.width
    val h = size.height
    val far = ridge(w, h, top = 0.52f, height = 0.34f, FAR_RIDGE)
    drawPath(
        far,
        Brush.verticalGradient(
            0f to tint(0xFF2B2440, look.horizon),
            1f to tint(0xFF1A1630, look.horizon),
            startY = h * 0.52f,
            endY = h * 0.86f,
        ),
    )
    for (cap in snowCaps(FAR_RIDGE)) drawPath(ridge(w, h, top = 0.52f, height = 0.34f, cap), Color(0xFFE6DCFF).copy(alpha = 0.16f))
    drawPath(ridge(w, h, top = 0.74f, height = 0.26f, NEAR_RIDGE), tint(0xFF0C0B18, look.horizon))
}

/** The three clouds ([CLOUDS]), kept clear of the edges so neither the edge nor the drift's wrap-around shows. */
fun DrawScope.drawClouds() {
    val w = size.width
    val h = size.height
    for (cloud in CLOUDS) {
        val centre = Offset(w * cloud.x, h * cloud.y)
        withTransform({ scale(1f, CLOUD_SQUASH, centre) }) {
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White.copy(alpha = HomeSkyLayout.CLOUD_ALPHA),
                    1f to Color.Transparent,
                    center = centre,
                    radius =
                        w * cloud.radius,
                ),
                radius = w * cloud.radius,
                center = centre,
            )
        }
    }
}

/** Rain streaks, slanted about 15° from vertical. */
fun DrawScope.drawRain() {
    val w = size.width
    val h = size.height
    val random = Random(RAIN_SEED)
    repeat(RAIN_COUNT) {
        val x = random.nextFloat() * w
        val y = random.nextFloat() * h
        val length = h * (0.03f + random.nextFloat() * 0.03f)
        drawLine(
            Color.White.copy(alpha = 0.18f + random.nextFloat() * 0.2f),
            start = Offset(x, y),
            end = Offset(x - length * 0.27f, y + length),
            strokeWidth = 1f,
            cap = StrokeCap.Round,
        )
    }
}

fun DrawScope.drawSnow() {
    val random = Random(SNOW_SEED)
    repeat(SNOW_COUNT) {
        drawCircle(
            Color.White.copy(alpha = 0.35f + random.nextFloat() * 0.4f),
            radius = 0.8f + random.nextFloat() * 1.4f,
            center = Offset(random.nextFloat() * size.width, random.nextFloat() * size.height),
        )
    }
}

/**
 * The weather layer [picture] at [phase] (0 to 1 through its loop): clouds
 * move right, rain and snow move down. Two copies so the loop never shows a gap.
 */
fun DrawScope.drawWeatherLayer(picture: ImageBitmap, phase: Float, sideways: Boolean, alpha: Float) {
    val w = size.width.toInt()
    val h = size.height.toInt()
    val dstSize = IntSize(w, h)
    val shift = phase - 1f // -1 to 0.
    for (copy in 0..1) {
        val offset = if (sideways) IntOffset(((shift + copy) * w).toInt(), 0) else IntOffset(0, ((shift + copy) * h).toInt())
        drawImage(picture, dstOffset = offset, dstSize = dstSize, alpha = alpha, filterQuality = FilterQuality.Low)
    }
}

/**
 * A picture [width] x [height] units across, at [scale] pixels per unit (the screensaver's: half the
 * screen's pixels, 960 x 540 at 1), stretched to fill its space when drawn. [block] always draws in units,
 * so a bigger [scale] gives the same picture, only sharper (the home screens paint at the screen's own pixels).
 */
fun paint(width: Int = SKY_W, height: Int = SKY_H, scale: Float = 1f, block: DrawScope.() -> Unit): ImageBitmap {
    val picture = ImageBitmap((width * scale).roundToInt(), (height * scale).roundToInt())
    paintOn(picture, width, height, scale, block)
    return picture
}

/** More drawing on a [picture] made by [paint] with the same [width], [height] and [scale] (e.g. after its pixels were changed). */
fun paintOn(picture: ImageBitmap, width: Int, height: Int, scale: Float, block: DrawScope.() -> Unit) {
    val canvas = Canvas(picture)
    if (scale != 1f) canvas.scale(scale, scale)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, canvas, Size(width.toFloat(), height.toFloat()), block)
}

/** A ridge from its outline [points] (x%, y% of a box 104% wide, [height] high, from [top]). */
private fun ridge(w: Float, h: Float, top: Float, height: Float, points: List<Pair<Float, Float>>): Path = Path().apply {
    points.forEachIndexed { i, (px, py) ->
        val x = -0.02f * w + px / 100f * 1.04f * w
        val y = (top + py / 100f * height) * h
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A small snow cap on each high peak of [outline]: the top 30% of the slopes either side of it. */
private fun snowCaps(outline: List<Pair<Float, Float>>): List<List<Pair<Float, Float>>> =
    outline.indices.drop(1).dropLast(1).mapNotNull { i ->
        val (x, y) = outline[i]
        val before = outline[i - 1]
        val after = outline[i + 1]
        if (y >= SNOW_LINE || y >= before.second || y >= after.second) return@mapNotNull null
        fun toward(p: Pair<Float, Float>) = (x + (p.first - x) * 0.3f) to (y + (p.second - y) * 0.3f)
        listOf(x to y, toward(after), toward(before))
    }

private fun tint(colour: Long, toward: Int): Color {
    val from = Color(colour)
    val to = Color(toward)
    return Color(
        red = from.red + (to.red - from.red) * TINT,
        green = from.green + (to.green - from.green) * TINT,
        blue = from.blue + (to.blue - from.blue) * TINT,
    )
}

// The far and near ridge outlines. Snow caps are worked out from the peaks ([snowCaps]):
// a separate snow outline drew a band across the ridge on the TV.
private val FAR_RIDGE = listOf(
    0f to 70f, 6f to 52f, 11f to 60f, 17f to 30f, 21f to 42f, 26f to 18f, 31f to 38f, 36f to 26f, 41f to 48f, 47f to 22f,
    52f to 8f, 57f to 30f, 62f to 20f, 67f to 44f, 72f to 28f, 78f to 46f, 83f to 24f, 88f to 40f, 93f to 30f, 100f to 50f,
    100f to 100f, 0f to 100f,
)
private val NEAR_RIDGE = listOf(
    0f to 40f, 8f to 30f, 16f to 44f, 25f to 26f, 34f to 40f, 44f to 30f, 55f to 46f, 64f to 28f, 74f to 42f, 84f to 30f,
    92f to 40f, 100f to 32f, 100f to 100f, 0f to 100f,
)

private const val SKY_W = 960
private const val SKY_H = 540
private const val TINT = 0.3f
private const val SNOW_LINE = 32f // Peaks higher than this (y% from the box's top) get snow.
private const val STAR_COUNT = 60
private const val STARS_SEED = 7
private const val RAIN_COUNT = 160
private const val RAIN_SEED = 11
private const val SNOW_COUNT = 140
private const val SNOW_SEED = 13
