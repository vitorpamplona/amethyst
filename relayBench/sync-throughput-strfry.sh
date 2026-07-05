#!/usr/bin/env bash
# Measures strfry's native sync throughput: import N events into a source
# strfry, boot it as a relay, then `strfry sync --dir down` an empty sink from
# it and report events/second. Usage: sync-throughput-strfry.sh <N>
set -euo pipefail
N="${1:-1000000}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
STRFRY="${STRFRY_BIN:-$HERE/.cache/strfry/strfry}"
CORPUS="${CORPUS:-$HERE/.corpus-cache/corpus-download-relay.damus.io-n1000000.ndjson}"
WORK="${WORK:-/tmp/strfry-sync-$N}"
PORT="${PORT:-18821}"

rm -rf "$WORK"; mkdir -p "$WORK/src-db" "$WORK/dst-db"
mkconf() { cat > "$1" <<EOF
db = "$2"
events { maxEventSize = 1048576 rejectEventsOlderThanSeconds = 3155760000 maxNumTags = 10000 }
relay { bind = "127.0.0.1" port = $3 nofiles = 0 maxWebsocketPayloadSize = 1114112 }
EOF
}
mkconf "$WORK/src.conf" "$WORK/src-db" "$PORT"
mkconf "$WORK/dst.conf" "$WORK/dst-db" "$((PORT+1))"

echo "importing $N events into strfry source…"
head -n "$N" "$CORPUS" | "$STRFRY" --config="$WORK/src.conf" import > "$WORK/import.log" 2>&1
srcN=$("$STRFRY" --config="$WORK/src.conf" export 2>/dev/null | wc -l)
echo "source has $srcN events"

"$STRFRY" --config="$WORK/src.conf" relay > "$WORK/src.log" 2>&1 &
SRCPID=$!
trap 'kill $SRCPID 2>/dev/null || true' EXIT
sleep 3

echo "syncing sink from source (strfry sync --dir down)…"
t0=$(date +%s.%N)
"$STRFRY" --config="$WORK/dst.conf" sync "ws://127.0.0.1:$PORT" --dir down --timeout 120 > "$WORK/sync.log" 2>&1
t1=$(date +%s.%N)
dstN=$("$STRFRY" --config="$WORK/dst.conf" export 2>/dev/null | wc -l)
secs=$(echo "$t1 - $t0" | bc)
eps=$(echo "scale=0; $dstN / $secs" | bc)
echo "════════════════════════════════════════════"
echo "strfry→strfry: synced $dstN/$srcN in ${secs}s  =>  $eps events/s"
echo "════════════════════════════════════════════"
grep -iE "reconcile complete|Have [0-9]" "$WORK/sync.log" | grep -viE "arguments|Current dir" | tail -3
