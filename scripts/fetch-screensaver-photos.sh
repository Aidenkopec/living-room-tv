#!/usr/bin/env bash
# Downloads the approved screensaver photos and makes the TV-sized copies the
# app is built with. It follows "Downloading the photos safely" in the design
# doc:
#   1. Only the files on the approved list (docs/screensaver-images.md), and
#      only from Wikimedia's own file server, upload.wikimedia.org, over HTTPS.
#   2. Each download must really be a JPEG, with the list's exact fingerprint
#      (SHA-1) and pixel size. Anything else is deleted and the script stops.
#   3. A fresh copy of each is made with macOS's sips: cropped to the TV's
#      shape around the middle, 1920×1080, in sRGB, saved first as a BMP
#      (pixels only) and then as a JPEG. That leaves out the camera, author
#      and location details the original may carry. The copy is checked too.
#   4. Nothing downloaded is ever run. Downloads go in their own new folder
#      outside the code and are deleted at the end.
# It goes slowly (one file every few seconds) and waits when Wikimedia says
# "too many requests". A photo already made is skipped, so a stopped run can
# carry on. Uses only curl, shasum, file, xxd and sips, all built into macOS.
# The download checks are shared with fetch-home-photo.sh, in lib/wikimedia-photo.sh.
#
#   scripts/fetch-screensaver-photos.sh          Download and make the copies.
#   DRY=1 scripts/fetch-screensaver-photos.sh    Only read and check the list.
#
# Makes launcher/app/src/main/assets/screensaver/NN.jpg (kept out of git) and
# launcher/app/src/main/assets/screensaver-photos.tsv (the list the app reads,
# with each photo's caption and credit).
#
# Settings: LIST=<photo list> (default docs/screensaver-images.md),
# OUT=<assets folder> (default above), CURL=<path to curl> (default curl),
# WAIT_S=<seconds between downloads> (default 4), RETRY_S=<first wait after
# "too many requests"> (default 30; Wikimedia's own Retry-After wins, up to
# 30 minutes), EXPECT="<photos> <day> <dusk> <night>"
# (default "61 32 14 15"; the script stops if the list doesn't match).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LIST="${LIST:-$ROOT/docs/screensaver-images.md}"
OUT="${OUT:-$ROOT/launcher/app/src/main/assets}"
CURL="${CURL:-curl}"
WAIT_S="${WAIT_S:-4}"
RETRY_S="${RETRY_S:-30}"
EXPECT="${EXPECT:-61 32 14 15}"
DRY="${DRY:-0}"

source "$ROOT/scripts/lib/wikimedia-photo.sh"

[ -f "$LIST" ] || die "no photo list at $LIST"
ROWS="$(read_list "$LIST")"

# Check the whole list before downloading anything.
n=0; day=0; dusk=0; night=0
while IFS=$'\t' read -r id mood caption licence credit w h sha page url; do
  n=$((n + 1))
  [ "$id" = "$n" ] || die "the list's photos aren't numbered 1, 2, 3… (found $id where $n was expected)"
  [[ "$url" =~ $URL_RULE ]] || die "photo $id's link isn't a file on upload.wikimedia.org: $url"
  [[ "$sha" =~ ^[0-9a-f]{40}$ ]] || die "photo $id has no SHA-1 fingerprint in the list"
  [ "$mood" != "-" ] && [ -n "$w" ] && [ -n "$h" ] || die "photo $id is missing its section or size"
  case "$mood" in day) day=$((day + 1)) ;; dusk) dusk=$((dusk + 1)) ;; night) night=$((night + 1)) ;; esac
done <<<"$ROWS"
[ "$n $day $dusk $night" = "$EXPECT" ] \
  || die "the list has $n photos ($day day, $dusk dusk, $night night); expected $EXPECT"
echo "$n photos: $day day, $dusk dusk, $night night."
[ "$DRY" = "1" ] && exit 0

[ -f "$SRGB" ] || die "macOS's sRGB colour profile isn't at $SRGB"
DL="$(mktemp -d)"
trap 'rm -rf "$DL"' EXIT
mkdir -p "$OUT/screensaver"

# A finished copy: a 1920×1080 JPEG with no camera, author or location details.
good_copy() {
  [ -s "$1" ] && [ "$(file -b --mime-type "$1")" = image/jpeg ] \
    && [ "$(prop "$1" pixelWidth)" = 1920 ] && [ "$(prop "$1" pixelHeight)" = 1080 ] \
    && [ "$(sips -g make -g model -g artist -g copyright -g description "$1" | grep -c '<nil>')" -eq 5 ] \
    && [ "$(app1_len "$1")" -le 160 ]  # macOS's own small block: resolution and size only
}

first=1
while IFS=$'\t' read -r id mood caption licence credit w h sha page url; do
  name="$(printf '%02d' "$id").jpg"
  copy="$OUT/screensaver/$name"
  if good_copy "$copy"; then continue; fi
  [ "$first" -eq 1 ] || sleep "$WAIT_S"
  first=0
  echo "Photo $id: $caption"
  orig="$DL/original"
  download "$id" "$url" "$orig"

  check_original "$id" "$orig" "$sha" "$w" "$h"

  # The TV's 16:9, cut around the middle (as the preview page shows it).
  if [ $((w * 9)) -gt $((h * 16)) ]; then cw=$(((h * 32 + 9) / 18)); ch=$h; else cw=$w; ch=$(((w * 18 + 16) / 32)); fi
  sips --matchTo "$SRGB" --cropToHeightWidth "$ch" "$cw" -s format bmp "$orig" --out "$DL/cropped.bmp" >/dev/null
  rm -f "$orig"
  sips --resampleHeightWidth 1080 1920 "$DL/cropped.bmp" --out "$DL/sized.bmp" >/dev/null
  sips -s format jpeg -s formatOptions 82 "$DL/sized.bmp" --out "$DL/copy.jpg" >/dev/null
  rm -f "$DL/cropped.bmp" "$DL/sized.bmp"
  good_copy "$DL/copy.jpg" || die "photo $id: the fresh copy didn't come out as a clean 1920×1080 JPEG"
  mv "$DL/copy.jpg" "$copy"
done <<<"$ROWS"

# The app's list: every photo, with its caption split into place and region.
{
  printf 'id\tfile\tmood\tplace\tregion\tcredit\tlicence\tpage\n'
  while IFS=$'\t' read -r id mood caption licence credit w h sha page url; do
    place="${caption%% · *}"; region="${caption#* · }"
    [ "$region" = "$caption" ] && region=""
    printf '%s\t%02d.jpg\t%s\t%s\t%s\t%s\t%s\t%s\n' "$id" "$id" "$mood" "$place" "$region" "$credit" "$licence" "$page"
  done <<<"$ROWS"
} > "$OUT/screensaver-photos.tsv"

echo "Done: $n photos in $OUT/screensaver ($(du -sh "$OUT/screensaver" | cut -f1)), list in $OUT/screensaver-photos.tsv."
