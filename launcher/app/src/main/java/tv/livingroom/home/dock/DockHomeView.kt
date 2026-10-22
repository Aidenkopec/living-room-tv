package tv.livingroom.home.dock

import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import java.time.ZoneId
import tv.livingroom.home.SettingsActivity
import tv.livingroom.home.TAG
import tv.livingroom.home.apps.AppArt
import tv.livingroom.home.apps.RowItem
import tv.livingroom.home.apps.key
import tv.livingroom.home.apps.label
import tv.livingroom.home.apps.openApp
import tv.livingroom.home.apps.openGoogleHome
import tv.livingroom.home.apps.openInput
import tv.livingroom.home.apps.start
import tv.livingroom.home.homescreen.Background
import tv.livingroom.home.homescreen.RefocusWhenTileLeaves
import tv.livingroom.home.homescreen.minuteClock
import tv.livingroom.home.homescreen.mostRecentApp
import tv.livingroom.home.homescreen.moveKeys
import tv.livingroom.home.homescreen.rememberAppArt
import tv.livingroom.home.homescreen.rememberHomeWeather
import tv.livingroom.home.homescreen.shownTime
import tv.livingroom.home.homescreen.startIndex
import tv.livingroom.home.livingsky.greeting
import tv.livingroom.home.livingsky.movingTopAreaText
import tv.livingroom.home.screensaver.saverClock
import tv.livingroom.home.sky.DOCK_LINE_ALPHA
import tv.livingroom.home.sky.DockSky
import tv.livingroom.home.sky.DockSkyLayout
import tv.livingroom.home.sky.cornerSkyLine
import tv.livingroom.home.sky.homeSkyScene
import tv.livingroom.home.testswitch.HomeOverrides
import tv.livingroom.home.tiles.AppTile
import tv.livingroom.home.tiles.ExploreTile
import tv.livingroom.home.tiles.InputTile
import tv.livingroom.home.tiles.MenuAction
import tv.livingroom.home.tiles.TileMenu
import tv.livingroom.home.tiles.tileMenu

// Positions in dp on the TV's 960 x 540 dp screen (1920 x 1080 px).
private val ClockStart = 48.dp
private val ClockTop = 30.dp
private val LineMaxWidth = 512.dp // To 560 dp across, where the shade behind it starts to fade (DockSkyLayout).
private val DockBottom = 26.dp // From the bottom of the screen to the bottom of the bar.
private val BarHeight = 50.dp
private val BarInset = 8.dp // From the bar's bottom to the tiles' bottoms.
private val BarCorner = 13.dp
private val RingRoom = 4.dp // Above the tallest tile, for its ring.
private val SeparatorHeight = 24.dp
private const val GROW_MS = 200
private val SquareBase = Color(0xFF15141F)
private val SquareShape = RoundedCornerShape(6.dp) // The tiles' own corners.

/**
 * The Mountains and dock home screen: the whole screen is Calgary's sky over
 * the Rockies, with a quiet clock at the top left and the apps in a floating
 * dock at the bottom. No Continue watching (shows are still saved, for Living
 * sky). The same row, focus rules, long-press menu and test switches as
 * [tv.livingroom.home.livingsky.HomeScreen]: each time [resumes] goes up (every Home press),
 * focus goes back to the app used last.
 */
@Composable
fun DockHomeScreen(
    row: List<RowItem>?,
    lastOpened: Map<String, Long>,
    overrides: HomeOverrides?,
    resumes: Int,
    onMove: (key: String, by: Int) -> Unit,
    onHide: (key: String) -> Unit,
) {
    val context = LocalContext.current
    val apps = remember(row) { row.orEmpty().filterIsInstance<RowItem.App>().associate { it.app.packageName to it.app } }
    val art = rememberAppArt(apps, resumes)
    val focusRequesters = remember(row?.size) { List(row?.size ?: 0) { FocusRequester() } }
    val mostRecent = remember(row, lastOpened) { mostRecentApp(lastOpened, apps.keys) }
    val clock = minuteClock()
    var selected by remember { mutableStateOf<String?>(null) } // The selected tile's key.
    var menu by remember { mutableStateOf<RowItem?>(null) } // The tile whose long-press menu is open.
    var moving by remember { mutableStateOf<String?>(null) } // The key of the tile being moved.
    val items = row.orEmpty()
    val rowKeys = remember(row) { items.map { it.key } }
    // What was selected as the row changed, read while composing (see HomeScreen).
    val tileAtChange = remember(rowKeys) { selected }
    val now = shownTime(clock, overrides)
    val zone = ZoneId.systemDefault()
    val is24Hour = DateFormat.is24HourFormat(context)
    val weather = rememberHomeWeather(clock, resumes, overrides?.weatherCode)
    // Before focus lands (or if the selected tile left), sizes follow where focus is about to go.
    val focusedIndex = rowKeys.indexOf(selected).takeIf { it >= 0 } ?: startIndex(items, mostRecent)
    val separator = remember(row) { dockSeparatorBefore(items) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        // No Surface at the root, so text would default to black.
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(Modifier.fillMaxSize().background(Background)) {
                // Calgary's sky right now, with the weather in it only when the clock's line shows it (on and fresh).
                val skyScene = remember(now, weather.skyCode) { homeSkyScene(now.atZone(zone), weather.skyCode, arc = DockSkyLayout.ARC) }
                DockSky(skyScene) // First, so everything else is drawn on top of it.
                val skyLine = remember(now, is24Hour) { cornerSkyLine(now.atZone(zone), is24Hour) } // Once a minute, not on every move.
                DockClock(saverClock(now.toLocalTime(), is24Hour), dockInfoLine(now.toLocalDate(), weather.corner, skyLine))
                if (row != null) {
                    val squares = remember(row) { items.map { it !is RowItem.App } }
                    // Where focus is, gliding from tile to tile: the one thing that changes on each frame of a move.
                    val at = animateFloatAsState(focusedIndex.toFloat(), tween(GROW_MS, easing = FastOutSlowInEasing), label = "dock focus")
                    // Every tile's place, worked out again on each frame but only read while drawing, so
                    // a move never recomposes or re-lays out the tiles (too slow on this TV).
                    val slots = remember(squares, separator) { derivedStateOf { dockSlots(squares, at.value, separator) } }
                    val wide = remember(squares, separator) { dockWidth(dockSlots(squares, 0f, separator)) > DOCK_MAX_WIDTH }
                    Column(
                        Modifier.align(Alignment.BottomCenter).padding(bottom = DockBottom),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        moving?.let { key -> items.firstOrNull { it.key == key } }?.let { item ->
                            val hint = movingTopAreaText(item.label)
                            Text(
                                "${hint.greeting} ${hint.title} · ${hint.meta}",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(bottom = 10.dp),
                            )
                        }
                        Box(
                            Modifier
                                .size(DOCK_MAX_WIDTH.dp, RingRoom + DOCK_TALLEST.dp + BarInset)
                                .then(if (wide) Modifier.clipToBounds() else Modifier)
                                .drawBehind { drawDock(slots.value, separator, focusedIndex) },
                        ) {
                            items.forEachIndexed { index, item ->
                                key(item.key) {
                                    val tileModifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(bottom = BarInset)
                                        .graphicsLayer {
                                            // Drawn at the tallest size, scaled down from its bottom left corner and slid into place.
                                            val all = slots.value
                                            val slot = all[index]
                                            val scale = slot.height / DOCK_TALLEST
                                            transformOrigin = TransformOrigin(0f, 1f)
                                            scaleX = scale
                                            scaleY = scale
                                            translationX = (dockLeft(all, focusedIndex) + slot.x).dp.toPx()
                                        }
                                        .focusRequester(focusRequesters[index])
                                        .onFocusChanged { if (it.isFocused) selected = item.key }
                                        .onPreviewKeyEvent { event ->
                                            if (moving == item.key) {
                                                moveKeys(event, onMove = { by -> onMove(item.key, by) }, onDone = { moving = null })
                                            } else {
                                                dockKeys(event, index, items.size) { runCatching { focusRequesters[it].requestFocus() } }
                                            }
                                        }
                                    DockTile(
                                        item,
                                        squares[index],
                                        showLabel = index == focusedIndex,
                                        art[(item as? RowItem.App)?.app?.packageName],
                                        tileModifier,
                                    ) { menu = item }
                                }
                            }
                        }
                    }
                }
                menu?.let { tile ->
                    TileMenu(
                        title = tile.label,
                        actions = tileMenu(tile),
                        onDismiss = { menu = null },
                        onPick = { action ->
                            menu = null
                            when (action) {
                                MenuAction.MOVE -> moving = tile.key
                                MenuAction.HIDE -> onHide(tile.key)
                                // Only Continue watching's cards offer it.
                                MenuAction.REMOVE_SHOW -> {}
                                MenuAction.SETTINGS -> context.start(Intent(context, SettingsActivity::class.java))
                            }
                        },
                    )
                }
            }
        }
    }

    val currentRow by rememberUpdatedState(row)
    val currentMostRecent by rememberUpdatedState(mostRecent)
    val currentFocusRequesters by rememberUpdatedState(focusRequesters)

    // Moving ends if its tile leaves the row (its device went away) or focus lands somewhere else.
    LaunchedEffect(rowKeys, selected) {
        val key = moving ?: return@LaunchedEffect
        if (key !in rowKeys || selected != key) moving = null
    }

    RefocusWhenTileLeaves(rowKeys, resumes, tileAtChange, focusRequesters)

    // Only on Home presses: focus goes back to the app used last.
    LaunchedEffect(resumes) {
        moving = null
        menu = null
        val startRow = currentRow
        if (startRow.isNullOrEmpty()) return@LaunchedEffect
        val index = startIndex(startRow, currentMostRecent)
        val key = startRow[index].key
        selected = key
        withFrameNanos {} // Let the tile be laid out first.
        // The row may have changed during that frame: find the tile again by its key.
        val requester =
            currentFocusRequesters.getOrNull(currentRow.orEmpty().indexOfFirst { it.key == key }) ?: currentFocusRequesters.firstOrNull()
        runCatching { requester?.requestFocus() }.onFailure { Log.w(TAG, "Could not focus tile $index", it) }
    }
}

/** The quiet clock at the top left, as on the screensaver (no AM/PM), with one [line] under it. */
@Composable
private fun DockClock(time: String, line: String) {
    Column(Modifier.padding(start = ClockStart, top = ClockTop)) {
        Text(
            time,
            fontSize = 68.sp,
            lineHeight = 68.sp,
            fontWeight = FontWeight.W300,
            letterSpacing = (-0.035).em,
            style = TextStyle(fontFeatureSettings = "tnum"), // Even-width digits, so the clock doesn't shift as it ticks.
        )
        Text(
            line,
            color = Color.White.copy(alpha = DOCK_LINE_ALPHA),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp).widthIn(max = LineMaxWidth),
        )
    }
}

/** Where the tiles start, in dp from the dock's box: inside the bar's left end, the bar centred (or slid, see [dockStart]). */
private fun dockLeft(slots: List<DockSlot>, focused: Int): Float {
    val slot = slots.getOrNull(focused) ?: return DOCK_PADDING
    return dockStart(dockWidth(slots), DOCK_PADDING + slot.x + slot.width / 2, DOCK_MAX_WIDTH) + DOCK_PADDING
}

/**
 * Left and Right go to the tile next door (the tiles all sit in one place until drawn, so
 * Android's own search can't find them); Up and Down go nowhere, as nothing else on the screen takes focus.
 */
private fun dockKeys(event: KeyEvent, index: Int, count: Int, focus: (Int) -> Unit): Boolean {
    val by = when (event.nativeKeyEvent.keyCode) {
        android.view.KeyEvent.KEYCODE_DPAD_LEFT -> -1
        android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> 1
        android.view.KeyEvent.KEYCODE_DPAD_UP, android.view.KeyEvent.KEYCODE_DPAD_DOWN -> 0
        else -> return false
    }
    val next = (index + by).coerceIn(0, count - 1)
    if (event.type == KeyEventType.KeyDown && next != index) focus(next)
    return true
}

/** One tile in the dock at the tallest size (its [modifier] scales it down); a [square] one shows its words when [showLabel]. */
@Composable
private fun DockTile(item: RowItem, square: Boolean, showLabel: Boolean, art: AppArt?, modifier: Modifier, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val width = dockTileWidth(DOCK_TALLEST, square).dp
    val height = DOCK_TALLEST.dp
    // The tile grows instead of lifting, so no extra scale when selected; the white ring stays.
    when (item) {
        is RowItem.App -> AppTile(
            item.app.label,
            art?.tile,
            loaded = art != null,
            onClick = { context.openApp(item.app) },
            onLongClick = onLongClick,
            modifier = modifier,
            width = width,
            height = height,
            focusedScale = 1f,
        )
        is RowItem.Input -> InputTile(
            item.input,
            onClick = { context.openInput(item.input) },
            onLongClick = onLongClick,
            modifier = modifier.background(SquareBase, SquareShape), // Solid, so the bar's edge doesn't show through.
            size = width,
            height = height,
            focusedScale = 1f,
            showLabel = showLabel,
        )
        RowItem.Explore -> ExploreTile(
            onClick = { context.openGoogleHome() },
            onLongClick = onLongClick,
            modifier = modifier.background(SquareBase, SquareShape), // Solid, so the bar's edge doesn't show through.
            size = width,
            height = height,
            focusedScale = 1f,
            showLabel = showLabel,
        )
    }
}

/** The see-through bar behind the tiles (as wide as they are, [slots]) and the thin line before [separator], if any. */
private fun DrawScope.drawDock(slots: List<DockSlot>, separator: Int?, focused: Int) {
    val left = (dockLeft(slots, focused) - DOCK_PADDING).dp.toPx()
    val width = dockWidth(slots).dp.toPx()
    val bar = BarHeight.toPx()
    val top = size.height - bar
    val corner = CornerRadius(BarCorner.toPx())
    drawRoundRect(Color(DockSkyLayout.BAR).copy(alpha = DockSkyLayout.BAR_ALPHA), Offset(left, top), Size(width, bar), corner)
    val edge = 1.dp.toPx()
    drawRoundRect(
        Color.White.copy(alpha = 0.14f),
        Offset(left + edge / 2, top + edge / 2),
        Size(width - edge, bar - edge),
        corner,
        style = Stroke(edge),
    )
    if (separator != null) {
        val x = (dockLeft(slots, focused) + dockSeparatorX(slots, separator)).dp.toPx()
        val line = SeparatorHeight.toPx()
        drawRect(Color.White.copy(alpha = 0.2f), Offset(x, top + (bar - line) / 2), Size(edge, line))
    }
}
