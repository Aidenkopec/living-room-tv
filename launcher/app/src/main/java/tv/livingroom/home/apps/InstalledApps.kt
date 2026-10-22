package tv.livingroom.home.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.scale
import tv.livingroom.home.TAG

private val GOOGLE_HOME = ComponentName(
    "com.google.android.apps.tv.launcherx",
    "com.google.android.apps.tv.launcherx.home.HomeActivity",
)

/** Every app in the TV's app list (the same list Google's home shows). */
@Suppress("DEPRECATION") // The ResolveInfoFlags form is Android 13+; the TV is Android 12.
fun loadTvApps(pm: PackageManager): List<TvApp> = pm.queryIntentActivities(
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
    0,
).map {
    TvApp(
        packageName = it.activityInfo.packageName,
        activityName = it.activityInfo.name,
        label = it.loadLabel(pm).toString(),
    )
}

/** When [packageName] was last installed or updated, or [ART_FAILED] if that can't be read. */
@Suppress("DEPRECATION") // The PackageInfoFlags form is Android 13+; the TV is Android 12.
fun lastUpdateTime(pm: PackageManager, packageName: String): Long = try {
    pm.getPackageInfo(packageName, 0).lastUpdateTime
} catch (ignored: Exception) {
    ART_FAILED
}

/**
 * [app]'s banner (or icon) and its glow colours, marked with [version] (its
 * [lastUpdateTime]). Never throws; worst case a named tile and a neutral glow, marked [ART_FAILED].
 */
@Suppress("DEPRECATION") // The ComponentInfoFlags form is Android 13+; the TV is Android 12.
fun loadAppArt(pm: PackageManager, app: TvApp, version: Long): AppArt = try {
    val info = pm.getActivityInfo(ComponentName(app.packageName, app.activityName), 0)
    // Both fall back to the app's own banner and icon.
    val banner = info.loadBanner(pm)?.toBitmap(BANNER_W_PX, BANNER_H_PX)
    val icon = if (banner == null) info.loadIcon(pm)?.toBitmap(ICON_PX, ICON_PX) else null
    val glow = (banner ?: icon)?.let { glowColoursOf(it) } ?: NEUTRAL_GLOW
    AppArt(tileArt(banner?.asImageBitmap(), icon?.asImageBitmap()) { scaleValue(glow.primary, 0.5f) }, glow, version)
} catch (e: Exception) {
    Log.w(TAG, "Could not load the picture for ${app.packageName}", e)
    AppArt(null, NEUTRAL_GLOW, ART_FAILED) // Tried again next time.
}

/** Colours from a small copy of [bitmap]; a 32 x 18 sample is plenty. */
private fun glowColoursOf(bitmap: Bitmap): GlowColours = try {
    val small = bitmap.scale(SAMPLE_W, SAMPLE_H)
    pickGlowColours(IntArray(SAMPLE_W * SAMPLE_H).also { small.getPixels(it, 0, SAMPLE_W, 0, 0, SAMPLE_W, SAMPLE_H) })
} catch (e: Exception) {
    Log.w(TAG, "Could not pick glow colours", e)
    NEUTRAL_GLOW
}

// A little over the lifted Continue watching card's size (176 x 99 dp at 1.06x, 2 px per dp: 373 x 210),
// so a card showing the app's banner stays sharp; the smaller tiles draw it scaled down.
private const val BANNER_W_PX = 396
private const val BANNER_H_PX = 222
private const val ICON_PX = 64
private const val SAMPLE_W = 32
private const val SAMPLE_H = 18

fun Context.openApp(app: TvApp) = start(
    Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        .setComponent(ComponentName(app.packageName, app.activityName)),
)

fun Context.openGoogleHome() = start(
    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(GOOGLE_HOME),
)

/** Starts [intent]; false (and logged) if Android wouldn't, whatever the reason. */
fun Context.start(intent: Intent): Boolean = try {
    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: Exception) {
    Log.w(TAG, "Could not open ${intent.component ?: intent.data}", e)
    false
}

/** [packageName]'s name as its tile shows it (see [loadTvApps]), else the app's own name, else [packageName]. */
@Suppress("DEPRECATION") // The flags forms are Android 13+; the TV is Android 12.
fun tvAppLabel(pm: PackageManager, packageName: String): String = try {
    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(packageName), 0)
        .firstOrNull()?.loadLabel(pm)?.toString()
        ?: pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (ignored: Exception) {
    packageName
}
