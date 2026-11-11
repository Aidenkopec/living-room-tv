package tv.livingroom.home.christmas

import kotlin.math.abs
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VillagePeopleTest {
    private val adult = Villager()
    private val kid = Villager(kid = true)

    private fun Area.right() = x + w

    private fun Area.bottom() = y + h

    /** Where a figure's two legs end (x of each foot), from its two leg lines: the first lines drawn after the shadow. */
    private fun feet(who: Villager, swing: Float): List<Float> =
        personMarks(who, PersonPose(swing = swing)).filterIsInstance<LineMark>().take(2).map { it.outline.points[2] }

    @Test
    fun aGrownUpStandsInABoxAroundTheFeet() {
        val box = marksBounds(personMarks(adult, PersonPose()))
        assertTrue("left ${box.x}", box.x > -40f)
        assertTrue("right ${box.right()}", box.right() < 40f)
        assertTrue("top ${box.y}", box.y > -130f && box.y < -118f) // The bobble on the hat.
        assertTrue("bottom ${box.bottom()}", box.bottom() > 0f && box.bottom() < 8f) // The shadow, just under the feet.
    }

    @Test
    fun aTreeOverTheShoulderTrailsBehind() {
        val plain = marksBounds(personMarks(adult, PersonPose()))
        val tree = marksBounds(personMarks(adult, PersonPose(carry = Carry.TREE)))
        assertTrue("tree's top at ${tree.x}", tree.x < -55f && tree.x > -80f)
        assertEquals(plain.right(), tree.right(), 0.01f)
        assertEquals(plain.y, tree.y, 0.01f)
    }

    @Test
    fun aStrideSwingsTheLegsApartAndAcross() {
        // Mid-step the feet are together; at either end of the stride they're the same distance either side of the middle.
        assertEquals(listOf(-4f, 4f), feet(adult, 0f))
        assertEquals(listOf(-17f, 17f), feet(adult, 1f))
        assertEquals(listOf(9f, -9f), feet(adult, -1f))
        for (swing in listOf(-1f, -0.5f, 0.3f, 1f)) {
            val (back, front) = feet(adult, swing)
            assertEquals(-back, front, 0.001f)
        }
    }

    @Test
    fun aRunnerStridesLongerAndAKidShorter() {
        val walking = feet(kid, 1f)[1]
        val running = feet(kid.copy(run = true), 1f)[1]
        assertTrue(walking < feet(adult, 1f)[1])
        assertEquals(walking + 4.5f, running, 0.001f)
    }

    @Test
    fun aStrideLiftsTheWholeFigure() {
        val standing = marksBounds(personMarks(adult, PersonPose(carry = Carry.LEAD)))
        val striding = marksBounds(personMarks(adult, PersonPose(swing = 1f, carry = Carry.LEAD)))
        assertEquals(standing.y - 2.5f, striding.y, 0.01f)
        assertEquals(marksBounds(personMarks(adult, PersonPose(swing = -1f, carry = Carry.LEAD))).y, striding.y, 0.01f)
    }

    @Test
    fun aKidIsShorterThanAGrownUp() {
        val tall = marksBounds(personMarks(adult, PersonPose()))
        val small = marksBounds(personMarks(kid, PersonPose()))
        assertTrue(small.h < tall.h - 25f)
        assertEquals(tall.bottom(), small.bottom(), 0.01f)
    }

    @Test
    fun theHandOnALeadIsOutInFrontBelowTheShoulder() {
        val (x, y) = personHand(adult, PersonPose(carry = Carry.LEAD))
        assertTrue("x $x", x > 15f && x < 35f)
        assertTrue("y $y", y > -90f && y < -48f) // Between the shoulder and the hip.
        // The mitten is drawn where the hand is said to be.
        val mitten = personMarks(adult, PersonPose(carry = Carry.LEAD)).filterIsInstance<FillMark>().last().outline.bounds()
        assertEquals(x, (mitten[0] + mitten[2]) / 2, 0.01f)
        assertEquals(y, (mitten[1] + mitten[3]) / 2, 0.01f)
    }

    @Test
    fun theHandFollowsTheStridesLiftAndADuck() {
        val still = personHand(adult, PersonPose(carry = Carry.LEAD))
        val lifted = personHand(adult, PersonPose(swing = -1f, carry = Carry.LEAD))
        val ducked = personHand(adult, PersonPose(carry = Carry.LEAD, duck = 1f))
        assertEquals(still.first, lifted.first, 0.001f)
        assertEquals(still.second - 2.5f, lifted.second, 0.001f)
        assertEquals(still.second * 0.65f, ducked.second, 0.001f)
        assertTrue(marksBounds(personMarks(adult, PersonPose(duck = 1f))).h < marksBounds(personMarks(adult, PersonPose())).h * 0.7f)
    }

    @Test
    fun anEmptyHandSwingsAgainstTheStrideAndARunnersPumps() {
        assertTrue(personHand(adult, PersonPose(swing = 1f)).first < personHand(adult, PersonPose(swing = -1f)).first)
        val runner = adult.copy(run = true)
        assertTrue(personHand(runner, PersonPose(swing = 1f)).second < personHand(runner, PersonPose(swing = -1f)).second)
        // A wave is overhead, wherever the stride is.
        assertTrue(personHand(adult, PersonPose(carry = Carry.WAVE, arm = 2.5f)).second < -100f)
    }

    @Test
    fun everyLoadIsDrawnAndOnlyWhenCarried() {
        val empty = personMarks(adult, PersonPose()).size
        val added = Carry.entries.associateWith { personMarks(adult, PersonPose(carry = it)).size - empty }
        assertEquals(2, added[Carry.BAG])
        assertEquals(16, added[Carry.PARCELS])
        assertEquals(3, added[Carry.TREE])
        assertEquals(1, added[Carry.CUP])
        assertEquals(2, added[Carry.BOOK])
        for (carry in listOf(Carry.NONE, Carry.WAVE, Carry.THROW, Carry.WARM, Carry.LEAD)) assertEquals(0, added[carry])
        // Running, the arm pumps and the bag is left out.
        val runner = adult.copy(run = true)
        assertEquals(personMarks(runner, PersonPose()).size, personMarks(runner, PersonPose(carry = Carry.BAG)).size)
    }

    @Test
    fun singingOpensTheMouthAndTheScarfFlutters() {
        val quiet = personMarks(adult, PersonPose())
        assertEquals(quiet.size + 1, personMarks(adult, PersonPose(sing = 0f)).size)

        fun scarfTip(flutter: Float): Float {
            val scarf = personMarks(adult, PersonPose(flutter = flutter)).filterIsInstance<LineMark>()[2].outline.points
            return scarf[scarf.size - 1]
        }
        assertNotEquals(scarfTip(-1f), scarfTip(1f), 1f)
        // The tail's end stays within 5 of where it hangs (12 below the shoulder line, at -90).
        for (flutter in listOf(-1f, -0.4f, 0f, 0.7f, 1f, 4f)) assertTrue(abs(scarfTip(flutter) + 78f) <= 5.01f)
    }

    @Test
    fun figuresAreDrawnTheSameEveryTime() {
        val pose = PersonPose(swing = 0.4f, carry = Carry.PARCELS, flutter = 0.2f)
        assertEquals(marksBounds(personMarks(adult, pose)), marksBounds(personMarks(adult, pose)))
        val dog = DogPose(stride = 0.3f, tail = 0.5f, ball = true)
        assertEquals(marksBounds(dogMarks(Dog.BIG, dog)), marksBounds(dogMarks(Dog.BIG, dog)))
    }
}
