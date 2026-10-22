package tv.livingroom.home.apps

/** One tile on the home screen's app row. */
sealed interface RowItem {
    data class App(val app: TvApp) : RowItem
    data class Input(val input: HdmiInput) : RowItem
    data object Explore : RowItem
}

/** What a tile is known by in the saved order and hidden list: its package, its input, or "explore". */
val RowItem.key: String
    get() = when (this) {
        is RowItem.App -> app.packageName
        is RowItem.Input -> input.id
        RowItem.Explore -> "explore"
    }

/** A tile's name, as the menu and the settings list show it. */
val RowItem.label: String
    get() = when (this) {
        is RowItem.App -> app.label
        is RowItem.Input -> input.label
        RowItem.Explore -> "Explore"
    }

/** The user's row: the tiles in [order] first, and those [hidden] left off. Defaults until the user changes it. */
data class RowSettings(val order: List<String> = ROW_ORDER, val hidden: Set<String> = HIDDEN_BY_DEFAULT)

/** The row's default order, most used over the past year first. */
val ROW_ORDER = listOf(
    "com.netflix.ninja",
    "com.google.android.youtube.tv",
    "com.cbs.ca", // Paramount+
    "ca.bellmedia.cravetv",
    "com.amazon.amazonvideo.livingroom",
    "com.apple.atve.androidtv.appletv",
    "com.disney.disneyplus",
)

/** Kept off the row; reached through Explore (Google's full Apps list). */
val HIDDEN_BY_DEFAULT = setOf(
    "com.android.tv.settings",
    "com.android.vending", // Play Store
    "com.vt.tv.protector", // Device Assistant
    "com.vt.tv.tvcenter", // Live TV
    "com.vt.tv.mediacenter",
    "com.vt.tv.mobiledeviceconnectiontips", // Screen Casting
    "com.google.android.videos", // Google TV app
)

/**
 * The app row: the tiles in [settings]' order, then any input not in it (by
 * port), then any app not in it (in the order given, which is A–Z from
 * [buildAppList]), leaving out the hidden ones. Explore is always last and never hidden.
 */
fun appRow(apps: List<TvApp>, inputs: List<HdmiInput> = emptyList(), settings: RowSettings = RowSettings()): List<RowItem> {
    val tiles = apps.map { RowItem.App(it) } +
        inputs.sortedWith(compareBy(nullsLast()) { input: HdmiInput -> input.portLabel }).map { RowItem.Input(it) }
    val byKey = tiles.associateBy { it.key }
    val ordered = settings.order.mapNotNull { byKey[it] }
    val rest = tiles.filter { it !in ordered }.sortedBy { if (it is RowItem.Input) 0 else 1 } // Stable: keeps A–Z and port order.
    return (ordered + rest).distinct().filter { it.key !in settings.hidden } + RowItem.Explore
}

/** The row's order with [key]'s tile moved [by] places (left if negative), never before the first or past Explore. */
fun moveKey(row: List<RowItem>, key: String, by: Int): List<String> {
    val keys = row.filter { it != RowItem.Explore }.map { it.key }.toMutableList()
    val from = keys.indexOf(key)
    if (from < 0) return keys
    keys.add((from + by).coerceIn(0, keys.size - 1), keys.removeAt(from))
    return keys
}

/**
 * The order to save after a move: the row as now [shown], with every tile in
 * the [saved] order that isn't showing (hidden, or a device unplugged) put back
 * right after the tile it followed, so it returns to the same place.
 */
fun mergeOrder(saved: List<String>, shown: List<String>): List<String> {
    val merged = shown.toMutableList()
    saved.forEachIndexed { index, key ->
        if (key in merged) return@forEachIndexed
        val before = saved.subList(0, index).lastOrNull { it in merged }
        merged.add(if (before == null) 0 else merged.indexOf(before) + 1, key)
    }
    return merged
}
