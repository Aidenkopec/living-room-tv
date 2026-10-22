#!/usr/bin/env bash
# Presses Home the way the real remote does, for testing the Home-button takeover.
#
#   scripts/press-home.sh          Short press.
#   scripts/press-home.sh 1000     Hold for 1000 ms (a long press).
#
# Why not "adb shell input keyevent KEYCODE_HOME"? On this TV, keys sent that
# way skip accessibility services, so our Home-button service never sees them.
# Instead this uses Android's built-in "hid" test tool to plug in a virtual
# remote (a USB-style "consumer control" device) for a few seconds, press
# AC Home on it (HID usage 0x0223, which Linux turns into KEY_HOMEPAGE, the
# same scancode 172 the real remote sends), then unplug it. Nothing is saved
# on the TV: the commands go in through stdin, and the device disappears when
# the tool exits.
#
# Settings: ADB=<path to adb>, TV=<address> (or the .tv file, see lib/tv.sh).
set -euo pipefail

ADB="${ADB:-adb}"
source "$(dirname "$0")/lib/tv.sh"
HOLD_MS="${1:-100}"

case "$HOLD_MS" in
  ''|*[!0-9]*) echo "Usage: $0 [hold_ms]" >&2; exit 2 ;;
esac

if ! "$ADB" devices | tr -d '\r' | grep -qE "^${TV}[[:space:]]+device\$"; then
  echo "TV not connected ($TV). Run: adb connect $TV" >&2
  exit 1
fi

# Report descriptor: Consumer Control, report ID 1, one 16-bit usage (0-1023).
DESCRIPTOR='"0x05","0x0c","0x09","0x01","0xa1","0x01","0x85","0x01","0x15","0x00","0x26","0xff","0x03","0x19","0x00","0x2a","0xff","0x03","0x75","0x10","0x95","0x01","0x81","0x00","0xc0"'

# 1.5 s after plugging in, for Android to pick the device up.
"$ADB" -s "$TV" shell hid - >/dev/null <<EOF
{"id":1,"command":"register","name":"Living Room test remote","vid":"0x18d1","pid":"0x7e57","bus":"usb","descriptor":[$DESCRIPTOR]}
{"id":1,"command":"delay","duration":1500}
{"id":1,"command":"report","report":["0x01","0x23","0x02"]}
{"id":1,"command":"delay","duration":$HOLD_MS}
{"id":1,"command":"report","report":["0x01","0x00","0x00"]}
{"id":1,"command":"delay","duration":500}
EOF
