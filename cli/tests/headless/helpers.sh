# shellcheck shell=bash
#
# helpers.sh — thin wrappers that keep the per-test code tight.

# --- amy wrapper -------------------------------------------------------------
# Tests isolate by overriding $HOME for the amy subprocess; amy reads the
# env var directly (not Java's `user.home`, which JDK 21 pulls from
# /etc/passwd) and treats $HOME/.amy/ as its tree. No --data-dir flag,
# no test-only escape hatch — production code path, fresh every run.
#
# `--secret-backend=plaintext` keeps these throwaway runs headless —
# the default `auto` would try the OS keychain (not available in CI) and
# then ask for a NIP-49 passphrase. Plaintext still writes 0600-owner-only.
#
# `--json` opts into amy's machine-readable output (the harness parses it
# with jq); the default human-text output is for terminal use.
amy_a() { HOME="$STATE_DIR" "$AMY_BIN" --account A --secret-backend plaintext --json "$@"; }

# Run amy, log stderr, surface JSON on stdout, remember last result.
amy_json() {
  local out rc
  # Capture the status separately: inside `if ! cmd; then`, `$?` is the status
  # of the negation (always 0), so the message reported "exit 0" for every
  # failure and told a reader nothing about what went wrong.
  out=$(amy_a "$@" 2>>"$LOG_FILE"); rc=$?
  if [[ $rc -ne 0 ]]; then
    fail_msg "amy $*: exit $rc (see $LOG_FILE)"
    printf '%s\n' "$out" >>"$LOG_FILE"
    return 1
  fi
  printf '%s' "$out"
}

# Convenience extractors — the CLI emits one JSON object per success so we can
# jq with impunity.
amy_field() {
  # usage: amy_field '.group_id' init [args...]
  local path="$1"; shift
  amy_json "$@" | jq -r "$path"
}

# --- assertion helpers -------------------------------------------------------
# Assert a substring is present in a variable; append a failed result on miss
# and return 1. Positive case just logs.
assert_contains() {
  local haystack="$1" needle="$2" test_id="$3" note="${4:-}"
  if [[ "$haystack" == *"$needle"* ]]; then
    info "assertion hit: $test_id contains \"$needle\""
    return 0
  fi
  fail_msg "$test_id: missing \"$needle\" (${note:-no note})"
  info "actual: $haystack"
  record_result "$test_id" fail "${note:-missing \"$needle\"}"
  return 1
}

# Assert two strings are equal (leniently trimmed).
assert_eq() {
  local actual="$1" expected="$2" test_id="$3" note="${4:-}"
  if [[ "${actual// /}" == "${expected// /}" ]]; then
    info "assertion hit: $test_id \"$actual\" == \"$expected\""
    return 0
  fi
  fail_msg "$test_id: expected \"$expected\", got \"$actual\" (${note:-})"
  record_result "$test_id" fail "${note:-mismatch}"
  return 1
}

# --- embedded relay (amy serve → geode) --------------------------------------
# Every relay-backed harness talks to ONE loopback relay, and that relay is
# `amy serve` — i.e. geode, the relay this repo ships — booted from the amy
# binary the harness already built. No Rust toolchain, no clone, no cargo
# build, no external relay binary: the relay under test is part of the
# product, so a harness run exercises the same server code `amy serve`
# and the standalone geode distribution run in production.
#
# Callers set (before sourcing or at least before calling):
#   AMY_BIN      amy launcher (built via `./gradlew :cli:installDist`)
#   RELAY_HOST   host clients connect to. The harnesses use 127.0.0.2 for
#                parity with each other; note that Quartz's isLocalHost()
#                treats all of 127.0.0.0/8 as loopback, so it does NOT
#                survive the NIP-17 / NIP-65 relay-list parsers any better
#                than 127.0.0.1 does.
#   RELAY_BIND   optional bind address; defaults to $RELAY_HOST. Set to
#                0.0.0.0 when a device on the LAN must reach the relay.
#   RELAY_PORT   listen port
#   RELAY_URL    ws://$RELAY_HOST:$RELAY_PORT
#   RELAY_DATA   scratch dir for the relay's own $HOME, pid file and logs
#
# The relay process runs as its own amy account ("relay") inside its own
# $HOME under $RELAY_DATA, so its identity and store never mix with the
# test identities. The store is in-memory (amy serve's default): every run
# starts from an empty relay, matching the state wipe the harnesses do.
start_local_relay() {
  banner "Starting embedded relay (amy serve / geode) on $RELAY_URL"
  local relay_home="$RELAY_DATA/home"
  local bind="${RELAY_BIND:-$RELAY_HOST}"
  mkdir -p "$relay_home" "$RELAY_DATA/logs"

  [[ -x "$AMY_BIN" ]] || { fail_msg "amy not found at $AMY_BIN — build it with ./gradlew :cli:installDist"; exit 1; }

  # Abort early if something else is already bound to the port — failing
  # with a clear error beats a mysterious-looking daemon stall later.
  # bash's /dev/tcp probe needs no `ss`/`lsof`; a refused connect on
  # loopback returns immediately.
  if (exec 3<>"/dev/tcp/$RELAY_HOST/$RELAY_PORT") 2>/dev/null; then
    fail_msg "port $RELAY_PORT already in use on $RELAY_HOST — pass --port N or free it"
    exit 1
  fi

  # `amy serve` resolves its admin pubkey from the account, so the relay
  # needs an identity of its own. Idempotent across --reuse-state runs.
  if [[ ! -d "$relay_home/.amy/relay" ]]; then
    HOME="$relay_home" "$AMY_BIN" --account relay --secret-backend plaintext --json init \
      >"$RELAY_DATA/logs/init.log" 2>&1 \
      || { fail_msg "amy init failed for the relay account (see $RELAY_DATA/logs/init.log)"; exit 1; }
  fi

  nohup env HOME="$relay_home" "$AMY_BIN" --account relay --secret-backend plaintext \
    serve --host "$bind" --port "$RELAY_PORT" \
    >"$RELAY_DATA/logs/stdout.log" 2>"$RELAY_DATA/logs/stderr.log" &
  echo "$!" > "$RELAY_DATA/pid"
  step "relay pid $(cat "$RELAY_DATA/pid"); waiting for $RELAY_URL …"

  # Readiness = the NIP-11 document answers on the same port. geode serves
  # it on a plain GET with `Accept: application/nostr+json` (anything else
  # gets a 426 hint, which curl -f would treat as failure).
  local deadline=$(( $(date +%s) + 60 ))
  while [[ $(date +%s) -lt $deadline ]]; do
    if curl -sSf -m 1 -H 'Accept: application/nostr+json' \
        "http://$RELAY_HOST:$RELAY_PORT/" >/dev/null 2>&1; then
      info "relay up"
      return 0
    fi
    if ! kill -0 "$(cat "$RELAY_DATA/pid")" 2>/dev/null; then
      break
    fi
    sleep 0.5
  done
  fail_msg "relay never came up (see $RELAY_DATA/logs/stderr.log)"
  tail -n 40 "$RELAY_DATA/logs/stderr.log" 2>/dev/null | sed 's/^/  /' >&2 || true
  exit 1
}

stop_local_relay() {
  local pid_file="$RELAY_DATA/pid"
  [[ -f "$pid_file" ]] || return 0
  local pid; pid=$(cat "$pid_file" 2>/dev/null || echo "")
  if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
    info "stopping relay pid $pid"
    kill "$pid" 2>/dev/null || true
    sleep 1
    kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$pid_file"
}

# --- wn-side pollers (delegates to lib.sh) -----------------------------------
# Both exist in lib.sh already; this file only adds headless-specific niceties.
