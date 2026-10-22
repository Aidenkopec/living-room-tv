#!/usr/bin/env bash
# Downloads the approved home screen photo (Mount Rundle, docs/home-photo.md)
# and makes the two files the app paints Mountains and dock with. It follows
# "Downloading the photos safely" in the design doc, with the same checks as
# the screensaver photos (they share scripts/lib/wikimedia-photo.sh):
#   1. Only the one file on the approved list, and only from Wikimedia's own
#      file server, upload.wikimedia.org, over HTTPS, with no redirects.
#   2. The download must really be a JPEG, with the list's exact fingerprint
#      (SHA-1) and pixel size. Anything else is deleted and the script stops.
#   3. A fresh copy is made with macOS's sips: the top 16:9 of the photo, in
#      sRGB, as a BMP (pixels only, no camera or location details), resized
#      to 1920×1080. The original is deleted straight away.
#   4. The project's own prep tool (launcher/photoprep) cuts the sky away and
#      makes the layers; it stops if the framing is off. Its two files are
#      checked before they're moved into the app.
#   5. Nothing downloaded is ever run. The download goes in its own new folder
#      outside the code, deleted at the end.
# If the files are already made, nothing is downloaded. Uses only curl,
# shasum, file, xxd and sips (built into macOS) and the project's Gradle.
#
#   scripts/fetch-home-photo.sh          Download and make the files.
#   DRY=1 scripts/fetch-home-photo.sh    Only read and check the list.
#   FORCE=1 scripts/fetch-home-photo.sh  Make them again even if they're there.
#
# Makes launcher/app/src/main/assets/home/rundle-land.jpg and rundle-layers.png
# (kept out of git) and launcher/app/src/main/assets/home-photo.tsv (what the
# app reads: the credit and where the lake's eye level is).
#
# Settings: LIST=<photo list> (default docs/home-photo.md), OUT=<assets folder>
# (default above), CURL=<path to curl> (default curl), RETRY_S=<first wait
# after "too many requests"> (default 30), JAVA_HOME (default Homebrew's
# openjdk@21), PREP=<prep command, given the copy and an output folder>
# (default: the project's photoprep tool, through Gradle).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIST="${LIST:-$ROOT/docs/home-photo.md}"
OUT="${OUT:-$ROOT/launcher/app/src/main/assets}"
CURL="${CURL:-curl}"
RETRY_S="${RETRY_S:-30}"
DRY="${DRY:-0}"
FORCE="${FORCE:-0}"
PREP="${PREP:-}"
CHANGES="Cropped, sky replaced, colours changed with the time of day"

source "$ROOT/scripts/lib/wikimedia-photo.sh"

[ -f "$LIST" ] || die "no photo list at $LIST"
ROWS="$(read_list "$LIST")"
[ "$(grep -c . <<<"$ROWS")" -eq 1 ] || die "the list should have exactly one photo; it has $(grep -c . <<<"$ROWS")"
IFS=$'\t' read -r id _ caption licence credit w h sha page url <<<"$ROWS"
[ "$id" = 1 ] || die "the photo should be numbered 1 (found $id)"
[[ "$url" =~ $URL_RULE ]] || die "the photo's link isn't a file on upload.wikimedia.org: $url"
[[ "$sha" =~ ^[0-9a-f]{40}$ ]] || die "the photo has no SHA-1 fingerprint in the list"
[ -n "$w" ] && [ -n "$h" ] || die "the photo is missing its size"
echo "1 photo: $caption."
[ "$DRY" = "1" ] && exit 0

LAND="$OUT/home/rundle-land.jpg"
LAYERS="$OUT/home/rundle-layers.png"
TSV="$OUT/home-photo.tsv"

# The land photo: a 1920×1080 JPEG with no camera, author or location details.
good_land() {
  [ -s "$1" ] && [ "$(file -b --mime-type "$1")" = image/jpeg ] \
    && [ "$(prop "$1" pixelWidth)" = 1920 ] && [ "$(prop "$1" pixelHeight)" = 1080 ] \
    && [ "$(sips -g make -g model -g artist -g copyright -g description "$1" | grep -c '<nil>')" -eq 5 ] \
    && [ "$(app1_len "$1")" -le 160 ]
}
# The layers: a 1920×1080 PNG with its fourth (ripple) channel.
good_layers() {
  [ -s "$1" ] && [ "$(file -b --mime-type "$1")" = image/png ] \
    && [ "$(prop "$1" pixelWidth)" = 1920 ] && [ "$(prop "$1" pixelHeight)" = 1080 ] \
    && [ "$(prop "$1" hasAlpha)" = yes ]
}

if [ "$FORCE" != 1 ] && good_land "$LAND" && good_layers "$LAYERS" && [ -s "$TSV" ]; then
  echo "Already made: $LAND and $LAYERS (FORCE=1 makes them again)."
  exit 0
fi

[ -f "$SRGB" ] || die "macOS's sRGB colour profile isn't at $SRGB"
DL="$(mktemp -d)"
trap 'rm -rf "$DL"' EXIT

echo "Downloading: $caption"
orig="$DL/original"
download "$id" "$url" "$orig"
check_original "$id" "$orig" "$sha" "$w" "$h"

# The top 16:9 of the photo. (sips takes an offset of 0 to mean "the middle",
# so the cut starts at row 1: half a pixel on the TV.)
ch=$(((w * 9 + 8) / 16))
[ $((ch + 1)) -le "$h" ] || die "the photo is too wide for a 16:9 cut from the top"
sips --matchTo "$SRGB" -s format bmp --cropToHeightWidth "$ch" "$w" --cropOffset 1 0 "$orig" --out "$DL/cropped.bmp" >/dev/null
rm -f "$orig"
[ "$(prop "$DL/cropped.bmp" pixelWidth) $(prop "$DL/cropped.bmp" pixelHeight)" = "$w $ch" ] \
  || die "the 16:9 cut came out $(prop "$DL/cropped.bmp" pixelWidth)×$(prop "$DL/cropped.bmp" pixelHeight), not ${w}×${ch}"
sips --resampleHeightWidth 1080 1920 "$DL/cropped.bmp" --out "$DL/sized.bmp" >/dev/null
rm -f "$DL/cropped.bmp"

echo "Cutting the sky away (launcher/photoprep)"
mkdir -p "$DL/made"
if [ -n "$PREP" ]; then
  "$PREP" "$DL/sized.bmp" "$DL/made"
else
  (cd "$ROOT/launcher" && JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}" \
    ./gradlew -q :photoprep:run --args="$DL/sized.bmp $DL/made")
fi
rm -f "$DL/sized.bmp"
good_land "$DL/made/land.jpg" || die "the prep tool's land.jpg isn't a clean 1920×1080 JPEG"
good_layers "$DL/made/layers.png" || die "the prep tool's layers.png isn't a 1920×1080 PNG with four channels"
eye_row="$(awk -F'\t' '$1 == "eye_row" { print $2 }' "$DL/made/photo.txt")"
land_top="$(awk -F'\t' '$1 == "land_top" { print $2 }' "$DL/made/photo.txt")"
[ -n "$eye_row" ] && [ -n "$land_top" ] || die "the prep tool didn't say where the eye level and the land are"

mkdir -p "$OUT/home"
mv "$DL/made/land.jpg" "$LAND"
mv "$DL/made/layers.png" "$LAYERS"
place="${caption%% · *}"; region="${caption#* · }"
[ "$region" = "$caption" ] && region=""
{
  printf 'land\tlayers\tplace\tregion\tcredit\tlicence\tpage\tchanges\teye_row\tland_top\n'
  printf 'home/rundle-land.jpg\thome/rundle-layers.png\t%s\t%s\t%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$place" "$region" "$credit" "$licence" "$page" "$CHANGES" "$eye_row" "$land_top"
} > "$TSV"
echo "Done: $LAND and $LAYERS ($(du -ch "$LAND" "$LAYERS" | tail -1 | cut -f1)), list in $TSV."
