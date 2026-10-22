package tv.livingroom.home.livingsky

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.livingroom.home.apps.GlowColours
import tv.livingroom.home.apps.TileArt
import tv.livingroom.home.apps.scaleValue
import tv.livingroom.home.tiles.TileSurface
import tv.livingroom.home.watch.WatchCard
import tv.livingroom.home.watch.progressFraction

// Sized so four whole cards fit across the 960 dp screen and a fifth peeks in.
val CardWidth = 200.dp
val CardHeight = 112.5.dp
val CardGap = 16.dp
private val CardShape = RoundedCornerShape(8.dp)
private val BadgeShape = RoundedCornerShape(4.dp)
private const val CARD_LIFT = 1.06f // Less than the tiles' lift: the cards are bigger.

/**
 * A Continue watching card: the show's [picture] with the app's banner as a
 * small badge, or without a picture the app's own artwork ([art]: its banner,
 * or its icon on its colour; failing both, a gradient in its [glow] colours).
 * A progress bar when the app said how long it is. Only the title goes under
 * the picture: the selected card's episode and time are in the top area.
 */
@Composable
fun WatchCardTile(
    card: WatchCard,
    picture: ImageBitmap?,
    art: TileArt?,
    glow: GlowColours,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(Modifier.width(CardWidth)) {
        TileSurface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
            width = CardWidth,
            height = CardHeight,
            shape = CardShape,
            focusedScale = CARD_LIFT,
        ) {
            when {
                picture != null -> {
                    Image(picture, contentDescription = card.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Badge(art, Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
                art is TileArt.Banner ->
                    Image(art.image, contentDescription = card.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                art is TileArt.Icon -> Box(Modifier.fillMaxSize().background(Color(art.background)), Alignment.Center) {
                    Image(art.image, contentDescription = card.title, modifier = Modifier.size(40.dp))
                }
                else -> Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            0f to Color(scaleValue(glow.secondary, 0.35f)),
                            0.6f to Color(scaleValue(glow.primary, 0.6f)),
                            1f to Color(0xFF0A1014),
                        ),
                    ),
                )
            }
            progressFraction(card)?.let { done ->
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.28f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(done).background(Color.White))
                }
            }
        }
        // Two balanced lines; a title too long even for that ends in "…" here only (the top area shows it whole).
        val measure = rememberWordMeasure { CardTitleStyle }
        val density = LocalDensity.current
        val title = remember(card.title, measure) {
            fitCardTitle(card.title, with(density) { CardWidth.toPx() } * 0.97f) { words -> measure(words, CARD_TITLE_SP) }
        }
        Text(
            title.lines.joinToString("\n"),
            style = CardTitleStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** The app's banner (or icon on its colour) in the picture's corner, to show which app the show is in. */
@Composable
private fun Badge(art: TileArt?, modifier: Modifier) {
    when (art) {
        is TileArt.Banner -> Image(
            art.image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(40.dp, 22.dp).clip(BadgeShape),
        )
        is TileArt.Icon -> Box(modifier.size(40.dp, 22.dp).clip(BadgeShape).background(Color(art.background)), Alignment.Center) {
            Image(art.image, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        null -> Unit
    }
}

private val CardTitleStyle = TextStyle(fontSize = CARD_TITLE_SP.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
