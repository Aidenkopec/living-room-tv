package tv.livingroom.home.watch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Field layouts are exactly what each app sent on the TV; show names are made up. */
class ContinueWatchingTest {
    private val nowMs = 1_800_000_000_000L
    private val min = 60_000L
    private val day = 24 * 60 * min

    private fun playing(
        pkg: String,
        fields: Map<String, String>,
        positionMs: Long,
        durationMs: Long? = null,
        playUpdatedAtMs: Long = nowMs,
    ) = NowPlaying(pkg, fields, durationMs, positionMs, playing = true, playUpdatedAtMs = playUpdatedAtMs)

    private fun card(
        pkg: String = CRAVE,
        title: String = "Silver Coast",
        episode: String? = "S1 E1 · The Old Pier, Part 1",
        positionMs: Long = 13 * min,
        durationMs: Long? = 48 * min,
        savedAtMs: Long = nowMs,
    ) = WatchCard(pkg, title, episode, positionMs, durationMs, savedAtMs, mediaId = null, mediaUri = null)

    // --- What makes a card ---------------------------------------------------

    @Test
    fun craveShowMakesACard() {
        // Crave sends one line, show and episode joined by a non-breaking hyphen, and no length.
        val crave = playing(CRAVE, mapOf(TITLE to "Silver Coast ‑ S1 E1 The Old Pier, Part 1"), positionMs = 868_000)
        assertEquals(
            WatchCard(CRAVE, "Silver Coast", "S1 E1 · The Old Pier, Part 1", 868_000, null, nowMs, null, null),
            cardFrom(crave, "Crave", nowMs),
        )
    }

    @Test
    fun craveFilmHasNoEpisode() {
        val film = playing(CRAVE, mapOf(TITLE to "A Long Film"), positionMs = 30 * min)
        val card = cardFrom(film, "Crave", nowMs)!!
        assertEquals("A Long Film", card.title)
        assertNull(card.episode)
    }

    @Test
    fun youTubeChannelIsTheEpisodeLine() {
        val video = playing(
            YOUTUBE,
            mapOf(TITLE to "How I Built a Cabin", ARTIST to "Some Channel", ALBUM to "", DURATION to "2646981"),
            positionMs = 5 * min,
            durationMs = 2_646_981,
        )
        val card = cardFrom(video, "YouTube", nowMs)!!
        assertEquals("How I Built a Cabin", card.title)
        assertEquals("Some Channel", card.episode)
        assertEquals(2_646_981L, card.durationMs)
    }

    @Test
    fun paramountShowIsTheSubtitle() {
        // Paramount+ puts the episode name in the title and the show in the subtitle; its ID is in MEDIA_URI.
        val episode = playing(
            PARAMOUNT,
            mapOf(
                TITLE to "Sunday Funday",
                DISPLAY_TITLE to "Sunday Funday",
                DISPLAY_SUBTITLE to "Beach House: Summer Trip",
                "android.media.metadata.DISPLAY_DESCRIPTION" to "Everyone goes to the beach.",
                MEDIA_URI to "AbC123xyz",
                DURATION to "2515000",
                "android.media.metadata.ART_URI" to "https://example.com/a.jpg",
            ),
            positionMs = 22 * min,
            durationMs = 2_515_000,
        )
        val card = cardFrom(episode, "Paramount+", nowMs)!!
        assertEquals("Beach House: Summer Trip", card.title)
        assertEquals("Sunday Funday", card.episode)
        assertEquals("AbC123xyz", card.mediaUri)
    }

    @Test
    fun primeEmptyTitleIsNotAShow() {
        val film = playing(
            PRIME,
            mapOf(DISPLAY_TITLE to "", MEDIA_ID to "amzn1.dv.gti.0000", "android.media.metadata.AUTHOR" to "PrimeVideo", DURATION to "-1"),
            positionMs = 64 * min,
        )
        assertNull(cardFrom(film, "Prime Video", nowMs))
    }

    @Test
    fun appleTvLengthOnlyIsNotAShow() {
        assertNull(cardFrom(playing(APPLE_TV, mapOf(DURATION to "1516000"), 15 * min, 1_516_000), "Apple TV", nowMs))
    }

    @Test
    fun showTitleMatchesTheCard() {
        // Used to check a live session is still on the card's show (resume level 1).
        assertEquals("Silver Coast", showTitle(CRAVE, mapOf(TITLE to "Silver Coast \u2011 S1 E2 Another Night")))
        assertEquals("Beach House: Summer Trip", showTitle(PARAMOUNT, mapOf(TITLE to "Ep", DISPLAY_SUBTITLE to "Beach House: Summer Trip")))
        assertNull(showTitle(PRIME, mapOf(DISPLAY_TITLE to "")))
    }

    @Test
    fun appNameIsNotAShow() {
        assertNull(cardFrom(playing(CRAVE, mapOf(TITLE to "Crave"), 10 * min), "Crave", nowMs))
    }

    @Test
    fun shortClipIsNotAShow() {
        assertNull(cardFrom(playing(YOUTUBE, mapOf(TITLE to "A trailer"), 90_000, durationMs = 2 * min), "YouTube", nowMs))
    }

    @Test
    fun underAMinuteIsNotAShow() {
        assertNull(cardFrom(playing(YOUTUBE, mapOf(TITLE to "A video"), 40_000, durationMs = 20 * min), "YouTube", nowMs))
    }

    @Test
    fun noTitleIsNotAShow() {
        assertNull(cardFrom(playing(YOUTUBE, mapOf(TITLE to "", ARTIST to ""), 5 * min, 20 * min), "YouTube", nowMs))
        assertNull(cardFrom(playing(YOUTUBE, emptyMap(), 5 * min, 20 * min), "YouTube", nowMs))
    }

    // --- Several shows per app ----------------------------------------------

    @Test
    fun newerShowJoinsTheOlder() {
        val old = card(title = "Old Show", savedAtMs = nowMs - min)
        val new = card(title = "New Show")
        assertEquals(listOf(new, old), mergeCard(listOf(old), new))
    }

    @Test
    fun secondShowFromSameAppIsKept() {
        val a = card(title = "Show A", savedAtMs = nowMs - min)
        val b = card(title = "Show B")
        assertEquals(2, mergeCard(listOf(a), b).size)
    }

    @Test
    fun sameShowNewEpisodeUpdates() {
        val e1 = card(episode = "S1 E1", savedAtMs = nowMs - min)
        val e2 = card(episode = "S1 E2")
        assertEquals(listOf(e2), mergeCard(listOf(e1), e2))
    }

    @Test
    fun nullKeepsTheOldCards() {
        val old = card()
        assertEquals(listOf(old), mergeCard(listOf(old), null))
    }

    @Test
    fun sameCardAgainKeepsItsTime() {
        // A show left paused in its app is saved again on every Home press.
        val old = card(savedAtMs = nowMs - 5 * min)
        assertEquals(listOf(old), mergeCard(listOf(old), old.copy(savedAtMs = nowMs)))
    }

    @Test
    fun movedOnCardGetsTheNewTime() {
        val old = card(positionMs = 13 * min, savedAtMs = nowMs - 5 * min)
        val new = old.copy(positionMs = 18 * min, savedAtMs = nowMs)
        assertEquals(listOf(new), mergeCard(listOf(old), new))
    }

    @Test
    fun pausedShowDoesNotJumpAhead() {
        // Crave paused and left open, then YouTube watched, then Home pressed again.
        val crave = card(savedAtMs = nowMs - 10 * min)
        val youTube = card(pkg = YOUTUBE, savedAtMs = nowMs - 2 * min)
        val cards = mergeCard(listOf(crave, youTube), crave.copy(savedAtMs = nowMs))
        assertEquals(listOf(youTube, crave), continueRow(cards, rowApps, nowMs))
    }

    @Test
    fun twelveIsTheLimit() {
        val apps = listOf(CRAVE, YOUTUBE, PARAMOUNT)
        val thirteen = (0 until 13).map { i -> card(pkg = apps[i % 3], title = "Show $i", savedAtMs = nowMs - (13 - i) * min) }
        val cards = thirteen.fold(emptyList<WatchCard>()) { acc, c -> mergeCard(acc, c) }
        assertEquals(12, cards.size)
        assertEquals(false, cards.any { it.title == "Show 0" }) // The oldest went.
        assertEquals("Show 12", cards.first().title)
    }

    @Test
    fun perAppLimit() {
        val crave = card(savedAtMs = nowMs - 100 * min)
        val videos = (0 until 7).map { i -> card(pkg = YOUTUBE, title = "Video $i", savedAtMs = nowMs - (7 - i) * min) }
        val cards = videos.fold(listOf(crave)) { acc, c -> mergeCard(acc, c) }
        assertEquals(6, cards.count { it.packageName == YOUTUBE })
        assertEquals(false, cards.any { it.title == "Video 0" })
        assertEquals(true, cards.contains(crave)) // An older show from another app stays.
    }

    @Test
    fun markersDontCount() {
        val removed = (0 until 12).map { i -> card(title = "Gone $i", savedAtMs = nowMs - 50 * min).copy(removedAtMs = nowMs - min) }
        val cards = mergeCard(removed, card(pkg = YOUTUBE, title = "New"))
        assertEquals(13, cards.size)
        assertEquals("New", continueRow(cards, rowApps, nowMs).single().title)
    }

    @Test
    fun showKeyIsAppAndTitle() {
        assertEquals("ca.bellmedia.cravetv/Silver Coast", card().key)
    }

    // --- Removing a show ------------------------------------------------------

    @Test
    fun removingMakesAMarker() {
        val a = card(title = "Show A")
        val b = card(title = "Show B")
        assertEquals(listOf(a.copy(removedAtMs = nowMs), b), removeShow(listOf(a, b), a.key, nowMs))
    }

    @Test
    fun removingSomethingNotThereChangesNothing() {
        val a = card()
        assertEquals(listOf(a), removeShow(listOf(a), "nothing/here", nowMs))
    }

    @Test
    fun removedShowStaysOut() {
        val paused = playing(CRAVE, crave, positionMs = 16 * min).copy(playing = false)
        assertEquals(false, acceptSave(appRemovedAtMs = null, showRemovedAtMs = removedAt, now = paused))
    }

    @Test
    fun removedShowBackWhenPlayedAgain() {
        assertEquals(
            true,
            acceptSave(appRemovedAtMs = null, showRemovedAtMs = removedAt, now = playing(CRAVE, crave, positionMs = 17 * min)),
        )
    }

    @Test
    fun removingOneShowLeavesTheAppsOthers() {
        // Crave's "Silver Coast" removed; "Another Show" paused in Crave still saves (no marker for it).
        val cards = removeShow(listOf(card()), card().key, nowMs)
        val other = card(title = "Another Show")
        val marker = cards.firstOrNull { it.key == other.key }?.removedAtMs
        assertNull(marker)
        val paused = playing(CRAVE, mapOf(TITLE to "Another Show"), positionMs = 16 * min).copy(playing = false)
        assertEquals(true, acceptSave(appRemovedAtMs = null, showRemovedAtMs = marker, now = paused))
    }

    @Test
    fun oldAppWideRemovalStillHolds() {
        // An app-wide marker from the older one-show-per-app format: nothing from that app until something plays after it.
        val paused = playing(CRAVE, crave, positionMs = 16 * min).copy(playing = false)
        assertEquals(false, acceptSave(appRemovedAtMs = removedAt, showRemovedAtMs = null, now = paused))
        assertEquals(
            true,
            acceptSave(appRemovedAtMs = removedAt, showRemovedAtMs = null, now = playing(CRAVE, crave, positionMs = 17 * min)),
        )
    }

    @Test
    fun playedAgainReplacesTheMarker() {
        val marker = card(savedAtMs = nowMs - 10 * min).copy(removedAtMs = nowMs - 5 * min)
        val back = card(positionMs = 20 * min)
        assertEquals(listOf(back), mergeCard(listOf(marker), back))
    }

    @Test
    fun markersDropAfter30Days() {
        val keptMarker = card(title = "A", savedAtMs = nowMs - 40 * day).copy(removedAtMs = nowMs - 30 * day)
        val goneMarker = card(title = "B", savedAtMs = nowMs - 40 * day).copy(removedAtMs = nowMs - 30 * day - min)
        val goneCard = card(title = "C", savedAtMs = nowMs - 30 * day - min)
        val keptCard = card(title = "D", savedAtMs = nowMs - 30 * day)
        assertEquals(listOf(keptMarker, keptCard), dropOld(listOf(keptMarker, goneMarker, goneCard, keptCard), nowMs))
    }

    // --- The row -------------------------------------------------------------

    private val rowApps = setOf(CRAVE, YOUTUBE, PARAMOUNT)

    @Test
    fun newestFirst() {
        val crave = card(savedAtMs = nowMs - 2 * min)
        val youTube = card(pkg = YOUTUBE, savedAtMs = nowMs - min)
        assertEquals(listOf(youTube, crave), continueRow(listOf(crave, youTube), rowApps, nowMs))
    }

    @Test
    fun oldCardsDropOffAfter30Days() {
        val kept = card(savedAtMs = nowMs - 30 * day)
        val gone = card(pkg = YOUTUBE, savedAtMs = nowMs - 30 * day - min)
        assertEquals(listOf(kept), continueRow(listOf(kept, gone), rowApps, nowMs))
    }

    @Test
    fun cardsOnlyForRowApps() {
        val usb = card(pkg = "com.vt.tv.mediacenter")
        assertEquals(emptyList<WatchCard>(), continueRow(listOf(usb), rowApps, nowMs))
    }

    @Test
    fun rowHasNoMarkers() {
        val shown = card(title = "Shown")
        val removed = card(title = "Removed").copy(removedAtMs = nowMs)
        assertEquals(listOf(shown), continueRow(listOf(shown, removed), rowApps, nowMs))
    }

    @Test
    fun rowNewestFirstAcrossApps() {
        val a = card(title = "A", savedAtMs = nowMs - 3 * min)
        val b = card(pkg = YOUTUBE, title = "B", savedAtMs = nowMs - min)
        val c = card(title = "C", savedAtMs = nowMs - 2 * min)
        assertEquals(listOf(b, c, a), continueRow(listOf(a, b, c), rowApps, nowMs))
    }

    @Test
    fun rowAtMostTwelve() {
        val many = (0 until 15).map { card(title = "Show $it", savedAtMs = nowMs - it * min) }
        assertEquals(12, continueRow(many, rowApps, nowMs).size)
    }

    @Test
    fun sampleShowsNewestFirstAndDistinct() {
        val samples = sampleShows(12, listOf(CRAVE, YOUTUBE, PARAMOUNT), nowMs)
        assertEquals(12, samples.size)
        assertEquals(12, samples.map { it.key }.toSet().size)
        assertEquals(samples.sortedByDescending { it.savedAtMs }, samples)
        assertEquals("Sample show 1", samples.first().title)
        assertEquals(true, samples.any { it.title.length > 60 }) // One long title, for the two-line test.
        assertEquals(listOf(CRAVE, YOUTUBE, PARAMOUNT), samples.take(3).map { it.packageName })
        assertEquals(emptyList<WatchCard>(), sampleShows(0, listOf(CRAVE), nowMs))
        assertEquals(emptyList<WatchCard>(), sampleShows(5, emptyList(), nowMs))
    }

    // --- Words ---------------------------------------------------------------

    @Test
    fun progressTexts() {
        assertEquals("Just started", progressText(40_000))
        assertEquals("13 min in", progressText(13 * min))
        assertEquals("1 hr 5 min in", progressText(65 * min))
    }

    @Test
    fun watchTimeForms() {
        assertEquals("31 min left", watchTime(card(positionMs = 17 * min, durationMs = 48 * min)))
        assertEquals("13 min in", watchTime(card(durationMs = null)))
        assertEquals("Just started", watchTime(card(positionMs = 40_000, durationMs = null)))
    }

    @Test
    fun showMetaKeepsTheWholeEpisode() {
        // The top area has room: the episode's name stays, and the time is the same words the card's bar stands for.
        assertEquals(
            "S1 E1 · The Old Pier, Part 1 · 15 min in",
            showMeta(
                card(
                    episode = "S1 E1 · The Old Pier, Part 1",
                    positionMs =
                        15 * min,
                    durationMs = null,
                ),
            ),
        )
        assertEquals("S2 E5 · 31 min left", showMeta(card(episode = "S2 E5", positionMs = 17 * min, durationMs = 48 * min)))
        assertEquals("31 min left", showMeta(card(episode = null, positionMs = 17 * min, durationMs = 48 * min)))
    }

    @Test
    fun finishedShowsFullBar() {
        val done = card(episode = null, positionMs = 47 * min, durationMs = 48 * min)
        assertEquals("Finished", watchTime(done))
        assertEquals(1f, progressFraction(done)!!, 0f)
    }

    @Test
    fun progressFractions() {
        assertEquals(0.27f, progressFraction(card())!!, 0.01f)
        assertNull(progressFraction(card(durationMs = null)))
    }

    // --- Saved file ----------------------------------------------------------

    @Test
    fun versionTwoRoundTrip() {
        val plain = card()
        val odd = WatchCard(PARAMOUNT, "Tab\there\nand a break", null, 5 * min, null, nowMs, "id\t1", null)
        val back = decodeCards(encodeCards(listOf(plain, odd)))
        assertEquals(listOf(plain, odd.copy(title = "Tab here and a break", mediaId = "id 1")), back)
        assertEquals("continue-watching v2", encodeCards(listOf(plain)).lines().first())
    }

    @Test
    fun removedMarkerRoundTrip() {
        val marker = card().copy(removedAtMs = nowMs - min)
        assertEquals(listOf(marker), decodeCards(encodeCards(listOf(marker))))
    }

    @Test
    fun readsVersionOneFile() {
        // A real v1 file (tab-separated: app, title, episode, position, length, saved, media ID, media URI).
        val v1 = "continue-watching v1\n" +
            "ca.bellmedia.cravetv\tSilver Coast\tS1 E1 · The Old Pier, Part 1\t660000\t\t1800000000000\t\t\n" +
            "com.google.android.youtube.tv\tThe Quiet Season Of Northern Ferries\tIron Notebook\t420000\t2640000\t1800000060000\t\t\n" +
            "com.cbs.ca\tHarbor Watch: Family Reunions\tSunday Supper\t1380000\t2520000\t1799999940000\t\tAbC123xyz"
        assertEquals(
            listOf(
                WatchCard(CRAVE, "Silver Coast", "S1 E1 · The Old Pier, Part 1", 660_000, null, 1_800_000_000_000, null, null),
                WatchCard(
                    YOUTUBE,
                    "The Quiet Season Of Northern Ferries",
                    "Iron Notebook",
                    420_000,
                    2_640_000,
                    1_800_000_060_000,
                    null,
                    null,
                ),
                WatchCard(
                    PARAMOUNT,
                    "Harbor Watch: Family Reunions",
                    "Sunday Supper",
                    1_380_000,
                    2_520_000,
                    1_799_999_940_000,
                    null,
                    "AbC123xyz",
                ),
            ),
            decodeCards(v1),
        )
    }

    @Test
    fun brokenLinesSkipped() {
        val good = card()
        val text = encodeCards(listOf(good)) + "\nnot\ta\tcard\n" + "x\t\t\tNaN\t\t1\t\t\t\n" + "x\ty\t\t1\t\t1\t\t\tnot-a-time\n"
        assertEquals(listOf(good), decodeCards(text))
    }

    @Test
    fun unknownVersionGivesNoShows() {
        assertEquals(
            emptyList<WatchCard>(),
            decodeCards("continue-watching v9\n" + encodeCards(listOf(card())).lines().drop(1).joinToString("\n")),
        )
        assertEquals(emptyList<WatchCard>(), decodeCards("something else entirely"))
    }

    @Test
    fun emptyFileGivesNoShows() {
        assertEquals(emptyList<WatchCard>(), decodeCards(""))
    }

    // --- Pictures --------------------------------------------------------------

    @Test
    fun pictureNameUnchanged() {
        // The app, then the title's hash; unchanged since v1, so pictures saved earlier carry over.
        assertEquals("ca.bellmedia.cravetv-${"Silver Coast".hashCode().toUInt()}.jpg", pictureName(card()))
    }

    @Test
    fun picturesKeptForShowsOnly() {
        val shown = card(title = "Shown")
        val removed = card(title = "Removed").copy(removedAtMs = nowMs)
        assertEquals(setOf(pictureName(shown)), picturesToKeep(listOf(shown, removed)))
    }

    private companion object {
        const val CRAVE = "ca.bellmedia.cravetv"
        const val YOUTUBE = "com.google.android.youtube.tv"
        const val PARAMOUNT = "com.cbs.ca"
        const val PRIME = "com.amazon.amazonvideo.livingroom"
        const val APPLE_TV = "com.apple.atve.androidtv.appletv"
        const val TITLE = "android.media.metadata.TITLE"
        const val DISPLAY_TITLE = "android.media.metadata.DISPLAY_TITLE"
        const val DISPLAY_SUBTITLE = "android.media.metadata.DISPLAY_SUBTITLE"
        const val ARTIST = "android.media.metadata.ARTIST"
        const val ALBUM = "android.media.metadata.ALBUM"
        const val DURATION = "android.media.metadata.DURATION"
        const val MEDIA_ID = "android.media.metadata.MEDIA_ID"
        const val MEDIA_URI = "android.media.metadata.MEDIA_URI"
    }

    private val removedAt = nowMs - 5 * min
    private val crave = mapOf("android.media.metadata.TITLE" to "Silver Coast - S1 E1 The Old Pier, Part 1")

    @Test
    fun removedShowIgnoredWhilePaused() {
        val paused = playing(CRAVE, crave, positionMs = 16 * min).copy(playing = false)
        assertEquals(false, acceptSave(appRemovedAtMs = null, showRemovedAtMs = removedAt, now = paused))
    }

    @Test
    fun playingAgainBringsItBack() {
        assertEquals(
            true,
            acceptSave(appRemovedAtMs = null, showRemovedAtMs = removedAt, now = playing(CRAVE, crave, positionMs = 17 * min)),
        )
    }

    @Test
    fun neverRemovedAlwaysSaves() {
        val paused = playing(CRAVE, crave, positionMs = 16 * min).copy(playing = false)
        assertEquals(true, acceptSave(appRemovedAtMs = null, showRemovedAtMs = null, now = paused))
    }

    @Test
    fun playingFromBeforeTheRemovalIgnored() {
        // An app still saying "playing" since before its show was removed (it never paused).
        assertEquals(
            false,
            acceptSave(
                appRemovedAtMs = null,
                showRemovedAtMs = removedAt,
                now = playing(
                    CRAVE,
                    crave,
                    positionMs = 16 * min,
                    playUpdatedAtMs =
                        removedAt - min,
                ),
            ),
        )
    }

    @Test
    fun playingWithUnknownTimeIgnored() {
        assertEquals(
            false,
            acceptSave(
                appRemovedAtMs = null,
                showRemovedAtMs = removedAt,
                now = playing(CRAVE, crave, positionMs = 16 * min, playUpdatedAtMs = 0),
            ),
        )
    }
}
