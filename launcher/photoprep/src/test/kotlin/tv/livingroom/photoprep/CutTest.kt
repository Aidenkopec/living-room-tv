package tv.livingroom.photoprep

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.pow
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CutTest {
    // A made-up photo: a smooth sky getting paler toward the horizon over a dark, rocky triangle.
    private fun mountain(w: Int = 200, h: Int = 120, peakX: Int = 100, peakY: Int = 40, base: Int = 90): Pair<Channels, IntArray> {
        val random = Random(1)
        val top = IntArray(w) { x -> (peakY + abs(x - peakX) * 0.6f).toInt().coerceAtMost(base) }
        val r = FloatArray(w * h)
        val g = FloatArray(w * h)
        val b = FloatArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                if (y < top[x]) {
                    val t = y / h.toFloat()
                    r[i] = 0.45f + 0.3f * t
                    g[i] = 0.6f + 0.25f * t
                    b[i] = 0.9f
                } else {
                    val n = random.nextFloat() * 0.15f
                    r[i] = 0.18f + n
                    g[i] = 0.2f + n
                    b[i] = 0.22f + n
                }
            }
        }
        return Channels(w, h, r, g, b) to top
    }

    @Test fun skylineFindsTheRidgeRoughly() {
        // The first pass is on a blurred half-size copy, so it may be a couple of rows off.
        val (p, top) = mountain()
        val line = skyline(p, 10..110)
        val errors = line.indices.map { abs(line[it] - top[it]) }
        assertTrue("median error ${errors.sorted()[errors.size / 2]}", errors.sorted()[errors.size / 2] <= 2)
        assertTrue("worst error ${errors.max()}", errors.max() <= 4)
    }

    @Test fun cutFindsTheExactEdgePlusTheRim() {
        // At full size the edge snaps to the ridge, then 2 rows lower for the sharpening rim.
        val (p, top) = mountain(w = 400, h = 240, peakX = 200, peakY = 80, base = 180)
        val settings = RUNDLE.copy(
            band = 20..110,
            guides = emptyList(),
            cloudColumns = emptyList(),
            floor = emptyList(),
            exact = emptyList(),
            eyeRow = null,
            landNeverAbove = 0,
            summitRows = 0..1000,
        )
        val edge = cut(p, settings).edge
        val errors = edge.indices.map { abs(edge[it] - (top[it] + 2f)) }.sorted()
        assertTrue("median error ${errors[errors.size / 2]}", errors[errors.size / 2] <= 1f)
    }

    @Test fun guidesHoldTheLine() {
        val (p, _) = mountain()
        val line = skyline(p, 10..110, guides = listOf(Guide(0, 100, 2), Guide(199, 100, 2)))
        assertTrue(line.all { abs(it - 100) <= 2 })
    }

    @Test fun lineAtFullSizeDoublesRowsPlusOne() {
        val full = lineAtFullSize(intArrayOf(10, 10, 20, 20), 8)
        assertEquals(21f, full[0], 1e-4f)
        assertEquals(41f, full[7], 1e-4f)
    }

    // A full-size picture: sky above row 50, dark land from row 50.
    private fun sharp(w: Int = 40, h: Int = 100, edgeRow: Int = 50): Channels {
        val r = FloatArray(w * h) { if (it / w < edgeRow) 0.6f else 0.1f }
        val g = FloatArray(w * h) { if (it / w < edgeRow) 0.7f else 0.12f }
        val b = FloatArray(w * h) { if (it / w < edgeRow) 0.9f else 0.1f }
        return Channels(w, h, r, g, b)
    }

    @Test fun refineSnapsToTheEdgePlusTwo() {
        val p = sharp()
        val line = FloatArray(p.w) { 45f }
        val distance = distanceFromSky(p, skyAbove(p, line))
        val edge = refineEdge(line, distance, p.w, p.h)
        assertTrue(edge.all { abs(it - 52f) < 1e-4f })
    }

    @Test fun cloudCutClearsABroadBrightPatch() {
        val w = 60
        val h = 200
        // Bright cloud in rows 50-59, dark forest below, in every column.
        val lum = FloatArray(w * h) {
            val y = it / w
            if (y < 50) {
                0.7f
            } else if (y < 60) {
                0.8f
            } else {
                0.1f
            }
        }
        val out = cloudCut(FloatArray(w) { 50f }, lum, w, h, 0 until w)
        assertTrue(out.all { abs(it - 61.5f) < 1e-4f })
    }

    @Test fun cloudCutStopsAt40Rows() {
        val w = 60
        val h = 300
        val lum = FloatArray(w * h) {
            val y = it / w
            if (y < 50) {
                0.7f
            } else if (y < 150) {
                0.8f
            } else {
                0.1f
            }
        }
        val out = cloudCut(FloatArray(w) { 50f }, lum, w, h, 0 until w)
        assertTrue(out.all { it <= 50f + 40f + 1.5f + 1e-4f })
    }

    @Test fun cloudCutLeavesGapsBetweenTrees() {
        val w = 60
        val h = 200
        // Only 3 bright columns (sky between two trees): not a cloud.
        val lum = FloatArray(w * h) {
            val x =
                it % w
            val y = it / w
            if (y >= 50 && y < 60 && x in 30..32) {
                0.8f
            } else if (y < 50) {
                0.7f
            } else {
                0.1f
            }
        }
        val out = cloudCut(FloatArray(w) { 50f }, lum, w, h, 0 until w)
        assertTrue(out.all { it == 50f })
    }

    @Test fun floorsHoldAndExactSets() {
        val edge = FloatArray(10) { 5f }
        val out = applyFloors(edge, floor = listOf(Point(0, 8), Point(4, 8)), exact = listOf(Point(4, 2), Point(9, 2)))
        assertEquals(9f, out[0])
        assertEquals(9f, out[4]) // At least one under the floor line.
        assertEquals(3f, out[5])
        assertEquals(3f, out[9]) // Set exactly, even above the old edge.
    }

    @Test fun skyAlphaIsSkyAboveAndLandBelow() {
        val p = sharp()
        val edge = FloatArray(p.w) { 50f }
        val distance = distanceFromSky(p, skyAbove(p, edge))
        val a = skyAlpha(edge, distance, p.w, p.h)
        assertEquals(1f, a[10 * p.w], 1e-4f)
        assertEquals(0f, a[60 * p.w], 1e-4f)
    }

    @Test fun cleanLandTakesTheOldSkyOut() {
        val w = 1
        val h = 1
        val sky = Channels(1, 1, floatArrayOf(0.8f), floatArrayOf(0.8f), floatArrayOf(1f))
        // Half sky, half land of (0.2, 0.3, 0.1).
        val mixed =
            Channels(w, h, floatArrayOf(0.5f * 0.8f + 0.5f * 0.2f), floatArrayOf(0.5f * 0.8f + 0.5f * 0.3f), floatArrayOf(0.5f + 0.05f))
        val out = cleanLand(mixed, sky, floatArrayOf(0.5f), floatArrayOf(5f))
        assertEquals(0.2f, out.r[0], 1e-4f)
        assertEquals(0.3f, out.g[0], 1e-4f)
        assertEquals(0.1f, out.b[0], 1e-4f)
    }

    @Test fun waterShowsTheMirroredSkyBelowTheShore() {
        val w = 8
        val h = 100
        val edge = FloatArray(w) { 30f }
        val alpha = FloatArray(w * h) { if (it / w < 30) 1f else 0f }
        val water = waterAlpha(edge, alpha, w, h, eyeRow = 50f, shoreRow = 55f)
        // The skyline mirrors to row 70: sky below it, the mountain's reflection above it.
        assertEquals(1f, water[90 * w + 3], 1e-4f)
        assertEquals(0f, water[60 * w + 3], 1e-4f)
        assertEquals(0f, water[40 * w + 3], 1e-4f) // Above the shore: never water.
    }

    @Test fun rippleIsOneWhereTheWaterIsFlat() {
        val p = Channels(30, 30, FloatArray(900) { 0.4f }, FloatArray(900) { 0.5f }, FloatArray(900) { 0.6f })
        assertTrue(ripple(p).all { abs(it - 1f) < 1e-3f })
    }

    @Test fun layerPixelKeepsEachValue() {
        val px = layerPixel(sky = 1f, water = 0.5f, high = 0.25f, ripple = 1f)
        assertEquals(255, (px shr 16) and 0xFF)
        assertEquals(128, (px shr 8) and 0xFF)
        assertEquals(64, px and 0xFF)
        assertEquals((255f / RIPPLE_TOP).toInt().toFloat(), ((px ushr 24) and 0xFF).toFloat(), 1f)
        // The quietest shimmer still has some ripple stored, never 0.
        assertTrue(((layerPixel(0f, 0f, 0f, 0.6f) ushr 24) and 0xFF) > 100)
    }

    @Test fun pngHasNoColourChunks() {
        val image = BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)
        val bytes = ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
        val names = pngChunkNames(bytes)
        assertTrue(names.first() == "IHDR" && names.last() == "IEND")
        assertTrue("found ${names.filter { it in COLOUR_CHUNKS }}", names.none { it in COLOUR_CHUNKS })
    }

    @Test fun framingCatchesLandWhereTheSunTravels() {
        val (p, _) = mountain()
        val settings = RUNDLE.copy(
            band = 5..55,
            guides = emptyList(),
            cloudColumns = emptyList(),
            floor = emptyList(),
            exact = emptyList(),
            eyeRow = null,
            landNeverAbove = 60,
            summitRows = 0..1000,
        )
        val problems = framingProblems(cut(p, settings), settings)
        assertTrue(problems.joinToString(), problems.any { "above row 60" in it })
    }

    @Test fun helpersMatchTheMockupsMaths() {
        assertEquals(2f, median(floatArrayOf(3f, 1f, 2f)))
        assertEquals(2.5f, median(floatArrayOf(4f, 1f, 2f, 3f)))
        assertEquals(9.1f, percentile(FloatArray(11) { it.toFloat() }, 91f), 1e-4f)
        assertEquals(1.5f, interp(0.5f, floatArrayOf(0f, 1f), floatArrayOf(1f, 2f)))
        assertEquals(1f, interp(-3f, floatArrayOf(0f, 1f), floatArrayOf(1f, 2f)))
        // A 3 × 3 average of one bright pixel in the middle of 3 × 3.
        assertEquals(1f / 9f, box(floatArrayOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f), 3, 3, 1)[4], 1e-5f)
        assertEquals(0.5f.pow(2.2f), toLinear(0.5f), 1e-6f)
    }
}
