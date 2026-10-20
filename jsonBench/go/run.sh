#!/usr/bin/env bash
# jsonBench Go harness launcher: build (pinned go.mod/go.sum) and run.
# All HARNESS.md flags pass through, e.g. ./jsonBench/go/run.sh --quick --workloads all
# The binary re-executes itself once per parser (fresh heap/GC state per parser).
# GOGC/GOMEMLIMIT/GOMAXPROCS are inherited from the environment and recorded in `runtime`.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"

(cd "$here" && go build -o jsonbench .) >&2

cd "$repo"  # the default --corpus/--out paths are relative to the repo root
exec "$here/jsonbench" "$@"
