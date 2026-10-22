package tv.livingroom.home.livingsky

import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.livingroom.home.R
import tv.livingroom.home.SettingsActivity
import tv.livingroom.home.TAG
import tv.livingroom.home.apps.NEUTRAL_GLOW
import tv.livingroom.home.apps.RowItem
import tv.livingroom.home.apps.key
import tv.livingroom.home.apps.label
import tv.livingroom.home.apps.openApp
import tv.livingroom.home.apps.openGoogleHome
import tv.livingroom.home.apps.openInput
import tv.livingroom.home.apps.start
import tv.livingroom.home.homescreen.Background
import tv.livingroom.home.homescreen.RefocusWhenTileLeaves
import tv.livingroom.home.homescreen.SeenRow
import tv.livingroom.home.homescreen.minuteClock
import tv.livingroom.home.homescreen.mostRecentApp
import tv.livingroom.home.homescreen.moveKeys
import tv.livingroom.home.homescreen.rememberAppArt
import tv.livingroom.home.homescreen.rememberHomeWeather
import tv.livingroom.home.homescreen.shownTime
import tv.livingroom.home.sky.HEADING_TEXT_ALPHA
import tv.livingroom.home.sky.HomeSky
import tv.livingroom.home.sky.cornerSkyLine
import tv.livingroom.home.sky.homeSkyScene
import tv.livingroom.home.testswitch.HomeOverrides
import tv.livingroom.home.tiles.AppTile
import tv.livingroom.home.tiles.ExploreTile
import tv.livingroom.home.tiles.InputTile
import tv.livingroom.home.tiles.MenuAction
import tv.livingroom.home.tiles.TileMenu
import tv.livingroom.home.tiles.cardMenu
import tv.livingroom.home.tiles.tileMenu
import tv.livingroom.home.watch.WatchCard
import tv.livingroom.home.watch.WatchStore
import tv.livingroom.home.watch.continueRow
import tv.livingroom.home.watch.key
import tv.livingroom.home.watch.resume

// Positions in dp on the TV's 960 x 540 dp screen (1920 x 1080 px).
private val SideMargin = 43.dp
private val CardsLabelTop = 230.dp // 16 dp above the cards, so the lifted card's ring clears it.
private val CardsRowTop = 262.dp
private val AppsRowTop = 448.dp // With or without shows: without, the sky fills the space above.
private val TileGap = 8.dp
private val LiftRoom = 12.dp // Room above and below a row for the lifted tile's ring.

/**
 * The home screen. [row] is null until it has loaded; [cards] are the saved
 * Continue watching shows; [lastOpened] is when each app was last used. Each
 * time [resumes] goes up (every Home press), focus goes back to the app used
 * last: its show's card if it shared one, else its tile. [overrides] come
 * from a test switch (TestSwitchActivity).
 * [onMove] and [onHide] change the app row (from a tile's long-press menu);
 * [onRemoveShow] takes a show off Continue watching (by its [WatchCard.key]).
 */
@OptIn(ExperimentalFoundationApi::class) // LocalBringIntoViewSpec, for the card row's scrolling.
@Composable
fun HomeScreen(
    row: List<RowItem>?,
    cards: List<WatchCard>,
    lastOpened: Map<String, Long>,
    overrides: HomeOverrides?,
    resumes: Int,
    onMove: (key: String, by: Int) -> Unit,
    onHide: (key: String) -> Unit,
    onRemoveShow: (key: String) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val cardListState = rememberLazyListState()
    val pictures = remember { mutableStateMapOf<String, ImageBitmap?>() } // A show's key → its picture.
    val pictureVersion by WatchStore.pictureVersion.collectAsState()
    val apps = remember(row) { row.orEmpty().filterIsInstance<RowItem.App>().associate { it.app.packageName to it.app } }
    val art = rememberAppArt(apps, resumes)
    val focusRequesters = remember(row?.size) { List(row?.size ?: 0) { FocusRequester() } }
    val mostRecent = remember(row, lastOpened) { mostRecentApp(lastOpened, apps.keys) }
    val clock = minuteClock()
    val watchRow = remember(cards, apps, clock) { continueRow(cards, apps.keys, System.currentTimeMillis()) }
    val cardRequesters = remember(watchRow.size) { List(watchRow.size) { FocusRequester() } }
    val firstFocus = remember(watchRow, row, mostRecent) { startFocus(watchRow, row.orEmpty(), mostRecent) }
    // The show that gets "Pick up where you left off": the one focus starts on.
    val leadShow = (firstFocus as? Focus.Card)?.let { watchRow.getOrNull(it.index)?.key }
    var selected by remember { mutableStateOf<Selected?>(null) }
    var menu by remember { mutableStateOf<Selected?>(null) } // The tile or show whose long-press menu is open.
    var moving by remember { mutableStateOf<String?>(null) } // The key of the tile being moved.
    val rowKeys = remember(row) { row.orEmpty().map { it.key } }
    val cardKeys = remember(watchRow) { watchRow.map { it.key } }
    // What was selected as each row changed, read while composing: by the time an effect runs,
    // a tile or card that left has already lost focus to whatever Compose picked instead.
    val tileAtChange = remember(rowKeys) { (selected as? Selected.Tile)?.item?.key }
    val cardAtChange = remember(cardKeys) { (selected as? Selected.Show)?.key }
    val seenCards = remember { SeenRow() }
    val now = shownTime(clock, overrides)
    val zone = ZoneId.systemDefault()
    val is24Hour = DateFormat.is24HourFormat(context)
    val weather = rememberHomeWeather(clock, resumes, overrides?.weatherCode)

    MaterialTheme(colorScheme = darkColorScheme()) {
        // No Surface at the root, so text would default to black.
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(Modifier.fillMaxSize().background(Background)) {
                // A selected show that has since left the row falls back to where focus starts.
                val shown = selected?.takeIf { it !is Selected.Show || watchRow.any { c -> c.key == it.key } }
                    ?.let { it.current(row) }
                    ?: firstFocus.toSelected(watchRow, row)
                val shownCardIndex = (shown as? Selected.Show)?.let { s -> watchRow.indexOfFirst { it.key == s.key } }
                val shownCard = shownCardIndex?.let { watchRow[it] }
                // Calgary's sky right now, with the weather in it only when the corner shows it (on and fresh).
                val skyScene = remember(now, weather.skyCode) { homeSkyScene(now.atZone(zone), weather.skyCode) }
                // First, so everything else is drawn on top of it.
                HomeSky(skyScene)
                val (date, time) = cornerText(now, is24Hour)
                val skyLine = remember(now, is24Hour) { cornerSkyLine(now.atZone(zone), is24Hour) } // Once a minute, not on every move.
                Corner(weather.corner, date, time, skyLine = skyLine)
                val movingItem = moving?.let { key -> row?.firstOrNull { it.key == key } }
                when {
                    movingItem != null -> TopAreaCopy(movingTopAreaText(movingItem.label))
                    shownCard != null -> TopAreaCopy(
                        cardTopAreaText(
                            shownCard,
                            apps[shownCard.packageName]?.label ?: shownCard.packageName,
                            shownCard.key == leadShow,
                            now,
                        ),
                    )
                    shown is Selected.Tile -> TopAreaCopy(topAreaText(shown.item, now, lastOpened, mostRecent, zone, is24Hour))
                }
                val hasShows = watchRow.isNotEmpty()
                if (hasShows) {
                    RowLabel(stringResource(R.string.continue_watching), top = CardsLabelTop)
                    // The selected card keeps one card to its left in view.
                    val density = LocalDensity.current
                    // Remembered: a new one on each recomposition would make the whole card row recompose on every move.
                    val keepOneBefore = remember(density) { OneCardBefore(with(density) { (SideMargin + CardWidth + CardGap).toPx() }) }
                    CompositionLocalProvider(LocalBringIntoViewSpec provides keepOneBefore) {
                        LazyRow(
                            state = cardListState,
                            contentPadding = PaddingValues(horizontal = SideMargin, vertical = LiftRoom),
                            horizontalArrangement = Arrangement.spacedBy(CardGap),
                            modifier = Modifier.padding(top = CardsRowTop - LiftRoom),
                        ) {
                            itemsIndexed(watchRow, key = { _, card -> card.key }) { index, card ->
                                WatchCardTile(
                                    card,
                                    picture = pictures[card.key],
                                    art = art[card.packageName]?.tile,
                                    glow = art[card.packageName]?.glow ?: NEUTRAL_GLOW,
                                    onClick = { apps[card.packageName]?.let { context.resume(card, it) } },
                                    onLongClick = { menu = Selected.Show(card.key) },
                                    modifier = Modifier
                                        .focusRequester(cardRequesters[index])
                                        .onFocusChanged { if (it.isFocused) selected = Selected.Show(card.key) },
                                )
                            }
                        }
                    }
                }
                // No heading: the app logos say what the row is.
                if (row != null) {
                    LazyRow(
                        state = listState,
                        // Room above and below for the lifted tile's ring and shadow.
                        contentPadding = PaddingValues(horizontal = SideMargin, vertical = LiftRoom),
                        horizontalArrangement = Arrangement.spacedBy(TileGap),
                        modifier = Modifier.padding(top = AppsRowTop - LiftRoom),
                    ) {
                        itemsIndexed(row, key = { _, item -> item.key }) { index, item ->
                            val tileModifier = Modifier
                                .focusRequester(focusRequesters[index])
                                .onFocusChanged { if (it.isFocused) selected = Selected.Tile(item) }
                                .onPreviewKeyEvent { event ->
                                    moving == item.key &&
                                        moveKeys(event, onMove = { by -> onMove(item.key, by) }, onDone = { moving = null })
                                }
                            val openMenu = { menu = Selected.Tile(item) }
                            when (item) {
                                is RowItem.App -> {
                                    val loaded = art[item.app.packageName]
                                    AppTile(
                                        item.app.label,
                                        loaded?.tile,
                                        loaded = loaded != null,
                                        onClick = { context.openApp(item.app) },
                                        onLongClick = openMenu,
                                        modifier = tileModifier,
                                    )
                                }
                                is RowItem.Input -> InputTile(item.input, onClick = {
                                    context.openInput(item.input)
                                }, onLongClick = openMenu, modifier = tileModifier)
                                RowItem.Explore -> ExploreTile(onClick = {
                                    context.openGoogleHome()
                                }, onLongClick = openMenu, modifier = tileModifier)
                            }
                        }
                    }
                }
                menu?.let { target ->
                    val card = (target as? Selected.Show)?.let { s -> watchRow.firstOrNull { it.key == s.key } }
                    val tile = (target as? Selected.Tile)?.item
                    TileMenu(
                        title = card?.title ?: tile?.label ?: "",
                        actions = if (card != null) cardMenu() else tile?.let { tileMenu(it) }.orEmpty(),
                        onDismiss = { menu = null },
                        onPick = { action ->
                            menu = null
                            when (action) {
                                MenuAction.MOVE -> moving = tile?.key
                                MenuAction.HIDE -> tile?.let { onHide(it.key) }
                                MenuAction.REMOVE_SHOW -> card?.let { onRemoveShow(it.key) }
                                MenuAction.SETTINGS -> context.start(Intent(context, SettingsActivity::class.java))
                            }
                        },
                    )
                }
            }
        }
    }

    // While moving a tile, the row scrolls to keep it in view.
    LaunchedEffect(row) {
        val key = moving ?: return@LaunchedEffect
        val index = row.orEmpty().indexOfFirst { it.key == key }
        val visible = listState.layoutInfo.visibleItemsInfo
        val fullyShown = visible.any {
            it.index == index && it.offset >= 0 && it.offset + it.size <= listState.layoutInfo.viewportEndOffset
        }
        if (index >= 0 && !fullyShown) listState.animateScrollToItem(index)
    }

    val currentRow by rememberUpdatedState(row)
    val currentMostRecent by rememberUpdatedState(mostRecent)
    val currentWatchRow by rememberUpdatedState(watchRow)
    val currentFocusRequesters by rememberUpdatedState(focusRequesters)
    val currentCardRequesters by rememberUpdatedState(cardRequesters)

    // Moving ends if its tile leaves the row (its device went away) or focus lands somewhere else.
    LaunchedEffect(rowKeys, selected) {
        val key = moving ?: return@LaunchedEffect
        if (key !in rowKeys || (selected as? Selected.Tile)?.item?.key != key) moving = null
    }

    RefocusWhenTileLeaves(rowKeys, resumes, tileAtChange, focusRequesters)

    // If the selected show leaves the row (removed from its menu), focus goes to the next one, or to the app row if it was the last.
    LaunchedEffect(cardKeys, resumes) {
        val index = seenCards.changed(cardKeys, resumes, cardAtChange) ?: return@LaunchedEffect
        withFrameNanos {}
        val requester = if (index < 0) currentFocusRequesters.firstOrNull() else currentCardRequesters.getOrNull(index)
        runCatching { requester?.requestFocus() }
        // Focus coming back scrolls the row by itself, but from where the removed card was: put it where the rule says.
        if (index >= 0) settleCardRow(cardListState, cardRowStart(index, cardKeys.size))
    }

    // The shows' pictures: loaded when a show changes or a new picture is saved. A saved
    // picture never changes (it's named after its show), so only shows without one are looked at.
    LaunchedEffect(cardKeys, pictureVersion) {
        pictures.keys.retainAll(cardKeys.toSet())
        for (card in watchRow) {
            if (pictures[card.key] != null) continue
            pictures[card.key] = withContext(Dispatchers.IO) { WatchStore.pictureFor(context, card) }
        }
    }

    // Only on Home presses: the row also changes while the screen is showing (a tile moved or hidden, the PS5 turning on).
    LaunchedEffect(resumes) {
        moving = null
        menu = null
        val startRow = currentRow
        if (startRow.isNullOrEmpty()) return@LaunchedEffect
        val focus = startFocus(currentWatchRow, startRow, currentMostRecent)
        val target = focus.toSelected(currentWatchRow, startRow)
        selected = target
        when (focus) {
            is Focus.Card -> cardListState.scrollToItem(cardRowStart(focus.index, currentWatchRow.size))
            is Focus.Tile -> {
                listState.scrollToItem(focus.index)
                if (currentWatchRow.isNotEmpty()) cardListState.scrollToItem(0) // Not left scrolled to the last cards.
            }
        }
        withFrameNanos {} // Let the tile be laid out first.
        // The rows may have changed during that frame: find the target again by its key.
        val requester = when (target) {
            is Selected.Show -> currentCardRequesters.getOrNull(currentWatchRow.indexOfFirst { it.key == target.key })
            is Selected.Tile -> currentFocusRequesters.getOrNull(currentRow.orEmpty().indexOfFirst { it.key == target.item.key })
            null -> null
        } ?: currentFocusRequesters.firstOrNull()
        runCatching { requester?.requestFocus() }.onFailure { Log.w(TAG, "Could not focus $focus", it) }
        if (focus is Focus.Card) settleCardRow(cardListState, cardRowStart(focus.index, currentWatchRow.size))
    }
}

/** After focus lands on a card, a frame later: scroll the card row to [firstIndex] (this scroll wins over focus's own). */
private suspend fun settleCardRow(state: LazyListState, firstIndex: Int) {
    withFrameNanos {}
    if (state.firstVisibleItemIndex != firstIndex || state.firstVisibleItemScrollOffset != 0) state.animateScrollToItem(firstIndex)
}

/** A row's heading. */
@Composable
private fun RowLabel(text: String, top: Dp) {
    Text(
        text.uppercase(),
        color = Color.White.copy(alpha = HEADING_TEXT_ALPHA),
        fontSize = 14.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.14.em,
        modifier = Modifier.padding(start = SideMargin, top = top),
    )
}

/** Scrolls a row so the item being brought into view starts at [startPx] (one card in from the left); the row stops at its ends. */
private class OneCardBefore(private val startPx: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = offset - startPx
}

/** What's selected: an app row tile, or a Continue watching card (by its show's key, since the card itself changes as it's saved). */
private sealed interface Selected {
    data class Tile(val item: RowItem) : Selected
    data class Show(val key: String) : Selected
}

/** The same tile as it is now in [row] (an input's on/off may have changed since it was selected). */
private fun Selected.current(row: List<RowItem>?): Selected =
    if (this is Selected.Tile) Selected.Tile(row?.firstOrNull { it.key == item.key } ?: item) else this

private fun Focus.toSelected(watchRow: List<WatchCard>, row: List<RowItem>?): Selected? = when (this) {
    is Focus.Card -> watchRow.getOrNull(index)?.let { Selected.Show(it.key) }
    is Focus.Tile -> row?.getOrNull(index)?.let { Selected.Tile(it) }
}
