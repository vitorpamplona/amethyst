#!/usr/bin/env bash
#
# migrate.sh — one account, two devices, a handoff between them.
#
# The claim tier-b.sh does not test. There, two accounts talk to each other.
# Here ONE account moves from the phone it is on to a new one, which is the
# case `spec/applications/multi-device.md` calls device addition (§11) and we
# implement as a one-shot handoff rather than continuous sync.
#
# What this proves that the unit tests cannot:
#
#   * the sealed documents survive a real HTTP round trip to a blob server,
#   * the tip survives a real relay as a real replaceable event,
#   * the new device, starting from an empty home and a scanned string alone,
#     ends up holding the same groups.
#
# The blob server is a throwaway python stand-in for Blossom: PUT /upload
# stores by sha256, GET /<sha256> serves it back. Enough to exercise the real
# HttpCordnBlobStore, and deliberately not a Blossom implementation.
#
# Usage: cli/tests/cordn/migrate.sh
set -uo pipefail

WORK="${WORK:-$(mktemp -d)}"
CONTAINER="cordn-migrate"
# shellcheck source=stack.sh
. "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/stack.sh"

export AMY_PASSPHRASE="${AMY_PASSPHRASE:-migrate}"

BLOB_PORT="${BLOB_PORT:-877}"
BLOB="http://127.0.0.1:$BLOB_PORT"
BLOB_DIR="$WORK/blobs"
BLOB_PID=""

fail=0
step() { echo; echo "── $*"; }
ok() { echo "   ok: $*"; }
bad() { echo "   FAIL: $*"; fail=1; }

# Two HOMEs, one identity: the same nsec on an old phone and a new one. That is
# what a migration is, and it is why the new device needs no Welcome — it
# adopts the shared leaf rather than joining as a member (§9).
old() { HOME="$WORK/old" "$AMY" --account me --secret-backend ncryptsec "$@" 2>/dev/null; }
new() { HOME="$WORK/new" "$AMY" --account me --secret-backend ncryptsec "$@" 2>/dev/null; }
field() { python3 -c "import json,sys; d=json.load(sys.stdin); print(json.dumps(d$1) if not isinstance(d$1,str) else d$1)"; }

blob_up() {
  mkdir -p "$BLOB_DIR"
  python3 - "$BLOB_PORT" "$BLOB_DIR" >"$WORK/blob.log" 2>&1 &
  BLOB_PID=$!
  sleep 1
} <<'PYEOF'
import hashlib, os, sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

port, root = int(sys.argv[1]), sys.argv[2]

class H(BaseHTTPRequestHandler):
    def do_PUT(self):
        body = self.rfile.read(int(self.headers.get("Content-Length", 0)))
        digest = hashlib.sha256(body).hexdigest()
        open(os.path.join(root, digest), "wb").write(body)
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(('{"sha256":"%s","size":%d}' % (digest, len(body))).encode())

    def do_GET(self):
        path = os.path.join(root, self.path.lstrip("/"))
        if not os.path.isfile(path):
            self.send_response(404); self.end_headers(); return
        body = open(path, "rb").read()
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *a): pass

ThreadingHTTPServer(("127.0.0.1", port), H).serve_forever()
PYEOF

cleanup() {
  [ -n "$BLOB_PID" ] && kill "$BLOB_PID" 2>/dev/null
  stack_down
}
trap cleanup EXIT

stack_require

step "boot geode on $RELAY, the reference coordinator, and a blob server on $BLOB"
stack_up
blob_up
ok "coordinator $COORD"

step "one account, on its old phone"
mkdir -p "$WORK/old" "$WORK/new"
old create --json >/dev/null
PK=$(old whoami --json | field "['hex']")
ok "npub $PK"

# The new phone is the SAME identity. Copy the key across the way a user would
# by signing in again; the migration carries group state, never identity (§4.3).
NSEC=$(old key export --json 2>/dev/null | field "['nsec']" 2>/dev/null)
if [ -z "$NSEC" ]; then
  cp -r "$WORK/old/.amy" "$WORK/new/.amy"
  ok "new phone signed in (identity copied)"
else
  new import --nsec "$NSEC" --json >/dev/null
  ok "new phone signed in"
fi

step "the old phone has a group"
old cordn coordinator add --coordinator "$COORD" --relay "$RELAY" --label migrate --json >/dev/null
GROUP=$(old cordn group create --name "Moves with me" --about "handoff" --json)
GID=$(echo "$GROUP" | field "['gid']")
[ -n "$GID" ] && ok "gid $GID" || bad "no group to migrate"

step "the old phone exports a handoff"
EXPORT=$(old cordn migrate export --relay "$RELAY" --server "$BLOB" --json)
echo "   $EXPORT"
CODE=$(echo "$EXPORT" | field "['code']")
if [ -n "$CODE" ] && [ "${CODE:0:9}" = "cordndev1" ]; then ok "code minted"; else bad "no handoff code"; fi
[ "$(echo "$EXPORT" | field "['groups']")" = "1" ] && ok "one group in the snapshot" || bad "wrong group count"
[ "$(echo "$EXPORT" | field "['uploaded']")" = "true" ] && ok "documents stored" || bad "nothing stored"

step "blobs really landed on the server"
COUNT=$(ls "$BLOB_DIR" 2>/dev/null | wc -l | tr -d ' ')
# One group document plus the meta document.
[ "$COUNT" -ge 2 ] && ok "$COUNT blobs" || bad "expected >=2 blobs, found $COUNT"

step "the new phone has nothing yet"
BEFORE=$(new cordn group list --json 2>/dev/null | field "['groups']" 2>/dev/null || echo "[]")
[ "$BEFORE" = "[]" ] && ok "empty" || echo "   (starting from: $BEFORE)"

step "the new phone imports, from the code alone"
IMPORT=$(new cordn migrate import --code "$CODE" --json)
echo "   $IMPORT"
[ "$(echo "$IMPORT" | field "['groups']")" = "1" ] && ok "one group adopted" || bad "group did not arrive"
[ "$(echo "$IMPORT" | field "['replaced_local_state']")" = "true" ] && ok "replaced, not merged" || bad "did not replace"

step "the group is really there, with the same gid"
AFTER=$(new cordn group list --json 2>/dev/null)
echo "   $AFTER"
echo "$AFTER" | grep -q "$GID" && ok "gid $GID on the new phone" || bad "gid missing after import"

step "a group ref is not a handoff code"
# Called directly rather than through new(): the --json error contract writes
# to stderr, which the helper sends to /dev/null. The first version of this
# check compared an empty string and passed for the wrong reason.
STRANGER=$(HOME="$WORK/new" "$AMY" --account me --secret-backend ncryptsec \
  cordn migrate import --code "cordn1qqqqqq" --json 2>&1 || true)
echo "   $STRANGER"
echo "$STRANGER" | grep -q "bad_args" && ok "refused" || bad "accepted a non-handoff code"

echo
if [ "$fail" -eq 0 ]; then
  echo "── migrate: PASS"
else
  echo "── migrate: FAIL"
fi
exit "$fail"
