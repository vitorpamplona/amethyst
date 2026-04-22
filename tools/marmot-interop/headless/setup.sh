# shellcheck shell=bash
#
# headless/setup.sh — preflight + daemon lifecycle + identity bootstrap.
# Sourced from marmot-interop-headless.sh.

# --- preflight ---------------------------------------------------------------
preflight() {
  banner "Preflight"
  for cmd in jq git curl cargo protoc patch; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      fail_msg "missing required tool: $cmd"
      case "$cmd" in
        protoc) info "hint: apt-get install protobuf-compiler   (or brew install protobuf on macOS)" ;;
        patch)  info "hint: apt-get install patch" ;;
      esac
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

  # Three harness-only patches to wnd so it runs fully offline / in
  # sandboxes that block outbound + kernel keyring:
  #   1. discovery-env: honour $WHITENOISE_DISCOVERY_RELAYS so we can
  #      point wnd at our loopback relay instead of the baked-in public
  #      set. Without it wnd exits with NoRelayConnections.
  #   2. mock-keyring: honour $WHITENOISE_MOCK_KEYRING so wnd uses the
  #      integration-tests mock keyring store when the kernel keyutils
  #      syscalls are blocked (common in containers / CI).
  #   3. defaults-env: reuse the same env var so `Relay::defaults()`
  #      (what `create-identity` stamps into the new account's NIP-65 /
  #      inbox / key-package lists) points at the loopback relay too.
  #      Without it every account wnd creates carries damus.io /
  #      primal.net / nos.lol, and every later activate / publish burns
  #      connection budget on unreachable sockets — enough to break the
  #      account-inbox subscription plane and drop kind:1059 delivery.
  local -a patches=(
    "whitenoise-discovery-env.patch"
    "whitenoise-mock-keyring.patch"
    "whitenoise-defaults-env.patch"
  )
  for name in "${patches[@]}"; do
    local marker="$WN_REPO/.headless-patched-${name%.patch}"
    if [[ ! -f "$marker" ]]; then
      step "patching whitenoise-rs: $name"
      ( cd "$WN_REPO" && patch -p1 --forward --reject-file=- \
          <"$SCRIPT_DIR/headless/patches/$name" ) 2>&1 | tee -a "$LOG_FILE"
      touch "$marker"
      # Invalidate the previous build so the patched source is picked up.
      rm -f "$WN_BIN" "$WND_BIN"
    fi
  done

  if [[ ! -x "$WN_BIN" || ! -x "$WND_BIN" ]]; then
    if [[ "$NO_BUILD" -eq 1 ]]; then
      fail_msg "wn/wnd not found and --no-build set"; exit 1
    fi
    step "building wn + wnd with integration-tests feature (~5 min first run)"
    ( cd "$WN_REPO" && \
        cargo build --release --features cli,integration-tests --bin wn --bin wnd ) \
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
  # Env vars consumed by the two harness-only wnd patches applied in
  # preflight:
  #   WHITENOISE_DISCOVERY_RELAYS — forces the discovery plane at our
  #     loopback relay (kills the "can't reach nos.lol" exit path).
  #   WHITENOISE_MOCK_KEYRING     — swaps in the integration-tests mock
  #     secret store so wnd doesn't fall over when the kernel blocks
  #     keyutils syscalls.
  # Both are harmless on a real host with connectivity + a real keyring.
  WHITENOISE_DISCOVERY_RELAYS="$RELAY_URL" \
    WHITENOISE_MOCK_KEYRING=1 \
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
    # create-identity sometimes exits non-zero (e.g. transient "failed to
    # connect to any relays") even though the account was created. Probe
    # --json whoami afterwards before giving up.
    "$cmd" create-identity 2>&1 | tee -a "$LOG_FILE" || true
    raw=$("$cmd" --json whoami 2>/dev/null || true)
    npub=$(extract_pubkey "$raw")
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
  # Give nostr-rs-relay a breath to fsync the kind:10002 / 10050 / 30443
  # writes and push them out on the discovery subscription so that the
  # first `wn keys check` that follows actually sees them instead of
  # racing the relay's WAL flush.
  sleep 2
}
