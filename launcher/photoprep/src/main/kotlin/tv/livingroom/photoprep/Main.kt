package tv.livingroom.photoprep

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.system.exitProcess

/**
 * Makes the home screen photo's two files from the 16:9 cut the fetch script made:
 *
 *   photoprep <copy.bmp> <out folder>
 *
 * A cut bigger than the TV's 1920 × 1080 is shrunk to it with Lanczos-3 ([shrink]). Writes land.jpg
 * (the photo, sky area filled, lightly sharpened: [LAND_SHARPENING]), layers.png (R sky, G water shows
 * sky, B high rock, A ripple), and photo.txt (the eye level and where the land starts, for the app's
 * list). Stops with a reason if the framing is off (land where the sun travels, or the summit
 * somewhere else).
 */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("usage: photoprep <copy.bmp> <out folder>")
        exitProcess(2)
    }
    val image = ImageIO.read(File(args[0])) ?: fail("can't read ${args[0]} as a picture")
    if (!fitsScreen(
            image.width,
            image.height,
        )
    ) {
        fail("the copy is ${image.width} × ${image.height}, not 16:9 and at least $SCREEN_W × $SCREEN_H")
    }
    val photo = readPhoto(image)
    val result = cut(photo, RUNDLE)
    val problems = framingProblems(result, RUNDLE)
    if (problems.isNotEmpty()) fail(problems.joinToString("; "))

    val out = File(args[1]).apply { mkdirs() }
    // The sky part is replaced by the app's sky, so only the land is sharpened (and the skyline can't halo).
    val land = sharpen(result.land, LAND_SHARPENING, weight = FloatArray(result.sky.size) { 1f - result.sky[it] })
    writeJpeg(land, File(out, "land.jpg"), 0.95f)
    writeLayers(result, File(out, "layers.png"))
    val landTop = result.edge.min().toInt()
    File(out, "photo.txt").writeText("eye_row\t${RUNDLE.eyeRow}\nland_top\t$landTop\n")
    println(
        "Made land.jpg and layers.png; the land starts at row $landTop, the summit at column ${result.edge.withIndex().minBy {
            it.value
        }.index}.",
    )
}

private const val SCREEN_W = 1920
private const val SCREEN_H = 1080

/** At least the screen's size and 16:9 to within a row (the fetch script rounds the cut's height). */
fun fitsScreen(w: Int, h: Int): Boolean = w >= SCREEN_W && h >= SCREEN_H && abs(w * 9 - h * 16) <= 16

/** The cut at the screen's size: as it is when it already is, otherwise shrunk. */
private fun readPhoto(image: BufferedImage): Channels {
    val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
    if (image.width == SCREEN_W && image.height == SCREEN_H) return Channels.fromRgb(SCREEN_W, SCREEN_H, pixels)
    return shrink(image.width, image.height, pixels, SCREEN_W, SCREEN_H)
}

private fun fail(why: String): Nothing {
    System.err.println("STOPPED: $why")
    exitProcess(1)
}

/** The layers as a plain RGBA PNG with no colour-space chunks, so Android reads the numbers as they are. */
private fun writeLayers(r: CutResult, file: File) {
    val w = r.land.w
    val h = r.land.h
    val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, w, h, IntArray(w * h) { layerPixel(r.sky[it], r.water[it], r.high[it], r.ripple[it]) }, 0, w)
    ImageIO.write(image, "png", file)
    val colourChunks = pngChunkNames(file.readBytes()).filter { it in COLOUR_CHUNKS }
    if (colourChunks.isNotEmpty()) fail("layers.png came out with colour chunks $colourChunks")
}
