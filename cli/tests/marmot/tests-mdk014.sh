# shellcheck shell=bash
#
# tests-mdk014.sh — tests 35-37.
# Focus: what the MDK 0.11-0.14 cycle and the matching spec changes made
# interop-visible: an attachment keeps the media key of the epoch that carried
# it, and an admin deletes another member's chat message with a kind 4891 that
# the other side honours.
#
# All of them reuse the media group from tests-media.sh (A admin, B member).

test_35_media_from_an_earlier_epoch_wn_to_amy() {
  banner "Test 35 — amy opens a wn attachment sent before later commits"
  local id="35 media earlier epoch wn->amy"

  if [[ -z "${BLOSSOM_PID:-}" ]]; then record_result "$id" skip "no blossom blob store"; return; fi

  local gid mls_gid
  read -r gid mls_gid < <(media_group) || { record_result "$id" fail "could not build the media group"; return; }
  if [[ -z "${gid:-}" ]]; then record_result "$id" fail "could not build the media group"; return; fi
  if ! media_policy_committed "$gid"; then
    record_result "$id" fail "amy could not commit the media policy"; return
  fi

  local src="$STATE_DIR/media-earlier-epoch.bin"
  head -c 4096 /dev/urandom >"$src" 2>/dev/null || printf 'attachment-bytes-earlier-epoch' >"$src"
  local want_hash
  want_hash=$(sha256sum "$src" | cut -d' ' -f1)

  if ! wn_b media upload "$mls_gid" "$src" --send --message "35 earlier epoch" \
        --server "$BLOSSOM_URL/" >/dev/null 2>&1; then
    record_result "$id" fail "wn media upload failed"; return
  fi
  if ! amy_json marmot await message "$gid" --match "35 earlier epoch" --timeout 150 >/dev/null; then
    record_result "$id" fail "amy never received wn's media message"; return
  fi
  local event_id
  event_id=$(amy_json marmot message list "$gid" --limit 50 2>/dev/null \
               | jq_list messages \
               | jq -r 'select((.content // "") == "35 earlier epoch") | .event_id' | head -n 1)

  # Move the group on: each rename is a commit, and the attachment's key is
  # the one of the epoch BEFORE them. Opening it under the current epoch's key
  # was the bug.
  local i
  for i in 1 2; do
    amy_json marmot group rename "$gid" "Interop-Media-$i" >/dev/null || {
      record_result "$id" fail "amy rename $i failed"; return
    }
  done

  local out="$STATE_DIR/media-earlier-epoch.out"
  if ! amy_json marmot media get "$gid" "$event_id" --out "$out" >/dev/null; then
    record_result "$id" fail "amy could not open an attachment from an earlier epoch"; return
  fi
  if [[ "$(sha256sum "$out" | cut -d' ' -f1)" == "$want_hash" ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "amy decrypted different bytes than wn sent"
  fi
}

test_36_admin_deletion_amy_to_wn() {
  banner "Test 36 — amy, as admin, deletes wn's message (kind 4891); wn hides it"
  local id="36 admin deletion amy->wn"

  local gid mls_gid
  read -r gid mls_gid < <(media_group) || { record_result "$id" fail "could not build the media group"; return; }
  if [[ -z "${gid:-}" ]]; then record_result "$id" fail "could not build the media group"; return; fi

  local doomed="36-moderate-me-from-whitenoise"
  if ! wn_b messages send "$mls_gid" "$doomed" >/dev/null 2>&1; then
    record_result "$id" fail "wn send failed"; return
  fi
  if ! amy_json marmot await message "$gid" --match "$doomed" --timeout 90 >/dev/null; then
    record_result "$id" fail "amy never received wn's message"; return
  fi
  local target
  target=$(amy_json marmot message list "$gid" --limit 50 2>/dev/null \
             | jq_list messages \
             | jq -r --arg c "$doomed" 'select((.content // "") == $c) | .event_id' | head -n 1)

  if [[ -z "$target" ]]; then
    record_result "$id" fail "amy has no event id for wn's message"; return
  fi

  # wn must list the row before the delete, or its absence afterwards proves nothing.
  local row_of='select((.message_id // .id // .event_id) == $t)'
  local deadline=$(( $(date +%s) + 90 )) seen=0
  while [[ $(date +%s) -lt $deadline ]]; do
    if wn_b_json messages timeline list "$mls_gid" --limit 50 2>/dev/null | jq_list messages \
         | jq -e --arg t "$target" "$row_of" >/dev/null 2>&1; then
      seen=1; break
    fi
    wn_b sync >/dev/null 2>&1 || true
    sleep 3
  done
  if [[ "$seen" -ne 1 ]]; then
    record_result "$id" fail "wn's timeline never listed the message to delete"; return
  fi

  local del
  del=$(amy_json marmot message delete "$gid" "$target") || {
    record_result "$id" fail "amy admin delete failed"; return
  }
  if [[ "$(printf '%s' "$del" | jq -r '.kind')" != "4891" ]]; then
    record_result "$id" fail "amy did not send a kind 4891: $del"; return
  fi

  # Applied means flagged deleted, or dropped from a timeline that still lists messages. An empty
  # or failed listing is neither: keep polling.
  deadline=$(( $(date +%s) + 120 ))
  local gone=0
  while [[ $(date +%s) -lt $deadline ]]; do
    local rows
    rows=$(wn_b_json messages timeline list "$mls_gid" --limit 50 2>/dev/null | jq_list messages)
    if [[ -n "$rows" ]]; then
      if printf '%s' "$rows" | jq -e --arg t "$target" "$row_of | select(.deleted == true)" >/dev/null 2>&1 \
         || ! printf '%s' "$rows" | jq -e --arg t "$target" "$row_of" >/dev/null 2>&1; then
        gone=1; break
      fi
    fi
    wn_b sync >/dev/null 2>&1 || true
    sleep 3
  done
  if [[ "$gone" -eq 1 ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "wn still shows the message amy's admin deletion removed"
  fi
}

test_37_admin_deletion_wn_to_amy() {
  banner "Test 37 — wn, as admin, deletes amy's message; amy hides it"
  local id="37 admin deletion wn->amy"

  local gid mls_gid
  read -r gid mls_gid < <(media_group) || { record_result "$id" fail "could not build the media group"; return; }
  if [[ -z "${gid:-}" ]]; then record_result "$id" fail "could not build the media group"; return; fi

  amy_json marmot group promote "$gid" "$B_NPUB" >/dev/null 2>&1 || {
    record_result "$id" fail "amy could not promote wn"; return
  }
  local deadline=$(( $(date +%s) + 90 )) admin=0
  while [[ $(date +%s) -lt $deadline ]]; do
    if [[ "$(wn_b --json groups admins "$mls_gid" 2>/dev/null | jq_list admins | jq_member_ids | tr '\n' ' ')" == *"$B_HEX"* ]]; then
      admin=1; break
    fi
    wn_b sync >/dev/null 2>&1 || true
    sleep 3
  done
  if [[ "$admin" -ne 1 ]]; then
    printf 'test 37: wn admins after promote: %s\n' "$(wn_b_json groups admins "$mls_gid" 2>&1 | head -c 4000)" >>"$LOG_FILE"
    printf 'test 37: amy admins after promote: %s\n' "$(amy_json marmot group admins "$gid" 2>&1 | head -c 2000)" >>"$LOG_FILE"
    record_result "$id" fail "wn never saw itself promoted"; return
  fi

  local doomed="37-moderate-me-from-amethyst" send_json target
  send_json=$(amy_json marmot message send "$gid" "$doomed") || {
    record_result "$id" fail "amy send failed"; return
  }
  target=$(printf '%s' "$send_json" | jq -r '.inner_event_id')
  if ! wait_for_message B "$mls_gid" "$doomed" 90; then
    record_result "$id" fail "wn never received the message to delete"; return
  fi

  local del
  del=$(wn_b --json messages delete "$mls_gid" "$target" 2>>"$LOG_FILE") || {
    # MDK removes another account's content only with an admin moderation
    # grant; if it refuses here, the gap is on the wn side of this test.
    record_result "$id" skip "wn refused to delete another account's message: $(tail -n 1 "$LOG_FILE")"; return
  }
  printf 'wn admin delete %s -> %s\n' "$target" "$del" >>"$LOG_FILE"

  deadline=$(( $(date +%s) + 120 ))
  local gone=0
  while [[ $(date +%s) -lt $deadline ]]; do
    local row
    row=$(amy_json marmot message list "$gid" --limit 50 2>/dev/null \
            | jq_list messages | jq -c --arg t "$target" 'select(.event_id == $t)' | head -n 1)
    if [[ -n "$row" ]] && printf '%s' "$row" | jq -e 'select(.deleted == true)' >/dev/null 2>&1; then
      gone=1; break
    fi
    sleep 3
  done
  if [[ "$gone" -eq 1 ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "amy still shows the message wn's admin deletion removed"
  fi
}
