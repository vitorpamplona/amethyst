# shellcheck shell=bash
#
# tests-manage.sh — tests 06, 07, 08, 11.
# Focus: removal, metadata rename, admin promote/demote, leave.

test_06_member_removal() {
  banner "Test 06 — Member removal + forward secrecy"
  local id="06 member removal"

  # MIP-03 only admins may commit Remove proposals. In GROUP_05 (wn-created
  # by B, A joined later) A is not an admin, so the test used to fail with
  # `IllegalStateException: non-admin members may only commit...`. Test on
  # GROUP_02 instead, where amy is the creator and therefore sole admin,
  # and where test 04 has already added C. amy calls use the nostr id,
  # wn calls use the MLS id.
  local gid mls_gid
  gid=$(load_state GROUP_02 || true)
  mls_gid=$(load_state GROUP_02_MLS || true)
  if [[ -z "${gid:-}" || -z "${mls_gid:-}" ]]; then
    record_result "$id" skip "no GROUP_02"; return
  fi

  amy_json marmot group remove "$gid" "$C_NPUB" >/dev/null || {
    record_result "$id" fail "amy remove C failed"; return
  }

  # C should no longer see the group on its own member view.
  local deadline=$(( $(date +%s) + 120 )) removed=0
  while [[ $(date +%s) -lt $deadline ]]; do
    if ! wn_c --json groups members "$mls_gid" 2>/dev/null \
         | jq_list members | jq -e --arg p "$C_HEX" \
             'select((.member_id // .pubkey // .public_key) == $p)' \
         >/dev/null 2>&1; then
      removed=1; break
    fi
    sleep 3
  done
  if [[ "$removed" -ne 1 ]]; then
    warn "C still appears as a member — continuing"
  fi

  amy_json marmot message send "$gid" "after removing C" >/dev/null || {
    record_result "$id" fail "amy send failed"; return
  }
  wait_for_message B "$mls_gid" "after removing C" 90 || {
    record_result "$id" fail "B lost access after C's removal"; return
  }

  # Forward secrecy: C must NOT see the post-removal message.
  sleep 5
  if wait_for_message C "$mls_gid" "after removing C" 10; then
    record_result "$id" fail "C still decrypted a post-removal message"
  else
    record_result "$id" pass
  fi
}

test_07_metadata_rename() {
  banner "Test 07 — Metadata rename round-trip (MIP-01)"
  local id="07 metadata rename"

  # amy was the creator of GROUP_02 so its own nostr_group_id is saved as
  # GROUP_02; wn keys its copy by the MLS group id saved as GROUP_02_MLS.
  # Pass each CLI the id it understands.
  local gid mls_gid
  gid=$(load_state GROUP_02 || true)
  mls_gid=$(load_state GROUP_02_MLS || true)
  if [[ -z "${gid:-}" || -z "${mls_gid:-}" ]]; then
    record_result "$id" skip "no GROUP_02"; return
  fi

  amy_json marmot group rename "$gid" "Interop-02-renamed" >/dev/null || {
    record_result "$id" fail "amy rename failed"; return
  }

  local deadline=$(( $(date +%s) + 120 )) seen=""
  while [[ $(date +%s) -lt $deadline ]]; do
    # whitenoise-rs ≥ v0.2.x wraps the group payload one level deeper as
    # `{"result": {"group": {…name…}}}`; older builds returned the bare
    # group object under `.result`. Probe both shapes so the test survives
    # either schema.
    # MDK 0.9.x keeps the display name in the profile component, not a
    # top-level `name`: `.result.group.profile.name`.
    seen=$(wn_b --json groups show "$mls_gid" 2>/dev/null \
             | jq -r '(.result // .) | (.group // .) | (.profile.name // .name) // empty')
    [[ "$seen" == "Interop-02-renamed" ]] && break
    sleep 3
  done
  [[ "$seen" == "Interop-02-renamed" ]] || {
    record_result "$id" fail "B saw name=\"$seen\" not \"Interop-02-renamed\""; return
  }

  # MIP-01: only admins may rename. GROUP_02 was created by amy (sole
  # admin), so for B's rename to be accepted by wn's MIP-01 check amy
  # must first promote B. amy adds B to admin_pubkeys via GCE, which
  # the harness consumes via `marmot group promote` — equivalent to
  # `wn groups promote` but issued by the quartz side. Without this
  # step B's own wn silently refuses the rename on its MIP-01
  # `ensure_account_is_group_admin` check, and the kind:445 is never
  # published.
  amy_json marmot group promote "$gid" "$B_NPUB" >/dev/null 2>&1 || {
    record_result "$id" fail "amy promote-B failed"; return
  }
  # Let wn apply the promote commit before issuing the rename.
  sleep 3

  # Now B renames back and A should pick it up.
  wn_b groups rename "$mls_gid" "Interop-02-reverse" >/dev/null 2>&1 || true
  if amy_json marmot await rename "$gid" --name "Interop-02-reverse" --timeout 120 >/dev/null; then
    record_result "$id" pass
  else
    record_result "$id" fail "A did not pick up B's rename"
  fi
}

test_08_admin_promote_demote() {
  banner "Test 08 — Admin promote / demote"
  local id="08 admin promote/demote"

  # GROUP_03 was created by wn so both sides need different ids:
  #   GROUP_03     → amy's nostr_group_id (captured in test 03 after
  #                  `amy await group` returned `.group_id`)
  #   GROUP_03_MLS → wn's mls_group_id    (wn's `groups create` output)
  local a_gid mls_gid
  a_gid=$(load_state GROUP_03 || true)
  mls_gid=$(load_state GROUP_03_MLS || true)
  if [[ -z "${mls_gid:-}" ]]; then
    record_result "$id" skip "no GROUP_03"; return
  fi

  # Ensure 3 members (add C if missing).
  wn_c keys publish >/dev/null 2>&1 || true
  sleep 2
  wn_b groups add-members "$mls_gid" "$C_NPUB" >/dev/null 2>&1 || true
  wait_for_invite C 30 >/dev/null && wn_c groups accept "$mls_gid" >/dev/null 2>&1 || true

  # B promotes A.
  wn_b groups promote "$mls_gid" "$A_NPUB" >/dev/null 2>&1 || {
    record_result "$id" fail "wn promote failed"; return
  }

  # A should reflect the new admin set — poll via amy.
  if ! amy_json marmot await admin "$a_gid" "$A_NPUB" --timeout 90 >/dev/null; then
    record_result "$id" fail "A never saw itself promoted"; return
  fi

  # A now commits a rename — only possible if we're admin.
  amy_json marmot group rename "$a_gid" "Interop-03-by-A" >/dev/null || {
    record_result "$id" fail "A (now admin) could not rename"; return
  }

  # B demotes A.
  wn_b groups demote "$mls_gid" "$A_NPUB" >/dev/null 2>&1 || warn "demote returned nonzero"
  sleep 5

  local admins
  admins=$(wn_b --json groups admins "$mls_gid" 2>/dev/null \
             | jq_list admins | jq_member_ids | tr '\n' ' ')
  if [[ "$admins" == *"$A_HEX"* ]]; then
    record_result "$id" fail "A still admin after demote"
  else
    record_result "$id" pass
  fi
}

test_11_leave_group() {
  banner "Test 11 — Leave group"
  local id="11 leave group"

  local gid mls_gid
  gid=$(load_state GROUP_02 || true)
  mls_gid=$(load_state GROUP_02_MLS || true)
  if [[ -z "${gid:-}" ]]; then
    record_result "$id" skip "no GROUP_02"; return
  fi

  amy_json marmot group leave "$gid" >/dev/null || {
    record_result "$id" fail "amy leave failed"; return
  }

  local deadline=$(( $(date +%s) + 120 )) gone=0
  while [[ $(date +%s) -lt $deadline ]]; do
    if ! wn_b --json groups members "$mls_gid" 2>/dev/null \
         | jq_list admins | jq -e --arg p "$A_HEX" \
             'select((.admin_id // .pubkey // .public_key) == $p)' \
         >/dev/null 2>&1; then
      gone=1; break
    fi
    sleep 3
  done
  if [[ "$gone" -eq 1 ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "A still in B's member list after leave"
  fi
}

# Regression guard for the Marmot group-icon feature: setting a group image writes
# the MIP-01 v2 image fields into the NostrGroupData extension. mdk-core (the library
# whitenoise uses) rejects ANY trailing bytes in that extension at a known version, so
# a mis-encoded image commit would make the whole group unprocessable for wn and stall
# it a full epoch behind A.
#
# We prove wn applied the image commit the hard way: A sets an image, then sends an
# application message at the POST-image epoch. wn can only decrypt that message if it
# advanced past the image-bearing GCE commit — so wn receiving it is direct evidence
# the image extension parsed. (Once it parses, whitenoise even tries to fetch the
# avatar from Blossom via background_sync_group_image_cache_if_needed.)
#
# A single application message is used rather than a second commit on purpose: two
# commits fired 3s apart race wn's per-epoch processing and give a flaky signal.
test_17_group_image_commit() {
  banner "Test 17 — Group image commit stays parseable on whitenoise (MIP-01 v2)"
  local id="17 group image"

  local gid mls_gid
  gid=$(load_state GROUP_02 || true)
  mls_gid=$(load_state GROUP_02_MLS || true)
  if [[ -z "${gid:-}" || -z "${mls_gid:-}" ]]; then
    record_result "$id" skip "no GROUP_02"; return
  fi

  # Skip cleanly if A is no longer a member of GROUP_02 (a later test may have removed
  # A) — this test only makes sense while A can still commit to the group.
  if ! wn_b --json groups members "$mls_gid" 2>/dev/null \
        | jq_list members | jq -e --arg p "$A_HEX" \
            'select((.member_id // .pubkey // .public_key) == $p)' \
        >/dev/null 2>&1; then
    record_result "$id" skip "A not in GROUP_02"; return
  fi

  # Contents are irrelevant — amy encrypts whatever bytes it's given; the interop
  # question is purely whether the resulting image extension parses on wn.
  local img="$STATE_DIR/marmot-icon.bin"
  head -c 1024 /dev/urandom >"$img" 2>/dev/null || printf 'fake-avatar-bytes-for-interop' >"$img"

  if ! amy_json marmot group set-image "$gid" "$img" >/dev/null; then
    record_result "$id" fail "amy set-image failed"; return
  fi

  sleep 3
  local tag="post-image-ping-from-amethyst"
  if ! amy_json marmot message send "$gid" "$tag" >/dev/null; then
    record_result "$id" fail "amy post-image send failed"; return
  fi

  if wait_for_message B "$mls_gid" "$tag" 120; then
    record_result "$id" pass
  else
    record_result "$id" fail "wn could not decrypt A's post-image message — image commit not applied"
  fi
}

# The device sequence that forked Amethyst out of a group White Noise joined:
# amy creates, invites wn, commits an AppDataUpdate (the app's "Use encrypted
# attachments" is one; set-retention is the same proposal type), promotes wn,
# and then wn makes its FIRST commit — a rename carrying an UpdatePath. On the
# device Amethyst refused that commit ("UpdatePath at common ancestor carries
# no ciphertext for us") and every later wn message failed to decrypt.
test_30_wn_commit_after_app_data_update() {
  banner "Test 30 — wn's first commit after an amy AppDataUpdate commit"
  local id="30 wn commit after app-data"

  local out gid mls_gid b_gid
  out=$(amy_json marmot group create --name "Interop-30") || {
    record_result "$id" fail "amy group create failed"; return
  }
  gid=$(printf '%s' "$out" | jq -r '.group_id')
  mls_gid=$(printf '%s' "$out" | jq -r '.mls_group_id')
  amy_json marmot group add "$gid" "$B_NPUB" >/dev/null || {
    record_result "$id" fail "amy could not invite wn"; return
  }
  b_gid=$(wait_for_invite B 60) || { record_result "$id" fail "wn never received the Welcome"; return; }
  wn_b groups accept "$b_gid" >/dev/null 2>&1 || true
  wn_group_field_becomes "$mls_gid" '.group.group_id // empty' "$mls_gid" 120 || {
    record_result "$id" fail "wn never surfaced the group"; return
  }

  wn_b messages send "$mls_gid" "30 before" >/dev/null 2>&1 || true
  amy_json marmot await message "$gid" --match "30 before" --timeout 90 >/dev/null || {
    record_result "$id" fail "amy never received wn's first message"; return
  }

  amy_json marmot group set-retention "$gid" 3600 >/dev/null || {
    record_result "$id" fail "amy set-retention failed"; return
  }
  sleep 3
  amy_json marmot group promote "$gid" "$B_NPUB" >/dev/null || {
    record_result "$id" fail "amy promote failed"; return
  }
  sleep 5

  wn_b groups rename "$mls_gid" "Interop-30-by-wn" >/dev/null 2>&1 || true
  if ! amy_json marmot await rename "$gid" --name "Interop-30-by-wn" --timeout 120 >/dev/null; then
    record_result "$id" fail "amy did not apply wn's rename (commit after app-data update)"; return
  fi
  wn_b messages send "$mls_gid" "30 after" >/dev/null 2>&1 || true
  if amy_json marmot await message "$gid" --match "30 after" --timeout 90 >/dev/null; then
    record_result "$id" pass
  else
    record_result "$id" fail "amy applied the rename but cannot decrypt wn's next message (forked)"
  fi
}

# A reaction White Noise can SEE. Test 09 only proves wn's raw event log holds a
# kind:7; the app renders the materialized timeline, which attaches a reaction
# to its target by its own rules. An amy reaction the raw log kept but the
# timeline dropped read as a pass there and as "no reaction" in the app.
test_31_reaction_materializes_on_wn() {
  banner "Test 31 — amy's reaction shows in wn's materialized timeline"
  local id="31 reaction materialized"

  local out gid mls_gid b_gid anchor_id
  out=$(amy_json marmot group create --name "Interop-31") || { record_result "$id" fail "amy group create failed"; return; }
  gid=$(printf '%s' "$out" | jq -r '.group_id')
  mls_gid=$(printf '%s' "$out" | jq -r '.mls_group_id')
  amy_json marmot group add "$gid" "$B_NPUB" >/dev/null || { record_result "$id" fail "amy could not invite wn"; return; }
  b_gid=$(wait_for_invite B 60) || { record_result "$id" fail "wn never received the Welcome"; return; }
  wn_b groups accept "$b_gid" >/dev/null 2>&1 || true
  wn_group_field_becomes "$mls_gid" '.group.group_id // empty' "$mls_gid" 120 || { record_result "$id" fail "wn never surfaced the group"; return; }

  wn_b messages send "$mls_gid" "31 anchor" >/dev/null 2>&1 || true
  amy_json marmot await message "$gid" --match "31 anchor" --timeout 90 >/dev/null || { record_result "$id" fail "amy never got the anchor"; return; }
  anchor_id=$(amy_json marmot message list "$gid" --limit 50 2>/dev/null | jq_list messages \
                | jq -r 'select((.plaintext // .content // "") == "31 anchor") | (.message_id // .event_id)' | head -n 1)
  [[ -n "$anchor_id" && "$anchor_id" != "null" ]] || { record_result "$id" fail "no anchor id in amy's log"; return; }
  amy_json marmot message react "$gid" "$anchor_id" "🍕" >/dev/null || { record_result "$id" fail "amy react failed"; return; }

  local deadline=$(( $(date +%s) + 90 )) tl=""
  while [[ $(date +%s) -lt $deadline ]]; do
    tl=$(wn_b --json messages timeline list "$mls_gid" --limit 50 2>/dev/null || true)
    if printf '%s' "$tl" | grep -q '🍕'; then
      record_result "$id" pass; return
    fi
    sleep 3
  done
  printf '%s\n' "$tl" >> "$LOG_FILE"
  record_result "$id" fail "wn's timeline never showed amy's reaction (timeline JSON in the log)"
}

# The other direction of test 30. After wn commits (a rename carries an
# UpdatePath), both sides reached the same epoch and wn's messages kept
# decrypting on amy, yet wn never showed another amy message.
test_32_amy_message_after_wn_commit() {
  banner "Test 32 — amy's message after wn's commit reaches wn"
  local id="32 amy after wn commit"

  local out gid mls_gid b_gid
  out=$(amy_json marmot group create --name "Interop-32") || { record_result "$id" fail "amy group create failed"; return; }
  gid=$(printf '%s' "$out" | jq -r '.group_id')
  mls_gid=$(printf '%s' "$out" | jq -r '.mls_group_id')
  amy_json marmot group add "$gid" "$B_NPUB" >/dev/null || { record_result "$id" fail "amy could not invite wn"; return; }
  b_gid=$(wait_for_invite B 60) || { record_result "$id" fail "wn never received the Welcome"; return; }
  wn_b groups accept "$b_gid" >/dev/null 2>&1 || true
  wn_group_field_becomes "$mls_gid" '.group.group_id // empty' "$mls_gid" 120 || { record_result "$id" fail "wn never surfaced the group"; return; }

  amy_json marmot group promote "$gid" "$B_NPUB" >/dev/null || { record_result "$id" fail "amy promote failed"; return; }
  sleep 5
  wn_b groups rename "$mls_gid" "Interop-32-by-wn" >/dev/null 2>&1 || true
  amy_json marmot await rename "$gid" --name "Interop-32-by-wn" --timeout 120 >/dev/null || { record_result "$id" fail "amy did not apply wn's rename"; return; }

  amy_json marmot message send "$gid" "32 from amy after wn commit" >/dev/null || { record_result "$id" fail "amy send failed"; return; }
  if wait_for_message B "$mls_gid" "32 from amy after wn commit" 90; then
    record_result "$id" pass
  else
    record_result "$id" fail "wn never received amy's message sent after wn's commit"
  fi
}

test_33_wn_leaves_amy_admin_group() {
  banner "Test 33 — wn leaves a group amy administers; amy commits the departure"
  local id="33 wn leaves amy's group"

  # Leaving is a SelfRemove proposal that only an admin can commit. Test 15
  # covers a wn admin committing it; here amy is the only admin, so the
  # departure takes effect only if amy commits it during sync.
  local out gid mls_gid b_gid
  out=$(amy_json marmot group create --name "Interop-33") || { record_result "$id" fail "amy group create failed"; return; }
  gid=$(printf '%s' "$out" | jq -r '.group_id')
  mls_gid=$(printf '%s' "$out" | jq -r '.mls_group_id')
  amy_json marmot group add "$gid" "$B_NPUB" >/dev/null || { record_result "$id" fail "amy could not invite wn"; return; }
  b_gid=$(wait_for_invite B 60) || { record_result "$id" fail "wn never received the Welcome"; return; }
  wn_b groups accept "$b_gid" >/dev/null 2>&1 || true
  wn_group_field_becomes "$mls_gid" '.group.group_id // empty' "$mls_gid" 120 || { record_result "$id" fail "wn never surfaced the group"; return; }

  wn_b groups leave "$mls_gid" >/dev/null 2>&1 || { record_result "$id" fail "wn leave failed"; return; }

  local deadline=$(( $(date +%s) + 120 )) show b_still=1
  while [[ $(date +%s) -lt $deadline ]]; do
    show=$(amy_json marmot group show "$gid" 2>/dev/null) || { sleep 3; continue; }
    b_still=$(printf '%s' "$show" | jq --arg p "$B_HEX" '[.members[]? | select((.pubkey // .member_id) == $p)] | length')
    [[ "$b_still" == "0" ]] && break
    sleep 3
  done
  if [[ "$b_still" == "0" ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "amy never committed wn's SelfRemove; wn is still in the tree"
  fi
}

test_34_amy_removes_last_other_member() {
  banner "Test 34 — amy removes the only other member; wn processes its own removal"
  local id="34 amy removes wn from a 2-member group"

  # Test 06 removes one of three members. Removing the only other member leaves
  # the committer alone in the tree, which is the shape a device removal hit:
  # White Noise never applied it and kept showing itself as a member.
  local out gid mls_gid b_gid
  out=$(amy_json marmot group create --name "Interop-34") || { record_result "$id" fail "amy group create failed"; return; }
  gid=$(printf '%s' "$out" | jq -r '.group_id')
  mls_gid=$(printf '%s' "$out" | jq -r '.mls_group_id')
  amy_json marmot group add "$gid" "$B_NPUB" >/dev/null || { record_result "$id" fail "amy could not invite wn"; return; }
  b_gid=$(wait_for_invite B 60) || { record_result "$id" fail "wn never received the Welcome"; return; }
  wn_b groups accept "$b_gid" >/dev/null 2>&1 || true
  wn_group_field_becomes "$mls_gid" '.group.group_id // empty' "$mls_gid" 120 || { record_result "$id" fail "wn never surfaced the group"; return; }

  wn_b messages send "$mls_gid" "34 before removal" >/dev/null 2>&1 || true
  amy_json marmot await message "$gid" --match "34 before removal" --timeout 90 >/dev/null || { record_result "$id" fail "amy never got wn's message"; return; }

  amy_json marmot group remove "$gid" "$B_NPUB" >/dev/null || { record_result "$id" fail "amy remove failed"; return; }

  local deadline=$(( $(date +%s) + 120 )) gone=0 view
  while [[ $(date +%s) -lt $deadline ]]; do
    wn_b sync >/dev/null 2>&1 || true
    # `groups members`, as test 06 reads it: `groups show` carries no member list, and
    # reading one there made this pass before wn had processed anything.
    view=$(wn_b_json groups show "$mls_gid" 2>/dev/null || true)
    if ! wn_b --json groups members "$mls_gid" 2>/dev/null \
         | jq_list members | jq -e --arg p "$B_HEX" \
             'select((.member_id // .pubkey // .public_key) == $p)' >/dev/null 2>&1; then
      gone=1; break
    fi
    sleep 3
  done
  printf '%s' "$view" >"$STATE_DIR/test34-wn-view.json"
  if [[ "$gone" -eq 1 ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "wn still lists itself as a member after amy removed it"
  fi
}
