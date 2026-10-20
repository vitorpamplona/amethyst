#!/usr/bin/env bash
# jsonBench JavaScript harness launcher.
#   jsonBench/node/run.sh [--runtime node|bun|deno|all] [HARNESS.md flags...]
# Installs the pinned packages (package-lock.json) on first use. Bun and Deno come from
# npm (devDependencies), so their versions are pinned with everything else.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

runtime=node
args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --runtime) runtime="$2"; shift 2 ;;
    --runtime=*) runtime="${1#--runtime=}"; shift ;;
    *) args+=("$1"); shift ;;
  esac
done

if [[ ! -d "$HERE/node_modules" ]]; then
  (cd "$HERE" && npm ci --no-audit --no-fund)
fi

run_one() {
  case "$1" in
    node) node "$HERE/bench.mjs" "${args[@]+"${args[@]}"}" ;;
    bun) "$HERE/node_modules/.bin/bun" "$HERE/bench.mjs" "${args[@]+"${args[@]}"}" ;;
    deno) "$HERE/node_modules/.bin/deno" run -A "$HERE/bench.mjs" "${args[@]+"${args[@]}"}" ;;
    *) echo "unknown runtime: $1 (node|bun|deno|all)" >&2; exit 2 ;;
  esac
}

if [[ "$runtime" == all ]]; then
  for a in "${args[@]+"${args[@]}"}"; do
    if [[ "$a" == --out || "$a" == --out=* ]]; then
      echo "--out names one file; with --runtime all each runtime writes results/<runtime>.json" >&2
      exit 2
    fi
  done
  for rt in node bun deno; do run_one "$rt"; done
else
  run_one "$runtime"
fi
