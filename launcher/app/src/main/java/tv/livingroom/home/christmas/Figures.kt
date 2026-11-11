package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * The village's figures as ready-drawn frames: each villager, the two dogs on
 * their walk and the dog that rides the dock are a handful of poses painted
 * into the moving sheet, so a figure costs one piece per frame however it's
 * posed. This file says which poses there are and picks the one to show.
 */

/** A stride is shown in twelve steps, which are seven different poses: the legs' swing from full back to full forward. */
val STRIDE_SWINGS = floatArrayOf(-1f, -0.866f, -0.5f, 0f, 0.5f, 0.866f, 1f)
private val STRIDE_STEPS = intArrayOf(3, 4, 5, 6, 5, 4, 3, 2, 1, 0, 1, 2)

/** Which of the [STRIDE_SWINGS] poses shows [walk] strides into a walk. */
fun strideFrame(walk: Double): Int = STRIDE_STEPS[step(walk, STRIDE_STEPS.size)]

/** Which of [steps] equal steps of a cycle [phase] (in whole cycles) is in. */
fun step(phase: Double, steps: Int): Int = floor((phase - floor(phase)) * steps).toInt().coerceIn(0, steps - 1)

/** Which of three tail frames (left, middle, right) shows a tail at [wag], -1 to 1. */
fun tailFrame(wag: Float): Int = wag.roundToInt().coerceIn(-1, 1) + 1

/**
 * Adds every pose in [groups] to this sheet in one box they all share (so a figure doesn't shift as its
 * pose changes), [scale] times life size, and gives back each group's sprite numbers in the same order.
 */
fun SheetMaker.addGroups(groups: List<List<List<Mark>>>, scale: Float): List<List<Int>> {
    val numbers = addFrames(groups.flatten(), scale)
    var at = 0
    return groups.map { group -> numbers.subList(at, at + group.size).also { at += group.size } }
}

/** A villager's frames: the seven stride poses, and standing still (with a cup, for those who stop at a stall). */
class WalkerFrames<T>(val stride: List<T>, val standing: T) {
    fun <R> map(f: (T) -> R) = WalkerFrames(stride.map(f), f(standing))
}

/** The poses of a stride, each with the scarf at its own place in its flapping. */
private fun stridePoses(carry: Carry) = STRIDE_SWINGS.mapIndexed { i, swing -> PersonPose(swing, carry, flutter = sin(i * 1.9f)) }

/** Adds [walker]'s frames to [maker], drawn at the size its lane gives it. */
fun walkerFrames(maker: SheetMaker, walker: Walker): WalkerFrames<Int> {
    val stride = stridePoses(walker.carry).map { personMarks(walker.who, it) }
    val standing = personMarks(walker.who, PersonPose(carry = if (walker.cupAtStop) Carry.CUP else walker.carry))
    val groups = maker.addGroups(listOf(stride, listOf(standing)), personScale(walker.lane))
    return WalkerFrames(groups[0], groups[1].single())
}

/** How big someone is drawn, from how far down the picture (how near) they stand. */
fun personScale(y: Float): Float = lerp(0.44f, 0.62f, ((y - 790) / 100).coerceIn(0f, 1f))

/** The angles the second dog walker's arm waves through. */
val WAVE_ARMS = floatArrayOf(2.15f, 2.5f, 2.85f)

/**
 * The dog walkers' poses, in the order their frames are numbered: holding a lead through the stride's
 * seven poses, then standing ([LEAD_POSES]); the second walker also has [WAVE_POSES] after those.
 * The frames and the hands the leads start from ([walkHands]) are both made from these lists.
 */
val LEAD_POSES: List<PersonPose> = stridePoses(Carry.LEAD) + PersonPose(carry = Carry.LEAD)
val WAVE_POSES: List<PersonPose> = WAVE_ARMS.map { PersonPose(carry = Carry.WAVE, arm = it) }

/** The frames of the two people who walk the dogs: each one's stride and standing pose, and the second one waving. */
class DogWalkerFrames<T>(val first: WalkerFrames<T>, val second: WalkerFrames<T>, val waving: List<T>) {
    fun <R> map(f: (T) -> R) = DogWalkerFrames(first.map(f), second.map(f), waving.map(f))
}

/** Adds the two dog walkers' frames to [maker], [scale] times life size. */
fun dogWalkerFrames(maker: SheetMaker, scale: Float): DogWalkerFrames<Int> {
    val (firstWho, secondWho) = DOG_WALKERS
    val stride = STRIDE_SWINGS.size
    val first = maker.addGroups(listOf(LEAD_POSES.map { personMarks(firstWho, it) }), scale).single()
    val second = maker.addGroups(listOf((LEAD_POSES + WAVE_POSES).map { personMarks(secondWho, it) }), scale).single()
    return DogWalkerFrames(
        WalkerFrames(first.take(stride), first[stride]),
        WalkerFrames(second.take(stride), second[stride]),
        second.drop(LEAD_POSES.size),
    )
}

/** The big dog's frames on the walk: eight steps of its walk, and standing with the ball and sniffing, each with three tails. */
class BigDogFrames<T>(val walk: List<T>, val standing: List<T>, val sniffing: List<T>, val shadow: T) {
    fun <R> map(f: (T) -> R) = BigDogFrames(walk.map(f), standing.map(f), sniffing.map(f), f(shadow))
}

/** The small dog's frames on the walk: its walk, looking up at the snow, standing, and three depths of bow, each with three tails. */
class SmallDogFrames<T>(val walk: List<T>, val lookingUp: List<T>, val standing: List<T>, val bows: List<List<T>>, val shadow: T) {
    fun <R> map(f: (T) -> R) = SmallDogFrames(walk.map(f), lookingUp.map(f), standing.map(f), bows.map { it.map(f) }, f(shadow))
}

/**
 * The frames of the dog that rides the dock: four of a bound through the air, three of landing (from a
 * crouch up), five of standing with the tail across its wag, then bows and looks round, each of those at
 * [RIDER_DEPTHS] depths with the tail left and right.
 */
class RiderFrames<T>(
    val air: List<T>,
    val landing: List<T>,
    val wag: List<T>,
    val bows: List<List<T>>,
    val looks: List<List<T>>,
    val shadow: T,
) {
    fun <R> map(f: (T) -> R) =
        RiderFrames(air.map(f), landing.map(f), wag.map(f), bows.map { it.map(f) }, looks.map { it.map(f) }, f(shadow))
}

/** How fast a dog's tail wags, radians a second. */
const val WAG_RATE = 13.0

/** How big the dock's rider is drawn, and the dogs on their walk (as a share of their walkers' size). */
const val RIDER_SCALE = 0.4f
const val BIG_DOG_SHARE = 0.37f
const val SMALL_DOG_SHARE = 0.31f

/** How many depths of bow and of looking round the rider has frames for, and how far his head turns at the most. */
const val RIDER_DEPTHS = 3
const val RIDER_LOOK = 0.3f

/** How many depths of bow the small dog has frames for on its walk. */
const val WALK_BOWS = 3
private val TAILS = floatArrayOf(-1f, 0f, 1f)
private val SIDES = floatArrayOf(-1f, 1f)
private const val DOG_STEPS = 8

/** [dog]'s shadow added to [maker] on its own, [scale] times life size. */
private fun shadowFrame(maker: SheetMaker, dog: Dog, scale: Float): Int = maker.addFrames(listOf(dogShadowMarks(dog)), scale).single()

/** Adds the rider's frames to [maker]. */
fun riderFrames(maker: SheetMaker): RiderFrames<Int> {
    val dog = Dog.SMALL
    val air = List(4) { dogMarks(dog, DogPose(up = 0.75f, stride = it / 4f, head = 0.15f)) }
    val landing = List(3) { dogMarks(dog, DogPose(up = 0.7f + it * 0.1f)) }
    val wag = List(5) { dogMarks(dog, DogPose(tail = -1f + it * 0.5f)) }
    val bows = (1..RIDER_DEPTHS).flatMap { depth -> SIDES.map { dogMarks(dog, DogPose(bow = depth.toFloat() / RIDER_DEPTHS, tail = it)) } }
    val looks = (1..RIDER_DEPTHS).flatMap { depth ->
        SIDES.map { dogMarks(dog, DogPose(head = RIDER_LOOK * depth / RIDER_DEPTHS, tail = it)) }
    }
    val g = maker.addGroups(listOf(air, landing, wag, bows, looks), RIDER_SCALE)
    return RiderFrames(g[0], g[1], g[2], g[3].chunked(SIDES.size), g[4].chunked(SIDES.size), shadowFrame(maker, dog, RIDER_SCALE))
}

/** Adds the walking big dog's frames to [maker], [scale] times life size. */
fun bigDogFrames(maker: SheetMaker, scale: Float): BigDogFrames<Int> {
    val dog = Dog.BIG
    val walk =
        List(DOG_STEPS) { dogMarks(dog, DogPose(stride = it.toFloat() / DOG_STEPS, tail = 0.4f * sin(it * PI.toFloat() / 2), ball = true)) }
    val standing = TAILS.map { dogMarks(dog, DogPose(tail = it, ball = true)) }
    val sniffing = TAILS.map { dogMarks(dog, DogPose(tail = it, head = -0.4f)) }
    val g = maker.addGroups(listOf(walk, standing, sniffing), scale)
    return BigDogFrames(g[0], g[1], g[2], shadowFrame(maker, dog, scale))
}

/** Adds the walking small dog's frames to [maker], [scale] times life size. */
fun smallDogFrames(maker: SheetMaker, scale: Float): SmallDogFrames<Int> {
    val dog = Dog.SMALL
    val walk = List(DOG_STEPS) { dogMarks(dog, DogPose(stride = it.toFloat() / DOG_STEPS, tail = sin(it * PI.toFloat() / 2))) }
    val lookingUp = TAILS.map { dogMarks(dog, DogPose(tail = it, head = 0.45f)) }
    val standing = TAILS.map { dogMarks(dog, DogPose(tail = it)) }
    val bows = (1..WALK_BOWS).flatMap { depth -> TAILS.map { dogMarks(dog, DogPose(bow = depth.toFloat() / WALK_BOWS, tail = it)) } }
    val g = maker.addGroups(listOf(walk, lookingUp, standing, bows), scale)
    return SmallDogFrames(g[0], g[1], g[2], g[3].chunked(TAILS.size), shadowFrame(maker, dog, scale))
}
