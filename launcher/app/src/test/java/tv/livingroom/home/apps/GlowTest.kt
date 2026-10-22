package tv.livingroom.home.apps

import kotlin.math.abs
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlowTest {
    private val black = 0xFF000000.toInt()
    private val netflixRed = 0xFFE50914.toInt()
    private val blue = 0xFF1F5F9A.toInt()
    private val orange = 0xFFC8783C.toInt()

    private fun pixels(vararg runs: Pair<Int, Int>) = runs.flatMap { (colour, count) -> List(count) { colour } }.toIntArray()

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun alpha(c: Int) = ((c ushr 24) and 0xFF) / 255f

    /** Hue in degrees, worked out by the JVM, not by the code under test. */
    private fun hue(c: Int) = java.awt.Color.RGBtoHSB(r(c), g(c), b(c), null)[0] * 360f

    private fun assertHueNear(expected: Float, actual: Int, within: Float) {
        val d = abs(expected - hue(actual)) % 360f
        val distance = min(d, 360f - d)
        assertTrue("hue ${hue(actual)} not within $within° of $expected°", distance <= within)
    }

    private fun luminance(c: Int) = 0.2126f * r(c) + 0.7152f * g(c) + 0.0722f * b(c)

    @Test
    fun redOnBlackGivesRed() {
        val colours = pickGlowColours(pixels(black to 80, netflixRed to 20))
        assertHueNear(357f, colours.primary, within = 10f)
    }

    @Test
    fun twoHuesGiveTwoColours() {
        val colours = pickGlowColours(pixels(blue to 50, orange to 30, black to 20))
        assertHueNear(hue(blue), colours.primary, within = 15f)
        assertHueNear(hue(orange), colours.secondary, within = 15f)
    }

    @Test
    fun greyBannerGivesNeutral() {
        val greys = pixels(0xFF1C1C1E.toInt() to 50, 0xFF3A3A3C.toInt() to 30, 0xFFF2F2F2.toInt() to 20)
        assertEquals(NEUTRAL_GLOW, pickGlowColours(greys))
    }

    @Test
    fun smallAccentGivesNeutral() {
        // Like Apple TV's banner: grey, with a small coloured logo (0.5% of it, measured).
        val greyWithAccent = pixels(0xFF2C2C2E.toInt() to 98, netflixRed to 2)
        assertEquals(NEUTRAL_GLOW, pickGlowColours(greyWithAccent))
    }

    @Test
    fun smallLogoOnWhiteKeepsItsColour() {
        // Like YouTube's banner: white, with a red logo (5.6%, measured).
        val colours = pickGlowColours(pixels(0xFFF2F2F2.toInt() to 94, netflixRed to 6))
        assertHueNear(357f, colours.primary, within = 10f)
    }

    @Test
    fun transparentPixelsIgnored() {
        val colours = pickGlowColours(pixels((netflixRed and 0x00FFFFFF) to 90, blue to 10))
        assertHueNear(hue(blue), colours.primary, within = 15f)
    }

    @Test
    fun emptyGivesNeutral() {
        assertEquals(NEUTRAL_GLOW, pickGlowColours(IntArray(0)))
    }

    @Test
    fun oneHueGivesADifferentDarkerSecondary() {
        val colours = pickGlowColours(pixels(blue to 100))
        assertTrue(colours.secondary != colours.primary)
        assertTrue(luminance(colours.secondary) < luminance(colours.primary))
    }
}
