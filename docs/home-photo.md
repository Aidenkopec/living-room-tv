# Home screen photo (Mount Rundle)

**Status: approved (2026-10-21).** Milestone 7 downloads exactly this file and nothing else, with `scripts/fetch-home-photo.sh` and the same safeguards as the screensaver photos (see "Downloading the photos safely" in the design doc).

It's the photo you picked in the mockup: the original file, checked by its fingerprint (SHA-1), as with the screensaver photos.

## How it's used (your decisions and my calls are in the plan)

- **The crop:** the top 16:9 of the photo (the full width, 7114 × 4002 pixels from the top), resized to the TV's 1920 × 1080. Rundle's summit sits about a third of the way down, so the sun's arc passes over it.
- **What's changed:** the photo's own sky is cut away and replaced by the app's live sky, and its colours change with the time of day. CC BY-SA asks for that to be said, so the credit in Settings says it.
- **What stays on the Mac:** the original is deleted once the TV copies are made. The TV copies are kept out of git; the script can make them again. CC BY-SA also means that if you ever share the TV copies, they carry the same licence.

## The photo (1)

| # | Caption | Licence | Credit line | Size | Commons page | File |
|---|---|---|---|---|---|---|
| 1 | Mount Rundle · First Vermilion Lake, Banff National Park, Canada | CC BY-SA 4.0 | Photo: DXR, CC BY-SA 4.0, via Wikimedia Commons | 7114×4743, 9.4 MB, SHA-1 `a4e7a1647b40e43df5bc85576399ab233dd11601` | <https://commons.wikimedia.org/wiki/File:Mount_Rundle,_as_seen_from_First_Vermillion_Lake_20240821_1.jpg> | <https://upload.wikimedia.org/wikipedia/commons/8/85/Mount_Rundle%2C_as_seen_from_First_Vermillion_Lake_20240821_1.jpg> |
