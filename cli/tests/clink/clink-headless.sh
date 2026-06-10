#!/usr/bin/env bash
#
# clink-headless.sh — local-decode checks for `amy offer info` / `amy debit info`.
#
# These verbs are pure pointer decode (no network), so this suite needs no relay:
# it asserts that amy decodes the canonical CLINK interop vectors (the same fixtures
# the quartz ClinkInteropTest uses) to the right fields, in both success and error
# paths. The round-trip verbs (`offer request`, `debit pay`, `debit budget`) need a
# live CLINK service and are out of scope here.
#
# Usage: ./clink-headless.sh [--no-build]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-clink-headless"
LOG_DIR="$STATE_DIR/logs"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"
AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"

NO_BUILD=0
[[ "${1:-}" == "--no-build" ]] && NO_BUILD=1

mkdir -p "$LOG_DIR"
: >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"
# shellcheck source=../headless/helpers.sh
source "$TESTS_DIR/headless/helpers.sh"

cleanup() {
    local rc=$?
    trap - EXIT INT TERM HUP
    print_summary
    exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Canonical interop vectors (fixed pubkey/relay), from quartz ClinkInteropTest.
EXPECTED_PUB="7e7e9c42a91bfef19fa929e5fda1b72e0ebc1a4c1141673e2794234d86addf4e"
NOFFER_FIXED="noffer1qszqqqzjpqpszqqzpphkven9wgkkjeqprpmhxue69uhhyetvv9ujuumgda3kkmn9wshxgetkqqs8ul5ug253hlh3n75jne0a5xmjur4urfxpzst88cnegg6ds6ka7nsx7zr9c"
NOFFER_SPONT="noffer1qvqsyqs9wd5x7up3qyv8wumn8ghj7un9d3shjtnndphkx6mwv46zuer9wcqzqln7n3p2jxl77x06j209lksmwtswhsdycy2pvulz09prfkr2mh6wexeyu2"
NDEBIT_STATIC="ndebit1qgyhqmmfde6x2u3dxuq3samnwvaz7tmjv4kxz7fwwd5x7cmtdejhgtnyv4mqqgr706wy92gmlmcel2ffuh76rdewp67p5nq3g9nnufu5ydxcdtwlfcg94z44"

banner "CLINK pointer decode headless ($RUN_TS)"

if [[ "$NO_BUILD" -eq 0 ]]; then
    step "Building amy (installDist)"
    (cd "$REPO_ROOT" && ./gradlew -q :cli:installDist >>"$LOG_FILE" 2>&1) ||
        { fail_msg "amy build failed (see $LOG_FILE)"; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { fail_msg "amy binary missing: $AMY_BIN"; exit 1; }

# Bare local account — `init` does no relay traffic, which is all we need.
rm -rf "${STATE_DIR:?}/.amy"
amy_a init >>"$LOG_FILE" 2>&1 || { fail_msg "amy init failed (see $LOG_FILE)"; exit 1; }

# --- offer info: fixed-price ---
step "offer info decodes a fixed-price noffer"
OUT=$(amy_json offer info "$NOFFER_FIXED") || true
assert_eq "$(jq -r '.pubkey' <<<"$OUT")" "$EXPECTED_PUB" offer.info.pubkey &&
    record_result offer.info.pubkey pass "pubkey decoded"
assert_eq "$(jq -r '.pointer' <<<"$OUT")" "offer-id" offer.info.pointer &&
    record_result offer.info.pointer pass "pointer=offer-id"
assert_eq "$(jq -r '.price_type' <<<"$OUT")" "fixed" offer.info.price_type &&
    record_result offer.info.price_type pass "price_type=fixed"
assert_eq "$(jq -r '.price_sats' <<<"$OUT")" "21000" offer.info.price_sats &&
    record_result offer.info.price_sats pass "price_sats=21000"

# --- offer info: spontaneous (no price) ---
step "offer info decodes a spontaneous noffer (no price)"
OUT=$(amy_json offer info "$NOFFER_SPONT") || true
assert_eq "$(jq -r '.price_type' <<<"$OUT")" "spontaneous" offer.info.spont_type &&
    record_result offer.info.spont_type pass "price_type=spontaneous"
assert_eq "$(jq -r '.price_sats' <<<"$OUT")" "null" offer.info.spont_price &&
    record_result offer.info.spont_price pass "price_sats=null"

# --- offer info: bad pointer => non-zero exit ---
step "offer info rejects a non-noffer string"
if amy_a offer info "definitely-not-a-noffer" >>"$LOG_FILE" 2>&1; then
    record_result offer.info.bad fail "bad pointer should exit non-zero"
else
    record_result offer.info.bad pass "bad pointer exits non-zero"
fi

# --- debit info: static pointer ---
step "debit info decodes a static ndebit"
OUT=$(amy_json debit info "$NDEBIT_STATIC") || true
assert_eq "$(jq -r '.pubkey' <<<"$OUT")" "$EXPECTED_PUB" debit.info.pubkey &&
    record_result debit.info.pubkey pass "pubkey decoded"
assert_eq "$(jq -r '.pointer' <<<"$OUT")" "pointer-7" debit.info.pointer &&
    record_result debit.info.pointer pass "pointer=pointer-7"
assert_eq "$(jq -r '.session' <<<"$OUT")" "false" debit.info.session &&
    record_result debit.info.session pass "session=false (no k1)"

# --- debit budget: argument validation (no network needed) ---
step "debit budget rejects an unknown frequency"
if amy_a debit budget "$NDEBIT_STATIC" --amount 1000 --frequency fortnight >>"$LOG_FILE" 2>&1; then
    record_result debit.budget.badfreq fail "unknown frequency should exit non-zero"
else
    record_result debit.budget.badfreq pass "unknown frequency exits non-zero"
fi

step "debit budget requires --amount"
if amy_a debit budget "$NDEBIT_STATIC" >>"$LOG_FILE" 2>&1; then
    record_result debit.budget.noamount fail "missing --amount should exit non-zero"
else
    record_result debit.budget.noamount pass "missing --amount exits non-zero"
fi

# --- offer pay: requires a --with funding pointer (validated before any network) ---
step "offer pay requires --with <ndebit>"
if amy_a offer pay "$NOFFER_SPONT" --amount 1000 >>"$LOG_FILE" 2>&1; then
    record_result offer.pay.nowith fail "missing --with should exit non-zero"
else
    record_result offer.pay.nowith pass "missing --with exits non-zero"
fi

step "offer pay rejects a non-ndebit --with"
if amy_a offer pay "$NOFFER_SPONT" --with "not-an-ndebit" >>"$LOG_FILE" 2>&1; then
    record_result offer.pay.badwith fail "bad --with should exit non-zero"
else
    record_result offer.pay.badwith pass "bad --with exits non-zero"
fi

# --- profile edit --clink-offer: validates the noffer locally before publishing ---
step "profile edit rejects a non-noffer --clink-offer"
if amy_a profile edit --clink-offer "not-a-noffer" >>"$LOG_FILE" 2>&1; then
    record_result profile.clinkoffer.bad fail "bad --clink-offer should exit non-zero"
else
    record_result profile.clinkoffer.bad pass "bad --clink-offer exits non-zero"
fi
