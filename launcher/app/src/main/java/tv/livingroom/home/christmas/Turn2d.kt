package tv.livingroom.home.christmas

import kotlin.math.cos
import kotlin.math.sin

/**
 * A placement on the screen that can be built up step by step, as a canvas's is: each [translate], [rotate] and [scale]
 * applies inside the ones before it, so a jointed figure's parts (a hand inside an arm inside a body that leans) are placed
 * exactly as the mockup's canvas places them. Mutable and reused, so a frame makes no objects; [set] copies one into another
 * to branch from a joint. A point (x, y) goes to (a x + c y + e, b x + d y + f).
 */
class Turn2d {
    var a = 1f
        private set
    var b = 0f
        private set
    var c = 0f
        private set
    var d = 1f
        private set
    var e = 0f
        private set
    var f = 0f
        private set

    fun reset(): Turn2d = apply {
        a = 1f
        b = 0f
        c = 0f
        d = 1f
        e = 0f
        f = 0f
    }

    fun set(other: Turn2d): Turn2d = apply {
        a = other.a
        b = other.b
        c = other.c
        d = other.d
        e = other.e
        f = other.f
    }

    fun translate(x: Float, y: Float): Turn2d = apply {
        e += a * x + c * y
        f += b * x + d * y
    }

    /** Turns what follows [radians] clockwise on the screen (y points down), about the current origin. */
    fun rotate(radians: Float): Turn2d = apply {
        if (radians == 0f) return this
        val cs = cos(radians)
        val sn = sin(radians)
        val na = a * cs + c * sn
        val nb = b * cs + d * sn
        c = -a * sn + c * cs
        d = -b * sn + d * cs
        a = na
        b = nb
    }

    fun scale(sx: Float, sy: Float = sx): Turn2d = apply {
        a *= sx
        b *= sx
        c *= sy
        d *= sy
    }

    fun mapX(x: Float, y: Float): Float = a * x + c * y + e

    fun mapY(x: Float, y: Float): Float = b * x + d * y + f
}
