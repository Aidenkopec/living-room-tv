package tv.livingroom.home.christmas

import kotlin.math.PI
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InkTest {
    private fun outlineOf(sketch: Sketch) = (sketch.marks.single() as FillMark).outline

    @Test
    fun inkPutsAlphaOnAColour() {
        assertEquals(0xFF123456.toInt(), ink(0x123456))
        assertEquals(0x80123456.toInt(), ink(0xFF123456.toInt(), 0.5f))
        assertEquals(0x00123456, ink(0x123456, -1f))
    }

    @Test
    fun circleClosesAndFillsItsBox() {
        val sketch = Sketch().apply { disc(ink(0xFFFFFF), 100f, 50f, 20f) }
        val outline = outlineOf(sketch)
        assertEquals(Outline.MOVE, outline.verbs.first())
        assertEquals(Outline.CLOSE, outline.verbs.last())
        assertEquals(4, outline.verbs.count { it == Outline.CUBIC })
        assertArrayEquals(floatArrayOf(80f, 30f, 120f, 70f), outline.bounds(), 0.01f)
    }

    @Test
    fun roundRectKeepsItsBoxAndSquareFeet() {
        val sketch = Sketch().apply { roundBox(ink(0), Area(10f, 20f, 40f, 60f), 6f, bottomRadius = 0f) }
        val outline = outlineOf(sketch)
        assertArrayEquals(floatArrayOf(10f, 20f, 50f, 80f), outline.bounds(), 0.01f)
        assertEquals(2, outline.verbs.count { it == Outline.CUBIC }) // Only the top two corners are round.
    }

    @Test
    fun aRadiusTooBigIsHeldToHalfTheBox() {
        val sketch = Sketch().apply { roundBox(ink(0), Area(0f, 0f, 20f, 8f), 11f) }
        assertArrayEquals(floatArrayOf(0f, 0f, 20f, 8f), outlineOf(sketch).bounds(), 0.01f)
    }

    @Test
    fun placedMovesScalesTurnsAndFlips() {
        val sketch = Sketch()
        sketch.placed(100f, 200f, scale = 2f) { sketch.shape(ink(0), 0f, 0f, 10f, 0f, 10f, 5f) }
        sketch.placed(0f, 0f, turn = (PI / 2).toFloat()) { sketch.shape(ink(0), 10f, 0f, 20f, 0f, 20f, 1f) }
        sketch.placed(50f, 0f, flip = true) { sketch.shape(ink(0), 0f, 0f, 10f, 0f, 10f, 5f) }
        val (scaled, turned, flipped) = sketch.marks.map { (it as FillMark).outline.points }
        assertArrayEquals(floatArrayOf(100f, 200f, 120f, 200f, 120f, 210f), scaled, 0.01f)
        assertArrayEquals(floatArrayOf(0f, 10f, 0f, 20f, -1f, 20f), turned, 0.01f) // A quarter turn clockwise on screen.
        assertArrayEquals(floatArrayOf(50f, 0f, 40f, 0f, 40f, 5f), flipped, 0.01f)
    }

    @Test
    fun placementsNestAndEnd() {
        val sketch = Sketch()
        sketch.placed(10f, 0f) { sketch.placed(0f, 5f, scale = 3f) { sketch.glow(1f, 1f, 2f, 0xFFFFFF, 1f) } }
        sketch.glow(1f, 1f, 2f, 0xFFFFFF, 1f)
        val (inside, after) = sketch.marks.map { it as GlowMark }
        assertEquals(13f, inside.x, 0.01f)
        assertEquals(8f, inside.y, 0.01f)
        assertEquals(6f, inside.radius, 0.01f)
        assertEquals(1f, after.x, 0.01f)
        assertEquals(2f, after.radius, 0.01f)
    }

    @Test
    fun linesGrowWithTheirPlacementAndFaintGlowsAreDropped() {
        val sketch = Sketch()
        sketch.placed(0f, 0f, scale = 0.5f) { sketch.line(ink(0), 8f) { through(0f, 0f, 10f, 0f) } }
        sketch.glow(0f, 0f, 10f, 0xFFFFFF, 0.001f)
        assertEquals(4f, (sketch.marks.single() as LineMark).width, 0.01f)
    }

    @Test
    fun arcRunsClockwiseFromThreeOClock() {
        val sketch = Sketch().apply { line(ink(0), 1f) { arc(Ellipse(0f, 0f, 10f, 10f), PI.toFloat(), 2 * PI.toFloat()) } }
        val outline = (sketch.marks.single() as LineMark).outline
        val box = outline.bounds()
        assertEquals(-10f, outline.points[0], 0.01f) // Starts at 9 o'clock...
        assertEquals(10f, outline.points[outline.points.size - 2], 0.01f) // ...ends at 3...
        assertEquals(-10f, box[1], 0.6f) // ...over the top.
        assertEquals(0f, box[3], 0.01f)
    }

    @Test
    fun aWordIsPlacedAndSizedButStaysUpright() {
        val sketch = Sketch()
        sketch.placed(100f, 50f, scale = 2f, turn = 1f) { sketch.word("Pip", 0f, 10f, WordStyle(12f, ink(0xFF0000), widest = 100f)) }
        val word = sketch.marks.single() as WordMark
        assertEquals(100 - 20 * kotlin.math.sin(1f), word.x, 1e-3f)
        assertEquals(50 + 20 * kotlin.math.cos(1f), word.y, 1e-3f)
        assertEquals(24f, word.size, 1e-4f)
        val box = marksBounds(listOf(word))
        assertEquals(24f, box.h, 1e-4f)
        assertTrue(box.w >= 3 * 24f * 0.6f)
        assertEquals(200f, word.style.widest, 1e-3f)
        val long = Sketch().apply { word("Bartholomew", 0f, 0f, WordStyle(20f, 0, widest = 64f)) }.marks
        assertEquals(64f, marksBounds(long).w, 1e-3f)
    }

    @Test
    fun squashedShortensHeightsOnly() {
        val sketch = Sketch()
        sketch.placed(10f, 100f) { sketch.squashed(0.5f) { sketch.box(ink(0), -4f, -40f, 8f, 40f) } }
        sketch.box(ink(0), 0f, 0f, 8f, 40f)
        val (squashed, after) = sketch.marks.map { (it as FillMark).outline.bounds() }
        assertArrayEquals(floatArrayOf(6f, 80f, 14f, 100f), squashed, 0.01f)
        assertArrayEquals(floatArrayOf(0f, 0f, 8f, 40f), after, 0.01f)
    }

    @Test
    fun aClippedFillKeepsItsClipThroughPlacements() {
        val sketch = Sketch()
        sketch.placed(10f, 0f) {
            sketch.clipped({ rect(0f, 0f, 10f, 10f) }) {
                sketch.placed(5f, 5f, scale = 2f) { sketch.box(ink(0), 0f, 0f, 10f, 10f) }
                sketch.line(ink(0), 2f) { through(0f, 0f, 30f, 0f) }
            }
        }
        sketch.box(ink(0), 0f, 0f, 1f, 1f)
        val (inside, line, after) = sketch.marks
        // The clip sits where things were placed when it was made, not where the fill inside it is.
        assertArrayEquals(floatArrayOf(10f, 0f, 20f, 10f), (inside as FillMark).clip!!.bounds(), 0.01f)
        assertArrayEquals(floatArrayOf(15f, 5f, 35f, 25f), inside.outline.bounds(), 0.01f)
        assertTrue(line is LineMark)
        assertEquals(null, (after as FillMark).clip)
    }

    @Test
    fun boundsCoverFillsLinesAndGlows() {
        assertEquals(Area(0f, 0f, 0f, 0f), marksBounds(emptyList()))
        val disc = Sketch().apply { disc(ink(0), 100f, 50f, 20f) }.marks
        assertEquals(Area(80f, 30f, 40f, 40f), marksBounds(disc).rounded())
        val line = Sketch().apply { line(ink(0), 6f) { through(0f, 0f, 10f, 0f) } }.marks
        assertEquals(Area(-3f, -3f, 16f, 6f), marksBounds(line).rounded())
        val glow = listOf<Mark>(GlowMark(5f, 5f, 20f, 0xFFFFFF, 1f))
        assertEquals(Area(-15f, -15f, 40f, 40f), marksBounds(glow).rounded())
        assertEquals(Area(-15f, -15f, 135f, 85f), marksBounds(disc + line + glow).rounded())
    }

    @Test
    fun boundsStopAtAClipAndMirrorWithAFlip() {
        val clipped = Sketch().apply { clipped({ rect(0f, 0f, 10f, 10f) }) { box(ink(0), 5f, -20f, 50f, 25f) } }.marks
        assertEquals(Area(5f, 0f, 5f, 5f), marksBounds(clipped).rounded())
        val missed = Sketch().apply { clipped({ rect(0f, 0f, 10f, 10f) }) { box(ink(0), 20f, 20f, 5f, 5f) } }.marks
        assertEquals(Area(0f, 0f, 0f, 0f), marksBounds(missed))

        fun wedge(flip: Boolean) = Sketch().apply { placed(0f, 0f, flip = flip) { shape(ink(0), -5f, 0f, 30f, 0f, 30f, 8f) } }.marks
        assertEquals(Area(-5f, 0f, 35f, 8f), marksBounds(wedge(flip = false)).rounded())
        assertEquals(Area(-30f, 0f, 35f, 8f), marksBounds(wedge(flip = true)).rounded())
    }

    private fun Area.rounded() = Area(Math.round(x).toFloat(), Math.round(y).toFloat(), Math.round(w).toFloat(), Math.round(h).toFloat())

    @Test
    fun mulberryIsTheSameEveryTimeAndStaysBelowOne() {
        val a = Mulberry(7)
        val b = Mulberry(7)
        repeat(1000) {
            val v = a.next()
            assertEquals(v, b.next(), 0f)
            assertTrue(v >= 0f && v < 1f)
        }
        // The values JavaScript's mulberry32(7) gives, so the village is laid out as its design was.
        val design = Mulberry(7)
        assertEquals(0.011704753f, design.next(), 1e-6f)
        assertEquals(0.06195826f, design.next(), 1e-6f)
        assertEquals(0.9769076f, design.next(), 1e-6f)
    }

    @Test
    fun linesAreClippedLikeFills() {
        val sketch = Sketch()
        sketch.clipped({ rect(0f, 0f, 10f, 10f) }) { sketch.line(ink(0x102030, 0.5f), 2f) { through(-20f, 5f, 30f, 5f) } }
        val line = sketch.marks.single() as LineMark
        assertArrayEquals(floatArrayOf(0f, 0f, 10f, 10f), line.clip!!.bounds(), 0.01f)
        // No further than the clip across, half its width either side of it down.
        assertEquals(Area(0f, 4f, 10f, 2f), marksBounds(sketch.marks).rounded())
    }
}
