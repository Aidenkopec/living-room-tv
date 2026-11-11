package tv.livingroom.home.christmas

import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Who is out in the village and where they are. Everyone's place is worked
 * out from the time alone: a walker crosses the square at a steady pace on
 * their own lane, some stop at a stall, and the dog walk stops twice. The
 * village keeps its own hours, and someone only joins or leaves the scene
 * while off the edge of the picture, so nobody appears or vanishes mid-step.
 */

/** The village's hours: who is out depends on them. */
enum class VillageHours { MORNING, AFTERNOON, EVENING, NIGHT }

/**
 * Which hours it is at [time] on a day with the sun up from [sunrise] to [sunset]: morning until noon,
 * afternoon until sunset, evening until 10 PM, then night until the sun comes up.
 */
fun villageHours(time: LocalTime, sunrise: LocalTime, sunset: LocalTime): VillageHours = when {
    time < sunrise -> VillageHours.NIGHT
    time < LocalTime.NOON && time < sunset -> VillageHours.MORNING
    time < sunset -> VillageHours.AFTERNOON
    time < BEDTIME -> VillageHours.EVENING
    else -> VillageHours.NIGHT
}

private val BEDTIME: LocalTime = LocalTime.of(22, 0)
private val DAY = setOf(VillageHours.MORNING, VillageHours.AFTERNOON)
private val LATER = setOf(VillageHours.AFTERNOON, VillageHours.EVENING)
private val ALL_DAY = DAY + VillageHours.EVENING

/**
 * Someone who crosses the square: what they look like and carry, the [lane] they walk on (their feet's
 * height in the picture), their [speed] in px a second (negative walks left), an [offset] that sets
 * where on the lap they are, and the [hours] they're out. With [stopAt], they stand there (an x in
 * the picture) for [stopFor] seconds on each crossing, holding a cup if [cupAtStop].
 */
data class Walker(
    val who: Villager,
    val carry: Carry,
    val lane: Float,
    val speed: Float,
    val offset: Float,
    val hours: Set<VillageHours>,
    val stopAt: Float? = null,
    val stopFor: Float = 0f,
    val cupAtStop: Boolean = false,
    /** Posts a letter to Santa at their stop (the post box is there). */
    val letter: Boolean = false,
)

/**
 * The village's walkers, nearest last, so drawing them in order puts nearer people in front. Everyone
 * walks in front of the tree and the stalls: the picture behind them is one flat picture.
 */
val WALKERS: List<Walker> = listOf(
    Walker(Villager(coat = 0x7A4F9A, hat = 0xF4EFE6, skin = 0x8F5F43, bag = 0xC9413F), Carry.BAG, 846f, 19f, 900f, LATER, 742f, 7f, true),
    Walker(Villager(coat = 0xE0A83A, hat = 0x4F8F5A), Carry.NONE, 847f, -18f, 240f, LATER, 1252f, 8f, true),
    Walker(Villager(coat = 0xC9413F, hat = 0x2F6F8F, bag = 0x4F8F5A), Carry.BAG, 852f, 25f, 60f, ALL_DAY),
    Walker(Villager(coat = 0x2B3F6A, hat = 0xF0B83A), Carry.NONE, 856f, 16f, 400f, ALL_DAY),
    Walker(Villager(coat = 0x4F8F5A, hat = 0xC9413F, skin = 0xE2B08C), Carry.TREE, 860f, -28f, 1300f, ALL_DAY),
    Walker(Villager(coat = 0xB5543A, hat = 0xF4EFE6, skin = 0x8F5F43), Carry.BAG, 864f, -14f, 1700f, LATER),
    Walker(Villager(coat = 0x2F6F8F, hat = 0xF0B83A, skin = 0xC98D66), Carry.PARCELS, 868f, -21f, 520f, LATER),
    Walker(Villager(coat = 0x2F6F8F, hat = 0xF4EFE6, kid = true), Carry.NONE, 872f, 22f, 1150f, LATER, 836f, 6f, letter = true),
    Walker(Villager(coat = 0xD9D2C4, hat = 0xC9413F, skin = 0xE2B08C), Carry.CUP, 876f, 17f, 1500f, setOf(VillageHours.EVENING)),
    Walker(Villager(coat = 0xC9413F, hat = 0x4F8F5A, skin = 0xC98D66, kid = true, run = true), Carry.NONE, 879f, 66f, 214f, DAY),
    Walker(Villager(coat = 0xE0A83A, hat = 0x2F6F8F, kid = true, run = true), Carry.NONE, 882f, 66f, 300f, DAY),
)

/** How far outside the picture a lap starts and ends, px: wider than anyone is, so the wrap is never seen. */
const val LAP_EDGE = 120f
private const val LAP = VILLAGE_W + 2 * LAP_EDGE

/** Px of ground to a stride: a runner's is longer. */
private fun strideLength(walker: Walker) = if (walker.who.run) 38f else 26f

/**
 * Where [walker] is at [t] seconds: x in [into]`[0]`, how long they've been standing at their stop in
 * `[1]` (0 while walking) and how many strides into their walk they are in `[2]`.
 */
fun walkerAt(walker: Walker, t: Double, into: FloatArray) {
    val pace = abs(walker.speed).toDouble()
    val stop = walker.stopAt
    val along: Double
    var standing = 0.0
    if (stop == null) {
        along = (walker.offset + t * pace).mod(LAP.toDouble())
    } else {
        val toStop = if (walker.speed > 0) stop + LAP_EDGE else VILLAGE_W + LAP_EDGE - stop
        val arrives = toStop / pace
        val since = (t + walker.offset / pace).mod(LAP / pace + walker.stopFor)
        along = when {
            since < arrives -> since * pace
            since < arrives + walker.stopFor -> toStop.toDouble()
            else -> (since - walker.stopFor) * pace
        }
        if (since >= arrives && since < arrives + walker.stopFor) standing = since - arrives + 0.001
    }
    into[0] = (if (walker.speed > 0) along - LAP_EDGE else VILLAGE_W + LAP_EDGE - along).toFloat()
    into[1] = standing.toFloat()
    into[2] = (along / strideLength(walker)).mod(1.0).toFloat()
}

/** Whether a figure whose middle is at [x] and which reaches [reach] px either side is wholly off the picture. */
fun offPicture(x: Float, reach: Float = FIGURE_REACH): Boolean = x < -reach || x > VILLAGE_W + reach

/** The furthest a walker's picture reaches from their feet: the one with a tree over a shoulder. */
const val FIGURE_REACH = 60f

/**
 * The two people who walk the dogs, after the dogs' owners: the first, in front, holds the big dog's lead (a red coat to
 * mid-thigh, a cream knit hat with long brown hair falling below it, dark tights and tall black boots); the second, a little
 * taller, the small dog's (a navy jacket, short brown hair, a red scarf, dark jeans and brown boots).
 */
val DOG_WALKERS = Villager(
    coat = 0xB52E36,
    hat = 0xEFE6D6,
    scarf = 0xEFE6D6,
    legs = 0x25232B,
    mitt = 0xEFE6D6,
    hair = 0x5E3B24,
    longHair = true,
    boots = 0x1A1718,
    tallBoots = true,
    coatLength = 22f,
    tall = 0.96f,
) to Villager(
    coat = 0x2A3A5E,
    scarf = 0x9E2B30,
    legs = 0x34425E,
    mitt = 0x3A3A40,
    hair = 0x4A3020,
    bareHead = true,
    boots = 0x6A4A2E,
    tall = 1.05f,
)

/** What the dog walk is doing: walking, stopped while the small dog jumps at snowflakes, or stopped at the snowman. */
enum class WalkStop { NONE, SNOWFLAKES, SNOWMAN }

/** The dog walk at one instant: the group's middle ([x]), how far into their walk the legs are, and seconds into a [stop]. */
class DogWalk {
    var x = 0f
    var stop = WalkStop.NONE
    var stopped = 0f
    var strides = 0.0

    /** The walk at [t] seconds: across the picture at 24 px a second, with 4.5 s at the snowflakes and 6 s at the snowman. */
    fun at(t: Double): DogWalk {
        val since = (t + WALK_OFFSET).mod(WALK_LAP)
        val firstStop = FLAKES_AT / WALK_PACE
        val secondStop = SNOWMAN_AT / WALK_PACE + FLAKES_FOR
        var along = since * WALK_PACE
        stop = WalkStop.NONE
        stopped = 0f
        when {
            since < firstStop -> Unit
            since < firstStop + FLAKES_FOR -> {
                along = FLAKES_AT
                stop = WalkStop.SNOWFLAKES
                stopped = (since - firstStop).toFloat()
            }
            since < secondStop -> along = (since - FLAKES_FOR) * WALK_PACE
            since < secondStop + SNOWMAN_FOR -> {
                along = SNOWMAN_AT
                stop = WalkStop.SNOWMAN
                stopped = (since - secondStop).toFloat()
            }
            else -> along = (since - FLAKES_FOR - SNOWMAN_FOR) * WALK_PACE
        }
        x = (along - WALK_EDGE).toFloat()
        strides = along / 26
        return this
    }

    /** How high the small dog is off the ground, jumping at snowflakes, for walkers [scale] big. */
    fun jump(scale: Float): Float = if (stop == WalkStop.SNOWFLAKES) abs(sin(stopped * 4.2f)) * 16 * scale else 0f

    /** How deep the small dog's play bow at the snowman is, 0 to 1: down over 0.4 s, held, and up again. */
    fun bow(): Float = if (stop == WalkStop.SNOWMAN) smooth((stopped - 0.6f) / 0.4f) * (1 - smooth((stopped - 4.6f) / 0.6f)) else 0f
}

/** The dog walk's ground line, and where everyone is across from the group's middle. */
const val WALK_Y = 886f
const val FIRST_WALKER_X = -8f
const val SECOND_WALKER_X = -46f
const val BIG_DOG_X = 96f
const val SMALL_DOG_X = 32f

/** How far the dog walk reaches from its middle: off the picture only when its last figure is. */
const val WALK_REACH = 150f
private const val WALK_PACE = 24.0
private const val WALK_EDGE = 200.0
private const val WALK_OFFSET = 40.0
private const val FLAKES_AT = 820.0 // Px along the lap: 200 less across the picture.
private const val FLAKES_FOR = 4.5
private const val SNOWMAN_AT = 1228.0
private const val SNOWMAN_FOR = 6.0
private const val WALK_LAP = (VILLAGE_W + 2 * WALK_EDGE) / WALK_PACE + FLAKES_FOR + SNOWMAN_FOR

/**
 * Who is in the scene now: the walkers, then the dog walk, then the rest of the village's life ([CAST_SIZE] in all). A walker
 * (or the dog walk, or anyone else crossing) joins or leaves only while off the picture; a group that stays in one place only
 * when the scene starts afresh. When it does, everyone whose hours it is is simply there.
 */
class VillageCast {
    val out = BooleanArray(CAST_SIZE)
    private var started = false

    /** Settles whether figure [i] is out, given where it is ([offPicture]) and whether its hours include now ([due]). */
    fun settle(i: Int, offPicture: Boolean, due: Boolean): Boolean {
        if (!started || offPicture) out[i] = due
        return out[i]
    }

    /** Settles whether group [i] (which stays in one place, in view) is out: it changes only when the scene starts afresh. */
    fun settleInPlace(i: Int, due: Boolean): Boolean = settle(i, offPicture = false, due = due)

    /** Call once every figure has been settled for a frame. */
    fun started() {
        started = true
    }

    /** Forget who was out: the next frame starts afresh (the screen was away for a while). */
    fun restart() {
        started = false
    }
}

/** A place on whole pixels: a figure drawn between pixels is smeared across two. */
fun onPixel(x: Float): Float = x.roundToInt().toFloat()
