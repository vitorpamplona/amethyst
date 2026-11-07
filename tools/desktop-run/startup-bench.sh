#!/usr/bin/env bash
#
# Measures how long the desktop app takes, from the process starting, to show a home feed whose top
# no longer changes. Boots the app's JVM directly (no Gradle in the timing) on the headless display
# run-headless.sh uses, with StartupTimeline on, N times, and prints the milestones of each boot and
# when the top of the feed settled (startup_settle.py has the definition).
#
#   tools/desktop-run/startup-bench.sh capture             # while run-headless.sh's app runs: keep its java command
#   tools/desktop-run/startup-bench.sh run [N] [seconds]   # N boots (default 5) of `seconds` each (default 90)
#   tools/desktop-run/startup-bench.sh report [files...]   # re-read saved timelines
#   tools/desktop-run/startup-bench.sh classes             # one boot: rewrite StartupWarmUp's class list
#
# The account: AMETHYST_BENCH_KEY_FILE (default $RUN_DIR/profile/signup.nsec, what
# `run-headless.sh start --signup` leaves), logged in transiently as --login-file does. Unset it and
# set AMETHYST_BENCH_SAVED=1 to boot the profile's saved account instead.
#
# After a code change: `run-headless.sh stop`, `run-headless.sh start` (it builds), `capture`, `stop`, `run`.
#
# Env: AMETHYST_DISPLAY, AMETHYST_SCREEN, AMETHYST_RUN_DIR as in run-headless.sh;
#      AMETHYST_BENCH_LABEL names the output folder ($RUN_DIR/bench/<label>, default a timestamp);
#      AMETHYST_BENCH_TOP how many top posts must hold still (default 5);
#      AMETHYST_BENCH_JVM_ARGS extra JVM options for every boot, e.g. an AppCDS archive;
#      AMETHYST_BENCH_CMD_FILE another captured command line (default $RUN_DIR/app-cmd), for A/B builds.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HEADLESS="$REPO_ROOT/tools/desktop-run/run-headless.sh"
SETTLE="$REPO_ROOT/tools/desktop-run/startup_settle.py"
DISPLAY_NUM="${AMETHYST_DISPLAY:-:99}"
SCREEN="${AMETHYST_SCREEN:-1600x1000x24}"
RUN_DIR="${AMETHYST_RUN_DIR:-/tmp/amethyst-desktop-run}"
DATA_DIR="$RUN_DIR/profile"
CMD_FILE="${AMETHYST_BENCH_CMD_FILE:-$RUN_DIR/app-cmd}"
TOP="${AMETHYST_BENCH_TOP:-5}"
APP_PATTERN='^[^ ]*java .*AmethystDesktopKt'

export DISPLAY="$DISPLAY_NUM"
export SKIKO_RENDER_API="${SKIKO_RENDER_API:-SOFTWARE}"

app_pid() { pgrep -f "$APP_PATTERN" | head -1; }

ensure_xvfb() {
  if [ -S "/tmp/.X11-unix/X${DISPLAY_NUM#:}" ] && pgrep -f "^[^ ]*Xvfb $DISPLAY_NUM" > /dev/null; then return; fi
  Xvfb "$DISPLAY_NUM" -screen 0 "$SCREEN" -nolisten tcp > "$RUN_DIR/xvfb.log" 2>&1 &
  for _ in $(seq 1 50); do [ -S "/tmp/.X11-unix/X${DISPLAY_NUM#:}" ] && return; sleep 0.1; done
  echo "Xvfb did not start on $DISPLAY_NUM" >&2
  exit 1
}

case "${1:-}" in
  capture)
    pid="$(app_pid)"
    [ -n "$pid" ] || { echo "No app running: run-headless.sh start first" >&2; exit 1; }
    # NUL-separated, so arguments with spaces (the proxy's nonProxyHosts) survive.
    cat "/proc/$pid/cmdline" > "$CMD_FILE"
    (cd "/proc/$pid/cwd" && pwd -P) > "$CMD_FILE.cwd"
    echo "Saved the app's command line to $CMD_FILE"
    ;;
  run)
    runs="${2:-5}"
    secs="${3:-90}"
    [ -s "$CMD_FILE" ] || { echo "No command captured: see 'capture'" >&2; exit 1; }
    [ -z "$(app_pid)" ] || { echo "An app is running: run-headless.sh stop first" >&2; exit 1; }
    key_file="${AMETHYST_BENCH_KEY_FILE:-$DATA_DIR/signup.nsec}"
    unset AMETHYST_LOGIN AMETHYST_SIGNUP
    if [ "${AMETHYST_BENCH_SAVED:-0}" != "1" ]; then
      [ -r "$key_file" ] || { echo "Cannot read the key file $key_file" >&2; exit 1; }
      AMETHYST_LOGIN="$(tr -d '[:space:]' < "$key_file")"
      export AMETHYST_LOGIN
    fi
    out="$RUN_DIR/bench/${AMETHYST_BENCH_LABEL:-$(date +%Y%m%d-%H%M%S)}"
    mkdir -p "$out"
    ensure_xvfb
    mapfile -d '' cmd < "$CMD_FILE"
    cwd="$(cat "$CMD_FILE.cwd")"
    files=()
    for i in $(seq 1 "$runs"); do
      tl="$out/run$i.tsv"
      rm -f "$DATA_DIR/window-bounds.properties"
      # The timeline switches go right after the java binary, ahead of the main class.
      (cd "$cwd" && AMETHYST_STARTUP_TIMELINE=1 AMETHYST_STARTUP_TIMELINE_FILE="$tl" \
        exec "${cmd[0]}" ${AMETHYST_BENCH_JVM_ARGS:-} "${cmd[@]:1}") > "$out/run$i.log" 2>&1 < /dev/null &
      sleep "$secs"
      pkill -f "$APP_PATTERN" 2> /dev/null
      for _ in $(seq 1 50); do [ -z "$(app_pid)" ] && break; sleep 0.2; done
      pkill -9 -f "$APP_PATTERN" 2> /dev/null
      files+=("$tl")
      python3 "$SETTLE" --top "$TOP" "$tl" | tail -1
      sleep 2
    done
    echo "--- $out"
    python3 "$SETTLE" --top "$TOP" "${files[@]}"
    ;;
  classes)
    # The classes a boot loads from the app's jars until the logged-in window is up, in load order,
    # for StartupWarmUp to preload on spare cores. A stale list only loses speed: missing names are
    # skipped and new classes load as usual. Regenerate after large changes to the start or the shell.
    [ -s "$CMD_FILE" ] || { echo "No command captured: see 'capture'" >&2; exit 1; }
    list="$REPO_ROOT/desktopApp/src/jvmMain/resources/startup/preload-classes.txt"
    log="$RUN_DIR/classload.log"
    rm -f "$log"
    AMETHYST_BENCH_JVM_ARGS="${AMETHYST_BENCH_JVM_ARGS:-} -Damethyst.startupWarmUp=false -Xlog:class+load=info:file=$log:uptime" \
      AMETHYST_BENCH_LABEL=classes "$0" run 1 "${2:-20}" > /dev/null 2>&1
    python3 - "$log" "$RUN_DIR/bench/classes/run1.tsv" "$list" << 'PY'
import re, sys
log, timeline, out = sys.argv[1:4]
until = None
for line in open(timeline):
    parts = line.rstrip("\n").split("\t")
    if len(parts) >= 3 and parts[1] == "mark" and parts[2] == "logged-in-effects":
        until = int(parts[0]) / 1000 + 0.5
if until is None:
    sys.exit("The boot never reached the logged-in window: no list written")
seen, names = set(), []
for line in open(log):
    m = re.match(r"\[(\d+\.\d+)s\] (\S+) source: (\S+)", line)
    if not m or float(m.group(1)) > until or "/" in m.group(2):
        continue
    if m.group(3).startswith(("file:", "jar:")) and m.group(2) not in seen:
        seen.add(m.group(2))
        names.append(m.group(2))
with open(out, "w") as f:
    f.write("# Written by tools/desktop-run/startup-bench.sh classes; read by StartupWarmUp.\n")
    f.write("\n".join(names) + "\n")
print(f"{len(names)} classes to {out}")
PY
    ;;
  report)
    shift
    python3 "$SETTLE" --top "$TOP" "$@"
    ;;
  *)
    sed -n '3,20p' "$0"
    exit 2
    ;;
esac
