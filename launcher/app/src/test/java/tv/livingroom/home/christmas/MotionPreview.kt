package tv.livingroom.home.christmas

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Line2D
import java.awt.image.BufferedImage
import tv.livingroom.home.screensaver.mix

/**
 * Draws the moving layer's pieces with Java2D for the preview pictures, as the TV does: the moving
 * sheet painted once, each list's pieces cut from it and placed by their corners, lights added and
 * lit pieces tinted for the hour.
 */
internal object MotionPreview {
    private val plan = motionSheet()
    private val sheet: BufferedImage by lazy { paint(plan.sheet) }
    private val tints = HashMap<Pair<Int, Float>, BufferedImage>()

    /**
     * Everything that moves over the village in [scene], as it stands [t] seconds in, as the TV draws it: the train with the
     * village's front ([front], its marks) painted over it again in the strip it passes through, then everything else.
     */
    fun draw(g: Graphics2D, scene: VillageScene, t: Double, front: List<Mark> = inFrontOfTheTrain(scene)) {
        val frame = MotionFrame()
        val train = villageFrameInto(frame, plan.sprites, scene, VillageCast(), t)
        val lit = tints.getOrPut(scene.look.amb to scene.look.ambK) { tinted(sheet, scene.look.amb, scene.look.ambK) }
        draw(g, lit, frame.far)
        draw(g, sheet, frame.farGlass)
        draw(g, sheet, frame.farLights, additive = true)
        if (!train.isNaN()) {
            val before = g.clip
            g.clip(java.awt.geom.Rectangle2D.Float(0f, STRIP_TOP, VILLAGE_W, STRIP_BOTTOM - STRIP_TOP))
            Java2dInk.draw(g, front)
            g.clip = before
        }
        draw(g, sheet, frame.lights, additive = true)
        draw(g, lit, frame.lit)
        g.color = Color(scene.look.lit(0x2A2A30))
        g.stroke = BasicStroke(1.5f)
        for (i in 0 until frame.leadValues step 4) {
            g.draw(Line2D.Float(frame.leads[i], frame.leads[i + 1], frame.leads[i + 2], frame.leads[i + 3]))
        }
        draw(g, sheet, frame.plain)
    }

    /** The dock's rider a few seconds after landing on a tile whose top edge runs from ([left], [top]) for [width]. */
    fun drawRider(g: Graphics2D, left: Float, top: Float, width: Float) {
        val target = RideTarget().apply {
            focus = 1
            tiles = 2
            this.left = left
            this.top = top
            this.width = width
        }
        val ride = DockRide().apply { step(0.0, target, false) }
        val quads = SpriteQuads(2).also { riderInto(it, plan.sprites.rider, ride, 5.9, 1f) }
        draw(g, sheet, quads)
    }

    /** The whole moving sheet, as it's painted. */
    fun sheetPicture(): BufferedImage = sheet

    /** A sheet's pieces painted where the layout puts them. */
    fun paint(sheet: Sheet): BufferedImage {
        val image = BufferedImage(sheet.width, sheet.height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        for (piece in sheet.pieces) {
            val before = g.transform
            g.translate(piece.x.toDouble(), piece.y.toDouble())
            g.scale(piece.scale.toDouble(), piece.scale.toDouble())
            Java2dInk.draw(g, piece.marks)
            g.transform = before
        }
        g.dispose()
        return image
    }

    /** [image] with every colour moved [amount] of the way to [rgb], alpha kept: the TV's tint filter. */
    fun tinted(image: BufferedImage, rgb: Int, amount: Float): BufferedImage {
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                out.setRGB(x, y, (argb and 0xFF000000.toInt()) or (mix(argb, rgb, amount) and 0xFFFFFF))
            }
        }
        return out
    }

    /** [quads]' pieces of [image], each mapped from its box in the sheet onto its corners on screen. */
    fun draw(g: Graphics2D, image: BufferedImage, quads: SpriteQuads, additive: Boolean = false) {
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        val before = g.transform
        for (i in 0 until quads.count) {
            val at = i * SpriteQuads.CORNER_VALUES
            val c = quads.corners
            val s = quads.sheet
            val w = s[at + 2] - s[at]
            val h = s[at + 7] - s[at + 1]
            val strength = (quads.strengths[i * SpriteQuads.CORNERS] ushr 24) / 255f
            // The piece's own (0, 0), (w, 0) and (0, h) land on its first, second and fourth corners.
            g.transform(
                AffineTransform(
                    ((c[at + 2] - c[at]) / w).toDouble(),
                    ((c[at + 3] - c[at + 1]) / w).toDouble(),
                    ((c[at + 6] - c[at]) / h).toDouble(),
                    ((c[at + 7] - c[at + 1]) / h).toDouble(),
                    c[at].toDouble(),
                    c[at + 1].toDouble(),
                ),
            )
            g.composite = if (additive) AddLight(strength) else AlphaComposite.SrcOver.derive(strength)
            var piece = image.getSubimage(s[at].toInt(), s[at + 1].toInt(), w.toInt(), h.toInt())
            val keep = (quads.strengths[i * SpriteQuads.CORNERS] and 0xFF) / 255f // Darkened by its corners' colour, as on the TV.
            if (keep <
                1f
            ) {
                piece = java.awt.image.RescaleOp(floatArrayOf(keep, keep, keep, 1f), FloatArray(4), null).filter(piece.copy(), null)
            }
            g.drawImage(piece, 0, 0, null)
            g.transform = before
        }
        g.composite = AlphaComposite.SrcOver
    }
}

/** A copy of this picture with its own pixels (a sub-image shares its parent's, which a filter can't take). */
private fun BufferedImage.copy(): BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).also {
    it.createGraphics().drawImage(this, 0, 0, null)
}
