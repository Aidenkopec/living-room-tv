package tv.livingroom.home.christmas

import kotlin.math.PI
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpritesTest {
    private val sprite = Sprite(x = 10f, y = 20f, w = 8f, h = 4f, anchorX = 2f, anchorY = 3f)

    @Test
    fun sheetPiecesFitAndNeverTouch() {
        val sizes = List(40) { (7f + it % 9 * 5.5f) to (5f + it % 4 * 11.25f) }
        val layout = sheetLayout(sizes, maxWidth = 128, gap = 2)
        assertEquals(sizes.size, layout.cells.size)
        layout.cells.forEachIndexed { i, cell ->
            assertTrue("Wide enough", cell.w >= sizes[i].first && cell.h >= sizes[i].second)
            assertTrue(
                "Inside the sheet",
                cell.x >= 2 && cell.y >= 2 && cell.x + cell.w + 2 <= layout.width && cell.y + cell.h + 2 <= layout.height,
            )
            assertTrue("No wider than asked", cell.x + cell.w + 2 <= 128)
            for (other in layout.cells.drop(i + 1)) {
                val apartAcross = cell.x + cell.w + 2 <= other.x || other.x + other.w + 2 <= cell.x
                val apartDown = cell.y + cell.h + 2 <= other.y || other.y + other.h + 2 <= cell.y
                assertTrue("Cells $cell and $other are a gap apart", apartAcross || apartDown)
            }
        }
    }

    @Test
    fun aPieceWiderThanTheSheetStillGetsARowOfItsOwn() {
        val layout = sheetLayout(listOf(10f to 10f, 300f to 10f, 10f to 10f), maxWidth = 128)
        assertEquals(3, layout.cells.map { it.y }.distinct().size)
        assertTrue(layout.width >= 304)
    }

    @Test
    fun aSheetsSpritesAnchorWhereTheMarksOriginWas() {
        val maker = SheetMaker()
        val first = maker.add(Sketch().apply { disc(ink(0xFFFFFF), 0f, 0f, 5f) }.marks, Area(-6f, -6f, 12f, 12f))
        val second = maker.add(Sketch().apply { box(ink(0xFFFFFF), 0f, -30f, 10f, 30f) }.marks, Area(-1f, -31f, 12f, 32f))
        val sheet = maker.build(maxWidth = 64)
        assertEquals(6f, sheet.sprites[first].anchorX, 0f)
        assertEquals(31f, sheet.sprites[second].anchorY, 0f)
        // Each piece is painted so its own (0, 0) lands on its sprite's anchor.
        for (i in listOf(first, second)) {
            assertEquals(sheet.sprites[i].x + sheet.sprites[i].anchorX, sheet.pieces[i].x, 0f)
            assertEquals(sheet.sprites[i].y + sheet.sprites[i].anchorY, sheet.pieces[i].y, 0f)
        }
    }

    @Test
    fun aPieceIsPlacedByItsAnchor() {
        val quads = SpriteQuads(4)
        quads.add(sprite, 100f, 50f, scale = 2f, alpha = 0.5f)
        assertEquals(1, quads.count)
        assertArrayEquals(floatArrayOf(96f, 44f, 112f, 44f, 112f, 52f, 96f, 52f), quads.corners.copyOf(8), 0f)
        assertArrayEquals(floatArrayOf(10f, 20f, 18f, 20f, 18f, 24f, 10f, 24f), quads.sheet.copyOf(8), 0f)
        assertEquals(List(4) { 0x80FFFFFF.toInt() }, quads.strengths.take(4))
    }

    @Test
    fun aMirroredPieceFlipsAboutItsAnchor() {
        val quads = SpriteQuads(4)
        quads.addFacing(sprite, 100f, 50f, flipped = true)
        // The anchor is 2 from the piece's own left edge, which is now on the right.
        assertArrayEquals(floatArrayOf(102f, 47f, 94f, 47f, 94f, 51f, 102f, 51f), quads.corners.copyOf(8), 0f)
        assertArrayEquals(floatArrayOf(10f, 20f, 18f, 20f, 18f, 24f, 10f, 24f), quads.sheet.copyOf(8), 0f)
    }

    @Test
    fun aQuarterTurnSwingsThePieceRoundItsAnchor() {
        val quads = SpriteQuads(4)
        quads.addTurned(sprite, 100f, 50f, scale = 1f, turn = (PI / 2).toFloat())
        // Clockwise on screen (y down): the top left corner (-2, -3) from the anchor goes to (3, -2).
        assertArrayEquals(floatArrayOf(103f, 48f, 103f, 56f, 99f, 56f, 99f, 48f), quads.corners.copyOf(8), 1e-4f)
    }

    @Test
    fun aPiecePlacedThroughATransformMatchesThePlainWays() {
        val piece = Sprite(4f, 6f, 10f, 2f, anchorX = 3f, anchorY = 1f)
        val plain = SpriteQuads(1).apply { add(piece, 7f, 9f) }
        val through = SpriteQuads(1).apply { addThrough(piece, Turn2d().translate(7f, 9f)) }
        assertArrayEquals(plain.corners, through.corners, 1e-4f)
        assertArrayEquals(plain.sheet, through.sheet, 1e-4f)
        val turn = 0.7f
        val placed = SpriteQuads(1).apply { addPlaced(piece, 7f, 9f, Stretch(2f, 3f), turn) }
        val built = SpriteQuads(1).apply { addThrough(piece, Turn2d().translate(7f, 9f).rotate(turn).scale(2f, 3f)) }
        assertArrayEquals(placed.corners, built.corners, 1e-4f)
        // Painted at twice the size of the units it's placed in: drawn half as big.
        val painted = SpriteQuads(1).apply { addThrough(piece, Turn2d(), painted = 2f) }
        assertEquals(-1.5f, painted.corners[0], 1e-4f)
        assertEquals(3.5f, painted.corners[2], 1e-4f)
    }

    @Test
    fun dimmingDarkensOnlyTheNewPiecesAndKeepsTheirStrength() {
        val piece = Sprite(0f, 0f, 4f, 4f)
        val quads = SpriteQuads(4)
        quads.add(piece, 0f, 0f)
        quads.add(piece, 0f, 0f, alpha = 0.5f)
        quads.dimFrom(1, 0.5f)
        assertEquals(ink(0xFFFFFF), quads.strengths[0])
        assertEquals(ink(0x808080, 0.5f), quads.strengths[SpriteQuads.CORNERS])
    }

    @Test
    fun aSquashOutsideATurnShearsThePieceAsACanvasWould() {
        val piece = Sprite(0f, 0f, 10f, 0f, anchorX = 0f, anchorY = 0f)
        val quads = SpriteQuads(1).apply { addThrough(piece, Turn2d().scale(1f, 0.5f).rotate((PI / 4).toFloat())) }
        // The piece's far end (10, 0) turns to (7.07, 7.07), then the squash halves its height.
        assertEquals(7.0711f, quads.corners[2], 1e-3f)
        assertEquals(3.5355f, quads.corners[3], 1e-3f)
    }

    @Test
    fun aPlacedPieceStretchesEachWayThenTurns() {
        val piece = Sprite(0f, 0f, 10f, 2f, anchorX = 0f, anchorY = 0f)
        val plain = SpriteQuads(1).apply { addStretched(piece, 5f, 5f, 2f, 3f) }
        val placed = SpriteQuads(1).apply { addPlaced(piece, 5f, 5f, Stretch(2f, 3f)) }
        assertArrayEquals(plain.corners, placed.corners, 1e-4f)
        val turned = SpriteQuads(1).apply { addPlaced(piece, 0f, 0f, Stretch(1f, 1f), (PI / 2).toFloat()) }
        val xs = (0 until 4).map { turned.corners[it * 2] }
        val ys = (0 until 4).map { turned.corners[it * 2 + 1] }
        assertEquals(2f, xs.max() - xs.min(), 1e-4f)
        assertEquals(10f, ys.max() - ys.min(), 1e-4f)
        val mirrored = SpriteQuads(1).apply { addPlaced(piece, 0f, 0f, Stretch(-1f, 1f)) }
        assertEquals(-10f, mirrored.corners[2], 1e-4f)
        assertEquals(-1.5f, Stretch(-1.5f, 0.25f).x)
        assertEquals(0.25f, Stretch(-1.5f, 0.25f).y)
    }

    @Test
    fun invisiblePiecesAndPiecesOverTheLimitAreDropped() {
        val quads = SpriteQuads(2)
        quads.add(sprite, 0f, 0f, alpha = 0f)
        quads.add(sprite, 0f, 0f, scale = 0f)
        assertEquals(0, quads.count)
        repeat(5) { quads.add(sprite, it.toFloat(), 0f) }
        assertEquals(2, quads.count)
        quads.clear()
        assertEquals(0, quads.count)
    }

    @Test
    fun trianglesCoverEachPieceTwice() {
        val quads = SpriteQuads(3)
        assertEquals(listOf<Short>(0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7), quads.triangles.take(12))
    }

    @Test
    fun theMovingSheetsPiecesAreAllThereAndApart() {
        val plan = motionSheet()
        val sprites = plan.sheet.sprites
        assertEquals(3, plan.sprites.flakes.size)
        assertEquals(BULBS.size + 2, plan.sprites.lights.size)
        assertTrue("Fits a 1024 px texture across", plan.sheet.width <= 1024)
        // The whole village's life is in it; the home screen's memory has room for about 5.6 MB of it (measured on the TV).
        assertTrue(
            "No more than 5.6 MB of picture: ${plan.sheet.width} x ${plan.sheet.height}",
            plan.sheet.width * plan.sheet.height <= 1024 * 1400,
        )
        for (sprite in sprites) {
            assertTrue(
                sprite.x >= 0 && sprite.y >= 0 && sprite.x + sprite.w <= plan.sheet.width && sprite.y + sprite.h <= plan.sheet.height,
            )
        }
        // A flake's disc is at least as big as its depth's biggest flake, so flakes are only ever drawn smaller.
        plan.sprites.flakes.forEachIndexed { depth, disc -> assertTrue(disc.w / 2 >= SNOW_DEPTHS[depth].big) }
    }

    @Test
    fun theStressTestAddsTheNumberAskedFor() {
        val plan = motionSheet()
        for (count in listOf(0, 50, 400)) {
            val frame = MotionFrame()
            stressInto(frame, plan.sprites, count, t = 12.5)
            assertEquals(count, frame.lights.count + frame.lit.count + frame.plain.count)
        }
        val earlier = MotionFrame().also { stressInto(it, plan.sprites, 50, 1.0) }
        val later = MotionFrame().also { stressInto(it, plan.sprites, 50, 2.0) }
        assertTrue("Pieces move", earlier.plain.corners[0] != later.plain.corners[0])
    }
}
