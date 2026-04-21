# shellcheck shell=bash
#
# headless/tests-extras.sh — tests 09, 10, 12, 13.
# Focus: reactions/replies, concurrent commits, offline catchup, KP rotation.

test_09_reply_react_unreact() {
  banner "Test 09 — reply / react / unreact"
  local id="09 reply/react"

  local gid; gid=$(load_state GROUP_02 || true)
  if [[ -z "${gid:-}" ]]; then
    record_result "$id" skip "no GROUP_02"; return
  fi

  # B anchors. Needs a member to be present — if Test 11 already ran and A left,
  # skip cleanly so we don't double-fail.
  if ! wn_b --json groups members "$gid" 2>/dev/null \
        | jq -e --arg p "$A_HEX" '.[]? | select((.pubkey // .public_key) == $p)' \
        >/dev/null 2>&1; then
    record_result "$id" skip "A already left GROUP_02"; return
  fi

  wn_b messages send "$gid" "anchor for reactions" >/dev/null 2>&1 || true
  sleep 3
  local msg_id
  msg_id=$(wn_b --json messages list "$gid" --limit 10 2>/dev/null \
             | jq -r '[.[]? | select((.content // .text // "") == "anchor for reactions")][0].id // empty')
  if [[ -z "$msg_id" || "$msg_id" == "null" ]]; then
    record_result "$id" fail "couldn't find anchor message id"; return
  fi

  wn_b messages react "$gid" "$msg_id" "🌮" >/dev/null 2>&1 || true
  sleep 3
  # amy reply
  amy_json marmot message send "$gid" "replying via amy" >/dev/null || {
    record_result "$id" fail "amy send reply failed"; return
  }
  if wait_for_message B "$gid" "replying via amy" 30; then
    record_result "$id" pass
  else
    record_result "$id" fail "B didn't receive reply"
  fi
  # NB: react/unreact round-trip verification requires a CLI verb we don't have
  # yet (amy marmot message react). Once we add it, expand this test.
}

test_10_concurrent_commits() {
  banner "Test 10 — Concurrent commits race"
  local id="10 concurrent commits"

  local gid; gid=$(load_state GROUP_02 || true)
  if [[ -z "${gid:-}" ]]; then
    record_result "$id" skip "no GROUP_02"; return
  fi
  if ! wn_b --json groups members "$gid" 2>/dev/null \
        | jq -e --arg p "$A_HEX" '.[]? | select((.pubkey // .public_key) == $p)' \
        >/dev/null 2>&1; then
    record_result "$id" skip "A already left GROUP_02"; return
  fi

  # Fire both renames in parallel. One wins — MLS single-commit-per-epoch
  # guarantees a deterministic outcome.
  ( amy_json marmot group rename "$gid" "race-from-amethyst" >/dev/null ) &
  local a_pid=$!
  ( wn_b groups rename "$gid" "race-from-wn" >/dev/null 2>&1 ) &
  local b_pid=$!
  wait "$a_pid" "$b_pid" 2>/dev/null || true

  sleep 10
  local b_name
  b_name=$(wn_b --json groups show "$gid" 2>/dev/null | jq -r '.name // empty')
  local a_name
  a_name=$(amy_field '.name' marmot group show "$gid" 2>/dev/null || echo "")

  if [[ -n "$a_name" && "$a_name" == "$b_name" ]]; then
    info "race converged: both sides see \"$a_name\""
    # Verify encryption still works.
    wn_b messages send "$gid" "post-race ping" >/dev/null 2>&1 || true
    if amy_json marmot await message "$gid" --match "post-race ping" --timeout 15 >/dev/null; then
      record_result "$id" pass
    else
      record_result "$id" fail "encryption broken after race"
    fi
  else
    record_result "$id" fail "diverged: A sees \"$a_name\", B sees \"$b_name\""
  fi
}

test_12_offline_catchup() {
  banner "Test 12 — Offline catch-up"
  local id="12 offline catchup"

  # Fresh group so we don't collide with other tests.
  local out gid
  out=$(wn_b --json groups create "Interop-12" "$A_NPUB" 2>>"$LOG_FILE")
  gid=$(printf '%s' "$out" | jq_group_id)
  [[ -n "$gid" ]] || { record_result "$id" fail "couldn't create Interop-12"; return; }
  save_state GROUP_12 "$gid"

  # A joins.
  amy_json marmot await group --name "Interop-12" --timeout 30 >/dev/null || {
    record_result "$id" fail "A never received Interop-12 invite"; return
  }

  # "Go offline" == don't invoke amy. Meanwhile B sends 5 messages + adds C + sends 3 more + rename.
  for i in 1 2 3 4 5; do
    wn_b messages send "$gid" "offline-msg-$i" >/dev/null 2>&1 || true
    sleep 1
  done
  wn_b groups add-members "$gid" "$C_NPUB" >/dev/null 2>&1 || true
  wait_for_invite C 30 >/dev/null && wn_c groups accept "$gid" >/dev/null 2>&1 || true
  for i in 6 7 8; do
    wn_b messages send "$gid" "offline-msg-$i" >/dev/null 2>&1 || true
    sleep 1
  done
  wn_b groups rename "$gid" "Interop-12-renamed" >/dev/null 2>&1 || true
  sleep 3

  # A comes back online — single sync pulls everything.
  local show
  show=$(amy_json marmot group show "$gid") || {
    record_result "$id" fail "amy group show failed"; return
  }
  local name; name=$(printf '%s' "$show" | jq -r '.name')
  if [[ "$name" != "Interop-12-renamed" ]]; then
    record_result "$id" fail "A replay missed rename (saw \"$name\")"; return
  fi

  # All 8 messages should be locally stored.
  local msgs; msgs=$(amy_json marmot message list "$gid" --limit 100)
  local missing=0
  for i in 1 2 3 4 5 6 7 8; do
    if ! printf '%s' "$msgs" | jq -e --arg t "offline-msg-$i" \
          '.messages[]? | select((.content // "") == $t)' >/dev/null; then
      missing=$((missing+1))
      info "missing offline-msg-$i"
    fi
  done
  if [[ "$missing" -eq 0 ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "A missed $missing of 8 offline messages"
  fi
}

test_13_keypackage_rotation() {
  banner "Test 13 — KeyPackage rotation"
  local id="13 keypackage rotation"

  local before
  before=$(wn_b --json keys check "$A_NPUB" 2>/dev/null | jq -r '.result.event_id // empty')
  if [[ -z "$before" ]]; then
    record_result "$id" fail "no prior KP for A"; return
  fi

  amy_json marmot key-package publish >/dev/null || {
    record_result "$id" fail "amy key-package publish failed"; return
  }

  local deadline=$(( $(date +%s) + 60 )) after=""
  while [[ $(date +%s) -lt $deadline ]]; do
    after=$(wn_b --json keys check "$A_NPUB" 2>/dev/null | jq -r '.result.event_id // empty')
    [[ -n "$after" && "$after" != "$before" ]] && break
    sleep 3
  done
  if [[ -n "$after" && "$after" != "$before" ]]; then
    info "KP rotated: ${before:0:8}… → ${after:0:8}…"
    record_result "$id" pass
  else
    record_result "$id" fail "no new KP event_id observed"
  fi
}
