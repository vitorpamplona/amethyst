#!/usr/bin/env bash
# Starts a packaged Amethyst on a Wayland-only session and fails unless it opens a native Wayland
# window. That proves the bundled runtime has the JetBrains Runtime's Wayland toolkit and the app
# picked it (desktopApp/.../platform/WaylandDisplay.kt), not XWayland.
#
# The session is sway on its headless backend with Xwayland turned off, so the app has no X11 to
# fall back to: if it reaches for X11 it dies, and if it draws natively sway lists its window with
# the app_id the app sets ("Amethyst", what the desktop entries match).
# Needs the sway package.
#
# Usage: wayland-smoke-test.sh <command that starts the app> [args...]
#   e.g. wayland-smoke-test.sh /opt/amethyst/bin/Amethyst
#        wayland-smoke-test.sh flatpak run com.vitorpamplona.amethyst.Amethyst
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "usage: $0 <app command> [args...]" >&2
  exit 2
fi

EXPECTED_APP_ID="${EXPECTED_APP_ID:-Amethyst}"
TIMEOUT_SECS=60

WORK="$(mktemp -d)"
chmod 700 "$WORK"
export XDG_RUNTIME_DIR="$WORK/runtime"
mkdir -m 700 "$XDG_RUNTIME_DIR"
printf 'xwayland disable\noutput HEADLESS-1 resolution 1280x800\n' > "$WORK/sway.conf"

SWAY_PID=""
APP_PID=""
cleanup() {
  [[ -n "$APP_PID" ]] && kill "$APP_PID" 2>/dev/null || true
  [[ -n "$SWAY_PID" ]] && kill "$SWAY_PID" 2>/dev/null || true
  wait 2>/dev/null || true
  [[ -n "${KEEP_LOG:-}" ]] && cp "$WORK/app.log" "$KEEP_LOG"
  rm -rf "$WORK"
}
trap cleanup EXIT

env -u DISPLAY WLR_BACKENDS=headless WLR_LIBINPUT_NO_DEVICES=1 WLR_RENDERER=pixman \
  sway -c "$WORK/sway.conf" > "$WORK/sway.log" 2>&1 &
SWAY_PID=$!

# Sway creates its IPC socket after the Wayland one: wait for both, or swaymsg can't ask it anything.
socket=""
ipc=""
for _ in $(seq 1 50); do
  socket="$(find "$XDG_RUNTIME_DIR" -maxdepth 1 -name 'wayland-*' -type s -print -quit)"
  ipc="$(find "$XDG_RUNTIME_DIR" -maxdepth 1 -name 'sway-ipc.*' -type s -print -quit)"
  [[ -n "$socket" && -n "$ipc" ]] && break
  sleep 0.2
done
if [[ -z "$socket" || -z "$ipc" ]]; then
  echo "FAIL: sway did not start" >&2
  cat "$WORK/sway.log" >&2
  exit 1
fi
export WAYLAND_DISPLAY="$(basename "$socket")"
export SWAYSOCK="$ipc"

# A throwaway profile, so the run neither reads nor writes a real one. A jpackage launcher hands its
# arguments to the app, not the JVM, so the property goes through JAVA_TOOL_OPTIONS.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:-} -Damethyst.dataDir=$WORK/profile"
env -u DISPLAY -u AMETHYST_X11 "$@" > "$WORK/app.log" 2>&1 &
APP_PID=$!

deadline=$((SECONDS + TIMEOUT_SECS))
while ((SECONDS < deadline)); do
  if ! kill -0 "$APP_PID" 2>/dev/null; then
    echo "FAIL: the app exited before opening a window" >&2
    tail -50 "$WORK/app.log" >&2
    exit 1
  fi
  # Captured first: `grep -q` on a pipe can exit before swaymsg finishes, and pipefail would read
  # that SIGPIPE as no match.
  tree="$(swaymsg -t get_tree 2>/dev/null || true)"
  if grep -q "\"app_id\": \"$EXPECTED_APP_ID\"" <<< "$tree"; then
    echo "PASS: native Wayland window with app_id $EXPECTED_APP_ID"
    exit 0
  fi
  sleep 1
done

echo "FAIL: no Wayland window with app_id $EXPECTED_APP_ID after ${TIMEOUT_SECS}s" >&2
swaymsg -t get_tree >&2 || true
tail -50 "$WORK/app.log" >&2
exit 1
