#!/usr/bin/env bash
#
# run-suite.sh — one interop suite, the way the nightly workflow runs it.
#
#   tools/interop-ci/run-suite.sh resolve <suite> <pinned|latest>
#       Which counterpart commit this run tests. Prints `ref=…`, `version=…` and the
#       suite's registry fields as `key=value` lines (the workflow appends them to
#       $GITHUB_OUTPUT), and saves them to $INTEROP_OUT/<channel>/<suite>.env.
#   tools/interop-ci/run-suite.sh prepare <suite> <pinned|latest>
#       Fetch and build the counterpart at that commit (network allowed). Built
#       binaries go under $INTEROP_CACHE/<suite>/, which the workflow caches.
#   tools/interop-ci/run-suite.sh run <suite> <pinned|latest>
#       Run the harness, fully local: inside tools/interop-ci/netns-run.sh for an
#       `isolation: netns` suite, else on the host with AMY_LOCAL_ONLY=1. Results
#       land in $INTEROP_OUT/<channel>/<suite>.jsonl (one JSON line per scenario,
#       written by cli/tests/lib.sh) plus <suite>.meta.json. Exits 1 when any
#       scenario failed or the harness died before recording a result.
#
# The amy launcher must already be built (./gradlew :cli:installDist).
#
# Environment:
#   INTEROP_TIMEOUT  how long `run` lets the harness take (`timeout` syntax), default none
#   INTEROP_OUT    default <repo>/build/interop-ci
#   INTEROP_CACHE  default ~/.cache/amethyst-interop
#
# Each suite is tools/interop-ci/suites/<suite>.sh, sourced here. It defines
#   suite_resolve <channel>   sets REF (a commit, or "-" when there is no counterpart
#                             to fetch) and VERSION (a label for the report)
#   suite_prepare             fetches/builds at $REF
#   suite_run                 runs the harness (already inside the isolation); its
#                             exit status is informational, the results decide
# and may read REPO_ROOT, SUITE, CHANNEL, REF, VERSION, SUITE_CACHE, STATE_OUT.
set -uo pipefail

HERE="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$HERE/../.." && pwd)"
REGISTRY="$HERE/suites.json"
INTEROP_OUT="${INTEROP_OUT:-$REPO_ROOT/build/interop-ci}"
INTEROP_CACHE="${INTEROP_CACHE:-$HOME/.cache/amethyst-interop}"
AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"

die() { echo "run-suite: $*" >&2; exit 2; }

[[ $# -ge 3 ]] || die "usage: $0 resolve|prepare|run <suite> <pinned|latest>"
CMD="$1" SUITE="$2" CHANNEL="$3"
case "$CHANNEL" in pinned|latest) ;; *) die "channel must be pinned or latest, not $CHANNEL" ;; esac
command -v jq >/dev/null || die "jq is required"

field() { jq -r --arg id "$SUITE" --arg f "$1" '.suites[] | select(.id == $id) | .[$f] // empty | if type == "array" then join(" ") else tostring end' "$REGISTRY"; }
[[ -n "$(field id)" ]] || die "no suite '$SUITE' in $REGISTRY"
DRIVER="$HERE/suites/$SUITE.sh"
[[ -f "$DRIVER" ]] || die "no driver $DRIVER"

OUT_DIR="$INTEROP_OUT/$CHANNEL"
ENV_FILE="$OUT_DIR/$SUITE.env"
SUITE_CACHE="$INTEROP_CACHE/$SUITE"
STATE_OUT="$INTEROP_OUT/state/$SUITE"
mkdir -p "$OUT_DIR" "$SUITE_CACHE" "$STATE_OUT"
export REPO_ROOT SUITE CHANNEL SUITE_CACHE STATE_OUT AMY_BIN

REF="" VERSION=""

# Helpers for the drivers.
# git_head URL [BRANCH] — the commit the remote's default branch (or BRANCH) points at.
git_head() {
  local ref="${2:-HEAD}"
  [[ "$ref" == HEAD ]] || ref="refs/heads/$ref"
  git ls-remote "$1" "$ref" | awk 'NR==1{print $1}'
}
# git_tag_of URL SHA — every tag pointing at SHA, one per line, in version order.
git_tag_of() {
  git ls-remote --tags "$1" | awk -v s="$2" '$1==s{sub("refs/tags/","",$2); sub(/\^\{\}$/,"",$2); print $2}' \
    | sort -uV
}
# git_checkout URL DIR SHA — DIR holds a (blobless) clone of URL with SHA checked out.
git_checkout() {
  local url="$1" dir="$2" sha="$3"
  if [[ ! -d "$dir/.git" ]]; then
    git clone --quiet --filter=blob:none --no-checkout "$url" "$dir" || return 1
  fi
  if ! git -C "$dir" cat-file -e "$sha^{commit}" 2>/dev/null; then
    git -C "$dir" fetch --quiet --filter=blob:none origin "$sha" || git -C "$dir" fetch --quiet origin || return 1
  fi
  git -C "$dir" -c advice.detachedHead=false checkout --quiet --force --detach "$sha"
}

# shellcheck source=/dev/null
source "$DRIVER"

load_env() {
  [[ -f "$ENV_FILE" ]] || die "run '$0 resolve $SUITE $CHANNEL' first"
  REF=$(sed -n 's/^ref=//p' "$ENV_FILE")
  VERSION=$(sed -n 's/^version=//p' "$ENV_FILE")
  export REF VERSION
}

case "$CMD" in
  resolve)
    suite_resolve "$CHANNEL" || die "could not resolve the $CHANNEL counterpart of $SUITE"
    [[ -n "$REF" && -n "$VERSION" ]] || die "$SUITE's suite_resolve set no REF/VERSION"
    {
      echo "ref=$REF"
      echo "version=$VERSION"
      echo "cache_key=interop-$SUITE-$CHANNEL-$REF"
    } | tee "$ENV_FILE"
    ;;

  prepare)
    load_env
    [[ -x "$AMY_BIN" ]] || die "amy is not built at $AMY_BIN (./gradlew :cli:installDist)"
    suite_prepare
    ;;

  run)
    load_env
    [[ -x "$AMY_BIN" ]] || die "amy is not built at $AMY_BIN (./gradlew :cli:installDist)"
    RESULTS="$OUT_DIR/$SUITE.jsonl"
    : >"$RESULTS"
    isolation=$(field isolation)
    counterpart=$(field counterpart)
    export INTEROP_RESULTS_DIR="$OUT_DIR" INTEROP_SUITE="$SUITE" \
      INTEROP_COUNTERPART="$counterpart $VERSION" AMY_LOCAL_ONLY=1
    LOG="$STATE_OUT/harness-$CHANNEL.log"
    # INTEROP_TIMEOUT (a `timeout` duration, e.g. 90m; 0 = none) bounds the harness here
    # rather than around this script, so a timed-out run still gets its results filed.
    tmo=(timeout --signal=TERM --kill-after=60 "${INTEROP_TIMEOUT:-0}")
    started=$(date +%s)
    if [[ "$isolation" == "netns" ]]; then
      addr_args=()
      for a in $(field addresses); do addr_args+=(--addr "$a"); done
      "${tmo[@]}" "$HERE/netns-run.sh" "${addr_args[@]}" -- "$HERE/run-suite.sh" _exec "$SUITE" "$CHANNEL" 2>&1 | tee "$LOG"
      rc=${PIPESTATUS[0]}
    else
      echo "run-suite: $SUITE runs on the host (isolation: $isolation) with AMY_LOCAL_ONLY=1"
      "${tmo[@]}" "$HERE/run-suite.sh" _exec "$SUITE" "$CHANNEL" 2>&1 | tee "$LOG"
      rc=${PIPESTATUS[0]}
    fi
    finished=$(date +%s)

    fails=$(grep -c '"status":"fail"' "$RESULTS" || true)
    total=$(grep -c . "$RESULTS" || true)
    # A harness that died before its first result, or with a non-zero status and no
    # failure on record, still has to show up red: file it as a scenario of its own.
    if [[ "$total" -eq 0 || ( "$rc" -ne 0 && "$fails" -eq 0 && "$rc" -ne 1 ) ]]; then
      note="harness exited $rc after $total result(s); see harness-$CHANNEL.log"
      [[ "$rc" -eq 124 || "$rc" -eq 137 ]] && note="harness timed out after ${INTEROP_TIMEOUT:-?} with $total result(s); see harness-$CHANNEL.log"
      printf '{"suite":"%s","counterpart":%s,"id":"harness","status":"fail","note":"%s","section":"","feature":"Harness","direction":"","ts":%d,"elapsed_s":%d}\n' \
        "$SUITE" "$(jq -Rn --arg s "$counterpart $VERSION" '$s')" "$note" "$finished" "$((finished - started))" >>"$RESULTS"
      fails=$((fails + 1))
    fi
    jq -n --arg suite "$SUITE" --arg channel "$CHANNEL" --arg counterpart "$counterpart" \
      --arg version "$VERSION" --arg ref "$REF" --arg isolation "$isolation" \
      --argjson exit "$rc" --argjson seconds "$((finished - started))" \
      '{suite:$suite, channel:$channel, counterpart:$counterpart, version:$version, ref:$ref,
        isolation:$isolation, exit:$exit, seconds:$seconds}' >"$OUT_DIR/$SUITE.meta.json"
    echo "run-suite: $SUITE ($CHANNEL, $counterpart $VERSION): $total result(s), $fails failure(s), harness exit $rc"
    [[ "$fails" -eq 0 ]]
    ;;

  _exec)
    # Inside the isolation. Re-read the resolved commit and hand over to the driver.
    load_env
    suite_run
    ;;

  *) die "unknown command $CMD" ;;
esac
