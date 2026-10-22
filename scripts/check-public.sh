#!/usr/bin/env bash
# Checks that nothing private is in the repo's tracked files, so it's safe to
# publish. Prints each line it objects to and exits 1, or prints "Nothing
# private found." and exits 0. CI runs it on every push.
#
#   scripts/check-public.sh
#
# It looks for:
#   - home-network addresses (192.168.x.x, 10.x.x.x, 172.16-31.x.x), such as the TV's;
#   - a date with a time of day (a date, then "14:02"): the change log keeps dates only;
#   - links to private mockups on claude.ai, and paths to local-only mockups;
#   - paths into a home folder on the Mac;
#   - any word or phrase listed in .private-words (show names and the like),
#     one per line. That file is git-ignored, so the list itself is never
#     published; without it this check is skipped.
#
# Settings: ROOT=<repo to check> (default: this repo).
set -euo pipefail

ROOT="${ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
found=0

# Each pattern is written so it doesn't match itself (for example "super[p]owers").
check() {
  local why="$1" pattern="$2" hits
  hits="$(git -C "$ROOT" grep -I -n -E -e "$pattern" -- . || true)"
  if [ -n "$hits" ]; then
    echo "PRIVATE ($why):"
    printf '%s\n' "$hits" | sed 's/^/  /'
    found=1
  fi
}

check "home-network address" '(^|[^0-9.])(192\.168|10\.[0-9]{1,3}|172\.(1[6-9]|2[0-9]|3[01]))\.[0-9]{1,3}\.[0-9]{1,3}([^0-9]|$)'
check "date with a time of day" '[0-9]{4}-[0-9]{2}-[0-9]{2} [0-2][0-9]:[0-5][0-9]'
check "private mockup link" 'claude\.ai/(artifact|code/artifact)'
check "local-only mockup path" 'super[p]owers/brainstorm|docs/super[p]owers'
check "home folder path" '/User[s]/[^/[:space:]]+/'

if [ -f "$ROOT/.private-words" ]; then
  hits="$(git -C "$ROOT" grep -I -n -i -F -f "$ROOT/.private-words" -- . || true)"
  if [ -n "$hits" ]; then
    echo "PRIVATE (listed in .private-words):"
    printf '%s\n' "$hits" | sed 's/^/  /'
    found=1
  fi
else
  echo "Note: no .private-words file, so show names and the like weren't checked."
fi

if [ "$found" = 0 ]; then
  echo "Nothing private found."
fi
exit "$found"
