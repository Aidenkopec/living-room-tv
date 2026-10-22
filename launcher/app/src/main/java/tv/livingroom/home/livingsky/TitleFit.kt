package tv.livingroom.home.livingsky

/*
 * The top area's big title wraps onto two balanced lines rather than ending in "…", and gets
 * smaller only if two lines aren't enough (three small lines at the most). The widths come from
 * the screen's own text measurer; here they're just a function, so this runs in JVM tests.
 */

/** A title fitted to its space: its [lines], at [sizeSp]. [cut] means the words didn't fit and the last line ends in "…". */
data class TitleFit(val lines: List<String>, val sizeSp: Int, val cut: Boolean = false)

/**
 * [words] on two lines, as even as they can be: every break between words is
 * tried and the one whose longer line is shortest wins. On a tie, the shorter
 * first line (a heading reads better a little wider at the bottom).
 */
fun balancedLines(words: List<String>, width: (String) -> Float): List<String> {
    if (words.size < 2) return listOf(words.joinToString(" "))
    return (1 until words.size)
        .map { i -> listOf(words.subList(0, i).joinToString(" "), words.subList(i, words.size).joinToString(" ")) }
        .minBy { (first, second) -> maxOf(width(first), width(second)) } // The first of equals: the earliest break.
}

/**
 * The top area's [title] within [maxWidth]: one line, then two balanced lines,
 * at each of [sizes] in turn. If none fits, three lines at [LONG_TITLE_SP],
 * which take no more room than two at the biggest size (a single word too long
 * for a line stays whole; the screen then wraps it inside the word). Only a
 * title too long even for that (well over 100 characters) has its third line
 * cut, with [TitleFit.cut]: the rest of the words go on it, and the screen ends it in "…".
 */
fun fitTitle(title: String, maxWidth: Float, sizes: List<Int> = TITLE_SIZES, width: (text: String, sizeSp: Int) -> Float): TitleFit {
    val words = title.split(SPACES).filter { it.isNotEmpty() }
    if (words.isEmpty()) return TitleFit(listOf(""), sizes.first())
    oneOrTwoLines(words, maxWidth, sizes, width)?.let { return it }
    val lines = greedyLines(words, maxWidth) { width(it, LONG_TITLE_SP) }
    if (lines.size <= LONG_TITLE_LINES) return TitleFit(lines, LONG_TITLE_SP)
    return TitleFit(lines.take(LONG_TITLE_LINES - 1) + lines.drop(LONG_TITLE_LINES - 1).joinToString(" "), LONG_TITLE_SP, cut = true)
}

/**
 * A Continue watching card's [title] at 14 sp: one line or two balanced ones.
 * A title too long even for two keeps its first line, and the rest goes on the
 * second with [TitleFit.cut] (the card adds the "…"; the top area shows it whole).
 */
fun fitCardTitle(title: String, maxWidth: Float, width: (String) -> Float): TitleFit {
    val words = title.split(SPACES).filter { it.isNotEmpty() }
    if (words.isEmpty()) return TitleFit(listOf(""), CARD_TITLE_SP)
    oneOrTwoLines(words, maxWidth, listOf(CARD_TITLE_SP)) { text, _ -> width(text) }?.let { return it }
    val lines = greedyLines(words, maxWidth, width)
    if (lines.size <= 2) return TitleFit(lines, CARD_TITLE_SP)
    return TitleFit(listOf(lines.first(), lines.drop(1).joinToString(" ")), CARD_TITLE_SP, cut = true)
}

/**
 * A line's width from its words' widths and a space's. The screen measures
 * each word once this way, instead of every line [fitTitle] tries (dozens for a
 * long title, on every move onto its card).
 */
fun lineWidth(line: String, wordWidth: (String) -> Float, spaceWidth: Float): Float {
    val words = line.split(' ').filter { it.isNotEmpty() }
    return words.sumOf { wordWidth(it).toDouble() }.toFloat() + spaceWidth * (words.size - 1).coerceAtLeast(0)
}

/** [words] on one line, or two balanced ones, at the first of [sizes] where they fit; null if none. */
private fun oneOrTwoLines(words: List<String>, maxWidth: Float, sizes: List<Int>, width: (text: String, sizeSp: Int) -> Float): TitleFit? {
    for (size in sizes) {
        val one = words.joinToString(" ")
        if (width(one, size) <= maxWidth) return TitleFit(listOf(one), size)
        val two = balancedLines(words) { width(it, size) }
        if (two.all { width(it, size) <= maxWidth }) return TitleFit(two, size)
    }
    return null
}

/** The top area's title sizes, biggest first (40 sp is 80 px on the 1920 px screen). */
val TITLE_SIZES = listOf(40, 34, 28)

/** A title too long for two lines at 28 sp gets three at 24 sp: 3 x 25 sp of line height fits in 2 x 42. */
const val LONG_TITLE_SP = 24
const val LONG_TITLE_LINES = 3
const val CARD_TITLE_SP = 14

/** Fills each line with as many [words] as fit, in order. */
private fun greedyLines(words: List<String>, maxWidth: Float, width: (String) -> Float): List<String> {
    val lines = mutableListOf<String>()
    var line = ""
    for (word in words) {
        val longer = if (line.isEmpty()) word else "$line $word"
        if (line.isNotEmpty() && width(longer) > maxWidth) {
            lines += line
            line = word
        } else {
            line = longer
        }
    }
    return lines + line
}

private val SPACES = Regex("\\s+")
