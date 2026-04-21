#!/usr/bin/env bash
#
# marmot-interop-headless.sh — zero-prompt interop harness.
#
# Drives Identity A via the `amy` CLI (./gradlew :cli:installDist) and
# Identities B/C via whitenoise-rs `wn`/`wnd`. Matches the 13 test
# scenarios in marmot-interop.sh but without any human prompts — all
# checks run to completion and the exit code reflects pass/fail totals.
#
# Usage: ./marmot-interop-headless.sh [--local-relays] [--no-build]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-headless"
LOG_DIR="$STATE_DIR/logs"
A_DIR="$STATE_DIR/A"
B_DIR="$STATE_DIR/B"
C_DIR="$STATE_DIR/C"
B_SOCKET="$B_DIR/release/wnd.sock"
C_SOCKET="$C_DIR/release/wnd.sock"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

WN_REPO="${WN_REPO:-$SCRIPT_DIR/state/whitenoise-rs}"
WN_BIN="$WN_REPO/target/release/wn"
WND_BIN="$WN_REPO/target/release/wnd"
AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"

DEFAULT_RELAYS=( "wss://relay.damus.io" "wss://nos.lol" "wss://relay.primal.net" )
USE_LOCAL_RELAYS=0
NO_BUILD=0

A_NPUB=""
A_HEX=""
B_NPUB=""
B_HEX=""
C_NPUB=""
C_HEX=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --local-relays) USE_LOCAL_RELAYS=1 ;;
    --no-build)     NO_BUILD=1 ;;
    -h|--help)
      sed -n '3,12p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
      exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

mkdir -p "$STATE_DIR" "$LOG_DIR" "$A_DIR" "$B_DIR/logs" "$C_DIR/logs"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# Reuse colours / logging / dump_daemon_diagnostics from the interactive harness.
# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

# shellcheck source=headless/setup.sh
source "$SCRIPT_DIR/headless/setup.sh"
# shellcheck source=headless/helpers.sh
source "$SCRIPT_DIR/headless/helpers.sh"
# shellcheck source=headless/tests-create.sh
source "$SCRIPT_DIR/headless/tests-create.sh"
# shellcheck source=headless/tests-manage.sh
source "$SCRIPT_DIR/headless/tests-manage.sh"
# shellcheck source=headless/tests-extras.sh
source "$SCRIPT_DIR/headless/tests-extras.sh"

trap 'stop_daemons; print_summary' EXIT

banner "Marmot headless interop harness ($RUN_TS)"
preflight
start_daemon B "$B_DIR" "$B_SOCKET"
start_daemon C "$C_DIR" "$C_SOCKET"
ensure_identity_a
ensure_identity B
ensure_identity C
configure_relays

test_01_keypackage_discovery
test_02_a_creates_group
test_03_b_creates_group
test_04_three_member_group
test_05_b_adds_a_existing
test_06_member_removal
test_07_metadata_rename
test_08_admin_promote_demote
test_09_reply_react_unreact
test_10_concurrent_commits
test_11_leave_group
test_12_offline_catchup
test_13_keypackage_rotation
