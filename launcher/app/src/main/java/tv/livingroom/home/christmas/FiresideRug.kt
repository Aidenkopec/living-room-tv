package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * What's on the rug, for one frame: the two dogs (lying, or up on their feet
 * for a moment), the ball, Santa's bones and parcel once he's left them, and
 * the blue present, drawn back to front by where each meets the floor, as the
 * mockup sorts them. Plain Kotlin, so it runs in JVM tests.
 */

/** The things on the rug, in the order they're numbered while being sorted. */
private const val BIG = 0
private const val SMALL = 1
private const val BALL = 2
private const val SMALL_BONE = 3
private const val BIG_BONE = 4
private const val PARCEL = 5
private const val PRESENT = 6
private const val RUG_THINGS = 7

/** The dogs, the ball, the bones, the parcel and the blue present at [FiresideNow.t] into [frame]'s rug (and their lights). */
fun rugInto(frame: FiresideFrame, sprites: FiresideSprites) {
    val now = frame.now
    val acts = frame.acts
    dogsAt(now, acts)
    val keys = frame.rugKeys
    val order = frame.rugOrder
    keys[BIG] = acts.big.y
    keys[SMALL] = acts.small.y
    keys[BALL] = acts.ball.y + 6
    keys[SMALL_BONE] = BONES[0].second
    keys[BIG_BONE] = BONES[1].second
    keys[PARCEL] = SANTAS_PARCEL.y
    keys[PRESENT] = BLUE_PRESENT.y
    for (i in 0 until RUG_THINGS) order[i] = i
    for (i in 1 until RUG_THINGS) { // Seven things: an insertion sort, with no list made.
        val item = order[i]
        var j = i - 1
        while (j >= 0 && keys[order[j]] > keys[item]) {
            order[j + 1] = order[j]
            j--
        }
        order[j + 1] = item
    }
    for (i in 0 until RUG_THINGS) {
        when (order[i]) {
            BIG -> dogInto(frame, sprites, Dog.BIG)
            SMALL -> dogInto(frame, sprites, Dog.SMALL)
            BALL -> ballInto(frame, sprites)
            SMALL_BONE, BIG_BONE -> boneInto(frame, sprites, order[i] - SMALL_BONE)
            PARCEL -> parcelInto(frame, sprites)
            PRESENT -> presentInto(frame, sprites)
        }
    }
}

/** Both dogs and the ball at [now]: at home, woken by a visit or watching the mantel, then whatever moment is playing. */
internal fun dogsAt(now: FiresideNow, acts: RugActs) {
    val st = now.visit
    val visiting = !st.isNaN()
    // The small dog lifts his head while a gingerbread person walks the mantel, easing up as it sets off and down as it goes.
    val ginger = now.t - now.gingerAt
    val watching = if (now.gingerbread) GINGER_WATCH * smooth(minOf(ginger, GINGER_LENGTH - ginger).toFloat() / 0.4f) else 0f
    acts.small.atHome(SMALL_DOG_RUG, max(if (visiting) smallDogAwake(st) else 0f, watching), if (visiting) dogsWag(st) else 0f)
    acts.big.atHome(BIG_DOG_RUG, if (visiting) dogsAwake(st) else 0f, if (visiting) dogsWag(st) else 0f)
    acts.ball.atRest()
    now.moment?.let { momentInto(it.moment, it.dog, now.t - it.start, acts) }
}

/** How far the small dog lifts his head to watch a gingerbread person on the mantel. */
private const val GINGER_WATCH = 0.8f

private fun dogInto(frame: FiresideFrame, sprites: FiresideSprites, dog: Dog) {
    val act = frame.acts.of(dog)
    val lying = if (dog == Dog.BIG) sprites.bigDog else sprites.smallDog
    if (act.roll > 0f && dog == Dog.SMALL) {
        rollInto(frame, sprites, act)
    } else if (act.up < STANDS_FROM) {
        lyingInto(frame, lying, act, sprites)
    } else {
        standingInto(frame, if (dog == Dog.BIG) sprites.bigStanding else sprites.smallStanding, lying, act)
    }
}

/** A dog rising past this is drawn on its feet. */
private const val STANDS_FROM = 0.4f

/**
 * A lying dog: breathing, stirring, its head lifted at the neck as it wakes, its paws paddling in a dream; [flatten]ed to that
 * share of its height as it rolls over.
 */
internal fun lyingInto(frame: FiresideFrame, parts: RestingSprites, act: DogAct, sprites: FiresideSprites, flatten: Float = 1f) {
    val dog = parts.dog
    val s = parts.at.scale
    val t = frame.now.t
    val sleep = frame.sleep
    sleepAt(dog, t, sleep)
    val base = frame.turns[0].reset().translate(act.x, act.y).scale(if (act.flip) -s else s, s * (1 + act.up * RISE) * flatten)
    val part = frame.turns[1]
    val q = frame.rug
    val wag = max(sleep.wag, act.wag * abs(sin(t * 11 + sleepPhase(dog))).toFloat()).coerceIn(0f, 1f)
    val twitch = if (act.paddle > 0f) sin(t * 17).toFloat() * 8 * act.paddle else sleep.twitch
    q.addThrough(parts.shadow, base, s)
    q.addThrough(parts.tails[(wag * (parts.tails.size - 1)).roundToInt()], base, s)
    q.addThrough(parts.body, part.set(base).scale(1f, sleep.breath), s)
    q.addThrough(parts.front, base, s)
    q.addThrough(parts.hindPaw, part.set(base).translate(-twitch * 0.8f, 0f), s) // The hind paw goes the other way: running.
    q.addThrough(parts.frontPaw, part.set(base).translate(twitch, 0f), s)
    val head = when {
        act.awake > 0.5f -> headFor(act)
        sleep.ear > 0f -> HEAD_EAR
        else -> HEAD_ASLEEP
    }
    q.addThrough(parts.heads[head], part.set(base).translate(-64f, -28f).rotate(act.awake * 0.42f + act.head).translate(64f, 28f), s)
    q.addThrough(parts.collar, base, s)
    val light = BACK_LIGHT * frame.now.light
    if (light > 0.01f) frame.over.addThrough(parts.backLight, part.set(base).scale(1f, sleep.breath), s, light)
    if (act.awake < 0.3f) zsInto(frame.over, if (act.paddle > 0.05f) sprites.visit.wuf else sprites.z, parts, act, t)
}

/** How much taller a lying dog gets as it starts to rise, at the point it's drawn standing. */
private const val RISE = 0.35f

private fun headFor(act: DogAct): Int = if (act.pant) HEAD_PANT else HEAD_AWAKE

/** Three z's (or "wuf"s, in a dream) floating up from a lying dog's head and fading. */
private fun zsInto(quads: SpriteQuads, z: Sprite, parts: RestingSprites, act: DogAct, t: Double) {
    val s = parts.at.scale
    val away = if (act.flip) -1f else 1f // They drift back over the dog, away from its face.
    val headX = act.x - 96 * s * away
    for (i in 0 until 3) {
        val a = (t * 0.28 + i / 3.0 + sleepPhase(parts.dog)).mod(1.0).toFloat()
        val size = (16 + a * 18) * s
        val x = headX + away * a * 40 * s + sin(a * 6) * 8
        quads.add(z, x, act.y - (88 + a * 90) * s - size * 0.35f, size / Z_SIZE, sin(a * PI).toFloat() * 0.7f)
    }
}

/**
 * A dog on its feet: its pieces placed by [DogRig] for how it stands (rising, bowing, walking or galloping, its head turned),
 * hopping off the floor by [DogAct.hop], its tail wagging side to side.
 */
private fun standingInto(frame: FiresideFrame, parts: StandingSprites, lying: RestingSprites, act: DogAct) {
    val dog = lying.dog
    val s = parts.scale
    val rig = frame.rig
    rig.up = ((act.up - STANDS_FROM) / (1 - STANDS_FROM)).coerceIn(0f, 1f)
    rig.bow = act.bow
    rig.head = act.head
    rig.stride = if (act.walking) act.walk else Float.NaN
    rig.gallop = act.gallop
    rig.pose(dog)
    val base = frame.turns[0].reset().translate(act.x, act.y - act.hop).scale(if (act.flip) -s else s, s)
    val at = frame.turns[1]
    val q = frame.rug
    q.addThrough(parts.shadow, at.set(base).translate(0f, act.hop / s), s)
    val wag = sin(frame.now.t * 13 + (if (dog == Dog.BIG) 0 else 2)).toFloat() * act.wag
    val tail = parts.tails[((wag.coerceIn(-1f, 1f) + 1) / 2 * (STAND_TAIL_FRAMES - 1)).roundToInt()]
    val head = lying.heads[headFor(act)]
    for (part in RigPart.entries) {
        val sprite = when (part) {
            RigPart.TAIL -> tail
            RigPart.HEAD -> head
            else -> parts.parts[part.ordinal] ?: continue
        }
        q.addThrough(sprite, at.set(base).translate(rig.x(part), rig.y(part)).rotate(rig.turn(part)), s)
        if (part == RigPart.TORSO) {
            val light = STANDING_BACK_LIGHT * frame.now.light
            if (light > 0.01f) frame.over.addThrough(parts.backLight, at, s, light)
        }
    }
}

/** The fire's light along a standing dog's back, at a full fire. */
private const val STANDING_BACK_LIGHT = 0.36f

private fun ballInto(frame: FiresideFrame, sprites: FiresideSprites) {
    val ball = frame.acts.ball
    frame.rug.add(sprites.ballShadow, ball.x, BALL_Y)
    frame.rug.addTurned(sprites.ball, ball.x, ball.y, 1f, ball.turn - BALL_TURN)
}

/** Bone [i] (0 the small dog's) on the rug, once Santa has tossed it there. */
private fun boneInto(frame: FiresideFrame, sprites: FiresideSprites, i: Int) {
    val now = frame.now
    if (now.visitStart.isNaN() || now.t < now.visitStart + boneLands(i)) return
    val (x, y, turn) = BONES[i]
    val from = frame.rug.count
    frame.rug.addTurned(sprites.visit.bone, x, y, 1f, turn)
    inTheRoomsDark(frame, from, x, y)
}

/**
 * Darkens [frame]'s rug pieces since [from] by the dark at the room's edges at ([x], [y]), as the still things around them
 * have it painted over them.
 */
private fun inTheRoomsDark(frame: FiresideFrame, from: Int, x: Float, y: Float) {
    val look = frame.now.look ?: return
    frame.rug.dimFrom(from, 1 - edgeDark(look, x, y))
}

/** Santa's parcel under the tree, with a little bounce as it lands. */
private fun parcelInto(frame: FiresideFrame, sprites: FiresideSprites) {
    val now = frame.now
    if (now.visitStart.isNaN()) return
    val b = now.t - (now.visitStart + PARCEL_LANDS)
    if (b < 0) return
    val bounce = if (b < 0.4) abs(sin(b / 0.4 * PI)).toFloat() * 14 * (1 - b.toFloat() / 0.4f) else 0f
    val p = SANTAS_PARCEL
    val from = frame.rug.count
    val at = frame.turns[0].reset().translate(p.x, p.y - bounce)
    frame.rug.addThrough(sprites.visit.parcelBox, at)
    frame.rug.addThrough(sprites.visit.parcelLid, at.translate(0f, -p.h))
    inTheRoomsDark(frame, from, p.x, p.y - p.h / 2)
}

/**
 * The blue present: shut, it gives a little wobble every few seconds; popped (by the small dog's sniff or by Left), its lid
 * flies up, a spring shoots out with a star on top, and confetti flies.
 */
private fun presentInto(frame: FiresideFrame, sprites: FiresideSprites) {
    val now = frame.now
    val t = now.t
    val a = t - now.popAt
    val open = presentOpen(a)
    val v = sprites.visit
    val p = BLUE_PRESENT
    val q = frame.rug
    val from = q.count
    if (open > 0.05f) {
        val up = open * (SPRING_H + sin(a * 9).toFloat() * 16 * exp(-a).toFloat())
        val top = p.y - p.h
        q.addStretched(v.spring, p.x, top, 1f, up / SPRING_H)
        q.addTurned(v.popStar, p.x, top - up - 14, 1f, sin(a * 6).toFloat() * 0.3f)
        frame.glows.add(sprites.glows.star, p.x, top - up - 14, 70f / GLOW_REACH, 0.7f * open)
        val spread = minOf(1.0, a / 0.7).toFloat()
        val fade = (1.6f - a.toFloat()).coerceIn(0f, 1f)
        for (i in 0 until 16) {
            val angle = i / 16.0 * 2 * PI
            val d = spread * (70 + (i % 4) * 26)
            q.add(
                v.confetti[i % v.confetti.size],
                p.x + cos(angle).toFloat() * d,
                900 + sin(angle).toFloat() * d * 0.8f + (a * a * 60).toFloat(),
                1f,
                fade,
            )
        }
    }
    val shut = !(a >= 0 && a < POP_LENGTH)
    val wobble = if (shut && t.mod(WOBBLE_EVERY) < 0.6) sin(t * 18).toFloat() * 0.7f else 0f
    val at = frame.turns[0].reset().translate(p.x, p.y).rotate(wobble * 0.12f)
    q.addThrough(v.blueBox, at)
    q.addThrough(v.blueLid, at.translate(-p.w * 0.2f * open, -p.h - open * p.h * 0.55f).rotate(-open * 0.5f))
    inTheRoomsDark(frame, from, p.x, p.y - p.h / 2)
}

/** How often the shut blue present wobbles, seconds. */
private const val WOBBLE_EVERY = 6.0
