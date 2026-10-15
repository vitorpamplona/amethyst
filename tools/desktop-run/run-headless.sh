#!/usr/bin/env bash
#
# Runs the desktop app (the same shared UI Android renders) on a virtual X display, so an agent in a
# headless container can launch it, look at it and click through it. See README.md next to this file.
#
#   tools/desktop-run/run-headless.sh start [--fresh]   # build + launch, wait for the first paint
#   tools/desktop-run/run-headless.sh shot [file.png]   # screenshot (default: $RUN_DIR/shot.png)
#   tools/desktop-run/run-headless.sh logs              # tail of the app log, errors first
#   tools/desktop-run/run-headless.sh stop              # stop the app (Xvfb keeps running)
#
# Env: AMETHYST_DISPLAY (default :99), AMETHYST_SCREEN (default 1600x1000x24),
#      AMETHYST_RUN_DIR (default /tmp/amethyst-desktop-run: log, profile, screenshots),
#      AMETHYST_START_TIMEOUT seconds (default 900: a cold Gradle build is slow),
#      AMETHYST_RUN_ARGS extra Gradle arguments, e.g. "-Damethyst.update.devCheck=true".

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DRIVE="$REPO_ROOT/tools/desktop-run/Drive.java"
DISPLAY_NUM="${AMETHYST_DISPLAY:-:99}"
SCREEN="${AMETHYST_SCREEN:-1600x1000x24}"
RUN_DIR="${AMETHYST_RUN_DIR:-/tmp/amethyst-desktop-run}"
TIMEOUT="${AMETHYST_START_TIMEOUT:-900}"
LOG="$RUN_DIR/app.log"
DATA_DIR="$RUN_DIR/profile"
PID_FILE="$RUN_DIR/gradle.pid"

mkdir -p "$RUN_DIR"
export DISPLAY="$DISPLAY_NUM"
# Xvfb has no GL: start on the software renderer instead of failing over from OpenGL.
export SKIKO_RENDER_API="${SKIKO_RENDER_API:-SOFTWARE}"
# Anchored on the java binary so pgrep/pkill never match a shell whose command line mentions the class.
APP_PATTERN='^[^ ]*java .*AmethystDesktopKt'

drive() { java "$DRIVE" "$@" 2>&1 | grep -v '^Picked up JAVA_TOOL_OPTIONS'; }

app_running() { pgrep -f "$APP_PATTERN" > /dev/null; }

ensure_xvfb() {
  if xdpy_alive; then return; fi
  command -v Xvfb > /dev/null || { echo "Xvfb is not installed (apt-get install xvfb)" >&2; exit 1; }
  Xvfb "$DISPLAY_NUM" -screen 0 "$SCREEN" -nolisten tcp > "$RUN_DIR/xvfb.log" 2>&1 &
  for _ in $(seq 1 50); do xdpy_alive && return; sleep 0.1; done
  echo "Xvfb did not start on $DISPLAY_NUM; see $RUN_DIR/xvfb.log" >&2
  exit 1
}

# The X socket exists once Xvfb accepts connections.
xdpy_alive() { [ -S "/tmp/.X11-unix/X${DISPLAY_NUM#:}" ] && pgrep -f "^[^ ]*Xvfb $DISPLAY_NUM" > /dev/null; }

stop_app() {
  pkill -f "$APP_PATTERN" 2> /dev/null
  [ -f "$PID_FILE" ] && kill "$(cat "$PID_FILE")" 2> /dev/null
  rm -f "$PID_FILE"
  for _ in $(seq 1 50); do app_running || return 0; sleep 0.2; done
  pkill -9 -f "$APP_PATTERN" 2> /dev/null
}

print_errors() {
  # Uncaught exceptions, crash reports and Compose errors, most useful first.
  grep -nE 'Exception|Error:|FATAL|\bE/|Caused by' "$LOG" | grep -vE 'JAVA_TOOL_OPTIONS' | head -40
}

case "${1:-}" in
  start)
    [ "${2:-}" = "--fresh" ] && rm -rf "$DATA_DIR"
    # The window sizes itself to the screen only when no bounds were saved; keep it reproducible.
    rm -f "$DATA_DIR/window-bounds.properties"
    stop_app
    ensure_xvfb
    : > "$LOG"
    # Redirected as a whole, so no process keeps the caller's stdout open: `start | tail` returns.
    (cd "$REPO_ROOT" && exec nohup ./gradlew :desktopApp:run --console=plain \
      "-Damethyst.dataDir=$DATA_DIR" -Damethyst.debug=true ${AMETHYST_RUN_ARGS:-}) >> "$LOG" 2>&1 < /dev/null &
    echo $! > "$PID_FILE"
    echo "Building and launching (log: $LOG, profile: $DATA_DIR, DISPLAY=$DISPLAY_NUM)..."
    deadline=$((SECONDS + TIMEOUT))
    until app_running; do
      if ! kill -0 "$(cat "$PID_FILE")" 2> /dev/null; then
        echo "Gradle exited before the app started:" >&2
        tail -40 "$LOG" >&2
        exit 1
      fi
      [ $SECONDS -ge $deadline ] && { echo "Timed out waiting for the app JVM" >&2; tail -20 "$LOG" >&2; exit 1; }
      sleep 2
    done
    if ! drive waitpaint 120000; then
      echo "The app JVM started but never painted a window:" >&2
      print_errors >&2
      exit 1
    fi
    sleep 3 # first frames settle (fonts, theme)
    if ! app_running; then
      echo "The app painted and then died:" >&2
      print_errors >&2
      exit 1
    fi
    drive shot "$RUN_DIR/shot.png"
    echo "Running. Look at $RUN_DIR/shot.png; drive it with: DISPLAY=$DISPLAY_NUM java $DRIVE <cmd>"
    ;;
  shot)
    drive shot "${2:-$RUN_DIR/shot.png}"
    ;;
  logs)
    app_running && echo "app: running" || echo "app: NOT running"
    echo "--- errors ---"
    print_errors
    echo "--- tail ---"
    tail -30 "$LOG"
    ;;
  stop)
    stop_app
    echo "stopped"
    ;;
  *)
    sed -n '3,14p' "$0"
    exit 2
    ;;
esac
