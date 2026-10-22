package tv.livingroom.home

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.util.Log
import tv.livingroom.home.watch.Snapshot
import tv.livingroom.home.watch.WatchStore
import tv.livingroom.home.watch.listenerComponent
import tv.livingroom.home.watch.playStateOf
import tv.livingroom.home.watch.snapshot
import tv.livingroom.home.watch.snapshotOf

/**
 * Sees what other apps say is playing (their media sessions) and saves each
 * app's show as a Continue watching card. Android only shows sessions to an
 * app with notification access, granted over ADB. The notifications themselves
 * are never looked at: there's no onNotificationPosted.
 */
class NowPlayingListener : NotificationListenerService() {
    private val handler = Handler(Looper.getMainLooper())
    private val followed = mutableMapOf<MediaSession.Token, Followed>()
    private val onSessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { follow(it.orEmpty()) }

    /** One session: what it last said ([last]), and what was last saved from it. */
    private inner class Followed(val controller: MediaController) : MediaController.Callback() {
        var last: Snapshot? = null
        var savedAs: Pair<Map<String, String>, Boolean>? = null

        // Built from what each call hands over: a position tick (Crave sends one each second)
        // doesn't fetch the show and its picture from the app again.
        override fun onMetadataChanged(metadata: MediaMetadata?) =
            changed(snapshotOf(controller.packageName, controller.playbackState, metadata, last))
        override fun onPlaybackStateChanged(state: PlaybackState?) = changed(last?.copy(play = playStateOf(state)))
        override fun onSessionDestroyed() = gone()

        /** Saved when the show or playing/paused changes, not on every position tick. */
        fun changed(now: Snapshot? = controller.snapshot(last)) {
            last = now ?: return
            val key = now.fields to now.play.playing
            if (key != savedAs) {
                savedAs = key
                WatchStore.save(this@NowPlayingListener, now.at(SystemClock.elapsedRealtime()), now.art)
            }
        }

        /** YouTube and Apple TV vanish at Home without saying "paused", so the last state saved here counts. */
        fun gone() {
            last?.let { WatchStore.save(this@NowPlayingListener, it.at(SystemClock.elapsedRealtime()), it.art) }
            last = null
        }
    }

    override fun onListenerConnected() {
        Log.i(TAG, "Now-playing listener connected")
        try {
            sessionManager().addOnActiveSessionsChangedListener(onSessionsChanged, listenerComponent(this), handler)
            follow(sessionManager().getActiveSessions(listenerComponent(this)))
        } catch (e: SecurityException) {
            Log.w(TAG, "Can't see media sessions", e)
        }
    }

    override fun onListenerDisconnected() {
        Log.i(TAG, "Now-playing listener disconnected")
        sessionManager().removeOnActiveSessionsChangedListener(onSessionsChanged)
        // Each show is saved at its latest spot, as when its session ends.
        followed.values.forEach {
            it.controller.unregisterCallback(it)
            it.gone()
        }
        followed.clear()
    }

    private fun sessionManager() = getSystemService(MediaSessionManager::class.java)

    /** Follows each session in [controllers]; saves and drops the ones that are gone. */
    private fun follow(controllers: List<MediaController>) {
        val tokens = controllers.map { it.sessionToken }.toSet()
        followed.keys.filter { it !in tokens }.forEach { token ->
            followed.remove(token)?.let {
                it.controller.unregisterCallback(it)
                it.gone()
            }
        }
        for (controller in controllers) {
            if (controller.sessionToken in followed) continue
            val session = Followed(controller)
            controller.registerCallback(session, handler)
            followed[controller.sessionToken] = session
            session.changed()
        }
    }
}
