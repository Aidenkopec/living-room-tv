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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
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
import kotlin.math.round
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
import tv.livingroom.home.christmas.DockBar
import tv.livingroom.home.christmas.DockSprites
import tv.livingroom.home.christmas.DockTop
import tv.livingroom.home.christmas.DockTwinkle
import tv.livingroom.home.christmas.MotionClock
import tv.livingroom.home.christmas.MotionPictures
import tv.livingroom.home.christmas.TileSpots
import tv.livingroom.home.christmas.VillageBackdrop
import tv.livingroom.home.christmas.VillageMotion
import tv.livingroom.home.christmas.christmasLine
import tv.livingroom.home.christmas.drawDockGlow
import tv.livingroom.home.christmas.drawDockLights
import tv.livingroom.home.christmas.drawDockTop
import tv.livingroom.home.christmas.drawDockTwinkles
import tv.livingroom.home.christmas.rememberDockSprites
import tv.livingroom.home.christmas.rememberMotionClock
import tv.livingroom.home.christmas.rememberMotionPictures
import tv.livingroom.home.christmas.rememberVillagePicture
import tv.livingroom.home.christmas.villageScene
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
import tv.livingroom.home.screensaver.SkyWeather
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
 * focus goes back to the app used last. With [christmas], the Christmas village takes the sky's
 * place, the dock is dressed (snow, holly, fairy lights) and the clock's line counts down to Christmas.
 */
@Composable
fun DockHomeScreen(
    row: List<RowItem>?,
    lastOpened: Map<String, Long>,
    overrides: HomeOverrides?,
    resumes: Int,
    christmas: Boolean = false,
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
                // First, so everything else is drawn on top of it: the Christmas village, or Calgary's sky right now.
                // Either has the weather in it only when the clock's line shows it (on and fresh).
                val village =
                    remember(now, weather.skyCode, christmas) { if (christmas) villageScene(now.atZone(zone), weather.skyCode) else null }
                // What moves over the village and on the dock: one clock and one sheet of ready-made pieces for both.
                val motion = if (village != null) DockMotion(rememberMotionClock(), rememberMotionPictures()) else null
                if (village != null && motion != null) {
                    val backdrop = rememberVillagePicture(village)
                    VillageBackdrop(backdrop)
                    // A test switch's opening is a new scene: whoever was walking through the old one isn't carried over.
                    key(overrides) { VillageMotion(village, backdrop, motion.pictures, motion.clock, overrides?.sprites) }
                } else {
                    val skyScene =
                        remember(now, weather.skyCode) { homeSkyScene(now.atZone(zone), weather.skyCode, arc = DockSkyLayout.ARC) }
                    DockSky(skyScene)
                }
                // Once a minute, not on every move. In December the Christmas countdown takes the sky note's place.
                val skyLine = remember(now, is24Hour, christmas) {
                    (if (christmas) christmasLine(now.toLocalDate()) else null) ?: cornerSkyLine(now.atZone(zone), is24Hour)
                }
                DockClock(saverClock(now.toLocalTime(), is24Hour), dockInfoLine(now.toLocalDate(), weather.corner, skyLine))
                if (row != null) {
                    val squares = remember(row) { items.map { it !is RowItem.App } }
                    // Where focus is, gliding from tile to tile: the one thing that changes on each frame of a move.
                    val at = animateFloatAsState(focusedIndex.toFloat(), tween(GROW_MS, easing = FastOutSlowInEasing), label = "dock focus")
                    // Every tile's place, worked out again on each frame but only read while drawing, so
                    // a move never recomposes or re-lays out the tiles (too slow on this TV).
                    val slots = remember(squares, separator) { derivedStateOf { dockSlots(squares, at.value, separator) } }
                    val wide = remember(squares, separator) { dockWidth(dockSlots(squares, 0f, separator)) > DOCK_MAX_WIDTH }
                    // The Christmas dressing's pictures, made for each tile's tallest size (2 px to a dp).
                    val tallest = remember(squares) { squares.map { dockTileWidth(DOCK_TALLEST, it) * 2 } }
                    val typicalBar = remember(squares, separator) { dockWidth(dockSlots(squares, (squares.size - 1) / 2f, separator)) * 2 }
                    val dressing = village?.let { rememberDockSprites(it.look, tallest, typicalBar) }
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
                                .then(
                                    if (christmas) {
                                        Modifier.drawWithContent { drawDressedDock(slots.value, separator, focusedIndex, wide, dressing) }
                                    } else {
                                        (if (wide) Modifier.clipToBounds() else Modifier).drawBehind {
                                            drawDock(slots.value, separator, focusedIndex)
                                        }
                                    },
                                ),
                        ) {
                            // The bulbs' twinkling, in its own layer between the bar and the tiles: only this is redrawn on each frame.
                            val pictures = motion?.pictures
                            if (dressing != null && motion != null && pictures != null) {
                                val twinkle = remember { DockTwinkle() }
                                Spacer(
                                    Modifier.matchParentSize().graphicsLayer().drawBehind {
                                        val t = motion.clock.seconds
                                        fillDockBar(twinkle.bar, slots.value, focusedIndex)
                                        dockClip(wide) { drawDockTwinkles(twinkle, dressing, pictures, t) }
                                    },
                                )
                            }
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
                                            // On a whole pixel: a picture drawn between pixels is blended across two and looks soft.
                                            translationX = round((dockLeft(all, focusedIndex) + slot.x).dp.toPx())
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
                            // Over the tiles, in a layer of its own: the snow on each tile, the holly, and the dog that rides the dock.
                            if (dressing != null && motion != null) {
                                val top = remember { DockTop() }
                                top.snowing = village.sky.weather == SkyWeather.SNOW
                                Spacer(
                                    Modifier.matchParentSize().graphicsLayer().drawBehind {
                                        val t = motion.clock.seconds
                                        val all = slots.value
                                        fillTileSpots(top.spots, all, focusedIndex)
                                        fillDockBar(top.bar, all, focusedIndex)
                                        if (top.away.cameBack(motion.clock)) top.ride.restart()
                                        dockClip(wide) { drawDockTop(top, dressing, motion.pictures, t) }
                                    },
                                )
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

/** The clock that moving things follow and the sheet they're cut from ([pictures], null until painted). */
private class DockMotion(val clock: MotionClock, val pictures: MotionPictures?)

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
            art?.dockTile,
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

/**
 * The dock dressed for Christmas, back to front: the glow under the selected tile, the bar, the
 * fairy lights, then the tiles and the layers among them (the bulbs' twinkling under the tiles; the
 * snow, the holly and the rider over them). Without [dressing] (its pictures aren't painted yet) just
 * the bar and the tiles. A [wide] dock, which scrolls, is kept inside its box across, but what's on
 * it may stand above it.
 */
private fun ContentDrawScope.drawDressedDock(slots: List<DockSlot>, separator: Int?, focused: Int, wide: Boolean, dressing: DockSprites?) {
    dockClip(wide) { this@drawDressedDock.drawDockLayers(slots, separator, focused, dressing) }
}

/** Draws [block], kept inside the dock's box across when the dock is [wide] (it scrolls), with room above and below it. */
private inline fun DrawScope.dockClip(wide: Boolean, block: DrawScope.() -> Unit) {
    if (wide) clipRect(top = -2 * size.height, bottom = 2 * size.height, block = block) else block()
}

/** Puts where the bar is in the dock's box into [bar], in pixels, with its ends on whole pixels. */
private fun DrawScope.fillDockBar(bar: DockBar, slots: List<DockSlot>, focused: Int) {
    bar.left = round((dockLeft(slots, focused) - DOCK_PADDING).dp.toPx())
    bar.top = size.height - BarHeight.toPx()
    bar.width = round(dockWidth(slots).dp.toPx())
    bar.height = BarHeight.toPx()
}

/** [drawDressedDock]'s layers, in order. */
private fun ContentDrawScope.drawDockLayers(slots: List<DockSlot>, separator: Int?, focused: Int, dressing: DockSprites?) {
    if (dressing == null) {
        drawDock(slots, separator, focused)
        drawContent()
        return
    }
    val tilesLeft = dockLeft(slots, focused)
    val bar = DockBar().also { fillDockBar(it, slots, focused) }
    val bottom = size.height - BarInset.toPx()
    slots.getOrNull(focused)?.let { selected ->
        val middle = round((tilesLeft + selected.x).dp.toPx()) + selected.width.dp.toPx() / 2
        drawDockGlow(dressing, middle, bottom - selected.height.dp.toPx() / 2, selected.width.dp.toPx())
    }
    drawDock(slots, separator, focused)
    drawDockLights(dressing, bar)
    drawContent()
}

/** Tells the dock's top layer where every tile is on this frame (pixels in the dock's box) and which is selected. */
private fun DrawScope.fillTileSpots(spots: TileSpots, slots: List<DockSlot>, focused: Int) {
    val tilesLeft = dockLeft(slots, focused)
    val bottom = size.height - BarInset.toPx()
    spots.resize(slots.size)
    spots.focused = focused
    for (i in slots.indices) {
        val slot = slots[i]
        spots.left[i] = round((tilesLeft + slot.x).dp.toPx())
        spots.top[i] = bottom - slot.height.dp.toPx()
        spots.scale[i] = slot.height / DOCK_TALLEST
        spots.tallest[i] = slot.width / slot.height * DOCK_TALLEST * 2 // The dressing is measured 2 units to a dp.
    }
}

/** The see-through bar behind the tiles (as wide as they are, [slots]) and the thin line before [separator], if any. */
private fun DrawScope.drawDock(slots: List<DockSlot>, separator: Int?, focused: Int) {
    // Edges on whole pixels, so the bar's thin outline is drawn crisp instead of smeared across two.
    val left = round((dockLeft(slots, focused) - DOCK_PADDING).dp.toPx())
    val width = round(dockWidth(slots).dp.toPx())
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
        val x = round((dockLeft(slots, focused) + dockSeparatorX(slots, separator)).dp.toPx())
        val line = SeparatorHeight.toPx()
        drawRect(Color.White.copy(alpha = 0.2f), Offset(x, top + (bar - line) / 2), Size(edge, line))
    }
}
