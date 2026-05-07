#!/usr/bin/env bash
# Drive the quic-interop-runner against the :quic-interop endpoint image.
#
# Idempotent: clones the runner alongside this repo if missing, sets up its
# venv, registers `amethyst` in implementations_quic.json, builds our endpoint
# image, then invokes run.py with the user's args.
#
# Usage:
#   quic/interop/run-matrix.sh                                      # full matrix vs aioquic
#   quic/interop/run-matrix.sh -s aioquic -t handshake              # one test
#   quic/interop/run-matrix.sh -s quic-go -t handshake,chacha20     # different peer
#
# Env overrides:
#   RUNNER_DIR   — where to clone / find the runner (default: ../quic-interop-runner)
#   LOG_DIR      — qlog / pcap output (default: $RUNNER_DIR/logs)
#   SKIP_BUILD=1 — skip `make build` (image already current)
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/../.." && pwd)
RUNNER_DIR="${RUNNER_DIR:-$REPO_ROOT/../quic-interop-runner}"
LOG_DIR="${LOG_DIR:-$RUNNER_DIR/logs}"

is_macos=false
case "${OSTYPE:-}" in
    darwin*) is_macos=true ;;
esac

install_hint() {
    if $is_macos; then
        echo "    brew install $1"
    else
        echo "    sudo apt-get install -y $1"
    fi
}

need() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "error: '$1' not found in PATH. Install with:" >&2
        install_hint "$2" >&2
        exit 1
    fi
}

need docker docker.io
need python3 python3
need jq jq
need git git
need make make

# Docker daemon must be reachable, not just installed. On macOS this is the
# usual gotcha — Docker Desktop installed but not running.
if ! docker info >/dev/null 2>&1; then
    echo "error: docker daemon not reachable." >&2
    if $is_macos; then
        echo "  → launch Docker Desktop and retry." >&2
    else
        echo "  → 'sudo systemctl start docker' (and add yourself to the docker group)." >&2
    fi
    exit 1
fi

# 1. Clone the runner if missing.
if [ ! -d "$RUNNER_DIR" ]; then
    echo "==> cloning quic-interop-runner into $RUNNER_DIR"
    git clone --depth 1 https://github.com/quic-interop/quic-interop-runner.git "$RUNNER_DIR"
fi

# 2. venv + Python deps. Prefer 3.13 — pyshark (the runner's pcap parser)
# trips on 3.14's asyncio.get_event_loop() removal. Fall back to whatever
# `python3` resolves to if 3.13 isn't installed.
PYTHON_BIN=""
for candidate in python3.13 python3.12 python3.11 python3; do
    if command -v "$candidate" >/dev/null 2>&1; then
        if "$candidate" -c 'import sys; sys.exit(0 if sys.version_info < (3,14) else 1)' 2>/dev/null; then
            PYTHON_BIN="$candidate"
            break
        fi
    fi
done
if [ -z "$PYTHON_BIN" ]; then
    echo "warning: no Python < 3.14 found; pyshark will likely crash on pcap validation." >&2
    install_hint python3.13
    PYTHON_BIN="python3"
fi
if [ ! -d "$RUNNER_DIR/.venv" ]; then
    echo "==> creating venv at $RUNNER_DIR/.venv (using $PYTHON_BIN)"
    "$PYTHON_BIN" -m venv "$RUNNER_DIR/.venv"
fi
"$RUNNER_DIR/.venv/bin/pip" install -q -r "$RUNNER_DIR/requirements.txt"

# 3. Register our endpoint in implementations_quic.json (idempotent).
if ! jq -e '.amethyst' "$RUNNER_DIR/implementations_quic.json" >/dev/null 2>&1; then
    echo "==> registering 'amethyst' in implementations_quic.json"
    tmp=$(mktemp)
    jq -s '.[0] * .[1]' \
        "$RUNNER_DIR/implementations_quic.json" \
        "$SCRIPT_DIR/quic-interop-runner-snippet.json" \
        > "$tmp"
    mv "$tmp" "$RUNNER_DIR/implementations_quic.json"
fi

# 4. Build the endpoint image (skippable for tight loops).
if [ "${SKIP_BUILD:-0}" != "1" ]; then
    echo "==> building amethyst-quic-interop image"
    make -C "$SCRIPT_DIR" build
fi

# 5. Drive the runner.
#
# run.py hard-exits if --log-dir already exists, so we use a fresh
# per-invocation subdirectory under $LOG_DIR. The parent must exist; the
# child must not. Tail of `ls -t "$LOG_DIR" | head -1` finds the latest.
#
# NOTE: this script is NOT safe to run concurrently against itself.
# quic-interop-runner's docker-compose.yml hardcodes `container_name:
# sim/server/client`, which Docker enforces globally regardless of
# COMPOSE_PROJECT_NAME. Two simultaneous invocations collide on
# `docker create container "sim"`. Run sequentially:
#   for peer in aioquic picoquic quic-go; do
#       quic/interop/run-matrix.sh -s $peer -t handshake,chacha20,...
#   done
#
# Output filter: by default we drop the runner / container boilerplate
# (interface checksum offload toggles, route setup, container lifecycle,
# the long Command: WAITFORSERVER=... line, the platform-mismatch warning
# that fires once per test on Apple Silicon). Set VERBOSE=1 to bypass.
mkdir -p "$LOG_DIR"
RUN_LOG_DIR="$LOG_DIR/run-$(date +%Y%m%d-%H%M%S)"
echo "==> running matrix (args: $* | logs: $RUN_LOG_DIR)"
cd "$RUNNER_DIR"

if [ "${VERBOSE:-0}" = "1" ]; then
    exec "$RUNNER_DIR/.venv/bin/python" run.py \
        -d -i amethyst --log-dir "$RUN_LOG_DIR" "$@"
else
    "$RUNNER_DIR/.venv/bin/python" run.py \
        -d -i amethyst --log-dir "$RUN_LOG_DIR" "$@" 2>&1 \
        | grep -Ev \
            -e '^(client|server|sim) +\| +(Setting up routes|Actual changes:|tx-[a-z0-9-]+:|Endpoint'\''s IPv[46] address is)' \
            -e '^ Container [a-z]+  +(Recreate|Recreated|Stopping|Stopped|Starting|Started)( [0-9.]+s)?$' \
            -e '^ server The requested image'\''s platform' \
            -e '^Attaching to client, server, sim$' \
            -e '^Aborting on container exit\.\.\.$' \
            -e '^(client|server|sim) exited with code [0-9]+$' \
            -e '^Packets captured: [0-9]+$' \
            -e '^sim +\| +Packets received/dropped on interface ' \
            -e '^sim +\| +(Received signal:|msg=|NS_FATAL)' \
            -e '^Using the client'\''s key log file\.$' \
            -e '^Command: WAITFORSERVER=' \
            -e '^==> ' \
            -e '^$'
    exit "${PIPESTATUS[0]}"
fi
