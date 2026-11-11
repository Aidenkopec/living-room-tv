package tv.livingroom.home.christmas

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Not a pass/fail test: paints the villagers and the two dogs in their poses into `people.png` with
 * Java2D, to look at on the computer. It runs only when the environment variable VILLAGE_PREVIEW
 * names a folder to write to, as [VillagePreviewTest] does:
 * `VILLAGE_PREVIEW=/tmp/village ./gradlew :app:testDebugUnitTest --tests '*PeoplePreviewTest'`.
 * The figures are in their plain daylight colours, twice life size.
 */
class PeoplePreviewTest {
    /**
     * One row of figures: each [cell] wide and the row [height] high (in the picture's pixels), their
     * feet 30 px above the row's bottom and their origins [across] of the way along their cells.
     */
    private class Row(val cell: Int, val height: Int, val figures: List<List<Mark>>, val across: Float = 0.5f)

    private val strides = listOf(-1f, -0.87f, -0.5f, 0f, 0.5f, 0.87f, 1f)

    @Test
    fun paintThePeopleAndTheDogs() {
        val folder = System.getenv("VILLAGE_PREVIEW")
        assumeTrue("VILLAGE_PREVIEW isn't set", !folder.isNullOrEmpty())
        val rows = peopleRows() + dogRows(Dog.SMALL) + dogRows(Dog.BIG)
        val image = BufferedImage(rows.maxOf { it.cell * it.figures.size }, rows.sumOf { it.height }, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x7F93B5)
        g.fillRect(0, 0, image.width, image.height)
        var bottom = 0
        for (row in rows) {
            bottom += row.height
            row.figures.forEachIndexed { i, marks ->
                val before = g.transform
                g.translate((row.cell * (i + row.across)).toDouble(), bottom - FOOT)
                g.scale(SCALE, SCALE)
                Java2dInk.draw(g, marks)
                g.transform = before
            }
        }
        g.dispose()
        File(folder!!).mkdirs()
        ImageIO.write(image, "png", File(folder, "people.png"))
    }

    /** A grown-up's stride, everything a hand can hold or do, and a running kid's stride. */
    private fun peopleRows(): List<Row> {
        val adult = Villager()
        val kid = Villager(coat = 0xE0A83A, hat = 0x2F6F8F, kid = true, run = true)
        val carrying = Carry.entries.map { carry ->
            val arm = when (carry) {
                Carry.WAVE -> 2.5f
                Carry.THROW -> 1f
                else -> 0f
            }
            personMarks(adult.copy(bag = 0x4F8F5A), PersonPose(carry = carry, arm = arm, sing = if (carry == Carry.BOOK) 1f else null))
        }
        return listOf(
            Row(230, 320, strides.map { personMarks(adult, PersonPose(swing = it, flutter = it)) }),
            Row(230, 320, carrying),
            Row(230, 240, strides.map { personMarks(kid, PersonPose(swing = it, flutter = -it)) }),
        )
    }

    /** [dog] wagging, walking, bowing and turning its head; the big one with its ball and the small one crouched to finish. */
    private fun dogRows(dog: Dog): List<Row> {
        val poses = listOf(-1f, 0f, 1f).map { DogPose(tail = it) } +
            List(6) { DogPose(stride = it / 6f) } +
            listOf(DogPose(bow = 0.5f), DogPose(bow = 1f), DogPose(head = 0.45f), DogPose(head = -0.4f)) +
            if (dog == Dog.BIG) DogPose(ball = true) else DogPose(up = 0.7f)
        val height = if (dog == Dog.BIG) 540 else 440
        return poses.map { dogShadowMarks(dog) + dogMarks(dog, it) }.chunked(5).map { Row(680, height, it, across = 0.6f) }
    }

    private companion object {
        const val SCALE = 2.0
        const val FOOT = 30.0
    }
}
