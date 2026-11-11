package tv.livingroom.home.apps

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TileArtTest {
    /** Stands in for a decoded image; only its identity matters here. */
    private class FakeImage : ImageBitmap {
        override val width = 1
        override val height = 1
        override val config = ImageBitmapConfig.Argb8888
        override val colorSpace: ColorSpace = ColorSpaces.Srgb
        override val hasAlpha = true
        override fun readPixels(buffer: IntArray, startX: Int, startY: Int, width: Int, height: Int, bufferOffset: Int, stride: Int) {}
        override fun prepareToDraw() {}
    }

    private val banner = FakeImage()
    private val icon = FakeImage()

    @Test
    fun bannerWins() {
        assertEquals(TileArt.Banner(banner), tileArt(banner, icon) { error("not needed") })
    }

    @Test
    fun noBannerGivesIconOnColouredTile() {
        assertEquals(TileArt.Icon(icon, 0xFF123456.toInt()), tileArt(null, icon) { 0xFF123456.toInt() })
    }

    @Test
    fun neitherGivesNull() {
        // The tile then shows the app's name instead.
        assertNull(tileArt(null, null) { 0 })
    }

    private val glow = GlowColours(0xFF112233.toInt(), 0xFF445566.toInt())

    @Test
    fun loadsArtNotYetLoaded() {
        assertTrue(needsArtReload(null, lastUpdateTime = 1_000L))
    }

    @Test
    fun keepsArtWhileAppUnchanged() {
        assertFalse(needsArtReload(AppArt(TileArt.Banner(banner), glow, version = 1_000L), lastUpdateTime = 1_000L))
    }

    @Test
    fun reloadsArtAfterAppUpdate() {
        // An app update can bring a new banner (and so new glow colours).
        assertTrue(needsArtReload(AppArt(TileArt.Banner(banner), glow, version = 1_000L), lastUpdateTime = 2_000L))
    }

    @Test
    fun retriesArtThatFailedToLoad() {
        assertTrue(needsArtReload(AppArt(null, NEUTRAL_GLOW, version = ART_FAILED), lastUpdateTime = 1_000L))
    }

    @Test
    fun theDockTileIsTheSameTileUnlessOneIsMade() {
        val art = AppArt(TileArt.Banner(banner), NEUTRAL_GLOW, version = 1L)
        assertEquals(art.tile, art.dockTile)
        val small = FakeImage()
        assertEquals(TileArt.Banner(small), art.copy(dockTile = TileArt.Banner(small)).dockTile)
    }

    @Test
    fun halvingsStopBeforeTheLastStepWouldShrinkTwice() {
        assertEquals(0, halvings(320, 208)) // A TV banner (320 x 180) to the dock tile: one 1.5x step.
        assertEquals(0, halvings(208, 208))
        assertEquals(1, halvings(640, 208)) // 640 -> 320, then 1.5x.
        assertEquals(1, halvings(416, 208)) // 416 -> 208 exactly.
        assertEquals(2, halvings(1280, 208))
        assertEquals(0, halvings(100, 208)) // Growing: no halvings.
        assertEquals(0, halvings(100, 0))
    }
}
