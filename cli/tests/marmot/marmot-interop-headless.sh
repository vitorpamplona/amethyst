#!/usr/bin/env bash
#
# marmot-interop-headless.sh — zero-prompt, zero-internet interop harness.
#
# Drives Identity A via the `amy` CLI (./gradlew :cli:installDist) and
# Identities B/C via MDK's `wn`/`wnd`. Spins up a local
# nostr-rs-relay on ws://127.0.0.1:$RELAY_PORT so nothing ever leaves the
# machine. Matches the 13 test scenarios in marmot-interop.sh but without
# any human prompts — all checks run to completion and the exit code
# reflects pass/fail totals.
#
# Usage: ./marmot-interop-headless.sh [--port N] [--no-build] [--reuse-state]
#                                     [--tests "name ..."]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-headless"
LOG_DIR="$STATE_DIR/logs"
# A is the amy account inside the fake $HOME=$STATE_DIR layout. B and
# C are wnd (whitenoise) state dirs — different binary, separate
# convention, so they stay as plain $STATE_DIR siblings.
A_DIR="$STATE_DIR/.amy/A"
B_DIR="$STATE_DIR/B"
C_DIR="$STATE_DIR/C"
B_SOCKET="${B_SOCKET:-$B_DIR/wnd.sock}"
C_SOCKET="${C_SOCKET:-$C_DIR/wnd.sock}"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

WN_REPO="${WN_REPO:-$SCRIPT_DIR/state/mdk}"
WN_BIN="$WN_REPO/target/release/wn"
WND_BIN="$WN_REPO/target/release/wnd"
AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"

# Local relay wiring — cloned + built during preflight, started on
# $RELAY_PORT. The harness never touches the public internet for test
# traffic; wn/wnd/amy all point at this one loopback endpoint.
#
# Bind to 127.0.0.2 rather than 127.0.0.1: Quartz's RelayUrlNormalizer
# strips literal 127.0.0.1 / localhost / 192.168.* out of NIP-17 inbox
# (kind:10050) and KeyPackage (kind:10051) relay-list events as a
# privacy guard, which would silently leave the harness publishing to
# Amethyst's public defaults instead of the loopback. 127.0.0.2 is
# still pure loopback (no network traffic) but isn't on the strip list.
RELAY_HOST="${RELAY_HOST:-127.0.0.2}"
RELAY_REPO="${RELAY_REPO:-$STATE_DIR/nostr-rs-relay}"
RELAY_BIN="$RELAY_REPO/target/release/nostr-rs-relay"
RELAY_DATA="$STATE_DIR/relay"
RELAY_PORT="${RELAY_PORT:-8080}"
RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"

# MDK's reference QUIC broker, for the agent-text-stream tests. Loopback like
# everything else; the tests skip when the binary was never built.
BROKER_BIN="$WN_REPO/target/release/marmot-quic-broker"
BROKER_HOST="${BROKER_HOST:-127.0.0.1}"
BROKER_PORT="${BROKER_PORT:-4455}"
BROKER_URI="quic://$BROKER_HOST:$BROKER_PORT"
BROKER_PID=""
# SHA-256 of the broker's self-signed leaf, read from its startup JSON.
BROKER_PIN=""

# A loopback Blossom blob store for the encrypted-media tests. Ciphertext only:
# the file key comes from each group's MLS exporter and never reaches it.
BLOSSOM_HOST="${BLOSSOM_HOST:-127.0.0.1}"
BLOSSOM_PORT="${BLOSSOM_PORT:-8456}"
BLOSSOM_URL="http://$BLOSSOM_HOST:$BLOSSOM_PORT"
BLOSSOM_PID=""

NO_BUILD=0
# Every run starts from empty stores. wnd already wipes B's and C's data dirs
# on each start, but A's amy home and the relay's SQLite file used to survive,
# and the leftovers are not inert: a KeyPackage A published in an earlier run
# is still on the relay for B to invite with, an old group's kind:445 events
# still arrive and fail to decrypt, and A's cursors still say it has seen them.
# That drift is what made tests 03 and 08 fail on a dirty tree and pass on a
# clean one. Pass --reuse-state when you are deliberately debugging carry-over.
RESET_STATE=1
# Space-separated test function names; empty means the full suite below.
ONLY_TESTS=""

# Required as of MDK 0.9.x. `validate_relay_url` accepts `wss://`
# unconditionally but `ws://` only for a loopback host AND only behind this
# explicit opt-in; without it wnd refuses the harness relay with "invalid relay
# URL" and exits before creating its socket. 127.0.0.2 is inside 127.0.0.0/8 and
# already passes MDK's own loopback test, so the env var is the gate, not the
# address. Exported once here so `wn` and `wnd` both inherit it — `wn` runs the
# same validation on any relay argument.
export WN_ALLOW_LOOPBACK_RELAYS=1
# Same shape for blob stores: wn refuses a loopback Blossom endpoint unless it
# is told this is a dev/test run. The harness's blob store is loopback by
# design — nothing in a test run may leave the machine.
export WN_ALLOW_LOOPBACK_BLOB_ENDPOINTS=1

A_NPUB=""
A_HEX=""
B_NPUB=""
B_HEX=""
C_NPUB=""
C_HEX=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)      RELAY_PORT="$2"; RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"; shift ;;
    --host)      RELAY_HOST="$2"; RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"; shift ;;
    --no-build)  NO_BUILD=1 ;;
    --reuse-state) RESET_STATE=0 ;;
    --tests)     ONLY_TESTS="$2"; shift ;;
    -h|--help)
      sed -n '3,14p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
      exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

if [[ $RESET_STATE -eq 1 && -d "$STATE_DIR" ]]; then
  # Keep the relay checkout + its build (minutes to rebuild) and the log and
  # results history; drop everything that holds protocol state.
  #
  # run.env counts as protocol state: it is where tests hand each other group
  # ids. Leaving it behind a wipe leaves ids naming groups nobody is in any
  # more, and a later `--tests` subset that consumes without re-creating then
  # fails on "not a member" for a group id from a previous run.
  rm -rf "$STATE_DIR/.amy" "$B_DIR" "$C_DIR" "$RELAY_DATA" "$STATE_DIR/run.env"
fi

mkdir -p "$STATE_DIR" "$LOG_DIR" "$B_DIR/logs" "$C_DIR/logs"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# Reuse colours / logging / dump_daemon_diagnostics from the interactive harness.
# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"

# shellcheck source=setup.sh
source "$SCRIPT_DIR/setup.sh"
# shellcheck source=../headless/helpers.sh
source "$TESTS_DIR/headless/helpers.sh"
# shellcheck source=tests-create.sh
source "$SCRIPT_DIR/tests-create.sh"
# shellcheck source=tests-manage.sh
source "$SCRIPT_DIR/tests-manage.sh"
# shellcheck source=tests-extras.sh
source "$SCRIPT_DIR/tests-extras.sh"
# shellcheck source=tests-media.sh
source "$SCRIPT_DIR/tests-media.sh"

# Make sure Ctrl+C / SIGTERM / SIGHUP all run the full cleanup path —
# otherwise wnd is nohup'd and keeps running after the script dies,
# and the next run's `ss` check then complains the port is in use.
# Trap INT/TERM/HUP forces `exit`, which triggers the EXIT handler once.
cleanup() {
  local rc=$?
  trap - EXIT INT TERM HUP
  stop_daemons
  stop_quic_broker
  stop_blossom
  stop_local_relay
  print_summary
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

banner "Marmot headless interop harness ($RUN_TS)"
preflight
start_local_relay
start_quic_broker || true
start_blossom || true
start_daemon B "$B_DIR" "$B_SOCKET"
start_daemon C "$C_DIR" "$C_SOCKET"
ensure_identity_a
ensure_identity B
ensure_identity C
configure_relays

ALL_TESTS=(
  test_01_keypackage_discovery
  test_02_a_creates_group
  test_03_b_creates_group
  test_04_three_member_group
  test_05_b_adds_a_existing
  test_06_member_removal
  test_07_metadata_rename
  test_08_admin_promote_demote
  test_17_group_image_commit
  test_09_reply_react_unreact
  test_10_concurrent_commits
  test_11_leave_group
  test_12_offline_catchup
  test_13_keypackage_rotation
  test_14_wn_removes_a
  test_15_wn_member_leaves
  test_16_wn_keypackage_rotation
  test_18_agent_stream_amy_publishes
  test_19_agent_stream_wn_publishes
  test_20_avatar_url_amy_to_wn
  test_21_avatar_url_wn_to_amy
  test_22_message_edit_amy_to_wn
  test_23_deletion_amy_to_wn
  test_24_media_v2_amy_to_wn
  test_25_media_v2_wn_to_amy
  test_26_retention_amy_to_wn
  test_27_deletion_wn_to_amy
  test_28_retention_wn_to_amy
  test_29_disband_amy_to_wn
)

# --tests runs a subset in the order given. Most tests read state a previous
# one saved (GROUP_02, GROUP_05, …), so a subset that skips a producer will
# report `skip`, not a false failure.
if [[ -n "$ONLY_TESTS" ]]; then
  read -r -a ALL_TESTS <<<"$ONLY_TESTS"
fi

for t in "${ALL_TESTS[@]}"; do
  if ! declare -F "$t" >/dev/null; then
    fail_msg "unknown test: $t"
    continue
  fi
  "$t"
done
