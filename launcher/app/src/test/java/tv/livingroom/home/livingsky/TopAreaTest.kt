package tv.livingroom.home.livingsky

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import tv.livingroom.home.apps.HdmiInput
import tv.livingroom.home.apps.RowItem
import tv.livingroom.home.apps.TvApp
import tv.livingroom.home.watch.WatchCard

class TopAreaTest {
    private val zone = ZoneId.of("America/Edmonton")
    private val now = LocalDateTime.of(2026, 10, 19, 19, 42) // A Monday evening.

    private val crave = TvApp("ca.bellmedia.cravetv", "x.Main", "Crave")
    private val netflix = TvApp("com.netflix.ninja", "x.Main", "Netflix")
    private val row = listOf(RowItem.App(netflix), RowItem.App(crave), RowItem.Explore)

    private fun at(time: LocalDateTime) = time.atZone(zone).toInstant().toEpochMilli()

    private fun textFor(item: RowItem, lastOpened: Map<String, Long>, mostRecent: String?, time: LocalDateTime = now) =
        topAreaText(item, time, lastOpened, mostRecent, zone, is24Hour = false)

    @Test
    fun partOfDayBoundaries() {
        assertEquals(PartOfDay.EVENING, partOfDay(4))
        assertEquals(PartOfDay.MORNING, partOfDay(5))
        assertEquals(PartOfDay.MORNING, partOfDay(11))
        assertEquals(PartOfDay.AFTERNOON, partOfDay(12))
        assertEquals(PartOfDay.AFTERNOON, partOfDay(16))
        assertEquals(PartOfDay.EVENING, partOfDay(17))
        assertEquals(PartOfDay.EVENING, partOfDay(23))
        assertEquals(PartOfDay.EVENING, partOfDay(0))
    }

    @Test
    fun greetings() {
        assertEquals("Good morning.", greeting(PartOfDay.MORNING))
        assertEquals("Good afternoon.", greeting(PartOfDay.AFTERNOON))
        assertEquals("Good evening.", greeting(PartOfDay.EVENING))
    }

    @Test
    fun mostRecentAppSentence() {
        val opened = mapOf(crave.packageName to at(now.withHour(18).withMinute(10)))
        assertEquals(
            TopAreaText(
                greeting = "Good evening.",
                lead = "Pick up where you left off on",
                chip = "Crave",
                title = "Crave",
                meta = "Last opened today at 6:10 PM",
            ),
            textFor(RowItem.App(crave), opened, mostRecent = crave.packageName),
        )
    }

    @Test
    fun otherAppLastOpenedYesterday() {
        val opened = mapOf(netflix.packageName to at(LocalDateTime.of(2026, 10, 18, 21, 40)))
        val text = textFor(RowItem.App(netflix), opened, mostRecent = crave.packageName)
        assertEquals(TopAreaText("Good evening.", null, null, "Netflix", "Last opened yesterday at 9:40 PM"), text)
    }

    @Test
    fun otherAppLastOpenedWithinAWeek() {
        val opened = mapOf(netflix.packageName to at(LocalDateTime.of(2026, 10, 15, 20, 0)))
        assertEquals("Last opened Thursday", textFor(RowItem.App(netflix), opened, crave.packageName).meta)
    }

    @Test
    fun otherAppLastOpenedOlder() {
        val opened = mapOf(netflix.packageName to at(LocalDateTime.of(2026, 10, 2, 20, 0)))
        assertEquals("Last opened Oct 2", textFor(RowItem.App(netflix), opened, crave.packageName).meta)
    }

    @Test
    fun neverOpenedHasNoMeta() {
        val text = textFor(RowItem.App(netflix), emptyMap(), mostRecent = null)
        assertNull(text.meta)
        assertNull(text.lead)
        assertNull(text.chip)
        assertEquals("Netflix", text.title)
    }

    @Test
    fun zeroTimestampHasNoMeta() {
        // Usage history can list an app it only saw in the background, with a last-used time of 0.
        val text = textFor(RowItem.App(netflix), mapOf(netflix.packageName to 0L), mostRecent = crave.packageName)
        assertNull(text.meta)
    }

    @Test
    fun exploreText() {
        val expected = TopAreaText(
            greeting = "Good evening.",
            lead = "Looking for something new?",
            chip = null,
            title = "Explore",
            meta = "Opens Google TV",
        )
        assertEquals(expected, textFor(RowItem.Explore, emptyMap(), null))
        val morning = textFor(RowItem.Explore, emptyMap(), null, time = now.withHour(11).withMinute(15))
        assertEquals("Good morning.", morning.greeting)
    }

    @Test
    fun twentyFourHourClockInMeta() {
        val opened = mapOf(crave.packageName to at(now.withHour(18).withMinute(10)))
        val text = topAreaText(RowItem.App(crave), now, opened, crave.packageName, zone, is24Hour = true)
        assertEquals("Last opened today at 18:10", text.meta)
    }

    @Test
    fun cornerText12h() {
        assertEquals("Mon, Oct 19" to "7:42 PM", cornerText(now, is24Hour = false))
    }

    @Test
    fun cornerText24h() {
        assertEquals("Mon, Oct 19" to "19:42", cornerText(now, is24Hour = true))
    }

    // --- Continue watching cards ---------------------------------------------

    private val youTube = TvApp("com.google.android.youtube.tv", "x.Main", "YouTube")
    private val minute = 60_000L
    private fun watch(pkg: String, title: String, saved: Long = 0) =
        WatchCard(pkg, title, "S1 E1 · The Old Pier, Part 1", 13 * minute, 48 * minute, saved, null, null)
    private val craveCard = watch("ca.bellmedia.cravetv", "Silver Coast")
    private val netflixCard = watch("com.netflix.ninja", "A Netflix Series")

    @Test
    fun mostRecentCardSentence() {
        val text = cardTopAreaText(craveCard, "Crave", isMostRecent = true, now = now)
        assertEquals("Good evening.", text.greeting)
        assertEquals("Pick up where you left off on", text.lead)
        assertEquals("Crave", text.chip)
        assertEquals("Silver Coast", text.title)
        // One line, in the same words as everywhere: 13 of 48 minutes watched is "35 min left".
        assertEquals("S1 E1 · The Old Pier, Part 1 · 35 min left", text.meta)
    }

    @Test
    fun otherCardSaysContinueOn() {
        val text = cardTopAreaText(netflixCard, "Netflix", isMostRecent = false, now = now)
        assertEquals("Continue on", text.lead)
        assertEquals("Netflix", text.chip)
    }

    private val rowWithYouTube = listOf(RowItem.App(netflix), RowItem.App(youTube), RowItem.App(crave), RowItem.Explore)

    @Test
    fun startFocusOnMostRecentAppsCard() {
        val cards = listOf(netflixCard, craveCard)
        assertEquals(Focus.Card(1), startFocus(cards, rowWithYouTube, mostRecent = "ca.bellmedia.cravetv"))
    }

    @Test
    fun startFocusOnTileWhenAppDidNotShare() {
        assertEquals(Focus.Tile(1), startFocus(listOf(craveCard), rowWithYouTube, mostRecent = "com.google.android.youtube.tv"))
    }

    @Test
    fun noUsageAccessPicksNewestCard() {
        assertEquals(Focus.Card(0), startFocus(listOf(netflixCard, craveCard), rowWithYouTube, mostRecent = null))
    }

    @Test
    fun noCardsFallsBackToTile() {
        assertEquals(Focus.Tile(2), startFocus(emptyList(), rowWithYouTube, mostRecent = "ca.bellmedia.cravetv"))
        assertEquals(Focus.Tile(0), startFocus(emptyList(), rowWithYouTube, mostRecent = null))
    }

    private fun ps5(on: Boolean?) = RowItem.Input(HdmiInput("hdmi/HDMI100004", "PlayStation 5", "HDMI 1", on))

    @Test
    fun inputOnText() {
        val expected = TopAreaText(greeting = "Good evening.", lead = null, chip = null, title = "PlayStation 5", meta = "On · HDMI 1")
        assertEquals(expected, textFor(ps5(on = true), emptyMap(), null))
    }

    @Test
    fun inputStandbyText() {
        assertEquals("In standby · HDMI 1", textFor(ps5(on = false), emptyMap(), null).meta)
    }

    @Test
    fun inputUnknownStateText() {
        assertEquals("HDMI 1", textFor(ps5(on = null), emptyMap(), null).meta)
        val noPort = RowItem.Input(HdmiInput("hdmi/HDMI100004", "PlayStation 5", null, null))
        assertEquals(null, textFor(noPort, emptyMap(), null).meta)
    }

    @Test
    fun movingText() {
        val expected =
            TopAreaText(greeting = "Moving", lead = null, chip = null, title = "PlayStation 5", meta = "◀ ▶ to move it, OK when done")
        assertEquals(expected, movingTopAreaText("PlayStation 5"))
    }

    // --- Several shows per app, and the scroll ----------------------

    @Test
    fun startFocusOnNewestCardOfMostRecentApp() {
        // Newest first: YouTube's video, then Crave's two shows.
        val cards =
            listOf(
                watch("com.google.android.youtube.tv", "A Video"),
                watch("ca.bellmedia.cravetv", "Newer"),
                watch("ca.bellmedia.cravetv", "Older"),
            )
        assertEquals(Focus.Card(1), startFocus(cards, rowWithYouTube, mostRecent = "ca.bellmedia.cravetv"))
    }

    @Test
    fun rowStartAtEnds() {
        assertEquals(0, cardRowStart(selectedIndex = 0, count = 12))
        assertEquals(0, cardRowStart(selectedIndex = 1, count = 12))
        assertEquals(4, cardRowStart(selectedIndex = 5, count = 12))
        assertEquals(8, cardRowStart(selectedIndex = 11, count = 12))
        for (i in 0 until 3) assertEquals(0, cardRowStart(selectedIndex = i, count = 3))
        assertEquals(0, cardRowStart(selectedIndex = 0, count = 0))
    }
}
