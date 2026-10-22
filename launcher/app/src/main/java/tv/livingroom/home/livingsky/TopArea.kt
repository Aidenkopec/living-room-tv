package tv.livingroom.home.livingsky

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import tv.livingroom.home.apps.HdmiInput
import tv.livingroom.home.apps.RowItem
import tv.livingroom.home.apps.label
import tv.livingroom.home.homescreen.startIndex
import tv.livingroom.home.watch.WatchCard
import tv.livingroom.home.watch.showMeta

enum class PartOfDay { MORNING, AFTERNOON, EVENING }

/** Morning 5:00–11:59, afternoon 12:00–16:59, evening the rest (late night included). */
fun partOfDay(hour: Int): PartOfDay = when (hour) {
    in 5..11 -> PartOfDay.MORNING
    in 12..16 -> PartOfDay.AFTERNOON
    else -> PartOfDay.EVENING
}

fun greeting(part: PartOfDay): String = when (part) {
    PartOfDay.MORNING -> "Good morning."
    PartOfDay.AFTERNOON -> "Good afternoon."
    PartOfDay.EVENING -> "Good evening."
}

/** The top area's words: "[greeting] [lead] [chip]", then a big [title] and one [meta] line under it. */
data class TopAreaText(val greeting: String, val lead: String?, val chip: String?, val title: String, val meta: String?)

/**
 * What the top area says about the selected [item] at [now]. [lastOpened] is
 * package → when it was last used (epoch ms); [mostRecent] is the row app
 * used last, which gets the "pick up where you left off" sentence.
 */
fun topAreaText(
    item: RowItem,
    now: LocalDateTime,
    lastOpened: Map<String, Long>,
    mostRecent: String?,
    zone: ZoneId,
    is24Hour: Boolean,
): TopAreaText {
    val hello = greeting(partOfDay(now.hour))
    return when (item) {
        RowItem.Explore -> TopAreaText(
            greeting = hello,
            lead = "Looking for something new?",
            chip = null,
            title = "Explore",
            meta = "Opens Google TV",
        )
        is RowItem.Input -> TopAreaText(
            greeting = hello,
            lead = null,
            chip = null,
            title = item.input.label,
            meta = inputMeta(item.input),
        )
        is RowItem.App -> {
            val isMostRecent = item.app.packageName == mostRecent
            TopAreaText(
                greeting = hello,
                lead = if (isMostRecent) "Pick up where you left off on" else null,
                chip = if (isMostRecent) item.app.label else null,
                title = item.app.label,
                // 0 means seen only in the background, never opened.
                meta = lastOpened[item.app.packageName]?.takeIf { it > 0 }?.let { lastOpenedText(it, now, zone, is24Hour) },
            )
        }
    }
}

/**
 * What the top area says about a Continue watching [card] from [appLabel]: the
 * show watched last "picks up where you left off" ([isMostRecent]), the others "continue on".
 */
fun cardTopAreaText(card: WatchCard, appLabel: String, isMostRecent: Boolean, now: LocalDateTime): TopAreaText = TopAreaText(
    greeting = greeting(partOfDay(now.hour)),
    lead = if (isMostRecent) "Pick up where you left off on" else "Continue on",
    chip = appLabel,
    title = card.title,
    meta = showMeta(card), // The selected card under it carries the progress bar.
)

/** What the top area says while a tile is being moved along the row (from its long-press menu). */
fun movingTopAreaText(label: String): TopAreaText =
    TopAreaText(greeting = "Moving", lead = null, chip = null, title = label, meta = "◀ ▶ to move it, OK when done")

/** Where focus starts: a card in the Continue watching row, or a tile in the app row. */
sealed interface Focus {
    data class Card(val index: Int) : Focus
    data class Tile(val index: Int) : Focus
}

/**
 * The app used last ([mostRecent]) gets focus: its card if it shared a show,
 * else its tile. Without usage history, the newest card, else the first tile.
 */
fun startFocus(cards: List<WatchCard>, row: List<RowItem>, mostRecent: String?): Focus {
    if (mostRecent == null) return if (cards.isNotEmpty()) Focus.Card(0) else Focus.Tile(0)
    val card = cards.indexOfFirst { it.packageName == mostRecent }
    return if (card >= 0) Focus.Card(card) else Focus.Tile(startIndex(row, mostRecent))
}

/**
 * The first card scrolled into view so the selected one keeps a card to its left. [fits] is how
 * many whole cards fit (a fifth shows in part); near the end the row itself stops a little short of this.
 */
fun cardRowStart(selectedIndex: Int, count: Int, fits: Int = 4): Int = (selectedIndex - 1).coerceIn(0, maxOf(0, count - fits))

/** "On · HDMI 1", "In standby · HDMI 1", or just the port when the TV can't tell. */
private fun inputMeta(input: HdmiInput): String? {
    val state = when (input.on) {
        true -> "On"
        false -> "In standby"
        null -> null
    }
    return listOfNotNull(state, input.portLabel).joinToString(" · ").ifEmpty { null }
}

/** "Last opened today at 6:10 PM", "… yesterday at …", "… Thursday" (this week) or "… Oct 2". */
private fun lastOpenedText(millis: Long, now: LocalDateTime, zone: ZoneId, is24Hour: Boolean): String {
    val then = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zone)
    val daysAgo = ChronoUnit.DAYS.between(then.toLocalDate(), now.toLocalDate())
    val time = timeText(then, is24Hour)
    return when {
        daysAgo <= 0 -> "Last opened today at $time"
        daysAgo == 1L -> "Last opened yesterday at $time"
        daysAgo < 7 -> "Last opened ${then.format(WEEKDAY)}"
        else -> "Last opened ${then.format(MONTH_DAY)}"
    }
}

/** The corner's date and clock: ("Mon, Oct 19", "7:42 PM"). */
fun cornerText(now: LocalDateTime, is24Hour: Boolean): Pair<String, String> = now.format(DATE) to timeText(now, is24Hour)

private fun timeText(time: LocalDateTime, is24Hour: Boolean): String = time.format(if (is24Hour) TIME_24 else TIME_12)

// Fixed English patterns: Canadian English would print "p.m.", and all the screen's words are English.
private val DATE = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)
private val WEEKDAY = DateTimeFormatter.ofPattern("EEEE", Locale.US)
private val MONTH_DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val TIME_12 = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val TIME_24 = DateTimeFormatter.ofPattern("H:mm", Locale.US)
