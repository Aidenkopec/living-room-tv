package tv.livingroom.home

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tv.livingroom.home.apps.HdmiInput
import tv.livingroom.home.apps.RowItem
import tv.livingroom.home.apps.RowSettings
import tv.livingroom.home.apps.TvApp
import tv.livingroom.home.apps.appRow
import tv.livingroom.home.apps.buildAppList
import tv.livingroom.home.apps.loadHdmiInputs
import tv.livingroom.home.apps.loadLastOpened
import tv.livingroom.home.apps.loadTvApps
import tv.livingroom.home.apps.mergeOrder
import tv.livingroom.home.apps.moveKey
import tv.livingroom.home.apps.watchInputs
import tv.livingroom.home.dock.DockHomeScreen
import tv.livingroom.home.livingsky.HomeScreen
import tv.livingroom.home.settings.DEFAULT_LAYOUT
import tv.livingroom.home.settings.HomeLayout
import tv.livingroom.home.settings.HomePrefs
import tv.livingroom.home.testswitch.HomeOverrides
import tv.livingroom.home.testswitch.HomeTestSwitch
import tv.livingroom.home.watch.WatchCard
import tv.livingroom.home.watch.WatchStore
import tv.livingroom.home.watch.key
import tv.livingroom.home.watch.liveSessions
import tv.livingroom.home.watch.sampleShows
import tv.livingroom.home.watch.snapshot
import tv.livingroom.home.weather.WeatherSource

class MainActivity : ComponentActivity() {
    private val row = mutableStateOf<List<RowItem>?>(null)
    private val lastOpened = mutableStateOf<Map<String, Long>>(emptyMap())
    private val overrides = mutableStateOf<HomeOverrides?>(null) // From a test switch, for one opening.
    private val sampleShows = mutableStateOf<List<WatchCard>?>(null) // Test switch: placeholder shows, never saved.
    private val layout = mutableStateOf(DEFAULT_LAYOUT)
    private val resumes = mutableIntStateOf(0)
    private var apps: List<TvApp> = emptyList()
    private var inputs: List<HdmiInput> = emptyList()
    private var stopWatchingInputs: (() -> Unit)? = null
    private var inputsChanged: Job? = null
    private var loading: Job? = null
    private var inputReads = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is the home screen: Back has nowhere to go.
        onBackPressedDispatcher.addCallback(this) {}
        // The screen paints its own background; the window's would be one more full-screen fill per frame.
        window.setBackgroundDrawable(null)
        setContent {
            val onMove = { key: String, by: Int ->
                changeRow { it.copy(order = mergeOrder(it.order, moveKey(row.value.orEmpty(), key, by))) }
            }
            val onHide = { key: String -> changeRow { it.copy(hidden = it.hidden + key) } }
            // The two home screens (Settings → Look): Living sky with Continue watching, and Mountains and dock.
            when (layout.value) {
                HomeLayout.MOUNTAINS -> DockHomeScreen(
                    row.value,
                    lastOpened.value,
                    overrides.value,
                    resumes.intValue,
                    onMove = onMove,
                    onHide = onHide,
                )
                HomeLayout.LIVING_SKY -> {
                    val saved by WatchStore.cards.collectAsState()
                    HomeScreen(
                        row.value,
                        sampleShows.value ?: saved,
                        lastOpened.value,
                        overrides.value,
                        resumes.intValue,
                        onMove = onMove,
                        onHide = onHide,
                        onRemoveShow = { key ->
                            val samples = sampleShows.value
                            if (samples != null) sampleShows.value = samples.filter { it.key != key } else WatchStore.remove(this, key)
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // TestSwitchActivity has no window, so starting it doesn't pause this screen: when it
        // opens this one again only the new intent arrives, without an onResume to read the switch.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) applyTestSwitch()
    }

    override fun onResume() {
        super.onResume()
        applyTestSwitch()
        WeatherSource.refreshIfDue(this)
        // Apps may have been added, removed or used since the last showing. A quick
        // second resume replaces the first one's load rather than racing it.
        loading?.cancel()
        loading = lifecycleScope.launch {
            // A show still playing right up to Home is saved at its latest spot, while the apps load.
            val saving = launch(Dispatchers.IO) {
                val now = SystemClock.elapsedRealtime()
                WatchStore.saveAll(this@MainActivity, liveSessions().mapNotNull { s -> s.snapshot()?.let { it.at(now) to it.art } })
            }
            val readingInputs = launch { reloadInputs() }
            val (loadedApps, opened) = withContext(Dispatchers.IO) {
                buildAppList(loadTvApps(packageManager), packageName) to loadLastOpened(this@MainActivity)
            }
            apps = loadedApps
            readingInputs.join()
            saving.join() // Focus may go to the card just saved.
            row.value = appRow(apps, inputs, HomePrefs.rowSettings(this@MainActivity))
            lastOpened.value = opened
            resumes.intValue++
        }
    }

    // While the screen shows, a console turning on or off (or a device plugged in) updates its tile.
    override fun onStart() {
        super.onStart()
        stopWatchingInputs = watchInputs {
            // The PS5 sends a few of these in a row as it wakes: only the last one's read counts.
            inputsChanged?.cancel()
            inputsChanged = lifecycleScope.launch {
                reloadInputs()
                if (row.value != null) row.value = appRow(apps, inputs, HomePrefs.rowSettings(this@MainActivity))
            }
        }
    }

    /** Reads the TV's inputs again. A read that a newer one started after is thrown away, so an older state never wins. */
    private suspend fun reloadInputs() {
        val read = ++inputReads
        val loaded = withContext(Dispatchers.IO) { loadHdmiInputs(this@MainActivity) }
        if (read == inputReads) inputs = loaded
    }

    override fun onStop() {
        stopWatchingInputs?.invoke()
        stopWatchingInputs = null
        super.onStop()
    }

    /**
     * Takes a pending test switch (from TestSwitchActivity, adb only), which applies to this opening
     * only, and reads the layout setting, so coming back from Settings shows the new look at once.
     */
    private fun applyTestSwitch() {
        val test = HomeTestSwitch.take()
        overrides.value = test
        sampleShows.value = test?.sampleShows?.let { sampleShows(it, SAMPLE_APPS, System.currentTimeMillis()) }
        layout.value = test?.layout ?: HomePrefs.homeLayout(this)
    }

    /** Saves a change to the app row (a tile moved or hidden) and shows it at once. */
    private fun changeRow(change: (RowSettings) -> RowSettings) {
        val settings = change(HomePrefs.rowSettings(this))
        HomePrefs.saveRowSettings(this, settings)
        row.value = appRow(apps, inputs, settings)
    }

    private companion object {
        // The apps whose shows the placeholder cards use: the ones that share show names.
        val SAMPLE_APPS = listOf("ca.bellmedia.cravetv", "com.google.android.youtube.tv", "com.cbs.ca")
    }
}
