package tv.livingroom.home.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.livingroom.home.livingsky.cornerText

class WeatherTest {
    // A real Open-Meteo reply for Calgary.
    private val reply = """{"latitude":51.054447,"longitude":-114.07635,"generationtime_ms":0.11491775512695312,""" +
        """"utc_offset_seconds":-21600,"timezone":"America/Edmonton","timezone_abbreviation":"GMT-6","elevation":1051.0,""" +
        """"current_units":{"time":"iso8601","interval":"seconds","temperature_2m":"°C","weather_code":"wmo code","is_day":""},""" +
        """"current":{"time":"2026-10-19T18:45","interval":900,"temperature_2m":8.5,"weather_code":2,"is_day":0}}"""
    private val fetched = 1_000_000_000L
    private val minute = 60_000L

    private fun weather(tempC: Double, code: Int, isDay: Boolean = true) = WeatherNow(tempC, code, isDay, fetched)

    @Test
    fun parsesOpenMeteoReply() {
        assertEquals(WeatherNow(8.5, 2, isDay = false, fetchedAtMs = fetched), parseWeather(reply, fetched))
    }

    @Test
    fun badJsonGivesNull() {
        assertNull(parseWeather("<html>Service unavailable</html>", fetched))
        assertNull(parseWeather("", fetched))
    }

    @Test
    fun missingFieldGivesNull() {
        assertNull(parseWeather("""{"current":{"temperature_2m":8.5,"is_day":0}}""", fetched))
        assertNull(parseWeather("""{"error":true,"reason":"Too many requests"}""", fetched))
    }

    @Test
    fun cornerText() {
        assertEquals("☁ 9° Partly cloudy", cornerWeather(weather(8.5, 2, isDay = false), fetched, on = true))
    }

    @Test
    fun clearAtNightIsMoon() {
        assertEquals("☾ 3° Clear", cornerWeather(weather(3.2, 0, isDay = false), fetched, on = true))
        assertEquals("☀ 3° Clear", cornerWeather(weather(3.2, 0, isDay = true), fetched, on = true))
    }

    @Test
    fun minusZeroIsZero() {
        assertEquals("❄ 0° Snow", cornerWeather(weather(-0.4, 71), fetched, on = true))
        assertEquals("❄ -5° Heavy snow", cornerWeather(weather(-4.6, 75), fetched, on = true))
    }

    @Test
    fun unknownCodeShowsTempOnly() {
        assertEquals("12°", cornerWeather(weather(12.0, 42), fetched, on = true))
    }

    @Test
    fun noSymbolsWhenAsked() {
        assertEquals("9° Partly cloudy", cornerWeather(weather(8.5, 2), fetched, on = true, symbols = false))
    }

    @Test
    fun staleWeatherHidden() {
        val w = weather(8.5, 2)
        assertEquals("☁ 9° Partly cloudy", cornerWeather(w, fetched + 180 * minute, on = true))
        assertNull(cornerWeather(w, fetched + 181 * minute, on = true))
        assertNull(cornerWeather(null, fetched, on = true))
    }

    @Test
    fun offHidesWeather() {
        assertNull(cornerWeather(weather(8.5, 2), fetched, on = false))
    }

    @Test
    fun wordsForEachKind() {
        assertEquals("☂ 5° Rain", cornerWeather(weather(5.0, 61), fetched, on = true))
        assertEquals("☂ 5° Showers", cornerWeather(weather(5.0, 80), fetched, on = true))
        assertEquals("⚡ 20° Thunderstorm", cornerWeather(weather(20.0, 95), fetched, on = true))
        assertEquals("☁ 1° Fog", cornerWeather(weather(1.0, 45), fetched, on = true))
    }

    @Test
    fun dueAfterHalfAnHour() {
        assertFalse(weatherDue(lastOkMs = fetched, lastTryMs = fetched, nowMs = fetched + 29 * minute))
        assertTrue(weatherDue(lastOkMs = fetched, lastTryMs = fetched, nowMs = fetched + 31 * minute))
    }

    @Test
    fun retryWaitsFiveMinutes() {
        val failedTry = fetched + 60 * minute
        assertFalse(weatherDue(lastOkMs = fetched, lastTryMs = failedTry, nowMs = failedTry + 4 * minute))
        assertTrue(weatherDue(lastOkMs = fetched, lastTryMs = failedTry, nowMs = failedTry + 6 * minute))
        assertFalse(weatherDue(lastOkMs = null, lastTryMs = failedTry, nowMs = failedTry + 4 * minute))
    }

    @Test
    fun dueWhenNeverFetched() {
        assertTrue(weatherDue(lastOkMs = null, lastTryMs = null, nowMs = fetched))
    }
}
