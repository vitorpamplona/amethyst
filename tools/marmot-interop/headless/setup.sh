# shellcheck shell=bash
#
# headless/setup.sh — preflight + daemon lifecycle + identity bootstrap.
# Sourced from marmot-interop-headless.sh.

# --- preflight ---------------------------------------------------------------
preflight() {
  banner "Preflight"
  for cmd in jq git curl cargo protoc; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      fail_msg "missing required tool: $cmd"
      # protoc is a build-time dep of nostr-rs-relay — give a hint.
      if [[ "$cmd" == "protoc" ]]; then
        info "hint: apt-get install protobuf-compiler   (or brew install protobuf on macOS)"
      fi
      exit 1
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
  if [[ ! -d "$WN_REPO/.git" ]]; then
    if [[ "$NO_BUILD" -eq 1 ]]; then
      fail_msg "whitenoise-rs checkout missing at $WN_REPO and --no-build set"; exit 1
    fi
    step "cloning whitenoise-rs into $WN_REPO"
    git clone --depth 1 https://github.com/marmot-protocol/whitenoise-rs.git "$WN_REPO" \
      2>&1 | tee -a "$LOG_FILE"
  fi

  # Patch wnd's hardcoded discovery relays so $WHITENOISE_DISCOVERY_RELAYS
  # wins — without this the daemon exits immediately in sandboxes / offline
  # environments where public relays are unreachable.
  local patch_file="$SCRIPT_DIR/headless/patches/whitenoise-discovery-env.patch"
  local patch_marker="$WN_REPO/.headless-discovery-patched"
  if [[ ! -f "$patch_marker" ]]; then
    step "patching whitenoise-rs: honour WHITENOISE_DISCOVERY_RELAYS"
    ( cd "$WN_REPO" && patch -p1 --forward --reject-file=- <"$patch_file" ) \
      2>&1 | tee -a "$LOG_FILE"
    touch "$patch_marker"
    # Invalidate the previous build so the patched source is picked up.
    rm -f "$WN_BIN" "$WND_BIN"
  fi

  if [[ ! -x "$WN_BIN" || ! -x "$WND_BIN" ]]; then
    if [[ "$NO_BUILD" -eq 1 ]]; then
      fail_msg "wn/wnd not found and --no-build set"; exit 1
    fi
    step "building wn + wnd (~5 min first run; incremental thereafter)"
    ( cd "$WN_REPO" && cargo build --release --features cli --bin wn --bin wnd ) \
      2>&1 | tee -a "$LOG_FILE"
  fi
  info "wn:  $WN_BIN"
  info "wnd: $WND_BIN"

  # Clone/build nostr-rs-relay — the harness's single loopback relay.
  if [[ ! -x "$RELAY_BIN" ]]; then
    if [[ "$NO_BUILD" -eq 1 ]]; then
      fail_msg "nostr-rs-relay not found at $RELAY_BIN and --no-build set"; exit 1
    fi
    if [[ ! -d "$RELAY_REPO/.git" ]]; then
      step "cloning nostr-rs-relay into $RELAY_REPO"
      git clone --depth 1 https://github.com/scsibug/nostr-rs-relay "$RELAY_REPO" \
        2>&1 | tee -a "$LOG_FILE"
    fi
    step "building nostr-rs-relay (~3 min first run)"
    ( cd "$RELAY_REPO" && cargo build --release --bin nostr-rs-relay ) \
      2>&1 | tee -a "$LOG_FILE"
  fi
  info "relay bin: $RELAY_BIN"
}

# --- local relay -------------------------------------------------------------
# Start nostr-rs-relay on $RELAY_PORT with a minimal config. Every test
# runs against this one loopback endpoint — no external network traffic.
start_local_relay() {
  banner "Starting local nostr-rs-relay on $RELAY_URL"
  mkdir -p "$RELAY_DATA" "$RELAY_DATA/logs"

  # Render a minimal config file each run so port/limits come from the
  # harness rather than whatever was left on disk from a previous session.
  cat >"$RELAY_DATA/config.toml" <<EOF
[info]
relay_url = "$RELAY_URL"
name = "amethyst-headless-harness"
description = "Loopback relay for marmot-interop-headless.sh — do not use for anything real."

[database]
data_directory = "$RELAY_DATA"

[network]
address = "127.0.0.1"
port = $RELAY_PORT

[options]
reject_future_seconds = 3600

[limits]
# Keep kind:444 / 445 / 1059 / 30443 wide open — the whole point is
# exercising Marmot traffic the public relays reject.
max_event_bytes = 524288
max_ws_message_bytes = 1048576
max_ws_frame_bytes = 1048576
EOF

  # Abort early if something else is already bound to the port — failing
  # with a clear error beats a mysterious-looking daemon stall later.
  if ss -ltn 2>/dev/null | awk '{print $4}' | grep -qE "[:.]$RELAY_PORT\$"; then
    fail_msg "port $RELAY_PORT already in use — pass --port N or free it"
    exit 1
  fi

  nohup "$RELAY_BIN" --db "$RELAY_DATA" --config "$RELAY_DATA/config.toml" \
    >"$RELAY_DATA/logs/stdout.log" 2>"$RELAY_DATA/logs/stderr.log" &
  echo "$!" > "$RELAY_DATA/pid"
  step "relay pid $(cat "$RELAY_DATA/pid"); waiting for $RELAY_URL …"

  local deadline=$(( $(date +%s) + 20 ))
  while [[ $(date +%s) -lt $deadline ]]; do
    if curl -sSf -m 1 "http://127.0.0.1:$RELAY_PORT/" >/dev/null 2>&1; then
      info "relay up"
      return 0
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

# --- daemons -----------------------------------------------------------------
start_daemon() {
  local name="$1" data_dir="$2" socket="$3"
  step "starting $name daemon"
  if [[ -S "$socket" ]] && "$WN_BIN" --socket "$socket" whoami >/dev/null 2>&1; then
    info "$name daemon already running"; return 0
  fi
  rm -f "$socket"
  mkdir -p "$data_dir/logs" "$data_dir/release"
  # Point wnd's discovery plane at our loopback relay. Needs the env-var
  # patch applied in preflight to take effect; without it wnd falls back
  # to the baked-in public set and exits with NoRelayConnections.
  WHITENOISE_DISCOVERY_RELAYS="$RELAY_URL" \
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
# Point all three identities at the loopback relay. We never publish any
# test traffic off-box — public relays reject kind:445 anyway and the
# goal here is tight, deterministic iteration.
configure_relays() {
  banner "Configuring relays → $RELAY_URL"
  amy_a relay add "$RELAY_URL" --type all >/dev/null
  for t in nip65 inbox key_package; do
    wn_b relays add --type "$t" "$RELAY_URL" 2>/dev/null || true
    wn_c relays add --type "$t" "$RELAY_URL" 2>/dev/null || true
  done

  # A advertises its NIP-65 + DM inbox lists so B/C can discover where to
  # deliver gift wraps. With a single shared relay the lookup is trivial
  # but we still publish so we catch regressions in the advertise path.
  step "publishing A's NIP-65 + kind:10050 lists"
  amy_a relay publish-lists >>"$LOG_FILE" 2>&1 || warn "amy relay publish-lists failed"

  step "publishing A's KeyPackage"
  amy_a marmot key-package publish >>"$LOG_FILE" 2>&1 || warn "amy marmot key-package publish failed"
}
