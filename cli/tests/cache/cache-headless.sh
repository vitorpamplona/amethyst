#!/usr/bin/env bash
#
# cache-headless.sh — verifies the file-backed event store is the
# source of truth for `amy` reads.
#
# Two amy identities (A and B) talk to a local nostr-rs-relay. We
# assert that:
#
#   1. After A runs `amy create`, A's local store contains the bootstrap
#      events (kind:0 / 3 / 10002 / 10050 / 10051 …).
#   2. A's `amy profile show` is served from cache (`source: "cache"`)
#      — no relay round-trip needed.
#   3. A's `amy profile show --refresh` bypasses the cache and pulls
#      from relays (`source: "relays"`).
#   4. B fetches A's profile once over the relay (cache miss), and the
#      second invocation serves from B's local store (cache hit).
#   5. `amy store stat` reports the right kind histogram and a non-zero
#      event count + disk usage.
#   6. `amy relay list` reads the relay URLs back out of the local
#      kind:10002 / 10050 / 10051 events (the pre-relays.json contract).
#
# Usage: ./cache-headless.sh [--port N] [--no-build]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-cache-headless"
LOG_DIR="$STATE_DIR/logs"
A_DIR="$STATE_DIR/A"
B_DIR="$STATE_DIR/B"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"

# Reuse the relay binary the marmot harness builds.
RELAY_HOST="${RELAY_HOST:-127.0.0.2}"
RELAY_REPO="${RELAY_REPO:-$TESTS_DIR/marmot/state-headless/nostr-rs-relay}"
RELAY_BIN="$RELAY_REPO/target/release/nostr-rs-relay"
RELAY_DATA="$STATE_DIR/relay"
RELAY_PORT="${RELAY_PORT:-8092}"
RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"
NO_BUILD=0

A_NPUB=""
A_HEX=""
B_NPUB=""
B_HEX=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)     RELAY_PORT="$2"; RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"; shift ;;
    --host)     RELAY_HOST="$2"; RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"; shift ;;
    --no-build) NO_BUILD=1 ;;
    -h|--help)
      sed -n '3,21p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
      exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

mkdir -p "$STATE_DIR" "$LOG_DIR" "$A_DIR" "$B_DIR"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"
# shellcheck source=../marmot/setup.sh — provides start_local_relay / stop_local_relay
source "$TESTS_DIR/marmot/setup.sh"
# shellcheck source=../headless/helpers.sh
source "$TESTS_DIR/headless/helpers.sh"

# Keep the dm setup's preflight (just checks for amy + the relay) but
# define our own identity bootstrap so we don't pull in DM-specific
# wiring.
# shellcheck source=../dm/setup.sh
source "$TESTS_DIR/dm/setup.sh"

amy_b() { "$AMY_BIN" --data-dir "$B_DIR" "$@"; }

# Helper: B-side amy_json. The dm headless's helpers.sh hardcodes A_DIR
# in amy_a; we need a parallel for B without re-sourcing.
amy_b_json() {
  local out
  if ! out=$(amy_b "$@" 2>>"$LOG_FILE"); then
    fail_msg "amy_b $*: exit $? (see $LOG_FILE)"
    printf '%s\n' "$out" >>"$LOG_FILE"
    return 1
  fi
  printf '%s' "$out"
}

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
trap 'exit 129' HUP

banner "Amethyst event-store cache headless ($RUN_TS)"
preflight_dm
start_local_relay

# Identity A: full bootstrap so the store has kind:0 / 3 / 10002 / …
ensure_identity_for A "$A_DIR"
banner "Bootstrapping A's account (publishes kind:0 + bootstrap events)"
"$AMY_BIN" --data-dir "$A_DIR" relay add "$RELAY_URL" --type all >>"$LOG_FILE" 2>&1
# `amy create` here would mint a *second* identity; A_DIR already has one
# from `init`. Build the bootstrap events ourselves by publishing a
# minimal kind:0 + the relay lists, all of which land in A's local store
# via verifyAndStore.
"$AMY_BIN" --data-dir "$A_DIR" relay publish-lists >>"$LOG_FILE" 2>&1
amy_a profile edit --name "AAA" --about "cache test subject" \
    >>"$LOG_FILE" 2>&1 \
    || fail_msg "amy_a profile edit failed"

# Identity B: separate data-dir, separate cache, also pointed at the relay.
ensure_identity_for B "$B_DIR"
"$AMY_BIN" --data-dir "$B_DIR" relay add "$RELAY_URL" --type all >>"$LOG_FILE" 2>&1
"$AMY_BIN" --data-dir "$B_DIR" relay publish-lists >>"$LOG_FILE" 2>&1

# ----------------------------------------------------------------------
# 1. amy store stat reports a non-empty store after bootstrap.
# ----------------------------------------------------------------------
banner "T1 — amy store stat after bootstrap"
T1=$(amy_a store stat) || fail_msg "store stat failed"
T1_EVENTS=$(printf '%s' "$T1" | jq -r '.events')
T1_K0=$(printf '%s' "$T1" | jq -r '.by_kind."0" // 0')
T1_K10002=$(printf '%s' "$T1" | jq -r '.by_kind."10002" // 0')
T1_BYTES=$(printf '%s' "$T1" | jq -r '.disk_bytes')

if [[ "$T1_EVENTS" -gt 0 ]]; then
  record_result T1.events pass "$T1_EVENTS event(s) in A's store"
else
  record_result T1.events fail "store should be non-empty after publish-lists+profile edit"
fi
if [[ "$T1_K0" -ge 1 ]]; then
  record_result T1.kind0 pass "kind:0 present"
else
  record_result T1.kind0 fail "no kind:0 in by_kind histogram"
fi
if [[ "$T1_K10002" -ge 1 ]]; then
  record_result T1.kind10002 pass "kind:10002 present"
else
  record_result T1.kind10002 fail "no kind:10002 in by_kind histogram"
fi
if [[ "$T1_BYTES" -gt 0 ]]; then
  record_result T1.disk_bytes pass "$T1_BYTES bytes used"
else
  record_result T1.disk_bytes fail "disk_bytes should be > 0"
fi

# ----------------------------------------------------------------------
# 2. profile show on self → source: "cache" (slot lookup, no network).
# ----------------------------------------------------------------------
banner "T2 — A's profile show is served from cache by default"
T2=$(amy_a profile show)
T2_SRC=$(printf '%s' "$T2" | jq -r '.source')
T2_FOUND=$(printf '%s' "$T2" | jq -r '.found')
T2_NAME=$(printf '%s' "$T2" | jq -r '.metadata.name // ""')
assert_eq T2.source "cache" "$T2_SRC" "default reads should hit the local store"
assert_eq T2.found "true" "$T2_FOUND" ""
assert_eq T2.name "AAA" "$T2_NAME" "profile edit must round-trip"

# ----------------------------------------------------------------------
# 3. profile show --refresh forces a relay drain.
# ----------------------------------------------------------------------
banner "T3 — --refresh forces source: relays"
T3=$(amy_a profile show --refresh)
T3_SRC=$(printf '%s' "$T3" | jq -r '.source')
T3_QR=$(printf '%s' "$T3" | jq -r '.queried_relays | length')
assert_eq T3.source "relays" "$T3_SRC" "--refresh must skip cache"
if [[ "$T3_QR" -ge 1 ]]; then
  record_result T3.queried_relays pass "$T3_QR relay(s) queried"
else
  record_result T3.queried_relays fail "expected at least one queried_relays entry"
fi

# ----------------------------------------------------------------------
# 4. B has not seen A → first profile show is a relay miss; second is a hit.
# ----------------------------------------------------------------------
banner "T4 — B sees A first via relay, then via cache"
T4A=$(amy_b_json profile show "$A_NPUB")
T4A_SRC=$(printf '%s' "$T4A" | jq -r '.source')
T4A_NAME=$(printf '%s' "$T4A" | jq -r '.metadata.name // ""')
assert_eq T4a.source "relays" "$T4A_SRC" "first lookup of a stranger comes from relays"
assert_eq T4a.name "AAA" "$T4A_NAME" "B should resolve A's name on first fetch"

T4B=$(amy_b_json profile show "$A_NPUB")
T4B_SRC=$(printf '%s' "$T4B" | jq -r '.source')
T4B_NAME=$(printf '%s' "$T4B" | jq -r '.metadata.name // ""')
assert_eq T4b.source "cache" "$T4B_SRC" "second lookup of the same stranger must serve from cache"
assert_eq T4b.name "AAA" "$T4B_NAME" "cached metadata must match"

# ----------------------------------------------------------------------
# 5. relay list reads URLs back from the local kind:10002 / 10050 / 10051.
# ----------------------------------------------------------------------
banner "T5 — relay list reads from store"
T5=$(amy_a relay list)
T5_NIP65=$(printf '%s' "$T5" | jq -r '.nip65 | length')
T5_INBOX=$(printf '%s' "$T5" | jq -r '.inbox | length')
T5_KP=$(printf '%s' "$T5" | jq -r '.key_package | length')
if [[ "$T5_NIP65" -ge 1 ]]; then
  record_result T5.nip65 pass "$T5_NIP65 nip65 url(s)"
else
  record_result T5.nip65 fail "nip65 bucket empty after relay add --type all"
fi
if [[ "$T5_INBOX" -ge 1 ]]; then
  record_result T5.inbox pass "$T5_INBOX inbox url(s)"
else
  record_result T5.inbox fail "inbox bucket empty after relay add --type all"
fi
if [[ "$T5_KP" -ge 1 ]]; then
  record_result T5.key_package pass "$T5_KP key_package url(s)"
else
  record_result T5.key_package fail "key_package bucket empty after relay add --type all"
fi

# ----------------------------------------------------------------------
# 6. relays.json must NOT exist anywhere — the events ARE the config.
# ----------------------------------------------------------------------
banner "T6 — relays.json is gone"
if [[ ! -f "$A_DIR/relays.json" && ! -f "$B_DIR/relays.json" ]]; then
  record_result T6.no_relays_json pass "neither data-dir contains relays.json"
else
  record_result T6.no_relays_json fail "relays.json found — should have been removed"
fi

# ----------------------------------------------------------------------
# 7. Maintenance verbs work without an identity (they only need the store).
# ----------------------------------------------------------------------
banner "T7 — store maintenance verbs (sweep/scrub/compact) without identity"
TMP_NO_ID=$(mktemp -d)
T7_STAT=$(${AMY_BIN} --data-dir "$TMP_NO_ID" store stat)
T7_SWEEP=$(${AMY_BIN} --data-dir "$TMP_NO_ID" store sweep-expired)
T7_SCRUB=$(${AMY_BIN} --data-dir "$TMP_NO_ID" store scrub)
T7_COMPACT=$(${AMY_BIN} --data-dir "$TMP_NO_ID" store compact)
assert_eq T7.stat.events "0" "$(printf '%s' "$T7_STAT" | jq -r '.events')" ""
assert_eq T7.sweep.swept "0" "$(printf '%s' "$T7_SWEEP" | jq -r '.swept')" ""
assert_eq T7.scrub.ok "true" "$(printf '%s' "$T7_SCRUB" | jq -r '.ok')" ""
assert_eq T7.compact.ok "true" "$(printf '%s' "$T7_COMPACT" | jq -r '.ok')" ""
rm -rf "$TMP_NO_ID"
