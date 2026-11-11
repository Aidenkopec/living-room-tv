package tv.livingroom.home.christmas

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.livingroom.home.HomePlace

class VillageTest {
    private val day = LocalDate.of(2026, 12, 24)
    private fun scene(h: Int, m: Int, code: Int? = null) = villageScene(ZonedDateTime.of(day, LocalTime.of(h, m), HomePlace.ZONE), code)
    private val fourTimes = listOf(scene(9, 0), scene(15, 10), scene(18, 30), scene(0, 5))

    /** Left, top, right, bottom of what a mark covers (a glow's whole reach; a line's middle, without its thickness). */
    private fun box(mark: Mark): FloatArray = when (mark) {
        is FillMark -> mark.outline.bounds()
        is LineMark -> mark.outline.bounds()
        is GlowMark -> floatArrayOf(mark.x - mark.radius, mark.y - mark.radius, mark.x + mark.radius, mark.y + mark.radius)
        is WordMark -> marksBounds(listOf(mark)).let { floatArrayOf(it.x, it.y, it.x + it.w, it.y + it.h) }
    }

    @Test
    fun everyMarkIsARealPlaceNearThePicture() {
        for (scene in fourTimes + scene(12, 0, code = 73)) {
            val marks = villageMarks(scene)
            assertTrue(marks.size > 500)
            for (mark in marks) {
                val b = box(mark)
                assertTrue(b.all { it.isFinite() })
                // The far range and the viaduct run a little off both sides; nothing else leaves the picture.
                assertTrue(b.toList().toString(), b[0] >= -80f && b[2] <= VILLAGE_W + 80 && b[1] >= -50f && b[3] <= VILLAGE_H + 50)
                assertTrue(b[2] >= b[0] && b[3] >= b[1])
            }
        }
    }

    @Test
    fun theLandKeepsClearOfTheClock() {
        for (scene in fourTimes) {
            for (mark in villageBack(scene.look) + villageFront(scene)) {
                val (left, top) = box(mark)
                assertTrue("a mark reaches ($left, $top)", top >= 250f || left >= 1120f)
            }
        }
    }

    @Test
    fun whatMovesIsNotInTheStillPicture() {
        // No flakes hang in the air: the picture is the same whatever falls.
        assertEquals(villageMarks(scene(12, 0)).size, villageMarks(scene(12, 0, code = 73)).size)
        // Every fairy light is painted at its dimmest, so the moving layer only ever adds light.
        val evening = villageFront(scene(18, 30))
        val bulbGlows = evening.filterIsInstance<GlowMark>().filter { it.rgb in BULBS.toList() }
        assertEquals(58, bulbGlows.size)
        assertTrue(bulbGlows.all { it.alpha <= 0.55f * STRING_DIMMEST + 1e-4f })
    }

    @Test
    fun everyTwinkleSitsOnALightOfThePicture() {
        val glows = villageFront(scene(18, 30)).filterIsInstance<GlowMark>()
        assertEquals(58 + 42 + 1, VILLAGE_TWINKLES.size)
        for (twinkle in VILLAGE_TWINKLES) {
            assertTrue(
                "A glow at (${twinkle.x}, ${twinkle.y})",
                glows.any {
                    abs(it.x - twinkle.x) < 0.01f && abs(it.y - twinkle.y) < 0.01f
                },
            )
            assertTrue(twinkle.light in 0..HALO_LIGHT)
        }
        assertEquals(43, VILLAGE_TWINKLES.count { it.onTree })
    }

    @Test
    fun fiveChimneysSmokeAboveTheirRoofs() {
        assertEquals(5, VILLAGE_CHIMNEYS.size)
        for ((x, y) in VILLAGE_CHIMNEYS) assertTrue("($x, $y)", x in 500f..1800f && y in 380f..620f)
    }

    @Test
    fun thirtyWindowsGlowInTheEvening() {
        val glows = villageFront(scene(18, 30)).filterIsInstance<GlowMark>().filter { it.rgb == WINDOW_GLOW }
        // 9 houses of 3 windows and the church's house; the same warm light also comes from the church clock and the two stalls.
        assertEquals(30 + 3, glows.size)
    }

    @Test
    fun windowsAreBrighterInTheEveningThanInTheAfternoon() {
        fun warmth(c: Int) = ((c shr 16) and 0xFF) - (c and 0xFF) // Yellow glass is redder than it is blue.
        assertTrue(warmth(windowGlass(EVENING, 1f)) > warmth(windowGlass(AFTERNOON, AFTERNOON.lights * 1.2f)))
        assertTrue(windowLight(1f) > windowLight(0.3f))
        val evening = villageFront(scene(18, 30)).filterIsInstance<GlowMark>().filter {
            it.rgb == WINDOW_GLOW
        }.sumOf { it.alpha.toDouble() }
        val afternoon = villageFront(scene(15, 10)).filterIsInstance<GlowMark>().filter {
            it.rgb == WINDOW_GLOW
        }.sumOf { it.alpha.toDouble() }
        assertTrue(evening > 2 * afternoon)
    }

    @Test
    fun northernLightsOnlyOnAClearQuietNight() {
        assertTrue(villageAurora(scene(18, 30)).isEmpty())
        val lights = villageAurora(scene(0, 5))
        assertTrue(lights.size > 100)
        assertTrue(lights.all { it is FillMark && it.additive })
        // Cloud, rain and snow hide them, as they hide the stars.
        for (code in listOf(3, 61, 73)) assertTrue(villageAurora(scene(0, 5, code)).isEmpty())
    }

    @Test
    fun northernLightsArePaintedWithTheSkyNotTheLand() {
        val midnight = scene(0, 5)
        assertTrue(villageMarks(midnight).none { it is FillMark && it.additive })
        for (mark in villageAurora(midnight)) {
            val b = box(mark)
            assertTrue(b[1] >= 0f && b[3] <= 620f) // In the sky, above the village's roofs.
        }
    }

    @Test
    fun brazierBurnsLowInTheQuietNight() {
        fun fire(scene: VillageScene) = villageFront(scene).filterIsInstance<GlowMark>().single { it.rgb == 0xFF8A3A }.alpha
        assertTrue(fire(scene(18, 30)) > 3 * fire(scene(0, 5)))
        assertEquals(fire(scene(18, 30)), fire(scene(15, 10)), 0.001f)
    }

    @Test
    fun churchClockHandsPointAtTheTime() {
        val quarter = (PI / 2).toFloat()
        val (hourAtThree, minuteAtThree) = clockHands(15, 0)
        assertEquals(0f, hourAtThree, 0.001f) // 3 o'clock: the hour hand points right...
        assertEquals(-quarter, minuteAtThree, 0.001f) // ...and the minute hand straight up.
        val (hour, minute) = clockHands(0, 30)
        assertEquals(quarter, minute, 0.001f) // Half past: straight down.
        assertEquals(-quarter + 2 * PI.toFloat() / 24, hour, 0.001f) // Halfway from 12 to 1.
        assertEquals(clockHands(3, 10), clockHands(15, 10))
    }

    @Test
    fun groundFollowsItsLine() {
        val line = floatArrayOf(100f, 200f, 150f)
        assertEquals(100f, groundY(line, 0f), 0.01f)
        assertEquals(200f, groundY(line, VILLAGE_W / 2), 0.01f)
        assertEquals(150f, groundY(line, VILLAGE_W), 0.01f)
        assertEquals(150f, groundY(line, VILLAGE_W + 500), 0.01f)
        assertEquals(150f, groundY(line, VILLAGE_W / 4), 0.01f) // Halfway up the first rise.
    }

    @Test
    fun theSameSceneAlwaysGivesTheSameVillage() {
        val a = villageMarks(scene(18, 30))
        val b = villageMarks(scene(18, 30))
        assertEquals(a.size, b.size)
        for (i in a.indices) assertEquals(box(a[i]).toList(), box(b[i]).toList())
    }

    @Test
    fun adventCountsDecemberFirstToTheTwentyFourthAndIsWholeOtherwise() {
        assertEquals(1, adventDay(LocalDate.of(2026, 12, 1)))
        assertEquals(12, adventDay(LocalDate.of(2026, 12, 12)))
        assertEquals(24, adventDay(LocalDate.of(2026, 12, 24)))
        for (other in listOf(
            LocalDate.of(2026, 11, 30),
            LocalDate.of(2026, 12, 25),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2027, 1, 2),
        )) {
            assertEquals(other.toString(), ADVENT_DAYS, adventDay(other))
        }
        assertEquals(1, villageScene(ZonedDateTime.of(LocalDate.of(2026, 12, 1), LocalTime.of(18, 0), HomePlace.ZONE), null).advent)
    }

    @Test
    fun oneMoreWindowIsLitEachDayInTheDesignsOrder() {
        // The mockup's order (its rng(42) shuffle): on December 1 only the first house's second window.
        assertEquals(listOf(1), litWindows(1).withIndex().filter { it.value }.map { it.index })
        assertEquals(14, litWindows(12).count { it })
        assertTrue(litWindows(24).all { it })
        for (day in 2..ADVENT_DAYS) {
            val before = litWindows(day - 1)
            val now = litWindows(day)
            assertTrue("Day $day keeps every window lit the day before", before.indices.all { !before[it] || now[it] })
            assertTrue(now.count { it } >= before.count { it })
        }
        assertEquals(0.5875f, roofSnow(1), 1e-4f)
        assertEquals(1.45f, roofSnow(24), 1e-4f)
    }

    @Test
    fun aDarkerDayOfAdventHasFewerLitWindowsInThePicture() {
        fun litGlass(advent: Int) = villageMarks(scene(18, 30).copy(advent = advent)).count {
            it is FillMark &&
                (it.tone as? Flat)?.argb == windowGlass(scene(18, 30).look, (scene(18, 30).look.lights * 1.2f).coerceIn(0f, 1f))
        }
        assertTrue(litGlass(1) < litGlass(12))
        assertTrue(litGlass(12) < litGlass(24))
    }
}
