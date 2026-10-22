package tv.livingroom.home.weather

import kotlin.math.roundToInt
import org.json.JSONObject
import tv.livingroom.home.HomePlace

/** [HomePlace]'s weather as Open-Meteo last reported it, and when it was fetched (epoch ms). */
data class WeatherNow(val tempC: Double, val code: Int, val isDay: Boolean, val fetchedAtMs: Long)

/** Open-Meteo (free, no account, no key). Only [HomePlace]'s fixed coordinates are sent. */
val WEATHER_URL =
    "https://api.open-meteo.com/v1/forecast?latitude=${HomePlace.LATITUDE}&longitude=${HomePlace.LONGITUDE}" +
        "&current=temperature_2m,weather_code,is_day&timezone=${HomePlace.zoneForUrl}"

/** The weather in Open-Meteo's [json] reply, or null if anything is missing or unreadable. */
fun parseWeather(json: String, fetchedAtMs: Long): WeatherNow? = try {
    val current = JSONObject(json).getJSONObject("current")
    WeatherNow(
        tempC = current.getDouble("temperature_2m"),
        code = current.getInt("weather_code"),
        isDay = current.getInt("is_day") == 1,
        fetchedAtMs = fetchedAtMs,
    )
} catch (ignored: Exception) {
    null
}

/** A few words for a WMO weather [code], or null for an unknown one. */
fun weatherWords(code: Int): String? = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Cloudy"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61, 63 -> "Rain"
    65 -> "Heavy rain"
    66, 67 -> "Freezing rain"
    71, 73, 77 -> "Snow"
    75 -> "Heavy snow"
    80, 81 -> "Showers"
    82 -> "Heavy showers"
    85, 86 -> "Snow showers"
    95, 96, 99 -> "Thunderstorm"
    else -> null
}

/** A symbol for [code]: sun or moon when clear, else cloud, umbrella, snowflake or lightning. */
fun weatherSymbol(code: Int, isDay: Boolean): String? = when (code) {
    0, 1 -> if (isDay) "☀" else "☾"
    2, 3, 45, 48 -> "☁"
    in 51..67, in 80..82 -> "☂"
    in 71..77, 85, 86 -> "❄"
    95, 96, 99 -> "⚡"
    else -> null
}

/**
 * The corner's weather: "☁ 8° Partly cloudy". Null when it's switched off
 * ([on]), never fetched, or older than 3 hours. [symbols] off drops the symbol.
 */
fun cornerWeather(weather: WeatherNow?, nowMs: Long, on: Boolean, symbols: Boolean = true): String? {
    if (!on || weather == null || nowMs - weather.fetchedAtMs > STALE_MS) return null
    val degrees = weather.tempC.roundToInt() // A whole number, so -0.4 reads "0°", never "-0°".
    return listOfNotNull(
        weatherSymbol(weather.code, weather.isDay)?.takeIf { symbols },
        "$degrees°",
        weatherWords(weather.code),
    ).joinToString(" ")
}

/** Fetch again if the last good reading is over 30 minutes old, but don't retry more than every 5 minutes. */
fun weatherDue(lastOkMs: Long?, lastTryMs: Long?, nowMs: Long): Boolean {
    if (lastTryMs != null && nowMs - lastTryMs < RETRY_MS) return false
    return lastOkMs == null || nowMs - lastOkMs > REFRESH_MS
}

private const val STALE_MS = 3 * 60 * 60_000L
private const val REFRESH_MS = 30 * 60_000L
private const val RETRY_MS = 5 * 60_000L
