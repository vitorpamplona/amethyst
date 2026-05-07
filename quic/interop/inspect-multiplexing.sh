#!/usr/bin/env bash
# Pull the post-mortem diagnostics for the most recent multiplexing run.
# Runs from anywhere; resolves logs relative to the runner clone path
# (../quic-interop-runner from this repo).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUNNER_LOGS="${REPO_ROOT}/../quic-interop-runner/logs"

if [[ ! -d "$RUNNER_LOGS" ]]; then
    echo "no runner logs at $RUNNER_LOGS" >&2
    exit 1
fi

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
echo "=============== writer-side debug traces (DEBUG=1 build only) ==============="
# Per-drain frame/budget stats from buildApplicationPacket. The
# smoking-gun section for "writer is iterating N active streams but
# emits only 1 STREAM frame per packet". Empty unless the image was
# built with `make build DEBUG=1`.
#
# `grep -c` exits 1 on no matches AND prints "0", so a naive
# `grep -c ... || echo 0` doubles up. Suppress the exit code
# instead.
WRITER_LINES=$(grep -cE '\[(writer|batch|interop)' "$CASE_DIR/output.txt" 2>/dev/null) || WRITER_LINES=0
if [[ "$WRITER_LINES" -gt 0 ]]; then
    echo "($WRITER_LINES diagnostic lines; [batch] / [interop] entries first then first 30 [writer.app]:)"
    grep -E '\[(batch|interop)' "$CASE_DIR/output.txt" | head -n 20
    echo "..."
    grep '\[writer' "$CASE_DIR/output.txt" | head -n 30
    echo
    echo "stream_frames histogram (writer-reported):"
    grep -oE 'stream_frames=[0-9]+' "$CASE_DIR/output.txt" | sort | uniq -c | sort -rn
    echo
    echo "active histogram (active stream count at drain time):"
    grep -oE 'active=[0-9]+' "$CASE_DIR/output.txt" | sort | uniq -c | sort -rn
else
    echo "(no diagnostic lines yet — to enable, REBUILD the image with DEBUG=1:"
    echo "   DEBUG=1 ./quic/interop/run-matrix.sh -s aioquic -t multiplexing"
    echo " then re-run this script)"
fi

echo
echo "=============== server stderr (last 50 lines) ==============="
# Peer's CONNECTION_CLOSE reason if any; processing pace via stream
# create/discard cadence.
tail -n 50 "$CASE_DIR/server/stderr.log" 2>/dev/null \
    || echo "(no server/stderr.log)"

QLOG="$(ls -1 "$CASE_DIR"/client/qlog/*.sqlog \
        "$CASE_DIR"/client/qlog/*.qlog \
        2>/dev/null | head -n 1 || true)"
if [[ -z "$QLOG" ]]; then
    echo
    echo "(no qlog under $CASE_DIR/client/qlog — skipping qlog sections)"
    exit 0
fi

echo
echo "=============== qlog ($QLOG, $(wc -l <"$QLOG" | tr -d ' ') lines) ==============="

echo
echo "stream-frames-per-sent-packet histogram:"
# Many `0` = ack-only; many `1` = the bug; many `>=4` = writer
# coalescing as intended.
grep '"name":"transport:packet_sent"' "$QLOG" \
  | awk -F'"frame_type":"stream"' '{print NF - 1}' \
  | sort -n | uniq -c

echo
echo "transport_parameters (local + remote):"
grep '"name":"transport:parameters_set"' "$QLOG"

echo
echo "first 10 packet_sent (full) — burst shape after handshake:"
grep '"name":"transport:packet_sent"' "$QLOG" | head -n 10

echo
echo "connection_closed events (peer CCs are the smoking gun for spec violations):"
grep '"name":"transport:connection_closed"' "$QLOG" || echo "(none)"

echo
echo "packet_dropped events (last 10):"
grep '"name":"transport:packet_dropped"' "$QLOG" | tail -n 10 \
    || echo "(none)"
