#!/usr/bin/env bash
#
# nests-interop.sh — manual interop harness:
#   Amethyst (Android) <-> nostrnests.com (web NestsUI-v2)
#
# Audio-rooms (NIP-53 kind:30312 + moq-lite Lite-03 transport) end-to-end
# verification. Prompts the operator at every step to perform actions on
# either side, then asks p/f/s on each cross-side rendering check.
#
# Sequential, all-or-nothing. No daemons, no relay setup, no amy commands —
# every action is operator-driven through the two production UIs because
# `amy` does not (yet) ship `amy nests <verb>` subcommands.
#
# Usage:
#   ./nests-interop.sh                           # run every test
#   ./nests-interop.sh --only 02 --only 03       # run a subset
#   ./nests-interop.sh --skip 30 --skip 31       # skip slow ones
#   ./nests-interop.sh --keep-state              # reuse cached npubs
#
# Prereqs: see README.md in this directory.

set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
STATE_DIR="$SCRIPT_DIR/state"
LOG_DIR="$STATE_DIR/logs"
RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"
STATE_FILE_RUNTIME="$STATE_DIR/run.env"

mkdir -p "$STATE_DIR" "$LOG_DIR"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# ------- args ----------------------------------------------------------------
SKIP_IDS=()
ONLY_IDS=()
KEEP_STATE=0

usage() {
  cat <<'EOF'
nests-interop.sh — Amethyst <-> nostrnests.com manual interop harness

Options:
  --skip <id>       Skip a test by id (e.g. --skip 17). Repeatable.
  --only <id>       Run only the listed tests. Repeatable.
  --keep-state      Don't clear state/run.env at start (resume mode).
  -h, --help        Show this help.

Identities are cached in state/run.env; re-running without --keep-state
asks again for the npubs.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip) SKIP_IDS+=("$2"); shift ;;
    --only) ONLY_IDS+=("$2"); shift ;;
    --keep-state) KEEP_STATE=1 ;;
    -h|--help) usage; exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; usage; exit 2 ;;
  esac
  shift
done

# ------- colors --------------------------------------------------------------
if [[ -t 1 ]]; then
    C_RESET=$'\033[0m'
    C_BOLD=$'\033[1m'
    C_RED=$'\033[31m'
    C_GREEN=$'\033[32m'
    C_YELLOW=$'\033[33m'
    C_BLUE=$'\033[34m'
    C_MAGENTA=$'\033[35m'
    C_CYAN=$'\033[36m'
    C_GREY=$'\033[90m'
else
    C_RESET=""; C_BOLD=""; C_RED=""; C_GREEN=""
    C_YELLOW=""; C_BLUE=""; C_CYAN=""; C_MAGENTA=""; C_GREY=""
fi

# ------- logging primitives --------------------------------------------------
log()      { printf '%s\n' "$*" | tee -a "$LOG_FILE" >&2; }
banner()   {
    printf '\n%s%s================================================================%s\n' \
        "$C_BOLD" "$C_BLUE" "$C_RESET" >&2
    printf '%s%s  %s%s\n' "$C_BOLD" "$C_BLUE" "$1" "$C_RESET" >&2
    printf '%s%s================================================================%s\n\n' \
        "$C_BOLD" "$C_BLUE" "$C_RESET" >&2
    printf '\n=== %s ===\n' "$1" >>"$LOG_FILE"
}
step()     { printf '%s>%s %s\n' "$C_CYAN" "$C_RESET" "$1" >&2; printf '> %s\n' "$1" >>"$LOG_FILE"; }
info()     { printf '%s  %s%s\n' "$C_GREY" "$1" "$C_RESET" >&2; printf '  %s\n' "$1" >>"$LOG_FILE"; }
warn()     { printf '%s!%s %s\n' "$C_YELLOW" "$C_RESET" "$1" >&2; printf '! %s\n' "$1" >>"$LOG_FILE"; }
pass_msg() { printf '%s✓ PASS%s %s\n' "$C_GREEN" "$C_RESET" "$1" >&2; printf 'PASS %s\n' "$1" >>"$LOG_FILE"; }
fail_msg() { printf '%s✗ FAIL%s %s\n' "$C_RED" "$C_RESET" "$1" >&2;   printf 'FAIL %s\n' "$1" >>"$LOG_FILE"; }
skip_msg() { printf '%s- SKIP%s %s\n' "$C_YELLOW" "$C_RESET" "$1" >&2;     printf 'SKIP %s\n' "$1" >>"$LOG_FILE"; }

# ------- result tracking -----------------------------------------------------
record_result() {
    local test_id="$1" status="$2" note="${3:-}"
    printf '%s\t%s\t%s\n' "$test_id" "$status" "$note" >>"$RESULTS_FILE"
}

print_summary() {
    local total=0 passed=0 failed=0 skipped=0 col sym
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
            pass) passed=$((passed+1));   col="$C_GREEN";  sym="✓" ;;
            fail) failed=$((failed+1));   col="$C_RED";    sym="✗" ;;
            skip) skipped=$((skipped+1)); col="$C_YELLOW"; sym="-" ;;
            *)    col="$C_GREY";          sym="?" ;;
        esac
        printf '  %b %s%-50s%s %s\n' "$sym" "$col" "$tid" "$C_RESET" "$note"
    done <"$RESULTS_FILE"

    printf '\n  %s%d passed%s, %s%d failed%s, %s%d skipped%s   (of %d)\n\n' \
        "$C_GREEN" "$passed" "$C_RESET" \
        "$C_RED"   "$failed" "$C_RESET" \
        "$C_YELLOW" "$skipped" "$C_RESET" \
        "$total"
    printf '  full log: %s\n' "$LOG_FILE"
    printf '  results:  %s\n\n' "$RESULTS_FILE"
}

# ------- persistent state ----------------------------------------------------
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

if [[ "$KEEP_STATE" -ne 1 ]]; then
    : >"$STATE_FILE_RUNTIME"
fi

# ------- prompt helpers ------------------------------------------------------
# Two-target prompts. Color-coded so the operator can't confuse "do this in
# Amethyst" with "do this on nostrnests.com".

prompt_amethyst() {
    printf '\n%s%s---- DO THIS IN AMETHYST (Android) ----%s\n' "$C_BOLD" "$C_YELLOW" "$C_RESET" >&2
    printf '%s%s%s\n' "$C_YELLOW" "$1" "$C_RESET" >&2
    printf '%s%s----------------------------------------%s\n' "$C_BOLD" "$C_YELLOW" "$C_RESET" >&2
    printf '%s[Press Enter when done]%s ' "$C_CYAN" "$C_RESET" >&2
    read -r _
    printf 'AMETHYST-PROMPT: %s\n' "$1" >>"$LOG_FILE"
}

prompt_web() {
    printf '\n%s%s---- DO THIS ON nostrnests.com (web) ----%s\n' "$C_BOLD" "$C_MAGENTA" "$C_RESET" >&2
    printf '%s%s%s\n' "$C_MAGENTA" "$1" "$C_RESET" >&2
    printf '%s%s------------------------------------------%s\n' "$C_BOLD" "$C_MAGENTA" "$C_RESET" >&2
    printf '%s[Press Enter when done]%s ' "$C_CYAN" "$C_RESET" >&2
    read -r _
    printf 'WEB-PROMPT: %s\n' "$1" >>"$LOG_FILE"
}

prompt_both() {
    printf '\n%s%s---- DO THIS ON BOTH SIDES ----%s\n' "$C_BOLD" "$C_BLUE" "$C_RESET" >&2
    printf '%s%s%s\n' "$C_BLUE" "$1" "$C_RESET" >&2
    printf '%s%s--------------------------------%s\n' "$C_BOLD" "$C_BLUE" "$C_RESET" >&2
    printf '%s[Press Enter when done]%s ' "$C_CYAN" "$C_RESET" >&2
    read -r _
    printf 'BOTH-PROMPT: %s\n' "$1" >>"$LOG_FILE"
}

prompt_third() {
    printf '\n%s%s---- DO THIS ON THE 3RD IDENTITY (X) ----%s\n' "$C_BOLD" "$C_CYAN" "$C_RESET" >&2
    printf '%s%s%s\n' "$C_CYAN" "$1" "$C_RESET" >&2
    printf '%s%s------------------------------------------%s\n' "$C_BOLD" "$C_CYAN" "$C_RESET" >&2
    printf '%s[Press Enter when done]%s ' "$C_CYAN" "$C_RESET" >&2
    read -r _
    printf 'THIRD-PROMPT: %s\n' "$1" >>"$LOG_FILE"
}

# Capture a value, optionally reusing a cached one.
prompt_text() {
    local key="$1" desc="$2" current ans
    current=$(load_state "$key" 2>/dev/null || true)
    if [[ -n "$current" && "$KEEP_STATE" -eq 1 ]]; then
        info "using cached $key = $current"
        printf '%s' "$current"
        return
    fi
    printf '%s? %s%s\n' "$C_CYAN" "$desc" "$C_RESET" >&2
    if [[ -n "$current" ]]; then
        printf '   (cached: %s) — Enter to reuse, or paste new value: ' "$current" >&2
    else
        printf '   > ' >&2
    fi
    read -r ans
    if [[ -z "$ans" && -n "$current" ]]; then
        ans="$current"
    fi
    if [[ -n "$ans" ]]; then
        save_state "$key" "$ans"
    fi
    printf '%s' "$ans"
}

# Multi-line confirm. Returns 0=pass, 1=fail, 2=skip.
confirm() {
    local q="$1" ans
    while true; do
        printf '\n%s? %s%s\n   [p]ass / [f]ail / [s]kip: ' "$C_CYAN" "$q" "$C_RESET" >&2
        read -r ans
        case "$(printf '%s' "$ans" | tr '[:upper:]' '[:lower:]')" in
            p|pass) printf 'CONFIRM PASS: %s\n' "$q" >>"$LOG_FILE"; return 0 ;;
            f|fail) printf 'CONFIRM FAIL: %s\n' "$q" >>"$LOG_FILE"; return 1 ;;
            s|skip) printf 'CONFIRM SKIP: %s\n' "$q" >>"$LOG_FILE"; return 2 ;;
            *) printf 'enter p, f, or s\n' >&2 ;;
        esac
    done
}

# Wrapper for confirm that records the result and prints a per-test verdict.
record_confirm() {
    local tid="$1" desc="$2" question="$3"
    local rc
    if confirm "$question"; then
        rc=0
    else
        rc=$?
    fi
    case "$rc" in
        0) pass_msg "$tid: $desc"; record_result "$tid" pass "$desc" ;;
        2) skip_msg "$tid: $desc"; record_result "$tid" skip "$desc" ;;
        *) fail_msg "$tid: $desc"; record_result "$tid" fail "$desc" ;;
    esac
}

# ------- test orchestration --------------------------------------------------
should_run() {
    local tid="$1" x
    if (( ${#ONLY_IDS[@]} > 0 )); then
        for x in "${ONLY_IDS[@]}"; do [[ "$x" == "$tid" ]] && return 0; done
        return 1
    fi
    if (( ${#SKIP_IDS[@]} > 0 )); then
        for x in "${SKIP_IDS[@]}"; do [[ "$x" == "$tid" ]] && return 1; done
    fi
    return 0
}

# Wraps a test body so filtered/skipped tests still appear in the summary.
run_test() {
    local tid="$1" name="$2" body="$3"
    if ! should_run "$tid"; then
        skip_msg "$tid: $name (filtered)"
        record_result "$tid" skip "filtered"
        return
    fi
    banner "Test $tid — $name"
    "$body" "$tid"
}

# ------- identities ----------------------------------------------------------
A_NPUB=""   # Amethyst (Android)
W_NPUB=""   # nostrnests.com (web)
X_NPUB=""   # optional 3rd identity for multi-speaker / third-party kick

collect_identities() {
    banner "Identities"
    info "Two clients are required:"
    info "  A — Amethyst Android, account 'A'"
    info "  W — nostrnests.com web, account 'W'"
    info "A 3rd identity (X) is optional, only used by tests 32 and 25 (kick by"
    info "third-party admin). Press Enter to skip."
    A_NPUB=$(prompt_text "A_NPUB" "Amethyst (Android) account npub")
    W_NPUB=$(prompt_text "W_NPUB" "nostrnests.com (web) account npub")
    X_NPUB=$(prompt_text "X_NPUB" "Optional third account npub (Enter to skip)")
    info "A_NPUB = ${A_NPUB:-<missing>}"
    info "W_NPUB = ${W_NPUB:-<missing>}"
    info "X_NPUB = ${X_NPUB:-<not provided>}"
    if [[ -z "$A_NPUB" || -z "$W_NPUB" ]]; then
        fail_msg "both A_NPUB and W_NPUB are required"
        exit 1
    fi
}

require_three_identities() {
    if [[ -z "$X_NPUB" ]]; then
        return 1
    fi
    return 0
}

# ------- preflight checklist ------------------------------------------------
preflight() {
    banner "Preflight checklist"
    info "Verify the operator-side environment is ready before running tests."
    cat >&2 <<EOF

  ${C_YELLOW}On Amethyst (account A):${C_RESET}
    1. Build & install this branch (claude/test-nests-amethyst-interop-WETiY)
       on a real Android device. Emulator works for most tests but not for
       mic capture (test 02 forwards), background audio (33), or PIP (34).
    2. Log in to account A.
    3. Settings → Relays — make sure damus.io / nos.lol / primal.net (or
       the same relay set as the web side) are connected.
    4. Settings → Audio-room servers — confirm the default
       'nostrnests.com → https://moq.nostrnests.com' entry is present.
    5. Grant microphone + foreground-service permissions on first prompt.
    6. Open the Nests tab (bottom nav). Leave it open while we run.

  ${C_MAGENTA}On nostrnests.com (account W):${C_RESET}
    1. Open https://nostrnests.com/ in a Chromium-based browser with a
       Nostr signer extension (Alby, nos2x, getalby).
    2. Sign in with account W via the extension.
    3. Settings — confirm relay list overlaps Amethyst's (otherwise rooms
       will not show up cross-side).
    4. Allow microphone in browser permissions when prompted.

  Both sides should see each other's profile (kind:0) — search for the
  other npub on each platform to confirm they're discoverable.

EOF
    prompt_both "When both clients are ready and the relay sets overlap, press Enter."
}

# ============================================================================
#  TESTS
# ============================================================================
# Naming convention:
#   test_<id>_<short_slug>() { local tid="$1"; ...; }
#
# Each test body should:
#   1. step / info — describe the goal
#   2. prompt_amethyst / prompt_web — drive the operator
#   3. record_confirm — ask p/f/s and write to results
#
# Tests 01–07: Amethyst-hosted room, web listener
# Tests 08–14: web-hosted room, Amethyst listener
# Tests 15–17: mute / publish-permission gating
# Tests 18–22: hand raise + role promotion / demotion
# Tests 23–26: reactions (kind 7)
# Tests 27–30: live chat (kind 1311) + file uploads
# Tests 31–32: kick (kind 4312)
# Tests 33–34: edit & close room (kind 30312 re-publish)
# Tests 35:    scheduled rooms (status=planned, starts)
# Tests 36–38: lifecycle resilience (network, token, force-close)
# Tests 39:    multi-speaker (3 identities)
# Tests 40–41: background audio + PIP (Amethyst-side platform features)
# Tests 42–44: theming, share via naddr, custom moq server (kind 10112)
# Tests 45–47: edge cases (empty room, long title, leave-stage)

# ----------------------------------------------------------------------------
# 01 — Amethyst hosts an open room; web discovers it via the feed.
# ----------------------------------------------------------------------------
test_01_amethyst_creates_room_web_discovers() {
    local tid="$1"
    step "A creates a kind:30312 'Interop-$RUN_TS-A' on the default moq server"
    prompt_amethyst "1. Tap the floating 'Start space' FAB on the Nests tab.
2. Title: 'Interop-$RUN_TS-A'
3. Summary: 'Amethyst-hosted interop test'
4. Leave MoQ service / endpoint at defaults (https://moq.nostrnests.com).
5. Tap 'Start space'.
6. Confirm Amethyst transitions into the full-screen room view with you
   listed as the only host (top of the Stage section)."
    save_state "ROOM_A_TITLE" "Interop-$RUN_TS-A"

    prompt_web "1. Reload nostrnests.com home (or click 'Discover').
2. The room 'Interop-$RUN_TS-A' should appear in the live rooms list,
   hosted by A's profile picture / display name.
3. Don't join yet — just verify it is visible."

    record_confirm "$tid" \
        "kind:30312 from Amethyst is visible on nostrnests.com discover" \
        "Does the web client list the new room within ~10 s of creation?"
}

# ----------------------------------------------------------------------------
# 02 — Web joins the Amethyst-hosted room as a listener; presence flows.
# ----------------------------------------------------------------------------
test_02_web_joins_as_listener() {
    local tid="$1"
    step "W joins the room as a listener (no publish JWT yet)"
    prompt_web "1. Click the room 'Interop-$RUN_TS-A' on the home page.
2. Wait for the join handshake (NIP-98 → /auth → WebTransport CONNECT
   → moq-lite ALPN 'moq-lite-03').
3. Confirm the audio status pill shows 'Connected' (green/blue)."

    prompt_amethyst "1. In the room view, watch the Audience section.
2. W should appear with their profile picture within ~5 s of joining
   (kind:10312 presence event, ['onstage','0']['publishing','0'])."

    record_confirm "$tid" \
        "Web listener visible in Amethyst Audience grid" \
        "Does Amethyst's Audience now show W?"
}

# ----------------------------------------------------------------------------
# 03 — Amethyst broadcasts audio; web hears it.
# ----------------------------------------------------------------------------
test_03_amethyst_audio_to_web() {
    local tid="$1"
    step "A starts speaking — Opus over moq-lite uni-stream → W decodes via Web Audio"
    prompt_amethyst "1. Tap 'Connect audio' (or the mic icon if already on stage).
2. The status banner should walk through 'Resolving room' → 'Opening
   transport' → 'Negotiating audio' → 'Audio connected'.
3. Speak continuously for ~5 s — say 'one, two, three, four, five'."

    prompt_web "1. Confirm the volume meter on A's avatar pulses while A speaks.
2. Confirm you actually HEAR the audio through your speakers (not just
   the meter — the meter would show even if Opus decoding silently
   produced silence)."

    record_confirm "$tid" \
        "Amethyst → web audio decoded cleanly" \
        "Did you hear A's voice with no robot artifacts and < 1 s of latency?"
}

# ----------------------------------------------------------------------------
# 04 — Listener counter increments; web sees presence updates.
# ----------------------------------------------------------------------------
test_04_listener_counter_amethyst_host() {
    local tid="$1"
    step "Verify Amethyst aggregates kind:10312 presence (a 30-second window)"
    prompt_web "1. Already in the room from test 02 — note the 'N listening' badge
   on the room header in the web UI."

    if require_three_identities; then
        prompt_third "1. Open nostrnests.com (or another Amethyst install) signed in as
   X ($X_NPUB).
2. Join the room 'Interop-$RUN_TS-A'."
        prompt_amethyst "Watch the Audience section + listener counter (the avatar grid +
the number near the room header). It should show 2 listeners now (W + X)."

        record_confirm "$tid" \
            "Amethyst listener counter == 2 with W and X present" \
            "Does Amethyst correctly aggregate kind:10312 presence into a count of 2?"

        prompt_third "Leave the room (close tab / Leave button)."
    else
        skip_msg "$tid: 3-identity portion (no X_NPUB provided) — counter will only show 1"
        record_confirm "$tid" \
            "Amethyst listener counter == 1 (only W)" \
            "Does Amethyst show exactly 1 listener while W is in the room?"
    fi
}

# ----------------------------------------------------------------------------
# 05 — Amethyst host edits the room metadata; web reflects it.
# ----------------------------------------------------------------------------
test_05_amethyst_edits_room() {
    local tid="$1"
    step "Re-publish the kind:30312 with new title / summary / image"
    prompt_amethyst "1. In the room view, open the host menu (overflow / 'Edit room').
2. Change the title to 'Interop-$RUN_TS-A (edited)'.
3. Change the summary to 'Edit round-trip test'.
4. Save."

    prompt_web "Wait up to 5 s for the relay round-trip. The room header should now
show the new title and summary. (If the web client does not auto-refresh
metadata, refresh the page once.)"

    record_confirm "$tid" \
        "Edit propagates Amethyst → web" \
        "Does the web room header show the updated title + summary?"
}

# ----------------------------------------------------------------------------
# 06 — Web leaves the Amethyst-hosted room; counter goes back down.
# ----------------------------------------------------------------------------
test_06_web_leaves() {
    local tid="$1"
    prompt_web "Click 'Leave' on the web room view."
    prompt_amethyst "Watch the Audience grid — W should disappear within ~10 s
(presence is published with status 'leaving' and Amethyst trims them
out, then any remaining drop is via heartbeat-timeout)."
    record_confirm "$tid" \
        "Web leave reflected in Amethyst Audience" \
        "Did W leave Amethyst's Audience cleanly?"
}

# ----------------------------------------------------------------------------
# 07 — Amethyst host closes the room; web sees the ended state.
# ----------------------------------------------------------------------------
test_07_amethyst_closes_room() {
    local tid="$1"
    prompt_amethyst "1. Open the host menu → 'Close room'.
2. Confirm 'Close room' on the dialog (NOT 'Just leave').
3. The kind:30312 is re-published with ['status','ended'] and the
   foreground service stops."
    prompt_web "Watch the room. It should show 'CLOSED' (or auto-disconnect you)
within ~5 s. If you'd left already, navigate back to the home page —
the room should now appear as ended / hidden."
    record_confirm "$tid" \
        "status=ended propagates Amethyst → web" \
        "Does the web client mark the room CLOSED and disconnect cleanly?"
}

# ----------------------------------------------------------------------------
# 08 — Web hosts a new room; Amethyst discovers it.
# ----------------------------------------------------------------------------
test_08_web_creates_amethyst_discovers() {
    local tid="$1"
    step "Reverse direction: W hosts on the same moq server"
    prompt_web "1. Click 'Start space' / 'New room'.
2. Title: 'Interop-$RUN_TS-W'
3. Summary: 'Web-hosted interop test'
4. Theme / color / font — leave defaults.
5. Click 'Go live'."

    prompt_amethyst "1. Pull-to-refresh the Nests feed.
2. The room 'Interop-$RUN_TS-W' should appear within ~10 s, showing W as
   host and the moq.nostrnests.com endpoint."

    record_confirm "$tid" \
        "kind:30312 from web visible in Amethyst Nests feed" \
        "Does Amethyst show the web-hosted room in its Nests feed?"
}

# ----------------------------------------------------------------------------
# 09 — Amethyst joins the web-hosted room; web sees the new listener.
# ----------------------------------------------------------------------------
test_09_amethyst_joins_web_room() {
    local tid="$1"
    prompt_amethyst "1. Tap the room 'Interop-$RUN_TS-W' in the feed.
2. Tap 'Join' on the NestJoinCard.
3. Wait for connect (Resolving → Opening transport → Negotiating audio
   → Audio connected)."

    prompt_web "Watch the participants grid. A should appear in the listener row
within ~5 s with their profile picture."

    record_confirm "$tid" \
        "Amethyst listener visible on web" \
        "Does the web client show A in the listeners row?"
}

# ----------------------------------------------------------------------------
# 10 — Web speaker → Amethyst hears audio.
# ----------------------------------------------------------------------------
test_10_web_audio_to_amethyst() {
    local tid="$1"
    prompt_web "Speak continuously for ~5 s — say 'six, seven, eight, nine, ten'."
    prompt_amethyst "Confirm:
- W's avatar pulses with audio activity in the Stage section.
- You actually HEAR the voice through the device speakers / headphones.
- Latency feels < 1 s (no buffering longer than that)."
    record_confirm "$tid" \
        "Web → Amethyst audio decoded cleanly" \
        "Did you hear W's voice in Amethyst with no glitches?"
}

# ----------------------------------------------------------------------------
# 11 — Amethyst listener counter accuracy with X joining.
# ----------------------------------------------------------------------------
test_11_amethyst_listener_counter_in_web_room() {
    local tid="$1"
    if ! require_three_identities; then
        skip_msg "$tid: needs X_NPUB"
        record_result "$tid" skip "no third identity"
        return
    fi
    prompt_third "On the third identity X (web or another Amethyst install): join
'Interop-$RUN_TS-W'."
    prompt_amethyst "Look at the listener count on the room header. It should now show
2 (you + X)."
    record_confirm "$tid" \
        "Amethyst dedupes kind:10312 by pubkey" \
        "Does the listener counter show 2 (no double-count, no zeroes)?"
    prompt_third "X leaves the room."
}

# ----------------------------------------------------------------------------
# 12 — Listener cannot publish (publish=true gated by host role).
# ----------------------------------------------------------------------------
test_12_listener_cannot_publish() {
    local tid="$1"
    step "A is a plain listener; the mic should be either hidden or disabled"
    prompt_amethyst "1. Look at the bottom action bar. The 'Talk' / mic button should
   be hidden, OR present but greyed out (not tappable).
2. If you can tap it, the moq-auth /auth call should be rejected with
   'publish=true not allowed' or the local UI should refuse with
   'You aren't on the stage yet'.
3. Try tapping it anyway."
    record_confirm "$tid" \
        "Listener publish is gated client-side" \
        "Did Amethyst correctly prevent A from publishing audio?"
}

# ----------------------------------------------------------------------------
# 13 — Web host mute / unmute → Amethyst sees the muted indicator.
# ----------------------------------------------------------------------------
test_13_web_speaker_mute() {
    local tid="$1"
    prompt_web "1. Click the mic icon to toggle MUTE on yourself.
2. Wait ~3 s.
3. Try speaking — your audio should NOT reach A.
4. Click the mic icon again to UNMUTE.
5. Speak briefly — audio should resume."
    prompt_amethyst "1. While W is muted: W's avatar should show a 'muted' badge
   (kind:10312 with ['muted','1']) and you should hear silence even
   though they're talking.
2. After W unmutes: badge clears within 3 s and you hear them again."
    record_confirm "$tid" \
        "Web mute state propagates to Amethyst" \
        "Did the muted badge appear/disappear correctly and audio gating work?"
}

# ----------------------------------------------------------------------------
# 14 — Amethyst host mute / unmute → web sees the muted indicator.
# ----------------------------------------------------------------------------
test_14_amethyst_speaker_mute() {
    local tid="$1"
    step "Setup: A becomes a speaker first (host-promote A in the W room)."
    prompt_web "1. As host, open A's profile card in the listeners row.
2. Click 'Promote to speaker' (re-publishes kind:30312 with
   ['p', A_HEX, '<relay>', 'speaker'])."
    prompt_amethyst "1. The mic / 'Talk' button in the bottom bar should unlock within
   ~5 s of the role event arriving.
2. Tap 'Talk' to start broadcasting (becomes 'Stop talking').
3. Speak briefly to confirm audio is actually reaching W."
    prompt_amethyst "Now toggle MUTE:
1. Tap 'Mute mic'.
2. Try speaking — should NOT reach W.
3. Tap 'Unmute mic'.
4. Speak — should reach W."
    prompt_web "Verify A's avatar shows the muted badge while muted, and clears
when unmuted. Confirm audio gating matches."
    record_confirm "$tid" \
        "Amethyst mute state propagates to web" \
        "Did the muted badge round-trip correctly Amethyst → web?"
}

# ----------------------------------------------------------------------------
# 15 — Listener raises hand; host's queue shows it (Amethyst is host).
# ----------------------------------------------------------------------------
test_15_amethyst_host_sees_hand_raise() {
    local tid="$1"
    step "Reset: A creates a fresh room since the W room is now polluted with role state"
    prompt_amethyst "1. Leave the current room (Just leave / Close → confirm).
2. Tap 'Start space' again. Title: 'Interop-$RUN_TS-A2'.
3. Click Start. You should be the only host."
    prompt_web "Leave the W-hosted room ('End room' or just close tab).
Then join 'Interop-$RUN_TS-A2' as a listener."

    prompt_web "Click the 'Raise hand' button in the listener row (publishes
kind:10312 with ['hand','1'])."
    prompt_amethyst "Look at the Stage / hand-raise queue section. W should appear
under 'Raised hands' / 'Wants to speak' within ~5 s."
    record_confirm "$tid" \
        "Hand-raise queue shows web user on Amethyst" \
        "Does Amethyst's host UI show W in the raised-hand queue?"
}

# ----------------------------------------------------------------------------
# 16 — Amethyst host promotes web; mic unlocks on web.
# ----------------------------------------------------------------------------
test_16_amethyst_promotes_web() {
    local tid="$1"
    prompt_amethyst "1. Tap W's avatar in the raised-hand queue.
2. Choose 'Promote to speaker' from the action sheet.
3. Amethyst re-publishes kind:30312 with W as ['p', W_HEX, '', 'speaker']."
    prompt_web "Within ~5 s, the web UI should:
- Move W from listeners row → speakers row.
- Unlock the mic icon.
- Auto-mint a new JWT with publish=true and reconnect the moq-lite
  publisher.
Speak briefly to confirm audio actually reaches A."
    prompt_amethyst "Confirm you hear W speaking after promotion."
    record_confirm "$tid" \
        "Amethyst → web promote round-trip including publish JWT" \
        "Did W successfully publish audio after the promotion?"
}

# ----------------------------------------------------------------------------
# 17 — Amethyst host demotes web; mic locks on web.
# ----------------------------------------------------------------------------
test_17_amethyst_demotes_web() {
    local tid="$1"
    prompt_amethyst "1. Tap W's avatar in the speakers row.
2. Choose 'Demote to listener'.
3. Amethyst re-publishes kind:30312 dropping W's role."
    prompt_web "Within ~5 s, the web UI should:
- Move W from speakers row → listeners row.
- Lock the mic icon (greyed out).
- Stop publishing audio (existing moq-lite publish stream closes).
Try to speak — A should hear silence."
    prompt_amethyst "Confirm W's audio stream stops within a few seconds of the demote."
    record_confirm "$tid" \
        "Amethyst → web demote round-trip" \
        "Did W's mic gate and audio stream stop after demote?"
}

# ----------------------------------------------------------------------------
# 18 — Reverse direction: web host promotes Amethyst.
# ----------------------------------------------------------------------------
test_18_web_promotes_amethyst() {
    local tid="$1"
    step "Reset: switch back to W-hosted room"
    prompt_amethyst "1. Close the A-hosted room ('Close room' on the host menu).
2. Open Nests feed."
    prompt_web "Start a new space: 'Interop-$RUN_TS-W2'."
    prompt_amethyst "Join 'Interop-$RUN_TS-W2' as listener.
Tap 'Raise hand' (kind:10312 with ['hand','1'] is published)."
    prompt_web "Within ~5 s, A should appear in the raised-hand queue. Click
A's avatar → 'Promote to speaker'."
    prompt_amethyst "Within ~5 s, the bottom bar's mic button should unlock. Tap
'Talk' and speak briefly."
    prompt_web "Confirm A's audio reaches the web speakers."
    record_confirm "$tid" \
        "Web → Amethyst promote round-trip" \
        "Did A successfully publish after web's promote?"
}

# ----------------------------------------------------------------------------
# 19 — Web host demotes Amethyst; mic re-gates on Amethyst.
# ----------------------------------------------------------------------------
test_19_web_demotes_amethyst() {
    local tid="$1"
    prompt_web "Click A's avatar in speakers → 'Remove from stage' / 'Demote'."
    prompt_amethyst "Within ~5 s:
- Mic button should re-gate.
- Bottom bar should reset to listener controls.
- Any in-flight audio should stop."
    record_confirm "$tid" \
        "Web → Amethyst demote round-trip" \
        "Did A's mic re-gate cleanly after web's demote?"
}

# ----------------------------------------------------------------------------
# 20 — Amethyst sends a standard emoji reaction (kind 7).
# ----------------------------------------------------------------------------
test_20_amethyst_reacts_to_web_speaker() {
    local tid="$1"
    prompt_amethyst "1. Tap the 'React' button in the bottom action bar (or long-press
   on a speaker's avatar).
2. Pick 🎉 (or any standard emoji).
3. Send."
    prompt_web "Within ~3 s, a 🎉 floating overlay should appear over the avatar
of the targeted speaker (or in the room's reaction stream).
The reaction lingers for the standard ~30 s window."
    record_confirm "$tid" \
        "kind:7 with ['a', roomATag] surfaces on web" \
        "Did the web reaction overlay show 🎉 within ~3 s?"
}

# ----------------------------------------------------------------------------
# 21 — Web sends an emoji reaction; Amethyst overlay shows it.
# ----------------------------------------------------------------------------
test_21_web_reacts_to_amethyst_speaker() {
    local tid="$1"
    prompt_web "1. Click the 'React' (smiley) button.
2. Pick 🔥 (or any standard emoji).
3. Send."
    prompt_amethyst "Within ~3 s, a 🔥 reaction overlay should float over a speaker's
avatar (or the room's reaction strip)."
    record_confirm "$tid" \
        "kind:7 from web visible in Amethyst overlay" \
        "Did Amethyst show the reaction within ~3 s?"
}

# ----------------------------------------------------------------------------
# 22 — Custom emoji reaction (NIP-30 + kind 7).
# ----------------------------------------------------------------------------
test_22_custom_emoji_reaction() {
    local tid="$1"
    prompt_amethyst "1. Open the React picker.
2. Pick a CUSTOM emoji (one with a shortcode + image URL — e.g. one
   from your custom emoji set or an emoji pack you follow).
3. Send.
   The kind:7 should carry ['emoji', shortcode, url] tag (NIP-30) plus
   ['a', roomATag]."
    prompt_web "Verify the custom-emoji image renders (not the literal text
':shortcode:' — actual image bitmap)."
    record_confirm "$tid" \
        "Custom emoji renders cross-side (NIP-30)" \
        "Did the web client render the custom-emoji image correctly?"
}

# ----------------------------------------------------------------------------
# 23 — Reactions auto-clear after the 30-second window.
# ----------------------------------------------------------------------------
test_23_reactions_auto_clear() {
    local tid="$1"
    prompt_amethyst "Send another reaction (any emoji)."
    info "Wait ~35 seconds without any new reactions on either side."
    sleep 35
    record_confirm "$tid" \
        "Reaction overlay clears after ~30 s" \
        "Did both Amethyst's and web's reaction overlay clear by themselves?"
}

# ----------------------------------------------------------------------------
# 24 — Amethyst sends an in-room chat message (kind 1311).
# ----------------------------------------------------------------------------
test_24_amethyst_chat_to_web() {
    local tid="$1"
    prompt_amethyst "1. Open the Chat tab / panel inside the room view.
2. Type 'hello from amethyst $RUN_TS' and send.
   The kind:1311 should carry ['a', roomATag] referencing 'Interop-$RUN_TS-W2'."
    prompt_web "Within ~3 s, the chat panel should show 'hello from amethyst $RUN_TS'
attributed to A's profile."
    record_confirm "$tid" \
        "kind:1311 from Amethyst surfaces on web chat" \
        "Did the message appear in the web chat panel within ~3 s?"
}

# ----------------------------------------------------------------------------
# 25 — Web sends an in-room chat message; Amethyst displays it.
# ----------------------------------------------------------------------------
test_25_web_chat_to_amethyst() {
    local tid="$1"
    prompt_web "Type 'hello from web $RUN_TS' and send."
    prompt_amethyst "Within ~3 s, the chat panel should show 'hello from web $RUN_TS'
attributed to W's profile."
    record_confirm "$tid" \
        "kind:1311 from web surfaces on Amethyst chat" \
        "Did the message render in Amethyst's chat panel?"
}

# ----------------------------------------------------------------------------
# 26 — Long message + emoji + link in chat (parsing edge cases).
# ----------------------------------------------------------------------------
test_26_chat_long_msg_emoji_link() {
    local tid="$1"
    prompt_amethyst "Send this exact message in chat:
   'A long line — 漢字 + emoji 🚀🔥 + link https://nostr.com/ + a
    second line\nwith manual newline character. abc abc abc abc abc abc
    abc abc abc abc abc abc abc abc abc abc abc abc abc abc abc abc abc
    abc abc abc abc abc abc abc abc abc abc abc abc abc abc abc abc.'"
    prompt_web "Verify on the web side:
- Unicode (漢字) renders correctly.
- Emoji renders as actual emoji.
- 'https://nostr.com/' is a clickable link.
- Long message wraps without truncation."
    record_confirm "$tid" \
        "Chat parsing: unicode + emoji + URL + long lines" \
        "Did all four parsing aspects render correctly on web?"
}

# ----------------------------------------------------------------------------
# 27 — File upload in chat (Amethyst NestFileUploadDialog → kind 1311 with imeta).
# ----------------------------------------------------------------------------
test_27_chat_file_upload() {
    local tid="$1"
    prompt_amethyst "1. In the chat composer, tap the attachment / paperclip icon.
2. Pick an image from the gallery (any small image works).
3. Wait for upload (Blossom / NIP-96 server) and confirm the kind:1311
   message includes the image link + imeta tag.
4. Send."
    prompt_web "Confirm the image renders inline in the web chat panel (not just
a raw URL)."
    record_confirm "$tid" \
        "Image upload round-trip Amethyst → web" \
        "Did the image render inline in the web chat?"
}

# ----------------------------------------------------------------------------
# 28 — Chat history when joining mid-room.
# ----------------------------------------------------------------------------
test_28_chat_history_on_join() {
    local tid="$1"
    if ! require_three_identities; then
        skip_msg "$tid: needs X_NPUB to join after history accumulates"
        record_result "$tid" skip "no third identity"
        return
    fi
    prompt_third "Join the room 'Interop-$RUN_TS-W2' as identity X."
    info "X should backfill chat history from previous tests (24, 25, 26, 27)."
    record_confirm "$tid" \
        "Chat history backfill on late join" \
        "Does X see the prior messages from tests 24–27 after joining?"
    prompt_third "X leaves the room."
}

# ----------------------------------------------------------------------------
# 29 — Web host kicks Amethyst (kind 4312 admin command).
# ----------------------------------------------------------------------------
test_29_web_kicks_amethyst() {
    local tid="$1"
    step "Setup: A needs to be in the W room before the kick"
    prompt_amethyst "If you're not still in 'Interop-$RUN_TS-W2', join again."
    prompt_web "Click A's avatar in the participants area → 'Remove from room' /
'Kick'. The web client publishes:
   { kind: 4312, content: '',
     tags: [['a', roomATag], ['p', A_HEX], ['action', 'kick']] }
This is an ephemeral admin event signed by the host."
    prompt_amethyst "Within ~5 s, Amethyst should:
- Auto-disconnect from the moq-lite session.
- Show a 'You were removed from the room by the host' (or similar)
  message.
- Drop you back to the Nests feed."
    record_confirm "$tid" \
        "kind:4312 kick honored on Amethyst" \
        "Did Amethyst self-disconnect after the kick event?"
}

# ----------------------------------------------------------------------------
# 30 — Amethyst host kicks web user.
# ----------------------------------------------------------------------------
test_30_amethyst_kicks_web() {
    local tid="$1"
    step "Reset: A creates a fresh room, W joins"
    prompt_amethyst "1. Tap 'Start space' — title 'Interop-$RUN_TS-A3'.
2. Confirm you're hosting."
    prompt_web "Reload home, join 'Interop-$RUN_TS-A3'."

    prompt_amethyst "1. Tap W's avatar in the Audience grid.
2. Choose 'Kick' from the action sheet (publishes kind:4312).
3. Amethyst also drops W's ['p', W_HEX] from the kind:30312 (so they
   can't rejoin via the same role)."
    prompt_web "Within ~5 s, the web UI should auto-disconnect with a kick
notification. Try clicking the room from home — confirm the join
attempts are now rejected by moq-auth (since W's pubkey is no longer
in the room's allowed-publishers list, if applicable; for plain
listener kick the JWT mint may still succeed but the moq-lite path
should refuse based on the admin event)."
    record_confirm "$tid" \
        "Amethyst kick honored on web" \
        "Did the web client self-disconnect cleanly?"
}

# ----------------------------------------------------------------------------
# 31 — Web closes the room; Amethyst sees the ended state.
# ----------------------------------------------------------------------------
test_31_web_closes_room() {
    local tid="$1"
    step "Setup: switch to a W-hosted room"
    prompt_amethyst "Close the A-hosted room ('Close room' on host menu)."
    prompt_web "Start a new space 'Interop-$RUN_TS-W3' and let A join."
    prompt_amethyst "Join 'Interop-$RUN_TS-W3'."

    prompt_web "Click 'End room' / 'Close room'. Web re-publishes kind:30312 with
['status','ended'] and disconnects everyone."
    prompt_amethyst "Within ~5 s, Amethyst should:
- Auto-disconnect from moq-lite.
- Show the ended-room state (or kick back to Nests feed).
- The room card should read CLOSED."
    record_confirm "$tid" \
        "Web close honored on Amethyst" \
        "Did Amethyst handle the close cleanly?"
}

# ----------------------------------------------------------------------------
# 32 — Multi-speaker conference (A + W + X all speakers).
# ----------------------------------------------------------------------------
test_32_multi_speaker() {
    local tid="$1"
    if ! require_three_identities; then
        skip_msg "$tid: needs X_NPUB"
        record_result "$tid" skip "no third identity"
        return
    fi
    step "Reset: A hosts, promote W and X to speakers"
    prompt_amethyst "Start a fresh space 'Interop-$RUN_TS-MULTI'."
    prompt_web "Join 'Interop-$RUN_TS-MULTI'."
    prompt_third "X also joins 'Interop-$RUN_TS-MULTI'."

    prompt_amethyst "1. Promote both W and X to speakers (long-press their avatars).
2. Confirm all three are listed in the speakers row."
    prompt_both "W and X both speak briefly (one says 'web speaking', other says
'third speaking'), overlapping for ~2 s in the middle."
    record_confirm "$tid" \
        "Three concurrent speakers heard cleanly" \
        "Did A hear both W and X clearly even with overlap?"

    prompt_third "X leaves the room."
    prompt_amethyst "Close the room."
}

# ----------------------------------------------------------------------------
# 33 — Background audio (Amethyst foreground service).
# ----------------------------------------------------------------------------
test_33_background_audio() {
    local tid="$1"
    step "Verify AudioRoomForegroundService keeps audio playing under Home"
    prompt_web "Start 'Interop-$RUN_TS-BG' on web. Speak continuously throughout."
    prompt_amethyst "1. Join the W-hosted room.
2. Confirm you hear W speaking.
3. Press the device's HOME button to background Amethyst (don't kill it).
4. Confirm in the notification shade there's a persistent
   'AudioRoomForegroundService' notification with play/leave controls.
5. Listen for ~10 s — audio should keep playing."
    record_confirm "$tid" \
        "Background audio continues with foreground service" \
        "Did audio keep playing after backgrounding Amethyst?"
}

# ----------------------------------------------------------------------------
# 34 — Picture-in-Picture (PIP).
# ----------------------------------------------------------------------------
test_34_pip_screen() {
    local tid="$1"
    step "Verify NestPipScreen renders + minimal controls work"
    prompt_amethyst "1. From the still-active room (test 33), tap the PIP / minimize
   button (or use Android system PIP gesture).
2. The room should shrink to the PIP overlay window with mute / leave
   controls visible.
3. Move the window — drag it around the screen.
4. Tap mute in the PIP overlay (if A is on stage); else just leave."
    record_confirm "$tid" \
        "PIP overlay renders + controls are reachable" \
        "Did the PIP window render with audio + functional controls?"
    prompt_amethyst "Tap the PIP window to expand back to full screen, then leave."
    prompt_web "End the W-hosted room."
}

# ----------------------------------------------------------------------------
# 35 — Network drop / reconnect (Amethyst).
# ----------------------------------------------------------------------------
test_35_network_drop_reconnect() {
    local tid="$1"
    step "Verify ReconnectingNestsListener + NestsReconnectPolicy"
    prompt_web "Start 'Interop-$RUN_TS-NET'. Speak throughout."
    prompt_amethyst "Join the room. Confirm 'Audio connected'."

    prompt_amethyst "1. Toggle Airplane mode ON for ~5 seconds.
2. Toggle Airplane mode OFF.
3. Watch the status banner: 'Audio connected' → 'Reconnecting…' →
   eventually 'Audio connected' again. Should reconnect within ~30 s
   with exponential backoff.
4. After reconnect, confirm you hear W's voice again."
    record_confirm "$tid" \
        "Amethyst auto-reconnects after airplane mode" \
        "Did Amethyst reconnect within ~30 s and resume audio?"
}

# ----------------------------------------------------------------------------
# 36 — Long session: JWT re-mint after 10-minute moq-auth token expiry.
# ----------------------------------------------------------------------------
test_36_token_refresh_long_session() {
    local tid="$1"
    info "Tokens minted by /auth on moq.nostrnests.com live 600 s (10 min)."
    info "This test waits in-room for 11 minutes."
    info "Skip with 's' if you don't have time right now."
    if ! confirm "Run the 11-minute long-session test?"; then
        local rc=$?
        if (( rc == 1 )); then
            warn "marking as fail since operator declined"
            fail_msg "$tid: token refresh — operator declined"
            record_result "$tid" fail "operator declined"
        else
            skip_msg "$tid: token refresh — skipped"
            record_result "$tid" skip "operator skipped"
        fi
        return
    fi
    prompt_amethyst "Stay in 'Interop-$RUN_TS-NET' for 11 minutes. Don't lock the
screen (or rely on test 33 — backgrounding is fine)."
    info "Sleeping 11 minutes — talk to W or browse to keep events flowing."
    sleep 660
    prompt_amethyst "Confirm: are you still in the room with audio working?
The internal moq-auth JWT should have expired ~1 minute ago and been
re-minted transparently. The session should not have been visibly
interrupted."
    record_confirm "$tid" \
        "JWT re-mint after 10-min expiry is transparent" \
        "Did the long session continue uninterrupted across token expiry?"
    prompt_amethyst "Leave the room."
    prompt_web "End the room."
}

# ----------------------------------------------------------------------------
# 37 — Force-close Amethyst (process kill); presence stops; counter decays.
# ----------------------------------------------------------------------------
test_37_force_close_presence_decay() {
    local tid="$1"
    prompt_web "Start 'Interop-$RUN_TS-FORCE'."
    prompt_amethyst "Join 'Interop-$RUN_TS-FORCE'."
    prompt_amethyst "1. Press the device's RECENTS button.
2. Swipe Amethyst away (force-stop its process).
   This skips the normal Leave path — no kind:10312 with status='leaving'
   is published."
    prompt_web "1. Note the listener counter immediately after force-close.
2. Wait ~90 seconds (longer than the 30-second presence heartbeat
   window) and re-check.
3. Counter should drop to 0 once Amethyst's last kind:10312 ages out."
    record_confirm "$tid" \
        "Presence heartbeat-timeout removes ghost listener" \
        "Did the web counter eventually drop to 0?"
    prompt_web "End the room."
}

# ----------------------------------------------------------------------------
# 38 — Scheduled / planned room (status=planned + starts).
# ----------------------------------------------------------------------------
test_38_scheduled_room() {
    local tid="$1"
    step "Amethyst creates a room scheduled 5 minutes in the future"
    prompt_amethyst "1. Tap 'Start space'.
2. Title: 'Interop-$RUN_TS-SCHED'.
3. Toggle 'Schedule for later'.
4. Pick a start time ~5 minutes from now.
5. Submit (publishes kind:30312 with ['status','planned']
   ['starts','<unix>'])."
    prompt_web "Reload home. The web client should list the scheduled room with
an 'upcoming' / 'starts in 5m' label (NOT joinable yet)."
    record_confirm "$tid" \
        "Scheduled room visible as upcoming on web" \
        "Did the web client show 'Interop-$RUN_TS-SCHED' as upcoming?"

    info "Waiting 6 minutes for the scheduled time to pass…"
    info "(skip the rest of this test by Ctrl-C if you don't have time)"
    sleep 360
    prompt_both "Both clients should now show the room as joinable. Try joining
from each side."
    record_confirm "$tid" \
        "Scheduled room becomes joinable past start time" \
        "Did the room become joinable on both sides after the start time?"

    prompt_amethyst "Close 'Interop-$RUN_TS-SCHED'."
}

# ----------------------------------------------------------------------------
# 39 — Themed room (web hosts with non-default theme).
# ----------------------------------------------------------------------------
test_39_themed_room() {
    local tid="$1"
    step "Amethyst should parse theme tags gracefully (graceful-fallback level)"
    prompt_web "Create a room 'Interop-$RUN_TS-THEME' with a non-default
theme — pick a different background color, font, and (optionally) a
background image."
    prompt_amethyst "Join the themed room. Confirm:
- Amethyst doesn't crash on the unrecognized tags.
- The room renders readably (Amethyst may or may not honor the theme;
  graceful fallback to default theme is acceptable).
- Audio + chat still work."
    record_confirm "$tid" \
        "Amethyst handles themed room without breaking" \
        "Did Amethyst render the themed room without crashing or visual breakage?"
    prompt_web "End 'Interop-$RUN_TS-THEME'."
}

# ----------------------------------------------------------------------------
# 40 — Share via naddr (deep link).
# ----------------------------------------------------------------------------
test_40_share_naddr_deep_link() {
    local tid="$1"
    prompt_amethyst "1. Start a new space 'Interop-$RUN_TS-SHARE'.
2. Tap the share button (overflow → 'Share room').
3. Choose 'Copy naddr' (or post as a kind:1 referencing the room).
4. Copy the naddr1… string somewhere shareable (paste in a notes app
   or send via DM)."
    prompt_web "1. Open the naddr in the web client (paste in the URL bar / 'Open
   nostr address' search box, or click it from a relay-followed feed).
2. The web client should resolve the naddr → kind:30312 → join the
   room directly."
    record_confirm "$tid" \
        "naddr1 deep link resolves cross-client" \
        "Did the web client successfully open the room from the naddr?"
    prompt_amethyst "Close 'Interop-$RUN_TS-SHARE'."
}

# ----------------------------------------------------------------------------
# 41 — Custom moq server via Settings (kind 10112).
# ----------------------------------------------------------------------------
test_41_custom_moq_server() {
    local tid="$1"
    step "Verify NestsServerListState publishes kind:10112 and is honored"
    info "If you don't have a non-default moq-rs deployment, skip this."
    if ! confirm "Skip if you have no custom moq server, otherwise proceed."; then
        local rc=$?
        if (( rc == 2 )); then
            skip_msg "$tid: no custom moq server"
            record_result "$tid" skip "no custom moq server available"
            return
        fi
    fi
    local custom_url
    custom_url=$(prompt_text "CUSTOM_MOQ_URL" "Custom moq server URL (e.g. https://moq.example.com)")
    [[ -z "$custom_url" ]] && { skip_msg "$tid: no URL"; record_result "$tid" skip "no URL"; return; }

    prompt_amethyst "1. Settings → Audio-room servers.
2. Add a new entry — name '$custom_url', baseUrl '$custom_url'.
3. Mark it as DEFAULT (publishes kind:10112 with this server first).
4. Save."

    prompt_amethyst "1. Tap 'Start space' — verify the service / endpoint fields
   default to '$custom_url'.
2. Title 'Interop-$RUN_TS-CUSTOM'.
3. Start the room."

    prompt_web "1. Verify the kind:30312 carries service / endpoint pointing at
   '$custom_url' (inspect via the room's 'Details' panel or a relay
   debug viewer).
2. If the web client supports arbitrary moq servers, attempt to join
   the room. The join should hit '$custom_url' (NOT moq.nostrnests.com)."

    record_confirm "$tid" \
        "kind:10112 server-list overrides the default endpoint" \
        "Did the room publish + connect through the custom moq server?"

    prompt_amethyst "Settings → Audio-room servers → revert default to nostrnests.com.
Close 'Interop-$RUN_TS-CUSTOM'."
}

# ----------------------------------------------------------------------------
# 42 — Empty room (host is alone, no listeners) — sanity for the audio path.
# ----------------------------------------------------------------------------
test_42_empty_room_host_alone() {
    local tid="$1"
    prompt_amethyst "1. Start a fresh space 'Interop-$RUN_TS-SOLO'.
2. Confirm the moq-lite session opens cleanly even with 0 listeners.
3. Speak briefly — there should be no errors, just no audience.
4. Watch the listener counter — should read 0 throughout."
    record_confirm "$tid" \
        "Empty-room host path doesn't error" \
        "Did Amethyst stay in 'Audio connected' with no listeners + no errors?"

    prompt_amethyst "Close the room."
}

# ----------------------------------------------------------------------------
# 43 — Long title / very long summary (UI truncation, kind:30312 size).
# ----------------------------------------------------------------------------
test_43_long_title_and_summary() {
    local tid="$1"
    prompt_amethyst "1. Start a space.
2. Title (paste this 200-char string):
   'A very very very very very very very very long room title that is
    deliberately too long to fit on a normal phone screen so we can see
    how the UI handles overflow truncation in lists and headers ABCDEFG'
3. Summary: paste any ~1KB text (multiple paragraphs of lorem ipsum).
4. Submit."
    prompt_web "Confirm in the web list view:
- Title is truncated with '…' (or wraps cleanly), no horizontal scroll.
- Summary is shown / expandable without breaking the layout."
    record_confirm "$tid" \
        "Long metadata renders without UI breakage" \
        "Did both clients handle the long title + summary gracefully?"
    prompt_amethyst "Close the room."
}

# ----------------------------------------------------------------------------
# 44 — Speaker leaves stage (onstage=0) without disconnecting.
# ----------------------------------------------------------------------------
test_44_speaker_leave_stage() {
    local tid="$1"
    step "Amethyst speaker drops to listener via onstage=0 (no role removal)"
    prompt_web "Start 'Interop-$RUN_TS-STAGE'."
    prompt_amethyst "Join 'Interop-$RUN_TS-STAGE'."
    prompt_web "Promote A to speaker."
    prompt_amethyst "1. After being promoted, tap 'Talk' to broadcast briefly.
2. Then tap 'Leave stage' (publishes kind:10312 with ['onstage','0']
   but keeps the speaker role tag in kind:30312 — A can re-take the
   stage without being re-promoted).
3. Confirm the mic gates and your avatar moves back to the listener row."
    prompt_web "Within ~5 s, A should appear in the LISTENER row even though the
underlying ['p', A_HEX, '', 'speaker'] role is still in the kind:30312.
The web UI uses the onstage flag to decide where to render A."
    record_confirm "$tid" \
        "onstage=0 demotes visually without role removal" \
        "Did A move to the listener row while role tag was preserved?"

    prompt_amethyst "Now re-take the stage: tap 'Talk' / 'Take stage'.
Avatar should move back to the speaker row without web needing to
re-promote."
    record_confirm "$tid-rejoin" \
        "Speaker re-takes stage without re-promotion" \
        "Did A move back to speaker row without W manually re-promoting?"

    prompt_web "End 'Interop-$RUN_TS-STAGE'."
}

# ----------------------------------------------------------------------------
# 45 — Listener-counter dedupe: same pubkey on two devices counts once.
# ----------------------------------------------------------------------------
test_45_listener_dedupe() {
    local tid="$1"
    info "Skip if you don't have a 2nd device for A."
    if ! confirm "Run the listener-dedupe test (needs a 2nd device for A)?"; then
        local rc=$?
        if (( rc == 2 )); then
            skip_msg "$tid: no second device"; record_result "$tid" skip "no 2nd device"
        else
            fail_msg "$tid: declined"; record_result "$tid" fail "declined"
        fi
        return
    fi
    prompt_web "Start 'Interop-$RUN_TS-DEDUPE'."
    prompt_amethyst "1. Join from your primary device.
2. ALSO sign in to A on a second Android device / emulator and join
   the same room.
3. Both devices publish kind:10312 from the same pubkey."
    prompt_web "Verify the listener counter shows 1, NOT 2 (the web client should
dedupe by pubkey)."
    record_confirm "$tid" \
        "Web dedupes presence by pubkey" \
        "Did the counter show 1 with both devices in the room?"

    prompt_amethyst "Leave from the second device."
    prompt_web "End the room."
}

# ----------------------------------------------------------------------------
# 46 — Profile-card actions (zap, follow, mute) inside a room.
# ----------------------------------------------------------------------------
test_46_per_participant_actions() {
    local tid="$1"
    prompt_web "Start 'Interop-$RUN_TS-ACTIONS'."
    prompt_amethyst "Join the room. Tap on W's avatar in the speakers row."
    prompt_amethyst "From W's profile card / sheet, exercise:
- View profile (opens W's profile screen).
- Follow W (kind:3 / kind:10000 update).
- Mute W (kind:10000 mute list, in-app only — doesn't disconnect).
- Zap W (NIP-57; only if W has a lud16/lud06 + you can pay).
Cancel/close any sheet you open without paying actual sats unless
that's part of the run."
    record_confirm "$tid" \
        "Profile-card actions reachable from a room" \
        "Did all four actions open without crashing?"
    prompt_web "End the room."
}

# ----------------------------------------------------------------------------
# 47 — Concurrent edits race (A and X both have host privileges).
# ----------------------------------------------------------------------------
test_47_concurrent_host_edits() {
    local tid="$1"
    if ! require_three_identities; then
        skip_msg "$tid: needs X_NPUB"; record_result "$tid" skip "no third identity"; return
    fi
    prompt_amethyst "Create 'Interop-$RUN_TS-RACE'. Promote both W and X to admin
role (re-publishes kind:30312 with both as ['p', _, '', 'admin'])."
    prompt_both "Both W and X open the Edit Room sheet on their respective clients."
    prompt_web "Change the title to '...-by-W' and save."
    prompt_third "At the same time (within ~1 s), change the title to '...-by-X' and save."
    prompt_amethyst "Wait ~10 s. Last-write-wins on the relay — Amethyst should
converge on whichever event has the higher created_at (or, on tie,
the higher event id by lexicographic order)."
    record_confirm "$tid" \
        "Concurrent edits resolve to last-write-wins" \
        "Did both clients eventually converge on the same title?"
    prompt_amethyst "Close 'Interop-$RUN_TS-RACE'."
}

# ============================================================================
#  MAIN
# ============================================================================
cleanup() {
    local rc=$?
    trap - EXIT INT TERM HUP
    print_summary
    exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

main() {
    banner "Amethyst <-> nostrnests.com manual interop ($RUN_TS)"
    info "Branch: $(git -C "$SCRIPT_DIR" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
    info "Log:    $LOG_FILE"
    info "Result: $RESULTS_FILE"
    collect_identities
    preflight

    run_test 01 "Amethyst hosts; web discovers"            test_01_amethyst_creates_room_web_discovers
    run_test 02 "Web joins as listener"                    test_02_web_joins_as_listener
    run_test 03 "Amethyst → web audio"                     test_03_amethyst_audio_to_web
    run_test 04 "Listener counter (Amethyst host)"         test_04_listener_counter_amethyst_host
    run_test 05 "Amethyst host edits room"                 test_05_amethyst_edits_room
    run_test 06 "Web leaves Amethyst-hosted room"          test_06_web_leaves
    run_test 07 "Amethyst host closes room"                test_07_amethyst_closes_room

    run_test 08 "Web hosts; Amethyst discovers"            test_08_web_creates_amethyst_discovers
    run_test 09 "Amethyst joins web-hosted room"           test_09_amethyst_joins_web_room
    run_test 10 "Web → Amethyst audio"                     test_10_web_audio_to_amethyst
    run_test 11 "Listener counter (web host)"              test_11_amethyst_listener_counter_in_web_room
    run_test 12 "Listener cannot publish"                  test_12_listener_cannot_publish

    run_test 13 "Web speaker mute round-trip"              test_13_web_speaker_mute
    run_test 14 "Amethyst speaker mute round-trip"         test_14_amethyst_speaker_mute

    run_test 15 "Hand-raise → Amethyst host queue"         test_15_amethyst_host_sees_hand_raise
    run_test 16 "Amethyst promotes web to speaker"         test_16_amethyst_promotes_web
    run_test 17 "Amethyst demotes web to listener"         test_17_amethyst_demotes_web
    run_test 18 "Web promotes Amethyst to speaker"         test_18_web_promotes_amethyst
    run_test 19 "Web demotes Amethyst to listener"         test_19_web_demotes_amethyst

    run_test 20 "Amethyst → web reaction (kind 7)"         test_20_amethyst_reacts_to_web_speaker
    run_test 21 "Web → Amethyst reaction"                  test_21_web_reacts_to_amethyst_speaker
    run_test 22 "Custom emoji reaction (NIP-30)"           test_22_custom_emoji_reaction
    run_test 23 "Reactions auto-clear after 30 s"          test_23_reactions_auto_clear

    run_test 24 "Amethyst → web chat (kind 1311)"          test_24_amethyst_chat_to_web
    run_test 25 "Web → Amethyst chat"                      test_25_web_chat_to_amethyst
    run_test 26 "Chat: long + emoji + link + unicode"      test_26_chat_long_msg_emoji_link
    run_test 27 "Chat: image upload Amethyst → web"        test_27_chat_file_upload
    run_test 28 "Chat history backfill on late join"       test_28_chat_history_on_join

    run_test 29 "Web kicks Amethyst (kind 4312)"           test_29_web_kicks_amethyst
    run_test 30 "Amethyst kicks web (kind 4312)"           test_30_amethyst_kicks_web

    run_test 31 "Web closes room (status=ended)"           test_31_web_closes_room
    run_test 32 "Multi-speaker (3 identities)"             test_32_multi_speaker

    run_test 33 "Background audio (foreground service)"    test_33_background_audio
    run_test 34 "PIP screen + controls"                    test_34_pip_screen

    run_test 35 "Network drop → reconnect"                 test_35_network_drop_reconnect
    run_test 36 "Long session: token refresh > 10 min"     test_36_token_refresh_long_session
    run_test 37 "Force-close: ghost listener decay"        test_37_force_close_presence_decay

    run_test 38 "Scheduled / planned room"                 test_38_scheduled_room
    run_test 39 "Themed room graceful fallback"            test_39_themed_room
    run_test 40 "Share via naddr deep link"                test_40_share_naddr_deep_link
    run_test 41 "Custom moq server (kind 10112)"           test_41_custom_moq_server
    run_test 42 "Empty room (host alone)"                  test_42_empty_room_host_alone
    run_test 43 "Long title + summary"                     test_43_long_title_and_summary
    run_test 44 "Speaker leave-stage (onstage=0)"          test_44_speaker_leave_stage
    run_test 45 "Listener dedupe by pubkey"                test_45_listener_dedupe
    run_test 46 "Per-participant profile actions"          test_46_per_participant_actions
    run_test 47 "Concurrent host edits race"               test_47_concurrent_host_edits
}

main "$@"
