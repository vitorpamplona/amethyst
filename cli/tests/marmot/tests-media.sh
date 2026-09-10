# shellcheck shell=bash
#
# tests-media.sh — tests 20-25.
# Focus: the avatar URL component, message edits, deletions, and
# encrypted-media v2 — each in both directions where the reference CLI can
# drive it.
#
# These all need A and B in one group with A as admin, so they share a group
# built once by the first test that needs it.

# Create (or reuse) the group these tests run in: A creates it, so A is the
# admin who may commit component updates, and B joins.
#
# It is its own group rather than GROUP_02 because by this point in the run A
# has left GROUP_02 and been removed from others, and every check here needs
# both parties actually present.
media_group() {
  local gid mls_gid
  gid=$(load_state GROUP_MEDIA || true)
  mls_gid=$(load_state GROUP_MEDIA_MLS || true)
  if [[ -n "${gid:-}" && -n "${mls_gid:-}" ]]; then
    printf '%s %s\n' "$gid" "$mls_gid"
    return 0
  fi

  local out
  out=$(amy_json marmot group create --name "Interop-Media") || return 1
  gid=$(printf '%s' "$out" | jq -r '.group_id')
  mls_gid=$(printf '%s' "$out" | jq -r '.mls_group_id')
  amy_json marmot group add "$gid" "$B_NPUB" >/dev/null || return 1

  local b_gid
  b_gid=$(wait_for_invite B 60) || return 1
  wn_b groups accept "$b_gid" >/dev/null 2>&1 || true

  save_state GROUP_MEDIA "$gid"
  save_state GROUP_MEDIA_MLS "$mls_gid"
  printf '%s %s\n' "$gid" "$mls_gid"
}

# Poll wn's view of the group until [jq filter] matches, or time out.
wn_group_field_becomes() {
  local mls_gid="$1" filter="$2" want="$3" timeout="${4:-90}"
  local deadline=$(( $(date +%s) + timeout )) got
  while [[ $(date +%s) -lt $deadline ]]; do
    # wn wraps every --json payload in {"ok":…,"result":…}; try inside the
    # envelope first and fall back to a bare payload so a future shape change
    # does not silently make this poll always fail.
    got=$(wn_b_json groups show "$mls_gid" 2>/dev/null \
            | jq -r "(.result | $filter) // ($filter) // empty" 2>/dev/null || true)
    [[ "$got" == "$want" ]] && return 0
    wn_b sync >/dev/null 2>&1 || true
    sleep 3
  done
  printf 'wn_group_field_becomes: %s was %s, wanted %s\n' "$filter" "${got:-<none>}" "$want" >>"$LOG_FILE"
  return 1
}

test_20_avatar_url_amy_to_wn() {
  banner "Test 20 — amy commits a URL avatar; wn reads it back"
  local id="20 avatar-url amy->wn"

  local gid mls_gid
  read -r gid mls_gid < <(media_group) || { record_result "$id" fail "could not build the media group"; return; }
  if [[ -z "${gid:-}" ]]; then record_result "$id" fail "could not build the media group"; return; fi

  # The stored bytes are the NORMALIZED URL, so this deliberately passes a URL
  # that is not: the default port and the dot-segment both have to disappear,
  # and both sides have to agree on exactly what is left. A decoder rejects
  # state whose bytes differ from its own serialization, so a mismatch here is
  # a group wn cannot read at all rather than a cosmetic difference.
  local raw="https://Example.COM:443/a/./avatars/../pic.png"
  local want="https://example.com/a/pic.png"

  local out stored
  out=$(amy_json marmot group set-avatar-url "$gid" "$raw" --dim "512x512") || {
    record_result "$id" fail "amy set-avatar-url failed"; return
  }
  stored=$(printf '%s' "$out" | jq -r '.avatar_url // empty')
  if [[ "$stored" != "$want" ]]; then
    record_result "$id" fail "amy stored '$stored', expected the normalized '$want'"; return
  fi

  if wn_group_field_becomes "$mls_gid" '.group.avatar_url.url // empty' "$want" 120; then
    record_result "$id" pass
  else
    record_result "$id" fail "wn never saw the URL avatar"
  fi
}

test_21_avatar_url_wn_to_amy() {
  banner "Test 21 — wn commits a URL avatar; amy reads it back"
  local id="21 avatar-url wn->amy"

  local gid mls_gid
  read -r gid mls_gid < <(media_group) || { record_result "$id" fail "could not build the media group"; return; }
  if [[ -z "${gid:-}" ]]; then record_result "$id" fail "could not build the media group"; return; fi

  # B has to be an admin to commit a component update.
  amy_json marmot group promote "$gid" "$B_NPUB" >/dev/null || {
    record_result "$id" fail "amy could not promote B"; return
  }
  sleep 3
  wn_b sync >/dev/null 2>&1 || true

  local want="https://cdn.example.org/group.png"
  if ! wn_b groups set-avatar-url "$mls_gid" --url "$want" >/dev/null 2>&1; then
    record_result "$id" fail "wn set-avatar-url failed"; return
  fi

  local deadline=$(( $(date +%s) + 120 )) got
  while [[ $(date +%s) -lt $deadline ]]; do
    got=$(amy_json marmot group show "$gid" 2>/dev/null | jq -r '.avatar_url // empty')
    [[ "$got" == "$want" ]] && break
    sleep 3
  done
  if [[ "${got:-}" == "$want" ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "amy saw '${got:-<none>}', expected '$want'"
  fi
}

test_22_message_edit_amy_to_wn() {
  banner "Test 22 — amy edits a message; wn receives the 1009 and amy overlays it"
  local id="22 edit amy->wn"

  local gid mls_gid
  read -r gid mls_gid < <(media_group) || { record_result "$id" fail "could not build the media group"; return; }
  if [[ -z "${gid:-}" ]]; then record_result "$id" fail "could not build the media group"; return; fi

  local original="edit-target-frist-post"
  local replacement="edit-target-first-post"
  local send_json target
  send_json=$(amy_json marmot message send "$gid" "$original") || {
    record_result "$id" fail "amy send failed"; return
  }
  target=$(printf '%s' "$send_json" | jq -r '.inner_event_id')
  if ! wait_for_message B "$mls_gid" "$original" 90; then
    record_result "$id" fail "wn never received the original"; return
  fi

  if ! amy_json marmot message edit "$gid" "$target" "$replacement" >/dev/null; then
    record_result "$id" fail "amy message edit failed"; return
  fi

  # What is checked on wn's side is that the EDIT EVENT interoperates: a
  # kind:1009 carrying exactly one `e` tag naming the target, the replacement
  # as its body, authored by A. Whether the reference CLI paints the overlay is
  # its rendering choice — MDK's storage deliberately leaves the original row's
  # body alone and lets the client compute the chain — so asserting on painted
  # text would be testing its TUI, not the protocol.
  local deadline=$(( $(date +%s) + 120 )) saw=0
  while [[ $(date +%s) -lt $deadline ]]; do
    local payload
    payload=$(wn_b_json messages list "$mls_gid" --limit 50 2>/dev/null || true)
    if [[ -n "$payload" ]] && \
         printf '%s' "$payload" | jq_list messages \
           | jq -e --arg t "$target" --arg r "$replacement" --arg a "$A_HEX" \
                 'select(.kind == 1009)
                  | select((.plaintext // .content // "") == $r)
                  | select((.pubkey // .author // $a) == $a)
                  | select([(.tags // [])[] | select(.[0] == "e") | .[1]] == [$t])' \
                 >/dev/null 2>&1; then
      saw=1; break
    fi
    wn_b sync >/dev/null 2>&1 || true
    sleep 3
  done
  if [[ "$saw" -ne 1 ]]; then
    record_result "$id" fail "wn never received a well-formed kind:1009 for the target"; return
  fi

  # And our own reader must apply it: the target's body reads as the
  # replacement and is flagged as edited, with no separate row for the edit.
  local body edited
  body=$(amy_json marmot message list "$gid" --limit 50 2>/dev/null \
           | jq_list messages | jq -r --arg t "$target" 'select(.event_id == $t) | .content' | head -n 1)
  edited=$(amy_json marmot message list "$gid" --limit 50 2>/dev/null \
             | jq_list messages | jq -r --arg t "$target" 'select(.event_id == $t) | .edited' | head -n 1)
  if [[ "$body" == "$replacement" && "$edited" == "true" ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "amy shows '$body' (edited=$edited) for the edited message"
  fi
}

test_23_deletion_amy_to_wn() {
  banner "Test 23 — amy deletes a message; wn marks it deleted"
  local id="23 deletion amy->wn"

  local gid mls_gid
  read -r gid mls_gid < <(media_group) || { record_result "$id" fail "could not build the media group"; return; }
  if [[ -z "${gid:-}" ]]; then record_result "$id" fail "could not build the media group"; return; fi

  local doomed="delete-me-from-amethyst"
  local send_json target
  send_json=$(amy_json marmot message send "$gid" "$doomed") || {
    record_result "$id" fail "amy send failed"; return
  }
  target=$(printf '%s' "$send_json" | jq -r '.inner_event_id')
  if ! wait_for_message B "$mls_gid" "$doomed" 90; then
    record_result "$id" fail "wn never received the message to delete"; return
  fi

  if ! amy_json marmot message delete "$gid" "$target" >/dev/null; then
    record_result "$id" fail "amy message delete failed"; return
  fi

  # wn's materialized timeline carries a `deleted` flag per row — that is the
  # user-visible truth, and it is what a kind:5 from another implementation has
  # to be able to set. The raw event log keeps both events either way.
  local deadline=$(( $(date +%s) + 120 )) gone=0
  while [[ $(date +%s) -lt $deadline ]]; do
    local payload
    payload=$(wn_b_json messages timeline list "$mls_gid" --limit 50 2>/dev/null || true)
    if [[ -n "$payload" ]] && \
         printf '%s' "$payload" | jq_list messages \
           | jq -e --arg t "$target" \
                 'select((.message_id // .id // .event_id) == $t) | select(.deleted == true)' \
                 >/dev/null 2>&1; then
      gone=1; break
    fi
    wn_b sync >/dev/null 2>&1 || true
    sleep 3
  done

  if [[ "$gone" -eq 1 ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "wn never marked the message deleted"
  fi
}

# --- encrypted media v2 (0x800b) --------------------------------------------
# Both directions upload ciphertext to the harness's loopback Blossom store and
# fetch the other side's back. The store never holds a key: the file key comes
# from each group's own MLS exporter, so a successful download that hashes back
# to the original bytes is proof both implementations derived the same one.

media_policy_committed() {
  local gid="$1"
  if [[ -n "$(load_state MEDIA_POLICY_SET || true)" ]]; then return 0; fi
  amy_json marmot media set-policy "$gid" "$BLOSSOM_URL/" >/dev/null || return 1
  save_state MEDIA_POLICY_SET 1
  sleep 3
  wn_b sync >/dev/null 2>&1 || true
  return 0
}

test_24_media_v2_amy_to_wn() {
  banner "Test 24 — amy sends an encrypted attachment; wn decrypts it"
  local id="24 media-v2 amy->wn"

  if [[ -z "${BLOSSOM_PID:-}" ]]; then record_result "$id" skip "no blossom blob store"; return; fi

  local gid mls_gid
  read -r gid mls_gid < <(media_group) || { record_result "$id" fail "could not build the media group"; return; }
  if [[ -z "${gid:-}" ]]; then record_result "$id" fail "could not build the media group"; return; fi

  if ! media_policy_committed "$gid"; then
    record_result "$id" fail "amy could not commit the media policy"; return
  fi

  local src="$STATE_DIR/media-from-amy.bin"
  head -c 4096 /dev/urandom >"$src" 2>/dev/null || printf 'attachment-bytes-from-amethyst' >"$src"
  local want_hash
  want_hash=$(sha256sum "$src" | cut -d' ' -f1)

  local send_json
  send_json=$(amy_json marmot media send "$gid" "$src" --caption "from amethyst" --mime "application/octet-stream") || {
    record_result "$id" fail "amy media send failed"; return
  }
  printf 'media24 send=%s\n' "$send_json" >>"$LOG_FILE"

  # wn recovers the plaintext by hash. Its `media download` takes the PLAINTEXT
  # hash, which is what it also uses to key its own reference index — so
  # finding it there at all already proves the imeta tag parsed.
  local out="$STATE_DIR/media-to-wn.bin"
  local deadline=$(( $(date +%s) + 150 )) ok=1
  while [[ $(date +%s) -lt $deadline ]]; do
    if wn_b media download "$mls_gid" "$want_hash" --output "$out" >/dev/null 2>&1; then ok=0; break; fi
    wn_b sync >/dev/null 2>&1 || true
    sleep 5
  done

  if [[ "$ok" -ne 0 ]]; then
    record_result "$id" fail "wn could not download the attachment"; return
  fi
  if [[ "$(sha256sum "$out" | cut -d' ' -f1)" == "$want_hash" ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "wn decrypted different bytes than amy sent"
  fi
}

test_25_media_v2_wn_to_amy() {
  banner "Test 25 — wn sends an encrypted attachment; amy decrypts it"
  local id="25 media-v2 wn->amy"

  if [[ -z "${BLOSSOM_PID:-}" ]]; then record_result "$id" skip "no blossom blob store"; return; fi

  local gid mls_gid
  read -r gid mls_gid < <(media_group) || { record_result "$id" fail "could not build the media group"; return; }
  if [[ -z "${gid:-}" ]]; then record_result "$id" fail "could not build the media group"; return; fi

  if ! media_policy_committed "$gid"; then
    record_result "$id" fail "amy could not commit the media policy"; return
  fi

  local src="$STATE_DIR/media-from-wn.bin"
  head -c 4096 /dev/urandom >"$src" 2>/dev/null || printf 'attachment-bytes-from-whitenoise' >"$src"
  local want_hash
  want_hash=$(sha256sum "$src" | cut -d' ' -f1)

  if ! wn_b media upload "$mls_gid" "$src" --send --message "from whitenoise" \
        --server "$BLOSSOM_URL/" >/dev/null 2>&1; then
    record_result "$id" fail "wn media upload failed"; return
  fi

  # Find the kind:9 wn just sent, by its caption, and pull the attachment out
  # of its imeta tag.
  local deadline=$(( $(date +%s) + 150 )) event_id=""
  while [[ $(date +%s) -lt $deadline ]]; do
    event_id=$(amy_json marmot message list "$gid" --limit 50 2>/dev/null \
                 | jq_list messages \
                 | jq -r 'select((.content // "") == "from whitenoise") | .event_id' | head -n 1)
    [[ -n "$event_id" && "$event_id" != "null" ]] && break
    sleep 5
  done
  if [[ -z "$event_id" || "$event_id" == "null" ]]; then
    record_result "$id" fail "amy never received wn's media message"; return
  fi

  local out="$STATE_DIR/media-to-amy.bin"
  if ! amy_json marmot media get "$gid" "$event_id" --out "$out" >/dev/null; then
    record_result "$id" fail "amy media get failed"; return
  fi
  if [[ "$(sha256sum "$out" | cut -d' ' -f1)" == "$want_hash" ]]; then
    record_result "$id" pass
  else
    record_result "$id" fail "amy decrypted different bytes than wn sent"
  fi
}

# --- 26: disappearing messages ------------------------------------------------
# `marmot.group.message-retention.v1` (0x8005) has the nastiest encoding in the
# component set: eight big-endian bytes with NO length prefix, unlike almost
# every other Marmot field, and MIP-01 spelled it differently. Nothing else
# proves MDK accepts a GroupContext that REQUIRES it with our bytes — and if it
# does not, the failure is not cosmetic: wn cannot read the group at all.
#
# One-directional on purpose. `wn` has no command that sets retention
# (`wn groups` is list/create/show/add-members/remove-members/members/admins/
# relays/leave/rename/set-avatar-url), so the reverse direction is untestable
# here. The encode side is the one that can be wrong anyway. What amy DOES with
# the expiry once it holds one is unit-tested in `MarmotRetentionTest`; this is
# purely "does the other implementation accept and read what we wrote".
test_26_retention_amy_to_wn() {
  banner "Test 26 — amy creates a group with disappearing messages; wn reads the policy"
  local id="26 retention amy->wn"

  local want=3600
  local out gid mls_gid
  out=$(amy_json marmot group create --name "Interop-Retention" --disappearing-secs "$want") || {
    record_result "$id" fail "amy group create --disappearing-secs failed"; return
  }
  gid=$(printf '%s' "$out" | jq -r '.group_id')
  mls_gid=$(printf '%s' "$out" | jq -r '.mls_group_id')
  if [[ -z "$gid" || "$gid" == "null" ]]; then
    record_result "$id" fail "amy reported no group id"; return
  fi

  amy_json marmot group add "$gid" "$B_NPUB" >/dev/null || {
    record_result "$id" fail "amy could not invite wn"; return
  }

  # The Welcome is the real assertion: a group requiring a component wn cannot
  # decode is a group wn refuses to join.
  local b_gid
  b_gid=$(wait_for_invite B 60) || {
    record_result "$id" fail "wn never received a Welcome for a group requiring 0x8005"; return
  }
  wn_b groups accept "$b_gid" >/dev/null 2>&1 || true

  # wn's CLI `group_json` does not surface the retention value — it is on the
  # uniffi group struct the apps consume, not this surface — so the assertion
  # is acceptance rather than read-back. That is still the encoding test: the
  # group REQUIRES 0x8005, and a required component whose bytes wn cannot
  # decode makes the group unreadable, so `groups show` returning it at all
  # means our eight big-endian bytes parsed.
  if ! wn_group_field_becomes "$mls_gid" '.group.group_id // empty' "$mls_gid" 120; then
    record_result "$id" fail "wn never surfaced a group that requires 0x8005"; return
  fi

  # And the group still works: a required component that decodes but breaks
  # messaging would pass the check above and still be useless.
  wn_b messages send "$mls_gid" "retention round trip" >/dev/null 2>&1 || {
    record_result "$id" fail "wn could not send into the retention group"; return
  }
  if amy_json marmot await message "$gid" --match "retention round trip" --timeout 90 >/dev/null; then
    record_result "$id" pass
  else
    record_result "$id" fail "amy never received wn's message in the retention group"
  fi
}

# --- 27: a deletion the other way ---------------------------------------------
# Test 23 proves MDK applies OUR kind:5. This is the direction that was never
# covered, and it is the worse failure of the two: a message its sender believes
# is gone that stays on screen here.
#
# The authorization rule is the whole test. A kind:5 is authorized by ACCOUNT,
# so wn retracting its OWN message must land, and the forged cross-author case
# — which no CLI can send — is pinned in `MarmotEditsAndSystemRowsTest`.
test_27_deletion_wn_to_amy() {
  banner "Test 27 — wn deletes its own message; amy marks it deleted"
  local id="27 deletion wn->amy"

  local gid mls_gid
  read -r gid mls_gid < <(media_group) || { record_result "$id" fail "could not build the media group"; return; }
  if [[ -z "${gid:-}" ]]; then record_result "$id" fail "could not build the media group"; return; fi

  local doomed="delete-me-from-whitenoise"
  if ! wn_b messages send "$mls_gid" "$doomed" >/dev/null 2>&1; then
    record_result "$id" fail "wn send failed"; return
  fi
  if ! amy_json marmot await message "$gid" --match "$doomed" --timeout 90 >/dev/null; then
    record_result "$id" fail "amy never received the message to delete"; return
  fi

  # Both sides key the message by the SAME inner event id, so amy's view of it
  # is what we hand back to wn's deleter.
  local target
  target=$(amy_json marmot message list "$gid" --limit 50 2>/dev/null \
             | jq_list messages \
             | jq -r --arg c "$doomed" 'select((.content // "") == $c) | .event_id' | head -n 1)
  if [[ -z "$target" || "$target" == "null" ]]; then
    record_result "$id" fail "amy has no event id for wn's message"; return
  fi

  if ! wn_b messages delete "$mls_gid" "$target" >/dev/null 2>&1; then
    record_result "$id" fail "wn messages delete failed"; return
  fi

  # The row stays, blanked and flagged: "retracted" and "never arrived" are
  # different states to a reader, and only one of them is worth telling them
  # about.
  local deadline=$(( $(date +%s) + 120 )) gone=0 body="unset"
  while [[ $(date +%s) -lt $deadline ]]; do
    local row
    row=$(amy_json marmot message list "$gid" --limit 50 2>/dev/null \
            | jq_list messages | jq -c --arg t "$target" 'select(.event_id == $t)' | head -n 1)
    if [[ -n "$row" ]] && printf '%s' "$row" | jq -e 'select(.deleted == true)' >/dev/null 2>&1; then
      gone=1
      body=$(printf '%s' "$row" | jq -r '.content')
      break
    fi
    sleep 3
  done

  if [[ "$gone" -ne 1 ]]; then
    record_result "$id" fail "amy never marked wn's message deleted"; return
  fi
  if [[ -n "$body" ]]; then
    record_result "$id" fail "amy flagged the message deleted but still shows '$body'"; return
  fi
  record_result "$id" pass
}

# --- 28: retention, applied to what wn sends ----------------------------------
# Test 26 proves MDK ACCEPTS a group that requires `0x8005` with our bytes. This
# is the read side, and it is the half the epoch-pinning rule lives in: each
# message keeps the retention of the epoch that DELIVERED it, so a later change
# must not shorten, extend or restore an expiry that already exists.
#
# Driving it from wn is the point. Our own sends pin at persist time from state
# this client just wrote; an inbound message arrives under an epoch the group
# may already have moved past, which is exactly where the fallback used to be
# wrong. (`wn` itself has no retention setter — `wn groups` is list/create/show/
# add-members/remove-members/members/admins/relays/leave/rename/set-avatar-url —
# so amy owns the setting and wn owns the sending.)
test_28_retention_wn_to_amy() {
  banner "Test 28 — amy re-times a group; wn's messages pin the epoch that delivered them"
  local id="28 retention wn->amy"

  local short=60 long=86400
  local out gid mls_gid
  out=$(amy_json marmot group create --name "Interop-Retention-Inbound" --disappearing-secs "$short") || {
    record_result "$id" fail "amy group create --disappearing-secs failed"; return
  }
  gid=$(printf '%s' "$out" | jq -r '.group_id')
  mls_gid=$(printf '%s' "$out" | jq -r '.mls_group_id')
  if [[ -z "$gid" || "$gid" == "null" ]]; then
    record_result "$id" fail "amy reported no group id"; return
  fi

  amy_json marmot group add "$gid" "$B_NPUB" >/dev/null || {
    record_result "$id" fail "amy could not invite wn"; return
  }
  local b_gid
  b_gid=$(wait_for_invite B 60) || {
    record_result "$id" fail "wn never received the Welcome"; return
  }
  wn_b groups accept "$b_gid" >/dev/null 2>&1 || true

  local early="retention-inbound-early"
  wn_b messages send "$mls_gid" "$early" >/dev/null 2>&1 || {
    record_result "$id" fail "wn could not send under the first policy"; return
  }
  if ! amy_json marmot await message "$gid" --match "$early" --timeout 90 >/dev/null; then
    record_result "$id" fail "amy never received wn's first message"; return
  fi

  # Now move the policy. The commit opens a new epoch, and only messages
  # delivered by that epoch take the new duration.
  if ! amy_json marmot group set-retention "$gid" "$long" >/dev/null; then
    record_result "$id" fail "amy set-retention failed"; return
  fi
  sleep 3
  wn_b sync >/dev/null 2>&1 || true

  local late="retention-inbound-late"
  local sent=0 attempt
  for attempt in 1 2 3 4 5; do
    if wn_b messages send "$mls_gid" "$late" >/dev/null 2>&1; then sent=1; break; fi
    wn_b sync >/dev/null 2>&1 || true
    sleep 5
  done
  if [[ "$sent" -ne 1 ]]; then
    record_result "$id" fail "wn could not send after the retention change"; return
  fi
  if ! amy_json marmot await message "$gid" --match "$late" --timeout 120 >/dev/null; then
    record_result "$id" fail "amy never received wn's second message"; return
  fi

  # `expires_at` is the value amy PINNED, not a recomputation: created_at plus
  # the duration that applied at the delivering epoch.
  local rows early_created early_expiry late_created late_expiry
  rows=$(amy_json marmot message list "$gid" --limit 50 2>/dev/null | jq_list messages)
  early_created=$(printf '%s' "$rows" | jq -r --arg c "$early" 'select((.content // "") == $c) | .created_at' | head -n 1)
  early_expiry=$(printf '%s' "$rows" | jq -r --arg c "$early" 'select((.content // "") == $c) | .expires_at' | head -n 1)
  late_created=$(printf '%s' "$rows" | jq -r --arg c "$late" 'select((.content // "") == $c) | .created_at' | head -n 1)
  late_expiry=$(printf '%s' "$rows" | jq -r --arg c "$late" 'select((.content // "") == $c) | .expires_at' | head -n 1)
  printf 'retention28 early=%s/%s late=%s/%s\n' \
    "${early_created:-?}" "${early_expiry:-?}" "${late_created:-?}" "${late_expiry:-?}" >>"$LOG_FILE"

  if [[ -z "$early_expiry" || "$early_expiry" == "null" || -z "$late_expiry" || "$late_expiry" == "null" ]]; then
    record_result "$id" fail "amy pinned no expiry for one of wn's messages"; return
  fi
  if [[ "$early_expiry" -ne $(( early_created + short )) ]]; then
    record_result "$id" fail "the first message expires at $early_expiry, wanted $(( early_created + short ))"; return
  fi
  if [[ "$late_expiry" -ne $(( late_created + long )) ]]; then
    record_result "$id" fail "the second message expires at $late_expiry, wanted $(( late_created + long ))"; return
  fi
  record_result "$id" pass
}

# --- 29: disband ---------------------------------------------------------------
# The terminal state, and the one with no way back: there is no un-disband
# commit, no later branch supersedes it, and a replacement conversation is a new
# MLS group with a new id. Two implementations disagreeing about whether a group
# ended is unrecoverable by construction, which is why it is worth a test even
# though it can only run one way.
#
# `group-lifecycle-v1.md` fixes the whole Commit shape — the lifecycle update,
# an admin-policy replacement naming only the committer, and a Remove for every
# other leaf — and MDK validates all of it before applying anything. A Commit
# carrying only the lifecycle update, which is what we used to send, is rejected
# as an unsupported lifecycle transition, so wn ACCEPTING this one is the
# assertion.
#
# What that acceptance looks like through `wn` is narrower than you would hope,
# and the narrowing is not our side being coy:
#
#   - the group does not disappear. The spec has a disbanded client keep a
#     read-only authenticated tombstone, and MDK keeps listing it.
#   - the app-level projection freezes. `groups members` and `groups admins`
#     keep reporting the last state in which wn held a leaf, because this Commit
#     removes that leaf — so neither moves, however the Commit is handled.
#   - `wn` does not print the state that would say it outright. MDK's
#     `AppGroupMlsState` carries `lifecycle_state` and `disbanding_enabled`, and
#     its uniffi surface hands both to the apps, but the CLI's
#     `group_mls_state_json` emits only group_id/epoch/member_count/
#     required_app_components.
#
# That leaves the MLS epoch, and it is enough: the disband is the only Commit
# published in the window, both sides are made to agree on the epoch before it,
# and a wn that refused it stays where it was.
#
# One direction only: MDK exposes disband on its uniffi surface (the apps call
# `disband_group`) but `wn groups` has no verb for it, so wn cannot originate
# one here.
test_29_disband_amy_to_wn() {
  banner "Test 29 — amy disbands a group; wn accepts the terminal commit"
  local id="29 disband amy->wn"

  # Its own group, and the LAST thing that happens to it: disband is absorbing,
  # so nothing else can be tested in a group afterwards.
  local out gid mls_gid
  out=$(amy_json marmot group create --name "Interop-Disband") || {
    record_result "$id" fail "amy group create failed"; return
  }
  gid=$(printf '%s' "$out" | jq -r '.group_id')
  mls_gid=$(printf '%s' "$out" | jq -r '.mls_group_id')
  if [[ -z "$gid" || "$gid" == "null" ]]; then
    record_result "$id" fail "amy reported no group id"; return
  fi

  amy_json marmot group add "$gid" "$B_NPUB" >/dev/null || {
    record_result "$id" fail "amy could not invite wn"; return
  }
  local b_gid
  b_gid=$(wait_for_invite B 60) || {
    record_result "$id" fail "wn never received the Welcome"; return
  }
  wn_b groups accept "$b_gid" >/dev/null 2>&1 || true

  # A message first, so wn is demonstrably live in the group before the end —
  # otherwise a quiet wn afterwards could just mean it never joined.
  local alive="before-the-end"
  amy_json marmot message send "$gid" "$alive" >/dev/null || {
    record_result "$id" fail "amy could not send into the group"; return
  }
  if ! wait_for_message B "$mls_gid" "$alive" 90; then
    record_result "$id" fail "wn never joined the group properly"; return
  fi

  # Agree on the epoch before committing anything terminal. This is both the
  # baseline the assertion below reads against and what a real client does — it
  # reads the group before acting on it — and it matters more here than
  # anywhere else: an ordinary commit off a stale epoch is survivable because
  # convergence settles it, and this one is not, since a disbanded client stops
  # processing group traffic by design and can never learn it lost a branch.
  local wn_epoch amy_epoch before_epoch=""
  local settle=$(( $(date +%s) + 120 ))
  while [[ $(date +%s) -lt $settle ]]; do
    wn_epoch=$(wn_b_json groups show "$mls_gid" 2>/dev/null | jq -r '.result.mls.epoch // empty')
    amy_epoch=$(amy_json marmot group show "$gid" 2>/dev/null | jq -r '.epoch // empty')
    if [[ -n "$wn_epoch" && "$wn_epoch" == "$amy_epoch" ]]; then before_epoch="$amy_epoch"; break; fi
    wn_b sync >/dev/null 2>&1 || true
    sleep 5
  done
  if [[ -z "$before_epoch" ]]; then
    record_result "$id" fail "amy (epoch ${amy_epoch:-?}) and wn (epoch ${wn_epoch:-?}) never agreed before the disband"
    return
  fi

  # Retry a disband the relay never acknowledged. That is not a protocol
  # failure — the commit stays a queued publish obligation and the group stays
  # live, exactly as `disbandGroup` reports — and a real client tries again. The
  # loopback relay drops a connection often enough under a full-suite run to be
  # worth spelling out rather than reading as a conformance failure.
  local disbanded=0 attempt
  for attempt in 1 2 3; do
    if amy_json marmot group disband "$gid" --yes >/dev/null; then disbanded=1; break; fi
    sleep 10
  done
  if [[ "$disbanded" -ne 1 ]]; then
    record_result "$id" fail "amy group disband failed"; return
  fi

  # Terminal here, and the tree is down to the committing leaf — the Remove
  # half of the shape, which the reference client cannot show us but our own
  # state can.
  local after after_epoch members
  after=$(amy_json marmot group show "$gid" 2>/dev/null || true)
  after_epoch=$(printf '%s' "$after" | jq -r '.epoch // empty')
  members=$(printf '%s' "$after" | jq -r '.members | length')
  if [[ "$(printf '%s' "$after" | jq -r '.disbanded // false')" != "true" ]]; then
    record_result "$id" fail "amy does not read its own group as disbanded"; return
  fi
  if [[ "$members" != "1" ]]; then
    record_result "$id" fail "amy kept $members leaves after the disband; the shape requires only the committer"; return
  fi
  if [[ -z "$after_epoch" || "$after_epoch" == "$before_epoch" ]]; then
    record_result "$id" fail "amy's epoch did not advance past $before_epoch"; return
  fi

  local deadline=$(( $(date +%s) + 150 )) accepted=0 saw=""
  while [[ $(date +%s) -lt $deadline ]]; do
    saw=$(wn_b_json groups show "$mls_gid" 2>/dev/null | jq -r '.result.mls.epoch // empty')
    if [[ -n "$saw" && "$saw" == "$after_epoch" ]]; then accepted=1; break; fi
    wn_b sync >/dev/null 2>&1 || true
    sleep 5
  done
  printf 'disband29 epoch %s -> %s, wn at %s\n' "$before_epoch" "$after_epoch" "${saw:-<none>}" >>"$LOG_FILE"

  if [[ "$accepted" -ne 1 ]]; then
    record_result "$id" fail "wn stayed at epoch ${saw:-<none>} instead of amy's $after_epoch — it rejected the disband commit"
    return
  fi
  record_result "$id" pass
}
