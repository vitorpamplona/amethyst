#!/usr/bin/env bash
#
# relaygroup-headless.sh — end-to-end check of `amy relaygroup` (NIP-29) against
# an embedded relay (`amy serve`, which boots :geode). No external relay binary.
#
# What it proves:
#   1. relaygroup create publishes 9007 + 9002 and returns a group_id.
#   2. relaygroup message publishes a kind-9 chat scoped to the group.
#   3. relaygroup join publishes 9021 AND adds the group to the kind:10009 list.
#   4. relaygroup list reads that list back (private NIP-44 item decrypts).
#   5. relaygroup browse emits well-formed JSON.
#
# geode is not a NIP-29 relay (it does not sign 39000-39003), so browse/info
# return empty — that's a relay capability, not a client bug, and is out of
# scope here. We assert the client's publish + list-maintenance round-trip.
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-relaygroup-headless"
LOG_DIR="$STATE_DIR/logs"
LOG_FILE="$LOG_DIR/run.log"
RESULTS_FILE="$STATE_DIR/results"
AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"
PORT="${PORT:-7466}"
NO_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)     PORT="$2"; shift ;;
    --no-build) NO_BUILD=1 ;;
    -h|--help)  sed -n '3,17p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR" "$LOG_DIR"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"

amy_a() { HOME="$STATE_DIR" "$AMY_BIN" --account A --secret-backend plaintext --json "$@"; }

SERVE_PID=""
cleanup() {
  [[ -n "$SERVE_PID" ]] && kill "$SERVE_PID" 2>/dev/null
  wait "$SERVE_PID" 2>/dev/null
}
trap cleanup EXIT

if [[ "$NO_BUILD" -eq 0 ]]; then
  step "building :cli:installDist"
  ( cd "$REPO_ROOT" && ./gradlew -q :cli:installDist ) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "installDist failed (see $LOG_FILE)"; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { fail_msg "amy binary missing at $AMY_BIN"; exit 1; }

banner "relaygroup headless (embedded relay on port $PORT)"

step "init account A"
amy_a init >>"$LOG_FILE" 2>&1 || { fail_msg "init failed"; exit 1; }

step "boot embedded relay (amy serve)"
HOME="$STATE_DIR" "$AMY_BIN" --account A --secret-backend plaintext serve --port "$PORT" >"$LOG_DIR/serve.log" 2>&1 &
SERVE_PID=$!
for _ in $(seq 1 30); do grep -q "listening" "$LOG_DIR/serve.log" 2>/dev/null && break; sleep 0.5; done
grep -q "listening" "$LOG_DIR/serve.log" || { fail_msg "relay did not start"; exit 1; }
RELAY="ws://127.0.0.1:$PORT"

amy_a relay add "$RELAY" >>"$LOG_FILE" 2>&1 || { fail_msg "relay add failed"; exit 1; }

# 1. create
step "relaygroup create"
CREATE=$(amy_a relaygroup create "$RELAY" --name "Headless Group" --about "hi" --closed 2>>"$LOG_FILE")
printf '%s\n' "$CREATE" >>"$LOG_FILE"
GID=$(printf '%s' "$CREATE" | jq -r '.group_id // empty')
if [[ -n "$GID" && "$(printf '%s' "$CREATE" | jq -r '.published')" == "true" ]]; then
  pass_msg "create → $GID"; record_result create pass
else
  fail_msg "create did not publish"; record_result create fail
fi

# 2. message
step "relaygroup message"
MSG=$(amy_a relaygroup message "$RELAY" "$GID" "gm from the harness" 2>>"$LOG_FILE")
if [[ "$(printf '%s' "$MSG" | jq -r '.published')" == "true" && "$(printf '%s' "$MSG" | jq -r '.kind')" == "9" ]]; then
  pass_msg "message → kind 9 published"; record_result message pass
else
  fail_msg "message did not publish"; record_result message fail
fi

# 3. join (updates kind:10009)
step "relaygroup join"
JOIN=$(amy_a relaygroup join "$RELAY" "$GID" 2>>"$LOG_FILE")
if [[ "$(printf '%s' "$JOIN" | jq -r '.published')" == "true" && "$(printf '%s' "$JOIN" | jq -r '.listed')" == "true" ]]; then
  pass_msg "join → published + listed"; record_result join pass
else
  fail_msg "join did not publish/list"; record_result join fail
fi

# 4. list (reads kind:10009 back, decrypting the private item)
step "relaygroup list"
LIST=$(amy_a relaygroup list 2>>"$LOG_FILE")
if printf '%s' "$LIST" | jq -e --arg g "$GID" '.groups[] | select(.group_id == $g)' >/dev/null 2>&1; then
  pass_msg "list contains the joined group"; record_result list pass
else
  fail_msg "list missing the joined group: $LIST"; record_result list fail
fi

# 5. browse emits well-formed JSON (empty against geode)
step "relaygroup browse"
BROWSE=$(amy_a relaygroup browse "$RELAY" --timeout 3 2>>"$LOG_FILE")
if printf '%s' "$BROWSE" | jq -e '.count != null' >/dev/null 2>&1; then
  pass_msg "browse emitted well-formed JSON"; record_result browse pass
else
  fail_msg "browse JSON malformed: $BROWSE"; record_result browse fail
fi

print_summary
# results are "<id>\t<pass|fail|skip>"; any fail row → nonzero exit.
awk -F'\t' '$2=="fail"{f=1} END{exit f}' "$RESULTS_FILE" && exit 0 || exit 1
