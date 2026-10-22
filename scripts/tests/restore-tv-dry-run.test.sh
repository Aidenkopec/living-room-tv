#!/usr/bin/env bash
# Tests scripts/restore-tv.sh against a fake TV, so nothing real is touched.
# The fake "adb" logs every call and answers with canned output.
# Run: bash scripts/tests/restore-tv-dry-run.test.sh
set -uo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$HERE/../restore-tv.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

SMARTSENSE_LOG=com.vt.tv.smartsense/com.hisense.tv.smartsense.module.logclient.service.LogReportManagerService
OUR_SERVICE=tv.livingroom.home/tv.livingroom.home.HomeKeyService
OUR_LISTENER=tv.livingroom.home/tv.livingroom.home.NowPlayingListener
GOOGLE_LISTENER=com.google.android.apps.tv.launcherx/com.google.android.apps.tv.launcherx.coreservices.notificationlistener.TvNotificationListenerService
FAILS=0

pass() { echo "PASS: $1"; }
fail() { echo "FAIL: $1"; FAILS=$((FAILS + 1)); }

# --- fake adb -------------------------------------------------------------
cat > "$WORK/adb" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >> "$FAKE/calls.log"
# Real "adb shell" reads stdin; copy that when asked.
[ -n "${FAKE_EATS_STDIN:-}" ] && cat > /dev/null
[ "${1:-}" = "-s" ] && shift 2
case "$*" in
  "shell pm default-state"*) [ -n "${FAKE_FAIL_WRITES:-}" ] && { echo "Error: refused" >&2; exit 1; } ;;
  "shell settings get global test_key_"*) echo "changed" ;;
  "devices") cat "$FAKE/devices.txt" ;;
  "shell pm list packages -d --user 0") cat "$FAKE/disabled.txt" ;;
  "shell pm list packages --user 0") cat "$FAKE/installed.txt" ;;
  "shell dumpsys package com.vt.tv.smartsense") cat "$FAKE/dumpsys-smartsense.txt" ;;
  "shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME") cat "$FAKE/home.txt" ;;
  "shell settings get secure screensaver_components") cat "$FAKE/screensaver.txt" ;;
  "shell settings get secure sleep_timeout") cat "$FAKE/sleep-timeout.txt" ;;
  "shell settings get global alexa_policy_agreed") cat "$FAKE/alexa-policy.txt" ;;
  "shell dumpsys sensor_privacy") cat "$FAKE/sensor-privacy.txt" ;;
  "shell settings get secure enabled_accessibility_services") cat "$FAKE/a11y-services.txt" ;;
  "shell settings get secure accessibility_enabled") cat "$FAKE/a11y-enabled.txt" ;;
  "shell appops get tv.livingroom.home GET_USAGE_STATS") cat "$FAKE/usage-access.txt" ;;
  "shell settings get secure enabled_notification_listeners") cat "$FAKE/listeners.txt" ;;
  "shell cmd notification disallow_listener"*) ;;
  "shell settings put"*|"shell settings delete"*|"shell pm uninstall"*) ;;
  *) echo "fake adb: unexpected call: $*" >&2; exit 0 ;;
esac
FAKE
chmod +x "$WORK/adb"

ALL_INSTALLED="package:ca.cbc.android.cbctv
package:com.discovery.discoveryplus.androidtv
package:com.rogers.sportsnet.sportsnet
package:com.spotify.tv.android
package:com.vt.alexa"

# Writes a stock (nothing changed) TV into directory $1.
stock_tv() {
  mkdir -p "$1"; : > "$1/calls.log"
  printf 'List of devices attached\n203.0.113.7:5555\tdevice\n\n' > "$1/devices.txt"
  : > "$1/disabled.txt"
  echo "$ALL_INSTALLED" > "$1/installed.txt"
  cat > "$1/dumpsys-smartsense.txt" <<'EOF'
Packages:
  Package [com.vt.tv.smartsense] (abc):
    User 0: ceDataInode=1 installed=true hidden=false enabled=0
      disabledComponents:
        android.app.AppDetailsActivity
    User 10: ceDataInode=0 installed=false hidden=false enabled=0
EOF
  printf 'priority=0 preferredOrder=0 match=0x108000 specificIndex=-1 isDefault=false\ncom.google.android.apps.tv.launcherx/.home.HomeActivity\n' > "$1/home.txt"
  echo "com.google.android.apps.tv.dreamx/.service.Backdrop" > "$1/screensaver.txt"
  echo "1200000" > "$1/sleep-timeout.txt"   # stock: the TV sleeps after 20 minutes
  echo "true" > "$1/alexa-policy.txt"
  echo "" > "$1/a11y-services.txt"   # stock: empty, not "null"
  echo "0" > "$1/a11y-enabled.txt"
  echo "No operations." > "$1/usage-access.txt"   # stock: never granted
  echo "$GOOGLE_LISTENER" > "$1/listeners.txt"   # stock: Google's home only
  cat > "$1/sensor-privacy.txt" <<'EOF2'
SENSOR PRIVACY MANAGER STATE (dumpsys sensor_privacy)
{
  users=[
    {
      user_id=0
      is_enabled=false
    }
    {
      user_id=10
      is_enabled=false
    }
  ]
}
EOF2
}

run() { # $1 = fake dir, rest = args
  local dir="$1"; shift
  FAKE="$dir" ADB="$WORK/adb" TV=203.0.113.7:5555 bash "$SCRIPT" "$@" 2>&1
}

no_writes() { # $1 = fake dir
  ! grep -qE 'default-state|pm enable|pm disable|settings put|settings delete|set-home-activity|am start|install|uninstall|disallow_listener' "$1/calls.log"
}

# --- 1. changed TV, dry run -----------------------------------------------
T="$WORK/changed"; stock_tv "$T"
echo "package:com.vt.alexa" > "$T/disabled.txt"
echo "$ALL_INSTALLED" | grep -v spotify > "$T/installed.txt"
cat > "$T/dumpsys-smartsense.txt" <<'EOF'
Packages:
  Package [com.vt.tv.smartsense] (abc):
    User 0: ceDataInode=1 installed=true hidden=false enabled=0
      disabledComponents:
        android.app.AppDetailsActivity
        com.hisense.tv.smartsense.module.logclient.service.LogReportManagerService
    User 10: ceDataInode=0 installed=false hidden=false enabled=0
EOF
OUT="$(run "$T")"

grep -qF "WOULD: pm default-state --user 0 com.vt.alexa" <<<"$OUT" \
  && pass "dry run lists turned-off app" || fail "dry run lists turned-off app"
grep -qF "WOULD: open Play Store for com.spotify.tv.android" <<<"$OUT" \
  && pass "dry run lists removed app" || fail "dry run lists removed app"
grep -qF "WOULD: pm default-state --user 0 $SMARTSENSE_LOG" <<<"$OUT" \
  && pass "dry run lists turned-off component" || fail "dry run lists turned-off component"
! grep -qiE 'home|screensaver' <<<"$OUT" \
  && pass "dry run leaves stock home and screensaver alone" || fail "dry run leaves stock home and screensaver alone"
no_writes "$T" \
  && pass "dry run sends no write commands" || fail "dry run sends no write commands"

# --- 2. stock TV ----------------------------------------------------------
T="$WORK/stock"; stock_tv "$T"
OUT="$(run "$T")"
[ "$OUT" = "Nothing to restore." ] \
  && pass "stock TV: Nothing to restore." || fail "stock TV: Nothing to restore. (got: $OUT)"

# --- 3. changed home and screensaver -----------------------------------------
T="$WORK/home"; stock_tv "$T"
printf 'com.example.home/.Main\n' > "$T/home.txt"
echo "com.example.home/.Dream" > "$T/screensaver.txt"
OUT="$(run "$T")"
grep -qF "WOULD: cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity" <<<"$OUT" \
  && pass "dry run lists home screen" || fail "dry run lists home screen"
grep -qF "WOULD: settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop" <<<"$OUT" \
  && pass "dry run lists screensaver" || fail "dry run lists screensaver"

# --- 3b. Living Room's own screensaver (Phase 3) ----------------------------
T="$WORK/oursaver"; stock_tv "$T"
echo "tv.livingroom.home/tv.livingroom.home.ScreensaverService" > "$T/screensaver.txt"
printf '%s\npackage:tv.livingroom.home\n' "$ALL_INSTALLED" > "$T/installed.txt"
OUT="$(run "$T")"
grep -qxF "WOULD: settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop" <<<"$OUT" \
  && pass "dry run puts Google's screensaver back over Living Room's" || fail "dry run puts Google's screensaver back over Living Room's ($OUT)"
grep -qF "WOULD: pm uninstall tv.livingroom.home" <<<"$OUT" \
  && [ "$(grep -n 'screensaver_components' <<<"$OUT" | cut -d: -f1)" -lt "$(grep -n 'pm uninstall' <<<"$OUT" | cut -d: -f1)" ] \
  && pass "screensaver put back before Living Room is removed" || fail "screensaver put back before Living Room is removed"

# --- 3c. the TV sleeps after 2 hours (Phase 3) -------------------------------
T="$WORK/sleep"; stock_tv "$T"
echo "7200000" > "$T/sleep-timeout.txt"
OUT="$(run "$T")"
[ "$OUT" = "WOULD: settings put secure sleep_timeout 1200000" ] \
  && pass "sleepTimeoutRestored: dry run puts 20 minutes back" || fail "sleepTimeoutRestored: dry run puts 20 minutes back (got: $OUT)"

# --- 4. --apply with nobody at the keyboard -------------------------------
T="$WORK/apply"; stock_tv "$T"
echo "$ALL_INSTALLED" | grep -v spotify > "$T/installed.txt"
printf 'com.example.home/.Main\n' > "$T/home.txt"
OUT="$(run "$T" --apply </dev/null)"; CODE=$?
grep -qF "SKIPPED: com.spotify.tv.android" <<<"$OUT" \
  && pass "apply without a keyboard skips reinstalls" || fail "apply without a keyboard skips reinstalls ($OUT)"
grep -qF "set-home-activity" "$T/calls.log" && [ "$CODE" -eq 0 ] \
  && pass "apply without a keyboard carries on to later items" || fail "apply without a keyboard carries on to later items (code $CODE)"
! grep -qF "am start" "$T/calls.log" \
  && pass "apply without a keyboard opens no Play Store pages" || fail "apply without a keyboard opens no Play Store pages"

# --- 5. consents changed in the Hisense menu ------------------------------
T="$WORK/consent"; stock_tv "$T"
echo "false" > "$T/alexa-policy.txt"
OUT="$(run "$T" --apply </dev/null)"
grep -qF "BY HAND: Settings → System → About → Legal information → EULA & Privacy" <<<"$OUT" \
  && pass "Hisense consents: gives menu steps" || fail "Hisense consents: gives menu steps ($OUT)"
! grep -qF "settings put" "$T/calls.log" \
  && pass "Hisense consents: not written directly" || fail "Hisense consents: not written directly"

# --- 6. several changed settings, adb reading stdin -----------------------
T="$WORK/settings"; stock_tv "$T"
OUT="$(SETTINGS_CHANGED=$'global test_key_1 stock1\nglobal test_key_2 stock2' FAKE_EATS_STDIN=1 run "$T" </dev/null)"
grep -qF "WOULD: settings put global test_key_1 stock1" <<<"$OUT" && grep -qF "WOULD: settings put global test_key_2 stock2" <<<"$OUT" \
  && pass "every changed setting is listed" || fail "every changed setting is listed ($OUT)"

# --- 7. one undo fails during --apply -------------------------------------
T="$WORK/fails"; stock_tv "$T"
printf 'package:com.vt.alexa\npackage:com.vt.hishow\n' > "$T/disabled.txt"
OUT="$(FAKE_FAIL_WRITES=1 run "$T" --apply </dev/null)"; CODE=$?
grep -qF "FAILED: pm default-state --user 0 com.vt.alexa" <<<"$OUT" && grep -qF "com.vt.hishow" "$T/calls.log" \
  && pass "a failed undo is reported and the rest still run" || fail "a failed undo is reported and the rest still run (code $CODE: $OUT)"
[ "$CODE" -eq 1 ] \
  && pass "a failed undo makes the script exit 1" || fail "a failed undo makes the script exit 1 (code $CODE)"

# --- 8. dots in package names are not wildcards -----------------------------
T="$WORK/dots"; stock_tv "$T"
echo "package:comXvtXalexa" > "$T/disabled.txt"
OUT="$(run "$T")"
[ "$OUT" = "Nothing to restore." ] \
  && pass "package names match exactly" || fail "package names match exactly ($OUT)"

# --- 9. microphone blocked, and Google Assistant turned off ----------------
T="$WORK/mic"; stock_tv "$T"
echo "package:com.google.android.katniss" > "$T/disabled.txt"
cat > "$T/sensor-privacy.txt" <<'EOF2'
SENSOR PRIVACY MANAGER STATE (dumpsys sensor_privacy)
{
  users=[
    {
      user_id=0
      is_enabled=false
      individual_enabled_sensor={
        sensor=1
        is_enabled=true
      }
    }
    {
      user_id=10
      is_enabled=false
    }
  ]
}
EOF2
OUT="$(run "$T" </dev/null)"
grep -qF "WOULD: cmd sensor_privacy disable 0 microphone" <<<"$OUT" \
  && pass "blocked microphone is listed" || fail "blocked microphone is listed ($OUT)"
grep -qF "WOULD: pm default-state --user 0 com.google.android.katniss" <<<"$OUT" \
  && pass "turned-off Assistant is listed" || fail "turned-off Assistant is listed ($OUT)"

# --- 10. microphone blocked only for the spare user: not ours ---------------
T="$WORK/mic10"; stock_tv "$T"
sed 's/user_id=0/user_id=X/; s/user_id=10/user_id=0/; s/user_id=X/user_id=10/' "$WORK/mic/sensor-privacy.txt" > "$T/sensor-privacy.txt"
OUT="$(run "$T" </dev/null)"
[ "$OUT" = "Nothing to restore." ] \
  && pass "spare user's microphone setting is left alone" || fail "spare user's microphone setting is left alone ($OUT)"

# --- 11. our home screen app installed, Home-button service on ------------
T="$WORK/ours"; stock_tv "$T"
printf '%s\npackage:tv.livingroom.home\n' "$ALL_INSTALLED" > "$T/installed.txt"
echo "$OUR_SERVICE" > "$T/a11y-services.txt"
echo "1" > "$T/a11y-enabled.txt"
OUT="$(run "$T")"
grep -qF "WOULD: turn off Living Room's Home-button service" <<<"$OUT" \
  && pass "our Home-button service is listed" || fail "our Home-button service is listed ($OUT)"
grep -qF "WOULD: pm uninstall tv.livingroom.home" <<<"$OUT" \
  && pass "our app is listed for uninstall" || fail "our app is listed for uninstall ($OUT)"
no_writes "$T" \
  && pass "our app: dry run sends no write commands" || fail "our app: dry run sends no write commands"
: > "$T/calls.log"
run "$T" --apply </dev/null >/dev/null
SVC_LINE="$(grep -nF "settings delete secure enabled_accessibility_services" "$T/calls.log" | cut -d: -f1)"
UNINSTALL_LINE="$(grep -nF "pm uninstall tv.livingroom.home" "$T/calls.log" | cut -d: -f1)"
grep -qF "settings put secure accessibility_enabled 0" "$T/calls.log" \
  && pass "our app: accessibility switched back off" || fail "our app: accessibility switched back off"
[ -n "$SVC_LINE" ] && [ -n "$UNINSTALL_LINE" ] && [ "$SVC_LINE" -lt "$UNINSTALL_LINE" ] \
  && pass "our app: service turned off before uninstalling" || fail "our app: service turned off before uninstalling (lines ${SVC_LINE:-none}, ${UNINSTALL_LINE:-none})"

# --- 12. someone else's accessibility service is kept -----------------------
T="$WORK/a11y-other"; stock_tv "$T"
echo "com.example.reader/.Reader:$OUR_SERVICE" > "$T/a11y-services.txt"
echo "1" > "$T/a11y-enabled.txt"
run "$T" --apply </dev/null >/dev/null
grep -qxF -- "-s 203.0.113.7:5555 shell settings put secure enabled_accessibility_services com.example.reader/.Reader" "$T/calls.log" \
  && pass "other accessibility services are kept" || fail "other accessibility services are kept ($(cat "$T/calls.log" | grep a11y\|access))"
! grep -qF "accessibility_enabled 0" "$T/calls.log" \
  && pass "accessibility stays on for the other service" || fail "accessibility stays on for the other service"

# --- 12b. our app's usage access ----------------------------------------
T="$WORK/usage-stock"; stock_tv "$T"
printf '%s\npackage:tv.livingroom.home\n' "$ALL_INSTALLED" > "$T/installed.txt"
OUT="$(run "$T")"
! grep -qF "appops set" <<<"$OUT" \
  && pass "usage access never granted: not listed" || fail "usage access never granted: not listed ($OUT)"

T="$WORK/usage"; stock_tv "$T"
printf '%s\npackage:tv.livingroom.home\n' "$ALL_INSTALLED" > "$T/installed.txt"
echo "GET_USAGE_STATS: allow; time=+1m2s ago" > "$T/usage-access.txt"
OUT="$(run "$T")"
grep -qF "WOULD: appops set tv.livingroom.home GET_USAGE_STATS default" <<<"$OUT" \
  && pass "our usage access is listed" || fail "our usage access is listed ($OUT)"
no_writes "$T" && ! grep -qF "appops set" "$T/calls.log" \
  && pass "usage access: dry run sends no write commands" || fail "usage access: dry run sends no write commands"
: > "$T/calls.log"
run "$T" --apply </dev/null >/dev/null
APPOPS_LINE="$(grep -nF "appops set tv.livingroom.home GET_USAGE_STATS default" "$T/calls.log" | cut -d: -f1)"
UNINSTALL_LINE="$(grep -nF "pm uninstall tv.livingroom.home" "$T/calls.log" | cut -d: -f1)"
[ -n "$APPOPS_LINE" ] && [ -n "$UNINSTALL_LINE" ] && [ "$APPOPS_LINE" -lt "$UNINSTALL_LINE" ] \
  && pass "usage access taken back before uninstalling" || fail "usage access taken back before uninstalling (lines ${APPOPS_LINE:-none}, ${UNINSTALL_LINE:-none})"

T="$WORK/usage-not-installed"; stock_tv "$T"
echo "GET_USAGE_STATS: allow" > "$T/usage-access.txt"
run "$T" >/dev/null
! grep -qF "appops" "$T/calls.log" \
  && pass "app not installed: usage access not asked" || fail "app not installed: usage access not asked"

# --- 12c. our app's notification access ---------------------------------
T="$WORK/listener-stock"; stock_tv "$T"
printf '%s\npackage:tv.livingroom.home\n' "$ALL_INSTALLED" > "$T/installed.txt"
OUT="$(run "$T")"
! grep -qF "listener" <<<"$OUT" \
  && pass "notification access never granted: not listed" || fail "notification access never granted: not listed ($OUT)"

T="$WORK/listener"; stock_tv "$T"
printf '%s\npackage:tv.livingroom.home\n' "$ALL_INSTALLED" > "$T/installed.txt"
echo "$GOOGLE_LISTENER:$OUR_LISTENER" > "$T/listeners.txt"
OUT="$(run "$T")"
grep -qF "WOULD: cmd notification disallow_listener $OUR_LISTENER" <<<"$OUT" \
  && pass "our notification access is listed" || fail "our notification access is listed ($OUT)"
no_writes "$T" \
  && pass "notification access: dry run sends no write commands" || fail "notification access: dry run sends no write commands"
: > "$T/calls.log"
run "$T" --apply </dev/null >/dev/null
LISTENER_LINE="$(grep -nF "cmd notification disallow_listener $OUR_LISTENER" "$T/calls.log" | cut -d: -f1)"
UNINSTALL_LINE="$(grep -nF "pm uninstall tv.livingroom.home" "$T/calls.log" | cut -d: -f1)"
[ -n "$LISTENER_LINE" ] && [ -n "$UNINSTALL_LINE" ] && [ "$LISTENER_LINE" -lt "$UNINSTALL_LINE" ] \
  && pass "notification access taken back before uninstalling" || fail "notification access taken back before uninstalling (lines ${LISTENER_LINE:-none}, ${UNINSTALL_LINE:-none})"
! grep -qF "$GOOGLE_LISTENER" "$T/calls.log" \
  && pass "Google's notification access is never touched" || fail "Google's notification access is never touched"

T="$WORK/listener-not-installed"; stock_tv "$T"
run "$T" >/dev/null
! grep -qF "cmd notification" "$T/calls.log" \
  && pass "app not installed, not listed: no notification call" || fail "app not installed, not listed: no notification call"

# --- 12d. after the app's own "Switch back to Google's home screen" ---------
# The service switched itself off: gone from the list (empty, or "null" on
# some builds), while Android may leave accessibility itself on.
for LIST in "" "null"; do
  T="$WORK/switched-back-${LIST:-empty}"; stock_tv "$T"
  printf '%s\npackage:tv.livingroom.home\n' "$ALL_INSTALLED" > "$T/installed.txt"
  echo "$LIST" > "$T/a11y-services.txt"
  echo "1" > "$T/a11y-enabled.txt"
  OUT="$(run "$T")"
  grep -qxF "WOULD: settings put secure accessibility_enabled 0" <<<"$OUT" \
    && pass "switched back (${LIST:-empty} list): accessibility listed" || fail "switched back (${LIST:-empty} list): accessibility listed ($OUT)"
  ! grep -qF "Home-button service" <<<"$OUT" \
    && pass "switched back (${LIST:-empty} list): no service line" || fail "switched back (${LIST:-empty} list): no service line ($OUT)"
  grep -qxF "WOULD: pm uninstall tv.livingroom.home" <<<"$OUT" \
    && pass "switched back (${LIST:-empty} list): app still listed" || fail "switched back (${LIST:-empty} list): app still listed ($OUT)"
done

# --- 13. TV not connected ---------------------------------------------------
T="$WORK/offline"; stock_tv "$T"
printf 'List of devices attached\n\n' > "$T/devices.txt"
OUT="$(run "$T")"; CODE=$?
[ "$CODE" -eq 1 ] && grep -qF "TV not connected" <<<"$OUT" \
  && pass "not connected: exit 1 with message" || fail "not connected: exit 1 with message (code $CODE: $OUT)"

echo
[ "$FAILS" -eq 0 ] && echo "All tests passed." || { echo "$FAILS test(s) failed."; exit 1; }
