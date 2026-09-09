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
