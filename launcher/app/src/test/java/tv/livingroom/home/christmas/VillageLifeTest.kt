package tv.livingroom.home.christmas

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.livingroom.home.HomePlace

class VillageLifeTest {
    private val sprites = motionSheet().sprites
    private val at = FloatArray(8)

    @Test
    fun aGroupThatStaysInOnePlaceOnlyLeavesWhenTheSceneStartsAfresh() {
        val cast = VillageCast()
        val frame = MotionFrame()
        snowballInto(frame, sprites, cast, VillageHours.MORNING, 1.0)
        cast.started()
        assertEquals(2, frame.lit.count)
        // The sun sets while you watch: the children stay until you come back to the screen.
        frame.clear()
        snowballInto(frame, sprites, cast, VillageHours.EVENING, 2.0)
        assertEquals(2, frame.lit.count)
        cast.restart()
        frame.clear()
        snowballInto(frame, sprites, cast, VillageHours.EVENING, 3.0)
        assertEquals(0, frame.lit.count)
        // Nor do they appear in view when their hours begin.
        cast.started()
        frame.clear()
        snowballInto(frame, sprites, cast, VillageHours.MORNING, 4.0)
        assertEquals(0, frame.lit.count)
    }

    @Test
    fun theSnowballFightTakesTurnsThrowingAndDucking() {
        snowballRound(0.1, at)
        assertEquals(0f, at[0])
        snowballRound(1.8, at)
        assertEquals(1f, at[0])
        assertEquals(0.1f, at[1], 1e-4f)
        assertEquals(0, snowballFrame(throwing = true, u = 0f))
        assertEquals(THROW_ARMS.size - 1, snowballFrame(throwing = true, u = 0.3f))
        assertEquals(THROW_ARMS.size - 1, snowballFrame(throwing = true, u = 1.5f))
        assertEquals(THROW_ARMS.size, snowballFrame(throwing = false, u = 0.2f))
        assertEquals(THROW_ARMS.size + 1, snowballFrame(throwing = false, u = 1f))
    }

    @Test
    fun aSledgerGoesDownFastWaitsAndWalksBackUp() {
        sledgerAt(0.0, 0.0, at)
        assertEquals(44f, at[0], 0.01f)
        assertEquals(752f, at[1], 0.01f)
        assertEquals(1f, at[3]) // On the sledge,
        assertEquals(1f, at[4]) // going down.
        sledgerAt(1.0, 0.0, at)
        assertTrue("Slow at first", at[5] < 0.5f)
        sledgerAt(2.3, 0.0, at)
        assertEquals(874f, at[1], 0.01f)
        assertEquals(1f, at[3])
        assertEquals(0f, at[4])
        sledgerAt(5.0, 0.0, at)
        assertEquals(0f, at[3]) // Walking up.
        assertTrue(at[1] in 752f..874f)
        sledgerAt(9.0, 0.0, at)
        assertEquals(1f, at[3]) // On again at the top.
        sledgerAt(9.2, 0.0, at)
        assertEquals(752f, at[1], 0.01f)
    }

    @Test
    fun theLearnerFallsOnceEveryTwelveSeconds() {
        assertFalse(learnerDown(8.0))
        assertTrue(learnerDown(9.0))
        assertFalse(learnerDown(10.5))
        assertTrue(learnerDown(21.0))
    }

    @Test
    fun theFoxTrotsLeftAcrossTheSquareOnceAMinuteOrSo() {
        assertEquals(-44f, foxX(11.0) - foxX(10.0), 0.01f)
        val lap = (VILLAGE_W + 700) / 44.0
        assertEquals(foxX(3.0), foxX(3.0 + lap), 0.5f)
        val xs = (0 until 600).map { foxX(it / 10.0) }
        assertTrue(xs.max() <= VILLAGE_W + 120 && xs.min() >= -580)
    }

    @Test
    fun theLetterFliesUpAndAwayFromThePostBox() {
        letterAt(0f, at)
        assertEquals(852f, at[0])
        assertEquals(786f, at[1])
        assertEquals(1f, at[2])
        letterAt(3.2f, at)
        assertTrue(at[1] < 0f) // Off the top of the picture.
        assertEquals(0f, at[2])
        assertTrue(WALKERS[LETTER_WALKER].who.kid)
        assertEquals(836f, WALKERS[LETTER_WALKER].stopAt)
    }

    @Test
    fun rareThingsComeAsOftenAsTheDesignSays() {
        fun gaps(slots: EventSlots) = (0L until 300L).map { slots.start(it + 1) - slots.start(it) }
        assertTrue(gaps(TRAIN).all { it in 360.0..600.0 }) // Every 6 to 10 minutes.
        assertTrue(gaps(SLEIGH).all { it in 1200.0..1800.0 }) // Every 20 to 30 minutes.
        assertTrue(gaps(SHOOTING_STAR).all { it in 90.0..270.0 }) // Every few minutes.
        // They really are spread, not all at one moment of their slot.
        assertTrue(gaps(TRAIN).toSet().size > 100)
    }

    @Test
    fun anEventIsUnderWayFromItsStartForItsLengthAndNotOtherwise() {
        for (k in 0L until 50L) {
            val start = TRAIN.start(k)
            assertTrue(TRAIN.age(start - 0.01).isNaN())
            assertEquals(0.0, TRAIN.age(start), 1e-9)
            assertEquals(TRAIN.length - 0.01, TRAIN.age(start + TRAIN.length - 0.01), 1e-6)
            assertTrue(TRAIN.age(start + TRAIN.length).isNaN())
            assertEquals(k, TRAIN.slotOf(start + 3, 3.0))
        }
        assertTrue(TRAIN.age(-5.0).isNaN())
    }

    @Test
    fun theTrainCrossesTheWholePictureAndIsDrawnBehindTheVillage() {
        val frame = MotionFrame()
        val start = TRAIN.start(2)
        assertTrue(trainInto(frame, sprites, 1f, start - 1).isNaN())
        assertEquals(0, frame.far.count)
        assertEquals(VILLAGE_W + 120, trainInto(frame, sprites, 1f, start), 0.01f)
        assertTrue("Its last coach is off the left edge by the end", trainX(TRAIN.length) + TRAIN_LENGTH < 0)
        frame.clear()
        trainInto(frame, sprites, 1f, start + 10)
        assertTrue(frame.far.count > 1) // The train and its steam,
        assertTrue(frame.farLights.count > 1) // its lit windows and lamp,
        assertEquals(0, frame.lit.count + frame.lights.count) // and nothing in front of the village.
    }

    @Test
    fun theSleighFliesOnlyAfterDarkAndComesOutClearOfTheVillage() {
        val mid = SLEIGH.start(1) + SLEIGH.length * 0.7
        val dark = MotionFrame().also { villageSleighInto(it, sprites, dark = true, t = mid) }
        val day = MotionFrame().also { villageSleighInto(it, sprites, dark = false, t = mid) }
        assertTrue(dark.plain.count > 0)
        assertEquals(0, day.plain.count + day.lights.count)
        // Until it's clear of the steeple, nothing of it shows.
        val early = MotionFrame().also { villageSleighInto(it, sprites, dark = true, t = SLEIGH.start(1) + SLEIGH.length * 0.3) }
        assertEquals(0, early.plain.count + early.lights.count)
        // Once it starts to show (just past 0.42 of the way), its tail is past the steeple and it's above the houses.
        assertEquals(0f, sleighShows(0.42f))
        sleighAt(0.44f, at)
        assertTrue(sleighShows(0.44f) > 0f)
        assertTrue("Past the steeple and above the houses", at[0] - 184 > 1134 && at[1] < 330)
    }

    @Test
    fun shootingStarsOnlyOnClearDarkNights() {
        val evening = villageScene(ZonedDateTime.of(LocalDate.of(2026, 12, 12), LocalTime.of(23, 30), HomePlace.ZONE), null)
        val noon = villageScene(ZonedDateTime.of(LocalDate.of(2026, 12, 12), LocalTime.of(12, 0), HomePlace.ZONE), null)
        assertTrue(starry(evening.look, clear = true))
        assertFalse(starry(evening.look, clear = false))
        assertFalse(starry(noon.look, clear = true))
        val falling = SHOOTING_STAR.start(4) + 0.4
        val frame = MotionFrame().also { shootingStarInto(it, sprites, starry = true, t = falling) }
        assertEquals(1, frame.lights.count)
        // Its head stays in the top right, clear of the clock.
        assertTrue(frame.lights.corners[0] > 600f && frame.lights.corners[1] < 360f)
    }

    @Test
    fun eachHourHasItsOwnPeopleOut() {
        fun lit(time: LocalTime): Int {
            val scene = villageScene(ZonedDateTime.of(LocalDate.of(2026, 12, 12), time, HomePlace.ZONE), null)
            val smoke = VILLAGE_CHIMNEYS.size * SMOKE_PUFFS
            return MotionFrame().also { villageFrameInto(it, sprites, scene, VillageCast(), 2.0) }.lit.count - smoke
        }
        assertTrue("The morning is busy", lit(LocalTime.of(10, 30)) > 20)
        assertTrue("At night only the fox, if it's passing", lit(LocalTime.of(23, 30)) <= 1)
    }

    @Test
    fun theVillagesFrontIsDrawnOverEverythingTheTrainShows() {
        val frame = MotionFrame()
        val start = TRAIN.start(3)
        var age = 0.0
        while (age < TRAIN.length) {
            frame.clear()
            val x = trainInto(frame, sprites, 1f, start + age)
            for (quads in listOf(frame.farGlass, frame.farLights)) {
                for (c in 0 until quads.count * SpriteQuads.CORNERS) {
                    val cx = quads.corners[c * 2]
                    val cy = quads.corners[c * 2 + 1]
                    assertTrue("x $cx for a train at $x", cx >= x - STRIP_AHEAD && cx <= x + STRIP_BEHIND)
                    assertTrue("y $cy", cy >= STRIP_TOP && cy <= STRIP_BOTTOM)
                }
            }
            for (c in 0 until frame.far.count * SpriteQuads.CORNERS) {
                val cx = frame.far.corners[c * 2]
                assertTrue(cx >= x - STRIP_AHEAD && cx <= x + STRIP_BEHIND && frame.far.corners[c * 2 + 1] <= STRIP_BOTTOM)
            }
            age += 0.5
        }
    }

    @Test
    fun theStallholdersAndThePeopleAtTheBrazierStayUntilTheSceneStartsAfresh() {
        val cast = VillageCast()
        fun count(hours: VillageHours) = MotionFrame().also {
            stallsInto(it, sprites, cast, hours, 1.0)
            brazierPeopleInto(it, sprites, cast, hours)
            cast.started()
        }.lit.count
        val evening = count(VillageHours.EVENING)
        assertEquals("Two stallholders, six puffs of steam, the keeper and two at the brazier", 11, evening)
        assertEquals(evening, count(VillageHours.NIGHT)) // 10 PM comes while you watch: nobody goes in yet.
        cast.restart()
        assertEquals(0, count(VillageHours.NIGHT))
        cast.restart()
        assertEquals("Mornings: the stalls and the keeper, nobody warming their hands", 9, count(VillageHours.MORNING))
    }

    @Test
    fun figuresAreDrawnBackToFrontByWhereTheyStand() {
        val order = DepthOrder(4).apply {
            add(860f, 0)
            add(850f, 1)
            add(860f, 2)
            add(846f, 3)
            sort()
        }
        assertEquals(listOf(3, 1, 0, 2), (0 until order.count).map { order.id(it) })
        // A sledger at the foot of the hill comes after the walkers on the farther lanes.
        val scene = villageScene(ZonedDateTime.of(LocalDate.of(2026, 12, 12), LocalTime.of(10, 30), HomePlace.ZONE), null)
        val frame = MotionFrame()
        villageFrameInto(frame, sprites, scene, VillageCast(), 2.4) // The first sledger is at the bottom, y 874.
        val ids = (0 until frame.order.count).map { frame.order.id(it) }
        assertTrue(ids.indexOf(SLEDGER) > ids.indexOf(0) && ids.indexOf(SLEDGER) > ids.indexOf(WALKERS.indexOfFirst { it.lane == 872f }))
        assertTrue("The carollers after the two farthest walkers", ids.indexOf(CAROLS) > ids.indexOf(1))
        assertTrue(ids.indexOf(SNOWBALL) < ids.indexOf(0))
    }

    @Test
    fun aSleighOnceSeenFliesOnEvenIfDawnComesOnTheMinute() {
        val frame = MotionFrame()
        val start = SLEIGH.start(2)
        frame.clear()
        villageSleighInto(frame, sprites, dark = true, t = start + 7)
        val seen = frame.plain.count
        assertTrue(seen > 0)
        frame.clear()
        villageSleighInto(frame, sprites, dark = false, t = start + 7.1)
        assertTrue(frame.plain.count > 0)
        // And one that wasn't seen in the dark doesn't start part way when night falls.
        val day = MotionFrame()
        villageSleighInto(day, sprites, dark = false, t = SLEIGH.start(3) + 1)
        day.clear()
        villageSleighInto(day, sprites, dark = true, t = SLEIGH.start(3) + 7)
        assertEquals(0, day.plain.count)
    }

    @Test
    fun shootingStarsEndAboveTheMountains() {
        for (k in 0L until 200L) {
            val frame = MotionFrame()
            shootingStarInto(frame, sprites, starry = true, t = SHOOTING_STAR.start(k) + SHOOTING_STAR.length * 0.999)
            for (c in 0 until frame.lights.count * SpriteQuads.CORNERS) assertTrue(frame.lights.corners[c * 2 + 1] < 280f)
        }
    }
}
