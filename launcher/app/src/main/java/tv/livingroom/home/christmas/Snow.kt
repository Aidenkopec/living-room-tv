package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import tv.livingroom.home.screensaver.SkyWeather

/*
 * Falling snow over the village: three depths of flakes laid out once from a
 * seed, each falling at its own speed, swaying, and drifting with a light wind.
 * Where a flake is depends only on the time, so nothing is carried from frame
 * to frame.
 */

/** One depth of snow: how many flakes at a steady fall, their sizes, how fast they fall (px a second) and how strongly they show. */
class SnowDepth(val count: Int, val small: Float, val big: Float, val fall: Float, val alpha: Float)

/** Far and fine, middle, and a few big near ones. */
val SNOW_DEPTHS = listOf(SnowDepth(150, 1.2f, 2.4f, 40f, 0.6f), SnowDepth(90, 2.2f, 3.8f, 66f, 0.8f), SnowDepth(26, 5f, 9f, 110f, 0.5f))

/** How much snow is in the air: a little always, properly when the weather says snow (1 is a steady fall). */
fun snowAmount(weather: SkyWeather): Float = if (weather == SkyWeather.SNOW) 1.15f else 0.4f

/** The flakes of one depth, as columns: where each starts, its radius, where in its sway it is, and its own share of the depth's speed. */
class Flakes(val depth: SnowDepth, seedFrom: Mulberry) {
    val size = depth.count * SPARE
    val x = FloatArray(size)
    val y = FloatArray(size)
    val radius = FloatArray(size)
    val phase = FloatArray(size)
    val speed = FloatArray(size)

    init {
        for (i in 0 until size) {
            x[i] = seedFrom.next() * SNOW_SPAN_X
            y[i] = seedFrom.next() * SNOW_SPAN_Y
            radius[i] = lerp(depth.small, depth.big, seedFrom.next())
            phase[i] = seedFrom.next() * 2 * PI.toFloat()
            speed[i] = 0.6f + seedFrom.next() * 0.8f
        }
    }

    /** How many of them fall when there's [amount] snow in the air. */
    fun falling(amount: Float): Int = min(size, (depth.count * amount).roundToInt())
}

/** The village's flakes, the same on every run. */
val SNOW: List<Flakes> = Mulberry(3).let { seed -> SNOW_DEPTHS.map { Flakes(it, seed) } }

/** Where flake [i] of [flakes] is at [t] seconds, as x, y and strength in [into]. It wraps round just outside the picture. */
fun flakeAt(flakes: Flakes, i: Int, t: Double, into: FloatArray) {
    val depth = flakes.depth
    val s = flakes.speed[i]
    val sway = sin(t * 0.6 * s + flakes.phase[i]) * SWAY
    into[0] = ((flakes.x[i] + sway + t * depth.fall * WIND).mod(SNOW_SPAN_X.toDouble()) - SNOW_EDGE_X).toFloat()
    into[1] = ((flakes.y[i] + t * depth.fall * s).mod(SNOW_SPAN_Y.toDouble()) - SNOW_EDGE_Y).toFloat()
    into[2] = depth.alpha * (0.6f + 0.4f * sin(flakes.phase[i] + t).toFloat())
}

/** Adds [amount] of snow at [t] seconds to [frame]: every depth, far first, each flake a scaled copy of its depth's disc. */
fun snowInto(frame: MotionFrame, discs: List<Sprite>, t: Double, amount: Float) {
    val at = frame.scratch
    for (d in SNOW.indices) {
        val flakes = SNOW[d]
        val disc = discs[d]
        val painted = FLAKE_DISCS[d]
        for (i in 0 until flakes.falling(amount)) {
            flakeAt(flakes, i, t, at)
            frame.plain.add(disc, at[0], at[1], flakes.radius[i] / painted, at[2])
        }
    }
}

/** Up to three times a steady fall's flakes are laid out, for the heaviest snow. */
private const val SPARE = 3
private const val SWAY = 18.0 // How far a flake swings either side as it falls, px.
private const val WIND = 0.36 // Sideways drift, as a share of the depth's falling speed.

/** Flakes wrap round a little outside the picture, so none pops in or out at an edge. */
private const val SNOW_EDGE_X = 30f
private const val SNOW_EDGE_Y = 15f
private const val SNOW_SPAN_X = VILLAGE_W + 2 * SNOW_EDGE_X
private const val SNOW_SPAN_Y = VILLAGE_H + 2 * SNOW_EDGE_Y
