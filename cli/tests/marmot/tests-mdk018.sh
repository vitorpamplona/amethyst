# shellcheck shell=bash
#
# tests-mdk018.sh — test 38.
# Focus: what MDK 0.18-0.19 made interop-visible. A NIP-22 thread message
# (kind 1111) is chat: White Noise Linux sends one through the custom-event
# path, and MDK's reply contract
# (docs/marmot-architecture/overview/thread-messages.md, mdk#3581) names the
# thread root with E/K/P and the immediate parent with the first lowercase e/k.
#
# Reuses the media group from tests-media.sh (A admin, B member).

test_38_thread_message_wn_to_amy() {
  banner "Test 38 — wn answers in a thread (kind 1111); amy shows it in the group"
  local id="38 thread message wn->amy"

  local gid mls_gid
  read -r gid mls_gid < <(media_group) || { record_result "$id" fail "could not build the media group"; return; }
  if [[ -z "${gid:-}" ]]; then record_result "$id" fail "could not build the media group"; return; fi

  # The root is an ordinary kind 9 from wn; the thread message answers it.
  local root_text="thread-root-from-whitenoise"
  if ! wn_b messages send "$mls_gid" "$root_text" >/dev/null 2>&1; then
    record_result "$id" fail "wn send failed"; return
  fi
  if ! amy_json marmot await message "$gid" --match "$root_text" --timeout 90 >/dev/null; then
    record_result "$id" fail "amy never received the thread root"; return
  fi

  local row root root_author
  row=$(amy_json marmot message list "$gid" --limit 50 2>/dev/null \
          | jq_list messages \
          | jq -c --arg c "$root_text" 'select((.content // "") == $c)' | head -n 1)
  root=$(printf '%s' "$row" | jq -r '.event_id // empty')
  root_author=$(printf '%s' "$row" | jq -r '.author // empty')
  if [[ -z "$root" || -z "$root_author" ]]; then
    record_result "$id" fail "amy has no event id for the thread root"; return
  fi

  # The tags MDK's send_thread_message builds from the parent's row: the root with
  # E/K/P, the parent with e/k. The parent here IS the root. No parent-author p:
  # on Marmot a p tag is a mention.
  local thread_text="thread-answer-from-whitenoise" sent
  # The tags go before the positionals: CONTENT is variadic, so anything after it is text.
  sent=$(wn_b --json messages send-event \
           --tag "[\"E\",\"$root\",\"\",\"$root_author\"]" \
           --tag '["K","9"]' \
           --tag "[\"P\",\"$root_author\"]" \
           --tag "[\"e\",\"$root\",\"\",\"$root_author\"]" \
           --tag '["k","9"]' \
           "$mls_gid" 1111 "$thread_text" 2>>"$LOG_FILE") || {
    record_result "$id" fail "wn messages send-event 1111 failed"; return
  }
  printf 'wn send-event 1111 -> %s\n' "$sent" >>"$LOG_FILE"

  if ! amy_json marmot await message "$gid" --match "$thread_text" --timeout 90 >/dev/null; then
    record_result "$id" fail "amy never received the thread message"; return
  fi

  local kind
  kind=$(amy_json marmot message list "$gid" --limit 50 2>/dev/null \
           | jq_list messages \
           | jq -r --arg c "$thread_text" 'select((.content // "") == $c) | .kind' | head -n 1)
  if [[ "$kind" != "1111" ]]; then
    record_result "$id" fail "amy lists the thread message as kind '${kind}', not 1111"; return
  fi
  record_result "$id" pass
}
