package tv.livingroom.home.screensaver

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tv.livingroom.home.TAG

/** The "Beautiful places" photos packed into the app (see scripts/fetch-screensaver-photos.sh). */
object PhotoLoader {
    @Volatile private var photos: List<SaverPhoto>? = null

    /** The photo list, keeping only photos whose file is really in the app. Empty if there's no list. */
    fun list(context: Context): List<SaverPhoto> = photos ?: run {
        val assets = context.assets
        val files = assets.list(FOLDER)?.toSet().orEmpty()
        val listed = try {
            assets.open(LIST).bufferedReader().use { parsePhotoList(it.readText()) }
        } catch (e: Exception) {
            Log.w(TAG, "Screensaver: no photo list ($e)")
            emptyList()
        }
        listed.filter { it.file in files }.also {
            Log.i(TAG, "Screensaver: ${it.size} of ${listed.size} listed photos are in the app")
            photos = it
        }
    }

    /** One photo's picture, decoded off the main thread; null if it can't be read. */
    suspend fun load(context: Context, photo: SaverPhoto): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            context.assets.open("$FOLDER/${photo.file}").use { stream ->
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply {
                        inPreferredConfig =
                            android.graphics.Bitmap.Config.ARGB_8888
                    },
                )
            }?.asImageBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "Screensaver: couldn't read photo ${photo.id} ($e)")
            null
        } catch (ignored: OutOfMemoryError) {
            // Not enough memory for it right now: skip this photo rather than crash the home screen too.
            Log.w(TAG, "Screensaver: no memory for photo ${photo.id}")
            null
        }
    }

    private const val FOLDER = "screensaver"
    private const val LIST = "screensaver-photos.tsv"
}
