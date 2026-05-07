#!/usr/bin/env bash
# Summarize per-testcase results for the most recent matrix run.
# Useful when run-matrix.sh terminated early — the per-testcase
# output.txt files have status lines we can extract.
set -o pipefail
# NOT set -u: bash 3.2 (macOS default) treats `${arr[@]}` on an
# empty array as an unbound-variable error, which we hit on runs
# that have no log files (the artifact-inspection path is still
# valid).

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

# Some runner versions don't write status to per-testcase output.txt.
# Fall back to inferring from artifacts (qlog connection_closed events,
# pcap presence, etc.). We tag these with [inf] in the result column.
infer_status_from_artifacts() {
    local tc_dir="$1"
    local tc="$2"
    # Did the connection formally close with an error?
    local qlog
    qlog=$(ls -1 "$tc_dir"/client/qlog/*.sqlog "$tc_dir"/client/qlog/*.qlog 2>/dev/null | head -n 1 || true)
    if [[ -n "$qlog" ]]; then
        # Did the server close us with an error code?
        local cc
        cc=$(grep '"name":"transport:connection_closed"' "$qlog" 2>/dev/null | tail -n 1)
        if [[ -n "$cc" ]]; then
            local reason
            reason=$(echo "$cc" | sed -nE 's/.*"reason":"([^"]+)".*/\1/p')
            echo "FAILED: $reason"
            return
        fi
        # No close → did we get >0 packets received? If so, infer
        # something happened. Status is genuinely uncertain.
        local rx
        rx=$(grep -c '"name":"transport:packet_received"' "$qlog" 2>/dev/null || echo 0)
        if [[ "$rx" -gt 0 ]]; then
            echo "RAN ($rx pkts rx; no formal close)"
            return
        fi
    fi
    echo "UNKNOWN (no qlog)"
}

# The "Test: X took Y, status: TestResult.Z" line is written by run.py
# to its own stdout, not into the per-testcase output.txt. Search a few
# likely locations: the per-testcase output.txt (in case the runner
# version we're using writes there), the run dir, the runner-logs root,
# and the working dir we were invoked from.
# Build list of files to grep, in priority order. Bash 3.2 (macOS
# default) chokes on multiline process-substitution with comments,
# so we just push to an array imperatively.
SEARCH_FILES=()
# 1. run-matrix.sh tees the runner stdout to a sibling .stdout.log
#    file — that has the authoritative "Test: X took Y, status:" lines.
if [[ -f "${RUN_DIR}.stdout.log" ]]; then
    SEARCH_FILES+=("${RUN_DIR}.stdout.log")
fi
# 2. Per-testcase output.txt — older runner versions wrote status here.
while IFS= read -r f; do
    SEARCH_FILES+=("$f")
done < <(find "$PAIR_DIR" -maxdepth 3 -name 'output.txt' -type f 2>/dev/null)
# 3. Run-dir / runner-logs *.log — catch-all.
while IFS= read -r f; do
    SEARCH_FILES+=("$f")
done < <(find "$RUN_DIR" -maxdepth 1 -name '*.log' -type f 2>/dev/null)
while IFS= read -r f; do
    SEARCH_FILES+=("$f")
done < <(find "$RUNNER_LOGS" -maxdepth 1 -name 'run-*.log' -type f 2>/dev/null)

printf "%-22s %-15s %-10s\n" "TESTCASE" "RESULT" "TIME"
printf "%-22s %-15s %-10s\n" "----------------------" "---------------" "----------"
for tc_dir in "$PAIR_DIR"/*/; do
    tc=$(basename "$tc_dir")
    # Search every candidate file for a status line matching this
    # testcase. Allow optional leading timestamp from the runner's
    # logging format (`2026-05-07 12:34:56,789 Test: ...`).
    line=""
    for f in "${SEARCH_FILES[@]}"; do
        [[ -f "$f" ]] || continue
        match=$(grep -E "Test: $tc took [0-9.]+s, status: TestResult\." "$f" 2>/dev/null | tail -n 1)
        if [[ -n "$match" ]]; then
            line="$match"
            break
        fi
    done
    if [[ -n "$line" ]]; then
        status=$(echo "$line" | sed -nE 's/.*TestResult\.([A-Z_]+).*/\1/p')
        time=$(echo "$line" | sed -nE 's/.*took ([0-9.]+s).*/\1/p')
        case "$status" in
            SUCCEEDED) marker="✓" ;;
            UNSUPPORTED) marker="?" ;;
            FAILED) marker="✕" ;;
            *) marker="·" ;;
        esac
        printf "%-22s %s %-13s %-10s\n" "$tc" "$marker" "$status" "$time"
    else
        # Fall back to artifact inspection.
        inferred=$(infer_status_from_artifacts "$tc_dir" "$tc")
        printf "%-22s %s [inf] %s\n" "$tc" "·" "$inferred"
    fi
done
