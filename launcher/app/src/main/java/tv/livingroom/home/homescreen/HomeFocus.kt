package tv.livingroom.home.homescreen

import java.time.LocalDateTime
import tv.livingroom.home.apps.RowItem
import tv.livingroom.home.testswitch.HomeOverrides

/** The row app used most recently, or null if none of them has been. */
fun mostRecentApp(lastOpened: Map<String, Long>, rowPackages: Set<String>): String? =
    lastOpened.filter { (pkg, millis) -> pkg in rowPackages && millis > 0 }.maxByOrNull { it.value }?.key

/** Where focus starts on the app row: the most recent app's tile, else the first. */
fun startIndex(row: List<RowItem>, mostRecent: String?): Int =
    row.indexOfFirst { it is RowItem.App && it.app.packageName == mostRecent }.coerceAtLeast(0)

/**
 * Where focus goes when the tile or card that had it ([selectedKey]) has left
 * its row ([before] → [after]): the one that took its place, or the new last
 * one; -1 if the row is now empty. Null if it's still there (or never was).
 */
fun refocusIndex(selectedKey: String?, before: List<String>, after: List<String>): Int? {
    if (selectedKey == null || selectedKey in after) return null
    val index = before.indexOf(selectedKey)
    return if (index < 0) null else index.coerceAtMost(after.size - 1)
}

/**
 * A row's keys as last seen, and on which Home press, so a change to the row
 * can be told apart from a new visit. Plain fields, not Compose state: only the
 * refocus effects read it.
 */
class SeenRow(private var keys: List<String> = emptyList(), private var resumes: Int = -1) {
    /** Records [now]; where focus should go if [selectedKey] has just left the row (see [refocusIndex]), else null. */
    fun changed(now: List<String>, resumes: Int, selectedKey: String?): Int? {
        val before = keys
        val homePress = resumes != this.resumes
        keys = now
        this.resumes = resumes
        return if (homePress) null else refocusIndex(selectedKey, before, now)
    }
}

/** The time the screen shows: the real [clock], or a test switch's time ([HomeOverrides.at] wins over [HomeOverrides.hour]). */
fun shownTime(clock: LocalDateTime, overrides: HomeOverrides?): LocalDateTime =
    overrides?.at?.let { clock.with(it) } ?: overrides?.hour?.let { clock.withHour(it) } ?: clock
