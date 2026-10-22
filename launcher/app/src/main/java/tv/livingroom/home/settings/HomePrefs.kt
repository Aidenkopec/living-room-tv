package tv.livingroom.home.settings

import android.content.Context
import androidx.core.content.edit
import java.time.LocalDate
import tv.livingroom.home.apps.RowSettings
import tv.livingroom.home.screensaver.SaverSettings
import tv.livingroom.home.screensaver.shownToday

/** The user's home screen choices, kept in Living Room's own preferences file on the TV. */
object HomePrefs {
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("home", Context.MODE_PRIVATE)

    /** Which home screen shows; [DEFAULT_LAYOUT] until one is picked. */
    fun homeLayout(context: Context): HomeLayout = homeLayoutFrom(prefs(context).getString(HOME_LAYOUT, null))

    fun setHomeLayout(context: Context, layout: HomeLayout) {
        prefs(context).edit { putString(HOME_LAYOUT, layout.key) }
    }

    fun weatherOn(context: Context): Boolean = prefs(context).getBoolean(WEATHER_ON, true)

    fun setWeatherOn(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(WEATHER_ON, on) }
    }

    /** The app row's order and hidden tiles; the defaults until a tile is moved or hidden. */
    fun rowSettings(context: Context): RowSettings {
        val prefs = prefs(context)
        val defaults = RowSettings()
        return RowSettings(
            order = prefs.getString(ROW_ORDER_KEY, null)?.split('\n')?.filter { it.isNotEmpty() } ?: defaults.order,
            hidden = prefs.getStringSet(ROW_HIDDEN_KEY, null)?.toSet() ?: defaults.hidden,
        )
    }

    fun saveRowSettings(context: Context, settings: RowSettings) {
        prefs(context).edit {
            putString(ROW_ORDER_KEY, settings.order.joinToString("\n"))
            putStringSet(ROW_HIDDEN_KEY, settings.hidden)
        }
    }

    /** When [packageName]'s show was removed from Continue watching, or null. */
    fun removedAt(context: Context, packageName: String): Long? = prefs(context).getLong(REMOVED_PREFIX + packageName, 0L).takeIf { it > 0 }

    fun setRemovedAt(context: Context, packageName: String, millis: Long?) {
        prefs(context).edit { if (millis == null) remove(REMOVED_PREFIX + packageName) else putLong(REMOVED_PREFIX + packageName, millis) }
    }

    /** The screensaver's two switches (both on unless turned off). */
    fun saverSettings(context: Context): SaverSettings {
        val prefs = prefs(context)
        return SaverSettings(skyOn = prefs.getBoolean(SAVER_SKY_ON, true), lateNightOn = prefs.getBoolean(SAVER_LATE_NIGHT_ON, true))
    }

    fun setSaverSettings(context: Context, settings: SaverSettings) {
        prefs(context).edit {
            putBoolean(SAVER_SKY_ON, settings.skyOn)
            putBoolean(SAVER_LATE_NIGHT_ON, settings.lateNightOn)
        }
    }

    /** The screensaver photos shown so far [today], oldest first. */
    fun saverShown(context: Context, today: LocalDate): List<Int> {
        val prefs = prefs(context)
        val savedDate = prefs.getString(SAVER_SHOWN_DATE, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val saved = prefs.getString(SAVER_SHOWN, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }
        return shownToday(savedDate, saved, today)
    }

    fun addSaverShown(context: Context, today: LocalDate, id: Int) {
        val shown = saverShown(context, today) + id
        prefs(context).edit {
            putString(SAVER_SHOWN_DATE, today.toString())
            putString(SAVER_SHOWN, shown.joinToString(","))
        }
    }

    private const val HOME_LAYOUT = "home_layout"
    private const val WEATHER_ON = "weather_on"
    private const val SAVER_SKY_ON = "saver_sky_on"
    private const val SAVER_LATE_NIGHT_ON = "saver_late_night_on"
    private const val SAVER_SHOWN_DATE = "saver_shown_date"
    private const val SAVER_SHOWN = "saver_shown"
    private const val ROW_ORDER_KEY = "row_order"
    private const val ROW_HIDDEN_KEY = "row_hidden"
    private const val REMOVED_PREFIX = "removed_show."
}
