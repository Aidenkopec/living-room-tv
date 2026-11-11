package tv.livingroom.home.christmas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FiresidePlanTest {
    private val threeHours = 3 * 3600.0

    /** A plan carried forward a second at a time, as the frames do, [late] from [lateFrom] seconds on. */
    private fun planned(
        seed: Long,
        lateFrom: Double = Double.MAX_VALUE,
        until: Double = threeHours,
        plan: FiresidePlan = FiresidePlan(seed),
    ): FiresidePlan {
        var t = 0.0
        while (t < until) {
            plan.planTo(t, t >= lateFrom)
            t += 1.0
        }
        return plan
    }

    @Test
    fun santaComesFirstBetweenOneAndTenMinutesInThenOnceAnHour() {
        for (seed in 0L until 200L) {
            val visits = planned(seed).plannedVisits
            assertTrue("seed $seed: ${visits.first()}", visits.first() in FIRST_VISIT_FROM..FIRST_VISIT_TO + 20)
            assertTrue("seed $seed: ${visits.size} visits", visits.size in 3..4)
            for (i in 1 until visits.size) assertTrue(visits[i] - visits[i - 1] >= VISIT_LENGTH + 600)
            for (k in 1 until visits.size) assertTrue("seed $seed: ${visits[k]}", visits[k] >= 3600.0 * k)
        }
    }

    @Test
    fun theDogsHaveAMomentEveryTwoToFiveMinutesNeverTheSameTwiceAndNeverDuringAVisit() {
        for (seed in 0L until 200L) {
            val plan = planned(seed)
            val moments = plan.plannedMoments
            val first = moments.first().start // 2 to 5 minutes in, unless Santa came first and the dogs rested after.
            assertTrue("seed $seed: $first", first in MOMENT_GAP_FROM..MOMENT_GAP_TO || plan.plannedVisits.first() < first)
            for (i in 1 until moments.size) {
                val gap = moments[i].start - moments[i - 1].end
                assertTrue("seed $seed: gap $gap", gap >= MOMENT_GAP_FROM - 1e-9)
                assertTrue(moments[i].moment != moments[i - 1].moment)
            }
            for (m in moments) {
                for (v in plan.plannedVisits) assertTrue("seed $seed: $m and Santa at $v", m.end <= v || m.start >= v + VISIT_LENGTH)
                assertEquals(momentDog(m.moment, m.dog), m.dog)
            }
            // Over three hours every gap is under 5 minutes, except where Santa came between.
            assertTrue(moments.size > 30)
        }
    }

    @Test
    fun lateAtNightOnlyTheDream() {
        for (seed in 0L until 100L) {
            val moments = planned(seed, lateFrom = 1800.0).plannedMoments
            for (m in moments.filter { it.start >= 1800.0 }) assertEquals("$m", Moment.DREAM, m.moment)
        }
    }

    @Test
    fun aTestSwitchPutsSantaOrAMomentFirst() {
        val santa = planned(7, until = 60.0, plan = FiresidePlan(7, firstSanta = 5.0))
        assertEquals(5.0, santa.plannedVisits.first(), 0.0)
        assertEquals(10.0, santa.visitAt(15.0), 1e-9)
        assertTrue(santa.visitAt(4.0).isNaN())
        val asked = planned(7, until = 60.0, plan = FiresidePlan(7, firstMoment = AskedMoment(Moment.DREAM, Dog.BIG)))
        assertEquals(PlannedMoment(ASKED_AFTER, Moment.DREAM, Dog.BIG), asked.plannedMoments.first())
        assertEquals(Moment.DREAM, asked.momentAt(ASKED_AFTER + 1)?.moment)
    }

    @Test
    fun santaWaitsForTheDogsToSettle() {
        // A moment asked for at 3 s and Santa at 5 s: he comes once the moment has ended.
        val plan = planned(3, until = 60.0, plan = FiresidePlan(3, firstSanta = 5.0, firstMoment = AskedMoment(Moment.ZOOMIES, null)))
        assertTrue(plan.plannedVisits.first() >= ASKED_AFTER + Moment.ZOOMIES.length)
    }

    @Test
    fun theSamePlanForTheSameSeed() {
        val a = planned(42)
        val b = planned(42)
        assertEquals(a.plannedVisits, b.plannedVisits)
        assertEquals(a.plannedMoments, b.plannedMoments)
    }

    @Test
    fun giftsLeftByAnEarlierVisitAreThereFromTheStart() {
        val plan = FiresidePlan(5, giftsLeft = true)
        assertTrue(plan.santaHasBeen(0.0))
        assertEquals(EARLIER_VISIT, plan.latestVisitStart(0.0), 0.0)
        assertTrue(plan.visitAt(0.0).isNaN())
        assertTrue(0.0 - EARLIER_VISIT > VISIT_LENGTH) // It's all over: no visit plays out.
        val fresh = FiresidePlan(5)
        assertTrue(!fresh.santaHasBeen(0.0) && fresh.latestVisitStart(0.0).isNaN())
    }
}
