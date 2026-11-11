package tv.livingroom.home.testswitch

import java.time.LocalDate
import java.time.LocalTime
import tv.livingroom.home.christmas.AskedMoment
import tv.livingroom.home.christmas.Dog
import tv.livingroom.home.christmas.Moment
import tv.livingroom.home.settings.HomeLayout

/**
 * What a test switch asks the home screen for, for one opening: the time ([hour] or [at], which stands
 * still), a pretend [date], a weather code for the sky, a number of placeholder shows, one of the two
 * layouts, the Christmas theme on or off, or extra moving pieces on the Christmas village ([sprites])
 * for measuring what the TV can draw. Null means "as normal".
 */
data class HomeOverrides(
    val hour: Int? = null,
    val at: LocalTime? = null,
    val weatherCode: Int? = null,
    val sampleShows: Int? = null,
    val layout: HomeLayout? = null,
    val christmas: Boolean? = null,
    val date: LocalDate? = null,
    val sprites: Int? = null,
)

/**
 * What a test switch asks the screensaver for: a preview as if it were [at] (null: now), with
 * [weatherCode] in the sky, the late-night turn-off after [offAfterMin], and Fireside (the Christmas
 * screensaver) on or off whatever the setting says ([christmas]; null: as set), Santa coming [santa] seconds after Fireside
 * starts, or a dog [moment] a few seconds in. With [nextOnly], the next real screensaver gets them instead of a preview opening.
 */
data class SaverOverrides(
    val at: LocalTime?,
    val weatherCode: Int? = null,
    val offAfterMin: Int? = null,
    val nextOnly: Boolean = false,
    val christmas: Boolean? = null,
    val santa: Int? = null,
    val moment: AskedMoment? = null,
)

data class TestSwitches(val home: HomeOverrides?, val saver: SaverOverrides?)

const val EXTRA_HOUR = "tv.livingroom.home.HOUR"
const val EXTRA_AT = "tv.livingroom.home.AT"
const val EXTRA_WEATHER = "tv.livingroom.home.WEATHER"
const val EXTRA_SAMPLE_SHOWS = "tv.livingroom.home.SAMPLE_SHOWS"
const val EXTRA_LAYOUT = "tv.livingroom.home.LAYOUT"
const val EXTRA_CHRISTMAS = "tv.livingroom.home.CHRISTMAS"
const val EXTRA_DATE = "tv.livingroom.home.DATE"
const val EXTRA_SPRITES = "tv.livingroom.home.SPRITES"
const val EXTRA_SAVER_AT = "tv.livingroom.home.SAVER_AT"
const val EXTRA_SAVER_WEATHER = "tv.livingroom.home.SAVER_WEATHER"
const val EXTRA_SAVER_OFF_MIN = "tv.livingroom.home.SAVER_OFF_MIN"
const val EXTRA_SAVER_NEXT = "tv.livingroom.home.SAVER_NEXT"
const val EXTRA_SAVER_CHRISTMAS = "tv.livingroom.home.SAVER_CHRISTMAS"
const val EXTRA_SAVER_SANTA = "tv.livingroom.home.SAVER_SANTA"
const val EXTRA_SAVER_MOMENT = "tv.livingroom.home.SAVER_MOMENT"
const val EXTRA_SAVER_DOG = "tv.livingroom.home.SAVER_DOG"

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
        christmas = onOffSwitch(extras[EXTRA_CHRISTMAS] as? String),
        date = (extras[EXTRA_DATE] as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        sprites = int(EXTRA_SPRITES)?.takeIf { it in 0..MAX_TEST_SPRITES },
    )
    val saver = if (EXTRA_SAVER_AT in extras) {
        SaverOverrides(
            at = time(EXTRA_SAVER_AT),
            weatherCode = int(EXTRA_SAVER_WEATHER)?.takeIf { it >= 0 },
            offAfterMin = int(EXTRA_SAVER_OFF_MIN)?.takeIf { it > 0 },
            nextOnly = extras[EXTRA_SAVER_NEXT] == true,
            christmas = onOffSwitch(extras[EXTRA_SAVER_CHRISTMAS] as? String),
            santa = int(EXTRA_SAVER_SANTA)?.takeIf { it in 0..MAX_SANTA_SECONDS },
            moment = momentSwitch(extras[EXTRA_SAVER_MOMENT] as? String, extras[EXTRA_SAVER_DOG] as? String),
        )
    } else {
        null
    }
    return TestSwitches(home.takeIf { it != HomeOverrides() }, saver)
}

/** The longest a test switch may ask Santa to wait, seconds: an hour. */
const val MAX_SANTA_SECONDS = 3600

/**
 * [EXTRA_SAVER_MOMENT]'s value: a dog moment's name (`roll`, `zoomies`, `dream`), with [EXTRA_SAVER_DOG]'s `big` or `small`
 * for the dream's dog (else either); anything else is no switch.
 */
fun momentSwitch(name: String?, dog: String?): AskedMoment? {
    val moment = Moment.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
    return AskedMoment(moment, dogSwitch(dog))
}

/** [EXTRA_SAVER_DOG]'s value: `big` or `small`. */
fun dogSwitch(value: String?): Dog? = when (value) {
    "big" -> Dog.BIG
    "small" -> Dog.SMALL
    else -> null
}

/** [moment] as the values [momentSwitch] reads back: its name and its dog's. */
fun momentValues(moment: AskedMoment?): Pair<String?, String?> =
    moment?.moment?.name?.lowercase() to moment?.dog?.let { if (it == Dog.BIG) "big" else "small" }

/** The most extra moving pieces a test switch may ask for. */
const val MAX_TEST_SPRITES = 1000

/** [EXTRA_LAYOUT]'s value: `dock` or `sky`; anything else is no switch. */
fun layoutSwitch(value: String?): HomeLayout? = when (value) {
    "dock" -> HomeLayout.MOUNTAINS
    "sky" -> HomeLayout.LIVING_SKY
    else -> null
}

/** [EXTRA_CHRISTMAS]'s and [EXTRA_SAVER_CHRISTMAS]'s value: `on` or `off`; anything else is no switch. */
fun onOffSwitch(value: String?): Boolean? = when (value) {
    "on" -> true
    "off" -> false
    else -> null
}

/** [on] as an [onOffSwitch] value: `on`, `off`, or null for no switch. */
fun onOffValue(on: Boolean?): String? = on?.let { if (it) "on" else "off" }

/**
 * Whether the Christmas village shows on this opening: what a test switch says, else the [saved] setting.
 * A switch that only names a layout shows that layout, so it turns the village off for that opening.
 */
fun showsChristmas(test: HomeOverrides?, saved: Boolean): Boolean = test?.christmas ?: (test?.layout == null && saved)

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
