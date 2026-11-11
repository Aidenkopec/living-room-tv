package tv.livingroom.home.christmas

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.livingroom.home.HomePlace

class WalkersTest {
    private val sprites = motionSheet().sprites
    private val at = FloatArray(3)

    private fun x(walker: Walker, t: Double): Float {
        walkerAt(walker, t, at)
        return at[0]
    }

    @Test
    fun theVillageKeepsItsHours() {
        val sunrise = LocalTime.of(8, 36)
        val sunset = LocalTime.of(16, 32)
        fun hours(h: Int, m: Int) = villageHours(LocalTime.of(h, m), sunrise, sunset)
        assertEquals(VillageHours.NIGHT, hours(0, 5))
        assertEquals(VillageHours.NIGHT, hours(8, 35))
        assertEquals(VillageHours.MORNING, hours(8, 36))
        assertEquals(VillageHours.MORNING, hours(11, 59))
        assertEquals(VillageHours.AFTERNOON, hours(12, 0))
        assertEquals(VillageHours.AFTERNOON, hours(16, 31))
        assertEquals(VillageHours.EVENING, hours(16, 32))
        assertEquals(VillageHours.EVENING, hours(21, 59))
        assertEquals(VillageHours.NIGHT, hours(22, 0))
    }

    @Test
    fun theScenesHoursAreThePlacesOwn() {
        fun scene(h: Int, m: Int) = villageScene(ZonedDateTime.of(LocalDate.of(2026, 12, 24), LocalTime.of(h, m), HomePlace.ZONE), null)
        assertEquals(VillageHours.MORNING, scene(10, 30).hours)
        assertEquals(VillageHours.AFTERNOON, scene(16, 10).hours)
        assertEquals(VillageHours.EVENING, scene(19, 30).hours)
        assertEquals(VillageHours.NIGHT, scene(23, 0).hours)
        // A screen an hour ahead of the place shows the place's hours, not its own clock's.
        val ahead = ZonedDateTime.of(LocalDate.of(2026, 12, 24), LocalTime.of(22, 30), java.time.ZoneId.of("America/New_York"))
        assertEquals(VillageHours.EVENING, villageScene(ahead, null).hours)
    }

    @Test
    fun walkersAreListedFurthestFirst() {
        assertEquals(WALKERS.map { it.lane }.sorted(), WALKERS.map { it.lane })
        assertEquals("Each has a lane of their own", WALKERS.size, WALKERS.map { it.lane }.distinct().size)
        // In front of the tree, the stalls, the lamps, the post box and the snowman (their feet are at 838 or above).
        assertTrue(WALKERS.all { it.lane > 840f && it.lane < WALK_Y })
    }

    @Test
    fun aWalkerMovesAtTheirOwnPace() {
        for (walker in WALKERS.filter { it.stopAt == null }) {
            for (t in listOf(3.0, 500.25, 90_000.0)) {
                val moved = x(walker, t + 1) - x(walker, t)
                if (abs(moved) > 500) continue // Round the lap's end.
                assertEquals(walker.speed, moved, 0.02f)
            }
        }
    }

    @Test
    fun nobodyEverJumpsWhileOnThePicture() {
        for (walker in WALKERS) {
            var last = x(walker, 0.0)
            for (frame in 1..(400 * 60)) {
                val now = x(walker, frame / 60.0)
                val wrapped = abs(now - last) > 100
                if (wrapped) {
                    assertTrue("Wraps unseen: $last to $now", offPicture(last) && offPicture(now))
                } else {
                    assertTrue(abs(now - last) < 1.2f)
                }
                last = now
            }
        }
    }

    @Test
    fun someoneWhoStopsStandsStillAtTheirStallForTheirTime() {
        for (walker in WALKERS.filter { it.stopAt != null }) {
            var standing = 0
            var steps = 0
            val lap = (VILLAGE_W + 2 * LAP_EDGE) / abs(walker.speed) + walker.stopFor
            for (frame in 0 until (lap * 60).toInt()) {
                walkerAt(walker, frame / 60.0, at)
                if (at[1] > 0f) {
                    standing++
                    assertEquals(walker.stopAt!!, at[0], 0.01f)
                }
                steps++
            }
            assertEquals(walker.stopFor, standing / 60f, 0.05f)
            assertTrue(steps > standing)
        }
        // The two who stop at the stalls stand at them; the child stops at the post box.
        assertEquals(listOf(742f, 1252f, 836f), WALKERS.mapNotNull { it.stopAt })
    }

    @Test
    fun legsKeepStepWithTheGround() {
        val walker = WALKERS.first { it.stopAt == null && !it.who.run }
        walkerAt(walker, 10.0, at)
        val before = at[2]
        walkerAt(walker, 10.0 + 13 / abs(walker.speed).toDouble(), at) // 13 px on: half a 26 px stride.
        assertEquals(0.5f, abs(at[2] - before), 0.01f)
        assertEquals(listOf(3, 4, 5, 6, 5, 4, 3, 2, 1, 0, 1, 2), (0 until 12).map { strideFrame(it / 12.0 + 0.001) })
        assertEquals(strideFrame(0.3), strideFrame(7.3))
    }

    private fun out(hours: VillageHours, cast: VillageCast = VillageCast(), t: Double = 50.0): Int {
        val frame = MotionFrame()
        walkersInto(frame, sprites.cast.walkers, cast, hours, t)
        cast.started()
        return frame.lit.count
    }

    @Test
    fun whoIsOutFollowsTheVillagesHours() {
        assertEquals(5, out(VillageHours.MORNING))
        assertEquals(10, out(VillageHours.AFTERNOON))
        assertEquals(9, out(VillageHours.EVENING))
        assertEquals(0, out(VillageHours.NIGHT))
    }

    @Test
    fun peopleOnlyJoinOrLeaveWhileOffThePicture() {
        val cast = VillageCast()
        var t = 50.0
        out(VillageHours.AFTERNOON, cast, t)
        val wasOut = cast.out.copyOf()
        // Night falls: whoever is on the picture finishes their crossing; whoever is off it has gone in.
        var seen = 0
        while (t < 50.0 + 200) {
            t += 1 / 60.0
            for (i in WALKERS.indices) {
                val before = cast.out[i]
                val onPicture = !offPicture(x(WALKERS[i], t))
                cast.settle(i, !onPicture, due = false)
                if (before && !cast.out[i]) assertFalse("Walker $i left while on the picture", onPicture)
                if (cast.out[i]) seen++
            }
        }
        assertTrue(wasOut.any { it } && seen > 0)
        assertTrue("Everyone has gone in within a lap", cast.out.none { it })
        // And nobody comes out mid-picture when morning comes.
        for (i in WALKERS.indices) assertFalse(cast.settle(i, offPicture = false, due = true))
        assertTrue(cast.settle(0, offPicture = true, due = true))
        // After the screen has been away, the scene starts afresh.
        cast.restart()
        assertTrue(cast.settle(1, offPicture = false, due = true))
    }

    @Test
    fun theDogWalkStopsTwiceALap() {
        val walk = DogWalk()
        val lap = (VILLAGE_W + 400) / 24.0 + 10.5
        var flakes = 0
        var snowman = 0
        var last = walk.at(0.0).x
        for (frame in 1..(lap * 60).toInt()) {
            walk.at(frame / 60.0)
            when (walk.stop) {
                WalkStop.SNOWFLAKES -> {
                    flakes++
                    assertEquals(620f, walk.x, 0.01f)
                }
                WalkStop.SNOWMAN -> {
                    snowman++
                    assertEquals(1028f, walk.x, 0.01f)
                }
                WalkStop.NONE -> Unit
            }
            val moved = walk.x - last
            assertTrue("Never backwards or jumping on the picture: $moved", (moved >= 0f && moved < 0.5f) || offPicture(last, WALK_REACH))
            last = walk.x
        }
        assertEquals(4.5f, flakes / 60f, 0.05f)
        assertEquals(6f, snowman / 60f, 0.05f)
    }

    @Test
    fun theSmallDogJumpsAtSnowflakesAndBowsAtTheSnowman() {
        val walk = DogWalk()
        val scale = personScale(WALK_Y)
        val firstStop = (820 / 24.0) - 40
        walk.at(firstStop + 0.37)
        assertEquals(WalkStop.SNOWFLAKES, walk.stop)
        assertTrue(walk.jump(scale) > 8f && walk.jump(scale) <= 16f * scale)
        assertEquals(0f, walk.bow(), 0f)
        val secondStop = 1228 / 24.0 + 4.5 - 40
        assertEquals(0f, walk.at(secondStop + 0.3).bow(), 0f)
        assertEquals(1f, walk.at(secondStop + 2.5).bow(), 0f)
        assertEquals(0f, walk.at(secondStop + 5.5).bow(), 0.001f)
        assertEquals(0f, walk.jump(scale), 0f)
        // The big dog and its walker are in front of the snowman there.
        assertTrue(abs(walk.x + BIG_DOG_X - 1118f) < 20f)
    }

    @Test
    fun theDogWalkIsTwoPeopleTwoDogsTheirShadowsAndTwoLeads() {
        val frame = MotionFrame()
        val cast = VillageCast()
        dogWalkInto(frame, sprites.cast, cast, VillageHours.AFTERNOON, 30.0)
        assertEquals(6, frame.lit.count)
        assertEquals(2 * 4 * 4, frame.leadValues)
        // Each lead runs from above the ground at a walker to a dog further right, in one unbroken line.
        for (lead in 0..1) {
            val from = lead * 16
            assertTrue(frame.leads[from + 14] > frame.leads[from])
            assertTrue(frame.leads[from + 1] < WALK_Y - 20 && frame.leads[from + 15] < WALK_Y - 10)
            for (part in 1..3) {
                assertEquals(frame.leads[from + part * 4 - 2], frame.leads[from + part * 4], 0f)
                assertEquals(frame.leads[from + part * 4 - 1], frame.leads[from + part * 4 + 1], 0f)
            }
        }
        val night = MotionFrame().also { dogWalkInto(it, sprites.cast, VillageCast(), VillageHours.NIGHT, 30.0) }
        assertEquals(0, night.lit.count + night.leadValues)
    }

    @Test
    fun theDogWalkShowsADifferentPictureAtEachStop() {
        fun dogs(t: Double): List<Float> {
            val frame = MotionFrame().also { dogWalkInto(it, sprites.cast, VillageCast(), VillageHours.MORNING, t) }
            // The sheet corner of each piece says which picture it is: second walker, first walker, shadow, big dog, shadow, small dog.
            return (0 until frame.lit.count).flatMap { listOf(frame.lit.sheet[it * 8], frame.lit.sheet[it * 8 + 1]) }
        }
        val walking = dogs(20.0)
        val atFlakes = dogs(820 / 24.0 - 40 + 1)
        val atSnowman = dogs(1250 / 24.0 + 4.5 - 40 + 2.5)
        assertTrue(walking != atFlakes && atFlakes != atSnowman)
        // At the snowman the second walker waves and the small dog bows: neither picture is used anywhere else.
        assertTrue(atSnowman.subList(0, 2) != atFlakes.subList(0, 2))
        assertTrue(atSnowman.subList(10, 12) != atFlakes.subList(10, 12))
    }

    @Test
    fun everyFigureHasItsFrames() {
        assertEquals(WALKERS.size, sprites.cast.walkers.size)
        for (frames in sprites.cast.walkers) {
            assertEquals(7, frames.stride.size)
            // One box for all of a walker's frames, so they don't shift as they change.
            assertEquals(1, (frames.stride + frames.standing).map { listOf(it.w, it.h, it.anchorX, it.anchorY) }.distinct().size)
        }
        assertEquals(3, sprites.cast.dogWalkers.waving.size)
        assertEquals(8, sprites.cast.bigDog.walk.size)
        assertEquals(listOf(3, 3, 3), sprites.cast.smallDog.bows.map { it.size })
        assertEquals(16 to 22, sprites.cast.hands.first.size to sprites.cast.hands.second.size)
        // A kid is drawn smaller than a grown-up on a nearby lane.
        val kid = sprites.cast.walkers[WALKERS.indexOfFirst { it.who.kid }]
        val adult = sprites.cast.walkers[WALKERS.indexOfFirst { !it.who.kid && it.carry == Carry.NONE }]
        assertTrue(kid.standing.h < adult.standing.h)
    }

    @Test
    fun theDogWalkersFramesAndHandsComeFromTheSamePoses() {
        val walkers = sprites.cast.dogWalkers
        val hands = sprites.cast.hands
        assertEquals(LEAD_POSES.size, walkers.first.stride.size + 1)
        assertEquals(WAVE_POSES.size, walkers.waving.size)
        assertEquals(LEAD_POSES.size * 2, hands.first.size)
        assertEquals((LEAD_POSES.size + WAVE_POSES.size) * 2, hands.second.size)
        // A waving hand is up by the head (the people here are drawn 0.62 life size: the head is about 64 px up);
        // a hand holding a lead is down by the hip.
        val leadHands = (0 until LEAD_POSES.size).map { hands.second[it * 2 + 1] }
        val waveHands = (LEAD_POSES.size until LEAD_POSES.size + WAVE_POSES.size).map { hands.second[it * 2 + 1] }
        assertTrue("$waveHands above $leadHands", waveHands.max() < leadHands.min() - 15f && waveHands.max() < -55f)
        assertTrue(leadHands.all { it in -60f..-20f })
    }

    @Test
    fun eachDogsFramesAreWhatTheirNamesSay() {
        val small = sprites.cast.smallDog
        val big = sprites.cast.bigDog
        val rider = sprites.rider
        assertEquals(listOf(8, 3, 3), listOf(small.walk.size, small.lookingUp.size, small.standing.size))
        assertEquals(listOf(8, 3, 3), listOf(big.walk.size, big.standing.size, big.sniffing.size))
        assertEquals(listOf(4, 3, 5), listOf(rider.air.size, rider.landing.size, rider.wag.size))
        assertEquals(List(RIDER_DEPTHS) { 2 }, rider.bows.map { it.size })
        assertEquals(List(RIDER_DEPTHS) { 2 }, rider.looks.map { it.size })
        // Every pose of one dog is a different picture, and the shadows are pictures of their own.
        val riderPoses = rider.air + rider.landing + rider.wag + rider.bows.flatten() + rider.looks.flatten()
        assertEquals(riderPoses.size, riderPoses.map { it.x to it.y }.distinct().size)
        assertTrue(rider.shadow.h < riderPoses[0].h / 2)
        assertTrue(big.shadow.w > small.shadow.w)
    }
}
