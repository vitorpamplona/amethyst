# shellcheck shell=bash
#
# headless/setup.sh — preflight + daemon lifecycle + identity bootstrap.
# Sourced from marmot-interop-headless.sh.

# --- preflight ---------------------------------------------------------------
preflight() {
  banner "Preflight"
  for cmd in jq git curl cargo; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      fail_msg "missing required tool: $cmd"; exit 1
    fi
    info "$cmd: $(command -v "$cmd")"
  done

  # Build `amy` via gradle if missing.
  if [[ ! -x "$AMY_BIN" ]]; then
    if [[ "$NO_BUILD" -eq 1 ]]; then
      fail_msg "amy not found at $AMY_BIN and --no-build set"; exit 1
    fi
    step "building :cli:installDist"
    ( cd "$REPO_ROOT" && ./gradlew :cli:installDist ) 2>&1 | tee -a "$LOG_FILE"
  fi
  [[ -x "$AMY_BIN" ]] || { fail_msg "amy still missing after build"; exit 1; }
  info "amy: $AMY_BIN"

  # Clone/build whitenoise-rs if needed (shared between both harnesses).
  if [[ ! -x "$WN_BIN" || ! -x "$WND_BIN" ]]; then
    if [[ "$NO_BUILD" -eq 1 ]]; then
      fail_msg "wn/wnd not found and --no-build set"; exit 1
    fi
    if [[ ! -d "$WN_REPO/.git" ]]; then
      step "cloning whitenoise-rs into $WN_REPO"
      git clone --depth 1 https://github.com/marmot-protocol/whitenoise-rs.git "$WN_REPO" \
        2>&1 | tee -a "$LOG_FILE"
    fi
    step "building wn + wnd (~5 min first run)"
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
    info "$name daemon already running"; return 0
  fi
  rm -f "$socket"
  mkdir -p "$data_dir/logs" "$data_dir/release"
  nohup "$WND_BIN" --data-dir "$data_dir" --logs-dir "$data_dir/logs" \
    >"$data_dir/logs/stdout.log" 2>"$data_dir/logs/stderr.log" &
  echo "$!" > "$data_dir/pid"
  local deadline=$(( $(date +%s) + 30 ))
  while [[ $(date +%s) -lt $deadline ]]; do
    if [[ -S "$socket" ]] && "$WN_BIN" --socket "$socket" whoami >/dev/null 2>&1; then
      info "$name ready"; return 0
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
ensure_identity_a() {
  step "initialising Identity A (amy)"
  local out
  out=$(amy_a init) || { fail_msg "amy init failed: $out"; exit 1; }
  A_NPUB=$(printf '%s' "$out" | jq -r '.npub')
  A_HEX=$(printf '%s' "$out" | jq -r '.hex')
  info "A npub: $A_NPUB"
  info "A hex:  $A_HEX"
}

ensure_identity() {
  local who="$1" cmd npub=""
  if [[ "$who" == "B" ]]; then cmd=wn_b; else cmd=wn_c; fi
  step "ensuring Identity $who (wn)"

  local raw
  raw=$("$cmd" --json whoami 2>/dev/null || true)
  npub=$(extract_pubkey "$raw")
  if [[ -z "${npub:-}" ]]; then
    raw=$("$cmd" create-identity 2>&1 | tee -a "$LOG_FILE")
    npub=$(extract_pubkey "$raw")
    [[ -n "$npub" ]] || npub=$(extract_pubkey "$("$cmd" whoami 2>/dev/null || true)")
  fi
  [[ -n "$npub" ]] || { fail_msg "could not determine $who npub"; exit 1; }

  local hex; hex=$(npub_to_hex "$npub")
  if [[ "$who" == "B" ]]; then B_NPUB="$npub"; B_HEX="$hex"
  else                         C_NPUB="$npub"; C_HEX="$hex"; fi
  info "$who npub: $npub"
  [[ "$hex" != "$npub" ]] && info "$who hex:  $hex"
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

  for r in "${relays[@]}"; do
    step "adding $r to A/B/C"
    amy_a relay add "$r" --type all >/dev/null
    for t in nip65 inbox key_package; do
      wn_b relays add --type "$t" "$r" 2>/dev/null || true
      wn_c relays add --type "$t" "$r" 2>/dev/null || true
    done
  done

  # A advertises its NIP-65 + DM inbox lists so B/C can discover where to
  # deliver gift wraps. Without this, cross-client welcomes only work
  # through relay-set overlap — fine for this harness but still worth
  # publishing so we catch regressions in the advertise path.
  step "publishing A's NIP-65 + kind:10050 lists"
  amy_a relay publish-lists >>"$LOG_FILE" 2>&1 || warn "amy relay publish-lists failed"

  step "publishing A's KeyPackage"
  amy_a marmot key-package publish >>"$LOG_FILE" 2>&1 || warn "amy marmot key-package publish failed"
}
