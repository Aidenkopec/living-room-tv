package tv.livingroom.home.christmas

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Composite
import java.awt.CompositeContext
import java.awt.Graphics2D
import java.awt.LinearGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.awt.image.ColorModel
import java.awt.image.Raster
import java.awt.image.WritableRaster
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.imageio.ImageIO
import kotlin.math.min
import kotlin.math.roundToInt
import org.junit.Assume.assumeTrue
import org.junit.Test
import tv.livingroom.home.HomePlace
import tv.livingroom.home.dock.DOCK_PADDING
import tv.livingroom.home.dock.dockSlots
import tv.livingroom.home.dock.dockWidth
import tv.livingroom.home.screensaver.mix
import tv.livingroom.home.sky.DockSkyLayout
import tv.livingroom.home.sky.bottomShade
import tv.livingroom.home.sky.clockShade
import tv.livingroom.home.sky.sunColour

/**
 * Not a pass/fail test: paints the village and the dressed dock into PNG files with Java2D, to look
 * at on the computer before a build goes to the TV. It runs only when the environment variable
 * VILLAGE_PREVIEW names a folder to write to:
 * `VILLAGE_PREVIEW=/tmp/village ./gradlew :app:testDebugUnitTest --tests '*VillagePreviewTest'`.
 * The TV's own painter draws the same marks; its sky has stars and the real moon's shape as well.
 * What moves is shown as it stands a few seconds in.
 */
class VillagePreviewTest {
    private val looks = listOf(
        "1-morning" to LocalTime.of(8, 40),
        "2-afternoon" to LocalTime.of(15, 10),
        "3-evening" to LocalTime.of(18, 30),
        "4-midnight" to LocalTime.of(0, 5),
    )

    @Test
    fun paintTheFourLooks() {
        val folder = System.getenv("VILLAGE_PREVIEW")
        assumeTrue("VILLAGE_PREVIEW isn't set", !folder.isNullOrEmpty())
        val date = System.getenv("VILLAGE_PREVIEW_DATE")?.let(LocalDate::parse) ?: LocalDate.of(2026, 11, 10)
        val weather = System.getenv("VILLAGE_PREVIEW_WEATHER")?.toIntOrNull()
        File(folder!!).mkdirs()
        for ((name, time) in looks) {
            val scene = villageScene(ZonedDateTime.of(date, time, HomePlace.ZONE), weather)
            val image = BufferedImage(VILLAGE_W.toInt(), VILLAGE_H.toInt(), BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            paintSky(g, scene)
            Java2dInk.draw(g, villageMarks(scene))
            MotionPreview.draw(g, scene, MOVING_AT)
            shade(image)
            paintDock(g, scene.look)
            g.dispose()
            ImageIO.write(image, "png", File(folder, "village-$name.png"))
        }
        ImageIO.write(MotionPreview.sheetPicture(), "png", File(folder, "moving-sheet.png"))
    }

    /** The village's life at its busiest moments: each look with what's out, the train, the sleigh and a shooting star. */
    @Test
    fun paintTheVillagesLife() {
        val folder = System.getenv("VILLAGE_PREVIEW")
        assumeTrue("VILLAGE_PREVIEW isn't set", !folder.isNullOrEmpty())
        val date = System.getenv("VILLAGE_PREVIEW_DATE")?.let(LocalDate::parse) ?: LocalDate.of(2026, 12, 12)
        File(folder!!).mkdirs()
        val train = TRAIN.start(0) + 9.0
        val sleigh = SLEIGH.start(0)
        val star = SHOOTING_STAR.start(0) + 0.45
        val moments = listOf(
            Triple("life-1-morning", LocalTime.of(10, 30), 2.5),
            Triple("life-2-afternoon", LocalTime.of(14, 30), 16.2),
            Triple("life-3-afternoon-train", LocalTime.of(14, 30), train),
            Triple("life-4-evening", LocalTime.of(18, 30), 41.0),
            Triple("life-5-evening-sleigh-a", LocalTime.of(18, 30), sleigh + 5.4),
            Triple("life-6-evening-sleigh-b", LocalTime.of(18, 30), sleigh + 8.0),
            Triple("life-7-night-fox", LocalTime.of(23, 30), 10.0),
            Triple("life-8-night-star", LocalTime.of(23, 30), star),
            Triple("life-9-evening-train", LocalTime.of(18, 30), train + 6),
        )
        for ((name, time, t) in moments) {
            val scene = villageScene(ZonedDateTime.of(date, time, HomePlace.ZONE), null)
            val image = BufferedImage(VILLAGE_W.toInt(), VILLAGE_H.toInt(), BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            paintSky(g, scene)
            Java2dInk.draw(g, villageMarks(scene))
            MotionPreview.draw(g, scene, t)
            shade(image)
            paintDock(g, scene.look)
            g.dispose()
            ImageIO.write(image, "png", File(folder, "$name.png"))
        }
    }

    /** The sky as the TV's painter lays it out, without stars: gradient, glow, then the sun or a plain moon. */
    private fun paintSky(g: Graphics2D, scene: VillageScene) {
        val sky = scene.sky
        val look = sky.look
        g.paint = LinearGradientPaint(
            Point2D.Float(0f, 0f),
            Point2D.Float(0f, VILLAGE_H),
            floatArrayOf(0f, VILLAGE_SKY.middleAt, VILLAGE_SKY.horizonAt, 1f),
            arrayOf(Color(look.top), Color(look.middle), Color(look.horizon), Color(look.horizon)),
        )
        g.fill(Rectangle2D.Float(0f, 0f, VILLAGE_W, VILLAGE_H))
        if (look.glowAlpha > 0f) {
            val centre = Point2D.Float(VILLAGE_W * sky.glowX, VILLAGE_H * VILLAGE_SKY.glowY)
            val radius = VILLAGE_W * VILLAGE_SKY.glowRadius
            val squash = AffineTransform.getTranslateInstance(0.0, centre.y.toDouble())
            squash.scale(1.0, VILLAGE_SKY.glowSquash.toDouble())
            squash.translate(0.0, -centre.y.toDouble())
            g.paint = RadialGradientPaint(
                centre,
                radius,
                centre,
                floatArrayOf(0f, 1f),
                arrayOf(Color(ink(look.glow, look.glowAlpha), true), Color(ink(look.glow, 0f), true)),
                java.awt.MultipleGradientPaint.CycleMethod.NO_CYCLE,
                java.awt.MultipleGradientPaint.ColorSpaceType.SRGB,
                squash,
            )
            g.fill(Rectangle2D.Float(0f, 0f, VILLAGE_W, VILLAGE_H))
        }
        Java2dInk.draw(g, villageAurora(scene))
        val px = VILLAGE_W / DockSkyLayout.WIDTH
        if (sky.bodyAlpha > 0f) {
            sky.moon?.let { Java2dInk.draw(g, listOf(FillMark(circle(it.x * px, it.y * px, 26f), Flat(ink(0xEEE9DC, sky.bodyAlpha))))) }
            sky.sun?.let {
                val colour = sunColour(sky.sunElevationDeg)
                val glow =
                    Burst(it.x * px, it.y * px, 160f, listOf(Stop(0f, ink(colour, 0.55f * sky.bodyAlpha)), Stop(1f, ink(colour, 0f))))
                Java2dInk.draw(
                    g,
                    listOf(
                        FillMark(circle(it.x * px, it.y * px, 160f), glow),
                        FillMark(circle(it.x * px, it.y * px, 34f), Flat(ink(colour, sky.bodyAlpha))),
                    ),
                )
            }
        }
    }

    private fun circle(x: Float, y: Float, r: Float): Outline = (Sketch().apply { disc(0, x, y, r) }.marks.single() as FillMark).outline

    /** The home screen's shades behind the clock and the dock, from the same rules the TV's painter follows. */
    private fun shade(image: BufferedImage) {
        val px = DockSkyLayout.WIDTH / VILLAGE_W
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val dark = 1 - (1 - clockShade(x * px, y * px)) * (1 - bottomShade(y * px))
                if (dark > 0f) image.setRGB(x, y, mix(image.getRGB(x, y), 0x000000, dark))
            }
        }
    }

    /** A stand-in dock with the second app selected, dressed: the glow, the bar, the lights, plain tiles, snow and holly. */
    private fun paintDock(g: Graphics2D, look: VillageLook) {
        val px = VILLAGE_W / DockSkyLayout.WIDTH
        val squares = List(9) { it >= 7 }
        val slots = dockSlots(squares, 1f, 7)
        val barWidth = dockWidth(slots) * px
        val barLeft = ((VILLAGE_W - barWidth) / 2).roundToInt().toFloat()
        val barTop = VILLAGE_H - 52 - 100
        val tilesBottom = barTop + 100 - 16
        val tileColours = intArrayOf(0xFFFFFF, 0xF4F4F4, 0x0A64FF, 0x07173F, 0x1A98FF, 0x18181B, 0x0E5A6E, 0x15141F, 0x15141F)
        val selected = slots[1]
        val selectedX = barLeft + (DOCK_PADDING + selected.x + selected.width / 2) * px
        val selectedY = tilesBottom - selected.height * px / 2
        Java2dInk.draw(g, listOf(GlowMark(selectedX, selectedY, selected.width * px * DOCK_GLOW_REACH, DOCK_GLOW, DOCK_GLOW_ALPHA)))
        g.color = Color(ink(DockSkyLayout.BAR, DockSkyLayout.BAR_ALPHA), true)
        g.fill(RoundRectangle2D.Float(barLeft, barTop, barWidth, 100f, 52f, 52f))
        at(g, barLeft, barTop + 100 - 11.5f + BULB_DROP - BULB_SIZE / 2) {
            Java2dInk.draw(g, dockStringMarks(barWidth, dockLightsBrightness(look)))
        }
        slots.forEachIndexed { i, slot ->
            val x = barLeft + (DOCK_PADDING + slot.x) * px
            val top = tilesBottom - slot.height * px
            val corner = 24f * slot.height / slots[1].height
            g.color = Color(tileColours[i])
            g.fill(RoundRectangle2D.Float(x, top, slot.width * px, slot.height * px, corner, corner))
            if (i == 1) {
                g.color = Color.WHITE
                g.stroke = BasicStroke(5f)
                g.draw(RoundRectangle2D.Float(x, top, slot.width * px, slot.height * px, corner, corner))
            }
        }
        slots.forEachIndexed { i, slot ->
            val scale = slot.height / slots[1].height
            val tallest = dockSlots(squares, i.toFloat(), 7)[i].width * px
            val x = barLeft + (DOCK_PADDING + slot.x) * px
            val top = tilesBottom - slot.height * px
            at(g, x - CAP_PAD_X * scale, top - CAP_PAD_TOP * capHeightScale(scale), scale, capHeightScale(scale)) {
                Java2dInk.draw(g, snowCapMarks(tallest, i, capColours(look)))
            }
        }
        MotionPreview.drawRider(g, barLeft + (DOCK_PADDING + selected.x) * px, tilesBottom - selected.height * px, selected.width * px)
        at(g, barLeft + 7 - HOLLY_X, barTop + 5 - HOLLY_Y) { Java2dInk.draw(g, hollyMarks(flip = false)) }
        at(g, barLeft + barWidth - 7 - (HOLLY_W - HOLLY_X), barTop + 5 - HOLLY_Y) { Java2dInk.draw(g, hollyMarks(flip = true)) }
    }

    private fun at(g: Graphics2D, x: Float, y: Float, scaleX: Float = 1f, scaleY: Float = 1f, block: () -> Unit) {
        val before = g.transform
        g.translate(x.toDouble(), y.toDouble())
        g.scale(scaleX.toDouble(), scaleY.toDouble())
        block()
        g.transform = before
    }
}

/** The moment the moving pieces are shown at, in seconds. */
private const val MOVING_AT = 18.0

/** Paints marks with Java2D, as InkPainter does on the TV. */
internal object Java2dInk {
    fun draw(g: Graphics2D, marks: List<Mark>) {
        for (mark in marks) {
            when (mark) {
                is FillMark -> {
                    g.composite = if (mark.additive) AddLight() else AlphaComposite.SrcOver
                    g.paint = paintOf(mark.tone)
                    val before = g.clip
                    mark.clip?.let { g.clip(trace(it)) }
                    g.fill(trace(mark.outline))
                    g.clip = before
                }
                is LineMark -> {
                    g.composite = AlphaComposite.SrcOver
                    g.color = Color(mark.argb, true)
                    g.stroke =
                        BasicStroke(mark.width, if (mark.round) BasicStroke.CAP_ROUND else BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND)
                    val before = g.clip
                    mark.clip?.let { g.clip(trace(it)) }
                    g.draw(trace(mark.outline))
                    g.clip = before
                }
                is GlowMark -> {
                    g.composite = AddLight()
                    g.paint = RadialGradientPaint(
                        Point2D.Float(mark.x, mark.y),
                        mark.radius,
                        FloatArray(GLOW_FALLOFF.size) { GLOW_FALLOFF[it].first },
                        Array(GLOW_FALLOFF.size) { Color(ink(mark.rgb, GLOW_FALLOFF[it].second * mark.alpha), true) },
                    )
                    g.fill(Ellipse2D.Float(mark.x - mark.radius, mark.y - mark.radius, mark.radius * 2, mark.radius * 2))
                }
                is WordMark -> {
                    g.composite = AlphaComposite.SrcOver
                    g.color = Color(mark.style.argb, true)
                    val sans = mark.style.face == WordFace.SANS
                    val face = java.awt.Font(
                        if (sans) java.awt.Font.SANS_SERIF else java.awt.Font.SERIF,
                        if (sans) 0 else java.awt.Font.BOLD,
                        1,
                    )
                    g.font = face.deriveFont(mark.size)
                    val natural = g.fontMetrics.stringWidth(mark.text)
                    val size = if (natural > mark.style.widest) mark.size * mark.style.widest / natural else mark.size
                    g.font = face.deriveFont(size)
                    val width = g.fontMetrics.stringWidth(mark.text)
                    g.drawString(mark.text, mark.x - width / 2f, mark.y + size * WORD_BASELINE)
                }
            }
        }
        g.composite = AlphaComposite.SrcOver
    }

    private fun paintOf(tone: Tone): java.awt.Paint = when (tone) {
        is Flat -> Color(tone.argb, true)
        is Fade -> LinearGradientPaint(
            Point2D.Float(tone.x0, tone.y0),
            Point2D.Float(tone.x1, tone.y1),
            places(tone.stops),
            colours(tone.stops),
        )
        is Burst -> RadialGradientPaint(Point2D.Float(tone.x, tone.y), tone.radius, places(tone.stops), colours(tone.stops))
    }

    private fun places(stops: List<Stop>) = FloatArray(stops.size) { stops[it].at }

    private fun colours(stops: List<Stop>) = Array(stops.size) { Color(stops[it].argb, true) }

    private fun trace(outline: Outline): Path2D.Float {
        val path = Path2D.Float()
        val p = outline.points
        var i = 0
        for (verb in outline.verbs) {
            when (verb) {
                Outline.MOVE -> path.moveTo(p[i], p[i + 1])
                Outline.LINE -> path.lineTo(p[i], p[i + 1])
                Outline.QUAD -> path.quadTo(p[i], p[i + 1], p[i + 2], p[i + 3])
                Outline.CUBIC -> path.curveTo(p[i], p[i + 1], p[i + 2], p[i + 3], p[i + 4], p[i + 5])
                else -> path.closePath()
            }
            i += pointsUsed(verb)
        }
        return path
    }
}

/** Adds the paint's light, [strength] of it, to what's there, as the TV's "add" blend does. */
internal class AddLight(private val strength: Float = 1f) : Composite {
    override fun createContext(src: ColorModel, dst: ColorModel, hints: RenderingHints?): CompositeContext = object : CompositeContext {
        override fun dispose() = Unit

        override fun compose(srcIn: Raster, dstIn: Raster, dstOut: WritableRaster) {
            val s = IntArray(4)
            val d = IntArray(4)
            val premultiplied = src.isAlphaPremultiplied
            for (y in 0 until min(srcIn.height, dstIn.height)) {
                for (x in 0 until min(srcIn.width, dstIn.width)) {
                    srcIn.getPixel(x, y, s)
                    dstIn.getPixel(x, y, d)
                    val alpha = min(255, (s[3] * strength).toInt() + d[3])
                    for (band in 0..2) {
                        val light = ((if (premultiplied) s[band] else s[band] * s[3] / 255) * strength).toInt()
                        val lit = min(255, d[band] * d[3] / 255 + light)
                        d[band] = if (alpha == 0) 0 else min(255, lit * 255 / alpha)
                    }
                    d[3] = alpha
                    dstOut.setPixel(x, y, d)
                }
            }
        }
    }
}
