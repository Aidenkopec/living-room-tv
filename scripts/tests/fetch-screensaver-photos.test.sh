#!/usr/bin/env bash
# Tests scripts/fetch-screensaver-photos.sh against a fake Wikimedia server,
# so nothing is downloaded. The fake "curl" logs every call and answers from
# a list of canned replies. The test photo is made here with sips, from one of
# macOS's own icons, with made-up camera details in it.
# Run: bash scripts/tests/fetch-screensaver-photos.test.sh
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$HERE/../fetch-screensaver-photos.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
FAILS=0

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAILS=$((FAILS + 1)); }

# --- fake curl ------------------------------------------------------------
# Each call takes the next line of $FAKE/replies: "<http code> <body file> [Retry-After]".
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

# --- test files -----------------------------------------------------------
ICON=/System/Library/CoreServices/CoreTypes.bundle/Contents/Resources/ARDocument.icns
sips -s format jpeg "$ICON" --out "$WORK/plain.jpg" >/dev/null 2>&1
sips -s make TestCam -s model X1 -s artist Someone "$WORK/plain.jpg" --out "$WORK/good.jpg" >/dev/null 2>&1
sips -s format png "$ICON" --out "$WORK/not-a-jpeg.png" >/dev/null 2>&1
GOOD_SHA="$(shasum -a 1 "$WORK/good.jpg" | cut -d' ' -f1)"
GOOD_W="$(sips -g pixelWidth "$WORK/good.jpg" | awk '/pixelWidth/ {print $2}')"
GOOD_H="$(sips -g pixelHeight "$WORK/good.jpg" | awk '/pixelHeight/ {print $2}')"
GOOD_URL="https://upload.wikimedia.org/wikipedia/commons/5/58/Test_photo.jpg"

# A one-photo list in the same shape as docs/screensaver-images.md. $1 = file link, $2 = SHA-1.
one_row_list() {
  cat <<EOF
# Test list

## Day (1)

| # | Caption | Licence | Credit line | Size | Commons page | File |
|---|---|---|---|---|---|---|
| 1 | Moraine Lake · Banff National Park, Canada | CC BY-SA 2.0 | Photo: David Zhang, CC BY-SA 2.0, via Wikimedia Commons | ${GOOD_W}×${GOOD_H}, 0.1 MB, SHA-1 \`$2\` | <https://commons.wikimedia.org/wiki/File:Test_photo.jpg> | <$1> |
EOF
}

# Runs the script in a fresh case directory $1 with replies $2 (one per line); list from $3/$4.
run_case() {
  local dir="$WORK/$1"; mkdir -p "$dir/out" "$dir/tmp"
  printf '%s\n' "$2" > "$dir/replies"; : > "$dir/calls.log"
  one_row_list "$3" "$4" > "$dir/list.md"
  OUT_CODE=0
  OUT="$(FAKE="$dir" CURL="$WORK/curl" LIST="$dir/list.md" OUT="$dir/out" EXPECT="1 1 0 0" \
    WAIT_S=0 RETRY_S=0 TMPDIR="$dir/tmp" bash "$SCRIPT" 2>&1)" || OUT_CODE=$?
  CASE="$dir"
}

# --- 1. a good photo ------------------------------------------------------
run_case good "200 $WORK/good.jpg" "$GOOD_URL" "$GOOD_SHA"
COPY="$CASE/out/screensaver/01.jpg"
[ "$OUT_CODE" -eq 0 ] && pass "goodPhotoMade: exit 0" || fail "goodPhotoMade: exit 0 (got $OUT_CODE: $OUT)"
[ "$(sips -g pixelWidth -g pixelHeight "$COPY" 2>/dev/null | awk '/pixel/ {printf "%s ", $2}')" = "1920 1080 " ] \
  && pass "goodPhotoMade: 1920×1080 copy" || fail "goodPhotoMade: 1920×1080 copy"
[ "$(file -b --mime-type "$COPY" 2>/dev/null)" = "image/jpeg" ] && pass "goodPhotoMade: copy is a JPEG" || fail "goodPhotoMade: copy is a JPEG"
TSV="$CASE/out/screensaver-photos.tsv"
[ "$(sed -n 1p "$TSV" 2>/dev/null)" = "$(printf 'id\tfile\tmood\tplace\tregion\tcredit\tlicence\tpage')" ] \
  && pass "goodPhotoMade: list header" || fail "goodPhotoMade: list header"
[ "$(sed -n 2p "$TSV" 2>/dev/null)" = "$(printf '1\t01.jpg\tday\tMoraine Lake\tBanff National Park, Canada\tPhoto: David Zhang, CC BY-SA 2.0, via Wikimedia Commons\tCC BY-SA 2.0\thttps://commons.wikimedia.org/wiki/File:Test_photo.jpg')" ] \
  && pass "goodPhotoMade: list row" || fail "goodPhotoMade: list row ($(sed -n 2p "$TSV" 2>/dev/null))"
[ -z "$(ls -A "$CASE/tmp")" ] && pass "goodPhotoMade: download folder gone" || fail "goodPhotoMade: download folder gone ($(ls -A "$CASE/tmp"))"
grep -qF -- "--proto =https" "$CASE/calls.log" && grep -qF -- "--max-redirs 0" "$CASE/calls.log" \
  && pass "goodPhotoMade: HTTPS only, no redirects" || fail "goodPhotoMade: HTTPS only, no redirects"

# --- 2. no camera details in the copy ---------------------------------------
[ "$(sips -g make -g model -g artist -g copyright -g description "$COPY" 2>/dev/null | grep -c '<nil>')" -eq 5 ] \
  && pass "noCameraDataInCopy: make, model, artist, copyright, description gone" \
  || fail "noCameraDataInCopy: make, model, artist, copyright, description gone"
# The info block (APP1) right after the JPEG's header: macOS writes its own small one
# (resolution and size); camera details make it bigger. 0 if there is none.
app1_len() { if [ "$(xxd -p -s 20 -l 2 "$1")" = ffe1 ]; then echo $((16#$(xxd -p -s 22 -l 2 "$1"))); else echo 0; fi; }
[ "$(app1_len "$WORK/good.jpg")" -gt 160 ] && [ "$(app1_len "$COPY" 2>/dev/null)" -le 160 ] \
  && pass "noCameraDataInCopy: only macOS's small info block" || fail "noCameraDataInCopy: only macOS's small info block"

# --- 3. a link that isn't Wikimedia's file server -------------------------
run_case wronghost "200 $WORK/good.jpg" "https://example.com/wikipedia/commons/5/58/Test_photo.jpg" "$GOOD_SHA"
[ "$OUT_CODE" -ne 0 ] && [ ! -s "$CASE/calls.log" ] && pass "wrongHostRefused: stops before downloading" \
  || fail "wrongHostRefused: stops before downloading (exit $OUT_CODE)"

# --- 4. not a JPEG --------------------------------------------------------
run_case notjpeg "200 $WORK/not-a-jpeg.png" "$GOOD_URL" "$GOOD_SHA"
[ "$OUT_CODE" -ne 0 ] && pass "notAJpegDeleted: stops" || fail "notAJpegDeleted: stops"
[ -z "$(ls -A "$CASE/tmp")" ] && [ ! -e "$CASE/out/screensaver/01.jpg" ] && pass "notAJpegDeleted: file deleted" || fail "notAJpegDeleted: file deleted"
grep -q "not a JPEG" <<<"$OUT" && pass "notAJpegDeleted: says why" || fail "notAJpegDeleted: says why ($OUT)"

# --- 5. wrong fingerprint -------------------------------------------------
run_case wrongsha "200 $WORK/good.jpg" "$GOOD_URL" "0000000000000000000000000000000000000000"
[ "$OUT_CODE" -ne 0 ] && grep -q "fingerprint" <<<"$OUT" && [ ! -e "$CASE/out/screensaver/01.jpg" ] \
  && pass "fingerprintMismatchDeleted" || fail "fingerprintMismatchDeleted (exit $OUT_CODE: $OUT)"

# --- 6. too many requests, then fine --------------------------------------
run_case busy "429 $WORK/not-a-jpeg.png 0
200 $WORK/good.jpg" "$GOOD_URL" "$GOOD_SHA"
[ "$OUT_CODE" -eq 0 ] && [ "$(wc -l < "$CASE/calls.log" | tr -d ' ')" -eq 2 ] && [ -e "$CASE/out/screensaver/01.jpg" ] \
  && pass "tooManyRequestsRetried" || fail "tooManyRequestsRetried (exit $OUT_CODE: $OUT)"

# --- 7. server error ------------------------------------------------------
run_case missing "404 $WORK/not-a-jpeg.png" "$GOOD_URL" "$GOOD_SHA"
[ "$OUT_CODE" -ne 0 ] && [ "$(wc -l < "$CASE/calls.log" | tr -d ' ')" -eq 1 ] && grep -q "404" <<<"$OUT" \
  && pass "serverErrorStops" || fail "serverErrorStops (exit $OUT_CODE: $OUT)"

# --- 8. already made: not downloaded again --------------------------------
run_case resume "200 $WORK/good.jpg" "$GOOD_URL" "$GOOD_SHA"
: > "$CASE/calls.log"
FAKE="$CASE" CURL="$WORK/curl" LIST="$CASE/list.md" OUT="$CASE/out" EXPECT="1 1 0 0" WAIT_S=0 RETRY_S=0 \
  TMPDIR="$CASE/tmp" bash "$SCRIPT" >/dev/null 2>&1
[ -e "$CASE/out/screensaver/01.jpg" ] && [ ! -s "$CASE/calls.log" ] && pass "finishedPhotoSkipped" || fail "finishedPhotoSkipped"

# --- 9. the real list's counts ---------------------------------------------
REAL="$(DRY=1 CURL=/usr/bin/false bash "$SCRIPT" 2>&1)" && grep -q "61 photos: 32 day, 14 dusk, 15 night" <<<"$REAL" \
  && pass "rowCountChecked: the approved list" || fail "rowCountChecked: the approved list ($REAL)"

echo
if [ "$FAILS" -eq 0 ]; then echo "All tests passed."; else echo "$FAILS test(s) failed."; exit 1; fi
