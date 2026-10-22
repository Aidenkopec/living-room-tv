package tv.livingroom.home

import java.net.URLEncoder
import java.time.ZoneId

/**
 * Where the TV is. The sky, sunrise and sunset, the moon and the weather are all worked out for
 * this one place; change it here to move the app to another city.
 */
object HomePlace {
    const val NAME = "Calgary, AB"
    const val LATITUDE = 51.0447
    const val LONGITUDE = -114.0719
    val ZONE: ZoneId = ZoneId.of("America/Edmonton")

    /** [ZONE] as Open-Meteo takes it in a URL. */
    val zoneForUrl: String = URLEncoder.encode(ZONE.id, "UTF-8") // The Charset overload is Android 13+.
}
