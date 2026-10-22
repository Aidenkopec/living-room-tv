package tv.livingroom.home.watch

import android.content.Context
import android.content.Intent
import android.media.session.MediaController
import android.net.Uri
import android.util.Log
import tv.livingroom.home.TAG
import tv.livingroom.home.apps.TvApp
import tv.livingroom.home.apps.openApp
import tv.livingroom.home.apps.start

/** Gets back to [card]'s show as closely as its app allows: tries [resumeSteps] in order until one starts. */
fun Context.resume(card: WatchCard, app: TvApp) {
    val live = liveSessions().firstOrNull { it.packageName == card.packageName }
    val liveTitle = live?.snapshot()?.let { showTitle(it.packageName, it.fields) }
    for (step in resumeSteps(card, liveTitle, RESUME_RECIPES[card.packageName])) {
        val started = when (step) {
            ResumeStep.PlayLive -> live != null && playLive(live, app)
            is ResumeStep.OpenLink -> view(step.uri, card.packageName)
            is ResumeStep.Search -> view(step.uri, card.packageName)
            ResumeStep.OpenApp -> openApp(app)
        }
        if (started) {
            Log.i(TAG, "Resumed ${card.packageName} at level ${step.level.number} (${step.level.words})")
            return
        }
    }
}

/** Brings the app's player back up (no app here sets sessionActivity, so through its launcher) and presses play. */
private fun Context.playLive(session: MediaController, app: TvApp): Boolean = try {
    val player = session.sessionActivity
    val shown = if (player != null) true.also { player.send() } else openApp(app)
    if (shown) session.transportControls.play()
    shown
} catch (e: Exception) {
    Log.w(TAG, "Could not resume ${app.packageName}'s session", e)
    false
}

private fun Context.view(uri: String, packageName: String): Boolean =
    start(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setPackage(packageName))
