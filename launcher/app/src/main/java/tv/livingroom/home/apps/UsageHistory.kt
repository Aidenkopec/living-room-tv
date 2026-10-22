package tv.livingroom.home.apps

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import java.util.concurrent.TimeUnit
import tv.livingroom.home.TAG

/**
 * When each app was last used (package → epoch ms), from Android's usage
 * history. Empty if usage access is off (it's granted over ADB) or anything fails.
 */
@Suppress("DEPRECATION") // Deprecated only on later Androids; the TV is Android 12.
fun loadLastOpened(context: Context): Map<String, Long> {
    val appOps = context.getSystemService(AppOpsManager::class.java)
    val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
    if (mode != AppOpsManager.MODE_ALLOWED) {
        Log.i(TAG, "No usage access: the top area won't say when apps were last opened")
        return emptyMap()
    }
    return try {
        val now = System.currentTimeMillis()
        context.getSystemService(UsageStatsManager::class.java)
            .queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - TimeUnit.DAYS.toMillis(HISTORY_DAYS), now)
            .groupBy { it.packageName }
            .mapValues { (_, stats) -> stats.maxOf { it.lastTimeUsed } }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read usage history", e)
        emptyMap()
    }
}

private const val HISTORY_DAYS = 60L
