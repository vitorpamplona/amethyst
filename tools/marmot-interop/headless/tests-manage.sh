# shellcheck shell=bash
#
# headless/tests-manage.sh — tests 06, 07, 08, 11.
# Focus: removal, metadata rename, admin promote/demote, leave.

test_06_member_removal() {
  banner "Test 06 — Member removal + forward secrecy"
  local id="06 member removal"

  local gid; gid=$(load_state GROUP_05 || true)
  if [[ -z "${gid:-}" ]]; then
    record_result "$id" skip "no GROUP_05"; return
  fi

  amy_json marmot group remove "$gid" "$C_NPUB" >/dev/null || {
    record_result "$id" fail "amy remove C failed"; return
  }

  # C should no longer see the group on its own member view.
  local deadline=$(( $(date +%s) + 60 )) removed=0
  while [[ $(date +%s) -lt $deadline ]]; do
    if ! wn_c --json groups members "$gid" 2>/dev/null \
         | jq -e --arg p "$C_HEX" '.[]? | select((.pubkey // .public_key) == $p)' \
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
  wait_for_message B "$gid" "after removing C" 30 || {
    record_result "$id" fail "B lost access after C's removal"; return
  }

  # Forward secrecy: C must NOT see the post-removal message.
  sleep 5
  if wait_for_message C "$gid" "after removing C" 10; then
    record_result "$id" fail "C still decrypted a post-removal message"
  else
    record_result "$id" pass
  fi
}

test_07_metadata_rename() {
  banner "Test 07 — Metadata rename round-trip (MIP-01)"
  local id="07 metadata rename"

  local gid; gid=$(load_state GROUP_02 || true)
  if [[ -z "${gid:-}" ]]; then
    record_result "$id" skip "no GROUP_02"; return
  fi

  amy_json marmot group rename "$gid" "Interop-02-renamed" >/dev/null || {
    record_result "$id" fail "amy rename failed"; return
  }

  local deadline=$(( $(date +%s) + 60 )) seen=""
  while [[ $(date +%s) -lt $deadline ]]; do
    seen=$(wn_b --json groups show "$gid" 2>/dev/null | jq -r '.name // empty')
    [[ "$seen" == "Interop-02-renamed" ]] && break
    sleep 3
  done
  [[ "$seen" == "Interop-02-renamed" ]] || {
    record_result "$id" fail "B saw name=\"$seen\" not \"Interop-02-renamed\""; return
  }

  # Now B renames back and A should pick it up.
  wn_b groups rename "$gid" "Interop-02-reverse" >/dev/null 2>&1 || true
  if amy_json marmot await rename "$gid" --name "Interop-02-reverse" --timeout 60 >/dev/null; then
    record_result "$id" pass
  else
    record_result "$id" fail "A did not pick up B's rename"
  fi
}

test_08_admin_promote_demote() {
  banner "Test 08 — Admin promote / demote"
  local id="08 admin promote/demote"

  local gid; gid=$(load_state GROUP_03 || true)
  if [[ -z "${gid:-}" ]]; then
    record_result "$id" skip "no GROUP_03"; return
  fi

  # Ensure 3 members (add C if missing).
  wn_c keys publish >/dev/null 2>&1 || true
  sleep 2
  wn_b groups add-members "$gid" "$C_NPUB" >/dev/null 2>&1 || true
  wait_for_invite C 30 >/dev/null && wn_c groups accept "$gid" >/dev/null 2>&1 || true

  # B promotes A.
  wn_b groups promote "$gid" "$A_NPUB" >/dev/null 2>&1 || {
    record_result "$id" fail "wn promote failed"; return
  }

  # A should reflect the new admin set — poll via amy.
  if ! amy_json marmot await admin "$gid" "$A_NPUB" --timeout 30 >/dev/null; then
    record_result "$id" fail "A never saw itself promoted"; return
  fi

  # A now commits a rename — only possible if we're admin.
  amy_json marmot group rename "$gid" "Interop-03-by-A" >/dev/null || {
    record_result "$id" fail "A (now admin) could not rename"; return
  }

  # B demotes A.
  wn_b groups demote "$gid" "$A_NPUB" >/dev/null 2>&1 || warn "demote returned nonzero"
  sleep 5

  local admins
  admins=$(wn_b --json groups admins "$gid" 2>/dev/null \
             | jq -r '.[].pubkey // .[].public_key // .[]' | tr '\n' ' ')
  if [[ "$admins" == *"$A_HEX"* ]]; then
    record_result "$id" fail "A still admin after demote"
  else
    record_result "$id" pass
  fi
}

test_11_leave_group() {
  banner "Test 11 — Leave group"
  local id="11 leave group"

  local gid; gid=$(load_state GROUP_02 || true)
  if [[ -z "${gid:-}" ]]; then
    record_result "$id" skip "no GROUP_02"; return
  fi

  amy_json marmot group leave "$gid" >/dev/null || {
    record_result "$id" fail "amy leave failed"; return
  }

  local deadline=$(( $(date +%s) + 60 )) gone=0
  while [[ $(date +%s) -lt $deadline ]]; do
    if ! wn_b --json groups members "$gid" 2>/dev/null \
         | jq -e --arg p "$A_HEX" '.[]? | select((.pubkey // .public_key) == $p)' \
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
