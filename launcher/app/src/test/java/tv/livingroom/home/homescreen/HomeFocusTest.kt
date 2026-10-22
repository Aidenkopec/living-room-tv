package tv.livingroom.home.homescreen

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.livingroom.home.apps.RowItem
import tv.livingroom.home.apps.TvApp
import tv.livingroom.home.testswitch.HomeOverrides

class HomeFocusTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val now = LocalDateTime.of(2026, 10, 19, 19, 42) // A Monday evening.

    private val crave = TvApp("ca.bellmedia.cravetv", "x.Main", "Crave")
    private val netflix = TvApp("com.netflix.ninja", "x.Main", "Netflix")
    private val row = listOf(RowItem.App(netflix), RowItem.App(crave), RowItem.Explore)
    private val twelve = (1..12).map { "app/Show $it" }

    private fun at(time: LocalDateTime) = time.atZone(zone).toInstant().toEpochMilli()

    @Test
    fun mostRecentIgnoresAppsNotOnRow() {
        val opened = mapOf(
            crave.packageName to at(now.minusHours(3)),
            netflix.packageName to at(now.minusHours(5)),
            "tv.livingroom.home" to at(now.minusMinutes(1)),
            "com.google.android.apps.tv.launcherx" to at(now.minusMinutes(2)),
            "com.android.tv.settings" to at(now.minusMinutes(3)),
        )
        assertEquals(crave.packageName, mostRecentApp(opened, setOf(crave.packageName, netflix.packageName)))
    }

    @Test
    fun mostRecentIsNullWithoutHistory() {
        assertNull(mostRecentApp(emptyMap(), setOf(crave.packageName)))
    }

    @Test
    fun startIndexIsMostRecent() {
        assertEquals(1, startIndex(row, crave.packageName))
    }

    @Test
    fun startIndexFallsBackToZero() {
        assertEquals(0, startIndex(row, null))
        assertEquals(0, startIndex(row, "com.example.gone"))
    }

    @Test
    fun refocusNotNeededWhileStillThere() {
        assertNull(refocusIndex("b", before = listOf("a", "b", "c"), after = listOf("b", "a", "c")))
    }

    @Test
    fun refocusGoesToTheOneThatTookItsPlace() {
        assertEquals(1, refocusIndex("b", before = listOf("a", "b", "c"), after = listOf("a", "c")))
    }

    @Test
    fun refocusAfterTheLastGoesToTheNewLast() {
        assertEquals(1, refocusIndex("c", before = listOf("a", "b", "c"), after = listOf("a", "b")))
    }

    @Test
    fun refocusWhenTheRowIsNowEmpty() {
        assertEquals(-1, refocusIndex("a", before = listOf("a"), after = emptyList()))
    }

    @Test
    fun refocusNotNeededForSomethingThatWasntThere() {
        assertNull(refocusIndex("x", before = listOf("a", "b"), after = listOf("a")))
        assertNull(refocusIndex(null, before = listOf("a", "b"), after = listOf("a")))
    }

    @Test
    fun refocusAfterRemovingFirstOfTwelve() {
        assertEquals(0, refocusIndex(twelve[0], before = twelve, after = twelve - twelve[0]))
    }

    @Test
    fun refocusAfterRemovingMiddleOfTwelve() {
        assertEquals(5, refocusIndex(twelve[5], before = twelve, after = twelve - twelve[5]))
    }

    @Test
    fun refocusAfterRemovingLastOfTwelve() {
        assertEquals(10, refocusIndex(twelve[11], before = twelve, after = twelve - twelve[11]))
    }

    @Test
    fun refocusAfterRemovingOnlyShow() {
        assertEquals(-1, refocusIndex("app/Only", before = listOf("app/Only"), after = emptyList()))
    }

    @Test
    fun seenRowRefocusesOnlyWithinOneVisit() {
        val seen = SeenRow()
        assertNull(seen.changed(listOf("a", "b", "c"), resumes = 1, selectedKey = null)) // First sight.
        assertEquals(1, seen.changed(listOf("a", "c"), resumes = 1, selectedKey = "b")) // "b" was hidden.
        assertNull(seen.changed(listOf("c"), resumes = 2, selectedKey = "a")) // A Home press decides instead.
    }

    @Test
    fun shownTimeIsRealWithoutASwitch() {
        assertEquals(now, shownTime(now, null))
        assertEquals(now, shownTime(now, HomeOverrides(sampleShows = 3)))
    }

    @Test
    fun shownTimeFollowsTheSwitch() {
        assertEquals(now.withHour(11), shownTime(now, HomeOverrides(hour = 11)))
        assertEquals(now.with(LocalTime.of(7, 45)), shownTime(now, HomeOverrides(hour = 11, at = LocalTime.of(7, 45))))
    }
}
