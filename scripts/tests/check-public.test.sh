#!/usr/bin/env bash
# Tests scripts/check-public.sh against small throwaway repos, one private
# thing at a time, so each kind of slip is shown to be caught.
#
#   bash scripts/tests/check-public.test.sh
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$HERE/../check-public.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
fails=0

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; fails=$((fails + 1)); }

# A new repo holding one tracked file with the given text.
repo_with() {
  local dir="$WORK/$1"
  mkdir -p "$dir"
  git -C "$dir" init -q
  printf '%s\n' "$2" > "$dir/notes.md"
  git -C "$dir" add notes.md
  echo "$dir"
}

expect_clean() {
  local out
  if out="$(ROOT="$2" bash "$SCRIPT" 2>&1)"; then pass "$1"; else fail "$1: $out"; fi
}

expect_caught() {
  local out
  if out="$(ROOT="$2" bash "$SCRIPT" 2>&1)"; then
    fail "$1: not caught"
  elif printf '%s' "$out" | grep -qF "$3"; then
    pass "$1"
  else
    fail "$1: wrong reason: $out"
  fi
}

# The private examples are split in two so this file passes the check itself.
expect_clean "plain text passes" "$(repo_with clean 'Commands start with adb -s $TV. Version 1.10.2.')"
expect_clean "a public test address passes" "$(repo_with testnet 'TV=203.0.113.7:5555')"
expect_clean "a date alone passes" "$(repo_with date '| 12 | 2026-10-19 | Removed an app |')"
expect_clean "a time alone passes" "$(repo_with time 'Looked at with AT 18:04.')"
expect_caught "home address" "$(repo_with ip "adb connect 192.""168.1.37:5555")" "home-network address"
expect_caught "10.x address" "$(repo_with ten "nc 10.""0.0.12 5555")" "home-network address"
expect_caught "date and time" "$(repo_with stamp "| 1 | 2026-10-19 ""14:02 | Removed |")" "date with a time of day"
expect_caught "mockup link" "$(repo_with link "https://claude"".ai/artifact/abc")" "private mockup link"
expect_caught "mockup path" "$(repo_with path ".superpowers/brain""storm/1/final.html")" "local-only mockup path"
expect_caught "home folder" "$(repo_with home "cd /Users""/someone/Documents")" "home folder path"

dir="$(repo_with words 'We watched Some Example Show last night.')"
printf '%s\n' 'some example show' > "$dir/.private-words"
expect_caught "a listed word, any case" "$dir" "listed in .private-words"

dir="$(repo_with untracked 'Nothing here.')"
printf '%s\n' "adb connect 192.""168.1.37:5555" > "$dir/scratch.txt"
expect_clean "untracked files are ignored" "$dir"

echo
if [ "$fails" = 0 ]; then echo "All tests passed."; else echo "$fails test(s) failed."; exit 1; fi
