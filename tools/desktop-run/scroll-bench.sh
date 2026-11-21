#!/usr/bin/env bash
#
# Scroll benchmark of the desktop app's home feed (ScrollBenchmark in desktopApp): boots the app on
# the virtual display N times, lets each boot scroll its feed with the mouse wheel (down, back up,
# down again) while it records every frame, then prints the median of each pass across the boots.
#
#   tools/desktop-run/scroll-bench.sh [options] [-- extra Gradle arguments]
#
# Options:
#   --runs N        measured boots (default 3)
#   --warmup N      boots run first and left out of the summary (default 1): the first boot after a
#                   build fills the class archive and the image and video disk caches
#   --login <key>   the account whose feed scrolls (default: Vitor's npub, ~260 follows); a public
#                   key only, as run-headless.sh --login
#   --out <dir>     where the reports go (default: $AMETHYST_RUN_DIR/scroll-bench/<time>)
#   --app <dir>     benchmark a packaged image instead of the dev `run`: the folder holding
#                   bin/Amethyst, e.g. desktopApp/build/compose/binaries/main-release/app/Amethyst
#                   (createReleaseDistributable, trainReleaseAotCache). Arguments after `--` then go
#                   to its JVM through _JAVA_OPTIONS, e.g. -XX:AOTMode=on to require its AOT cache
#
# Anything after `--` goes to Gradle, for example the benchmark's own settings:
#   -Damethyst.scrollBench.notches=250      wheel notches per pass
#   -Damethyst.scrollBench.intervalMs=16    one notch every N ms
#   -Damethyst.scrollBench.waitSeconds=30   how long the feed gets to load before the first pass
#   -Damethyst.scrollBench.at=0.5,0.6       where to scroll, as fractions of the window
#   -Damethyst.scrollBench.screen=PICTURES  a drawer entry (NavBarItem name) to open and scroll
#                                           instead of the home feed
#
# Compare two builds by running this on each and handing both report folders to the summary:
#   python3 tools/desktop-run/scroll_bench_summary.py <before-dir> <after-dir>
#
# Env: what run-headless.sh reads (AMETHYST_DISPLAY, AMETHYST_SCREEN, AMETHYST_RUN_DIR, ...).

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUN_HEADLESS="$REPO_ROOT/tools/desktop-run/run-headless.sh"
SUMMARY="$REPO_ROOT/tools/desktop-run/scroll_bench_summary.py"
RUN_DIR="${AMETHYST_RUN_DIR:-/tmp/amethyst-desktop-run}"
APP_PATTERN='^[^ ]*java .*AmethystDesktopKt'

runs=3
warmup=1
login="npub1gcxzte5zlkncx26j68ez60fzkvtkm9e0vrwdcvsjakxf9mu9qewqlfnj5z"
out="$RUN_DIR/scroll-bench/$(date +%Y%m%d-%H%M%S)"
app=""
extra=()
while [ $# -gt 0 ]; do
  case "$1" in
    --runs) runs="$2"; shift 2 ;;
    --warmup) warmup="$2"; shift 2 ;;
    --login) login="$2"; shift 2 ;;
    --out) out="$2"; shift 2 ;;
    --app) app="$2"; shift 2 ;;
    --) shift; extra=("$@"); break ;;
    *) awk 'NR >= 3 && /^#/ { sub(/^# ?/, ""); print; next } NR >= 3 { exit }' "$0"; exit 2 ;;
  esac
done

# The report folder goes to the app inside one -D argument, which run-headless.sh word-splits.
case "$out" in
  *[[:space:]]*) echo "--out must not contain spaces: $out" >&2; exit 2 ;;
esac

# How long one boot may take once the window is up: the wait, then three passes of notches x
# interval each, plus their pauses, screenshots and a slow first pass (180 s).
wait_seconds=30
notches=250
interval_ms=16
for arg in "${extra[@]+"${extra[@]}"}"; do
  case "$arg" in
    -Damethyst.scrollBench.waitSeconds=*) wait_seconds="${arg#*=}" ;;
    -Damethyst.scrollBench.notches=*) notches="${arg#*=}" ;;
    -Damethyst.scrollBench.intervalMs=*) interval_ms="${arg#*=}" ;;
  esac
done
boot_timeout=$((wait_seconds + 3 * notches * interval_ms / 1000 + 180))

mkdir -p "$out" || exit 1
echo "Reports: $out"

if [ -n "$app" ]; then
  [ -x "$app/bin/Amethyst" ] || { echo "No launcher at $app/bin/Amethyst" >&2; exit 2; }
  # The virtual display run-headless.sh uses, started the same way when it isn't up.
  export DISPLAY="${AMETHYST_DISPLAY:-:99}"
  # Up means its socket exists and its Xvfb runs: a socket alone is left over from one that exited.
  xdpy_alive() { [ -S "/tmp/.X11-unix/X${DISPLAY#:}" ] && pgrep -f "^[^ ]*Xvfb $DISPLAY" > /dev/null; }
  if ! xdpy_alive; then
    command -v Xvfb > /dev/null || { echo "Xvfb is not installed (apt-get install xvfb)" >&2; exit 1; }
    # No Xvfb runs on this display, so its socket and lock are left over: remove them, so the socket
    # that appears next is the new server's, listening.
    rm -f "/tmp/.X11-unix/X${DISPLAY#:}" "/tmp/.X${DISPLAY#:}-lock"
    Xvfb "$DISPLAY" -screen 0 "${AMETHYST_SCREEN:-1600x1000x24}" -nolisten tcp > "$out/xvfb.log" 2>&1 &
    for _ in $(seq 1 50); do xdpy_alive && break; sleep 0.1; done
    xdpy_alive || { echo "Xvfb did not start on $DISPLAY:" >&2; cat "$out/xvfb.log" >&2; exit 1; }
  fi
fi

# One boot of the packaged image at $app, reporting to $1; returns once the report is written or
# the app is gone.
boot_app() {
  local dir="$1" log="$2"
  # The image's own launcher, as a user starts it; the benchmark's settings reach its JVM through
  # _JAVA_OPTIONS, after the ones the environment already sets.
  AMETHYST_LOGIN="$login" SKIKO_RENDER_API="${SKIKO_RENDER_API:-SOFTWARE}" \
    _JAVA_OPTIONS="${_JAVA_OPTIONS:-} -Damethyst.scrollBench=$dir -Damethyst.dataDir=$RUN_DIR/profile-app ${extra[*]+"${extra[*]}"}" \
    "$app/bin/Amethyst" > "$log" 2>&1 &
  local pid=$!
  local deadline=$((SECONDS + boot_timeout + 120))
  until [ -f "$dir/summary.tsv" ] || [ -f "$dir/error.txt" ]; do
    if ! kill -0 "$pid" 2> /dev/null; then
      echo "  the app exited without a report; its log: $log" >&2
      tail -5 "$log" >&2
      break
    fi
    if [ $SECONDS -ge $deadline ]; then
      echo "  no report after $((boot_timeout + 120)) s" >&2
      break
    fi
    sleep 2
  done
  # It exits by itself after the report; this only cleans up a run that went wrong.
  for _ in $(seq 1 30); do kill -0 "$pid" 2> /dev/null || break; sleep 1; done
  kill "$pid" 2> /dev/null
}

total=$((warmup + runs))
for i in $(seq 1 "$total"); do
  if [ "$i" -le "$warmup" ]; then dir="$out/warmup-$i"; else dir="$out/run-$((i - warmup))"; fi
  rm -rf "$dir"
  echo "Boot $i of $total -> $dir"
  if [ -n "$app" ]; then
    boot_app "$dir" "$out/app-$i.log"
    [ -f "$dir/error.txt" ] && { echo "  the benchmark failed:" >&2; head -5 "$dir/error.txt" >&2; }
    continue
  fi
  if ! AMETHYST_RUN_ARGS="-Damethyst.scrollBench=$dir ${extra[*]+"${extra[*]}"}" "$RUN_HEADLESS" start --login "$login" > "$out/boot-$i.log" 2>&1; then
    echo "  the app did not start; see $out/boot-$i.log" >&2
    tail -20 "$out/boot-$i.log" >&2
    exit 1
  fi
  deadline=$((SECONDS + boot_timeout))
  until [ -f "$dir/summary.tsv" ] || [ -f "$dir/error.txt" ]; do
    if ! pgrep -f "$APP_PATTERN" > /dev/null; then
      echo "  the app exited without a report; its log: $RUN_DIR/app.log" >&2
      break
    fi
    if [ $SECONDS -ge $deadline ]; then
      echo "  no report after ${boot_timeout} s" >&2
      break
    fi
    sleep 2
  done
  # The app exits by itself once it wrote the report; this only cleans up a run that went wrong.
  "$RUN_HEADLESS" stop > /dev/null 2>&1
  cp "$RUN_DIR/app.log" "$out/app-$i.log" 2> /dev/null
  [ -f "$dir/error.txt" ] && { echo "  the benchmark failed:" >&2; head -5 "$dir/error.txt" >&2; }
done

python3 "$SUMMARY" "$out"
