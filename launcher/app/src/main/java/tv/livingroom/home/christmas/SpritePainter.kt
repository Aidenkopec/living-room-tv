package tv.livingroom.home.christmas

import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/** How a frame's pieces are put on the screen: as they are, or adding their light to what's under them. */
enum class SpriteBlend { OVER, ADD }

/**
 * Draws pieces of one [sheet] picture. A whole list ([SpriteQuads]) goes to the GPU as one textured
 * mesh in one call, whatever each piece's place, size, turn and strength: on this TV's GPU the cost of
 * a frame is counted in draw calls far more than in pieces (measured: 800 pieces in one call cost the
 * render thread 0.2 ms more than none; 100 pieces one call each cost 2 ms more, 12 ms at worst).
 */
internal class SpritePainter(sheet: ImageBitmap) {
    private val picture = sheet.asAndroidBitmap()
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val paint = Paint().apply {
        isFilterBitmap = true
        shader = BitmapShader(picture, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }

    /** [quads] in order; [tint], if any, recolours every piece (see [tintTowards]). */
    fun draw(canvas: Canvas, quads: SpriteQuads, blend: SpriteBlend = SpriteBlend.OVER, tint: ColorFilter? = null) {
        if (quads.count == 0) return
        paint.xfermode = if (blend == SpriteBlend.ADD) add else null
        paint.colorFilter = tint
        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES,
            quads.count * SpriteQuads.CORNER_VALUES,
            quads.corners,
            0,
            quads.sheet,
            0,
            quads.strengths,
            0,
            quads.triangles,
            0,
            quads.count * SpriteQuads.TRIANGLE_CORNERS,
            paint,
        )
    }
}

/**
 * A filter that moves every colour [amount] of the way towards [rgb] and leaves alpha alone: what
 * [VillageLook.lit] does to one colour, done to a whole piece as it's drawn. The moving pieces are
 * painted once in their own colours and take the hour's light this way, instead of being repainted
 * whenever the light changes.
 */
internal fun tintTowards(rgb: Int, amount: Float): ColorFilter {
    val keep = 1 - amount
    fun towards(shift: Int) = ((rgb shr shift) and 0xFF) * amount
    return ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                keep, 0f, 0f, 0f, towards(16),
                0f, keep, 0f, 0f, towards(8),
                0f, 0f, keep, 0f, towards(0),
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}
