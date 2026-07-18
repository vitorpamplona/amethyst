#!/usr/bin/env bash
#
# blossom-live.sh — end-to-end interop smoke test for `amy blossom`
# against a LIVE public Blossom server (BUD-01/02/04/09).
#
# Exercises the full blob lifecycle with a throwaway identity:
#
#   1. upload   — PUT /upload returns a Blob Descriptor; sha256 matches
#                 the bytes we sent (content addressing).
#   2. check    — HEAD /<sha256> reports the blob present (BUD-01).
#   3. download — GET /<sha256> returns bytes whose sha256 matches.
#   4. list     — GET /list/<pubkey> includes the uploaded hash (BUD-02).
#   5. mirror   — PUT /mirror on a 2nd server pulls the blob by URL and
#                 reports it present (BUD-04). [needs --mirror-server]
#   6. delete   — DELETE /<sha256> then HEAD shows it gone (BUD-02).
#
# Third-party servers gate writes in wildly different ways (whitelists,
# rate limits, payment, content sniffing). So a server-side *rejection*
# of a write is recorded as SKIP, not FAIL — only a broken client
# contract (bad descriptor, hash mismatch, unparuseable list) FAILS.
#
# Usage:
#   ./blossom-live.sh [--server URL] [--mirror-server URL] [--no-build]
#
# Defaults: --server https://files.sovbit.host (accepts anonymous
# uploads + clean deletes at time of writing). Override freely.
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-blossom-live"
LOG_DIR="$STATE_DIR/logs"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"

SERVER="${BLOSSOM_SERVER:-https://files.sovbit.host}"
MIRROR_SERVER="${BLOSSOM_MIRROR_SERVER:-}"
NO_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --server)        SERVER="$2"; shift ;;
    --mirror-server) MIRROR_SERVER="$2"; shift ;;
    --no-build)      NO_BUILD=1 ;;
    -h|--help)
      sed -n '3,27p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
      exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

mkdir -p "$STATE_DIR" "$LOG_DIR"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"

# Throwaway identity under a private fake $HOME with the plaintext secret
# backend (no passphrase prompt). Never publishes to relays.
export HOME="$STATE_DIR/home"
mkdir -p "$HOME"
amy() { "$AMY_BIN" --secret-backend plaintext --account blossomtest --json "$@" 2>>"$LOG_FILE"; }

cleanup() {
  local rc=$?
  trap - EXIT INT TERM HUP
  print_summary
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

banner "amy blossom live interop ($RUN_TS) — server=$SERVER"

if [[ ! -x "$AMY_BIN" ]]; then
  if [[ "$NO_BUILD" -eq 1 ]]; then
    fail_msg "amy binary missing at $AMY_BIN and --no-build set"
    record_result preflight.amy fail "no amy binary"
    exit 1
  fi
  step "building amy (./gradlew :cli:installDist)"
  (cd "$REPO_ROOT" && ./gradlew :cli:installDist -q) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "amy build failed"; record_result preflight.build fail ""; exit 1; }
fi

# A tiny 1x1 PNG — Blossom servers are media-oriented and often reject
# arbitrary octet-streams, so use a real recognizable image.
BLOB="$STATE_DIR/px.png"
printf '\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde\x00\x00\x00\x0cIDATx\x9cc\xf8\xcf\xc0\x00\x00\x00\x03\x00\x01\xf5\xd7\xd4\xc2\x00\x00\x00\x00IEND\xaeB\x60\x82' >"$BLOB"

step "creating throwaway identity"
amy create --name "Blossom Live Test" >>"$LOG_FILE" 2>&1 || { skip_msg "could not create identity"; record_result preflight.identity skip ""; exit 0; }
PUBKEY=$(amy whoami | jq -r '.hex // empty')

# ---------------------------------------------------------------------------
# 1. upload
# ---------------------------------------------------------------------------
banner "T1 — upload"
UP=$(amy blossom upload --server "$SERVER" --mime-type image/png "$BLOB")
UP_RC=$?
HASH=$(printf '%s' "$UP" | jq -r '.sha256 // empty')
URL=$(printf '%s' "$UP" | jq -r '.url // empty')
if [[ "$UP_RC" -ne 0 || -z "$HASH" ]]; then
  skip_msg "server rejected the upload (policy/whitelist/rate limit) — $(printf '%s' "$UP" | jq -r '.detail // .' 2>/dev/null)"
  record_result T1.upload skip "server-side write rejection"
  exit 0
fi
if [[ ${#HASH} -eq 64 && -n "$URL" ]]; then
  record_result T1.upload pass "sha256=$HASH"
else
  record_result T1.upload fail "descriptor missing sha256/url: $UP"
fi

# ---------------------------------------------------------------------------
# 2. check (HEAD)
# ---------------------------------------------------------------------------
banner "T2 — HEAD check reports present"
CK=$(amy blossom check --server "$SERVER" "$HASH")
if [[ "$(printf '%s' "$CK" | jq -r '.all_found')" == "true" ]]; then
  record_result T2.check pass "HEAD found the blob"
else
  record_result T2.check fail "HEAD did not find the just-uploaded blob: $CK"
fi

# ---------------------------------------------------------------------------
# 3. download — bytes hash back to the same sha256
# ---------------------------------------------------------------------------
banner "T3 — download round-trips the content hash"
DL=$(amy blossom download "$HASH" --server "$SERVER")
DL_HASH=$(printf '%s' "$DL" | jq -r '.sha256 // empty')
if [[ "$DL_HASH" == "$HASH" ]]; then
  record_result T3.download pass "downloaded bytes hash to $DL_HASH"
else
  record_result T3.download fail "download hash $DL_HASH != upload hash $HASH"
fi

# ---------------------------------------------------------------------------
# 4. list — our hash appears in the pubkey's blob list
# ---------------------------------------------------------------------------
banner "T4 — list includes the uploaded blob"
LS=$(amy blossom list --server "$SERVER")
if printf '%s' "$LS" | jq -e --arg h "$HASH" '.blobs[]? | select(.sha256 == $h)' >/dev/null 2>&1; then
  record_result T4.list pass "blob present in /list/$PUBKEY"
elif [[ "$(printf '%s' "$LS" | jq -r '.error // empty')" != "" ]]; then
  skip_msg "server does not implement /list"
  record_result T4.list skip "no /list endpoint"
else
  record_result T4.list fail "uploaded hash not in list: $(printf '%s' "$LS" | head -c 200)"
fi

# ---------------------------------------------------------------------------
# 5. mirror (optional 2nd server)
# ---------------------------------------------------------------------------
if [[ -n "$MIRROR_SERVER" ]]; then
  banner "T5 — mirror $SERVER -> $MIRROR_SERVER"
  MR=$(amy blossom mirror --server "$MIRROR_SERVER" "$URL")
  if [[ $? -eq 0 && "$(printf '%s' "$MR" | jq -r '.sha256 // empty')" == "$HASH" ]]; then
    CK2=$(amy blossom check --server "$MIRROR_SERVER" "$HASH")
    if [[ "$(printf '%s' "$CK2" | jq -r '.all_found')" == "true" ]]; then
      record_result T5.mirror pass "mirrored blob present on $MIRROR_SERVER"
    else
      record_result T5.mirror fail "mirror reported ok but HEAD misses it"
    fi
  else
    skip_msg "mirror target rejected the write (policy) — $(printf '%s' "$MR" | jq -r '.detail // .' 2>/dev/null | head -c 160)"
    record_result T5.mirror skip "server-side mirror rejection"
  fi
else
  skip_msg "no --mirror-server given; skipping BUD-04 mirror"
  record_result T5.mirror skip "not configured"
fi

# ---------------------------------------------------------------------------
# 6. delete then confirm gone
# ---------------------------------------------------------------------------
banner "T6 — delete removes the blob"
DEL=$(amy blossom delete --server "$SERVER" "$HASH")
DEL_OK=$(printf '%s' "$DEL" | jq -r '.deleted // false')
CK3=$(amy blossom check --server "$SERVER" "$HASH")
GONE=$([[ "$(printf '%s' "$CK3" | jq -r '.all_found')" == "false" ]] && echo true || echo false)
if [[ "$GONE" == "true" ]]; then
  record_result T6.delete pass "blob no longer found after delete (deleted flag=$DEL_OK)"
else
  skip_msg "server did not honor delete (may forbid deletes) — deleted=$DEL_OK"
  record_result T6.delete skip "delete not honored"
fi
