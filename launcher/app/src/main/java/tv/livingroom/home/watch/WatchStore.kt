package tv.livingroom.home.watch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import tv.livingroom.home.TAG
import tv.livingroom.home.apps.tvAppLabel
import tv.livingroom.home.settings.HomePrefs

/**
 * The Continue watching shows (up to 12, several per app, plus the markers of
 * removed ones) and their pictures, saved in Living Room's own storage on the
 * TV. Nothing leaves the TV. Every change runs on one background thread, so
 * the listener and the screen never collide.
 */
object WatchStore {
    private val worker = Executors.newSingleThreadExecutor()
    private val workerDispatcher = worker.asCoroutineDispatcher()
    private val state = MutableStateFlow<List<WatchCard>>(emptyList())
    private val pictures = MutableStateFlow(0)
    private var loaded = false
    private val labels = mutableMapOf<String, String>() // Each app's name, looked up once. Only used on the worker.

    val cards: StateFlow<List<WatchCard>> = state

    /** Goes up each time a picture is saved, so the screen loads it again. */
    val pictureVersion: StateFlow<Int> = pictures

    /** Saves what [now] says is playing, if it's a real show (see [cardFrom]), with its [art]. Returns at once. */
    fun save(context: Context, now: NowPlaying, art: Bitmap?) {
        val app = context.applicationContext
        worker.execute { saveNow(app, now, art) }
    }

    /** Saves each of [playing], suspending until done (on the store's thread, after anything already queued). */
    suspend fun saveAll(context: Context, playing: List<Pair<NowPlaying, Bitmap?>>) {
        val app = context.applicationContext
        withContext(workerDispatcher) {
            load(app)
            playing.forEach { (now, art) -> saveNow(app, now, art) }
        }
    }

    /**
     * Takes the show known by [key] (see [WatchCard.key]) off the row, with its
     * picture. It comes back only once it plays again (see [acceptSave]). Returns at once.
     */
    fun remove(context: Context, key: String) {
        val app = context.applicationContext
        worker.execute {
            load(app)
            state.value = removeShow(state.value, key, System.currentTimeMillis())
            writeCards(app, state.value)
            prunePictures(app, state.value)
            Log.i(TAG, "Removed the show $key")
        }
    }

    /** The saved picture for [card]'s show, or null. Call off the main thread. */
    fun pictureFor(context: Context, card: WatchCard): ImageBitmap? = try {
        pictureFile(context, card).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
    } catch (e: Exception) {
        Log.w(TAG, "Could not read the picture for ${card.packageName}", e)
        null
    }

    /**
     * Reads the saved shows once: the v2 file, or, before one exists, the older v1 file. The v1
     * file is never written, so a previous app version reinstalled still finds the shows it knew.
     */
    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        state.value = try {
            val old = File(context.filesDir, OLD_CARDS_FILE)
            val file = listOf(cardsFile(context), old).map { AtomicFile(it) }.firstOrNull { it.baseFile.exists() }
            val cards = file?.let { decodeCards(it.readFully().decodeToString()) }.orEmpty()
            val from = file?.baseFile?.name ?: "nowhere (none yet)"
            val oldFile = if (old.exists()) "kept, ${old.length()} bytes" else "not there"
            Log.i(TAG, "Read ${cards.size} saved shows from $from; old file $oldFile")
            dropOld(cards, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the saved shows", e)
            emptyList()
        }
    }

    private fun saveNow(context: Context, now: NowPlaying, art: Bitmap?) {
        load(context)
        val label = labels.getOrPut(now.packageName) { tvAppLabel(context.packageManager, now.packageName) }
        val nowMs = System.currentTimeMillis()
        val card = cardFrom(now, label, nowMs) ?: return
        val appRemovedAt = HomePrefs.removedAt(context, now.packageName) // App-wide marker from the one-show-per-app format.
        val showRemovedAt = state.value.firstOrNull { it.key == card.key }?.removedAtMs
        if (!acceptSave(appRemovedAt, showRemovedAt, now)) return
        if (appRemovedAt != null) HomePrefs.setRemovedAt(context, now.packageName, null) // Playing again: back to normal.
        val cards = mergeCard(dropOld(state.value, nowMs), card)
        if (cards != state.value) {
            state.value = cards
            writeCards(context, cards)
            prunePictures(context, cards)
            Log.i(TAG, "Saved ${card.packageName}: ${card.title}, ${showMeta(card)}")
        }
        val kept = cards.any { it.key == card.key && it.removedAtMs == null }
        if (kept && art != null && !pictureFile(context, card).exists()) savePicture(context, card, art)
    }

    private fun writeCards(context: Context, cards: List<WatchCard>) {
        try {
            val file = AtomicFile(cardsFile(context))
            val out = file.startWrite()
            try {
                out.write(encodeCards(cards).encodeToByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not save the shows", e)
        }
    }

    private fun savePicture(context: Context, card: WatchCard, art: Bitmap) {
        try {
            val file = pictureFile(context, card)
            file.parentFile?.mkdirs()
            // Written beside it and then renamed, so the screen (loading pictures meanwhile) never reads half a picture.
            val partial = File(file.path + ".part")
            partial.outputStream().use { art.scale(PICTURE_W, PICTURE_H).compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (partial.renameTo(file)) pictures.value++ else partial.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Could not save the picture for ${card.packageName}", e)
        }
    }

    /** Lets go of the pictures of shows no longer saved (pushed out, removed, or over 30 days old). */
    private fun prunePictures(context: Context, cards: List<WatchCard>) {
        val keep = picturesToKeep(cards)
        File(context.filesDir, "art").listFiles { f -> f.name !in keep }?.forEach { it.delete() }
    }

    private fun cardsFile(context: Context) = File(context.filesDir, "continue-watching-2.txt")

    /** Named after the show too, so a new show never borrows another one's picture. */
    private fun pictureFile(context: Context, card: WatchCard) = File(context.filesDir, "art/${pictureName(card)}")

    private const val OLD_CARDS_FILE = "continue-watching.txt" // The v1 file. Read once, never written.

    // Sized for the earlier 163 x 92 dp cards, kept so saved pictures stay valid. Slightly under the
    // current 176 x 99 dp card lifted 1.06x (373 x 210 px), which looks fine.
    private const val PICTURE_W = 366
    private const val PICTURE_H = 206
}
