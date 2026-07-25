#!/usr/bin/env bash
#
# job-loop.sh — self-contained headless test for the Buzz agent-job loop.
#
# Two `amy` accounts (alice = requester, bot = agent) talk through an embedded
# relay (`amy serve`, i.e. geode — no external binary). Exercises the whole
# drive-an-agent loop end to end:
#
#   alice: buzz job request  (kind-43001, targeting the bot)
#   bot:   buzz agent serve --once   (accept 43002 → progress 43003 →
#                                     run --exec → result 43004)
#   alice: buzz job show     (BuzzJobAggregator folds it to state=completed)
#
# It also proves the permission gate: a responder whose --accept-from allowlist
# excludes alice handles NOTHING; only an allowlisted requester is obeyed.
#
# Usage: ./job-loop.sh [--port N] [--no-build]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-job-loop"
AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"

PORT=7799
BUILD=1
while [[ $# -gt 0 ]]; do
    case "$1" in
        --port) PORT="$2"; shift 2 ;;
        --no-build) BUILD=0; shift ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done
RELAY="ws://127.0.0.1:$PORT"

if [[ $BUILD -eq 1 ]]; then
    echo "> building amy…" >&2
    (cd "$REPO_ROOT" && ./gradlew -q :cli:installDist) || { echo "build failed" >&2; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { echo "amy not built at $AMY_BIN (drop --no-build)" >&2; exit 1; }

rm -rf "$STATE_DIR"; mkdir -p "$STATE_DIR"
REQ_HOME="$STATE_DIR/alice"; AGENT_HOME="$STATE_DIR/bot"; mkdir -p "$REQ_HOME" "$AGENT_HOME"
RELAY_LOG="$STATE_DIR/relay.log"
PASS=0; FAIL=0
RELAY_PID=""
cleanup() { [[ -n "$RELAY_PID" ]] && kill "$RELAY_PID" 2>/dev/null; }
trap cleanup EXIT

run_req()   { HOME="$REQ_HOME"   "$AMY_BIN" --account alice --secret-backend plaintext --json "$@" 2>/dev/null; }
run_agent() { HOME="$AGENT_HOME" "$AMY_BIN" --account bot   --secret-backend plaintext --json "$@" 2>/dev/null; }
jkey() {
    python3 -c '
import sys, json
v = json.load(sys.stdin).get("'"$1"'", "")
print(str(v).lower() if isinstance(v, bool) else v)'
}
check() { # check <label> <condition-desc> <actual> <expected>
    if [[ "$3" == "$4" ]]; then echo "  ✓ PASS $1 ($3)" >&2; PASS=$((PASS+1));
    else echo "  ✗ FAIL $1: expected [$4] got [$3]" >&2; FAIL=$((FAIL+1)); fi
}
contains() { # contains <label> <haystack> <needle>
    if [[ "$2" == *"$3"* ]]; then echo "  ✓ PASS $1" >&2; PASS=$((PASS+1));
    else echo "  ✗ FAIL $1: [$2] has no [$3]" >&2; FAIL=$((FAIL+1)); fi
}

run_req init >/dev/null; run_agent init >/dev/null
ALICE=$(run_req whoami | jkey hex)
BOT=$(run_agent whoami | jkey hex)
echo "> alice=$ALICE bot=$BOT" >&2

HOME="$AGENT_HOME" "$AMY_BIN" --account bot --secret-backend plaintext serve --port "$PORT" >"$RELAY_LOG" 2>&1 &
RELAY_PID=$!
for _ in $(seq 1 60); do grep -q "relay up" "$RELAY_LOG" && break; sleep 0.5; done
grep -q "relay up" "$RELAY_LOG" || { echo "relay never came up" >&2; cat "$RELAY_LOG" >&2; exit 1; }

echo "> 1. alice files a job targeting the bot" >&2
JOB=$(run_req buzz job request "$RELAY" "hello from alice" --agent "$BOT")
JOB_ID=$(echo "$JOB" | jkey job_id)
check "request published" _ "$(echo "$JOB" | jkey published)" "true"
[[ -n "$JOB_ID" ]] && { echo "  ✓ PASS got job id $JOB_ID" >&2; PASS=$((PASS+1)); } || { echo "  ✗ FAIL no job id" >&2; FAIL=$((FAIL+1)); }

echo "> 2. UNAUTHORIZED responder (allowlist excludes alice) handles nothing" >&2
UNAUTH=$(run_agent buzz agent serve "$RELAY" --exec 'cat' --accept-from "$BOT" --once)
check "allowlist blocks non-listed requester" _ "$(echo "$UNAUTH" | jkey handled)" "0"

echo "> 3. authorized responder handles it, piping task text through --exec" >&2
AUTH=$(run_agent buzz agent serve "$RELAY" --exec 'printf "PR opened for: "; cat' --accept-from "$ALICE" --once)
check "authorized responder handled 1" _ "$(echo "$AUTH" | jkey handled)" "1"

echo "> 4. alice reads back the folded job" >&2
SHOW=$(run_req buzz job show "$RELAY" "$JOB_ID")
check "state" _ "$(echo "$SHOW" | jkey state)" "completed"
check "requester" _ "$(echo "$SHOW" | jkey requester)" "$ALICE"
check "agent" _ "$(echo "$SHOW" | jkey agent)" "$BOT"
contains "result carries exec stdout with piped stdin" "$(echo "$SHOW" | jkey result)" "PR opened for: hello from alice"

echo "" >&2
echo "> RESULTS: $PASS passed, $FAIL failed" >&2
[[ $FAIL -eq 0 ]] && exit 0 || exit 1
