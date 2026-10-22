#!/usr/bin/env bash
# Puts the TV back the way it was before the clean-up (Phase 1 and later).
#
#   scripts/restore-tv.sh           Look only. Prints "WOULD: ..." for each thing
#                                   that differs from stock, or "Nothing to restore."
#   scripts/restore-tv.sh --apply   Actually put those things back.
#
# It only turns apps back on, reinstalls removed apps (by opening their Play
# Store page for you to press Install), and resets the home screen,
# screensaver and changed settings (such as how long the TV waits before it
# sleeps). The only thing it ever deletes is our own
# home screen app (Phase 2), after switching off its Home-button service
# and taking back its usage access and notification access (Google's home
# keeps its own notification access).
#
# Settings: ADB=<path to adb> (default: adb), TV=<address> (or the .tv file, see lib/tv.sh).
set -euo pipefail

ADB="${ADB:-adb}"
source "$(dirname "$0")/lib/tv.sh"

# Built-in apps that Phase 1 turns off (pm disable-user).
TURN_OFF_PKGS="
com.google.android.youtube.tvmusic
com.google.android.play.games
com.hisense.odinbrowser
com.vt.tv.singlelisten
com.vt.hishow
net.vidaatv.hisense.android.tv
com.vidaa.oemtabcontentprovider
com.vt.alexa
com.vt.tv.evservice
com.google.android.katniss
"

# Parts of apps that Phase 1 turns off (pm disable), as package/class.
OFF_COMPONENTS="
com.vt.tv.smartsense/com.hisense.tv.smartsense.module.logclient.service.LogReportManagerService
"

# Downloaded apps that Phase 1 removes. Undo = reinstall from the Play Store.
REMOVED_PKGS="
ca.cbc.android.cbctv
com.discovery.discoveryplus.androidtv
com.rogers.sportsnet.sportsnet
com.spotify.tv.android
"

# Our home screen app (Phase 2) and its Home-button (accessibility) service.
OUR_APP=tv.livingroom.home
OUR_SERVICE=tv.livingroom.home/tv.livingroom.home.HomeKeyService
# Its notification listener (milestone 3), which sees what apps say is playing.
OUR_LISTENER=tv.livingroom.home/tv.livingroom.home.NowPlayingListener

STOCK_HOME=com.google.android.apps.tv.launcherx/.home.HomeActivity
STOCK_SCREENSAVER=com.google.android.apps.tv.dreamx/.service.Backdrop

# Settings changed directly, one per line: namespace key stock_value.
# Phase 3: the TV sleeps after 2 hours untouched instead of 20 minutes.
SETTINGS_CHANGED="${SETTINGS_CHANGED:-secure sleep_timeout 1200000}"

APPLY=0
case "${1:-}" in
  --apply) APPLY=1 ;;
  "") ;;
  *) echo "Usage: $0 [--apply]" >&2; exit 2 ;;
esac

# </dev/null: "adb shell" reads stdin, which would eat the rest of a while-read list.
tv() { "$ADB" -s "$TV" shell "$@" </dev/null | tr -d '\r'; }

if ! "$ADB" devices | tr -d '\r' | grep -qE "^${TV}[[:space:]]+device\$"; then
  echo "TV not connected ($TV). Run: adb connect $TV" >&2
  exit 1
fi

FOUND=0
FAILED=0

# $1 = what to show, rest = the adb shell command to run with --apply.
act() {
  local label="$1"; shift
  FOUND=1
  if [ "$APPLY" -eq 1 ]; then
    echo "DOING: $label"
    # Carry on with the rest if one undo is refused.
    if ! tv "$@"; then echo "FAILED: $label"; FAILED=1; fi
  else
    echo "WOULD: $label"
  fi
}

# Classes listed under "disabledComponents:" for user 0 in a dumpsys package dump.
user0_disabled_components() {
  awk '
    /^    User [0-9]+:/ { in0 = ($2 == "0:"); inlist = 0; next }
    in0 && /^      disabledComponents:/ { inlist = 1; next }
    in0 && inlist && /^        [^ ]/ { sub(/^ +/, ""); print; next }
    { inlist = 0 }
  '
}

DISABLED="$(tv pm list packages -d --user 0)"
for pkg in $TURN_OFF_PKGS; do
  if grep -qxF "package:$pkg" <<<"$DISABLED"; then
    act "pm default-state --user 0 $pkg" pm default-state --user 0 "$pkg"
  fi
done

for comp in $OFF_COMPONENTS; do
  pkg="${comp%%/*}"; cls="${comp#*/}"
  # Stored first: grep -q stopping early on a long pipe would read as "not found".
  OFF_NOW="$(tv dumpsys package "$pkg" | user0_disabled_components)"
  if grep -qxF "$cls" <<<"$OFF_NOW"; then
    act "pm default-state --user 0 $comp" pm default-state --user 0 "$comp"
  fi
done

INSTALLED="$(tv pm list packages --user 0)"
for pkg in $REMOVED_PKGS; do
  if ! grep -qxF "package:$pkg" <<<"$INSTALLED"; then
    FOUND=1
    if [ "$APPLY" -eq 1 ] && [ ! -t 0 ]; then
      # Reinstalling needs someone pressing Install on the TV; carry on with the rest.
      echo "SKIPPED: $pkg needs reinstalling by hand (run this from a terminal to be walked through it)"
    elif [ "$APPLY" -eq 1 ]; then
      echo "DOING: open Play Store for $pkg"
      tv am start -a android.intent.action.VIEW -d "market://details?id=$pkg" >/dev/null
      read -r -p "  Press Install on the TV, then Enter here to continue... " _
    else
      echo "WOULD: open Play Store for $pkg"
    fi
  fi
done

HOME_NOW="$(tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)"
if [ "$HOME_NOW" != "$STOCK_HOME" ]; then
  act "cmd package set-home-activity --user 0 $STOCK_HOME" cmd package set-home-activity --user 0 "$STOCK_HOME"
fi

# Microphone blocked with Android's master switch (sensor 1) for the main user?
MIC_BLOCKED="$(tv dumpsys sensor_privacy | awk '
  /user_id=/ { u = $0; sub(/.*user_id=/, "", u) }
  /sensor=1[[:space:]]*$/ { s = 1; next }
  s && /is_enabled=/ { if (u == "0" && $0 ~ /is_enabled=true/) print "yes"; s = 0 }
')"
if [ "$MIC_BLOCKED" = "yes" ]; then
  act "cmd sensor_privacy disable 0 microphone" cmd sensor_privacy disable 0 microphone
fi

SAVER_NOW="$(tv settings get secure screensaver_components)"
if [ "$SAVER_NOW" != "$STOCK_SCREENSAVER" ]; then
  act "settings put secure screensaver_components $STOCK_SCREENSAVER" settings put secure screensaver_components "$STOCK_SCREENSAVER"
fi

# Accessibility services are one ":"-separated list; stock is empty and off.
A11Y_NOW="$(tv settings get secure enabled_accessibility_services)"
A11Y_OTHERS="$(tr ':' '\n' <<<"$A11Y_NOW" | grep -vxF -e "$OUR_SERVICE" -e '' -e null | paste -sd: - || true)"
if tr ':' '\n' <<<"$A11Y_NOW" | grep -qxF "$OUR_SERVICE"; then
  if [ -z "$A11Y_OTHERS" ]; then
    act "turn off Living Room's Home-button service (settings delete secure enabled_accessibility_services)" \
      settings delete secure enabled_accessibility_services
  else
    act "turn off Living Room's Home-button service (settings put secure enabled_accessibility_services $A11Y_OTHERS)" \
      settings put secure enabled_accessibility_services "$A11Y_OTHERS"
  fi
fi
A11Y_ON="$(tv settings get secure accessibility_enabled)"
if [ -z "$A11Y_OTHERS" ] && [ "$A11Y_ON" != "0" ] && [ "$A11Y_ON" != "null" ]; then
  act "settings put secure accessibility_enabled 0" settings put secure accessibility_enabled 0
fi

# Usage access (milestone 2) is taken back too. Uninstalling would also drop
# it, but this way the look-only run lists it.
if grep -qxF "package:$OUR_APP" <<<"$INSTALLED" \
  && grep -qF "GET_USAGE_STATS: allow" <<<"$(tv appops get "$OUR_APP" GET_USAGE_STATS)"; then
  act "appops set $OUR_APP GET_USAGE_STATS default" appops set "$OUR_APP" GET_USAGE_STATS default
fi

# Notification access (milestone 3) is one ":"-separated list too. Only ours
# is taken out; Google's home keeps its own.
LISTENERS_NOW="$(tv settings get secure enabled_notification_listeners)"
if tr ':' '\n' <<<"$LISTENERS_NOW" | grep -qxF "$OUR_LISTENER"; then
  act "cmd notification disallow_listener $OUR_LISTENER" cmd notification disallow_listener "$OUR_LISTENER"
fi

# Uninstalled after its service is off, so Home never points at a missing app.
if grep -qxF "package:$OUR_APP" <<<"$INSTALLED"; then
  act "pm uninstall $OUR_APP" pm uninstall "$OUR_APP"
fi

while read -r ns key value; do
  [ -z "${ns:-}" ] && continue
  if [ "$(tv settings get "$ns" "$key")" != "$value" ]; then
    act "settings put $ns $key $value" settings put "$ns" "$key" "$value"
  fi
done <<<"$SETTINGS_CHANGED"

# Hisense consents were changed in the TV's menus. "Product Improvement" lives
# inside Hisense's own app, so writing the Alexa setting alone would leave the
# two out of step. Give the menu steps instead.
if [ "$(tv settings get global alexa_policy_agreed)" = "false" ]; then
  FOUND=1
  echo "BY HAND: Settings → System → About → Legal information → EULA & Privacy:" \
    "tick Product Improvement and Alexa Voice Privacy Policy, then Accept."
fi

if [ "$FOUND" -eq 0 ]; then
  echo "Nothing to restore."
fi

# The deleted Google advertising ID can't be detected or brought back; a new
# one can be made in the menus.
if [ "$APPLY" -eq 1 ]; then
  echo "ALSO BY HAND, if wanted: Settings → Privacy → Ads → Get new advertising ID."
fi

if [ "$FAILED" -eq 1 ]; then
  echo "Some undos failed (see FAILED above)." >&2
  exit 1
fi
