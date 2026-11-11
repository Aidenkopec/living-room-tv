package tv.livingroom.photoprep

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.metadata.IIOMetadataNode
import kotlin.math.roundToInt

/** The name of the JPEG writer's own metadata tree, where the colour sampling is set. */
const val JPEG_METADATA = "javax_imageio_jpeg_image_1.0"

/**
 * [p] saved as a baseline JPEG at [quality] (0..1). Colour is kept at full resolution (4:4:4):
 * Java's writer halves it both ways by default (4:2:0), which softens coloured edges such as
 * sunlit rock against shadow. No camera or location details are written.
 */
fun writeJpeg(p: Channels, file: File, quality: Float) {
    val image = BufferedImage(p.w, p.h, BufferedImage.TYPE_INT_RGB)
    fun byte(v: Float) = (clip(v) * 255f).roundToInt()
    image.setRGB(0, 0, p.w, p.h, IntArray(p.w * p.h) { (byte(p.r[it]) shl 16) or (byte(p.g[it]) shl 8) or byte(p.b[it]) }, 0, p.w)
    val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
    val param = writer.defaultWriteParam.apply {
        compressionMode = ImageWriteParam.MODE_EXPLICIT
        compressionQuality = quality
    }
    val metadata = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image), param)
    val tree = metadata.getAsTree(JPEG_METADATA) as IIOMetadataNode
    val components = (tree.getElementsByTagName("sof").item(0) as IIOMetadataNode).getElementsByTagName("componentSpec")
    for (i in 0 until components.length) {
        (components.item(i) as IIOMetadataNode).apply {
            setAttribute("HsamplingFactor", "1")
            setAttribute("VsamplingFactor", "1")
        }
    }
    metadata.setFromTree(JPEG_METADATA, tree)
    file.delete() // The output stream writes over the start of an old file without shortening it.
    ImageIO.createImageOutputStream(file).use { stream ->
        writer.output = stream
        writer.write(null, IIOImage(image, null, metadata), param)
    }
    writer.dispose()
}
