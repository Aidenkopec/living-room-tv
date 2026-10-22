package tv.livingroom.home.sky

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.util.Log
import tv.livingroom.home.TAG

/** The photo's two files, decoded to read pixels from (never drawn). Recycle them as soon as the picture is painted. */
class HomePhotoFiles(val land: Bitmap, val layers: Bitmap) {
    fun recycle() {
        land.recycle()
        layers.recycle()
    }
}

/** The home screen photo packed into the app (made by scripts/fetch-home-photo.sh; see HomePhoto.kt). */
object HomePhotoLoader {
    @Volatile private var checked = false

    @Volatile private var photo: HomePhoto? = null

    /** The photo, if its list and both files are in the app; null otherwise (logged once: Mountains and dock then draws its ridges). */
    fun photo(assets: AssetManager): HomePhoto? {
        if (checked) return photo
        val found = try {
            val listed = assets.open(LIST).bufferedReader().use { parseHomePhoto(it.readText()) }
            val files = assets.list(FOLDER)?.map { "$FOLDER/$it" }?.toSet().orEmpty()
            listed?.takeIf { it.land in files && it.layers in files }
        } catch (ignored: Exception) {
            null
        }
        if (found == null) Log.i(TAG, "Home photo: not in the app, drawing the ridges (run scripts/fetch-home-photo.sh)")
        photo = found
        checked = true
        return found
    }

    /**
     * Both files decoded off the main thread: sRGB, full size, and the layers with their numbers as stored
     * (not premultiplied). Null if they can't be read right now (e.g. no memory), so the last picture stays up.
     */
    fun decode(assets: AssetManager, photo: HomePhoto): HomePhotoFiles? {
        var land: Bitmap? = null
        return try {
            land = read(assets, photo.land, premultiplied = true)
            val layers = read(assets, photo.layers, premultiplied = false)
            val fine = land != null && layers != null &&
                land.width == layers.width && land.height == layers.height &&
                land.config == Bitmap.Config.ARGB_8888 && layers.config == Bitmap.Config.ARGB_8888 &&
                !layers.isPremultiplied && layers.colorSpace == SRGB
            if (!fine) {
                Log.w(TAG, "Home photo: the files didn't decode as expected")
                land?.recycle()
                layers?.recycle()
                null
            } else {
                HomePhotoFiles(land, layers)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Home photo: couldn't read the files ($e)")
            land?.recycle()
            null
        } catch (ignored: OutOfMemoryError) {
            Log.w(TAG, "Home photo: no memory to read the files right now")
            land?.recycle()
            null
        }
    }

    private fun read(assets: AssetManager, file: String, premultiplied: Boolean): Bitmap? = assets.open(file).use { stream ->
        BitmapFactory.decodeStream(
            stream,
            null,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPreferredColorSpace = SRGB
                inScaled = false
                inPremultiplied = premultiplied
            },
        )
    }

    private val SRGB = ColorSpace.get(ColorSpace.Named.SRGB)
    private const val FOLDER = "home"
    private const val LIST = "home-photo.tsv"
}
