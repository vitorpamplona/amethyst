#!/usr/bin/env bash
#
# buzz-relay-interop.sh — amy's Buzz + NIP-29 verbs against a REAL block/buzz relay.
#
# Every other Buzz/NIP-29 harness here runs against geode (`amy serve`), which is
# not a Buzz relay. This one boots upstream's `buzz-relay` binary on Postgres +
# Redis (docker compose, project `buzz-b-interop`) and drives it with fresh amy
# identities, one $HOME each so no account reads another's local store.
#
# Usage:
#   cli/tests/buzz/buzz-relay-interop.sh --buzz-src DIR [--relay-bin PATH]
#       [--port 7500] [--keep] [--build-amy]
#
#   --buzz-src DIR   a block/buzz checkout. If DIR/target/ci/buzz-relay is missing
#                    it is built with `cargo build --profile ci -j2 -p buzz-relay`
#                    (the profile upstream's scripts/start-isolated-test-relay.sh
#                    uses; ~45 min and ~3 GB on 4 cores the first time).
#   --relay-bin P    use this buzz-relay binary instead (skips the build).
#   --port N         relay port (default 7500); health N+1, metrics N+2,
#                    Postgres N+10, Redis N+11, MinIO N+12.
#   --keep           leave the relay and containers running at the end.
#   --build-amy      run ./gradlew :cli:installDist first (default: use the
#                    installed cli/build/install/amy).
#
# Environment: AMY_BIN (the amy launcher, default this checkout's installed one),
# BUZZ_COMPOSE_PROJECT (compose project name, default buzz-b-interop).
#
# Relay-side setup, the way upstream's own harness does it (no admin UI):
#   * Postgres/Redis/MinIO from buzz-relay-compose.yml (upstream's harness
#     images, tmpfs data, so every run starts empty). MinIO is not optional: the
#     relay exits at startup if its git object-store conformance probe can't
#     write to S3.
#   * BUZZ_AUTO_MIGRATE=true applies the embedded SQLx migrations at startup; the
#     relay then seeds its own community row from RELAY_URL's host
#     (`ensure_deployment_community`), so no setup-desktop-test-data.sh is needed.
#   * RELAY_OWNER_PUBKEY = identity A (bootstrapped as relay owner), open relay
#     (BUZZ_REQUIRE_RELAY_MEMBERSHIP unset), BUZZ_REQUIRE_AUTH_TOKEN=false,
#     a random BUZZ_RELAY_PRIVATE_KEY.
#   * The agent identity G is registered as A's agent with one SQL update
#     (`users.agent_owner_pubkey`), which is what a NIP-OA AUTH would record;
#     amy cannot attach a NIP-OA tag to its AUTH, so this stands in for it.
#
# Identities: A (channel owner, relay owner), B (second person), C and D (third
# and fourth), G (agent owned by A), plus throwaway pubkeys for the DM people cap.
#
# Requires: docker (compose v2), jq, psql, python3, curl.
#
set -uo pipefail
export LC_ALL=C.UTF-8 LANG=C.UTF-8

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-buzz-relay-interop"
LOG_DIR="$STATE_DIR/logs"
LOG_FILE="$LOG_DIR/run.log"
RESULTS_FILE="$STATE_DIR/results"
TIMINGS_FILE="$STATE_DIR/timings"
AMY_BIN="${AMY_BIN:-$REPO_ROOT/cli/build/install/amy/bin/amy}"
COMPOSE_FILE="$SCRIPT_DIR/buzz-relay-compose.yml"
PROJECT="${BUZZ_COMPOSE_PROJECT:-buzz-b-interop}"
PORT=7500
BUZZ_SRC=""
RELAY_BIN=""
KEEP=0
BUILD_AMY=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --buzz-src)  BUZZ_SRC="$2"; shift ;;
    --relay-bin) RELAY_BIN="$2"; shift ;;
    --port)      PORT="$2"; shift ;;
    --keep)      KEEP=1 ;;
    --build-amy) BUILD_AMY=1 ;;
    -h|--help)   sed -n '3,45p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

export BUZZ_PG_PORT=$((PORT + 10)) BUZZ_REDIS_PORT=$((PORT + 11)) BUZZ_MINIO_PORT=$((PORT + 12))
RELAY="ws://127.0.0.1:$PORT"
HTTP="http://127.0.0.1:$PORT"
PG_URL="postgres://buzz:buzz_dev@127.0.0.1:$BUZZ_PG_PORT/buzz"

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR" "$LOG_DIR"
: >"$LOG_FILE"; : >"$RESULTS_FILE"; : >"$TIMINGS_FILE"

# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"

RUN_START=$(date +%s)
RELAY_PID=""

cleanup() {
  if [[ $KEEP -eq 1 ]]; then
    info "--keep: relay pid ${RELAY_PID:-none} and project $PROJECT left running"
    return
  fi
  if [[ -n "$RELAY_PID" ]] && kill -0 "$RELAY_PID" 2>/dev/null; then
    kill "$RELAY_PID" 2>/dev/null; sleep 1; kill -9 "$RELAY_PID" 2>/dev/null
  fi
  docker compose -p "$PROJECT" -f "$COMPOSE_FILE" down -v >>"$LOG_FILE" 2>&1
}
trap cleanup EXIT

for tool in docker jq psql python3 curl; do
  command -v "$tool" >/dev/null || { fail_msg "missing tool: $tool"; exit 1; }
done

# --- amy -----------------------------------------------------------------------
if [[ $BUILD_AMY -eq 1 ]]; then
  step "building :cli:installDist"
  ( cd "$REPO_ROOT" && ./gradlew -q :cli:installDist ) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "installDist failed (see $LOG_FILE)"; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { fail_msg "amy missing at $AMY_BIN (pass --build-amy)"; exit 1; }

# amy as identity WHO, in its own $HOME. Sets OUT (stdout), RC, and appends a timing row.
OUT=""; RC=0
amy_as() {
  local who="$1"; shift
  local t0 t1
  t0=$(date +%s%N)
  OUT=$(HOME="$STATE_DIR/home-$who" "$AMY_BIN" --account "$who" --secret-backend plaintext --json "$@" \
        2>"$LOG_DIR/last-$who.err"); RC=$?
  cat "$LOG_DIR/last-$who.err" >>"$LOG_DIR/amy-$who.err"
  t1=$(date +%s%N)
  printf '%s\t%s\t%s\n' "$(( (t1 - t0) / 1000000 ))" "$who" "$*" >>"$TIMINGS_FILE"
  { printf '[%s rc=%s] amy %s\n' "$who" "$RC" "$*"; printf '%s\n' "$OUT"; } >>"$LOG_FILE"
}
# jq on OUT; empty string on parse failure.
j() { printf '%s' "$OUT" | jq -r "$1" 2>/dev/null; }
# The JSON error object amy printed on stderr under --json in WHO's last call (empty if none).
last_err() { grep '^{' "$LOG_DIR/last-$1.err" | tail -n1; }
# The relay's reason in WHO's last refusal (rejected_by[0].reason, else the error detail).
last_reason() { last_err "$1" | jq -r '.rejected_by[0].reason // .detail // empty' 2>/dev/null; }

pass() { pass_msg "$1 — $2"; record_result "$1" pass "$2"; }
fail() { fail_msg "$1 — $2"; record_result "$1" fail "$2"; }
gap()  { skip_msg "$1 — GAP: $2"; record_result "$1" skip "GAP: $2"; }

psql_q() { psql "$PG_URL" -qtA -v ON_ERROR_STOP=1 -c "$1" 2>>"$LOG_FILE"; }

new_uuid() { python3 -c 'import uuid; print(uuid.uuid4())'; }

# --- relay binary ----------------------------------------------------------------
if [[ -z "$RELAY_BIN" ]]; then
  [[ -n "$BUZZ_SRC" ]] || { fail_msg "pass --buzz-src DIR or --relay-bin PATH"; exit 2; }
  RELAY_BIN="$BUZZ_SRC/target/ci/buzz-relay"
  if [[ ! -x "$RELAY_BIN" ]]; then
    step "building buzz-relay (cargo --profile ci -j2) in $BUZZ_SRC"
    ( cd "$BUZZ_SRC" && cargo build --profile ci -j2 -p buzz-relay ) >>"$LOG_DIR/cargo.log" 2>&1 \
      || { fail_msg "cargo build failed (see $LOG_DIR/cargo.log)"; exit 1; }
  fi
fi
[[ -x "$RELAY_BIN" ]] || { fail_msg "buzz-relay missing at $RELAY_BIN"; exit 1; }
BUZZ_COMMIT=$( [[ -n "$BUZZ_SRC" ]] && git -C "$BUZZ_SRC" rev-parse --short=9 HEAD 2>/dev/null || echo unknown)

banner "Buzz relay interop — buzz $BUZZ_COMMIT on $RELAY"

# --- identities ------------------------------------------------------------------
declare -A HEX NSEC
for who in A B C D G; do
  key=$("$AMY_BIN" --json key generate 2>/dev/null)
  HEX[$who]=$(jq -r .pubkey <<<"$key"); NSEC[$who]=$(jq -r .nsec <<<"$key")
  mkdir -p "$STATE_DIR/home-$who"
  amy_as "$who" init --nsec "${NSEC[$who]}"
  [[ $RC -eq 0 ]] || { fail_msg "amy init $who failed"; exit 1; }
  amy_as "$who" relay add "$RELAY"
  info "$who = ${HEX[$who]}"
done
EXTRA=()
for _ in 1 2 3 4 5 6 7 8 9; do
  EXTRA+=("$("$AMY_BIN" --json key generate 2>/dev/null | jq -r .pubkey)")
done

# --- backing services + relay ----------------------------------------------------
step "docker compose up ($PROJECT: postgres :$BUZZ_PG_PORT, redis :$BUZZ_REDIS_PORT, minio :$BUZZ_MINIO_PORT)"
docker compose -p "$PROJECT" -f "$COMPOSE_FILE" down -v >>"$LOG_FILE" 2>&1
{ docker compose -p "$PROJECT" -f "$COMPOSE_FILE" up -d --wait postgres redis minio \
    && docker compose -p "$PROJECT" -f "$COMPOSE_FILE" run --rm minio-init; } >>"$LOG_FILE" 2>&1 \
  || { fail_msg "compose up failed (see $LOG_FILE)"; exit 1; }

if (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; then
  fail_msg "port $PORT already in use"; exit 1
fi

step "starting buzz-relay"
T_RELAY0=$(date +%s%N)
env DATABASE_URL="$PG_URL" \
  REDIS_URL="redis://127.0.0.1:$BUZZ_REDIS_PORT" \
  RELAY_URL="$RELAY" \
  BUZZ_BIND_ADDR="127.0.0.1:$PORT" \
  BUZZ_HEALTH_PORT=$((PORT + 1)) \
  BUZZ_METRICS_PORT=$((PORT + 2)) \
  BUZZ_RELAY_PRIVATE_KEY="$(python3 -c 'import secrets; print(secrets.token_hex(32))')" \
  BUZZ_S3_ENDPOINT="http://127.0.0.1:$BUZZ_MINIO_PORT" \
  BUZZ_S3_ACCESS_KEY=buzz_dev \
  BUZZ_S3_SECRET_KEY=buzz_dev_secret \
  BUZZ_S3_BUCKET=buzz-media \
  BUZZ_REQUIRE_AUTH_TOKEN=false \
  BUZZ_AUTO_MIGRATE=true \
  RELAY_OWNER_PUBKEY="${HEX[A]}" \
  RUST_LOG="${RUST_LOG:-info}" \
  "$RELAY_BIN" >"$LOG_DIR/relay.log" 2>&1 &
RELAY_PID=$!
for _ in $(seq 1 120); do
  curl -sf -m 1 "$HTTP/health" >/dev/null 2>&1 && break
  kill -0 "$RELAY_PID" 2>/dev/null || break
  sleep 0.5
done
if ! curl -sf -m 1 "$HTTP/health" >/dev/null 2>&1; then
  fail_msg "relay did not come up (see $LOG_DIR/relay.log)"; tail -n 30 "$LOG_DIR/relay.log" >&2; exit 1
fi
T_RELAY1=$(date +%s%N)
info "relay up in $(( (T_RELAY1 - T_RELAY0) / 1000000 )) ms"
RELAY_SELF=$(curl -s -H 'Accept: application/nostr+json' "$HTTP/" | jq -r '.self // .pubkey // empty')
info "NIP-11 self = $RELAY_SELF"

source "$SCRIPT_DIR/buzz-relay-interop-tests.sh"

# --- summary ---------------------------------------------------------------------
print_summary
TOTAL=$(( $(date +%s) - RUN_START ))
{
  printf '\nTotal wall time: %ss. amy calls: %s, summed %s ms. Slowest:\n' "$TOTAL" \
    "$(wc -l <"$TIMINGS_FILE")" "$(awk -F'\t' '{s+=$1} END{print s}' "$TIMINGS_FILE")"
  sort -t$'\t' -k1,1nr "$TIMINGS_FILE" | head -n 12 | awk -F'\t' '{printf "  %6d ms  %s  %s\n", $1, $2, $3}'
} | tee -a "$LOG_FILE"
awk -F'\t' '$2=="fail"{f=1} END{exit f}' "$RESULTS_FILE"
