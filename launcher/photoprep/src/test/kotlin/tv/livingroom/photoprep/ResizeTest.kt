package tv.livingroom.photoprep

import java.io.File
import javax.imageio.ImageIO
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResizeTest {
    private fun grey(v: Int) = (v shl 16) or (v shl 8) or v

    private fun flat(w: Int, h: Int, v: Float) = Channels(w, h, FloatArray(w * h) { v }, FloatArray(w * h) { v }, FloatArray(w * h) { v })

    @Test fun lanczosIsOneAtZeroAndZeroAtWholeNumbersAndPastThree() {
        assertEquals(1.0, lanczos3(0.0), 1e-9)
        for (x in listOf(1.0, 2.0, -1.0, -2.0, 3.0, 3.5, -4.0)) assertEquals(0.0, lanczos3(x), 1e-9)
    }

    @Test fun tapsSumToOneAndCoverTheWidenedKernel() {
        val taps = lanczosTaps(7114, 1920)
        for (w in taps.weights) assertEquals(1f, w.sum(), 1e-4f)
        // 3 lobes each side, widened 3.7 times: about 22 input pixels per output pixel.
        assertTrue(taps.weights[960].size in 21..24)
    }

    @Test fun onlyShrinks() {
        val grew = runCatching { lanczosTaps(100, 200) }
        assertTrue(grew.isFailure)
    }

    @Test fun aFlatPictureStaysFlat() {
        val shrunk = shrink(40, 30, IntArray(40 * 30) { grey(128) }, 16, 9)
        assertEquals(16, shrunk.w)
        assertEquals(9, shrunk.h)
        for (v in shrunk.r + shrunk.g + shrunk.b) assertEquals(128 / 255f, v, 1e-4f)
    }

    @Test fun fineStripesAverageToGreyInsteadOfAliasing() {
        // One-pixel black and white columns shrunk 4 times: a plain scaler picks some up; this averages them.
        // (The outermost columns lean to the edge pixel, which repeats outwards.)
        val shrunk = shrink(64, 8, IntArray(64 * 8) { if (it % 2 == 0) grey(255) else grey(0) }, 16, 2)
        for (y in 0 until 2) for (x in 2 until 14) assertEquals(0.5f, shrunk.r[y * 16 + x], 0.03f)
    }

    @Test fun channelsStayApart() {
        val red = 0xFF0000
        val shrunk = shrink(20, 20, IntArray(400) { red }, 10, 10)
        assertTrue(shrunk.r.all { abs(it - 1f) < 1e-4f })
        assertTrue(shrunk.g.all { abs(it) < 1e-4f } && shrunk.b.all { abs(it) < 1e-4f })
    }

    @Test fun aHardEdgeStaysInPlaceAndWithinRange() {
        // Dark left half, light right half, shrunk 3 times: the edge stays in the middle, values stay 0..1.
        val shrunk = shrink(60, 3, IntArray(180) { if (it % 60 < 30) grey(20) else grey(230) }, 20, 1)
        assertTrue(shrunk.r.all { it in 0f..1f })
        assertTrue(shrunk.r[8] < 0.2f && shrunk.r[11] > 0.8f)
        assertEquals((20 + 230) / 2 / 255f, (shrunk.r[9] + shrunk.r[10]) / 2, 0.03f)
    }

    @Test fun gaussianKeepsFlatAndSpreadsADot() {
        val flatBlur = gaussian(FloatArray(100) { 0.3f }, 10, 10, 0.7f)
        assertTrue(flatBlur.all { abs(it - 0.3f) < 1e-5f })
        val dot = FloatArray(81).also { it[40] = 1f }
        val spread = gaussian(dot, 9, 9, 0.7f)
        assertEquals(1f, spread.sum(), 1e-4f)
        assertTrue(spread[40] in 0.2f..0.5f && spread[41] > 0f && spread[41] < spread[40])
    }

    @Test fun sharpeningLeavesFlatAreasAlone() {
        val p = flat(12, 12, 0.4f)
        val out = sharpen(p, LAND_SHARPENING)
        assertTrue(out.r.contentEquals(p.r) && out.g.contentEquals(p.g) && out.b.contentEquals(p.b))
    }

    @Test fun sharpeningMakesAnEdgeSteeperButOnlyALittle() {
        val w = 20
        val p = Channels(
            w,
            4,
            FloatArray(80) {
                if (it % w <
                    10
                ) {
                    0.3f
                } else {
                    0.6f
                }
            },
            FloatArray(80) { if (it % w < 10) 0.3f else 0.6f },
            FloatArray(80) { if (it % w < 10) 0.3f else 0.6f },
        )
        val out = sharpen(p, LAND_SHARPENING)
        val dark = out.r[w + 9]
        val light = out.r[w + 10]
        assertTrue("dark side darker", dark < 0.3f)
        assertTrue("light side lighter", light > 0.6f)
        // Light: the overshoot is a small part of the 0.3 step, not a halo.
        assertTrue(0.3f - dark < 0.06f && light - 0.6f < 0.06f)
        // Away from the edge nothing changes.
        assertEquals(0.3f, out.r[w + 2], 1e-6f)
        assertEquals(0.6f, out.r[w + 17], 1e-6f)
    }

    @Test fun sharpeningIgnoresDifferencesUnderTheThreshold() {
        val w = 20
        val tiny = Channels(w, 4, FloatArray(80) { if (it % w < 10) 0.4f else 0.402f }, FloatArray(80) { 0.4f }, FloatArray(80) { 0.4f })
        val out = sharpen(tiny, LAND_SHARPENING)
        assertTrue(out.r.contentEquals(tiny.r))
    }

    @Test fun sharpeningLiftsBrightnessNotColour() {
        // A red patch on grey: every channel moves by the same amount, so the hue doesn't fringe.
        val w = 10
        val r = FloatArray(100) { if (it % w < 5) 0.7f else 0.4f }
        val g = FloatArray(100) { 0.4f }
        val b = FloatArray(100) { 0.4f }
        val out = sharpen(Channels(w, 10, r, g, b), LAND_SHARPENING)
        val i = 5 * w + 4
        assertEquals(out.r[i] - r[i], out.g[i] - g[i], 1e-6f)
        assertEquals(out.g[i] - g[i], out.b[i] - b[i], 1e-6f)
    }

    @Test fun aZeroWeightLeavesAPixelExactlyAsItWas() {
        val w = 20
        val p = Channels(w, 4, FloatArray(80) { if (it % w < 10) 0.3f else 0.6f }, FloatArray(80) { 0.5f }, FloatArray(80) { 0.5f })
        val out = sharpen(p, LAND_SHARPENING, weight = FloatArray(80) { 0f })
        assertTrue(out.r.contentEquals(p.r))
    }

    @Test fun theScreenCheckWantsSixteenByNineAndAtLeastTheScreen() {
        assertTrue(fitsScreen(1920, 1080))
        assertTrue(fitsScreen(7114, 4002)) // Rundle's cut: 7114 × 9 / 16 = 4001.6, rounded up.
        assertFalse(fitsScreen(1280, 720))
        assertFalse(fitsScreen(1920, 1200))
    }

    @Test fun theJpegKeepsColourAtFullResolution() {
        val file = File.createTempFile("land", ".jpg")
        try {
            writeJpeg(flat(32, 18, 0.5f), file, 0.95f)
            val reader = ImageIO.getImageReadersByFormatName("jpeg").next()
            ImageIO.createImageInputStream(file).use { stream ->
                reader.input = stream
                val tree = reader.getImageMetadata(0).getAsTree(JPEG_METADATA) as IIOMetadataNode
                val components = tree.getElementsByTagName("componentSpec")
                assertEquals(3, components.length)
                for (i in 0 until components.length) {
                    val c = components.item(i) as IIOMetadataNode
                    assertEquals("1", c.getAttribute("HsamplingFactor"))
                    assertEquals("1", c.getAttribute("VsamplingFactor"))
                }
            }
            reader.dispose()
            val back = ImageIO.read(file)
            assertEquals(32, back.width)
            assertEquals(18, back.height)
        } finally {
            file.delete()
        }
    }
}
