# shellcheck shell=bash
#
# setup.sh — amy-only preflight + identity bootstrap for the
# NIP-17 DM interop harness. Much slimmer than the Marmot setup:
#
#   - Builds `amy` (same retry-on-503 logic as setup.sh). The loopback
#     relay is `amy serve` (geode), so amy is the only binary needed.
#   - Bootstraps two fresh amy identities (A and D), each with its own
#     `--data-dir`, both pointed at the loopback relay.
#   - Publishes kind:10050 (plus NIP-65) for both so NIP-17's strict
#     recipient-inbox routing has something to resolve to.
#
# The `start_local_relay` / `stop_local_relay` helpers (embedded
# `amy serve`) live in ../headless/helpers.sh and are sourced by the
# top-level harness.

# --- preflight (amy only, no wn / Marmot patches, no Rust) -------------------
preflight_dm() {
  banner "Preflight (DM harness)"
  for cmd in jq git curl; do
    if ! command -v "$cmd" >/dev/null 2>&1; then
      fail_msg "missing required tool: $cmd"
      exit 1
    fi
    info "$cmd: $(command -v "$cmd")"
  done

  if [[ ! -x "$AMY_BIN" ]]; then
    if [[ "$NO_BUILD" -eq 1 ]]; then
      fail_msg "amy not found at $AMY_BIN and --no-build set"; exit 1
    fi
    # Gradle + jitpack/dl.google.com occasionally return transient 503s;
    # retry so a single bad roll doesn't tank the whole harness.
    local attempt max=4
    for attempt in $(seq 1 $max); do
      step "building :cli:installDist (attempt $attempt/$max)"
      if ( cd "$REPO_ROOT" && ./gradlew :cli:installDist ) 2>&1 | tee -a "$LOG_FILE" \
          && [[ -x "$AMY_BIN" ]]; then
        break
      fi
      [[ "$attempt" -lt "$max" ]] && warn "gradle build failed — retrying"
    done
  fi
  [[ -x "$AMY_BIN" ]] || { fail_msg "amy still missing after build"; exit 1; }
  info "amy: $AMY_BIN"

}

# --- amy identity wrappers ---------------------------------------------------
# Two identities: A (sender) and D (recipient), both inside the same
# $STATE_DIR/.amy tree. They share $STATE_DIR/.amy/shared/events-store/
# — the same code path real users hit with multiple accounts. The
# enclosing harness sets STATE_DIR to a fresh tempdir per run, so amy
# sees a virgin home each time.
#
# `--secret-backend=plaintext` keeps these throwaway interop runs headless —
# the default `auto` would try the OS keychain (not available in CI) and then
# ask for a NIP-49 passphrase. Plaintext still writes 0600-owner-only.
amy_a() { HOME="$STATE_DIR" "$AMY_BIN" --account A --secret-backend plaintext --json "$@"; }
amy_d() { HOME="$STATE_DIR" "$AMY_BIN" --account D --secret-backend plaintext --json "$@"; }

# --- identity bootstrap ------------------------------------------------------
ensure_identity_for() {
  local who="$1"
  step "initialising Identity $who (amy at \$HOME=$STATE_DIR --account $who)"
  local out
  out=$(HOME="$STATE_DIR" "$AMY_BIN" --account "$who" --secret-backend plaintext --json init) || {
    fail_msg "amy init failed for $who: $out"; exit 1
  }
  local npub hex
  npub=$(printf '%s' "$out" | jq -r '.npub')
  hex=$(printf '%s' "$out" | jq -r '.hex')
  case "$who" in
    A) A_NPUB="$npub"; A_HEX="$hex" ;;
    D) D_NPUB="$npub"; D_HEX="$hex" ;;
  esac
  info "$who npub: $npub"
  info "$who hex:  $hex"
}

# --- relay wiring ------------------------------------------------------------
# Point both identities at the loopback relay (all three buckets:
# nip65 / inbox / key_package), then publish each identity's kind:10050
# so the DM strict-relay routing has something to resolve to.
configure_relays_dm() {
  banner "Configuring relays → $RELAY_URL"
  amy_a relay add "$RELAY_URL" >/dev/null
  amy_d relay add "$RELAY_URL" >/dev/null

  step "publishing A's NIP-65 + kind:10050 lists"
  amy_a relay publish-lists >>"$LOG_FILE" 2>&1 \
    || warn "amy_a relay publish-lists failed"

  step "publishing D's NIP-65 + kind:10050 lists"
  amy_d relay publish-lists >>"$LOG_FILE" 2>&1 \
    || warn "amy_d relay publish-lists failed"
}
