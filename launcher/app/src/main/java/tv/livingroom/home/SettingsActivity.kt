package tv.livingroom.home

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.livingroom.home.apps.HdmiInput
import tv.livingroom.home.apps.RowItem
import tv.livingroom.home.apps.RowSettings
import tv.livingroom.home.apps.TvApp
import tv.livingroom.home.apps.appRow
import tv.livingroom.home.apps.buildAppList
import tv.livingroom.home.apps.loadHdmiInputs
import tv.livingroom.home.apps.loadTvApps
import tv.livingroom.home.apps.openGoogleHome
import tv.livingroom.home.apps.start
import tv.livingroom.home.homekey.homeCaught
import tv.livingroom.home.screensaver.PhotoLoader
import tv.livingroom.home.screensaver.SaverPhoto
import tv.livingroom.home.screensaver.SaverSettings
import tv.livingroom.home.settings.DEFAULT_LAYOUT
import tv.livingroom.home.settings.HomePrefs
import tv.livingroom.home.settings.SettingsScreen
import tv.livingroom.home.sky.HomePhoto
import tv.livingroom.home.sky.HomePhotoLoader
import tv.livingroom.home.sky.sunTimes
import tv.livingroom.home.testswitch.EXTRA_SAVER_AT

/** The home screen's own settings, opened from a long-press menu. Back returns home. */
class SettingsActivity : ComponentActivity() {
    private val layout = mutableStateOf(DEFAULT_LAYOUT)
    private val weatherOn = mutableStateOf(true)
    private val rowSettings = mutableStateOf(RowSettings())
    private val tiles = mutableStateOf<List<RowItem>>(emptyList())
    private val homeCaught = mutableStateOf(true)
    private val confirming = mutableStateOf(false)
    private val saver = mutableStateOf(SaverSettings())
    private val credits = mutableStateOf<List<SaverPhoto>>(emptyList())
    private val homePhoto = mutableStateOf<HomePhoto?>(null)
    private var apps: List<TvApp> = emptyList()
    private var inputs: List<HdmiInput> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(null) // The screen paints its own.
        @Suppress("DEPRECATION") // The PackageInfoFlags form is Android 13+; the TV is Android 12.
        val build = packageManager.getPackageInfo(packageName, 0).longVersionCode
        setContent {
            SettingsScreen(
                layout = layout.value,
                weatherOn = weatherOn.value,
                tiles = tiles.value,
                hidden = rowSettings.value.hidden,
                homeCaught = homeCaught.value,
                confirmingSwitchBack = confirming.value,
                saver = saver.value,
                credits = credits.value,
                homePhoto = homePhoto.value,
                build = build,
                onSaverSky = { changeSaver { it.copy(skyOn = !it.skyOn) } },
                onSaverLateNight = { changeSaver { it.copy(lateNightOn = !it.lateNightOn) } },
                onPreviewSky = { preview(sunTimes(LocalDate.now(), ZoneId.systemDefault()).sunset.toLocalTime()) },
                onPreviewPlaces = { preview(LocalTime.of(11, 15)) },
                onLayout = {
                    layout.value = layout.value.other
                    HomePrefs.setHomeLayout(this, layout.value)
                },
                onWeather = {
                    weatherOn.value = !weatherOn.value
                    HomePrefs.setWeatherOn(this, weatherOn.value)
                },
                onToggleTile = { key ->
                    changeRow { it.copy(hidden = if (key in it.hidden) it.hidden - key else it.hidden + key) }
                },
                onStandardOrder = { changeRow { it.copy(order = RowSettings().order) } },
                onSwitchBack = { confirming.value = true },
                onConfirmSwitchBack = ::switchBack,
                onCancelSwitchBack = { confirming.value = false },
                onUseAgain = { start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        layout.value = HomePrefs.homeLayout(this)
        weatherOn.value = HomePrefs.weatherOn(this)
        rowSettings.value = HomePrefs.rowSettings(this)
        saver.value = HomePrefs.saverSettings(this)
        homeCaught.value = homeCaught(
            Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            ComponentName(this, HomeKeyService::class.java).flattenToString(),
        )
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                apps = buildAppList(loadTvApps(packageManager), packageName)
                inputs = loadHdmiInputs(this@SettingsActivity)
                credits.value = PhotoLoader.list(this@SettingsActivity)
                homePhoto.value = HomePhotoLoader.photo(assets)
            }
            showTiles()
        }
    }

    /** Every app and input in row order, hidden ones in their place too, so each can be shown again. */
    private fun showTiles() {
        tiles.value = appRow(apps, inputs, rowSettings.value.copy(hidden = emptySet())).filter { it != RowItem.Explore }
    }
    private fun changeSaver(change: (SaverSettings) -> SaverSettings) {
        saver.value = change(saver.value)
        HomePrefs.setSaverSettings(this, saver.value)
    }

    /** The screensaver as it looks at [at] today: Living sky at sunset, Beautiful places at 11:15 AM. */
    private fun preview(at: LocalTime) {
        startActivity(
            Intent(this, ScreensaverPreviewActivity::class.java)
                .putExtra(EXTRA_SAVER_AT, at.withSecond(0).withNano(0).toString()),
        )
    }
    private fun changeRow(change: (RowSettings) -> RowSettings) {
        rowSettings.value = change(rowSettings.value)
        HomePrefs.saveRowSettings(this, rowSettings.value)
        showTiles()
    }

    /**
     * Hands Home back to Google's home screen and goes there. If the service is
     * on but not running just now, it can't turn itself off: the TV's own
     * Accessibility settings open instead, where it can be turned off by hand.
     */
    private fun switchBack() {
        confirming.value = false
        if (!HomeKeyService.switchOff()) {
            Log.w(TAG, "Home-button service isn't running; opening Accessibility settings to switch it off there")
            start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        openGoogleHome()
        finish()
    }
}
