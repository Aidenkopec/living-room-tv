#!/usr/bin/env bash
# Takes a memory reading of the TV, so "faster" is something we can check.
# Presses Home (the real way, scripts/press-home.sh), waits 2 minutes for things to settle, then takes 3 readings
# a minute apart and prints the middle one. Takes about 4 minutes.
#
# "Available" (MemAvailable) is the best single number: how much memory apps
# can use without the TV having to close something.
#
# Settings: ADB=<path to adb>, TV=<address> (or the .tv file, see lib/tv.sh), SETTLE=<seconds>, GAP=<seconds>.
set -euo pipefail

ADB="${ADB:-adb}"
source "$(dirname "$0")/lib/tv.sh"
SETTLE="${SETTLE:-120}"
GAP="${GAP:-60}"

# Apps that Phase 1 removes or turns off; their memory is shown when running.
WATCH="ca.cbc.android.cbctv com.discovery.discoveryplus.androidtv com.rogers.sportsnet.sportsnet
com.spotify.tv.android com.google.android.youtube.tvmusic com.google.android.play.games
com.hisense.odinbrowser com.vt.tv.singlelisten com.vt.hishow net.vidaatv.hisense.android.tv
com.vidaa.oemtabcontentprovider com.vt.alexa com.vt.tv.evservice com.vt.tv.smartsense"

tv() { "$ADB" -s "$TV" shell "$@" | tr -d '\r'; }

if ! "$ADB" devices | tr -d '\r' | grep -qE "^${TV}[[:space:]]+device\$"; then
  echo "TV not connected ($TV). Run: adb connect $TV" >&2
  exit 1
fi

ADB="$ADB" TV="$TV" "$(dirname "$0")/press-home.sh"
echo "On the home screen; waiting ${SETTLE}s to settle..."
sleep "$SETTLE"

READINGS=""
for i in 1 2 3; do
  [ "$i" -gt 1 ] && sleep "$GAP"
  avail_mb=$(tv cat /proc/meminfo | awk '/^MemAvailable:/ {printf "%d", $2 / 1024}')
  MEMINFO="$(tv dumpsys meminfo)"
  free_ram=$(awk '/Free RAM:/ {print $3}' <<<"$MEMINFO")
  used_ram=$(awk '/Used RAM:/ {print $3}' <<<"$MEMINFO")
  echo "Reading $i ($(date +%H:%M:%S)): Available ${avail_mb} MB | Free RAM ${free_ram} | Used RAM ${used_ram}"
  READINGS="$READINGS$avail_mb $free_ram $used_ram"$'\n'
  if [ "$i" -eq 1 ]; then
    echo "  Watched apps running (PSS):"
    found=0
    for pkg in $WATCH; do
      lines=$(sed -n '/Total PSS by process/,/Total PSS by OOM/p' <<<"$MEMINFO" | grep -E "K: ${pkg}( |:)" || true)
      [ -n "$lines" ] && { echo "$lines" | sed 's/^ */   /'; found=1; }
    done
    [ "$found" -eq 0 ] && echo "   (none)"
    echo "  Our home screen app (tv.livingroom.home):"
    tv dumpsys meminfo tv.livingroom.home | grep -m1 'TOTAL PSS' | sed 's/^ */   /' || echo "   (not running)"
    echo "  Running in the background (largest 8):"
    sed -n '/Total PSS by process/,/Total PSS by OOM/p' <<<"$MEMINFO" | sed -n '2,9p'
  fi
done

MIDDLE=$(sed '/^$/d' <<<"$READINGS" | sort -n | sed -n '2p')
read -r m_avail m_free m_used <<<"$MIDDLE"
echo "Middle reading: Available ${m_avail} MB | Free RAM ${m_free} | Used RAM ${m_used}"
