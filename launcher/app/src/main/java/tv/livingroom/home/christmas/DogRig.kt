package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/*
 * A standing dog's skeleton. A dog in profile is a handful of rigid pieces
 * (torso, neck, head, tail and three bones to a leg) hung from joints, so a pose
 * is worked out the way a dog's body moves: the torso drops for a crouch and
 * tips about the hips for a bow, each paw is put where the gait says, and each
 * leg reaches it with fixed bone lengths, the elbow bending back and the stifle
 * (the knee) forward, with the hock above the hind paw. The village draws the
 * pieces into ready-made frames; Fireside places the same pieces on every
 * frame. Plain Kotlin, so it runs in JVM tests, and it makes no objects per
 * pose. Units are the dog's own, facing left, with (0, 0) on the floor under
 * its middle.
 */

/**
 * The village's two dogs. The small one is tan with a white blaze, muzzle, chest and socks, big ears
 * with folded tips, a blue collar and a tail with a dark band and a white tip. The big one is a black
 * and tan Dobermann with long pointed ears, a studded black collar and a short tail.
 */
enum class Dog { SMALL, BIG }

/**
 * How a dog stands at one instant. [up] is 0 crouched to 1 on straight legs. [stride] is the gait's
 * phase in whole strides (null: standing), a walk or, with [gallop], a gallop. [bow] tips the front
 * end down, 0 to 1 (a stretch or a play bow). [head] turns the head (radians; negative lowers the
 * nose). [tail], -1 to 1, is where the wagging tail is. [ball] puts the red ball in its mouth, [bone]
 * a bone; [pant] opens the mouth with the tongue out; not [open] closes the eyes. [ear], 0 to 1, is an
 * ear's twitch: the small dog's folded tip lifts, the big dog's point tips back.
 */
data class DogPose(
    val up: Float = 1f,
    val stride: Float? = null,
    val bow: Float = 0f,
    val head: Float = 0f,
    val tail: Float = 0f,
    val ball: Boolean = false,
    val open: Boolean = true,
    val ear: Float = 0f,
    val bone: Boolean = false,
    val gallop: Boolean = false,
    val pant: Boolean = false,
)

/** A standing dog's pieces, in the order they're drawn (back to front). Each is placed by [DogRig]. */
enum class RigPart {
    FAR_THIGH,
    FAR_GASKIN,
    FAR_HOCK,
    FAR_HIND_PAW,
    FAR_UPPER_ARM,
    FAR_FOREARM,
    FAR_FRONT_PAW,
    TAIL,
    TORSO,
    NECK,
    COLLAR,
    HEAD,
    NEAR_THIGH,
    NEAR_GASKIN,
    NEAR_HOCK,
    NEAR_HIND_PAW,
    NEAR_UPPER_ARM,
    NEAR_FOREARM,
    NEAR_FRONT_PAW,
}

/**
 * A dog's skeleton standing square, in its own units: where each joint is, and how it moves. The torso, neck, collar and tail
 * are drawn in these same units; a leg bone is drawn hanging straight down from its upper joint, [boneLength] long; the head
 * in the heads' units, with the neck's joint at [HEAD_JOINT].
 */
internal class DogBuild(
    /** Shoulder, elbow and wrist of the front leg; hip, stifle, hock and ankle of the hind leg (x, y pairs). */
    val front: FloatArray,
    val hind: FloatArray,
    /** Where the neck turns on the body, and where the head turns on the neck. */
    val neckBase: FloatArray,
    val headJoint: FloatArray,
    /** Half a walking stride, and how high a walking paw lifts. */
    val reach: Float,
    val lift: Float,
)

/** The head's joint with the neck, in the heads' own units: every head is drawn round it. */
internal const val HEAD_JOINT_X = -64f
internal const val HEAD_JOINT_Y = -28f

internal val SMALL_BUILD = DogBuild(
    front = floatArrayOf(-56f, -88f, -40f, -60f, -44f, -14f),
    hind = floatArrayOf(46f, -90f, 30f, -55f, 58f, -25f, 56f, -11f),
    neckBase = floatArrayOf(-58f, -100f),
    headJoint = floatArrayOf(-84f, -134f),
    reach = 17f,
    lift = 11f,
)

internal val BIG_BUILD = DogBuild(
    front = floatArrayOf(-62f, -114f, -44f, -80f, -48f, -16f),
    hind = floatArrayOf(56f, -118f, 34f, -72f, 70f, -30f, 67f, -12f),
    neckBase = floatArrayOf(-64f, -136f),
    headJoint = floatArrayOf(-96f, -182f),
    reach = 21f,
    lift = 14f,
)

internal fun buildOf(dog: Dog): DogBuild = if (dog == Dog.BIG) BIG_BUILD else SMALL_BUILD

/** The length of bone [i] of a leg whose joints are [joints] (x, y pairs): from joint i to joint i + 1. */
internal fun boneLength(joints: FloatArray, i: Int): Float = hypot(joints[2 * i + 2] - joints[2 * i], joints[2 * i + 3] - joints[2 * i + 1])

/** How far a full bow tips a standing dog's front end down, radians. */
internal const val BOW_LEAN = 0.4f

/** How far the far legs sit from the near ones, across and up: they're a little behind and a little higher, being further off. */
private const val FAR_X = 5f
private const val FAR_Y = -2f

/**
 * Works out where every piece of a standing dog goes for one pose, into [placed]: for each [RigPart] its turn (radians,
 * clockwise) and where its own origin lands, so a piece's point (x, y) is drawn at (x cos - y sin + px, x sin + y cos + py).
 * Reused from pose to pose.
 */
class DogRig {
    /** For each [RigPart], by ordinal: turn, x, y. */
    val placed = FloatArray(RigPart.entries.size * 3)

    // Scratch: the torso's and neck's placements, and a leg's joints.
    private val torso = FloatArray(3)
    private val neck = FloatArray(3)
    private val leg = FloatArray(8)

    /** Turn, x and y for [part]. */
    fun turn(part: RigPart): Float = placed[part.ordinal * 3]

    fun x(part: RigPart): Float = placed[part.ordinal * 3 + 1]

    fun y(part: RigPart): Float = placed[part.ordinal * 3 + 2]

    // The pose being worked out: see [DogPose]. [stride] is NaN standing still.
    var up = 1f
    var bow = 0f
    var head = 0f
    var stride = Float.NaN
    var gallop = false

    /** [dog] in [pose] (only how it stands: the tail, eyes and mouth are the pieces' own). */
    fun pose(dog: Dog, pose: DogPose) {
        up = pose.up
        bow = pose.bow
        head = pose.head
        stride = pose.stride ?: Float.NaN
        gallop = pose.gallop
        pose(dog)
    }

    /** [dog] as [up], [bow], [head], [stride] and [gallop] say. */
    fun pose(dog: Dog) {
        val b = buildOf(dog)
        val phase = stride
        val moving = !phase.isNaN()
        val galloping = moving && gallop
        // A gallop pitches the body nose-down as the front legs land and lifts it clear of the floor between strides.
        val pitch = if (galloping) sin(2 * PI.toFloat() * phase + GALLOP_PITCH_AT) * GALLOP_PITCH else 0f
        val float = if (galloping) max(0f, sin(2 * PI.toFloat() * phase + GALLOP_FLOAT_AT)) * b.lift * GALLOP_FLOAT else 0f
        val bob = if (moving && !galloping) abs(sin(2 * PI.toFloat() * phase * 2)) * WALK_BOB else 0f
        val drop = (1 - up.coerceIn(0f, 1f)) * CROUCH * -b.front[3] + bob - float
        val lean = -bow * BOW_LEAN + pitch
        val hipX = b.hind[0]
        val hipY = b.hind[1]
        // The torso tips about the hip and drops: p' = R(lean) (p - hip) + hip + (0, drop).
        torso[0] = lean
        torso[1] = hipX - (cos(lean) * hipX - sin(lean) * hipY)
        torso[2] = hipY + drop - (sin(lean) * hipX + cos(lean) * hipY)
        placeAt(RigPart.TORSO, torso[0], torso[1], torso[2])
        placeAt(RigPart.TAIL, torso[0], torso[1], torso[2])
        // The neck takes half the head's turn about its base; the head the rest, about its joint. In a bow the head stays up.
        val headTurn = head + bow * BOW_HEAD_UP
        rotateAbout(torso, b.neckBase[0], b.neckBase[1], headTurn * NECK_SHARE, neck)
        placeAt(RigPart.NECK, neck[0], neck[1], neck[2])
        placeAt(RigPart.COLLAR, neck[0], neck[1], neck[2])
        val jx = mapX(neck, b.headJoint[0], b.headJoint[1])
        val jy = mapY(neck, b.headJoint[0], b.headJoint[1])
        val headAngle = neck[0] + headTurn * (1 - NECK_SHARE)
        val hx = jx - (cos(headAngle) * HEAD_JOINT_X - sin(headAngle) * HEAD_JOINT_Y)
        placeAt(RigPart.HEAD, headAngle, hx, jy - (sin(headAngle) * HEAD_JOINT_X + cos(headAngle) * HEAD_JOINT_Y))
        hindLeg(b, far = true)
        frontLeg(b, far = true)
        hindLeg(b, far = false)
        frontLeg(b, far = false)
    }

    /** Where a paw goes in the gait: its offset forward (toward -x) and how high it's lifted, into [step] (fwd, lift). */
    private fun gait(b: DogBuild, legPhase: Float, step: FloatArray) {
        val phase = stride
        if (phase.isNaN()) {
            step[0] = 0f
            step[1] = 0f
            return
        }
        val duty = if (gallop) GALLOP_DUTY else WALK_DUTY
        val p = (phase + legPhase).mod(1f)
        val reach = b.reach * (if (gallop) GALLOP_REACH else 1f)
        val lift = b.lift * (if (gallop) GALLOP_LIFT else 1f)
        if (p < duty) {
            step[0] = (1 - 2 * p / duty) * reach // On the floor: from forward to back.
            step[1] = 0f
        } else {
            val u = (p - duty) / (1 - duty)
            step[0] = (-1 + 2 * smooth(u)) * reach // In the air: back to forward, lifted.
            step[1] = sin(u * PI.toFloat()) * lift
        }
    }

    private val step = FloatArray(2)

    private fun hindLeg(b: DogBuild, far: Boolean) {
        val h = b.hind
        gait(b, (if (gallop) GALLOP_PHASES else WALK_PHASES)[if (far) FAR_HIND else NEAR_HIND], step)
        val dx = if (far) FAR_X else 0f
        val dy = if (far) FAR_Y else 0f
        val hipX = mapX(torso, h[0], h[1]) + dx
        val hipY = mapY(torso, h[0], h[1]) + dy
        // The ankle's place: under the hip as it stands, moved by the gait; the hock above and behind it, more bent when lifted.
        val ankleX = h[6] + dx - step[0]
        val ankleY = h[7] + dy - step[1]
        val hockAngle = atan2(h[4] - h[6], h[7] - h[5]) + step[1] / b.lift * HOCK_FLEX
        val hockLen = boneLength(h, 2)
        val hockX = ankleX + sin(hockAngle) * hockLen
        val hockY = ankleY - cos(hockAngle) * hockLen
        reach(hipX, hipY, hockX, hockY, boneLength(h, 0), boneLength(h, 1), bendForward = true)
        val names = if (far) FAR_HIND_PARTS else NEAR_HIND_PARTS
        bone(names[0], hipX, hipY, leg[0], leg[1])
        bone(names[1], leg[0], leg[1], leg[2], leg[3])
        bone(names[2], leg[2], leg[3], ankleX, ankleY)
        placeAt(names[3], -step[1] / b.lift * HIND_PAW_FLEX, ankleX, ankleY)
    }

    private fun frontLeg(b: DogBuild, far: Boolean) {
        val f = b.front
        gait(b, (if (gallop) GALLOP_PHASES else WALK_PHASES)[if (far) FAR_FRONT else NEAR_FRONT], step)
        val dx = if (far) FAR_X else 0f
        val dy = if (far) FAR_Y else 0f
        val shoulderX = mapX(torso, f[0], f[1]) + dx
        val shoulderY = mapY(torso, f[0], f[1]) + dy
        // In a bow the front paws slide out ahead and the forearms come down to the floor.
        val wristX = f[4] + dx - step[0] - bow * BOW_REACH * b.reach
        val wristY = f[5] + dy - step[1] + bow * (-f[5] - FOREARM_DOWN)
        reach(shoulderX, shoulderY, wristX, wristY, boneLength(f, 0), boneLength(f, 1), bendForward = false)
        val names = if (far) FAR_FRONT_PARTS else NEAR_FRONT_PARTS
        bone(names[0], shoulderX, shoulderY, leg[0], leg[1])
        bone(names[1], leg[0], leg[1], leg[2], leg[3])
        placeAt(names[2], -step[1] / b.lift * FRONT_PAW_FLEX, leg[2], leg[3])
    }

    /**
     * A two-bone reach from the root ([rx], [ry]) toward ([tx], [ty]) with bones [a] and [b]: the middle joint into leg[0..1]
     * and the end into leg[2..3]. The middle joint bends forward (toward -x: a stifle) or back (an elbow). Out of reach, the
     * leg points straight at the target.
     */
    private fun reach(rx: Float, ry: Float, tx: Float, ty: Float, a: Float, b: Float, bendForward: Boolean) {
        val dx = tx - rx
        val dy = ty - ry
        val d = hypot(dx, dy).coerceIn(abs(a - b) + 0.01f, a + b - 0.01f)
        val base = atan2(dy, dx)
        val inner = acos(((a * a + d * d - b * b) / (2 * a * d)).coerceIn(-1f, 1f))
        // For a leg hanging down, turning its direction clockwise moves the joint toward -x (forward).
        val angle = if (bendForward) base + inner else base - inner
        leg[0] = rx + cos(angle) * a
        leg[1] = ry + sin(angle) * a
        val toward = atan2(ty - leg[1], tx - leg[0])
        leg[2] = leg[0] + cos(toward) * b
        leg[3] = leg[1] + sin(toward) * b
    }

    /** A bone from ([x0], [y0]) to ([x1], [y1]): drawn hanging down from its upper joint, so turned to point at the lower one. */
    private fun bone(part: RigPart, x0: Float, y0: Float, x1: Float, y1: Float) = placeAt(part, atan2(-(x1 - x0), y1 - y0), x0, y0)

    private fun placeAt(part: RigPart, turn: Float, x: Float, y: Float) {
        val i = part.ordinal * 3
        placed[i] = turn
        placed[i + 1] = x
        placed[i + 2] = y
    }

    private companion object {
        val FAR_HIND_PARTS = arrayOf(RigPart.FAR_THIGH, RigPart.FAR_GASKIN, RigPart.FAR_HOCK, RigPart.FAR_HIND_PAW)
        val NEAR_HIND_PARTS = arrayOf(RigPart.NEAR_THIGH, RigPart.NEAR_GASKIN, RigPart.NEAR_HOCK, RigPart.NEAR_HIND_PAW)
        val FAR_FRONT_PARTS = arrayOf(RigPart.FAR_UPPER_ARM, RigPart.FAR_FOREARM, RigPart.FAR_FRONT_PAW)
        val NEAR_FRONT_PARTS = arrayOf(RigPart.NEAR_UPPER_ARM, RigPart.NEAR_FOREARM, RigPart.NEAR_FRONT_PAW)
    }
}

// Which leg is where in the gait. A walk is a four-beat walk: near hind, near front, far hind, far front, a quarter apart.
// A gallop is a rotary gallop: the hinds land close together, then the fronts half a stride later.
private const val NEAR_HIND = 0
private const val NEAR_FRONT = 1
private const val FAR_HIND = 2
private const val FAR_FRONT = 3
private val WALK_PHASES = floatArrayOf(0f, 0.25f, 0.5f, 0.75f)
private val GALLOP_PHASES = floatArrayOf(0.1f, 0.58f, 0f, 0.5f)

private const val WALK_DUTY = 0.62f // Share of a walking stride a paw is on the floor.
private const val GALLOP_DUTY = 0.34f
private const val GALLOP_REACH = 2.3f // A galloping stride against a walking one.
private const val GALLOP_LIFT = 1.7f
private const val GALLOP_PITCH = 0.09f
private const val GALLOP_PITCH_AT = 0.6f
private const val GALLOP_FLOAT = 0.9f
private const val GALLOP_FLOAT_AT = 2.2f
private const val WALK_BOB = 1.5f
private const val CROUCH = 0.42f // How far a full crouch drops the body, against the elbow's height.
private const val BOW_REACH = 2.6f // How far the front paws slide out in a full bow, in half strides.
private const val FOREARM_DOWN = 9f // A bowing wrist's height off the floor.
private const val BOW_HEAD_UP = 0.3f
private const val NECK_SHARE = 0.5f
private const val HOCK_FLEX = 0.9f // How much more the hock bends with the paw fully lifted, radians.
private const val HIND_PAW_FLEX = 0.5f
private const val FRONT_PAW_FLEX = 1.3f

/** The placement [t] (turn, x, y) turned [angle] more about its own point ([px], [py]), into [out]. */
private fun rotateAbout(t: FloatArray, px: Float, py: Float, angle: Float, out: FloatArray) {
    // p' = T(P + R(angle)(p - P)) = R(t + angle) p + T(P) - R(t + angle) P.
    val turn = t[0] + angle
    val wx = mapX(t, px, py)
    val wy = mapY(t, px, py)
    out[0] = turn
    out[1] = wx - (cos(turn) * px - sin(turn) * py)
    out[2] = wy - (sin(turn) * px + cos(turn) * py)
}

private fun mapX(t: FloatArray, x: Float, y: Float): Float = cos(t[0]) * x - sin(t[0]) * y + t[1]

private fun mapY(t: FloatArray, x: Float, y: Float): Float = sin(t[0]) * x + cos(t[0]) * y + t[2]
