#!/usr/bin/env bash
#
# tier-b.sh — the cordn binding, end to end against the REFERENCE coordinator.
#
# Tier B of quartz/plans/2026-09-17-cordn-interop.md §6.4: a live
# counterparty, not a fixture. Two amy accounts, one local relay (geode), one
# reference coordinator, and the whole lifecycle — publish a KeyPackage,
# create a group, invite, open the Welcome without joining, join, talk in both
# directions, and check both sides agree on epoch and membership.
#
# ─────────────────────────────────────────────────────────────────────────────
# READ THIS BEFORE RUNNING IT
#
# The reference coordinator (`ghcr.io/cordn-msg/cordn`, and the
# `packages/coordinator` / `packages/server` sources it is built from) ships
# with NO LICENSE — default copyright, all rights reserved. See §7 of the plan.
#
# So this script is deliberately NOT wired into anything: no Gradle task, no
# CI job, no `cli/tests` runner references it, and nothing pulls the image for
# you. You pull it by hand, on your own machine, having decided that is
# something you want to do. It is a diagnostic you run when you change the
# ContextVM transport or the coordinator client, not part of the build.
#
# Do not add it to a build file. If Tier B should become routine, the plan says
# what has to happen first: ask upstream for a LICENSE.
# ─────────────────────────────────────────────────────────────────────────────
#
# Prereqs:
#   - a running docker daemon (start it if `docker info` fails), and
#     `docker pull ghcr.io/cordn-msg/cordn:latest`
#   - ./gradlew :cli:installDist :geode:installDist
#
# Usage:
#   ./cli/tests/cordn/tier-b.sh              # boot everything, run, tear down
#   KEEP=1 ./cli/tests/cordn/tier-b.sh       # leave the relay + coordinator up
#
# Exit 0 only if every step passed.

set -uo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../../.." && pwd)"
AMY="$ROOT/cli/build/install/amy/bin/amy"
GEODE="$ROOT/geode/build/install/geode/bin/geode"
WORK="${WORK:-$(mktemp -d)}"
PORT="${PORT:-7447}"
RELAY="ws://127.0.0.1:$PORT"
IMAGE="ghcr.io/cordn-msg/cordn:latest"
CONTAINER="cordn-tier-b"

export AMY_PASSPHRASE="${AMY_PASSPHRASE:-tier-b}"

fail=0
step() { echo; echo "── $*"; }
ok() { echo "   ok: $*"; }
bad() { echo "   FAIL: $*"; fail=1; }

# Every amy run is its own process, which is the point: the cursor on disk is
# the only thing carrying continuity between them.
alice() { HOME="$WORK/alice" "$AMY" --account alice --secret-backend ncryptsec "$@" 2>/dev/null; }
bob() { HOME="$WORK/bob" "$AMY" --account bob --secret-backend ncryptsec "$@" 2>/dev/null; }
field() { python3 -c "import json,sys; d=json.load(sys.stdin); print(json.dumps(d$1) if not isinstance(d$1,str) else d$1)"; }

cleanup() {
    if [ "${KEEP:-0}" != "1" ]; then
        docker rm -f "$CONTAINER" >/dev/null 2>&1
        [ -n "${GEODE_PID:-}" ] && kill "$GEODE_PID" 2>/dev/null
    else
        echo
        echo "KEEP=1: relay on $RELAY, coordinator $CONTAINER, state in $WORK"
    fi
}
trap cleanup EXIT

for f in "$AMY" "$GEODE"; do
    [ -x "$f" ] || { echo "missing $f — run ./gradlew :cli:installDist :geode:installDist"; exit 2; }
done
# Two different problems that used to produce the same message. A dead daemon
# and an unpulled image both fail `docker image inspect`, and telling someone
# to pull an image they cannot pull sends them the wrong way.
docker info >/dev/null 2>&1 || {
    echo "the docker daemon is not reachable — start it (e.g. 'sudo dockerd &' or 'systemctl start docker') and retry"
    exit 2
}
docker image inspect "$IMAGE" >/dev/null 2>&1 || {
    echo "missing $IMAGE — pull it by hand, and read the licence note at the top of this file first"
    exit 2
}

step "boot geode on $RELAY"
"$GEODE" --port "$PORT" >"$WORK/geode.log" 2>&1 &
GEODE_PID=$!
for _ in $(seq 30); do
    curl -sS --noproxy '*' -H 'Accept: application/nostr+json' "http://127.0.0.1:$PORT/" >/dev/null 2>&1 && break
    sleep 1
done
ok "relay up"

step "boot the reference coordinator"
# --network host so the container reaches a relay on the host's loopback.
# A stable key so the coordinator pubkey survives a restart of this script.
[ -f "$WORK/coordinator.key" ] || openssl rand -hex 32 >"$WORK/coordinator.key"
docker rm -f "$CONTAINER" >/dev/null 2>&1
docker run -d --name "$CONTAINER" --network host \
    -e CORDN_STORAGE_BACKEND=memory \
    -e CORDN_ANNOUNCED=false \
    -e CORDN_RELAY_URLS="$RELAY" \
    -e CORDN_SERVER_PRIVATE_KEY="$(cat "$WORK/coordinator.key")" \
    -e CORDN_SERVER_NAME="tier-b" \
    "$IMAGE" >/dev/null || { echo "could not start $CONTAINER"; exit 1; }

COORD=""
for _ in $(seq 60); do
    COORD=$(docker logs "$CONTAINER" 2>&1 | grep -oE 'serverPubkey":"[0-9a-f]{64}' | head -1 | cut -d'"' -f3)
    [ -n "$COORD" ] && break
    sleep 1
done
[ -n "$COORD" ] || { echo "coordinator never announced its pubkey"; docker logs "$CONTAINER" | tail -20; exit 1; }
ok "coordinator $COORD"

step "two accounts"
mkdir -p "$WORK/alice" "$WORK/bob"
alice create --json >/dev/null
bob create --json >/dev/null
ALICE_PK=$(alice whoami --json | field "['hex']")
BOB_PK=$(bob whoami --json | field "['hex']")
ok "alice $ALICE_PK"
ok "bob   $BOB_PK"

step "both remember the coordinator"
alice cordn coordinator add --coordinator "$COORD" --relay "$RELAY" --label tier-b --json >/dev/null
bob cordn coordinator add --coordinator "$COORD" --relay "$RELAY" --label tier-b --json >/dev/null

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
