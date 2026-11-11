package tv.livingroom.home.christmas

import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.livingroom.home.HomePlace

class FiresideVisitTest {
    private val pose = SantaPose()
    private val sprites = firesideSheet(stockingNames(null)).sprites

    @Test
    fun santaComesDownTheChimneyLandsAndGoesBackUp() {
        assertFalse(santaAt(5.9, 0.0, pose))
        assertTrue(santaAt(6.0, 0.0, pose))
        assertEquals(470f, pose.y, 0.01f) // Boots first, high in the chimney.
        assertFalse(pose.hat)
        santaAt(7.6, 0.0, pose)
        assertEquals(694f, pose.y, 0.01f) // Stuck at the belly.
        santaAt(8.22, 0.0, pose)
        assertEquals(806f, pose.y, 0.5f) // Landed.
        assertTrue(pose.inside)
        santaAt(9.2, 0.0, pose)
        assertFalse(pose.hat)
        santaAt(9.35, 0.0, pose)
        assertTrue(pose.hat) // It floated down onto his head.
        santaAt(12.0, 0.0, pose)
        assertFalse(pose.inside)
        assertEquals(898f, pose.y, 0f)
        assertEquals(SANTA_PAINTED, pose.s, 0f)
        assertTrue(pose.finger > 0.5f) // "Shh".
        santaAt(22.0, 0.0, pose)
        assertTrue(pose.wink)
        assertTrue(santaAt(23.0, 0.0, pose))
        assertTrue(pose.y < 300) // Up the chimney.
        assertFalse(santaAt(23.2, 0.0, pose))
    }

    @Test
    fun theFireGoesOutWhileHesHereAndRelights() {
        assertEquals(1f, visitFire(1.0), 0f)
        assertEquals(0f, visitFire(3.5), 1e-6f)
        for (st in listOf(4.0, 10.0, 23.3)) assertEquals(0f, visitFire(st), 0f)
        assertTrue(visitFire(24.0) > 0.5f)
        assertEquals(1f, visitFire(30.0), 1e-3f)
        assertTrue(relightFlash(23.5) > 0.8f)
        assertEquals(0f, relightFlash(25.0), 0f)
    }

    @Test
    fun theDogsWakeInTheSootAndSettleOnceHesGone() {
        assertEquals(0f, dogsAwake(3.0), 0f)
        assertTrue(smallDogAwake(3.0) > 0.4f) // The small one looks up at the sleigh's thump.
        assertEquals(1f, dogsAwake(12.0), 0f)
        assertEquals(0f, dogsAwake(VISIT_LENGTH - 1), 0f)
        assertTrue(dogsWag(15.0) > 0.9f)
        assertTrue(roomShake(2.0) > 0f)
        assertEquals(0f, roomShake(15.0), 0f)
    }

    @Test
    fun theStockingsFillOneAfterAnotherThenTheParcelThenTheBones() {
        for (i in 0 until 3) assertTrue(stockingFilled(i) < stockingFilled(i + 1))
        assertTrue(stockingFilled(3) < PARCEL_LANDS)
        assertTrue(PARCEL_LANDS < boneLands(0) && boneLands(0) < boneLands(1))
        assertTrue(boneLands(1) < VISIT_LENGTH)
    }

    @Test
    fun aWholeVisitFitsItsLayersAndLeavesTheRoomAsItWasPlusWhatHeBrought() {
        val scene = firesideScene(ZonedDateTime.of(2026, 12, 10, 18, 30, 0, 0, HomePlace.ZONE), 75)
        val live = FiresideLive(scene, roomTwinkles(scene), FiresidePlan(1, firstSanta = 2.0), FiresidePresses())
        val frame = FiresideFrame()
        var insideSeen = false
        var t = 0.0
        while (t < 2.0 + VISIT_LENGTH + 5) {
            firesideFrameAt(frame, sprites, live, t)
            val layers = listOf(frame.glows, frame.room, frame.inside, frame.insideLight, frame.santa, frame.rug, frame.over)
            for (q in layers + listOf(frame.snow, frame.windowLight, frame.fx, frame.light)) {
                assertTrue("${q.count} of ${q.capacity} at $t", q.count < q.capacity)
            }
            if (frame.inside.count > 0) insideSeen = true
            t += 1 / 30.0
        }
        assertTrue(insideSeen)
        // Afterwards: four stockings each with what he left, the bones and parcel on the rug, nothing in the chimney.
        assertEquals(0, frame.inside.count + frame.santa.count + frame.fx.count)
        assertEquals(STOCKINGS + 1 + 2 + 1 + 1 + 2, frame.room.count) // Stockings, the logs, two canes and parcels, two bones.
        assertEquals(2 * 8 + 2 + 2 + 2 + 2, frame.rug.count) // Dogs, the ball, the blue present, two bones, the parcel.
    }
}
