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
 * last-update time when it was loaded, or [ART_FAILED].
 */
data class AppArt(val tile: TileArt?, val glow: GlowColours, val version: Long)

const val ART_FAILED = -1L

/** Load (again) if not loaded yet, if the app was updated since (it may have a new banner), or if it failed. */
fun needsArtReload(cached: AppArt?, lastUpdateTime: Long): Boolean =
    cached == null || cached.version == ART_FAILED || cached.version != lastUpdateTime
