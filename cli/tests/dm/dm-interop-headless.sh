#!/usr/bin/env bash
#
# dm-interop-headless.sh — zero-prompt NIP-17 DM interop harness.
#
# Two `amy` processes (Identity A and Identity D) talk to each other
# through a local embedded relay (`amy serve`, i.e. geode) on
# ws://127.0.0.2:$RELAY_PORT. No MDK, no Marmot, no Rust toolchain, no
# public internet traffic.
#
# Usage: ./dm-interop-headless.sh [--port N] [--no-build]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-dm-headless"
LOG_DIR="$STATE_DIR/logs"
# Per-account dirs under the same fake $HOME=$STATE_DIR so amy treats
# this as one user with two accounts (production layout).
A_DIR="$STATE_DIR/.amy/A"
D_DIR="$STATE_DIR/.amy/D"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"

# Loopback relay = `amy serve` (geode), booted from $AMY_BIN by
# start_local_relay in headless/helpers.sh. Override RELAY_DATA if you
# want full isolation between runs.
# 127.0.0.2 used to dodge Quartz's `isLocalHost()` strip of loopback
# relays in kind:10050 inbox lists. That filter now covers all of
# 127.0.0.0/8, so the strict-inbox sends (dm-01/02/05/06) fail with
# no_dm_relays regardless of which loopback address the relay binds;
# only the fallback-chain tests (dm-03/04) are unaffected. Kept for
# parity with the other harnesses until that routing rule is revisited.
RELAY_HOST="${RELAY_HOST:-127.0.0.2}"
RELAY_DATA="$STATE_DIR/relay"
RELAY_PORT="${RELAY_PORT:-8090}"
RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"
NO_BUILD=0

A_NPUB=""
A_HEX=""
D_NPUB=""
D_HEX=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)     RELAY_PORT="$2"; RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"; shift ;;
    --host)     RELAY_HOST="$2"; RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"; shift ;;
    --no-build) NO_BUILD=1 ;;
    -h|--help)
      sed -n '3,12p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
      exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

mkdir -p "$STATE_DIR" "$LOG_DIR"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# Reuse the logging + result helpers shared between every harness.
# lib.sh declares wn-specific helpers too — harmless when unused.
# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"

# setup.sh in this dir defines the slim preflight_dm() (amy only, no
# MDK); the relay lifecycle (start_local_relay / stop_local_relay, the
# embedded `amy serve`) comes from the shared headless helpers.
# shellcheck source=setup.sh
source "$SCRIPT_DIR/setup.sh"
# shellcheck source=../headless/helpers.sh
source "$TESTS_DIR/headless/helpers.sh"
# shellcheck source=tests-dm.sh
source "$SCRIPT_DIR/tests-dm.sh"

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

banner "Amethyst NIP-17 DM headless interop ($RUN_TS)"
preflight_dm
start_local_relay
ensure_identity_for A
ensure_identity_for D
configure_relays_dm

test_01_dm_text_round_trip
test_02_dm_list_surfaces_history
test_03_dm_send_rejects_no_inbox
test_04_dm_send_allow_fallback
test_05_dm_file_reference_round_trip
test_06_dm_list_since_filter
