#!/usr/bin/env bash
#
# test-run-suite.sh — self-test of run-suite.sh's verdict and results_matrix.py's report,
# against fake suites whose harness records results through the real cli/tests/lib.sh and
# then ends one way or another. Offline, no Gradle, no amy: a stub stands in for the
# launcher. Needs bash, jq and python3.
#
#   tools/interop-ci/test-run-suite.sh
#
# Exits 0 when every case passes, 1 otherwise.

set -uo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$HERE/../.." && pwd)"
command -v jq >/dev/null || { echo "jq is required" >&2; exit 2; }

T="$(mktemp -d)"
trap 'rm -rf "$T"' EXIT

# A repo-shaped tree: the runner, the matrix, lib.sh, a stub amy and the fake suites.
mkdir -p "$T/tools/interop-ci/suites" "$T/cli/tests" "$T/cli/build/install/amy/bin"
cp "$HERE/run-suite.sh" "$HERE/results_matrix.py" "$T/tools/interop-ci/"
cp "$REPO_ROOT/cli/tests/lib.sh" "$T/cli/tests/"
printf '#!/bin/sh\nexit 0\n' >"$T/cli/build/install/amy/bin/amy"
chmod +x "$T/cli/build/install/amy/bin/amy"

# fake_<how>: passes three scenarios, then ends as <how> says.
#   ok        exits 0                  -> pass
#   failed    records a fail, exits 1  -> fail, not aborted
#   abort1    exits 1, no fail on file -> aborted at the third test
#   abort3    exits 3, no fail on file -> aborted
#   none      exits 0 before any result -> aborted, before its first result
#   timeout   sleeps past INTEROP_TIMEOUT -> aborted, timed out
cases=(ok failed abort1 abort3 none timeout)
{
  echo '{"suites": ['
  sep=""
  for c in "${cases[@]}"; do
    printf '%s{"id": "fake_%s", "name": "Fake %s", "counterpart": "fake", "isolation": "loopback"}\n' "$sep" "$c" "$c"
    sep=","
  done
  echo ']}'
} >"$T/tools/interop-ci/suites.json"

for c in "${cases[@]}"; do
  cat >"$T/tools/interop-ci/suites/fake_$c.sh" <<EOF
# shellcheck shell=bash
suite_resolve() { REF="-"; VERSION="test"; }
suite_prepare() { :; }
suite_run() { bash "\$REPO_ROOT/cli/tests/fake-harness.sh" $c; }
EOF
done

cat >"$T/cli/tests/fake-harness.sh" <<'EOF'
# The harness the fake suites run: lib.sh's record_result, then the ending asked for.
how="$1"
STATE_DIR="$(mktemp -d)"
LOG_FILE="$STATE_DIR/run.log"
RESULTS_FILE="$STATE_DIR/results.tsv"
: >"$LOG_FILE"; : >"$RESULTS_FILE"
# shellcheck source=/dev/null
source "$(dirname "$0")/lib.sh"
[[ "$how" == none ]] && exit 0
banner "Section A"
record_result "test-01-amy->fake" pass
record_result "test-02-fake->amy" pass
banner "Section B"
record_result "test-03-amy->fake" pass
case "$how" in
  ok) exit 0 ;;
  failed) record_result "test-04-amy->fake" fail "wrong answer"; exit 1 ;;
  abort1) exit 1 ;;
  abort3) exit 3 ;;
  timeout) sleep 30 ;;
esac
EOF

pass=0 fail=0
check() { # name, condition...
  local name="$1"
  shift
  if "$@"; then
    pass=$((pass + 1))
  else
    fail=$((fail + 1))
    echo "FAIL: $name" >&2
  fi
}

OUT="$T/out"
for c in "${cases[@]}"; do
  export INTEROP_OUT="$OUT" INTEROP_CACHE="$T/cache"
  "$T/tools/interop-ci/run-suite.sh" resolve "fake_$c" pinned >/dev/null 2>&1
  if [[ "$c" == timeout ]]; then
    INTEROP_TIMEOUT=3s "$T/tools/interop-ci/run-suite.sh" run "fake_$c" pinned >"$T/$c.out" 2>&1
  else
    "$T/tools/interop-ci/run-suite.sh" run "fake_$c" pinned >"$T/$c.out" 2>&1
  fi
  echo "$?" >"$T/$c.rc"
done

rc() { cat "$T/$1.rc"; }
jsonl() { cat "$OUT/pinned/fake_$1.jsonl"; }
meta() { jq -r "$2" "$OUT/pinned/fake_$1.meta.json"; }
aborted_row() { jsonl "$1" | jq -c 'select(.status == "aborted")'; }

# The run's own verdict.
check "ok passes" test "$(rc ok)" -eq 0
check "ok files no aborted row" test -z "$(aborted_row ok)"
check "ok meta says not aborted" test "$(meta ok .aborted)" = false
check "a recorded failure fails" test "$(rc failed)" -eq 1
check "a recorded failure is not an abort" test -z "$(aborted_row failed)"
for c in abort1 abort3; do
  check "$c fails the run" test "$(rc "$c")" -eq 1
  check "$c files one aborted row" test "$(aborted_row "$c" | grep -c .)" -eq 1
  check "$c names the last test reached" test "$(aborted_row "$c" | jq -r .last_test)" = "test-03-amy->fake"
  check "$c meta says aborted" test "$(meta "$c" .aborted)" = true
done
check "abort1 names exit 1" test "$(aborted_row abort1 | jq -r .exit)" -eq 1
check "abort1 note names the exit code" grep -q 'exited 1 with 3 result(s) and no failure recorded; the last test it reached was test-03-amy->fake (in Section B)' <<<"$(aborted_row abort1 | jq -r .note)"
check "abort3 names exit 3" test "$(aborted_row abort3 | jq -r .exit)" -eq 3
check "no result at all is an abort" test "$(rc none)" -eq 1
check "no result at all says so" grep -q 'before its first result' <<<"$(aborted_row none | jq -r .note)"
check "a timeout is an abort" test "$(rc timeout)" -eq 1
check "a timeout says so" grep -q 'timed out after 3s (exit 124)' <<<"$(aborted_row timeout | jq -r .note)"
check "the runner prints ABORTED" grep -q 'ABORTED' "$T/abort1.out"

# The report.
python3 "$T/tools/interop-ci/results_matrix.py" --results "$OUT" --registry "$T/tools/interop-ci/suites.json" \
  --markdown "$T/matrix.md" --junit-dir "$T/junit" >/dev/null
md="$(cat "$T/matrix.md")"
check "matrix marks an aborted suite" grep -q '^| 🛑 Fake abort1 | fake | test | 3 | 0 + aborted |' <<<"$md"
check "matrix marks a passing suite" grep -q '^| ✅ Fake ok | fake | test | 3 | 0 |' <<<"$md"
check "matrix lists the aborted runs" grep -q '^### 🛑 Aborted runs' <<<"$md"
check "matrix names exit and last test" grep -q '^| fake_abort1 | 1 | test-03-amy->fake |' <<<"$md"
check "matrix lists a run aborted before any result" grep -q '^| fake_none | 0 | (none) |' <<<"$md"
check "junit counts an abort as an error" grep -q 'errors="1"' "$T/junit/TEST-interop.pinned.fake_abort1.xml"
check "junit passing suite has no error" grep -q 'errors="0"' "$T/junit/TEST-interop.pinned.fake_ok.xml"

echo "test-run-suite: $pass passed, $fail failed"
[[ "$fail" -eq 0 ]]
