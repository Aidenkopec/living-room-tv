package tv.livingroom.home.livingsky

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import tv.livingroom.home.sky.CORNER_TEXT_ALPHA

private val Pill = RoundedCornerShape(50)

/** The words in the top area, in line with the rows' left edge and half the screen wide: one line, the title, one line. */
@Composable
fun TopAreaCopy(text: TopAreaText, modifier: Modifier = Modifier) {
    val measure = rememberWordMeasure(::titleStyle)
    val density = LocalDensity.current
    // Two balanced lines rather than "…", smaller only if two aren't enough (see fitTitle).
    val title = remember(text.title, measure) { fitTitle(text.title, with(density) { TitleWidth.toPx() } * FIT_MARGIN, width = measure) }
    Column(modifier.padding(start = 43.dp, top = 56.dp).width(TitleWidth)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(text.greeting) }
                    text.lead?.let { append(" $it") }
                },
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 16.sp,
            )
            text.chip?.let {
                Text(
                    it,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(Color.White.copy(alpha = 0.14f), Pill)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Text(
            title.lines.joinToString("\n"),
            style = titleStyle(title.sizeSp),
            maxLines = LONG_TITLE_LINES,
            overflow = TextOverflow.Ellipsis, // Only a title of well over 100 characters (see fitTitle).
            modifier = Modifier.padding(top = 10.dp),
        )
        text.meta?.let {
            Text(
                it,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/**
 * Weather (when there is any), date and clock in the top-right corner, and
 * under them the [skyLine] ("Sunset 6:33 PM", "Moon 74% full") when there is one.
 * Its right edge is 43 dp in, lined up with the rows' left edge.
 */
@Composable
fun Corner(weather: String?, date: String, time: String, skyLine: String?, modifier: Modifier = Modifier) {
    val dim = Color.White.copy(alpha = CORNER_TEXT_ALPHA)
    Box(modifier.fillMaxWidth().padding(top = 25.dp, end = 43.dp), contentAlignment = Alignment.TopEnd) {
        Column(horizontalAlignment = Alignment.End) {
            Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                weather?.let { Text(it, color = dim, fontSize = 14.sp, modifier = Modifier.alignByBaseline()) }
                Text(date, color = dim, fontSize = 14.sp, modifier = Modifier.alignByBaseline())
                Text(time, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.alignByBaseline())
            }
            skyLine?.let { Text(it, color = dim, fontSize = 14.sp) }
        }
    }
}

private val TitleWidth = 540.dp

/**
 * A width function for [fitTitle] and [fitCardTitle] that measures each word
 * once per size (and a space), then adds them up ([lineWidth]): measuring every
 * line tried would take dozens of measures on the main thread for each move onto a long title.
 */
@Composable
fun rememberWordMeasure(style: (sizeSp: Int) -> TextStyle): (text: String, sizeSp: Int) -> Float {
    val measurer = rememberTextMeasurer()
    return remember(measurer) {
        val words = HashMap<Pair<String, Int>, Float>()
        val spaces = HashMap<Int, Float>()
        val measure = { text: String, sizeSp: Int ->
            measurer.measure(text, style(sizeSp), softWrap = false, maxLines = 1).size.width.toFloat()
        }
        val width: (String, Int) -> Float = { text, sizeSp ->
            val space = spaces.getOrPut(sizeSp) { measure("x x", sizeSp) - measure("xx", sizeSp) }
            lineWidth(text, { word -> words.getOrPut(word to sizeSp) { measure(word, sizeSp) } }, space)
        }
        width
    }
}

/** Measured line widths leave out kerning between words, so fitting aims a little short of the edge. */
private const val FIT_MARGIN = 0.97f

/** The big title at [sizeSp], for drawing and for measuring alike. */
private fun titleStyle(sizeSp: Int) = TextStyle(
    fontSize = sizeSp.sp,
    lineHeight = (sizeSp * 1.05f).sp,
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.035).em,
)
