package tv.livingroom.home

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.time.LocalTime
import tv.livingroom.home.screensaver.Screensaver
import tv.livingroom.home.screensaver.saverClockFrom
import tv.livingroom.home.testswitch.EXTRA_SAVER_AT
import tv.livingroom.home.testswitch.EXTRA_SAVER_OFF_MIN
import tv.livingroom.home.testswitch.EXTRA_SAVER_WEATHER
import tv.livingroom.home.weather.WeatherSource

/**
 * The screensaver on demand, from Home screen settings or a test switch, as
 * if it were [EXTRA_SAVER_AT] ("18:04"), with [EXTRA_SAVER_WEATHER] as the sky's weather.
 * Any key closes it. It can't put the TV to sleep, so at late-night turn-off
 * it just closes.
 */
class ScreensaverPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(null)
        val at = intent.getStringExtra(EXTRA_SAVER_AT)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val weatherCode = intent.getIntExtra(EXTRA_SAVER_WEATHER, -1).takeIf { it >= 0 }
        val offAfterMin = intent.getIntExtra(EXTRA_SAVER_OFF_MIN, -1).takeIf { it > 0 } ?: 30
        Log.i(TAG, "Screensaver preview as if it were ${at ?: "now"}, weather ${weatherCode ?: "real"}")
        WeatherSource.refreshIfDue(this)
        setContent {
            Screensaver(
                clock = saverClockFrom(at),
                weatherCode = weatherCode,
                offAfterMin = offAfterMin,
                onDimChange = { dim -> Log.i(TAG, "Screensaver preview: dimmed=$dim") },
                onTurnOffDue = {
                    Log.i(TAG, "Screensaver preview: would turn the TV off now")
                    finish()
                },
            )
        }
    }

    // Nothing on screen takes keys, so every key ends up here.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        finish()
        return true
    }
}
