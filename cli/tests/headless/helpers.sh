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
amy_a() { HOME="$STATE_DIR" "$AMY_BIN" --name alice --secret-backend plaintext --json "$@"; }

# Run amy, log stderr, surface JSON on stdout, remember last result.
amy_json() {
  local out
  if ! out=$(amy_a "$@" 2>>"$LOG_FILE"); then
    fail_msg "amy $*: exit $? (see $LOG_FILE)"
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

# --- wn-side pollers (delegates to lib.sh) -----------------------------------
# Both exist in lib.sh already; this file only adds headless-specific niceties.
