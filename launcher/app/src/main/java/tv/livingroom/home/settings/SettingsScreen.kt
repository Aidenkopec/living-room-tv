package tv.livingroom.home.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.livingroom.home.HomePlace
import tv.livingroom.home.apps.RowItem
import tv.livingroom.home.apps.key
import tv.livingroom.home.apps.label
import tv.livingroom.home.homekey.homeCaught
import tv.livingroom.home.homescreen.Background
import tv.livingroom.home.screensaver.SaverPhoto
import tv.livingroom.home.screensaver.SaverSettings
import tv.livingroom.home.sky.HomePhoto

private val LineShape = RoundedCornerShape(8.dp)

/**
 * The home screen's settings: which home screen ([layout]), the weather, which tiles show on the
 * row, the screensaver, the way back to Google's home screen, and the photo credits. [tiles] are
 * every app and input in row order, hidden ones included; [homeCaught] is whether Living Room
 * catches the Home button right now.
 */
@Composable
fun SettingsScreen(
    layout: HomeLayout,
    weatherOn: Boolean,
    tiles: List<RowItem>,
    hidden: Set<String>,
    homeCaught: Boolean,
    confirmingSwitchBack: Boolean,
    saver: SaverSettings,
    credits: List<SaverPhoto>,
    homePhoto: HomePhoto?,
    build: Long,
    onLayout: () -> Unit,
    onWeather: () -> Unit,
    onSaverSky: () -> Unit,
    onSaverLateNight: () -> Unit,
    onPreviewSky: () -> Unit,
    onPreviewPlaces: () -> Unit,
    onToggleTile: (key: String) -> Unit,
    onStandardOrder: () -> Unit,
    onSwitchBack: () -> Unit,
    onConfirmSwitchBack: () -> Unit,
    onCancelSwitchBack: () -> Unit,
    onUseAgain: () -> Unit,
) {
    val first = remember { FocusRequester() }
    CompositionLocalProvider(LocalContentColor provides Color.White) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Background),
            contentPadding = PaddingValues(start = 43.dp, end = 43.dp, top = 40.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text("Home screen settings", fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
            }
            item { Heading("Home screen") }
            item { SettingLine("Look", layoutName(layout), onLayout, Modifier.focusRequester(first)) }
            item { Heading("Weather") }
            item { SettingLine(HomePlace.NAME, if (weatherOn) "On" else "Off", onWeather) }
            item { Heading("Apps on the home screen") }
            items(tiles, key = { it.key }) { tile ->
                SettingLine(tile.label, if (tile.key in hidden) "Hidden" else "Shown", { onToggleTile(tile.key) })
            }
            item { SettingLine("Put the apps back in the standard order", null, onStandardOrder) }
            item { Heading("Screensaver") }
            item { SettingLine("Living sky at sunrise and sunset", if (saver.skyOn) "On" else "Off", onSaverSky) }
            item { SettingLine("Late night: dimmed photos, TV off after 30 min", if (saver.lateNightOn) "On" else "Off", onSaverLateNight) }
            item { SettingLine("Preview Living sky", null, onPreviewSky) }
            item { SettingLine("Preview Beautiful places", null, onPreviewPlaces) }
            item {
                Text(
                    "The TV's own menus can't choose a screensaver, so going back to Google's is done over ADB " +
                        "(scripts/restore-tv.sh puts it back).",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    modifier = Modifier.width(620.dp).padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            item { Heading("Home button") }
            item {
                if (homeCaught) {
                    SettingLine("Switch back to Google's home screen", null, onSwitchBack)
                } else {
                    Column {
                        Text(
                            "Home opens Google's home screen now.",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 16.sp,
                            modifier = Modifier.padding(start = 12.dp, bottom = 4.dp),
                        )
                        SettingLine("Use Living Room for Home again", null, onUseAgain)
                    }
                }
            }
            if (homePhoto != null) {
                item { Heading("Home screen photo") }
                item(key = "home-photo") {
                    val caption = if (homePhoto.region.isEmpty()) homePhoto.place else "${homePhoto.place} · ${homePhoto.region}"
                    // CC BY-SA asks to say what was changed.
                    CreditLine(caption, listOf(homePhoto.credit, "${homePhoto.changes}."))
                }
            }
            if (credits.isNotEmpty()) {
                item { Heading("Screensaver photo credits") }
                items(credits, key = { "photo-${it.id}" }) { photo ->
                    val caption = if (photo.region.isEmpty()) photo.place else "${photo.place} · ${photo.region}"
                    CreditLine("${photo.id}. $caption", listOf(photo.credit))
                }
            }
            item {
                Text(
                    "Living Room build $build",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(start = 12.dp, top = 24.dp),
                )
            }
        }
        if (confirmingSwitchBack) SwitchBackConfirm(onConfirmSwitchBack, onCancelSwitchBack)
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** What the "Look" line says for each home screen. */
private fun layoutName(layout: HomeLayout): String = when (layout) {
    HomeLayout.MOUNTAINS -> "Mountains and dock"
    HomeLayout.LIVING_SKY -> "Living sky and shows"
}

@Composable
private fun Heading(text: String) {
    Text(
        text.uppercase(),
        color = Color.White.copy(alpha = 0.55f),
        fontSize = 14.sp,
        letterSpacing = 0.14.em,
        modifier = Modifier.padding(start = 12.dp, top = 20.dp, bottom = 4.dp),
    )
}

/** One pressable line: [label] on the left, its current [value] (if any) on the right. */
@Composable
private fun SettingLine(label: String, value: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.width(620.dp),
        shape = ClickableSurfaceDefaults.shape(LineShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF111316),
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 18.sp, modifier = Modifier.weight(1f))
            value?.let { Text(it, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

/** One photo's [caption], then its credit [details] in smaller type. It can take focus only so the list scrolls with the remote. */
@Composable
private fun CreditLine(caption: String, details: List<String>) {
    Surface(
        onClick = {},
        modifier = Modifier.width(620.dp),
        shape = ClickableSurfaceDefaults.shape(LineShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.White.copy(alpha = 0.12f),
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(caption, fontSize = 16.sp)
            for (line in details) Text(line, color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }
    }
}

/** "Are you sure?" before handing Home back to Google's home screen. */
@Composable
private fun SwitchBackConfirm(onConfirm: () -> Unit, onCancel: () -> Unit) {
    val confirm = remember { FocusRequester() }
    Dialog(onDismissRequest = onCancel) {
        Column(
            Modifier.width(520.dp).background(Color(0xFF1C1E24), RoundedCornerShape(12.dp)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Switch back to Google's home screen?", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Home will open Google's home screen again. Living Room stays installed. To come back: here, or " +
                    "TV Settings → System → Accessibility → Living Room.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Box { SettingLine("Switch back", null, onConfirm, Modifier.focusRequester(confirm)) }
            SettingLine("Cancel", null, onCancel)
        }
    }
    LaunchedEffect(Unit) { runCatching { confirm.requestFocus() } }
}
