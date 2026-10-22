package tv.livingroom.home.homescreen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import tv.livingroom.home.apps.AppArt
import tv.livingroom.home.apps.TvApp
import tv.livingroom.home.apps.lastUpdateTime
import tv.livingroom.home.apps.loadAppArt
import tv.livingroom.home.apps.needsArtReload
import tv.livingroom.home.settings.HomePrefs
import tv.livingroom.home.sky.HomeSkyLayout
import tv.livingroom.home.tiles.MoveKeyAction
import tv.livingroom.home.tiles.moveKeyAction
import tv.livingroom.home.weather.WeatherSource
import tv.livingroom.home.weather.cornerWeather

/** Behind everything, before the sky's picture is painted. */
val Background = Color(HomeSkyLayout.BACKGROUND)

/** The time now, updated on each new minute while the screen is showing. */
@Composable
fun minuteClock(): LocalDateTime {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clock by produceState(LocalDateTime.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = LocalDateTime.now()
                delay(MINUTE_MS - System.currentTimeMillis() % MINUTE_MS)
            }
        }
    }
    return clock
}

private const val MINUTE_MS = 60_000L

/** What the weather adds to a home screen: the words for the corner (null when off or stale), and the sky's weather code. */
data class HomeWeather(val corner: String?, val skyCode: Int?)

/**
 * The current [HomeWeather], fetched again when due on each new minute of [clock]. The sky only
 * shows weather the corner shows too, unless a test switch gives a code ([overrideCode]).
 */
@Composable
fun rememberHomeWeather(clock: LocalDateTime, resumes: Int, overrideCode: Int?): HomeWeather {
    val context = LocalContext.current
    val weather by WeatherSource.now.collectAsState()
    val weatherOn = remember(resumes) { HomePrefs.weatherOn(context) }
    LaunchedEffect(clock) { WeatherSource.refreshIfDue(context) }
    val corner = cornerWeather(weather, System.currentTimeMillis(), weatherOn)
    return HomeWeather(corner, overrideCode ?: weather?.takeIf { corner != null }?.code)
}

/**
 * Each app's banner and glow colours, loaded off the main thread and kept until the app is
 * updated. Checked on every Home press ([resumes]), since an update may bring a new banner;
 * keyed on which apps there are, so moving a tile or an input turning on doesn't check again.
 */
@Composable
fun rememberAppArt(apps: Map<String, TvApp>, resumes: Int): Map<String, AppArt> {
    val context = LocalContext.current
    val art = remember { mutableStateMapOf<String, AppArt>() }
    LaunchedEffect(apps.keys, resumes) {
        for (app in apps.values) {
            val pkg = app.packageName
            val updated = withContext(Dispatchers.IO) { lastUpdateTime(context.packageManager, pkg) }
            if (!needsArtReload(art[pkg], updated)) continue
            art[pkg] = withContext(Dispatchers.IO) { loadAppArt(context.packageManager, app, updated) }
        }
    }
    return art
}

/**
 * If the tile that had focus leaves the row (hidden, or its device went away), focus goes to the
 * one that took its place (Explore at worst). [tileAtChange] is the selected tile's key as the row
 * changed, read while composing: by the time an effect runs, focus has already moved elsewhere.
 * On a Home press the Home press decides instead.
 */
@Composable
fun RefocusWhenTileLeaves(rowKeys: List<String>, resumes: Int, tileAtChange: String?, requesters: List<FocusRequester>) {
    val seenRow = remember { SeenRow() }
    val currentRequesters by rememberUpdatedState(requesters)
    LaunchedEffect(rowKeys, resumes) {
        val index = seenRow.changed(rowKeys, resumes, tileAtChange) ?: return@LaunchedEffect
        withFrameNanos {}
        runCatching { currentRequesters.getOrNull(index)?.requestFocus() }
    }
}

/** While moving a tile: Left and Right move it, OK or Back ends it, and nothing else leaves it (see [moveKeyAction]). */
fun moveKeys(event: KeyEvent, onMove: (Int) -> Unit, onDone: () -> Unit): Boolean {
    when (moveKeyAction(event.nativeKeyEvent.keyCode, down = event.type == KeyEventType.KeyDown)) {
        MoveKeyAction.LEFT -> onMove(-1)
        MoveKeyAction.RIGHT -> onMove(1)
        MoveKeyAction.FINISH -> onDone()
        MoveKeyAction.SWALLOW -> {}
        MoveKeyAction.PASS -> return false
    }
    return true
}
