package tv.livingroom.home.homekey

/** How long after a Home press Google's launcher coming to the front is treated as unwanted. */
const val HOME_GUARD_MS = 5_000L

/** The most times one Home press re-opens Living Room, so it can never fight the launcher in a loop. */
const val HOME_GUARD_MAX_REOPENS = 3

/**
 * Decides whether Living Room should be opened again because the system's home (Google's
 * launcher) came to the front on its own after a Home press.
 *
 * Consuming Home does not stop the system from starting the default launcher about 200 ms after
 * the key goes down. When that launcher has been in the background for a while (after a long
 * session in another app, or under the screensaver), it then starts its own home activity a
 * second time, 0.4–0.8 s later, from its own process. That second start lands after Living
 * Room's and covers it. The guard turns the one-shot open into "Living Room ends up in front":
 * for [windowMs] after an open, the launcher's home window gaining focus re-opens Living Room.
 *
 * Any key going down ends the guard, because then the user chose what comes next (Explore opens
 * Google's home on purpose; a held Home opens its side panel).
 */
class HomeGuard(private val windowMs: Long = HOME_GUARD_MS, private val maxReopens: Int = HOME_GUARD_MAX_REOPENS) {
    /** When Living Room was last opened by Home, while the guard is on; null when off. */
    var openedAt: Long? = null
        private set
    private var reopens = 0

    /** Living Room was just opened by a Home press, at [now] (ms, monotonic). */
    fun opened(now: Long) {
        openedAt = now
        reopens = 0
    }

    /** A key went down, so whatever opens next was the user's choice. */
    fun keyPressed() {
        openedAt = null
    }

    /** Google's home window came to the front at [now]: true means open Living Room again (and counts it). */
    fun shouldReopen(now: Long): Boolean {
        val since = openedAt ?: return false
        if (now - since > windowMs || reopens >= maxReopens) return false
        reopens++
        return true
    }
}
