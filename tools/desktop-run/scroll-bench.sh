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
#
# Anything after `--` goes to Gradle, for example the benchmark's own settings:
#   -Damethyst.scrollBench.notches=250      wheel notches per pass
#   -Damethyst.scrollBench.intervalMs=16    one notch every N ms
#   -Damethyst.scrollBench.waitSeconds=30   how long the feed gets to load before the first pass
#   -Damethyst.scrollBench.at=0.5,0.6       where to scroll, as fractions of the window
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
extra=()
while [ $# -gt 0 ]; do
  case "$1" in
    --runs) runs="$2"; shift 2 ;;
    --warmup) warmup="$2"; shift 2 ;;
    --login) login="$2"; shift 2 ;;
    --out) out="$2"; shift 2 ;;
    --) shift; extra=("$@"); break ;;
    *) sed -n '3,27p' "$0"; exit 2 ;;
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

total=$((warmup + runs))
for i in $(seq 1 "$total"); do
  if [ "$i" -le "$warmup" ]; then dir="$out/warmup-$i"; else dir="$out/run-$((i - warmup))"; fi
  rm -rf "$dir"
  echo "Boot $i of $total -> $dir"
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
