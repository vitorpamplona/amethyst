#!/usr/bin/env bash
#
# git-grasp-e2e.sh — pull requests, PR updates, in-app edits and merges against
# a GRASP server, end to end, with nothing leaving this machine.
#
# `amy serve --git-root` is a relay that is also a dev GRASP server on the same
# port: git smart HTTP for announced repositories, refs/nostr/<id> open to
# anyone, branch pushes only as the maintainers' signed state (30618) says, and
# NIP-98 on every request of a private repository (GRASP-08). A second relay is
# the maintainer's outbox, to check what a private repository must not reach.
#
# Flow: announce + state + push main → refuse an unsigned branch push → fork →
# `amy git pr --source` (commits to refs/nostr/<id>, event names the server) →
# `amy git pr-update --source` → `amy git edit` (a commit made from file text) →
# `amy git merge --yes` → private repo: anonymous fetch refused, a maintainer's
# edit works, nothing on the outbox relay.
#
# Usage: ./git-grasp-e2e.sh [--port N] [--no-build] [--keep]
#   --keep  leave both relays running afterwards, for the desktop app to be pointed at
#           (the maintainer's key is in state-git-grasp/m.nsec; stop with --stop)
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-git-grasp"
LOG_DIR="$STATE_DIR/logs"
RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"
HOST="127.0.0.1"
PORT="${PORT:-7795}"
NO_BUILD=0
KEEP=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --port) PORT="$2"; shift ;;
    --no-build) NO_BUILD=1 ;;
    --keep) KEEP=1 ;;
    --stop)
      for f in "$STATE_DIR"/*.pid; do [[ -f "$f" ]] && kill "$(cat "$f")" 2>/dev/null; done
      echo stopped; exit 0 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
  shift
done
OUTBOX_PORT=$((PORT + 1))
RELAY="ws://$HOST:$PORT"
OUTBOX="ws://$HOST:$OUTBOX_PORT"
HTTP="http://$HOST:$PORT"

# Relays a --keep run left behind hold the ports.
for f in "$STATE_DIR"/*.pid; do [[ -f "$f" ]] && kill "$(cat "$f")" 2>/dev/null; done
rm -rf "$STATE_DIR"
mkdir -p "$LOG_DIR"
: >"$RESULTS_FILE"
# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"

assert_eq() {
  if [[ "$1" == "$2" ]]; then info "assert: $3 \"$1\""; record_result "$3" pass "${4:-}"; return 0; fi
  fail_msg "$3: expected \"$2\", got \"$1\" (${4:-})"; record_result "$3" fail "${4:-mismatch}"; return 1
}
assert_true() {
  if eval "$1"; then info "assert: $2"; record_result "$2" pass "${3:-}"; return 0; fi
  fail_msg "$2: $1 was false (${3:-})"; record_result "$2" fail "${3:-false}"; return 1
}

PIDS=()
cleanup() {
  if [[ "$KEEP" -eq 1 ]]; then
    printf '%s\n' "${PIDS[0]}" >"$STATE_DIR/relay.pid"
    printf '%s\n' "${PIDS[1]}" >"$STATE_DIR/outbox.pid"
    info "kept: GRASP relay $RELAY, outbox $OUTBOX; maintainer key $STATE_DIR/m.nsec"
  else
    for pid in "${PIDS[@]}"; do kill "$pid" 2>/dev/null; done
  fi
  trap - EXIT INT TERM HUP
  print_summary
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

banner "amy git — GRASP end to end ($RUN_TS)"
command -v jq >/dev/null || { fail_msg "jq is required"; exit 1; }
if [[ "$NO_BUILD" -eq 0 ]]; then
  step "Building amy (installDist)…"
  (cd "$REPO_ROOT" && ./gradlew -q :cli:installDist) >>"$LOG_FILE" 2>&1 || { fail_msg "build failed (see $LOG_FILE)"; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { fail_msg "amy binary not found at $AMY_BIN"; exit 1; }

strip() { grep -vE "Picked up JAVA_TOOL|DEBUG:|INFO:"; }
amy_as() {
  local who="$1"; shift
  HOME="$STATE_DIR/$who" "$AMY_BIN" --account "$who" --secret-backend plaintext --json "$@" 2>>"$LOG_FILE" | strip
}
M() { amy_as m "$@"; }
C() { amy_as c "$@"; }
# git over plain HTTP to this machine: no proxy, no prompts.
g() { NO_PROXY="$HOST,localhost" no_proxy="$HOST,localhost" GIT_TERMINAL_PROMPT=0 git -c http.proxy= -c user.email=t@e.st -c user.name=tester "$@"; }

mkdir -p "$STATE_DIR"/{m,c,relay,outbox}
step "Starting the GRASP relay on $RELAY and the outbox relay on $OUTBOX…"
HOME="$STATE_DIR/relay" "$AMY_BIN" --account r --secret-backend plaintext init >>"$LOG_FILE" 2>&1
HOME="$STATE_DIR/relay" "$AMY_BIN" --account r --secret-backend plaintext serve --host "$HOST" --port "$PORT" --git-root "$STATE_DIR/git" >>"$LOG_DIR/relay.log" 2>&1 &
PIDS+=($!)
HOME="$STATE_DIR/outbox" "$AMY_BIN" --account o --secret-backend plaintext init >>"$LOG_FILE" 2>&1
HOME="$STATE_DIR/outbox" "$AMY_BIN" --account o --secret-backend plaintext serve --host "$HOST" --port "$OUTBOX_PORT" >>"$LOG_DIR/outbox.log" 2>&1 &
PIDS+=($!)
for _ in $(seq 1 90); do
  grep -q "relay up at" "$LOG_DIR/relay.log" 2>/dev/null && grep -q "relay up at" "$LOG_DIR/outbox.log" 2>/dev/null && break
  sleep 0.5
done
grep -q "relay up at" "$LOG_DIR/relay.log" || { fail_msg "GRASP relay did not come up"; exit 1; }

# Accounts whose relay lists name only these relays: nothing goes to public relays. The
# maintainer's key is kept, so the desktop app can log in as them (--keep).
M_NSEC="$("$AMY_BIN" --json key generate 2>>"$LOG_FILE" | strip | jq -r .nsec)"
( umask 077; printf '%s\n' "$M_NSEC" >"$STATE_DIR/m.nsec" )
amy_as m init --nsec "$M_NSEC" >/dev/null
amy_as c init >/dev/null
for who in m c; do
  amy_as "$who" relay nip65 clear >/dev/null
  amy_as "$who" relay add "$OUTBOX" >/dev/null
  amy_as "$who" relay publish-lists >/dev/null
done
M_NPUB="$(M whoami | jq -r .npub)"
C_NPUB="$(C whoami | jq -r .npub)"
info "maintainer $M_NPUB, contributor $C_NPUB"

# =============================================================================
banner "repository: announce, sign the state, push main"
WORK="$STATE_DIR/work"
g init -q -b main "$WORK"
printf 'hello\n' >"$WORK/README.md"
g -C "$WORK" add README.md
g -C "$WORK" commit -qm "initial commit"
ROOT="$(g -C "$WORK" rev-parse HEAD)"
UP="$HTTP/$M_NPUB/demo.git"
ADDR="$(M git announce --name demo --d demo --clone "$UP" --relay "$RELAY" --earliest-commit "$ROOT" | jq -r .address)"
M git state "$ADDR" --head main --branch "main=$ROOT" --relay "$RELAY" >/dev/null
g -C "$WORK" push -q "$UP" main:main >>"$LOG_FILE" 2>&1
assert_eq "$(g ls-remote "$UP" refs/heads/main | cut -f1)" "$ROOT" grasp.push_signed "a push the signed state names is taken"

printf 'unsigned\n' >>"$WORK/README.md"
g -C "$WORK" commit -qam "not signed"
assert_true '! g -C "$WORK" push -q "$UP" main:main >>"$LOG_FILE" 2>&1' grasp.push_unsigned "a branch push the state does not name is refused"
g -C "$WORK" reset -q --hard "$ROOT"

# =============================================================================
banner "fork and open a pull request (amy git pr --source)"
FORK="$HTTP/$C_NPUB/demo.git"
FADDR="$(C git announce --name demo --d demo --clone "$FORK" --relay "$RELAY" --earliest-commit "$ROOT" | jq -r .address)"
g -C "$WORK" checkout -qb feature
printf 'a feature\n' >"$WORK/feature.txt"
g -C "$WORK" add feature.txt
g -C "$WORK" commit -qm "Add a feature"
FTIP="$(g -C "$WORK" rev-parse HEAD)"
C git state "$FADDR" --head main --branch "main=$ROOT,feature=$FTIP" --relay "$RELAY" >/dev/null
g -C "$WORK" push -q "$FORK" main:main feature:feature >>"$LOG_FILE" 2>&1

PR="$(C git pr "$ADDR" --source "$FORK" --branch feature --subject "Add a feature" "Adds feature.txt" --relay "$RELAY")"
info "pr: $PR"
PRID="$(echo "$PR" | jq -r .event_id)"
UPDIR="$STATE_DIR/git/$M_NPUB/demo.git"
assert_eq "$(g --git-dir "$UPDIR" rev-parse "refs/nostr/$PRID" 2>/dev/null)" "$FTIP" pr.ref "the PR's commits are on the upstream server under refs/nostr/<id>"
assert_eq "$(echo "$PR" | jq -r '.servers[0]')" "$UP" pr.servers "the event names the server that holds them"
PREV="$(C fetch --id "$PRID" --relay "$RELAY")"
assert_eq "$(echo "$PREV" | jq -r '.events[0].tags[] | select(.[0]=="c") | .[1]')" "$FTIP" pr.tip_tag "the PR's c tag is the tip"
assert_eq "$(echo "$PREV" | jq -r '[.events[0].tags[] | select(.[0]=="clone")][0][1]')" "$UP" pr.clone_tag "its clone tag is the GRASP server"

# =============================================================================
banner "update the pull request (amy git pr-update --source)"
printf 'more\n' >>"$WORK/feature.txt"
g -C "$WORK" commit -qam "More feature"
FTIP2="$(g -C "$WORK" rev-parse HEAD)"
C git state "$FADDR" --head main --branch "main=$ROOT,feature=$FTIP2" --relay "$RELAY" >/dev/null
g -C "$WORK" push -q "$FORK" feature:feature >>"$LOG_FILE" 2>&1
UPD="$(C git pr-update "$PRID" --source "$FORK" --relay "$RELAY")"
info "update: $UPD"
UPDID="$(echo "$UPD" | jq -r .event_id)"
assert_eq "$(echo "$UPD" | jq -r .kind)" "1619" update.kind "a kind 1619"
assert_eq "$(g --git-dir "$UPDIR" rev-parse "refs/nostr/$UPDID" 2>/dev/null)" "$FTIP2" update.ref "the new tip is under refs/nostr/<update id>"

# =============================================================================
banner "propose an edit (amy git edit)"
EDIT="$(C git edit "$ADDR" --path README.md --text $'hello, edited\n' --message "Edit the README" --relay "$RELAY")"
info "edit: $EDIT"
EDID="$(echo "$EDIT" | jq -r .event_id)"
assert_eq "$(echo "$EDIT" | jq -r .parent)" "$ROOT" edit.parent "made on the signed tip of main"
assert_eq "$(g --git-dir "$UPDIR" show "refs/nostr/$EDID:README.md" 2>/dev/null)" "hello, edited" edit.content "the commit made from the text is on the server"
assert_eq "$(g --git-dir "$UPDIR" rev-parse "refs/nostr/$EDID^" 2>/dev/null)" "$ROOT" edit.on_parent "and sits on main"
assert_true 'g --git-dir "$UPDIR" fsck --no-progress --strict >>"$LOG_FILE" 2>&1' edit.fsck "the server's repository is valid"

# =============================================================================
banner "merge the pull request (amy git merge --yes)"
MERGE="$(M git merge "$PRID" --yes --relay "$RELAY")"
info "merge: $MERGE"
NEWMAIN="$(g ls-remote "$UP" refs/heads/main | cut -f1)"
assert_true '[[ "$NEWMAIN" != "$ROOT" ]]' merge.moved "main moved on the server"
assert_true 'g --git-dir "$UPDIR" merge-base --is-ancestor "$FTIP2" "$NEWMAIN"' merge.contains "and holds the updated PR's tip"

# =============================================================================
banner "private repository (GRASP-08)"
SECRET="$HTTP/$M_NPUB/secret.git"
SADDR="$(M git announce --name secret --d secret --private --clone "$SECRET" --relay "$RELAY" --earliest-commit "$ROOT" | jq -r .address)"
M git state "$SADDR" --head main --branch "main=$ROOT" --relay "$RELAY" >/dev/null
# Seed its main from disk (git's command line has no NIP-98): what a maintainer's client would push.
SDIR="$STATE_DIR/git/$M_NPUB/secret.git"
mkdir -p "$(dirname "$SDIR")"
g init -q --bare "$SDIR"
g --git-dir "$SDIR" config http.receivepack true
g --git-dir "$SDIR" config uploadpack.allowFilter true
g --git-dir "$SDIR" config uploadpack.allowAnySHA1InWant true
g -C "$WORK" push -q "$SDIR" "$ROOT:refs/heads/main" >>"$LOG_FILE" 2>&1
assert_true '! g ls-remote "$SECRET" >>"$LOG_FILE" 2>&1' private.anonymous "an anonymous fetch is refused"
SEDIT="$(M git edit "$SADDR" --path README.md --text $'secret\n' --message "A private edit" --relay "$RELAY")"
info "private edit: $SEDIT"
SEDID="$(echo "$SEDIT" | jq -r .event_id)"
assert_eq "$(g --git-dir "$SDIR" show "refs/nostr/$SEDID:README.md" 2>/dev/null)" "secret" private.maintainer "a maintainer's signed requests are served"
# Not a maintainer: the server refuses their requests, so amy fails (a non-zero exit).
amy_rc() { local who="$1"; shift; HOME="$STATE_DIR/$who" "$AMY_BIN" --account "$who" --secret-backend plaintext --json "$@" >>"$LOG_FILE" 2>&1; }
assert_true '! amy_rc c git edit "$SADDR" --path README.md --text x --message no --relay "$RELAY"' private.stranger "someone else's requests are refused"
assert_eq "$(M fetch --id "$SEDID" --relay "$OUTBOX" | jq -r '.events | length')" "0" private.not_outbox "the private PR is not on the outbox relay"
assert_eq "$(M fetch --id "$SEDID" --relay "$RELAY" | jq -r '.events | length')" "1" private.on_repo "it is on the repository's relay"
assert_eq "$(M fetch --id "$PRID" --relay "$OUTBOX" | jq -r '.events | length')" "1" public.outbox "a public PR does reach the outbox"
