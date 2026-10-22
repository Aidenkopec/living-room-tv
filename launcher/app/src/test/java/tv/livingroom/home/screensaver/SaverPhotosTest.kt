package tv.livingroom.home.screensaver

import java.time.LocalDate
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SaverPhotosTest {
    private val header = "id\tfile\tmood\tplace\tregion\tcredit\tlicence\tpage"
    private val row1 = "1\t01.jpg\tday\tMoraine Lake\tBanff National Park, Canada\t" +
        "Photo: David Zhang, CC BY-SA 2.0, via Wikimedia Commons\tCC BY-SA 2.0\t" +
        "https://commons.wikimedia.org/wiki/File:Moraine_Lake_at_evening2.jpg"

    private fun photo(id: Int, mood: PhotoMood) =
        SaverPhoto(id, "%02d.jpg".format(id), mood, "Place $id", "Region", "Photo: someone", "CC0", "https://example.org/$id")

    private val dusk = listOf(photo(1, PhotoMood.DUSK), photo(2, PhotoMood.DUSK), photo(3, PhotoMood.DUSK))
    private val mixed = dusk + listOf(photo(4, PhotoMood.DAY), photo(5, PhotoMood.NIGHT))

    @Test
    fun parsesListRow() {
        val expected = SaverPhoto(
            1,
            "01.jpg",
            PhotoMood.DAY,
            "Moraine Lake",
            "Banff National Park, Canada",
            "Photo: David Zhang, CC BY-SA 2.0, via Wikimedia Commons",
            "CC BY-SA 2.0",
            "https://commons.wikimedia.org/wiki/File:Moraine_Lake_at_evening2.jpg",
        )
        assertEquals(listOf(expected), parsePhotoList("$header\n$row1\n"))
    }

    @Test
    fun headerSkipped() {
        assertEquals(emptyList<SaverPhoto>(), parsePhotoList(header))
    }

    @Test
    fun malformedRowsSkipped() {
        val text = listOf(
            header,
            "2\t02.jpg\tday\tToo few",
            "3\t03.jpg\tmidday\tPlace\tRegion\tCredit\tCC0\thttps://example.org",
            "x\t04.jpg\tday\tPlace\tRegion\tCredit\tCC0\thttps://example.org",
            row1,
            "",
        ).joinToString("\n")
        assertEquals(listOf(1), parsePhotoList(text).map { it.id })
    }

    @Test
    fun picksOnlyMatchingMood() {
        repeat(20) { seed ->
            assertEquals(PhotoMood.DAY, pickPhoto(mixed, PhotoMood.DAY, emptyList(), Random(seed))?.mood)
        }
    }

    @Test
    fun noRepeatUntilAllShown() {
        repeat(20) { seed ->
            val random = Random(seed)
            val shown = mutableListOf<Int>()
            repeat(3) { shown += pickPhoto(mixed, PhotoMood.DUSK, shown, random)!!.id }
            assertEquals(setOf(1, 2, 3), shown.toSet())
        }
    }

    @Test
    fun afterAllShownOldestFirst() {
        assertEquals(2, pickPhoto(dusk, PhotoMood.DUSK, listOf(2, 1, 3), Random(0))?.id)
        // Shown again later counts from its latest showing.
        assertEquals(1, pickPhoto(dusk, PhotoMood.DUSK, listOf(2, 1, 3, 2), Random(0))?.id)
    }

    @Test
    fun noPhotosOfMoodGivesNull() {
        assertNull(pickPhoto(dusk, PhotoMood.NIGHT, emptyList(), Random(0)))
        assertNull(pickPhoto(emptyList(), PhotoMood.DAY, emptyList(), Random(0)))
    }

    @Test
    fun newDayStartsFresh() {
        val today = LocalDate.parse("2026-10-20")
        assertEquals(emptyList<Int>(), shownToday(LocalDate.parse("2026-10-19"), listOf(1, 2), today))
        assertEquals(emptyList<Int>(), shownToday(null, listOf(1, 2), today))
    }

    @Test
    fun sameDayKeepsList() {
        val today = LocalDate.parse("2026-10-19")
        assertEquals(listOf(1, 2), shownToday(today, listOf(1, 2), today))
    }

    // --- when there's no photo to show, Living sky instead ---

    private val duskScene = SaverScene(SaverStyle.PLACES, PhotoMood.DUSK)
    private val skyScene = SaverScene(SaverStyle.LIVING_SKY, PhotoMood.DUSK)

    @Test
    fun photosOfTheMoodShow() = assertEquals(duskScene, sceneToShow(duskScene, mixed, broken = setOf(1)))

    @Test
    fun noPhotosGivesSky() = assertEquals(skyScene, sceneToShow(duskScene, emptyList(), broken = emptySet()))

    // Every dusk photo failed to load: the sky, not a black screen with a clock.
    @Test
    fun allBrokenGivesSky() = assertEquals(skyScene, sceneToShow(duskScene, mixed, broken = setOf(1, 2, 3)))

    @Test
    fun lateNightWithoutPhotosGivesSky() {
        val late = SaverScene(SaverStyle.LATE_NIGHT, PhotoMood.NIGHT)
        assertEquals(SaverScene(SaverStyle.LIVING_SKY, PhotoMood.NIGHT), sceneToShow(late, mixed, broken = setOf(5)))
    }
}
