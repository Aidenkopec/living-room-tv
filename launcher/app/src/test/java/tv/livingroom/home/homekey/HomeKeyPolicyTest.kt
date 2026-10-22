package tv.livingroom.home.homekey

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeKeyPolicyTest {
    private val home = KeyEvent.KEYCODE_HOME
    private val down = KeyEvent.ACTION_DOWN
    private val up = KeyEvent.ACTION_UP

    @Test
    fun otherKeysAlwaysPass() {
        for (key in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_CENTER)) {
            assertEquals(HomeKeyAction.PASS, homeKeyAction(key, down))
            assertEquals(HomeKeyAction.PASS, homeKeyAction(key, up))
        }
    }

    @Test
    fun homeDownIsConsumed() {
        assertEquals(HomeKeyAction.CONSUME, homeKeyAction(home, down))
    }

    @Test
    fun homeUpOpensOurScreen() {
        assertEquals(HomeKeyAction.CONSUME_AND_OPEN, homeKeyAction(home, up))
    }

    @Test
    fun homeUpAfterLongPressOnlyConsumes() {
        // The side panel already opened while Home was held; letting go does nothing more.
        assertEquals(HomeKeyAction.CONSUME, homeKeyAction(home, up, longPressDone = true))
    }

    private val ours = "tv.livingroom.home/tv.livingroom.home.HomeKeyService"

    @Test
    fun homeCaughtWhenListed() {
        assertEquals(true, homeCaught(ours, ours))
        assertEquals(true, homeCaught("com.example.reader/.Reader:$ours", ours))
        assertEquals(true, homeCaught("tv.livingroom.home/.HomeKeyService", ours))
    }

    @Test
    fun homeNotCaughtWhenSwitchedOff() {
        assertEquals(false, homeCaught(null, ours))
        assertEquals(false, homeCaught("", ours))
        assertEquals(false, homeCaught("null", ours))
        assertEquals(false, homeCaught("com.example.reader/.Reader", ours))
    }
}
