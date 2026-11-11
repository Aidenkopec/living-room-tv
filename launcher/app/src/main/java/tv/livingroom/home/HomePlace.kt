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

    /**
     * Calgary's clock. Alberta keeps UTC−6 all year now, but the TV's time zone rules predate that change and would put
     * America/Edmonton an hour behind in winter; Regina is UTC−6 all year in every version of the rules, so it gives Calgary's
     * time on any device. (The TV's own zone is set the same way, so this matches the clock it shows.)
     */
    val ZONE: ZoneId = ZoneId.of("America/Regina")

    /** [ZONE] as Open-Meteo takes it in a URL. */
    val zoneForUrl: String = URLEncoder.encode(ZONE.id, "UTF-8") // The Charset overload is Android 13+.
}
