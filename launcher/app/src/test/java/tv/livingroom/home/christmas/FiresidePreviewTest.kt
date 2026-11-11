package tv.livingroom.home.christmas

import java.awt.Color
import java.awt.LinearGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Point2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import javax.imageio.ImageIO
import org.junit.Assume.assumeTrue
import org.junit.Test
import tv.livingroom.home.HomePlace
import tv.livingroom.home.sky.DockSkyLayout

/**
 * Not a pass/fail test: paints Fireside at four times of day (and late at night) into PNG files with Java2D,
 * with what moves as it stands a few seconds in, to look at before a build goes to the TV. It runs only when
 * VILLAGE_PREVIEW names a folder: `VILLAGE_PREVIEW=/tmp/village ./gradlew :app:testDebugUnitTest --tests '*FiresidePreviewTest'`.
 * The window's sky here is a plain gradient with a plain sun or moon; the TV's has stars and the moon's shape.
 * Placeholder names are on the stockings unless VILLAGE_PREVIEW_NAMES points at a names file.
 */
class FiresidePreviewTest {
    private val looks = listOf(
        "1-morning" to LocalTime.of(8, 40),
        "2-afternoon" to LocalTime.of(15, 10),
        "3-evening" to LocalTime.of(18, 30),
        "4-midnight" to LocalTime.of(22, 50),
        "5-late" to LocalTime.of(23, 30),
    )

    @Test
    fun paintFireside() {
        val folder = System.getenv("VILLAGE_PREVIEW")
        assumeTrue("VILLAGE_PREVIEW isn't set", !folder.isNullOrEmpty())
        val date = System.getenv("VILLAGE_PREVIEW_DATE")?.let(LocalDate::parse) ?: LocalDate.of(2026, 12, 10)
        val weather = System.getenv("VILLAGE_PREVIEW_WEATHER")?.toIntOrNull()
        val names = stockingNames(System.getenv("VILLAGE_PREVIEW_NAMES")?.let { File(it).readText() })
        File(folder!!).mkdirs()
        val plan = firesideSheet(names)
        val sheet = MotionPreview.paint(plan.sheet)
        ImageIO.write(sheet, "png", File(folder, "fireside-sheet.png"))
        for ((name, time) in looks) {
            val scene = firesideScene(ZonedDateTime.of(date, time, HomePlace.ZONE), weather)
            val live = FiresideLive(scene, roomTwinkles(scene), FiresidePlan(1), FiresidePresses())
            ImageIO.write(paint(scene, plan.sprites, sheet, live, MOVING_AT), "png", File(folder, "fireside-$name.png"))
        }
        // Santa's visit, at its moments, in the evening and the morning.
        for ((name, time) in listOf("evening" to LocalTime.of(18, 30), "morning" to LocalTime.of(8, 40))) {
            val scene = firesideScene(ZonedDateTime.of(date, time, HomePlace.ZONE), weather)
            val live = FiresideLive(scene, roomTwinkles(scene), FiresidePlan(1, firstSanta = SANTA_AT), FiresidePresses())
            for (st in VISIT_STILLS) {
                val label = "%04.1f".format(st)
                ImageIO.write(paint(scene, plan.sprites, sheet, live, SANTA_AT + st), "png", File(folder, "santa-$name-$label.png"))
            }
        }
        // Each dog moment, a few seconds in, and the remote's buttons.
        val evening = firesideScene(ZonedDateTime.of(date, LocalTime.of(18, 30), HomePlace.ZONE), weather)
        for (moment in Moment.entries) {
            val asked = FiresidePlan(1, firstMoment = AskedMoment(moment, null))
            val live = FiresideLive(evening, roomTwinkles(evening), asked, FiresidePresses())
            var v = 0.5
            while (v < moment.length) {
                val file = File(folder, "moment-${moment.name.lowercase()}-${"%04.1f".format(v)}.png")
                ImageIO.write(paint(evening, plan.sprites, sheet, live, ASKED_AFTER + v), "png", file)
                v += MOMENT_STEP
            }
        }
        val presses = FiresidePresses()
        for (key in FiresideKey.entries) presses.press(key, 10.0)
        val live = FiresideLive(evening, roomTwinkles(evening), FiresidePlan(1), presses)
        for (a in listOf(0.6, 1.5, 4.5)) {
            ImageIO.write(paint(evening, plan.sprites, sheet, live, 10.0 + a), "png", File(folder, "remote-$a.png"))
        }
    }

    /** [scene] with what moves at [t] seconds into [live]'s evening, painted as the TV paints it (see `FiresideView`'s drawFrame). */
    private fun paint(scene: FiresideScene, sprites: FiresideSprites, sheet: BufferedImage, live: FiresideLive, t: Double): BufferedImage {
        val image = BufferedImage(VILLAGE_W.toInt(), VILLAGE_H.toInt(), BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        val before = g.transform
        Java2dInk.draw(g, roomWallMarks(scene))
        g.clip(Rectangle2D.Float(WINDOW.x, WINDOW.y, WINDOW.w, WINDOW.h))
        g.translate(WINDOW_VIEW_X.toDouble(), WINDOW_VIEW_Y.toDouble())
        g.scale(WINDOW_VIEW_SCALE.toDouble(), WINDOW_VIEW_SCALE.toDouble())
        windowSky(g, scene)
        Java2dInk.draw(g, windowLandMarks(scene.look))
        g.transform = before
        g.clip = null
        Java2dInk.draw(g, firesideMarks(scene))
        val frame = FiresideFrame()
        firesideFrameAt(frame, sprites, live, t)
        val room = MotionPreview.tinted(sheet, scene.look.room, scene.look.roomK)
        val santa = MotionPreview.tinted(sheet, scene.look.room, scene.look.roomK * 0.35f)
        MotionPreview.draw(g, sheet, frame.glows, additive = true)
        g.clip(arch())
        MotionPreview.draw(g, sheet, frame.tongues)
        g.clip = null
        MotionPreview.draw(g, sheet, frame.flames)
        MotionPreview.draw(g, room, frame.room)
        MotionPreview.draw(g, sheet, frame.embers)
        g.clip(arch())
        MotionPreview.draw(g, sheet, frame.insideLight, additive = true)
        MotionPreview.draw(g, santa, frame.inside)
        g.clip = null
        MotionPreview.draw(g, santa, frame.santa)
        MotionPreview.draw(g, room, frame.rug)
        MotionPreview.draw(g, sheet, frame.over)
        g.clip(Rectangle2D.Float(WINDOW_SNOW.x, WINDOW_SNOW.y, WINDOW_SNOW.w, WINDOW_SNOW.h))
        g.translate(WINDOW_VIEW_X.toDouble(), WINDOW_VIEW_Y.toDouble())
        g.scale(WINDOW_VIEW_SCALE.toDouble(), WINDOW_VIEW_SCALE.toDouble())
        MotionPreview.draw(g, sheet, frame.snow)
        MotionPreview.draw(g, sheet, frame.windowLight, additive = true)
        g.transform = before
        g.clip = null
        MotionPreview.draw(g, room, frame.fx)
        MotionPreview.draw(g, sheet, frame.light, additive = true)
        if (scene.late) {
            g.color = Color(0, 0, 0, (0.7f * 255).toInt())
            g.fill(Rectangle2D.Float(0f, 0f, VILLAGE_W, VILLAGE_H))
        }
        g.dispose()
        return image
    }

    /** The fireplace's opening, as the TV cuts the fire and the inside of the chimney to it. */
    private fun arch() = java.awt.geom.Path2D.Float().apply {
        moveTo(858f, 822f)
        lineTo(858f, 610f)
        curveTo(858f, 500f, 1142f, 500f, 1142f, 610f)
        lineTo(1142f, 822f)
        closePath()
    }

    /** The window's sky as a gradient in the village's colours, with a plain sun or moon where the TV puts them. */
    private fun windowSky(g: java.awt.Graphics2D, scene: FiresideScene) {
        val look = scene.sky.look
        g.paint = LinearGradientPaint(
            Point2D.Float(0f, 0f),
            Point2D.Float(0f, VILLAGE_H),
            floatArrayOf(0f, VILLAGE_SKY.middleAt, VILLAGE_SKY.horizonAt, 1f),
            arrayOf(Color(look.top), Color(look.middle), Color(look.horizon), Color(look.horizon)),
        )
        g.fill(Rectangle2D.Float(0f, 0f, VILLAGE_W, VILLAGE_H))
        val px = VILLAGE_W / DockSkyLayout.WIDTH
        val body = scene.sky.sun ?: scene.sky.moon ?: return
        g.color = if (scene.sky.sun != null) Color(0xFFF0C0) else Color(0xEEE9DC)
        g.fill(java.awt.geom.Ellipse2D.Float(body.x * px - 30, body.y * px - 30, 60f, 60f))
    }
}

private const val MOVING_AT = 6.5
private const val MOMENT_STEP = 0.7
private const val SANTA_AT = 5.0
private val VISIT_STILLS = listOf(
    1.0, 2.2, 3.0, 5.0, 5.4, 7.0, 7.7, 8.1, 8.6, 9.35, 10.0, 11.0, 12.2,
    13.3, 14.4, 16.5, 17.2, 18.5, 19.9, 21.0, 21.9, 22.6, 23.6, 26.0, 29.0,
)
