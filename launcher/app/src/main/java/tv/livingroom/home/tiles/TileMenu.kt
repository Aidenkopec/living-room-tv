package tv.livingroom.home.tiles

import android.view.KeyEvent
import tv.livingroom.home.apps.RowItem

/** What the long-press menu offers. */
enum class MenuAction(val words: String) {
    MOVE("Move"),
    HIDE("Hide from the home screen"),
    REMOVE_SHOW("Remove this show"),
    SETTINGS("Home screen settings"),
}

/** A tile's menu. Explore can't be moved or hidden: it stays last, as the way to Google's home. */
fun tileMenu(item: RowItem): List<MenuAction> = when (item) {
    is RowItem.App, is RowItem.Input -> listOf(MenuAction.MOVE, MenuAction.HIDE, MenuAction.SETTINGS)
    RowItem.Explore -> listOf(MenuAction.SETTINGS)
}

/** A Continue watching card's menu. */
fun cardMenu(): List<MenuAction> = listOf(MenuAction.REMOVE_SHOW, MenuAction.SETTINGS)

/**
 * Whether an OK key event reaches the menu. Holding OK opens it, and that same
 * press goes on repeating and is let go of while the menu shows: none of that
 * may pick an item. Only once a fresh press starts inside the menu ([armed]
 * after the first key-down with no repeats) does OK count.
 */
fun okReachesMenu(armed: Boolean, down: Boolean, repeatCount: Int): Boolean = armed || (down && repeatCount == 0)

/** What a key does while a tile is being moved. */
enum class MoveKeyAction { LEFT, RIGHT, FINISH, SWALLOW, PASS }

/**
 * While moving a tile: Left and Right move it (on the press), OK or Back ends
 * it (on the release), and Up and Down can't leave it. Other keys pass.
 */
fun moveKeyAction(keyCode: Int, down: Boolean): MoveKeyAction = when (keyCode) {
    KeyEvent.KEYCODE_DPAD_LEFT -> if (down) MoveKeyAction.LEFT else MoveKeyAction.SWALLOW
    KeyEvent.KEYCODE_DPAD_RIGHT -> if (down) MoveKeyAction.RIGHT else MoveKeyAction.SWALLOW
    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK ->
        if (down) MoveKeyAction.SWALLOW else MoveKeyAction.FINISH
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> MoveKeyAction.SWALLOW
    else -> MoveKeyAction.PASS
}
