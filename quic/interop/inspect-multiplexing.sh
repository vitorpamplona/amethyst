#!/usr/bin/env bash
# Pull the post-mortem diagnostics for the most recent multiplexing run.
# Runs from anywhere; resolves logs relative to the runner clone path
# you've been using (../quic-interop-runner from this repo).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUNNER_LOGS="${REPO_ROOT}/../quic-interop-runner/logs"

if [[ ! -d "$RUNNER_LOGS" ]]; then
    echo "no runner logs at $RUNNER_LOGS" >&2
    exit 1
fi

# Most recent run dir.
RUN_DIR="$(ls -1dt "$RUNNER_LOGS"/run-* 2>/dev/null | head -n 1 || true)"
if [[ -z "$RUN_DIR" ]]; then
    echo "no run-* dirs under $RUNNER_LOGS" >&2
    exit 1
fi
echo "==> run dir: $RUN_DIR"

# Layout: <run>/<server>_<client>/<testcase>/{client,server,sim}/
CASE_DIR="$(ls -1d "$RUN_DIR"/*amethyst*/multiplexing 2>/dev/null | head -n 1 || true)"
if [[ -z "$CASE_DIR" ]]; then
    echo "no <pair>/multiplexing dir; tree under run:" >&2
    find "$RUN_DIR" -maxdepth 3 -type d >&2
    exit 1
fi
echo "==> case dir: $CASE_DIR"

echo
echo "=============== client/log.txt (tail) ==============="
tail -n 200 "$CASE_DIR"/client/log.txt 2>/dev/null \
    || echo "(no client/log.txt)"

echo
echo "=============== server/log.txt (tail) ==============="
tail -n 200 "$CASE_DIR"/server/log.txt 2>/dev/null \
    || echo "(no server/log.txt)"

echo
echo "=============== client qlog (tail of last 100 events) ==============="
QLOG="$(ls -1 "$CASE_DIR"/client/*.sqlog "$CASE_DIR"/client/*.qlog 2>/dev/null | head -n 1 || true)"
if [[ -n "$QLOG" ]]; then
    echo "(file: $QLOG)"
    tail -n 100 "$QLOG"
else
    echo "(no qlog under $CASE_DIR/client)"
fi

echo
echo "=============== /downloads contents ==============="
DL_DIR="$CASE_DIR/download"
[[ -d "$DL_DIR" ]] || DL_DIR="$CASE_DIR/client/downloads"
[[ -d "$DL_DIR" ]] || DL_DIR="$CASE_DIR/downloads"
if [[ -d "$DL_DIR" ]]; then
    DOWNLOADED="$(find "$DL_DIR" -type f | wc -l | tr -d ' ')"
    echo "files downloaded: $DOWNLOADED"
    echo "first 5:"
    find "$DL_DIR" -type f | head -n 5
    echo "last 5:"
    find "$DL_DIR" -type f | tail -n 5
else
    echo "(no downloads dir found; checked $CASE_DIR/download and $CASE_DIR/client/downloads)"
fi

echo
echo "=============== qlog event-type histogram ==============="
if [[ -n "${QLOG:-}" ]]; then
    # Each NDJSON line carries `"name":"transport:packet_sent"` etc.
    # Histogram the names so you see "many packet_dropped" or "lots of
    # ack_received but stream_state_updated tapered off".
    grep -oE '"name":"[^"]+"' "$QLOG" | sort | uniq -c | sort -rn | head -n 20
fi
