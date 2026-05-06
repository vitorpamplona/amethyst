#!/usr/bin/env bash
# Drive the quic-interop-runner against the :quic-interop endpoint image.
#
# Idempotent: clones the runner alongside this repo if missing, sets up its
# venv, registers `amethyst` in implementations.json, builds our endpoint
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

# 2. venv + Python deps.
if [ ! -d "$RUNNER_DIR/.venv" ]; then
    echo "==> creating venv at $RUNNER_DIR/.venv"
    python3 -m venv "$RUNNER_DIR/.venv"
fi
"$RUNNER_DIR/.venv/bin/pip" install -q -r "$RUNNER_DIR/requirements.txt"

# 3. Register our endpoint in implementations.json (idempotent).
if ! jq -e '.amethyst' "$RUNNER_DIR/implementations.json" >/dev/null 2>&1; then
    echo "==> registering 'amethyst' in implementations.json"
    tmp=$(mktemp)
    jq -s '.[0] * .[1]' \
        "$RUNNER_DIR/implementations.json" \
        "$SCRIPT_DIR/quic-interop-runner-snippet.json" \
        > "$tmp"
    mv "$tmp" "$RUNNER_DIR/implementations.json"
fi

# 4. Build the endpoint image (skippable for tight loops).
if [ "${SKIP_BUILD:-0}" != "1" ]; then
    echo "==> building amethyst-quic-interop image"
    make -C "$SCRIPT_DIR" build
fi

# 5. Drive the runner.
mkdir -p "$LOG_DIR"
echo "==> running matrix (args: $* | logs: $LOG_DIR)"
cd "$RUNNER_DIR"
exec "$RUNNER_DIR/.venv/bin/python" run.py \
    -d -i amethyst --log-dir "$LOG_DIR" "$@"
