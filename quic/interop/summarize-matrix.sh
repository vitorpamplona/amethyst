#!/usr/bin/env bash
# Summarize per-testcase results for the most recent matrix run.
# Useful when run-matrix.sh terminated early — the per-testcase
# output.txt files have status lines we can extract.
set -uo pipefail

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
echo

# Layout: <run>/<pair>/<testcase>/output.txt
# The runner writes a "Test: <name> took X, status: TestResult.{SUCCEEDED|FAILED|UNSUPPORTED}"
# line at the end of each test.
PAIR_DIR="$(ls -1d "$RUN_DIR"/*amethyst* 2>/dev/null | head -n 1 || true)"
if [[ -z "$PAIR_DIR" ]]; then
    echo "no <pair> dir under $RUN_DIR" >&2
    exit 1
fi

printf "%-22s %-15s %-10s\n" "TESTCASE" "RESULT" "TIME"
printf "%-22s %-15s %-10s\n" "----------------------" "---------------" "----------"
for tc_dir in "$PAIR_DIR"/*/; do
    tc=$(basename "$tc_dir")
    out="$tc_dir/output.txt"
    [[ -f "$out" ]] || continue
    # Last "Test: ... status: TestResult.X" line for this testcase.
    line=$(grep -E "^Test: $tc took" "$out" | tail -n 1)
    if [[ -n "$line" ]]; then
        status=$(echo "$line" | sed -nE 's/.*TestResult\.([A-Z_]+).*/\1/p')
        time=$(echo "$line" | sed -nE 's/.*took ([0-9.]+s).*/\1/p')
        # Color: green=succeeded, yellow=unsupported, red=failed.
        case "$status" in
            SUCCEEDED) marker="✓" ;;
            UNSUPPORTED) marker="?" ;;
            FAILED) marker="✕" ;;
            *) marker="·" ;;
        esac
        printf "%-22s %s %-13s %-10s\n" "$tc" "$marker" "$status" "$time"
    else
        printf "%-22s %s %-13s\n" "$tc" "·" "(no status — terminated mid-test)"
    fi
done
