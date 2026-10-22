package tv.livingroom.home.testswitch

import java.time.LocalTime
import tv.livingroom.home.settings.HomeLayout

/**
 * What a test switch asks the home screen for, for one opening: the time ([hour] or [at], which stands
 * still), a weather code for the sky, a number of placeholder shows, or one of the two layouts.
 * Null means "as normal".
 */
data class HomeOverrides(
    val hour: Int? = null,
    val at: LocalTime? = null,
    val weatherCode: Int? = null,
    val sampleShows: Int? = null,
    val layout: HomeLayout? = null,
)

/**
 * What a test switch asks the screensaver for: a preview as if it were [at] (null: now), with
 * [weatherCode] in the sky and the late-night turn-off after [offAfterMin]. With [nextOnly], the
 * next real screensaver gets the time and turn-off instead of a preview opening.
 */
data class SaverOverrides(val at: LocalTime?, val weatherCode: Int? = null, val offAfterMin: Int? = null, val nextOnly: Boolean = false)

data class TestSwitches(val home: HomeOverrides?, val saver: SaverOverrides?)

const val EXTRA_HOUR = "tv.livingroom.home.HOUR"
const val EXTRA_AT = "tv.livingroom.home.AT"
const val EXTRA_WEATHER = "tv.livingroom.home.WEATHER"
const val EXTRA_SAMPLE_SHOWS = "tv.livingroom.home.SAMPLE_SHOWS"
const val EXTRA_LAYOUT = "tv.livingroom.home.LAYOUT"
const val EXTRA_SAVER_AT = "tv.livingroom.home.SAVER_AT"
const val EXTRA_SAVER_WEATHER = "tv.livingroom.home.SAVER_WEATHER"
const val EXTRA_SAVER_OFF_MIN = "tv.livingroom.home.SAVER_OFF_MIN"
const val EXTRA_SAVER_NEXT = "tv.livingroom.home.SAVER_NEXT"

/**
 * Reads the switches from an intent's [extras] (`--ei` gives an Int, `--es` a String, `--ez` a
 * Boolean). Values out of range or of the wrong type are ignored. The screensaver's switches apply
 * only when [EXTRA_SAVER_AT] is given, even if its time can't be read (the preview then shows now).
 */
fun parseTestSwitches(extras: Map<String, Any?>): TestSwitches {
    fun int(key: String) = extras[key] as? Int
    fun time(key: String) = (extras[key] as? String)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

    val home = HomeOverrides(
        hour = int(EXTRA_HOUR)?.takeIf { it in 0..23 },
        at = time(EXTRA_AT),
        weatherCode = int(EXTRA_WEATHER)?.takeIf { it >= 0 },
        sampleShows = int(EXTRA_SAMPLE_SHOWS)?.takeIf { it >= 0 },
        layout = layoutSwitch(extras[EXTRA_LAYOUT] as? String),
    )
    val saver = if (EXTRA_SAVER_AT in extras) {
        SaverOverrides(
            at = time(EXTRA_SAVER_AT),
            weatherCode = int(EXTRA_SAVER_WEATHER)?.takeIf { it >= 0 },
            offAfterMin = int(EXTRA_SAVER_OFF_MIN)?.takeIf { it > 0 },
            nextOnly = extras[EXTRA_SAVER_NEXT] == true,
        )
    } else {
        null
    }
    return TestSwitches(home.takeIf { it != HomeOverrides() }, saver)
}

/** [EXTRA_LAYOUT]'s value: `dock` or `sky`; anything else is no switch. */
fun layoutSwitch(value: String?): HomeLayout? = when (value) {
    "dock" -> HomeLayout.MOUNTAINS
    "sky" -> HomeLayout.LIVING_SKY
    else -> null
}

/**
 * Hands [HomeOverrides] from [tv.livingroom.home.TestSwitchActivity] to the home screen's next
 * opening. It lives in the app's memory, so only that activity (which only adb can open) sets it.
 */
object HomeTestSwitch {
    @Volatile private var pending: HomeOverrides? = null

    fun arm(overrides: HomeOverrides) {
        pending = overrides
    }

    /** The overrides for this opening, once; null when no switch was given. */
    fun take(): HomeOverrides? = pending.also { pending = null }
}
