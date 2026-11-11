package tv.livingroom.home.christmas

/*
 * Fireside's own sheet: every piece that moves in the room, painted once in its
 * own colours (the room's light is a tint added as the room's pieces are drawn;
 * flames, glows and steam are light and are drawn as they are). Pieces placed
 * in the room are drawn on the room's 1920 x 1080 units, one pixel to a unit;
 * the dogs at the size they lie on the rug.
 */

/**
 * A lying dog's sprites, for [dog] lying [at] its place: see [RestingPart]. [tails] go from still to fully up; [heads] are asleep
 * (the ear still and twitched), awake, and awake panting, numbered by [HEAD_ASLEEP] and the rest (the
 * standing dog's head is the same drawing, so it uses these too).
 */
class RestingSprites(
    val dog: Dog,
    val at: RugPlace,
    val shadow: Sprite,
    val tails: List<Sprite>,
    val body: Sprite,
    val backLight: Sprite,
    val front: Sprite,
    val hindPaw: Sprite,
    val frontPaw: Sprite,
    val heads: List<Sprite>,
    val collar: Sprite,
)

/** Where each of a dog's heads comes in [RestingSprites.heads]. */
const val HEAD_ASLEEP = 0
const val HEAD_EAR = 1
const val HEAD_AWAKE = 2
const val HEAD_PANT = 3

/**
 * A standing dog's sprites, painted at [scale]: each [RigPart]'s piece by its ordinal (the tail's and the head's are null: the
 * tail is one of [tails], from -1 to 1 in [STAND_TAIL_FRAMES] steps, and the head one of the lying dog's heads), its shadow,
 * and the fire's light along its back.
 */
class StandingSprites(val scale: Float, val shadow: Sprite, val parts: List<Sprite?>, val tails: List<Sprite>, val backLight: Sprite)

/** How many steps of a standing dog's wag are painted, from one side to the other. */
const val STAND_TAIL_FRAMES = 5

/** Santa's sprites, by [SantaPart], painted at [SANTA_PAINTED]. */
class SantaSprites(val parts: List<Sprite>) {
    operator fun get(part: SantaPart): Sprite = parts[part.ordinal]
}

/** Everything else the visit and the remote add to the room. See the marks each is painted from. */
class VisitSprites(
    val sack: Sprite,
    val parcelBox: Sprite,
    val parcelLid: Sprite,
    val blueBox: Sprite,
    val blueLid: Sprite,
    val bone: Sprite,
    val sparkle: Sprite,
    /** The four [SOOT_GREYS], then a smoke wisp, then a clump of snow, each [PUFF_R] across. */
    val puffs: List<Sprite>,
    val grit: Sprite,
    val footprints: Sprite,
    val sleighs: List<Sprite>,
    val spring: Sprite,
    val popStar: Sprite,
    val confetti: List<Sprite>,
    val spark: Sprite,
    val gingerWalk: List<Sprite>,
    val gingerWave: List<Sprite>,
    val cane: Sprite,
    val stockingParcel: Sprite,
    val wuf: Sprite,
)

/** Where the smoke wisp and the snow clump come in [VisitSprites.puffs], after the soot. */
const val WISP = 4
const val CLUMP = 5

/** Soft round lights to add, each at full strength in the middle and [GLOW_REACH] across, by colour. */
class RoomGlows(val fire: Sprite, val candle: Sprite, val star: Sprite, val bulb: Sprite, val berry: Sprite)

/** Fireside's sprites, by what they are. */
class FiresideSprites(
    /** The village's three depths of snowflake (see [FLAKE_DISCS]). */
    val flakes: List<Sprite>,
    /** A light with a bright middle for each of [BULBS] and then [ROOM_BULB] ([ROOM_LIGHT]), as the village's are made. */
    val lights: List<Sprite>,
    val glows: RoomGlows,
    /** A flame's tongue in each of the fire's three colours, [TONGUE_H] high and [TONGUE_HW] either side of its foot. */
    val tongues: List<Sprite>,
    /** The logs, the embers and the stockings, placed where they are in the room (draw them at (0, 0)) or by their own anchor. */
    val logs: Sprite,
    val embers: Sprite,
    val steam: Sprite,
    val candleFlame: Sprite,
    /** Hung from their hooks at (0, 0), left to right. */
    val stockings: List<Sprite>,
    val bigDog: RestingSprites,
    val smallDog: RestingSprites,
    val bigStanding: StandingSprites,
    val smallStanding: StandingSprites,
    /** The small dog on his back, by [BellyPart] ordinal. */
    val belly: List<Sprite>,
    val ball: Sprite,
    val ballShadow: Sprite,
    val z: Sprite,
    val santa: SantaSprites,
    val visit: VisitSprites,
) {
    /** The lights, then the plain glows (fire, candle, star, bulb, berry): what [Twinkle.light] numbers. */
    val lightList: List<Sprite> = lights + listOf(glows.fire, glows.candle, glows.star, glows.bulb, glows.berry)
}

class FiresideSheet(val sheet: Sheet, val sprites: FiresideSprites)

/** Where [ROOM_BULB]'s light comes in [FiresideSprites.lights]. */
const val ROOM_LIGHT = 5

/** How far a glow reaches, as painted. */
const val GLOW_REACH = 64f

/**
 * The size a flame's tongue is painted at, at a full fire: its height and half its foot's width. A poked or relighting fire
 * draws it up to about twice as tall, a little softer.
 */
const val TONGUE_H = 250f
const val TONGUE_HW = 40f

/** The fire's three colours, outer to inner. */
val FLAME_COLOURS = intArrayOf(0xFF5A1F, 0xFF9A2A, 0xFFE27A)

/** How much bigger a person's stocking is than a dog's (what's in it is painted at that size). */
const val PERSON_STOCKING = 1.28f

/** The stockings, left to right: their colour, their size, and whether they're a dog's (smaller, with a paw print). */
data class StockingLook(val colour: Int, val scale: Float, val paw: Boolean)

val STOCKING_LOOKS = listOf(
    StockingLook(0xC9302C, PERSON_STOCKING, paw = false),
    StockingLook(0x3F7FB8, 1f, paw = true),
    StockingLook(0xD1A03F, 1f, paw = true),
    StockingLook(0x2F7A4A, PERSON_STOCKING, paw = false),
)

fun firesideSheet(names: StockingNames): FiresideSheet {
    val maker = SheetMaker()
    val flakes = FLAKE_DISCS.map { r -> maker.add(Sketch().apply { disc(ink(0xFFFFFF), 0f, 0f, r) }.marks, around(r + 1)) }
    val lights = (BULBS.toList() + ROOM_BULB).map { colour ->
        val light = Sketch().apply {
            glow(0f, 0f, BULB_REACH, colour, 0.55f)
            disc(ink(colour, 0.6f), 0f, 0f, BULB_CORE)
        }
        maker.add(light.marks, around(BULB_REACH + 1))
    }
    val glows = listOf(0xFF7A2A, 0xFFBE5A, 0xFFE08A, ROOM_BULB, 0xFF5A4F).map { colour ->
        maker.add(Sketch().apply { glow(0f, 0f, GLOW_REACH, colour, 1f) }.marks, around(GLOW_REACH + 1))
    }
    val tongues = FLAME_COLOURS.map { maker.add(tongueMarks(it), Area(-TONGUE_HW * 1.4f, -TONGUE_H - 2, TONGUE_HW * 2.8f, TONGUE_H + 4)) }
    val logs = addPlaced(maker, logMarks())
    val embers = addPlaced(maker, emberMarks())
    val steam = addPlaced(maker, steamMarks())
    val candleFlame = addPlaced(maker, candleFlameMarks())
    val stockings = STOCKING_LOOKS.mapIndexed { i, look -> addPlaced(maker, stockingMarks(look, names.names[i])) }
    val big = restingFrames(maker, Dog.BIG, BIG_DOG_RUG.scale)
    val small = restingFrames(maker, Dog.SMALL, SMALL_DOG_RUG.scale)
    val bigStand = standingFrames(maker, Dog.BIG, BIG_DOG_RUG.scale)
    val smallStand = standingFrames(maker, Dog.SMALL, SMALL_DOG_RUG.scale)
    val belly = BellyPart.entries.map { maker.addFrames(listOf(bellyMarks(it)), SMALL_DOG_RUG.scale).single() }
    val ball = addPlaced(maker, rugBallMarks())
    val ballShadow = addPlaced(maker, rugBallShadowMarks())
    val z = maker.add(Sketch().apply { word("z", 0f, 0f, WordStyle(Z_SIZE, ink(0xFFFFFF), WordFace.SANS)) }.marks, around(Z_SIZE * 0.6f))
    val santa = SantaPart.entries.map { maker.addFrames(listOf(santaMarks(it)), SANTA_PAINTED).single() }
    val visit = visitFrames(maker)
    val sheet = maker.build(SHEET_WIDTH)
    fun s(i: Int) = sheet.sprites[i]
    fun dog(frames: RestingFrames, dog: Dog, at: RugPlace) = RestingSprites(
        dog,
        at,
        s(frames.shadow),
        frames.tails.map(::s),
        s(frames.body),
        s(frames.backLight),
        s(frames.front),
        s(frames.hindPaw),
        s(frames.frontPaw),
        frames.heads.map(::s),
        s(frames.collar),
    )
    return FiresideSheet(
        sheet,
        FiresideSprites(
            flakes = flakes.map(::s),
            lights = lights.map(::s),
            glows = RoomGlows(s(glows[0]), s(glows[1]), s(glows[2]), s(glows[3]), s(glows[4])),
            tongues = tongues.map(::s),
            logs = s(logs),
            embers = s(embers),
            steam = s(steam),
            candleFlame = s(candleFlame),
            stockings = stockings.map(::s),
            bigDog = dog(big, Dog.BIG, BIG_DOG_RUG),
            smallDog = dog(small, Dog.SMALL, SMALL_DOG_RUG),
            bigStanding = bigStand.sprites(BIG_DOG_RUG.scale, ::s),
            smallStanding = smallStand.sprites(SMALL_DOG_RUG.scale, ::s),
            belly = belly.map(::s),
            ball = s(ball),
            ballShadow = s(ballShadow),
            z = s(z),
            santa = SantaSprites(santa.map(::s)),
            visit = visit.sprites(::s),
        ),
    )
}

/** A lying dog's pieces' numbers in the sheet. */
private class RestingFrames(
    val shadow: Int,
    val tails: List<Int>,
    val body: Int,
    val backLight: Int,
    val front: Int,
    val hindPaw: Int,
    val frontPaw: Int,
    val heads: List<Int>,
    val collar: Int,
)

private fun restingFrames(maker: SheetMaker, dog: Dog, scale: Float): RestingFrames {
    fun one(part: RestingPart) = maker.addFrames(listOf(restingDogMarks(dog, part)), scale).single()
    return RestingFrames(
        shadow = one(RestingPart.SHADOW),
        tails = maker.addFrames(List(TAIL_FRAMES) { restingDogMarks(dog, RestingPart.TAIL, wag = it / (TAIL_FRAMES - 1f)) }, scale),
        body = one(RestingPart.BODY),
        backLight = one(RestingPart.BACK_LIGHT),
        front = one(RestingPart.FRONT),
        hindPaw = one(RestingPart.HIND_PAW),
        frontPaw = one(RestingPart.FRONT_PAW),
        heads = maker.addFrames(
            listOf(
                DogPose(open = false),
                DogPose(open = false, ear = 1f),
                DogPose(),
                DogPose(pant = true),
            ).map { headMarks(dog, it) },
            scale,
        ),
        collar = one(RestingPart.COLLAR),
    )
}

/**
 * A standing dog's pieces' numbers in the sheet: its shadow, its [RigPart]s in order (-1 for the tail and head), its tails,
 * its back light.
 */
private class StandingFrames(val shadow: Int, val parts: List<Int>, val tails: List<Int>, val backLight: Int) {
    fun sprites(scale: Float, s: (Int) -> Sprite) =
        StandingSprites(scale, s(shadow), parts.map { if (it < 0) null else s(it) }, tails.map(s), s(backLight))
}

private fun standingFrames(maker: SheetMaker, dog: Dog, scale: Float) = StandingFrames(
    shadow = maker.addFrames(listOf(dogShadowMarks(dog)), scale).single(),
    parts = RigPart.entries.map {
        if (it == RigPart.TAIL || it == RigPart.HEAD) -1 else maker.addFrames(listOf(rigPartMarks(dog, it)), scale).single()
    },
    tails = maker.addFrames(
        List(STAND_TAIL_FRAMES) {
            rigPartMarks(dog, RigPart.TAIL, DogPose(tail = it * 2f / (STAND_TAIL_FRAMES - 1) - 1))
        },
        scale,
    ),
    backLight = maker.addFrames(listOf(standingBackLightMarks(dog)), scale).single(),
)

/** The visit's and the remote's pieces' numbers in the sheet, in [VisitSprites]' order. */
private class VisitFrames(
    val one: List<Int>,
    val puffs: List<Int>,
    val sleighs: List<Int>,
    val confetti: List<Int>,
    val walk: List<Int>,
    val wave: List<Int>,
) {
    fun sprites(s: (Int) -> Sprite): VisitSprites {
        val o = one.map(s)
        return VisitSprites(
            sack = o[0], parcelBox = o[1], parcelLid = o[2], blueBox = o[3], blueLid = o[4], bone = o[5], sparkle = o[6],
            puffs = puffs.map(s), grit = o[7], footprints = o[8], sleighs = sleighs.map(s), spring = o[9], popStar = o[10],
            confetti = confetti.map(s), spark = o[11], gingerWalk = walk.map(s), gingerWave = wave.map(s), cane = o[12],
            stockingParcel = o[13], wuf = o[14],
        )
    }
}

private fun visitFrames(maker: SheetMaker): VisitFrames {
    val raw: (Int) -> Int = { ink(it) }
    fun box(p: Present) = Sketch().apply { giftBox(p, raw) }.marks
    fun lid(p: Present) = Sketch().apply { giftLid(p, raw) }.marks
    val one = listOf(
        sackMarks() to SACK_SCALE,
        box(SANTAS_PARCEL) to 1f,
        lid(SANTAS_PARCEL) to 1f,
        box(BLUE_PRESENT) to 1f,
        lid(BLUE_PRESENT) to 1f,
        Sketch().apply { bone(0f, 0f, 1f, 0f) }.marks to 1f,
        sparkleMarks() to 1f,
        Sketch().apply { disc(ink(0x2A2523), 0f, 0f, GRIT_R) }.marks to 1f,
        footprintMarks() to 1f,
        springMarks() to 1f,
        popStarMarks() to 1f,
        sparkMarks() to 1f,
        candyCaneMarks() to PERSON_STOCKING,
        stockingParcelMarks() to PERSON_STOCKING,
        Sketch().apply { word("wuf", 0f, 0f, WordStyle(Z_SIZE, ink(0xFFFFFF), WordFace.SANS)) }.marks to 1f,
    ).map { (marks, scale) -> maker.addFrames(listOf(marks), scale).single() }
    val puffs = (SOOT_GREYS.toList() + 0xCFC9C4 + 0xF4F7FF).map {
        maker.add(Sketch().apply { disc(ink(it), 0f, 0f, PUFF_R) }.marks, around(PUFF_R + 1))
    }
    val sleighs = maker.addFrames(List(GALLOP_FRAMES) { sleighMarks(it * 2 * kotlin.math.PI.toFloat() / GALLOP_FRAMES) })
    val confetti = BULBS.map { maker.addFrames(listOf(confettiMarks(it))).single() }
    val walk = maker.addFrames(List(GINGER_WALK_FRAMES) { gingerbreadMarks(it * 2f / GINGER_WALK_FRAMES, 0f, 0f) }, GINGER_SCALE)
    val wave = maker.addFrames(List(GINGER_WAVE_FRAMES) { gingerbreadMarks(0f, 1f, it * 2f / (GINGER_WAVE_FRAMES - 1) - 1) }, GINGER_SCALE)
    return VisitFrames(one, puffs, sleighs, confetti, walk, wave)
}

/** The sack's size in the room, and the gingerbread person's on the mantel: they're painted at it. */
const val SACK_SCALE = 0.82f
const val GINGER_SCALE = 0.5f

/** A grit speck's size as painted. */
const val GRIT_R = 5f

/** Adds [marks] as one piece in the box around them (a pixel to spare), its anchor at their own (0, 0). */
private fun addPlaced(maker: SheetMaker, marks: List<Mark>): Int = maker.addFrames(listOf(marks)).single()

/** A box [half] either side of (0, 0). */
private fun around(half: Float) = Area(-half, -half, 2 * half, 2 * half)

/** One tongue of flame, its foot's middle at (0, 0): it bulges out low down and leans in to its tip. */
private fun tongueMarks(colour: Int): List<Mark> = Sketch().apply {
    val hw = TONGUE_HW
    val hh = TONGUE_H
    fill(ink(colour)) {
        moveTo(-hw, 0f)
        cubicTo(-hw * 1.35f to -hh * 0.5f, -hw * 0.2f to -hh * 0.7f, 0f, -hh)
        cubicTo(hw * 0.25f to -hh * 0.65f, hw * 1.35f to -hh * 0.45f, hw, 0f)
        close()
    }
}.marks

/** The two logs in the grate and their cut ends, where they lie in the room. */
private fun logMarks(): List<Mark> = Sketch().apply {
    placed(1000f, 796f, turn = -0.05f) { roundBox(ink(0x4A2C1A), Area(-118f, -16f, 236f, 32f), 16f) }
    placed(1010f, 778f, turn = 0.09f) { roundBox(ink(0x4A2C1A), Area(-96f, -14f, 192f, 28f), 14f) }
    disc(ink(0x8A5C3C), 884f, 802f, 15f)
    disc(ink(0x8A5C3C), 1104f, 786f, 13f)
}.marks

/** The glowing cracks along the logs, where they lie in the room. */
private fun emberMarks(): List<Mark> = Sketch().apply {
    for (i in 0 until 6) {
        val dy = (i % 2) * 6f
        line(ink(0xFF963C), 4f) { through(910f + i * 34, 806f - dy, 926f + i * 34, 803f - dy) }
    }
}.marks

/** One curl of steam, rising from its foot at (0, 0). */
private fun steamMarks(): List<Mark> = Sketch().apply {
    line(ink(0xFFFFFF), 4f) { moveTo(0f, 0f).quadTo(9f, -14f, 0f, -28f).quadTo(-9f, -42f, 0f, -54f) }
}.marks

/** A candle's flame around its middle at (0, 0). */
private fun candleFlameMarks(): List<Mark> = Sketch().apply {
    oval(ink(0xFFD98A), Ellipse(0f, 0f, 5f, 11f))
    oval(ink(0xFFF6D8), Ellipse(0f, 3f, 2.2f, 5f))
}.marks

/** A stocking hanging from its hook at (0, 0), [look]'s colour and size, [name] stitched on the cuff. */
private fun stockingMarks(look: StockingLook, name: String): List<Mark> = Sketch().apply {
    placed(0f, 0f, scale = look.scale) {
        line(ink(0xE0B34F), 3f) { through(-14f, 4f, -14f, -12f) }
        roundBox(ink(look.colour), Area(-24f, 16f, 48f, 86f), 6f)
        roundBox(ink(look.colour), Area(-24f, 74f, 82f, 42f), 21f)
        roundBox(ink(0x000000, 0.12f), Area(6f, 30f, 18f, 60f), 4f)
        val cream = ink(0xFFF4E6)
        // The toe, square where it meets the foot and round at the tip; the heel, round at its back.
        box(cream, 32f, 86f, 13f, 30f)
        roundBox(cream, Area(32f, 86f, 26f, 30f), 15f)
        roundBox(cream, Area(-24f, 96f, 20f, 20f), 0f, bottomRadius = 10f)
        box(cream, -14f, 96f, 10f, 20f)
        if (look.paw) {
            disc(cream, 0f, 62f, 8f)
            for ((dx, dy) in listOf(-10f to 48f, 0f to 44f, 10f to 48f)) disc(cream, dx, dy, 3.6f)
        } else {
            for ((dx, dy) in listOf(-8f to 44f, 9f to 58f, -6f to 76f)) fill(cream) { star(dx, dy, 5f) }
        }
        roundBox(cream, Area(-34f, -2f, 68f, 30f), 10f)
        word(name, 0f, 14f, WordStyle(cuffTextSize(name), ink(0xB3262B), widest = CUFF_TEXT_WIDTH))
    }
}.marks

private const val SHEET_WIDTH = 1024
