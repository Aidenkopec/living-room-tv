package tv.livingroom.home.tiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text

private val PanelShape = RoundedCornerShape(12.dp)
private val ItemShape = RoundedCornerShape(8.dp)

/**
 * The long-press menu: a small dark panel named after the tile or show
 * ([title]), one line per action. It's its own window, so focus stays in it;
 * Back closes it.
 */
@Composable
fun TileMenu(title: String, actions: List<MenuAction>, onPick: (MenuAction) -> Unit, onDismiss: () -> Unit) {
    val first = remember { FocusRequester() }
    var armed by remember { mutableStateOf(false) } // A fresh OK press started in the menu (see okReachesMenu).
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(300.dp)
                .background(Color(0xFF1C1E24), PanelShape)
                .padding(10.dp)
                .onPreviewKeyEvent { event ->
                    if (event.key != Key.DirectionCenter && event.key != Key.Enter &&
                        event.key != Key.NumPadEnter
                    ) {
                        return@onPreviewKeyEvent false
                    }
                    val down = event.type == KeyEventType.KeyDown
                    if (!okReachesMenu(armed, down, event.nativeKeyEvent.repeatCount)) return@onPreviewKeyEvent true
                    if (down) armed = true
                    false
                },
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                title,
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 6.dp),
            )
            actions.forEachIndexed { index, action ->
                Surface(
                    onClick = { onPick(action) },
                    modifier = Modifier.fillMaxWidth().let { if (index == 0) it.focusRequester(first) else it },
                    shape = ClickableSurfaceDefaults.shape(ItemShape),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        focusedContainerColor = Color.White,
                        focusedContentColor = Color(0xFF111316),
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                ) {
                    Text(action.words, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp))
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}
