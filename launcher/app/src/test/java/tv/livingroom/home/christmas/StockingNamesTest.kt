package tv.livingroom.home.christmas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StockingNamesTest {
    @Test
    fun noFileGivesThePlaceholders() {
        assertEquals(PLACEHOLDER_NAMES, stockingNames(null).names)
        assertEquals(PLACEHOLDER_NAMES, stockingNames("").names)
    }

    @Test
    fun blankOrMissingLinesArePlaceholdersAndExtrasAreIgnored() {
        assertEquals(listOf("Ann", "Pip", "Cy", "Ivy"), stockingNames(" Ann \n\nCy").names)
        assertEquals(listOf("A", "B", "C", "D"), stockingNames("A\nB\nC\nD\nE\n").names)
        assertEquals(listOf("A", "B", "C", "D"), stockingNames("A\r\nB\r\nC\r\nD").names)
        assertEquals("A", stockingNames("\uFEFFA\nB").names[0]) // A byte-order mark some editors add.
    }

    @Test
    fun aLongNameIsKeptWholeAndShrunkToFitTheCuff() {
        val long = "Bartholomewz"
        assertEquals(long, stockingNames(long).names[0])
        assertEquals(19f, cuffTextSize("Ann"), 0f)
        assertEquals(17f, cuffTextSize("Noelle"), 0f)
        assertTrue(cuffTextSize(long) * long.length * LETTER_WIDTH <= CUFF_TEXT_WIDTH + 1e-3f)
        assertTrue(cuffTextSize(long) < 17f)
    }

    @Test
    fun theNamesNeverShowInALog() {
        assertFalse(stockingNames("Secret").toString().contains("Secret"))
    }
}
