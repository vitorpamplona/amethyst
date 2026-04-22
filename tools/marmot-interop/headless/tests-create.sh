# shellcheck shell=bash
#
# headless/tests-create.sh — tests 01..05.
# Focus: KeyPackage discovery, group creation, invites, initial messages.

test_01_keypackage_discovery() {
  banner "Test 01 — KeyPackage publish & discovery (MIP-00)"
  local id="01 KeyPackage A<->B"

  # B finds A's KP
  local raw ev
  raw=$(wn_b --json keys check "$A_NPUB" 2>>"$LOG_FILE" || true)
  ev=$(printf '%s' "$raw" | jq -r '.result.event_id // .event_id // empty')
  if [[ -z "$ev" || "$ev" == "null" ]]; then
    record_result "$id (B->A)" fail "wn couldn't find A's KP"; return
  fi
  info "B saw A's KP $ev"

  # A finds B's KP (wn publishes automatically via create-identity flow? if not, ask).
  wn_b keys publish >>"$LOG_FILE" 2>&1 || warn "wn_b keys publish failed"
  sleep 3
  local out
  out=$(amy_json marmot key-package check "$B_NPUB" 2>/dev/null || true)
  if [[ -z "$out" ]]; then
    record_result "$id (A->B)" fail "amy couldn't find B's KP"; return
  fi
  info "A saw B's KP $(printf '%s' "$out" | jq -r .event_id)"

  record_result "$id" pass
}

test_02_a_creates_group() {
  banner "Test 02 — A creates group, invites B"
  local id="02 A->B create+invite"

  # amy keys groups by the MIP-01 nostr_group_id (`group_id`), wn (via
  # mdk-core) keys by the MLS GroupContext groupId (`mls_group_id`). Both
  # are 32 random bytes and they are NOT the same. We need both: amy calls
  # use `gid`, wn calls use `mls_gid`.
  local out gid mls_gid
  out=$(amy_json marmot group create --name "Interop-02") || {
    record_result "$id" fail "create returned no group_id"; return
  }
  gid=$(printf '%s' "$out" | jq -r '.group_id')
  mls_gid=$(printf '%s' "$out" | jq -r '.mls_group_id')
  save_state GROUP_02 "$gid"
  save_state GROUP_02_MLS "$mls_gid"
  info "created group nostr=$gid mls=$mls_gid"

  amy_json marmot group add "$gid" "$B_NPUB" >/dev/null || {
    record_result "$id" fail "amy group add failed"; return
  }

  # B waits for invite, accepts.
  local b_gid
  if ! b_gid=$(wait_for_invite B 60); then
    record_result "$id" fail "B never received invite"; return
  fi
  wn_b groups accept "$b_gid" >/dev/null 2>&1 || true
  info "B joined $b_gid"

  # A -> B message. amy takes the nostr id; wait_for_message calls
  # `wn messages list` which needs the MLS id.
  amy_json marmot message send "$gid" "hello from amethyst" >/dev/null || {
    record_result "$id" fail "amy send failed"; return
  }
  if ! wait_for_message B "$mls_gid" "hello from amethyst" 90; then
    record_result "$id" fail "B didn't receive A's message"; return
  fi

  # B -> A message
  wn_b messages send "$mls_gid" "hello from wn" >/dev/null 2>&1 || warn "wn send returned nonzero"
  if ! amy_json marmot await message "$gid" --match "hello from wn" --timeout 30 >/dev/null; then
    record_result "$id" fail "A didn't receive B's reply"; return
  fi
  record_result "$id" pass
}

test_03_b_creates_group() {
  banner "Test 03 — B creates group, invites A"
  local id="03 B->A create+invite"

  # `wn groups create` returns the MLS group id (what wn's messages API
  # expects). amy indexes by the MIP-01 nostr_group_id, which we only learn
  # once A has processed the welcome and we can ask `amy await group` for
  # it. Keep them distinct so each CLI gets the id it understands.
  local out mls_gid
  out=$(wn_b --json groups create "Interop-03" "$A_NPUB" 2>>"$LOG_FILE") || {
    record_result "$id" fail "wn groups create failed"; return
  }
  mls_gid=$(printf '%s' "$out" | jq_group_id)
  if [[ -z "$mls_gid" ]]; then
    record_result "$id" fail "could not parse group_id"; return
  fi
  save_state GROUP_03_MLS "$mls_gid"
  info "mls_group_id: $mls_gid"

  # A: poll until it joins, and capture its nostr_group_id.
  local a_out a_gid
  a_out=$(amy_json marmot await group --name "Interop-03" --timeout 30) || {
    record_result "$id" fail "A never joined B's group"; return
  }
  a_gid=$(printf '%s' "$a_out" | jq -r '.group_id')
  save_state GROUP_03 "$a_gid"
  info "A joined as nostr_group_id=$a_gid"

  # B -> A message
  wn_b messages send "$mls_gid" "ping from wn" >/dev/null 2>&1 || true
  if ! amy_json marmot await message "$a_gid" --match "ping from wn" --timeout 30 >/dev/null; then
    record_result "$id" fail "A didn't see B's ping"; return
  fi

  # A -> B reply
  amy_json marmot message send "$a_gid" "pong from amethyst" >/dev/null || {
    record_result "$id" fail "amy send pong failed"; return
  }
  if wait_for_message B "$mls_gid" "pong from amethyst" 90; then
    record_result "$id" pass
  else
    record_result "$id" fail "B didn't see A's pong"
  fi
}

test_04_three_member_group() {
  banner "Test 04 — 3-member group, add C after create"
  local id="04 3-member add-after-create"

  wn_c keys publish >/dev/null 2>&1 || true
  sleep 3

  local gid mls_gid
  gid=$(load_state GROUP_02 || true)
  mls_gid=$(load_state GROUP_02_MLS || true)
  if [[ -z "${gid:-}" ]]; then
    record_result "$id" skip "no GROUP_02"; return
  fi

  # A adds C
  amy_json marmot group add "$gid" "$C_NPUB" >/dev/null || {
    record_result "$id" fail "amy group add C failed"; return
  }

  local c_gid
  if ! c_gid=$(wait_for_invite C 60); then
    record_result "$id" fail "C never received invite"; return
  fi
  wn_c groups accept "$c_gid" >/dev/null 2>&1 || true

  amy_json marmot message send "$gid" "hello three-member world" >/dev/null || {
    record_result "$id" fail "amy send failed"; return
  }
  # wn's event_processor retries undecryptable pre-membership commits with
  # exponential backoff (2+4+8+16=~30s), and kind:445 processing is serial
  # per-account; a fresh joiner routinely needs ~60s to burn through that
  # retry queue before the add-C commit is applied and "hello three-member
  # world" decrypts. The 30s we used for the inline A<->B send/recv
  # wouldn't clear that.
  if wait_for_message B "$mls_gid" "hello three-member world" 90 \
     && wait_for_message C "$c_gid" "hello three-member world" 90; then
    record_result "$id" pass
  else
    record_result "$id" fail "B or C missed A's post-add message"
  fi
}

test_05_b_adds_a_existing() {
  banner "Test 05 — B adds A to an existing B+C group"
  local id="05 wn adds A existing"

  local out gid
  out=$(wn_b --json groups create "Interop-05" "$C_NPUB" 2>>"$LOG_FILE") || {
    record_result "$id" fail "wn create Interop-05 failed"; return
  }
  gid=$(printf '%s' "$out" | jq_group_id)
  if [[ -z "$gid" ]]; then
    record_result "$id" fail "no group_id from create"; return
  fi
  save_state GROUP_05 "$gid"
  wait_for_invite C 30 >/dev/null && wn_c groups accept "$gid" >/dev/null 2>&1 || true

  wn_b groups add-members "$gid" "$A_NPUB" >/dev/null 2>&1 || {
    record_result "$id" fail "wn add-members A failed"; return
  }

  # A joins
  if ! amy_json marmot await group --name "Interop-05" --timeout 30 >/dev/null; then
    record_result "$id" fail "A never received invite to Interop-05"; return
  fi

  amy_json marmot message send "$gid" "joined from amethyst" >/dev/null || {
    record_result "$id" fail "amy send failed"; return
  }
  if wait_for_message B "$gid" "joined from amethyst" 90 \
     && wait_for_message C "$gid" "joined from amethyst" 90; then
    record_result "$id" pass
  else
    record_result "$id" fail "B or C didn't see A's message"
  fi
}
