package tv.livingroom.home.watch

/**
 * One show, as saved on the TV. [episode] is the line under the title
 * ("S1 E1 · The Old Pier, Part 1", or a YouTube channel); [savedAtMs] is when
 * it was last seen playing. [mediaId] and [mediaUri] are whatever ID the app
 * shared (Paramount+ puts its content ID in [mediaUri]). [removedAtMs] is set
 * when the viewer removed it: the line stays as a marker, so it comes back only
 * once it plays again (see [acceptSave]).
 */
data class WatchCard(
    val packageName: String,
    val title: String,
    val episode: String?,
    val positionMs: Long,
    val durationMs: Long?,
    val savedAtMs: Long,
    val mediaId: String?,
    val mediaUri: String?,
    val removedAtMs: Long? = null,
)

/** What a show is known by: its app and its title (a new episode of the same show is the same card). */
val WatchCard.key: String
    get() = "$packageName/$title"

/**
 * A card for what [now] says is playing, or null unless it's a real show: a
 * title that isn't just the app's name ([appLabel]), at least a minute
 * watched, and at least 5 minutes long when the app says how long.
 */
fun cardFrom(now: NowPlaying, appLabel: String, nowMs: Long): WatchCard? {
    val (title, episode) = showAndEpisode(now.packageName, now.fields) ?: return null
    if (title.equals(appLabel, ignoreCase = true)) return null
    if (now.positionMs < MIN_WATCHED_MS) return null
    if (now.durationMs != null && now.durationMs < MIN_LENGTH_MS) return null
    val field = { key: String -> now.fields[key]?.trim()?.takeIf { it.isNotEmpty() } }
    return WatchCard(now.packageName, title, episode, now.positionMs, now.durationMs, nowMs, field(MEDIA_ID), field(MEDIA_URI))
}

/** The show a session's [fields] name, the same way [cardFrom] names it (null if none). */
fun showTitle(packageName: String, fields: Map<String, String>): String? = showAndEpisode(packageName, fields)?.first

/** Each app's way of naming the show and episode, as observed on this TV. */
private fun showAndEpisode(packageName: String, fields: Map<String, String>): Pair<String, String?>? {
    val field = { key: String -> fields[key]?.trim()?.takeIf { it.isNotEmpty() } }
    val title = field(DISPLAY_TITLE) ?: field(TITLE)
    return when (packageName) {
        // One line: "Silver Coast ‑ S1 E1 The Old Pier, Part 1" (a non-breaking hyphen).
        CRAVE -> title?.let { line ->
            val show = line.substringBefore(CRAVE_SEPARATOR).trim()
            val rest = line.substringAfter(CRAVE_SEPARATOR, "").trim().takeIf { it.isNotEmpty() }
            show to rest?.replace(SEASON_EPISODE, "$1 · ")
        }

        // The episode's name is the title; the show is the subtitle.
        PARAMOUNT -> field(DISPLAY_SUBTITLE)?.let { show -> show to title } ?: title?.let { it to null }
        else -> title?.let { it to (field(DISPLAY_SUBTITLE) ?: field(ARTIST)) }
    }
}

/**
 * Whether to save what [now] says, given when its show was removed ([showRemovedAtMs]) and any
 * app-wide removal marker from the older one-show-per-app format ([appRemovedAtMs]); each null
 * if none. Only once something plays again after that, so a show still paused in the app, or one
 * still saying "playing" from before the removal, can't put its card straight back.
 */
fun acceptSave(appRemovedAtMs: Long?, showRemovedAtMs: Long?, now: NowPlaying): Boolean {
    fun allowed(removedAtMs: Long?) = removedAtMs == null || (now.playing && now.playUpdatedAtMs > removedAtMs)
    return allowed(appRemovedAtMs) && allowed(showRemovedAtMs)
}

/**
 * [cards] with [card] in, newest first, then the removal markers. Null (not a
 * show) changes nothing, and nor does the same card seen again (a show left
 * paused in its app keeps the time it was last watched, not the time of each
 * Home press). The newest [maxShows] are kept, no more than [perApp] from one
 * app, so a week of YouTube can't push the other apps' shows out. Markers
 * don't count.
 */
fun mergeCard(cards: List<WatchCard>, card: WatchCard?, maxShows: Int = MAX_SHOWS, perApp: Int = MAX_PER_APP): List<WatchCard> {
    if (card == null) return cards
    val same = cards.firstOrNull { it.key == card.key }
    if (same != null && same.removedAtMs == null && same.copy(savedAtMs = card.savedAtMs) == card) return cards
    val (shows, markers) = (cards.filter { it.key != card.key } + card).partition { it.removedAtMs == null }
    val perAppCount = mutableMapOf<String, Int>()
    val kept = shows.sortedByDescending { it.savedAtMs }
        .filter { perAppCount.merge(it.packageName, 1, Int::plus)!! <= perApp }
        .take(maxShows)
    return kept + markers
}

/** [cards] with the show known by [key] turned into a removal marker, at [nowMs]. */
fun removeShow(cards: List<WatchCard>, key: String, nowMs: Long): List<WatchCard> =
    cards.map { if (it.key == key) it.copy(removedAtMs = nowMs) else it }

/** [cards] without shows last seen, or markers made, over 30 days ago. */
fun dropOld(cards: List<WatchCard>, nowMs: Long): List<WatchCard> = cards.filter { nowMs - (it.removedAtMs ?: it.savedAtMs) <= MAX_AGE_MS }

/** The Continue watching row: newest first, apps on the row only, nothing older than 30 days, no removed shows, at most 12. */
fun continueRow(cards: Collection<WatchCard>, rowPackages: Set<String>, nowMs: Long): List<WatchCard> =
    cards.filter { it.removedAtMs == null && it.packageName in rowPackages && nowMs - it.savedAtMs <= MAX_AGE_MS }
        .sortedByDescending { it.savedAtMs }
        .take(MAX_SHOWS)

/** A show's saved picture file name. Unchanged since the v1 format, so pictures saved earlier carry over. */
fun pictureName(card: WatchCard): String = "${card.packageName}-${card.title.hashCode().toUInt()}.jpg"

/** The pictures to keep: those of shows still saved (not removed). */
fun picturesToKeep(cards: Collection<WatchCard>): Set<String> = cards.filter { it.removedAtMs == null }.map { pictureName(it) }.toSet()

/**
 * [count] placeholder shows for the test switch (never saved): "Sample show 1"
 * onwards, newest first, the apps in [packages] taken in turn, one with a very long title.
 */
fun sampleShows(count: Int, packages: List<String>, nowMs: Long): List<WatchCard> {
    if (packages.isEmpty()) return emptyList()
    return (1..count).map { n ->
        val title = if (n == 4) LONG_SAMPLE_TITLE else "Sample show $n"
        val duration = if (n % 3 == 2) null else (20L + n * 5) * MINUTE_MS
        WatchCard(packages[(n - 1) % packages.size], title, "S1 E$n", (5L + n) * MINUTE_MS, duration, nowMs - n * MINUTE_MS, null, null)
    }
}

/** "Just started", "13 min in", "1 hr 5 min in". */
fun progressText(positionMs: Long): String = if (positionMs < MINUTE_MS) "Just started" else "${duration(positionMs / MINUTE_MS)} in"

/** How far in the viewer is: "31 min left" when the app said how long, "13 min in" when it didn't, or "Finished". */
fun watchTime(card: WatchCard): String = when {
    progressFraction(card) == 1f -> "Finished"
    card.durationMs != null -> "${duration((card.durationMs - card.positionMs + MINUTE_MS - 1) / MINUTE_MS)} left"
    else -> progressText(card.positionMs)
}

/** The line under the top area's title: the whole episode, then the time ("S1 E1 · The Old Pier, Part 1 · 31 min left"). */
fun showMeta(card: WatchCard): String = listOfNotNull(card.episode, watchTime(card)).joinToString(" · ")

/** How far in, 0 to 1 (1 once within the last 3%), or null if the app didn't say how long. */
fun progressFraction(card: WatchCard): Float? {
    val length = card.durationMs?.takeIf { it > 0 } ?: return null
    val fraction = card.positionMs.toFloat() / length
    return if (fraction >= FINISHED) 1f else fraction.coerceIn(0f, 1f)
}

private fun duration(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0L -> "${minutes / 60} hr"
    else -> "${minutes / 60} hr ${minutes % 60} min"
}

/** The saved file: a version line, then one show per line, tab-separated (v2: v1's eight columns, then when it was removed). */
fun encodeCards(cards: Collection<WatchCard>): String = buildString {
    append(FILE_VERSION)
    for (c in cards) {
        append('\n')
        listOf(c.packageName, c.title, c.episode, c.positionMs, c.durationMs, c.savedAtMs, c.mediaId, c.mediaUri, c.removedAtMs)
            .joinTo(this, "\t") { it?.toString()?.replace(LINE_BREAKS, " ").orEmpty() }
    }
}

/**
 * Reads [encodeCards]' file back, or the older v1 format (no removal column).
 * A line that doesn't parse is skipped; an unknown version gives no shows.
 */
fun decodeCards(text: String): List<WatchCard> {
    val lines = text.lines()
    val columns = when (lines.firstOrNull()) {
        FILE_VERSION -> 9
        FILE_VERSION_1 -> 8
        else -> return emptyList()
    }
    return lines.drop(1).mapNotNull { line ->
        val f = line.split('\t')
        if (f.size != columns || f[0].isEmpty() || f[1].isEmpty()) return@mapNotNull null
        val position = f[3].toLongOrNull() ?: return@mapNotNull null
        val saved = f[5].toLongOrNull() ?: return@mapNotNull null
        val removed = if (columns == 9 && f[8].isNotEmpty()) f[8].toLongOrNull() ?: return@mapNotNull null else null
        WatchCard(
            packageName = f[0],
            title = f[1],
            episode = f[2].ifEmpty { null },
            positionMs = position,
            durationMs = f[4].toLongOrNull(),
            savedAtMs = saved,
            mediaId = f[6].ifEmpty { null },
            mediaUri = f[7].ifEmpty { null },
            removedAtMs = removed,
        )
    }
}

private const val CRAVE = "ca.bellmedia.cravetv"
private const val PARAMOUNT = "com.cbs.ca"
private const val CRAVE_SEPARATOR = " ‑ "
private val SEASON_EPISODE = Regex("""^(S\d+ E\d+) """)

// Metadata keys as plain strings (android.media.MediaMetadata's constants), so this runs in JVM tests.
private const val TITLE = "android.media.metadata.TITLE"
private const val DISPLAY_TITLE = "android.media.metadata.DISPLAY_TITLE"
private const val DISPLAY_SUBTITLE = "android.media.metadata.DISPLAY_SUBTITLE"
private const val ARTIST = "android.media.metadata.ARTIST"
private const val MEDIA_ID = "android.media.metadata.MEDIA_ID"
private const val MEDIA_URI = "android.media.metadata.MEDIA_URI"

private const val MINUTE_MS = 60_000L
private const val LONG_SAMPLE_TITLE =
    "Sample show 4, with a much longer name than most shows have, to fill two lines on its card and three up top"
private const val MIN_WATCHED_MS = MINUTE_MS
private const val MIN_LENGTH_MS = 5 * MINUTE_MS
private const val MAX_AGE_MS = 30 * 24 * 60 * MINUTE_MS
private const val FINISHED = 0.97f
private const val FILE_VERSION = "continue-watching v2"
private const val FILE_VERSION_1 = "continue-watching v1"

/** At most 12 shows on the row, and no more than 6 from one app. */
const val MAX_SHOWS = 12
const val MAX_PER_APP = 6
private val LINE_BREAKS = Regex("[\t\r\n]")
