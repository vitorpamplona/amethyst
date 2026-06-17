#!/usr/bin/env bash
#
# deploy.sh — build, install, and launch the debug app on the connected
# emulator/device, for whichever worktree/branch you run it from.
#
# Single-install: there is one debug package (com.vitorpamplona.amethyst.debug),
# so deploying a different stream REPLACES whatever was installed before. You
# test streams one at a time.
#
# Usage:
#   tools/streams/deploy.sh [--play|--fdroid] [--no-launch]
#
# Honors ANDROID_SERIAL to target a specific emulator (adb + gradle both respect it).
#
set -euo pipefail

FLAVOR="Play"
LAUNCH=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --play)      FLAVOR="Play"; shift ;;
    --fdroid)    FLAVOR="Fdroid"; shift ;;
    --no-launch) LAUNCH=0; shift ;;
    *) echo "deploy: unknown arg '$1'" >&2; exit 2 ;;
  esac
done

REPO_ROOT="$(git rev-parse --show-toplevel)"
cd "$REPO_ROOT"

# Locate adb: PATH first, then the usual SDK locations.
ADB="$(command -v adb || true)"
if [[ -z "$ADB" ]]; then
  for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/Library/Android/sdk"; do
    if [[ -n "$d" && -x "$d/platform-tools/adb" ]]; then ADB="$d/platform-tools/adb"; break; fi
  done
fi
[[ -n "$ADB" ]] || { echo "deploy: adb not found (set ANDROID_HOME or put adb on PATH)" >&2; exit 1; }

if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "deploy: no device/emulator connected (start one, or set ANDROID_SERIAL)" >&2
  exit 1
fi

PKG="com.vitorpamplona.amethyst.debug"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
echo "▶ branch '$BRANCH' → :amethyst:install${FLAVOR}Debug ..."
./gradlew ":amethyst:install${FLAVOR}Debug"
echo "✔ installed $PKG (replacing any previous stream)"

if [[ "$LAUNCH" -eq 1 ]]; then
  # monkey with a single LAUNCHER intent is the activity-agnostic way to start the app.
  "$ADB" shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null
  echo "✔ launched $PKG on ${ANDROID_SERIAL:-default device}"
fi
