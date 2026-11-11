package tv.livingroom.home.christmas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import kotlin.math.roundToInt

/**
 * Paints [Mark]s on an Android canvas. Glows are stamped from one small picture per colour, made
 * the first time it's needed: a gradient built for each of a scene's few hundred glows would be
 * slower. Use one painter per picture and [recycle] it afterwards.
 */
internal class InkPainter {
    private val paint = Paint()
    private val path = Path()
    private val clipPath = Path() // Its own path: a clipped fill traces its clip and its outline at once.
    private val area = RectF()
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val glows = HashMap<Int, Bitmap>()

    /** Paints [marks] in order. False if [keepGoing] (asked every few marks) stopped it part way. */
    fun draw(canvas: Canvas, marks: List<Mark>, keepGoing: () -> Boolean = { true }): Boolean {
        for ((i, mark) in marks.withIndex()) {
            if (i % CHECK_EVERY == 0 && !keepGoing()) return false
            paint.reset()
            paint.isAntiAlias = true
            when (mark) {
                is FillMark -> fill(canvas, mark)
                is LineMark -> line(canvas, mark)
                is GlowMark -> glow(canvas, mark)
                is WordMark -> word(canvas, mark)
            }
        }
        return true
    }

    fun recycle() {
        glows.values.forEach { it.recycle() }
        glows.clear()
    }

    private fun fill(canvas: Canvas, mark: FillMark) {
        when (val tone = mark.tone) {
            is Flat -> paint.color = tone.argb
            is Fade ->
                paint.shader =
                    LinearGradient(tone.x0, tone.y0, tone.x1, tone.y1, colours(tone.stops), places(tone.stops), Shader.TileMode.CLAMP)
            is Burst ->
                paint.shader =
                    RadialGradient(tone.x, tone.y, tone.radius, colours(tone.stops), places(tone.stops), Shader.TileMode.CLAMP)
        }
        if (mark.additive) paint.xfermode = add
        val clip = mark.clip
        if (clip == null) {
            canvas.drawPath(trace(mark.outline), paint)
            return
        }
        canvas.save()
        canvas.clipPath(trace(clip, clipPath))
        canvas.drawPath(trace(mark.outline), paint)
        canvas.restore()
    }

    private fun line(canvas: Canvas, mark: LineMark) {
        paint.style = Paint.Style.STROKE
        paint.color = mark.argb
        paint.strokeWidth = mark.width
        paint.strokeCap = if (mark.round) Paint.Cap.ROUND else Paint.Cap.BUTT
        paint.strokeJoin = Paint.Join.ROUND
        val clip = mark.clip
        if (clip == null) {
            canvas.drawPath(trace(mark.outline), paint)
            return
        }
        canvas.save()
        canvas.clipPath(trace(clip, clipPath))
        canvas.drawPath(trace(mark.outline), paint)
        canvas.restore()
    }

    private fun glow(canvas: Canvas, mark: GlowMark) {
        val picture = glows.getOrPut(mark.rgb and 0xFFFFFF) { glowPicture(mark.rgb) }
        paint.isFilterBitmap = true
        paint.alpha = (mark.alpha * 255).roundToInt()
        paint.xfermode = add
        area.set(mark.x - mark.radius, mark.y - mark.radius, mark.x + mark.radius, mark.y + mark.radius)
        canvas.drawBitmap(picture, null, area, paint)
    }

    private fun word(canvas: Canvas, mark: WordMark) {
        paint.color = mark.style.argb
        paint.typeface = if (mark.style.face == WordFace.SANS) SANS_FACE else WORD_FACE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = mark.size
        val width = paint.measureText(mark.text)
        val size = if (width > mark.style.widest) mark.size * mark.style.widest / width else mark.size
        paint.textSize = size
        canvas.drawText(mark.text, mark.x, mark.y + size * WORD_BASELINE, paint)
    }

    private fun trace(outline: Outline, path: Path = this.path): Path {
        val p = outline.points
        var i = 0
        path.rewind()
        for (verb in outline.verbs) {
            when (verb) {
                Outline.MOVE -> path.moveTo(p[i], p[i + 1])
                Outline.LINE -> path.lineTo(p[i], p[i + 1])
                Outline.QUAD -> path.quadTo(p[i], p[i + 1], p[i + 2], p[i + 3])
                Outline.CUBIC -> path.cubicTo(p[i], p[i + 1], p[i + 2], p[i + 3], p[i + 4], p[i + 5])
                else -> path.close()
            }
            i += pointsUsed(verb)
        }
        return path
    }

    private fun colours(stops: List<Stop>) = IntArray(stops.size) { stops[it].argb }

    private fun places(stops: List<Stop>) = FloatArray(stops.size) { stops[it].at }
}

/** How many numbers of an outline's points [verb] uses. */
internal fun pointsUsed(verb: Byte): Int = when (verb) {
    Outline.MOVE, Outline.LINE -> 2
    Outline.QUAD -> 4
    Outline.CUBIC -> 6
    else -> 0
}

/** How a glow fades from its middle to its edge: as much of its full strength as each stop says. */
internal val GLOW_FALLOFF = listOf(0f to 1f, 0.25f to 0.55f, 0.6f to 0.16f, 1f to 0f)
private const val GLOW_PICTURE = 128

// The words' two faces, made when first used: JVM tests load this file too, where Typeface isn't there.
private val WORD_FACE: Typeface by lazy { Typeface.create(Typeface.SERIF, Typeface.BOLD) }
private val SANS_FACE: Typeface by lazy { Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }

/** Where a word's baseline sits below its middle, as a share of its size (about half a capital's height). */
internal const val WORD_BASELINE = 0.35f
private const val CHECK_EVERY = 64

/** One soft round light in [rgb], full strength in the middle: stamped at any size and strength. */
private fun glowPicture(rgb: Int): Bitmap {
    val picture = Bitmap.createBitmap(GLOW_PICTURE, GLOW_PICTURE, Bitmap.Config.ARGB_8888)
    val half = GLOW_PICTURE / 2f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = RadialGradient(
        half,
        half,
        half,
        IntArray(GLOW_FALLOFF.size) { ink(rgb, GLOW_FALLOFF[it].second) },
        FloatArray(GLOW_FALLOFF.size) { GLOW_FALLOFF[it].first },
        Shader.TileMode.CLAMP,
    )
    Canvas(picture).drawCircle(half, half, half, paint)
    return picture
}

/** Some [marks] to paint with their own corner at ([x], [y]) of a picture, in the marks' units. */
internal class InkPiece(val marks: List<Mark>, val x: Float = 0f, val y: Float = 0f)

/** [pieces] painted into a new see-through picture [width] x [height] pixels, [scale] pixels to each of the marks' units. */
internal fun inkPicture(width: Int, height: Int, pieces: List<InkPiece>, scale: Float = 1f): ImageBitmap {
    val picture = ImageBitmap(width, height)
    val canvas = Canvas(picture.asAndroidBitmap())
    canvas.scale(scale, scale)
    val painter = InkPainter()
    try {
        for (piece in pieces) {
            canvas.save()
            canvas.translate(piece.x, piece.y)
            painter.draw(canvas, piece.marks)
            canvas.restore()
        }
    } finally {
        painter.recycle()
    }
    return picture
}
