#!/usr/bin/env bash
#
# notes-headless.sh — the core social loop between two amy accounts over the
# embedded `amy serve` relay (geode). No external relay, no other client.
#
# Alice and Bob live in separate $HOMEs (separate event stores), so every read
# Bob does of Alice's events really crosses the relay. We assert that:
#
#   1. `notes post` tags an inline #hashtag (`t`), and `notes feed --hashtag`
#      finds the note.
#   2. `notes show` renders Alice's note for Bob (fetched from the relay):
#      author, hashtag body span, seen_on.
#   3. `notes reply` emits a NIP-10 kind:1 (root marker + author p-tag);
#      `notes react` a kind:7 `+`; `notes repost` a kind:6;
#      `notes quote` a kind:1 with a `q` tag and a nostr:nevent in the text.
#   4. `notes thread` from Alice's nested reply lays out root → reply → nested
#      at depths 0/1/2, focus marked.
#   5. `notifications` shows Alice the reply, reaction, repost and the quote
#      (as a mention); `--type reaction` narrows to one.
#   6. `delete` refuses someone else's note (exit 1, forbidden) and deletes
#      your own: the relay then no longer serves it (`notes show` → not_found).
#
# Usage: ./notes-headless.sh [--no-build] [--port N]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-notes-headless"
LOG_DIR="$STATE_DIR/logs"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"
NO_BUILD=0
RELAY_HOST=127.0.0.2
RELAY_PORT=7787

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) NO_BUILD=1 ;;
    --port) RELAY_PORT="$2"; shift ;;
    -h|--help)
      sed -n '3,24p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
      exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"
RELAY_DATA="$STATE_DIR/relay"

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR" "$LOG_DIR" "$STATE_DIR/alice" "$STATE_DIR/bob"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"
# shellcheck source=../headless/helpers.sh
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

# Boolean flags go AFTER positionals: amy's parser reads `--flag VALUE`.
alice() { HOME="$STATE_DIR/alice" "$AMY_BIN" --account alice --secret-backend plaintext --json "$@" 2>>"$LOG_FILE"; }
bob() { HOME="$STATE_DIR/bob" "$AMY_BIN" --account bob --secret-backend plaintext --json "$@" 2>>"$LOG_FILE"; }

check() {
  # usage: check TEST_ID JSON JQ_EXPR [NOTE]
  local id="$1" json="$2" expr="$3" note="${4:-}"
  if jq -e "$expr" <<<"$json" >/dev/null 2>&1; then
    record_result "$id" pass "$note"
  else
    record_result "$id" fail "$(head -c 300 <<<"$json")"
  fi
}

banner "two accounts on $RELAY_URL"
for who in alice bob; do
  "$who" init >>"$LOG_FILE" || { record_result "$who-init" fail; exit 1; }
  "$who" relay nip65 clear >>"$LOG_FILE"
  "$who" relay add "$RELAY_URL" >>"$LOG_FILE"
  "$who" relay publish-lists >>"$LOG_FILE"
done
ALICE_HEX="$(alice whoami | jq -r .hex)"
BOB_HEX="$(bob whoami | jq -r .hex)"
record_result "setup" pass "alice=${ALICE_HEX:0:8} bob=${BOB_HEX:0:8}"

step "alice posts"
POST="$(alice notes post "hello #AmyTest from alice")"
PID="$(jq -r .event_id <<<"$POST")"
check post "$POST" '.kind == 1 and (.published_to | length) == 1'

step "bob shows alice's note (from the relay)"
SHOW="$(bob notes show "$PID")"
check show "$SHOW" \
  ".source == \"relays\" and .note.author.pubkey == \"$ALICE_HEX\" and (.note.hashtags | index(\"amytest\")) and (.note.body | map(.type) | index(\"hashtag\"))"

step "bob reads the hashtag feed"
FEED="$(bob notes feed --hashtag amytest)"
check feed-hashtag "$FEED" ".mode == \"hashtag\" and (.notes | map(.event_id) | index(\"$PID\"))"

step "bob replies, reacts, reposts, quotes"
REPLY="$(bob notes reply "$PID" "hi alice")"
RID="$(jq -r .event_id <<<"$REPLY")"
check reply "$REPLY" \
  ".kind == 1 and (.tags | any(.[0] == \"e\" and .[1] == \"$PID\" and .[3] == \"root\")) and (.tags | any(.[0] == \"p\" and .[1] == \"$ALICE_HEX\"))"
check react "$(bob notes react "$PID")" '.kind == 7 and .content == "+"'
check repost "$(bob notes repost "$PID")" ".kind == 6 and .target.event_id == \"$PID\""
check quote "$(bob notes quote "$PID" "look at this")" \
  ".kind == 1 and (.content | contains(\"nostr:nevent1\")) and (.tags | any(.[0] == \"q\" and .[1] == \"$PID\"))"

step "alice answers bob; bob reads the thread from the leaf"
NESTED="$(alice notes reply "$RID" "thanks bob")"
NID="$(jq -r .event_id <<<"$NESTED")"
THREAD="$(bob notes thread "$NID")"
check thread "$THREAD" \
  ".root_id == \"$PID\" and ([.notes[].event_id] == [\"$PID\", \"$RID\", \"$NID\"]) and ([.notes[].depth] == [0, 1, 2]) and .notes[2].is_focus"

step "alice's notifications"
NOTIF="$(alice notifications)"
check notifications "$NOTIF" \
  '[.notifications[].type] | (index("reply") and index("reaction") and index("repost") and index("mention"))'
check notifications-type "$(alice notifications --type reaction)" \
  ".count == 1 and .notifications[0].type == \"reaction\" and .notifications[0].from.pubkey == \"$BOB_HEX\""

step "delete"
bob delete "$PID" >>"$LOG_FILE"
RC=$?
[[ $RC -eq 1 ]] && record_result delete-forbidden pass || record_result delete-forbidden fail "exit=$RC"
check delete "$(alice delete "$PID")" ".deleted == [\"$PID\"] and (.deletions[0].published_to | length) == 1"
bob notes show "$PID" --refresh >>"$LOG_FILE"
RC=$?
[[ $RC -eq 1 ]] && record_result delete-honoured pass "relay no longer serves it" || record_result delete-honoured fail "exit=$RC"

print_summary
! grep -q $'\tfail\t' "$RESULTS_FILE"
