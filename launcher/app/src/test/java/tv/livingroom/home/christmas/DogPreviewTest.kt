package tv.livingroom.home.christmas

import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Not a pass/fail test: paints the two dogs large in their poses into `dogs.png`, and their heads close up into `dog-heads.png`,
 * to look at on the computer. It runs only when VILLAGE_PREVIEW names a folder:
 * `VILLAGE_PREVIEW=/tmp/village ./gradlew :app:testDebugUnitTest --tests '*DogPreviewTest'`.
 */
class DogPreviewTest {
    private val poses = listOf(
        "standing" to DogPose(),
        "tail left" to DogPose(tail = -1f),
        "tail right" to DogPose(tail = 1f),
        "walk 0" to DogPose(stride = 0f),
        "walk .25" to DogPose(stride = 0.25f),
        "walk .5" to DogPose(stride = 0.5f),
        "walk .75" to DogPose(stride = 0.75f),
        "bow .5" to DogPose(bow = 0.5f),
        "bow 1" to DogPose(bow = 1f, tail = 1f),
        "sniff" to DogPose(head = -0.5f),
        "look up" to DogPose(head = 0.45f),
        "crouch" to DogPose(up = 0.3f),
        "gallop 0" to DogPose(stride = 0f, gallop = true, pant = true),
        "gallop .25" to DogPose(stride = 0.25f, gallop = true, pant = true),
        "gallop .5" to DogPose(stride = 0.5f, gallop = true, pant = true),
        "gallop .75" to DogPose(stride = 0.75f, gallop = true, pant = true),
        "ball" to DogPose(ball = true),
        "pant" to DogPose(pant = true),
    )

    @Test
    fun paintTheDogs() {
        val folder = System.getenv("VILLAGE_PREVIEW")
        assumeTrue("VILLAGE_PREVIEW isn't set", !folder.isNullOrEmpty())
        File(folder!!).mkdirs()
        val cellW = 640
        val cellH = 600
        val columns = 6
        val rows = (poses.size + columns - 1) / columns
        for (dog in Dog.entries) {
            val image = BufferedImage(cellW * columns, cellH * rows, BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color(0x8A97B0)
            g.fillRect(0, 0, image.width, image.height)
            poses.forEachIndexed { i, (name, pose) ->
                val before = g.transform
                g.translate((cellW * (i % columns) + cellW * 0.55), (cellH * (i / columns + 1) - 40).toDouble())
                g.scale(2.0, 2.0)
                Java2dInk.draw(g, dogShadowMarks(dog) + dogMarks(dog, pose))
                g.transform = before
                g.color = Color.WHITE
                g.drawString(name, cellW * (i % columns) + 12, cellH * (i / columns) + 20)
            }
            g.dispose()
            ImageIO.write(image, "png", File(folder, "dogs-${dog.name.lowercase()}.png"))
        }
        val heads = BufferedImage(1500, 520, BufferedImage.TYPE_INT_ARGB)
        val g = heads.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x8A97B0)
        g.fillRect(0, 0, heads.width, heads.height)
        val headPoses = listOf(DogPose(), DogPose(open = false), DogPose(pant = true), DogPose(ear = 1f, open = false))
        Dog.entries.forEachIndexed { row, dog ->
            headPoses.forEachIndexed { i, pose ->
                val before = g.transform
                g.translate(370.0 * i + 420, 250.0 * row + 210)
                g.scale(2.4, 2.4)
                Java2dInk.draw(g, headMarks(dog, pose))
                g.transform = before
            }
        }
        g.dispose()
        ImageIO.write(heads, "png", File(folder, "dog-heads.png"))
    }
}
