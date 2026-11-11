package tv.livingroom.home.christmas

import android.content.Context
import android.util.Log
import tv.livingroom.home.TAG

/*
 * The names stitched on the four stockings over Fireside's fireplace, left to
 * right. They are personal, and the source is public, so they are read from a
 * local file that is never checked in (assets/christmas/stockings.txt, one
 * name a line); without it, or for a blank line, a placeholder shows.
 */

/** The four stockings' names, left to right: a person's, the small dog's, the big dog's, a person's. */
class StockingNames(val names: List<String>) {
    init {
        require(names.size == STOCKINGS) { "Four stockings, four names" }
    }

    override fun equals(other: Any?): Boolean = other is StockingNames && other.names == names

    override fun hashCode(): Int = names.hashCode()

    override fun toString(): String = "StockingNames(${names.size})" // Never the names themselves: logs can be shared.
}

/** What shows on a stocking whose name isn't in the file. */
val PLACEHOLDER_NAMES = listOf("Holly", "Pip", "Rex", "Ivy")

const val STOCKINGS = 4

/** The names in [text] (the file's contents, null if there's no file): a line each, trimmed; a blank or missing line is a placeholder. */
fun stockingNames(text: String?): StockingNames {
    val lines = text?.removePrefix(BYTE_ORDER_MARK)?.lines().orEmpty().map { it.trim() }
    return StockingNames(List(STOCKINGS) { i -> lines.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: PLACEHOLDER_NAMES[i] })
}

/**
 * The size of a name's letters on a stocking's cuff, in the stocking's own units: 19 for a short
 * name, 17 over five letters, and smaller still for a long one, so it always fits across the cuff.
 */
fun cuffTextSize(name: String): Float {
    val size = if (name.length > 5) 17f else 19f
    val fits = CUFF_TEXT_WIDTH / (name.length.coerceAtLeast(1) * LETTER_WIDTH)
    return minOf(size, fits)
}

/** How wide the cuff's writing may be, and a serif bold letter's width as a share of its size (a generous average). */
const val CUFF_TEXT_WIDTH = 64f
const val LETTER_WIDTH = 0.58f

/** The names from the app's local file, or placeholders. */
fun loadStockingNames(context: Context): StockingNames {
    val text = try {
        context.assets.open(NAMES_FILE).bufferedReader().use { it.readText() }
    } catch (e: Exception) { // Missing (the usual case without the file) or unreadable: placeholders either way.
        Log.i(TAG, "Fireside: no stocking names file (${e.javaClass.simpleName}), using placeholders")
        null
    }
    return stockingNames(text)
}

private const val NAMES_FILE = "christmas/stockings.txt"
private const val BYTE_ORDER_MARK = "\uFEFF" // Some editors start a text file with it.
