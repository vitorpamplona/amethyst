#!/usr/bin/env bash
#
# agents-headless.sh — `amy agent` (NIP-FA AI agent keys, kind 12000) and `amy schedule`
# (NIP-FB scheduled tasks, kind 34817) against an embedded `amy serve` relay and a stub AI
# provider on loopback. Nothing here reaches a real relay or a real provider.
#
#   agent:    show / set (--key-file, --key-stdin, --default) / test (ok, rejected,
#             unreachable) / default / remove / clear, the argument checks (no key on
#             the command line, https unless loopback or no key), and that no full key
#             ever appears on stdout or stderr, nor on the relay.
#   schedule: set (new task in a named zone or this machine's, a new time keeping the
#             zone, an unchanged set), list, show, pause / resume, delete, bad cron and
#             zone names, and not_found.
#   contract: --json shapes and exit codes 0 / 1 / 2.
#
# Usage: ./agents-headless.sh [--port N] [--stub-port N] [--no-build]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-agents-headless"
LOG_DIR="$STATE_DIR/logs"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"
ALL_OUT="$STATE_DIR/all-output-$RUN_TS.txt"
ERR_FILE="$STATE_DIR/last-stderr"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"
RELAY_HOST="${RELAY_HOST:-127.0.0.2}"
RELAY_DATA="$STATE_DIR/relay"
RELAY_PORT="${RELAY_PORT:-8094}"
STUB_PORT="${STUB_PORT:-8095}"
NO_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)      RELAY_PORT="$2"; shift ;;
    --stub-port) STUB_PORT="$2"; shift ;;
    --no-build)  NO_BUILD=1 ;;
    -h|--help)
      sed -n '3,17p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
      exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done
RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"
STUB_URL="http://127.0.0.1:$STUB_PORT/v1"
# Where amy looks for an account's own relay list when it has none stored: the local relay only.
export AMY_BOOTSTRAP_RELAYS="$RELAY_URL"

# A fresh world each run: the relay's store is in memory, and so is nothing else.
rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR" "$LOG_DIR"
: >"$LOG_FILE"
: >"$RESULTS_FILE"
: >"$ALL_OUT"

# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"
# shellcheck source=../headless/helpers.sh
source "$TESTS_DIR/headless/helpers.sh"
# shellcheck source=../dm/setup.sh
source "$TESTS_DIR/dm/setup.sh"

STUB_PID=""
cleanup() {
  local rc=$?
  trap - EXIT INT TERM HUP
  [[ -n "$STUB_PID" ]] && kill "$STUB_PID" 2>/dev/null
  stop_local_relay
  print_summary
  if grep -q $'\tfail\t' "$RESULTS_FILE" 2>/dev/null; then exit 1; fi
  exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

# --- amy runner: RC, OUT, ERR; every byte of output also lands in $ALL_OUT for the key scan.
# A fixed zone (TZ) makes "this machine's zone" checkable.
run_as() {
  local who="$1"; shift
  OUT=$(HOME="$STATE_DIR" TZ="${AMY_TZ:-Europe/Lisbon}" "$AMY_BIN" --account "$who" --secret-backend plaintext --json "$@" 2>"$ERR_FILE" </dev/null); RC=$?
  ERR=$(cat "$ERR_FILE")
  printf '%s\n%s\n' "$OUT" "$ERR" >>"$ALL_OUT"
  printf '$ amy %s  (exit %s)\n%s\n%s\n' "$(shown "$@")" "$RC" "$OUT" "$ERR" >>"$LOG_FILE"
}
run() { run_as A "$@"; }

# Same, with --verbose: its progress logs land in $ALL_OUT for the key scan too.
run_verbose() {
  OUT=$(HOME="$STATE_DIR" TZ=Europe/Lisbon "$AMY_BIN" --account A --secret-backend plaintext --verbose --json "$@" 2>"$ERR_FILE" </dev/null); RC=$?
  ERR=$(cat "$ERR_FILE")
  printf '%s\n%s\n' "$OUT" "$ERR" >>"$ALL_OUT"
  printf '$ amy --verbose %s  (exit %s)\n%s\n%s\n' "$(shown "$@")" "$RC" "$OUT" "$ERR" >>"$LOG_FILE"
}

# Same, with stdin from a file.
run_stdin() {
  local file="$1"; shift
  OUT=$(HOME="$STATE_DIR" TZ=Europe/Lisbon "$AMY_BIN" --account A --secret-backend plaintext --json "$@" 2>"$ERR_FILE" <"$file"); RC=$?
  ERR=$(cat "$ERR_FILE")
  printf '%s\n%s\n' "$OUT" "$ERR" >>"$ALL_OUT"
  printf '$ amy %s <%s  (exit %s)\n%s\n%s\n' "$*" "$file" "$RC" "$OUT" "$ERR" >>"$LOG_FILE"
}

# check ID ACTUAL EXPECTED [NOTE]
check() {
  if [[ "$2" == "$3" ]]; then
    record_result "$1" pass "${4:-$2}"
  else
    fail_msg "$1: expected '$3', got '$2'"
    record_result "$1" fail "expected '$3', got '$2' ${4:-}"
  fi
}
out() { printf '%s' "$OUT" | jq -r "$1"; }
# stderr carries JVM notices too: the error object is its JSON line.
err() { printf '%s\n' "$ERR" | grep '^{' | tail -n 1 | jq -r "$1" 2>/dev/null; }

# The command line as logged, with any test key replaced (A6 passes one as an argument on purpose).
shown() {
  local line="$*"
  for key in ${GOOD_KEY:-} ${BAD_KEY:-} ${OR_KEY:-}; do line=${line//"$key"/<key>}; done
  printf '%s' "$line"
}

banner "amy agent + schedule headless ($RUN_TS)"
preflight_dm
command -v python3 >/dev/null || { fail_msg "python3 is needed for the stub provider"; exit 1; }
start_local_relay

# --- secrets: three throwaway keys, only ever in files -------------------------------------
rand() { od -An -tx1 -N12 /dev/urandom | tr -d ' \n'; }
GOOD_KEY="sk-stub-good-$(rand)"
BAD_KEY="sk-stub-bad-$(rand)"
OR_KEY="sk-or-v1-$(rand)$(rand)"
umask 077
printf '%s\n' "$GOOD_KEY" >"$STATE_DIR/good.key"
printf '%s\n' "$BAD_KEY" >"$STATE_DIR/bad.key"
printf '%s' "$OR_KEY" >"$STATE_DIR/or.key"
umask 022

step "starting the stub provider on $STUB_URL"
python3 "$SCRIPT_DIR/stub-provider.py" "$STUB_PORT" "$STATE_DIR/good.key" 2>"$LOG_DIR/stub.log" &
STUB_PID=$!
for _ in $(seq 1 40); do curl -s -o /dev/null "$STUB_URL/models" && break; sleep 0.25; done
check S0.stub "$(curl -s -o /dev/null -w '%{http_code}' "$STUB_URL/models")" 401 "the stub refuses a request with no key"

ensure_identity_for A
HOME="$STATE_DIR" "$AMY_BIN" --account A --secret-backend plaintext --json relay add "$RELAY_URL" >>"$LOG_FILE" 2>&1
HOME="$STATE_DIR" "$AMY_BIN" --account B --secret-backend plaintext --json init >>"$LOG_FILE" 2>&1

# ============================================================================================
banner "A — amy agent"

run_as B agent show
check A1.no_relays.rc "$RC" 1
check A1.no_relays.code "$(err .error)" no_relays

run agent show
check A2.empty.rc "$RC" 0
check A2.empty.count "$(out .count)" 0

run agent set "$STUB_URL" --key-file "$STATE_DIR/good.key"
check A3.set.rc "$RC" 0
check A3.set.changed "$(out .changed)" true
check A3.set.default "$(out '.keys[0].default')" true
check A3.set.masked "$(out '.keys[0].key_masked')" "sk-stub-…${GOOD_KEY: -4}" "its prefix and the last 4, a third of the key at most"
check A3.set.published "$(out '.published_to | length')" 1

run_stdin "$STATE_DIR/or.key" agent set https://openrouter.ai/api/v1 --key-stdin
check A4.stdin.rc "$RC" 0
check A4.stdin.order "$(out '[.keys[].provider] | join(",")')" "127.0.0.1:$STUB_PORT,OpenRouter" "a new key goes last"
check A4.stdin.masked "$(out '.keys[1].key_masked')" "sk-or-v1-…${OR_KEY: -4}"

run agent set http://localhost:11434/v1 --no-key
check A5.nokey.rc "$RC" 0
check A5.nokey.has_key "$(out '.keys[2].has_key')" false
check A5.nokey.local "$(out '.keys[2].local')" true

# Argument checks: exit 2, nothing written, and the key never echoed.
run agent set http://runner.example:8080/v1 --key-file "$STATE_DIR/good.key"
check A6.https.rc "$RC" 2
check A6.https.code "$(err .error)" bad_args
run agent set https://openrouter.ai/api/v1 "$OR_KEY"
check A6.positional_key.rc "$RC" 2
run agent set https://openrouter.ai/api/v1
check A6.no_key_flag.rc "$RC" 2 "an empty key only with --no-key"
run agent set https://openrouter.ai/api/v1 --no-key
check A6.needs_key.rc "$RC" 2
run agent set "$STUB_URL" --key-file "$STATE_DIR/good.key" --key-stdin
check A6.both.rc "$RC" 2
run agent set "$STUB_URL" --key-file "$STATE_DIR/missing.key"
check A6.unreadable_file.rc "$RC" 2
# A value-less --key-file must never fall back to "no key" and wipe the saved one.
run agent set "$STUB_URL" --key-file
check A6.bare_key_file.rc "$RC" 2
check A6.bare_key_file.code "$(err .error)" bad_args
run agent set "$STUB_URL" --key-stdin=x
check A6.key_stdin_value.rc "$RC" 2
run agent set --key-stdin "$STUB_URL" extra
check A6.key_stdin_then_value.rc "$RC" 2
: >"$STATE_DIR/empty.key"
run agent set "$STUB_URL" --key-file "$STATE_DIR/empty.key"
check A6.empty_key_file.rc "$RC" 2
# A key typed where a URL or a path goes is never printed back (the K scan checks every byte).
run agent set "$OR_KEY" --key-file "$STATE_DIR/good.key"
check A6.key_as_url.rc "$RC" 2
run agent set "http://$OR_KEY@runner.example/v1" --key-file "$STATE_DIR/good.key"
check A6.key_in_userinfo.rc "$RC" 2
run agent set "$STUB_URL" --key-file "$OR_KEY"
check A6.key_as_path.rc "$RC" 2
run agent default "$OR_KEY"
check A6.key_as_default.code "$(err .error)" not_found
run agent remove "$OR_KEY"
check A6.key_as_remove.code "$(err .error)" not_found
run agent test "$OR_KEY"
check A6.key_as_test.code "$(err .error)" not_found
run agent show
check A6.unchanged "$(out .count)" 3
check A6.key_kept "$(out '.keys[0].key_masked')" "sk-stub-…${GOOD_KEY: -4}"

run agent test
check A7.ok.rc "$RC" 0
check A7.ok.result "$(out .result)" ok
check A7.ok.models "$(out .model_count)" 2

run agent set "$STUB_URL" --key-file "$STATE_DIR/bad.key"
check A8.replace.rc "$RC" 0
check A8.replace.in_place "$(out '[.keys[].provider] | join(",")')" "127.0.0.1:$STUB_PORT,OpenRouter,Ollama" "replaced where it stood"
run agent test "$STUB_URL"
check A8.rejected.rc "$RC" 1
check A8.rejected.code "$(err .error)" key_rejected
check A8.rejected.result "$(err .result)" rejected
check A8.rejected.status "$(err .status)" 401

run agent set http://127.0.0.1:1/v1 --no-key
run agent test http://127.0.0.1:1/v1
check A9.unreachable.rc "$RC" 1
check A9.unreachable.code "$(err .error)" provider_unreachable
run agent test https://nowhere.example/v1
check A9.not_found.rc "$RC" 1
check A9.not_found.code "$(err .error)" not_found

run agent default https://openrouter.ai/api/v1/
check A10.default.rc "$RC" 0
check A10.default.first "$(out '.keys[0].provider')" OpenRouter
run agent default https://openrouter.ai/api/v1
check A10.default_again.changed "$(out .changed)" false

run agent remove http://127.0.0.1:1/v1
check A11.remove.rc "$RC" 0
check A11.remove.count "$(out .count)" 3
run agent remove http://127.0.0.1:1/v1
check A11.remove_again.rc "$RC" 1
check A11.remove_again.code "$(err .error)" not_found

run agent show
check A12.show.json "$(out '[.keys[0] | keys[]] | join(",")')" "default,has_key,key_masked,local,provider,url,usable"
check A12.show.order "$(out '[.keys[].provider] | join(",")')" "OpenRouter,127.0.0.1:$STUB_PORT,Ollama"

# The same runs with --verbose: their progress logs are scanned for the key too (K1).
run_verbose agent show
check A12.verbose.rc "$RC" 0
run_verbose agent test "$STUB_URL"
run_verbose agent default "$STUB_URL"
check A12.verbose_default.rc "$RC" 0
run_verbose agent default https://openrouter.ai/api/v1

# The kind 12000 the relay holds is ciphertext.
HOME="$STATE_DIR" "$AMY_BIN" --account A --secret-backend plaintext --json fetch --kind 12000 --author "$A_HEX" --relay "$RELAY_URL" >>"$ALL_OUT" 2>&1
run fetch --kind 12000 --author "$A_HEX" --relay "$RELAY_URL"
check A13.on_relay.rc "$RC" 0

run agent clear
check A14.clear.rc "$RC" 0
check A14.clear.count "$(out .count)" 0
run agent show
check A14.cleared "$(out .count)" 0

run agent frobnicate
check A15.unknown_verb.rc "$RC" 2
run agent
check A15.no_verb.rc "$RC" 2

# Another client wrote a key over plain http to another machine, first. NIP-FA's readers ignore
# it: amy lists it, never as the default, and never sends it.
CLEAR_KEY="sk-cleartext-$(rand)"
KEYS_JSON='[["key","'"$CLEAR_KEY"'","http://evil.example/v1"],["key","","http://localhost:11434/v1"]]'
CT=$(HOME="$STATE_DIR" "$AMY_BIN" --account A --secret-backend plaintext --json encrypt --to "$A_HEX" "$KEYS_JSON" 2>>"$LOG_FILE" | jq -r .ciphertext)
EV=$(HOME="$STATE_DIR" "$AMY_BIN" --account A --secret-backend plaintext --json event --kind 12000 --content "$CT" --created-at $(( $(date +%s) + 5 )) 2>>"$LOG_FILE" | jq -c .event)
printf '%s\n' "$EV" | HOME="$STATE_DIR" "$AMY_BIN" --account A --secret-backend plaintext --json publish --file - --relay "$RELAY_URL" >>"$LOG_FILE" 2>&1
run agent show
check A16.ignored.listed "$(out .count)" 2
check A16.ignored.not_default "$(out '.keys[0].default')" false
check A16.ignored.usable "$(out '.keys[0].usable')" false
check A16.ignored.default_is_local "$(out '.keys[1].default')" true
run agent test
check A16.test_skips_it.provider "$(err .provider)" Ollama "the default is the local runner (not running here), never the cleartext key"
# The rewrite keeps the tag as read.
run agent remove http://localhost:11434/v1
check A16.kept_on_rewrite "$(out '.keys[0].url')" http://evil.example/v1
run agent clear

# More than half of the write relays must answer the read: with a dead one beside the local
# relay, 1 of 2 isn't enough, and amy says so at once (not a timeout).
HOME="$STATE_DIR" "$AMY_BIN" --account A --secret-backend plaintext --json relay outbox add ws://127.0.0.1:1 >>"$LOG_FILE" 2>&1
STARTED=$(date +%s)
run agent set "$STUB_URL" --key-file "$STATE_DIR/good.key"
check A17.no_quorum.rc "$RC" 1
check A17.no_quorum.code "$(err .error)" no_quorum
check A17.no_quorum.answered "$(err .answered)" 1
check A17.no_quorum.fast "$(( $(date +%s) - STARTED < 20 ))" 1 "an error, not the 23 s timeout"
HOME="$STATE_DIR" "$AMY_BIN" --account A --secret-backend plaintext --json relay outbox remove ws://127.0.0.1:1 >>"$LOG_FILE" 2>&1
run agent show
check A17.nothing_written "$(out .count)" 0

# ============================================================================================
banner "S — amy schedule"

run schedule list
check S1.empty.rc "$RC" 0
check S1.empty.count "$(out .count)" 0
run schedule show
check S2.missing.rc "$RC" 1
check S2.missing.code "$(err .error)" not_found

run schedule set --cron "0 7 * * *" --tz America/New_York
check S3.set.rc "$RC" 0
check S3.set.changed "$(out .changed)" true
check S3.set.d "$(out .d)" observer
check S3.set.task "$(out .task)" observer
check S3.set.tz "$(out .tz)" America/New_York
check S3.set.next "$(out '.next_run > now - 1')" true
check S3.set.paused "$(out .paused)" false
SET_AT=$(out .set_at)

run schedule set digest --cron "30  6 * * 1-5"
check S4.local_zone.rc "$RC" 0
check S4.local_zone.tz "$(out .tz)" Europe/Lisbon "this machine's zone"
check S4.local_zone.cron "$(out .cron)" "30 6 * * 1-5"
check S4.local_zone.task "$(out .task)" digest

sleep 1
AMY_TZ=Asia/Tokyo run schedule set --cron "0 8 * * *"
check S5.keeps_zone.rc "$RC" 0
check S5.keeps_zone.tz "$(out .tz)" America/New_York "a new time keeps the task's zone"
check S5.keeps_zone.set_at "$(( $(out .set_at) > SET_AT ))" 1
run schedule set --cron "0 8 * * *"
check S6.unchanged.rc "$RC" 0
check S6.unchanged.changed "$(out .changed)" false

run schedule set --cron "61 * * * *"
check S7.bad_cron.rc "$RC" 2
run schedule set --cron "0 7 * * *" --tz +05:00
check S7.offset_zone.rc "$RC" 2
run schedule set --cron "0 7 * * *" --tz Mars/Olympus
check S7.unknown_zone.rc "$RC" 2
run schedule set
check S7.no_cron.rc "$RC" 2

run schedule pause
check S8.pause.rc "$RC" 0
check S8.pause.paused "$(out .paused)" true
check S8.pause.next "$(out .next_run)" null
run schedule pause
check S8.pause_again.changed "$(out .changed)" false
sleep 1
run schedule resume
check S8.resume.paused "$(out .paused)" false
check S8.resume.set_at "$(( $(out .set_at) > SET_AT ))" 1 "resuming restarts the schedule from now"

run schedule list
check S9.list.rc "$RC" 0
check S9.list.count "$(out .count)" 2
check S9.list.ds "$(out '[.tasks[].d] | join(",")')" "digest,observer"
check S9.list.readable "$(out '[.tasks[].readable] | all')" true
check S9.list.quorum "$(out .quorum)" true
check S9.list.json "$(out '[.tasks[0] | keys[]] | join(",")')" "claim,created_at,cron,cron_readable,d,event_id,missed,next_run,paused,ran,readable,set_at,task,tz"

run schedule show observer
check S10.show.cron "$(out .cron)" "0 8 * * *"
check S10.show.quorum "$(out .quorum)" true

run schedule delete digest
check S11.delete.rc "$RC" 0
check S11.delete.deleted "$(out .deleted)" true
check S11.delete.address "$(out .address)" "34817:$A_HEX:digest"
run schedule show digest
check S11.gone.rc "$RC" 1
check S11.gone.code "$(err .error)" not_found
run schedule list
check S11.list_after.count "$(out .count)" 1

run schedule pause nosuch
check S12.not_found.rc "$RC" 1
check S12.not_found.code "$(err .error)" not_found
run_as B schedule list
check S12.no_relays.code "$(err .error)" no_relays
run schedule frobnicate
check S12.unknown_verb.rc "$RC" 2

# --paused on a task that already runs pauses it.
run schedule set --cron "0 9 * * *" --paused
check S14.paused_existing.rc "$RC" 0
check S14.paused_existing.paused "$(out .paused)" true
check S14.paused_existing.published "$(out '.published_to | length')" 1
run schedule resume
# A boolean before the positional doesn't swallow it: this edits `weekly`, not `observer`.
run schedule set --paused weekly --cron "0 10 * * 1" --tz UTC
check S15.flag_first.d "$(out .d)" weekly
check S15.flag_first.paused "$(out .paused)" true
run schedule show observer
check S15.observer_untouched.cron "$(out .cron)" "0 9 * * *"
check S15.observer_untouched.paused "$(out .paused)" false
run schedule set weekly --cron "0 10 * * 1" --paused=yes
check S15.paused_value.rc "$RC" 2

# Deleted and set again in the same second: the new task is newer than the deletion (NIP-09).
run schedule delete weekly
check S16.deleted.rc "$RC" 0
run schedule set weekly --cron "0 11 * * 1" --tz UTC
check S16.again.rc "$RC" 0
run schedule show weekly
check S16.again.shown "$(out .cron)" "0 11 * * 1"
run schedule list
check S16.again.listed "$(out '[.tasks[].d] | join(",")')" "observer,weekly"

# The default output is text, not JSON.
TEXT=$(HOME="$STATE_DIR" "$AMY_BIN" --account A --secret-backend plaintext schedule show 2>>"$ALL_OUT")
printf '%s\n' "$TEXT" >>"$ALL_OUT"
check S13.text "$([[ "$TEXT" == *"cron"* && "$TEXT" != "{"* ]] && echo yes)" yes

# ============================================================================================
banner "K — no key anywhere, not even in part"
# Every place a key could leak to: stdout and stderr (--verbose runs included), the run log, the
# stub's and the relay's logs, and everything amy keeps under $HOME/.amy. A file that should be
# there and isn't fails the check: an empty scan proves nothing.
SCANNED=("$ALL_OUT" "$LOG_FILE" "$LOG_DIR/stub.log" "$RELAY_DATA/logs/stdout.log" "$RELAY_DATA/logs/stderr.log")
MISSING=0
for f in "${SCANNED[@]}"; do [[ -f "$f" ]] || { fail_msg "missing $f"; MISSING=$((MISSING + 1)); }; done
[[ -d "$STATE_DIR/.amy" ]] || { fail_msg "missing $STATE_DIR/.amy"; MISSING=$((MISSING + 1)); }
check K0.files_scanned "$MISSING" 0
# The secret part of each key (after its provider prefix): no 8 characters of it in a row may
# show anywhere; a mask shows the last 4 only.
leaks_of() {
  local key="$1" secret="${1#sk-or-v1-}"
  secret="${secret#sk-stub-good-}"; secret="${secret#sk-stub-bad-}"; secret="${secret#sk-cleartext-}"
  local found=0 i
  for (( i = 0; i + 8 <= ${#secret}; i++ )); do
    local part="${secret:i:8}"
    if grep -qaF -- "$part" "${SCANNED[@]}" 2>/dev/null || grep -rqaF -- "$part" "$STATE_DIR/.amy" 2>/dev/null; then found=$((found + 1)); fi
  done
  printf '%s' "$found"
}
LEAKS=0
for key in "$GOOD_KEY" "$BAD_KEY" "$OR_KEY" "$CLEAR_KEY"; do
  n=$(leaks_of "$key")
  [[ "$n" != 0 ]] && fail_msg "a key leaked: $n of its 8-character windows found"
  LEAKS=$((LEAKS + n))
done
check K1.no_key_in_output "$LEAKS" 0 "stdout, stderr, --verbose, the logs and ~/.amy, in part or whole"
# Masks show at most a third of a key.
MASKS_OK=1
while read -r mask; do
  [[ -z "$mask" ]] && continue
  shown="${mask//…/}"
  (( ${#shown} > 13 )) && MASKS_OK=0
done < <(grep -ahoE '"key_masked":"[^"]*"' "$ALL_OUT" | sed -E 's/.*:"([^"]*)"/\1/' | sort -u)
check K2.masks_short "$MASKS_OK" 1
