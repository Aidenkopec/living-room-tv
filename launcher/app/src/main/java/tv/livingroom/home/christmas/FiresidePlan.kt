package tv.livingroom.home.christmas

import kotlin.random.Random

/** A dog moment as planned: when it starts (seconds on the screensaver's clock), which one, and which dog does it. */
data class PlannedMoment(val start: Double, val moment: Moment, val dog: Dog) {
    val end: Double get() = start + moment.length
}

/** A moment asked for by a test switch: which, and (for the dream and the chew) which dog, or null to pick. */
data class AskedMoment(val moment: Moment, val dog: Dog?)

/**
 * When Santa comes and when the dogs have their moments, for one showing of Fireside, from [seed]: the same seed always plans
 * the same evening. Santa first comes between 1 and 10 minutes in, then once at a random point in each further hour. A moment
 * comes 2 to 5 minutes after the last one settled, never the same one twice running; none during a visit, and if Santa is due
 * while the dogs are busy he waits for them to settle. Late at night only the dream. [firstSanta] (seconds) or [firstMoment]
 * (3 s in) are what a test switch asks for. With [giftsLeft], Santa came in an earlier showing: his gifts are in the room from
 * the start, until he comes again.
 *
 * It's planned as the screensaver's clock moves on ([planTo]), because which moments may come depends on the hour then.
 */
class FiresidePlan(seed: Long, firstSanta: Double? = null, firstMoment: AskedMoment? = null, private val giftsLeft: Boolean = false) {
    private val random = Random(seed)
    private val visits = ArrayList<Double>()
    private val moments = ArrayList<PlannedMoment>()
    private var asked = firstMoment
    private var last: Moment? = null
    private var nextSanta = firstSanta ?: between(FIRST_VISIT_FROM, FIRST_VISIT_TO)
    private var nextHour = 1
    private var nextMoment = if (firstMoment != null) ASKED_AFTER else between(MOMENT_GAP_FROM, MOMENT_GAP_TO)

    private fun between(from: Double, to: Double) = from + random.nextDouble() * (to - from)

    /** Plans whatever comes by [t] seconds, [late] being whether it's late night now. Cheap when nothing is due: call it every frame. */
    fun planTo(t: Double, late: Boolean) {
        while (true) {
            if (nextSanta <= nextMoment) {
                if (nextSanta > t) return
                val start = nextSanta
                visits += start
                nextMoment = maxOf(nextMoment, start + VISIT_LENGTH + AFTER_VISIT)
                nextSanta = maxOf(HOUR * nextHour + between(0.0, HOUR), start + VISIT_LENGTH + VISITS_APART)
                nextHour++
            } else {
                if (nextMoment > t) return
                val planned = pick(nextMoment, late)
                if (planned == null) { // Nothing may come just now (late, nothing new to do): wait for the next turn.
                    nextMoment += between(MOMENT_GAP_FROM, MOMENT_GAP_TO)
                    continue
                }
                moments += planned
                last = planned.moment
                if (nextSanta < planned.end + SANTA_WAITS) nextSanta = planned.end + SANTA_WAITS
                nextMoment = planned.end + between(MOMENT_GAP_FROM, MOMENT_GAP_TO)
            }
        }
    }

    private fun pick(start: Double, late: Boolean): PlannedMoment? {
        val either = if (random.nextBoolean()) Dog.BIG else Dog.SMALL
        asked?.let {
            asked = null
            return PlannedMoment(start, it.moment, it.dog ?: momentDog(it.moment, either))
        }
        val pool = Moment.entries.filter { it != last && (!late || it == Moment.DREAM) }
        if (pool.isEmpty()) return null
        val moment = pool[random.nextInt(pool.size)]
        return PlannedMoment(start, moment, momentDog(moment, either))
    }

    /** Whether, by [t], Santa's bones are on the rug: left by a visit in this showing, or before it. */
    fun santaHasBeen(t: Double): Boolean = giftsLeft || leftGifts(t)

    /** Whether a visit in this showing has left its gifts by [t]. */
    fun leftGifts(t: Double): Boolean = visits.any { it + boneLands(1) <= t }

    /** How many seconds into a visit [t] is, or NaN if no visit is under way. */
    fun visitAt(t: Double): Double {
        for (i in visits.indices.reversed()) {
            val start = visits[i]
            if (start <= t) return if (t - start < VISIT_LENGTH) t - start else Double.NaN
        }
        return Double.NaN
    }

    /** When the latest visit to have started by [t] started (long before the showing, if his gifts were left earlier), or NaN. */
    fun latestVisitStart(t: Double): Double {
        for (i in visits.indices.reversed()) if (visits[i] <= t) return visits[i]
        return if (giftsLeft) EARLIER_VISIT else Double.NaN
    }

    /** The moment under way at [t], or null. */
    fun momentAt(t: Double): PlannedMoment? = moments.lastOrNull { it.start <= t }?.takeIf { t < it.end }

    /** Everything planned so far, for tests. */
    val plannedVisits: List<Double> get() = visits
    val plannedMoments: List<PlannedMoment> get() = moments
}

/** When a visit in an earlier showing is taken to have been, on this showing's clock: long enough ago that it's all over. */
const val EARLIER_VISIT = -10 * VISIT_LENGTH

/** Santa's first visit, seconds after Fireside starts. */
const val FIRST_VISIT_FROM = 60.0
const val FIRST_VISIT_TO = 600.0

/** A dog moment comes this many seconds after the last one settled (or after Fireside starts). */
const val MOMENT_GAP_FROM = 120.0
const val MOMENT_GAP_TO = 300.0

/** A moment a test switch asks for starts this many seconds in. */
const val ASKED_AFTER = 3.0

private const val HOUR = 3600.0
private const val AFTER_VISIT = 30.0 // The dogs rest a while after Santa before a moment.
private const val VISITS_APART = 600.0 // Two visits never come closer than this, even across an hour's edge.
private const val SANTA_WAITS = 2.0 // After the dogs settle.
