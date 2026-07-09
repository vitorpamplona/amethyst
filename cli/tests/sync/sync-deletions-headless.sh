#!/usr/bin/env bash
#
# sync-deletions-headless.sh — drives the real `amy` binary against a real
# `amy serve` relay to prove NIP-77 deletion propagation end-to-end.
#
# `amy sync` converges deletions in a second pass over the reconcile residual
# (see quartz `negentropySettleDeletions`). This exercises both directions plus
# the opt-out:
#
#   T1 (up)   — we deleted a note the relay still has → `amy sync` sends our
#               kind-5 up and the relay drops the note. Verified by an ISOLATED
#               third account whose store reads the relay only (no tombstone).
#   T2 (off)  — same setup with `--no-sync-deletions` → the relay keeps the note
#               and nothing is sent.
#   T3 (down) — the relay deleted a note we still hold → `amy sync --up` pulls the
#               relay's kind-5 down and applies it locally (converges on re-sync).
#
# Each amy account gets its OWN $HOME so their file stores don't share (accounts
# under one $HOME share ~/.amy/shared/events-store). The relay (amy serve) keeps
# a separate store from any client store.
#
# Usage: ./sync-deletions-headless.sh [--port N] [--no-build]
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-sync-deletions"
LOG_DIR="$STATE_DIR/logs"
RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"
RELAY_HOST="127.0.0.1"
RELAY_PORT="${RELAY_PORT:-7790}"
RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"
NO_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)     RELAY_PORT="$2"; RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"; shift ;;
    --no-build) NO_BUILD=1 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
  shift
done

# Fresh state every run — stale per-account $HOME dirs from a prior run must not
# leak into this one.
rm -rf "$STATE_DIR"
mkdir -p "$LOG_DIR"
: >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"

# Leniently-trimmed equality assertion (assert helpers live in the DM-specific
# helpers.sh, which hardcodes its own amy wrappers — so define our own here).
assert_eq() {
  local actual="$1" expected="$2" test_id="$3" note="${4:-}"
  if [[ "${actual// /}" == "${expected// /}" ]]; then
    info "assert: $test_id \"$actual\" == \"$expected\""
    return 0
  fi
  fail_msg "$test_id: expected \"$expected\", got \"$actual\" (${note:-})"
  record_result "$test_id" fail "${note:-mismatch}"
  return 1
}

SERVE_PID=""
RELAY_HOME=""
cleanup() {
  [[ -n "$SERVE_PID" ]] && kill "$SERVE_PID" 2>/dev/null
  trap - EXIT INT TERM HUP
  print_summary
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

banner "amy sync — NIP-77 deletion propagation headless ($RUN_TS)"

# ---- build ------------------------------------------------------------------
if [[ "$NO_BUILD" -eq 0 ]]; then
  step "Building amy (installDist)…"
  (cd "$REPO_ROOT" && ./gradlew -q :cli:installDist) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "build failed (see $LOG_FILE)"; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { fail_msg "amy binary not found at $AMY_BIN"; exit 1; }

# ---- amy wrappers (one isolated $HOME per account) --------------------------
strip() { grep -vE "Picked up JAVA_TOOL|DEBUG:|INFO:|MarmotManager|MlsGroup"; }
mk_home() { mktemp -d "$STATE_DIR/home.XXXXXX"; }
# amy_run <home> <account> args...
amy_run() {
  local home="$1" acct="$2"; shift 2
  HOME="$home" "$AMY_BIN" --account "$acct" --secret-backend plaintext --json "$@" 2>>"$LOG_FILE" | strip
}

RELAY_HOME="$(mk_home)"
amy_run "$RELAY_HOME" a init >/dev/null

step "Starting amy serve on $RELAY_URL…"
HOME="$RELAY_HOME" "$AMY_BIN" --account a --secret-backend plaintext \
  serve --host "$RELAY_HOST" --port "$RELAY_PORT" >>"$LOG_FILE" 2>&1 &
SERVE_PID=$!

# Wait for the relay to accept connections (poll the serve log).
for _ in $(seq 1 60); do
  grep -q "relay up at" "$LOG_FILE" && break
  sleep 0.5
done
grep -q "relay up at" "$LOG_FILE" || { fail_msg "relay did not come up"; exit 1; }

# Isolated verifier: its own empty store, reads the relay only (no tombstone).
VERIFY_HOME="$(mk_home)"
amy_run "$VERIFY_HOME" v init >/dev/null
relay_count() { amy_run "$VERIFY_HOME" v fetch --id "$1" --relay "$RELAY_URL" | jq -r '.count // 0'; }

# =============================================================================
# T1 — up direction: we deleted it, the relay still has it → sync sends it up.
# =============================================================================
banner "T1 — amy sync sends our deletion up (relay drops the note)"
NOTE="$(amy_run "$RELAY_HOME" a event --kind 1 --content "delete-me-t1" | jq -c '.event')"
NID="$(echo "$NOTE" | jq -r '.id')"
echo "$NOTE" | amy_run "$RELAY_HOME" a publish --relay "$RELAY_URL" >/dev/null

before="$(relay_count "$NID")"
assert_eq "$before" "1" T1.setup "relay should hold the note before sync" \
  && record_result T1.setup pass "relay has the note"

# Delete locally only (no --relay → stored, applied, not sent to the relay).
amy_run "$RELAY_HOME" a event --kind 5 --tags "[[\"e\",\"$NID\"]]" --content "" --publish >/dev/null

SYNC="$(amy_run "$RELAY_HOME" a sync --relay "$RELAY_URL")"
info "sync: $SYNC"
sent="$(echo "$SYNC" | jq -r '.deletions_sent_up // 0')"
assert_eq "$sent" "1" T1.sent_up "sync should report one deletion sent up" \
  && record_result T1.sent_up pass "deletions_sent_up=1"

sleep 1
after="$(relay_count "$NID")"
assert_eq "$after" "0" T1.relay_dropped "relay must have removed the note after sync" \
  && record_result T1.relay_dropped pass "relay note count 1 → 0"

# =============================================================================
# T2 — opt-out: --no-sync-deletions leaves the relay untouched.
# =============================================================================
banner "T2 — --no-sync-deletions propagates nothing"
NOTE2="$(amy_run "$RELAY_HOME" a event --kind 1 --content "keep-me-t2" | jq -c '.event')"
NID2="$(echo "$NOTE2" | jq -r '.id')"
echo "$NOTE2" | amy_run "$RELAY_HOME" a publish --relay "$RELAY_URL" >/dev/null
amy_run "$RELAY_HOME" a event --kind 5 --tags "[[\"e\",\"$NID2\"]]" --content "" --publish >/dev/null

SYNC2="$(amy_run "$RELAY_HOME" a sync --relay "$RELAY_URL" --no-sync-deletions)"
info "sync: $SYNC2"
sent2="$(echo "$SYNC2" | jq -r '.deletions_sent_up // 0')"
assert_eq "$sent2" "0" T2.no_send "--no-sync-deletions must send nothing" \
  && record_result T2.no_send pass "deletions_sent_up=0"
sleep 1
kept="$(relay_count "$NID2")"
assert_eq "$kept" "1" T2.relay_kept "relay must still hold the note" \
  && record_result T2.relay_kept pass "relay note untouched"

# =============================================================================
# T3 — down direction: the relay deleted it, we still hold it → sync --up pulls
#      the relay's deletion down and applies it locally.
# =============================================================================
banner "T3 — amy sync --up applies the relay's deletion locally"
BOB_HOME="$(mk_home)"
amy_run "$BOB_HOME" b init >/dev/null
NOTE3="$(amy_run "$RELAY_HOME" a event --kind 1 --content "delete-me-t3" | jq -c '.event')"
NID3="$(echo "$NOTE3" | jq -r '.id')"
echo "$NOTE3" | amy_run "$RELAY_HOME" a publish --relay "$RELAY_URL" >/dev/null
# bob's isolated store learns the note from the relay…
amy_run "$BOB_HOME" b fetch --id "$NID3" --relay "$RELAY_URL" >/dev/null
# …then the relay deletes it (author pushes a kind-5 straight to the relay).
amy_run "$RELAY_HOME" a event --kind 5 --tags "[[\"e\",\"$NID3\"]]" --content "" | jq -c '.event' \
  | amy_run "$RELAY_HOME" a publish --relay "$RELAY_URL" >/dev/null

SYNC3="$(amy_run "$BOB_HOME" b sync --up --relay "$RELAY_URL")"
info "sync: $SYNC3"
applied="$(echo "$SYNC3" | jq -r '.deletions_applied_down // 0')"
assert_eq "$applied" "1" T3.applied_down "sync --up should apply one relay deletion locally" \
  && record_result T3.applied_down pass "deletions_applied_down=1"

# Converged: a second --up sync finds nothing left to apply.
SYNC3B="$(amy_run "$BOB_HOME" b sync --up --relay "$RELAY_URL")"
applied2="$(echo "$SYNC3B" | jq -r '.deletions_applied_down // 0')"
assert_eq "$applied2" "0" T3.converged "re-sync applies nothing (converged)" \
  && record_result T3.converged pass "second sync stable"

# print_summary runs from the cleanup trap; exit non-zero if any test failed.
grep -q $'\tfail\t' "$RESULTS_FILE" && exit 1
exit 0
