# shellcheck shell=bash
#
# tests-dm.sh — NIP-17 DM interop tests for two `amy` clients.
#
# Identity A (sender) and Identity D (recipient) each live in their own
# --data-dir and share one loopback nostr-rs-relay. Tests cover:
#
#   dm-01   text round-trip (both directions)
#   dm-02   dm list surfaces prior exchange with type:text discriminator
#   dm-03   strict kind:10050 routing refuses sends to inboxless recipient
#   dm-04   --allow-fallback opts into the NIP-65 read / bootstrap chain
#   dm-05   file message reference mode round-trip (kind:15)
#   dm-06   cursor advance (subsequent no-flag `dm list` is empty)

# Wrap amy_json around either account so the per-test code stays tight.
# Both share $HOME=$STATE_DIR (set by the harness); --name picks the
# account inside it. `--json` opts into amy's machine-readable contract;
# assertions below parse with jq.
amy_json_for() {
  local account="$1"; shift
  local out
  if ! out=$(HOME="$STATE_DIR" "$AMY_BIN" --name "$account" --secret-backend plaintext --json "$@" 2>>"$LOG_FILE"); then
    fail_msg "amy --name $account $*: exit $? (see $LOG_FILE)"
    printf '%s\n' "$out" >>"$LOG_FILE"
    return 1
  fi
  printf '%s' "$out"
}

amy_json_a() { amy_json_for A "$@"; }
amy_json_d() { amy_json_for D "$@"; }

test_01_dm_text_round_trip() {
  banner "DM-01 — text round-trip A↔D (kind:14)"
  local id="dm-01 text round-trip"

  # A → D
  local out_send
  out_send=$(amy_json_a dm send "$D_NPUB" "hello from A") || {
    record_result "$id" fail "A send failed"; return
  }
  local event_id recipients_ok
  event_id=$(printf '%s' "$out_send" | jq -r '.event_id')
  recipients_ok=$(printf '%s' "$out_send" \
    | jq -r '[.recipients[] | select(.relay_source == "kind_10050")] | length')
  info "A sent kind:14 event_id=$event_id (to ${recipients_ok} kind:10050 inboxes)"
  if [[ -z "$event_id" || "$event_id" == "null" ]]; then
    record_result "$id" fail "A send response missing event_id"; return
  fi

  # D awaits the text
  if ! amy_json_d dm await --peer "$A_NPUB" --match "hello from A" --timeout 30 >/dev/null; then
    record_result "$id" fail "D never received A's text"; return
  fi
  info "D received A's text"

  # D → A
  amy_json_d dm send "$A_NPUB" "reply from D" >/dev/null || {
    record_result "$id" fail "D send failed"; return
  }
  if ! amy_json_a dm await --peer "$D_NPUB" --match "reply from D" --timeout 30 >/dev/null; then
    record_result "$id" fail "A never received D's reply"; return
  fi
  info "A received D's reply"

  record_result "$id" pass
}

test_02_dm_list_surfaces_history() {
  banner "DM-02 — dm list returns text messages with type discriminator"
  local id="dm-02 dm list text history"

  local out
  out=$(amy_json_a dm list --peer "$D_NPUB" --timeout 5) || {
    record_result "$id" fail "amy dm list failed"; return
  }
  local text_count reply_present
  text_count=$(printf '%s' "$out" \
    | jq -r '[.messages[] | select(.type == "text")] | length')
  reply_present=$(printf '%s' "$out" \
    | jq -r '[.messages[] | select(.content == "reply from D")] | length')
  info "A sees $text_count text message(s) with D; reply_present=$reply_present"
  if [[ "$reply_present" == "1" ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "D's reply missing from A's dm list"
  fi
}

test_03_dm_send_rejects_no_inbox() {
  banner "DM-03 — strict kind:10050 refuses sends to inboxless recipient"
  local id="dm-03 strict no_dm_relays"

  # Generate a throwaway identity but do NOT publish its kind:10050.
  # The ghost lives in its own fake $HOME so it doesn't pollute the
  # test's main STATE_DIR with a third account.
  local ghost_home; ghost_home=$(mktemp -d "${STATE_DIR}/ghost-home.XXXXXX")
  local ghost_out ghost_npub
  ghost_out=$(HOME="$ghost_home" "$AMY_BIN" --name ghost --secret-backend plaintext --json init) || {
    record_result "$id" fail "ghost init failed"; rm -rf "$ghost_home"; return
  }
  ghost_npub=$(printf '%s' "$ghost_out" | jq -r '.npub')
  info "ghost: $ghost_npub (no relays advertised)"

  # A sends without --allow-fallback; amy should refuse.
  local raw rc
  raw=$(HOME="$STATE_DIR" "$AMY_BIN" --name A --secret-backend plaintext --json dm send "$ghost_npub" "should be rejected" 2>&1)
  rc=$?
  rm -rf "$ghost_home"
  if [[ "$rc" -ne 0 ]] && printf '%s' "$raw" | grep -q '"error":"no_dm_relays"'; then
    info "amy refused with no_dm_relays as expected (exit $rc)"
    record_result "$id" pass
  else
    fail_msg "expected no_dm_relays error; got rc=$rc raw=$raw"
    record_result "$id" fail "send to inboxless recipient did not refuse"
  fi
}

test_04_dm_send_allow_fallback() {
  banner "DM-04 — --allow-fallback opts into NIP-65 read / bootstrap chain"
  local id="dm-04 allow-fallback"

  # Same ghost pattern as dm-03. With --allow-fallback, resolution should
  # fall through kind:10050 → NIP-65 read → bootstrap. Our bootstrap set
  # always includes the loopback relay via the shared RelayConfig, so the
  # publish should succeed even though the ghost has no 10050.
  local ghost_home; ghost_home=$(mktemp -d "${STATE_DIR}/ghost-home.XXXXXX")
  local ghost_out ghost_npub
  ghost_out=$(HOME="$ghost_home" "$AMY_BIN" --name ghost --secret-backend plaintext --json init) || {
    record_result "$id" fail "ghost init failed"; rm -rf "$ghost_home"; return
  }
  ghost_npub=$(printf '%s' "$ghost_out" | jq -r '.npub')
  rm -rf "$ghost_home"

  local out source
  out=$(amy_json_a dm send "$ghost_npub" "hi via fallback" --allow-fallback) || {
    record_result "$id" fail "send with --allow-fallback failed"; return
  }
  # The ghost's own wrap will land in the recipients list; its source
  # should be bootstrap (no 10050, no 10002) — which confirms the
  # fallback actually fired.
  source=$(printf '%s' "$out" \
    | jq -r --arg pk "${ghost_npub}" \
        '.recipients[] | select(.pubkey != $pk) | .relay_source' \
    | head -1)
  info "relay_source for ghost wrap: $source"
  if printf '%s' "$out" | jq -e '[.recipients[] | .relay_source] | index("bootstrap")' >/dev/null \
     || printf '%s' "$out" | jq -e '[.recipients[] | .relay_source] | index("nip65_read")' >/dev/null; then
    record_result "$id" pass
  else
    record_result "$id" fail "no recipient reported a fallback relay_source"
  fi
}

test_05_dm_file_reference_round_trip() {
  banner "DM-05 — file message reference mode (kind:15) round-trip"
  local id="dm-05 file reference round-trip"

  # 64 hex chars = 32-byte AES-GCM key; 32 hex chars = 16-byte nonce.
  local key="0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
  local nonce="fedcba9876543210fedcba9876543210"
  local url="https://example.test/blob/test-dm-05.bin"
  local mime="application/octet-stream"

  amy_json_a dm send-file "$D_NPUB" "$url" \
    --key "$key" --nonce "$nonce" --mime-type "$mime" --size 1024 >/dev/null || {
    record_result "$id" fail "A send-file failed"; return
  }
  info "A published kind:15 for $url"

  # D awaits — `dm await --match` searches URL for kind:15.
  if ! amy_json_d dm await --peer "$A_NPUB" --match "$url" --timeout 30 >/dev/null; then
    record_result "$id" fail "D never received the file message"; return
  fi

  # Verify the recovered JSON carries all crypto material.
  local raw
  raw=$(amy_json_d dm list --peer "$A_NPUB" --timeout 5) || {
    record_result "$id" fail "D dm list failed"; return
  }
  local fields
  fields=$(printf '%s' "$raw" | jq -r \
    '[.messages[] | select(.type == "file" and .url == "'"$url"'")][0]')
  if [[ -z "$fields" || "$fields" == "null" ]]; then
    record_result "$id" fail "D did not decode a kind:15 matching the URL"; return
  fi

  local got_key got_nonce got_mime
  got_key=$(printf '%s' "$fields" | jq -r '.decryption_key')
  got_nonce=$(printf '%s' "$fields" | jq -r '.decryption_nonce')
  got_mime=$(printf '%s' "$fields" | jq -r '.mime_type')
  info "D decoded key=${got_key:0:16}… nonce=${got_nonce:0:16}… mime=$got_mime"
  assert_eq "$got_key" "$key" "$id" "decryption_key mismatch" || return
  assert_eq "$got_nonce" "$nonce" "$id" "decryption_nonce mismatch" || return
  assert_eq "$got_mime" "$mime" "$id" "mime_type mismatch" || return

  record_result "$id" pass
}

test_06_dm_list_since_filter() {
  banner "DM-06 — --since filters out older messages"
  local id="dm-06 since filter"

  # Baseline: a --peer query (stateless) should surface every message so
  # far. We need at least one to have a meaningful window to slide past.
  local baseline baseline_count
  baseline=$(amy_json_d dm list --peer "$A_NPUB" --timeout 5) || {
    record_result "$id" fail "baseline dm list failed"; return
  }
  baseline_count=$(printf '%s' "$baseline" | jq -r '.messages | length')
  info "baseline messages with A: $baseline_count"
  if [[ "$baseline_count" -lt 1 ]]; then
    record_result "$id" fail "no messages to filter — earlier tests didn't populate"; return
  fi

  # Now query with --since set to a timestamp 2 days after the newest
  # message. filterGiftWrapsToPubkey subtracts its own two-day lookback
  # window, so the effective `since` lands right after the newest event
  # — nothing should come back.
  local newest
  newest=$(printf '%s' "$baseline" | jq -r '[.messages[].created_at] | max')
  local future=$(( newest + 2 * 24 * 60 * 60 + 3600 ))
  local after after_count
  after=$(amy_json_d dm list --peer "$A_NPUB" --since "$future" --timeout 5) || {
    record_result "$id" fail "since-query failed"; return
  }
  after_count=$(printf '%s' "$after" | jq -r '.messages | length')
  info "after --since $future: $after_count message(s)"

  if [[ "$after_count" -eq 0 ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "expected 0 after future --since; got $after_count"
  fi
}
