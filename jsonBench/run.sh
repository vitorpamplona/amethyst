#!/usr/bin/env bash
#
# jsonBench — which JSON parser, in any language, decodes Nostr events fastest
# and with the least garbage?
#
#   ./jsonBench/run.sh                         # every harness whose toolchain is installed
#   ./jsonBench/run.sh --quick                 # smoke run (seconds per parser, numbers are noisy)
#   ./jsonBench/run.sh --only jvm,rust         # a subset of harnesses
#   ./jsonBench/run.sh --workloads all,notes   # any HARNESS.md flag is passed to every harness
#   CORPUS_SOURCE=dump.ndjson.gz ./jsonBench/run.sh --rebuild-corpus
#
# Harnesses run one after another (never in parallel: they would steal cores
# from each other). Results land in jsonBench/results/<harness>.json and the
# merged ranking in jsonBench/results/report.md.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ALL_HARNESSES="jvm rust cpp go node bun deno python"

ONLY=""
REBUILD=0
PASS=()
while [[ $# -gt 0 ]]; do
    case "$1" in
        --only) ONLY="${2//,/ }"; shift 2 ;;
        --rebuild-corpus) REBUILD=1; shift ;;
        *) PASS+=("$1"); shift ;;
    esac
done
HARNESSES="${ONLY:-$ALL_HARNESSES}"

if [[ $REBUILD == 1 || ! -f "$HERE/.corpus/manifest.json" ]]; then
    if [[ -n "${CORPUS_SOURCE:-}" ]]; then
        python3 "$HERE/corpus/prepare.py" --source "$CORPUS_SOURCE" ${CORPUS_CANONICAL:+--canonical}
    else
        python3 "$HERE/corpus/prepare.py" ${CORPUS_CANONICAL:+--canonical}
    fi
fi

have() { command -v "$1" >/dev/null 2>&1; }

FAILED=()
for h in $HARNESSES; do
    case "$h" in
        jvm) need=java; launcher=("$HERE/jvm/run.sh") ;;
        rust) need=cargo; launcher=("$HERE/rust/run.sh") ;;
        cpp) need=cmake; launcher=("$HERE/cpp/run.sh") ;;
        go) need=go; launcher=("$HERE/go/run.sh") ;;
        node | bun | deno) need=node; launcher=("$HERE/node/run.sh" --runtime "$h") ;;
        python) need=python3; launcher=("$HERE/python/run.sh") ;;
        *) echo "unknown harness: $h (known: $ALL_HARNESSES)" >&2; exit 2 ;;
    esac
    if ! have "$need"; then
        echo "== $h: skipped, '$need' not installed" >&2
        continue
    fi
    echo "== $h"
    if ! "${launcher[@]}" "${PASS[@]}"; then
        echo "== $h: FAILED" >&2
        FAILED+=("$h")
    fi
done

python3 "$HERE/report/report.py"
if [[ ${#FAILED[@]} -gt 0 ]]; then
    echo "harnesses that failed: ${FAILED[*]}" >&2
    exit 1
fi
