#!/usr/bin/env bash
# The checks run after every TV change. Prints PASS/FAIL per check and
# exits 0 only if all pass. It uses the TV for about a minute (presses Home,
# switches to HDMI 1, plays a YouTube clip) and ends back on the home screen.
#
#   scripts/check-tv.sh                    Run the checks.
#   CHECK_SELFTEST=1 scripts/check-tv.sh   Run them against wrong targets;
#                                          every check must FAIL. Proves the
#                                          checks can catch a problem.
#
# Run it when the room is free: it plays the clip out loud at the current volume,
# and switching to HDMI 1 can wake the PlayStation 5 over HDMI-CEC.
# Settings: ADB=<path to adb>, TV=<address> (or the .tv file, see lib/tv.sh), SHOTS=<folder for screenshots>,
# HOME_ACT=<screen the Home button should open>. Presses Home with
# scripts/press-home.sh, which works the way the real remote does.
set -uo pipefail

ADB="${ADB:-adb}"
source "$(dirname "$0")/lib/tv.sh"
SHOTS="${SHOTS:-${TMPDIR:-/tmp}/tv-checks}"
mkdir -p "$SHOTS"

GOOGLE_HOME_ACT=com.google.android.apps.tv.launcherx/.home.HomeActivity
# Since change #22, Home opens our home screen app ("Living Room").
HOME_ACT="${HOME_ACT:-tv.livingroom.home/.MainActivity}"
TVCENTER_ACT=com.vt.tv.tvcenter/.LiveTVMainActivity
HDMI1_URI="content://android.media.tv/passthrough/com.vt.source.external%2F.hdmi.HdmiTvInputService%2FHW2"
# Inputs that mean "HDMI 1": the port itself, or the PS5's own (HDMI-CEC) input on it.
# Read from the untouched TV on 2026-10-19: after switching, the active session's
# inputId was com.vt.source.external/.hdmi.HdmiTvInputService/HDMI100004.
HDMI1_INPUTS="com.vt.source.external/.hdmi.HdmiTvInputService/HW2 com.vt.source.external/.hdmi.HdmiTvInputService/HDMI100004"
CAST_NAME=SmartTV-4K
AIRPLAY_NAME="Living room TV"
YOUTUBE=com.google.android.youtube.tv
VIDEO_URL="https://www.youtube.com/watch?v=aqz-KE-bpKQ"  # Big Buck Bunny, Blender Foundation
OPEN_VIDEO=1

if [ "${CHECK_SELFTEST:-0}" = "1" ]; then
  echo "SELF-TEST: every check below should FAIL."
  HOME_ACT=com.example/.NotTheHomeScreen
  GOOGLE_HOME_ACT=com.example/.NotGoogleHome
  HDMI1_INPUTS=com.vt.source.external/.hdmi.HdmiTvInputService/HW9
  CAST_NAME=NoSuchTV
  AIRPLAY_NAME=NoSuchTV
  OPEN_VIDEO=0
fi

tv() { "$ADB" -s "$TV" shell "$@" | tr -d '\r'; }
# Home the way the remote presses it ("input keyevent" skips our Home-button service).
press_home() { ADB="$ADB" TV="$TV" "$(dirname "$0")/press-home.sh"; }
shot() { "$ADB" -s "$TV" exec-out screencap -p > "$SHOTS/$1.png" 2>/dev/null; }
resumed() { tv dumpsys activity activities | grep -m1 mResumedActivity; }
listening() { tv "netstat -tln" | grep -qE "[:.]$1 .*LISTEN"; }
FAILS=0
result() { # $1 = name, $2 = 0 for pass, $3 = detail
  if [ "$2" -eq 0 ]; then echo "PASS  $1"; else echo "FAIL  $1 ($3)"; FAILS=$((FAILS + 1)); fi
}

# mDNS names the TV advertises for service type $1, browsed from the Mac for 4 s.
advertised() {
  local out="$SHOTS/dns-sd-$1.txt"
  dns-sd -B "$1" local. > "$out" 2>&1 &
  local pid=$!
  sleep 4
  kill "$pid" 2>/dev/null; wait "$pid" 2>/dev/null
  cat "$out"
}

if ! "$ADB" devices | tr -d '\r' | grep -qE "^${TV}[[:space:]]+device\$"; then
  echo "TV not connected ($TV). Run: adb connect $TV" >&2
  exit 1
fi

# 1. Home screen opens.
press_home; sleep 3
NOW="$(resumed)"; shot 1-home
grep -qF "$HOME_ACT" <<<"$NOW"; result "Home screen opens" $? "$NOW"

# 2. HDMI 1 switches.
tv am start -a android.intent.action.VIEW -d "$HDMI1_URI" >/dev/null; sleep 6
NOW="$(resumed)"; shot 2-hdmi1
INPUT="$(tv dumpsys tv_input | sed -n '/sessionStateMap/,/mainSessionToken/p' | grep -m1 'inputId: ' | sed 's/.*inputId: //')"
ok=1
grep -qF "$TVCENTER_ACT" <<<"$NOW" && for want in $HDMI1_INPUTS; do [ "$INPUT" = "$want" ] && ok=0; done
result "HDMI 1 switches" $ok "screen: $NOW / input: ${INPUT:-none}"
press_home; sleep 2

# 3. Casting and AirPlay are offered: advertised on Wi-Fi, and the TV is listening.
grep -qE "_googlecast\._tcp\.[[:space:]]+$CAST_NAME" <<<"$(advertised _googlecast._tcp)" && listening 8009
result "Casting offered" $? "not advertised as $CAST_NAME, or nothing listening on 8009"
grep -qF "$AIRPLAY_NAME" <<<"$(advertised _airplay._tcp)" && listening 7000
result "AirPlay offered" $? "not advertised as $AIRPLAY_NAME, or nothing listening on 7000"

# 4. A streaming app plays: YouTube reports "playing" set during this check,
#    and has a media audio track running.
START_MS="$(tv cat /proc/uptime | awk '{printf "%d", $1 * 1000}')"
yt_state() { tv dumpsys media_session | awk -v p="package=$YOUTUBE" '$0 ~ p {f=1} f && /state=PlaybackState/ {print; exit}'; }
[ "$OPEN_VIDEO" -eq 1 ] && tv am start -a android.intent.action.VIEW -d "$VIDEO_URL" "$YOUTUBE" >/dev/null
sleep 10
STATE="$(yt_state)"
# A freshly started YouTube first shows "Who's watching?" with the last-used
# account highlighted. Its screen can't be read as text, so if nothing plays
# yet, press OK once to pick the highlighted account.
if [ "$OPEN_VIDEO" -eq 1 ] && ! grep -q 'state=3,' <<<"$STATE"; then
  shot 4-before-ok
  tv input keyevent KEYCODE_DPAD_CENTER
  sleep 12
  STATE="$(yt_state)"
fi
YT_UID="$(tv dumpsys package "$YOUTUBE" | grep -m1 -oE 'userId=[0-9]+' | cut -d= -f2)"
AUDIO="$(tv dumpsys audio | grep -E "u/pid:${YT_UID}/.*state:started.*USAGE_MEDIA")"
UPDATED="$(sed -nE 's/.*updated=([0-9]+).*/\1/p' <<<"$STATE")"
shot 4-playback
ok=1
grep -q 'state=3,' <<<"$STATE" && [ "${UPDATED:-0}" -ge "$START_MS" ] && [ -n "$AUDIO" ] && ok=0
result "YouTube plays" $ok "state: ${STATE:-none}; audio playing: $([ -n "$AUDIO" ] && echo yes || echo no)"
tv input keyevent KEYCODE_MEDIA_STOP

# 5. Google's home opens directly, the way our screen's "Google TV home" entry opens it.
tv am start -a android.intent.action.MAIN -c android.intent.category.HOME -n "$GOOGLE_HOME_ACT" >/dev/null 2>&1; sleep 3
NOW="$(resumed)"; shot 5-google-home
grep -qF "$GOOGLE_HOME_ACT" <<<"$NOW"; result "Google home opens directly" $? "$NOW"
press_home

echo "Screenshots: $SHOTS"
if [ "${CHECK_SELFTEST:-0}" = "1" ]; then
  [ "$FAILS" -eq 6 ] && { echo "Self-test OK: all 6 checks failed as they should."; exit 0; }
  echo "Self-test PROBLEM: only $FAILS of 6 checks failed."; exit 1
fi
[ "$FAILS" -eq 0 ] && echo "All checks passed." || { echo "$FAILS check(s) failed."; exit 1; }
