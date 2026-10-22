#!/usr/bin/env bash
# Tests scripts/fetch-home-photo.sh against a fake Wikimedia server and a fake
# prep tool, so nothing is downloaded and Gradle isn't run. The fake "curl"
# logs every call and answers from a list of canned replies; the fake prep
# tool records the copy it was given and hands back ready-made files. The test
# pictures are made here with sips, from one of macOS's own icons.
# Run: bash scripts/tests/fetch-home-photo.test.sh
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$HERE/../fetch-home-photo.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FAILS=0

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAILS=$((FAILS + 1)); }

# --- fake curl (as in fetch-screensaver-photos.test.sh) --------------------
cat > "$WORK/curl" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >> "$FAKE/calls.log"
out=""; hdr=""
while [ $# -gt 0 ]; do
  case "$1" in
    -o) out="$2"; shift 2 ;;
    -D) hdr="$2"; shift 2 ;;
    -A|-w|--proto|--max-redirs) shift 2 ;;
    *) shift ;;
  esac
done
n=$(( $(cat "$FAKE/count" 2>/dev/null || echo 0) + 1 )); echo "$n" > "$FAKE/count"
read -r code body after <<<"$(sed -n "${n}p" "$FAKE/replies")"
cp "$body" "$out"
{ echo "HTTP/2 $code"; [ -n "${after:-}" ] && echo "retry-after: $after"; echo; } > "$hdr"
printf '%s' "$code"
FAKE
chmod +x "$WORK/curl"

# --- test pictures ----------------------------------------------------------
ICON=/System/Library/CoreServices/CoreTypes.bundle/Contents/Resources/ARDocument.icns
sips -s format jpeg "$ICON" --out "$WORK/plain.jpg" >/dev/null 2>&1
sips -s make TestCam -s model X1 -s artist Someone "$WORK/plain.jpg" --out "$WORK/good.jpg" >/dev/null 2>&1
GOOD_SHA="$(shasum -a 1 "$WORK/good.jpg" | cut -d' ' -f1)"
GOOD_W="$(sips -g pixelWidth "$WORK/good.jpg" | awk '/pixelWidth/ {print $2}')"
GOOD_H="$(sips -g pixelHeight "$WORK/good.jpg" | awk '/pixelHeight/ {print $2}')"
GOOD_URL="https://upload.wikimedia.org/wikipedia/commons/8/85/Test_photo.jpg"
# What the prep tool makes: a 1920×1080 JPEG and a 1920×1080 PNG with a fourth channel.
sips -s format jpeg -z 1080 1920 "$WORK/plain.jpg" --out "$WORK/made-land.jpg" >/dev/null 2>&1
sips -s format png -z 1080 1920 "$ICON" --out "$WORK/made-layers.png" >/dev/null 2>&1

# The fake prep tool: notes the copy's size, then hands back the made files (or fails, with PREP_FAILS=1).
cat > "$WORK/prep" <<FAKE
#!/usr/bin/env bash
sips -g pixelWidth -g pixelHeight "\$1" | awk '/pixel/ {printf "%s ", \$2}' > "\$FAKE/prep-input"
[ "\${PREP_FAILS:-0}" = 1 ] && { echo "STOPPED: framing" >&2; exit 1; }
cp "$WORK/made-land.jpg" "\$2/land.jpg"
cp "$WORK/made-layers.png" "\$2/layers.png"
printf 'eye_row\t747.24\nland_top\t388\n' > "\$2/photo.txt"
FAKE
chmod +x "$WORK/prep"

# A list in the same shape as docs/home-photo.md. $1 = file link, $2 = SHA-1, $3 = how many rows.
list() {
  cat <<EOF
# Test list

## The photo (1)

| # | Caption | Licence | Credit line | Size | Commons page | File |
|---|---|---|---|---|---|---|
EOF
  for i in $(seq 1 "${3:-1}"); do
    echo "| $i | Mount Rundle · First Vermilion Lake, Banff National Park, Canada | CC BY-SA 4.0 | Photo: DXR, CC BY-SA 4.0, via Wikimedia Commons | ${GOOD_W}×${GOOD_H}, 0.1 MB, SHA-1 \`$2\` | <https://commons.wikimedia.org/wiki/File:Test_photo.jpg> | <$1> |"
  done
}

# Runs the script in a fresh case directory $1 with replies $2; the list from $3/$4/$5.
run_case() {
  local dir="$WORK/$1"; mkdir -p "$dir/out" "$dir/tmp"
  printf '%s\n' "$2" > "$dir/replies"; : > "$dir/calls.log"
  list "$3" "$4" "${5:-1}" > "$dir/list.md"
  OUT_CODE=0
  OUT="$(FAKE="$dir" CURL="$WORK/curl" PREP="$WORK/prep" LIST="$dir/list.md" OUT="$dir/out" \
    RETRY_S=0 TMPDIR="$dir/tmp" bash "$SCRIPT" 2>&1)" || OUT_CODE=$?
  CASE="$dir"
}

# --- 1. a good photo ----------------------------------------------------------
run_case good "200 $WORK/good.jpg" "$GOOD_URL" "$GOOD_SHA"
[ "$OUT_CODE" -eq 0 ] && pass "goodPhotoMade: exit 0" || fail "goodPhotoMade: exit 0 (got $OUT_CODE: $OUT)"
[ -s "$CASE/out/home/rundle-land.jpg" ] && [ -s "$CASE/out/home/rundle-layers.png" ] \
  && pass "goodPhotoMade: both files in place" || fail "goodPhotoMade: both files in place"
[ "$(cat "$CASE/prep-input" 2>/dev/null)" = "1920 1080 " ] \
  && pass "goodPhotoMade: the prep tool got a 1920×1080 copy" || fail "goodPhotoMade: the prep tool got a 1920×1080 copy ($(cat "$CASE/prep-input" 2>/dev/null))"
TSV="$CASE/out/home-photo.tsv"
[ "$(sed -n 1p "$TSV" 2>/dev/null)" = "$(printf 'land\tlayers\tplace\tregion\tcredit\tlicence\tpage\tchanges\teye_row\tland_top')" ] \
  && pass "goodPhotoMade: list header" || fail "goodPhotoMade: list header"
[ "$(sed -n 2p "$TSV" 2>/dev/null)" = "$(printf 'home/rundle-land.jpg\thome/rundle-layers.png\tMount Rundle\tFirst Vermilion Lake, Banff National Park, Canada\tPhoto: DXR, CC BY-SA 4.0, via Wikimedia Commons\tCC BY-SA 4.0\thttps://commons.wikimedia.org/wiki/File:Test_photo.jpg\tCropped, sky replaced, colours changed with the time of day\t747.24\t388')" ] \
  && pass "goodPhotoMade: list row" || fail "goodPhotoMade: list row ($(sed -n 2p "$TSV" 2>/dev/null))"
[ -z "$(ls -A "$CASE/tmp")" ] && pass "goodPhotoMade: download folder gone" || fail "goodPhotoMade: download folder gone ($(ls -A "$CASE/tmp"))"
grep -qF -- "--proto =https" "$CASE/calls.log" && grep -qF -- "--max-redirs 0" "$CASE/calls.log" \
  && pass "goodPhotoMade: HTTPS only, no redirects" || fail "goodPhotoMade: HTTPS only, no redirects"

# --- 2. already made: not downloaded again --------------------------------------
: > "$CASE/calls.log"
FAKE="$CASE" CURL="$WORK/curl" PREP="$WORK/prep" LIST="$CASE/list.md" OUT="$CASE/out" RETRY_S=0 \
  TMPDIR="$CASE/tmp" bash "$SCRIPT" >/dev/null 2>&1
[ ! -s "$CASE/calls.log" ] && pass "finishedPhotoSkipped" || fail "finishedPhotoSkipped"

# --- 3. a link that isn't Wikimedia's file server -------------------------------
run_case wronghost "200 $WORK/good.jpg" "https://example.com/wikipedia/commons/8/85/Test_photo.jpg" "$GOOD_SHA"
[ "$OUT_CODE" -ne 0 ] && [ ! -s "$CASE/calls.log" ] && pass "wrongHostRefused: stops before downloading" \
  || fail "wrongHostRefused: stops before downloading (exit $OUT_CODE)"

# --- 4. wrong fingerprint ---------------------------------------------------------
run_case wrongsha "200 $WORK/good.jpg" "$GOOD_URL" "0000000000000000000000000000000000000000"
[ "$OUT_CODE" -ne 0 ] && grep -q "fingerprint" <<<"$OUT" && [ ! -e "$CASE/out/home/rundle-land.jpg" ] && [ -z "$(ls -A "$CASE/tmp")" ] \
  && pass "fingerprintMismatchDeleted" || fail "fingerprintMismatchDeleted (exit $OUT_CODE: $OUT)"

# --- 5. too many requests, then fine -------------------------------------------
run_case busy "429 $WORK/plain.jpg 0
200 $WORK/good.jpg" "$GOOD_URL" "$GOOD_SHA"
[ "$OUT_CODE" -eq 0 ] && [ "$(wc -l < "$CASE/calls.log" | tr -d ' ')" -eq 2 ] \
  && pass "tooManyRequestsRetried" || fail "tooManyRequestsRetried (exit $OUT_CODE: $OUT)"

# --- 6. the prep tool stops (framing off): nothing moved in -----------------------
dir="$WORK/prepfails"; mkdir -p "$dir/out" "$dir/tmp"; printf '200 %s\n' "$WORK/good.jpg" > "$dir/replies"; : > "$dir/calls.log"
list "$GOOD_URL" "$GOOD_SHA" > "$dir/list.md"
code=0; out="$(FAKE="$dir" PREP_FAILS=1 CURL="$WORK/curl" PREP="$WORK/prep" LIST="$dir/list.md" OUT="$dir/out" RETRY_S=0 \
  TMPDIR="$dir/tmp" bash "$SCRIPT" 2>&1)" || code=$?
[ "$code" -ne 0 ] && [ ! -e "$dir/out/home/rundle-land.jpg" ] && [ ! -e "$dir/out/home-photo.tsv" ] && [ -z "$(ls -A "$dir/tmp")" ] \
  && pass "prepFailureStops" || fail "prepFailureStops (exit $code: $out)"

# --- 7. more than one photo on the list ----------------------------------------
run_case tworows "200 $WORK/good.jpg" "$GOOD_URL" "$GOOD_SHA" 2
[ "$OUT_CODE" -ne 0 ] && [ ! -s "$CASE/calls.log" ] && grep -q "exactly one" <<<"$OUT" \
  && pass "oneRowOnly" || fail "oneRowOnly (exit $OUT_CODE: $OUT)"

# --- 8. the real list ---------------------------------------------------------------
REAL="$(DRY=1 CURL=/usr/bin/false bash "$SCRIPT" 2>&1)" && grep -q "1 photo: Mount Rundle" <<<"$REAL" \
  && pass "realListChecks" || fail "realListChecks ($REAL)"

echo
if [ "$FAILS" -eq 0 ]; then echo "All tests passed."; else echo "$FAILS test(s) failed."; exit 1; fi
