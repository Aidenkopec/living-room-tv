package tv.livingroom.home.sky

import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.imageio.ImageIO
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import tv.livingroom.home.screensaver.SkyWeather

class HomePhotoTest {
    // --- The list ---------------------------------------------------------------------

    private val header = "land\tlayers\tplace\tregion\tcredit\tlicence\tpage\tchanges\teye_row\tland_top"
    private val row = "home/rundle-land.jpg\thome/rundle-layers.png\tMount Rundle\tFirst Vermilion Lake, Banff National Park, Canada\t" +
        "Photo: DXR, CC BY-SA 4.0, via Wikimedia Commons\tCC BY-SA 4.0\thttps://commons.wikimedia.org/wiki/File:X.jpg\t" +
        "Cropped, sky replaced, colours changed with the time of day\t747.24\t392"

    @Test
    fun readsTheList() {
        val p = parseHomePhoto("$header\n$row\n")!!
        assertEquals("home/rundle-land.jpg", p.land)
        assertEquals("Mount Rundle", p.place)
        assertEquals(747.24f, p.eyeRow, 1e-4f)
        assertEquals(392, p.landTop)
    }

    @Test
    fun brokenListsReadAsNone() {
        assertNull(parseHomePhoto(""))
        assertNull(parseHomePhoto(header))
        assertNull(parseHomePhoto("$header\n${row.replace("747.24", "high")}"))
        assertNull(parseHomePhoto("$header\n${row.substringBeforeLast('\t')}"))
        assertNull(parseHomePhoto("$header\n$row\n$row"))
    }

    @Test
    fun theCommittedListIsSane() {
        val p = parseHomePhoto(File("src/main/assets/home-photo.tsv").readText())
        assertNotNull("home-photo.tsv doesn't read", p)
        p!!
        // The eye level is inside the picture, the land starts below the sun's arc (150 dp = 300 px) and above the eye level.
        assertTrue(p.eyeRow in 0f..1080f)
        assertTrue(p.landTop >= (DockSkyLayout.ARC.top * 2).toInt() && p.landTop < p.eyeRow)
        assertTrue(p.credit.isNotBlank() && p.licence.isNotBlank() && p.changes.isNotBlank())
    }

    // --- The light ----------------------------------------------------------------------

    @Test
    fun gradeMatchesTheKeyframes() {
        val day = landGrade(12.0, SkyWeather.CLEAR, moonUp = false)
        assertEquals(1.08f, day.gain, 1e-5f)
        assertEquals(0f, day.glow, 1e-5f)
        val low = landGrade(1.0, SkyWeather.CLEAR, moonUp = false)
        assertEquals(0.70f, low.gain, 1e-5f)
        assertEquals(0.5f, low.glow, 1e-5f)
        assertTrue("warm at low sun", low.tintR > low.tintB)
        val night = landGrade(-12.0, SkyWeather.CLEAR, moonUp = true)
        assertEquals(0.15f, night.gain, 1e-5f)
        assertTrue("blue at night", night.tintB > night.tintR)
        assertEquals(1f, night.moonlight, 1e-5f)
    }

    @Test
    fun gradeIsFlatOutsideAndHalfwayBetween() {
        assertEquals(landGrade(12.0, SkyWeather.CLEAR, false), landGrade(60.0, SkyWeather.CLEAR, false))
        assertEquals(landGrade(-12.0, SkyWeather.CLEAR, true), landGrade(-40.0, SkyWeather.CLEAR, true))
        val mid = landGrade(3.0, SkyWeather.CLEAR, false)
        assertEquals((0.86f + 0.70f) / 2, mid.gain, 1e-5f)
    }

    @Test
    fun gradeMovesSmoothly() {
        var e = 20.0
        var last = landGrade(e, SkyWeather.CLEAR, true)
        while (e > -20.0) {
            e -= 0.05
            val g = landGrade(e, SkyWeather.CLEAR, true)
            assertTrue("gain jumps at $e", abs(g.gain - last.gain) < 0.01f)
            assertTrue("glow jumps at $e", abs(g.glow - last.glow) < 0.01f)
            last = g
        }
    }

    @Test
    fun weatherTakesTheGlowAway() {
        val clear = landGrade(3.0, SkyWeather.CLEAR, false)
        for (w in listOf(SkyWeather.CLOUDY, SkyWeather.RAIN, SkyWeather.SNOW)) {
            val g = landGrade(3.0, w, false)
            assertEquals(0f, g.glow, 1e-6f)
            assertEquals(clear.gain * 0.9f, g.gain, 1e-5f)
        }
    }

    @Test
    fun moonDownGivesLessMoonlight() {
        assertEquals(0.4f, landGrade(-12.0, SkyWeather.CLEAR, moonUp = false).moonlight, 1e-6f)
    }

    @Test
    fun everyCodeSurvivesTheTripToLinearAndBack() {
        for (c in 0..255) assertEquals("code $c", c, Light.code(Light.toLinear[c]))
        assertEquals(0, Light.code(-1f))
        assertEquals(255, Light.code(2f))
    }

    // --- Composing ------------------------------------------------------------------------

    private val neutral = LandGrade(1f, 1f, 1f, 1f, 1f, 0f, 0f)
    private fun layers(sky: Int, water: Int, high: Int, ripple: Int) = (ripple shl 24) or (sky shl 16) or (water shl 8) or high
    private val skyBlue = 0xFF4A78C0.toInt()
    private val rock = 0xFF707478.toInt()

    @Test
    fun skyPassesThrough() {
        assertEquals(skyBlue, composePixel(skyBlue, skyBlue, rock, layers(255, 0, 0, 200), neutral))
    }

    @Test
    fun landWithANeutralGradeIsUnchanged() {
        assertEquals(rock, composePixel(skyBlue, skyBlue, rock, layers(0, 0, 0, 200), neutral))
    }

    @Test
    fun waterShowsTheMirroredSky() {
        // Full water, a ripple of 1 (stored as 1 ÷ 1.5^0.8): the mirrored sky at 78%.
        val one = (255f / Math.pow(1.5, 0.8).toFloat()).toInt()
        val out = composePixel(skyBlue, 0xFFFFFFFF.toInt(), rock, layers(0, 255, 0, one), neutral)
        val expected = Light.code(0.78f * one / 255f * Math.pow(1.5, 0.8).toFloat())
        assertEquals(expected.toFloat(), ((out shr 16) and 0xFF).toFloat(), 1f)
    }

    @Test
    fun theGlowWarmsTheHighRock() {
        val warm = composePixel(skyBlue, skyBlue, rock, layers(0, 0, 255, 200), neutral.copy(glow = 0.5f))
        assertTrue(((warm shr 16) and 0xFF) > ((rock shr 16) and 0xFF))
        assertTrue((warm and 0xFF) < (rock and 0xFF))
    }

    @Test
    fun mirrorRowIsAboveAndOtherwiseNone() {
        assertEquals(741, mirrorRow(752, 747.24f, 1080)) // 2 × 747.24 − 752.5 = 741.98.
        assertEquals(414, mirrorRow(1079, 747.24f, 1080))
        assertNull(mirrorRow(700, 747.24f, 1080)) // Above the eye level, the "mirror" would be below.
    }

    @Test
    fun bottomUpReadsUntouchedSky() {
        // A 2 × 8 picture: sky rows of different blues, land with water from row 5 down, eye level at row 4.
        val w = 2
        val h = 8
        val sky = IntArray(w * h) { 0xFF000000.toInt() or ((it / w) * 20 shl 16) or 0xC0 }
        val land = IntArray(w * h) { rock }
        val lay = IntArray(w * h) {
            val y =
                it / w
            if (y < 3) {
                layers(255, 0, 0, 200)
            } else if (y >= 5) {
                layers(0, 255, 0, 200)
            } else {
                layers(0, 0, 0, 200)
            }
        }
        val expected = IntArray(w * h) {
            val y = it / w
            val m = mirrorRow(y, 4f, h)
            composePixel(sky[it], if (m != null) sky[m * w + it % w] else sky[it], land[it], lay[it], neutral)
        }
        val out = sky.copyOf()
        assertTrue(composeLand(rows(out, w), rows(land, w), rows(lay, w), w, h, 4f, neutral, firstRow = 0))
        assertTrue(out.contentEquals(expected))
    }

    @Test
    fun stopsWhenAskedAndLeavesRowsAboveTheLandAlone() {
        val w = 2
        val h = 64
        val sky = IntArray(w * h) { skyBlue }
        val land = IntArray(w * h) { rock }
        val lay = IntArray(w * h) { layers(0, 0, 0, 200) }
        assertFalse(composeLand(rows(sky.copyOf(), w), rows(land, w), rows(lay, w), w, h, 10f, neutral, firstRow = 0) { false })
        val out = sky.copyOf()
        composeLand(rows(out, w), rows(land, w), rows(lay, w), w, h, 10f, neutral, firstRow = 40)
        assertEquals(skyBlue, out[39 * w]) // Above the first row: untouched.
        assertEquals(rock, out[40 * w])
    }

    // --- The dock's words over the real photo ------------------------------------------------

    /**
     * A selected square tile's words ("PS5", "Explore") stay readable over the photo's lake, across 2026
     * (every 15 minutes on the 21st of each month) and all four weathers: the real files composed as on
     * the TV, under the shades, the bar and the tile. Skipped when the files aren't made yet (a fresh
     * checkout before scripts/fetch-home-photo.sh), as the app then shows the drawn ridges.
     */
    @Test
    fun dockTileWordsReadableOverThePhoto() {
        val assets = File("src/main/assets")
        val photo = parseHomePhoto(File(assets, "home-photo.tsv").readText())!!
        val landFile = File(assets, photo.land)
        val layersFile = File(assets, photo.layers)
        assumeTrue("the photo files aren't made (scripts/fetch-home-photo.sh)", landFile.exists() && layersFile.exists())
        val land = ImageIO.read(landFile)
        val layers = ImageIO.read(layersFile)
        val white = 0xFFFFFFFF.toInt()
        var worst = Double.MAX_VALUE to ""
        val zone = ZoneId.of("America/Edmonton")
        for (month in 1..12) {
            for (minute in 0 until 24 * 60 step 15) {
                for (code in listOf(0, 3, 61, 73)) {
                    val time = ZonedDateTime.of(LocalDate.of(2026, month, 21).atTime(minute / 60, minute % 60), zone)
                    val scene = homeSkyScene(time, code, arc = DockSkyLayout.ARC)
                    val grade = landGrade(scene.sunElevationDeg, scene.weather, moonUp = scene.moon != null && scene.bodyAlpha > 0f)
                    var x = 120f
                    while (x <= 840f) {
                        var y = 480f
                        while (y <= 520f) {
                            val px = (x * 2).toInt().coerceAtMost(land.width - 1)
                            val py = (y * 2).toInt().coerceAtMost(land.height - 1)
                            val sky = dockSkyAt(scene, x, y)
                            val m = mirrorRow(py, photo.eyeRow, land.height)
                            val mirrored = if (m != null) dockSkyAt(scene, x, (m + 0.5f) / 2f) else sky
                            val c = composePixel(sky, mirrored, land.getRGB(px, py), layers.getRGB(px, py), grade)
                            val ratio = contrast(white, 0.9f, dockTileOver(dockShadesOver(c, x, y)))
                            if (ratio < worst.first) worst = ratio to "$time code $code at ($x, $y)"
                            y += 10f
                        }
                        x += 10f
                    }
                }
            }
        }
        assertTrue("tile words: ${worst.first} at ${worst.second}", worst.first >= 4.5)
    }
}

/** [PixelRows] over an array of [width]-pixel rows, as the TV's bitmaps are read. */
private fun rows(pixels: IntArray, width: Int) = object : PixelRows {
    override fun read(y: Int, into: IntArray) = System.arraycopy(pixels, y * width, into, 0, width)

    override fun write(y: Int, from: IntArray) = System.arraycopy(from, 0, pixels, y * width, width)
}
