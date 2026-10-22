package tv.livingroom.home.dock

import java.time.LocalDate
import tv.livingroom.home.apps.RowItem
import tv.livingroom.home.screensaver.saverDateLine

/*
 * Mountains and dock: the floating dock's rules. The selected tile is biggest
 * and the ones next to it a little bigger, so the dock swells around focus
 * (sizes in dp). DockHomeView draws it.
 */

/** Tile heights by how far a tile is from the selected one: the selected one, its neighbours, the next ones out, everything further. */
private val DOCK_HEIGHTS = listOf(58.5f, 45f, 38f, 34f)

/** The tallest tile (the selected one): tiles are drawn at this size and scaled down. */
val DOCK_TALLEST = DOCK_HEIGHTS.first()

/** Space between tiles, inside the bar's ends, and added by the thin line before the PS5 and Explore (the line and one more gap), in dp. */
const val DOCK_GAP = 6f
const val DOCK_PADDING = 9f
const val DOCK_SEPARATOR_SPACE = 1f + DOCK_GAP

/** The widest the dock gets before it scrolls: the screen less Living sky's side margins. */
const val DOCK_MAX_WIDTH = 960f - 2 * 43f

/**
 * A tile's height in dp, [distance] tiles from where focus is. While focus glides from one tile to
 * the next the distance is a fraction, and the height runs smoothly between the steps.
 */
fun dockHeightAt(distance: Float): Float {
    val d = kotlin.math.abs(distance).coerceAtMost((DOCK_HEIGHTS.size - 1).toFloat())
    val step = d.toInt().coerceAtMost(DOCK_HEIGHTS.size - 2)
    val t = d - step
    return DOCK_HEIGHTS[step] + (DOCK_HEIGHTS[step + 1] - DOCK_HEIGHTS[step]) * t
}

/** A tile's width for its [height]: an app's banner is 16:9, the PS5 and Explore are square. */
fun dockTileWidth(height: Float, square: Boolean): Float = if (square) height else height * 16 / 9

/** Where a tile sits in the dock: [x] from the bar's inner left edge, and its size, in dp. */
data class DockSlot(val x: Float, val width: Float, val height: Float)

/**
 * Every tile's place with focus [at] (a tile's index, or between two while gliding); [squares] says
 * which tiles are square. The thin line, if any, comes before [separatorBefore].
 */
fun dockSlots(squares: List<Boolean>, at: Float, separatorBefore: Int?): List<DockSlot> {
    var x = 0f
    return squares.mapIndexed { i, square ->
        if (i > 0) x += DOCK_GAP
        if (i == separatorBefore) x += DOCK_SEPARATOR_SPACE
        val height = dockHeightAt(i - at)
        DockSlot(x, dockTileWidth(height, square), height).also { x += it.width }
    }
}

/** Where the thin line goes, from the bar's inner left edge: one gap before the tile it comes before. */
fun dockSeparatorX(slots: List<DockSlot>, separatorBefore: Int): Float = slots[separatorBefore].x - DOCK_GAP - 1f

/** The dock's whole width in dp: its tiles and the bar's ends. */
fun dockWidth(slots: List<DockSlot>): Float = (slots.lastOrNull()?.let { it.x + it.width } ?: 0f) + 2 * DOCK_PADDING

/**
 * How far from the left of its [view] (dp) the dock starts: centred when it fits; when it's wider,
 * slid so the tile at [focusCentre] (dp from the dock's left) sits in the middle, stopping at the ends.
 */
fun dockStart(width: Float, focusCentre: Float, view: Float): Float =
    if (width <= view) (view - width) / 2 else (view / 2 - focusCentre).coerceIn(view - width, 0f)

/**
 * Where the thin line goes: before the inputs and Explore at the end of the
 * row, so they sit apart from the apps. None if an input was moved in among the
 * apps, if there are no inputs, or if nothing comes before them.
 */
fun dockSeparatorBefore(row: List<RowItem>): Int? {
    val start = row.indexOfLast { it is RowItem.App } + 1
    if (start == 0 || start >= row.size) return null
    return start.takeIf { row.drop(start).any { it is RowItem.Input } }
}

/** The line under the clock: "Tuesday, October 20 · ☀ −2° Clear · Sunset 6:33 PM", without the weather or sky part when there's none. */
fun dockInfoLine(date: LocalDate, weather: String?, skyLine: String?): String =
    listOfNotNull(saverDateLine(date, weather), skyLine).joinToString(" · ")
