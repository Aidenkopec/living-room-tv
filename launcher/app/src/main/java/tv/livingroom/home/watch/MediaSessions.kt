package tv.livingroom.home.watch

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import tv.livingroom.home.NowPlayingListener

/** What a session last said: its show [fields], length, playback and picture. */
data class Snapshot(
    val packageName: String,
    val fields: Map<String, String>,
    val durationMs: Long?,
    val play: PlayState,
    val art: Bitmap?,
) {
    /** As it stands at [nowElapsedMs], position moved on if it was playing. */
    fun at(nowElapsedMs: Long) = NowPlaying(
        packageName,
        fields,
        durationMs,
        positionAt(play, nowElapsedMs, durationMs),
        play.playing,
        wallTimeOf(play.updatedElapsedMs, nowElapsedMs, System.currentTimeMillis()),
    )
}

/**
 * What this session says now, or null if it has never named a show. Some apps
 * (YouTube) send a blank show for a moment; [previous]'s show is kept through that.
 */
fun MediaController.snapshot(previous: Snapshot? = null): Snapshot? = snapshotOf(packageName, playbackState, metadata, previous)

/** [MediaController.snapshot] from a session's [state] and [metadata] already in hand. */
fun snapshotOf(packageName: String, state: PlaybackState?, metadata: MediaMetadata?, previous: Snapshot?): Snapshot? {
    val play = playStateOf(state)
    val fields = metadata?.let { m ->
        m.keySet().filter { it !in PICTURE_KEYS }.associateWith { key ->
            if (key in NUMBER_KEYS) m.getLong(key).toString() else m.getText(key)?.toString().orEmpty()
        }
    }
    if (fields == null || NAME_KEYS.none { !fields[it].isNullOrBlank() }) {
        return previous?.copy(play = play)
    }
    val duration = metadata.getLong(MediaMetadata.METADATA_KEY_DURATION).takeIf { it > 0 }
    // The picture often arrives a moment after the show's name.
    val art = PICTURE_KEYS.firstNotNullOfOrNull { metadata.getBitmap(it) } ?: previous?.art?.takeIf { previous.fields == fields }
    return Snapshot(packageName, fields, duration, play, art)
}

internal fun playStateOf(state: PlaybackState?) = PlayState(
    playing = state?.state == PlaybackState.STATE_PLAYING,
    positionMs = state?.position ?: 0,
    updatedElapsedMs = state?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
    speed = state?.playbackSpeed ?: 0f,
)

/** Every session playing right now (empty without notification access). */
fun Context.liveSessions(): List<MediaController> = try {
    getSystemService(MediaSessionManager::class.java).getActiveSessions(listenerComponent(this))
} catch (ignored: SecurityException) {
    emptyList()
}

internal fun listenerComponent(context: Context) = ComponentName(context, NowPlayingListener::class.java)

private val PICTURE_KEYS = listOf(
    MediaMetadata.METADATA_KEY_ART,
    MediaMetadata.METADATA_KEY_ALBUM_ART,
    MediaMetadata.METADATA_KEY_DISPLAY_ICON,
)

private val NAME_KEYS = listOf(
    MediaMetadata.METADATA_KEY_TITLE,
    MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
    MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
)

private val NUMBER_KEYS = setOf(
    MediaMetadata.METADATA_KEY_DURATION,
    MediaMetadata.METADATA_KEY_YEAR,
    MediaMetadata.METADATA_KEY_TRACK_NUMBER,
    MediaMetadata.METADATA_KEY_NUM_TRACKS,
    MediaMetadata.METADATA_KEY_DISC_NUMBER,
    MediaMetadata.METADATA_KEY_BT_FOLDER_TYPE,
)
