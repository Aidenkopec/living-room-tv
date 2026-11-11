# Architecture

Living Room is one Android app (`launcher/app`, package `tv.livingroom.home`) for one TV:
- a 2021 Hisense Google TV;
- Android 12 (API 31);
- 32-bit `armeabi-v7a`;
- about 1.8 GB of RAM;
- a GPU that's slow at full-screen gradients.

Those limits shaped most of the decisions below.

## Components

| Component | Kind | Job |
|---|---|---|
| `HomeKeyService` | Accessibility service (`flagRequestFilterKeyEvents`) | Catches the remote's Home key and opens `MainActivity` (see below). |
| `MainActivity` | Activity, `singleTask`, `LEANBACK_LAUNCHER` | The home screen. Reloads apps, inputs and usage on every resume, then shows one of two layouts. |
| `SettingsActivity` | Activity, not exported | Layout, weather, hidden tiles, screensaver options, "switch back to Google's home". |
| `NowPlayingListener` | Notification listener | Follows other apps' media sessions for Continue watching. It never reads notifications. |
| `ScreensaverService` | `DreamService` | The screensaver. Compose runs in it with its own lifecycle and saved-state owners. |
| `ScreensaverPreviewActivity` | Activity, not exported | The screensaver on demand, from Settings or a test switch. |
| `TestSwitchActivity` | Activity, needs `android.permission.DUMP` | The only entry for test switches (see below). |

**Feature packages.** Everything else is grouped by feature, and each feature splits pure logic from Android code:

| Package | Pure, unit-tested | Android / Compose |
|---|---|---|
| `homekey` | `homeKeyAction`, `homeCaught` | (the service, in the root package) |
| `apps` | `appRow`, `moveKey`, `mergeOrder`, `pickGlowColours`, `inputOn` | Installed apps, HDMI-CEC inputs, usage stats |
| `homescreen` | `startIndex`, `refocusIndex`, `SeenRow`, `shownTime` | The parts both layouts share: minute clock, weather, app art, refocus |
| `livingsky` | Greeting and top-area text, `fitTitle`, card-row scrolling | The Living sky layout |
| `dock` | `dockSlots`, `dockStart`, `dockInfoLine` | The Mountains and dock layout |
| `sky` | NOAA sun, Meeus moon, `homeSkyScene`, `backdropAt`, `composeLand` | Painting the sky and the home photo |
| `watch` | `cardFrom`, `mergeCard`, `resumeSteps`, the saved-file format | Media sessions, `WatchStore`, resuming |
| `screensaver` | `saverScene`, `lateNightOffDue`, `pickPhoto` | The screensaver UI, photo loading |
| `weather` | `parseWeather`, `cornerWeather`, `weatherDue` | `WeatherSource` (Open-Meteo) |
| `testswitch` | `parseTestSwitches` | `HomeTestSwitch` (in-memory hand-off) |

The rule throughout: every decision is made by a pure function that the JVM tests can call. The Android code around it only fetches inputs and draws results.

## The Home key

Android allows one HOME app. Claiming that role would mean replacing Google's launcher, which on Google TV also handles accounts, recommendations and settings entry points. So Google's launcher stays the system default, and an accessibility service takes the key instead.

**What the service does:**
- **Home down:** consumes the key and starts a long-press timer. The service only receives down and up, with no key repeats, so it times the hold itself.
- **Home up before the timeout:** starts `MainActivity`. If that throws, it calls `GLOBAL_ACTION_HOME`, so Home never does nothing.
- **Held past the timeout:** opens Google's side panel with `GLOBAL_ACTION_NOTIFICATIONS`.

**Why this fails safe.** If the service is off, the TV is exactly stock. Android turns it off when the app is force-stopped, and Settings → "Switch back to Google's home screen" calls `disableSelf()`.

**Testing it.** On this TV, `adb shell input keyevent KEYCODE_HOME` bypasses accessibility services, so it can't test the takeover. `scripts/press-home.sh` plugs in a virtual HID consumer-control device through Android's `hid` test tool. It then presses AC Home (usage `0x0223`), the same scancode the real remote sends.

**The Home race:**
- Even with the key consumed, Android starts Google's launcher on its own, about 200 ms after the key goes down. Living Room starts on key-up, usually 20–60 ms after that, and ends on top.
- When Google's launcher has been in the background for a while (a long session in another app, the screensaver, the TV turned on with an app's shortcut button), it doesn't stop there: as it resumes it starts its own home activity again, 0.4–0.8 s later, from its own process. That lands after Living Room's start and covers it. In the logs it is a second `START … launcherx/.home.HomeActivity` from the launcher's own uid.
- So a single start can't win reliably. `HomeGuard` makes the outcome the rule instead: for 5 s after a Home press opens Living Room, the service watches window-state events from the system's home package (narrowed at runtime, resolved from the HOME intent), and if the home activity's window comes to the front it opens Living Room again. The user may see Google's launcher for a moment first.
- Any key going down ends the guard, so choosing Explore, or holding Home for the side panel, is never undone. One press re-opens Living Room at most three times, so it can't fight the launcher in a loop.
- The service logs each correction ("Google's home came to the front … ms after Home"). `scripts/check-tv.sh` checks the result.
- Becoming the HOME app would remove the race entirely; that is the trade-off this design deliberately avoids.

## Rendering on a slow GPU

**The skies are painted, not animated.**
- `homeSkyScene` turns a time and a weather code into a small, comparable value. It only changes when the minute or the weather does.
- Each new scene is painted once, on `Dispatchers.Default`, into one `ImageBitmap` at the screen's own density (1920 × 1080 px).
- Compose then draws that bitmap as is. One picture is held, or two while the next one is painted.

**The home photo is composited, not layered.**
- The photo is Mount Rundle, CC BY-SA. An offline tool (`launcher/photoprep`) shrinks the full-size crop to 1920 × 1080 with Lanczos-3, cuts its sky away along the ridge and stores the result in a layers PNG: sky, water, high rock and ripple as four channels. The land gets a light luminance-only unsharp mask (σ 0.7 px, 45%, threshold 2/255; not on sky pixels, so the skyline can't halo), which offsets the TV stretching its 1080p graphics plane onto the 4K panel, and is saved as a q95 JPEG with full-resolution chroma (4:4:4).
- At paint time, `composeLand` blends the land into the sky picture row by row, from the bottom up, so the lake can mirror sky rows that haven't been changed yet. The land is graded for the sun's height and the weather.
- The function works on a small `PixelRows` interface. The app passes bitmap rows and the tests pass arrays, so the tested code is the code that runs.

**The dock never re-lays out.**
- All tiles are laid out once at the tallest size, in one place.
- Each frame, `graphicsLayer` scales and translates them from a single animated focus position. Resizing them each frame dropped frames on this TV.
- Because the tiles overlap until drawn, Android's focus search can't find neighbours, so the dock handles Left and Right itself.
- Each banner gets a dock copy at exactly the tallest tile's pixels (208 × 117), shrunk by halving and then one filtered step. HWUI on Android 12 draws bitmaps with plain bilinear filtering and no mipmaps, so a shrink of 2× or more skips source pixels. With this copy the selected tile is drawn 1:1, and the smallest tile shrinks it 1.7×. Translations and the bar's edges are rounded to whole pixels, so the tiles aren't resampled between pixels.

**Measured after every build:**
- **Memory:** `dumpsys meminfo`, about 10 s after moving along the row. Living Room shows at 51–60 MB, against an 80 MB budget.
- **Smoothness:** `dumpsys gfxinfo`, compared with Google's launcher doing the same moves. The median frame is about 16 ms.

## Continue watching

**How a card is made:**
- Android shows other apps' media sessions only to an app with notification access. That's the listener's only purpose.
- For each session it keeps the last state. It saves a card when:
  - the show changes;
  - playback switches between playing and paused;
  - the session ends. YouTube and Apple TV drop their sessions at Home without pausing.
- `MainActivity` also saves every live session on resume.

**Per-app parsing.** Each app reports metadata differently, so `cardFrom` has a small rule for each:
- Crave puts show and episode on one line.
- Paramount+ puts the episode name in the title and the show in the subtitle.
- YouTube gives a title and a channel.

Netflix, Prime Video and Apple TV don't share a show name, so they get no cards.

**What's kept.** At most 12 shows, no more than 6 per app. Removing a show leaves a timestamped marker, so a session still paused in its app can't put the card straight back.

**Storage:**
- `WatchStore` does every read and write on one thread.
- The shows are saved through `AtomicFile`, in a versioned, tab-separated text format. v2 adds a removal column; v1 is still read.
- Each show also gets one JPEG. It's written to a temporary file and renamed, so the screen never reads half a picture.

**Resuming.** `resumeSteps` picks the best resume level each app was seen to support on the TV:
1. play the paused session;
2. open the episode by ID;
3. search for it;
4. open the app.

## Screensaver

`saverScene` decides what the screensaver shows:
- **Within 30 minutes of sunrise or sunset:** Living sky.
- **Otherwise:** one of 61 photos, a new one each minute, drifting by fractions of a pixel.
- **After 11 PM:** dimmed night photos.

Late at night it ends itself after 30 minutes. That matters because Android only puts the TV in standby when a screensaver *ends* after the idle timeout has passed.

With the Christmas theme on, the screensaver is Fireside instead (below). The minute clock and the late-night turn-off are shared by both; each does its own dimming, because the everyday one only dims its late-night photos.

## The Christmas theme

A setting (`christmas_on`, read on every resume like the layout) swaps the dock screen's sky for a drawn village and dresses the dock. The chosen layout is kept for when it's off.

**The village is data, then one picture.**
- `christmas/Ink.kt` is a small drawing vocabulary: filled outlines, lines and additive glows as plain values (`Mark`), built through a `Sketch` that can move, scale and turn what's drawn inside a block.
- `villageMarks(scene)` returns the whole scene as a list of marks on 1920 × 1080, back to front. It has no Android in it, so tests check it directly: nothing behind the clock, the window count, the church clock's hands.
- `VillageBackdrop` paints a scene once a minute off the main thread (a paint whose minute has passed is stopped), the way the skies are: the shared sky painter first (with the village's colours and a lower horizon, `SkyShape`), then the marks through `InkPainter`, then the screen's usual shades.
- `VillagePreviewTest` paints the same marks with Java2D into PNG files when `VILLAGE_PREVIEW` names a folder, so new drawing can be looked at on the computer before a build goes to the TV.

**The light is a blend of four looks.**
- `villageLook` blends a morning, an afternoon, an evening and a midnight look from the sun's elevation, how far through the daylight it is, and how late in the evening. A test walks whole days minute by minute and fails on a visible jump.
- The daylight fraction and "late" are read from the clock of the place whose sun is shown (`HomePlace.ZONE`), not the TV's, so a TV in another time zone can't pair one day's sun with another day's sunrise. Only the church clock shows the TV's time.
- The northern lights are part of the sky: painted under the moon by the shared sky painter's `underBodies` hook, and only in clear weather.
- The clock's contrast test runs over the village's sky as it does over the others.

**The dock's dressing is three pictures.**
- Snow caps, holly, a string of lights and a glow are painted once (again when the light changes) and only placed and scaled in the dock's draw phase.
- One picture per piece (about 47) cost roughly 4 ms a frame on this TV. The snow and holly now share one sheet, and the string is a single picture stretched to the bar's width; the cost fell to about 1 ms.

**What moves is sprites from one sheet, one draw call per layer.**
- The village screen is the only one that redraws on every frame. `MotionClock` advances once per frame while the activity is started (Compose stops handing out frames to a stopped activity) and is read only in draw lambdas, so a frame redraws without recomposing or laying out.
- Everything that moves is a sprite cut from one sheet (`MotionSheet.kt`), painted once at start off the main thread: flake discs, a smoke puff, soft lights, and every figure's ready-drawn poses (`Figures.kt`; the drawings are `VillagePeople.kt`, as `Ink` marks). The sheet is in the pieces' own colours; the hour's light is a colour filter applied as they're drawn (`tintTowards`), so nothing is repainted when the light changes.
- A frame's pieces are collected in `SpriteQuads` (reused arrays: corners on screen, corners in the sheet, strength) and sent to the GPU as one textured mesh (`SpritePainter`, `Canvas.drawVertices`). Measured on this TV: 800 pieces in one call cost the render thread 0.2 ms more than none, while 100 pieces at one call each cost 2 ms more. The village is three calls a frame (six while the train is out): lights (added), tinted pieces (smoke, people, dogs), snow.
- The moving layer and the dock's two extra layers (the bulbs' twinkling under the tiles; snow caps, holly, loose snow and the rider over them) are `graphicsLayer`s of their own, so a new frame re-records only those few calls.
- Where things are is a pure function of the time: `flakeAt`, `smokePuffAt`, `Twinkle.strength`, `walkerAt`, `DogWalk.at`. The still picture has every bulb at its dimmest and the layer only adds light. Two things carry state from frame to frame, both plain classes stepped in the draw phase and tested on the JVM: `VillageCast` (who is in the scene: someone whose hours start or end joins or leaves only while off the picture) and `DockRide` (the rider's bound from tile to tile, each tile's depth of snow, and the loose snow a landing throws up).
- The village keeps its own hours (`villageHours`, on the same clock as its light): who is out in the morning, afternoon and evening, and nobody at night.
- The rest of the village's life (`VillageLife.kt`: the snowball fight, sledgers, carol singers, skaters, the brazier's flicker, steam off the stalls, the fox, gingerbread people, the letter to Santa) follows the mockup's timelines as functions of the time; `villageFrameInto` fills a frame with all of it, and the Mac preview calls the same function. Groups that stay in one place (`VillageCast.settleInPlace`) only come or go when the scene starts afresh, so nobody vanishes in view. Every figure goes into the tinted mesh back to front by where it stands (`DepthOrder`, an insertion sort of reused arrays), since one mesh has no depth of its own. Figures behind something in the still picture (a stallholder behind a counter, the child behind the snow wall) are painted into the sheet already cut away where it hides them.
- Rare things (`VillageEvents.kt`: the train, the sleigh after dark, shooting stars on clear nights) come once in each slot of the motion clock's time, at a moment picked from the slot's number (`EventSlots`), so the gaps between them are bounded with no state kept.
- The train passes behind the village. Moving pieces can only be drawn over the still picture, so the train and its steam are drawn first (`MotionFrame.far`), then a strip of the picture where the forest, houses and church stand in front of the line is drawn over them again. The strip is cut from the finished minute's picture (`paintStrip`: the band copied, then kept only where the front's marks cover it), so it matches it pixel for pixel; it's 1920 × 144 and painted and replaced with the picture (`VillagePicture`). The sleigh, which can't go behind the village either, fades in once it's clear of the church's steeple.
- The windows of the village's houses light one more each day from December 1 to 24, in the mockup's shuffled order (`litWindows`), and the roofs' snow deepens (`roofSnow`); on other dates all are lit.
- Sheets are packed tallest piece first (`SheetMaker.build`), which wastes far less of each row: the village's sheet with everything is 1024 × 1340 and Fireside's fell from 1022 × 1778 to 1017 × 1272.
- The frame cost is read on the TV from `dumpsys gfxinfo … framestats` (missed refreshes and the main and render threads' time per frame): once the screen redraws all the time, gfxinfo's percentiles include a frame's wait for the display and stop saying anything about the work.

**Fireside, the Christmas screensaver, is built the same way.**
- `firesideScene` is the village's look at that minute seen from indoors: the look carries a room tint, a fire strength and how dark it is (`VillageLook.room`, `roomK`, `fire`, `dark`), and the window's sky is the shared sky scene with the sun and moon on a path inside the window (`WINDOW_ARC`). Late night (the screensaver's own 11 PM to 5 AM) turns the fire to embers and the tree's lights to a slow breathing.
- The still picture (`roomWallMarks`, the window's view through the shared sky painter and `windowLandMarks`, then `firesideMarks`) is painted once a minute off the main thread and the replaced one recycled. The dark at the room's edges is part of it, so lights added over it are dimmed by `edgeDark` at their place.
- What moves comes from Fireside's own sheet (`FiresideSheet.kt`: flame tongues, logs, embers, steam, candle flames, the stockings, the two dogs in parts lying and standing, Santa in parts, the sleigh, the small props, glows) and is drawn as up to fourteen meshes a frame, in order (`FiresideFrame` in `FiresideMotion.kt`): added lights; the fire's tongues, clipped to the fireplace's arch; the candles and steam; tinted room pieces; embers; inside the arch (clipped) Santa's glow and the chimney's contents in Santa's lighter tint; Santa on the hearth; the rug (dogs, ball, bones, parcel, the blue present, sorted by their floor line); z's and back lights; the window's snow and sleigh (placed and clipped to the glass) and their added light; soot and things in flight; and added light over everything (firelight, sparkles, sparks). Empty meshes cost nothing. Flames are tongues stretched and leaned per frame (`SpriteQuads.addPlaced`); the sleeping dogs breathe by stretching their body piece upward from the floor.
- Jointed figures (the standing dogs, Santa) are placed with `Turn2d`, a reusable affine transform built up like a canvas's (`translate`, `rotate`, `scale`, each inside the last), and `SpriteQuads.addThrough`, which carries a piece's box through it, shears included. A standing dog is a skeleton (`DogRig.kt`): rigid pieces (torso, neck, head, tail, and per leg upper arm and forearm, or thigh, gaskin and hock, then the paw) placed for a pose by dropping and tipping the torso, putting each paw where the gait says (a four-beat walk, or a rotary gallop with the body pitching and leaving the floor) and reaching it with a two-bone solve of fixed bone lengths, elbows bending back and stifles forward. `DogRig.placed` holds each piece's turn and place and makes no objects, so Fireside poses it every frame; the village draws the same pieces into ready-made frames (`dogMarks`). The drawings are `DogLooks.kt` and `DogHeads.kt`. The small dog on his back is a set of pieces of its own (`FiresideRoll.kt`); a roll is shown side on as the lying dog flattening, then the belly-up one filling out.
- Timelines are pure functions of seconds, ported from the mockup: `santaAt` (Santa's pose through the visit, with `visitFire`, `dogsAwake`, `roomShake`), `momentInto` (each dog moment's placement into `RugActs`: the roll-over, the zoomies' laps round an oval paced by its arc length, the dream), and the remote's effects (`snowBoost`, `fireFlare`, `presentOpen`, `gingerbreadAt`, `treeShow`). `FiresidePlan` decides, from a seed per showing, when Santa comes (1 to 10 minutes in, then once in each further hour) and when the dogs' moments start (2 to 5 minutes apart, none during a visit, Santa waiting for the dogs, only the dream late at night). It's planned as the clock moves on, since what may happen depends on the hour then. `nowAt` works out each frame's state (`FiresideNow`) before anything is placed.
- The remote: while Fireside shows, `ScreensaverService` is interactive and hands OK and the arrows to a `FiresideRemote` catcher that Fireside sets and clears; every other key, and every key once Fireside has gone, calls `wakeUp()`. `HomeKeyService` still takes Home first. Presses are stamped with the dream's clock in `FiresidePresses`. The Fireside preview takes the same keys.
- The room shakes by moving the whole room's layer (`graphicsLayer` translation, slightly enlarged only while it shakes so no edge shows): nothing is redrawn for it.
- The still picture has the dark at the room's edges painted in; a moving piece there (the blue present, Santa's parcel) is darkened to match by its vertices' colour (`SpriteQuads.dimFrom`), which `drawVertices` multiplies with the sheet.
- Santa's gifts stay until his next visit, across screensavers: once a visit has left them, a flag in the app's preferences (`FiresideGifts`) starts the next showing with them in place (`FiresidePlan(giftsLeft)`).
- A press on Fireside holds the late-night turn-off until the screen-off time has passed since it (`turnOffHeldByPress`): Android only sends the TV to standby when a screensaver ends after that.
- The dream has its own `MotionClock`: the home screen's stops while it's covered.
- If either picture can't be made (any throwable, out of memory included) before anything shows, the everyday screensaver takes over (`showsFireside`).
- The stockings' names come from a local asset that git ignores (`assets/christmas/stockings.txt`, read by `loadStockingNames`), with placeholders when it's missing; words are drawn as `WordMark`s, so they're painted into the sheet with everything else.

## Test switches

The switches set a pretend time or date, weather, layout, the Christmas theme (for the screensaver too), a number of placeholder shows or a number of extra moving pieces (to measure what the TV can draw), for one opening:
- They enter only through `TestSwitchActivity`.
- The manifest restricts that activity to callers holding `android.permission.DUMP`. Only the shell user has it, so only `adb shell am start` can open it; installed apps can't be granted it.
- It parses the extras (`parseTestSwitches`) and hands them over in memory. `MainActivity` reads no intent extras at all, so nothing else on the TV can change what it shows.

## Testing

- **Unit tests:** about 500 JVM tests, mostly on the pure functions. Their reference data comes from outside:
  - USNO tables for the moon;
  - NOAA and Open-Meteo for sunrise and sunset;
  - a real Open-Meteo reply for weather parsing.
- **Contrast test:** checks the text stays readable over every sky of the year, using a pure model of the painter (`backdropAt`).
- **Scripts:** tested against fakes (`adb`, `curl`, `sips`). `check-public.sh` keeps private details out of the repo.
- **On the TV:** `scripts/check-tv.sh` runs after every install and change. It checks that Home opens Living Room, HDMI 1 switches, Cast and AirPlay are advertised, YouTube plays, and Google's home still opens. Each result is recorded in [the change log](tv-change-log.md).
