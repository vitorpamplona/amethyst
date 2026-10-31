#!/usr/bin/env bash
#
# chess-headless.sh — Jester (kind 30) chess end to end between amy accounts
# over the embedded `amy serve` relay (geode). `amy chess` builds its games and
# moves with the same commons code as the app's board, so a pass here means the
# app's protocol path works across a real relay. Alice, Bob and Carol live in
# separate $HOMEs, so every read crosses the relay. We assert:
#
#   1. Fool's mate, move by move: Bob takes the open seat by moving in it, the
#      mate ends the game 0-1, and Carol (a spectator) sees exactly that game.
#   2. The rules hold: a move out of turn, an illegal move, a move by someone
#      with no seat, and a move after the end are all refused.
#   3. Bot against bot: a challenger playing Black, a minimax bot and a random
#      bot play a whole game (or --plies half-moves); both players and the
#      spectator end with the same move list, every move legal.
#   4. A direct challenge seats only the player it names: Carol cannot take it,
#      Bob holds the black seat before moving.
#   5. Draws: an offer rides on a move, the offerer cannot accept it, the other
#      player can, and a move instead declines it. Nobody can claim a game whose
#      opponent moved minutes ago.
#
# Usage: cli/tests/chess/chess-headless.sh [--no-build] [--port N] [--plies N]
#
# To watch or play a game in the desktop app on the same relay, see chess-ui.sh.

set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-chess-headless"
LOG_DIR="$STATE_DIR/logs"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"
NO_BUILD=0
RELAY_HOST=127.0.0.2
RELAY_PORT=7797
PLIES=40

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) NO_BUILD=1 ;;
    --port) RELAY_PORT="$2"; shift ;;
    --plies) PLIES="$2"; shift ;;
    -h|--help)
      sed -n '3,23p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
      exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"
RELAY_DATA="$STATE_DIR/relay"

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR" "$LOG_DIR" "$STATE_DIR/alice" "$STATE_DIR/bob" "$STATE_DIR/carol"
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
[[ -x "$AMY_BIN" ]] || { fail_msg "amy binary missing at $AMY_BIN"; exit 1; }

trap stop_local_relay EXIT
start_local_relay

amy_as() { local who="$1"; shift; HOME="$STATE_DIR/$who" "$AMY_BIN" --account "$who" --secret-backend plaintext --json "$@"; }
alice() { amy_as alice "$@" 2>>"$LOG_FILE"; }
bob() { amy_as bob "$@" 2>>"$LOG_FILE"; }
carol() { amy_as carol "$@" 2>>"$LOG_FILE"; }

check() {
  local id="$1" json="$2" expr="$3" note="${4:-}"
  if jq -e "$expr" <<<"$json" >/dev/null 2>&1; then
    record_result "$id" pass "$note"
  else
    record_result "$id" fail "$(head -c 300 <<<"$json")"
  fi
}

# refused ID CODE WHO ARGS…: passes when WHO's amy command exits non-zero with error CODE.
refused() {
  local id="$1" code="$2" out
  shift 2
  if out="$(amy_as "$@" 2>&1)"; then
    record_result "$id" fail "accepted: $(head -c 200 <<<"$out")"
  elif grep -q "$code" <<<"$out"; then
    record_result "$id" pass "$code"
  else
    record_result "$id" fail "wrong error: $(head -c 200 <<<"$out")"
  fi
}

banner "three accounts on $RELAY_URL"
for who in alice bob carol; do
  "$who" init >>"$LOG_FILE" || { record_result "$who-init" fail; exit 1; }
  "$who" relay nip65 clear >>"$LOG_FILE"
  "$who" relay add "$RELAY_URL" >>"$LOG_FILE"
  "$who" relay publish-lists >>"$LOG_FILE"
done
ALICE="$(alice whoami | jq -r .hex)"
BOB="$(bob whoami | jq -r .hex)"
CAROL="$(carol whoami | jq -r .hex)"
record_result "setup" pass "alice=${ALICE:0:8} bob=${BOB:0:8} carol=${CAROL:0:8}"

# --- 1. fool's mate -------------------------------------------------------------
step "fool's mate"
G1="$(alice chess challenge | jq -r .game_id)"
check mate-open "$(bob chess show "$G1")" \
  ".status == \"open\" and .white == \"$ALICE\" and .black == null and .my_color == \"black\""
alice chess move "$G1" f2 f3 >>"$LOG_FILE"
bob chess move "$G1" e7 e5 >>"$LOG_FILE"
alice chess move "$G1" g2 g4 >>"$LOG_FILE"
bob chess move "$G1" d8 h4 >>"$LOG_FILE"
MATE="$(carol chess show "$G1")"
check mate-result "$MATE" \
  ".status == \"finished\" and .result == \"0-1\" and .white == \"$ALICE\" and .black == \"$BOB\" and .my_color == null" \
  "seen by a spectator"
check mate-moves "$MATE" '.moves == ["f3","e5","g4","Qh4#"]'
check mate-same-for-white "$(alice chess show "$G1")" '.result == "0-1" and .my_color == "white" and .plies == 4'

# --- 2. the rules ---------------------------------------------------------------
step "refused moves"
G2="$(alice chess challenge | jq -r .game_id)"
alice chess move "$G2" e2 e4 >>"$LOG_FILE"
refused rule-out-of-turn not_your_turn alice chess move "$G2" d2 d4
bob chess move "$G2" e7 e5 >>"$LOG_FILE"
refused rule-illegal illegal_move alice chess move "$G2" e4 e6
refused rule-spectator not_your_turn carol chess move "$G2" d7 d5
refused rule-after-the-end not_your_turn alice chess move "$G1" a2 a3

# --- 3. bot against bot ---------------------------------------------------------
step "minimax (white) against random (black), $PLIES plies"
G3="$(alice chess challenge --color black | jq -r .game_id)"
bob chess play "$G3" --engine minimax --depth 2 --seed 1 --max-plies "$PLIES" --timeout 600 >"$STATE_DIR/bob-play.json" &
BOB_PID=$!
sleep 2
alice chess play "$G3" --engine random --seed 2 --max-plies "$PLIES" --timeout 600 >"$STATE_DIR/alice-play.json"
ALICE_RC=$?
wait "$BOB_PID"
BOB_RC=$?
if [[ $ALICE_RC -eq 0 && $BOB_RC -eq 0 ]]; then
  record_result bots-finished pass "$(jq -c '{plies, result}' "$STATE_DIR/alice-play.json")"
else
  record_result bots-finished fail "alice=$ALICE_RC bob=$BOB_RC"
fi
SEEN="$(carol chess show "$G3")"
check bots-seats "$SEEN" ".white == \"$BOB\" and .black == \"$ALICE\""
check bots-length "$SEEN" "(.plies >= $PLIES or .status == \"finished\") and (.moves | length) == .plies" \
  "every move replays: plies counts the moves the engine accepted"
for who in alice bob; do
  check "bots-$who-agrees" "$("$who" chess show "$G3")" ".moves == $(jq -c .moves <<<"$SEEN")"
done

# --- 4. direct challenge --------------------------------------------------------
step "a direct challenge seats only its opponent"
G4="$(alice chess challenge --opponent "$BOB" | jq -r .game_id)"
check direct-bob-seat "$(bob chess show "$G4")" ".my_color == \"black\" and .black == \"$BOB\" and .plies == 0"
check direct-carol-none "$(carol chess show "$G4")" '.my_color == null'
refused direct-carol-refused not_a_player carol chess play "$G4" --timeout 5

# --- 5. draws and claims --------------------------------------------------------
step "draw offers"
G5="$(alice chess challenge | jq -r .game_id)"
alice chess move "$G5" e2 e4 >>"$LOG_FILE"
bob chess move "$G5" e7 e5 --offer-draw >>"$LOG_FILE"
check draw-offered "$(alice chess show "$G5")" '.draw_offered_by == "black" and .status == "in_progress"'
refused draw-not-own no_draw_offer bob chess accept-draw "$G5"
refused claim-too-early too_early bob chess claim "$G5"
alice chess accept-draw "$G5" >>"$LOG_FILE"
check draw-agreed "$(carol chess show "$G5")" '.status == "finished" and .result == "1/2-1/2" and .termination == "draw_agreement"' "seen by a spectator"

G6="$(alice chess challenge | jq -r .game_id)"
alice chess move "$G6" e2 e4 --offer-draw >>"$LOG_FILE"
bob chess move "$G6" e7 e5 >>"$LOG_FILE"
check draw-declined-by-moving "$(alice chess show "$G6")" '.draw_offered_by == null and .status == "in_progress"'
refused draw-gone no_draw_offer bob chess accept-draw "$G6"

print_summary
! grep -q $'\tfail\t' "$RESULTS_FILE"
