package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.sin

/*
 * The ready-drawn frames of the village's other life, painted into the moving
 * sheet beside the walkers': the snowball fight, the sledgers, the carollers,
 * the skaters, the fox, the train, the gingerbread people, the sleigh and the
 * small things (notes, the letter, sparkles, a shooting star, the brazier's
 * flicker). Each figure's frames share one box, as the walkers' do.
 */

/** A sledger's frames: sitting on the sledge (still, then waving at [WAVE_ARMS]) and walking back up with the rope (the stride). */
class SledgerFrames<T>(val riding: List<T>, val climbing: List<T>) {
    fun <R> map(f: (T) -> R) = SledgerFrames(riding.map(f), climbing.map(f))
}

/** Every frame of the village's other life, by what it is (see [lifeFrames] for each list's order). */
class LifeFrames<T>(
    val snowballers: List<List<T>>,
    val sledgers: List<SledgerFrames<T>>,
    val carollers: List<List<T>>,
    val skaters: List<List<T>>,
    val learner: List<T>,
    val fox: List<T>,
    val gingerbread: List<T>,
    val sleigh: List<T>,
    val standing: List<T>,
    val things: LifeThings<T>,
) {
    fun <R> map(f: (T) -> R) = LifeFrames(
        snowballers.map { it.map(f) },
        sledgers.map { it.map(f) },
        carollers.map { it.map(f) },
        skaters.map { it.map(f) },
        learner.map(f),
        fox.map(f),
        gingerbread.map(f),
        sleigh.map(f),
        standing.map(f),
        things.map(f),
    )
}

/** The village's single small pieces: the sledge and its rope, the two notes, the train and its lights, and the sparkly things. */
class LifeThings<T>(
    val sledge: T,
    val rope: T,
    val notes: List<T>,
    val train: T,
    val trainLights: T,
    val letter: T,
    val star: T,
    val streak: T,
    val fireGlow: T,
) {
    fun <R> map(f: (T) -> R) =
        LifeThings(f(sledge), f(rope), notes.map(f), f(train), f(trainLights), f(letter), f(star), f(streak), f(fireGlow))
}

/** The snowball fight's two children: who they are and where they stand (feet), the second facing left. */
val SNOWBALLERS = listOf(
    Villager(coat = 0x2F6F8F, hat = 0xC9413F, kid = true) to (392f to 842f),
    Villager(coat = 0xE0A83A, hat = 0x4F8F5A, skin = 0xC98D66, kid = true) to (566f to 846f),
)

/** The throwing arm's angles a throw is shown at, from behind the head to thrown, then resting; the last frame ducks. */
val THROW_ARMS = floatArrayOf(-1f, 0.17f, 1.33f, 2.5f)
const val REST_ARM = 0.3f

/** How big the snowball fight's children are drawn. */
val SNOWBALL_SCALE = personScale(844f)

/** The two sledgers. */
val SLEDGERS = listOf(Villager(coat = 0xC9413F, hat = 0xF0B83A, kid = true), Villager(coat = 0x7A4F9A, hat = 0x4F8F5A, kid = true))

/** The largest a sledger is drawn (at the foot of the hill): their frames are painted at this size and drawn smaller up the hill. */
val SLEDGER_SCALE = personScale(874f) * 0.95f

/** The carollers under the lamp: who, and where their feet are. */
val CAROLLERS = listOf(
    Villager(coat = 0x2B3F6A, hat = 0xC9413F) to (462f to 850f),
    Villager(coat = 0x7A4F9A, hat = 0xF4EFE6, skin = 0xC98D66) to (492f to 853f),
    Villager(coat = 0xC9413F, hat = 0x4F8F5A, kid = true) to (520f to 856f),
    Villager(coat = 0xE0A83A, hat = 0x2F6F8F, skin = 0xC98D66, kid = true) to (544f to 853f),
)

/** How wide a caroller's mouth is open in each of their frames, 0 to 1. */
val SINGING = floatArrayOf(0f, 0.5f, 1f)

/** The two skaters' coats; how big they're drawn; how many steps their glide is shown in. */
val SKATER_COATS = intArrayOf(0x3F8FD8, 0xC9413F)
const val SKATER_SCALE = 0.86f
const val SKATER_STEPS = 6

/** The child learning to skate, and how big they're drawn. */
val LEARNER = Villager(coat = 0xE0A83A, hat = 0x7A4F9A, kid = true)
const val LEARNER_SCALE = 0.5f
const val LEARNER_ARMS_OUT = 2.5f

/** How big the fox is drawn, and how many steps of its trot are painted. */
const val FOX_SCALE = 0.72f
const val FOX_STEPS = 6

/** How big the biggest gingerbread person is drawn (the others are drawn smaller from the same frames). */
const val VILLAGE_GINGER_SCALE = 0.34f

/** Snowball fight children: the left one stands behind the snow wall, so their frames are cut away where it hides them. */
private fun snowballFrames(maker: SheetMaker): List<List<Int>> = SNOWBALLERS.mapIndexed { i, (who, feet) ->
    val poses = THROW_ARMS.map { PersonPose(carry = Carry.THROW, arm = it) } +
        PersonPose(carry = Carry.THROW, arm = REST_ARM) +
        PersonPose(carry = Carry.THROW, arm = REST_ARM, duck = 1f)
    val frames = poses.map { pose -> if (i == 0) behindTheWall(who, pose, feet) else personMarks(who, pose) }
    maker.addGroups(listOf(frames), SNOWBALL_SCALE).single()
}

/** [who] in [pose] standing at [feet], with whatever [SNOW_WALL] hides cut away, in [personMarks]' units. */
private fun behindTheWall(who: Villager, pose: PersonPose, feet: Pair<Float, Float>): List<Mark> = Sketch().apply {
    val s = SNOWBALL_SCALE
    val left = (SNOW_WALL.x - feet.first) / s
    val top = (SNOW_WALL.y - feet.second) / s
    val corner = WALL_CORNER / s
    // Everywhere but the wall: its top-left corner rounded off, as the wall's own is.
    val outline = floatArrayOf(
        -FAR, -FAR, FAR, -FAR, FAR, top,
        left + corner, top, left + corner * 0.3f, top + corner * 0.3f, left, top + corner,
        left, FAR, -FAR, FAR,
    )
    clipped({ polygon(*outline) }) { person(who, pose) }
}.marks

private const val FAR = 400f
private const val WALL_CORNER = 10f

/** The sledgers: sitting (cut at the coat's hem, so the sledge seems to hold them), and climbing back up facing the hill. */
private fun sledgerFrames(maker: SheetMaker): List<SledgerFrames<Int>> = SLEDGERS.map { who ->
    val sitting = (listOf(PersonPose()) + WAVE_ARMS.map { PersonPose(carry = Carry.WAVE, arm = it) }).map { pose ->
        Sketch().apply { clipped({ rect(-FAR, -FAR, 2 * FAR, FAR + SEAT_CUT) }) { person(who, pose) } }.marks
    }
    val climbing = STRIDE_SWINGS.map { personMarks(who, PersonPose(it, Carry.LEAD)) }
    val g = maker.addGroups(listOf(sitting, climbing), SLEDGER_SCALE)
    SledgerFrames(g[0], g[1])
}

/** Where a sitting sledger is cut, in their own units above their feet: just above the coat's hem. */
const val SEAT_CUT = -20f

/** Adds every frame of the village's other life to [maker]. */
fun lifeFrames(maker: SheetMaker): LifeFrames<Int> {
    val snowballers = snowballFrames(maker)
    val sledgers = sledgerFrames(maker)
    val carollers = CAROLLERS.map { (who, feet) ->
        val frames = SINGING.map { personMarks(who, PersonPose(carry = Carry.BOOK, sing = it)) }
        maker.addGroups(listOf(frames), personScale(feet.second)).single()
    }
    val skaters = SKATER_COATS.map { coat ->
        val frames = List(SKATER_STEPS) { skaterMarks(coat, sin(it * 2 * PI.toFloat() / SKATER_STEPS)) }
        maker.addGroups(listOf(frames), SKATER_SCALE).single()
    }
    val learning = STRIDE_SWINGS.map { personMarks(LEARNER, PersonPose(it, Carry.WAVE, arm = LEARNER_ARMS_OUT)) } +
        WAVE_ARMS.map { personMarks(LEARNER, PersonPose(carry = Carry.WAVE, arm = it, duck = FALLEN)) }
    val learner = maker.addGroups(listOf(learning), LEARNER_SCALE).single()
    val fox = maker.addGroups(listOf(List(FOX_STEPS) { foxMarks(it * 2 * PI.toFloat() / FOX_STEPS) }), FOX_SCALE).single()
    val ginger = List(2 * GINGER_WALK_FRAMES) {
        val armUp = if (it < GINGER_WALK_FRAMES) 0f else 1f
        gingerbreadMarks(2f * (it % GINGER_WALK_FRAMES) / GINGER_WALK_FRAMES, armUp, 0f)
    }
    val gingerbread = maker.addGroups(listOf(ginger), VILLAGE_GINGER_SCALE).single()
    val sleigh = maker.addGroups(listOf(List(GALLOP_FRAMES) { sleighMarks(it * 2 * PI.toFloat() / GALLOP_FRAMES) }), 1f).single()
    val standing = BYSTANDERS.map { maker.addFrames(listOf(bystanderMarks(it)), it.scale).single() }
    val things = LifeThings(
        sledge = maker.addFrames(listOf(sledgeMarks(SLEDGER_SCALE * SLEDGE_LONG))).single(),
        rope = maker.add(ropeMarks(), Area(0f, -1f, ROPE_LENGTH, 2f)),
        notes = listOf(false, true).map { maker.addFrames(listOf(noteMarks(it))).single() },
        train = maker.addFrames(listOf(trainMarks())).single(),
        trainLights = maker.addFrames(listOf(trainLightMarks())).single(),
        letter = maker.addFrames(listOf(letterMarks())).single(),
        star = maker.addFrames(listOf(sparkleStarMarks())).single(),
        streak = maker.add(streakMarks(), Area(-2f, -2f, STREAK_LENGTH + 2, 4f)),
        fireGlow = maker.add(fireGlowMarks(), Area(-FIRE_GLOW - 1, -FIRE_GLOW - 1, 2 * FIRE_GLOW + 2, 2 * FIRE_GLOW + 2)),
    )
    return LifeFrames(snowballers, sledgers, carollers, skaters, learner, fox, gingerbread, sleigh, standing, things)
}

/**
 * Someone who stands in one place warming their hands: [who], feet at ([x], [y]), [scale] times life size, facing left if
 * [flip]. Anything below [cut] (their own units above their feet) is cut away: a counter or the brazier is in front of it.
 */
class Bystander(val who: Villager, val x: Float, val y: Float, val scale: Float, val flip: Boolean = false, val cut: Float? = null)

/**
 * The stallholders behind the two counters (whose tops are 49 px above the stalls' feet), the brazier's keeper behind it, and
 * the two people warming their hands at it, in that order.
 */
val BYSTANDERS = listOf(
    Bystander(Villager(coat = 0x4F8F5A, hat = 0xC9413F), 704f, 826f, 0.62f, cut = -73f),
    Bystander(Villager(coat = 0xB5543A, hat = 0xF0B83A), 1214f, 826f, 0.62f, cut = -73f),
    Bystander(Villager(coat = 0x5A3A26, hat = 0xC9413F, skin = 0xE2B08C), BRAZIER_X + 2, BRAZIER_Y - 16, 0.58f, cut = -62f),
    Bystander(Villager(coat = 0x2F6F8F, hat = 0xF4EFE6), 1724f, 972f, 0.6f),
    Bystander(Villager(coat = 0xC9413F, hat = 0x2B3F6A, skin = 0x8F5F43), 1822f, 974f, 0.6f, flip = true),
)

/** How many of [BYSTANDERS] keep the stalls and the brazier (out all but the night); the rest warm their hands from noon. */
const val KEEPERS = 3

private fun bystanderMarks(b: Bystander): List<Mark> = Sketch().apply {
    val pose = PersonPose(carry = Carry.WARM)
    val cut = b.cut
    if (cut == null) person(b.who, pose) else clipped({ rect(-FAR, -FAR, 2 * FAR, FAR + cut) }) { person(b.who, pose) }
}.marks

/** How far a fallen learner is squashed down (a [PersonPose.duck]), and how long a sledge is for its rider, as in the mockup. */
const val FALLEN = 1.4f
const val SLEDGE_LONG = 1.6f
