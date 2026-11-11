package tv.livingroom.home.christmas

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FiresideRemoteTest {
    @Test
    fun theArrowsAndOkAreFiresides() {
        assertEquals(FiresideKey.TREE, firesideKey(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(FiresideKey.TREE, firesideKey(KeyEvent.KEYCODE_ENTER))
        assertEquals(FiresideKey.SNOW, firesideKey(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(FiresideKey.FIRE, firesideKey(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(FiresideKey.PRESENT, firesideKey(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(FiresideKey.GINGERBREAD, firesideKey(KeyEvent.KEYCODE_DPAD_RIGHT))
    }

    @Test
    fun everyOtherKeyWakesTheTv() {
        for (code in listOf(
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_MUTE,
            KeyEvent.KEYCODE_A,
        )) {
            assertNull(firesideKey(code))
        }
    }

    @Test
    fun withoutACatcherNothingIsTaken() {
        assertFalse(FiresideRemote().take(KeyEvent.KEYCODE_DPAD_UP, down = true, repeat = 0, nowMs = 0))
    }

    @Test
    fun aPressReachesTheCatcherOnceAndItsRepeatsAndKeyUpAreTakenQuietly() {
        val got = ArrayList<FiresideKey>()
        val remote = FiresideRemote().apply { catcher = { got += it } }
        assertTrue(remote.take(KeyEvent.KEYCODE_DPAD_UP, down = true, repeat = 0, nowMs = 0))
        assertTrue(remote.take(KeyEvent.KEYCODE_DPAD_UP, down = true, repeat = 1, nowMs = 0))
        assertTrue(remote.take(KeyEvent.KEYCODE_DPAD_UP, down = false, repeat = 0, nowMs = 0))
        assertFalse(remote.take(KeyEvent.KEYCODE_BACK, down = true, repeat = 0, nowMs = 0))
        assertEquals(listOf(FiresideKey.SNOW), got)
    }

    @Test
    fun upSnowsHarderUpToThreeTimesAndEasesOff() {
        val presses = FiresidePresses()
        assertEquals(0f, snowBoost(presses, 10.0), 0f)
        presses.press(FiresideKey.SNOW, 10.0)
        assertEquals(1f, snowBoost(presses, 10.0), 1e-6f)
        assertEquals(1 - 0.07f * 5, snowBoost(presses, 15.0), 1e-4f)
        for (i in 1..5) presses.press(FiresideKey.SNOW, 10.0 + i * 0.1)
        assertTrue(snowBoost(presses, 10.6) <= 3f)
        assertEquals(3f, snowBoost(presses, 10.5), 0.05f)
        assertEquals(0f, snowBoost(presses, 100.0), 0f)
        // Presses still to come (a clock that went back) count for nothing yet.
        assertTrue(snowBoost(presses, 9.0) == 0f)
    }

    @Test
    fun aPressRecordKeepsTheLatestOfEachKind() {
        val presses = FiresidePresses()
        assertTrue(presses.at(FiresideKey.FIRE).isNaN())
        for (i in 0 until 20) presses.press(FiresideKey.FIRE, i.toDouble())
        assertEquals(19.0, presses.at(FiresideKey.FIRE), 0.0)
        assertEquals(12.0, presses.at(FiresideKey.FIRE, 7), 0.0)
        assertTrue(presses.at(FiresideKey.FIRE, FiresidePresses.KEPT).isNaN())
        assertEquals(20, presses.count(FiresideKey.FIRE))
        assertTrue(presses.at(FiresideKey.TREE).isNaN())
    }

    @Test
    fun theFireFlaresThePresentPopsAndTheTreeChangesShow() {
        assertEquals(0f, fireFlare(Double.NaN), 0f)
        assertEquals(1f, fireFlare(1.5), 1e-4f)
        assertEquals(0f, fireFlare(3.0), 0f)
        assertEquals(0f, presentOpen(Double.NaN), 0f)
        assertTrue(presentOpen(1.0) > 0.99f)
        assertEquals(0f, presentOpen(POP_LENGTH), 0f)
        assertEquals(listOf(1, 2, 0, 1), (0..3).map(::treeShow))
    }

    @Test
    fun theGingerbreadPersonWalksTheMantelAndWavesInTheMiddle() {
        val at = FloatArray(3)
        assertFalse(gingerbreadAt(Double.NaN, at))
        assertFalse(gingerbreadAt(GINGER_LENGTH, at))
        assertTrue(gingerbreadAt(0.0, at))
        assertEquals(770f, at[0], 0.5f)
        assertTrue(gingerbreadAt(GINGER_LENGTH / 2, at))
        assertEquals(1f, at[2], 0f)
        assertEquals(1001f, at[0], 1f)
        assertTrue(gingerbreadAt(GINGER_LENGTH - 0.01, at))
        assertEquals(1232f, at[0], 1f)
    }

    @Test
    fun pressingAgainNeverMakesAnEffectJumpBack() {
        val presses = FiresidePresses()
        presses.press(FiresideKey.PRESENT, 10.0)
        presses.press(FiresideKey.PRESENT, 11.0) // While it's still open: ignored.
        assertEquals(10.0, runStart(presses, FiresideKey.PRESENT, POP_LENGTH, 12.0), 0.0)
        presses.press(FiresideKey.PRESENT, 20.0) // After it shut: a new pop.
        assertEquals(20.0, runStart(presses, FiresideKey.PRESENT, POP_LENGTH, 21.0), 0.0)
        assertEquals(10.0, runStart(presses, FiresideKey.PRESENT, POP_LENGTH, 19.0), 0.0)
        // The small dog's sniff pops it too, unless it's open already.
        assertEquals(30.0, runStart(presses, FiresideKey.PRESENT, POP_LENGTH, 31.0, also = 30.0), 0.0)
        assertEquals(20.0, runStart(presses, FiresideKey.PRESENT, POP_LENGTH, 22.0, also = 21.0), 0.0)
        assertTrue(runStart(FiresidePresses(), FiresideKey.PRESENT, POP_LENGTH, 5.0).isNaN())
        // A second poke keeps the fire as strong as the first left it.
        val pokes = FiresidePresses()
        pokes.press(FiresideKey.FIRE, 10.0)
        val before = fireFlareAt(pokes, 11.5)
        pokes.press(FiresideKey.FIRE, 11.5)
        assertEquals(before, fireFlareAt(pokes, 11.5), 1e-6f)
    }

    @Test
    fun harderSnowAlsoFallsFasterWithoutAJump() {
        val presses = FiresidePresses()
        assertEquals(0.0, snowBoostSoFar(presses, 50.0), 0.0)
        presses.press(FiresideKey.SNOW, 10.0)
        // One press: a boost of 1 easing off over 1 / 0.07 s, adding up to 1 / (2 x 0.07).
        assertEquals(1 / 0.14, snowBoostSoFar(presses, 100.0), 1e-6)
        assertEquals(1.0 - 0.07 / 2, snowBoostSoFar(presses, 11.0), 1e-6)
        var last = 0.0
        for (i in 0..400) {
            val now = snowBoostSoFar(presses, 9.0 + i * 0.1)
            assertTrue(now >= last && now - last < 0.11)
            last = now
        }
    }
}
