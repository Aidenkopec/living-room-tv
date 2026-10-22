package tv.livingroom.home.apps

/** A TV app that can be opened: its package, launch activity and name. */
data class TvApp(val packageName: String, val activityName: String, val label: String)

/** The apps to show: one per package, without our own, sorted A–Z. */
fun buildAppList(found: List<TvApp>, ownPackage: String): List<TvApp> = found
    .filter { it.packageName != ownPackage }
    .distinctBy { it.packageName }
    .map { if (it.label.isBlank()) it.copy(label = it.packageName) else it }
    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
