#!/usr/bin/env bash
#
# dm-interop-headless.sh — zero-prompt NIP-17 DM interop harness.
#
# Two `amy` processes (Identity A and Identity D) talk to each other
# through a local nostr-rs-relay on ws://127.0.0.1:$RELAY_PORT. No
# whitenoise-rs, no Marmot, no public internet traffic.
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

# Share the nostr-rs-relay checkout with the Marmot harness to avoid
# rebuilding it twice. Override RELAY_REPO / RELAY_DATA if you want full
# isolation between runs.
# Bind the loopback relay to 127.0.0.2 rather than 127.0.0.1 so Quartz's
# `isLocalHost()` filter doesn't silently strip it out of the kind:10050
# inbox events during recipient-relay resolution. 127.0.0.2 is still pure
# loopback — no network traffic, no config needed.
RELAY_HOST="${RELAY_HOST:-127.0.0.2}"
RELAY_REPO="${RELAY_REPO:-$TESTS_DIR/marmot/state-headless/nostr-rs-relay}"
RELAY_BIN="$RELAY_REPO/target/release/nostr-rs-relay"
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

# Reuse start_local_relay / stop_local_relay from the Marmot harness's
# setup.sh — the relay lifecycle is identical. preflight() there also
# builds whitenoise-rs, which we don't need; setup.sh in this dir
# defines a slimmer preflight_dm().
# shellcheck source=../marmot/setup.sh
source "$TESTS_DIR/marmot/setup.sh"
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
