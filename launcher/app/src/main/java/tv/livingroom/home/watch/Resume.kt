package tv.livingroom.home.watch

import java.net.URLEncoder

/** The four ways back to a show, best first. */
enum class ResumeLevel(val number: Int, val words: String) {
    EXACT(1, "resume exactly"),
    DIRECT_LINK(2, "open the show directly"),
    SEARCH(3, "search for it"),
    OPEN_APP(4, "open the app"),
}

/** One try at getting back to a show. */
sealed interface ResumeStep {
    val level: ResumeLevel

    /** The app's session for this show is still open: press play and bring the app up. */
    data object PlayLive : ResumeStep {
        override val level = ResumeLevel.EXACT
    }
    data class OpenLink(val uri: String) : ResumeStep {
        override val level get() = ResumeLevel.DIRECT_LINK
    }
    data class Search(val uri: String) : ResumeStep {
        override val level get() = ResumeLevel.SEARCH
    }
    data object OpenApp : ResumeStep {
        override val level = ResumeLevel.OPEN_APP
    }
}

/** How an app can be sent to a show: a direct [link] and a [search] link, each null when the card lacks what it needs. */
data class ResumeRecipe(val link: ((WatchCard) -> String?)? = null, val search: ((WatchCard) -> String?)? = null)

/** Only what was seen working on this TV; apps not listed fall back to opening the app. */
val RESUME_RECIPES: Map<String, ResumeRecipe> = mapOf(
    // Opens the episode at the saved position, after Paramount+ asks who's watching.
    "com.cbs.ca" to ResumeRecipe(link = { card -> card.mediaUri?.let { "https://www.paramountplus.com/shows/video/$it/" } }),
    // Results with the video first and selected; one OK plays it from where it left off.
    "com.google.android.youtube.tv" to ResumeRecipe(
        search = { card ->
            "https://www.youtube.com/results?search_query=" +
                linkText(listOfNotNull(card.title, card.episode).joinToString(" "))
        },
    ),
)

/**
 * The tries for [card], best first: the live session if it's still showing the
 * same show ([liveTitle]), then [recipe]'s link and search, and always the app itself last.
 */
fun resumeSteps(card: WatchCard, liveTitle: String?, recipe: ResumeRecipe?): List<ResumeStep> = buildList {
    if (liveTitle == card.title) add(ResumeStep.PlayLive)
    recipe?.link?.invoke(card)?.let { add(ResumeStep.OpenLink(it)) }
    recipe?.search?.invoke(card)?.let { add(ResumeStep.Search(it)) }
    add(ResumeStep.OpenApp)
}

/** Text for a link, spaces as %20 (YouTube shows a "+" as a plus sign). */
private fun linkText(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
