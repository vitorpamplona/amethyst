#!/usr/bin/env bash
#
# tier-b.sh — the cordn binding, end to end against the REFERENCE coordinator.
#
# Tier B of quartz/plans/2026-09-17-cordn-interop.md §6.4: a live
# counterparty, not a fixture. Three amy accounts, one local relay (geode),
# one reference coordinator, and the whole lifecycle — publish a KeyPackage,
# create a group, invite, open the Welcome without joining, join, talk in both
# directions, and check both sides agree on epoch and membership.
#
# Then the parts that lifecycle never reaches, each driven directly, so that
# all eleven coordinator tools of `spec/00.md` are exercised against a real
# coordinator: a third account who ASKS to join rather than being invited
# (join_request_store, join_request_take_many), a withdrawn KeyPackage
# (kp_remove), a backlog big enough to be chunked (CEP-22), and a message
# pushed down an open stream (msg_sub_many).
#
# The reference coordinator it runs against is UNLICENSED — read the header of
# stack.sh, which boots it, before running this. Nothing here is wired into a
# build, and it must not become so.
#
# Sibling: interop-client.sh puts amy and the reference CLIENT in one group,
# which is the test this one does not do — here both MLS endpoints are ours.
#
# Prereqs: see stack.sh.
#
# Usage:
#   ./cli/tests/cordn/tier-b.sh              # boot everything, run, tear down
#   KEEP=1 ./cli/tests/cordn/tier-b.sh       # leave the relay + coordinator up
#
# Exit 0 only if every step passed.

set -uo pipefail

WORK="${WORK:-$(mktemp -d)}"
PORT="${PORT:-7447}"
CONTAINER="cordn-tier-b"
# shellcheck source=stack.sh
. "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/stack.sh"

export AMY_PASSPHRASE="${AMY_PASSPHRASE:-tier-b}"

fail=0
step() { echo; echo "── $*"; }
ok() { echo "   ok: $*"; }
bad() { echo "   FAIL: $*"; fail=1; }

# Every amy run is its own process, which is the point: the cursor on disk is
# the only thing carrying continuity between them.
alice() { HOME="$WORK/alice" "$AMY" --account alice --secret-backend ncryptsec "$@" 2>/dev/null; }
bob() { HOME="$WORK/bob" "$AMY" --account bob --secret-backend ncryptsec "$@" 2>/dev/null; }
carol() { HOME="$WORK/carol" "$AMY" --account carol --secret-backend ncryptsec "$@" 2>/dev/null; }
field() { python3 -c "import json,sys; d=json.load(sys.stdin); print(json.dumps(d$1) if not isinstance(d$1,str) else d$1)"; }

trap stack_down EXIT

stack_require

step "boot geode on $RELAY, and the reference coordinator"
stack_up
ok "coordinator $COORD"

step "three accounts"
# Carol is here for the door nobody knocks on in the two-party flow: bob is
# invited, so he never sends a join request.
mkdir -p "$WORK/alice" "$WORK/bob" "$WORK/carol"
alice create --json >/dev/null
bob create --json >/dev/null
carol create --json >/dev/null
ALICE_PK=$(alice whoami --json | field "['hex']")
BOB_PK=$(bob whoami --json | field "['hex']")
CAROL_PK=$(carol whoami --json | field "['hex']")
ok "alice $ALICE_PK"
ok "bob   $BOB_PK"
ok "carol $CAROL_PK"

step "all three remember the coordinator"
alice cordn coordinator add --coordinator "$COORD" --relay "$RELAY" --label tier-b --json >/dev/null
bob cordn coordinator add --coordinator "$COORD" --relay "$RELAY" --label tier-b --json >/dev/null
carol cordn coordinator add --coordinator "$COORD" --relay "$RELAY" --label tier-b --json >/dev/null

step "the MCP handshake"
# The first thing to break if the transport regresses, and the cheapest to
# check. A timeout here means the coordinator never saw the request at all —
# which is exactly how §7.1's finding 1 presented.
INFO=$(alice cordn coordinator info --json)
echo "   $INFO"
[ "$(echo "$INFO" | field "['reachable']")" = "true" ] && ok "reachable" || bad "no initialize response"

step "bob publishes a KeyPackage"
KP=$(bob cordn keypackage publish --json)
echo "   $KP"
[ -n "$(echo "$KP" | field "['published'][0]['kp_ref']")" ] && ok "published" || bad "no kp_ref"

step "alice creates a group"
GROUP=$(alice cordn group create --name "Tier B" --about "live" --json)
echo "   $GROUP"
GID=$(echo "$GROUP" | field "['gid']")
[ -n "$GID" ] && ok "gid $GID" || bad "no gid"

step "alice invites bob"
INVITE=$(alice cordn invite --pubkey "$BOB_PK" --json)
echo "   $INVITE"
[ "$(echo "$INVITE" | field "['epoch']")" = "1" ] && ok "epoch 1 after the commit" || bad "epoch did not advance"

step "bob opens the invitation without joining"
PENDING=$(bob cordn welcomes --json)
echo "   $PENDING"
[ "$(echo "$PENDING" | field "['pending'][0]['gid']")" = "$GID" ] && ok "sees $GID" || bad "no pending welcome"
[ "$(echo "$PENDING" | field "['pending'][0]['name']")" = "Tier B" ] && ok "reads the name out of the Welcome" || bad "no metadata in the Welcome"

step "bob joins"
JOINED=$(bob cordn join --all --json)
echo "   $JOINED"
[ "$(echo "$JOINED" | field "['joined']")" = "[\"$GID\"]" ] && ok "joined" || bad "join failed"

step "alice sends, bob reads"
SENT=$(alice cordn send --text "hello from amy" --json)
MSG_ID=$(echo "$SENT" | field "['id']")
GOT=$(bob cordn fetch --json)
echo "   $GOT"
[ "$(echo "$GOT" | field "['messages'][0]['id']")" = "$MSG_ID" ] && ok "same envelope id" || bad "bob did not read it"
[ "$(echo "$GOT" | field "['messages'][0]['sender']")" = "$ALICE_PK" ] && ok "sender is what MLS authenticated" || bad "wrong sender"

step "bob replies, alice reads"
BACK=$(bob cordn send --text "and hello back" --json)
BACK_ID=$(echo "$BACK" | field "['id']")
GOT2=$(alice cordn fetch --json)
echo "   $GOT2"
echo "$GOT2" | grep -q "$BACK_ID" && ok "alice read the reply" || bad "alice did not read the reply"
# §7.1 finding 2: a sender must not report its own traffic as a gap in its own
# conversation. Everything alice posted came back at a cursor she re-reads,
# and recognising it needs bookkeeping that survives the process exit.
[ "$(echo "$GOT2" | field "['undecryptable']")" = "[]" ] && ok "no self-inflicted gaps" || bad "own traffic came back undecryptable: $(echo "$GOT2" | field "['undecryptable']")"

# ── The three tools the two-party flow never reaches, and CEP-22 ────────────
# Every other step above exercises a tool as a side effect of the lifecycle.
# These four do not happen on that path, so they are driven directly.

step "carol asks to join — join_request_store"
REQ=$(carol cordn request --gid "$GID" --json)
echo "   $REQ"
[ "$(echo "$REQ" | field "['gid']")" = "$GID" ] && ok "request stored" || bad "join request not stored"
# A ref is an invitation to ask, not membership (spec/01.md §5.3).
[ "$(echo "$REQ" | field "['member']")" = "false" ] && ok "asking is not joining" || bad "request reported membership"

step "alice reads it — join_request_take_many"
PEND=$(alice cordn requests list --json)
echo "   $PEND"
[ "$(echo "$PEND" | field "['requests'][0]['pubkey']")" = "$CAROL_PK" ] && ok "sees carol" || bad "no pending request"
[ "$(echo "$PEND" | field "['requests'][0]['gid']")" = "$GID" ] && ok "for $GID" || bad "wrong gid on the request"
# Listing must NOT consume. A request is retired by answering it, not by
# reading it, and the ack rides the next call — so a reader that lost the
# process between list and accept has to still find it. Two separate amy
# runs is exactly that case.
AGAIN=$(alice cordn requests list --json)
[ "$(echo "$AGAIN" | field "['requests'][0]['pubkey']")" = "$CAROL_PK" ] && ok "still there for a second reader" || bad "reading a join request consumed it"

step "alice accepts, carol joins"
ACC=$(alice cordn requests accept --pubkey "$CAROL_PK" --json)
echo "   $ACC"
[ "$(echo "$ACC" | field "['answered'][0]['pubkey']")" = "$CAROL_PK" ] && ok "accepted" || bad "accept failed"
CJOIN=$(carol cordn join --all --json)
[ "$(echo "$CJOIN" | field "['joined']")" = "[\"$GID\"]" ] && ok "carol joined" || bad "carol did not join"
# Now it is retired — and the ack for it rode a later call, so this also
# proves the retirement survived the process that issued it.
GONE=$(alice cordn requests list --json)
[ "$(echo "$GONE" | field "['requests']")" = "[]" ] && ok "answering retired it" || bad "an answered request is still pending: $(echo "$GONE" | field "['requests']")"

step "bob withdraws a KeyPackage — kp_remove"
# Two, so there is something left to prove the removal was targeted rather
# than a wipe.
PUB=$(bob cordn keypackage publish --count 2 --json)
KEEP=$(echo "$PUB" | field "['published'][0]['kp_ref']")
DROP=$(echo "$PUB" | field "['published'][1]['kp_ref']")
WD=$(bob cordn keypackage withdraw --kp-ref "$DROP" --json)
echo "   $WD"
[ "$(echo "$WD" | field "['removed']")" = "[\"$DROP\"]" ] && ok "coordinator confirms the removal" || bad "kp_remove did not confirm $DROP"
LIST=$(bob cordn keypackage list --json)
echo "$LIST" | grep -q "$DROP" && bad "the withdrawn package is still served" || ok "gone from kp_list"
echo "$LIST" | grep -q "$KEEP" && ok "the other one survived" || bad "kp_remove took more than it was asked for"

step "a response too big for one event — CEP-22"
# The reference coordinator switches to oversized transfer at a 48000-byte
# published envelope. One message does not reach it; a backlog of them does,
# and a fetch is where a backlog is delivered. Nothing below reads the
# messages differently — a reassembled response is meant to be invisible —
# so the count is the only thing that can tell us the profile ran.
BIG=$(python3 -c "print('x' * 6000)")
for i in $(seq 12); do
    alice cordn send --text "chunk-$i $BIG" --json >/dev/null
done
FETCH=$(bob cordn fetch --json)
COUNT=$(echo "$FETCH" | python3 -c "import json,sys; print(len(json.load(sys.stdin)['messages']))")
CHUNKED=$(echo "$FETCH" | field "['oversized_transfers']")
echo "   $COUNT messages, oversized_transfers=$CHUNKED"
[ "$COUNT" = "12" ] && ok "all 12 arrived" || bad "expected 12 messages, got $COUNT"
[ "${CHUNKED:-0}" -ge 1 ] && ok "reassembled over CEP-22" || bad "the response was never chunked — CEP-22 went unexercised"
# Carol was added at a later epoch, so she must read them too: an oversized
# response is still one response, not a per-member special case.
CGOT=$(carol cordn fetch --json)
CCOUNT=$(echo "$CGOT" | python3 -c "import json,sys; print(len(json.load(sys.stdin)['messages']))")
[ "$CCOUNT" = "12" ] && ok "carol read the same 12" || bad "carol got $CCOUNT of 12"

step "a message delivered over an open stream — msg_sub_many"
# The eleventh tool, and the only one a request/response client never
# reaches: `cordn watch` calls msg_sub_many and nothing else, so anything
# it prints arrived over an open CEP-41 stream rather than a poll.
#
# Ordering is the whole test. The watcher goes up FIRST and we wait for it
# to be listening; only then does alice send. A message sent beforehand
# would be backlog the subscription replays, which proves a stream opened
# but not that anything was pushed down it.
WATCH_OUT="$WORK/watch.json"
bob cordn watch --timeout 25000 --json >"$WATCH_OUT" 2>/dev/null &
WATCH_PID=$!
sleep 8
LIVE="live-$$-$(date +%s)"
alice cordn send --text "$LIVE" --json >/dev/null
wait "$WATCH_PID"
WATCHED=$(cat "$WATCH_OUT")
echo "   $(echo "$WATCHED" | head -c 400)"
[ "$(echo "$WATCHED" | field "['via']")" = "msg_sub_many" ] && ok "the stream was the source" || bad "cordn watch did not report a subscription"
echo "$WATCHED" | grep -q "$LIVE" && ok "pushed live, not polled for" || bad "the subscription never delivered $LIVE"
[ "$(echo "$WATCHED" | field "['messages'][0]['sender']")" = "$ALICE_PK" ] && ok "MLS authenticated the sender over the stream too" || bad "wrong sender on the streamed message"

step "the watcher saved what it ingested"
# Decrypting a streamed message advances the ratchet and the cursor. A
# subscription that ended without writing would leave that only in memory,
# and the next process would re-read its own progress as a gap.
AFTER=$(bob cordn fetch --json)
echo "$AFTER" | grep -q "$LIVE" && bad "the streamed message came back on the next fetch" || ok "the cursor survived the watching process"
[ "$(echo "$AFTER" | field "['undecryptable']")" = "[]" ] && ok "no gaps after the stream closed" || bad "gaps after the stream: $(echo "$AFTER" | field "['undecryptable']")"

step "both sides agree"
A=$(alice cordn group info --json)
B=$(bob cordn group info --json)
[ "$(echo "$A" | field "['epoch']")" = "$(echo "$B" | field "['epoch']")" ] && ok "same epoch" || bad "epochs differ"
[ "$(echo "$A" | field "['members']")" = "$(echo "$B" | field "['members']")" ] && ok "same members" || bad "membership differs"

echo
if [ "$fail" = "0" ]; then
    echo "TIER B PASSED"
else
    echo "TIER B FAILED"
fi
exit "$fail"
