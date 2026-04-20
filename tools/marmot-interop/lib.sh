# shellcheck shell=bash
#
# lib.sh — helpers for marmot-interop.sh
#
# All functions assume the parent script has set:
#   STATE_DIR        absolute path to state/ directory
#   LOG_FILE         absolute path to current run's log file
#   RESULTS_FILE     absolute path to results table (tab-separated)
#   WN_BIN, WND_BIN  absolute paths to wn / wnd binaries
#   B_SOCKET, C_SOCKET  socket paths for the two daemons

set -u

# ------- colors --------------------------------------------------------------
if [[ -t 1 ]]; then
    C_RESET=$'\033[0m'
    C_BOLD=$'\033[1m'
    C_RED=$'\033[31m'
    C_GREEN=$'\033[32m'
    C_YELLOW=$'\033[33m'
    C_BLUE=$'\033[34m'
    C_CYAN=$'\033[36m'
    C_GREY=$'\033[90m'
else
    C_RESET=""; C_BOLD=""; C_RED=""; C_GREEN=""
    C_YELLOW=""; C_BLUE=""; C_CYAN=""; C_GREY=""
fi

# ------- logging primitives --------------------------------------------------
log() { printf '%s\n' "$*" | tee -a "$LOG_FILE" >&2; }

banner() {
    printf '\n%s%s================================================================%s\n' \
        "$C_BOLD" "$C_BLUE" "$C_RESET" >&2
    printf '%s%s  %s%s\n' "$C_BOLD" "$C_BLUE" "$1" "$C_RESET" >&2
    printf '%s%s================================================================%s\n\n' \
        "$C_BOLD" "$C_BLUE" "$C_RESET" >&2
    printf '\n=== %s ===\n' "$1" >>"$LOG_FILE"
}

step() { printf '%s>%s %s\n' "$C_CYAN" "$C_RESET" "$1" >&2; printf '> %s\n' "$1" >>"$LOG_FILE"; }
info() { printf '%s  %s%s\n' "$C_GREY" "$1" "$C_RESET" >&2; printf '  %s\n' "$1" >>"$LOG_FILE"; }
warn() { printf '%s!%s %s\n' "$C_YELLOW" "$C_RESET" "$1" >&2; printf '! %s\n' "$1" >>"$LOG_FILE"; }
pass_msg() { printf '%s\u2713 PASS%s %s\n' "$C_GREEN" "$C_RESET" "$1" >&2; printf 'PASS %s\n' "$1" >>"$LOG_FILE"; }
fail_msg() { printf '%s\u2717 FAIL%s %s\n' "$C_RED" "$C_RESET" "$1" >&2;   printf 'FAIL %s\n' "$1" >>"$LOG_FILE"; }
skip_msg() { printf '%s- SKIP%s %s\n' "$C_YELLOW" "$C_RESET" "$1" >&2;     printf 'SKIP %s\n' "$1" >>"$LOG_FILE"; }

# ------- result tracking ------------------------------------------------------
# Each test calls record_result <test-id> <pass|fail|skip> [note]
record_result() {
    local test_id="$1" status="$2" note="${3:-}"
    printf '%s\t%s\t%s\n' "$test_id" "$status" "$note" >>"$RESULTS_FILE"
}

print_summary() {
    local total=0 passed=0 failed=0 skipped=0
    printf '\n%s%s================================================================%s\n' \
        "$C_BOLD" "$C_BLUE" "$C_RESET"
    printf '%s%s  RESULTS%s\n' "$C_BOLD" "$C_BLUE" "$C_RESET"
    printf '%s%s================================================================%s\n\n' \
        "$C_BOLD" "$C_BLUE" "$C_RESET"

    if [[ ! -s "$RESULTS_FILE" ]]; then
        printf '  (no tests ran)\n\n'
        return
    fi

    while IFS=$'\t' read -r tid status note; do
        total=$((total+1))
        case "$status" in
            pass) passed=$((passed+1));   col="$C_GREEN";  sym="\u2713" ;;
            fail) failed=$((failed+1));   col="$C_RED";    sym="\u2717" ;;
            skip) skipped=$((skipped+1)); col="$C_YELLOW"; sym="-" ;;
            *)    col="$C_GREY";          sym="?" ;;
        esac
        printf '  %b %s%-44s%s %s\n' "$sym" "$col" "$tid" "$C_RESET" "$note"
    done <"$RESULTS_FILE"

    printf '\n  %s%d passed%s, %s%d failed%s, %s%d skipped%s   (of %d)\n\n' \
        "$C_GREEN" "$passed" "$C_RESET" \
        "$C_RED"   "$failed" "$C_RESET" \
        "$C_YELLOW" "$skipped" "$C_RESET" \
        "$total"
    printf '  full log: %s\n\n' "$LOG_FILE"
}

# ------- human interaction ---------------------------------------------------

# Pretty-print multi-line instructions and wait for <Enter>.
prompt_human() {
    printf '\n%s%s---- DO THIS IN AMETHYST ----%s\n' "$C_BOLD" "$C_YELLOW" "$C_RESET" >&2
    printf '%s%s%s\n' "$C_YELLOW" "$1" "$C_RESET" >&2
    printf '%s%s-----------------------------%s\n' "$C_BOLD" "$C_YELLOW" "$C_RESET" >&2
    printf '%s[Press Enter to continue]%s ' "$C_CYAN" "$C_RESET" >&2
    read -r _
    printf '\n' >>"$LOG_FILE"
    printf 'HUMAN-PROMPT: %s\n' "$1" >>"$LOG_FILE"
}

# Ask the operator to confirm a UI rendering result.
# Returns 0 on pass, 1 on fail, 2 on skip.
confirm() {
    local q="$1" ans
    while true; do
        printf '%s? %s%s  [p]ass / [f]ail / [s]kip: ' "$C_CYAN" "$q" "$C_RESET" >&2
        read -r ans
        case "$(printf '%s' "$ans" | tr '[:upper:]' '[:lower:]')" in
            p|pass) printf 'CONFIRM PASS: %s\n' "$q" >>"$LOG_FILE"; return 0 ;;
            f|fail) printf 'CONFIRM FAIL: %s\n' "$q" >>"$LOG_FILE"; return 1 ;;
            s|skip) printf 'CONFIRM SKIP: %s\n' "$q" >>"$LOG_FILE"; return 2 ;;
            *) printf 'enter p, f, or s\n' >&2 ;;
        esac
    done
}

# ------- wn wrappers ---------------------------------------------------------
wn_b() { "$WN_BIN" --socket "$B_SOCKET" "$@"; }
wn_c() { "$WN_BIN" --socket "$C_SOCKET" "$@"; }

# Run wn with --json and pipe through jq; aborts the test on jq parse failure.
wn_b_json() { wn_b --json "$@"; }
wn_c_json() { wn_c --json "$@"; }

# ------- assertions ----------------------------------------------------------
expect_eq() {
    local actual="$1" expected="$2" what="${3:-value}"
    if [[ "$actual" == "$expected" ]]; then
        info "$what == \"$expected\""
        return 0
    fi
    fail_msg "$what mismatch: expected \"$expected\", got \"$actual\""
    return 1
}

expect_contains() {
    local haystack="$1" needle="$2" what="${3:-output}"
    if [[ "$haystack" == *"$needle"* ]]; then
        info "$what contains \"$needle\""
        return 0
    fi
    fail_msg "$what missing \"$needle\""
    info "actual: $haystack"
    return 1
}

# ------- polling helpers -----------------------------------------------------

# wait_for_invite <B|C> <timeout-seconds>
# Echoes the first new group_id that appears in `wn groups invites --json`.
# Returns 1 on timeout.
wait_for_invite() {
    local who="$1" timeout="${2:-60}" deadline gid
    deadline=$(( $(date +%s) + timeout ))
    while [[ $(date +%s) -lt $deadline ]]; do
        if [[ "$who" == "B" ]]; then
            gid=$(wn_b_json groups invites 2>/dev/null \
                  | jq -r '.[0].group_id // .[0].mls_group_id // empty' 2>/dev/null || true)
        else
            gid=$(wn_c_json groups invites 2>/dev/null \
                  | jq -r '.[0].group_id // .[0].mls_group_id // empty' 2>/dev/null || true)
        fi
        if [[ -n "${gid:-}" ]]; then
            printf '%s\n' "$gid"
            return 0
        fi
        sleep 2
    done
    return 1
}

# wait_for_message <B|C> <group_id> <substring> <timeout>
# Returns 0 if a message containing the substring shows up via wn messages list.
wait_for_message() {
    local who="$1" gid="$2" needle="$3" timeout="${4:-30}"
    local deadline payload
    deadline=$(( $(date +%s) + timeout ))
    while [[ $(date +%s) -lt $deadline ]]; do
        if [[ "$who" == "B" ]]; then
            payload=$(wn_b_json messages list "$gid" --limit 20 2>/dev/null || true)
        else
            payload=$(wn_c_json messages list "$gid" --limit 20 2>/dev/null || true)
        fi
        if [[ -n "${payload:-}" ]] && \
           printf '%s' "$payload" | jq -e --arg n "$needle" \
                '.[]? | select((.content // .text // "") | contains($n))' \
                >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    return 1
}

# wait_for_member <B|C> <group_id> <pubkey-hex> <timeout>
wait_for_member() {
    local who="$1" gid="$2" pubkey="$3" timeout="${4:-30}"
    local deadline payload
    deadline=$(( $(date +%s) + timeout ))
    while [[ $(date +%s) -lt $deadline ]]; do
        if [[ "$who" == "B" ]]; then
            payload=$(wn_b_json groups members "$gid" 2>/dev/null || true)
        else
            payload=$(wn_c_json groups members "$gid" 2>/dev/null || true)
        fi
        if printf '%s' "${payload:-}" | jq -e --arg p "$pubkey" \
               '.[]? | select((.pubkey // .public_key // "") == $p)' \
               >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done
    return 1
}

# ------- output parsing ------------------------------------------------------
# `wn` prints either JSON (with --json) or a yaml-ish "key: value" pretty form.
# This helper extracts the pubkey (npub) from whichever form we got.
extract_pubkey() {
    local raw="$1" v=""
    # JSON: single object
    v=$(printf '%s' "$raw" | jq -r '.pubkey // .npub // .public_key // empty' 2>/dev/null || true)
    if [[ -n "$v" && "$v" != "null" ]]; then printf '%s' "$v"; return; fi
    # JSON: array of accounts (whoami may return a list)
    v=$(printf '%s' "$raw" | jq -r '.[0].pubkey // .[0].npub // .[0].public_key // empty' 2>/dev/null || true)
    if [[ -n "$v" && "$v" != "null" ]]; then printf '%s' "$v"; return; fi
    # yaml-ish "pubkey: npub1..."
    v=$(printf '%s\n' "$raw" | sed -n 's/^[[:space:]]*pubkey[[:space:]]*:[[:space:]]*//p' | head -n1)
    if [[ -n "$v" ]]; then printf '%s' "$v"; return; fi
    # yaml-ish "npub: npub1..." (alternative key)
    v=$(printf '%s\n' "$raw" | sed -n 's/^[[:space:]]*npub[[:space:]]*:[[:space:]]*//p' | head -n1)
    if [[ -n "$v" ]]; then printf '%s' "$v"; return; fi
}

# Best-effort npub -> hex conversion via `wn users show`. If the lookup fails
# (e.g. --json not supported or user not resolvable), returns the input
# unchanged — downstream comparisons tolerate either form.
npub_to_hex() {
    local npub="$1" raw hex
    raw=$(wn_b --json users show "$npub" 2>/dev/null || true)
    hex=$(printf '%s' "$raw" | jq -r '.pubkey // .public_key // .hex // empty' 2>/dev/null || true)
    if [[ -z "$hex" || "$hex" == "null" ]]; then
        # fallback to yaml-ish output
        raw=$(wn_b users show "$npub" 2>/dev/null || true)
        hex=$(printf '%s\n' "$raw" | sed -n 's/^[[:space:]]*\(public_key\|hex\)[[:space:]]*:[[:space:]]*//p' | head -n1)
    fi
    if [[ -n "$hex" && "$hex" != "null" ]]; then
        printf '%s' "$hex"
    else
        printf '%s' "$npub"
    fi
}

# ------- state file ----------------------------------------------------------
# Simple key=value store at $STATE_DIR/run.env
STATE_FILE_RUNTIME="${STATE_DIR}/run.env"

save_state() {
    local key="$1" value="$2"
    : >"${STATE_FILE_RUNTIME}.tmp"
    if [[ -f "$STATE_FILE_RUNTIME" ]]; then
        grep -v "^${key}=" "$STATE_FILE_RUNTIME" >>"${STATE_FILE_RUNTIME}.tmp" || true
    fi
    printf '%s=%s\n' "$key" "$value" >>"${STATE_FILE_RUNTIME}.tmp"
    mv "${STATE_FILE_RUNTIME}.tmp" "$STATE_FILE_RUNTIME"
}

load_state() {
    local key="$1"
    [[ -f "$STATE_FILE_RUNTIME" ]] || return 1
    grep "^${key}=" "$STATE_FILE_RUNTIME" 2>/dev/null | tail -n1 | cut -d= -f2-
}

# ------- group cleanup -------------------------------------------------------
cleanup_group() {
    local gid="$1" who="${2:-B}"
    [[ -z "$gid" ]] && return 0
    if [[ "$who" == "B" ]]; then
        wn_b groups leave "$gid" >/dev/null 2>&1 || true
    else
        wn_c groups leave "$gid" >/dev/null 2>&1 || true
    fi
}
