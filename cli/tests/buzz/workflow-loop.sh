#!/usr/bin/env bash
#
# workflow-loop.sh — self-contained headless test for the Buzz workflow loop.
#
# Buzz's *source-confirmed* structured-work + human-approval primitive (kinds
# 30620 def / 46020 trigger / 46030-46031 grant-deny / 46001-46007 lifecycle),
# driven end to end through an embedded relay (`amy serve`, i.e. geode — no
# external binary). Three `amy` accounts:
#
#   alice = requester (triggers the workflow, kind-46020)
#   bot   = the runner (`amy buzz workflow run`; does agent work, posts the
#           46010 approval gate, then on grant runs --on-approve → 46005)
#   carol = the approver (publishes 46030 grant / 46031 deny)
#
# Because the run id IS the trigger event id (and doubles as the approval
# token), carol can grant/deny without any extra token bookkeeping.
#
#   1. alice triggers  → run_id
#   2. bot run --once  → agent work → 46010 gate (state=awaiting_approval)
#   3. carol approves  → 46030 (d = run_id)
#   4. bot run --once  → resolves the gate → --on-approve → 46005 completed
#   5. alice show      → WorkflowRunAggregator folds it to state=completed + PR
#   6. deny path: a second run, carol denies → state=denied (work discarded)
#
# Usage: ./workflow-loop.sh [--port N] [--no-build]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-workflow-loop"
AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"

PORT=7788
BUILD=1
while [[ $# -gt 0 ]]; do
    case "$1" in
        --port) PORT="$2"; shift 2 ;;
        --no-build) BUILD=0; shift ;;
        *) echo "unknown arg: $1" >&2; exit 2 ;;
    esac
done
RELAY="ws://127.0.0.1:$PORT"
CHANNEL="3f2504e0-4f89-41d3-9a0c-0305e82c3301"
WFID="agent-build"

if [[ $BUILD -eq 1 ]]; then
    echo "> building amy…" >&2
    (cd "$REPO_ROOT" && ./gradlew -q :cli:installDist) || { echo "build failed" >&2; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { echo "amy not built at $AMY_BIN (drop --no-build)" >&2; exit 1; }

rm -rf "$STATE_DIR"; mkdir -p "$STATE_DIR"
REQ_HOME="$STATE_DIR/alice"; BOT_HOME="$STATE_DIR/bot"; APV_HOME="$STATE_DIR/carol"
mkdir -p "$REQ_HOME" "$BOT_HOME" "$APV_HOME"
RELAY_LOG="$STATE_DIR/relay.log"
PASS=0; FAIL=0
RELAY_PID=""
cleanup() { [[ -n "$RELAY_PID" ]] && kill "$RELAY_PID" 2>/dev/null; }
trap cleanup EXIT

run_req()   { HOME="$REQ_HOME" "$AMY_BIN" --account alice --secret-backend plaintext --json "$@" 2>/dev/null; }
run_bot()   { HOME="$BOT_HOME" "$AMY_BIN" --account bot   --secret-backend plaintext --json "$@" 2>/dev/null; }
run_apv()   { HOME="$APV_HOME" "$AMY_BIN" --account carol --secret-backend plaintext --json "$@" 2>/dev/null; }
jkey() {
    python3 -c '
import sys, json
v = json.load(sys.stdin).get("'"$1"'", "")
print(str(v).lower() if isinstance(v, bool) else v)'
}
check() { # check <label> <actual> <expected>
    if [[ "$2" == "$3" ]]; then echo "  ✓ PASS $1 ($2)" >&2; PASS=$((PASS+1));
    else echo "  ✗ FAIL $1: expected [$3] got [$2]" >&2; FAIL=$((FAIL+1)); fi
}
contains() { # contains <label> <haystack> <needle>
    if [[ "$2" == *"$3"* ]]; then echo "  ✓ PASS $1" >&2; PASS=$((PASS+1));
    else echo "  ✗ FAIL $1: [$2] has no [$3]" >&2; FAIL=$((FAIL+1)); fi
}

run_req init >/dev/null; run_bot init >/dev/null; run_apv init >/dev/null
ALICE=$(run_req whoami | jkey hex)
BOT=$(run_bot whoami | jkey hex)
CAROL=$(run_apv whoami | jkey hex)
echo "> alice=$ALICE bot=$BOT carol=$CAROL" >&2

HOME="$BOT_HOME" "$AMY_BIN" --account bot --secret-backend plaintext serve --port "$PORT" >"$RELAY_LOG" 2>&1 &
RELAY_PID=$!
for _ in $(seq 1 60); do grep -q "relay up" "$RELAY_LOG" && break; sleep 0.5; done
grep -q "relay up" "$RELAY_LOG" || { echo "relay never came up" >&2; cat "$RELAY_LOG" >&2; exit 1; }

# A tiny repo for the runner to worktree-isolate each run.
REPO="$STATE_DIR/repo"; mkdir -p "$REPO"
git -C "$REPO" -c init.defaultBranch=main init -q
git -C "$REPO" -c user.email=a@b.c -c user.name=t commit -q --allow-empty -m init

echo "> 1. alice triggers the workflow (kind-46020)" >&2
TRIG=$(run_req buzz workflow trigger "$RELAY" "$WFID" --task "add dark mode" --channel "$CHANNEL")
RUN_ID=$(echo "$TRIG" | jkey run_id)
check "trigger published" "$(echo "$TRIG" | jkey published)" "true"
[[ -n "$RUN_ID" ]] && { echo "  ✓ PASS got run id $RUN_ID" >&2; PASS=$((PASS+1)); } || { echo "  ✗ FAIL no run id" >&2; FAIL=$((FAIL+1)); }

echo "> 2. bot runs the runner once: agent work → posts the 46010 approval gate" >&2
R1=$(run_bot buzz workflow run "$RELAY" \
    --exec 'printf "diff: %s on %s" "$(cat)" "$BUZZ_BRANCH"' \
    --on-approve 'printf "https://github.com/x/y/pull/7"' \
    --channel "$CHANNEL" --approver "$CAROL" --worktree "$REPO" --base-ref main --once)
check "runner handled the trigger" "$(echo "$R1" | jkey handled)" "1"
SHOW1=$(run_req buzz workflow show "$RELAY" "$RUN_ID")
check "run is parked at the gate" "$(echo "$SHOW1" | jkey state)" "awaiting_approval"
check "gate addressed to carol" "$(echo "$SHOW1" | jkey pending_approver)" "$CAROL"
check "approval token is the run id" "$(echo "$SHOW1" | jkey approval_token)" "$RUN_ID"

echo "> 3. carol approves (kind-46030, d = run id)" >&2
GRANT=$(run_apv buzz workflow approve "$RELAY" "$RUN_ID" --note "lgtm")
check "grant published" "$(echo "$GRANT" | jkey published)" "true"

echo "> 4. bot runs again: the grant resolves the gate → --on-approve → 46005" >&2
R2=$(run_bot buzz workflow run "$RELAY" \
    --exec 'printf "diff: %s on %s" "$(cat)" "$BUZZ_BRANCH"' \
    --on-approve 'printf "https://github.com/x/y/pull/7"' \
    --channel "$CHANNEL" --approver "$CAROL" --worktree "$REPO" --base-ref main --once)
check "runner resolved 1 gate" "$(echo "$R2" | jkey handled)" "1"

echo "> 5. alice reads back the completed run" >&2
SHOW2=$(run_req buzz workflow show "$RELAY" "$RUN_ID")
check "state completed" "$(echo "$SHOW2" | jkey state)" "completed"
check "requester" "$(echo "$SHOW2" | jkey requester)" "$ALICE"
check "workflow id" "$(echo "$SHOW2" | jkey workflow)" "$WFID"
contains "result carries the PR url from --on-approve" "$(echo "$SHOW2" | jkey result)" "https://github.com/x/y/pull/7"

echo "> 6. deny path: a second run, carol denies → denied (work discarded)" >&2
TRIG2=$(run_req buzz workflow trigger "$RELAY" "$WFID" --task "risky change" --channel "$CHANNEL")
RUN_ID2=$(echo "$TRIG2" | jkey run_id)
run_bot buzz workflow run "$RELAY" --exec 'printf done' --on-approve 'printf pr' \
    --channel "$CHANNEL" --approver "$CAROL" --worktree "$REPO" --base-ref main --once >/dev/null
run_apv buzz workflow deny "$RELAY" "$RUN_ID2" --note "not yet" >/dev/null
run_bot buzz workflow run "$RELAY" --exec 'printf done' --on-approve 'printf pr' \
    --channel "$CHANNEL" --approver "$CAROL" --worktree "$REPO" --base-ref main --once >/dev/null
SHOW3=$(run_req buzz workflow show "$RELAY" "$RUN_ID2")
check "denied run is terminal (denied)" "$(echo "$SHOW3" | jkey state)" "denied"

echo "> 7. leftover worktrees cleaned up" >&2
LEFTOVER=$(git -C "$REPO" worktree list | sed 1d | wc -l | tr -d ' ')
check "worktrees removed after runs" "$LEFTOVER" "0"

echo "" >&2
echo "> RESULTS: $PASS passed, $FAIL failed" >&2
[[ $FAIL -eq 0 ]] && exit 0 || exit 1
