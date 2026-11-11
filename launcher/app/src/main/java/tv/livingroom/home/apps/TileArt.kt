package tv.livingroom.home.apps

import androidx.compose.ui.graphics.ImageBitmap

/** What a tile shows: the app's wide TV banner, or its icon on a coloured tile. */
sealed interface TileArt {
    data class Banner(val image: ImageBitmap) : TileArt
    data class Icon(val image: ImageBitmap, val background: Int) : TileArt
}

/**
 * The banner if there is one, else the icon on a tile coloured by
 * [iconBackground] (worked out only when needed). Null if the app has
 * neither; the tile then shows the app's name.
 */
fun tileArt(banner: ImageBitmap?, icon: ImageBitmap?, iconBackground: () -> Int): TileArt? = when {
    banner != null -> TileArt.Banner(banner)
    icon != null -> TileArt.Icon(icon, iconBackground())
    else -> null
}

/**
 * A tile's picture and the glow colours picked from it. [version] is the app's
 * last-update time when it was loaded, or [ART_FAILED]. [dockTile] is the same
 * picture made at the dock's own tile size ([DOCK_BANNER_W_PX]).
 */
data class AppArt(val tile: TileArt?, val glow: GlowColours, val version: Long, val dockTile: TileArt? = tile)

/**
 * The dock's selected tile in pixels (104 x 58.5 dp at 2 px per dp). Its banner is made at exactly
 * this size, so the selected tile shows it pixel for pixel and the smaller tiles, scaled down from it
 * while drawing, never shrink it 2 times or more (Android's filtered drawing reads only the 2 x 2
 * pixels nearest each one it draws, so a bigger shrink skips pixels and looks rough).
 */
const val DOCK_BANNER_W_PX = 208
const val DOCK_BANNER_H_PX = 117

/**
 * How many times a picture [from] px across is halved (each halving averages 2 x 2 pixels, so
 * nothing is skipped) before one last filtered step to [to] px, which then shrinks it less than 2 times.
 */
fun halvings(from: Int, to: Int): Int {
    if (to <= 0) return 0
    var size = from
    var n = 0
    while (size >= 2 * to) {
        size /= 2
        n++
    }
    return n
}

const val ART_FAILED = -1L

/** Load (again) if not loaded yet, if the app was updated since (it may have a new banner), or if it failed. */
fun needsArtReload(cached: AppArt?, lastUpdateTime: Long): Boolean =
    cached == null || cached.version == ART_FAILED || cached.version != lastUpdateTime
