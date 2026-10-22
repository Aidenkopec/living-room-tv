package tv.livingroom.home.homekey

import android.view.KeyEvent

enum class HomeKeyAction { PASS, CONSUME, CONSUME_AND_OPEN }

/**
 * What to do with a key the accessibility service sees. Home is kept from the
 * system, and Living Room's screen opens when the button is let go, unless it was held
 * long enough to open Google's side panel instead ([longPressDone]). Every
 * other key passes through untouched.
 */
fun homeKeyAction(keyCode: Int, action: Int, longPressDone: Boolean = false): HomeKeyAction = when {
    keyCode != KeyEvent.KEYCODE_HOME -> HomeKeyAction.PASS
    action == KeyEvent.ACTION_UP && !longPressDone -> HomeKeyAction.CONSUME_AND_OPEN
    else -> HomeKeyAction.CONSUME
}

/**
 * Whether Android has [ourService] (package/class) switched on, from the
 * ":"-separated `enabled_accessibility_services` setting. Entries may use the
 * short ".Class" form.
 */
fun homeCaught(enabledServices: String?, ourService: String): Boolean =
    enabledServices.orEmpty().split(':').any { fullName(it) == fullName(ourService) }

/** "pkg/.Class" → "pkg/pkg.Class"; anything else as it is. */
private fun fullName(component: String): String {
    val pkg = component.substringBefore('/')
    val cls = component.substringAfter('/', missingDelimiterValue = "")
    return if (cls.startsWith('.')) "$pkg/$pkg$cls" else component
}
