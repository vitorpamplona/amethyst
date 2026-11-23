#!/usr/bin/env bash
#
# relaygroup-relay29.sh — amy's NIP-29 verbs against a SECOND relay implementation:
# fiatjaf/relay29 (khatru29, MIT; the NIP author's relay library), on loopback.
#
# relaygroup-headless.sh runs against geode, which stores but does not enforce
# NIP-29. This one runs against a relay that does: it signs 39000-39003, issues
# the 9000/9001 for join and leave requests, checks roles before a moderation
# action and keeps private groups from non-members.
#
# Usage:
#   cli/tests/relaygroup/relaygroup-relay29.sh [--relay29-bin PATH | --relay29-src DIR]
#       [--port 7329] [--no-build]
#
#   (default)        build relay29-runner/ with `go build` (Go >= 1.24); go.mod pins
#                    github.com/fiatjaf/relay29 at 3276594e9d85 (2025-06-21), fetched
#                    from the Go module proxy.
#   --relay29-bin P  a relay29 runner already built (skips the Go build).
#   --relay29-src D  build the runner against a local fiatjaf/relay29 checkout.
#   --port N         relay29 listens on 127.0.0.1:N; amy's home relay (geode,
#                    `amy serve`, for the kind-10009 lists) on N+1.
#   --no-build       use the installed cli/build/install/amy (default builds it).
#
# The runner (relay29-runner/main.go) configures relay29 the way its own example
# does: roles "admin" (the creator's; every action) and "moderator" (remove-user,
# delete-event), private groups allowed, events within ±60 s of now.
#
# Identities: A (creator, admin), B (member, then moderator), C (outsider),
# D (member B removes). Every amy call runs with AMY_LOCAL_ONLY=1.
#
# Requires: jq, go (unless --relay29-bin).
#
set -uo pipefail
export LC_ALL=C.UTF-8 LANG=C.UTF-8

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-relaygroup-relay29"
LOG_DIR="$STATE_DIR/logs"
LOG_FILE="$LOG_DIR/run.log"
RESULTS_FILE="$STATE_DIR/results"
AMY_BIN="${AMY_BIN:-$REPO_ROOT/cli/build/install/amy/bin/amy}"
PORT=7329
RELAY29_BIN=""
RELAY29_SRC=""
NO_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --relay29-bin) RELAY29_BIN="$2"; shift ;;
    --relay29-src) RELAY29_SRC="$2"; shift ;;
    --port)        PORT="$2"; shift ;;
    --no-build)    NO_BUILD=1 ;;
    -h|--help)     sed -n '3,30p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; exit 0 ;;
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
RELAY_PID=""
SERVE_PID=""

cleanup() {
  [[ -n "$RELAY_PID" ]] && kill "$RELAY_PID" 2>/dev/null
  [[ -n "$SERVE_PID" ]] && kill "$SERVE_PID" 2>/dev/null
  wait 2>/dev/null
}
trap cleanup EXIT

command -v jq >/dev/null || { fail_msg "missing tool: jq"; exit 1; }

# --- relay29 ---------------------------------------------------------------------
if [[ -z "$RELAY29_BIN" ]]; then
  command -v go >/dev/null || { fail_msg "missing tool: go"; exit 1; }
  step "building the relay29 runner${RELAY29_SRC:+ against $RELAY29_SRC}"
  BUILD_DIR="$STATE_DIR/relay29-runner"
  mkdir -p "$BUILD_DIR"
  cp "$SCRIPT_DIR/relay29-runner/main.go" "$SCRIPT_DIR/relay29-runner/go.mod" "$SCRIPT_DIR/relay29-runner/go.sum" "$BUILD_DIR/"
  ( cd "$BUILD_DIR" \
      && { [[ -z "$RELAY29_SRC" ]] || go mod edit -replace "github.com/fiatjaf/relay29=$(cd "$RELAY29_SRC" && pwd)"; } \
      && go build -o relay29-runner . ) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "relay29 runner build failed (see $LOG_FILE)"; exit 1; }
  RELAY29_BIN="$BUILD_DIR/relay29-runner"
fi
[[ -x "$RELAY29_BIN" ]] || { fail_msg "relay29 runner missing at $RELAY29_BIN"; exit 1; }

# --- amy -------------------------------------------------------------------------
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
# info GID → OUT holds `relaygroup info` as A (a member, authenticated).
info() { amy_as A relaygroup info "$RELAY" "$1" --timeout 4; }
# relay-issued KIND GID PUBKEY → 0 when relay29 signed a KIND naming PUBKEY in GID.
relay_issued() {
  amy_as A fetch --relay "$RELAY" --kind "$1" --tag "h=$2" --timeout 4
  jqo --arg r "$RELAY_PK" --arg p "$3" '[.events[] | select(.pubkey == $r) | select(any(.tags[]; .[0] == "p" and .[1] == $p))] | length' | grep -qv '^0$'
}

banner "NIP-29 interop: amy vs fiatjaf/relay29 on $RELAY"

step "boot relay29"
"$RELAY29_BIN" -port "$PORT" >"$LOG_DIR/relay29.log" 2>&1 &
RELAY_PID=$!
for _ in $(seq 1 40); do grep -q "listening" "$LOG_DIR/relay29.log" 2>/dev/null && break; sleep 0.25; done
RELAY_PK=$(sed -n 's/.*relay29 pubkey \([0-9a-f]\{64\}\).*/\1/p' "$LOG_DIR/relay29.log")
[[ -n "$RELAY_PK" ]] || { fail_msg "relay29 did not start (see $LOG_DIR/relay29.log)"; exit 1; }
info "relay29 pubkey $RELAY_PK"

step "init identities A B C D"
declare -A HEX
for who in A B C D; do
  amy_as "$who" init || { fail_msg "init $who failed"; exit 1; }
  HEX[$who]=$(jqo .hex)
done

step "boot amy's home relay (amy serve) on $HOME_RELAY"
HOME="$STATE_DIR/home" "$AMY_BIN" --account A --secret-backend plaintext serve --port "$((PORT + 1))" >"$LOG_DIR/serve.log" 2>&1 &
SERVE_PID=$!
for _ in $(seq 1 60); do grep -q "relay up\|listening" "$LOG_DIR/serve.log" 2>/dev/null && break; sleep 0.5; done
for who in A B C D; do amy_as "$who" relay add "$HOME_RELAY" || { fail_msg "relay add $who failed"; exit 1; }; done

# --- rg-01 create: relay29 signs 39000-39003 --------------------------------------
amy_as A relaygroup create "$RELAY" --name "Pizza Lovers" --about "a group for people who love pizza"
GID=$(jqo '.group_id // empty')
[[ -n "$GID" && "$(jqo .published)" == "true" ]]
check rg-01-create $? "created $GID" "create was not accepted: $OUT"
[[ -n "$GID" ]] || { print_summary; exit 1; }

info "$GID"
[[ "$(jqo .name)" == "Pizza Lovers" && "$(jqo .about)" == "a group for people who love pizza" && "$(jqo .private)" == "false" && "$(jqo .closed)" == "false" ]]
check rg-01-metadata $? "39000 carries the name and about of our 9002, public and open" "39000 read back: $OUT"
[[ "$(jqo --arg a "${HEX[A]}" '[.admins[] | select(.pubkey == $a) | .roles[]] | join(",")')" == "admin" && "$(jqo '.members | length')" == "1" ]]
check rg-01-roster $? "39001 lists A as admin, 39002 holds A alone" "roster read back: $OUT"
[[ "$(jqo '[.roles[].name] | sort | join(",")')" == "admin,moderator" ]]
check rg-01-roles $? "39003 advertises the relay's roles (admin, moderator)" "roles read back: $OUT"

amy_as A fetch --relay "$RELAY" --kind 39000,39001,39002,39003 --tag "d=$GID" --timeout 4
[[ "$(jqo '.count')" -ge 4 && "$(jqo --arg r "$RELAY_PK" '[.events[] | select(.pubkey != $r)] | length')" == "0" ]]
check rg-01-relay-signed $? "all four state events are signed by relay29's key" "state events: $OUT"

# --- rg-02 browse ------------------------------------------------------------------
amy_as C relaygroup browse "$RELAY" --timeout 4
jqo --arg g "$GID" '.groups[] | select(.group_id == $g)' | grep -q .
check rg-02-browse $? "an outsider's browse lists the open group" "browse: $OUT"

# --- rg-03 join an open group: relay29 issues the 9000 ------------------------------
amy_as B relaygroup join "$RELAY" "$GID"
[[ "$(jqo .published)" == "true" && "$(jqo .listed)" == "true" ]]
check rg-03-join $? "B's 9021 accepted and the group added to B's kind 10009" "join: $OUT"
amy_as D relaygroup join "$RELAY" "$GID"
relay_issued 9000 "$GID" "${HEX[B]}"
check rg-03-admitted $? "relay29 signed a 9000 putting B in" "no relay-signed 9000 for B: $OUT"
info "$GID"
[[ "$(jqo --arg b "${HEX[B]}" --arg d "${HEX[D]}" '[.members[] | select(. == $b or . == $d)] | length')" == "2" ]]
check rg-03-members $? "39002 now lists B and D" "members: $OUT"
amy_as B relaygroup list
jqo --arg g "$GID" '.groups[] | select(.group_id == $g)' | grep -q .
check rg-03-list $? "relaygroup list shows the joined group" "list: $OUT"

# --- rg-04 writing: members yes, outsiders no -------------------------------------
amy_as B relaygroup message "$RELAY" "$GID" "hello from B"
[[ "$RC" -eq 0 && "$(jqo .published)" == "true" ]]
check rg-04-member-posts $? "a member's kind 9 is accepted" "B's message: $OUT"
amy_as C relaygroup message "$RELAY" "$GID" "C is not a member"
refused_with "unknown member"
check rg-04-outsider-refused $? "an outsider's kind 9 is refused with relay29's reason (unknown member)" "C's message: rc=$RC $OUT"

# --- rg-05 put-user with a role ----------------------------------------------------
amy_as A relaygroup put-user "$RELAY" "$GID" "${HEX[B]}" --role moderator
info "$GID"
[[ "$(jqo --arg b "${HEX[B]}" '[.admins[] | select(.pubkey == $b) | .roles[]] | join(",")')" == "moderator" ]]
check rg-05-promote $? "A's 9000 with role moderator shows in 39001" "admins: $OUT"

# --- rg-06 remove-user by a moderator; removed users stay out ----------------------
amy_as B relaygroup remove-user "$RELAY" "$GID" "${HEX[D]}"
info "$GID"
[[ "$(jqo --arg d "${HEX[D]}" '[.members[] | select(. == $d)] | length')" == "0" ]]
check rg-06-remove $? "the moderator's 9001 drops D from 39002" "members: $OUT"
amy_as D relaygroup join "$RELAY" "$GID"
info "$GID"
[[ "$(jqo --arg d "${HEX[D]}" '[.members[] | select(. == $d)] | length')" == "0" ]]
check rg-06-no-rejoin $? "relay29 ignores a removed user's new join request (its policy)" "members: $OUT"

# --- rg-07 edit-metadata needs the admin role --------------------------------------
amy_as B relaygroup edit "$RELAY" "$GID" --name "B renames it"
refused_with "insufficient permissions"
check rg-07-moderator-cannot-edit $? "the moderator's 9002 is refused (insufficient permissions)" "B's edit: rc=$RC $OUT"
amy_as A relaygroup edit "$RELAY" "$GID" --name "Pizza Fans"
info "$GID"
[[ "$(jqo .name)" == "Pizza Fans" && "$(jqo .about)" == "a group for people who love pizza" ]]
check rg-07-admin-edits $? "the admin's 9002 renames it and keeps the about" "39000: $OUT"

# --- rg-08 delete-event by a moderator ----------------------------------------------
amy_as A relaygroup message "$RELAY" "$GID" "to be deleted"
DOOMED=$(jqo .event_id)
amy_as B event --kind 9005 --relay "$RELAY" --tags "[[\"h\",\"$GID\"],[\"e\",\"$DOOMED\"]]"
[[ "$(jqo .published)" == "true" ]]
check rg-08-delete-accepted $? "the moderator's 9005 is accepted" "9005: $OUT"
amy_as A fetch --relay "$RELAY" --kind 9 --tag "h=$GID" --timeout 4
[[ "$(jqo --arg id "$DOOMED" '[.events[] | select(.id == $id)] | length')" == "0" && "$(jqo '[.events[] | select(.content == "hello from B")] | length')" == "1" ]]
check rg-08-deleted $? "the deleted message is gone, the other one stays" "messages: $OUT"

# --- rg-09 private + closed -----------------------------------------------------------
amy_as A relaygroup edit "$RELAY" "$GID" --private --closed
info "$GID"
[[ "$(jqo .private)" == "true" && "$(jqo .closed)" == "true" && "$(jqo .name)" == "Pizza Fans" ]]
check rg-09-private-closed $? "39000 is private and closed, the name kept" "39000: $OUT"
amy_as C fetch --relay "$RELAY" --kind 9 --tag "h=$GID" --timeout 4
refused_with "restricted: not a member"
check rg-09-outsider-cannot-read $? "an outsider's read of the private group is refused (restricted: not a member)" "C's read: rc=$RC $OUT"
amy_as B fetch --relay "$RELAY" --kind 9 --tag "h=$GID" --timeout 4
[[ "$RC" -eq 0 && "$(jqo .count)" -ge 1 ]]
check rg-09-member-reads $? "a member, authenticated, still reads it" "B's read: rc=$RC $OUT"
amy_as C relaygroup browse "$RELAY" --timeout 4
[[ "$(jqo --arg g "$GID" '[.groups[] | select(.group_id == $g)] | length')" == "0" ]]
check rg-09-hidden-from-browse $? "an outsider's browse no longer lists it" "browse: $OUT"

# --- rg-10 a closed group ignores join requests ------------------------------------
amy_as C relaygroup join "$RELAY" "$GID"
info "$GID"
[[ "$(jqo --arg c "${HEX[C]}" '[.members[] | select(. == $c)] | length')" == "0" ]]
check rg-10-closed-join-ignored $? "C's join to the closed group admits nobody" "members: $OUT"

# --- rg-11 invite codes: relay29 has no kind 9009 ----------------------------------
amy_as A relaygroup invite "$RELAY" "$GID" --code pizza-night
refused_with "9009 is not a supported moderation action"
check rg-11-invite-unsupported $? "relay29 refuses the 9009 and amy reports its reason (no invite codes on relay29)" "invite: rc=$RC $OUT"

# --- rg-12 back to public and open --------------------------------------------------
# NIP-29 "Group state": a 9002 carries "all the fields of group-metadata", so a 9002 without
# `private` makes the group public. relay29 only flips back on an explicit legacy `public`/`open`
# tag; quartz > 2.24.0 adds those when an edit lifts the flag (GroupEditMetadataEvent.buildEdit).
amy_as A relaygroup edit "$RELAY" "$GID" --public --open
info "$GID"
[[ "$(jqo .private)" == "false" && "$(jqo .closed)" == "false" ]]
check rg-12-public-open $? "39000 is public and open again" "39000 still: $OUT (needs quartz > 2.24.0)"

# --- rg-13 leave: relay29 issues the 9001 -------------------------------------------
amy_as B relaygroup leave "$RELAY" "$GID"
[[ "$(jqo .published)" == "true" ]]
check rg-13-leave $? "B's 9022 accepted" "leave: $OUT"
relay_issued 9001 "$GID" "${HEX[B]}"
check rg-13-removed $? "relay29 signed a 9001 removing B" "no relay-signed 9001 for B: $OUT"
amy_as B relaygroup list
[[ "$(jqo --arg g "$GID" '[.groups[] | select(.group_id == $g)] | length')" == "0" ]]
check rg-13-unlisted $? "the group left B's kind 10009" "list: $OUT"

# --- rg-14 delete-group ----------------------------------------------------------------
amy_as A event --kind 9008 --relay "$RELAY" --tags "[[\"h\",\"$GID\"]]"
[[ "$(jqo .published)" == "true" ]]
check rg-14-delete-group $? "A's 9008 accepted" "9008: $OUT"
amy_as A relaygroup info "$RELAY" "$GID" --timeout 4
[[ "$RC" -ne 0 ]]
check rg-14-gone $? "info finds no group any more" "info: rc=$RC $OUT"

print_summary
awk -F'\t' '$2=="fail"{f=1} END{exit f}' "$RESULTS_FILE"
