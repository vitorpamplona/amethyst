#!/usr/bin/env bash
#
# Publish the test napplet (index.html) so it can be opened in Amethyst on a device:
#   1. uploads index.html to a Blossom server (BUD-02 signed upload),
#   2. publishes a NIP-5D named-napplet event (kind 35129) whose manifest pins
#      /index.html to the blob's sha256 and declares the capabilities it uses.
#
# Requirements: nak (https://github.com/fiatjaf/nak), curl, and sha256sum (or shasum/openssl).
#
# Usage:
#   ./publish.sh --sec nsec1... --server https://blossom.example [--relay wss://... ]... [--id napplet-test]
#
# The secret key can also come from $NOSTR_SECRET_KEY or $NSEC. Relays default to a couple of
# public ones if none are given. Use the SAME key you are logged in as in Amethyst, so the napplet
# shows up under your own account and the identity reads have data.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
HTML="$HERE/index.html"
ID="napplet-test"
SERVER=""
SEC="${NOSTR_SECRET_KEY:-${NSEC:-}}"
RELAYS=()

while [ $# -gt 0 ]; do
  case "$1" in
    --sec) SEC="$2"; shift 2 ;;
    --server) SERVER="${2%/}"; shift 2 ;;
    --relay) RELAYS+=("$2"); shift 2 ;;
    --id) ID="$2"; shift 2 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "Unknown arg: $1" >&2; exit 2 ;;
  esac
done

[ -n "$SEC" ]    || { echo "ERROR: no secret key (--sec / \$NOSTR_SECRET_KEY / \$NSEC)" >&2; exit 2; }
[ -n "$SERVER" ] || { echo "ERROR: no --server (a Blossom base URL, e.g. https://blossom.primal.net)" >&2; exit 2; }
command -v nak  >/dev/null || { echo "ERROR: nak not found (https://github.com/fiatjaf/nak)" >&2; exit 2; }
command -v curl >/dev/null || { echo "ERROR: curl not found" >&2; exit 2; }
[ ${#RELAYS[@]} -gt 0 ] || RELAYS=(wss://relay.damus.io wss://nos.lol)

sha256() {
  if command -v sha256sum >/dev/null; then sha256sum | cut -d' ' -f1
  elif command -v shasum >/dev/null; then shasum -a 256 | cut -d' ' -f1
  else openssl dgst -sha256 | sed 's/.* //'; fi
}
b64() { base64 | tr -d '\n'; }

HASH="$(sha256 < "$HTML")"
echo "index.html sha256 : $HASH"

# --- 1. Blossom upload (BUD-02: a kind-24242 'upload' auth event in the Authorization header) ---
EXP=$(( $(date +%s) + 3600 ))
AUTH_JSON="$(nak event -k 24242 --sec "$SEC" -t "t=upload" -t "x=$HASH" -t "expiration=$EXP" -c "Upload napplet test")"
AUTH_B64="$(printf '%s' "$AUTH_JSON" | b64)"

echo "Uploading to $SERVER/upload …"
UP="$(curl -sS -X PUT "$SERVER/upload" \
  -H "Authorization: Nostr $AUTH_B64" \
  -H "Content-Type: text/html" \
  --data-binary @"$HTML")"
echo "  server said: $UP"

echo "Verifying $SERVER/$HASH is retrievable …"
CODE="$(curl -sS -o /dev/null -w '%{http_code}' "$SERVER/$HASH")"
[ "$CODE" = "200" ] || { echo "ERROR: blob not retrievable (HTTP $CODE). Check the upload response above." >&2; exit 1; }
echo "  OK (HTTP 200)"

# --- 2. NIP-5A aggregate hash over the one path: sha256("<filehash> /index.html\n") ---
AGG="$(printf '%s /index.html\n' "$HASH" | sha256)"
echo "aggregate hash    : $AGG"

# --- 3. Publish the NIP-5D named napplet (kind 35129) ---
echo "Publishing napplet event (kind 35129, d=$ID) to: ${RELAYS[*]}"
nak event -k 35129 --sec "$SEC" \
  -d "$ID" \
  -t "path=/index.html;$HASH" \
  -t "x=$AGG;aggregate" \
  -t "server=$SERVER" \
  -t "requires=identity" \
  -t "requires=relay" \
  -t "requires=storage" \
  -t "requires=value" \
  -t "requires=resource" \
  -t "requires=upload" \
  -t "requires=keys" \
  -t "title=Napplet Test Harness" \
  -t "description=Exercises every napplet.* API for on-device verification." \
  "${RELAYS[@]}"

PUB="$(nak key public "$SEC")"
echo
echo "Done. Author pubkey: $PUB"
echo "Verify resolution with amy:"
echo "  amy napplet fetch $PUB --d $ID"
echo "Then in Amethyst (logged in as this key): open your Apps / Napplets list and tap \"Napplet Test Harness\"."
