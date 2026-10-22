# Living Room TV Makeover: Design

> Status: approved (2026-10-19). Phase 1 and Phase 2 done (2026-10-19); Phase 3's screensaver done (2026-10-20). Milestones 5 ("Living sky"), 6 ("Mountains and dock") and 7 ("Mount Rundle") built 2026-10-20 and 2026-10-21. Still open from Phase 3: testing the remaining apps for Continue watching. Each phase and milestone also had a step-by-step plan; those were working notes and are kept locally, not in git.
> **Decided** = you said it. **My call** = my (Claude's) suggestion; check these while reading. **Open** = still to settle.

## Why

The Hisense TV in the living room works, but it's full of ads, slow, and cluttered with apps nobody uses. The goal is a TV that feels like an Apple TV: clean, quick and easy to use, while keeping everything the TV can already do.

## Goals

- **Decided:** No ads on the home screen.
- **Decided:** A clean, modern home screen in the spirit of Apple TV, that's also useful: it knows what you were watching.
- **Decided:** Turn off tracking (Hisense viewing data, Google ad personalization).
- **Decided:** Make the TV faster by removing or turning off apps that aren't used.
- **Decided:** Keep the TV's full features (renting, trending shows, profiles, casting) one press away.
- **Decided (2026-10-19, during clean-up):** No voice control. You never use it and don't want a listening microphone. Microphone access is blocked and Google Assistant is off (change log #16, #17). Typed search on Google's home screen still works.

## Not doing (non-goals)

- **Decided:** Blocking YouTube ads. You pay for YouTube Premium.
- **Decided:** Showing the TFSA portfolio anywhere on the TV. It's the living room TV and guests would see it.
- **Decided:** A "Now playing" card for Spotify. You don't really use Spotify.

## Must not happen (anti-goals)

- **My call:** Anything breaks inputs (HDMI), casting, AirPlay, screen mirroring, YouTube Premium sign-in, renting, or system updates.
- **Decided:** Private info shows up on screen where guests can see it. (Note: show titles in "Continue watching" will be visible. That's accepted, like on any TV.)
- **My call:** The TV ends up slower or flakier than it is today.
- **My call:** A change that can't be undone. Every change gets a written "how to undo it" step.

## Constraints

- **Hardware (from the TV):** Hisense Google TV, Android 12 (API 31), MediaTek MT5896, 32-bit only (armeabi-v7a). 1.8 GB memory, about 380 MB free in normal use. Menus draw at 1080p on a 4K panel, and that stays as is. One Google profile (yours).
- **Decided (with a caveat):** No new outside services that need an account, a password or your personal data. Services the TV already uses (Google, the streaming apps) are fine. Anything live, like weather, has to come from somewhere online, so a free service with no account is allowed.
- **My call:** No "debloat" scripts. Change one thing at a time, and test after each.
- **My call:** Firmware updates from Hisense may turn apps back on. After each update, re-check the "Turn off" list.
- **My call:** The home screen app should use no more than about 80 MB of memory while showing.

## How we build it (Decided: phase by phase, no one-shot)

- **One plan per phase.** We write the Phase 1 plan, do Phase 1, check it, and only then write the Phase 2 plan. That way each plan uses what we learned in the phase before (memory numbers, which apps behave).
- **Small steps.** Each plan is a list of small steps, each with a check on the real TV before the next one starts.
- **You approve at checkpoints:** before any plan starts, and at the end of each milestone, by looking at the TV.
- **Phase 2 is split into milestones (My call),** each ending in something you can see on the TV:
  1. **Bare bones:** an app that lists your apps, opens them, and takes over the Home button. Proves the risky part (Option A) works on this TV before any polish.
  2. **The look:** the top area, tiles, real artwork, colour glow, fixed order, Explore.
  3. **Continue watching:** media sessions, the row, and the "resume" levels, tested app by app.
  4. **Extras:** weather, PlayStation 5 tile, settings screen, long-press menu.
- **Phase 3** (the screensaver) gets its own plan in the same way.
- **Milestone 5, "Living sky"** (added 2026-10-20): a new look for the home screen, after Phase 3. Its own plan too. See "Milestone 5: Living sky" below.
- **Everything is committed to git** as we go, so any step can be rolled back on the Mac side too.

## Undo and recovery (Decided: everything must be restorable)

Why this is safe: without root, ADB **cannot change the TV's system partition** (the read-only part that holds Android and Hisense's built-in apps). "Turn off" only hides a built-in app, and the original stays untouched underneath. That's why every change can be undone.

Ways back, from smallest to biggest:

1. **Undo one change:** every change goes into `docs/tv-change-log.md` with its exact undo command (re-enable an app, reinstall from the Play Store, put a setting back).
2. **Undo everything we did:** **My call:** a restore script, `scripts/restore-tv.sh`, that re-enables every app we turned off, sets the home screen and screensaver back to Google's, and resets the settings we changed to the values saved in `snapshots/`. Written and dry-run checked in Phase 1, before the first change.
3. **No Mac needed:** the "Switch back to Google's home screen" button in our app's settings. And if our app crashes, the TV falls back to Google's home screen on its own.
4. **Remove our app:** uninstalling it puts Google's home screen back. (Re-set the screensaver to Google's too; the restore script does both.)
5. **Last resort, factory reset:** Settings → System → Reset puts the TV back exactly as it came out of the box. It also erases sign-ins and installed apps, so you'd sign in again. **My call:** in Phase 1, before any change, look up and write down Hisense's button-based reset for this model, for the case where the menus can't be reached.

Keep for recovery: Developer options and network debugging stay on, and the Mac stays "always allowed". Without these, steps 1, 2 and 4 need the TV's own menus instead.

---

## Phase 1: Clean-up

Usage numbers are from the TV's own records for the past year (Oct 2025 – Oct 2026).

**Remove** = gone completely; reinstall from the Play Store to undo. **Turn off** = built in, so it can't be deleted, but turning it off hides it and stops it running; can be turned back on anytime.

### Keep (Decided)

| App | Use in past year | Notes |
|---|---|---|
| YouTube | 560 hrs | |
| Netflix | 243 hrs | |
| Paramount+ | 124 hrs | |
| Prime Video | 93 hrs | |
| Crave | 84 hrs | |
| Apple TV | 67 hrs | |
| Disney+ | 9 hrs | |
| Google TV app | never opened directly | Renting and buying through Google TV's Shop relies on it. It also runs Google's free live channels. |
| Casting (Chromecast) | 112 hrs | Must keep working. |
| TV Center (Hisense) | 50 hrs | Likely the HDMI inputs screen. |
| AirPlay | | Keep in case of iPhones or Macs. |
| Settings, Play Store, system parts | | Always. |

### Remove (Decided)

| App | Use in past year |
|---|---|
| CBC Gem | never opened |
| Discovery+ | never opened |
| Sportsnet | 3 opens, last Oct 2025 |
| Spotify | never opened; 218 hrs running in the background. **My call:** you don't use it, and it uses memory in the background. |

### Turn off (Decided)

| App | Use in past year | Notes |
|---|---|---|
| YouTube Music | never opened | |
| Play Games | never opened | |
| Hisense web browser | never opened | |
| VIDAA channels | never opened | Contains an ad-click component. |
| HiShow (Hisense) | never opened | Includes a Hisense screensaver option (not the one in use). |
| "Single listen" (Hisense) | never opened | |
| Alexa | ran briefly in background | Not used for voice control. |

### Keep, but leave off the home screen (Decided)

These do more than their names suggest. Our home screen decides what shows up, so hiding clutter doesn't need turning anything off.

| App | What it does | Decision |
|---|---|---|
| Phone connection tips | Screen mirroring from phones and laptops, and part of the AirPlay screen. Never runs in the background. | Keep. |
| Media Center | Plays USB sticks, and receives videos and photos sent from other devices on Wi-Fi (DLNA). Never runs in the background. | Keep. |
| SmartSense | Runs the whole time the TV is on (~24 MB). Handles remote buttons, the remote's backlight and input switching, and sends usage logs to Hisense. | Keep the app on. Try turning off just its log-sending part (`LogReportManagerService`) over ADB. The TV may refuse; if it does, leave it. Test the remote and inputs afterwards. |
| "Protector" (Hisense) | Not looked at yet. | Leave alone. |

### Tracking settings

- **Decided:** Turn off Hisense's viewing-data settings and Google's ad personalization in the TV's own menus.
- **Settled during clean-up (2026-10-19):** Hisense's tracking consent lives at Settings → System → About → Legal information → EULA & Privacy ("Product Improvement"). The screen-recognition (ACR) settings page doesn't exist on this Canadian software (`acr_support=false`). See `docs/tv-change-log.md`.

### How the clean-up gets done

- **My call:** Change one app at a time over ADB, and check after each one: home screen opens, an HDMI input switches, casting from a phone works, a streaming app plays.
- **My call:** Keep a written change log (`docs/tv-change-log.md`) with every change and the exact command to undo it.
- **My call:** Measure free memory before and after, so "faster" is something we can check.
- A "before" picture of the TV's apps and settings is saved in `snapshots/` (taken 2026-10-19). Check it for device IDs before committing it to git.

**Phase 1 is done when:** the restore script and factory-reset steps exist (written first), every Remove/Turn off item is done or knowingly skipped, tracking settings are off, the four checks above pass, and the before/after memory numbers are in the change log.

---

## Phase 2: The new home screen

### How it's built

- **Decided:** We build our own home screen app instead of using Projectivy. Building it is part of the fun, and Projectivy only lets you pick from its own settings (most of its styling is in the paid version).
- **Decided:** I (Claude) write the code. You steer and approve.
- **My call:** A normal Android TV app in Kotlin with Jetpack Compose for TV, targeting Android 12 (API 31) and 32-bit ARM. Built on the Mac, installed over ADB. Lives in this repo.
- **My call:** Permissions it needs, each granted once (over ADB where possible) and each reversible:
  - **Usage access**: to know which app you opened last (for the top area's fallback).
  - **Notification access**: to read what apps say is playing ("media sessions"), for Continue watching.
  - **Accessibility**: to catch the Home button (see "Taking over the Home button").

### How it works in the system

Your TV has one "home" slot: whatever opens when you press Home. Our app goes in that slot. Google's home screen stays installed like any other app, and the Explore tile opens it. Pressing Home always brings you back to ours. To undo, put Google's back in the slot.

### The look: "The shelf that remembers" (Decided)

Final mockup: kept locally, not in git.

Top to bottom:

1. **Corner:** weather, date and clock together in the top-right.
2. **Top area (big, full width):** describes whatever is selected. On power-on, the most recent show is selected and the top area reads, as one sentence: "**Good evening.** Pick up where you left off on [Crave]", then the show title large, then season/episode, then a progress bar ("13 min in"). The greeting follows the time of day. If an app didn't share what was playing, it falls back to the app name and when you last opened it.
3. **Continue watching row** (replaced by milestone 5: up to 12 shows, several per app)**:** the last show from each app, with a progress bar and the app's badge. Built up by the home screen itself and stored on the TV. **My call:** only real shows get a card. Apps that don't share what's playing are left out, so the row never repeats the app row. If there are no shows yet, the row is hidden and the app row moves up. The row may start small (only Crave is confirmed) and fill in as apps are tested.
4. **App row:** Netflix, YouTube, Paramount+, Crave, Prime Video, Apple TV, Disney+, PlayStation 5, and **Explore** always last. **Decided: fixed order** that you choose (move tiles with long-press), like Apple TV, so your thumb learns where things are. The smarts live in the top area and the Continue watching row, which already put your latest show first. **My call:** the starting order is by how much you used each app this past year (the order listed here). The row label is simply "Apps".

Behaviour:

- Selected tile or card lifts and glows. Moving to it changes the top area to match.
- Pressing OK opens it. No "OK to open" label.
- **Real app artwork:** each app's own wide TV banner, read from the TV. **My call:** if an app has no banner, show its icon on a tile coloured from that icon.
- **Background of the top area** (replaced by milestone 5's Living sky)**:** a soft glow in the selected app's colours, picked from its banner. Warmer and a little dimmer in the evening, brighter and cooler in the day. Later upgrades possible: your own images per app, or slow-motion video loops.
- **Explore selected:** top area reads "**Good evening.** Looking for something new?", title "Explore", and shows what's behind it: Trending & new, Rent or buy, Library, Profiles. OK opens Google TV's home screen.
- **HDMI tile:** named after the device plugged in, and a green dot when it's switched on. Checked 2026-10-19: the TV sees a **PlayStation 5 on HDMI 1** by name (over HDMI-CEC), including whether it's on or in standby. So the tile reads "PlayStation 5", not "HDMI 1". Inputs with nothing plugged in are hidden.
  - **Tested (milestone 4, 2026-10-19):** Android gives the console its own input (`…/HDMI100004`, "PlayStation 5") under HDMI 1's, so an app sees its name and port. Its input state reads 0 while the console is on and 1 in standby, and Android tells apps when it changes, so that's what the dot follows. The empty HDMI 2 and 3 also report "standby", which is why only devices that name themselves get a tile. OK on the tile opens TV Center on the console's input, like the remote's Input button.
- **New apps (My call):** an app you install later appears automatically at the end of the row, just before Explore.
- **Long-press OK on a tile (My call):** small menu to hide it, keep it in a fixed spot, or open the home screen's own settings.
- **Long-press OK on a Continue watching card (Decided 2026-10-19):** small menu to remove that show from the row, or open the home screen's settings. A removed show comes back only once you play something in that app again.
- **Home screen settings (My call):** a simple screen for the weather, which apps show on the row, screensaver styles and schedule, and "Switch back to Google's home screen". That last one means getting back to stock never needs the Mac.
  - **Decided (2026-10-19):** weather is Calgary, AB, with a switch to turn it off. No other cities.
  - **Decided (2026-10-19):** "Switch back" stops our app catching the Home button and opens Google's home. Our app, its saved shows and its permissions stay. To come back without the Mac: the same settings screen, or TV Settings → System → Accessibility → Living Room → on.
  - **My call:** the screensaver part of this screen comes with the screensaver, in Phase 3.
- **Readable from the couch (My call):** no text smaller than what Google's own TV guidelines recommend for 10-foot viewing.
- **Weather:** temperature and a few words. **My call:** use Open-Meteo (free, no account, no key). **Decided:** Calgary, AB.
- **Power-on (Decided):** if the TV was left on an HDMI input, it goes back to that input, so a console or cable box is right where you left it. Don't change Hisense's power-on setting. The "pick up where you left off" greeting shows when the TV was left on the home screen or an app, or whenever you press Home.
- **Screensaver:** see "The screensaver" below. (Replaces the earlier "rest mode" idea; there is now just one idle screen.)
- **Apps not on the home screen** (Media Center, Play Store, Settings, etc.): **My call:** reached through Explore, since Google's home screen has a full Apps list, and Settings through the remote's Settings button.

### Continue watching: how it works and how far it goes

Apps tell Android what's playing so the TV can show media controls. The home screen listens for that and remembers the last show per app. Tested 2026-10-19: Crave reports show, episode and position. Every app was tested in milestone 3 (table below).

Pressing a show tries these, best first:

1. **Resume exactly:** if the app is still running in the background, tell it to press play. (Crave closes its player when it comes back, so it lands one OK away instead; see below.)
2. **Open the show directly:** only Paramount+ shares the ID a link needs; its link opens the episode at your spot.
3. **Search for it:** YouTube's search finds the video by title and channel.
4. **Fallback:** open the app, where its own "continue watching" row is usually first.

#### Tested app by app (milestone 3, 2026-10-19)

What each app tells Android while a show plays, read by Living Room's listener. Show names below are examples, not what was watched.

| App | Shares | Kept after Home | Home-screen previews | Direct link | Search |
|---|---|---|---|---|---|
| **Crave** | One line, show and episode together ("Show ‑ S1 E1 Episode name"), and the position. No length, ID or picture. | Yes: paused, with the show, for at least 2 minutes. Opening Crave with a link ends it. | No session | No ID to link to | Nothing other apps can open; a search link lands on Crave's home |
| **YouTube** | Video title, channel, length, a picture. No video ID. | No: gone the moment you press Home | No session | No ID to link to | **Works:** a search link for title and channel opens the results with that video first and selected |
| **Paramount+** | Show name, episode name, length, a content ID, a web link to a picture. No season or episode number. | Paused, then marked inactive right away | No session | **Works:** opens the right episode at your spot, after Paramount+ asks "Who's watching?" | Not needed |
| **Netflix** | Nothing but "playing" or "paused" | No | (not seen) | — | — |
| **Prime Video** | Its own ID and the position, but an empty title | No: inactive at Home | No session | — | — |
| **Apple TV** | Only the length and position, no title | No: gone a second after Home | Reports "playing" with no show (no card made) | — | — |
| **Disney+** | Not tested: you're not signed in to it | | | | |

**Resume level each one reaches** (pressing its card, tested on the TV with build 18):

| App | Right after you press Home | Later (the app has closed its session) |
|---|---|---|
| **Crave** | Level 1 runs, but Crave closes its own paused player when it comes back to the front, so you land on the show's page with "Resume S1 E1" selected. One OK carries on at the exact spot. | Level 4: Crave opens ("Who's watching?", then its home, where the show is first in its Continue Watching). |
| **YouTube** | Level 3 (its session is already gone): search results with the video first and selected. One OK plays it from where you were. | Level 3, the same. |
| **Paramount+** | Level 2: "Who's watching?", then the episode plays from where you stopped. | Level 2, the same. |
| **Netflix, Prime Video, Apple TV** | No card (no show name shared). Their tiles open the app: level 4. | Level 4. |
| **Disney+** | Not tested (not signed in). A show would get the general rules: level 1 if its session is open, else level 4. | |

So **Crave, YouTube and Paramount+** get Continue watching cards. **Netflix, Prime Video and Apple TV** share no show name, so they're left out (the rule above), and their tiles show the app name and when you last opened it. Netflix, Apple TV and Disney+ also block screenshots, so you played Netflix and Apple TV for the test.

### The screensaver (Decided)

**How it works today (read from the TV 2026-10-19):** when nobody presses a button for 10 minutes and nothing is playing, Android starts the chosen screensaver app. Today that's Google's "Ambient mode", with images from Google's servers. After 20 minutes untouched, the TV goes to sleep. Any button press ends the screensaver.

**What changes:** our home screen app also provides a screensaver, and we set it as the TV's screensaver instead of Google's. Same trigger, just with our look. Undo: switch the screensaver back to Google's in Settings (or one ADB command).

**Longer screensaver (Decided 2026-10-19):** today the screensaver only shows for about 10 minutes before the TV sleeps (it starts at 10 minutes untouched, and the TV sleeps at 20). You want it to stay on much longer.
- The screensaver still **starts after 10 minutes** untouched (unchanged).
- The TV **goes to sleep after 2 hours** untouched instead of 20 minutes (Android's `sleep_timeout` setting, from 1,200,000 to 7,200,000 ms). That gives close to 2 hours of screensaver.
- **My call, late night:** after 11 PM the screensaver turns the TV off after 30 minutes instead, so it doesn't glow all night if someone falls asleep on the couch. **Tested (2026-10-20):** it works. When the screensaver ends itself with nobody pressing a key, Android puts the TV in standby (change log #59), and Home opens Living Room again after waking it.
- **Check during Phase 3:** that Hisense doesn't have its own separate "auto power off" timer that switches the TV off earlier. If it does, note it and ask before changing it. **Checked (2026-10-19):** Hisense's Sleep Timer, Auto Sleep and power timers are all Off (change log #54).
- Logged in the change log with its undo: put `sleep_timeout` back to 1,200,000 (20 minutes), or change it in Settings → System → Power & Energy.
- Cost: a TV on screensaver uses roughly the power of a bright light bulb or two. Two hours a day is about a few dollars a year. The photos change every minute, so there's no risk of the picture burning in.

- **Decided (changed 2026-10-19): Two styles, not three.** The painting Gallery and the Calgary-only Rockies journal are dropped. In their place: **"Beautiful places"**, scenic photos from around the world, including Banff.

  | Time | Style |
  |---|---|
  | Around sunrise and sunset | **Living sky** |
  | The rest of the day and evening | **Beautiful places** |
  | Late night (after 11 PM) | **Beautiful places**, night photos only, dimmed (**My call**) |

- **Living sky:** a sky drawn by the app (no photos) over a Rockies skyline. The app works out Calgary's sunrise and sunset times from the date and the city's position on the map. That's plain maths on the TV, with no internet needed. **My call:** it shows from 30 minutes before to 30 minutes after sunrise, and the same around sunset. During that hour, the colours follow the real sun as it moves (gold, pink, then deep blue and the first stars at sunset), and the real Calgary weather (clouds, rain or snow if it's happening). It has the same big clock and weather as the photos.
- **Beautiful places:** photos of the most beautiful places in the world, for example Moraine Lake and Lake Louise (Banff), Iceland's waterfalls and northern lights, the Norwegian fjords, the Swiss Alps, Patagonia, Santorini, Kyoto, Ha Long Bay, Bora Bora, Yosemite and the Grand Canyon. Banff is always part of the set.
  - **Same overlay as the "Rockies journal" mockup:** big thin clock, date and weather bottom-left. Place name with region and country bottom-right (for example "Moraine Lake · Banff National Park, Canada").
  - Each photo drifts slowly, then cross-fades to the next. **You decided (2026-10-20):** a new photo every minute (my first call was every 3 minutes). **My call:** no repeats in the same day.
  - Photos are tagged day, dusk or night, and the screensaver picks ones that match the time of day.
- **On screen in both styles:** big thin clock, date and Calgary weather. **My call:** no hourly forecast, to keep it clean.
- **Late-night dimming (My call):** after 11 PM, night photos only (stars, northern lights, cities at night), much darker, with just a soft clock.
- **When each style shows (My call, Phase 3 plan):** late night is 11 PM to 5 AM, and it comes first. Then Living sky within 30 minutes of sunrise or sunset; dusk photos 30 to 90 minutes from them; day photos while the sun is up; night photos (not dimmed) after dark until 11 PM. The late-night turn-off happens once the screensaver has shown for 30 minutes and it's late night.
- **"Show is waiting" reminder (My call):** off by default. Guests would see it, and you can turn it on later. **Decided (2026-10-19):** not built in Phase 3; it can be added later as its own step.
- **Where the images come from:** not from Google. We choose about 60 freely licensed photos once from Wikimedia Commons, download them on the Mac, and install them with the app. Around 30–40 MB, stored on the TV. Credits are kept in the app. Nothing is fetched while the screensaver runs, except the weather. You can add your own photos later. The photo list is approved by you before anything is downloaded, and downloading follows the safeguards in Phase 3. **Decided (2026-10-19):** the list (`docs/screensaver-images.md`, 61 photos) is approved; the originals are downloaded and checked by fingerprint; the resized copies stay out of git (the credits list is committed).
- Mockup: styles 1 and 3 (kept locally). Style 2 (Gallery) is no longer used.

### Keeping the TV's full features (Decided)

- **Renting and buying:** Google TV's Shop, through Explore.
- **Trending, popular and new shows:** Google TV's For You, through Explore.
- **Profiles and Library:** through Explore.
- **Voice search:** not wanted (decided 2026-10-19). Microphone access is blocked and Google Assistant is off, so the remote's mic button does nothing useful. Typed search still works through Explore.
- **Casting, AirPlay, screen mirroring:** untouched, because Google's home screen stays installed. (The screensaver is the one thing we deliberately replace.)
- The TV also has a second, unnamed system user slot ("new_user"). Leave it alone.

### Taking over the Home button

| Option | How it works | Upside | Downside |
|---|---|---|---|
| A. Catch the Home button | Our app sees the Home press (accessibility permission) and opens itself. Google's home screen stays installed. | Keeps ambient mode, casting and profiles working. Easy to undo. | Some people report a 2026 update made this unreliable for Projectivy on some TVs. Google's home screen still uses some memory. |
| B. Turn off Google's home screen | Turn off Google's home screen over ADB, so ours is the only one left. | Rock solid. Frees memory. | People report losing ambient mode settings, profile switching, and sometimes casting. Explore would have nothing to open. |

- **My call:** Use A.
- **Safety net:** if our app ever crashes, Android falls back to Google's home screen on its own. Nothing gets stuck.
- **After Hisense or Google updates (My call):** re-check that our app is still the home screen and screensaver, and that its three permissions are still on. Add this to the same checklist as the "Turn off" list.
- **My call:** B only if A is flaky, and only if casting, the screensaver and Explore still work after we try it. If not, undo and live with A's quirks. Worst case, the TV keeps Google's home screen as the default and ours opens from an app tile.

### Real limits of this design

1. No show recommendations or trending rows on our screen. That data comes from Google's servers and only Google's home screen gets it. Explore opens it.
2. Continue watching only works for apps that share what's playing: Crave, YouTube and Paramount+ (tested in milestone 3). Netflix, Prime Video and Apple TV don't share a show name.
3. Pressing a show doesn't always land on the exact spot (see the four levels above): Crave and YouTube are one OK away, Paramount+ asks who's watching first.
4. The Home button takeover may be unreliable on this TV.
5. Google's home screen keeps running in the background and uses some memory.
6. We maintain it. If a Google or Hisense update breaks something, we fix it.

**Phase 2 is done when:** pressing Home opens our screen, the look matches the final mockup (with as many Continue watching cards as apps actually share), Continue watching works for at least Crave, every app and input opens from its tile, Explore opens Google TV, the must-not-break list still holds, and the app stays within its memory budget.

---

## Phase 3: Polish

- Build the screensaver (see "The screensaver" in Phase 2) and switch the TV to it.
- **Downloading the photos safely (Decided 2026-10-19):**
  1. **Only from Wikimedia's own server** (`upload.wikimedia.org`, over HTTPS), and only the exact files on the approved list in `docs/screensaver-images.md`. No other sites, no search-and-grab.
  2. **Check every file really is an image** (JPEG or PNG by its actual contents, not just its name) and the expected size. Anything else is deleted unopened, and the step stops and reports it.
  3. **Make a fresh copy of each image** at TV size with the image tool built into macOS (`sips`), and keep only that copy. This throws away anything hidden in the original, including camera and location data.
  4. **Never run anything downloaded.** Downloads go in their own folder (outside the code), are only ever opened as pictures, and the originals are deleted after the fresh copies are made.
  - No new software is installed for this. `curl` and `sips` already come with macOS.
- Test the remaining apps for Continue watching and direct links, and improve where possible.
- Any extras that come up along the way go back through this doc first.

---

## Milestone 5: Living sky

A new look for the home screen. **Decided (2026-10-20):** you picked "Living sky with sun and moon" from five mocked-up options.

**Why:** you disliked four things about the screen from Phase 2: the red and blue app-coloured glow, titles cut off with "…", small tiles, and Continue watching stopping at 3 cards.

### What's in it (Decided 2026-10-20)

1. **Living sky with sun and moon.** The top area's background is Calgary's sky right now, in the screensaver's colours (by the sun's real height), instead of the app-coloured glow. The sun, and the moon with its real phase, are drawn in their real places on an arc kept to the right of the words. They set behind the mountains.
2. **Weather:** in the corner, as today, and in the sky too (clouds, rain, snow, like the screensaver).
3. **Continue watching holds up to 12 shows**, newest first, and the row scrolls. "5 of 9" showed next to the heading while you were in the row (taken out in the polish below). This needs several shows per app (today it keeps one per app), so the saved-shows file changes. Old saved files must still load.
4. **No more "…".** The big title wraps onto two balanced lines, and gets smaller only if two lines aren't enough. Cards get two lines too, with the words under the picture instead of on it.
5. **Bigger app tiles** (about 196×110 at 1080p, 14% bigger). The PS5 and Explore become small square tiles, so all nine still fit.
6. **Cards without a picture** (Crave, Paramount+) show the app's own banner instead of a dark box.

Learned while mocking it up:

- The mockup's banners were cut from a screenshot. The app uses the TV's real banners.
- The mockup's cards after the first three are placeholders.
- Older cards: YouTube and Paramount+ can still reopen that exact show (search or link). An older Crave card can only open Crave.
- The saved show pictures are small (366×206 px), which is fine for the bigger cards.

### Your calls (My call, confirmed by you when you approved the plan, 2026-10-20)

- **The PS5 tile reads "PS5"** under a controller icon. The top area still says "PlayStation 5" when it's selected.
- **The bright daytime sky is slightly dimmed** so the words stay readable: up to 22% darker with the sun high, plus a shade behind the words. A test checks the words are readable over every sky; if 22% isn't enough, it goes up. **Result (build 37):** 22% was enough once it applies from 6° up (the gold sky just after sunrise is already bright). The shade behind the words is stronger and wider than the mockup's (60% black, fading out between 55% and 85% of the width). A soft shade sits behind the corner, and the corner's words are 80% white (as in the mockup). Every sky of 2026 passes: 4.5:1 or better for the words, 3:1 for the big title.
- **At most 6 shows per app** within the 12, so a week of YouTube can't push the other apps' shows off the row.
- **The weather stays still on the home screen** (on the screensaver it keeps moving). Moving rain would redraw the screen every frame while you move between tiles, and this TV is slow at that.

### How it works (My call, from the plan)

- **The sky** is painted once a minute into one picture and shown as is, so nothing is painted on every frame. It uses the screensaver's colours and mountains, plus the weather's grey (only when the corner's weather is on and up to date; otherwise a clear sky).
- **The sun** moves along the arc from 30 minutes before sunrise to 30 minutes after sunset, at the top of the arc at midday in every season. **The moon** shows when it's up and the sun is down, by its real position and phase (worked out on the TV, no internet). Snow dims them; cloud and rain hide them.
- **The corner** gets a second small line: the next sunrise or sunset, or the moon ("Moon 74% full") while it's up.
- **Text stays at 14 sp or larger**, as since milestone 2, even where the mockup is smaller.
- **Card titles** wrap onto two lines. A title too long even for that ends in "…" on the card only; the top area always shows it whole.
- **The 12 (and 6 per app) count every saved show**, including those from an app you've hidden from the row. Those don't show, but keep their places until they're 30 days old. Fine for now, since the apps that share shows are all on the row.
- **Very long titles** (found by the review, build 39): a title too long for two lines at the smallest size goes onto three smaller lines (24 sp). They take no more room than two big lines, so the cards never move down. Only a title of well over 100 characters ends in "…" in the top area.
- **Saved shows:** a new file (`continue-watching-2.txt`). The old one is read once and then left alone, so putting an older build back still finds the shows from before. A show you remove stays out until that show plays again; the app's other shows aren't affected.

### Polish (2026-10-20, build 40)

You liked the layout but found it cramped, with wasted space and small details that added clutter.

**Decided (2026-10-20):** these go:

- The **"1 of 3" counter** next to "Continue watching".
- The **"Apps" heading**. The logos say what the row is.
- The **line under each card** ("Channel name · 37 min left"). Cards show just the title. The selected card's episode and time are in the top area, and the bar on the picture shows how far you are.
- **Explore's four pills** ("Trending & new" and the rest). They looked like buttons but couldn't be pressed.
- "Pick up where you left off on" **stays**.

**My call** (approved with the plan):

- **One line under the big title:** the whole episode and the time, in the same words as everywhere ("Channel name · 37 min left"). Before, the top said "7 min in" while the card said "37 min left". The top area's own progress bar is gone, since the selected card right below has the same bar.
- **Bigger cards:** 200×112.5 dp (was 176×99). Four reach across the screen and a fifth peeks in.
- **Even spacing:** about 33 dp between the top area and "Continue watching", 16 dp from the heading to the cards (the lifted card's ring clears it), and about 30 dp from the card titles to the apps. The app row stays at the bottom even with no shows, and the sky fills the space above it.
- **The corner's right edge** lines up with the rows (43 dp in, was 35).

## Milestone 6: Mountains and dock

A second home screen, next to Living sky, not in place of it. You picked version 5 of the mockups.

**Why:** you wanted the mountains to be the main picture, with the apps out of the way and a more minimal look.

### What's in it (Decided 2026-10-21)

1. **The whole screen is sky.** Calgary's sky right now, with the Rockies big at the bottom, as on the screensaver. The sun and moon cross the whole width and set behind the far ridge. The weather shows in the sky, as on Living sky.
2. **A quiet clock** at the top left: big and light, as on the screensaver, with one line under it: the date, the weather and the next sunrise or sunset. No greeting, nothing on the right.
3. **A floating dock** along the bottom. The tile you're on is biggest and the ones next to it are a bit bigger, so the dock swells around your place as you move. There's no name label: the white ring shows where you are.
4. **No Continue watching** on this screen.
5. **Both home screens stay.** Settings has a choice between them ("Look").

You picked version 5 from five mockups: a straight dock, a curved dock and a floating dock, with or without a quieter top.

### My call (approved with the plan)

- **The new look is the default.** Living sky is one setting away.
- **Shows are still saved in the background**, so switching back to Living sky shows them.
- **Words only** on the line under the clock, as in today's corner (the mockup had little icons).
- **A thin line** in the dock keeps the PS5 and Explore apart from the streaming apps. If you move the PS5 in among the apps, the line goes.
- **The dock scrolls** if you unhide so many apps that it's wider than the screen.

## Milestone 7: Mount Rundle

Mountains and dock's two drawn ridges become a real photo of Mount Rundle, with the app's live sky showing through where the photo's own sky was. The photo: `docs/home-photo.md`.

**Why:** you wanted the mountains to look real, as if you were there, while keeping the sky that follows the sun.

### What's in it (Decided 2026-10-21)

1. **The photo:** Mount Rundle from the First Vermilion Lake (DXR, CC BY-SA 4.0). You picked it over the Three Sisters and Peyto Lake.
2. **It replaces the drawn ridges** on Mountains and dock. Living sky stays the other choice. The ridges only come back if the photo files are missing.
3. **The sky is unchanged:** Calgary's sun, its colours, the stars, the moon's phase and the weather, with the sun and moon on today's arc.
4. **Framed low**, so the sun and moon pass over Rundle's summit rather than behind it. You chose the arc over the sun's true direction from that spot, which would only show the sun on autumn mornings.
5. **The photo's light follows the sun's height:** blue before sunrise, warm on the high rock in low sun, moonlit at night. The lake mirrors the live sky.
6. **Made on the Mac, kept out of git**, like the screensaver photos. The same download safeguards apply, the photo was approved before downloading, and nothing new is installed.

### My call (approved with the plan)

- **Cloud, rain or snow** take the warm glow off the rock and darken the land a little.
- **The credit** goes in Settings under "Home screen photo", with a note of what was changed, as CC BY-SA asks.
- **The cut-out is made by a small tool inside the project** (`launcher/photoprep`), so it can be rebuilt from a fresh copy.

## Still to settle

- Nothing is open. (Where Hisense's viewing-tracking setting is was settled during clean-up; see Tracking settings.)
