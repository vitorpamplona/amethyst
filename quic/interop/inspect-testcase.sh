#!/usr/bin/env bash
# Per-testcase deep-dive diagnostic. Use after running any single
# testcase that failed:
#   ./quic/interop/inspect-testcase.sh longrtt
#
# Auto-finds the most recent run dir that has the named testcase and
# reports the runner status line + qlog summary + frame histograms
# (sent/received) + connection-close events. Designed to fit in one
# screen of output.
set -o pipefail

TC="${1:-}"
if [[ -z "$TC" ]]; then
    echo "usage: $0 <testcase>   (e.g. longrtt, retry, http3)" >&2
    exit 2
fi

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
RUNNER_LOGS="${REPO_ROOT}/../quic-interop-runner/logs"

# Most recent run dir that has the named testcase.
RUN_DIR=""
# Filter to actual directories — zsh's glob also matches the
# sibling .stdout.log files that run-matrix.sh tees, which start
# with the same 'run-' prefix and would resolve as not-a-dir.
for d in $(ls -1dt "$RUNNER_LOGS"/run-* 2>/dev/null); do
    [[ -d "$d" ]] || continue
    if ls -d "$d"/*amethyst*/"$TC" >/dev/null 2>&1; then
        RUN_DIR="$d"
        break
    fi
done
if [[ -z "$RUN_DIR" ]]; then
    echo "no run dir with testcase '$TC' under $RUNNER_LOGS" >&2
    exit 1
fi
echo "==> run dir: $RUN_DIR"
TC_DIR="$(ls -1d "$RUN_DIR"/*amethyst*/"$TC" 2>/dev/null | head -n 1)"
echo "==> testcase dir: $TC_DIR"

echo
echo "=============== runner status ==============="
if [[ -f "${RUN_DIR}.stdout.log" ]]; then
    grep -E "Test: $TC took" "${RUN_DIR}.stdout.log" | tail -n 5
else
    echo "(no .stdout.log — run-matrix.sh predates the tee, status not saved)"
fi

echo
echo "=============== client diagnostic traces (DEBUG=1) ==============="
# Three places to look, in priority order:
#   1. Per-testcase client output (testcases that write to file)
#   2. Per-testcase output.txt (some runner versions)
#   3. The runner's tee'd .stdout.log narrowed to this testcase's
#      window — but only works for short tests, since matrix runs
#      restart containers and longer tests' stderr can be lost
#      when the container is killed mid-test.
CLIENT_TRACES=()
[[ -f "$TC_DIR/client/output.txt" ]] && CLIENT_TRACES+=("$TC_DIR/client/output.txt")
[[ -f "$TC_DIR/output.txt" ]] && CLIENT_TRACES+=("$TC_DIR/output.txt")
[[ -f "${RUN_DIR}.stdout.log" ]] && CLIENT_TRACES+=("${RUN_DIR}.stdout.log")

found=0
for f in "${CLIENT_TRACES[@]}"; do
    n=$(grep -cE '\[(boot|interop|batch|writer\.)' "$f" 2>/dev/null || echo 0)
    if [[ "$n" -gt 0 ]]; then
        echo "(found $n diagnostic lines in $f)"
        if [[ "$f" == *.stdout.log ]]; then
            # Narrow to this testcase's window.
            awk -v tc="$TC" '
                $0 ~ "Running test case: " tc { in_tc = 1; next }
                in_tc && /Running test case:/ { exit }
                in_tc && /\[(boot|interop|batch|writer\.)/ { print }
            ' "$f" | head -n 50 || true
        else
            grep -E '\[(boot|interop|batch|writer\.)' "$f" | head -n 50 || true
        fi
        found=1
        break
    fi
done

if [[ "$found" -eq 0 ]]; then
    echo "(no diagnostic lines for this testcase)"
    echo
    echo "Possible causes:"
    echo "  - The image was built without DEBUG=1: use 'DEBUG=1 ./run-matrix.sh ...'"
    echo "  - The container was killed mid-test before flushing stderr"
    echo "  - This testcase ran in a matrix and only the FIRST testcase's"
    echo "    traces were captured. Run this single testcase in isolation:"
    echo "    DEBUG=1 ./quic/interop/run-matrix.sh -s <peer> -t $TC"
fi

echo
echo "=============== file sizes generated for this testcase ==============="
if [[ -f "${RUN_DIR}.stdout.log" ]]; then
    # 'Generated random file: NAME of size: N' lines printed before
    # each test run. Extract the ones that came RIGHT BEFORE this
    # testcase's "Running test case" line.
    awk -v tc="$TC" '
        /^Running test case: / { current = $0 }
        /^Generated random file:/ { last_files = last_files "\n" $0 }
        $0 ~ ("Running test case: " tc) {
            print last_files
            last_files = ""
            exit
        }
    ' "${RUN_DIR}.stdout.log" | tail -n 10
else
    echo "(no .stdout.log)"
fi

echo
echo "=============== qlog event-type histogram ==============="
QLOG="$(ls -1 "$TC_DIR"/client/qlog/*.sqlog "$TC_DIR"/client/qlog/*.qlog 2>/dev/null | head -n 1 || true)"
if [[ -z "$QLOG" ]]; then
    echo "(no qlog — connection probably never made it past TLS)"
else
    echo "(qlog: $QLOG, $(wc -l <"$QLOG" | tr -d ' ') lines)"
    grep -oE '"name":"[^"]+"' "$QLOG" | sort | uniq -c | sort -rn | head
fi

if [[ -n "$QLOG" ]]; then
    echo
    echo "=============== transport_parameters (peer's flow-control budget) ==============="
    grep '"name":"transport:parameters_set"' "$QLOG"

    echo
    echo "=============== connection_closed (smoking gun for spec violations) ==============="
    grep '"name":"transport:connection_closed"' "$QLOG" | head -n 5 \
        || echo "(none — connection didn't formally close; ran out of time?)"

    echo
    echo "=============== last 10 sent / received packets (steady-state shape) ==============="
    echo "-- received --"
    grep '"name":"transport:packet_received"' "$QLOG" | tail -n 10
    echo "-- sent --"
    grep '"name":"transport:packet_sent"' "$QLOG" | tail -n 10

    echo
    echo "=============== first/last received packet timestamps (transfer rate hint) ==============="
    FIRST=$(grep '"name":"transport:packet_received"' "$QLOG" | head -n 1 | grep -oE '"time":[0-9]+' | head -n 1)
    LAST=$(grep '"name":"transport:packet_received"' "$QLOG" | tail -n 1 | grep -oE '"time":[0-9]+' | head -n 1)
    PKTS=$(grep -c '"name":"transport:packet_received"' "$QLOG")
    echo "first=$FIRST last=$LAST pkts=$PKTS"
fi

echo
echo "=============== server stderr (last 30 lines) ==============="
tail -n 30 "$TC_DIR/server/stderr.log" 2>/dev/null \
    || echo "(no server/stderr.log)"
