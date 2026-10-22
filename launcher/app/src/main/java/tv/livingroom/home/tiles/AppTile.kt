package tv.livingroom.home.tiles

import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import tv.livingroom.home.R
import tv.livingroom.home.apps.HdmiInput
import tv.livingroom.home.apps.TileArt
import tv.livingroom.home.apps.isConsole
import tv.livingroom.home.apps.label
import tv.livingroom.home.apps.shortInputLabel

// Living sky's tiles: 196 x 110 px and 112 x 110 px (square) on the 1920 px screen; a selected one lifts 10%.
val TileWidth = 98.dp
val TileHeight = 55.dp
val SquareTileWidth = 56.dp
private val TileShape = RoundedCornerShape(6.dp)
private const val TILE_LIFT = 1.10f

/**
 * An app's tile: its banner, its icon on a coloured tile, or just its name (blank until [loaded]).
 * [width], [height] and [focusedScale] are Living sky's unless the dock sets them.
 */
@Composable
fun AppTile(
    label: String,
    art: TileArt?,
    loaded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = TileWidth,
    height: Dp = TileHeight,
    focusedScale: Float = TILE_LIFT,
) {
    TileSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        width = width,
        height = height,
        focusedScale = focusedScale,
    ) {
        when (art) {
            is TileArt.Banner ->
                Image(art.image, contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            is TileArt.Icon -> Box(Modifier.fillMaxSize().background(Color(art.background)), Alignment.Center) {
                Image(art.image, contentDescription = label, modifier = Modifier.size(32.dp))
            }
            null -> Box(Modifier.fillMaxSize().background(Color(0xFF1C1B22)), Alignment.Center) {
                if (loaded) Text(label, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(4.dp))
            }
        }
    }
}

/**
 * A device on an HDMI port, as a small square tile: a controller for a
 * console, then its short name ("PS5"), and a green dot while it's on. No
 * blurred glow around the dot: blur was slow on this TV. Without
 * [showLabel] (a small tile in the dock), a console shows only its controller.
 */
@Composable
fun InputTile(
    input: HdmiInput,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = SquareTileWidth,
    height: Dp = TileHeight,
    focusedScale: Float = TILE_LIFT,
    showLabel: Boolean = true,
) {
    TileSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        width = size,
        height = height,
        focusedScale = focusedScale,
        restingBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)), shape = TileShape),
    ) {
        Column(
            Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.08f)),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val console = isConsole(input.label)
            if (console) {
                Image(painterResource(R.drawable.ic_controller), contentDescription = null, modifier = Modifier.size(20.dp), alpha = 0.9f)
            }
            // Only a console can go without its name: its controller says what it is.
            if (showLabel || !console) {
                Text(
                    shortInputLabel(input.label),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    fontFamily = Condensed,
                    lineHeight = 15.sp,
                    textAlign = TextAlign.Center,
                    maxLines = if (showLabel) 2 else 1,
                    overflow = if (showLabel) TextOverflow.Ellipsis else TextOverflow.Clip,
                    softWrap = showLabel,
                    modifier = Modifier.padding(horizontal = 3.dp),
                )
            }
        }
        if (input.on == true) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 5.dp, end = 5.dp)
                    .size(8.dp)
                    .border(BorderStroke(2.dp, OnGreen.copy(alpha = 0.35f)), CircleShape)
                    .padding(1.dp)
                    .background(OnGreen, CircleShape),
            )
        }
    }
}

private val OnGreen = Color(0xFF34C759)

/** The TV's own narrow Roboto (medium weight): "Explore" and a device's name fit the square tile at 14 sp only in these letters. */
private val Condensed = FontFamily(Typeface.create("sans-serif-condensed-medium", Typeface.NORMAL))

/** The last tile on the row, small and square: opens Google TV's home. Without [showLabel] (a small tile in the dock), just the ✦. */
@Composable
fun ExploreTile(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = SquareTileWidth,
    height: Dp = TileHeight,
    focusedScale: Float = TILE_LIFT,
    showLabel: Boolean = true,
) {
    TileSurface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        width = size,
        height = height,
        focusedScale = focusedScale,
        restingBorder = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.28f)), shape = TileShape),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.05f)))),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("✦", style = TextStyle(brush = GoogleBrush, fontSize = 18.sp, lineHeight = 18.sp))
            if (showLabel) {
                Text(
                    stringResource(R.string.explore),
                    fontSize = 14.sp,
                    fontFamily = Condensed,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** A tile that lifts and gets a white ring when selected (app tiles, Explore, Continue watching cards). */
@Composable
fun TileSurface(
    onClick: () -> Unit,
    modifier: Modifier,
    onLongClick: (() -> Unit)? = null,
    restingBorder: Border = Border.None,
    width: Dp = TileWidth,
    height: Dp = TileHeight,
    shape: Shape = TileShape,
    focusedScale: Float = TILE_LIFT,
    content: @Composable BoxScope.() -> Unit,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.size(width, height),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            focusedContainerColor = Color.Transparent,
            focusedContentColor = Color.White,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = focusedScale),
        border = ClickableSurfaceDefaults.border(
            border = restingBorder,
            focusedBorder = Border(BorderStroke(2.5.dp, Color.White), shape = shape),
        ),
        // No shadow under the lifted tile: on this near-black screen it barely shows, and
        // redrawing its blur on every frame of the lift was costly for the TV's GPU.
        content = content,
    )
}

private val GoogleBrush = Brush.linearGradient(
    0f to Color(0xFF4285F4),
    0.4f to Color(0xFFEA4335),
    0.7f to Color(0xFFFBBC05),
    1f to Color(0xFF34A853),
)
