#!/usr/bin/env bash
#
# relaygroup-zooid.sh — amy's NIP-29 verbs against zooid (coracle-social, MIT), the
# maintained multi-tenant relay behind Flotilla's spaces, on loopback.
#
# relaygroup-relay29.sh covers per-group roles; zooid is the second implementation
# for what relay29 lacks: invite codes (kind 9009 + a 9021 `code`) and `hidden`
# groups, and it reads a 9002 the way NIP-29 says (the tags it carries are the
# group's state). In zooid a group's admins are the relay's managers (its owner,
# `info.pubkey`, and its own key): there are no per-group roles.
#
# Usage:
#   cli/tests/relaygroup/relaygroup-zooid.sh --zooid-bin PATH [--port 7340] [--no-build]
#   cli/tests/relaygroup/relaygroup-zooid.sh --zooid-src DIR  [--port 7340] [--no-build]
#
#   --zooid-bin P   a zooid relay binary (`go build -o zooid ./cmd/relay` in a checkout of
#                   https://gitea.coracle.social/coracle/zooid; tested at a015ff5b0, 0.3.0).
#   --zooid-src D   build it from that checkout (needs Go; the module asks for a newer
#                   toolchain, which `go` downloads).
#   --port N        zooid on 127.0.0.1:N, amy's home relay (geode) on N+1, Postgres
#                   (docker, postgres:17-alpine on tmpfs) on N+10.
#   --no-build      use the installed cli/build/install/amy.
#
# Config: one virtual relay, host 127.0.0.1:N, groups on, public read and write
# (no relay membership step), A as owner. Identities: A (owner, so group admin),
# B (member), C (outsider, later invited).
#
# Requires: jq, docker.
#
set -uo pipefail
export LC_ALL=C.UTF-8 LANG=C.UTF-8

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-relaygroup-zooid"
LOG_DIR="$STATE_DIR/logs"
LOG_FILE="$LOG_DIR/run.log"
RESULTS_FILE="$STATE_DIR/results"
AMY_BIN="${AMY_BIN:-$REPO_ROOT/cli/build/install/amy/bin/amy}"
PORT=7340
ZOOID_BIN=""
ZOOID_SRC=""
NO_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --zooid-bin) ZOOID_BIN="$2"; shift ;;
    --zooid-src) ZOOID_SRC="$2"; shift ;;
    --port)      PORT="$2"; shift ;;
    --no-build)  NO_BUILD=1 ;;
    -h|--help)   sed -n '3,30p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR" "$LOG_DIR"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"

export AMY_LOCAL_ONLY=1
RELAY="ws://127.0.0.1:$PORT"
HOME_RELAY="ws://127.0.0.1:$((PORT + 1))"
PG_PORT=$((PORT + 10))
PG_NAME="amy-zooid-pg-$PORT"
RELAY_PID=""
SERVE_PID=""

cleanup() {
  [[ -n "$RELAY_PID" ]] && kill "$RELAY_PID" 2>/dev/null
  [[ -n "$SERVE_PID" ]] && kill "$SERVE_PID" 2>/dev/null
  docker rm -f "$PG_NAME" >/dev/null 2>&1
  wait 2>/dev/null
}
trap cleanup EXIT

for tool in jq docker; do command -v "$tool" >/dev/null || { fail_msg "missing tool: $tool"; exit 1; }; done

if [[ -z "$ZOOID_BIN" ]]; then
  [[ -n "$ZOOID_SRC" ]] || { fail_msg "pass --zooid-bin or --zooid-src (see --help)"; exit 2; }
  step "building zooid in $ZOOID_SRC"
  ( cd "$ZOOID_SRC" && go build -o "$STATE_DIR/zooid" ./cmd/relay ) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "zooid build failed (see $LOG_FILE)"; exit 1; }
  ZOOID_BIN="$STATE_DIR/zooid"
fi
[[ -x "$ZOOID_BIN" ]] || { fail_msg "zooid missing at $ZOOID_BIN"; exit 1; }

if [[ "$NO_BUILD" -eq 0 ]]; then
  step "building :cli:installDist"
  ( cd "$REPO_ROOT" && ./gradlew -q :cli:installDist ) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "installDist failed (see $LOG_FILE)"; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { fail_msg "amy binary missing at $AMY_BIN"; exit 1; }

# amy as identity WHO. Sets OUT (stdout) and RC; stderr goes to the log.
OUT=""; RC=0
amy_as() {
  local who="$1"; shift
  printf '$ [%s] amy %s\n' "$who" "$*" >>"$LOG_FILE"
  OUT=$(HOME="$STATE_DIR/home" "$AMY_BIN" --account "$who" --secret-backend plaintext --json "$@" 2>"$LOG_DIR/last.err")
  RC=$?
  cat "$LOG_DIR/last.err" >>"$LOG_FILE"
  printf '%s\n(rc=%s)\n' "$OUT" "$RC" >>"$LOG_FILE"
}
# refused_with TEXT → 0 when the last call failed and its error names TEXT (the relay's reason).
refused_with() { [[ "$RC" -ne 0 ]] && grep -q "$1" "$LOG_DIR/last.err"; }
jqo() { printf '%s' "$OUT" | jq -r "$@" 2>/dev/null; }
check() { # check ID COND-EXIT-STATUS PASS-DETAIL FAIL-DETAIL
  if [[ "$2" -eq 0 ]]; then pass_msg "$1: $3"; record_result "$1" pass "$3"; else fail_msg "$1: $4"; record_result "$1" fail "$4"; fi
}
# group_info GID → OUT holds `relaygroup info` as A (a member, authenticated). Not `info`: that is
# lib.sh's log line, which the boot messages below still use.
group_info() { amy_as A relaygroup info "$RELAY" "$1" --timeout 4; }
# relay-issued KIND GID PUBKEY → 0 when zooid signed a KIND naming PUBKEY in GID.
relay_issued() {
  amy_as A fetch --relay "$RELAY" --kind "$1" --tag "h=$2" --timeout 4
  jqo --arg r "$RELAY_PK" --arg p "$3" '[.events[] | select(.pubkey == $r) | select(any(.tags[]; .[0] == "p" and .[1] == $p))] | length' | grep -qv '^0$'
}

banner "NIP-29 interop: amy vs zooid on $RELAY"

step "init identities A B C"
declare -A HEX
for who in A B C; do
  amy_as "$who" init || { fail_msg "init $who failed"; exit 1; }
  HEX[$who]=$(jqo .hex)
done

step "postgres ($PG_NAME on 127.0.0.1:$PG_PORT)"
docker rm -f "$PG_NAME" >/dev/null 2>&1
docker run -d --name "$PG_NAME" -p "127.0.0.1:$PG_PORT:5432" --tmpfs /var/lib/postgresql/data \
  -e POSTGRES_USER=zooid -e POSTGRES_PASSWORD=zooid -e POSTGRES_DB=zooid postgres:17-alpine >>"$LOG_FILE" 2>&1 \
  || { fail_msg "docker run postgres failed"; exit 1; }
for _ in $(seq 1 60); do docker exec "$PG_NAME" pg_isready -U zooid >/dev/null 2>&1 && break; sleep 0.5; done

step "boot zooid"
for p in "$PORT" "$((PORT + 1))"; do
  port_in_use "$p" && { fail_msg "127.0.0.1:$p is already in use (a relay from an earlier run?); pass --port"; exit 1; }
done
RELAY_SK=$(od -An -N32 -tx1 /dev/urandom | tr -d ' \n')
mkdir -p "$STATE_DIR/zooid-config" "$STATE_DIR/zooid-media"
cat >"$STATE_DIR/zooid-config/interop.toml" <<TOML
host = "127.0.0.1:$PORT"
schema = "interop"
secret = "$RELAY_SK"

[info]
name = "zooid interop"
pubkey = "${HEX[A]}"
description = "zooid on loopback for amy's NIP-29 harness"

[policy]
public_read = true
public_write = true

[groups]
enabled = true
TOML
( cd "$STATE_DIR" && exec env PORT="$PORT" CONFIG="$STATE_DIR/zooid-config" MEDIA="$STATE_DIR/zooid-media" DATA="$STATE_DIR/zooid-data" \
    DATABASE_URL="postgres://zooid:zooid@127.0.0.1:$PG_PORT/zooid?sslmode=disable" "$ZOOID_BIN" >"$LOG_DIR/zooid.log" 2>&1 ) &
RELAY_PID=$!
for _ in $(seq 1 60); do curl -sf -m 1 -H 'Accept: application/nostr+json' "http://127.0.0.1:$PORT/" >/dev/null 2>&1 && break; sleep 0.5; done
RELAY_PK=$(curl -s -H 'Accept: application/nostr+json' "http://127.0.0.1:$PORT/" | jq -r '.self // empty')
# The relay answering is the one just started: alive, and announcing the key this run gave it.
kill -0 "$RELAY_PID" 2>/dev/null || RELAY_PK=""
[[ -n "$RELAY_PK" ]] || { fail_msg "zooid did not start (see $LOG_DIR/zooid.log)"; tail -20 "$LOG_DIR/zooid.log" >&2; exit 1; }
info "zooid self $RELAY_PK"

step "boot amy's home relay (amy serve) on $HOME_RELAY"
HOME="$STATE_DIR/home" "$AMY_BIN" --account A --secret-backend plaintext serve --port "$((PORT + 1))" >"$LOG_DIR/serve.log" 2>&1 &
SERVE_PID=$!
for _ in $(seq 1 60); do grep -q "relay up\|listening" "$LOG_DIR/serve.log" 2>/dev/null && break; sleep 0.5; done
for who in A B C; do amy_as "$who" relay add "$HOME_RELAY" || { fail_msg "relay add $who failed"; exit 1; }; done

# --- zg-01 create ---------------------------------------------------------------------
amy_as A relaygroup create "$RELAY" --name "Pizza Lovers" --about "a group for people who love pizza"
GID=$(jqo '.group_id // empty')
[[ -n "$GID" && "$(jqo .published)" == "true" ]]
check zg-01-create $? "the owner created $GID" "create: $OUT"
[[ -n "$GID" ]] || { print_summary; exit 1; }
group_info "$GID"
[[ "$(jqo .name)" == "Pizza Lovers" && "$(jqo .about)" == "a group for people who love pizza" && "$(jqo .private)" == "false" ]]
check zg-01-metadata $? "39000 carries our 9002's name and about" "39000: $OUT"
[[ "$(jqo --arg a "${HEX[A]}" '[.admins[] | select(.pubkey == $a)] | length')" == "1" ]]
check zg-01-admins $? "39001 lists the owner (zooid's group admins are its managers)" "39001: $OUT"
amy_as A fetch --relay "$RELAY" --kind 39000,39001 --tag "d=$GID" --timeout 4
[[ "$(jqo .count)" -ge 2 && "$(jqo --arg r "$RELAY_PK" '[.events[] | select(.pubkey != $r)] | length')" == "0" ]]
check zg-01-relay-signed $? "the state events are signed by zooid's NIP-11 self key" "state: $OUT"

# --- zg-02 join an open group -----------------------------------------------------------
amy_as B relaygroup join "$RELAY" "$GID"
relay_issued 9000 "$GID" "${HEX[B]}"
check zg-02-admitted $? "zooid signed a 9000 putting B in" "no relay-signed 9000 for B: $OUT"
group_info "$GID"
[[ "$(jqo --arg b "${HEX[B]}" '[.members[] | select(. == $b)] | length')" == "1" ]]
check zg-02-members $? "39002 lists B" "39002: $OUT"
amy_as B relaygroup message "$RELAY" "$GID" "hello from B"
[[ "$RC" -eq 0 ]]
check zg-02-member-posts $? "B's kind 9 is accepted" "B's message: rc=$RC $OUT"

# --- zg-03 only managers moderate --------------------------------------------------------
amy_as B relaygroup put-user "$RELAY" "$GID" "${HEX[C]}"
refused_with "not authorized to manage groups"
check zg-03-member-cannot-moderate $? "B's 9000 is refused (only zooid's managers moderate)" "B's put-user: rc=$RC $OUT"

# --- zg-04 private + closed ----------------------------------------------------------------
amy_as A relaygroup edit "$RELAY" "$GID" --private --closed
group_info "$GID"
[[ "$(jqo .private)" == "true" && "$(jqo .closed)" == "true" && "$(jqo .name)" == "Pizza Lovers" ]]
check zg-04-private-closed $? "39000 is private and closed, the name kept" "39000: $OUT"
amy_as C fetch --relay "$RELAY" --kind 9 --tag "h=$GID" --timeout 4
[[ "$(jqo '.count // 0')" == "0" ]]
check zg-04-outsider-reads-nothing $? "an outsider gets none of the private group's messages" "C's read: rc=$RC $OUT"
amy_as B fetch --relay "$RELAY" --kind 9 --tag "h=$GID" --timeout 4
[[ "$(jqo '.count // 0')" -ge 1 ]]
check zg-04-member-reads $? "a member still reads them" "B's read: rc=$RC $OUT"

# --- zg-05 closed: no code, no entry; an invite code lets C in -----------------------------
amy_as C relaygroup join "$RELAY" "$GID"
group_info "$GID"
[[ "$(jqo --arg c "${HEX[C]}" '[.members[] | select(. == $c)] | length')" == "0" ]]
check zg-05-closed-join-ignored $? "C's join without a code admits nobody" "members: $OUT"
amy_as A relaygroup invite "$RELAY" "$GID" --code pizza-night
[[ "$RC" -eq 0 && "$(jqo .published)" == "true" ]]
check zg-05-invite $? "the owner's 9009 create-invite is accepted" "invite: rc=$RC $OUT"
amy_as C relaygroup join "$RELAY" "$GID" --code pizza-night
group_info "$GID"
[[ "$(jqo --arg c "${HEX[C]}" '[.members[] | select(. == $c)] | length')" == "1" ]]
check zg-05-code-admits $? "C's 9021 with the code is admitted" "members: $OUT"

# --- zg-06 back to public and open (a 9002 without the flags) ---------------------------------
amy_as A relaygroup edit "$RELAY" "$GID" --public --open
group_info "$GID"
[[ "$(jqo .private)" == "false" && "$(jqo .closed)" == "false" ]]
check zg-06-public-open $? "39000 is public and open again" "39000: $OUT"

# --- zg-07 hidden survives an edit that does not mention it --------------------------------
amy_as A event --kind 9002 --relay "$RELAY" --tags "[[\"h\",\"$GID\"],[\"name\",\"Pizza Lovers\"],[\"about\",\"a group for people who love pizza\"],[\"hidden\"]]"
amy_as A relaygroup edit "$RELAY" "$GID" --name "Pizza Fans"
amy_as A fetch --relay "$RELAY" --kind 39000 --tag "d=$GID" --timeout 4
[[ "$(jqo '[.events[0].tags[] | select(.[0] == "hidden")] | length')" == "1" && "$(jqo '[.events[0].tags[] | select(.[0] == "name") | .[1]] | join(",")')" == "Pizza Fans" ]]
check zg-07-hidden-carried $? "amy's rename carried the hidden flag it has no switch for" "39000: $OUT"
amy_as B relaygroup leave "$RELAY" "$GID"
# Not vacuous: a public control group shows in the same browse, and the hidden one still exists.
amy_as A relaygroup create "$RELAY" --name "Control" --about "a public group the browse must list"
CONTROL=$(jqo '.group_id // empty')
group_info "$GID"
HIDDEN_EXISTS=$([[ "$RC" -eq 0 && "$(jqo .name)" == "Pizza Fans" ]] && echo 1 || echo 0)
amy_as B relaygroup browse "$RELAY" --timeout 4
[[ "$RC" -eq 0 && -n "$CONTROL" && "$HIDDEN_EXISTS" == 1 \
  && "$(jqo --arg g "$CONTROL" '[.groups[] | select(.group_id == $g)] | length')" == "1" \
  && "$(jqo --arg g "$GID" '[.groups[] | select(.group_id == $g)] | length')" == "0" ]]
check zg-07-hidden-from-outsiders $? "the hidden group exists for its members but is not listed to a non-member (the public one is)" "control=$CONTROL hidden-exists=$HIDDEN_EXISTS browse: $OUT"
relay_issued 9001 "$GID" "${HEX[B]}"
check zg-07-leave $? "B's 9022 made zooid sign a 9001" "no relay-signed 9001 for B: $OUT"

# --- zg-08 delete-event, delete-group ----------------------------------------------------------
amy_as C relaygroup message "$RELAY" "$GID" "to be deleted"
DOOMED=$(jqo .event_id)
amy_as A event --kind 9005 --relay "$RELAY" --tags "[[\"h\",\"$GID\"],[\"e\",\"$DOOMED\"]]"
amy_as A fetch --relay "$RELAY" --kind 9 --tag "h=$GID" --timeout 4
[[ -n "$DOOMED" && "$(jqo --arg id "$DOOMED" '[.events[] | select(.id == $id)] | length')" == "0" ]]
check zg-08-delete-event $? "the owner's 9005 removed C's message" "messages: $OUT"
amy_as A event --kind 9008 --relay "$RELAY" --tags "[[\"h\",\"$GID\"]]"
amy_as A relaygroup info "$RELAY" "$GID" --timeout 4
# The group's own absence, not any failure (a dead relay, a timeout): amy's not_found.
refused_with '"error":"not_found"'
check zg-08-delete-group $? "after the 9008 info finds no group (not_found)" "info: rc=$RC $OUT $(cat "$LOG_DIR/last.err")"

print_summary
awk -F'\t' '$2=="fail"{f=1} END{exit f}' "$RESULTS_FILE"
