package tv.livingroom.photoprep

/**
 * Mount Rundle from the First Vermilion Lake (docs/home-photo.md), cropped to the top 16:9 and
 * sized to 1920 × 1080. The guides, cloud stretches and floors were set by hand, each checked
 * zoomed in. The eye level was found by matching the photo's skyline to the
 * real terrain (Copernicus heights): there the photo looks along bearing 120°, 55° wide.
 */
val RUNDLE = CutSettings(
    band = 118..393, // Rows 0.22 to 0.73 of the half-size copy.
    guides = listOf(
        // The left treeline: under the distant haze and the cloud bottoms.
        Guide(0, 297, 3), Guide(40, 298, 3), Guide(80, 300, 3), Guide(112, 300, 3), Guide(122, 293, 6),
        Guide(140, 274, 300), Guide(740, 304, 300), // Free over the left hill and Rundle itself.
        // Rundle's lower right flank and the right hill, under the pink clouds.
        Guide(762, 310, 5), Guide(790, 318, 5), Guide(820, 328, 5), Guide(850, 334, 5), Guide(880, 330, 5),
        Guide(910, 324, 5), Guide(960, 314, 6),
    ),
    cloudColumns = listOf(0..729, 1655..1919), // Over the dark forest at the left and the right hill.
    floor = listOf(Point(1518, 603), Point(1550, 618), Point(1575, 630), Point(1600, 643), Point(1612, 650)),
    exact = listOf(Point(1612, 650), Point(1635, 661), Point(1654, 673)), // The flank continued into the trees.
    eyeRow = 747.24f,
    shoreRow = 752.1f,
    landNeverAbove = 300, // DockSkyLayout.ARC's top: 150 dp, 2 px per dp.
    summitRows = 380..405, // The summit is expected near row 390.
)
