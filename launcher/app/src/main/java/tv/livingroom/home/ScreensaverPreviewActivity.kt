package tv.livingroom.home

import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import java.time.LocalTime
import tv.livingroom.home.christmas.FiresideRemote
import tv.livingroom.home.screensaver.SaverChoices
import tv.livingroom.home.screensaver.Screensaver
import tv.livingroom.home.screensaver.saverClockFrom
import tv.livingroom.home.screensaver.saverShowsFireside
import tv.livingroom.home.settings.HomePrefs
import tv.livingroom.home.testswitch.EXTRA_SAVER_AT
import tv.livingroom.home.testswitch.EXTRA_SAVER_CHRISTMAS
import tv.livingroom.home.testswitch.EXTRA_SAVER_DOG
import tv.livingroom.home.testswitch.EXTRA_SAVER_MOMENT
import tv.livingroom.home.testswitch.EXTRA_SAVER_OFF_MIN
import tv.livingroom.home.testswitch.EXTRA_SAVER_SANTA
import tv.livingroom.home.testswitch.EXTRA_SAVER_WEATHER
import tv.livingroom.home.testswitch.momentSwitch
import tv.livingroom.home.testswitch.onOffSwitch
import tv.livingroom.home.weather.WeatherSource

/**
 * The screensaver on demand, from Home screen settings or a test switch, as
 * if it were [EXTRA_SAVER_AT] ("18:04"), with [EXTRA_SAVER_WEATHER] as the sky's weather,
 * Fireside or not as [EXTRA_SAVER_CHRISTMAS] says (`on` or `off`; otherwise as Christmas is set).
 * Any key closes it. It can't put the TV to sleep, so at late-night turn-off
 * it just closes.
 */
class ScreensaverPreviewActivity : ComponentActivity() {
    private val remote = FiresideRemote()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setBackgroundDrawable(null)
        val at = intent.getStringExtra(EXTRA_SAVER_AT)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val weatherCode = intent.getIntExtra(EXTRA_SAVER_WEATHER, -1).takeIf { it >= 0 }
        val offAfterMin = intent.getIntExtra(EXTRA_SAVER_OFF_MIN, -1).takeIf { it > 0 } ?: 30
        val fireside = saverShowsFireside(HomePrefs.christmasOn(this), onOffSwitch(intent.getStringExtra(EXTRA_SAVER_CHRISTMAS)))
        val santa = intent.getIntExtra(EXTRA_SAVER_SANTA, -1).takeIf { it >= 0 }?.toDouble()
        val moment = momentSwitch(intent.getStringExtra(EXTRA_SAVER_MOMENT), intent.getStringExtra(EXTRA_SAVER_DOG))
        Log.i(TAG, "Screensaver preview as if it were ${at ?: "now"}, weather ${weatherCode ?: "real"}, fireside $fireside")
        WeatherSource.refreshIfDue(this)
        setContent {
            Screensaver(
                clock = saverClockFrom(at),
                choices = SaverChoices(weatherCode, offAfterMin, fireside, santa, moment),
                remote = remote,
                onDimChange = { dim -> Log.i(TAG, "Screensaver preview: dimmed=$dim") },
                onTurnOffDue = {
                    Log.i(TAG, "Screensaver preview: would turn the TV off now")
                    finish()
                },
            )
        }
    }

    // Leaving it (Home, which the Home-button service takes before any key reaches here, or anything
    // else covering it) closes it: left behind, it would go on painting its pictures.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) finish()
    }

    // Nothing on screen takes keys, so every key ends up here: Fireside's go to it, as on the real screensaver; any other closes it.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!remote.take(keyCode, down = true, repeat = event.repeatCount, nowMs = SystemClock.elapsedRealtime())) finish()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        remote.take(keyCode, down = false, repeat = 0, nowMs = SystemClock.elapsedRealtime()) || super.onKeyUp(keyCode, event)
}
