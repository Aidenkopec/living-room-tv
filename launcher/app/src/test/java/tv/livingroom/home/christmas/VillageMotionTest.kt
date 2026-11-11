package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.livingroom.home.screensaver.SkyWeather

class VillageMotionTest {
    private val sprites = motionSheet().sprites

    @Test
    fun everyFlakeStaysJustInsideThePicturesWrap() {
        val at = FloatArray(3)
        for (flakes in SNOW) {
            for (i in 0 until flakes.size) {
                for (t in listOf(0.0, 0.37, 12.9, 861.0, 86_400.0, 2_000_000.5)) {
                    flakeAt(flakes, i, t, at)
                    assertTrue("x ${at[0]}", at[0] >= -30f && at[0] <= VILLAGE_W + 30)
                    assertTrue("y ${at[1]}", at[1] >= -15f && at[1] <= VILLAGE_H + 15)
                    assertTrue("alpha ${at[2]}", at[2] > 0f && at[2] <= flakes.depth.alpha + 1e-4f)
                }
            }
        }
    }

    @Test
    fun aFlakeFallsAtItsDepthsSpeedTimesItsOwn() {
        val before = FloatArray(3)
        val after = FloatArray(3)
        for (flakes in SNOW) {
            var checked = 0
            for (i in 0 until flakes.size) {
                flakeAt(flakes, i, 100.0, before)
                flakeAt(flakes, i, 100.5, after)
                if (after[1] < before[1]) continue // It wrapped round to the top.
                assertEquals(flakes.depth.fall * flakes.speed[i] * 0.5f, after[1] - before[1], 0.05f)
                assertTrue(flakes.speed[i] in 0.6f..1.4f)
                checked++
            }
            assertTrue(checked > flakes.size / 2)
        }
    }

    @Test
    fun aFlakeNeverJumpsBetweenFramesExceptRoundTheEdge() {
        val before = FloatArray(3)
        val after = FloatArray(3)
        val flakes = SNOW[2]
        for (frame in 0 until 600) {
            val t = 40.0 + frame / 60.0
            for (i in 0 until flakes.falling(1.15f)) {
                flakeAt(flakes, i, t, before)
                flakeAt(flakes, i, t + 1 / 60.0, after)
                val wrapped = abs(after[0] - before[0]) > VILLAGE_W || abs(after[1] - before[1]) > VILLAGE_H
                assertTrue(wrapped || (abs(after[0] - before[0]) < 2f && abs(after[1] - before[1]) < 3f))
            }
        }
    }

    @Test
    fun moreSnowFallsWhenTheWeatherSaysSnow() {
        val calm = MotionFrame().also { snowInto(it, sprites.flakes, 5.0, snowAmount(SkyWeather.CLEAR)) }.plain.count
        val snowing = MotionFrame().also { snowInto(it, sprites.flakes, 5.0, snowAmount(SkyWeather.SNOW)) }.plain.count
        assertEquals(60 + 36 + 10, calm)
        assertEquals(173 + 104 + 30, snowing)
        assertTrue("Room for the heaviest snow and the stress test's flakes", snowing + 700 <= MotionFrame().plain.capacity)
    }

    @Test
    fun aFlakeIsDrawnNoBiggerThanItsDisc() {
        val frame = MotionFrame().also { snowInto(it, sprites.flakes, 5.0, 3f) }
        for (i in 0 until frame.plain.count) {
            val width = frame.plain.corners[i * 8 + 2] - frame.plain.corners[i * 8]
            val sheetWidth = frame.plain.sheet[i * 8 + 2] - frame.plain.sheet[i * 8]
            assertTrue(width > 0f && width <= sheetWidth)
        }
    }

    @Test
    fun aPuffRisesGrowsAndIsUnseenAtBothEnds() {
        val (x, y) = VILLAGE_CHIMNEYS.first()
        val at = FloatArray(4)
        var last = FloatArray(4)
        // Follow puff 0 through one life from the moment it starts.
        val start = (1 - ((x * 0.11) % 1.0)) / 0.11
        for (step in 0..90) {
            smokePuffAt(x, y, 0, start + step * 0.1, at)
            if (step > 0) {
                assertTrue("rises", at[1] < last[1])
                assertTrue("grows", at[2] > last[2])
            }
            assertTrue(at[3] in 0f..0.4f)
            last = at.copyOf()
        }
        smokePuffAt(x, y, 0, start + 0.001, at)
        assertEquals(0f, at[3], 0.01f)
        assertEquals(y, at[1], 0.2f)
        smokePuffAt(x, y, 0, start + 1 / 0.11 - 0.001, at)
        assertEquals(0f, at[3], 0.01f)
        assertEquals(y - 125f, at[1], 0.2f)
    }

    @Test
    fun smokeIsNinePuffsAChimneyInTheLitList() {
        val frame = MotionFrame().also { smokeInto(it, sprites.puff, VILLAGE_CHIMNEYS, 33.3) }
        assertTrue(frame.lit.count in 40..45) // A puff that has just started is too faint to add.
        assertEquals(0, frame.plain.count + frame.lights.count)
    }

    @Test
    fun aTwinkleNeverGoesDarkerThanThePictureOrBrighterThanFull() {
        val string = VILLAGE_TWINKLES.first()
        val treeBulb = VILLAGE_TWINKLES.first { it.onTree && it.light == WARM_LIGHT && it.gain > 0.4f }
        val star = VILLAGE_TWINKLES.last()
        assertEquals(HALO_LIGHT, star.light)
        var low = 1f
        var high = 0f
        for (step in 0..2000) {
            val t = step * 0.013
            val s = string.strength(t)
            low = minOf(low, s)
            high = maxOf(high, s)
            assertTrue(s >= 0f && s <= 1 - STRING_DIMMEST + 1e-4f)
            // The tree's lights and the star: what's added on top of the picture's dimmest, in the sheet's 0.55 units.
            assertTrue(treeBulb.strength(t) in 0f..(1 - TREE_DIMMEST) * 0.6f / 0.55f + 1e-4f)
            assertTrue(star.strength(t) in 0f..(1 - STAR_DIMMEST) * 0.7f / 0.55f + 1e-4f)
        }
        assertEquals(0f, low, 0.01f)
        assertEquals(1 - STRING_DIMMEST, high, 0.01f)
    }

    @Test
    fun aTwinkleMovesGentlyFromFrameToFrame() {
        for (twinkle in VILLAGE_TWINKLES) {
            for (frame in 0 until 240) {
                val t = 9.0 + frame / 60.0
                assertTrue(abs(twinkle.strength(t + 1 / 60.0) - twinkle.strength(t)) < 0.02f)
            }
        }
    }

    @Test
    fun neighbouringBulbsAreOutOfStep() {
        val (a, b) = VILLAGE_TWINKLES.take(2)
        assertTrue(abs((b.offset - a.offset) % (2 * PI.toFloat())) > 1f)
    }

    @Test
    fun twinklesAreDimmerByDayButTheTreeStaysLit() {
        val t = 4.2
        val day = MotionFrame().also { twinklesInto(it.lights, sprites.lights, VILLAGE_TWINKLES, t, AFTERNOON.lights) }.lights
        val evening = MotionFrame().also { twinklesInto(it.lights, sprites.lights, VILLAGE_TWINKLES, t, EVENING.lights) }.lights
        fun light(quads: SpriteQuads) = (0 until quads.count).sumOf { quads.strengths[it * 4] ushr 24 }
        assertTrue(evening.count > 90 && day.count <= evening.count)
        assertTrue(light(day) < light(evening) / 2)
        assertEquals(0.35f, treeBrightness(AFTERNOON.lights), 0f)
        assertEquals(1f, treeBrightness(EVENING.lights), 0f)
    }

    @Test
    fun theDocksBulbsTwinkleOverTheStringsOwnBulbs() {
        val quads = SpriteQuads(64)
        val madeFor = 900f
        val place = StringPlace(left = 500f, bulbsY = 1000f, width = 945f, madeFor = madeFor)
        dockTwinklesInto(quads, sprites.lights, place, brightness = 1f, t = 0.7)
        val count = dockBulbCount(madeFor)
        assertTrue(quads.count in count - 3..count) // One at the very bottom of its swing isn't added.
        // Each light is centred on a bulb of the string's picture, stretched as the picture is.
        val centres = (0 until quads.count).map { (quads.corners[it * 8] + quads.corners[it * 8 + 2]) / 2 }
        val bulbs = (0 until count).map { 500f + dockBulbX(it, count, madeFor) * 945f / madeFor }
        for (centre in centres) assertTrue(bulbs.any { abs(it - centre) < 0.01f })
        assertEquals(1000f, (quads.corners[1] + quads.corners[5]) / 2, 0.01f)
    }
}
