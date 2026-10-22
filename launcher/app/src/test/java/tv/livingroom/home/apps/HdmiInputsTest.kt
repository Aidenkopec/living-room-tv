package tv.livingroom.home.apps

import android.media.tv.TvInputManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HdmiInputsTest {
    @Test
    fun connectedIsOn() {
        assertEquals(true, inputOn(TvInputManager.INPUT_STATE_CONNECTED))
    }

    @Test
    fun standbyIsOff() {
        assertEquals(false, inputOn(TvInputManager.INPUT_STATE_CONNECTED_STANDBY))
    }

    @Test
    fun disconnectedCantTell() {
        assertNull(inputOn(TvInputManager.INPUT_STATE_DISCONNECTED))
    }

    // The square tile's words: short names for the consoles, anything else as the device calls itself.

    @Test
    fun ps5IsShortened() {
        assertEquals("PS5", shortInputLabel("PlayStation 5"))
        assertEquals("PS5", shortInputLabel("PlayStation5"))
    }

    @Test
    fun ps4IsShortened() {
        assertEquals("PS4", shortInputLabel("PlayStation 4"))
    }

    @Test
    fun otherNamesUnchanged() {
        assertEquals("Xbox Series X", shortInputLabel("Xbox Series X"))
        assertEquals("Apple TV", shortInputLabel("Apple TV"))
        assertEquals("PlayStation", shortInputLabel("PlayStation"))
    }

    @Test
    fun consolesGetTheIcon() {
        for (name in listOf("PlayStation 5", "Xbox Series X", "Nintendo Switch", "playstation 4")) assertEquals(name, true, isConsole(name))
    }

    @Test
    fun cableBoxDoesNot() {
        for (name in listOf("Rogers Ignite", "Apple TV", "Blu-ray", "")) assertEquals(name, false, isConsole(name))
    }
}
