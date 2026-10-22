package tv.livingroom.home.watch

/**
 * An app's playback as it last reported it: where it was ([positionMs]) at
 * [updatedElapsedMs] (the TV's uptime clock), and how fast it was moving.
 */
data class PlayState(val playing: Boolean, val positionMs: Long, val updatedElapsedMs: Long, val speed: Float)

/**
 * Where playback is at [nowElapsedMs]: moved on if playing (and the app said
 * when it reported; 0 means it didn't), never below 0 or past [durationMs].
 */
fun positionAt(play: PlayState, nowElapsedMs: Long, durationMs: Long?): Long {
    val moved = if (play.playing && play.updatedElapsedMs > 0) ((nowElapsedMs - play.updatedElapsedMs) * play.speed).toLong() else 0L
    val position = (play.positionMs + moved).coerceAtLeast(0)
    return if (durationMs != null && durationMs > 0) position.coerceAtMost(durationMs) else position
}

/**
 * When something reported at [elapsedMs] on the TV's uptime clock happened on
 * the wall clock, given both clocks' [nowElapsedMs] and [nowWallMs]. 0 (not
 * reported) stays 0.
 */
fun wallTimeOf(elapsedMs: Long, nowElapsedMs: Long, nowWallMs: Long): Long =
    if (elapsedMs > 0) nowWallMs - (nowElapsedMs - elapsedMs) else 0L

/**
 * What one app's media session says at one moment. [fields] holds every text
 * metadata key (`android.media.metadata.TITLE`, `…MEDIA_ID`, …); [positionMs]
 * is already worked out for that moment. [playUpdatedAtMs] is when the app last
 * reported playing or paused, on the wall clock (0 if it didn't say).
 */
data class NowPlaying(
    val packageName: String,
    val fields: Map<String, String>,
    val durationMs: Long?,
    val positionMs: Long,
    val playing: Boolean,
    val playUpdatedAtMs: Long,
)
