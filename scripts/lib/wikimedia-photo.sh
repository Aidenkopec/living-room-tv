# shellcheck shell=bash
# Shared by the scripts that download approved photos from Wikimedia
# (fetch-screensaver-photos.sh and fetch-home-photo.sh). It holds the
# safeguards from "Downloading the photos safely" in the design doc: only
# upload.wikimedia.org over HTTPS with no redirects, polite waits when
# Wikimedia is busy, and checking a download is a JPEG with the list's exact
# fingerprint (SHA-1) and pixel size before it's opened as a picture.
# Not run on its own: the scripts load it with `source`.
#
# The scripts set these first: CURL (path to curl), DL (their own new download
# folder), RETRY_S (first wait after "too many requests").

# The settings below are read by the scripts that source this file.
# shellcheck disable=SC2034
UA="LivingRoomTV-photo-fetch/1.0 (one-time personal download)"
URL_RULE='^https://upload\.wikimedia\.org/wikipedia/commons/[0-9a-f]/[0-9a-f]{2}/[^/?#]+$'
SRGB="/System/Library/ColorSync/Profiles/sRGB Profile.icc"
MAX_TRIES=10
MAX_WAIT_S=1800 # Wikimedia has asked for 10 minutes (Retry-After: 600).

die() { echo "STOPPED: $*" >&2; exit 1; }

# A photo list's tables (docs/screensaver-images.md, docs/home-photo.md) as tab-separated rows:
# id mood caption licence credit width height sha1 page url
# (mood comes from a "## Day", "## Dusk" or "## Night" heading above the row, "-" otherwise:
# never empty, since `read` would run two tabs together and shift the columns).
read_list() {
  awk -F'|' '
    /^## / { mood = "-" } /^## Day/ { mood = "day" } /^## Dusk/ { mood = "dusk" } /^## Night/ { mood = "night" }
    /^\| [0-9]+ \|/ {
      for (i = 2; i <= 8; i++) { gsub(/^ +| +$/, "", $i) }
      size = $6
      if (!match(size, /^[0-9]+/)) next
      w = substr(size, RSTART, RLENGTH)
      rest = substr(size, RSTART + RLENGTH)
      match(rest, /[0-9]+/); h = substr(rest, RSTART, RLENGTH)
      sha = ""; if (match(size, /`[0-9a-f]+`/)) sha = substr(size, RSTART + 1, RLENGTH - 2)
      page = $7; gsub(/^<|>$/, "", page)
      url = $8; gsub(/^<|>$/, "", url)
      if (mood == "") mood = "-"
      print $2 "\t" mood "\t" $3 "\t" $4 "\t" $5 "\t" w "\t" h "\t" sha "\t" page "\t" url
    }
  ' "$1"
}

prop() { sips -g "$2" "$1" 2>/dev/null | awk -v k="$2:" '$1 == k { print $2 }'; }
# Size of the info block (APP1) right after the JPEG's header, or 0 if there is none.
app1_len() { if [ "$(xxd -p -s 20 -l 2 "$1")" = ffe1 ]; then echo $((16#$(xxd -p -s 22 -l 2 "$1"))); else echo 0; fi; }

# Downloads $2 to $3 for photo $1, waiting and retrying when Wikimedia is busy.
download() {
  local id="$1" url="$2" file="$3" tries=0 wait="$RETRY_S" code after
  while :; do
    tries=$((tries + 1))
    code="$("$CURL" --proto '=https' --max-redirs 0 --silent --show-error -A "$UA" \
      -D "$DL/headers" -o "$file" -w '%{http_code}' "$url")" || code="${code:-000}"
    case "$code" in
      200) return 0 ;;
      429|503)
        rm -f "$file"
        [ "$tries" -lt "$MAX_TRIES" ] || die "photo $id: Wikimedia still busy ($code) after $MAX_TRIES tries"
        after="$(awk 'tolower($1) == "retry-after:" { print $2 + 0 }' "$DL/headers" 2>/dev/null | tr -d '\r')"
        [ -n "$after" ] && [ "$after" -le "$MAX_WAIT_S" ] && wait="$after"
        echo "  Wikimedia busy ($code); waiting ${wait}s"
        sleep "$wait"
        wait=$((wait * 2)); [ "$wait" -le 300 ] || wait=300
        ;;
      *) rm -f "$file"; die "photo $id: Wikimedia answered $code for $url" ;;
    esac
  done
}

# Checks photo $1's download $2 against the list's SHA-1 $3 and size $4×$5; deletes it and stops if it's wrong.
# Checked as bytes first; only a confirmed, fingerprinted JPEG is opened as a picture.
check_original() {
  local id="$1" orig="$2" sha="$3" w="$4" h="$5" got pw ph
  [ "$(xxd -p -l 3 "$orig")" = ffd8ff ] || { rm -f "$orig"; die "photo $id: the download is not a JPEG (deleted)"; }
  got="$(shasum -a 1 "$orig" | cut -d' ' -f1)"
  [ "$got" = "$sha" ] || { rm -f "$orig"; die "photo $id: fingerprint $got doesn't match the list's $sha (deleted)"; }
  [ "$(file -b --mime-type "$orig")" = image/jpeg ] || { rm -f "$orig"; die "photo $id: the download is not a JPEG (deleted)"; }
  pw="$(prop "$orig" pixelWidth)"; ph="$(prop "$orig" pixelHeight)"
  [ "$pw $ph" = "$w $h" ] || { rm -f "$orig"; die "photo $id: ${pw}×${ph} pixels, the list says ${w}×${h} (deleted)"; }
}
