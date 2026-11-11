package tv.livingroom.home

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import tv.livingroom.home.screensaver.NextSaver
import tv.livingroom.home.screensaver.SaverTestSwitch
import tv.livingroom.home.testswitch.EXTRA_SAVER_AT
import tv.livingroom.home.testswitch.EXTRA_SAVER_CHRISTMAS
import tv.livingroom.home.testswitch.EXTRA_SAVER_DOG
import tv.livingroom.home.testswitch.EXTRA_SAVER_MOMENT
import tv.livingroom.home.testswitch.EXTRA_SAVER_OFF_MIN
import tv.livingroom.home.testswitch.EXTRA_SAVER_SANTA
import tv.livingroom.home.testswitch.EXTRA_SAVER_WEATHER
import tv.livingroom.home.testswitch.HomeTestSwitch
import tv.livingroom.home.testswitch.momentValues
import tv.livingroom.home.testswitch.onOffValue
import tv.livingroom.home.testswitch.parseTestSwitches

/**
 * The one way in for test switches, for example
 * `adb shell am start -n tv.livingroom.home/.TestSwitchActivity --es tv.livingroom.home.AT 18:04`.
 *
 * The manifest lets only callers holding `android.permission.DUMP` start it. On a TV that is adb's
 * shell: no installed app can be granted it. The home screen itself takes no extras, so nothing
 * else on the TV can change what it shows. This activity has no window; it passes the switches on
 * and closes.
 */
class TestSwitchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras

        @Suppress("DEPRECATION") // Bundle.get: the switches come in as Int, String or Boolean.
        val switches = parseTestSwitches(extras?.keySet().orEmpty().associateWith { extras?.get(it) })
        Log.i(TAG, "Test switches: $switches")

        switches.home?.let { overrides ->
            HomeTestSwitch.arm(overrides)
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        switches.saver?.let { saver ->
            if (saver.nextOnly) {
                SaverTestSwitch.setForNextSaver(
                    NextSaver(saver.at, saver.offAfterMin, saver.christmas, saver.weatherCode, saver.santa, saver.moment),
                )
            } else {
                startActivity(
                    Intent(this, ScreensaverPreviewActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(EXTRA_SAVER_AT, saver.at?.toString())
                        .putExtra(EXTRA_SAVER_WEATHER, saver.weatherCode ?: -1)
                        .putExtra(EXTRA_SAVER_OFF_MIN, saver.offAfterMin ?: -1)
                        .putExtra(EXTRA_SAVER_CHRISTMAS, onOffValue(saver.christmas))
                        .putExtra(EXTRA_SAVER_SANTA, saver.santa ?: -1)
                        .putExtra(EXTRA_SAVER_MOMENT, momentValues(saver.moment).first)
                        .putExtra(EXTRA_SAVER_DOG, momentValues(saver.moment).second),
                )
            }
        }
        finish()
    }
}
