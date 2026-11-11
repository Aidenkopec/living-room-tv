package tv.livingroom.home.christmas

/*
 * The one sheet every moving piece of the village is cut from: snowflakes,
 * smoke, the lights' glows, the villagers, the dogs on their walk and the dog
 * on the dock. It is painted once, in the pieces' own colours (the hour's
 * light is a tint added as they're drawn), on the village's 1920 x 1080
 * units, one pixel to a unit.
 */

/** The sprites of the moving sheet, by what they are. */
class MotionSprites(
    /** A white disc for each depth of snow (far, middle, near), about the size its flakes are drawn. */
    val flakes: List<Sprite>,
    /** A pale disc for chimney smoke. */
    val puff: Sprite,
    /**
     * Soft lights to add to the picture: one with a bright middle for each fairy-light colour ([BULBS]),
     * a warm one for the tree's bulbs ([WARM_LIGHT]), and a plain warm glow ([HALO_LIGHT]).
     */
    val lights: List<Sprite>,
    /** The villagers and the dog walk. */
    val cast: CastSprites,
    /** The dog that rides the dock. */
    val rider: RiderFrames<Sprite>,
    /** The rest of the village's life: children, carollers, skaters, the fox, the train, the sleigh and their small things. */
    val life: LifeFrames<Sprite>,
)

/** The moving sheet's layout and its sprites. */
class MotionSheet(val sheet: Sheet, val sprites: MotionSprites)

/** The radius each depth of snow's disc is painted at (far, middle, near): a little bigger than its biggest flake. */
val FLAKE_DISCS = floatArrayOf(2.6f, 4f, 9.5f)

/** The radius the smoke's disc is painted at, and the reach of a bulb's light and of its bright middle. */
const val PUFF_DISC = 28f
const val BULB_REACH = 22.5f
const val BULB_CORE = 5f

/** The tree's warm bulbs, and where their light and the plain glow come after the five fairy-light colours in [MotionSprites.lights]. */
const val WARM_BULB = 0xFFD88A
const val WARM_LIGHT = 5
const val HALO_LIGHT = 6

/** The plain glow's reach. */
const val HALO_REACH = 48f

fun motionSheet(): MotionSheet {
    val maker = SheetMaker()
    val flakes = FLAKE_DISCS.map { r -> maker.add(Sketch().apply { disc(ink(0xFFFFFF), 0f, 0f, r) }.marks, square(r + 1)) }
    val puff = maker.add(Sketch().apply { disc(ink(0xEEF0F8), 0f, 0f, PUFF_DISC) }.marks, square(PUFF_DISC + 1))
    val lights = (BULBS.toList() + WARM_BULB).map { colour ->
        val light = Sketch().apply {
            glow(0f, 0f, BULB_REACH, colour, LIGHT_STRENGTH)
            disc(ink(colour, 0.6f), 0f, 0f, BULB_CORE)
        }
        maker.add(light.marks, square(BULB_REACH + 1))
    } + maker.add(Sketch().apply { glow(0f, 0f, HALO_REACH, 0xFFE08A, LIGHT_STRENGTH) }.marks, square(HALO_REACH + 1))
    val walkers = WALKERS.map { walkerFrames(maker, it) }
    val walkScale = personScale(WALK_Y)
    val dogWalkers = dogWalkerFrames(maker, walkScale)
    val bigDog = bigDogFrames(maker, walkScale * BIG_DOG_SHARE)
    val smallDog = smallDogFrames(maker, walkScale * SMALL_DOG_SHARE)
    val rider = riderFrames(maker)
    val life = lifeFrames(maker)
    val sheet = maker.build(SHEET_WIDTH)
    val cast = CastSprites(
        walkers = walkers.map { frames -> frames.map { sheet.sprites[it] } },
        dogWalkers = dogWalkers.map { sheet.sprites[it] },
        bigDog = bigDog.map { sheet.sprites[it] },
        smallDog = smallDog.map { sheet.sprites[it] },
        hands = walkHands(walkScale),
    )
    return MotionSheet(
        sheet,
        MotionSprites(
            flakes = flakes.map { sheet.sprites[it] },
            puff = sheet.sprites[puff],
            lights = lights.map { sheet.sprites[it] },
            cast = cast,
            rider = rider.map { sheet.sprites[it] },
            life = life.map { sheet.sprites[it] },
        ),
    )
}

/** A box [half] either side of (0, 0). */
private fun square(half: Float) = Area(-half, -half, 2 * half, 2 * half)

private const val SHEET_WIDTH = 1024
private const val LIGHT_STRENGTH = 0.55f // A light's strength at its middle, as the still picture's glows are made.
