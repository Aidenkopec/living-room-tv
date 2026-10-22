package tv.livingroom.home.tiles

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import tv.livingroom.home.apps.HdmiInput
import tv.livingroom.home.apps.RowItem
import tv.livingroom.home.apps.TvApp

class TileMenuTest {
    @Test
    fun appTileMenu() {
        val netflix = RowItem.App(TvApp("com.netflix.ninja", "x.Main", "Netflix"))
        assertEquals(listOf(MenuAction.MOVE, MenuAction.HIDE, MenuAction.SETTINGS), tileMenu(netflix))
    }

    @Test
    fun inputTileMenu() {
        val ps5 = RowItem.Input(HdmiInput("hdmi/HDMI100004", "PlayStation 5", "HDMI 1", on = true))
        assertEquals(listOf(MenuAction.MOVE, MenuAction.HIDE, MenuAction.SETTINGS), tileMenu(ps5))
    }

    @Test
    fun exploreOnlyHasSettings() {
        assertEquals(listOf(MenuAction.SETTINGS), tileMenu(RowItem.Explore))
    }

    @Test
    fun cardMenuItems() {
        assertEquals(listOf(MenuAction.REMOVE_SHOW, MenuAction.SETTINGS), cardMenu())
    }

    @Test
    fun menuWords() {
        assertEquals(
            listOf("Move", "Hide from the home screen", "Remove this show", "Home screen settings"),
            MenuAction.entries.map { it.words },
        )
    }

    @Test
    fun heldOkFromTheLongPressIsIgnored() {
        // The press that opened the menu is still held: its repeats and its release don't count.
        assertEquals(false, okReachesMenu(armed = false, down = true, repeatCount = 7))
        assertEquals(false, okReachesMenu(armed = false, down = false, repeatCount = 0))
    }

    @Test
    fun freshPressInTheMenuCounts() {
        assertEquals(true, okReachesMenu(armed = false, down = true, repeatCount = 0))
        assertEquals(true, okReachesMenu(armed = true, down = false, repeatCount = 0))
    }

    @Test
    fun leftAndRightMoveOnThePress() {
        assertEquals(MoveKeyAction.LEFT, moveKeyAction(KeyEvent.KEYCODE_DPAD_LEFT, down = true))
        assertEquals(MoveKeyAction.RIGHT, moveKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT, down = true))
        assertEquals(MoveKeyAction.SWALLOW, moveKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT, down = false))
    }

    @Test
    fun okOrBackFinishOnTheRelease() {
        for (key in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK)) {
            assertEquals(MoveKeyAction.SWALLOW, moveKeyAction(key, down = true))
            assertEquals(MoveKeyAction.FINISH, moveKeyAction(key, down = false))
        }
    }

    @Test
    fun upAndDownCantLeaveTheTile() {
        assertEquals(MoveKeyAction.SWALLOW, moveKeyAction(KeyEvent.KEYCODE_DPAD_UP, down = true))
        assertEquals(MoveKeyAction.SWALLOW, moveKeyAction(KeyEvent.KEYCODE_DPAD_DOWN, down = false))
    }

    @Test
    fun otherKeysPassThrough() {
        assertEquals(MoveKeyAction.PASS, moveKeyAction(KeyEvent.KEYCODE_VOLUME_UP, down = true))
    }
}
