#!/usr/bin/env bash
#
# marmot-interop.sh — interop test harness: Amethyst <-> whitenoise-rs (wn/wnd)
#
# Sequential, all-or-nothing. Script drives the `wn` side automatically and
# prompts the human operator at each step that requires Amethyst UI action.
#
# Usage: ./marmot-interop.sh [--local-relays] [--transponder] [--no-build]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
STATE_DIR="$SCRIPT_DIR/state"
LOG_DIR="$STATE_DIR/logs"
B_DIR="$STATE_DIR/B"
C_DIR="$STATE_DIR/C"
# wnd derives its socket path as "{data_dir}/release/wnd.sock" for release
# builds (and ".../dev/wnd.sock" for debug); our preflight always uses
# --release, so we hardcode the "release" suffix here.
B_SOCKET="$B_DIR/release/wnd.sock"
C_SOCKET="$C_DIR/release/wnd.sock"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

WN_REPO="${WN_REPO:-$STATE_DIR/whitenoise-rs}"
WN_BIN=""
WND_BIN=""
B_NPUB=""
B_HEX=""
C_NPUB=""
C_HEX=""
A_NPUB=""
A_HEX=""

DEFAULT_RELAYS=( "wss://relay.damus.io" "wss://nos.lol" "wss://relay.primal.net" )
USE_LOCAL_RELAYS=0
ENABLE_TRANSPONDER=0
NO_BUILD=0

usage() {
  cat <<EOF
marmot-interop.sh — Amethyst <-> whitenoise-rs interop harness

Options:
  --local-relays    Use ws://localhost:8080 instead of public relays (requires 'just docker-up')
  --transponder     Run Test 14 (MIP-05 push notifications)
  --no-build        Don't rebuild wn/wnd if binaries are missing
  -h, --help        Show this help

Environment:
  WN_REPO           Path to whitenoise-rs checkout (default: state/whitenoise-rs)
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --local-relays) USE_LOCAL_RELAYS=1 ;;
    --transponder)  ENABLE_TRANSPONDER=1 ;;
    --no-build)     NO_BUILD=1 ;;
    -h|--help)      usage; exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; usage; exit 2 ;;
  esac
  shift
done

mkdir -p "$STATE_DIR" "$LOG_DIR" "$B_DIR/logs" "$C_DIR/logs"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# shellcheck source=lib.sh
source "$SCRIPT_DIR/lib.sh"

# --- preflight ---------------------------------------------------------------
preflight() {
  banner "Preflight"
  for cmd in jq git curl cargo; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      fail_msg "missing required tool: $cmd"; exit 1
    fi
    info "$cmd: $(command -v "$cmd")"
  done

  WN_BIN="$WN_REPO/target/release/wn"
  WND_BIN="$WN_REPO/target/release/wnd"

  if [[ ! -x "$WN_BIN" || ! -x "$WND_BIN" ]]; then
    if [[ "$NO_BUILD" -eq 1 ]]; then
      fail_msg "wn/wnd not found and --no-build set: $WN_BIN"; exit 1
    fi
    if [[ ! -d "$WN_REPO/.git" ]]; then
      step "cloning whitenoise-rs into $WN_REPO"
      git clone --depth 1 https://github.com/marmot-protocol/whitenoise-rs.git "$WN_REPO" \
        2>&1 | tee -a "$LOG_FILE"
    fi
    step "building wn + wnd (cargo build --release --features cli) — ~5 min first run"
    ( cd "$WN_REPO" && cargo build --release --features cli --bin wn --bin wnd ) \
      2>&1 | tee -a "$LOG_FILE"
  fi
  info "wn:  $WN_BIN"
  info "wnd: $WND_BIN"
}

# --- daemons -----------------------------------------------------------------
start_daemon() {
  local name="$1" data_dir="$2" socket="$3"
  step "starting $name daemon"
  if [[ -S "$socket" ]] && "$WN_BIN" --socket "$socket" whoami >/dev/null 2>&1; then
    info "$name daemon already running"
    return 0
  fi
  rm -f "$socket"
  mkdir -p "$data_dir/logs" "$data_dir/release"
  # wnd puts its socket at {data_dir}/release/wnd.sock (release build) — we
  # don't pass --socket because the daemon doesn't accept that flag.
  nohup "$WND_BIN" --data-dir "$data_dir" --logs-dir "$data_dir/logs" \
    >"$data_dir/logs/stdout.log" 2>"$data_dir/logs/stderr.log" &
  local pid=$!
  echo "$pid" > "$data_dir/pid"
  info "$name pid $pid; waiting for socket at $socket ..."
  local deadline=$(( $(date +%s) + 30 ))
  while [[ $(date +%s) -lt $deadline ]]; do
    if [[ -S "$socket" ]] && "$WN_BIN" --socket "$socket" whoami >/dev/null 2>&1; then
      info "$name ready"
      return 0
    fi
    sleep 1
  done
  fail_msg "$name daemon failed to start (see $data_dir/logs/stderr.log)"
  exit 1
}

stop_daemons() {
  for d in "$B_DIR" "$C_DIR"; do
    if [[ -f "$d/pid" ]]; then
      local pid; pid=$(cat "$d/pid" 2>/dev/null || echo "")
      if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
        info "stopping daemon pid $pid"
        kill "$pid" 2>/dev/null || true
        sleep 1
        kill -9 "$pid" 2>/dev/null || true
      fi
      rm -f "$d/pid"
    fi
  done
}

# --- identities --------------------------------------------------------------
ensure_identity() {
  local who="$1" cmd
  if [[ "$who" == "B" ]]; then cmd=wn_b; else cmd=wn_c; fi
  step "ensuring Identity $who"

  # First try whoami (will be empty if no account yet). Try --json, then the
  # yaml-ish pretty form as a fallback.
  local out npub=""
  out=$("$cmd" --json whoami 2>/dev/null || true)
  npub=$(extract_pubkey "$out")
  if [[ -z "${npub:-}" ]]; then
    out=$("$cmd" whoami 2>/dev/null || true)
    npub=$(extract_pubkey "$out")
  fi

  if [[ -z "${npub:-}" ]]; then
    info "creating new identity for $who"
    out=$("$cmd" create-identity 2>&1 | tee -a "$LOG_FILE")
    npub=$(extract_pubkey "$out")
    # Some builds only print the npub on whoami after create, not on
    # create-identity itself — re-query as a last resort.
    if [[ -z "${npub:-}" ]]; then
      out=$("$cmd" whoami 2>/dev/null || true)
      npub=$(extract_pubkey "$out")
    fi
  fi

  if [[ -z "${npub:-}" ]]; then
    fail_msg "could not determine npub for $who — raw output below:"
    printf '%s\n' "$out" | tee -a "$LOG_FILE" >&2
    exit 1
  fi

  local hex
  hex=$(npub_to_hex "$npub")
  if [[ "$who" == "B" ]]; then B_NPUB="$npub"; B_HEX="$hex"
  else                         C_NPUB="$npub"; C_HEX="$hex"; fi
  save_state "${who}_NPUB" "$npub"
  save_state "${who}_HEX"  "$hex"
  info "$who npub: $npub"
  [[ "$hex" != "$npub" ]] && info "$who hex:  $hex"
}

prompt_for_a_npub() {
  A_NPUB=$(load_state "A_NPUB" || true)
  if [[ -n "${A_NPUB:-}" ]]; then
    info "A npub (cached): $A_NPUB"
  else
    printf '\n%sPaste your Amethyst account npub:%s ' "$C_CYAN" "$C_RESET" >&2
    read -r A_NPUB
    save_state "A_NPUB" "$A_NPUB"
  fi
  A_HEX=$(npub_to_hex "$A_NPUB")
  save_state "A_HEX" "$A_HEX"
  info "A npub: $A_NPUB"
  [[ "$A_HEX" != "$A_NPUB" ]] && info "A hex:  $A_HEX"
}

# --- relays ------------------------------------------------------------------
configure_relays() {
  banner "Configuring relays"
  local relays=()
  if [[ "$USE_LOCAL_RELAYS" -eq 1 ]]; then
    relays=( "ws://localhost:8080" )
  else
    relays=( "${DEFAULT_RELAYS[@]}" )
  fi
  add_relay() {
    local wn_fn="$1" r="$2" t add_out
    for t in nip65 inbox key_package; do
      if add_out=$("$wn_fn" relays add --type "$t" "$r" 2>&1); then
        info "$wn_fn relays add --type $t $r: ok"
      else
        case "$add_out" in
          *"already exists"*|*"already added"*|*"duplicate"*)
            info "$wn_fn relays add --type $t $r: already exists, skipping" ;;
          *)
            warn "$wn_fn relays add --type $t $r failed: $add_out"
            printf '%s relays add --type %s %s: %s\n' "$wn_fn" "$t" "$r" "$add_out" >>"$LOG_FILE" ;;
        esac
      fi
    done
  }

  for r in "${relays[@]}"; do
    step "adding $r to both daemons"
    add_relay wn_b "$r"
    add_relay wn_c "$r"
  done

  step "B relay list after configure"
  local relay_list
  relay_list=$(wn_b --json relays list 2>/dev/null || true)
  info "B relays: $(printf '%s' "$relay_list" | jq -r '.result[]? | "\(.url) [\(.status)]"' 2>/dev/null | tr '\n' '  ')"
  printf 'B relay list: %s\n' "$relay_list" >>"$LOG_FILE"

  # Sanity check is split across three relay-level surfaces so a failure
  # points at the exact kind the relay set can't handle, instead of 10
  # later tests all timing out with the same "no invite" symptom:
  #
  #   kind:30443 (KeyPackage)        — both sides publish + discover
  #   kind:10050 (Inbox advertise)   — C must be reachable as a giftwrap target
  #   kind:1059  (Gift wrap)         — B->C welcome delivery
  #   kind:445   (MLS commit/msg)    — real group message round-trip
  #
  # All failures are warnings (not hard fails) so a known-broken relay set
  # still lets the harness continue into the 13 tests — they will fail
  # anyway but at least the operator will have the diagnostic hint here.
  step "sanity-check kind:30443: B+C publish + symmetric discover"
  local publish_out
  if publish_out=$(wn_b keys publish 2>&1); then
    info "wn_b keys publish: ok"
  else
    warn "wn_b keys publish failed: $publish_out"
    printf 'wn_b keys publish: %s\n' "$publish_out" >>"$LOG_FILE"
  fi
  if publish_out=$(wn_c keys publish 2>&1); then
    info "wn_c keys publish: ok"
  else
    warn "wn_c keys publish failed: $publish_out"
    printf 'wn_c keys publish: %s\n' "$publish_out" >>"$LOG_FILE"
  fi
  sleep 4
  local sanity_raw sanity_id
  sanity_raw=$(wn_c --json keys check "$B_NPUB" 2>>"$LOG_FILE" || true)
  sanity_id=$(printf '%s' "$sanity_raw" | jq -r '.result.event_id // .event_id // empty' 2>/dev/null || true)
  if [[ -n "$sanity_id" && "$sanity_id" != "null" ]]; then
    info "kind:30443 C->B ok (event_id: $sanity_id)"
  else
    warn "kind:30443 C->B failed — relays may be rejecting KeyPackages"
    printf 'sanity keys check B raw: %s\n' "$sanity_raw" >>"$LOG_FILE"
  fi
  sanity_raw=$(wn_b --json keys check "$C_NPUB" 2>>"$LOG_FILE" || true)
  sanity_id=$(printf '%s' "$sanity_raw" | jq -r '.result.event_id // .event_id // empty' 2>/dev/null || true)
  if [[ -n "$sanity_id" && "$sanity_id" != "null" ]]; then
    info "kind:30443 B->C ok (event_id: $sanity_id)"
  else
    warn "kind:30443 B->C failed — relays may be rejecting KeyPackages"
    printf 'sanity keys check C raw: %s\n' "$sanity_raw" >>"$LOG_FILE"
  fi

  step "sanity-check kinds 10050/1059/445: B creates group + invites C + exchanges a message"
  local sanity_gid sanity_c_gid sanity_create
  sanity_create=$(wn_b --json groups create "sanity-check" "$C_NPUB" 2>>"$LOG_FILE" || true)
  sanity_gid=$(printf '%s' "$sanity_create" | jq_group_id)
  printf 'sanity groups create raw: %s\n' "$sanity_create" >>"$LOG_FILE"
  if [[ -z "$sanity_gid" ]]; then
    warn "sanity group create failed — see $LOG_FILE (stale account state? rerun with 'rm -rf state/')"
  else
    info "sanity group id: $sanity_gid"
    if sanity_c_gid=$(wait_for_invite C 30); then
      info "kind:10050+1059 ok — C received welcome (gid: $sanity_c_gid)"
      wn_c groups accept "$sanity_c_gid" >/dev/null 2>&1 || true
      wn_b messages send "$sanity_gid" "sanity-ping" >/dev/null 2>&1 || true
      if wait_for_message C "$sanity_c_gid" "sanity-ping" 30; then
        info "kind:445 ok — C decrypted sanity-ping"
      else
        warn "kind:445 failed — C never decrypted sanity-ping (relays may be dropping group messages)"
        warn "Consider rerunning with --local-relays."
      fi
      # best-effort cleanup so re-runs don't accumulate dead sanity groups
      wn_c groups leave "$sanity_c_gid" >/dev/null 2>&1 || true
      wn_b groups leave "$sanity_gid" >/dev/null 2>&1 || true
    else
      warn "kind:10050/1059 failed — C never received welcome; relays likely dropping gift wraps or inbox lists"
      warn "Consider rerunning with --local-relays (requires 'just docker-up' in whitenoise-rs)."
    fi
  fi
}

instruct_amethyst_setup() {
  local list
  if [[ "$USE_LOCAL_RELAYS" -eq 1 ]]; then
    list="  ws://10.0.2.2:8080    (Android emulator)
  ws://<your-LAN-ip>:8080  (physical device on same Wi-Fi)"
  else
    list=$(printf '  %s\n' "${DEFAULT_RELAYS[@]}")
  fi
  prompt_human "Configure Amethyst to match this harness:
  1. Settings -> Relays:  add the following as READ+WRITE
$list
  2. Settings -> Key Package Relays:  add the SAME URLs
  3. Trigger key-package publish (toggle KP relay on/off if needed)
  4. Confirm your Amethyst account is logged in with npub: $A_NPUB"
}

# ==== tests ==================================================================

test_01_keypackage_discovery() {
  banner "Test 01 — KeyPackage publish & discovery (MIP-00)"

  step "B fetches A's KeyPackage from relays"
  local kp_relays kp_raw kp_event_id
  kp_relays=$(wn_b --json relays list 2>/dev/null || wn_b relays list 2>/dev/null || true)
  info "B relay list: $kp_relays"
  printf 'B relay list: %s\n' "$kp_relays" >>"$LOG_FILE"

  kp_raw=$(wn_b --json keys check "$A_NPUB" 2>>"$LOG_FILE" || true)
  printf 'wn keys check raw output: %s\n' "$kp_raw" >>"$LOG_FILE"
  kp_event_id=$(printf '%s' "$kp_raw" | jq -r '.result.event_id // .event_id // empty' 2>/dev/null || true)

  if [[ -n "$kp_event_id" && "$kp_event_id" != "null" ]]; then
    pass_msg "B discovered A's KeyPackage (event_id: $kp_event_id)"
    record_result "01a KeyPackage A->B" pass
  else
    fail_msg "B cannot find A's KeyPackage. Did Amethyst publish to the same relays?"
    info "raw response from wn keys check: $kp_raw"
    record_result "01a KeyPackage A->B" fail "wn keys check returned no event_id"
  fi

  prompt_human "In Amethyst: open 'Create Group' (or 'Add Member' on an existing group).
  Type or paste: $B_NPUB
  Amethyst should resolve the npub and enable 'Add' (KeyPackage discovered)."
  if confirm "Does Amethyst resolve $B_NPUB as having a valid KeyPackage?"; then
    record_result "01b KeyPackage B->A" pass
  else
    record_result "01b KeyPackage B->A" fail "Amethyst did not resolve B"
  fi
}

test_02_amethyst_creates_group() {
  banner "Test 02 — Amethyst creates group, invites B"

  # Baseline dump BEFORE the human triggers the publish in Amethyst.
  # If this diagnostic shows B has no inbox relays (or the inbox relays
  # differ from the ones Amethyst has cached for B's kind:10050 list),
  # the welcome gift wrap will never reach B no matter how correctly
  # Amethyst sends it.
  dump_daemon_diagnostics B "pre-invite baseline"

  prompt_human "In Amethyst:
  1. Tap '+' -> Create Group
  2. Name: Interop-02
  3. Add member: $B_NPUB
  4. Tap Create / Send Invite

Tip: watch the Android logcat for tag 'MarmotDbg' — you should see
'publishing welcome gift wrap id=… kind:1059 → N relay(s): [...]' listing
the same relays as B's inbox above. If the list is empty or different,
that's the bug."

  step "polling B's daemon for invite (60s, heartbeat every ~10s)"
  local gid
  if ! gid=$(wait_for_invite B 60); then
    fail_msg "no invite arrived at B"
    dump_daemon_diagnostics B "post-timeout (test_02)"
    prompt_amethyst_logcat "test_02 timeout"
    record_result "02 Amethyst->B create+invite" fail "invite never arrived"
    return
  fi
  save_state "GROUP_02" "$gid"
  info "group_id: $gid"

  step "B accepts invite"
  if ! wn_b groups accept "$gid" >/dev/null 2>&1; then
    fail_msg "B failed to accept invite"
    record_result "02 Amethyst->B create+invite" fail "accept failed"
    return
  fi

  step "verifying member list on B"
  local members
  members=$(wn_b --json groups members "$gid" 2>/dev/null | jq -r '(.result // .) | .[].pubkey // .[].public_key' 2>/dev/null | sort | tr '\n' ' ')
  if ! expect_contains "$members" "$A_HEX" "member list" \
     || ! expect_contains "$members" "$B_HEX" "member list"; then
      record_result "02 Amethyst->B create+invite" fail "member list mismatch"
      return
  fi

  prompt_human "In Amethyst, send this message in group 'Interop-02':
    hello from amethyst"

  step "B waits for the message (30s)"
  if wait_for_message B "$gid" "hello from amethyst" 30; then
    pass_msg "B decrypted message from Amethyst"
  else
    fail_msg "B never saw 'hello from amethyst'"
    record_result "02 Amethyst->B create+invite" fail "inbound message missing"
    return
  fi

  step "B sends reply"
  wn_b messages send "$gid" "hello from wn" >/dev/null 2>&1 || warn "send returned nonzero"

  if confirm "Does Amethyst now show 'hello from wn' in the same group?"; then
    record_result "02 Amethyst->B create+invite" pass
  else
    record_result "02 Amethyst->B create+invite" fail "Amethyst did not display reply"
  fi
}

test_03_wn_creates_group() {
  banner "Test 03 — B creates group, invites Amethyst"

  step "B creates group 'Interop-03' with A as sole invitee"
  local out gid
  out=$(wn_b --json groups create "Interop-03" "$A_NPUB" 2>>"$LOG_FILE")
  printf 'wn groups create raw output: %s\n' "$out" >>"$LOG_FILE"
  gid=$(printf '%s' "$out" | jq_group_id)
  if [[ -z "$gid" ]]; then
    fail_msg "could not parse group_id from 'wn groups create' output"
    info "raw output: $out"
    record_result "03 B->Amethyst create+invite" fail "no group_id"
    return
  fi
  save_state "GROUP_03" "$gid"
  info "group_id: $gid"

  prompt_human "In Amethyst, you should see a new pending group invite 'Interop-03' from $B_NPUB.
  Accept the invite and open the chat."
  if ! confirm "Did Amethyst accept and open group 'Interop-03'?"; then
    record_result "03 B->Amethyst create+invite" fail "Amethyst did not accept invite"
    return
  fi

  step "B sends: 'ping from wn'"
  wn_b messages send "$gid" "ping from wn" >/dev/null 2>&1 || true

  if ! confirm "Does Amethyst show 'ping from wn'?"; then
    record_result "03 B->Amethyst create+invite" fail "Amethyst did not show wn message"
    return
  fi

  prompt_human "In Amethyst, send: 'pong from amethyst'"
  if wait_for_message B "$gid" "pong from amethyst" 30; then
    record_result "03 B->Amethyst create+invite" pass
  else
    record_result "03 B->Amethyst create+invite" fail "B did not receive pong"
  fi
}

test_04_three_member_group() {
  banner "Test 04 — 3-member group, add C after create"

  step "C publishes KeyPackage so A can discover it"
  wn_c keys publish >/dev/null 2>&1 || true
  sleep 4

  local gid
  gid=$(load_state GROUP_02 || true)
  if [[ -z "${gid:-}" ]]; then
    warn "GROUP_02 not set (Test 02 likely skipped/failed); creating new group here"
    local out
    out=$(wn_b --json groups create "Interop-04-bootstrap" "$A_NPUB" 2>>"$LOG_FILE")
    gid=$(printf '%s' "$out" | jq_group_id)
    save_state GROUP_02 "$gid"
    prompt_human "In Amethyst, accept the new group invite 'Interop-04-bootstrap'."
  fi
  info "using group $gid"

  dump_daemon_diagnostics C "pre-invite baseline (test_04)"

  prompt_human "In Amethyst, open the group from Test 02 (or 'Interop-04-bootstrap').
  Group Info -> Add Member -> paste: $C_NPUB
  Confirm the invite is sent."

  step "polling C for invite (60s, heartbeat every ~10s)"
  local c_gid
  if ! c_gid=$(wait_for_invite C 60); then
    fail_msg "C never received invite"
    dump_daemon_diagnostics C "post-timeout (test_04)"
    prompt_amethyst_logcat "test_04 timeout"
    record_result "04 3-member add-after-create" fail "invite to C missing"
    return
  fi
  wn_c groups accept "$c_gid" >/dev/null 2>&1 || true
  info "C accepted invite ($c_gid)"

  step "verifying all three members on all three clients"
  local members_b
  members_b=$(wn_b --json groups members "$gid" 2>/dev/null | jq -r '(.result // .) | .[].pubkey // .[].public_key' | tr '\n' ' ')
  expect_contains "$members_b" "$A_HEX" "B's member list" || :
  expect_contains "$members_b" "$B_HEX" "B's member list" || :
  expect_contains "$members_b" "$C_HEX" "B's member list" || :

  prompt_human "In Amethyst, send: 'hello three-member world'"

  if wait_for_message B "$gid" "hello three-member world" 30 \
     && wait_for_message C "$c_gid" "hello three-member world" 30; then
    pass_msg "both B and C decrypted Amethyst's message after Add"
    record_result "04 3-member add-after-create" pass
  else
    fail_msg "B or C did not see Amethyst's post-Add message"
    record_result "04 3-member add-after-create" fail "message not received after Add"
  fi
}

test_05_wn_adds_amethyst_existing() {
  banner "Test 05 — wn adds Amethyst to existing B+C group"

  step "B creates group with only C, then adds A"
  local out gid
  out=$(wn_b --json groups create "Interop-05" "$C_NPUB" 2>>"$LOG_FILE")
  printf 'wn groups create raw output: %s\n' "$out" >>"$LOG_FILE"
  gid=$(printf '%s' "$out" | jq_group_id)
  if [[ -z "$gid" ]]; then
    fail_msg "could not create Interop-05"
    record_result "05 wn adds Amethyst existing" fail "create failed"
    return
  fi
  save_state GROUP_05 "$gid"
  if wait_for_invite C 30 >/dev/null; then
    wn_c groups accept "$gid" >/dev/null 2>&1 || true
  fi

  step "B adds A to the group"
  wn_b groups add-members "$gid" "$A_NPUB" >/dev/null 2>&1 || {
    fail_msg "wn groups add-members failed"
    record_result "05 wn adds Amethyst existing" fail "add-members failed"
    return
  }

  prompt_human "In Amethyst, you should see a new invite 'Interop-05'.
  Accept and open the chat."

  prompt_human "In Amethyst, send: 'joined from amethyst'"
  if wait_for_message B "$gid" "joined from amethyst" 30 \
     && wait_for_message C "$gid" "joined from amethyst" 30; then
    record_result "05 wn adds Amethyst existing" pass
  else
    record_result "05 wn adds Amethyst existing" fail "message not seen by B or C"
  fi
}

test_06_member_removal() {
  banner "Test 06 — Member removal, forward secrecy at next epoch"

  local gid
  gid=$(load_state GROUP_05 || true)
  if [[ -z "${gid:-}" ]]; then
    skip_msg "Test 06 requires Test 05 state"; record_result "06 member removal" skip "no GROUP_05"; return
  fi

  prompt_human "In Amethyst, open group 'Interop-05' -> Group Info -> Members.
  Tap C ($C_NPUB) -> Remove.
  Confirm the removal."

  step "verifying C is removed (60s poll)"
  local deadline=$(( $(date +%s) + 60 )) removed=0
  while [[ $(date +%s) -lt $deadline ]]; do
    if ! wn_c --json groups members "$gid" 2>/dev/null \
         | jq -e --arg p "$C_HEX" '(.result // .) | .[]? | select((.pubkey // .public_key) == $p)' >/dev/null 2>&1; then
      removed=1; break
    fi
    sleep 3
  done

  if [[ "$removed" -ne 1 ]]; then
    warn "C still appears to be a member; proceeding anyway"
  fi

  prompt_human "In Amethyst, send: 'after removing C'"
  if wait_for_message B "$gid" "after removing C" 30; then
    info "B still receives messages (expected)"
  else
    fail_msg "B stopped receiving messages (unexpected)"
    record_result "06 member removal" fail "B lost access"; return
  fi

  # C should NOT be able to decrypt the new message
  sleep 5
  if wait_for_message C "$gid" "after removing C" 10; then
    fail_msg "C still decrypted a post-removal message — forward secrecy broken"
    record_result "06 member removal" fail "forward secrecy broken"
  else
    pass_msg "C correctly cannot read post-removal messages"
    record_result "06 member removal" pass
  fi
}

test_07_metadata_rename() {
  banner "Test 07 — Group metadata rename round-trip (MIP-01)"

  local gid
  gid=$(load_state GROUP_02 || true)
  if [[ -z "${gid:-}" ]]; then
    skip_msg "no GROUP_02"; record_result "07 metadata rename" skip "no group state"; return
  fi

  prompt_human "In Amethyst, open group 'Interop-02' -> Group Info -> Edit.
  Rename to: Interop-02-renamed
  Save."

  step "polling B for name change (60s)"
  local deadline=$(( $(date +%s) + 60 )) name=""
  while [[ $(date +%s) -lt $deadline ]]; do
    name=$(wn_b --json groups show "$gid" 2>/dev/null | jq -r '(.result // .) | .name // empty')
    [[ "$name" == "Interop-02-renamed" ]] && break
    sleep 3
  done
  if [[ "$name" == "Interop-02-renamed" ]]; then
    pass_msg "B sees renamed group"
  else
    fail_msg "B still sees name: $name"
    record_result "07 metadata rename" fail "rename did not propagate to B"; return
  fi

  step "B renames back"
  wn_b groups rename "$gid" "Interop-02-reverse" >/dev/null 2>&1 || true

  if confirm "Does Amethyst now show the group name as 'Interop-02-reverse'?"; then
    record_result "07 metadata rename" pass
  else
    record_result "07 metadata rename" fail "Amethyst did not pick up rename"
  fi
}

test_08_admin_promote_demote() {
  banner "Test 08 — Admin promote/demote"

  local gid
  gid=$(load_state GROUP_03 || true)
  if [[ -z "${gid:-}" ]]; then
    skip_msg "no GROUP_03"; record_result "08 admin promote/demote" skip "no group"; return
  fi

  step "B (creator+admin) adds C so we have 3 members"
  wn_c keys publish >/dev/null 2>&1 || true
  sleep 3
  wn_b groups add-members "$gid" "$C_NPUB" >/dev/null 2>&1 || warn "add-members C returned nonzero"
  if wait_for_invite C 30 >/dev/null; then
    wn_c groups accept "$gid" >/dev/null 2>&1 || true
  fi

  step "B promotes A to admin"
  if ! wn_b groups promote "$gid" "$A_NPUB" >/dev/null 2>&1; then
    fail_msg "promote failed"
    record_result "08 admin promote/demote" fail "promote returned nonzero"; return
  fi

  step "verifying admin list contains both B and A"
  local admins
  admins=$(wn_b --json groups admins "$gid" 2>/dev/null | jq -r '(.result // .) | .[].pubkey // .[].public_key // .[]' | tr '\n' ' ')
  if expect_contains "$admins" "$A_HEX" "admin list" && expect_contains "$admins" "$B_HEX" "admin list"; then
    pass_msg "admin promote worked"
  else
    record_result "08 admin promote/demote" fail "admin list missing A or B"; return
  fi

  step "B demotes A"
  wn_b groups demote "$gid" "$A_NPUB" >/dev/null 2>&1 || warn "demote returned nonzero"
  sleep 5
  admins=$(wn_b --json groups admins "$gid" 2>/dev/null | jq -r '(.result // .) | .[].pubkey // .[].public_key // .[]' | tr '\n' ' ')
  if [[ "$admins" != *"$A_HEX"* ]]; then
    pass_msg "demote removed A"
    record_result "08 admin promote/demote" pass
  else
    record_result "08 admin promote/demote" fail "A still admin after demote"
  fi
}

test_09_reply_react_unreact() {
  banner "Test 09 — Reply / react / unreact (inner event kinds 9, 7)"

  local gid
  gid=$(load_state GROUP_02 || true)
  if [[ -z "${gid:-}" ]]; then
    skip_msg "no GROUP_02"; record_result "09 reply/react" skip "no group"; return
  fi

  step "B sends anchor message"
  wn_b messages send "$gid" "anchor for reactions" >/dev/null 2>&1 || true
  sleep 3
  local msg_id
  msg_id=$(wn_b --json messages list "$gid" --limit 10 2>/dev/null \
            | jq -r '(.result // .) | [.[]? | select((.content // .text // "") == "anchor for reactions")][0].id // .[0].event_id // empty' 2>/dev/null || true)
  if [[ -z "$msg_id" || "$msg_id" == "null" ]]; then
    fail_msg "could not find anchor message id"
    record_result "09 reply/react" fail "anchor id missing"; return
  fi
  info "anchor id: $msg_id"

  step "B reacts with 🌮 and then unreacts"
  wn_b messages react "$gid" "$msg_id" "🌮" >/dev/null 2>&1 || true
  sleep 3
  if ! confirm "Does Amethyst show 🌮 reaction on 'anchor for reactions'?"; then
    record_result "09 reply/react" fail "reaction not visible in Amethyst"; return
  fi
  wn_b messages unreact "$gid" "$msg_id" >/dev/null 2>&1 || true
  sleep 3
  if ! confirm "Did Amethyst remove the 🌮 reaction?"; then
    record_result "09 reply/react" fail "unreact not visible"; return
  fi

  step "B replies to the anchor"
  wn_b messages send "$gid" "replying via wn" --reply_to "$msg_id" >/dev/null 2>&1 || true
  sleep 3
  if confirm "Does Amethyst show 'replying via wn' as a threaded reply to the anchor?"; then
    record_result "09 reply/react" pass
  else
    record_result "09 reply/react" fail "reply not threaded"
  fi
}

test_10_concurrent_commits() {
  banner "Test 10 — Concurrent commits (race)"

  local gid
  gid=$(load_state GROUP_02 || true)
  if [[ -z "${gid:-}" ]]; then
    skip_msg "no GROUP_02"; record_result "10 concurrent commits" skip "no group"; return
  fi

  prompt_human "Get ready to trigger two conflicting commits SIMULTANEOUSLY.
  In Amethyst: open group 'Interop-02' -> Group Info -> Edit name.
  Type a new name (e.g. 'race-from-amethyst') but DO NOT SAVE YET.
  Press <Enter> here; the script will immediately issue a conflicting rename from B.
  Then within 1 second tap SAVE in Amethyst."

  ( sleep 0; wn_b groups rename "$gid" "race-from-wn" >/dev/null 2>&1 ) &
  local pid=$!
  info "issued wn rename (pid $pid). Tap SAVE in Amethyst NOW."
  wait "$pid" 2>/dev/null || true

  step "sleeping 10s for both sides to converge"
  sleep 10

  step "verifying B's view is consistent (name + epoch)"
  local b_view
  b_view=$(wn_b --json groups show "$gid" 2>/dev/null | jq '{name, epoch}' 2>/dev/null || echo "{}")
  info "B state: $b_view"

  prompt_human "Read the group name now shown in Amethyst."
  if confirm "Do Amethyst and wn (B view above) agree on the same group name?"; then
    step "post-race message exchange"
    wn_b messages send "$gid" "post-race ping" >/dev/null 2>&1 || true
    if confirm "Does Amethyst show 'post-race ping'? (proves encryption still works)"; then
      record_result "10 concurrent commits" pass
    else
      record_result "10 concurrent commits" fail "encryption broken after race"
    fi
  else
    record_result "10 concurrent commits" fail "diverged state after race"
  fi
}

test_11_leave_group() {
  banner "Test 11 — Leave group"

  local gid
  gid=$(load_state GROUP_02 || true)
  if [[ -z "${gid:-}" ]]; then
    skip_msg "no GROUP_02"; record_result "11 leave group" skip "no group"; return
  fi

  prompt_human "In Amethyst, open group 'Interop-02' -> Group Info -> Leave Group.
  Confirm the action."

  step "verifying B's member list no longer contains A (60s poll)"
  local deadline=$(( $(date +%s) + 60 )) gone=0
  while [[ $(date +%s) -lt $deadline ]]; do
    if ! wn_b --json groups members "$gid" 2>/dev/null \
         | jq -e --arg p "$A_HEX" '(.result // .) | .[]? | select((.pubkey // .public_key) == $p)' >/dev/null 2>&1; then
      gone=1; break
    fi
    sleep 3
  done
  if [[ "$gone" -eq 1 ]]; then
    pass_msg "A removed from B's view of the group"
    record_result "11 leave group" pass
  else
    record_result "11 leave group" fail "A still in member list after leave"
  fi
}

test_12_offline_catchup() {
  banner "Test 12 — Offline catch-up (replay multiple commits)"

  # Use a fresh group for cleanliness
  wn_c keys publish >/dev/null 2>&1 || true
  sleep 3
  local out gid
  out=$(wn_b --json groups create "Interop-12" "$A_NPUB" 2>>"$LOG_FILE")
  printf 'wn groups create raw output: %s\n' "$out" >>"$LOG_FILE"
  gid=$(printf '%s' "$out" | jq_group_id)
  if [[ -z "$gid" ]]; then
    fail_msg "could not create Interop-12"; record_result "12 offline catchup" fail "create failed"; return
  fi
  save_state GROUP_12 "$gid"

  prompt_human "In Amethyst, accept the new invite 'Interop-12' and confirm you see the group."

  prompt_human "Now force-Amethyst offline:
    - Turn on Airplane Mode, OR
    - Force-stop the app from Android settings, OR
    - On emulator: adb shell svc wifi disable
  Press <Enter> when Amethyst is offline."

  step "B sends 5 messages"
  for i in 1 2 3 4 5; do
    wn_b messages send "$gid" "offline-msg-$i" >/dev/null 2>&1 || true
    sleep 1
  done

  step "B adds C, then sends 3 more messages"
  wn_b groups add-members "$gid" "$C_NPUB" >/dev/null 2>&1 || true
  if wait_for_invite C 30 >/dev/null; then
    wn_c groups accept "$gid" >/dev/null 2>&1 || true
  fi
  for i in 6 7 8; do
    wn_b messages send "$gid" "offline-msg-$i" >/dev/null 2>&1 || true
    sleep 1
  done

  step "B renames the group"
  wn_b groups rename "$gid" "Interop-12-renamed" >/dev/null 2>&1 || true

  prompt_human "Now bring Amethyst back online and open group 'Interop-12-renamed'.
  Wait ~10 seconds for replay."

  if confirm "Does Amethyst show all 8 messages (offline-msg-1..8) AND the new name 'Interop-12-renamed' AND C as a member?"; then
    record_result "12 offline catchup" pass
  else
    record_result "12 offline catchup" fail "replay incomplete"
  fi
}

test_13_keypackage_rotation() {
  banner "Test 13 — KeyPackage rotation"

  step "B records A's current KP event id"
  local before
  before=$(wn_b --json keys check "$A_NPUB" 2>/dev/null | jq -r '.result.event_id // .event_id // empty')
  info "A KP before rotation: $before"
  if [[ -z "$before" ]]; then
    fail_msg "no existing KP for A"; record_result "13 keypackage rotation" fail "no KP found"; return
  fi

  prompt_human "In Amethyst, trigger a KeyPackage rotation:
    Option 1: Settings -> Key Package Relays -> toggle OFF all KP relays, then toggle ON.
    Option 2: Restart the Amethyst app (KeyPackageRotationManager runs at startup).
  Press <Enter> when Amethyst has published a new KP."

  step "waiting for a new KP event id at B (60s)"
  local deadline=$(( $(date +%s) + 60 )) after=""
  while [[ $(date +%s) -lt $deadline ]]; do
    after=$(wn_b --json keys check "$A_NPUB" 2>/dev/null | jq -r '.result.event_id // .event_id // empty')
    [[ -n "$after" && "$after" != "$before" ]] && break
    sleep 3
  done
  if [[ -n "$after" && "$after" != "$before" ]]; then
    pass_msg "A's KP rotated: $before -> $after"
    record_result "13 keypackage rotation" pass
  else
    record_result "13 keypackage rotation" fail "no new KP event_id observed"
  fi
}

test_14_push_notifications() {
  banner "Test 14 — Push notifications (MIP-05)"

  if [[ "$ENABLE_TRANSPONDER" -ne 1 ]]; then
    skip_msg "use --transponder to enable"
    record_result "14 push notifications" skip "not requested"
    return
  fi

  local gid
  gid=$(load_state GROUP_12 || load_state GROUP_02 || true)
  if [[ -z "${gid:-}" ]]; then
    skip_msg "no group state available"; record_result "14 push notifications" skip "no group"; return
  fi

  step "B subscribes to notifications"
  wn_b notifications subscribe "$gid" >/dev/null 2>&1 || warn "notifications subscribe returned nonzero"

  prompt_human "In Amethyst, send: 'ping for push'"
  sleep 5
  if confirm "Did B's transponder instance receive a push notification log entry (check $B_DIR/logs/stderr.log)?"; then
    record_result "14 push notifications" pass
  else
    record_result "14 push notifications" fail "no push observed"
  fi
}

# ==== main ===================================================================

main() {
  trap 'stop_daemons; print_summary' EXIT
  banner "Amethyst <-> whitenoise-rs interop harness ($RUN_TS)"

  preflight
  start_daemon B "$B_DIR" "$B_SOCKET"
  start_daemon C "$C_DIR" "$C_SOCKET"
  ensure_identity B
  ensure_identity C
  prompt_for_a_npub
  configure_relays

  # Baseline dump after relays are configured but before any tests run.
  # This is the single most useful log to forward when Test 02 fails —
  # it shows whether B's kind:10050 (inbox) relay list actually landed
  # on the relays Amethyst will look at for the giftwrap delivery target.
  banner "Post-configure baseline diagnostics"
  dump_daemon_diagnostics B "post-configure"
  dump_daemon_diagnostics C "post-configure"

  instruct_amethyst_setup

  test_01_keypackage_discovery
  test_02_amethyst_creates_group
  test_03_wn_creates_group
  test_04_three_member_group
  test_05_wn_adds_amethyst_existing
  test_06_member_removal
  test_07_metadata_rename
  test_08_admin_promote_demote
  test_09_reply_react_unreact
  test_10_concurrent_commits
  test_11_leave_group
  test_12_offline_catchup
  test_13_keypackage_rotation
  test_14_push_notifications
}

main "$@"
