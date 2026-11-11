package tv.livingroom.home.christmas

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * What moves in Fireside's room, worked out for one frame at a time as lists of
 * sprites: the fire, the candles, the steam off the mug, the stockings swaying,
 * the dogs breathing in their sleep, the twinkling lights, the snow outside and
 * the firelight on the room. Everything depends only on the time, so nothing is
 * carried from frame to frame. Plain Kotlin: the same functions run in JVM tests.
 */

/**
 * One frame's pieces, in the order they're drawn: [glows] add their light over the still picture; the fire's [tongues], cut to
 * the fireplace's opening (a poked fire burns taller than it); [flames] (the candles' flames, the steam) as they are; [room]
 * (logs, stockings and what's in them, the gingerbread person) take the room's tint; [embers] as they are; then, cut to the
 * fireplace's opening, [insideLight] (added) and [inside] (Santa's tint: grit, smoke, the sack, Santa in the chimney);
 * [santa] (Santa's tint: Santa on the hearth); [rug] (the room's tint: dogs, ball, bones, parcel, the blue present, back to
 * front); [over] (z's, the dogs' back lights) as they are; [snow] and [windowLight]
 * (added) through the window's placement, cut to its glass; [fx] (the room's tint: soot, the parcel and bones in flight); and
 * [light] added on top (the fire's light over the room, sparkles, glitter, sparks, the relight's flash).
 */
class FiresideFrame {
    val glows = SpriteQuads(GLOW_PIECES)
    val tongues = SpriteQuads(16)
    val flames = SpriteQuads(24)
    val room = SpriteQuads(48)
    val embers = SpriteQuads(2)
    val insideLight = SpriteQuads(64)
    val inside = SpriteQuads(64)
    val santa = SpriteQuads(64)
    val rug = SpriteQuads(96)
    val over = SpriteQuads(16)
    val snow = SpriteQuads(SNOW_PIECES)
    val windowLight = SpriteQuads(40)
    val fx = SpriteQuads(128)
    val light = SpriteQuads(LIGHT_PIECES)

    /** A few numbers' room for working things out, so a frame needs no new arrays. */
    val scratch = FloatArray(4)

    /** Each dog's sleep, worked out afresh for each dog on each frame. */
    val sleep = Sleep()

    /** What's going on at this frame's time; worked out first. */
    val now = FiresideNow()

    /** The dogs and ball on this frame, Santa's pose, transforms to build pieces with, and the rug's sorting. */
    val acts = RugActs()
    val pose = SantaPose()
    val turns = Array(4) { Turn2d() }

    /** Where a standing dog's pieces go on this frame. */
    val rig = DogRig()
    val rugKeys = FloatArray(8)
    val rugOrder = IntArray(8)
    val gingerbread = FloatArray(3)
    val flight = Flight()

    fun clear() {
        for (q in layers) q.clear()
    }

    private val layers = listOf(glows, tongues, flames, room, embers, insideLight, inside, santa, rug, over, snow, windowLight, fx, light)
}

/**
 * What's going on in the room at one frame's time [t]: how far into Santa's visit ([visit], NaN if he isn't on his way) and when
 * his latest visit started ([visitStart], NaN before his first); the dogs' [moment]; how strong the [fire] burns (its [light]
 * on things is at most 1) and the [embers]; the poked fire's [flare], and how strongly the fire would send up sparks
 * ([sparking]: none from a fire that's out); when the blue present last popped ([popAt]); whether a [gingerbread] person is on the
 * mantel (out since [gingerAt]); how much harder it's snowing ([boost]) and how far ahead the window's flakes have fallen for it
 * ([snowAhead], seconds); the room's [look]; the tree's light
 * show ([treeShow], see [treeLightsInto]) and when OK last relit it ([treeLit]).
 */
class FiresideNow {
    var t = 0.0
    var visit = Double.NaN
    var visitStart = Double.NaN
    var moment: PlannedMoment? = null
    var fire = 0f
    var light = 0f
    var embers = 1f
    var flare = 0f
    var popAt = Double.NaN
    var gingerbread = false
    var gingerAt = Double.NaN
    var boost = 0f
    var snowAhead = 0.0
    var sparking = 0f
    var look: VillageLook? = null
    var treeShow = 1
    var treeLit = Double.NaN
}

/** One showing of Fireside: its [scene] (and that scene's [twinkles]), the evening's [plan] and the remote's [presses]. */
class FiresideLive(val scene: FiresideScene, val twinkles: List<Twinkle>, val plan: FiresidePlan, val presses: FiresidePresses)

private const val GLOW_PIECES = 256
private const val SNOW_PIECES = 960
private const val LIGHT_PIECES = 720

/** Where each plain glow comes in [FiresideSprites.lightList]. */
const val STAR_LIGHT = 8
const val BULB_GLOW = 9
const val BERRY_GLOW = 10

/**
 * The room's twinkling for [scene], worked out once a minute: the tree's bulbs and star, the strings along the
 * wall and the garland, and the wreath's berries. Each one's strength already has how bright its lights are and
 * how much the dark at the room's edges leaves of it, since the lights are added over that dark.
 */
fun roomTwinkles(scene: FiresideScene): List<Twinkle> {
    val look = scene.look
    fun edge(x: Float, y: Float) = 1 - edgeDark(look, x, y)
    val k = roomTreeBrightness(scene)
    val h = TREE_HEIGHT
    val bulbs = treeBulbs(TREE_FOOT_X, TREE_FOOT_Y, h).mapIndexed { n, bulb ->
        Twinkle(
            bulb.x,
            bulb.y,
            n % BULBS.size,
            h * 0.05f / BULB_REACH,
            2.4f,
            n * 1.7f,
            0.5f * 0.6f / 0.55f * bulb.on * k * edge(bulb.x, bulb.y),
            true,
        )
    }
    val starY = TREE_FOOT_Y - h * 0.95f
    val star = Twinkle(TREE_FOOT_X, starY, STAR_LIGHT, h * 0.13f / GLOW_REACH, 2.2f, 0f, 0.175f * k * edge(TREE_FOOT_X, starY), true)
    fun strings(list: List<LightString>, wall: Boolean) = list.flatMap { s ->
        val brightness = roomStringBrightness(look, wall)
        stringTwinkles(s).map { it.copy(light = ROOM_LIGHT, gain = (1 - ROOM_STRING_DIMMEST) / 2 * brightness * edge(it.x, it.y)) }
    }
    val berries = List(10) { i ->
        val (x, y) = wreathBerry(i)
        val glow = if (wreathBerryColour(i) == ROOM_BULB) BULB_GLOW else BERRY_GLOW
        Twinkle(x, y, glow, 20f / GLOW_REACH, 2f, i * 1.7f, (1 - WREATH_DIMMEST) / 2 * wreathGlow(look) * edge(x, y))
    }
    return bulbs + star + strings(WALL_STRINGS, wall = true) + strings(GARLAND_STRINGS, wall = false) + berries
}

/** How the tree's lights are breathing at [t] seconds, as a share of their still strength: slowly, and only late at night. */
fun treeBreath(scene: FiresideScene, t: Double): Float =
    if (scene.late) (LATE_TREE + LATE_TREE_SWING * sin(t * LATE_BREATH_RATE).toFloat()) / LATE_TREE else 1f

/** The late night tree's breathing, radians a second: one breath every ten seconds or so. */
const val LATE_BREATH_RATE = 0.6

/** Adds [twinkles] (from number [from] on) at [t] seconds into [quads]; the tree's own swing with [treeBreath]. */
fun roomTwinklesInto(quads: SpriteQuads, sprites: List<Sprite>, twinkles: List<Twinkle>, t: Double, breath: Float, from: Int = 0) {
    for (i in from until twinkles.size) {
        val twinkle = twinkles[i]
        val alpha = twinkle.strength(t) * (if (twinkle.onTree) breath else 1f)
        quads.add(sprites[twinkle.light], twinkle.x, twinkle.y, twinkle.scale, alpha.coerceAtMost(1f))
    }
}

/** The tree's bulbs, as [roomTwinkles] lists them first, and the row each hangs in. */
private val TREE_ROWS: IntArray = IntArray(7) { 3 + it }.flatMapIndexed { row, n -> List(n) { row } }.toIntArray()

/**
 * The room's twinkling at [t] seconds: the tree's bulbs and star in [FiresideNow.treeShow]'s light show (0 warm white, 1 coloured
 * twinkling, 2 colours chasing up the rows), lighting up from the bottom after OK and the star flaring; then the rest of [live]'s
 * twinkles as they are. The tree breathes by [breath] late at night.
 */
fun treeInto(frame: FiresideFrame, sprites: FiresideSprites, live: FiresideLive, t: Double, breath: Float) {
    val twinkles = live.twinkles
    val now = frame.now
    val lights = sprites.lightList
    val lit = if (now.treeLit.isNaN()) 1f else ((t - now.treeLit) / RELIGHT).toFloat().coerceIn(0f, 1f)
    val bulbs = minOf(TREE_ROWS.size, twinkles.size)
    for (n in 0 until bulbs) {
        val bulb = twinkles[n]
        val row = TREE_ROWS[n]
        val v = 0.14f + row * 0.122f
        val on = if (lit >= 1f) 1f else ((lit - (1 - v)) * 6).coerceIn(0f, 1f)
        val strength = bulb.gain * 2 * on * breath
        val light = when (now.treeShow) {
            0 -> ROOM_LIGHT
            2 -> (row + floor(t * 1.5).toInt()).mod(BULBS.size)
            else -> bulb.light
        }
        val twinkle = when (now.treeShow) {
            0 -> 0.6f + 0.4f * sin(t * 1.6 + n * 2.3).toFloat()
            2 -> 0.35f + 0.65f * sin(t * 3 - row * 0.9).toFloat().coerceIn(0f, 1f)
            else -> 0.5f + 0.5f * sin(t * bulb.rate + bulb.offset).toFloat()
        }
        frame.glows.add(lights[light], bulb.x, bulb.y, bulb.scale, (strength * twinkle).coerceAtMost(1f))
    }
    if (twinkles.size <= bulbs) return
    val star = twinkles[bulbs]
    val starOn = ((lit - 0.9f) * 10).coerceIn(0f, 1f)
    frame.glows.add(lights[star.light], star.x, star.y, star.scale, (star.strength(t) * breath * starOn).coerceAtMost(1f))
    val flare = if (now.treeLit.isNaN()) 0f else (1 - abs(t - now.treeLit - 1.5).toFloat() / 0.5f).coerceIn(0f, 1f)
    if (flare > 0f) { // The star flares as the last bulbs come on, and a ring of light spreads from it.
        frame.glows.add(sprites.glows.star, star.x, star.y, TREE_HEIGHT * (0.13f + flare * 0.3f) / GLOW_REACH, flare)
        for (i in 0 until 14) {
            val a = i / 14.0 * 2 * Math.PI
            val d = (1 - flare) * 190 + 30
            frame.glows.add(sprites.glows.star, star.x + cos(a).toFloat() * d, star.y + sin(a).toFloat() * d, 24f / GLOW_REACH, flare)
        }
    }
    roomTwinklesInto(frame.glows, lights, twinkles, t, 1f, from = bulbs + 1)
}

/** How long the tree takes to light up again after OK, seconds. */
private const val RELIGHT = 1.4

/** The sparks a poke sends up: when each sets off (seconds after the poke), how long it lives, where it starts and how it flies. */
private class Sparks(val start: FloatArray, val life: FloatArray, val x: FloatArray, val vx: FloatArray, val vy: FloatArray)

private val SPARKS = Mulberry(11).let { r ->
    val n = 48
    Sparks(
        FloatArray(n) { 0.13f + r.next() * 2.74f }, // While the flare is over a third.
        FloatArray(n) { 0.8f + r.next() * 0.9f },
        FloatArray(n) { FIRE_X + (r.next() - 0.5f) * 160 },
        FloatArray(n) { (r.next() - 0.5f) * 60 },
        FloatArray(n) { -150 - r.next() * 160 },
    )
}

/**
 * Sparks flying up from the fire after a poke (Down), for each of the last few pokes: each a little light and a bright speck,
 * fading as it rises. Each poke throws its own spray (the set turned about, by which poke it is) and a low fire throws fewer.
 */
private fun sparksInto(frame: FiresideFrame, sprites: FiresideSprites, presses: FiresidePresses) {
    val now = frame.now
    val strength = (now.sparking / SPARKS_FULL).coerceIn(0f, 1f)
    if (strength <= 0.02f) return
    val s = SPARKS
    for (back in 0 until FLARES_KEPT) {
        val a = (now.t - presses.at(FiresideKey.FIRE, back)).toFloat()
        if (!(a >= 0 && a < FLARE_LENGTH + 2)) continue
        val poke = presses.count(FiresideKey.FIRE) - back
        val mirror = if (poke % 2 == 0) 1f else -1f
        val shift = (poke * 7) % s.start.size
        for (k in 0 until (s.start.size * strength).toInt()) {
            val i = (k + shift) % s.start.size
            val age = a - s.start[i]
            if (age <= 0 || age >= s.life[i]) continue
            val fade = 1 - age / s.life[i]
            val x = FIRE_X + mirror * (s.x[i] - FIRE_X + s.vx[i] * age) + sin(age * 9 + s.x[i]) * 8
            val y = 770 + s.vy[i] * age
            frame.light.add(sprites.glows.candle, x, y, 12f / GLOW_REACH, fade)
            frame.light.add(sprites.visit.spark, x, y, 1f, fade)
        }
    }
}

/** A fire this strong (before any poke) sends up every spark of a poke; a weaker one fewer, a dead one none. */
private const val SPARKS_FULL = 0.6f

/** How the firelight flickers at [t] seconds: 0.7 to 1. */
fun flicker(t: Double): Float = (0.85 + 0.15 * sin(t * 11) * sin(t * 3.7)).toFloat()

/** One of the fire's three layers of tongues: how wide and high it burns at full strength, how fast it moves, and its own offset. */
private class FlameLayer(val width: Float, val height: Float, val speed: Double, val seed: Double)

private val FLAME_LAYERS = listOf(FlameLayer(210f, 250f, 1.0, 0.0), FlameLayer(150f, 190f, 1.2, 2.0), FlameLayer(84f, 120f, 1.4, 4.0))

/** The foot of the fire's tongues, and the middle of its glow. */
const val FIRE_X = 1000f
const val FIRE_FOOT = 800f
private const val FIRE_GLOW_Y = 740f
private const val TONGUES = 5

/** Under this strength the fire shows no flames, only its glow. */
const val FLAMES_FROM = 0.03f

/**
 * The fire at [t] seconds burning [fire] strong: its glow into [frame]'s glows, three layers of five tongues
 * into its flames, the logs into the room and the embers over them, [embers] of their own glow.
 */
fun fireInto(frame: FiresideFrame, sprites: FiresideSprites, fire: Float, t: Double, embers: Float = 1f) {
    val flick = flicker(t)
    frame.glows.add(sprites.glows.fire, FIRE_X, FIRE_GLOW_Y, 230f / GLOW_REACH, (0.75f * min(1.2f, fire) * flick).coerceAtMost(1f))
    if (fire > FLAMES_FROM) {
        for (l in FLAME_LAYERS.indices) tonguesInto(frame.tongues, sprites.tongues[l], FLAME_LAYERS[l], fire, t)
    }
    frame.room.add(sprites.logs, 0f, 0f)
    frame.embers.add(sprites.embers, 0f, 0f, 1f, ((0.45f + 0.35f * sin(t * 2.2).toFloat()) * embers).coerceIn(0f, 1f))
    frame.light.add(
        sprites.glows.fire,
        FIRE_X,
        FIRE_FOOT,
        FIRELIGHT_REACH / GLOW_REACH,
        (0.2f * min(1.3f, fire) * flick).coerceAtMost(1f),
    )
}

/** How far the fire's light reaches across the room. */
const val FIRELIGHT_REACH = 1150f

private fun tonguesInto(quads: SpriteQuads, tongue: Sprite, layer: FlameLayer, fire: Float, t: Double) {
    val w = layer.width * (0.5f + fire * 0.5f)
    val tt = t * layer.speed
    val halfFoot = w / TONGUES * 0.95f
    for (i in 0 until TONGUES) {
        val env = 1 - 0.6f * abs(i - (TONGUES - 1) / 2f) / ((TONGUES - 1) / 2f)
        val x = FIRE_X - w / 2 + w * (i + 0.5f) / TONGUES
        val h = layer.height * fire * env * (0.6f + 0.4f * abs(sin(tt * (2.3 + i * 0.41) + i * 2.1 + layer.seed)).toFloat())
        val lean = sin(tt * 3.1 + i * 1.3 + layer.seed).toFloat() * w * 0.06f
        quads.addPlaced(tongue, x, FIRE_FOOT, Stretch(halfFoot / TONGUE_HW, h / TONGUE_H), atan2(lean, h))
    }
}

/** The candles' flames and their glow at [t] seconds, brighter by the fire's light than by day. */
fun candlesInto(frame: FiresideFrame, sprites: FiresideSprites, look: VillageLook, t: Double) {
    val brightness = candleBrightness(look)
    for (i in CANDLES.indices) {
        val c = CANDLES[i]
        val f = (0.85 + 0.15 * sin(t * 9 + c.x)).toFloat()
        val top = c.y - c.h
        frame.glows.add(sprites.glows.candle, c.x, top - 14, 70f / GLOW_REACH, 0.6f * f * brightness * (1 - edgeDark(look, c.x, top)))
        frame.flames.addPlaced(sprites.candleFlame, c.x + sin(t * 7 + c.x).toFloat() * 1.2f, top - 12, Stretch(1f, f))
    }
}

/** Three curls of steam rising off the mug and fading, at [t] seconds. */
fun steamInto(frame: FiresideFrame, sprites: FiresideSprites, look: VillageLook, t: Double) {
    val shade = 1 - edgeDark(look, MUG_X, MUG_Y)
    for (i in 0 until 3) {
        val a = (t * 0.22 + i / 3.0).mod(1.0).toFloat()
        frame.flames.add(sprites.steam, MUG_X + i * 10, MUG_Y - a * 50, 1f, sin(a * Math.PI).toFloat() * 0.4f * shade)
    }
}

private const val MUG_X = 498f
private const val MUG_Y = 650f

/** How far stocking [i] has swung at [t] seconds, radians: a gentle sway, each in its own time. */
fun stockingSway(i: Int, t: Double): Float = sin(t * 1.1 + i * 2).toFloat() * 0.04f

/**
 * The stockings swaying on their hooks at [t] seconds. Once Santa has been ([visitStart]), each holds what he left (a candy cane
 * and a parcel in a person's, a bone in a dog's), popping up out of it with a little jump of the stocking as it arrives.
 */
fun stockingsInto(frame: FiresideFrame, sprites: FiresideSprites, t: Double, visitStart: Double = Double.NaN) {
    val at = frame.turns[0]
    val stuff = frame.turns[1]
    val v = sprites.visit
    for (i in STOCKING_HOOKS.indices) {
        val hook = STOCKING_HOOKS[i]
        val since = t - (visitStart + stockingFilled(i))
        val filled = since >= 0 // False while NaN: he hasn't been.
        val pop = if (filled && since < 0.5) sin(since / 0.5 * Math.PI).toFloat() * 0.15f else 0f
        at.reset().translate(hook.first, hook.second).rotate(stockingSway(i, t) * (1 + pop * 0.5f / 0.04f)).scale(1 + pop)
        if (filled) {
            val g = smooth((since / 0.4).toFloat())
            val look = STOCKING_LOOKS[i]
            stuff.set(at).scale(look.scale)
            if (look.paw) {
                frame.room.addThrough(v.bone, stuff.translate(3f, 8 - 26 * g).rotate(1.25f).scale(0.95f))
            } else {
                frame.room.addThrough(v.cane, stuff.translate(0f, -30 * g), PERSON_STOCKING)
                frame.room.addThrough(v.stockingParcel, stuff.set(at).scale(look.scale).translate(0f, -22 * g), PERSON_STOCKING)
            }
        }
        frame.room.addThrough(sprites.stockings[i], at)
    }
}

/**
 * A sleeping dog at one instant: how full its breath is (1 ± 4.5%), its tail's wag (0 to 1), a paw's twitch (units) and an ear's
 * flick (0 or 1).
 */
class Sleep {
    var breath = 1f
    var wag = 0f
    var twitch = 0f
    var ear = 0f
}

/** Each dog's own offset in its breathing and its little stirrings, so the two never move together. */
fun sleepPhase(dog: Dog): Double = if (dog == Dog.BIG) 0.0 else 2.6

/** How fast each dog breathes, radians a second. */
fun breathRate(dog: Dog): Double = if (dog == Dog.BIG) 1.0 else 1.3

/** [dog] asleep at [t] seconds, into [sleep]: every eight seconds a wag of the tail, then a paw twitches, then an ear. */
fun sleepAt(dog: Dog, t: Double, sleep: Sleep) {
    val ph = sleepPhase(dog)
    sleep.breath = 1 + 0.045f * sin(t * breathRate(dog) + ph).toFloat()
    val c = (t + ph * 1.7).mod(STIR_EVERY)
    sleep.wag = if (c < 1.6) (abs(sin(c * 9)) * (1 - c / 1.6)).toFloat() else 0f
    sleep.twitch = if (c > 4.6 && c < 5.2) sin((c - 4.6) * 40).toFloat() * 3 else 0f
    sleep.ear = if (c > 6.2 && c < 6.5) 1f else 0f
}

/** How often a sleeping dog stirs, seconds. */
const val STIR_EVERY = 8.0

/**
 * The window's snow at [t] seconds into [frame], [amount] of it (see [snowAmount]), on the village's units: only flakes
 * the window shows, [shade] of their strength (the room's dark edges reach the window).
 */
fun windowSnowInto(frame: FiresideFrame, discs: List<Sprite>, t: Double, amount: Float, shade: Float) {
    val quads = frame.snow
    val at = frame.scratch
    for (d in SNOW.indices) {
        val flakes = SNOW[d]
        val painted = FLAKE_DISCS[d]
        for (i in 0 until flakes.falling(amount)) {
            flakeAt(flakes, i, t, at)
            if (at[0] < VIEW_LEFT || at[0] > VIEW_RIGHT || at[1] > VIEW_BOTTOM) continue
            quads.add(discs[d], at[0], at[1], flakes.radius[i] / painted, at[2] * shade)
        }
    }
}

/** How strong the fire's light along a sleeping dog's back is, at a full fire. */
const val BACK_LIGHT = 0.38f

/** The part of the outdoor scene the window shows, on the village's units (a little spare for a flake's size). */
private const val VIEW_LEFT = (WINDOW_X - WINDOW_VIEW_X) / WINDOW_VIEW_SCALE - 12
private const val VIEW_RIGHT = (WINDOW_X + WINDOW_W - WINDOW_VIEW_X) / WINDOW_VIEW_SCALE + 12
private const val VIEW_BOTTOM = (WINDOW_Y + WINDOW_H - WINDOW_VIEW_Y) / WINDOW_VIEW_SCALE + 12

/** How much more snow the window shows than the village: it's nearer. */
const val WINDOW_SNOW_SHARE = 1.3f

/** Everything that moves in [live]'s room at [t] seconds into [frame] (cleared first). */
fun firesideFrameAt(frame: FiresideFrame, sprites: FiresideSprites, live: FiresideLive, t: Double) {
    frame.clear()
    val scene = live.scene
    nowAt(frame, live, t)
    val now = frame.now
    treeInto(frame, sprites, live, t, treeBreath(scene, t))
    fireInto(frame, sprites, now.fire, t, now.embers)
    sparksInto(frame, sprites, live.presses)
    candlesInto(frame, sprites, scene.look, t)
    steamInto(frame, sprites, scene.look, t)
    stockingsInto(frame, sprites, t, now.visitStart)
    gingerbreadInto(frame, sprites)
    rugInto(frame, sprites)
    visitInto(frame, sprites)
    val shade = 1 - edgeDark(scene.look, WINDOW_X + WINDOW_W / 2, WINDOW_Y + WINDOW_H / 2)
    windowSnowInto(
        frame,
        sprites.flakes,
        t + now.snowAhead,
        snowAmount(scene.weather) * WINDOW_SNOW_SHARE * (1 + BOOST_SNOW * now.boost),
        shade,
    )
    val moon = scene.sky.moon?.takeIf { scene.sky.bodyAlpha > 0.3f }
    windowVisitInto(frame, sprites, moon?.let { it.x * DP } ?: Float.NaN, moon?.let { it.y * DP } ?: Float.NaN)
}

/** The sky painter's dp to the outdoor scene's units. */
private const val DP = 2f

/** How much more snow each step of Up's [snowBoost] brings. */
const val BOOST_SNOW = 1.15f

/** Works out [frame]'s [FiresideNow] for [live] at [t]: plans what's due, then the visit, the moment, the fire, the remote's effects. */
internal fun nowAt(frame: FiresideFrame, live: FiresideLive, t: Double) {
    val now = frame.now
    val scene = live.scene
    val plan = live.plan
    val presses = live.presses
    plan.planTo(t, scene.late)
    now.t = t
    now.visit = plan.visitAt(t)
    now.visitStart = plan.latestVisitStart(t)
    val moment = plan.momentAt(t)
    now.moment = moment
    now.flare = fireFlareAt(presses, t)
    val st = now.visit
    val out = if (st.isNaN()) 1f else visitFire(st)
    val relit = if (st.isNaN()) 0f else relightFlash(st)
    now.fire = scene.fire * (1 + now.flare * 0.9f) * out + relit * 0.4f
    now.sparking = scene.fire * out
    now.look = scene.look
    now.light = now.fire.coerceAtMost(1f)
    now.embers = 0.3f + 0.7f * minOf(1f, out)
    now.popAt = runStart(presses, FiresideKey.PRESENT, POP_LENGTH, t)
    now.gingerAt = runStart(presses, FiresideKey.GINGERBREAD, GINGER_LENGTH, t)
    now.gingerbread = gingerbreadAt(t - now.gingerAt, frame.gingerbread)
    now.snowAhead = BOOST_FALL * snowBoostSoFar(presses, t)
    now.boost = snowBoost(presses, t)
    now.treeShow = treeShow(presses.count(FiresideKey.TREE))
    now.treeLit = presses.at(FiresideKey.TREE)
}

/** The gingerbread person on the mantel, if one is out (into [FiresideNow.gingerbread]), waving in the middle. */
private fun gingerbreadInto(frame: FiresideFrame, sprites: FiresideSprites) {
    val now = frame.now
    val at = frame.gingerbread
    if (!now.gingerbread) return
    val v = sprites.visit
    val walk = at[1]
    val sprite = if (at[2] > 0f) {
        v.gingerWave[((sin(now.t * 9).toFloat() + 1) / 2 * (GINGER_WAVE_FRAMES - 1)).roundToInt()]
    } else {
        v.gingerWalk[step(walk.toDouble() / 2, GINGER_WALK_FRAMES)]
    }
    frame.room.add(sprite, at[0], MANTEL_TOP - abs(sin(walk * Math.PI.toFloat())) * 5 * GINGER_SCALE)
}

/** The mantel's top, where the gingerbread person walks. */
private const val MANTEL_TOP = 287f
