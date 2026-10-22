# shellcheck shell=bash
# Shared by the TV scripts (check-tv.sh, press-home.sh, restore-tv.sh,
# tv-memory.sh): works out the TV's address, so it never has to be written
# into the repo.
#
# The address comes from, in order:
#   1. TV=<address>, set when you run the script (TV=<address>:5555 scripts/...).
#   2. A file named .tv at the top of the repo, holding just the address.
#      It's git-ignored, so it stays on your Mac. Make it once with:
#        echo <address>:5555 > .tv
# If neither is there, the script stops and says how to set it.
# Not run on its own: the scripts load it with `source`.

if [ -z "${TV:-}" ]; then
  TV_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.tv"
  if [ -f "$TV_FILE" ]; then
    TV="$(tr -d '[:space:]' < "$TV_FILE")"
  fi
fi
if [ -z "${TV:-}" ]; then
  echo "No TV address. Run with TV=<address>:5555, or save it once: echo <address>:5555 > .tv" >&2
  exit 2
fi
