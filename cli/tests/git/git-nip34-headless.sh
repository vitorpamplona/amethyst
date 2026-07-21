#!/usr/bin/env bash
#
# git-nip34-headless.sh — drives the real `amy` binary against a real
# `amy serve` relay to prove the NIP-34 (git-over-nostr) collaboration surface
# end-to-end: repository announcement + state, patches, pull requests, issues,
# NIP-22 comments, and status events — plus the status-deriving reads.
#
# The verbs mirror the pure-Nostr surface of `ngit` and `nak git`. The git
# packfile transport (clone/fetch/push of real objects) is intentionally out of
# scope, so this harness only exercises the events amy publishes and reads back.
#
# Flow (single maintainer account against one relay):
#   announce (30617) → state (30618) → issue (1621) → patch (1617) →
#   pr (1618) → pr-update (1619) → comment (1111) → close the issue (1632) →
#   mark the pr applied (1631) → list issues/patches/prs (status derived) →
#   thread view (status timeline + comments).
#
# Usage: ./git-nip34-headless.sh [--port N] [--no-build]
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-git-nip34"
LOG_DIR="$STATE_DIR/logs"
RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"
RELAY_HOST="127.0.0.1"
RELAY_PORT="${RELAY_PORT:-7793}"
RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"
NO_BUILD=0
LIVE=0
LIVE_REPO="${LIVE_REPO:-https://github.com/octocat/Hello-World.git}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)     RELAY_PORT="$2"; RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"; shift ;;
    --no-build) NO_BUILD=1 ;;
    --live)     LIVE=1 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
  shift
done

rm -rf "$STATE_DIR"
mkdir -p "$LOG_DIR"
: >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"

assert_eq() {
  local actual="$1" expected="$2" test_id="$3" note="${4:-}"
  if [[ "${actual// /}" == "${expected// /}" ]]; then
    info "assert: $test_id \"$actual\" == \"$expected\""
    record_result "$test_id" pass "$note"
    return 0
  fi
  fail_msg "$test_id: expected \"$expected\", got \"$actual\" (${note:-})"
  record_result "$test_id" fail "${note:-mismatch}"
  return 1
}

assert_nonempty() {
  local actual="$1" test_id="$2" note="${3:-}"
  if [[ -n "$actual" && "$actual" != "null" ]]; then
    info "assert: $test_id nonempty (\"$actual\")"
    record_result "$test_id" pass "$note"
    return 0
  fi
  fail_msg "$test_id: expected a value, got empty/null (${note:-})"
  record_result "$test_id" fail "${note:-empty}"
  return 1
}

SERVE_PID=""
cleanup() {
  [[ -n "$SERVE_PID" ]] && kill "$SERVE_PID" 2>/dev/null
  trap - EXIT INT TERM HUP
  print_summary
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

banner "amy git — NIP-34 collaboration headless ($RUN_TS)"

# ---- build ------------------------------------------------------------------
if [[ "$NO_BUILD" -eq 0 ]]; then
  step "Building amy (installDist)…"
  (cd "$REPO_ROOT" && ./gradlew -q :cli:installDist) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "build failed (see $LOG_FILE)"; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { fail_msg "amy binary not found at $AMY_BIN"; exit 1; }

strip() { grep -vE "Picked up JAVA_TOOL|DEBUG:|INFO:"; }
mk_home() { mktemp -d "$STATE_DIR/home.XXXXXX"; }
# amy_run <home> <account> args...
amy_run() {
  local home="$1" acct="$2"; shift 2
  HOME="$home" "$AMY_BIN" --account "$acct" --secret-backend plaintext --json "$@" 2>>"$LOG_FILE" | strip
}

M_HOME="$(mk_home)"
amy_run "$M_HOME" m init >/dev/null
M() { amy_run "$M_HOME" m "$@"; }

step "Starting amy serve on $RELAY_URL…"
HOME="$M_HOME" "$AMY_BIN" --account m --secret-backend plaintext \
  serve --host "$RELAY_HOST" --port "$RELAY_PORT" >>"$LOG_FILE" 2>&1 &
SERVE_PID=$!
for _ in $(seq 1 60); do
  grep -q "relay up at" "$LOG_FILE" && break
  sleep 0.5
done
grep -q "relay up at" "$LOG_FILE" || { fail_msg "relay did not come up"; exit 1; }

# =============================================================================
# git init — bootstrap from a local (full, non-shallow) git checkout
# =============================================================================
banner "git init (from a full scratch checkout)"
INITREPO="$(mk_home)/initrepo"
git init -q "$INITREPO"
git -C "$INITREPO" config user.email a@b.c
git -C "$INITREPO" config user.name t
echo "hello" >"$INITREPO/README.md"
git -C "$INITREPO" add README.md
git -C "$INITREPO" commit -qm "initial commit"
ROOT_COMMIT="$(git -C "$INITREPO" rev-list --max-parents=0 --first-parent HEAD | tail -1)"
INIT="$(M git init --repo "$INITREPO" --relay "$RELAY_URL")"
info "init: $INIT"
assert_eq "$(echo "$INIT" | jq -r '.from_git_repo')" "true" init.from_git "init derived fields from the git repo"
assert_nonempty "$(echo "$INIT" | jq -r '.name')" init.name "repo name derived"
assert_eq "$(echo "$INIT" | jq -r '.earliest_commit')" "$ROOT_COMMIT" init.euc "earliest-unique-commit is the first-parent root"
assert_nonempty "$(echo "$INIT" | jq -r '.state_event_id')" init.state "init also published a 30618 state event"

# A SHALLOW clone must NOT invent an euc (it can't know the true root).
SHALLOWREPO="$(mk_home)/shallowrepo"
git clone -q --depth 1 "file://$INITREPO" "$SHALLOWREPO" 2>/dev/null || git clone -q --depth 1 "$INITREPO" "$SHALLOWREPO"
if [[ "$(git -C "$SHALLOWREPO" rev-parse --is-shallow-repository)" == "true" ]]; then
  SINIT="$(M git init --repo "$SHALLOWREPO" --d shallow-test --relay "$RELAY_URL")"
  assert_eq "$(echo "$SINIT" | jq -r '.earliest_commit')" "null" init.shallow_euc "shallow clone omits the euc (no wrong identity)"
else
  skip_msg "could not create a shallow clone for init.shallow_euc"
fi

# =============================================================================
# Repository announcement (30617) + state (30618)
# =============================================================================
banner "announce + state"
ANN="$(M git announce --name demo-repo --description "a demo" \
  --clone https://example.com/demo.git --earliest-commit abc123 --relay "$RELAY_URL")"
info "announce: $ANN"
ADDR="$(echo "$ANN" | jq -r '.address')"
assert_nonempty "$ADDR" announce.address "kind:pubkey:id coordinate returned"
assert_eq "$(echo "$ANN" | jq -r '.published_to | length')" "1" announce.published "relay accepted 30617"

STATE="$(M git state "$ADDR" --head main --branch main=deadbeef,dev=cafe00 --tag v1.0=abc123 --relay "$RELAY_URL")"
info "state: $STATE"
assert_eq "$(echo "$STATE" | jq -r '.branches')" "2" state.branches "two branch refs"
assert_eq "$(echo "$STATE" | jq -r '.head')" "main" state.head "HEAD=main"

# --- ngit interop: wire-format checks --------------------------------------
banner "ngit interop wire format"
# Announce a repo with TWO clone URLs; NIP-34/ngit want ONE multi-value tag.
IADDR="$(M git announce --name interop --clone https://a.git,https://b.git --web https://a.com,https://b.com --earliest-commit abc123 --relay "$RELAY_URL" | jq -r '.address')"
IEID="$(M git show "$IADDR" --relay "$RELAY_URL" | jq -r '.event_id')"
ITAGS="$(M fetch --id "$IEID" --relay "$RELAY_URL")"
assert_eq "$(echo "$ITAGS" | jq -r '[.events[0].tags[] | select(.[0]=="clone")] | length')" "1" interop.clone_single "clone is one tag (not repeated)"
assert_eq "$(echo "$ITAGS" | jq -r '.events[0].tags[] | select(.[0]=="clone") | length')" "3" interop.clone_multivalue "clone tag carries both URLs (name + 2 values)"
# The tolerant reader must round-trip both URLs back.
assert_eq "$(M git show "$IADDR" --relay "$RELAY_URL" | jq -r '.clone | length')" "2" interop.clone_read "reader returns both clone URLs"
# Issue must p-tag the repository owner (maintainer routing).
IISS="$(M git issue "$IADDR" --subject "interop" "b" --relay "$RELAY_URL" | jq -r '.event_id')"
IOWNER="$(echo "$IADDR" | cut -d: -f2)"
assert_eq "$(M fetch --id "$IISS" --relay "$RELAY_URL" | jq -r "[.events[0].tags[] | select(.[0]==\"p\" and .[1]==\"$IOWNER\")] | length")" "1" interop.issue_ptag "issue carries the repo owner p tag"

# GRASP server list (10317) round-trip.
GRASP="$(M git grasp set "wss://grasp.example.com,wss://grasp2.example.com" --relay "$RELAY_URL")"
assert_eq "$(echo "$GRASP" | jq -r '.kind')" "10317" grasp.kind "grasp list is kind 10317"
GRASP_READ="$(M git grasp list --relay "$RELAY_URL")"
assert_eq "$(echo "$GRASP_READ" | jq -r '.count')" "2" grasp.count "two grasp servers read back"
assert_eq "$(echo "$GRASP_READ" | jq -r '.grasps[0]')" "wss://grasp.example.com" grasp.order "preference order preserved"

# =============================================================================
# Issue (1621) + patch (1617) + PR (1618) + PR update (1619)
# =============================================================================
banner "issue / patch / pr / pr-update"
ISS="$(M git issue "$ADDR" --subject "First bug" "the issue body" --relay "$RELAY_URL")"
ISSID="$(echo "$ISS" | jq -r '.event_id')"
assert_eq "$(echo "$ISS" | jq -r '.kind')" "1621" issue.kind "issue is kind 1621"
assert_nonempty "$ISSID" issue.id "issue event id"

PATCH="$(printf 'From abc\nSubject: [PATCH] fix the thing\n\ndiff --git a/x b/x\n' \
  | M git patch "$ADDR" --root --commit deadbeef --relay "$RELAY_URL")"
info "patch: $PATCH"
assert_eq "$(echo "$PATCH" | jq -r '.kind')" "1617" patch.kind "patch is kind 1617"
assert_eq "$(echo "$PATCH" | jq -r '.subject')" "fix the thing" patch.subject "subject parsed from format-patch"

PR="$(M git pr "$ADDR" --commit feed01 --clone https://example.com/demo.git \
  --subject "add feature" "pr description" --relay "$RELAY_URL")"
PRID="$(echo "$PR" | jq -r '.event_id')"
assert_eq "$(echo "$PR" | jq -r '.kind')" "1618" pr.kind "pr is kind 1618"

PRUP="$(M git pr-update "$PRID" --commit feed02 --clone https://example.com/demo.git --relay "$RELAY_URL")"
assert_eq "$(echo "$PRUP" | jq -r '.kind')" "1619" prupdate.kind "pr-update is kind 1619"

# =============================================================================
# Comment (1111) + status events (1632 close, 1631 applied)
# =============================================================================
banner "comment + status"
CMT="$(M git comment "$ISSID" "thanks for reporting" --relay "$RELAY_URL")"
assert_eq "$(echo "$CMT" | jq -r '.kind')" "1111" comment.kind "comment is NIP-22 kind 1111"

LABEL="$(M git label "$ISSID" "bug,help-wanted" --relay "$RELAY_URL")"
assert_eq "$(echo "$LABEL" | jq -r '.kind')" "1985" label.kind "label is NIP-32 kind 1985"
assert_eq "$(echo "$LABEL" | jq -r '.labels | length')" "2" label.count "two labels attached"

CLOSE="$(M git close "$ISSID" "wontfix" --relay "$RELAY_URL")"
assert_eq "$(echo "$CLOSE" | jq -r '.kind')" "1632" close.kind "close status is kind 1632"

APPLIED="$(M git applied "$PRID" "merged it" --merge-commit feed99 --commit feed02 --relay "$RELAY_URL")"
assert_eq "$(echo "$APPLIED" | jq -r '.kind')" "1631" applied.kind "applied status is kind 1631"

# =============================================================================
# git apply — publish a real patch and apply it to a local working tree
# =============================================================================
banner "git apply (nostr patch → local git am)"
SCRATCH="$(mk_home)/scratch"
git init -q "$SCRATCH"
git -C "$SCRATCH" config user.email a@b.c
git -C "$SCRATCH" config user.name t
echo "line1" >"$SCRATCH/f.txt"
git -C "$SCRATCH" add f.txt
git -C "$SCRATCH" commit -qm "init"
# Make a real commit, capture its format-patch, then roll it back so `apply` can re-add it.
echo "line2" >>"$SCRATCH/f.txt"
git -C "$SCRATCH" commit -qam "add line2"
PATCHTXT="$(git -C "$SCRATCH" format-patch -1 --stdout)"
git -C "$SCRATCH" reset -q --hard HEAD~1
APATCH="$(printf '%s' "$PATCHTXT" | M git patch "$ADDR" --root --relay "$RELAY_URL")"
APID="$(echo "$APATCH" | jq -r '.event_id')"
CHECK="$(M git apply "$APID" --check --repo "$SCRATCH")"
assert_eq "$(echo "$CHECK" | jq -r '.mode')" "check" apply.check "apply --check dry-runs cleanly"
APPLY="$(M git apply "$APID" --repo "$SCRATCH")"
info "apply: $APPLY"
assert_eq "$(echo "$APPLY" | jq -r '.applied')" "true" apply.applied "patch applied via git am"
assert_eq "$(git -C "$SCRATCH" log --oneline | head -1 | sed 's/^[0-9a-f]* //')" "add line2" apply.commit "commit landed in the working tree"

# =============================================================================
# Status-deriving reads
# =============================================================================
banner "reads derive status"
ISSUES="$(M git issues "$ADDR" --relay "$RELAY_URL")"
info "issues: $ISSUES"
assert_eq "$(echo "$ISSUES" | jq -r '.items[0].status')" "closed" issues.status "closed issue reads as closed"

PRS="$(M git prs "$ADDR" --relay "$RELAY_URL")"
assert_eq "$(echo "$PRS" | jq -r '.items[0].status')" "applied" prs.status "applied pr reads as applied"

PATCHES="$(M git patches "$ADDR" --relay "$RELAY_URL")"
assert_eq "$(echo "$PATCHES" | jq -r '.items[0].status')" "open" patches.status "un-statused patch reads as open"

# --status filter: closed issues present, open issues empty.
FILTERED="$(M git issues "$ADDR" --closed --relay "$RELAY_URL")"
assert_eq "$(echo "$FILTERED" | jq -r '.count')" "1" issues.filter_closed "--closed keeps the closed issue"
OPEN_ONLY="$(M git issues "$ADDR" --open --relay "$RELAY_URL")"
assert_eq "$(echo "$OPEN_ONLY" | jq -r '.count')" "0" issues.filter_open "--open drops the closed issue"

# =============================================================================
# Thread view
# =============================================================================
banner "thread view"
THREAD="$(M git thread "$ISSID" --relay "$RELAY_URL")"
info "thread: $THREAD"
assert_eq "$(echo "$THREAD" | jq -r '.status')" "closed" thread.status "thread shows closed"
assert_eq "$(echo "$THREAD" | jq -r '.status_events | length')" "1" thread.status_events "one status event in timeline"
assert_eq "$(echo "$THREAD" | jq -r '.comments | length')" "1" thread.comments "one comment in thread"

# =============================================================================
# Read repo content over git smart-HTTP (--live only — needs a reachable host).
# =============================================================================
if [[ "$LIVE" -eq 1 ]]; then
  banner "live: git browse / cat / log ($LIVE_REPO)"
  BROWSE="$(M git browse "$LIVE_REPO" --json)"
  info "browse: $BROWSE"
  assert_nonempty "$(echo "$BROWSE" | jq -r '.head_commit')" live.browse "browse resolves a head commit"
  FIRST_FILE="$(echo "$BROWSE" | jq -r '.entries[] | select(.type=="file") | .name' | head -1)"
  if [[ -n "$FIRST_FILE" ]]; then
    CAT="$(M git cat "$LIVE_REPO" "$FIRST_FILE" --json)"
    assert_eq "$(echo "$CAT" | jq -r '.path')" "$FIRST_FILE" live.cat "cat returns the requested file"
    assert_nonempty "$(echo "$CAT" | jq -r '.oid')" live.cat_oid "cat resolves a blob oid"
  fi
  LOG="$(M git log "$LIVE_REPO" --depth 2 --json)"
  assert_nonempty "$(echo "$LOG" | jq -r '.commits[0].oid')" live.log "log returns at least one commit"

  # ngit interop against the REAL amethyst repo (published by ngit to relay.ngit.dev).
  # Proves our reader parses ngit's actual multi-value `clone` tag (4 URLs) — the
  # exact case the pre-fix reader collapsed to one.
  banner "live: reading the real ngit-published amethyst repo"
  NGIT_RELAY="${NGIT_RELAY:-wss://relay.ngit.dev}"
  NGIT_REPO="30617:460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c:amethyst"
  RSHOW="$(M git show "$NGIT_REPO" --relay "$NGIT_RELAY")"
  if [[ -n "$RSHOW" && "$(echo "$RSHOW" | jq -r '.name // empty')" == "amethyst" ]]; then
    CLONES="$(echo "$RSHOW" | jq -r '.clone | length')"
    if [[ "$CLONES" -ge 2 ]]; then
      pass_msg "ngit.clone_multivalue: read $CLONES clone URLs from ngit's multi-value tag"
      record_result ngit.clone_multivalue pass "$CLONES clone URLs"
    else
      fail_msg "ngit.clone_multivalue: only $CLONES clone URL parsed from ngit's multi-value tag"
      record_result ngit.clone_multivalue fail "expected >=2, got $CLONES"
    fi
    RISS="$(M git issues "$NGIT_REPO" --relay "$NGIT_RELAY" --limit 1 | jq -r '.count')"
    assert_nonempty "$RISS" ngit.issues "read ngit-published issues"
  else
    skip_msg "ngit live repo unreachable (relay.ngit.dev) — skipping real-repo interop check"
  fi
else
  skip_msg "live git smart-HTTP reads (browse/cat/log) + real ngit repo — pass --live to run"
fi

grep -q $'\tfail\t' "$RESULTS_FILE" && exit 1
exit 0
