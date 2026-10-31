#!/usr/bin/env bash
#
# chess-ui.sh — chess between amy bots and the desktop app, on a relay this
# script starts itself (`amy serve`). Nothing reaches public relays: the app
# account is a fresh key whose only relay is the local one.
#
#   chess-ui.sh watch   two bots play each other, slowly; the app opens as a
#                       spectator. In Chess › Lobby (Global) the game appears
#                       and its moves stream in.
#   chess-ui.sh play    a bot opens a challenge and plays White; join it from
#                       the app (Chess › Lobby › Join as Black) and play.
#   chess-ui.sh stop    stop the bots, the relay and the app.
#
# Options: --no-build  --port N (7798)  --think-ms N (bot pause per move, 2500)
#          --engine random|minimax (minimax)  --screen WxHxD (passed to the app)
#
# The app runs headless through tools/desktop-run/run-headless.sh; drive it
# with tools/desktop-run/Drive.java on the DISPLAY it prints. On a machine with
# a display, run the app yourself instead with the printed environment.

set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-chess-ui"
LOG_DIR="$STATE_DIR/logs"
LOG_FILE="$LOG_DIR/run.log"
RESULTS_FILE="$STATE_DIR/results.tsv"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"
MODE="${1:-}"
[[ $# -gt 0 ]] && shift
NO_BUILD=0
RELAY_HOST=127.0.0.1
RELAY_PORT=7798
THINK_MS=2500
ENGINE=minimax
SCREEN="${AMETHYST_SCREEN:-}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) NO_BUILD=1 ;;
    --port) RELAY_PORT="$2"; shift ;;
    --think-ms) THINK_MS="$2"; shift ;;
    --engine) ENGINE="$2"; shift ;;
    --screen) SCREEN="$2"; shift ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"
RELAY_DATA="$STATE_DIR/relay"

stop_all() {
  for pid_file in "$STATE_DIR"/*.pid "$RELAY_DATA"/pid; do
    [[ -f "$pid_file" ]] || continue
    kill "$(cat "$pid_file")" 2>/dev/null
    rm -f "$pid_file"
  done
  "$REPO_ROOT/tools/desktop-run/run-headless.sh" stop >/dev/null 2>&1 || true
}

case "$MODE" in
  stop) stop_all; echo "stopped"; exit 0 ;;
  watch|play) ;;
  *) sed -n '3,22p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; exit 2 ;;
esac

stop_all
rm -rf "$STATE_DIR"
mkdir -p "$LOG_DIR" "$STATE_DIR"/{alice,bob,app}
: >"$LOG_FILE"
: >"$RESULTS_FILE"

source "$SCRIPT_DIR/../lib.sh"
source "$SCRIPT_DIR/../headless/helpers.sh"

command -v jq >/dev/null || { fail_msg "jq is required"; exit 1; }
if [[ $NO_BUILD -eq 0 ]]; then
  step "building amy (installDist)"
  (cd "$REPO_ROOT" && ./gradlew -q :cli:installDist) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "gradle :cli:installDist failed"; exit 1; }
fi

start_local_relay

amy_as() { local who="$1"; shift; HOME="$STATE_DIR/$who" "$AMY_BIN" --account "$who" --secret-backend plaintext --json "$@" 2>>"$LOG_FILE"; }

# The app's account: a fresh key, set up by amy so its relay list names only the local relay.
APP_NSEC="$("$AMY_BIN" --json key generate 2>>"$LOG_FILE" | jq -r .nsec)"
umask 077
printf '%s\n' "$APP_NSEC" >"$STATE_DIR/app.nsec"
amy_as app init --nsec "$APP_NSEC" >>"$LOG_FILE"
for who in alice bob app; do
  [[ "$who" == app ]] || amy_as "$who" init >>"$LOG_FILE"
  amy_as "$who" relay nip65 clear >>"$LOG_FILE"
  amy_as "$who" relay add "$RELAY_URL" >>"$LOG_FILE"
  amy_as "$who" relay publish-lists >>"$LOG_FILE"
done
amy_as alice profile edit --name "Alice (bot)" >>"$LOG_FILE" 2>&1 || true
amy_as bob profile edit --name "Bob (bot)" >>"$LOG_FILE" 2>&1 || true

GAME="$(amy_as alice chess challenge | jq -r .game_id)"
[[ -n "$GAME" && "$GAME" != null ]] || { fail_msg "could not open a challenge (see $LOG_FILE)"; exit 1; }

bot() {
  local who="$1"
  nohup env HOME="$STATE_DIR/$who" "$AMY_BIN" --account "$who" --secret-backend plaintext --json \
    chess play "$GAME" --engine "$ENGINE" --think-ms "$THINK_MS" --timeout 7200 \
    >"$LOG_DIR/$who-play.json" 2>"$LOG_DIR/$who-play.log" &
  echo "$!" >"$STATE_DIR/$who.pid"
}

bot alice
[[ "$MODE" == watch ]] && bot bob

step "starting the desktop app on $RELAY_URL"
export AMETHYST_LOCAL_RELAYS="$RELAY_URL"
[[ -n "$SCREEN" ]] && export AMETHYST_SCREEN="$SCREEN"
"$REPO_ROOT/tools/desktop-run/run-headless.sh" start --login-file "$STATE_DIR/app.nsec" | tail -2

cat <<EOF

  relay      $RELAY_URL
  game       $GAME
  bots       $(ls "$STATE_DIR"/*.pid 2>/dev/null | xargs -n1 basename | sed 's/.pid//' | tr '\n' ' ')(logs in $LOG_DIR)
  app login  $STATE_DIR/app.nsec, local relay $RELAY_URL

  $( [[ "$MODE" == watch ]] && echo "Open Chess › Lobby: the game is listed; open it to watch the moves arrive." \
                            || echo "Open Chess › Lobby › Join as Black, then play your moves against Alice (bot)." )
  Follow the bots:  tail -f $LOG_DIR/*-play.log
  Check the game:   HOME=$STATE_DIR/app $AMY_BIN --account app --secret-backend plaintext chess show $GAME
  Stop everything:  $0 stop
EOF
