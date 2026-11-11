package tv.livingroom.home.christmas

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DogRigTest {
    private fun rig(dog: Dog, pose: DogPose = DogPose()) = DogRig().apply { pose(dog, pose) }

    /** Where a piece's own point ([x], [y]) lands. */
    private fun DogRig.at(part: RigPart, x: Float, y: Float): Pair<Float, Float> {
        val t = turn(part)
        return cos(t) * x - sin(t) * y + x(part) to sin(t) * x + cos(t) * y + y(part)
    }

    /** The bottom of a leg bone [length] long hanging from [part]'s joint. */
    private fun DogRig.end(part: RigPart, length: Float) = at(part, 0f, length)

    @Test
    fun standingSquareEveryJointIsWhereTheBuildPutsIt() {
        for (dog in Dog.entries) {
            val b = buildOf(dog)
            val r = rig(dog)
            assertEquals(0f, r.turn(RigPart.TORSO), 1e-5f)
            assertEquals(b.front[0], r.x(RigPart.NEAR_UPPER_ARM), 0.01f)
            assertEquals(b.front[1], r.y(RigPart.NEAR_UPPER_ARM), 0.01f)
            val (ex, ey) = r.end(RigPart.NEAR_UPPER_ARM, boneLength(b.front, 0))
            assertEquals(b.front[2], ex, 0.05f)
            assertEquals(b.front[3], ey, 0.05f)
            assertEquals(b.hind[2], r.end(RigPart.NEAR_THIGH, boneLength(b.hind, 0)).first, 0.05f)
            assertEquals(b.hind[6], r.x(RigPart.NEAR_HIND_PAW), 0.05f)
            assertEquals(b.hind[7], r.y(RigPart.NEAR_HIND_PAW), 0.05f)
            assertEquals(0f, r.turn(RigPart.NEAR_FRONT_PAW), 1e-5f) // The paws flat on the floor.
        }
    }

    @Test
    fun theBonesKeepTheirLengthsAndBendTheWayADogsDo() {
        for (dog in Dog.entries) {
            val b = buildOf(dog)
            for (pose in listOf(DogPose(up = 0.2f), DogPose(bow = 1f), DogPose(stride = 0.3f), DogPose(stride = 0.7f, gallop = true))) {
                val r = rig(dog, pose)
                // Each bone ends where the next begins.
                val elbow = r.end(RigPart.NEAR_UPPER_ARM, boneLength(b.front, 0))
                assertEquals("$dog $pose", r.x(RigPart.NEAR_FOREARM), elbow.first, 0.05f)
                assertEquals(r.y(RigPart.NEAR_FOREARM), elbow.second, 0.05f)
                val wrist = r.end(RigPart.NEAR_FOREARM, boneLength(b.front, 1))
                assertEquals(r.x(RigPart.NEAR_FRONT_PAW), wrist.first, 0.05f)
                val stifle = r.end(RigPart.NEAR_THIGH, boneLength(b.hind, 0))
                assertEquals(r.x(RigPart.NEAR_GASKIN), stifle.first, 0.05f)
                val hock = r.end(RigPart.NEAR_GASKIN, boneLength(b.hind, 1))
                assertEquals(r.x(RigPart.NEAR_HOCK), hock.first, 0.05f)
                // The elbow sits behind the line from shoulder to wrist; the stifle ahead of the line from hip to hock.
                assertTrue("$dog $pose elbow", side(r.x(RigPart.NEAR_UPPER_ARM), r.y(RigPart.NEAR_UPPER_ARM), wrist, elbow) < 0f)
                assertTrue("$dog $pose stifle", side(r.x(RigPart.NEAR_THIGH), r.y(RigPart.NEAR_THIGH), hock, stifle) > 0f)
            }
        }
    }

    /** Which side of the line from ([x0], [y0]) to [to] the point [p] is: positive toward -x for a line running down. */
    private fun side(x0: Float, y0: Float, to: Pair<Float, Float>, p: Pair<Float, Float>): Float =
        (to.first - x0) * (p.second - y0) - (to.second - y0) * (p.first - x0)

    @Test
    fun aCrouchBendsTheLegsWithThePawsWhereTheyWere() {
        for (dog in Dog.entries) {
            val standing = rig(dog)
            val crouched = rig(dog, DogPose(up = 0f))
            assertTrue(crouched.y(RigPart.TORSO) > standing.y(RigPart.TORSO) + 15f)
            assertEquals(standing.x(RigPart.NEAR_HIND_PAW), crouched.x(RigPart.NEAR_HIND_PAW), 0.05f)
            assertEquals(standing.y(RigPart.NEAR_FRONT_PAW), crouched.y(RigPart.NEAR_FRONT_PAW), 0.05f)
        }
    }

    @Test
    fun aBowBringsTheChestDownAndThePawsOutAheadAndLeavesTheHindFeet() {
        for (dog in Dog.entries) {
            val standing = rig(dog)
            val bowing = rig(dog, DogPose(bow = 1f))
            assertTrue(bowing.y(RigPart.NEAR_UPPER_ARM) > standing.y(RigPart.NEAR_UPPER_ARM) + 25f)
            assertTrue(bowing.x(RigPart.NEAR_FRONT_PAW) < standing.x(RigPart.NEAR_FRONT_PAW) - 30f)
            assertTrue(bowing.y(RigPart.NEAR_FRONT_PAW) > standing.y(RigPart.NEAR_FRONT_PAW)) // The wrist down near the floor.
            assertEquals(standing.x(RigPart.NEAR_HIND_PAW), bowing.x(RigPart.NEAR_HIND_PAW), 0.05f)
            assertEquals(standing.y(RigPart.NEAR_HIND_PAW), bowing.y(RigPart.NEAR_HIND_PAW), 0.05f)
        }
    }

    @Test
    fun aWalkIsFourBeatsAndRepeatsEachStride() {
        val b = buildOf(Dog.SMALL)
        var lowest = 0f
        val near = FloatArray(4)
        for (i in 0 until 40) {
            val r = rig(Dog.SMALL, DogPose(stride = i / 40f))
            lowest = maxOf(lowest, r.y(RigPart.NEAR_HIND_PAW) - b.hind[7])
            // A walk always has at least two paws on the floor.
            val down = listOf(RigPart.NEAR_HIND_PAW, RigPart.FAR_HIND_PAW, RigPart.NEAR_FRONT_PAW, RigPart.FAR_FRONT_PAW).count {
                abs(r.turn(it)) < 1e-4f
            }
            assertTrue("at ${i / 40f}: $down down", down >= 2)
            if (i % 10 == 0) near[i / 10] = r.x(RigPart.NEAR_HIND_PAW)
        }
        assertEquals(0f, lowest, 0.01f) // Never pushed into the floor.
        val once = rig(Dog.SMALL, DogPose(stride = 0.3f)).x(RigPart.NEAR_HIND_PAW)
        assertEquals(once, rig(Dog.SMALL, DogPose(stride = 1.3f)).x(RigPart.NEAR_HIND_PAW), 0.01f)
        assertTrue(near.max() - near.min() > b.reach) // The paw swings through a real stride.
    }

    @Test
    fun aGallopReachesFurtherAndLeavesTheFloor() {
        val walkReach = (0 until 40).map { rig(Dog.BIG, DogPose(stride = it / 40f)).x(RigPart.NEAR_FRONT_PAW) }
        val gallopReach = (0 until 40).map { rig(Dog.BIG, DogPose(stride = it / 40f, gallop = true)).x(RigPart.NEAR_FRONT_PAW) }
        assertTrue(gallopReach.max() - gallopReach.min() > 1.5f * (walkReach.max() - walkReach.min()))
        val airborne = (0 until 40).any { i ->
            val r = rig(Dog.BIG, DogPose(stride = i / 40f, gallop = true))
            listOf(RigPart.NEAR_HIND_PAW, RigPart.FAR_HIND_PAW, RigPart.NEAR_FRONT_PAW, RigPart.FAR_FRONT_PAW).none {
                abs(r.turn(it)) <
                    1e-4f
            }
        }
        assertTrue(airborne)
    }

    @Test
    fun theHeadTurnsAboutTheNeckAndTheNeckTakesPartOfIt() {
        val level = rig(Dog.SMALL)
        val sniff = rig(Dog.SMALL, DogPose(head = -0.5f))
        assertTrue(sniff.turn(RigPart.NECK) < 0f && sniff.turn(RigPart.NECK) > -0.5f)
        assertEquals(-0.5f, sniff.turn(RigPart.HEAD), 1e-4f)
        // The head's joint stays on the neck where the build puts it.
        val b = buildOf(Dog.SMALL)
        val joint = sniff.at(RigPart.HEAD, HEAD_JOINT_X, HEAD_JOINT_Y)
        val onNeck = sniff.at(RigPart.NECK, b.headJoint[0], b.headJoint[1])
        assertEquals(onNeck.first, joint.first, 0.01f)
        assertEquals(onNeck.second, joint.second, 0.01f)
        val sniffing = marksBounds(dogMarks(Dog.SMALL, DogPose(head = -0.5f)))
        assertTrue(sniffing.bottom() >= marksBounds(dogMarks(Dog.SMALL, DogPose())).bottom() - 0.01f)
        assertEquals(level.x(RigPart.TORSO), sniff.x(RigPart.TORSO), 0f)
    }

    @Test
    fun theDogsStandOnTheFloorFacingLeftTheBigOneLargest() {
        val small = marksBounds(dogMarks(Dog.SMALL, DogPose()))
        val big = marksBounds(dogMarks(Dog.BIG, DogPose()))
        assertTrue(big.w > small.w + 10f)
        assertTrue(big.h > small.h + 25f)
        for (box in listOf(small, big)) {
            assertTrue("nose at ${box.x}", box.x < -150f && box.x > -220f)
            assertTrue("tail at ${box.x + box.w}", box.x + box.w > 85f && box.x + box.w < 120f)
            assertTrue("paws on the floor: ${box.bottom()}", box.bottom() > -0.5f && box.bottom() < 2.5f)
        }
    }

    @Test
    fun theCollarCrossesTheNeckRatherThanRunningAlongIt() {
        // The collar's band runs from the back of the neck to the throat at a steep slant down and forward, roughly square to
        // the neck, which runs up and forward at the head.
        for (dog in Dog.entries) {
            val (lx, ly) = leadPoint(dog)
            val b = buildOf(dog)
            val neckAngle = atan2(b.headJoint[1] - b.neckBase[1], b.headJoint[0] - b.neckBase[0])
            val box = marksBounds(rigPartMarks(dog, RigPart.COLLAR))
            assertTrue(box.w > 25f) // Wide across: it wraps round the neck, not a short vertical tick.
            val ringAngle = atan2(ly - (box.y), lx - (box.x + box.w))
            val between = abs(((ringAngle - neckAngle + 3 * Math.PI) % (2 * Math.PI) - Math.PI).toFloat())
            assertTrue("$dog: collar at ${Math.toDegrees(between.toDouble())} degrees to the neck", between > 1.0f && between < 2.2f)
        }
    }

    @Test
    fun theShadowLiesOnTheFloorUnderTheDog() {
        for (dog in Dog.entries) {
            val shadow = marksBounds(dogShadowMarks(dog))
            val body = marksBounds(dogMarks(dog, DogPose()))
            assertEquals(0f, shadow.x + shadow.w / 2, 0.01f)
            assertTrue(shadow.h < 30f && abs(shadow.y + shadow.h / 2 - 1f) < 0.01f)
            assertTrue(shadow.x > body.x && shadow.x + shadow.w > body.x + body.w - 30f)
        }
    }

    @Test
    fun theBallIsCarriedAtTheNoseAndTheMouthOpensToPant() {
        val without = dogMarks(Dog.BIG, DogPose())
        val with = dogMarks(Dog.BIG, DogPose(ball = true))
        assertTrue(with.size > without.size)
        assertTrue(marksBounds(with).x < marksBounds(without).x - 2f) // It sticks out past the nose.
        assertTrue(dogMarks(Dog.SMALL, DogPose(pant = true)).size > dogMarks(Dog.SMALL, DogPose()).size)
    }

    @Test
    fun theTailWags() {
        for (dog in Dog.entries) {
            val left = marksBounds(rigPartMarks(dog, RigPart.TAIL, DogPose(tail = -1f)))
            val right = marksBounds(rigPartMarks(dog, RigPart.TAIL, DogPose(tail = 1f)))
            assertTrue(hypot(left.x - right.x, left.y - right.y) > 3f || abs(left.w - right.w) > 3f)
            assertEquals(right, marksBounds(rigPartMarks(dog, RigPart.TAIL, DogPose(tail = 3f)))) // Held to its range.
        }
    }

    @Test
    fun theLeadMeetsTheCollarUnderTheThroat() {
        for (dog in Dog.entries) {
            val (x, y) = leadPoint(dog)
            val collar = marksBounds(rigPartMarks(dog, RigPart.COLLAR))
            assertTrue(x >= collar.x - 2f && x <= collar.x + collar.w + 2f)
            assertTrue(y >= collar.y && y <= collar.y + collar.h + 2f)
        }
        assertTrue(leadPoint(Dog.BIG).second < leadPoint(Dog.SMALL).second)
    }
}

private fun Area.bottom() = y + h
