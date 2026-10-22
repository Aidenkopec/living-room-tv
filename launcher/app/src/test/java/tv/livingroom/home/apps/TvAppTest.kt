package tv.livingroom.home.apps

import org.junit.Assert.assertEquals
import org.junit.Test

class TvAppTest {
    private fun app(label: String, pkg: String = "com.example.${label.lowercase().replace(" ", "")}") =
        TvApp(packageName = pkg, activityName = "$pkg.Main", label = label)

    @Test
    fun sortsByLabelIgnoringCase() {
        val list = buildAppList(listOf(app("netflix"), app("Apple TV"), app("YouTube")), OWN)
        assertEquals(listOf("Apple TV", "netflix", "YouTube"), list.map { it.label })
    }

    @Test
    fun leavesOutOwnApp() {
        val list = buildAppList(listOf(app("Living Room", OWN), app("Netflix")), OWN)
        assertEquals(listOf("Netflix"), list.map { it.label })
    }

    @Test
    fun onePerPackage() {
        val first = TvApp("com.netflix.ninja", "com.netflix.ninja.MainActivity", "Netflix")
        val second = TvApp("com.netflix.ninja", "com.netflix.ninja.Other", "Netflix 2")
        assertEquals(listOf(first), buildAppList(listOf(first, second), OWN))
    }

    @Test
    fun blankLabelFallsBackToPackageName() {
        val list = buildAppList(listOf(app(" ", "com.example.app")), OWN)
        assertEquals(listOf("com.example.app"), list.map { it.label })
    }

    private companion object {
        const val OWN = "tv.livingroom.home"
    }
}
