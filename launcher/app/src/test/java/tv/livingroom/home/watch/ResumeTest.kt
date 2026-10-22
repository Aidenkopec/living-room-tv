package tv.livingroom.home.watch

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumeTest {
    private fun card(pkg: String, title: String, episode: String? = null, mediaUri: String? = null) =
        WatchCard(pkg, title, episode, 13 * 60_000L, 48 * 60_000L, 0, mediaId = null, mediaUri = mediaUri)

    private val crave = card(CRAVE, "Silver Coast", "S1 E1 · The Old Pier, Part 1")
    private val paramount = card(PARAMOUNT, "Beach House: Summer Trip", "Sunday Funday", mediaUri = "AbC123xyz")
    private val youTube = card(YOUTUBE, "How I Built a Cabin", "Some Channel")
    private val linkAndSearch = ResumeRecipe(link = { c -> c.mediaId?.let { "https://example.com/watch/$it" } }, search = { "s" })

    @Test
    fun liveSessionComesFirst() {
        assertEquals(listOf(ResumeStep.PlayLive, ResumeStep.OpenApp), resumeSteps(crave, liveTitle = "Silver Coast", recipe = null))
    }

    @Test
    fun noLiveSessionSkipsExact() {
        assertEquals(listOf(ResumeStep.OpenApp), resumeSteps(crave, liveTitle = null, recipe = null))
    }

    @Test
    fun liveSessionForAnotherShowSkipsExact() {
        assertEquals(listOf(ResumeStep.OpenApp), resumeSteps(crave, liveTitle = "Another Show", recipe = null))
    }

    @Test
    fun linkNeedsAnId() {
        assertEquals(listOf(ResumeStep.Search("s"), ResumeStep.OpenApp), resumeSteps(crave, liveTitle = null, recipe = linkAndSearch))
    }

    @Test
    fun linkUsesTheId() {
        assertEquals(
            listOf(ResumeStep.OpenLink("https://www.paramountplus.com/shows/video/AbC123xyz/"), ResumeStep.OpenApp),
            resumeSteps(paramount, liveTitle = null, recipe = RESUME_RECIPES[PARAMOUNT]),
        )
    }

    @Test
    fun searchUsesShowTitle() {
        assertEquals(
            listOf(
                ResumeStep.Search("https://www.youtube.com/results?search_query=How%20I%20Built%20a%20Cabin%20Some%20Channel"),
                ResumeStep.OpenApp,
            ),
            resumeSteps(youTube, liveTitle = null, recipe = RESUME_RECIPES[YOUTUBE]),
        )
    }

    @Test
    fun searchEncodesOddCharacters() {
        val odd = card(YOUTUBE, "Tom & Jerry? 100%", episode = null)
        assertEquals(
            ResumeStep.Search("https://www.youtube.com/results?search_query=Tom%20%26%20Jerry%3F%20100%25"),
            resumeSteps(odd, liveTitle = null, recipe = RESUME_RECIPES[YOUTUBE]).first(),
        )
    }

    @Test
    fun alwaysEndsWithOpenApp() {
        for (c in listOf(crave, paramount, youTube)) {
            for (live in listOf(null, c.title)) {
                assertEquals(ResumeStep.OpenApp, resumeSteps(c, live, RESUME_RECIPES[c.packageName]).last())
            }
        }
    }

    @Test
    fun unknownAppJustOpens() {
        val other = card("com.example.tv", "Some Show")
        assertEquals(listOf(ResumeStep.OpenApp), resumeSteps(other, liveTitle = null, recipe = RESUME_RECIPES["com.example.tv"]))
        assertEquals(listOf(ResumeStep.PlayLive, ResumeStep.OpenApp), resumeSteps(other, liveTitle = "Some Show", recipe = null))
    }

    // What each app was seen to reach on this TV.

    @Test
    fun craveTriesItsOpenSessionFirst() {
        assertEquals(listOf(ResumeStep.PlayLive, ResumeStep.OpenApp), resumeSteps(crave, "Silver Coast", RESUME_RECIPES[CRAVE]))
        assertEquals(listOf(ResumeStep.OpenApp), resumeSteps(crave, null, RESUME_RECIPES[CRAVE]))
    }

    @Test
    fun paramountOpensTheEpisode() {
        assertEquals(ResumeLevel.DIRECT_LINK, resumeSteps(paramount, null, RESUME_RECIPES[PARAMOUNT]).first().level)
    }

    @Test
    fun youTubeSearchesForTheVideo() {
        assertEquals(ResumeLevel.SEARCH, resumeSteps(youTube, null, RESUME_RECIPES[YOUTUBE]).first().level)
    }

    // Older cards: the app's live session (if any) is on a newer show.

    @Test
    fun olderYouTubeCardSearches() {
        val steps = resumeSteps(youTube, liveTitle = "A Newer Video", recipe = RESUME_RECIPES[YOUTUBE])
        assertEquals(true, steps.first() is ResumeStep.Search)
        assertEquals(false, ResumeStep.PlayLive in steps)
    }

    @Test
    fun olderParamountCardLinks() {
        val steps = resumeSteps(paramount, liveTitle = "A Newer Show", recipe = RESUME_RECIPES[PARAMOUNT])
        assertEquals(ResumeStep.OpenLink("https://www.paramountplus.com/shows/video/AbC123xyz/"), steps.first())
    }

    @Test
    fun olderCraveCardOpensCrave() {
        assertEquals(listOf(ResumeStep.OpenApp), resumeSteps(crave, liveTitle = "A Newer Show", recipe = RESUME_RECIPES[CRAVE]))
    }

    private companion object {
        const val CRAVE = "ca.bellmedia.cravetv"
        const val PARAMOUNT = "com.cbs.ca"
        const val YOUTUBE = "com.google.android.youtube.tv"
    }
}
