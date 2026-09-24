#!/usr/bin/env bash
#
# interop-client.sh — amy and the REFERENCE CLIENT in one group.
#
# The claim Tier B does not test. `tier-b.sh` runs amy against amy through the
# reference *coordinator*: it proves our transport and our coordinator client,
# but both MLS endpoints are ours, so the ratchet tree, the Welcome and the
# Commit are only ever agreeing with themselves. This script puts
# **`@cordn/cli` (ts-mls)** on one end and **amy (quartz)** on the other, which
# is the actual interop claim: two independent RFC 9420 implementations in one
# group, over a live wire.
#
# `@cordn/cli` is **MIT** and comes from npm, so this half carries no licensing
# problem. The coordinator underneath it still does — see stack.sh, and read it
# before running this.
#
# What it covers, and why each direction is its own test:
#
#   1. Their group, our joiner    — our engine opens a ts-mls Welcome, reads
#                                   the group metadata extension and the
#                                   roster out of it, and decrypts their
#                                   application messages.
#   2. Our group, their joiner    — their engine opens OUR Welcome. This is
#                                   the direction that tests our output, and
#                                   it is the one a fixture can never check,
#                                   because a fixture we wrote accepts what we
#                                   emit by construction.
#   3. Our later Commit           — the sharpest one. Our engine emits
#                                   **public-framed** handshake messages
#                                   (`MlsMessage(PublicMessage)`, wireformat
#                                   2) while cordn's client emits
#                                   private-framed. `CordnGroupManager.invite`
#                                   asserts in its KDoc that their
#                                   `processMessageBase64` admits both — a
#                                   claim read off their source and never
#                                   executed. Here they must process our
#                                   Commit to stay in the group at all: if
#                                   they cannot, their epoch stalls and every
#                                   later message fails to open.
#
# Prereqs: see stack.sh, plus network access to npm for `@cordn/cli`.
#
# Usage:
#   ./cli/tests/cordn/interop-client.sh
#   KEEP=1 ./cli/tests/cordn/interop-client.sh     # leave the stack up
#
# Exit 0 only if every step passed.

set -uo pipefail

WORK="${WORK:-$(mktemp -d)}"
PORT="${PORT:-7452}"
CONTAINER="cordn-interop-client"
# shellcheck source=stack.sh
. "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/stack.sh"

export AMY_PASSPHRASE="${AMY_PASSPHRASE:-interop}"

fail=0
step() { echo; echo "── $*"; }
ok() { echo "   ok: $*"; }
bad() { echo "   FAIL: $*"; fail=1; }
check() { if [ "$1" = "$2" ]; then ok "$3"; else bad "$3 (expected '$2', got '$1')"; fi; }

trap stack_down EXIT

stack_require
command -v npm >/dev/null 2>&1 || { echo "npm is needed to install @cordn/cli"; exit 2; }

# A stuck reference client should fail the run rather than hang it, but macOS
# ships no `timeout` (it is GNU coreutils; `gtimeout` if Homebrew installed
# them). Without this the whole harness died at the first `ref` call with
# nothing but "could not read the reference client's pubkey", because ref()
# folds stderr into the output it parses and "command not found" matches no
# field. Running untimed is better than not running.
if command -v timeout >/dev/null 2>&1; then
    REF_TIMEOUT="timeout 120"
elif command -v gtimeout >/dev/null 2>&1; then
    REF_TIMEOUT="gtimeout 120"
else
    REF_TIMEOUT=""
    echo "note: no timeout(1) — the reference client runs untimed"
fi

step "boot geode on $RELAY and the reference coordinator"
stack_up
ok "coordinator $COORD"

step "install @cordn/cli (MIT) from npm"
mkdir -p "$WORK/ref"
npm install --silent --prefix "$WORK/ref" @cordn/cli >"$WORK/npm.log" 2>&1 || {
    echo "npm install failed; see $WORK/npm.log"
    exit 1
}
CORDN="$WORK/ref/node_modules/.bin/cordn"
[ -x "$CORDN" ] || { echo "no cordn binary at $CORDN"; exit 1; }
ok "$("$CORDN" --version 2>/dev/null || echo unknown)"

# ---- the two clients ------------------------------------------------------
# amy is process-per-command by design; the reference client is driven the
# same way with --command, so neither side gets to hold state in RAM that the
# other cannot see. Whatever agreement they reach went over the wire.
amy() { HOME="$WORK/amy" "$AMY" --account a --secret-backend ncryptsec "$@" 2>/dev/null; }
amy2() { HOME="$WORK/amy2" "$AMY" --account b --secret-backend ncryptsec "$@" 2>/dev/null; }
ref() {
    # shellcheck disable=SC2086  # REF_TIMEOUT is a command prefix, or empty
    $REF_TIMEOUT "$CORDN" \
        --private-key-file "$WORK/ref.key" \
        --server-pubkey "$COORD" \
        --relay "$RELAY" \
        --state-file "$WORK/ref-state.json" \
        --command "$1" 2>&1
}
field() { python3 -c "import json,sys; d=json.load(sys.stdin); v=d$1; print(v if isinstance(v,str) else json.dumps(v))"; }
# Their output comes in two shapes and it is worth having both readers rather
# than one clever one: `group-info` and `available-kps` print `key=value`
# tokens, `status` prints `key: value`.
reffield() { grep -oE "$1=[^ ]+" | head -1 | cut -d= -f2-; }
refcolon() { grep -oE "^$1: .*" | head -1 | cut -d' ' -f2-; }

step "identities"
mkdir -p "$WORK/amy" "$WORK/amy2"
openssl rand -hex 32 >"$WORK/ref.key"
amy create --json >/dev/null
amy2 create --json >/dev/null
AMY_PK=$(amy whoami --json | field "['hex']")
AMY2_PK=$(amy2 whoami --json | field "['hex']")
REF_PK=$(ref "status" | refcolon "stablePubkey")
[ -n "$REF_PK" ] || { echo "could not read the reference client's pubkey"; exit 1; }
ok "amy      $AMY_PK"
ok "amy(2nd) $AMY2_PK"
ok "ts-mls   $REF_PK"

for a in amy amy2; do
    $a cordn coordinator add --coordinator "$COORD" --relay "$RELAY" --json >/dev/null
done

step "both sides publish a KeyPackage"
amy cordn keypackage publish --json >/dev/null
amy2 cordn keypackage publish --json >/dev/null
ref "gen-kp k1" >/dev/null
# Each client has to be able to READ the other's publication off the
# coordinator, which means our KeyPackage has to parse under their zod schema
# and their capability flags have to survive our encoder.
KPS=$(ref "available-kps")
echo "$KPS" | grep -q "$AMY_PK" && ok "ts-mls can read our published KeyPackage" || bad "our KeyPackage is invisible to them"
echo "$KPS" | grep -q "groupMetadataSupport=yes" && ok "and reads our metadata capability" || bad "our capability flags did not survive"

# ---------------------------------------------------------------------------
step "DIRECTION 1 — their group, our joiner"
# ---------------------------------------------------------------------------
ref "create-group g1 --name TheirGroup" >/dev/null
THEIR_GID=$(ref "group-info g1" | reffield "groupId")
ok "ts-mls created $THEIR_GID"

ref "add-member g1 $AMY_PK" >/dev/null
PENDING=$(amy cordn welcomes --json)
check "$(echo "$PENDING" | field "['pending'][0]['gid']")" "$THEIR_GID" "our engine opened a ts-mls Welcome"
# Read out of the Welcome itself, so these assert that their GroupContext
# extensions and their credentials decode under our parser.
check "$(echo "$PENDING" | field "['pending'][0]['name']")" "TheirGroup" "and read their metadata extension"
echo "$PENDING" | grep -q "$REF_PK" && ok "and their credential in the roster" || bad "their credential did not decode"

amy cordn join --all --json >/dev/null
ref "send-to g1 hello from ts-mls" >/dev/null
GOT=$(amy cordn fetch --json)
check "$(echo "$GOT" | field "['messages'][0]['content']")" "hello from ts-mls" "we decrypt their application message"
check "$(echo "$GOT" | field "['messages'][0]['sender']")" "$REF_PK" "and MLS authenticates them as the sender"

amy cordn send --gid "$THEIR_GID" --text "hello from quartz" --json >/dev/null
ref "sync g1" >/dev/null
ref "messages g1" | grep -q "hello from quartz" && ok "they decrypt ours" || bad "they could not read our message"

# ---------------------------------------------------------------------------
step "DIRECTION 2 — our group, their joiner"
# ---------------------------------------------------------------------------
# The direction a fixture cannot test: a fixture we wrote accepts what we emit
# by construction, so only a foreign implementation can say our Welcome is
# well formed.
OUR_GID="quartz-side-group"
amy cordn group create --gid "$OUR_GID" --name "OurGroup" --json >/dev/null
ref "gen-kp k2" >/dev/null
INVITE=$(amy cordn invite --gid "$OUR_GID" --pubkey "$REF_PK" --json)
SPENT=$(echo "$INVITE" | field "['kp_ref']")
check "$(echo "$INVITE" | field "['epoch']")" "1" "our commit advanced us to epoch 1"

ref "fetch-welcomes" >/dev/null
ACCEPTED=$(ref "accept-welcome $SPENT g2")
echo "$ACCEPTED" | grep -q "name=OurGroup" && ok "ts-mls opened OUR Welcome and read our metadata" || bad "ts-mls could not open our Welcome: $ACCEPTED"
check "$(ref "group-info g2" | reffield "groupId")" "$OUR_GID" "and agrees on the gid"

amy cordn send --gid "$OUR_GID" --text "quartz made this group" --json >/dev/null
ref "sync g2" >/dev/null
ref "messages g2" | grep -q "quartz made this group" && ok "they read ours at epoch 1" || bad "they could not read ours"

ref "send-to g2 ts-mls replying in a quartz group" >/dev/null
amy cordn fetch --json | grep -q "ts-mls replying in a quartz group" && ok "we read theirs" || bad "we could not read theirs"

# ---------------------------------------------------------------------------
step "DIRECTION 3 — our LATER commit, which they must process"
# ---------------------------------------------------------------------------
# Until now their epoch came from a Welcome, which carries the group state
# ready-made. This is the first time they have to apply one of our handshake
# messages, and ours are public-framed (wireformat 2) where theirs are
# private-framed. If they cannot parse it their epoch stalls at 1 and the
# message they send afterwards is sealed under a key we do not have.
amy2 cordn keypackage publish --json >/dev/null
COMMIT=$(amy cordn invite --gid "$OUR_GID" --pubkey "$AMY2_PK" --json)
check "$(echo "$COMMIT" | field "['epoch']")" "2" "our second commit advanced us to epoch 2"

ref "sync g2" >/dev/null
ref "send-to g2 after the quartz commit" >/dev/null
AFTER=$(amy cordn fetch --json)
# The real assertion: a message they sealed at epoch 2 only opens if they
# applied our Commit. A stalled peer would have sealed at epoch 1, and this
# would come back undecryptable instead.
check "$(echo "$AFTER" | field "['messages'][0]['content']")" "after the quartz commit" "ts-mls applied our public-framed Commit"
check "$(echo "$AFTER" | field "['messages'][0]['epoch']")" "2" "and sealed at the new epoch"

step "the third member joins a group two implementations built"
amy2 cordn join --all --json >/dev/null
THIRD=$(amy2 cordn fetch --json)
echo "$THIRD" | grep -q "after the quartz commit" && ok "reads the ts-mls message it was welcomed into" || bad "third member could not read history at its join epoch"

step "all three agree"
A_EPOCH=$(amy cordn group info --gid "$OUR_GID" --json | field "['epoch']")
B_EPOCH=$(amy2 cordn group info --gid "$OUR_GID" --json | field "['epoch']")
R_CURSOR=$(ref "group-info g2" | reffield "cursor")
check "$A_EPOCH" "2" "quartz (inviter) at epoch 2"
check "$B_EPOCH" "2" "quartz (invitee) at epoch 2"
[ -n "$R_CURSOR" ] && ok "ts-mls advanced to cursor $R_CURSOR" || bad "ts-mls reported no cursor"

MEMBERS=$(amy cordn group info --gid "$OUR_GID" --json | field "['members']")
for pk in "$AMY_PK" "$AMY2_PK" "$REF_PK"; do
    echo "$MEMBERS" | grep -q "$pk" || bad "roster is missing $pk"
done
ok "roster holds all three credentials"

echo
if [ "$fail" = "0" ]; then
    echo "CLIENT INTEROP PASSED"
else
    echo "CLIENT INTEROP FAILED"
fi
exit "$fail"
