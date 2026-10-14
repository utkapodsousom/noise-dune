#!/usr/bin/env bash
#
# Regenerates everything the storefront wants to look at: the 512x512 icon and
# the screenshots. Both are derived rather than collected, for the same reason
# the launcher icon is generated — an asset nobody can rebuild is an asset that
# quietly stops matching the app.
#
#   tools/store-assets.sh            icon and screenshots
#   tools/store-assets.sh icon       the icon alone, no device needed
#
# Screenshots need a running emulator or phone with the app installed. Taps are
# resolved against the accessibility tree by label, not by coordinates, so this
# survives a different screen size and tells you what it could not find instead
# of tapping empty space.

set -euo pipefail

cd "$(dirname "$0")/.."

OUT=store/assets
SHOTS=$OUT/screenshots
PKG=${PKG:-ru.noisedune.dev}
ACTIVITY=ru.noisedune.MainActivity

# The clock in the app's header is the system clock, and a store screenshot of a
# sleep app showing midday undoes the picture. Asia/Irkutsk is UTC+8: it puts a
# European working afternoon into the evening without anybody having to set a
# clock by hand.
TIMEZONE=${TIMEZONE:-Asia/Irkutsk}

icon() {
    python3 docs/icon.py --store
}

# --------------------------------------------------------------------------
# Talking to the device
# --------------------------------------------------------------------------
dump() {
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null
    adb shell cat /sdcard/ui.xml
}

# Centre of the first node whose $1 attribute is exactly $2, as "x y".
find_node() {
    local attr=$1 value=$2 bounds
    bounds=$(dump | tr '<' '\n' | grep -F "$attr=\"$value\"" | head -1 |
             grep -oE 'bounds="\[[0-9]+,[0-9]+\]\[[0-9]+,[0-9]+\]"' || true)
    if [ -z "$bounds" ]; then
        echo "no node with $attr=\"$value\" on screen" >&2
        return 1
    fi
    echo "$bounds" | grep -oE '[0-9]+' |
        paste -d' ' - - - - | awk '{print int(($1+$3)/2), int(($2+$4)/2)}'
}

tap() {
    local point
    point=$(find_node "$1" "$2")
    # shellcheck disable=SC2086
    adb shell input tap $point
    sleep "${3:-2}"
}

shot() {
    mkdir -p "$SHOTS"
    adb exec-out screencap -p > "$SHOTS/$1.png"
    echo "shot:  $SHOTS/$1.png"
}

# A storefront screenshot should carry the app, not the state of whatever device
# took it: no unread badges, no half-charged battery, no carrier name.
demo_status_bar() {
    adb shell settings put global sysui_demo_allowed 1
    local demo="am broadcast -a com.android.systemui.demo -e command"
    adb shell "$demo enter" > /dev/null
    adb shell "$demo clock -e hhmm 2241" > /dev/null
    adb shell "$demo battery -e level 72 -e plugged false" > /dev/null
    adb shell "$demo network -e wifi hide -e mobile hide -e carriernetworkchange hide" > /dev/null
    adb shell "$demo notifications -e visible false" > /dev/null
    adb shell "$demo status -e volume hide -e bluetooth hide" > /dev/null
}

screenshots() {
    adb get-state > /dev/null || { echo "no device; start the emulator first" >&2; exit 1; }
    adb shell pm path "$PKG" > /dev/null || {
        echo "$PKG is not installed; run ./gradlew :app:installDebug" >&2; exit 1; }

    adb shell setprop persist.sys.timezone "$TIMEZONE"
    demo_status_bar

    # From a clean slate every time, so the first-run notice and the permission
    # prompt appear where this script expects them rather than at random.
    adb shell pm clear "$PKG" > /dev/null
    adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS
    adb shell am start -n "$PKG/$ACTIVITY" > /dev/null
    sleep 5

    tap text Понятно        # the hearing-safety notice, shown once
    shot 01-idle

    tap content-desc Включить 13   # 10s fade-in, plus a moment to settle
    shot 02-playing

    tap text 45%            # the metadata line is what opens the sheet
    shot 03-settings

    tap text "60 мин"
    adb shell input keyevent KEYCODE_BACK
    sleep 2
    shot 04-timer

    # There is deliberately no fifth shot of the notification shade, though
    # background playback is the thing it would show. An emulator's shade keeps
    # its own undismissable notices — set a screen lock, serial console enabled
    # — and "Очистить все" does not touch them, so the frame arrives with two
    # pieces of somebody else's system in it. A debug build also names itself
    # «Шум Дюн (dev)» there, which is the one screen in the app where the label
    # shows. Worth adding later from a release build on a real phone; the
    # description carries the claim until then.

    check
}

# --------------------------------------------------------------------------
# What the store will reject
# --------------------------------------------------------------------------
check() {
    python3 - "$OUT" <<'PY'
import os, sys
from PIL import Image

root = sys.argv[1]
shots = os.path.join(root, "screenshots")
ok = True


def fail(msg):
    global ok
    print(f"FAIL {msg}", file=sys.stderr)
    ok = False


icon = os.path.join(root, "icon-512.png")
if os.path.exists(icon):
    image = Image.open(icon)
    if image.size != (512, 512):
        fail(f"icon is {image.size[0]}x{image.size[1]}, the store wants 512x512")
    if image.mode not in ("RGB", "L"):
        fail(f"icon mode {image.mode} carries alpha; the store wants no transparent edges")
    if os.path.getsize(icon) > 3 * 1024 * 1024:
        fail("icon is over the 3 MB limit")

files = sorted(f for f in os.listdir(shots) if f.endswith(".png")) if os.path.isdir(shots) else []
if len(files) < 3:
    fail(f"{len(files)} screenshots; the store wants at least 3")
for name in files:
    path = os.path.join(shots, name)
    image = Image.open(path)
    size = os.path.getsize(path)
    w, h = image.size
    if max(w, h) > 3840 or min(w, h) > 2160:
        fail(f"{name} is {w}x{h}, over the 2160x3840 limit")
    if size > 3 * 1024 * 1024:
        fail(f"{name} is {size / 1024 / 1024:.1f} MB, over the 3 MB phone limit")
    print(f"check: {name} {w}x{h} {size / 1024:.0f} KB")

sys.exit(0 if ok else 1)
PY
}

case "${1:-all}" in
    icon)  icon ;;
    shots) screenshots ;;
    check) check ;;
    all)   icon; screenshots ;;
    *)     echo "usage: $0 [all|icon|shots|check]" >&2; exit 2 ;;
esac
