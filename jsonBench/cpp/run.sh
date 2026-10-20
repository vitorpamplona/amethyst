#!/usr/bin/env bash
# jsonBench C/C++ harness launcher: fetch pinned deps, configure + build (Release), run.
# All HARNESS.md flags pass through, e.g. ./jsonBench/cpp/run.sh --quick --workloads all
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
repo="$(cd "$here/../.." && pwd)"
build="$here/build"

"$here/fetch.sh"

if [[ ! -f "$build/CMakeCache.txt" ]]; then
  gen=()
  command -v ninja >/dev/null && gen=(-G Ninja)
  cmake -S "$here" -B "$build" "${gen[@]}" -DCMAKE_BUILD_TYPE=Release \
    ${CXX:+-DCMAKE_CXX_COMPILER="$CXX"} ${CC:+-DCMAKE_C_COMPILER="$CC"} >&2
fi
cmake --build "$build" --parallel "${JB_JOBS:-$(nproc)}" >&2

cd "$repo"  # the default --corpus/--out paths are relative to the repo root
exec "$build/jsonbench" "$@"
