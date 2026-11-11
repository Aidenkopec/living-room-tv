package tv.livingroom.home

import android.os.SystemClock
import android.service.dreams.DreamService
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import tv.livingroom.home.christmas.FiresideRemote
import tv.livingroom.home.screensaver.SaverChoices
import tv.livingroom.home.screensaver.SaverTestSwitch
import tv.livingroom.home.screensaver.Screensaver
import tv.livingroom.home.screensaver.saverClockFrom
import tv.livingroom.home.screensaver.saverShowsFireside
import tv.livingroom.home.settings.HomePrefs
import tv.livingroom.home.weather.WeatherSource

/**
 * Living Room's screensaver ("Living sky" and "Beautiful places", or Fireside while the
 * Christmas theme is on), started by Android after 10 minutes untouched once it's the TV's
 * chosen screensaver.
 * Any key ends it, except that Fireside takes the arrows and OK for itself while it shows ([FiresideRemote]).
 * Compose needs lifecycle and saved-state owners, which a DreamService doesn't have, so this one provides its own.
 */
class ScreensaverService :
    DreamService(),
    LifecycleOwner,
    SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val remote = FiresideRemote()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFullscreen = true
        isScreenBright = true
        WeatherSource.refreshIfDue(this)
        val next = SaverTestSwitch.takeForNextSaver()
        next.at?.let { Log.i(TAG, "Screensaver: test switch, as if it were $it") }
        val fireside = saverShowsFireside(HomePrefs.christmasOn(this), next.christmas)
        Log.i(TAG, "Screensaver: ${if (fireside) "Fireside" else "everyday"}")
        // Only Fireside listens to the remote; with the everyday one, DreamService ends it on any key.
        isInteractive = fireside
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@ScreensaverService)
            setViewTreeSavedStateRegistryOwner(this@ScreensaverService)
            setContent {
                Screensaver(
                    clock = saverClockFrom(next.at),
                    choices = SaverChoices(next.weatherCode, next.offAfterMin ?: 30, fireside, next.santa?.toDouble(), next.moment),
                    remote = remote,
                    onDimChange = { dim -> isScreenBright = !dim },
                    onTurnOffDue = { finish() }, // Ending by itself after the idle time lets Android put the TV to standby.
                )
            }
        }
        setContentView(view)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    /** Fireside's keys go to it; every other key (and, once Fireside has gone, every key) wakes the TV as before. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isInteractive) return super.dispatchKeyEvent(event)
        if (remote.take(event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.repeatCount, SystemClock.elapsedRealtime())) return true
        wakeUp()
        return true
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!isInteractive) return super.dispatchGenericMotionEvent(event)
        wakeUp()
        return true
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractive) return super.dispatchTouchEvent(event)
        wakeUp()
        return true
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        running = this
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        Log.i(TAG, "Screensaver started")
    }

    override fun onDreamingStopped() {
        if (running === this) running = null
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        Log.i(TAG, "Screensaver stopped")
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDetachedFromWindow()
    }

    companion object {
        @Volatile private var running: ScreensaverService? = null

        /**
         * Ends this screensaver the way a key press would, if it's showing. The
         * Home-button service takes Home before the screensaver sees it, so it
         * calls this first. False if it isn't showing.
         */
        fun wakeIfShowing(): Boolean {
            val saver = running ?: return false
            Log.i(TAG, "Home on the screensaver: waking it")
            saver.wakeUp()
            return true
        }
    }
}
