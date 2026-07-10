#!/usr/bin/env bash
#
# pow-headless.sh — verifies amy's NIP-13 primitives without a relay.
#
# One throwaway amy identity in an isolated $HOME. We assert that:
#
#   1. `amy pow bench` reports a positive hash rate and estimates.
#   2. `amy pow mine --target 10 --pubkey HEX <template>` returns a
#      template whose recomputed id has >= 10 leading zero bits and
#      whose nonce tag commits to "10".
#   3. `amy pow mine` with a 0-second timeout on an impossible target
#      exits 124 (the await-timeout contract).
#   4. `amy event --kind 1` piped through `amy pow check -` reports
#      valid=true and has_commitment=false for an unmined event.
#   5. Mining via `amy event --tags <mined nonce tag>` … a signed event
#      carrying the mined nonce round-trips through `pow check` with
#      effective_pow >= 10 and has_commitment=true.
#
# Usage: ./pow-headless.sh [--no-build]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-pow-headless"
LOG_DIR="$STATE_DIR/logs"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"
NO_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) NO_BUILD=1 ;;
    -h|--help)
      sed -n '3,19p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
      exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR" "$LOG_DIR"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"

command -v jq >/dev/null || { fail_msg "jq is required"; exit 1; }

if [[ $NO_BUILD -eq 0 ]]; then
  step "building amy (installDist)"
  (cd "$REPO_ROOT" && ./gradlew -q :cli:installDist) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "gradle :cli:installDist failed"; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { fail_msg "amy binary missing at $AMY_BIN"; exit 1; }

export HOME="$STATE_DIR"
amy() { "$AMY_BIN" --account t --secret-backend plaintext --json "$@" 2>>"$LOG_FILE"; }

banner "amy NIP-13 primitives"

step "init throwaway identity"
amy init >>"$LOG_FILE" || { record_result "init" fail; exit 1; }
PUBKEY="$(amy whoami | jq -r .hex)"
[[ "$PUBKEY" =~ ^[0-9a-f]{64}$ ]] || { record_result "init" fail "no pubkey"; exit 1; }
record_result "init" pass

step "pow bench"
BENCH="$(amy pow bench)"
RATE="$(jq -r .hashes_per_second <<<"$BENCH")"
EST20="$(jq -r '.expected_seconds["20"]' <<<"$BENCH")"
if [[ "$RATE" -gt 0 ]] && jq -e '.expected_seconds["16"] < .expected_seconds["28"]' <<<"$BENCH" >/dev/null; then
  record_result "pow-bench" pass "rate=$RATE h/s, 20 bits ≈ ${EST20}s"
else
  record_result "pow-bench" fail "$BENCH"
fi

step "pow mine at 10 bits"
TEMPLATE='{"created_at":1683596206,"kind":1,"tags":[],"content":"pow harness"}'
MINE="$(amy pow mine --target 10 --pubkey "$PUBKEY" "$TEMPLATE")"
POW="$(jq -r .pow <<<"$MINE")"
NONCE_TARGET="$(jq -r '.template_json | fromjson | .tags[] | select(.[0]=="nonce") | .[2]' <<<"$MINE")"
if [[ "$POW" -ge 10 && "$NONCE_TARGET" == "10" ]]; then
  record_result "pow-mine" pass "pow=$POW committed=$NONCE_TARGET"
else
  record_result "pow-mine" fail "$MINE"
fi

step "pow mine timeout exits 124"
amy pow mine --target 60 --timeout 1 --pubkey "$PUBKEY" "$TEMPLATE" >>"$LOG_FILE"
RC=$?
if [[ $RC -eq 124 ]]; then
  record_result "pow-mine-timeout" pass
else
  record_result "pow-mine-timeout" fail "exit=$RC"
fi

step "pow check on an unmined signed event"
UNMINED="$(amy event --kind 1 --content "no pow here" | jq -c .event)"
CHECK1="$(amy pow check "$UNMINED")"
if jq -e '.valid == true and .has_commitment == false' <<<"$CHECK1" >/dev/null; then
  record_result "pow-check-unmined" pass
else
  record_result "pow-check-unmined" fail "$CHECK1"
fi

step "mined template signs into a valid PoW event"
NONCE_TAGS="$(jq -c '.template_json | fromjson | .tags' <<<"$MINE")"
CREATED_AT="$(jq -r '.template_json | fromjson | .created_at' <<<"$MINE")"
SIGNED="$(amy event --kind 1 --content "pow harness" --tags "$NONCE_TAGS" --created-at "$CREATED_AT" | jq -c .event)"
CHECK2="$(amy pow check "$SIGNED")"
if jq -e '.valid == true and .has_commitment == true and .effective_pow >= 10' <<<"$CHECK2" >/dev/null; then
  record_result "pow-check-mined" pass "effective_pow=$(jq -r .effective_pow <<<"$CHECK2")"
else
  record_result "pow-check-mined" fail "$CHECK2"
fi

print_summary
