package tv.livingroom.home.christmas

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.livingroom.home.HomePlace

class FiresideTest {
    private val day = LocalDate.of(2026, 12, 10)
    private fun at(h: Int, m: Int, s: Int = 0) = ZonedDateTime.of(day, LocalTime.of(h, m, s), HomePlace.ZONE)
    private fun scene(h: Int, m: Int, code: Int? = null) = firesideScene(at(h, m), code)
    private val sheet = firesideSheet(stockingNames(null))
    private val sprites = sheet.sprites

    @Test
    fun theFourLooksCarryTheRoomsLight() {
        assertEquals(0xDFE6F5, MORNING.room and 0xFFFFFF)
        assertEquals(0.18f, AFTERNOON.roomK, 0f)
        assertEquals(1f, EVENING.fire, 0f)
        assertEquals(0.28f, MIDNIGHT.fire, 0f)
        assertEquals(1f, MIDNIGHT.dark, 0f)
        assertTrue(scene(12, 0).look.dark < 0.05f)
        val evening = scene(21, 0).look
        assertEquals(1f, evening.dark, 0.01f)
        assertEquals(1f, evening.fire, 0.01f)
    }

    @Test
    fun aMinutesSceneIsTheSameAllMinute() {
        assertEquals(firesideScene(at(18, 30, 5), null), firesideScene(at(18, 30, 55), null))
    }

    @Test
    fun lateNightIsElevenToFiveAndBurnsDownToEmbers() {
        assertFalse(scene(22, 59).late)
        assertTrue(scene(23, 0).late)
        assertTrue(scene(4, 59).late)
        assertFalse(scene(5, 0).late)
        assertFalse(firesideScene(at(23, 30), null, lateNightOn = false).late)
        assertEquals(EMBERS, scene(23, 30).fire, 0f)
        assertEquals(scene(18, 30).look.fire, scene(18, 30).fire, 0f)
    }

    @Test
    fun theRoomFitsThePictureAndItsEdgesAreDarkerAtNight() {
        for (s in listOf(scene(8, 40), scene(15, 10), scene(18, 30), scene(23, 30), scene(12, 0, code = 73))) {
            val marks = firesideMarks(s)
            assertTrue("${marks.size} marks", marks.size > 300)
            val box = marksBounds(marks.filter { it !is GlowMark }) // Soft glows may spill past the edges.
            assertTrue("$box", box.x >= -60 && box.y >= -60 && box.x + box.w <= VILLAGE_W + 60 && box.y + box.h <= VILLAGE_H + 60)
            assertTrue(windowLandMarks(s.look).isNotEmpty())
        }
        val day = scene(12, 0).look
        val night = scene(21, 0).look
        assertTrue(edgeDark(night, 0f, 0f) > edgeDark(day, 0f, 0f) + 0.3f)
        assertEquals(0f, edgeDark(night, 1000f, 700f), 0f)
        assertTrue(edgeDark(night, 0f, 52f) > edgeDark(night, 1000f, 340f))
    }

    @Test
    fun theSheetFitsAndADogsTailFramesShareABox() {
        assertTrue("${sheet.sheet.width} x ${sheet.sheet.height}", sheet.sheet.width <= 1024 && sheet.sheet.height <= 2048)
        for (s in sheet.sheet.sprites) assertTrue(s.w > 0 && s.h > 0)
        for (dog in listOf(sprites.bigDog, sprites.smallDog)) {
            assertEquals(TAIL_FRAMES, dog.tails.size)
            assertEquals(1, dog.tails.map { it.w to it.h }.distinct().size)
            assertEquals(1, dog.heads.map { it.w to it.h }.distinct().size)
        }
        assertEquals(STOCKINGS, sprites.stockings.size)
        assertEquals(BULBS.size + 1, sprites.lights.size)
    }

    /** The arch's top edge at [x], by sampling its curve. */
    private fun archTop(x: Float): Float {
        var best = Float.MAX_VALUE
        var bestY = 610f
        for (i in 0..1000) {
            val u = i / 1000f
            val v = 1 - u
            val cx = 858 * v.pow(3) + 3 * 858 * v * v * u + 3 * 1142 * v * u * u + 1142 * u.pow(3)
            val cy = 610 * v.pow(3) + 3 * 500 * v * v * u + 3 * 500 * v * u * u + 610 * u.pow(3)
            if (abs(cx - x) < best) {
                best = abs(cx - x)
                bestY = cy
            }
        }
        return bestY
    }

    private fun flameTop(fire: Float): Float {
        var top = Float.MAX_VALUE
        for (step in 0 until 200) {
            val frame = FiresideFrame()
            fireInto(frame, sprites, fire, step * 0.137)
            val q = frame.tongues
            for (i in 0 until q.count) {
                // A tongue's tip is the middle of its piece's top edge (corners 0 and 1); its sides bulge less than its piece is wide.
                val c = i * SpriteQuads.CORNER_VALUES
                val x = (q.corners[c] + q.corners[c + 2]) / 2
                val y = (q.corners[c + 1] + q.corners[c + 3]) / 2
                assertTrue("tip at $x", x in 870f..1130f)
                assertTrue("tip at $x, $y", y >= archTop(x))
                for (k in 0 until 4) assertTrue(q.corners[c + k * 2] in 840f..1160f)
                top = minOf(top, y)
            }
        }
        return top
    }

    @Test
    fun theFlamesStayInTheFireplaceAndBurnLowLateAtNight() {
        val evening = FIRE_FOOT - flameTop(1f)
        val late = FIRE_FOOT - flameTop(EMBERS)
        assertTrue(late < evening / 3)
        val none = FiresideFrame().also { fireInto(it, sprites, 0.02f, 1.0) }
        assertEquals(0, none.tongues.count)
        val weak = FiresideFrame().also { fireInto(it, sprites, 0.3f, 1.0) }
        val strong = FiresideFrame().also { fireInto(it, sprites, 1f, 1.0) }
        assertTrue(alpha(weak.glows, 0) < alpha(strong.glows, 0))
    }

    private fun alpha(q: SpriteQuads, i: Int) = (q.strengths[i * SpriteQuads.CORNERS] ushr 24) / 255f

    @Test
    fun sleepingDogsBreatheGentlyNotInStepAndStirNowAndThen() {
        val big = Sleep()
        val small = Sleep()
        var wagging = 0
        var opposite = false
        var flicked = false
        for (i in 0 until 800) {
            val t = i * 0.01 * 8
            sleepAt(Dog.BIG, t, big)
            sleepAt(Dog.SMALL, t, small)
            assertTrue(big.breath in 0.954f..1.046f)
            if (big.wag > 0.01f) wagging++
            if ((big.breath - 1) * (small.breath - 1) < 0) opposite = true
            if (big.ear > 0f) flicked = true
        }
        assertTrue("wags $wagging of 800", wagging < 800 / 4)
        assertTrue(opposite)
        assertTrue(flicked)
    }

    /** A frame with only the rug drawn: the dogs lying at home at [t], the fire [light] strong on their backs. */
    private fun rugFrame(t: Double, light: Float): FiresideFrame = FiresideFrame().also {
        it.now.t = t
        it.now.light = light
        rugInto(it, sprites)
    }

    @Test
    fun eachDogsZsRiseAboveItsOwnHead() {
        for (t in listOf(0.0, 1.3, 2.9)) {
            for ((dog, at) in listOf(sprites.bigDog to BIG_DOG_RUG, sprites.smallDog to SMALL_DOG_RUG)) {
                val frame = rugFrame(t, 1f)
                val withoutLight = rugFrame(t, 0f)
                assertEquals(2, frame.over.count - withoutLight.over.count) // Each back's firelight, only with a fire.
                // The z's are the over pieces after each dog's back light; check them by where they are.
                for (i in 0 until frame.over.count) {
                    val c = i * SpriteQuads.CORNER_VALUES
                    val w = frame.over.corners[c + 2] - frame.over.corners[c]
                    if (kotlin.math.abs(w) > 60) continue // A back light, not a z.
                    val x = (frame.over.corners[c] + frame.over.corners[c + 2]) / 2
                    val mine = if (dog === sprites.bigDog) x > 1000 else x < 1000
                    if (!mine) continue
                    val bottom = (0 until 4).maxOf { frame.over.corners[c + it * 2 + 1] }
                    assertTrue("z at $x, $bottom", bottom < at.y - 80 * at.scale)
                }
                // Two dogs of eight parts, the ball and its shadow, the blue present's box and lid.
                assertEquals(2 * 8 + 2 + 2, frame.room.count + frame.rug.count)
            }
        }
    }

    @Test
    fun theRoomIsPaintedAgainOnlyWhenItWouldLookDifferent() {
        // Deep in the night the look holds still and the moon creeps: most minutes need no new picture.
        val night = (0 until 60).map { paintKey(firesideScene(at(1, it), null)) }
        assertTrue("${night.distinct().size} pictures in an hour", night.distinct().size < 30)
        // But the weather, the late night or a change of light always does.
        assertTrue(paintKey(firesideScene(at(1, 0), null)) != paintKey(firesideScene(at(1, 0), 75)))
        assertTrue(paintKey(scene(22, 59)) != paintKey(scene(23, 0)))
        assertTrue(paintKey(scene(16, 30)) != paintKey(scene(17, 30)))
    }

    @Test
    fun theTreeBreathesSlowlyOnlyLateAtNight() {
        val late = scene(23, 30)
        assertEquals(1f, treeBreath(scene(18, 30), 3.0), 0f)
        val swing = (0 until 100).map { treeBreath(late, it * 0.1) }
        assertTrue(swing.max() - swing.min() > 0.5f)
        assertTrue(2 * Math.PI / LATE_BREATH_RATE > 10)
        assertTrue(roomTreeBrightness(late) < roomTreeBrightness(scene(18, 30)))
    }

    @Test
    fun aBusyFrameFitsItsLists() {
        val snowy = firesideScene(at(18, 30), 75)
        val frame = FiresideFrame()
        firesideFrameAt(frame, sprites, FiresideLive(snowy, roomTwinkles(snowy), FiresidePlan(1), FiresidePresses()), 12.0)
        for (q in listOf(frame.glows, frame.tongues, frame.flames, frame.room, frame.rug, frame.over, frame.snow, frame.light)) {
            assertTrue("${q.count} of ${q.capacity}", q.count < q.capacity || q.capacity == 1)
        }
        assertTrue("${frame.snow.count} flakes", frame.snow.count > 50)
        assertEquals(STOCKINGS + 1, frame.room.count) // The stockings and the logs.
        assertEquals(2 * 8 + 2 + 2, frame.rug.count) // Two dogs of eight parts, the ball and its shadow, the blue present.
    }
}
