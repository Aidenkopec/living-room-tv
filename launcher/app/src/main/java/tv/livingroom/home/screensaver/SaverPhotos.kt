package tv.livingroom.home.screensaver

import java.time.LocalDate
import kotlin.random.Random

/** One "Beautiful places" photo, as listed in the app's `screensaver-photos.tsv`. */
data class SaverPhoto(
    val id: Int,
    val file: String,
    val mood: PhotoMood,
    val place: String,
    val region: String,
    val credit: String,
    val licence: String,
    val page: String,
)

/**
 * The photo list (tab-separated, a header row first, made by
 * `scripts/fetch-screensaver-photos.sh`). Rows that don't read cleanly are
 * skipped; it never throws.
 */
fun parsePhotoList(tsv: String): List<SaverPhoto> = tsv.lineSequence().mapNotNull { line ->
    val cells = line.split('\t')
    if (cells.size != 8) return@mapNotNull null
    val id = cells[0].toIntOrNull() ?: return@mapNotNull null
    val mood = when (cells[2]) {
        "day" -> PhotoMood.DAY
        "dusk" -> PhotoMood.DUSK
        "night" -> PhotoMood.NIGHT
        else -> return@mapNotNull null
    }
    SaverPhoto(id, cells[1], mood, cells[3], cells[4], cells[5], cells[6], cells[7])
}.toList()

/**
 * The next photo of [mood]: one not in [shownToday] (oldest first), at
 * random; once all have shown, the one shown longest ago. Null when there's
 * no photo of that mood.
 */
fun pickPhoto(photos: List<SaverPhoto>, mood: PhotoMood, shownToday: List<Int>, random: Random): SaverPhoto? {
    val candidates = photos.filter { it.mood == mood }
    if (candidates.isEmpty()) return null
    val unseen = candidates.filter { it.id !in shownToday }
    if (unseen.isNotEmpty()) return unseen.random(random)
    return candidates.minBy { shownToday.lastIndexOf(it.id) }
}

/** The photos already shown today: [saved] if it was saved [today], else none. */
fun shownToday(savedDate: LocalDate?, saved: List<Int>, today: LocalDate): List<Int> = if (savedDate == today) saved else emptyList()

/**
 * The scene to show for [wanted]: Living sky instead when no photo of its mood can be
 * shown, because there are none in the app or all of them failed to load ([broken] ids).
 */
fun sceneToShow(wanted: SaverScene, photos: List<SaverPhoto>, broken: Set<Int>): SaverScene =
    if (wanted.style != SaverStyle.LIVING_SKY && photos.none { it.mood == wanted.mood && it.id !in broken }) {
        wanted.copy(style = SaverStyle.LIVING_SKY)
    } else {
        wanted
    }
