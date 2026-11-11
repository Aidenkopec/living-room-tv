package tv.livingroom.home.screensaver

import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import java.time.ZonedDateTime
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tv.livingroom.home.TAG
import tv.livingroom.home.christmas.FiresideControls
import tv.livingroom.home.christmas.FiresideRemote
import tv.livingroom.home.christmas.FiresideRoom
import tv.livingroom.home.christmas.firesideScene
import tv.livingroom.home.christmas.loadStockingNames
import tv.livingroom.home.settings.FiresideGifts
import tv.livingroom.home.settings.HomePrefs
import tv.livingroom.home.sky.drawWeatherLayer
import tv.livingroom.home.sky.paintClouds
import tv.livingroom.home.sky.paintRain
import tv.livingroom.home.sky.paintSky
import tv.livingroom.home.sky.paintSnow
import tv.livingroom.home.sky.sunElevation
import tv.livingroom.home.weather.WeatherSource
import tv.livingroom.home.weather.cornerWeather

/**
 * The screensaver, for the real one ([ScreensaverService]) and the preview: with [SaverChoices.fireside],
 * Fireside (the Christmas one), falling back to the everyday screensaver if its pictures can't be made;
 * otherwise Living sky around sunrise and sunset (or whenever there are no photos), Beautiful places the rest
 * of the time, dimmed late at night. [clock] gives the time (a test switch can pretend); [choices] also hold
 * the weather to show instead of the real one and the late-night turn-off. [onDimChange] asks for Android's
 * dimming; [onTurnOffDue] means it's late and the screensaver has shown long enough: time for the TV to sleep.
 * [remote] hands the remote's arrows and OK to Fireside while it shows.
 */
@Composable
fun Screensaver(
    clock: () -> ZonedDateTime,
    choices: SaverChoices,
    onDimChange: (Boolean) -> Unit,
    onTurnOffDue: () -> Unit,
    remote: FiresideRemote? = null,
) {
    val context = LocalContext.current
    val settings = remember { HomePrefs.saverSettings(context) }
    val startedAt = remember { clock() }
    var now by remember { mutableStateOf(startedAt) }

    // The clock and the scene move on at each new minute.
    LaunchedEffect(Unit) {
        while (true) {
            val t = clock()
            delay(60_000L - (t.second * 1000L + t.nano / 1_000_000) + 50)
            now = clock()
        }
    }

    var turnedOff by remember { mutableStateOf(false) }
    val screenOffMs =
        remember { Settings.System.getLong(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, DEFAULT_SCREEN_OFF_MS) }
    LaunchedEffect(now) {
        val held = turnOffHeldByPress(remote?.lastTakenMs, SystemClock.elapsedRealtime(), screenOffMs)
        if (!turnedOff && !held && lateNightOffDue(startedAt, now, settings.lateNightOn, choices.offAfterMin)) {
            turnedOff = true
            Log.i(TAG, "Screensaver: late night, shown for ${choices.offAfterMin} min: turning the TV off")
            onTurnOffDue()
        }
    }

    var firesideFailed by remember { mutableStateOf(false) }
    if (showsFireside(choices.fireside, firesideFailed)) {
        val controls = FiresideControls(
            remote,
            choices.santaAt,
            choices.moment,
            giftsLeft = remember { FiresideGifts.left(context) },
            onGiftsLeft = { FiresideGifts.setLeft(context) },
        )
        FiresideSaver(now, choices.weatherCode, settings, onDimChange, controls) {
            Log.i(TAG, "Screensaver: Fireside couldn't be shown; the everyday screensaver instead")
            firesideFailed = true
        }
    } else {
        EverydaySaver(clock, now, choices.weatherCode, settings, onDimChange)
    }
}

/** Fireside at [now], with [weatherCode] in its window (null: the real weather). It dims late at night, as the everyday one does. */
@Composable
private fun FiresideSaver(
    now: ZonedDateTime,
    weatherCode: Int?,
    settings: SaverSettings,
    onDimChange: (Boolean) -> Unit,
    controls: FiresideControls,
    onFailed: () -> Unit,
) {
    val context = LocalContext.current
    val names = remember { loadStockingNames(context) }
    val weatherOn = remember { HomePrefs.weatherOn(context) }
    val weather by WeatherSource.now.collectAsState()
    val code = weatherCode ?: weather?.takeIf { cornerWeather(it, System.currentTimeMillis(), weatherOn) != null }?.code
    val scene = remember(now, code, settings.lateNightOn) {
        runCatching { firesideScene(now, code, settings.lateNightOn) }.onFailure { Log.e(TAG, "Fireside: no scene", it) }.getOrNull()
    }
    if (scene == null) {
        LaunchedEffect(Unit) { onFailed() }
        return
    }
    LaunchedEffect(scene.late) { onDimChange(scene.late) }
    val shade = remember { paintShade() }
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            FiresideRoom(scene, names, onFailed, controls = controls)
            Spacer(
                Modifier.fillMaxSize().drawBehind {
                    drawImage(shade, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
                    if (scene.late) drawRect(Color.Black.copy(alpha = LATE_DIM))
                },
            )
            SaverWords(
                now,
                scene.late,
                cornerWeather(weather, System.currentTimeMillis(), weatherOn),
                Modifier.align(Alignment.BottomStart),
            )
        }
    }
}

/** The big clock and, unless it's [late] (then just a small faint clock), the date and [weather] under it. */
@Composable
private fun SaverWords(now: ZonedDateTime, late: Boolean, weather: String?, modifier: Modifier) {
    val context = LocalContext.current
    val is24Hour = remember { DateFormat.is24HourFormat(context) }
    val clockText = saverClock(now.toLocalTime(), is24Hour)
    if (late) {
        Text(
            clockText,
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 48.sp,
            fontWeight = FontWeight.W200,
            modifier = modifier.padding(start = 48.dp, bottom = 40.dp),
        )
    } else {
        Column(modifier.padding(start = 48.dp, bottom = 40.dp)) {
            Text(clockText, fontSize = 77.sp, lineHeight = 77.sp, fontWeight = FontWeight.W200, letterSpacing = (-0.045).em)
            Text(
                saverDateLine(now.toLocalDate(), weather),
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Living sky and Beautiful places, at [now] (moving on each minute; [clock] for the time within it). */
@Composable
private fun EverydaySaver(
    clock: () -> ZonedDateTime,
    now: ZonedDateTime,
    weatherCode: Int?,
    settings: SaverSettings,
    onDimChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val photos = remember { PhotoLoader.list(context) }
    val weatherOn = remember { HomePrefs.weatherOn(context) }
    val weather by WeatherSource.now.collectAsState()

    // Photos that failed to load; when none of the right kind are left (or the build has none), the sky.
    val broken = remember { mutableStateSetOf<Int>() }
    val scene = sceneToShow(saverScene(now, settings.skyOn, settings.lateNightOn), photos, broken)
    val late = scene.style == SaverStyle.LATE_NIGHT

    LaunchedEffect(late) { onDimChange(late) }

    val skyShown = scene.style == SaverStyle.LIVING_SKY
    val slides = remember { PhotoSlides() }
    val fadeIn = remember { Animatable(1f) }
    LaunchedEffect(scene.mood, skyShown, photos) {
        if (skyShown) return@LaunchedEffect
        while (true) {
            val today = clock().toLocalDate()
            val usable = photos.filter { it.id !in broken }
            val next = pickPhoto(usable, scene.mood, HomePrefs.saverShown(context, today), Random.Default) ?: break
            HomePrefs.addSaverShown(context, today, next.id)
            val image = PhotoLoader.load(context, next)
            if (image == null) {
                broken += next.id
                continue
            }
            Log.i(TAG, "Screensaver: photo ${next.id} (${next.place}), ${scene.mood}")
            slides.previous = slides.current
            slides.current = Slide(next, image, SystemClock.uptimeMillis())
            fadeIn.snapTo(if (slides.previous == null) 1f else 0f)
            fadeIn.animateTo(1f, tween(SAVER_FADE_MS))
            slides.previous = null
            delay(SAVER_PHOTO_MS - SAVER_FADE_MS)
        }
    }

    // The drift moves 10 times a second: at this speed a step is a fraction of a pixel (drawn unrounded).
    var tick by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            tick = SystemClock.uptimeMillis()
            delay(DRIFT_STEP_MS)
        }
    }

    // Living sky: repainted each minute from the sun's height, faded in and out over the photos.
    val sky = remember { SkyPictures() }
    val skyIn = remember { Animatable(if (skyShown) 1f else 0f) }
    val code = weatherCode ?: weather?.takeIf { cornerWeather(it, System.currentTimeMillis(), weatherOn) != null }?.code
    val kind = skyWeather(code)
    val look = skyLook(sunElevation(now.toInstant()))
    val morning = now.hour < 12
    LaunchedEffect(skyShown, look, morning, kind) {
        if (!skyShown) return@LaunchedEffect
        withContext(Dispatchers.Default) {
            sky.sky = paintSky(look, glowX = if (morning) 0.28f else 0.72f)
            sky.clouds = if (kind != SkyWeather.CLEAR) sky.clouds ?: paintClouds() else null
            sky.falling = when (kind) {
                SkyWeather.RAIN -> paintRain()
                SkyWeather.SNOW -> paintSnow()
                else -> null
            }
        }
    }
    LaunchedEffect(skyShown) {
        skyIn.animateTo(if (skyShown) 1f else 0f, tween(SAVER_FADE_MS))
        // Whichever is hidden lets its pictures go (memory: this runs beside the home screen).
        if (skyShown) {
            slides.current = null
            slides.previous = null
        } else {
            sky.sky = null
            sky.clouds = null
            sky.falling = null
        }
    }
    // Rain and snow move every frame; only while they show.
    var frameMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(skyShown, kind) {
        if (skyShown && (kind == SkyWeather.RAIN || kind == SkyWeather.SNOW)) {
            while (true) withFrameMillis { frameMs = it }
        }
    }
    val fallMs = if (kind == SkyWeather.SNOW) SNOW_LOOP_MS else RAIN_LOOP_MS

    val shade = remember { paintShade() }
    val current = slides.current
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Spacer(
                Modifier.fillMaxSize().drawBehind {
                    slides.previous?.let { drawSlide(it, 1f, tick) }
                    slides.current?.let { drawSlide(it, fadeIn.value, tick) }
                    val skyAlpha = skyIn.value
                    if (skyAlpha > 0f) {
                        val full = IntSize(size.width.toInt(), size.height.toInt())
                        sky.sky?.let { drawImage(it, dstSize = full, alpha = skyAlpha, filterQuality = FilterQuality.Low) }
                        sky.clouds?.let {
                            drawWeatherLayer(it, (tick % CLOUD_LOOP_MS).toFloat() / CLOUD_LOOP_MS, sideways = true, alpha = skyAlpha)
                        }
                        sky.falling?.let { drawWeatherLayer(it, (frameMs % fallMs).toFloat() / fallMs, sideways = false, alpha = skyAlpha) }
                    }
                    drawImage(shade, dstSize = IntSize(size.width.toInt(), size.height.toInt()), filterQuality = FilterQuality.Low)
                    if (late) drawRect(Color.Black.copy(alpha = LATE_DIM))
                },
            )
            SaverWords(now, late, cornerWeather(weather, System.currentTimeMillis(), weatherOn), Modifier.align(Alignment.BottomStart))
            if (!late) {
                if (current != null && !skyShown) {
                    Column(Modifier.align(Alignment.BottomEnd).padding(end = 40.dp, bottom = 44.dp), horizontalAlignment = Alignment.End) {
                        Text(
                            current.photo.place.uppercase(),
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 16.sp,
                            letterSpacing = 0.16.em,
                        )
                        if (current.photo.region.isNotEmpty()) {
                            Text(current.photo.region, color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

/** Living sky's pictures: the sky itself, and the weather's layers when there is weather. Changes redraw. */
private class SkyPictures {
    var sky by mutableStateOf<ImageBitmap?>(null)
    var clouds by mutableStateOf<ImageBitmap?>(null)
    var falling by mutableStateOf<ImageBitmap?>(null)
}

/** A photo on screen, and when it came. */
private class Slide(val photo: SaverPhoto, val image: ImageBitmap, val shownAtMs: Long)

/** The photo on show and the one it's fading in over (at most two pictures held). Changes redraw. */
private class PhotoSlides {
    var current by mutableStateOf<Slide?>(null)
    var previous by mutableStateOf<Slide?>(null)
}

/** One photo, drifting ([slideDrift]), placed in fractions of a pixel so it glides instead of twitching. */
private fun DrawScope.drawSlide(slide: Slide, alpha: Float, tick: Long) {
    val t = slideProgress(tick - slide.shownAtMs)
    val drift = slideDrift(slide.photo.id, t, size.width, size.height)
    withTransform({
        translate(drift.left, drift.top)
        scale(drift.scale, drift.scale, pivot = Offset.Zero)
    }) {
        drawImage(
            slide.image,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            alpha = alpha,
            filterQuality = FilterQuality.Low,
        )
    }
}

/**
 * The shade under the words: black 62% at the bottom, 12% at 38% of the
 * height up, clear by 60% up. Painted once, small, and stretched:
 * full-size gradients on every frame were too slow on this TV.
 */
private fun paintShade(): ImageBitmap {
    val picture = ImageBitmap(SHADE_W, SHADE_H)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(picture), Size(SHADE_W.toFloat(), SHADE_H.toFloat())) {
        drawRect(
            Brush.verticalGradient(
                0.40f to Color.Transparent,
                0.62f to Color.Black.copy(alpha = 0.12f),
                1f to Color.Black.copy(alpha = 0.62f),
            ),
        )
    }
    return picture
}

private const val DRIFT_STEP_MS = 100L
private const val LATE_DIM = 0.7f
private const val CLOUD_LOOP_MS = 240_000L
private const val RAIN_LOOP_MS = 1_200L
private const val SNOW_LOOP_MS = 14_000L
private const val SHADE_W = 16
private const val SHADE_H = 270
