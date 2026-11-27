#!/usr/bin/env bash
# Takes the App Store screenshots on the simulators App Store Connect asks for: a 6.9-inch iPhone
# and a 13-inch iPad (TARGETED_DEVICE_FAMILY is iPhone and iPad, so both are required). macOS only.
#
#   iosApp/store/screenshots.sh path/to/Amethyst.app ["iPhone device name"] ["iPad device name"]
#
# Build the app for the simulator first (Xcode, scheme Amethyst, any iOS Simulator; the .app is under
# DerivedData/…/Build/Products/Debug-iphonesimulator/). The script boots each simulator, sets a
# clean status bar, installs and opens the app, then saves a screenshot each time you press Enter,
# until you type q. Navigate the app on the simulator between shots. Shots land in
# iosApp/store/screenshots/<device>/ (not committed: upload them in App Store Connect).
set -euo pipefail

APP="${1:?usage: screenshots.sh path/to/Amethyst.app [iphone-name] [ipad-name]}"
IPHONE="${2:-iPhone 17 Pro Max}"
IPAD="${3:-iPad Pro 13-inch (M5)}"
BUNDLE_ID="com.vitorpamplona.amethyst.ios"
OUT="$(cd "$(dirname "$0")" && pwd)/screenshots"

udid_of() {
  xcrun simctl list devices available -j |
    python3 -c 'import json,sys; name=sys.argv[1]; d=json.load(sys.stdin)["devices"]; print(next((x["udid"] for r in d.values() for x in r if x["name"]==name), ""))' "$1"
}

shoot() {
  local name="$1" udid
  udid=$(udid_of "$name")
  if [ -z "$udid" ]; then
    echo "No available simulator named \"$name\". These exist:" >&2
    xcrun simctl list devices available | grep -E "iPhone|iPad" >&2
    exit 1
  fi
  xcrun simctl boot "$udid" 2>/dev/null || true
  xcrun simctl bootstatus "$udid" -b >/dev/null
  open -a Simulator --args -CurrentDeviceUDID "$udid"
  xcrun simctl status_bar "$udid" override --time 9:41 --dataNetwork wifi --wifiBars 3 \
    --cellularMode active --cellularBars 4 --batteryState charged --batteryLevel 100
  xcrun simctl install "$udid" "$APP"
  xcrun simctl launch "$udid" "$BUNDLE_ID" >/dev/null

  local dir="$OUT/${name// /_}" n=1
  mkdir -p "$dir"
  echo "== $name: press Enter to take a screenshot, q then Enter to move on"
  while true; do
    read -r answer
    [ "$answer" = "q" ] && break
    local file
    file=$(printf '%s/%02d.png' "$dir" "$n")
    xcrun simctl io "$udid" screenshot --type=png "$file" >/dev/null
    echo "saved $file"
    n=$((n + 1))
  done
  xcrun simctl status_bar "$udid" clear
}

shoot "$IPHONE"
shoot "$IPAD"
echo "Done: $OUT"
