package tv.livingroom.photoprep

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.roundToInt
import kotlin.system.exitProcess

/**
 * Makes the home screen photo's two files from the 1920 × 1080 copy the fetch script made:
 *
 *   photoprep <copy.bmp> <out folder>
 *
 * Writes land.jpg (the photo, sky area filled), layers.png (R sky, G water shows sky, B high rock,
 * A ripple), and photo.txt (the eye level and where the land starts, for the app's list). Stops
 * with a reason if the framing is off (land where the sun travels, or the summit somewhere else).
 */
fun main(args: Array<String>) {
    if (args.size != 2) {
        System.err.println("usage: photoprep <copy.bmp> <out folder>")
        exitProcess(2)
    }
    val image = ImageIO.read(File(args[0])) ?: fail("can't read ${args[0]} as a picture")
    if (image.width != 1920 || image.height != 1080) fail("the copy is ${image.width} × ${image.height}, not 1920 × 1080")
    val photo = Channels.fromRgb(image.width, image.height, image.getRGB(0, 0, image.width, image.height, null, 0, image.width))
    val result = cut(photo, RUNDLE)
    val problems = framingProblems(result, RUNDLE)
    if (problems.isNotEmpty()) fail(problems.joinToString("; "))

    val out = File(args[1]).apply { mkdirs() }
    writeJpeg(result.land, File(out, "land.jpg"), 0.88f)
    writeLayers(result, File(out, "layers.png"))
    val landTop = result.edge.min().toInt()
    File(out, "photo.txt").writeText("eye_row\t${RUNDLE.eyeRow}\nland_top\t$landTop\n")
    println(
        "Made land.jpg and layers.png; the land starts at row $landTop, the summit at column ${result.edge.withIndex().minBy {
            it.value
        }.index}.",
    )
}

private fun fail(why: String): Nothing {
    System.err.println("STOPPED: $why")
    exitProcess(1)
}

private fun writeJpeg(p: Channels, file: File, quality: Float) {
    val image = BufferedImage(p.w, p.h, BufferedImage.TYPE_INT_RGB)
    fun byte(v: Float) = (clip(v) * 255f).roundToInt()
    image.setRGB(0, 0, p.w, p.h, IntArray(p.w * p.h) { (byte(p.r[it]) shl 16) or (byte(p.g[it]) shl 8) or byte(p.b[it]) }, 0, p.w)
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val param = writer.defaultWriteParam.apply {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionQuality = quality
    }
    ImageIO.createImageOutputStream(file).use { stream ->
        writer.output = stream
        writer.write(null, IIOImage(image, null, null), param)
    }
    writer.dispose()
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
