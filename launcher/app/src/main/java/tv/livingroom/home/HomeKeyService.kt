package tv.livingroom.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityEvent
import tv.livingroom.home.homekey.HomeKeyAction
import tv.livingroom.home.homekey.homeKeyAction

/**
 * Sees the remote's keys before the system does, and catches Home (see [homeKeyAction]). The app never
 * claims the HOME intent: Google's home stays the system default, so if this service is off or
 * crashes, Home falls back to Google's. Android still starts Google's home on every Home press;
 * [MainActivity] opens slightly later and usually ends up on top.
 */
class HomeKeyService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private var longPressDone = false

    // An accessibility service gets only Home's down and up, never the system's repeats, so a held
    // Home is timed here. As on the stock launcher, the side panel opens while Home is still held.
    private val openSidePanel = Runnable {
        longPressDone = true
        Log.i(TAG, "Home held: opening the side panel")
        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    override fun onServiceConnected() {
        running = this
        Log.i(TAG, "Home-button service connected")
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val isHome = event.keyCode == KeyEvent.KEYCODE_HOME
        if (isHome && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            // This service takes Home before the screensaver sees it, so end the screensaver first.
            ScreensaverService.wakeIfShowing()
            longPressDone = false
            handler.removeCallbacks(openSidePanel)
            handler.postDelayed(openSidePanel, ViewConfiguration.getLongPressTimeout().toLong())
        }
        if (isHome && event.action == KeyEvent.ACTION_UP) handler.removeCallbacks(openSidePanel)

        val action = homeKeyAction(event.keyCode, event.action, longPressDone)
        if (isHome) Log.i(TAG, "Home action=${event.action} repeat=${event.repeatCount} -> $action")
        if (action == HomeKeyAction.CONSUME_AND_OPEN) openOurHome()
        return action != HomeKeyAction.PASS
    }

    private fun openOurHome() {
        try {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            )
        } catch (e: Exception) {
            // Never leave Home doing nothing: fall back to the system's home (Google's).
            Log.w(TAG, "Could not open our screen; falling back to the system Home", e)
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (running === this) running = null
        handler.removeCallbacks(openSidePanel)
        super.onDestroy()
    }

    companion object {
        /** The service while Android has it running (it runs in the app's process). */
        @Volatile private var running: HomeKeyService? = null

        /**
         * "Switch back to Google's home screen": the service turns itself off,
         * exactly as if switched off in the TV's Accessibility settings, so
         * Home goes to Google's again. False if it wasn't running.
         */
        fun switchOff(): Boolean {
            val service = running ?: return false
            Log.i(TAG, "Switching back to Google's home screen: turning the Home-button service off")
            service.disableSelf()
            return true
        }
    }
}
