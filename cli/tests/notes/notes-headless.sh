#!/usr/bin/env bash
#
# notes-headless.sh — the core social loop between two amy accounts over the
# embedded `amy serve` relay (geode). No external relay, no other client.
#
# Alice and Bob live in separate $HOMEs (separate event stores), so every read
# Bob does of Alice's events really crosses the relay. We assert that:
#
#   1. `notes post` tags an inline #hashtag (`t`), and `notes feed --hashtag`
#      finds the note.
#   2. `notes show` renders Alice's note for Bob (fetched from the relay):
#      author, hashtag body span, seen_on.
#   3. `notes reply` emits a NIP-10 kind:1 (root marker + author p-tag);
#      `notes react` a kind:7 `+`; `notes repost` a kind:6;
#      `notes quote` a kind:1 with a `q` tag and a nostr:nevent in the text.
#   4. `notes thread` from Alice's nested reply lays out root → reply → nested
#      at depths 0/1/2, focus marked.
#   5. `notifications` shows Alice the reply, reaction, repost and the quote
#      (as a mention); `--type reaction` narrows to one.
#   6. `delete` refuses someone else's note (exit 1, forbidden) and deletes
#      your own: the relay then no longer serves it (`notes show` → not_found).
#
# Then the outbox model across two relays. amy (like the app) drops private and
# loopback relays from other people's NIP-65 lists, so routing can only be
# exercised on a relay whose address is not private. Relays A and B run on such
# an address (auto-detected from the interfaces, or --routing-host); Alice
# reads and writes on A, Bob on B, Carol reads on A and writes on B. We assert:
#
#   7. Bob's reply, reaction and repost of Alice's note reach her inbox (A) and
#      his outbox (B); a post mentioning Carol reaches her inbox (A).
#   8. Bob's deletion reaches both relays (where the reply was routed).
#   9. A NIP-25 reaction to Bob's reply (root `e` first, target `e` last) is
#      Bob's notification.
#  10. A reply deleted while relay A was down — A still serves it — stays out
#      of `notes thread`.
#
# Without a usable address the section is skipped. Any host can provide one on
# loopback: `sudo ip addr add 192.0.2.2/32 dev lo` (Linux) or
# `sudo ifconfig lo0 alias 192.0.2.2 up` (macOS), then --routing-host 192.0.2.2.
#
# Usage: ./notes-headless.sh [--no-build] [--port N] [--routing-host IP | --no-routing]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-notes-headless"
LOG_DIR="$STATE_DIR/logs"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"
NO_BUILD=0
RELAY_HOST=127.0.0.2
RELAY_PORT=7787
ROUTING_HOST=""
ROUTING=1

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) NO_BUILD=1 ;;
    --port) RELAY_PORT="$2"; shift ;;
    --routing-host) ROUTING_HOST="$2"; shift ;;
    --no-routing) ROUTING=0 ;;
    -h|--help)
      sed -n '3,46p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
      exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"
RELAY_DATA="$STATE_DIR/relay"

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR" "$LOG_DIR" "$STATE_DIR/alice" "$STATE_DIR/bob"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"
# shellcheck source=../headless/helpers.sh
source "$SCRIPT_DIR/../headless/helpers.sh"

command -v jq >/dev/null || { fail_msg "jq is required"; exit 1; }

if [[ $NO_BUILD -eq 0 ]]; then
  step "building amy (installDist)"
  (cd "$REPO_ROOT" && ./gradlew -q :cli:installDist) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "gradle :cli:installDist failed"; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { fail_msg "amy binary missing at $AMY_BIN"; exit 1; }

# --- the routing relays (A and B) --------------------------------------------
ROUTE_DIR="$STATE_DIR/routing"
ROUTE_PORT_A=$((RELAY_PORT + 1))
ROUTE_PORT_B=$((RELAY_PORT + 2))

# An IPv4 amy routes to: not loopback, RFC 1918 or link-local — the ranges
# RelayUrlNormalizer.isLocalHost drops from other people's relay lists.
is_routable_ipv4() {
  [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] || return 1
  case "$1" in 0.*|10.*|127.*|169.254.*|192.168.*) return 1 ;; esac
  [[ ! $1 =~ ^172\.(1[6-9]|2[0-9]|3[01])\. ]]
}

# The first routable IPv4 on any interface.
detect_routing_host() {
  local addr
  for addr in $( { hostname -I 2>/dev/null; ip -4 -o addr show 2>/dev/null | awk '{print $4}'; ifconfig 2>/dev/null | awk '/inet /{print $2}'; } | tr ' ' '\n' | sed 's#/.*##'); do
    is_routable_ipv4 "$addr" && { printf '%s\n' "$addr"; return 0; }
  done
  return 1
}

# start_routing_relay NAME PORT — `amy serve` with a persistent --db, so it can be
# stopped and restarted with its events. Returns 1 when it does not come up.
start_routing_relay() {
  local name="$1" port="$2" dir="$ROUTE_DIR/relay-$1"
  mkdir -p "$dir"
  if [[ ! -d "$dir/.amy" ]]; then
    HOME="$dir" "$AMY_BIN" --account relay --secret-backend plaintext --json init >>"$LOG_FILE" 2>&1 || return 1
  fi
  if (exec 3<>"/dev/tcp/$ROUTING_HOST/$port") 2>/dev/null; then
    fail_msg "port $port already in use on $ROUTING_HOST"
    return 1
  fi
  nohup env HOME="$dir" "$AMY_BIN" --account relay --secret-backend plaintext \
    serve --host "$ROUTING_HOST" --port "$port" --db "$dir/relay.db" >>"$dir/relay.log" 2>&1 &
  echo "$!" >"$dir/pid"
  local deadline=$(( $(date +%s) + 60 ))
  while [[ $(date +%s) -lt $deadline ]]; do
    curl -sSf -m 1 -H 'Accept: application/nostr+json' "http://$ROUTING_HOST:$port/" >/dev/null 2>&1 && return 0
    kill -0 "$(cat "$dir/pid")" 2>/dev/null || break
    sleep 0.5
  done
  return 1
}

stop_routing_relay() {
  local pid_file="$ROUTE_DIR/relay-$1/pid" pid
  [[ -f "$pid_file" ]] || return 0
  pid="$(cat "$pid_file")"
  if kill -0 "$pid" 2>/dev/null; then
    kill "$pid" 2>/dev/null
    for _ in $(seq 20); do kill -0 "$pid" 2>/dev/null || break; sleep 0.25; done
    kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$pid_file"
}

cleanup() {
  stop_routing_relay a
  stop_routing_relay b
  stop_local_relay
}

trap cleanup EXIT
start_local_relay

# Boolean flags go AFTER positionals: amy's parser reads `--flag VALUE`.
alice() { HOME="$STATE_DIR/alice" "$AMY_BIN" --account alice --secret-backend plaintext --json "$@" 2>>"$LOG_FILE"; }
bob() { HOME="$STATE_DIR/bob" "$AMY_BIN" --account bob --secret-backend plaintext --json "$@" 2>>"$LOG_FILE"; }

check() {
  # usage: check TEST_ID JSON JQ_EXPR [NOTE]
  local id="$1" json="$2" expr="$3" note="${4:-}"
  if jq -e "$expr" <<<"$json" >/dev/null 2>&1; then
    record_result "$id" pass "$note"
  else
    record_result "$id" fail "$(head -c 300 <<<"$json")"
  fi
}

banner "two accounts on $RELAY_URL"
for who in alice bob; do
  "$who" init >>"$LOG_FILE" || { record_result "$who-init" fail; exit 1; }
  "$who" relay nip65 clear >>"$LOG_FILE"
  "$who" relay add "$RELAY_URL" >>"$LOG_FILE"
  "$who" relay publish-lists >>"$LOG_FILE"
done
ALICE_HEX="$(alice whoami | jq -r .hex)"
BOB_HEX="$(bob whoami | jq -r .hex)"
record_result "setup" pass "alice=${ALICE_HEX:0:8} bob=${BOB_HEX:0:8}"

step "alice posts"
POST="$(alice notes post "hello #AmyTest from alice")"
PID="$(jq -r .event_id <<<"$POST")"
check post "$POST" '.kind == 1 and (.published_to | length) == 1'

step "bob shows alice's note (from the relay)"
SHOW="$(bob notes show "$PID")"
check show "$SHOW" \
  ".source == \"relays\" and .note.author.pubkey == \"$ALICE_HEX\" and (.note.hashtags | index(\"amytest\")) and (.note.body | map(.type) | index(\"hashtag\"))"

step "bob reads the hashtag feed"
FEED="$(bob notes feed --hashtag amytest)"
check feed-hashtag "$FEED" ".mode == \"hashtag\" and (.notes | map(.event_id) | index(\"$PID\"))"

step "bob replies, reacts, reposts, quotes"
REPLY="$(bob notes reply "$PID" "hi alice")"
RID="$(jq -r .event_id <<<"$REPLY")"
check reply "$REPLY" \
  ".kind == 1 and (.tags | any(.[0] == \"e\" and .[1] == \"$PID\" and .[3] == \"root\")) and (.tags | any(.[0] == \"p\" and .[1] == \"$ALICE_HEX\"))"
check react "$(bob notes react "$PID")" '.kind == 7 and .content == "+"'
check repost "$(bob notes repost "$PID")" ".kind == 6 and .target.event_id == \"$PID\""
check quote "$(bob notes quote "$PID" "look at this")" \
  ".kind == 1 and (.content | contains(\"nostr:nevent1\")) and (.tags | any(.[0] == \"q\" and .[1] == \"$PID\"))"

step "alice answers bob; bob reads the thread from the leaf"
NESTED="$(alice notes reply "$RID" "thanks bob")"
NID="$(jq -r .event_id <<<"$NESTED")"
THREAD="$(bob notes thread "$NID")"
check thread "$THREAD" \
  ".root_id == \"$PID\" and ([.notes[].event_id] == [\"$PID\", \"$RID\", \"$NID\"]) and ([.notes[].depth] == [0, 1, 2]) and .notes[2].is_focus"

step "alice's notifications"
NOTIF="$(alice notifications)"
check notifications "$NOTIF" \
  '[.notifications[].type] | (index("reply") and index("reaction") and index("repost") and index("mention"))'
check notifications-type "$(alice notifications --type reaction)" \
  ".count == 1 and .notifications[0].type == \"reaction\" and .notifications[0].from.pubkey == \"$BOB_HEX\""

step "delete"
bob delete "$PID" >>"$LOG_FILE"
RC=$?
[[ $RC -eq 1 ]] && record_result delete-forbidden pass || record_result delete-forbidden fail "exit=$RC"
check delete "$(alice delete "$PID")" ".deleted == [\"$PID\"] and (.deletions[0].published_to | length) == 1"
bob notes show "$PID" --refresh >>"$LOG_FILE"
RC=$?
[[ $RC -eq 1 ]] && record_result delete-honoured pass "relay no longer serves it" || record_result delete-honoured fail "exit=$RC"

# --- routing across two relays ---------------------------------------------

routing_skip() {
  record_result routing skip "$1"
  warn "routing section skipped: $1"
}

if [[ $ROUTING -eq 0 ]]; then
  routing_skip "--no-routing"
elif [[ -z "$ROUTING_HOST" ]] && ! ROUTING_HOST="$(detect_routing_host)"; then
  routing_skip "no non-private IPv4 on any interface; see --help for a loopback alias"
elif ! is_routable_ipv4 "$ROUTING_HOST"; then
  routing_skip "$ROUTING_HOST is loopback/private: amy drops such relays from others' lists"
elif ! start_routing_relay a "$ROUTE_PORT_A" || ! start_routing_relay b "$ROUTE_PORT_B"; then
  routing_skip "could not start relays on $ROUTING_HOST (see $ROUTE_DIR/relay-*/relay.log)"
else
  RA="ws://$ROUTING_HOST:$ROUTE_PORT_A"
  RB="ws://$ROUTING_HOST:$ROUTE_PORT_B"
  banner "routing: relay A $RA, relay B $RB"

  # Fresh accounts: the loopback ones above know the loopback relay.
  ra() { local who="$1"; shift; HOME="$ROUTE_DIR/$who" "$AMY_BIN" --account "$who" --secret-backend plaintext --json "$@" 2>>"$LOG_FILE"; }
  # reaches A B: a jq test that a publish result went to both relays.
  reaches_both="(.published_to | any(startswith(\"$RA\"))) and (.published_to | any(startswith(\"$RB\")))"

  for who in alice bob carol; do
    mkdir -p "$ROUTE_DIR/$who"
    ra "$who" init >>"$LOG_FILE" || { record_result "routing-init" fail "$who"; break; }
    ra "$who" relay nip65 clear >>"$LOG_FILE"
    ra "$who" relay dm clear >>"$LOG_FILE"
    ra "$who" relay key-package clear >>"$LOG_FILE"
  done
  ra alice relay outbox add "$RA" >>"$LOG_FILE"; ra alice relay inbox add "$RA" >>"$LOG_FILE"
  ra bob relay outbox add "$RB" >>"$LOG_FILE"; ra bob relay inbox add "$RB" >>"$LOG_FILE"
  ra carol relay outbox add "$RB" >>"$LOG_FILE"; ra carol relay inbox add "$RA" >>"$LOG_FILE"
  for who in alice bob carol; do ra "$who" relay publish-lists >>"$LOG_FILE"; done
  R_ALICE="$(ra alice whoami | jq -r .hex)"
  R_BOB="$(ra bob whoami | jq -r .hex)"
  R_CAROL_NPUB="$(ra carol whoami | jq -r .npub)"

  step "bob interacts with alice's note across relays"
  R_POST="$(ra alice notes post "hello from relay A" | jq -r .event_id)"
  R_NEVENT="$(ra alice encode nevent "$R_POST" --relay "$RA" | jq -r .nevent)"
  R_REPLY="$(ra bob notes reply "$R_NEVENT" "hi alice, from B")"
  R_RID="$(jq -r .event_id <<<"$R_REPLY")"
  check route-reply "$R_REPLY" "$reaches_both" "alice's inbox + bob's outbox"
  check route-react "$(ra bob notes react "$R_POST")" "$reaches_both"
  check route-repost "$(ra bob notes repost "$R_POST")" "$reaches_both"
  check route-mention "$(ra bob notes post "hey nostr:$R_CAROL_NPUB")" "$reaches_both" "carol's inbox + bob's outbox"

  step "bob deletes a reply on both relays"
  R_GONE="$(ra bob notes reply "$R_NEVENT" "short-lived" | jq -r .event_id)"
  check route-delete "$(ra bob delete "$R_GONE")" "[.deletions[] | $reaches_both] | all"

  step "carol reacts to bob's reply (NIP-25 shape)"
  ra carol event --kind 7 --content + \
    --tags "[[\"e\",\"$R_POST\"],[\"e\",\"$R_RID\"],[\"p\",\"$R_ALICE\"],[\"p\",\"$R_BOB\"]]" --relay "$RB" >>"$LOG_FILE"
  check route-reaction-notification "$(ra bob notifications --type reaction)" \
    "[.notifications[].reply_to.event_id] | index(\"$R_RID\")"

  step "a reply deleted while relay A was down stays out of the thread"
  R_STALE="$(ra bob notes reply "$R_NEVENT" "to be deleted" | jq -r .event_id)"
  stop_routing_relay a
  ra bob delete "$R_STALE" --timeout 3 >>"$LOG_FILE"
  if start_routing_relay a "$ROUTE_PORT_A"; then
    # Precondition: A missed the kind:5 and still serves the reply (Carol asks: her store
    # has neither), or the check below proves nothing.
    if ra carol notes show "$R_STALE" --refresh | jq -e ".seen_on | any(startswith(\"$RA\"))" >/dev/null; then
      check route-thread-deleted "$(ra bob notes thread "$R_POST")" \
        ".root_found and ([.notes[].event_id] | index(\"$R_RID\")) and ([.notes[].event_id] | index(\"$R_STALE\") | not)" \
        "relay A still serves it"
    else
      record_result route-thread-deleted fail "precondition: relay A no longer serves the deleted reply"
    fi
  else
    record_result route-thread-deleted fail "relay A did not restart"
  fi
fi

print_summary
! grep -q $'\tfail\t' "$RESULTS_FILE"
