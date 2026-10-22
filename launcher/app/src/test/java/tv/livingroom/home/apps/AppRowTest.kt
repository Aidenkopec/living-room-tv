package tv.livingroom.home.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class AppRowTest {
    private fun app(pkg: String, label: String) = TvApp(pkg, "$pkg.Main", label)

    private val netflix = app("com.netflix.ninja", "Netflix")
    private val youtube = app("com.google.android.youtube.tv", "YouTube")
    private val paramount = app("com.cbs.ca", "Paramount+")
    private val crave = app("ca.bellmedia.cravetv", "Crave")
    private val prime = app("com.amazon.amazonvideo.livingroom", "Prime Video")
    private val appleTv = app("com.apple.atve.androidtv.appletv", "Apple TV")
    private val disney = app("com.disney.disneyplus", "Disney+")

    /** The row apps as the TV lists them: A–Z (buildAppList's output). */
    private val rowAppsAz = listOf(appleTv, crave, disney, netflix, paramount, prime, youtube)

    private fun labels(row: List<RowItem>) = row.map {
        when (it) {
            is RowItem.App -> it.app.label
            is RowItem.Input -> it.input.label
            RowItem.Explore -> "Explore"
        }
    }

    @Test
    fun fixedOrderThenExplore() {
        assertEquals(
            listOf("Netflix", "YouTube", "Paramount+", "Crave", "Prime Video", "Apple TV", "Disney+", "Explore"),
            labels(appRow(rowAppsAz)),
        )
    }

    @Test
    fun hiddenAppsLeftOut() {
        val hidden = listOf(
            app("com.android.tv.settings", "Settings"),
            app("com.android.vending", "Google Play Store"),
            app("com.vt.tv.tvcenter", "Live TV"),
            app("com.vt.tv.mediacenter", "Media Center"),
            app("com.vt.tv.mobiledeviceconnectiontips", "Screen Casting"),
            app("com.vt.tv.protector", "Device Assistant"),
            app("com.google.android.videos", "Google Play Movies"),
        )
        assertEquals(listOf("Netflix", "Explore"), labels(appRow(hidden + netflix)))
    }

    @Test
    fun newAppGoesBeforeExplore() {
        val zed = app("com.example.zed", "Zed")
        val abc = app("com.example.abc", "Abc")
        // buildAppList has already sorted A–Z.
        val row = appRow(listOf(abc) + rowAppsAz + zed)
        assertEquals(
            listOf("Netflix", "YouTube", "Paramount+", "Crave", "Prime Video", "Apple TV", "Disney+", "Abc", "Zed", "Explore"),
            labels(row),
        )
    }

    @Test
    fun missingOrderedAppIsSkipped() {
        assertEquals(
            listOf("Netflix", "YouTube", "Crave", "Prime Video", "Apple TV", "Disney+", "Explore"),
            labels(appRow(rowAppsAz - paramount)),
        )
    }

    @Test
    fun exploreAlwaysLast() {
        assertEquals(listOf(RowItem.Explore), appRow(emptyList()))
    }

    private val ps5 = HdmiInput("hdmi/HDMI100004", "PlayStation 5", "HDMI 1", on = false)
    private val receiver = HdmiInput("hdmi/HDMI100008", "AV Receiver", "HDMI 3", on = null)
    private val abc = app("com.example.abc", "Abc")
    private val rowApps = listOf(netflix, youtube, paramount, crave, prime, appleTv, disney)

    @Test
    fun inputGoesAfterOrderedApps() {
        assertEquals(
            listOf("Netflix", "YouTube", "Paramount+", "Crave", "Prime Video", "Apple TV", "Disney+", "PlayStation 5", "Abc", "Explore"),
            labels(appRow(listOf(abc) + rowAppsAz, listOf(ps5))),
        )
    }

    @Test
    fun missingInputIsSkipped() {
        val saved = RowSettings(order = listOf(ps5.id, netflix.packageName, youtube.packageName))
        assertEquals(listOf("Netflix", "YouTube", "Explore"), labels(appRow(listOf(netflix, youtube), emptyList(), saved)))
    }

    @Test
    fun twoInputsByPort() {
        assertEquals(
            listOf("Netflix", "PlayStation 5", "AV Receiver", "Explore"),
            labels(appRow(listOf(netflix), listOf(receiver, ps5))),
        )
    }

    @Test
    fun savedOrderComesFirst() {
        val saved = RowSettings(order = listOf(crave.packageName, netflix.packageName))
        assertEquals(
            listOf("Crave", "Netflix", "Apple TV", "Disney+", "Paramount+", "Prime Video", "YouTube", "Explore"),
            labels(appRow(rowAppsAz, emptyList(), saved)),
        )
    }

    @Test
    fun savedOrderKeepsInputSpot() {
        val saved = RowSettings(order = listOf(netflix.packageName, ps5.id, youtube.packageName))
        assertEquals(listOf("Netflix", "PlayStation 5", "YouTube", "Explore"), labels(appRow(listOf(netflix, youtube), listOf(ps5), saved)))
        // Unplugged and back: the same spot.
        assertEquals(listOf("Netflix", "YouTube", "Explore"), labels(appRow(listOf(netflix, youtube), emptyList(), saved)))
    }

    @Test
    fun newAppAfterSavedOrder() {
        val saved = RowSettings(order = rowApps.map { it.packageName } + ps5.id)
        assertEquals(
            listOf("Netflix", "YouTube", "Paramount+", "Crave", "Prime Video", "Apple TV", "Disney+", "PlayStation 5", "Abc", "Explore"),
            labels(appRow(listOf(abc) + rowAppsAz, listOf(ps5), saved)),
        )
    }

    @Test
    fun hiddenInputLeftOut() {
        val saved = RowSettings(hidden = HIDDEN_BY_DEFAULT + ps5.id)
        assertEquals(listOf("Netflix", "Explore"), labels(appRow(listOf(netflix), listOf(ps5), saved)))
    }

    @Test
    fun shownBuiltInApp() {
        val settings = app("com.android.tv.settings", "Settings")
        val saved = RowSettings(hidden = HIDDEN_BY_DEFAULT - settings.packageName)
        assertEquals(listOf("Netflix", "Settings", "Explore"), labels(appRow(listOf(netflix, settings), emptyList(), saved)))
    }

    @Test
    fun exploreNeverHidden() {
        assertEquals(listOf("Netflix", "Explore"), labels(appRow(listOf(netflix), emptyList(), RowSettings(hidden = setOf("explore")))))
    }

    @Test
    fun hideEverythingLeavesExplore() {
        val all = RowSettings(hidden = (rowApps.map { it.packageName } + ps5.id).toSet())
        assertEquals(listOf(RowItem.Explore), appRow(rowAppsAz, listOf(ps5), all))
    }

    @Test
    fun keysOfEachKind() {
        assertEquals(
            listOf(netflix.packageName, ps5.id, "explore"),
            appRow(listOf(netflix), listOf(ps5)).map { it.key },
        )
    }

    private val shortRow = appRow(listOf(netflix, youtube, crave), listOf(ps5))
    private val nyc = listOf(netflix.packageName, youtube.packageName, crave.packageName, ps5.id)

    @Test
    fun moveRight() {
        assertEquals(listOf(youtube.packageName, netflix.packageName, crave.packageName, ps5.id), moveKey(shortRow, netflix.packageName, 1))
    }

    @Test
    fun moveClampsAtStart() {
        assertEquals(listOf(ps5.id, netflix.packageName, youtube.packageName, crave.packageName), moveKey(shortRow, ps5.id, -9))
        assertEquals(nyc, moveKey(shortRow, netflix.packageName, -1))
    }

    @Test
    fun moveNeverPassesExplore() {
        assertEquals(nyc, moveKey(shortRow, ps5.id, 1))
        assertEquals(listOf(youtube.packageName, crave.packageName, ps5.id, netflix.packageName), moveKey(shortRow, netflix.packageName, 5))
    }

    @Test
    fun moveUnknownKeyChangesNothing() {
        assertEquals(nyc, moveKey(shortRow, "com.example.gone", 1))
        assertEquals(nyc, moveKey(shortRow, "explore", -1))
    }

    @Test
    fun movingKeepsHiddenTilesInPlace() {
        // Disney+ hidden after Apple TV; the PS5 moved to the front.
        val saved = rowApps.map { it.packageName } + ps5.id
        val shown = listOf(ps5.id) + (rowApps - disney).map { it.packageName }
        assertEquals(listOf(ps5.id) + rowApps.map { it.packageName }, mergeOrder(saved, shown))
    }

    @Test
    fun movingKeepsUnpluggedInputInPlace() {
        val saved = listOf(netflix.packageName, ps5.id, youtube.packageName, crave.packageName)
        val shown = listOf(youtube.packageName, netflix.packageName, crave.packageName) // PS5 unplugged, YouTube moved first.
        assertEquals(listOf(youtube.packageName, netflix.packageName, ps5.id, crave.packageName), mergeOrder(saved, shown))
    }

    @Test
    fun mergeWithNothingMissing() {
        assertEquals(nyc, mergeOrder(listOf(crave.packageName), nyc))
        assertEquals(listOf("first.gone") + nyc, mergeOrder(listOf("first.gone", netflix.packageName), nyc))
    }
}
