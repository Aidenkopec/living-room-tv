package tv.livingroom.home.dock

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.livingroom.home.apps.HdmiInput
import tv.livingroom.home.apps.RowItem
import tv.livingroom.home.apps.TvApp
import tv.livingroom.home.settings.DEFAULT_LAYOUT
import tv.livingroom.home.settings.HomeLayout
import tv.livingroom.home.settings.homeLayoutFrom

class DockTest {
    private fun app(pkg: String) = RowItem.App(TvApp(pkg, pkg, pkg))
    private val ps5 = RowItem.Input(HdmiInput("hdmi1", "PlayStation 5", "HDMI 1", on = true))
    private val today = listOf(
        app("netflix"),
        app("youtube"),
        app("paramount"),
        app("crave"),
        app("prime"),
        app("appletv"),
        app("disney"),
        ps5,
        RowItem.Explore,
    )

    // --- Tile sizes ------------------------------------------------------------------

    private val squares = today.map { it !is RowItem.App }

    @Test
    fun selectedTileIsBiggestAndNeighboursGrowALittle() {
        assertEquals(58.5f, dockHeightAt(0f), 0f)
        assertEquals(45f, dockHeightAt(1f), 0f)
        assertEquals(45f, dockHeightAt(-1f), 0f)
        assertEquals(38f, dockHeightAt(2f), 0f)
        assertEquals(34f, dockHeightAt(3f), 0f)
        assertEquals(34f, dockHeightAt(8f), 0f)
        assertEquals(DOCK_TALLEST, dockHeightAt(0f), 0f)
    }

    @Test
    fun heightsGlideSmoothlyBetweenSteps() {
        assertEquals((58.5f + 45f) / 2, dockHeightAt(0.5f), 0.01f)
        assertEquals((38f + 34f) / 2, dockHeightAt(2.5f), 0.01f)
        var last = dockHeightAt(0f)
        var d = 0.05f
        while (d <= 4f) {
            val h = dockHeightAt(d)
            assertTrue("$d", h <= last)
            last = h
            d += 0.05f
        }
    }

    @Test
    fun appTilesAreWideAndPs5AndExploreSquare() {
        assertEquals(80f, dockTileWidth(45f, square = false), 0.01f)
        assertEquals(45f, dockTileWidth(45f, square = true), 0f)
        val slots = dockSlots(squares, at = 0f, separatorBefore = 7)
        assertEquals(104f, slots[0].width, 0.01f) // Netflix, selected.
        assertEquals(80f, slots[1].width, 0.01f)
        assertEquals(34f, slots[7].width, 0f) // The PS5, far away: small and square.
        assertEquals(34f, slots[8].width, 0f)
    }

    @Test
    fun theEndsOfTheRowHaveNeighboursOnOneSideOnly() {
        val slots = dockSlots(squares, at = 8f, separatorBefore = 7) // Explore.
        assertEquals(58.5f, slots[8].height, 0f)
        assertEquals(45f, slots[7].height, 0f)
        assertEquals(dockTileWidth(38f, square = false), slots[6].width, 0.01f)
    }

    @Test
    fun tilesSitSideBySideWithGapsAndTheLine() {
        val slots = dockSlots(squares, at = 3f, separatorBefore = 7)
        assertEquals(0f, slots[0].x, 0f)
        for (i in 1 until slots.size) {
            val extra = if (i == 7) DOCK_SEPARATOR_SPACE else 0f
            assertEquals("$i", slots[i - 1].x + slots[i - 1].width + DOCK_GAP + extra, slots[i].x, 0.01f)
        }
        val line = dockSeparatorX(slots, 7)
        assertEquals(slots[6].x + slots[6].width + DOCK_GAP, line, 0.01f)
        assertEquals(slots.last().x + slots.last().width + 2 * DOCK_PADDING, dockWidth(slots), 0.01f)
    }

    // --- The thin line before the PS5 and Explore ---------------------------------------

    @Test
    fun lineGoesBeforeTheInputsAndExploreAtTheEnd() {
        assertEquals(7, dockSeparatorBefore(today))
    }

    @Test
    fun noLineWhenThePs5IsMovedInAmongTheApps() {
        assertNull(dockSeparatorBefore(listOf(app("netflix"), ps5, app("youtube"), RowItem.Explore)))
    }

    @Test
    fun noLineWithoutInputs() {
        assertNull(dockSeparatorBefore(listOf(app("netflix"), app("youtube"), RowItem.Explore)))
    }

    @Test
    fun noLineWithNoAppsBeforeIt() {
        assertNull(dockSeparatorBefore(listOf(ps5, RowItem.Explore)))
    }

    // --- Fitting on the screen ---------------------------------------------------------

    @Test
    fun todaysNineTilesFitWhereverYouAreSoTheDockIsCentred() {
        var at = 0f
        while (at <= 8f) {
            val width = dockWidth(dockSlots(squares, at, separatorBefore = 7))
            assertTrue("focus at $at: $width", width <= DOCK_MAX_WIDTH)
            assertEquals((DOCK_MAX_WIDTH - width) / 2, dockStart(width, focusCentre = 0f, view = DOCK_MAX_WIDTH), 0.01f)
            at += 0.25f
        }
    }

    @Test
    fun aDockWiderThanTheScreenKeepsTheSelectedTileInTheMiddle() {
        val many = List(15) { false } + true
        val slots = dockSlots(many, at = 8f, separatorBefore = null)
        val width = dockWidth(slots)
        assertTrue(width > DOCK_MAX_WIDTH)
        val centre = DOCK_PADDING + slots[8].x + slots[8].width / 2
        assertEquals(DOCK_MAX_WIDTH / 2, dockStart(width, centre, DOCK_MAX_WIDTH) + centre, 0.01f)
    }

    @Test
    fun aWideDockStopsAtItsEnds() {
        assertEquals(0f, dockStart(width = 1200f, focusCentre = 50f, view = 874f), 0f) // First tile: no gap on the left.
        assertEquals(874f - 1200f, dockStart(width = 1200f, focusCentre = 1150f, view = 874f), 0f) // Last tile: no gap on the right.
    }

    // --- The line under the clock ------------------------------------------------------------

    private val oct20 = LocalDate.of(2026, 10, 20)

    @Test
    fun infoLineHasDateWeatherAndSky() {
        assertEquals("Tuesday, October 20 · ☀ −2° Clear · Sunset 6:33 PM", dockInfoLine(oct20, "☀ −2° Clear", "Sunset 6:33 PM"))
    }

    @Test
    fun infoLineLeavesOutWhatIsMissing() {
        assertEquals("Tuesday, October 20 · Sunset 6:33 PM", dockInfoLine(oct20, null, "Sunset 6:33 PM"))
        assertEquals("Tuesday, October 20 · ☀ −2° Clear", dockInfoLine(oct20, "☀ −2° Clear", null))
        assertEquals("Tuesday, October 20", dockInfoLine(oct20, null, null))
    }

    // --- Which home screen ------------------------------------------------------------

    @Test
    fun theNewLookUnlessYouPickedLivingSky() {
        assertEquals(HomeLayout.MOUNTAINS, DEFAULT_LAYOUT)
        assertEquals(DEFAULT_LAYOUT, homeLayoutFrom(null))
        assertEquals(DEFAULT_LAYOUT, homeLayoutFrom("something else"))
        for (layout in HomeLayout.entries) assertEquals(layout, homeLayoutFrom(layout.key))
    }

    @Test
    fun theOtherLook() {
        assertEquals(HomeLayout.LIVING_SKY, HomeLayout.MOUNTAINS.other)
        assertEquals(HomeLayout.MOUNTAINS, HomeLayout.LIVING_SKY.other)
    }
}
