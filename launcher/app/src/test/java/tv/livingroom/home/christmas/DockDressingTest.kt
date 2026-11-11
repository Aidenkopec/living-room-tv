package tv.livingroom.home.christmas

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DockDressingTest {
    @Test
    fun countdownRunsFromDecemberFirstToChristmasDay() {
        assertNull(christmasLine(LocalDate.of(2026, 11, 30)))
        assertEquals("24 sleeps until Christmas", christmasLine(LocalDate.of(2026, 12, 1)))
        assertEquals("2 sleeps until Christmas", christmasLine(LocalDate.of(2026, 12, 23)))
        assertEquals("1 sleep until Christmas", christmasLine(LocalDate.of(2026, 12, 24)))
        assertEquals("Merry Christmas", christmasLine(LocalDate.of(2026, 12, 25)))
        assertNull(christmasLine(LocalDate.of(2026, 12, 26)))
        assertNull(christmasLine(LocalDate.of(2027, 1, 1)))
        assertNull(christmasLine(LocalDate.of(2026, 11, 10)))
    }

    @Test
    fun bulbsAreEvenlySpacedInsideTheBar() {
        val width = 1500f
        val count = dockBulbCount(width)
        assertEquals(34, count)
        val xs = List(count) { dockBulbX(it, count, width) }
        assertTrue(xs.first() > STRING_INSET && xs.last() < width - STRING_INSET)
        val gaps = xs.zipWithNext { a, b -> b - a }
        for (gap in gaps) assertEquals(gaps.first(), gap, 0.01f)
        assertEquals(xs.first() - STRING_INSET, width - STRING_INSET - xs.last(), 0.01f)
    }

    @Test
    fun stringPictureHoldsItsWireBulbsAndGlows() {
        val width = 1318f
        val marks = dockStringMarks(width, 1f)
        val count = dockBulbCount(width)
        assertEquals(count, marks.filterIsInstance<GlowMark>().size)
        assertEquals(count, marks.filterIsInstance<FillMark>().size)
        assertEquals(1, marks.filterIsInstance<LineMark>().size)
        for (glow in marks.filterIsInstance<GlowMark>()) {
            assertTrue(glow.x - glow.radius >= 0f && glow.x + glow.radius <= width)
            assertTrue(glow.y - glow.radius >= 0f && glow.y + glow.radius <= BULB_SIZE)
        }
        assertTrue(dockStringMarks(0f, 1f).filterIsInstance<GlowMark>().isEmpty())
    }

    @Test
    fun atlasPiecesFitAndNeverTouch() {
        val widths = listOf(208f, 208f, 208f, 117f, 117f)
        val atlas = dockAtlas(widths)
        val pieces = atlas.caps + atlas.hollyLeft + atlas.hollyRight + atlas.clump + atlas.powder
        assertEquals(widths.size, atlas.caps.size)
        atlas.caps.forEachIndexed { i, cap -> assertEquals(widths[i] + 2 * CAP_PAD_X, cap.w, 0.001f) }
        for (piece in pieces) {
            assertTrue(piece.x >= 0f && piece.y >= 0f && piece.x + piece.w <= atlas.width && piece.y + piece.h <= atlas.height)
        }
        for (a in pieces) {
            for (b in pieces) {
                val apart = a === b || a.x + a.w < b.x || b.x + b.w < a.x || a.y + a.h < b.y || b.y + b.h < a.y
                assertTrue("$a and $b overlap or touch", apart)
            }
        }
        // With no tiles at all there's still a picture to paint, holding just the holly.
        val empty = dockAtlas(emptyList())
        assertTrue(empty.caps.isEmpty() && empty.width >= 2 * HOLLY_W && empty.height == HOLLY_H)
    }

    @Test
    fun stringNeverGoesDark() {
        assertEquals(0.6f, dockLightsBrightness(MORNING.copy(lights = 0f)), 0.001f)
        assertEquals(1f, dockLightsBrightness(EVENING), 0.001f)
    }

    @Test
    fun snowCapCoversItsTileAndStaysInItsPicture() {
        for (width in listOf(117f, 208f)) {
            for (seed in 0 until 12) {
                val marks = snowCapMarks(width, seed, capColours(EVENING))
                val snow = marks.filterIsInstance<FillMark>()[1].outline // The mound itself, after its shadow.
                val xs = snow.points.filterIndexed { i, _ -> i % 2 == 0 }
                val ys = snow.points.filterIndexed { i, _ -> i % 2 == 1 }
                assertTrue(xs.min() <= CAP_PAD_X - 4 && xs.max() >= CAP_PAD_X + width + 4) // Overhangs both ends.
                assertTrue(xs.min() >= 0f && xs.max() <= width + 2 * CAP_PAD_X)
                assertTrue(ys.min() >= 0f)
                for (mark in marks.filterIsInstance<FillMark>()) assertTrue(mark.outline.bounds()[1] >= 0f)
            }
        }
    }

    @Test
    fun snowCapsDripsStayAboveTheNextPiece() {
        for (width in listOf(117f, 208f)) {
            for (seed in 0 until 12) {
                for (mark in snowCapMarks(width, seed, capColours(EVENING)).filterIsInstance<FillMark>()) {
                    assertTrue("width $width seed $seed reaches ${lowest(mark.outline)}", lowest(mark.outline) <= CAP_HEIGHT)
                }
            }
        }
    }

    /** The lowest y an outline really reaches: its line ends, and the low point of each curve (sampled along it). */
    private fun lowest(outline: Outline): Float {
        val p = outline.points
        var i = 0
        var lastX = 0f
        var lastY = 0f
        var low = -Float.MAX_VALUE
        for (verb in outline.verbs) {
            val used = pointsUsed(verb)
            if (verb == Outline.QUAD) {
                for (step in 1..8) {
                    val t = step / 8f
                    low = maxOf(low, (1 - t) * (1 - t) * lastY + 2 * t * (1 - t) * p[i + 1] + t * t * p[i + 3])
                }
            } else if (used > 0) {
                low = maxOf(low, p[i + used - 1])
            }
            if (used > 0) {
                lastX = p[i + used - 2]
                lastY = p[i + used - 1]
            }
            i += used
        }
        assertTrue(lastX.isFinite())
        return low
    }

    /** One wide tile at (400, 900), [scale] of its tallest, on a screen with [unit] pixels to a unit. */
    private fun oneTile(scale: Float, unit: Float, tallest: Float = 208f) = TileSpots().apply {
        resize(1)
        left[0] = 400f
        top[0] = 900f
        this.scale[0] = scale
        this.tallest[0] = tallest
    } to dockPieces(dockAtlas(listOf(208f)), listOf(208f), unit)

    @Test
    fun capSitsOnItsTilesTopEdgeAtAnySize() {
        for (unit in listOf(1f, 1.5f)) {
            for (scale in listOf(1f, 45f / 58.5f, 34f / 58.5f)) {
                val (spots, pieces) = oneTile(scale, unit)
                val quads = SpriteQuads(4).also { dockSnowInto(it, pieces, spots, floatArrayOf(1f)) }
                assertEquals(1, quads.count)
                val c = quads.corners
                val across = (c[2] - c[0]) / (208f + 2 * CAP_PAD_X)
                val down = (c[5] - c[1]) / CAP_HEIGHT
                assertEquals(400f, c[0] + CAP_PAD_X * across, 0.01f) // The tile's left edge...
                assertEquals(900f, c[1] + CAP_PAD_TOP * down, 0.01f) // ...and its top edge, where the picture has them.
                assertEquals(208f * unit * scale, c[2] - c[0] - 2 * CAP_PAD_X * across, 0.01f) // As wide as the tile is now.
                assertEquals(unit * capHeightScale(scale), down, 0.001f)
            }
        }
    }

    @Test
    fun thinnerSnowIsSquashedTowardTheTilesEdge() {
        val (spots, pieces) = oneTile(1f, 1f)
        fun cap(amount: Float) = SpriteQuads(4).also { dockSnowInto(it, pieces, spots, floatArrayOf(amount)) }
        val full = cap(1f).corners
        val half = cap(0.5f).corners
        assertEquals(full[0], half[0], 0f)
        assertEquals(full[2], half[2], 0f)
        assertTrue("Lower on top", half[1] > full[1] && half[1] < 900f)
        assertTrue("Higher underneath", half[5] < full[5] && half[5] > 900f)
        // The row that sits on the tile's top edge stays there.
        assertEquals(900f, half[1] + CAP_PAD_TOP * (half[5] - half[1]) / CAP_HEIGHT, 0.01f)
        assertEquals(0, cap(0.03f).count)
        assertEquals(1, cap(0.05f).count)
        // A tile the rider hasn't a number for yet has its full snow.
        assertEquals(1, SpriteQuads(4).also { dockSnowInto(it, pieces, spots, FloatArray(0)) }.count)
    }

    @Test
    fun capMadeForAnotherTileIsLeftOff() {
        assertTrue(capFits(208f, 208f))
        assertTrue(!capFits(208f, 117f)) // A square tile took a wide one's place.
        val (spots, pieces) = oneTile(1f, 1f, tallest = 117f)
        assertEquals(0, SpriteQuads(4).also { dockSnowInto(it, pieces, spots, floatArrayOf(1f)) }.count)
        // One more tile than there are caps.
        spots.resize(2)
        assertEquals(0, SpriteQuads(4).also { dockSnowInto(it, pieces, spots, floatArrayOf(1f, 1f)) }.count)
    }

    @Test
    fun hollySitsOnTheBarsTopCorners() {
        val pieces = dockPieces(dockAtlas(listOf(208f)), listOf(208f), 1f)
        val quads = SpriteQuads(4).also { dockHollyInto(it, pieces, DockBar(left = 300f, top = 928f, width = 1200f, height = 100f), 1f) }
        assertEquals(2, quads.count)
        // The left sprig's berries are 7 px in and 5 px down; the right one mirrors it.
        assertEquals(307f - HOLLY_X, quads.corners[0], 0.01f)
        assertEquals(933f - HOLLY_Y, quads.corners[1], 0.01f)
        assertEquals(1493f - (HOLLY_W - HOLLY_X), quads.corners[8], 0.01f)
        assertEquals(933f - HOLLY_Y, quads.corners[9], 0.01f)
    }

    @Test
    fun snowIsWhiterByDayThanAtNight() {
        fun blue(c: Int) = c and 0xFF
        fun red(c: Int) = (c shr 16) and 0xFF
        assertTrue(red(capColours(AFTERNOON).snow) > red(capColours(MIDNIGHT).snow))
        assertTrue(blue(capColours(MIDNIGHT).shadow) > red(capColours(MIDNIGHT).shadow))
    }

    @Test
    fun hollyFitsItsPictureBothWays() {
        for (flip in listOf(false, true)) {
            for (mark in hollyMarks(flip).filterIsInstance<FillMark>()) {
                val xs = mark.outline.points.filterIndexed { i, _ -> i % 2 == 0 }
                val ys = mark.outline.points.filterIndexed { i, _ -> i % 2 == 1 }
                assertTrue("x ${xs.min()}..${xs.max()} flip $flip", xs.min() >= 0f && xs.max() <= HOLLY_W)
                assertTrue("y ${ys.min()}..${ys.max()} flip $flip", ys.min() >= 0f && ys.max() <= HOLLY_H)
            }
        }
    }

    @Test
    fun smallTilesKeepMostOfTheirSnow() {
        assertEquals(1f, capHeightScale(1f), 0.001f)
        assertTrue(capHeightScale(34f / 58.5f) > 0.8f)
    }
}
