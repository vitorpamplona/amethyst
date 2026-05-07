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
echo "=============== file tree under case dir ==============="
find "$CASE_DIR" -maxdepth 4 -type f -printf '%s\t%p\n' 2>/dev/null \
    || find "$CASE_DIR" -maxdepth 4 -type f -exec ls -l {} + 2>/dev/null

echo
echo "=============== output.txt (runner stdout — last 200 lines) ==============="
tail -n 200 "$CASE_DIR/output.txt" 2>/dev/null \
    || echo "(no output.txt)"

echo
echo "=============== server stderr (last 100 lines) ==============="
tail -n 100 "$CASE_DIR/server/stderr.log" 2>/dev/null \
    || echo "(no server/stderr.log)"

# Find the qlog. The runner mounts QLOGDIR=/logs/qlog so we land at
# client/qlog/<odcid>.sqlog inside the case dir.
QLOG="$(ls -1 "$CASE_DIR"/client/qlog/*.sqlog \
        "$CASE_DIR"/client/qlog/*.qlog \
        "$CASE_DIR"/client/*.sqlog \
        "$CASE_DIR"/client/*.qlog 2>/dev/null | head -n 1 || true)"

if [[ -n "$QLOG" ]]; then
    echo
    echo "=============== qlog event-type histogram ==============="
    echo "(file: $QLOG, $(wc -l <"$QLOG" | tr -d ' ') lines)"
    grep -oE '"name":"[^"]+"' "$QLOG" | sort | uniq -c | sort -rn | head -n 20

    echo
    echo "=============== frames-per-packet histogram (sent) ==============="
    # Smoking gun for "is the writer coalescing or sending one STREAM
    # per datagram":
    #   - Many `frames=1`  → regressed, one stream per packet on the wire
    #   - Most `frames>=4` → writer is bursting, server is the bottleneck
    grep '"name":"transport:packet_sent"' "$QLOG" \
      | awk -F'"frame_type":' '{print NF - 1}' \
      | sort -n | uniq -c

    echo
    echo "=============== stream-frames-per-sent-packet histogram ==============="
    # Same shape but counts STREAM frames specifically (vs ack/ping/etc).
    # A "stream" frame_type means request data going out.
    grep '"name":"transport:packet_sent"' "$QLOG" \
      | awk -F'"frame_type":"stream"' '{print NF - 1}' \
      | sort -n | uniq -c

    echo
    echo "=============== peer transport_parameters (initial_max_data is the key) ==============="
    # If initial_max_data is small (e.g. < 1000 bytes), our writer
    # gets throttled to one stream per packet because connBudget
    # exhausts after the first stream. Each subsequent stream has to
    # wait for a MAX_DATA frame from the peer (1 RTT each).
    grep '"name":"transport:parameters_set"' "$QLOG"

    echo
    echo "=============== peer MAX_DATA frame timestamps + values (received) ==============="
    # If MAX_DATA frames arrive with small bumps and at one-per-RTT
    # cadence, we're flow-control bottlenecked: peer extends credit
    # by ~50 bytes per RTT, we send one stream per credit bump.
    grep '"name":"transport:packet_received"' "$QLOG" \
      | grep -oE '"frame_type":"max_data"[^}]*' \
      | head -n 30 || echo "(no max_data frames in qlog — may need to upgrade qlog observer)"

    echo
    echo "=============== first 30 packet_sent stream offsets (per-stream byte count) ==============="
    # If every stream emits exactly one ~50-byte chunk before the next
    # stream starts, we're connection-flow-control bound.
    grep '"name":"transport:packet_sent"' "$QLOG" \
      | grep -oE '"stream_id":[0-9]+' \
      | head -n 30 || echo "(no stream_id field in qlog — may need to upgrade qlog observer)"

    echo
    echo "=============== last 5 packet_received events ==============="
    grep '"name":"transport:packet_received"' "$QLOG" | tail -n 5

    echo
    echo "=============== last 5 packet_sent events ==============="
    grep '"name":"transport:packet_sent"' "$QLOG" | tail -n 5

    echo
    echo "=============== connection_closed events ==============="
    grep '"name":"transport:connection_closed"' "$QLOG" || echo "(none — connection didn't formally close)"

    echo
    echo "=============== packet_dropped events (last 10) ==============="
    grep '"name":"transport:packet_dropped"' "$QLOG" | tail -n 10 \
        || echo "(none)"
fi
