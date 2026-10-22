package tv.livingroom.home.livingsky

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleFitTest {
    // A fake measure: every character the same width, in proportion to the size.
    private val width = { text: String, sizeSp: Int -> text.length * sizeSp * 0.55f }
    private val maxWidth = 540f // 24 characters at 40 sp, 28 at 34, 35 at 28.
    private fun fit(title: String) = fitTitle(title, maxWidth, width = width)

    @Test
    fun shortTitleOneBigLine() {
        assertEquals(TitleFit(listOf("Silver Coast"), 40), fit("Silver Coast"))
    }

    @Test
    fun jerseyShoreSplitsAtTheColon() {
        assertEquals(TitleFit(listOf("Harbor Watch:", "Family Reunions"), 40), fit("Harbor Watch: Family Reunions"))
    }

    @Test
    fun balancedNotGreedy() {
        // Greedy would put 4 words on the first line and 2 on the second.
        val lines = fit("The Quiet Season Of Northern Ferries").lines
        assertEquals(listOf("The Quiet Season", "Of Northern Ferries"), lines)
        val words = lines.map { it.split(' ') }
        assertTrue(abs(lines[0].length - lines[1].length) <= words.flatten().maxOf { it.length })
    }

    @Test
    fun tooLongShrinks() {
        // 54 characters: two lines at 40 sp hold 24 each, at 34 sp, 28 each.
        val fit = fit("A Much Longer Show Name That Needs Two Smaller Lines Now")
        assertEquals(34, fit.sizeSp)
        assertEquals(2, fit.lines.size)
        assertEquals(28, fit("A Much Longer Show Name That Needs Two Much Smaller Lines To Fit In").sizeSp)
    }

    @Test
    fun stillTooLongTakesThreeSmallerLines() {
        // Too long for two lines at 28 sp: three at 24 sp, which take no more room than two at 40.
        val title = "Sample show 4, with a much longer name than most shows have, to fill two lines and then some more"
        val fit = fit(title)
        assertEquals(24, fit.sizeSp)
        assertEquals(3, fit.lines.size)
        assertEquals(false, fit.cut)
        assertEquals(title, fit.lines.joinToString(" "))
        assertTrue(fit.lines.all { width(it, 24) <= maxWidth })
    }

    @Test
    fun veryLongTitleCutAfterThreeLines() {
        val title = (1..40).joinToString(" ") { "word$it" }
        val fit = fit(title)
        assertEquals(24, fit.sizeSp)
        assertEquals(3, fit.lines.size)
        assertEquals(true, fit.cut)
        assertEquals(title, fit.lines.joinToString(" ")) // Every word is there; the screen ends the third line in "…".
    }

    @Test
    fun neverTallerThanTwoBigLines() {
        // The top area has room for two lines at 40 sp; nothing may push the cards down.
        for (n in 1..60) {
            val fit = fit((1..n).joinToString(" ") { "word$it" })
            assertTrue("$n words: $fit", fit.lines.size * fit.sizeSp * 1.05f <= 2 * 40 * 1.05f + 0.01f)
        }
    }

    @Test
    fun unbreakableWordKept() {
        val word = "Supercalifragilisticexpialidociouslylonger"
        assertEquals(TitleFit(listOf(word), 24), fit(word))
    }

    @Test
    fun lineWidthAddsWordsAndSpaces() {
        assertEquals(3f + 5f + 2 * 1.5f + 4f, lineWidth("abc abcde abcd", { it.length.toFloat() }, spaceWidth = 1.5f))
        assertEquals(0f, lineWidth("", { it.length.toFloat() }, spaceWidth = 1.5f))
    }

    @Test
    fun emptyTitle() {
        assertEquals(TitleFit(listOf(""), 40), fit(""))
    }

    @Test
    fun extraSpacesDontMakeEmptyLines() {
        assertEquals(TitleFit(listOf("Silver Coast"), 40), fit("  Silver   Coast "))
    }

    // Cards: 14 sp only, 176 dp wide (about 22 characters with this measure).
    private val cardWidth = 176f
    private val cardMeasure = { text: String -> width(text, 14) }

    @Test
    fun cardTitleOneLine() {
        assertEquals(TitleFit(listOf("Silver Coast"), 14), fitCardTitle("Silver Coast", cardWidth, cardMeasure))
    }

    @Test
    fun cardTitleTwoLines() {
        assertEquals(
            TitleFit(listOf("The Quiet Season", "Of Northern Ferries"), 14),
            fitCardTitle("The Quiet Season Of Northern Ferries", cardWidth, cardMeasure),
        )
    }

    @Test
    fun cardTitleTooLongIsCut() {
        val fit = fitCardTitle("Sample show 4, with a much longer name than most shows have, to fill two lines", cardWidth, cardMeasure)
        assertEquals(2, fit.lines.size)
        assertEquals(true, fit.cut)
        assertTrue(fit.lines[0].let { cardMeasure(it) <= cardWidth })
    }

    @Test
    fun wordsNeverLost() {
        val words = "one two three four five six seven eight nine ten eleven twelve thirteen".split(' ')
        for (n in 1..words.size) {
            for (start in 0..(words.size - n)) {
                val title = words.subList(start, start + n).joinToString(" ")
                assertEquals(title, fit(title).lines.joinToString(" ")) // Cut or not, the lines hold every word.
                val card = fitCardTitle(title, cardWidth, cardMeasure)
                if (!card.cut) assertEquals(title, card.lines.joinToString(" "))
            }
        }
    }
}
