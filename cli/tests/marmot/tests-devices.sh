# shellcheck shell=bash
#
# tests-devices.sh — tests 40-44: Marmot device groups against White Noise.
# One account K on several devices: `W` and `W2` are wnd daemons logged in
# with K's nsec, `D` is an amy account holding the same key. They link with
# the seven-emoji comparison (`wn devices link`, `amy marmot devices link`),
# compared here programmatically before either side confirms.
#
#   40  wn W is the existing device, amy D the new one: both show the same
#       seven emoji, they link, and `wn devices list` and amy's roster agree.
#   41  a chat K was in before the link (amy A invited K while only W existed)
#       reaches D through W's sibling Add, and messages cross both ways.
#   42  amy D is the existing device, a fresh wn W2 the new one.
#   44  amy D, the approver, adds W2 to that chat; W2 reads a message there.
#   43  amy D removes W2; W sees two devices again and W2 sees itself unlinked.
#   45  history: D, added to the chat by W, has the message A sent before the link.
#   46  history: W2, added by amy D, has a message from before W2 linked.
#   47  archive and mute cross between amy D and wn W, both ways; amy's pin reaches W's
#       small state (wn has no pin verb, so only the archive and mute are read back).
#   48  a read position from W reaches D.
#   49  renames: D renames W and itself; W's list shows both labels, D's client and platform.
#   50  "That was me": a sign-in D answered stays answered in a later run.
#   51  archiving unpins: W's archive unpins D's pinned chat, D refuses to pin an archived chat,
#       and D's own archive of a pinned chat unpins it.
#   52  moving: amy M2 and M3 (K again) found a second device group; D moves into it with an
#       ordinary confirmed link; W, left behind, cleans D's old entry up; D ends in M2's group.
#   53  a rename that loses a fork comes back: D and M3 rename two devices at once.
#   54  history through Blossom, across restarts, with an attachment key: a fourth amy device M4
#       links with M2 while Blossom is down, so the history M2 owes it stays owed across runs and
#       goes once Blossom is back; M4 keeps the deliveries across a run that cannot fetch them,
#       then imports the whole history and opens the attachment sent before it existed.
#
# Needs the headless runner's relay, A identity and helpers. Device groups are
# behind `amy marmot devices enable`, so A and every other test stay as they are.

W_DIR="$STATE_DIR/W"
W2_DIR="$STATE_DIR/W2"
W_SOCKET="${W_SOCKET:-$W_DIR/wnd.sock}"
W2_SOCKET="${W2_SOCKET:-$W2_DIR/wnd.sock}"
# Unix socket paths are capped at ~108 bytes: a deep checkout gets a short one under /tmp, as B and C do.
W_SOCKET="$(short_socket_path "$W_SOCKET")"
W2_SOCKET="$(short_socket_path "$W2_SOCKET")"
K_NPUB=""
K_HEX=""
# K's nsec, in this shell's memory only (never exported or written): W and W2 log in with it.
K_NSEC=""

wn_w() { "$WN_BIN" --socket "$W_SOCKET" "$@"; }
wn_w2() { "$WN_BIN" --socket "$W2_SOCKET" "$@"; }
amy_d() { HOME="$STATE_DIR" "$AMY_BIN" --account D --secret-backend plaintext --json "$@"; }

# The result object of a wn --json reply, with or without the {ok,result} envelope.
wn_result() { jq -c '.result // .'; }

devices_setup() {
  [[ -n "$K_HEX" ]] && return 0
  banner "Device groups — one account on W (wn), D (amy) and later W2 (wn)"
  local key nsec
  key=$("$AMY_BIN" --json key generate) || { fail_msg "amy key generate failed"; return 1; }
  nsec=$(printf '%s' "$key" | jq -r '.nsec')
  K_NSEC="$nsec"
  K_NPUB=$(printf '%s' "$key" | jq -r '.npub')
  K_HEX=$(printf '%s' "$key" | jq -r '.hex')
  info "K npub: $K_NPUB"

  mkdir -p "$W_DIR/logs" "$W2_DIR/logs"
  start_daemon W "$W_DIR" "$W_SOCKET"
  printf '%s\n' "$nsec" | wn_w login --nsec-stdin --relay "$RELAY_URL" >>"$LOG_FILE" 2>&1 || { fail_msg "wn login on W failed"; return 1; }
  local t
  for t in nip65 inbox key_package; do wn_w relays add --type "$t" "$RELAY_URL" >>"$LOG_FILE" 2>&1 || true; done
  wn_w keys publish >>"$LOG_FILE" 2>&1 || warn "wn keys publish on W failed"

  # D: the same key in amy, device groups on. No KeyPackage yet: D's first one is the
  # one it links with, so the chat of test 41 is made before D can be invited.
  amy_d init --nsec "$nsec" >>"$LOG_FILE" 2>&1 || { fail_msg "amy init D failed"; return 1; }
  nsec=""
  amy_d relay add "$RELAY_URL" >>"$LOG_FILE" 2>&1
  amy_d marmot devices enable >>"$LOG_FILE" 2>&1 || { fail_msg "amy marmot devices enable failed"; return 1; }
  sleep 2
}

# compare_codes <amy-code-file> <wn-code-json> — 0 when all seven emoji match in order.
compare_codes() {
  local amy wn
  amy=$(jq -c '[.code[].emoji]' "$1" 2>/dev/null)
  wn=$(printf '%s' "$2" | jq -c '[.[].emoji]' 2>/dev/null)
  info "amy code: $(jq -r '[.code[].emoji] | join(" ")' "$1" 2>/dev/null)  wn code: $(printf '%s' "$2" | jq -r '[.[].emoji] | join(" ")')"
  [[ -n "$amy" && "$amy" == "$wn" && "$(printf '%s' "$amy" | jq length)" == 7 ]]
}

# The chat of test 41: amy A invites K while W is K's only device, W accepts.
GROUP_41=""
MLS_41=""
devices_prelink_chat() {
  local out gid
  out=$(amy_json marmot group create --name "Devices-41") || return 1
  gid=$(printf '%s' "$out" | jq -r '.group_id')
  MLS_41=$(printf '%s' "$out" | jq -r '.mls_group_id')
  amy_json marmot group add "$gid" "$K_NPUB" >/dev/null || return 1
  local deadline=$(( $(date +%s) + 90 )) inv
  while [[ $(date +%s) -lt $deadline ]]; do
    inv=$(wn_w --json groups invites 2>/dev/null | jq_list invites | jq_group_id | head -n 1)
    [[ -n "$inv" ]] && break
    sleep 2
  done
  [[ -n "${inv:-}" ]] || return 1
  wn_w groups accept "$inv" >>"$LOG_FILE" 2>&1 || true
  GROUP_41="$gid"
  # A message from before D exists, for the history test (45): W must hold it to send it.
  amy_json marmot message send "$gid" "before-link-41" >/dev/null || return 0
  deadline=$(( $(date +%s) + 60 ))
  while [[ $(date +%s) -lt $deadline ]]; do
    wn_w --json messages list "$MLS_41" --limit 20 2>/dev/null | jq_list messages \
      | jq -e 'select((.plaintext // .content // "") | contains("before-link-41"))' >/dev/null 2>&1 && return 0
    sleep 2
  done
  warn "W never showed the pre-link message"
}

test_40_link_amy_new_to_wn_existing() {
  banner "Test 40 — link: wn W existing, amy D new, seven emoji compared"
  local id="40 device link wn existing -> amy new"
  devices_setup || { record_result "$id" fail "setup failed"; return; }
  LAST_PAIRING_AT=$(date +%s)
  devices_prelink_chat || warn "the pre-link chat for test 41 could not be made"

  local fifo="$STATE_DIR/d-confirm.fifo" codef="$STATE_DIR/d-code.json" out="$STATE_DIR/d-link.json" rcf="$STATE_DIR/d-link.rc"
  rm -f "$fifo" "$codef" "$out" "$rcf"; mkfifo "$fifo"
  local started=$(date +%s)
  ( amy_d marmot devices link --new --confirm stdin --code-file "$codef" --timeout 300 --settle 40 <"$fifo" >"$out" 2>>"$LOG_FILE"; echo $? >"$rcf" ) &
  exec 7>"$fifo"

  local deadline=$(( started + 240 )) prompt="" wncode=""
  while [[ $(date +%s) -lt $deadline ]]; do
    local status
    status=$(wn_w --json devices link status 2>>"$LOG_FILE" | wn_result)
    prompt=$(printf '%s' "$status" | jq -r '[.prompts[]? | select(.state == "comparing")][0].prompt_id // empty')
    wncode=$(printf '%s' "$status" | jq -c '[.prompts[]? | select(.state == "comparing")][0].code // empty')
    [[ -n "$prompt" && -s "$codef" ]] && break
    sleep 2
  done
  if [[ -z "$prompt" || ! -s "$codef" ]]; then
    echo no >&7; exec 7>&-
    record_result "$id" fail "no code to compare (wn prompt '${prompt}', amy code file $( [[ -s $codef ]] && echo present || echo missing ))"; return
  fi
  if ! compare_codes "$codef" "$wncode"; then
    echo no >&7; exec 7>&-
    record_result "$id" fail "the seven emoji differ"; return
  fi
  local codes_at=$(( $(date +%s) - started ))
  echo yes >&7
  wn_w devices link approve --label "amy" "$prompt" >>"$LOG_FILE" 2>&1 || warn "wn devices link approve returned nonzero"
  exec 7>&-
  while [[ ! -s "$rcf" && $(date +%s) -lt $(( started + 330 )) ]]; do sleep 2; done
  local rc; rc=$(cat "$rcf" 2>/dev/null)
  if [[ "$rc" != 0 ]]; then
    record_result "$id" fail "amy devices link exited ${rc:-<none>}: $(tail -c 300 "$out")"; return
  fi
  local linked_at=$(( $(date +%s) - started ))

  local d_slot wn_slots amy_slots
  d_slot=$(jq -r '.this_slot' "$out")
  wn_slots=$(wn_w --json devices list 2>>"$LOG_FILE" | wn_result | jq -c '[.devices[].slot_id_hex] | sort')
  amy_slots=$(amy_d marmot devices list 2>>"$LOG_FILE" | jq -c '[.devices[].slot] | sort')
  info "wn devices: $wn_slots  amy roster: $amy_slots"
  if [[ "$(printf '%s' "$wn_slots" | jq length)" != 2 || "$wn_slots" != "$amy_slots" ]] || ! printf '%s' "$wn_slots" | jq -e --arg s "$d_slot" 'index($s) != null' >/dev/null; then
    record_result "$id" fail "rosters disagree: wn $wn_slots, amy $amy_slots"; return
  fi
  record_result "$id" pass "codes matched at ${codes_at}s, linked at ${linked_at}s"
}

test_41_prelink_chat_reaches_amy() {
  banner "Test 41 — a chat from before the link reaches D; messages cross"
  local id="41 pre-link chat reaches the new device"
  [[ -n "$GROUP_41" ]] || { record_result "$id" skip "no pre-link chat"; return; }
  local started=$(date +%s) deadline=$(( $(date +%s) + 240 )) joined=0
  while [[ $(date +%s) -lt $deadline ]]; do
    amy_d marmot devices sync --timeout 4 >/dev/null 2>>"$LOG_FILE"
    if amy_d marmot group list 2>/dev/null | jq -e --arg g "$GROUP_41" --arg m "$MLS_41" \
         '[.. | strings] | index($g) != null or index($m) != null' >/dev/null; then
      joined=1; break
    fi
    sleep 3
  done
  if [[ $joined -ne 1 ]]; then
    devices_diag "$MLS_41"
    record_result "$id" fail "D never joined the chat W was in"; return
  fi
  local joined_at=$(( $(date +%s) - started ))

  amy_json marmot message send "$GROUP_41" "from-A-to-both-devices" >/dev/null || { record_result "$id" fail "A send failed"; return; }
  amy_d marmot await message "$MLS_41" --match "from-A-to-both-devices" --timeout 90 >/dev/null 2>>"$LOG_FILE" \
    || { record_result "$id" fail "D never read A's message"; return; }
  amy_d marmot message send "$MLS_41" "from-D-linked-device" >/dev/null 2>>"$LOG_FILE" || { record_result "$id" fail "D send failed"; return; }
  amy_json marmot await message "$GROUP_41" --match "from-D-linked-device" --timeout 90 >/dev/null \
    || { record_result "$id" fail "A never read D's message"; return; }
  local wdeadline=$(( $(date +%s) + 90 )) wsaw=0
  while [[ $(date +%s) -lt $wdeadline ]]; do
    if wn_w --json messages list "$MLS_41" --limit 20 2>/dev/null | jq_list messages \
         | jq -e 'select((.plaintext // .content // "") | contains("from-D-linked-device"))' >/dev/null 2>&1; then
      wsaw=1; break
    fi
    sleep 2
  done
  [[ $wsaw -eq 1 ]] || { record_result "$id" fail "W (the sibling) never showed D's message"; return; }
  record_result "$id" pass "D joined at ${joined_at}s"
}

test_42_link_wn_new_to_amy_existing() {
  banner "Test 42 — link: amy D existing, a fresh wn W2 new"
  local id="42 device link amy existing -> wn new"
  [[ -n "$K_HEX" ]] || { record_result "$id" skip "no device-group account"; return; }
  LAST_PAIRING_AT=$(date +%s)
  start_daemon W2 "$W2_DIR" "$W2_SOCKET"
  local nsec="$K_NSEC"
  if [[ "$nsec" != nsec1* ]]; then record_result "$id" fail "K's nsec is not in this run's memory"; return; fi
  printf '%s\n' "$nsec" | wn_w2 login --nsec-stdin --relay "$RELAY_URL" >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "wn login on W2 failed"; return; }
  nsec=""
  local t
  for t in nip65 inbox key_package; do wn_w2 relays add --type "$t" "$RELAY_URL" >>"$LOG_FILE" 2>&1 || true; done
  wn_w2 keys publish >>"$LOG_FILE" 2>&1 || warn "wn keys publish on W2 failed"

  local fifo="$STATE_DIR/d2-confirm.fifo" codef="$STATE_DIR/d2-code.json" out="$STATE_DIR/d2-link.json" rcf="$STATE_DIR/d2-link.rc"
  rm -f "$fifo" "$codef" "$out" "$rcf"; mkfifo "$fifo"
  local started=$(date +%s)
  ( amy_d marmot devices link --existing --label "White Noise 2" --confirm stdin --code-file "$codef" --timeout 300 --settle 30 <"$fifo" >"$out" 2>>"$LOG_FILE"; echo $? >"$rcf" ) &
  exec 7>"$fifo"
  sleep 3
  # W is an existing device too and would answer W2's request; the first answer wins. Freeze W
  # while W2 links, so the answer under test is amy's. Resumed, W reads the device group's Commit.
  local w_pid; w_pid=$(cat "$W_DIR/pid" 2>/dev/null)
  [[ -n "$w_pid" ]] && kill -STOP "$w_pid" 2>/dev/null
  wn_w2 devices link start >>"$LOG_FILE" 2>&1 || { [[ -n "$w_pid" ]] && kill -CONT "$w_pid"; echo no >&7; exec 7>&-; record_result "$id" fail "wn devices link start failed"; return; }

  local deadline=$(( started + 240 )) wncode=""
  while [[ $(date +%s) -lt $deadline ]]; do
    wncode=$(wn_w2 --json devices link status 2>>"$LOG_FILE" | wn_result | jq -c 'select(.new_device.state == "comparing") | .new_device.code // empty')
    [[ -n "$wncode" && -s "$codef" ]] && break
    sleep 2
  done
  if [[ -z "$wncode" || ! -s "$codef" ]]; then
    echo no >&7; exec 7>&-; [[ -n "$w_pid" ]] && kill -CONT "$w_pid" 2>/dev/null
    record_result "$id" fail "no code to compare (wn $( [[ -n $wncode ]] && echo comparing || echo not-comparing ), amy code file $( [[ -s $codef ]] && echo present || echo missing ))"; return
  fi
  if ! compare_codes "$codef" "$wncode"; then
    echo no >&7; exec 7>&-; wn_w2 devices link cancel >>"$LOG_FILE" 2>&1; [[ -n "$w_pid" ]] && kill -CONT "$w_pid" 2>/dev/null
    record_result "$id" fail "the seven emoji differ"; return
  fi
  local codes_at=$(( $(date +%s) - started ))
  wn_w2 devices link confirm-match >>"$LOG_FILE" 2>&1 || warn "wn confirm-match returned nonzero"
  echo yes >&7; exec 7>&-
  while [[ ! -s "$rcf" && $(date +%s) -lt $(( started + 330 )) ]]; do sleep 2; done
  [[ -n "$w_pid" ]] && kill -CONT "$w_pid" 2>/dev/null
  local rc; rc=$(cat "$rcf" 2>/dev/null)
  [[ "$rc" == 0 ]] || { record_result "$id" fail "amy devices link --existing exited ${rc:-<none>}: $(tail -c 300 "$out")"; return; }

  local wdeadline=$(( $(date +%s) + 120 )) w2n="" wn=""
  while [[ $(date +%s) -lt $wdeadline ]]; do
    w2n=$(wn_w2 --json devices list 2>/dev/null | wn_result | jq '.devices | length')
    wn=$(wn_w --json devices list 2>/dev/null | wn_result | jq '.devices | length')
    [[ "$w2n" == 3 && "$wn" == 3 ]] && break
    amy_d marmot devices sync --timeout 3 >/dev/null 2>>"$LOG_FILE"
    sleep 2
  done
  local dn; dn=$(amy_d marmot devices list 2>/dev/null | jq '.devices | length')
  [[ "$w2n" == 3 && "$wn" == 3 && "$dn" == 3 ]] || { record_result "$id" fail "device counts W2=$w2n W=$wn D=$dn (want 3)"; return; }
  record_result "$id" pass "codes matched at ${codes_at}s, all three list three devices at $(( $(date +%s) - started ))s"
}

test_44_amy_adds_wn_device_to_chats() {
  banner "Test 44 — amy D, the approver, adds W2 to the chat; W2 reads a message"
  local id="44 approver amy adds the new wn device to a chat"
  [[ -n "$GROUP_41" ]] || { record_result "$id" skip "no chat"; return; }
  local started=$(date +%s) deadline=$(( $(date +%s) + 180 )) in=0
  while [[ $(date +%s) -lt $deadline ]]; do
    amy_d marmot devices sync --timeout 4 >/dev/null 2>>"$LOG_FILE"
    if wn_w2 --json groups list 2>/dev/null | jq -e --arg m "$MLS_41" '[.. | strings] | index($m) != null' >/dev/null; then
      in=1; break
    fi
    sleep 3
  done
  [[ $in -eq 1 ]] || { record_result "$id" fail "W2 never got the chat D was in"; return; }
  local joined_at=$(( $(date +%s) - started ))
  amy_json marmot message send "$GROUP_41" "from-A-to-three-devices" >/dev/null || { record_result "$id" fail "A send failed"; return; }
  local wdeadline=$(( $(date +%s) + 90 )) saw=0
  while [[ $(date +%s) -lt $wdeadline ]]; do
    if wn_w2 --json messages list "$MLS_41" --limit 20 2>/dev/null | jq_list messages \
         | jq -e 'select((.plaintext // .content // "") | contains("from-A-to-three-devices"))' >/dev/null 2>&1; then
      saw=1; break
    fi
    sleep 2
  done
  [[ $saw -eq 1 ]] || { record_result "$id" fail "W2 never showed A's message"; return; }
  record_result "$id" pass "W2 in the chat at ${joined_at}s"
}

test_43_amy_removes_wn_device() {
  banner "Test 43 — amy D removes W2"
  local id="43 amy removes a wn device"
  [[ -n "$K_HEX" ]] || { record_result "$id" skip "no device-group account"; return; }
  local slot
  slot=$(wn_w2 --json devices list 2>/dev/null | wn_result | jq -r '.devices[]? | select(.is_this_device) | .slot_id_hex')
  [[ -n "$slot" ]] || { record_result "$id" skip "W2 is not linked"; return; }
  local started=$(date +%s)
  amy_d marmot devices remove "$slot" >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "amy devices remove failed"; return; }
  local deadline=$(( $(date +%s) + 120 )) wn="" w2=""
  while [[ $(date +%s) -lt $deadline ]]; do
    wn=$(wn_w --json devices list 2>/dev/null | wn_result | jq '.devices | length')
    w2=$(wn_w2 --json devices list 2>/dev/null | wn_result | jq '.devices | length')
    [[ "$wn" == 2 && "${w2:-0}" == 0 ]] && break
    sleep 2
  done
  local dn; dn=$(amy_d marmot devices list 2>/dev/null | jq '.devices | length')
  [[ "$wn" == 2 && "$dn" == 2 ]] || { record_result "$id" fail "after removal W=$wn D=$dn (want 2), W2 lists ${w2:-?}"; return; }
  [[ "${w2:-0}" == 0 ]] || { record_result "$id" fail "W2 still lists ${w2} devices: it did not see its removal"; return; }
  record_result "$id" pass "removed in $(( $(date +%s) - started ))s"
}

test_45_history_reaches_amy() {
  banner "Test 45 — history: D, added by W, gets the message from before the link"
  local id="45 history wn adder -> amy new device"
  [[ -n "$GROUP_41" ]] || { record_result "$id" skip "no pre-link chat"; return; }
  local started=$(date +%s)
  if amy_d marmot await message "$MLS_41" --match "before-link-41" --timeout 120 >/dev/null 2>>"$LOG_FILE"; then
    record_result "$id" pass "the pre-link message arrived at $(( $(date +%s) - started ))s"
  else
    devices_diag "$MLS_41"
    record_result "$id" fail "D never got the message A sent before it linked"
  fi
}

test_46_history_reaches_wn() {
  banner "Test 46 — history: W2, added by amy D, gets messages from before it linked"
  local id="46 history amy adder -> wn new device"
  [[ -n "$GROUP_41" ]] || { record_result "$id" skip "no chat"; return; }
  local deadline=$(( $(date +%s) + 120 )) saw=0
  while [[ $(date +%s) -lt $deadline ]]; do
    if wn_w2 --json messages list "$MLS_41" --limit 50 2>/dev/null | jq_list messages \
         | jq -e 'select((.plaintext // .content // "") | contains("from-D-linked-device"))' >/dev/null 2>&1; then
      saw=1; break
    fi
    amy_d marmot devices sync --timeout 3 >/dev/null 2>>"$LOG_FILE"
    sleep 2
  done
  [[ $saw -eq 1 ]] || { wn_w2 --json messages list "$MLS_41" --limit 50 >>"$LOG_FILE" 2>&1; record_result "$id" fail "W2 never showed D's message from before W2 linked"; return; }
  record_result "$id" pass "W2 holds the pre-link message"
}

# d_chat_field <field>: one field of D's row for the test chat in `marmot devices chats`.
d_chat_field() {
  amy_d marmot devices chats 2>/dev/null | jq -r --arg m "$MLS_41" --arg g "$GROUP_41" --arg f "$1" \
    '.chats[] | select(.group_id == $g or .group_id == $m) | .[$f]' 2>/dev/null | head -n 1
}

test_47_chat_settings_cross() {
  banner "Test 47 — archive and mute cross between amy D and wn W"
  local id="47 chat settings amy <-> wn"
  [[ -n "$GROUP_41" ]] || { record_result "$id" skip "no chat"; return; }
  amy_d marmot devices pin "$MLS_41" on >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "amy pin failed"; return; }
  amy_d marmot devices archive "$MLS_41" on >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "amy archive failed"; return; }
  local deadline=$(( $(date +%s) + 90 )) archived=0
  while [[ $(date +%s) -lt $deadline ]]; do
    if wn_w --json chats list-archived 2>/dev/null | jq -e --arg m "$MLS_41" '[.. | strings] | index($m) != null' >/dev/null; then archived=1; break; fi
    sleep 2
  done
  [[ $archived -eq 1 ]] || { wn_w --json chats list-archived >>"$LOG_FILE" 2>&1; record_result "$id" fail "W never showed the chat D archived"; return; }

  wn_w chats unarchive "$MLS_41" >>"$LOG_FILE" 2>&1 || warn "wn chats unarchive returned nonzero"
  wn_w chats mute "$MLS_41" forever >>"$LOG_FILE" 2>&1 || warn "wn chats mute returned nonzero"
  deadline=$(( $(date +%s) + 90 ))
  local a="" m=""
  while [[ $(date +%s) -lt $deadline ]]; do
    amy_d marmot devices sync --timeout 3 >/dev/null 2>>"$LOG_FILE"
    a=$(d_chat_field archived); m=$(d_chat_field muted)
    [[ "$a" == false && "$m" == true ]] && break
    sleep 2
  done
  [[ "$a" == false && "$m" == true ]] || { amy_d marmot devices chats >>"$LOG_FILE" 2>&1; record_result "$id" fail "D after W's unarchive and mute: archived=$a muted=$m"; return; }
  record_result "$id" pass "D's archive reached W; W's unarchive and mute reached D (pinned=$(d_chat_field pinned))"
}

test_48_read_position_reaches_amy() {
  banner "Test 48 — a read position from W reaches D"
  local id="48 read position wn -> amy"
  [[ -n "$GROUP_41" ]] || { record_result "$id" skip "no chat"; return; }
  local before; before=$(d_chat_field read_at)
  wn_w chats mark-read "$MLS_41" >>"$LOG_FILE" 2>&1 || warn "wn chats mark-read returned nonzero"
  local deadline=$(( $(date +%s) + 90 )) r=""
  while [[ $(date +%s) -lt $deadline ]]; do
    amy_d marmot devices sync --timeout 3 >/dev/null 2>>"$LOG_FILE"
    r=$(d_chat_field read_at)
    [[ -n "$r" && "$r" != null && "$r" != "$before" ]] && break
    sleep 2
  done
  [[ -n "$r" && "$r" != null && "$r" != "$before" ]] || { record_result "$id" fail "D holds no new read position (before=$before now=$r)"; return; }
  record_result "$id" pass "read position at $r"
}

test_49_renames_cross() {
  banner "Test 49 — D renames W and itself"
  local id="49 rename amy -> wn"
  [[ -n "$K_HEX" ]] || { record_result "$id" skip "no device-group account"; return; }
  local w_slot
  w_slot=$(wn_w --json devices list 2>/dev/null | wn_result | jq -r '.devices[]? | select(.is_this_device) | .slot_id_hex')
  [[ -n "$w_slot" ]] || { record_result "$id" skip "W is not linked"; return; }
  amy_d marmot devices rename "$w_slot" "Phone (renamed)" >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "amy rename of W failed"; return; }
  amy_d marmot devices rename self "amy laptop" >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "amy rename of itself failed"; return; }
  local deadline=$(( $(date +%s) + 90 )) list=""
  while [[ $(date +%s) -lt $deadline ]]; do
    list=$(wn_w --json devices list 2>/dev/null | wn_result)
    printf '%s' "$list" | jq -e '[.. | strings] | (index("Phone (renamed)") != null and index("amy laptop") != null)' >/dev/null 2>&1 && break
    sleep 2
  done
  printf '%s\n' "$list" >>"$LOG_FILE"
  printf '%s' "$list" | jq -e '[.. | strings] | (index("Phone (renamed)") != null and index("amy laptop") != null)' >/dev/null 2>&1 \
    || { record_result "$id" fail "W's list does not show both new labels"; return; }
  local mine; mine=$(amy_d marmot devices list 2>/dev/null | jq -c '.devices[] | select(.is_this_device) | [.label, .client, .platform]')
  [[ "$mine" == *'"amy laptop","amy"'* ]] || { record_result "$id" fail "D's own entry is $mine"; return; }
  record_result "$id" pass "W lists both labels; D is $mine"
}

test_50_that_was_me_persists() {
  banner "Test 50 — That was me stays answered in a later run"
  local id="50 That was me persists"
  [[ -n "$K_HEX" ]] || { record_result "$id" skip "no device-group account"; return; }
  local nsec="$K_NSEC"
  [[ "$nsec" == nsec1* ]] || { record_result "$id" skip "K's nsec is not in this run's memory"; return; }
  # E: K signed in once more, on an amy that never links: a new sign-in for D.
  amy_e() { HOME="$STATE_DIR" "$AMY_BIN" --account E --secret-backend plaintext --json "$@"; }
  amy_e init --nsec "$nsec" >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "amy init E failed"; return; }
  nsec=""
  amy_e relay add "$RELAY_URL" >>"$LOG_FILE" 2>&1
  amy_e marmot key-package publish >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "E could not publish a KeyPackage"; return; }
  local deadline=$(( $(date +%s) + 60 )) slot=""
  while [[ $(date +%s) -lt $deadline ]]; do
    slot=$(amy_d marmot devices sync --timeout 4 2>/dev/null | jq -r '[.sign_ins[]? | select(.kind == "new_sign_in")][0].slot // empty')
    [[ -n "$slot" ]] && break
    sleep 2
  done
  [[ -n "$slot" ]] || { record_result "$id" fail "D never listed E's sign-in"; return; }
  amy_d marmot devices sign-in "$slot" mine >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "amy sign-in mine failed"; return; }
  local again; again=$(amy_d marmot devices sync --timeout 4 2>/dev/null | jq -r --arg s "$slot" '.sign_ins[]? | select(.slot == $s) | .confirmed')
  [[ "$again" == true ]] || { record_result "$id" fail "a later run lists the sign-in as confirmed=$again"; return; }
  record_result "$id" pass "sign-in ${slot:0:12} stays answered"
}

test_51_archive_unpins() {
  banner "Test 51 — archiving a chat unpins it (here and from wn)"
  local id="51 archive unpins"
  [[ -n "$GROUP_41" ]] || { record_result "$id" skip "no chat"; return; }
  amy_d marmot devices archive "$MLS_41" off >>"$LOG_FILE" 2>&1
  amy_d marmot devices pin "$MLS_41" on >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "amy pin failed"; return; }
  [[ "$(d_chat_field pinned)" == true ]] || { record_result "$id" fail "the pin did not hold on D"; return; }

  # W archives: D, applying it, unpins.
  wn_w chats archive "$MLS_41" >>"$LOG_FILE" 2>&1 || warn "wn chats archive returned nonzero"
  local deadline=$(( $(date +%s) + 90 )) a="" p=""
  while [[ $(date +%s) -lt $deadline ]]; do
    amy_d marmot devices sync --timeout 3 >/dev/null 2>>"$LOG_FILE"
    a=$(d_chat_field archived); p=$(d_chat_field pinned)
    [[ "$a" == true && "$p" == false ]] && break
    sleep 2
  done
  [[ "$a" == true && "$p" == false ]] || { record_result "$id" fail "after W's archive D has archived=$a pinned=$p"; return; }

  # An archived chat is not pinned.
  if amy_d marmot devices pin "$MLS_41" on >"$STATE_DIR/pin51.json" 2>&1; then
    record_result "$id" fail "D pinned an archived chat: $(cat "$STATE_DIR/pin51.json")"; return
  fi
  grep -q archived "$STATE_DIR/pin51.json" || { record_result "$id" fail "pinning an archived chat failed for another reason: $(cat "$STATE_DIR/pin51.json")"; return; }

  # D's own archive of a pinned chat unpins it too.
  amy_d marmot devices archive "$MLS_41" off >>"$LOG_FILE" 2>&1
  amy_d marmot devices pin "$MLS_41" on >>"$LOG_FILE" 2>&1
  local out; out=$(amy_d marmot devices archive "$MLS_41" on 2>>"$LOG_FILE")
  [[ "$(printf '%s' "$out" | jq -r '.pinned')" == false && "$(d_chat_field pinned)" == false ]] || { record_result "$id" fail "D's archive left the chat pinned: $out"; return; }
  amy_d marmot devices archive "$MLS_41" off >>"$LOG_FILE" 2>&1
  wn_w chats unarchive "$MLS_41" >>"$LOG_FILE" 2>&1 || true
  record_result "$id" pass "W's archive unpinned D's chat; pinning it refused; D's archive unpins"
}

# amy_k <account> ARGS…: another amy device of K (M2, M3).
amy_k() { local acct="$1"; shift; HOME="$STATE_DIR" "$AMY_BIN" --account "$acct" --secret-backend plaintext --json "$@"; }
M_READY=0

# amy_link_pair <existing-acct> <new-acct> [move]: link two amy devices of K, codes compared after.
# The other devices of K that would answer the request are frozen (W) or not running (D).
amy_link_pair() {
  local existing="$1" fresh="$2" verb="${3:-link}"
  local ce="$STATE_DIR/code-$existing.json" cn="$STATE_DIR/code-$fresh.json"
  local oe="$STATE_DIR/link-$existing.json" on="$STATE_DIR/link-$fresh.json"
  rm -f "$ce" "$cn" "$oe" "$on"
  local new_args=(marmot devices link --new)
  [[ "$verb" == move ]] && new_args=(marmot devices move)
  ( AMY_TEST_AUTO_CONFIRM=1 amy_k "$existing" marmot devices link --existing --label "$fresh" --confirm yes --code-file "$ce" --timeout 240 --settle 25 >"$oe" 2>>"$LOG_FILE"; echo $? >"$oe.rc" ) &
  local pe=$!
  sleep 3
  ( amy_k "$fresh" "${new_args[@]}" --confirm yes --code-file "$cn" --timeout 240 --settle 40 >"$on" 2>>"$LOG_FILE"; echo $? >"$on.rc" ) &
  local pn=$!
  wait "$pe" "$pn"
  [[ "$(cat "$oe.rc")" == 0 && "$(cat "$on.rc")" == 0 ]] || { warn "link $existing <- $fresh: rc $(cat "$oe.rc")/$(cat "$on.rc"): $(tail -c 200 "$on")"; return 1; }
  [[ "$(jq -c '[.code[].emoji]' "$ce")" == "$(jq -c '[.code[].emoji]' "$cn")" ]] || { warn "the two amy devices showed different codes"; return 1; }
}

test_52_move_amy_device() {
  banner "Test 52 — D moves from W's device group to a second one (M2, M3)"
  local id="52 move between device groups"
  [[ -n "$K_HEX" ]] || { record_result "$id" skip "no device-group account"; return; }
  local nsec="$K_NSEC"
  [[ "$nsec" == nsec1* ]] || { record_result "$id" skip "K's nsec is not in this run's memory"; return; }
  local a
  for a in M2 M3; do
    amy_k "$a" init --nsec "$nsec" >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "amy init $a failed"; return; }
    amy_k "$a" relay add "$RELAY_URL" >>"$LOG_FILE" 2>&1
    amy_k "$a" marmot devices enable >>"$LOG_FILE" 2>&1
  done
  nsec=""
  local d_slot w_slot old_group
  d_slot=$(amy_d marmot devices list 2>/dev/null | jq -r '.this_slot')
  w_slot=$(wn_w --json devices list 2>/dev/null | wn_result | jq -r '.devices[]? | select(.is_this_device) | .slot_id_hex')
  # A link Request stays live for 600 s, and an existing device that sees two live ones answers
  # neither: let the Requests of the earlier links (D's, W2's) run out first.
  local quiet=$(( ${LAST_PAIRING_AT:-0} + 610 - $(date +%s) ))
  if (( quiet > 0 )); then info "waiting ${quiet}s for earlier link requests to expire"; sleep "$quiet"; fi
  local w_pid; w_pid=$(cat "$W_DIR/pid" 2>/dev/null)
  [[ -n "$w_pid" ]] && kill -STOP "$w_pid" 2>/dev/null
  local started=$(date +%s)
  # M2 founds a second device group of K by approving M3.
  if ! amy_link_pair M2 M3; then
    [[ -n "$w_pid" ]] && kill -CONT "$w_pid" 2>/dev/null
    record_result "$id" fail "M2 and M3 did not link"; return
  fi
  M_READY=1
  LAST_PAIRING_AT=$(( $(date +%s) + 610 ))
  # D, in W's group, links with M2 again: that moves it. A fresh amy run reads M3's Request
  # back as live for its 600 s, which would compete with D's: wait it out (W stays frozen).
  info "waiting 610s for M3's link request to expire"
  sleep 610
  if ! amy_link_pair M2 D move; then
    [[ -n "$w_pid" ]] && kill -CONT "$w_pid" 2>/dev/null
    record_result "$id" fail "D's move did not complete"; return
  fi
  [[ -n "$w_pid" ]] && kill -CONT "$w_pid" 2>/dev/null
  local moved_at=$(( $(date +%s) - started ))

  # W reads D's `moved` and removes D's old entry; D then drops its copy of the old group.
  local deadline=$(( $(date +%s) + 150 )) wn="" dl=""
  while [[ $(date +%s) -lt $deadline ]]; do
    amy_d marmot devices sync --timeout 3 >/dev/null 2>>"$LOG_FILE"
    wn=$(wn_w --json devices list 2>/dev/null | wn_result | jq -c '[.devices[].slot_id_hex] | sort')
    [[ "$wn" == "[\"$w_slot\"]" ]] && break
    sleep 3
  done
  dl=$(amy_d marmot devices list 2>/dev/null)
  printf 'test52 D: %s\nW: %s\n' "$dl" "$wn" >>"$LOG_FILE"
  local dn m2n
  dn=$(printf '%s' "$dl" | jq '[.devices[].slot] | length')
  m2n=$(amy_k M2 marmot devices list 2>/dev/null | jq '[.devices[].slot] | length')
  [[ "$(printf '%s' "$dl" | jq -r '.moving')" == false && "$dn" == 3 && "$m2n" == 3 ]] || { record_result "$id" fail "after the move D lists $dn devices (moving=$(printf '%s' "$dl" | jq -r '.moving')), M2 lists $m2n (want 3)"; return; }
  printf '%s' "$dl" | jq -e --arg w "$w_slot" '[.devices[].slot] | index($w) == null' >/dev/null || { record_result "$id" fail "D still lists W"; return; }
  [[ "$wn" == "[\"$w_slot\"]" ]] || { devices_diag; record_result "$id" fail "W still lists $wn after D moved (moved at ${moved_at}s)"; return; }
  record_result "$id" pass "D moved at ${moved_at}s; W cleaned D up; D, M2 and M3 list three devices"
}

test_53_rename_fork_recovers() {
  banner "Test 53 — two renames at once: the one that loses its fork is made again"
  local id="53 rename lost in a fork comes back"
  [[ $M_READY -eq 1 ]] || { record_result "$id" skip "no second device group"; return; }
  local m2_slot m3_slot
  m2_slot=$(amy_k M2 marmot devices list 2>/dev/null | jq -r '.this_slot')
  m3_slot=$(amy_k M3 marmot devices list 2>/dev/null | jq -r '.this_slot')
  amy_d marmot devices sync --timeout 3 >/dev/null 2>>"$LOG_FILE"
  amy_k M3 marmot devices sync --timeout 3 >/dev/null 2>>"$LOG_FILE"
  # Both from the same epoch: two Commits on one parent.
  amy_d marmot devices rename "$m2_slot" "Fork-A" >>"$LOG_FILE" 2>&1 &
  local p1=$!
  amy_k M3 marmot devices rename self "Fork-B" >>"$LOG_FILE" 2>&1 &
  local p2=$!
  wait "$p1" "$p2"
  local deadline=$(( $(date +%s) + 150 )) labels=""
  while [[ $(date +%s) -lt $deadline ]]; do
    amy_d marmot devices sync --timeout 3 >/dev/null 2>>"$LOG_FILE"
    amy_k M3 marmot devices sync --timeout 3 >/dev/null 2>>"$LOG_FILE"
    amy_k M2 marmot devices sync --timeout 3 >/dev/null 2>>"$LOG_FILE"
    labels=$(for a in D M2 M3; do amy_k "$a" marmot devices list 2>/dev/null | jq -c --arg a "$m2_slot" --arg b "$m3_slot" '[(.devices[] | select(.slot == $a) | .label), (.devices[] | select(.slot == $b) | .label)]'; done | sort -u)
    [[ "$labels" == '["Fork-A","Fork-B"]' ]] && break
    sleep 2
  done
  [[ "$labels" == '["Fork-A","Fork-B"]' ]] || { record_result "$id" fail "the devices settled on $(printf '%s' "$labels" | tr '\n' ' ')"; return; }
  # Both renames showing proves nothing unless they forked: one that applied after the other never
  # had to be made again. Only a logged recovery shows the path under test ran.
  local recovered; recovered=$(grep -c 'lost a fork; making it again' "$LOG_FILE" 2>/dev/null || true)
  [[ "${recovered:-0}" -ge 1 ]] || { record_result "$id" fail "both renames show, but no fork was recovered (the two Commits did not race)"; return; }
  record_result "$id" pass "all three show both renames (recoveries logged: $recovered)"
}

test_54_history_blossom_restart_attachment() {
  banner "Test 54 — a long history through Blossom, across restarts, with an attachment key"
  local id="54 history via Blossom across restarts"
  [[ $M_READY -eq 1 && -n "$GROUP_41" ]] || { record_result "$id" skip "no second device group or chat"; return; }
  [[ -n "${BLOSSOM_PID:-}" ]] || { record_result "$id" skip "no blossom blob store"; return; }
  local nsec="$K_NSEC"
  [[ "$nsec" == nsec1* ]] || { record_result "$id" skip "K's nsec is not in this run's memory"; return; }
  amy_k M4 init --nsec "$nsec" >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "amy init M4 failed"; return; }
  nsec=""
  amy_k M4 relay add "$RELAY_URL" >>"$LOG_FILE" 2>&1
  amy_k M4 marmot devices enable >>"$LOG_FILE" 2>&1
  # K's Blossom server list (kind 10063): where a sibling uploads history.
  amy_k M2 event --kind 10063 --tags "[[\"server\",\"$BLOSSOM_URL\"]]" --relay "$RELAY_URL" >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "could not publish K's Blossom list"; return; }
  # A long chat: more than eight inline bundles' worth, and an attachment, before M4 exists.
  local i big
  big=$(head -c 9000 /dev/zero | tr '\0' 'h')
  for i in $(seq 1 20); do amy_json marmot message send "$GROUP_41" "long-54-$i $big" >/dev/null 2>>"$LOG_FILE" || true; done
  local src="$STATE_DIR/attach-54.bin"; head -c 3000 /dev/urandom >"$src"
  local sent inner
  sent=$(amy_json marmot media send "$GROUP_41" "$src" --caption "pic-54" --server "$BLOSSOM_URL" --mime application/octet-stream) || { record_result "$id" fail "A could not send the attachment"; return; }
  inner=$(printf '%s' "$sent" | jq -r '.inner_event_id')
  amy_k M2 marmot await message "$MLS_41" --match "pic-54" --timeout 90 >/dev/null 2>>"$LOG_FILE" || { record_result "$id" fail "M2 never got A's messages"; return; }

  # Blossom down: M4 links with M2, M2 adds it to the chat but cannot upload the history.
  local quiet=$(( ${LAST_PAIRING_AT:-0} + 610 - $(date +%s) ))
  if (( quiet > 0 )); then info "waiting ${quiet}s for earlier link requests to expire"; sleep "$quiet"; fi
  stop_blossom
  local w_pid; w_pid=$(cat "$W_DIR/pid" 2>/dev/null)
  [[ -n "$w_pid" ]] && kill -STOP "$w_pid" 2>/dev/null
  amy_link_pair M2 M4
  local linked=$?
  [[ -n "$w_pid" ]] && kill -CONT "$w_pid" 2>/dev/null
  [[ $linked -eq 0 ]] || { record_result "$id" fail "M4 did not link"; start_blossom; return; }
  local deadline=$(( $(date +%s) + 180 )) owed="" in4=0
  while [[ $(date +%s) -lt $deadline ]]; do
    amy_k M4 marmot devices sync --timeout 3 >/dev/null 2>>"$LOG_FILE"
    owed=$(amy_k M2 marmot devices sync --timeout 3 2>>"$LOG_FILE" | jq -r '.owed_histories')
    amy_k M4 marmot group list 2>/dev/null | jq -e --arg g "$GROUP_41" '[.. | strings] | index($g) != null' >/dev/null && in4=1
    [[ $in4 -eq 1 && "${owed:-0}" -ge 1 ]] && break
    sleep 2
  done
  [[ $in4 -eq 1 ]] || { record_result "$id" fail "M2 never added M4 to the chat (owed=$owed)"; start_blossom; return; }
  [[ "${owed:-0}" -ge 1 ]] || { record_result "$id" fail "no history was owed while Blossom was down"; start_blossom; return; }
  owed=$(amy_k M2 marmot devices list 2>/dev/null | jq -r '.owed_histories')
  [[ "${owed:-0}" -ge 1 ]] || { record_result "$id" fail "the owed history did not survive a restart"; start_blossom; return; }

  # Blossom back: M2's next run uploads and sends it.
  start_blossom || { record_result "$id" fail "blossom did not come back"; return; }
  deadline=$(( $(date +%s) + 120 ))
  while [[ $(date +%s) -lt $deadline ]]; do
    owed=$(amy_k M2 marmot devices sync --timeout 3 2>>"$LOG_FILE" | jq -r '.owed_histories')
    [[ "$owed" == 0 ]] && break
    sleep 2
  done
  [[ "$owed" == 0 ]] || { record_result "$id" fail "M2 still owes $owed histories with Blossom up"; return; }
  local blobs; blobs=$(ls "$STATE_DIR/blossom/blobs" 2>/dev/null | wc -l)

  # Blossom down again: M4 reads its deliveries and keeps them across runs.
  stop_blossom
  local kept=""
  deadline=$(( $(date +%s) + 90 ))
  while [[ $(date +%s) -lt $deadline ]]; do
    kept=$(amy_k M4 marmot devices sync --timeout 3 2>>"$LOG_FILE" | jq -r '.kept_deliveries')
    [[ "${kept:-0}" -ge 1 ]] && break
    sleep 2
  done
  [[ "${kept:-0}" -ge 1 ]] || { record_result "$id" fail "M4 kept no delivery while Blossom was down"; start_blossom; return; }
  kept=$(amy_k M4 marmot devices list 2>/dev/null | jq -r '.kept_deliveries')
  [[ "${kept:-0}" -ge 1 ]] || { record_result "$id" fail "M4's kept delivery did not survive a restart"; start_blossom; return; }
  start_blossom || { record_result "$id" fail "blossom did not come back"; return; }
  deadline=$(( $(date +%s) + 120 ))
  while [[ $(date +%s) -lt $deadline ]]; do
    kept=$(amy_k M4 marmot devices sync --timeout 3 2>>"$LOG_FILE" | jq -r '.kept_deliveries')
    [[ "$kept" == 0 ]] && break
    sleep 2
  done
  amy_k M4 marmot await message "$MLS_41" --match "long-54-1 " --timeout 30 >/dev/null 2>>"$LOG_FILE" || { record_result "$id" fail "M4 did not import the oldest long message (kept=$kept)"; return; }
  amy_k M4 marmot await message "$MLS_41" --match "before-link-41" --timeout 10 >/dev/null 2>>"$LOG_FILE" || { record_result "$id" fail "M4 has no message from before D linked"; return; }
  local out="$STATE_DIR/attach-54.out"
  amy_k M4 marmot media get "$GROUP_41" "$inner" --out "$out" >>"$LOG_FILE" 2>&1 || { record_result "$id" fail "M4 could not open the attachment sent before it existed"; return; }
  cmp -s "$src" "$out" || { record_result "$id" fail "M4 decrypted other bytes"; return; }
  record_result "$id" pass "history owed across M2's runs, $blobs blob(s) on Blossom, kept across M4's runs, attachment opened with the imported key"
}

# What W and D hold, into the log, when a device test fails.
devices_diag() {
  {
    printf -- '--- devices diagnostics ---\n'
    wn_w --json devices list 2>&1
    wn_w --json devices link status 2>&1
    [[ -n "${1:-}" ]] && wn_w --json groups members "$1" 2>&1
    [[ -n "${1:-}" ]] && wn_w --json groups show "$1" 2>&1
    amy_d marmot devices list 2>&1
    amy_d marmot group list 2>&1
  } >>"$LOG_FILE"
}

# Run from the harness's EXIT/INT/TERM/HUP trap too. Tests 52 and 54 freeze W (kill -STOP) for
# minutes, and a stopped process acts on no TERM until it is resumed: resume it first, then TERM,
# then KILL whatever is still there after 5 s.
stop_device_daemons() {
  local d pid
  for d in "$W_DIR" "$W2_DIR"; do
    [[ -f "$d/pid" ]] || continue
    pid=$(cat "$d/pid" 2>/dev/null)
    [[ -n "$pid" ]] || continue
    kill -CONT "$pid" 2>/dev/null || continue
    kill "$pid" 2>/dev/null
    local _
    for _ in $(seq 1 20); do kill -0 "$pid" 2>/dev/null || break; sleep 0.25; done
    kill -0 "$pid" 2>/dev/null && kill -9 "$pid" 2>/dev/null
  done
  return 0
}
