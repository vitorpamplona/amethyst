#!/usr/bin/env bash
#
# The scroll benchmark (scroll-bench.sh) on drawer screens, one after another: each boot opens a
# screen (-Damethyst.scrollBench.screen), scrolls it, and, with --profile, records the UI thread's
# CPU time during the warm passes with JFR's CPU-time sampler. Then screen_survey_report.py
# tabulates the screens and their hotspots.
#
#   tools/desktop-run/screen-survey.sh [options] [SCREEN ...]
#
# SCREENs are NavBarItem names (HOME, PICTURES, DISCOVER, ...); none runs every feed screen below.
#
# Options:
#   --out <dir>     where the reports go (default: $AMETHYST_RUN_DIR/screen-survey/<time>)
#   --runs N        measured boots per screen (default 1)
#   --profile       record a CPU profile of each screen's warm passes (JDK 25's jcmd on PATH); it
#                   costs a few percent of fps, alike on every screen
#
# Single boots swing by a few fps with a live feed: confirm a slow screen with --runs 2 or more.

set -uo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUN_DIR="${AMETHYST_RUN_DIR:-/tmp/amethyst-desktop-run}"
FEED_SCREENS="HOME MESSAGES VIDEO DISCOVER NOTIFICATIONS PROFILE MY_LISTS BOOKMARKS WEB_BOOKMARKS FRONT_PAGE INTEREST_SETS FAVORITE_ALGO_FEEDS COMMUNITIES ARTICLES PICTURES GIT_REPOSITORIES NIPS HIGHLIGHTS SOFTWARE_APPS CALENDARS SHORTS MUSIC_TRACKS MUSIC_PLAYLISTS PODCAST_EPISODES PODCASTS PUBLIC_CHATS RELAY_GROUPS FOLLOW_PACKS LIVE_STREAMS NESTS LONGS POLLS BADGES PRODUCTS EMOJI_SETS WORKOUTS GEOCACHES"

out="$RUN_DIR/screen-survey/$(date +%Y%m%d-%H%M%S)"
runs=1
profile=false
screens=()
while [ $# -gt 0 ]; do
  case "$1" in
    --out) out="$2"; shift 2 ;;
    --runs) runs="$2"; shift 2 ;;
    --profile) profile=true; shift ;;
    -*) awk 'NR >= 3 && /^#/ { sub(/^# ?/, ""); print; next } NR >= 3 { exit }' "$0"; exit 2 ;;
    *) screens+=("$1"); shift ;;
  esac
done
[ ${#screens[@]} -eq 0 ] && read -r -a screens <<< "$FEED_SCREENS"
if $profile && ! command -v jcmd > /dev/null; then echo "--profile needs jcmd (a JDK 25) on PATH" >&2; exit 2; fi

mkdir -p "$out" || exit 1
echo "Reports: $out"
for screen in "${screens[@]}"; do
  dir="$out/$screen"
  rm -rf "$dir"; mkdir -p "$dir"
  echo "== $screen"
  args=(); [ "$screen" != HOME ] && args=(-Damethyst.scrollBench.screen="$screen")
  : > "$RUN_DIR/app.log" 2> /dev/null
  "$REPO_ROOT/tools/desktop-run/scroll-bench.sh" --runs "$runs" --warmup 0 --out "$dir" -- "${args[@]+"${args[@]}"}" > "$dir/bench.out" 2>&1 &
  bench=$!
  if $profile; then
    # The warm passes start when the first one ends.
    until grep -q "Pass down:" "$RUN_DIR/app.log" 2> /dev/null || ! kill -0 "$bench" 2> /dev/null; do sleep 0.1; done
    pid=$(pgrep -n -f AmethystDesktopKt)
    if [ -n "$pid" ] && grep -q "Pass down:" "$RUN_DIR/app.log" 2> /dev/null; then
      jcmd "$pid" JFR.start name=warm duration=12s filename="$dir/warm.jfr" "jdk.CPUTimeSample#enabled=true" "jdk.CPUTimeSample#throttle=2ms" > "$dir/jfr.log" 2>&1
    else
      echo "  $screen: no first pass to profile" >&2
    fi
  fi
  wait "$bench"
  grep -E "exited|failed|no report" "$dir/bench.out" | head -3
done

python3 "$REPO_ROOT/tools/desktop-run/screen_survey_report.py" "$out"
