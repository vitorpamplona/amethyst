# shellcheck shell=bash
#
# tests-extras.sh — tests 09, 10, 12, 13.
# Focus: reactions/replies, concurrent commits, offline catchup, KP rotation.

test_09_reply_react_unreact() {
  banner "Test 09 — reply / react / unreact"
  local id="09 reply/react"

  local gid mls_gid
  gid=$(load_state GROUP_02 || true)
  mls_gid=$(load_state GROUP_02_MLS || true)
  if [[ -z "${gid:-}" ]]; then
    record_result "$id" skip "no GROUP_02"; return
  fi

  # B anchors. Needs a member to be present — if Test 11 already ran and A left,
  # skip cleanly so we don't double-fail.
  if ! wn_b --json groups members "$mls_gid" 2>/dev/null \
        | jq -e --arg p "$A_HEX" '(.result // .) | .[]? | select((.pubkey // .public_key) == $p)' \
        >/dev/null 2>&1; then
    record_result "$id" skip "A already left GROUP_02"; return
  fi

  wn_b messages send "$mls_gid" "anchor for reactions" >/dev/null 2>&1 || true
  sleep 3
  local msg_id
  msg_id=$(wn_b --json messages list "$mls_gid" --limit 10 2>/dev/null \
             | jq -r '[(.result // .) | .[]? | select((.content // .text // "") == "anchor for reactions")][0].id // empty')
  if [[ -z "$msg_id" || "$msg_id" == "null" ]]; then
    record_result "$id" fail "couldn't find anchor message id"; return
  fi

  wn_b messages react "$mls_gid" "$msg_id" "🌮" >/dev/null 2>&1 || true
  sleep 3
  # amy reply
  amy_json marmot message send "$gid" "replying via amy" >/dev/null || {
    record_result "$id" fail "amy send reply failed"; return
  }
  if ! wait_for_message B "$mls_gid" "replying via amy" 90; then
    record_result "$id" fail "B didn't receive reply"; return
  fi

  # amy react — look up the anchor id from amy's own decrypted log (B's kind:9
  # "anchor for reactions" was delivered + persisted during wait_for_message),
  # then hand that id to the new react verb.
  sleep 3
  local a_anchor_id
  a_anchor_id=$(amy_json marmot message list "$gid" --limit 50 2>/dev/null \
                  | jq -r '[.messages[]? | select((.content // "") == "anchor for reactions")][0].id // empty')
  if [[ -z "$a_anchor_id" || "$a_anchor_id" == "null" ]]; then
    record_result "$id" fail "amy couldn't find anchor message in local log"; return
  fi
  if ! amy_json marmot message react "$gid" "$a_anchor_id" "🍕" >/dev/null; then
    record_result "$id" fail "amy marmot message react failed"; return
  fi

  # Round-trip: B should surface amy's kind:7 reaction. wn aggregates
  # reactions onto the anchor message (`.reactions.by_emoji[<emoji>]`),
  # not as a standalone entry whose `.content` is the emoji — so polling
  # `messages list` for an entry whose content equals "🍕" would never
  # match, even when the reaction was successfully decrypted. Look for
  # the emoji under any message's `reactions.by_emoji` keys instead.
  local deadline=$(( $(date +%s) + 90 )) saw=0
  while [[ $(date +%s) -lt $deadline ]]; do
    local payload
    payload=$(wn_b_json messages list "$mls_gid" --limit 50 2>/dev/null || true)
    if [[ -n "$payload" ]] && \
         printf '%s' "$payload" \
           | jq -e '(.result // .) | .[]? | (.reactions.by_emoji // {}) | keys[]?' \
                  2>/dev/null \
           | grep -qF '"🍕"'; then
      saw=1; break
    fi
    sleep 3
  done
  if [[ "$saw" -eq 1 ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "B didn't receive amy's reaction"
  fi
}

test_10_concurrent_commits() {
  banner "Test 10 — Concurrent commits race"
  local id="10 concurrent commits"

  local gid mls_gid
  gid=$(load_state GROUP_02 || true)
  mls_gid=$(load_state GROUP_02_MLS || true)
  if [[ -z "${gid:-}" ]]; then
    record_result "$id" skip "no GROUP_02"; return
  fi
  if ! wn_b --json groups members "$mls_gid" 2>/dev/null \
        | jq -e --arg p "$A_HEX" '(.result // .) | .[]? | select((.pubkey // .public_key) == $p)' \
        >/dev/null 2>&1; then
    record_result "$id" skip "A already left GROUP_02"; return
  fi

  # Fire both renames in parallel. One wins — MLS single-commit-per-epoch
  # guarantees a deterministic outcome.
  ( amy_json marmot group rename "$gid" "race-from-amethyst" >/dev/null ) &
  local a_pid=$!
  ( wn_b groups rename "$mls_gid" "race-from-wn" >/dev/null 2>&1 ) &
  local b_pid=$!
  wait "$a_pid" "$b_pid" 2>/dev/null || true

  sleep 10
  local b_name
  # whitenoise-rs ≥ v0.2.x wraps the group payload one level deeper as
  # `{"result": {"group": {…name…}}}`; the older shape was a bare group
  # object under `.result`. Accept both.
  b_name=$(wn_b --json groups show "$mls_gid" 2>/dev/null | jq -r '(.result // .) | (.group // .) | .name // empty')
  local a_name
  a_name=$(amy_field '.name' marmot group show "$gid" 2>/dev/null || echo "")

  if [[ -n "$a_name" && "$a_name" == "$b_name" ]]; then
    info "race converged: both sides see \"$a_name\""
    # Verify encryption still works.
    wn_b messages send "$mls_gid" "post-race ping" >/dev/null 2>&1 || true
    if amy_json marmot await message "$gid" --match "post-race ping" --timeout 90 >/dev/null; then
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

  # wn-side keys groups by mls_group_id; amy-side by nostr_group_id. Learn
  # the amy side from `amy await group` so later amy calls target the right
  # group.
  local out mls_gid a_out a_gid
  out=$(wn_b --json groups create "Interop-12" "$A_NPUB" 2>>"$LOG_FILE")
  mls_gid=$(printf '%s' "$out" | jq_group_id)
  [[ -n "$mls_gid" ]] || { record_result "$id" fail "couldn't create Interop-12"; return; }
  save_state GROUP_12_MLS "$mls_gid"

  # A joins.
  a_out=$(amy_json marmot await group --name "Interop-12" --timeout 30) || {
    record_result "$id" fail "A never received Interop-12 invite"; return
  }
  a_gid=$(printf '%s' "$a_out" | jq -r '.group_id')
  save_state GROUP_12 "$a_gid"

  # "Go offline" == don't invoke amy. Meanwhile B sends 5 messages + adds C + sends 3 more + rename.
  for i in 1 2 3 4 5; do
    wn_b messages send "$mls_gid" "offline-msg-$i" >/dev/null 2>&1 || true
    sleep 1
  done
  wn_b groups add-members "$mls_gid" "$C_NPUB" >/dev/null 2>&1 || true
  wait_for_invite C 30 >/dev/null && wn_c groups accept "$mls_gid" >/dev/null 2>&1 || true
  for i in 6 7 8; do
    wn_b messages send "$mls_gid" "offline-msg-$i" >/dev/null 2>&1 || true
    sleep 1
  done
  wn_b groups rename "$mls_gid" "Interop-12-renamed" >/dev/null 2>&1 || true
  sleep 3

  # A comes back online — single sync pulls everything.
  local show
  show=$(amy_json marmot group show "$a_gid") || {
    record_result "$id" fail "amy group show failed"; return
  }
  local name; name=$(printf '%s' "$show" | jq -r '.name')
  if [[ "$name" != "Interop-12-renamed" ]]; then
    record_result "$id" fail "A replay missed rename (saw \"$name\")"; return
  fi

  # All 8 messages should be locally stored.
  local msgs; msgs=$(amy_json marmot message list "$a_gid" --limit 100)
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

# -- Inverted-role tests ----------------------------------------------------
# Tests 01–13 mostly exercise amethyst as the committer and wn as the
# receiver. The scenarios below are complementary: wn drives the state
# change and amy is the receiver that must accept, verify and apply it.
# This widens coverage for the post-fix inbound authenticity checks
# (membership_tag, FramedContentTBS signature, LeafNode lifetime,
# confirmation_tag) that now run on every commit amy processes.

test_14_wn_removes_a() {
  banner "Test 14 — wn (admin) removes A; amy processes Remove"
  local id="14 wn removes amy"

  # Exercises inbound filtered-direct-path per RFC 9420 §7.7: wn
  # (mdk-core/openmls) strips UpdatePathNodes whose copath has empty
  # resolution, and amy must accept the short path on receive. Quartz
  # wires this on both sides as of commit 3279c246.

  local out mls_gid
  out=$(wn_b --json groups create "Interop-14" "$C_NPUB" 2>>"$LOG_FILE") || {
    record_result "$id" fail "wn create Interop-14 failed"; return
  }
  mls_gid=$(printf '%s' "$out" | jq_group_id)
  [[ -n "$mls_gid" ]] || { record_result "$id" fail "no group_id from create"; return; }
  wait_for_invite C 30 >/dev/null && wn_c groups accept "$mls_gid" >/dev/null 2>&1 || true

  wn_b groups add-members "$mls_gid" "$A_NPUB" >/dev/null 2>&1 || {
    record_result "$id" fail "wn add-members A failed"; return
  }
  local a_gid
  a_gid=$(amy_json marmot await group --name "Interop-14" --timeout 30 | jq -r '.group_id // empty')
  [[ -n "$a_gid" ]] || { record_result "$id" fail "A never received Interop-14 invite"; return; }

  # B (admin) removes A. The resulting commit carries a filtered
  # UpdatePath; amy must apply it without throwing on the node-count
  # mismatch that used to happen pre-filter-support.
  wn_b groups remove-members "$mls_gid" "$A_NPUB" >/dev/null 2>&1 || {
    record_result "$id" fail "wn remove-members A failed"; return
  }

  # Amy should observe that she's no longer a member. `group show` returns
  # a not_member error as soon as the Remove commit is applied locally.
  #
  # The amy CLI contract puts error JSON on stderr (README: "stdout is
  # JSON … stderr is for humans"), but stderr is interleaved with debug
  # logs from `Log.d(…)` calls in quartz, so feeding the captured stream
  # straight into `jq` fails to parse. Capture stderr alongside stdout
  # via `2>&1` and grep for the `"error":"not_member"` literal — that
  # signature is unambiguous (the debug log lines never produce JSON).
  # Also tee the per-iteration capture into $LOG_FILE so post-mortem
  # logs include each polling sync's `[cli] ingest …` trace, mirroring
  # what amy_json normally writes when stderr isn't being captured.
  local deadline=$(( $(date +%s) + 120 )) removed=0
  while [[ $(date +%s) -lt $deadline ]]; do
    local show rc
    show=$(HOME="$STATE_DIR" "$AMY_BIN" --account A --secret-backend plaintext --json \
              marmot group show "$a_gid" 2>&1)
    rc=$?
    printf '%s\n' "$show" >>"$LOG_FILE"
    if [[ $rc -ne 0 ]] && printf '%s' "$show" | grep -qE '"error":[[:space:]]*"not_member"'; then
      removed=1; break
    fi
    sleep 3
  done
  if [[ "$removed" -eq 1 ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "amy still considered herself a member after wn Remove"
  fi
}

test_15_wn_member_leaves() {
  banner "Test 15 — wn_c leaves; amy + wn_b process SelfRemove"
  local id="15 wn_c leaves"

  # Same filtered-direct-path path as test 14, but the trigger is a
  # SelfRemove proposal from C that wn_b folds into a commit. Amy stays
  # a member here — verification is that the commit applies cleanly and
  # the group continues to function afterwards.

  local out mls_gid
  out=$(wn_b --json groups create "Interop-15" "$C_NPUB" 2>>"$LOG_FILE") || {
    record_result "$id" fail "wn create Interop-15 failed"; return
  }
  mls_gid=$(printf '%s' "$out" | jq_group_id)
  [[ -n "$mls_gid" ]] || { record_result "$id" fail "no group_id from create"; return; }
  wait_for_invite C 30 >/dev/null && wn_c groups accept "$mls_gid" >/dev/null 2>&1 || true

  wn_b groups add-members "$mls_gid" "$A_NPUB" >/dev/null 2>&1 || {
    record_result "$id" fail "wn add-members A failed"; return
  }
  local a_gid
  a_gid=$(amy_json marmot await group --name "Interop-15" --timeout 30 | jq -r '.group_id // empty')
  [[ -n "$a_gid" ]] || { record_result "$id" fail "A never received Interop-15 invite"; return; }

  # C self-removes. wn_b picks up the SelfRemove proposal and commits it.
  wn_c groups leave "$mls_gid" >/dev/null 2>&1 || true
  sleep 5
  # Nudge B to commit any outstanding proposals via a no-op rename round-trip.
  wn_b groups rename "$mls_gid" "Interop-15 (C left)" >/dev/null 2>&1 || true

  # Wait for amy to see C gone AND stay a member herself.
  local deadline=$(( $(date +%s) + 120 )) ok=0
  while [[ $(date +%s) -lt $deadline ]]; do
    local show
    show=$(amy_json marmot group show "$a_gid" 2>/dev/null) || { sleep 3; continue; }
    local c_still
    c_still=$(printf '%s' "$show" | jq --arg p "$C_HEX" '[.members[]? | select(.pubkey == $p)] | length')
    local a_still
    a_still=$(printf '%s' "$show" | jq --arg p "$A_HEX" '[.members[]? | select(.pubkey == $p)] | length')
    if [[ "$c_still" == "0" && "$a_still" == "1" ]]; then
      ok=1; break
    fi
    sleep 3
  done
  if [[ "$ok" -ne 1 ]]; then
    record_result "$id" fail "amy never saw a (C gone, A present) state"; return
  fi

  # Post-commit round-trip: amy sends, B receives. Confirms encryption
  # survived the filtered UpdatePath application on amy's side.
  amy_json marmot message send "$a_gid" "after wn_c leave" >/dev/null || {
    record_result "$id" fail "amy send failed after wn_c leave"; return
  }
  if wait_for_message B "$mls_gid" "after wn_c leave" 90; then
    record_result "$id" pass
  else
    record_result "$id" fail "B didn't receive amy's post-leave message"
  fi
}

test_16_wn_keypackage_rotation() {
  banner "Test 16 — wn rotates KeyPackage; amy discovers new KP"
  local id="16 wn keypackage rotation"

  # KeyPackageFetcher now drains to EOSE and returns the event with the
  # highest created_at, so a freshly-rotated KP is reliably preferred
  # over an older one still held on some relays. This test verifies
  # the wn->amy direction of that behaviour.

  # Capture the KP amy currently sees for B (the one B originally published).
  local before
  before=$(amy_json marmot key-package check "$B_NPUB" 2>/dev/null \
             | jq -r '.event_id // empty')
  if [[ -z "$before" ]]; then
    record_result "$id" fail "no prior KP visible to amy for B"; return
  fi

  # Ask B to rotate. `wn keys publish` writes a new kind:443 with a fresh
  # created_at; the old event may or may not be evicted depending on the
  # relay's retention policy, so both may coexist for a while.
  wn_b keys publish >/dev/null 2>&1 || {
    record_result "$id" fail "wn_b keys publish failed"; return
  }

  local deadline=$(( $(date +%s) + 60 )) after=""
  while [[ $(date +%s) -lt $deadline ]]; do
    after=$(amy_json marmot key-package check "$B_NPUB" 2>/dev/null \
              | jq -r '.event_id // empty')
    [[ -n "$after" && "$after" != "$before" ]] && break
    sleep 3
  done
  if [[ -n "$after" && "$after" != "$before" ]]; then
    info "amy saw KP rotation: ${before:0:8}… → ${after:0:8}…"
    record_result "$id" pass
  else
    record_result "$id" fail "amy kept seeing the pre-rotation KP"
  fi
}
