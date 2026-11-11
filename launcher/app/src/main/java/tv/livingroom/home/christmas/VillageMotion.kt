package tv.livingroom.home.christmas

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import tv.livingroom.home.screensaver.SkyWeather

/*
 * What moves over the village picture, worked out for one frame at a time as
 * lists of sprites. Plain Kotlin: the same functions run in JVM tests.
 */

/**
 * One frame's pieces, in the three ways they are drawn, back to front: [lights] add their light to
 * the picture; [lit] pieces (smoke, people, dogs) take the hour's tint; [plain] ones (snow) are drawn
 * as they are, in front of everything.
 */
class MotionFrame(lights: Int = 384, lit: Int = 384, plain: Int = 1024) {
    val lights = SpriteQuads(lights)
    val lit = SpriteQuads(lit)
    val plain = SpriteQuads(plain)

    /**
     * What passes behind the village (the train and its steam, tinted) and the light it gives (added), drawn before everything
     * else, with the village's front painted over them again ([VillagePicture.strip]).
     */
    val far = SpriteQuads(FAR_PIECES)
    val farLights = SpriteQuads(FAR_LIGHTS)

    /** The train's lit windows: drawn over it as they are, in the light's own colour, not added and not tinted. */
    val farGlass = SpriteQuads(FAR_GLASS)

    /** A transform for pieces placed through one (the sleigh). */
    val turn = Turn2d()

    /** The order figures are drawn in this frame. */
    val order = DepthOrder(DEPTHS)

    /** The slot of the last sleigh seen, and whether it flies: decided once, when it's first seen, so it can't vanish mid-flight. */
    var sleighSlot = -1L
    var sleighFlies = false

    /** The dog walk, worked out afresh on each frame. */
    val dogWalk = DogWalk()

    /** A few numbers' room for working things out, so a frame allocates nothing. */
    val scratch = FloatArray(8)

    /** The dogs' leads as line segments (x, y, x, y for each), [leadValues] numbers of them this frame. */
    val leads = FloatArray(2 * LEAD_PARTS * 4)

    var leadValues = 0

    /** Where the lead being added starts (a hand), and how low its middle hangs: set by [leadFrom]. */
    internal var leadHandX = 0f
    internal var leadHandY = 0f
    internal var leadSag = 0f

    fun clear() {
        lights.clear()
        lit.clear()
        plain.clear()
        far.clear()
        farGlass.clear()
        farLights.clear()
        leadValues = 0
    }
}

/**
 * Fills [frame] (cleared first) with everything that moves over the village in [scene] at [t] seconds; [cast] says who is out.
 * Figures go into the tinted list back to front by where they stand, as the mockup sorts them. Gives back where the train's
 * front is, or NaN when it isn't crossing.
 */
fun villageFrameInto(frame: MotionFrame, sprites: MotionSprites, scene: VillageScene, cast: VillageCast, t: Double): Float {
    val look = scene.look
    val hours = scene.hours
    frame.clear()
    val train = trainInto(frame, sprites, look.lights, t)
    twinklesInto(frame.lights, sprites.lights, VILLAGE_TWINKLES, t, look.lights)
    brazierInto(frame, sprites, look, t)
    shootingStarInto(frame, sprites, starry(look, scene.sky.weather == SkyWeather.CLEAR), t)
    smokeInto(frame, sprites.puff, VILLAGE_CHIMNEYS, t)
    stallsInto(frame, sprites, cast, hours, t)
    val order = frame.order
    order.clear()
    for (i in WALKERS.indices) order.add(WALKERS[i].lane, i)
    order.add(WALK_Y, DOG_WALK)
    order.add(SNOWBALL_Y, SNOWBALL)
    order.add(CAROL_Y, CAROLS)
    for (i in SLEDGERS.indices) order.add(sledgerY(i, t, frame.scratch), SLEDGER + i)
    for (i in GINGERBREAD.indices) order.add(gingerbreadY(i), GINGER + i)
    order.add(FOX_Y, FOX)
    order.sort()
    for (n in 0 until order.count) {
        when (val id = order.id(n)) {
            DOG_WALK -> dogWalkInto(frame, sprites.cast, cast, hours, t)
            SNOWBALL -> snowballInto(frame, sprites, cast, hours, t)
            CAROLS -> carolsInto(frame, sprites, cast, hours, t)
            FOX -> foxInto(frame, sprites, cast, hours, t)
            in SLEDGER until GINGER -> sledgerInto(frame, sprites, cast, hours, t, id - SLEDGER)
            in GINGER until FOX -> gingerbreadInto(frame, sprites, cast, hours, t, id - GINGER)
            else -> walkerInto(frame, sprites.cast.walkers, cast, hours, t, id)
        }
    }
    letterInto(frame, sprites, cast, t)
    brazierPeopleInto(frame, sprites, cast, hours)
    pondInto(frame, sprites, cast, hours, t)
    cast.started()
    villageSleighInto(frame, sprites, sleighWeather(look), t)
    snowInto(frame, sprites.flakes, t, snowAmount(scene.sky.weather))
    return train
}

// What [DepthOrder] sorts, by number: the walkers are 0 to WALKERS.size - 1, then these.
internal val DOG_WALK = WALKERS.size
internal val SNOWBALL = DOG_WALK + 1
internal val CAROLS = SNOWBALL + 1
internal val SLEDGER = CAROLS + 1
private val GINGER = SLEDGER + 2 // Two sledgers,
private val FOX = GINGER + 3 // three gingerbread people.
internal val DEPTHS = FOX + 1

// Where the groups stand, for the order they're drawn in: the snowball fight between its two children's feet, the carollers at
// the nearest of their lanes but one.
private const val SNOWBALL_Y = 843f
private const val CAROL_Y = 850f
private const val FOX_Y = 874f

/** Figures to draw back to front: each one's number, sorted by where it stands. Reused, so a frame makes nothing new. */
class DepthOrder(capacity: Int) {
    private val ys = FloatArray(capacity)
    private val ids = IntArray(capacity)
    var count = 0
        private set

    fun clear() {
        count = 0
    }

    fun add(y: Float, id: Int) {
        ys[count] = y
        ids[count] = id
        count++
    }

    /** Sorts by where they stand, nearest last; equals keep the order they were added in. */
    fun sort() {
        for (i in 1 until count) {
            val y = ys[i]
            val id = ids[i]
            var j = i - 1
            while (j >= 0 && ys[j] > y) {
                ys[j + 1] = ys[j]
                ids[j + 1] = ids[j]
                j--
            }
            ys[j + 1] = y
            ids[j + 1] = id
        }
    }

    fun id(n: Int): Int = ids[n]
}

/**
 * Adds the twinkling of [twinkles] at [t] seconds: each one's light at its strength for that instant,
 * times how bright the village's lights are ([lights], 0 to 1).
 */
fun twinklesInto(quads: SpriteQuads, sprites: List<Sprite>, twinkles: List<Twinkle>, t: Double, lights: Float) {
    val onTree = treeBrightness(lights)
    for (i in twinkles.indices) {
        val twinkle = twinkles[i]
        val alpha = twinkle.strength(t) * (if (twinkle.onTree) onTree else lights)
        quads.add(sprites[twinkle.light], twinkle.x, twinkle.y, twinkle.scale, alpha.coerceAtMost(1f))
    }
}

/** How many puffs of smoke hang over a chimney at once. */
const val SMOKE_PUFFS = 9

/**
 * Puff [i] of the smoke from a chimney whose top is ([x], [y]), at [t] seconds: its x, y, radius and
 * strength in [into]. A puff rises 125 px over nine seconds, growing, swaying and leaning with the
 * wind, and is see-through at both ends of its life, so it starts again unseen.
 */
fun smokePuffAt(x: Float, y: Float, i: Int, t: Double, into: FloatArray) {
    val age = ((t + x) * SMOKE_RATE + i.toDouble() / SMOKE_PUFFS).mod(1.0).toFloat()
    into[0] = x + (sin(age * 5 + i * 1.7f) * 16 * age + SMOKE_WIND * age * age * 260) * SMOKE_SCALE
    into[1] = y - age * 250 * SMOKE_SCALE
    into[2] = (10 + age * 44) * SMOKE_SCALE
    into[3] = (1 - age) * 0.4f * min(1f, age * 7)
}

/** Adds the smoke over every one of [chimneys] at [t] seconds, as scaled copies of the [puff] disc. */
fun smokeInto(frame: MotionFrame, puff: Sprite, chimneys: List<Pair<Float, Float>>, t: Double) {
    val at = frame.scratch
    for (chimney in chimneys.indices) {
        val (x, y) = chimneys[chimney]
        for (i in 0 until SMOKE_PUFFS) {
            smokePuffAt(x, y, i, t, at)
            frame.lit.add(puff, at[0], at[1], at[2] / PUFF_DISC, at[3])
        }
    }
}

/** The figures' sprites: each of [WALKERS]' frames in the same order, and the dog walk's. */
class CastSprites(
    val walkers: List<WalkerFrames<Sprite>>,
    val dogWalkers: DogWalkerFrames<Sprite>,
    val bigDog: BigDogFrames<Sprite>,
    val smallDog: SmallDogFrames<Sprite>,
    val hands: WalkHands,
)

/**
 * Where each dog walker's free hand is in each of their frames, from their feet, in px (x, y pairs):
 * the stride's seven poses, standing, and for the second walker the three waves. The leads start there.
 */
class WalkHands(val first: FloatArray, val second: FloatArray)

/** The hands of the two dog walkers for people drawn [scale] big, made from the same pose lists as their frames. */
fun walkHands(scale: Float): WalkHands {
    fun hands(who: Villager, poses: List<PersonPose>) = FloatArray(poses.size * 2).also {
        poses.forEachIndexed { i, pose ->
            val (x, y) = personHand(who, pose)
            it[i * 2] = x * scale
            it[i * 2 + 1] = y * scale
        }
    }
    return WalkHands(hands(DOG_WALKERS.first, LEAD_POSES), hands(DOG_WALKERS.second, LEAD_POSES + WAVE_POSES))
}

/**
 * Adds the villagers who are out at [t] seconds, furthest first. [cast] remembers who is in the
 * scene: someone whose hours ([Walker.hours]) start or end joins or leaves only while off the picture.
 */
fun walkersInto(frame: MotionFrame, sprites: List<WalkerFrames<Sprite>>, cast: VillageCast, hours: VillageHours, t: Double) {
    for (i in WALKERS.indices) walkerInto(frame, sprites, cast, hours, t, i)
}

/** Adds walker [i] if they're out at [t] seconds (see [walkersInto]). */
fun walkerInto(frame: MotionFrame, sprites: List<WalkerFrames<Sprite>>, cast: VillageCast, hours: VillageHours, t: Double, i: Int) {
    val at = frame.scratch
    val walker = WALKERS[i]
    walkerAt(walker, t, at)
    if (!cast.settle(i, offPicture(at[0]), hours in walker.hours)) return
    val frames = sprites[i]
    val sprite = if (at[1] > 0f) frames.standing else frames.stride[strideFrame(at[2].toDouble())]
    frame.lit.addFacing(sprite, onPixel(at[0]), walker.lane, flipped = walker.speed < 0)
}

/**
 * Adds the dog walk at [t] seconds, if it's out: the two walkers, the two dogs with their shadows, and
 * the leads between them ([MotionFrame.leads]). It's out in the morning, afternoon and evening.
 */
fun dogWalkInto(frame: MotionFrame, sprites: CastSprites, cast: VillageCast, hours: VillageHours, t: Double) {
    val walk = frame.dogWalk.at(t)
    if (!cast.settle(WALKERS.size, offPicture(walk.x, WALK_REACH), hours != VillageHours.NIGHT)) return
    val x = onPixel(walk.x)
    val moving = walk.stop == WalkStop.NONE
    val wag = tailFrame(sin(t * WAG_RATE).toFloat())
    // The walkers: which frame each shows is also which hand position its lead starts from.
    val first = if (moving) strideFrame(walk.strides) else STANDING
    val second = when {
        moving -> strideFrame(walk.strides + 0.5)
        walk.stop == WalkStop.SNOWMAN -> STANDING + 1 + tailFrame(sin(t * WAVE_RATE).toFloat())
        else -> STANDING
    }
    val walkers = sprites.dogWalkers
    val secondSprite = if (second > STANDING) walkers.waving[second - STANDING - 1] else walkers.second.frame(second)
    frame.lit.addFacing(secondSprite, x + SECOND_WALKER_X, WALK_Y - 2, flipped = false)
    frame.lit.addFacing(walkers.first.frame(first), x + FIRST_WALKER_X, WALK_Y, flipped = false)
    // The dogs, drawn facing left in the sheet, walk to the right.
    val scale = personScale(WALK_Y)
    val big = sprites.bigDog
    val bigSprite = when (walk.stop) {
        WalkStop.NONE -> big.walk[step(walk.strides * BIG_DOG_STRIDES, big.walk.size)]
        WalkStop.SNOWFLAKES -> big.standing[wag]
        WalkStop.SNOWMAN -> big.sniffing[wag]
    }
    frame.lit.add(big.shadow, x + BIG_DOG_X, WALK_Y + 3)
    frame.lit.addFacing(bigSprite, x + BIG_DOG_X, WALK_Y + 3, flipped = true)
    val small = sprites.smallDog
    val jump = walk.jump(scale)
    val bow = (walk.bow() * WALK_BOWS).roundToInt()
    val smallSprite = when {
        moving -> small.walk[step(walk.strides * SMALL_DOG_STRIDES + 0.3, small.walk.size)]
        walk.stop == WalkStop.SNOWFLAKES -> small.lookingUp[wag]
        bow > 0 -> small.bows[bow - 1][wag]
        else -> small.standing[wag]
    }
    frame.lit.add(small.shadow, x + SMALL_DOG_X, WALK_Y + 5)
    frame.lit.addFacing(smallSprite, x + SMALL_DOG_X, WALK_Y + 5 - jump, flipped = true)
    // The leads: from each walker's hand, sagging a little, to each dog's back.
    val bigScale = scale * BIG_DOG_SHARE
    val smallScale = scale * SMALL_DOG_SHARE
    val hands = sprites.hands
    frame.leadFrom(x + FIRST_WALKER_X + hands.first[first * 2], WALK_Y + hands.first[first * 2 + 1], WALK_Y - 30 * scale)
    frame.leadTo(x + BIG_DOG_X, x + BIG_DOG_X - BIG_CLIP.first * bigScale, WALK_Y + BIG_CLIP.second * bigScale)
    frame.leadFrom(x + SECOND_WALKER_X + hands.second[second * 2], WALK_Y - 2 + hands.second[second * 2 + 1], WALK_Y - 24 * scale)
    frame.leadTo(x + SMALL_DOG_X, x + SMALL_DOG_X - SMALL_CLIP.first * smallScale, WALK_Y + SMALL_CLIP.second * smallScale - jump)
}

// How many strides each dog takes to one of the walkers' strides, so its paws keep pace with the ground.
private const val BIG_DOG_STRIDES = 1.75
private const val SMALL_DOG_STRIDES = 2.5

// Where a lead meets each dog, in the dog's own units, facing left as it's drawn in the sheet.
private val BIG_CLIP = leadPoint(Dog.BIG)
private val SMALL_CLIP = leadPoint(Dog.SMALL)

/** Starts a lead at a hand, ([x], [y]), with its middle hanging to the height [sag]; [leadTo] finishes it. */
fun MotionFrame.leadFrom(x: Float, y: Float, sag: Float) {
    leadHandX = x
    leadHandY = y
    leadSag = sag
}

/** Adds the lead started by [leadFrom] to this frame's lines: a curve to ([x], [y]), sagging half way to [dogX]. */
fun MotionFrame.leadTo(dogX: Float, x: Float, y: Float) {
    val handX = leadHandX
    val handY = leadHandY
    val pullX = (handX + dogX) / 2
    var lastX = handX
    var lastY = handY
    for (part in 1..LEAD_PARTS) {
        val u = part.toFloat() / LEAD_PARTS
        val nextX = (1 - u) * (1 - u) * handX + 2 * u * (1 - u) * pullX + u * u * x
        val nextY = (1 - u) * (1 - u) * handY + 2 * u * (1 - u) * leadSag + u * u * y
        leads[leadValues++] = lastX
        leads[leadValues++] = lastY
        leads[leadValues++] = nextX
        leads[leadValues++] = nextY
        lastX = nextX
        lastY = nextY
    }
}

/** A walker's frame by number: the stride's seven poses, then standing. */
fun WalkerFrames<Sprite>.frame(number: Int): Sprite = if (number >= stride.size) standing else stride[number]

/**
 * Adds [count] extra pieces drifting across the picture at [t] seconds, for measuring what the TV
 * can draw: in every ten, seven snowflakes, two lights and one figure, about the real scene's mix.
 */
fun stressInto(frame: MotionFrame, sprites: MotionSprites, count: Int, t: Double) {
    val random = Mulberry(STRESS_SEED)
    for (i in 0 until count) {
        val x0 = random.next() * VILLAGE_W
        val y0 = random.next() * VILLAGE_H
        val speed = 20f + random.next() * 60f
        val x = ((x0 + t * speed) % VILLAGE_W).toFloat()
        when (i % 10) {
            0 -> frame.lit.add(sprites.cast.walkers[i / 10 % WALKERS.size].standing, x, y0)
            1, 2 -> frame.lights.add(sprites.lights[i % sprites.lights.size], x, y0, alpha = 0.3f + 0.7f * random.next())
            else -> frame.plain.add(sprites.flakes[i % sprites.flakes.size], x, ((y0 + t * speed) % VILLAGE_H).toFloat(), alpha = 0.8f)
        }
    }
}

private const val STRESS_SEED = 97
private const val FAR_PIECES = 32
private const val FAR_LIGHTS = 16
private const val FAR_GLASS = 4
private const val LEAD_PARTS = 4 // Straight pieces to a lead's curve.

private val STANDING = STRIDE_SWINGS.size // A walker's standing frame, after the stride's poses.
private const val WAVE_RATE = 9.0 // How fast a hand waves, radians a second.
private const val SMOKE_RATE = 0.11 // A puff lives 1 / 0.11 seconds.
private const val SMOKE_SCALE = 0.5f
private const val SMOKE_WIND = 0.4f
