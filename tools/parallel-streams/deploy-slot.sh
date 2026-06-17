#!/usr/bin/env bash
#
# deploy-slot.sh — build, install, and launch the current branch's debug build
# on the running emulator, as its own "slot" so it sits alongside other branches
# instead of overwriting them.
#
# The slot is derived from the git branch by amethyst/build.gradle.kts
# (resolveSlot). This script never re-implements that logic: it asks Gradle for
# the resolved applicationId via :amethyst:printDebugAppId. So whatever Gradle
# would install, this launches — one source of truth.
#
# Usage:
#   tools/parallel-streams/deploy-slot.sh [--no-launch] [-- <extra gradle args>]
#
# Honors:
#   ANDROID_SERIAL   target a specific emulator/device (adb + gradle both respect it)
#   AMETHYST_SLOT    force a slot regardless of branch (passed through to Gradle)
#
set -euo pipefail

LAUNCH=1
GRADLE_EXTRA=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-launch) LAUNCH=0; shift ;;
    --) shift; GRADLE_EXTRA=("$@"); break ;;
    *) echo "deploy-slot: unknown arg '$1'" >&2; exit 2 ;;
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
if [[ -z "$ADB" ]]; then
  echo "deploy-slot: adb not found (set ANDROID_HOME or put adb on PATH)" >&2
  exit 1
fi

# Bail early with a clear message if no emulator/device is connected.
if ! "$ADB" get-state >/dev/null 2>&1; then
  echo "deploy-slot: no device/emulator connected (start one, or set ANDROID_SERIAL)" >&2
  exit 1
fi

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
echo "▶ branch '$BRANCH' → building :amethyst:installPlayDebug ..."
./gradlew :amethyst:installPlayDebug "${GRADLE_EXTRA[@]}"

APP_ID="$(./gradlew -q :amethyst:printDebugAppId "${GRADLE_EXTRA[@]}" | tail -n 1)"
echo "✔ installed $APP_ID"

if [[ "$LAUNCH" -eq 1 ]]; then
  # monkey with a single LAUNCHER intent is the activity-agnostic way to start the app.
  "$ADB" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null
  echo "✔ launched $APP_ID on ${ANDROID_SERIAL:-default device}"
fi
