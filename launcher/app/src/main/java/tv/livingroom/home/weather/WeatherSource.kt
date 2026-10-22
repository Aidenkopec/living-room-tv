package tv.livingroom.home.weather

import android.content.Context
import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tv.livingroom.home.TAG
import tv.livingroom.home.settings.HomePrefs

/** Calgary's weather from Open-Meteo, fetched in the background and kept in memory. */
object WeatherSource {
    private val worker = Executors.newSingleThreadExecutor()
    private val state = MutableStateFlow<WeatherNow?>(null)

    @Volatile private var lastTryMs: Long? = null

    private val fetching = AtomicBoolean(false)

    /** The last good reading, or null if there hasn't been one. */
    val now: StateFlow<WeatherNow?> = state

    /** Fetches again if it's time (see [weatherDue]) and the weather is switched on. Returns at once. */
    fun refreshIfDue(context: Context) {
        val nowMs = System.currentTimeMillis()
        if (!HomePrefs.weatherOn(context) || !weatherDue(state.value?.fetchedAtMs, lastTryMs, nowMs)) return
        if (!fetching.compareAndSet(false, true)) return // One fetch at a time.
        lastTryMs = nowMs
        worker.execute {
            try {
                fetch()?.let { state.value = it }
            } finally {
                fetching.set(false)
            }
        }
    }

    private fun fetch(): WeatherNow? {
        val connection = URL(WEATHER_URL).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            val reply = connection.inputStream.bufferedReader().use { it.readText() }
            val weather = parseWeather(reply, System.currentTimeMillis())
            if (weather != null) {
                Log.i(TAG, "Weather: ${weather.tempC}°C, code ${weather.code}")
            } else {
                Log.w(TAG, "Weather: Open-Meteo's reply couldn't be read")
            }
            weather
        } catch (e: Exception) {
            Log.w(TAG, "Weather: couldn't reach Open-Meteo ($e)")
            null
        } finally {
            connection.disconnect()
        }
    }

    private const val TIMEOUT_MS = 10_000
}
