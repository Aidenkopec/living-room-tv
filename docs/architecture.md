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
- Even with the key consumed, Android starts Google's launcher on its own, about 100 ms after the key goes down.
- Living Room starts 20–60 ms after that and usually ends on top. Sometimes Google's launcher flashes first.
- Rarely, Google's launcher starts itself a second time, about 120 ms after Living Room, and stays. This has been seen on the first Home press after several idle minutes.
- `scripts/check-tv.sh` catches it, and a second press always opens Living Room.
- There's no fix from inside an app short of becoming the HOME app, which is the trade-off this design deliberately avoids.

## Rendering on a slow GPU

**The skies are painted, not animated.**
- `homeSkyScene` turns a time and a weather code into a small, comparable value. It only changes when the minute or the weather does.
- Each new scene is painted once, on `Dispatchers.Default`, into one `ImageBitmap` at the screen's own density (1920 × 1080 px).
- Compose then draws that bitmap as is. One picture is held, or two while the next one is painted.

**The home photo is composited, not layered.**
- The photo is Mount Rundle, CC BY-SA. An offline tool (`launcher/photoprep`) cuts its sky away along the ridge and stores the result in a layers PNG: sky, water, high rock and ripple as four channels.
- At paint time, `composeLand` blends the land into the sky picture row by row, from the bottom up, so the lake can mirror sky rows that haven't been changed yet. The land is graded for the sun's height and the weather.
- The function works on a small `PixelRows` interface. The app passes bitmap rows and the tests pass arrays, so the tested code is the code that runs.

**The dock never re-lays out.**
- All tiles are laid out once at the tallest size, in one place.
- Each frame, `graphicsLayer` scales and translates them from a single animated focus position. Resizing them each frame dropped frames on this TV.
- Because the tiles overlap until drawn, Android's focus search can't find neighbours, so the dock handles Left and Right itself.

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

## Test switches

The switches set a pretend time, weather, layout or number of placeholder shows, for one opening:
- They enter only through `TestSwitchActivity`.
- The manifest restricts that activity to callers holding `android.permission.DUMP`. Only the shell user has it, so only `adb shell am start` can open it; installed apps can't be granted it.
- It parses the extras (`parseTestSwitches`) and hands them over in memory. `MainActivity` reads no intent extras at all, so nothing else on the TV can change what it shows.

## Testing

- **Unit tests:** about 350 JVM tests, mostly on the pure functions. Their reference data comes from outside:
  - USNO tables for the moon;
  - NOAA and Open-Meteo for sunrise and sunset;
  - a real Open-Meteo reply for weather parsing.
- **Contrast test:** checks the text stays readable over every sky of the year, using a pure model of the painter (`backdropAt`).
- **Scripts:** tested against fakes (`adb`, `curl`, `sips`). `check-public.sh` keeps private details out of the repo.
- **On the TV:** `scripts/check-tv.sh` runs after every install and change. It checks that Home opens Living Room, HDMI 1 switches, Cast and AirPlay are advertised, YouTube plays, and Google's home still opens. Each result is recorded in [the change log](tv-change-log.md).
