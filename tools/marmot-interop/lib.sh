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
wn_b() { "$WN_BIN" --socket "$B_SOCKET" ${B_NPUB:+--account "$B_NPUB"} "$@"; }
wn_c() { "$WN_BIN" --socket "$C_SOCKET" ${C_NPUB:+--account "$C_NPUB"} "$@"; }

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

# ------- JSON helpers --------------------------------------------------------

# Extract MLS group ID as lowercase hex from wn JSON output.
# Handles both formats:
#   - plain hex string (from `groups list`)
#   - {"value":{"vec":[...]}} serde struct (from `groups create`)
#   - flat byte array [n, ...] (from some responses)
# Input: JSON string via stdin; optional 2nd arg = field name (default: mls_group_id)
jq_group_id() {
    local field="${1:-mls_group_id}"
    jq -r --arg f "$field" '
        def byte2hex:
            . as $n |
            [($n / 16 | floor), ($n % 16)] |
            map(if . < 10 then (48 + .) else (87 + .) end) |
            implode;
        (.result // .) |
        .[$f] |
        if type == "string" then .
        elif (type == "object" and (.value.vec != null)) then
            [.value.vec[] | byte2hex] | join("")
        elif type == "array" then
            [.[] | byte2hex] | join("")
        else empty end
    ' 2>/dev/null || true
}

# ------- polling helpers -----------------------------------------------------

# wait_for_invite <B|C> <timeout-seconds>
# Echoes the first new group_id that appears in `wn groups invites --json`.
# Returns 1 on timeout.
#
# Also prints a heartbeat every ~10s with:
#   - elapsed/remaining time
#   - current count of pending welcomes (should be 0 until it isn't)
#   - last relevant line of wnd stderr (grep for welcome/giftwrap/subscribe/error)
# so a stalled poll gives the operator something to forward.
wait_for_invite() {
    local who="$1" timeout="${2:-60}" deadline gid start last_hb
    local wnfn data_dir
    if [[ "$who" == "B" ]]; then wnfn=wn_b; data_dir="$B_DIR"
    else                         wnfn=wn_c; data_dir="$C_DIR"; fi
    start=$(date +%s)
    deadline=$(( start + timeout ))
    last_hb=$start
    while [[ $(date +%s) -lt $deadline ]]; do
        gid=$("$wnfn" --json groups invites 2>/dev/null \
                | jq -c '.[0] // empty' 2>/dev/null | jq_group_id || true)
        if [[ -n "${gid:-}" ]]; then
            printf '%s\n' "$gid"
            return 0
        fi

        # Heartbeat every ~10s.
        local now=$(date +%s)
        if (( now - last_hb >= 10 )); then
            local elapsed=$(( now - start )) remaining=$(( deadline - now ))
            local pending
            pending=$("$wnfn" --json groups invites 2>/dev/null \
                        | jq 'length' 2>/dev/null || echo "?")
            local recent=""
            if [[ -f "$data_dir/logs/stderr.log" ]]; then
                recent=$(tail -n 200 "$data_dir/logs/stderr.log" 2>/dev/null \
                    | grep -iE 'welcome|giftwrap|gift_wrap|mls|subscribe|1059|444|error|warn' \
                    | tail -n 1 || true)
            fi
            info "$who wait_for_invite +${elapsed}s (remaining ${remaining}s)  pending=$pending  tail: ${recent:-<no relevant wnd log yet>}"
            last_hb=$now
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
    # JSON: {"result": [ {"pubkey": …}, … ]} — post-v0.2 `wn --json whoami` shape
    v=$(printf '%s' "$raw" | jq -r '.result[0].pubkey // .result[0].npub // .result[0].public_key // empty' 2>/dev/null || true)
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

# ------- daemon diagnostics --------------------------------------------------
# Dumps everything we know about a daemon's state. Called before polling for
# invites (baseline) and on every invite-poll failure so the operator can
# forward a single log file that explains what the daemon saw (or didn't).
#
# Output is tee'd to both stderr (for live viewing) and $LOG_FILE (for forward).
dump_daemon_diagnostics() {
    local who="$1" tag="${2:-diagnostics}" data_dir socket wnfn npub hex
    if [[ "$who" == "B" ]]; then
        data_dir="$B_DIR"; socket="$B_SOCKET"; wnfn=wn_b
        npub="${B_NPUB:-?}"; hex="${B_HEX:-?}"
    else
        data_dir="$C_DIR"; socket="$C_SOCKET"; wnfn=wn_c
        npub="${C_NPUB:-?}"; hex="${C_HEX:-?}"
    fi

    printf '\n%s%s---- [%s] %s daemon diagnostics ----%s\n' \
        "$C_BOLD" "$C_CYAN" "$tag" "$who" "$C_RESET" >&2
    printf '\n==== [%s] %s daemon diagnostics ====\n' "$tag" "$who" >>"$LOG_FILE"

    info "$who identity  npub=$npub"
    info "$who identity  hex =$hex"
    info "$who socket    $socket"

    # Relay list grouped by type — this is the critical bit for invite
    # debugging. whitenoise-rs subscribes to kind:1059 on Inbox relays only,
    # so if Amethyst doesn't see $B_NPUB's kind:10050 list pointing at these
    # URLs, the gift wrap will never reach B.
    step "$who relays (per type):"
    for t in nip65 inbox key_package; do
        local raw urls
        raw=$("$wnfn" --json relays list --type "$t" 2>/dev/null || true)
        urls=$(printf '%s' "$raw" \
                 | jq -r '(.result // .) | .[]? | (.url // .relay.url // empty)' 2>/dev/null \
                 | paste -sd',' -)
        info "  $t: ${urls:-<none>}"
        printf '  %s %s raw: %s\n' "$who" "$t" "$raw" >>"$LOG_FILE"
    done

    # Connection status per relay — shows which sockets are actually live.
    step "$who relay connection status:"
    "$wnfn" --json relays list 2>/dev/null \
        | jq -r '(.result // .)[]? | "  \(.url // .relay.url // "?") [\(.type // "?")] [\(.status // .connection_status // "?")]"' 2>/dev/null \
        | tee -a "$LOG_FILE" >&2 || true

    # Daemon-level subscription health.
    step "$who wn debug health:"
    local health
    health=$("$wnfn" --json debug health 2>/dev/null || "$wnfn" debug health 2>/dev/null || true)
    printf '  %s\n' "${health:-<no output>}" | tee -a "$LOG_FILE" >&2

    # Relay-control state (subscriptions, filters, planes). Truncated to keep
    # stderr readable; full copy still goes to $LOG_FILE.
    step "$who wn debug relay-control-state (truncated to stderr; full in $LOG_FILE):"
    local rcs
    rcs=$("$wnfn" --json debug relay-control-state 2>/dev/null || "$wnfn" debug relay-control-state 2>/dev/null || true)
    printf '%s\n' "${rcs:-<no output>}" | head -n 20 | sed 's/^/    /' >&2
    printf '%s wn debug relay-control-state FULL:\n%s\n' "$who" "${rcs:-<no output>}" >>"$LOG_FILE"

    # Pending welcomes already decrypted by the daemon. Non-empty here while
    # wait_for_invite is polling would be a race worth noticing.
    step "$who pending invites:"
    local inv
    inv=$("$wnfn" --json groups invites 2>/dev/null || true)
    printf '  %s\n' "${inv:-<no output>}" | tee -a "$LOG_FILE" >&2

    # Tail of daemon stderr — contains the live 1059/welcome processing logs.
    step "$who wnd stderr (last 80 lines from $data_dir/logs/stderr.log):"
    if [[ -f "$data_dir/logs/stderr.log" ]]; then
        tail -n 80 "$data_dir/logs/stderr.log" 2>/dev/null | sed 's/^/    /' | tee -a "$LOG_FILE" >&2 || true
    else
        info "  (no stderr log at $data_dir/logs/stderr.log)"
    fi

    printf '%s%s---- end %s diagnostics ----%s\n\n' \
        "$C_BOLD" "$C_CYAN" "$who" "$C_RESET" >&2
    printf '==== end [%s] %s diagnostics ====\n\n' "$tag" "$who" >>"$LOG_FILE"
}

# Asks the operator to capture Amethyst's MarmotDbg logcat so the script log
# ends up with both sides of the pipe in one place.
prompt_amethyst_logcat() {
    local note="${1:-paste below}"
    prompt_human "On the host running adb, capture Amethyst's Marmot logs:

    adb logcat -d -v time | grep -E 'MarmotDbg|Marmot |MlsWelcome|GiftWrap' \\
        | tail -n 200 > /tmp/amethyst-marmot.log

Then paste /tmp/amethyst-marmot.log contents here so the failure report has
both the Amethyst-side publish log and the wn-side subscription log ($note)."
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
