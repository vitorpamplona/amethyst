#!/usr/bin/env bash
# jsonBench Python harness launcher: jsonBench/python/run.sh [HARNESS.md flags...]
# Creates .venv from the pinned requirements.txt on first use (or when it changes).
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PY="${PYTHON:-python3.13}"
VENV="$HERE/.venv"

if [[ ! -x "$VENV/bin/python" ]]; then
  "$PY" -m venv "$VENV"
fi
if ! cmp -s "$HERE/requirements.txt" "$VENV/.requirements.installed"; then
  "$VENV/bin/pip" install -q --disable-pip-version-check --only-binary=:all: -r "$HERE/requirements.txt"
  cp "$HERE/requirements.txt" "$VENV/.requirements.installed"
fi
exec "$VENV/bin/python" "$HERE/bench.py" "$@"
