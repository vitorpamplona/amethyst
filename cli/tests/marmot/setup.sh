# shellcheck shell=bash
#
# setup.sh — preflight + daemon lifecycle + identity bootstrap.
# Sourced from marmot-interop-headless.sh.

# --- preflight ---------------------------------------------------------------
preflight() {
  banner "Preflight"
  for cmd in jq git curl cargo protoc; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      fail_msg "missing required tool: $cmd"
      case "$cmd" in
        protoc) info "hint: apt-get install protobuf-compiler   (or brew install protobuf on macOS)" ;;
      esac
      exit 1
    fi
    info "$cmd: $(command -v "$cmd")"
  done

  # Build `amy` via gradle if missing.
  #
  # jitpack.io and dl.google.com both return transient 503s on a non-trivial
  # fraction of cold-cache fetches, and Gradle disables the entire repository
  # for the rest of the build the moment a single 503 lands — so one bad
  # roll aborts the whole harness. Retry a few times; each attempt resumes
  # from Gradle's cache so only the still-missing artifacts get re-fetched.
  if [[ ! -x "$AMY_BIN" ]]; then
    if [[ "$NO_BUILD" -eq 1 ]]; then
      fail_msg "amy not found at $AMY_BIN and --no-build set"; exit 1
    fi
    local attempt max_attempts=4
    for attempt in $(seq 1 $max_attempts); do
      step "building :cli:installDist (attempt $attempt/$max_attempts)"
      if ( cd "$REPO_ROOT" && ./gradlew :cli:installDist ) 2>&1 | tee -a "$LOG_FILE" \
          && [[ -x "$AMY_BIN" ]]; then
        break
      fi
      [[ "$attempt" -lt "$max_attempts" ]] && warn "gradle build failed (likely transient jitpack/Google 503) — retrying"
    done
  fi
  [[ -x "$AMY_BIN" ]] || { fail_msg "amy still missing after build"; exit 1; }
  info "amy: $AMY_BIN"

  # Clone/build the MDK reference client if needed (shared between both
  # harnesses).
  #
  # This used to point at marmot-protocol/whitenoise-rs. That repository was
  # archived on 2026-08-05 ("This repository is obsolete and is no longer
  # updated") pinned to mdk-core 0.8.0, and wn/wnd moved into
  # marmot-protocol/mdk as the `wn-cli` package. Pointing the harness at the
  # dead repo tested us against a frozen MIP-era client, which is exactly the
  # blind spot that let our implementation drift off the adopted spec.
  if [[ ! -d "$WN_REPO/.git" ]]; then
    if [[ "$NO_BUILD" -eq 1 ]]; then
      fail_msg "mdk checkout missing at $WN_REPO and --no-build set"; exit 1
    fi
    step "cloning mdk into $WN_REPO"
    git clone --depth 1 https://github.com/marmot-protocol/mdk.git "$WN_REPO" \
      2>&1 | tee -a "$LOG_FILE"
  fi

  # No source patches. The harness used to carry two against whitenoise-rs:
  #
  #   1. mock-keyring, so wnd could run where the kernel keyring is blocked.
  #      MDK replaces this with a native flag: `--secret-store file` keeps
  #      account secrets in files under the data dir instead of the OS
  #      keychain. start_daemon passes it.
  #   2. skip-unprocessable-retry, which made terminal MLS errors stop
  #      retrying. That patched `src/whitenoise/event_processor/`, a path MDK
  #      does not have. If MDK's retry behaviour turns out to stall this
  #      harness the same way, that is a fresh diagnosis against MDK's own
  #      code, not a patch to port.
  #
  # cargo's transitive deps (rustup, crates.io) both return 503 on cold
  # caches often enough that a single attempt fails ~30% of the time.
  # Retry each cargo build until the binary actually exists or we've
  # exhausted the budget — the build is incremental so retries are cheap.
  if [[ ! -x "$WN_BIN" || ! -x "$WND_BIN" ]]; then
    if [[ "$NO_BUILD" -eq 1 ]]; then
      fail_msg "wn/wnd not found and --no-build set"; exit 1
    fi
    local attempt max=4
    for attempt in $(seq 1 $max); do
      step "building wn + wnd (attempt $attempt/$max, ~5 min first run)"
      ( cd "$WN_REPO" && \
          cargo build --release -p wn-cli --bin wn --bin wnd ) \
        2>&1 | tee -a "$LOG_FILE"
      [[ -x "$WN_BIN" && -x "$WND_BIN" ]] && break
      [[ "$attempt" -lt "$max" ]] && warn "wn/wnd build failed (likely transient 503 from rustup or crates.io) — retrying"
    done
    [[ -x "$WN_BIN" && -x "$WND_BIN" ]] || {
      fail_msg "wn/wnd still missing after $max build attempts"; exit 1
    }
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
    local attempt max=4
    for attempt in $(seq 1 $max); do
      step "building nostr-rs-relay (attempt $attempt/$max, ~3 min first run)"
      ( cd "$RELAY_REPO" && cargo build --release --bin nostr-rs-relay ) \
        2>&1 | tee -a "$LOG_FILE"
      [[ -x "$RELAY_BIN" ]] && break
      [[ "$attempt" -lt "$max" ]] && warn "nostr-rs-relay build failed (likely transient 503 from crates.io) — retrying"
    done
    [[ -x "$RELAY_BIN" ]] || {
      fail_msg "nostr-rs-relay still missing after $max build attempts"; exit 1
    }
  fi
  info "relay bin: $RELAY_BIN"
}

# --- local QUIC broker -------------------------------------------------------
# MDK's own `marmot-quic-broker`, the reference implementation of the other
# side of `transports/quic.md`. Agent text stream previews are the only tests
# that need it, and they are the only way to know our binding is right — the
# ALPN, the control envelope, the frame prefix and the record key schedule all
# have to agree with an implementation that is not ours.
#
# `--replay-ttl-secs` is what lets a subscriber that connects after the
# records were pushed still see them; with the default 0 a test would have to
# race the publisher.
start_quic_broker() {
  if [[ ! -x "$BROKER_BIN" ]]; then
    info "marmot-quic-broker not built — agent text stream tests will skip"
    return 1
  fi
  step "starting QUIC broker on $BROKER_HOST:$BROKER_PORT"
  mkdir -p "$STATE_DIR/broker"
  nohup "$BROKER_BIN" --bind "$BROKER_HOST:$BROKER_PORT" --replay-ttl-secs 60 --json \
    >"$STATE_DIR/broker/stdout.log" 2>"$STATE_DIR/broker/stderr.log" &
  BROKER_PID=$!
  local deadline=$(( $(date +%s) + 15 ))
  while [[ $(date +%s) -lt $deadline ]]; do
    if grep -q '"local_addr"' "$STATE_DIR/broker/stdout.log" 2>/dev/null; then
      info "broker pid $BROKER_PID ready"
      return 0
    fi
    if ! kill -0 "$BROKER_PID" 2>/dev/null; then break; fi
    sleep 1
  done
  fail_msg "broker never came up (see $STATE_DIR/broker/stderr.log)"
  tail -n 20 "$STATE_DIR/broker/stderr.log" 2>/dev/null | sed 's/^/  /' >&2 || true
  BROKER_PID=""
  return 1
}

# --- blossom blob store ------------------------------------------------------
# A loopback Blossom server for the encrypted-media tests. Both implementations
# upload ciphertext to it and fetch each other's back; it never sees a key.
start_blossom() {
  if ! command -v python3 >/dev/null 2>&1; then
    info "python3 not found — encrypted-media tests will skip"
    return 1
  fi
  step "starting blossom blob store on $BLOSSOM_URL"
  mkdir -p "$STATE_DIR/blossom/blobs"
  nohup python3 "$SCRIPT_DIR/blossom-server.py" \
    --host "$BLOSSOM_HOST" --port "$BLOSSOM_PORT" --dir "$STATE_DIR/blossom/blobs" \
    >"$STATE_DIR/blossom/stdout.log" 2>"$STATE_DIR/blossom/stderr.log" &
  BLOSSOM_PID=$!
  local deadline=$(( $(date +%s) + 15 ))
  while [[ $(date +%s) -lt $deadline ]]; do
    if grep -q '"ready"' "$STATE_DIR/blossom/stdout.log" 2>/dev/null; then
      info "blossom pid $BLOSSOM_PID ready"
      return 0
    fi
    if ! kill -0 "$BLOSSOM_PID" 2>/dev/null; then break; fi
    sleep 1
  done
  fail_msg "blossom never came up (see $STATE_DIR/blossom/stderr.log)"
  tail -n 20 "$STATE_DIR/blossom/stderr.log" 2>/dev/null | sed 's/^/  /' >&2 || true
  BLOSSOM_PID=""
  return 1
}

stop_blossom() {
  [[ -n "${BLOSSOM_PID:-}" ]] || return 0
  step "stopping blossom pid $BLOSSOM_PID"
  kill "$BLOSSOM_PID" 2>/dev/null || true
  BLOSSOM_PID=""
}

stop_quic_broker() {
  [[ -n "${BROKER_PID:-}" ]] || return 0
  step "stopping broker pid $BROKER_PID"
  kill "$BROKER_PID" 2>/dev/null || true
  BROKER_PID=""
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
address = "${RELAY_HOST:-127.0.0.1}"
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
    if curl -sSf -m 1 "http://${RELAY_HOST:-127.0.0.1}:$RELAY_PORT/" >/dev/null 2>&1; then
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
  # Start every daemon from a cold data dir. A stale SQLite database whose
  # matching secret is gone leaves wnd unable to open its store, and it then
  # bails before it can even create the socket. The identities here are
  # disposable, so wiping is always the right move. Logs and the pid file are
  # preserved for post-mortem.
  if [[ -d "$data_dir" ]]; then
    find "$data_dir" -mindepth 1 -maxdepth 1 \
      ! -name 'logs' ! -name 'pid' \
      -exec rm -rf {} + 2>/dev/null || true
  fi
  mkdir -p "$data_dir/logs"
  # MDK refuses to create its socket if the socket's parent directory is
  # group-writable or world-accessible ("unsafe on-disk permissions"). A default
  # umask gives 0755, so tighten it explicitly rather than depending on whatever
  # umask the caller's shell happens to have.
  chmod 700 "$data_dir"
  # --discovery-relays / --default-account-relays are native wnd flags that
  # force both the discovery plane and freshly-created accounts' NIP-65 / inbox
  # / key-package lists onto our loopback relay (kills the "can't reach nos.lol"
  # exit path and stops accounts from carrying unreachable public relays).
  #
  # --socket pins the listen path instead of letting wnd derive it. MDK derives
  # it as {home}/dev/wnd.sock, whitenoise-rs used {data_dir}/{profile}/wnd.sock;
  # passing it explicitly makes the harness independent of that choice.
  #
  # --secret-store file replaces the old mock-keyring source patch: account
  # secrets live in files under the data dir, so the daemon comes up in
  # containers and CI where the kernel keyring is unavailable.
  #
  nohup "$WND_BIN" --data-dir "$data_dir" --logs-dir "$data_dir/logs" \
      --socket "$socket" --secret-store file \
      --discovery-relays "$RELAY_URL" --default-account-relays "$RELAY_URL" \
      >"$data_dir/logs/stdout.log" 2>"$data_dir/logs/stderr.log" &
  local pid=$!
  echo "$pid" > "$data_dir/pid"
  info "$name pid $pid; waiting for socket at $socket …"
  local deadline=$(( $(date +%s) + 30 ))
  while [[ $(date +%s) -lt $deadline ]]; do
    if [[ -S "$socket" ]] && "$WN_BIN" --socket "$socket" whoami >/dev/null 2>&1; then
      info "$name ready"; return 0
    fi
    # Exit early if wnd already crashed — no point waiting the full 30s.
    if ! kill -0 "$pid" 2>/dev/null; then
      break
    fi
    sleep 1
  done
  # Dump the actual failure so the operator doesn't have to chase a path.
  if kill -0 "$pid" 2>/dev/null; then
    fail_msg "$name daemon still running (pid $pid) but socket $socket never appeared within 30s"
    kill "$pid" 2>/dev/null || true
  else
    fail_msg "$name daemon (pid $pid) exited before creating socket $socket"
  fi
  if [[ -s "$data_dir/logs/stderr.log" ]]; then
    printf '  --- last 40 lines of %s ---\n' "$data_dir/logs/stderr.log" >&2
    tail -n 40 "$data_dir/logs/stderr.log" | sed 's/^/  /' >&2
    printf '  --- end stderr ---\n' >&2
  else
    info "stderr log is empty at $data_dir/logs/stderr.log"
  fi
  if [[ -s "$data_dir/logs/stdout.log" ]]; then
    printf '  --- last 20 lines of %s ---\n' "$data_dir/logs/stdout.log" >&2
    tail -n 20 "$data_dir/logs/stdout.log" | sed 's/^/  /' >&2
    printf '  --- end stdout ---\n' >&2
  fi
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
  amy_a relay add "$RELAY_URL" >/dev/null
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
