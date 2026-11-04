#!/usr/bin/env bash
#
# dm-interop-headless.sh — zero-prompt NIP-17 DM interop harness.
#
# Two `amy` processes (Identity A and Identity D) talk to each other
# through a local embedded relay (`amy serve`, i.e. geode). No MDK, no
# Marmot, no Rust toolchain, no public internet traffic.
#
# amy (like the app) drops loopback, RFC 1918 and link-local relays from
# other people's relay lists, so A only finds D's kind:10050 inbox when the
# relay runs on an address outside those ranges. The harness takes the
# first such address on any interface (or --host IP). Without one the
# relay runs on 127.0.0.2 and the inbox tests (dm-01/02/05/06) are skipped;
# dm-03/04 still run. Any host can provide one on loopback:
# `sudo ip addr add 192.0.2.2/32 dev lo` (Linux) or
# `sudo ifconfig lo0 alias 192.0.2.2 up` (macOS).
#
# Usage: ./dm-interop-headless.sh [--port N] [--host IP] [--no-build]
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

# The relay = `amy serve` (geode), booted from $AMY_BIN by
# start_local_relay in headless/helpers.sh. Override RELAY_DATA if you
# want full isolation between runs. RELAY_HOST is resolved after the
# helpers are sourced (see the header for why it must be routable).
RELAY_HOST="${RELAY_HOST:-}"
RELAY_DATA="$STATE_DIR/relay"
RELAY_PORT="${RELAY_PORT:-8090}"
NO_BUILD=0

A_NPUB=""
A_HEX=""
D_NPUB=""
D_HEX=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)     RELAY_PORT="$2"; shift ;;
    --host)     RELAY_HOST="$2"; shift ;;
    --no-build) NO_BUILD=1 ;;
    -h|--help)
      sed -n '3,19p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
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

INBOX_SKIP=""
if [[ -z "$RELAY_HOST" ]] && ! RELAY_HOST="$(detect_routing_host)"; then
  RELAY_HOST=127.0.0.2
  INBOX_SKIP="no non-loopback, non-private IPv4 on any interface (see --help)"
elif ! is_routable_ipv4 "$RELAY_HOST"; then
  INBOX_SKIP="$RELAY_HOST is loopback/private: amy drops it from D's kind:10050"
fi
RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"

# run_inbox_test FN ID — a test whose send resolves D's kind:10050 inbox.
run_inbox_test() {
  if [[ -n "$INBOX_SKIP" ]]; then
    record_result "$2" skip "$INBOX_SKIP"
  else
    "$1"
  fi
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

banner "Amethyst NIP-17 DM headless interop ($RUN_TS)"
preflight_dm
[[ -n "$INBOX_SKIP" ]] && warn "dm-01/02/05/06 will be skipped: $INBOX_SKIP"
start_local_relay
ensure_identity_for A
ensure_identity_for D
configure_relays_dm

run_inbox_test test_01_dm_text_round_trip "dm-01 text round-trip"
run_inbox_test test_02_dm_list_surfaces_history "dm-02 dm list text history"
test_03_dm_send_rejects_no_inbox
test_04_dm_send_allow_fallback
run_inbox_test test_05_dm_file_reference_round_trip "dm-05 file reference round-trip"
run_inbox_test test_06_dm_list_since_filter "dm-06 since filter"
