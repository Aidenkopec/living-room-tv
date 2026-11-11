package tv.livingroom.home.christmas

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import tv.livingroom.home.screensaver.SkyWeather
import tv.livingroom.home.screensaver.mix

/*
 * The Christmas village: a mountain village below Mount Rundle, with a market
 * square, a big tree, a church and houses up the hill. This is the still
 * picture behind the home screen, as a list of marks on 1920 x 1080, drawn
 * back to front. The sky itself (colours, stars, sun and moon) is painted by
 * the home screen's own sky painter before these.
 */

// The three ground lines, back to front: heights at nine evenly spaced points across the picture.
private val HILL = floatArrayOf(684f, 656f, 624f, 596f, 582f, 586f, 598f, 622f, 650f)
private val MIDDLE = floatArrayOf(770f, 750f, 732f, 720f, 716f, 718f, 724f, 736f, 752f)
private val FRONT = floatArrayOf(848f, 836f, 828f, 820f, 816f, 818f, 824f, 832f, 844f)

private val BACK_ROW = listOf(
    House(700f, 120f, 84f, 0xC94F4F),
    House(868f, 110f, 78f, 0xE0B34F),
    House(1340f, 114f, 80f, 0x4F8F8A),
    House(1482f, 110f, 76f, 0xC94F4F),
    House(1626f, 118f, 84f, 0x6F86C9),
)
private val MIDDLE_ROW = listOf(
    House(250f, 172f, 114f, 0x4F8F8A),
    House(470f, 160f, 104f, 0xE0B34F),
    House(1490f, 172f, 114f, 0xC94F4F),
    House(1706f, 160f, 104f, 0xEFE6D6, door = 0xA8323A),
)
private val CHURCH_HOUSE = House(1192f, 150f, 96f, 0xEFE6D6, door = 0x8A3A2A, chimney = false)
private const val CHURCH_X = 1096f

/** How deep the snow lies on the roofs on the last day of Advent and after: a full fall. Each day before it has a little less. */
private const val ROOF_SNOW = 1.45f

/** How deep the snow lies on the roofs on day [advent] of Advent: from 0.6 on December 1 to [ROOF_SNOW] on the 24th. */
fun roofSnow(advent: Int): Float = 0.55f + advent.coerceIn(0, ADVENT_DAYS).toFloat() / ADVENT_DAYS * 0.9f

/** The windows of the village's houses (back row, then middle row, three to a house), the order they're lit in, one more a day. */
private const val WINDOWS = 27
private val WINDOW_RANK: IntArray = run {
    val random = Mulberry(42)
    val order = IntArray(WINDOWS) { it }
    for (i in WINDOWS - 1 downTo 1) {
        val j = (random.next() * (i + 1)).toInt()
        order[i] = order[j].also { order[j] = order[i] }
    }
    IntArray(WINDOWS).also { rank -> order.forEachIndexed { place, window -> rank[window] = place } }
}

/** Which of the houses' windows are lit on day [advent] of Advent: one on December 1, all of them on the 24th. */
fun litWindows(advent: Int): BooleanArray {
    val lit = (advent.coerceIn(0, ADVENT_DAYS).toFloat() / ADVENT_DAYS * WINDOWS).roundToInt()
    return BooleanArray(WINDOWS) { WINDOW_RANK[it] < lit }
}

/** The northern lights for [scene]: only on a clear night, since cloud, rain and snow hide them as they hide the stars. */
fun villageAurora(scene: VillageScene): List<Mark> = auroraMarks(if (scene.sky.weather == SkyWeather.CLEAR) scene.look.aurora else 0f)

/**
 * The whole still village for [scene], back to front. What moves over it (snow, smoke, the lights'
 * twinkling, people) is drawn by the moving layer; the northern lights ([villageAurora]) belong to
 * the sky and are painted with it.
 */
fun villageMarks(scene: VillageScene): List<Mark> = villageBack(scene.look) + villageFront(scene)

/** Behind the village: the far range, Mount Rundle, the railway, the forest and the hill the back row stands on. */
fun villageBack(look: VillageLook): List<Mark> = villageFar(look) + villageHill(look)

/** Behind the train: the far range, Mount Rundle and the railway. */
fun villageFar(look: VillageLook): List<Mark> = Easel(look).apply {
    range(11, 580f, 175f)
    rundle(880f, 606f, 0.92f)
    railway()
}.sketch.marks

/** In front of the train, behind the village: the forest and the hill the back row stands on. */
fun villageHill(look: VillageLook): List<Mark> = Easel(look).apply {
    forest(HILL)
    ground(HILL, mix(look.snow, look.snowLow, 0.45f), mix(look.snow, look.snowLow, 0.9f))
}.sketch.marks

/** Everything that stands in front of the train: what the strip it passes behind is cut from ([STRIP_TOP] to [STRIP_BOTTOM]). */
fun inFrontOfTheTrain(scene: VillageScene): List<Mark> = inTheStrip(villageHill(scene.look) + villageFront(scene))

/** Those of [front] (marks in front of the train) that cross the strip and cover it: glows only add light, so they don't. */
fun inTheStrip(front: List<Mark>): List<Mark> =
    front.filter { it !is GlowMark && marksBounds(listOf(it)).let { b -> b.y < STRIP_BOTTOM && b.y + b.h > STRIP_TOP } }

/** The band of the picture the train, its steam and its lights pass through, top and bottom. */
const val STRIP_TOP = 400f
const val STRIP_BOTTOM = 544f

/** The village itself: the church, the houses, the square and everything standing in it. */
fun villageFront(scene: VillageScene): List<Mark> = Easel(scene.look).apply {
    val windows = (look.lights * 1.2f).coerceIn(0f, 1f)
    val today = litWindows(scene.advent)
    val roofs = roofSnow(scene.advent)
    fun lit(house: Int) = FloatArray(3) { if (today[house * 3 + it]) windows else 0f }
    church(scene.hour, scene.minute, roofs)
    BACK_ROW.forEachIndexed { i, home -> house(home, backRowY(home), lit(i), roofs) }
    for ((x, h) in listOf(600f to 150f, 790f to 120f, 958f to 132f, 1412f to 120f, 1554f to 116f, 1706f to 150f, 1790f to 120f)) {
        pine(x, groundY(HILL, x) + 34, h)
    }
    ground(MIDDLE, mix(look.snow, look.snowLow, 0.2f), mix(look.snow, look.snowLow, 0.8f))
    MIDDLE_ROW.forEachIndexed { i, home -> house(home, middleRowY(home), lit(BACK_ROW.size + i), roofs) }
    for ((x, h) in listOf(372f to 150f, 612f to 170f, 1368f to 170f, 1600f to 150f, 1850f to 250f)) pine(x, groundY(MIDDLE, x) + 44, h)
    ground(FRONT, look.snow, look.snowLow)
    sledgingHill()
    pond()
    square()
}.sketch.marks

private fun backRowY(home: House) = groundY(HILL, home.x) + 30

private fun middleRowY(home: House) = groundY(MIDDLE, home.x) + 36

/** The tops of the chimneys that smoke: every other house of the back row and of the middle row. */
val VILLAGE_CHIMNEYS: List<Pair<Float, Float>> =
    BACK_ROW.filterIndexed { i, _ -> i % 2 == 0 }.map { chimneyTop(it, backRowY(it)) } +
        MIDDLE_ROW.filterIndexed { i, _ -> i % 2 == 1 }.map { chimneyTop(it, middleRowY(it)) }

/** The railway behind the village: a stone viaduct on the left, a snowy bank the rest of the way. */
private fun Easel.railway() {
    val stone = c(0x7F8598)
    sketch.box(mix(look.snow, look.snowLow, 0.55f), 640f, 512f, VILLAGE_W - 640, 60f)
    sketch.box(stone, -20f, 512f, 690f, 13f)
    for (px in 34 until 660 step 104) sketch.box(stone, px.toFloat(), 520f, 16f, 120f)
    for (px in 34 until 560 step 104) sketch.line(stone, 9f) { arc(Ellipse(px + 60f, 566f, 46f, 46f), PI.toFloat(), 2 * PI.toFloat()) }
    sketch.roundBox(snow, Area(-20f, 508f, VILLAGE_W + 40, 6f), 3f)
}

/** The church and the house beside it. Its clock shows [hour]:[minute]. */
private fun Easel.church(hour: Int, minute: Int, roofs: Float) {
    val y = groundY(HILL, CHURCH_X) + 30
    sketch.box(c(0xEFE6D6), CHURCH_X - 30, y - 200, 60f, 200f)
    sketch.shape(c(0x5A342A), 1058f, y - 196, 1134f, y - 196, CHURCH_X, y - 300)
    sketch.line(snow, 9 * roofs) { through(1058f, y - 198, CHURCH_X, y - 304, 1134f, y - 198) }
    sketch.line(c(0xE0B34F), 4f) { through(CHURCH_X, y - 304, CHURCH_X, y - 334).through(1086f, y - 322, 1106f, y - 322) }
    sketch.glow(CHURCH_X, y - 150, 60f, WINDOW_GLOW, 0.5f * look.lights)
    sketch.disc(mix(c(0xDCD6C8), 0xFFE2A0, look.lights.coerceIn(0f, 1f)), CHURCH_X, y - 150, 16f)
    val hands = clockHands(hour, minute)
    sketch.line(ink(0x3A2A20), 2.6f) { through(CHURCH_X, y - 150, CHURCH_X + cos(hands.second) * 12, y - 150 + sin(hands.second) * 12) }
    sketch.line(ink(0x3A2A20), 3.2f) { through(CHURCH_X, y - 150, CHURCH_X + cos(hands.first) * 8, y - 150 + sin(hands.first) * 8) }
    val windows = (look.lights * 1.2f).coerceIn(0f, 1f)
    house(CHURCH_HOUSE, y, floatArrayOf(windows, windows, look.lights.coerceIn(0f, 1f)), roofs)
}

/** Where a clock's hour hand and minute hand point at [hour]:[minute], in radians clockwise from 3 o'clock. */
fun clockHands(hour: Int, minute: Int): Pair<Float, Float> {
    val turn = 2 * PI.toFloat()
    return ((hour % 12 + minute / 60f) / 12 * turn - turn / 4) to (minute / 60f * turn - turn / 4)
}

/** The sledging hill at the left edge. */
private fun Easel.sledgingHill() {
    sketch.fill(sketch.fade(0f, 716f, 0f, 890f, Stop(0f, mix(look.snow, WHITE, 0.12f)), Stop(1f, look.snow))) {
        moveTo(-10f, 714f).quadTo(116f, 742f, 244f, 886f).lineTo(-10f, 886f).close()
    }
}

/** The skating pond in the near left corner, with its string of lights. */
private fun Easel.pond() {
    val x = 142f
    val y = 990f
    sketch.oval(snow, Ellipse(x, y, 134f, 50f))
    sketch.fill(sketch.fade(0f, y - 40, 0f, y + 40, Stop(0f, c(0xB7D6EE)), Stop(1f, c(0x8FB4DC)))) { oval(Ellipse(x, y, 118f, 40f)) }
    for (i in 0 until 3) {
        sketch.line(ink(0xFFFFFF, 0.35f), 1.5f) {
            arc(Ellipse(x - 60 + i * 56, y + (i % 2) * 8 - 4, 46f, 10f, 0.1f * i), 0.2f + i, 2.6f + i)
        }
    }
    lights(POND_STRING)
}

/**
 * The market square: the strings of lights, the big tree, lamps, stalls, the post box, the snowman, the snowball fight's wall,
 * pines and the brazier. The people who stand at the stalls and the brazier are on the moving layer, so they come and go as
 * everyone else does.
 */
private fun Easel.square() {
    sketch.oval(ink(look.snowLow, 0.45f), Ellipse(1000f, 820f, 190f, 13f))
    for (string in SQUARE_STRINGS) lights(string)
    // Back to front, by where each thing stands.
    bigTree(TREE_X, TREE_Y, TREE_H, treeBrightness(look.lights))
    lamp(1410f, 830f, 150f)
    stall(730f, 830f, 190f, intArrayOf(0xD43A3A, 0xFFF4E6))
    stall(1240f, 830f, 190f, intArrayOf(0x2F8A5A, 0xFFF4E6))
    lamp(560f, 832f, 150f)
    postBox()
    snowman(1118f, 838f, 0.4f)
    pine(1640f, groundY(FRONT, 1640f) + 12, 120f)
    pine(330f, groundY(FRONT, 330f) + 12, 110f)
    snowWall()
    brazier()
}

/** The snow wall the snowball fight's left-hand child hides behind (the wall stays when they've gone in). */
private fun Easel.snowWall() {
    sketch.roundBox(snow, SNOW_WALL, 10f)
    sketch.roundBox(ink(look.snowLow, 0.5f), Area(SNOW_WALL.x, SNOW_WALL.y + 18, SNOW_WALL.w, 8f), 4f)
}

/** Where the snow wall stands. */
val SNOW_WALL = Area(390f, 820f, 52f, 26f)

// The strings of fairy lights: four across the square, meeting at the big tree's top, and one over the pond.
private val SQUARE_STRINGS = listOf(
    LightString(560f, 668f, 985f, 528f, sag = 30f, count = 15),
    LightString(985f, 528f, 1410f, 666f, sag = 30f, count = 15, shift = 2, phase = 2f),
    LightString(250f, 630f, 560f, 668f, sag = 22f, count = 10, shift = 1, phase = 4f),
    LightString(1410f, 666f, 1706f, 642f, sag = 22f, count = 10, shift = 3, phase = 5f),
)
private val POND_STRING = LightString(14f, 912f, 270f, 914f, sag = 12f, count = 8, radius = 4f)

// The big tree: its foot, and how tall it is.
private const val TREE_X = 985f
private const val TREE_Y = 818f
private const val TREE_H = 350f

/** Every light in the village that twinkles: the strings' bulbs, the big tree's, and its star. */
val VILLAGE_TWINKLES: List<Twinkle> = (SQUARE_STRINGS + POND_STRING).flatMap { stringTwinkles(it) } + treeTwinkles(TREE_X, TREE_Y, TREE_H)

/** The red post box where letters to Santa go. */
private fun Easel.postBox() {
    sketch.roundBox(c(0xC9302C), Area(841f, 788f, 22f, 48f), 6f)
    sketch.box(c(0x7A1C1C), 845f, 802f, 14f, 3f)
    sketch.box(c(0xF0C24A), 841f, 812f, 22f, 3f)
    sketch.roundBox(snow, Area(838f, 782f, 28f, 9f), 4.5f)
}

/** The chestnut brazier in the near right corner. Its fire is low in the quiet night; the moving layer adds its flicker. */
private fun Easel.brazier() {
    val x = BRAZIER_X
    val y = BRAZIER_Y
    val fire = brazierFire(look) * BRAZIER_LOW
    sketch.line(c(0x1F2028), 4f) { through(x - 16, y - 26, x - 22, y).through(x + 16, y - 26, x + 22, y) }
    sketch.roundBox(c(0x2A2A30), Area(x - 24, y - 52, 48f, 30f), 6f)
    sketch.glow(x, y - 54, 120f, 0xFF8A3A, 0.6f * fire)
    sketch.oval(mix(0x5A2A1A, 0xFFB14A, fire), Ellipse(x, y - 52, 21f, 6f))
}

/** Where the brazier stands (the middle of its foot). */
const val BRAZIER_X = 1772f
const val BRAZIER_Y = 968f

/** How strong the brazier's fire is in [look], 0 to 1: a quarter of itself in the quiet night. */
fun brazierFire(look: VillageLook): Float = 1 - 0.75f * look.quiet

/** The brazier's flicker swings between this share of its fire and all of it; the still picture has the low end. */
const val BRAZIER_LOW = 0.6f
