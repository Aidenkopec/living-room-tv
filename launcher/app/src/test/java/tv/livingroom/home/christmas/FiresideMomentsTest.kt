package tv.livingroom.home.christmas

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FiresideMomentsTest {
    private fun actsAt(moment: Moment, dog: Dog, v: Double): RugActs = RugActs().also {
        it.small.atHome(SMALL_DOG_RUG, 0f, 0f)
        it.big.atHome(BIG_DOG_RUG, 0f, 0f)
        it.ball.atRest()
        momentInto(moment, dog, v, it)
    }

    private fun assertAtHome(act: DogAct, place: RugPlace, label: String) {
        assertEquals("$label x", place.x, act.x, 0.5f)
        assertEquals("$label y", place.y, act.y, 0.5f)
        assertEquals("$label up", 0f, act.up, 1e-3f)
        assertEquals("$label awake", 0f, act.awake, 1e-3f)
        assertEquals("$label paddle", 0f, act.paddle, 1e-3f)
        assertEquals("$label roll", 0f, act.roll, 1e-3f)
        assertEquals("$label facing", place.facesRight, act.flip)
        assertFalse("$label pant", act.pant)
    }

    @Test
    fun everyMomentStartsAndEndsWithBothDogsLyingAtHome() {
        for (moment in Moment.entries) {
            for (dog in Dog.entries) {
                for (v in listOf(0.0, moment.length)) {
                    val acts = actsAt(moment, dog, v)
                    assertAtHome(acts.small, SMALL_DOG_RUG, "$moment small at $v")
                    assertAtHome(acts.big, BIG_DOG_RUG, "$moment big at $v")
                    assertEquals(BALL_X, acts.ball.x, 0.5f)
                    assertEquals(BALL_Y, acts.ball.y, 0.5f)
                }
            }
        }
    }

    @Test
    fun theSmallDogRollsOntoHisBackAndWriggles() {
        val over = actsAt(Moment.ROLL, Dog.SMALL, 4.5).small
        assertEquals(1f, over.roll, 1e-3f)
        assertEquals(1f, over.wriggle, 1e-3f)
        assertEquals(SMALL_DOG_RUG.x, over.x, 0.5f) // He rolls where he lies.
        assertTrue(actsAt(Moment.ROLL, Dog.SMALL, (ROLL_OVER_FROM + ROLL_OVER_TO) / 2).small.roll in 0.2f..0.8f)
        assertEquals(0f, actsAt(Moment.ROLL, Dog.SMALL, ROLL_BACK_TO + 0.1).small.roll, 1e-3f)
        assertTrue(actsAt(Moment.DREAM, Dog.BIG, 3.0).big.paddle > 0.9f)
    }

    @Test
    fun theZoomiesGoRoundTheRugTwiceAndHome() {
        val bow = actsAt(Moment.ZOOMIES, Dog.BIG, 2.9)
        assertTrue(bow.small.bow > 0.9f && bow.small.up > 0.9f)
        assertTrue(actsAt(Moment.ZOOMIES, Dog.BIG, 5.2).big.bow > 0.9f) // He bows back.
        // Round the rug: seen at many moments, he gets to both ends and both lanes, galloping and panting.
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var back = Float.MAX_VALUE
        var front = -Float.MAX_VALUE
        var turns = 0
        var lastFlip: Boolean? = null
        var v = ZOOM_FROM + 1.0
        while (v < ZOOM_TO - 1.0) {
            val m = actsAt(Moment.ZOOMIES, Dog.BIG, v).big
            assertTrue(m.gallop && m.walking && m.pant && m.up == 1f)
            left = minOf(left, m.x)
            right = maxOf(right, m.x)
            back = minOf(back, m.y)
            front = maxOf(front, m.y)
            if (lastFlip != null && lastFlip != m.flip) turns++
            lastFlip = m.flip
            v += 0.02
        }
        assertTrue("left $left", left < 600f)
        assertTrue("right $right", right > 1250f)
        assertTrue(back < 920f && front > 1010f)
        assertTrue("turns $turns", turns >= 3) // Two laps: he turns at each end.
        assertEquals(BIG_DOG_RUG.x, actsAt(Moment.ZOOMIES, Dog.BIG, ZOOM_TO).big.x, 0.5f)
    }

    @Test
    fun theGallopKeepsPaceWithTheFloor() {
        // From one moment to the next he moves about as far as his strides carry him.
        val a = actsAt(Moment.ZOOMIES, Dog.BIG, 9.0).big
        val b = actsAt(Moment.ZOOMIES, Dog.BIG, 9.1).big
        val moved = hypot(b.x - a.x, b.y - a.y)
        val strides = b.walk - a.walk
        assertTrue("moved $moved in $strides strides", abs(moved / strides - 250f) < 60f)
    }

    @Test
    fun theSmallDogWatchesTheBigOneRun() {
        var v = ZOOM_FROM + 0.5
        while (v < ZOOM_TO) {
            val acts = actsAt(Moment.ZOOMIES, Dog.BIG, v)
            assertEquals("at $v", acts.big.x > acts.small.x, acts.small.flip)
            v += 0.25
        }
    }

    @Test
    fun aDogOnItsFeetIsDrawnFromItsStandingPieces() {
        val sprites = firesideSheet(stockingNames(null)).sprites
        val frame = FiresideFrame()
        frame.now.t = 3.0
        frame.now.moment = PlannedMoment(0.0, Moment.ZOOMIES, Dog.BIG)
        rugInto(frame, sprites)
        // The small dog standing: a shadow and his 19 pieces; the big one lying: eight; the ball and its shadow; the blue present.
        assertEquals(20 + 8 + 2 + 2, frame.rug.count)
    }

    @Test
    fun aDogOnHisBackIsDrawnFromHisBellyPieces() {
        val sprites = firesideSheet(stockingNames(null)).sprites
        val frame = FiresideFrame()
        frame.now.t = 4.5
        frame.now.moment = PlannedMoment(0.0, Moment.ROLL, Dog.SMALL)
        rugInto(frame, sprites)
        // On his back: shadow, tail and six belly pieces; the big one lying: eight; the ball and its shadow; the blue present.
        assertEquals(8 + 8 + 2 + 2, frame.rug.count)
    }
}
