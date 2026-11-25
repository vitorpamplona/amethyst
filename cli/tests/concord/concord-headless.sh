#!/usr/bin/env bash
#
# concord-headless.sh — Concord chat verbs end to end, amy against amy on one loopback geode relay
# (`amy serve`), fresh keys every run, no public relay or server.
#
# What each member does to a message once it is posted, and what every member reads back through
# `amy concord read` (Quartz's chat fold: edits from the author only, deletes, moderation hides
# honored per the Control fold, reactions tallied once per reactor):
#
#   01 P creates a community; Q and R join through an invite link; Leo joins too
#   02 P defines a Moderator role (MANAGE_MESSAGES) and grants it to R
#   03 messages from P, Q and Leo read back in send order
#   04 Q edits its own message: everyone reads the new text, marked edited
#   05 R's edit of Q's message is refused (not_yours); readers would drop it anyway
#   06 reactions: Q and R react to P's message, one per reactor and key (+ is 👍)
#   07 Q withdraws its reaction (delete of its own kind 7): the tally drops it
#   08 Q deletes its own message: gone for every reader
#   09 Q's delete of P's message is refused (not_yours), and Q can't hide it (not_allowed)
#   10 R (moderator) hides Leo's message: gone for every reader, Leo included
#   11 R can't hide the owner's message (outranked): not_allowed
#   12 Leo leaves: a Guestbook LEAVE the owner's member list honors; the community is gone locally
#
# Usage: ./concord-headless.sh [--port N] [--no-build]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="${STATE_DIR:-$SCRIPT_DIR/state-concord-headless}"
LOG_DIR="$STATE_DIR/logs"
RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"
AMY_BIN="${AMY_BIN:-$REPO_ROOT/cli/build/install/amy/bin/amy}"
RELAY_PORT="${RELAY_PORT:-7783}"
NO_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port) RELAY_PORT="$2"; shift ;;
    --no-build) NO_BUILD=1 ;;
    -h|--help) sed -n '3,25p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR" "$LOG_DIR"
: >"$LOG_FILE"; : >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"
# shellcheck source=../headless/helpers.sh
source "$TESTS_DIR/headless/helpers.sh"

export LC_ALL=C.UTF-8
export NO_PROXY='*' no_proxy='*'
for t in jq; do command -v "$t" >/dev/null || { fail_msg "missing $t"; exit 2; }; done
if [[ "$NO_BUILD" -eq 0 ]]; then
  (cd "$REPO_ROOT" && ./gradlew -q :cli:installDist) >>"$LOG_FILE" 2>&1 || { fail_msg "amy build failed (see $LOG_FILE)"; exit 2; }
fi
[[ -x "$AMY_BIN" ]] || { fail_msg "amy not built: ./gradlew :cli:installDist"; exit 2; }

# The relay listens on a non-loopback local address and everyone names it by a hostname that amy's
# JVM resolves through a private hosts file: amy never dials a loopback relay it read from someone
# else's list (an invite link's relays), and nothing else resolves, so no public relay is reachable.
RELAY_IP="${RELAY_IP:-$(detect_routing_host || echo 127.0.0.2)}"
RELAY_NAME="${RELAY_NAME:-relay.concord-headless.test}"
RELAY_HOST="$RELAY_IP"
RELAY_URL="ws://$RELAY_IP:$RELAY_PORT"
RELAY_DATA="$STATE_DIR/relay"
printf '%s %s\n' "$RELAY_IP" "$RELAY_NAME" >"$STATE_DIR/hosts"
AMY_JAVA_OPTS="$(printf '%s' "${JAVA_TOOL_OPTIONS:-}" | sed -E 's/-D(https?|socks)\.(proxyHost|proxyPort|nonProxyHosts)=[^ ]*//g') -Djdk.net.hosts.file=$STATE_DIR/hosts"
NAMED_RELAY_URL="ws://$RELAY_NAME:$RELAY_PORT"
export AMY_BOOTSTRAP_RELAYS="$NAMED_RELAY_URL" AMY_LOCAL_ONLY=1

cleanup() {
  local rc=$?
  trap - EXIT INT TERM HUP
  stop_local_relay
  print_summary
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

banner "Concord chat verbs, amy vs amy on $NAMED_RELAY_URL ($RELAY_IP)"
start_local_relay
RELAY_URL="$NAMED_RELAY_URL"

AMY_HOME="$STATE_DIR/amy"
mkdir -p "$AMY_HOME"
LAST_ERR="$STATE_DIR/last.err"
declare -A HEX
amy_as() {
  local who="$1"; shift
  local rc
  HOME="$AMY_HOME" JAVA_TOOL_OPTIONS="$AMY_JAVA_OPTS" "$AMY_BIN" --account "$who" --secret-backend plaintext --json "$@" 2>"$LAST_ERR"; rc=$?
  { printf '$ amy[%s] %s (rc=%d)\n' "$who" "$*" "$rc"; grep -v '^Picked up' "$LAST_ERR"; } >>"$LOG_FILE"
  return $rc
}
err_code() { grep -v '^Picked up' "$LAST_ERR" | jq -r '.error // empty' 2>/dev/null | tail -1; }
for who in P Q R L; do
  HEX[$who]=$(amy_as "$who" init | jq -r .hex)
  amy_as "$who" relay add "$RELAY_URL" >/dev/null
  amy_as "$who" relay publish-lists >/dev/null
done

ok() { record_result "$1" pass "$2"; pass_msg "$1 — $2"; }
ko() { record_result "$1" fail "$2"; fail_msg "$1 — $2"; }
check() { if [[ $1 -eq 0 ]]; then ok "$2" "$3"; else ko "$2" "$4"; fi; }
read_as() { amy_as "$1" concord read "$C" general; }
row() { printf '%s' "$1" | jq -c --arg id "$2" '[.messages[] | select(.event_id == $id)][0] // empty'; }
rumor_id() { printf '%s' "$1" | jq -r --arg t "$2" '[.messages[] | select(.content == $t) | .event_id][0] // empty'; }

# 01 ----------------------------------------------------------------------------------------------
out=$(amy_as P concord create --name "Verbs Hall" --relay "$RELAY_URL")
C=$(printf '%s' "$out" | jq -r '.community_id // empty')
link=$(amy_as P concord invite "$C" | jq -r '.url // empty')
joined=0
for who in Q R L; do amy_as "$who" concord join "$link" >/dev/null && joined=$((joined + 1)); done
[[ -n "$C" && "$joined" -eq 3 ]]
check $? 01-create-and-join "community ${C:0:12}, Q R L joined by link" "create/join failed: $(err_code)"

# 02 ----------------------------------------------------------------------------------------------
role=$(amy_as P concord role "$C" Moderator 2 MANAGE_MESSAGES | jq -r '.role_id // empty')
amy_as P concord grant "$C" "${HEX[R]}" "$role" >/dev/null
holders=$(amy_as P concord roles "$C" | jq -c --arg r "${HEX[R]}" '[.grants[] | select(.member == $r) | .roles[]]')
[[ "$holders" == '["Moderator"]' ]]
check $? 02-moderator-role "R holds $holders" "R holds '$holders'"

# 03 ----------------------------------------------------------------------------------------------
amy_as P concord send "$C" general "p one" >/dev/null
amy_as Q concord send "$C" general "q one" >/dev/null
amy_as Q concord send "$C" general "q two" >/dev/null
amy_as L concord send "$C" general "leo one" >/dev/null
r=$(read_as R)
order=$(printf '%s' "$r" | jq -c '[.messages[].content]')
[[ "$order" == '["p one","q one","q two","leo one"]' ]]
check $? 03-send-order "R reads $order" "R reads $order"
P1=$(rumor_id "$r" "p one"); Q1=$(rumor_id "$r" "q one"); Q2=$(rumor_id "$r" "q two"); L1=$(rumor_id "$r" "leo one")

# 04 ----------------------------------------------------------------------------------------------
amy_as Q concord edit "$C" general "$Q1" "q one, edited" >/dev/null
r=$(read_as P)
[[ "$(row "$r" "$Q1" | jq -r '"\(.content)|\(.edited)"')" == "q one, edited|true" ]]
check $? 04-edit-own "P reads $(row "$r" "$Q1" | jq -c '{content, edited}')" "P reads $(row "$r" "$Q1")"

# 05 ----------------------------------------------------------------------------------------------
amy_as R concord edit "$C" general "$Q1" "rewritten by R" >/dev/null; rc=$?
[[ $rc -ne 0 && "$(err_code)" == "not_yours" ]]
check $? 05-edit-others-refused "refused: $(err_code)" "rc=$rc $(err_code)"

# 06 ----------------------------------------------------------------------------------------------
QR=$(amy_as Q concord react "$C" general "$P1" | jq -r '.rumor_id // empty')
amy_as R concord react "$C" general "$P1" "+" >/dev/null
amy_as R concord react "$C" general "$P1" "🤙" >/dev/null
r=$(read_as L)
tally=$(row "$r" "$P1" | jq -c '.reactions')
[[ "$(printf '%s' "$tally" | jq -r '.["👍"]')" == "2" && "$(printf '%s' "$tally" | jq -r '.["🤙"]')" == "1" ]]
check $? 06-reactions "Leo reads $tally on P's message" "Leo reads $tally"

# 07 ----------------------------------------------------------------------------------------------
amy_as Q concord delete "$C" general "$QR" >/dev/null
r=$(read_as L)
tally=$(row "$r" "$P1" | jq -c '.reactions')
[[ "$(printf '%s' "$tally" | jq -r '.["👍"]')" == "1" ]]
check $? 07-withdraw-reaction "Leo reads $tally" "Leo reads $tally"

# 08 ----------------------------------------------------------------------------------------------
amy_as Q concord delete "$C" general "$Q2" >/dev/null
r=$(read_as P)
[[ -z "$(row "$r" "$Q2")" ]]
check $? 08-delete-own "q two is gone for P" "P still reads $(row "$r" "$Q2")"

# 09 ----------------------------------------------------------------------------------------------
amy_as Q concord delete "$C" general "$P1" >/dev/null; d=$(err_code)
amy_as Q concord hide "$C" general "$P1" >/dev/null; h=$(err_code)
[[ "$d" == "not_yours" && "$h" == "not_allowed" ]]
check $? 09-member-cannot-remove-others "delete: $d, hide: $h" "delete: $d, hide: $h"

# 10 ----------------------------------------------------------------------------------------------
amy_as R concord hide "$C" general "$L1" >/dev/null; rc=$?
gone=0
for who in P Q L; do [[ -z "$(row "$(read_as "$who")" "$L1")" ]] && gone=$((gone + 1)); done
[[ $rc -eq 0 && "$gone" -eq 3 ]]
check $? 10-moderator-hide "R's hide honored by P, Q and Leo" "rc=$rc, gone for $gone of 3"

# 11 ----------------------------------------------------------------------------------------------
amy_as R concord hide "$C" general "$P1" >/dev/null; rc=$?
[[ $rc -ne 0 && "$(err_code)" == "not_allowed" ]]
check $? 11-hide-the-owner-refused "refused: $(err_code)" "rc=$rc $(err_code)"

# 12 ----------------------------------------------------------------------------------------------
out=$(amy_as L concord leave "$C")
left=$(printf '%s' "$out" | jq -r '"\(.left)|\(.announced)"')
amy_as L concord list | jq -e --arg c "$C" '[.communities[] | select(.community_id == $c)] | length == 0' >/dev/null; forgot=$?
members=$(amy_as P concord members "$C")
action=$(printf '%s' "$members" | jq -r --arg l "${HEX[L]}" '[.guestbook[] | select(.member == $l) | .action][0] // empty')
still=$(printf '%s' "$members" | jq -r --arg l "${HEX[L]}" '.members | index($l) != null')
[[ "$left" == "$C|true" && $forgot -eq 0 && "$action" == "leave" && "$still" == "false" ]]
check $? 12-leave "announced, forgotten locally; P's Guestbook reads Leo as '$action'" "leave: $left, forgotten rc=$forgot, P reads '$action', still listed: $still"
