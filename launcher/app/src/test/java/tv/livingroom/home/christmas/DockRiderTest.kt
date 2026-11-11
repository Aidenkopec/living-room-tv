package tv.livingroom.home.christmas

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockRiderTest {
    private val frames = motionSheet().sprites.rider

    /** Nine tiles 220 px apart with their tops at 900; [focus] is the selected one. */
    private fun target(focus: Int) = RideTarget().apply {
        this.focus = focus
        tiles = 9
        left = 300f + focus * 220f
        top = 900f
        width = 208f
    }

    /** Runs [ride] from [from] seconds for [seconds] at 60 frames a second on tile [focus]; gives back the time it ends. */
    private fun run(
        ride: DockRide,
        focus: Int,
        from: Double,
        seconds: Double,
        snowing: Boolean = false,
        each: (Double) -> Unit = {},
    ): Double {
        var t = from
        val frames = (seconds * 60).toInt()
        repeat(frames) {
            t += 1 / 60.0
            ride.step(t, target(focus), snowing)
            each(t)
        }
        return t
    }

    @Test
    fun itStartsStandingOnTheSelectedTileWithNoSnowUnderIt() {
        val ride = DockRide()
        ride.step(10.0, target(2), snowing = false)
        assertEquals(300f + 2 * 220f + 104f, ride.x, 0f)
        assertEquals(895f, ride.y, 0f) // On the selected tile's ring, 5 px above the tile.
        assertFalse(ride.inAir)
        assertEquals(0f, ride.height, 0f)
        assertEquals(listOf(1f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, 1f), ride.caps.toList())
        assertEquals(0, ride.bits.alive(10.0))
    }

    @Test
    fun aBoundEndsOnTheNewTileAndIsOnTheGroundAtBothEnds() {
        val ride = DockRide()
        var t = run(ride, 2, 0.0, 1.0)
        var highest = 0f
        var airFrames = 0
        t = run(ride, 5, t, 0.5) {
            highest = maxOf(highest, ride.height)
            if (ride.inAir) airFrames++ else assertEquals(0f, ride.height, 0f)
        }
        assertEquals(300f + 5 * 220f + 104f, ride.x, 0.01f)
        assertTrue(ride.facingRight)
        assertTrue("A long bound goes higher than 40 px: $highest", highest > 40f + 660f * 0.08f * 0.9f)
        assertEquals((HOP_SECONDS - 0.05) * 60, airFrames.toDouble(), 2.0)
        assertEquals(0.5 - HOP_SECONDS, ride.sinceLanding.toDouble(), 0.02)
        run(ride, 4, t, 0.5)
        assertFalse("It faces the way it went", ride.facingRight)
    }

    @Test
    fun aNewSelectionInMidAirStartsFromWhereItIs() {
        val ride = DockRide()
        var t = run(ride, 0, 0.0, 1.0)
        t = run(ride, 8, t, 0.2)
        assertTrue(ride.inAir)
        val midX = ride.x
        var last = midX
        var lastHeight = ride.height
        var landedAt = -1.0
        run(ride, 1, t, 0.6) {
            assertTrue("No jump between frames", abs(ride.x - last) < 80f)
            // It comes down from the height it was at, without dropping to the dock or crouching in mid-air.
            assertTrue("Height ${ride.height} after $lastHeight", abs(ride.height - lastHeight) < 40f)
            if (ride.inAir) assertTrue(ride.height > 0f)
            if (!ride.inAir && landedAt < 0) landedAt = it
            last = ride.x
            lastHeight = ride.height
        }
        assertTrue(midX > 400f)
        assertEquals(300f + 220f + 104f, ride.x, 0.01f)
        assertEquals("One bound without the crouch", t + HOP_SECONDS - 0.05, landedAt, 0.03)
    }

    @Test
    fun quickPressesKeepItBoundingTowardTheSelection() {
        val ride = DockRide()
        var t = run(ride, 0, 0.0, 1.0)
        var grounded = 0
        var frames = 0
        // A press every tenth of a second, all the way along.
        for (tile in 1..8) {
            t = run(ride, tile, t, 0.1) {
                frames++
                if (!ride.inAir) grounded++
            }
        }
        // Only the first crouch, three frames, is on the dock.
        assertTrue("$grounded of $frames frames on the dock", grounded <= 4)
        // Each new bound starts gently, so it trails the selection by a couple of tiles while the presses come this fast.
        assertTrue("It has kept up: at ${ride.x}", ride.x > 300f + 4 * 220f)
        run(ride, 8, t, 0.5)
        assertEquals(300f + 8 * 220f + 104f, ride.x, 0.01f)
        assertFalse(ride.inAir)
    }

    @Test
    fun snowGoesFromUnderItsFeetAndSettlesBackWhereItLeft() {
        val ride = DockRide()
        var t = run(ride, 2, 0.0, 1.0)
        // While it's in the air the new tile keeps its snow; within a fifth of a second of landing it's gone.
        t = run(ride, 3, t, HOP_SECONDS - 0.02)
        assertEquals(1f, ride.caps[3], 0f)
        t = run(ride, 3, t, 0.2)
        assertEquals(0f, ride.caps[3], 0f)
        // The tile it left fills again, never going down, in about half a minute; twice as fast in real snow.
        var last = ride.caps[2]
        t = run(ride, 3, t, 10.0) {
            assertTrue(ride.caps[2] >= last)
            last = ride.caps[2]
        }
        assertEquals(0.03f * (10 + HOP_SECONDS.toFloat() + 0.18f), ride.caps[2], 0.02f)
        val calm = ride.caps[2]
        t = run(ride, 3, t, 5.0, snowing = true)
        assertEquals(calm + 0.06f * 5, ride.caps[2], 0.02f)
        run(ride, 3, t, 40.0)
        assertEquals(1f, ride.caps[2], 0f)
        assertTrue(ride.caps.all { it in 0f..1f })
    }

    @Test
    fun aLandingThrowsUpMoreSnowTheDeeperItWas() {
        val deep = DockRide()
        var t = run(deep, 2, 0.0, 1.0)
        t = run(deep, 3, t, HOP_SECONDS + 0.05)
        assertEquals(10 + 24, deep.bits.alive(t))
        // Back onto the tile it has cleared: only the few bits a nearly bare tile gives.
        t = run(deep, 3, t, 0.3)
        assertEquals(0f, deep.caps[3], 0f)
        t = run(deep, 2, t, 2.0)
        t = run(deep, 3, t, HOP_SECONDS + 0.05)
        assertTrue("${deep.bits.alive(t)} bits", deep.bits.alive(t) in 13..16)
        run(deep, 3, t, 2.0)
        assertEquals("All of it has landed or faded", 0, deep.bits.alive(t + 2.0))
    }

    @Test
    fun looseSnowRisesFallsAndSpreads() {
        val ride = DockRide()
        var t = run(ride, 2, 0.0, 1.0)
        t = run(ride, 3, t, HOP_SECONDS + 0.05)
        val pieces = dockPieces(dockAtlas(listOf(208f)), listOf(208f), 1f)
        fun bits(at: Double) = SpriteQuads(96).also { ride.bits.into(it, pieces.clump, pieces.powder, at) }
        val early = bits(t)
        val late = bits(t + 0.7)
        assertEquals(34, early.count)
        fun middles(quads: SpriteQuads) = (0 until quads.count).map { (quads.corners[it * 8 + 1] + quads.corners[it * 8 + 5]) / 2 }
        // Everything starts at the tile's top; clumps are far below it most of a second later.
        assertTrue(middles(early).all { it in 850f..905f })
        assertTrue(middles(late).count { it > 950f } >= 8)
        // Bits start across the whole tile.
        val xs = (0 until early.count).map { early.corners[it * 8] }
        assertTrue(xs.min() < 300f + 3 * 220f + 60f && xs.max() > 300f + 3 * 220f + 150f)
    }

    @Test
    fun itShowsTheRightPictureForWhatItsDoing() {
        val ride = DockRide()
        var t = run(ride, 2, 0.0, 1.0)
        // Crouched for the first twentieth of a second, then through the air.
        ride.step(t + 0.01, target(4), false)
        assertEquals(frames.landing[0], riderSprite(frames, ride, t + 0.01))
        t = run(ride, 4, t, 0.2)
        assertTrue(riderSprite(frames, ride, t) in frames.air)
        // Landing: up from the crouch over the next sixth of a second.
        t = run(ride, 4, t, HOP_SECONDS - 0.2 + 0.03)
        assertTrue(riderSprite(frames, ride, t) in frames.landing)
        t = run(ride, 4, t, 1.0)
        assertTrue(riderSprite(frames, ride, t) in frames.wag)
        // Every 9.5 seconds: a look round after 2 s, a bow after 5.2 s, never both, never in the air.
        var looks = 0
        var bows = 0
        val seen = HashSet<Sprite>()
        run(ride, 4, t, 9.5) {
            val sprite = riderSprite(frames, ride, it)
            seen += sprite
            if (frames.looks.any { depth -> sprite in depth }) looks++
            if (frames.bows.any { depth -> sprite in depth }) bows++
            assertFalse(sprite in frames.air || sprite in frames.landing)
        }
        assertEquals(1.2f, looks / 60f, 0.25f)
        assertEquals(1.4f, bows / 60f, 0.25f)
        assertTrue(
            "The deepest bow and the widest look are reached",
            frames.bows.last().any { it in seen } && frames.looks.last().any { it in seen },
        )
        assertTrue("The tail wags across its frames", frames.wag.count { it in seen } >= 4)
    }

    @Test
    fun itIsDrawnOnItsFeetFacingTheWayItWent() {
        val ride = DockRide()
        var t = run(ride, 2, 0.0, 1.0)
        t = run(ride, 3, t, 1.0)
        val quads = SpriteQuads(2).also { riderInto(it, frames, ride, t, 1f) }
        assertEquals("Its shadow and itself", 2, quads.count)
        // Its feet are 3 px into the ring's top, and its picture is mirrored to face right.
        val sprite = riderSprite(frames, ride, t)
        assertEquals(895f + 3f, quads.corners[8 + 1] + sprite.anchorY, 0.01f)
        assertTrue(quads.corners[8] > quads.corners[8 + 2])
        t = run(ride, 2, t, 0.2)
        val air = SpriteQuads(2).also { riderInto(it, frames, ride, t, 1f) }
        assertEquals("No shadow in the air", 1, air.count)
        assertTrue(air.corners[0] < air.corners[2])
    }

    @Test
    fun aRowThatChangesPutsItStraightOnTheSelectedTile() {
        val ride = DockRide()
        val t = run(ride, 2, 0.0, 1.0)
        val wider = target(2).apply { tiles = 10 }
        ride.step(t + 0.02, wider, false)
        assertEquals(10, ride.caps.size)
        assertEquals(0f, ride.caps[2], 0f)
        assertFalse(ride.inAir)
    }

    @Test
    fun theRidersFramesShareOneBox() {
        val all = frames.air + frames.landing + frames.wag + frames.bows.flatten() + frames.looks.flatten()
        assertEquals(4 + 3 + 5 + 2 * RIDER_DEPTHS * 2, all.size)
        assertEquals(1, all.map { listOf(it.w, it.h, it.anchorX, it.anchorY) }.distinct().size)
        // About 110 x 75 px at the dock's size.
        assertTrue("${all[0].w} x ${all[0].h}", all[0].w in 90f..140f && all[0].h in 60f..100f)
    }

    @Test
    fun theRiderAimsAtTheSelectedTileOrNowhere() {
        val spots = TileSpots()
        val target = RideTarget()
        assertFalse("An empty row", target.aim(spots, 1.5f))
        spots.resize(3)
        for (i in 0 until 3) {
            spots.left[i] = 100f + i * 300f
            spots.top[i] = 950f - i
            spots.scale[i] = if (i == 1) 1f else 0.6f
            spots.tallest[i] = 208f
        }
        spots.focused = -1
        assertFalse("Nothing selected yet", target.aim(spots, 1.5f))
        spots.focused = 1
        assertTrue(target.aim(spots, 1.5f))
        assertEquals(1, target.focus)
        assertEquals(3, target.tiles)
        assertEquals(400f, target.left, 0f)
        assertEquals(949f, target.top, 0f)
        assertEquals(208f * 1.5f, target.width, 0.01f)
        spots.focused = 3
        assertFalse("A selection off the end of the row", target.aim(spots, 1.5f))
        assertEquals("Left as it was", 1, target.focus)
    }

    @Test
    fun manyQuickLandingsOnlyEverReplaceTheOldestLooseSnow() {
        val ride = DockRide()
        var t = run(ride, 0, 0.0, 1.0)
        // Every landing on a full tile throws up 34 bits; three in 1.2 s is more than the 96 there's room for.
        for (tile in 1..3) t = run(ride, tile, t, 0.4)
        val alive = ride.bits.alive(t)
        assertTrue("$alive bits", alive in 60..96)
        // What was dropped had already landed or nearly faded: everything from the last landing is still there.
        val last = DockRide().apply { run(this, 0, 0.0, 1.0) }
        assertTrue(alive >= 34)
        assertEquals(0, last.bits.alive(1.0))
    }
}
