# shellcheck shell=bash
#
# headless/tests-manage.sh — tests 06, 07, 08, 11.
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
         | jq -e --arg p "$C_HEX" '(.result // .) | .[]? | select((.pubkey // .public_key) == $p)' \
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
    seen=$(wn_b --json groups show "$mls_gid" 2>/dev/null | jq -r '(.result // .) | .name // empty')
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
             | jq -r '(.result // .) | .[]?.pubkey // .[]?.public_key // .[]?' | tr '\n' ' ')
  if [[ "$admins" == *"$A_HEX"* ]]; then
    record_result "$id" fail "A still admin after demote"
  else
    record_result "$id" pass
  fi
}

test_17_readd_after_remove() {
  banner "Test 17 — Re-add after remove restores B's leaf"
  local id="17 re-add after remove"

  # Scenario reported on the info screen: A creates a group, adds B,
  # removes B, then adds B again. Before the fix B rejoined without a
  # usable own_leaf, so B could see metadata but could neither decrypt
  # new posts nor sign messages. This test catches that regression by
  # driving a full round-trip (A→B and B→A) after the re-add.

  local out gid mls_gid
  out=$(amy_json marmot group create --name "Interop-17-readd") || {
    record_result "$id" fail "amy create failed"; return
  }
  gid=$(printf '%s' "$out" | jq -r '.group_id')
  mls_gid=$(printf '%s' "$out" | jq -r '.mls_group_id')
  [[ -n "$gid" && -n "$mls_gid" ]] || {
    record_result "$id" fail "missing ids from create"; return
  }

  # A adds B.
  amy_json marmot group add "$gid" "$B_NPUB" >/dev/null || {
    record_result "$id" fail "initial add B failed"; return
  }
  wait_for_invite B 60 >/dev/null || {
    record_result "$id" fail "B never received first invite"; return
  }
  wn_b groups accept "$mls_gid" >/dev/null 2>&1 || true

  # Confirm the baseline works: A → B and B → A both deliver.
  amy_json marmot message send "$gid" "pre-remove hello" >/dev/null || {
    record_result "$id" fail "A send before remove failed"; return
  }
  wait_for_message B "$mls_gid" "pre-remove hello" 90 || {
    record_result "$id" fail "B missed pre-remove hello"; return
  }
  wn_b messages send "$mls_gid" "pre-remove pong" >/dev/null 2>&1 || true
  amy_json marmot await message "$gid" --match "pre-remove pong" --timeout 60 >/dev/null || {
    record_result "$id" fail "A missed B's pre-remove pong"; return
  }

  # A removes B.
  amy_json marmot group remove "$gid" "$B_NPUB" >/dev/null || {
    record_result "$id" fail "remove B failed"; return
  }
  # Let the Remove commit propagate to B's wnd.
  local deadline=$(( $(date +%s) + 120 )) removed=0
  while [[ $(date +%s) -lt $deadline ]]; do
    if ! wn_b --json groups members "$mls_gid" 2>/dev/null \
         | jq -e --arg p "$B_HEX" '(.result // .) | .[]? | select((.pubkey // .public_key) == $p)' \
         >/dev/null 2>&1; then
      removed=1; break
    fi
    sleep 3
  done
  [[ "$removed" -eq 1 ]] || warn "B still appears in members after remove — continuing"

  # B republishes its KP so the next add can find a fresh one.
  wn_b keys publish >/dev/null 2>&1 || true
  sleep 3

  # A re-adds B.
  amy_json marmot group add "$gid" "$B_NPUB" >/dev/null || {
    record_result "$id" fail "re-add B failed"; return
  }
  wait_for_invite B 60 >/dev/null || {
    record_result "$id" fail "B never received second invite"; return
  }
  wn_b groups accept "$mls_gid" >/dev/null 2>&1 || true

  # Regression probes: after re-add B must have a usable leaf again.
  # (1) A→B: proves B can decrypt new group messages.
  amy_json marmot message send "$gid" "post-readd hello" >/dev/null || {
    record_result "$id" fail "A send after re-add failed"; return
  }
  wait_for_message B "$mls_gid" "post-readd hello" 120 || {
    record_result "$id" fail "B didn't decrypt post-readd message (missing own_leaf?)"; return
  }
  # (2) B→A: proves B can still sign/commit — the exact breakage reported.
  wn_b messages send "$mls_gid" "post-readd pong" >/dev/null 2>&1 || {
    record_result "$id" fail "B send after re-add failed (likely no leaf)"; return
  }
  if amy_json marmot await message "$gid" --match "post-readd pong" --timeout 120 >/dev/null; then
    record_result "$id" pass
  else
    record_result "$id" fail "A never saw B's post-readd pong (B couldn't type)"
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
         | jq -e --arg p "$A_HEX" '(.result // .) | .[]? | select((.pubkey // .public_key) == $p)' \
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
