#!/usr/bin/env bash
# The app on a headless emulator, for looking at a change without a phone.
#
#   tools/preview.sh up                 boot the emulator (once; it stays up)
#   tools/preview.sh hosts              pin the dashboard's name again (after booting it by hand)
#   tools/preview.sh build              build the debug APK
#   tools/preview.sh install            install the APK that is built
#   tools/preview.sh shot [screen] [f]  build, install, open <screen>, save a screenshot to f
#   tools/preview.sh snap [f]           screenshot what is on screen now
#   tools/preview.sh open <screen>      open a screen (home, health, ai, settings, usage, widgets,
#                                       servers, console, hermes, tilesmith)
#   tools/preview.sh tap <x> <y> | swipe <x1> <y1> <x2> <y2> | scroll [dy] | back | text <words>
#   tools/preview.sh ui                 the screen's text nodes with their tap points
#   tools/preview.sh down               stop the emulator
#
# A warm incremental build is about 30 s; install and screenshot are a second each.
# Setup (once): sdkmanager "emulator" "system-images;android-36;default;x86_64", then
#   avdmanager create avd -n dash -k "system-images;android-36;default;x86_64" -d pixel_6
# and membership of the kvm group. A dashboard that only resolves on a private network can be
# pinned in the emulator's hosts file: set PREVIEW_HOSTS, or `preview.hosts=<ip> <name>` in
# local.properties (which is not committed).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_SDK_ROOT:-$(sed -n 's/^sdk.dir=//p' "$ROOT/local.properties" 2>/dev/null)}"
SDK="${SDK:-/opt/android-sdk}"
ADB="$SDK/platform-tools/adb"
SERIAL="${PREVIEW_SERIAL:-emulator-5554}"
AVD="${PREVIEW_AVD:-dash}"
HOSTS="${PREVIEW_HOSTS:-$(sed -n 's/^preview.hosts=//p' "$ROOT/local.properties" 2>/dev/null)}"
PKG=com.macrotracker
OUT="${PREVIEW_OUT:-$ROOT/build/preview}"
LOG="${XDG_CACHE_HOME:-$HOME/.cache}/dash-emu"
a() { "$ADB" -s "$SERIAL" "$@"; }

booted() { [ "$(a shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; }

up() {
  if booted; then return; fi
  mkdir -p "$LOG"
  local run="$SDK/emulator/emulator -avd $AVD -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -cores 2 -memory 2048"
  if id -nG | grep -qw kvm; then setsid $run >"$LOG/emu.log" 2>&1 &
  else setsid sg kvm -c "$run" >"$LOG/emu.log" 2>&1 & fi
  for _ in $(seq 100); do booted && break; sleep 3; done
  booted || { echo "the emulator did not boot; see $LOG/emu.log" >&2; exit 1; }
  # no animation scale change: the app's own motion should show as on a phone
  a shell settings put system screen_off_timeout 2147483647 >/dev/null
  a shell svc power stayon true >/dev/null
  a shell cmd overlay enable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1 || true
  pin_hosts
}

pin_hosts() {
  # Public DNS can send a private dashboard's name somewhere that does not serve it.
  [ -n "$HOSTS" ] || return 0
  local ip="${HOSTS%% *}" name="${HOSTS#* }"
  a root >/dev/null 2>&1 || true
  "$ADB" -s "$SERIAL" wait-for-device
  for _ in 1 2 3 4; do
    a shell "setenforce 0; printf '127.0.0.1 localhost\n::1 ip6-localhost\n$HOSTS\n' > /data/local/tmp/hosts; umount /system/etc/hosts 2>/dev/null; mount -o bind /data/local/tmp/hosts /system/etc/hosts"
    # (permissive above: the resolver may not read a file that lives under /data)
    # drop what the resolver already cached
    a shell "cmd connectivity airplane-mode enable; sleep 2; cmd connectivity airplane-mode disable" >/dev/null
    sleep 6
    a shell "ping -c1 -W2 ${name%% *}" 2>/dev/null | grep -q "($ip)" && return 0
  done
  echo "could not pin $HOSTS in the emulator" >&2
}

seed() {
  # A first run would open onboarding; skip it so a screenshot shows the app.
  a shell "run-as $PKG sh -c 'mkdir -p shared_prefs; [ -f shared_prefs/macro_tracker_settings.xml ] || cat > shared_prefs/macro_tracker_settings.xml'" <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="onboarding_completed" value="true" />
    <string name="ai_provider">hermes</string>
</map>
XML
}

open_screen() {
  a shell am start -n "$PKG/.MainActivity" --es open "${1:-home}" --activity-clear-top >/dev/null
}

snap() {
  local f="${1:-$OUT/shot.png}"
  mkdir -p "$(dirname "$f")"
  a exec-out screencap -p >"$f"
  echo "$f"
}

case "${1:-}" in
  up) up ;;
  hosts) pin_hosts ;;
  down) a emu kill ;;
  build) (cd "$ROOT" && ./gradlew -q :app:assembleDebug) ;;
  install)
    up
    a install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
    seed ;;
  shot)
    up
    (cd "$ROOT" && ./gradlew -q :app:assembleDebug)
    a install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
    seed
    a shell am force-stop "$PKG"
    open_screen "${2:-home}"
    sleep "${PREVIEW_SETTLE:-4}"
    snap "${3:-$OUT/${2:-home}.png}" ;;
  open) open_screen "${2:-home}" ;;
  snap) snap "${2:-}" ;;
  tap) a shell input tap "$2" "$3" ;;
  swipe) a shell input swipe "$2" "$3" "$4" "$5" "${6:-250}" ;;
  scroll) a shell input swipe 540 1700 540 $((1700 - ${2:-900})) 400 ;;
  back) a shell input keyevent KEYCODE_BACK ;;
  text)
    shift
    # the device's shell reads it again: quote it whole, with spaces as input's %s
    t="${*// /%s}"
    a shell "input text '${t//\'/\'\\\'\'}'" ;;
  ui)
    a shell uiautomator dump /sdcard/ui.xml >/dev/null
    a shell cat /sdcard/ui.xml | python3 -c '
import re, sys
for m in re.finditer(r"<node[^>]*?text=\"([^\"]*)\"[^>]*?content-desc=\"([^\"]*)\"[^>]*?bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"", sys.stdin.read()):
    t, d, x1, y1, x2, y2 = m.groups()
    if t or d:
        print(f"{(int(x1)+int(x2))//2:5d} {(int(y1)+int(y2))//2:5d}  {t or d}")' ;;
  *) sed -n '2,16p' "$0" | sed 's/^# \{0,1\}//' ;;
esac
