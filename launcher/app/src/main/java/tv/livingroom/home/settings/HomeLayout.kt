package tv.livingroom.home.settings

/**
 * Which home screen shows: Mountains and dock, or Living sky with Continue
 * watching. [key] is how it's saved in [HomePrefs].
 */
enum class HomeLayout(val key: String) {
    MOUNTAINS("mountains"),
    LIVING_SKY("living_sky"),
    ;

    /** The one the setting switches to. */
    val other: HomeLayout get() = if (this == MOUNTAINS) LIVING_SKY else MOUNTAINS
}

/** What a fresh install shows until a choice is made in Settings. */
val DEFAULT_LAYOUT = HomeLayout.MOUNTAINS

/** The saved choice, or [DEFAULT_LAYOUT] when there's none (or it's one this build doesn't know). */
fun homeLayoutFrom(saved: String?): HomeLayout = HomeLayout.entries.firstOrNull { it.key == saved } ?: DEFAULT_LAYOUT
