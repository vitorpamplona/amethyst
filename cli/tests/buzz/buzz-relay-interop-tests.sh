# shellcheck shell=bash
#
# buzz-relay-interop-tests.sh — the scenarios of buzz-relay-interop.sh. Sourced
# by it (needs amy_as, j, pass/fail/gap, HEX, RELAY, HTTP, psql_q, …).
#
# Where amy has no verb for something the app sends (thread replies, edits,
# reactions, deletes, the Buzz channel create), the event is built with
# `amy event` in exactly the shape the app's builder makes (the quartz builder
# named in each case), so the relay side is still checked, and the missing verb
# is recorded as a GAP row.

# Fetch helper: kind K events with tag filter T (e.g. d=UUID) as identity WHO,
# newline-separated JSON events in OUT.
fetch_as() {
  local who="$1" kind="$2" tag="$3"; shift 3
  amy_as "$who" fetch --kind "$kind" ${tag:+--tag "$tag"} --relay "$RELAY" --timeout 6 "$@"
}
# A tag's value from the first event in a fetch result: tagval NAME.
first_event() { printf '%s' "$OUT" | jq -c 'if type=="array" then .[0] elif .events then .events[0] else . end' 2>/dev/null; }

# ---------------------------------------------------------------------------
banner "1. AUTH on a cold socket"
# ---------------------------------------------------------------------------
# What an unauthenticated socket gets. amy's retry (Context.publish / drain with
# pendingOnAuthRequired) keys on the `auth-required:` prefix of these answers.
PROBE_EVENT=$(HOME="$STATE_DIR/home-B" "$AMY_BIN" --account B --secret-backend plaintext --json \
  event --kind 9 --content probe --tags '[["h","00000000-0000-4000-8000-000000000000"]]' 2>/dev/null | jq -c .event)
python3 "$SCRIPT_DIR/ws-probe.py" "$RELAY" \
  '["REQ","probe",{"kinds":[39000],"limit":1}]' \
  "[\"EVENT\",$PROBE_EVENT]" >"$LOG_DIR/ws-probe.out" 2>&1
cat "$LOG_DIR/ws-probe.out" >>"$LOG_FILE"
if grep -q '^\["AUTH"' "$LOG_DIR/ws-probe.out"; then
  pass auth-challenge "relay sends a NIP-42 challenge on connect"
else
  fail auth-challenge "no AUTH challenge: $(head -c 200 "$LOG_DIR/ws-probe.out")"
fi
REQ_ANS=$(grep -E '^\["(CLOSED|EOSE)","probe"' "$LOG_DIR/ws-probe.out" | head -n1)
OK_ANS=$(grep -E '^\["OK"' "$LOG_DIR/ws-probe.out" | head -n1)
info "unauthenticated REQ → $REQ_ANS"
info "unauthenticated EVENT → $OK_ANS"
if [[ "$OK_ANS" == *'false'*'auth-required'* ]]; then
  pass auth-event-refused "unauthenticated EVENT → OK false auth-required"
else
  fail auth-event-refused "unauthenticated EVENT answered: ${OK_ANS:-nothing}"
fi
if [[ "$REQ_ANS" == *CLOSED*auth-required* ]]; then
  pass auth-req-closed "unauthenticated REQ → CLOSED auth-required"
elif [[ -n "$REQ_ANS" ]]; then
  pass auth-req-closed "unauthenticated REQ answered $REQ_ANS (relay serves it without AUTH)"
else
  fail auth-req-closed "unauthenticated REQ got no CLOSED/EOSE"
fi

# ---------------------------------------------------------------------------
banner "2. Channels: create (stream / forum / announce)"
# ---------------------------------------------------------------------------
# 2a. The verb: `amy relaygroup create`. It sends a bare 9007 (no `name`), which
#     Buzz refuses before storage; the app's createRelayGroup puts the metadata
#     on the 9007 (CreateGroupEvent.build(name=, about=, visibility=, channelType=)).
amy_as A relaygroup create "$RELAY" --name "amy-verb-$RANDOM" --about "made by the verb"
VERB_GID=$(j '.group_id // empty'); VERB_RC=$RC
if [[ $RC -eq 0 && "$(j .published)" == "true" ]]; then
  pass create-verb "relaygroup create published on Buzz (group $VERB_GID)"
else
  # The relay's words for the 9007 the verb sends (bare, 16-hex id), and for the same with a UUID.
  amy_as A event --kind 9007 --relay "$RELAY" --tags "[[\"h\",\"$VERB_GID\"]]"
  WHY_HEX=$(last_reason A)
  amy_as A event --kind 9007 --relay "$RELAY" --tags "[[\"h\",\"$(new_uuid)\"]]"
  WHY_UUID=$(last_reason A)
  fail create-verb "relaygroup create exits $VERB_RC with published=false and no reason; Buzz refuses its bare 9007 ('$WHY_HEX'; with a UUID id: '$WHY_UUID')"
fi

# 2b. The app's shape, via amy event: UUID id + name/about/visibility/channel_type on the 9007.
create_channel() { # who name type visibility [extra-tags-json-fragment]
  local who="$1" name="$2" type="$3" vis="$4" extra="${5:-}"
  local id; id=$(new_uuid)
  amy_as "$who" event --kind 9007 --relay "$RELAY" \
    --tags "[[\"h\",\"$id\"],[\"name\",\"$name\"],[\"about\",\"$name about\"],[\"visibility\",\"$vis\"],[\"channel_type\",\"$type\"]$extra]"
  CREATED_ID="$id"
}
STREAM_NAME="stream-$RANDOM"
create_channel A "$STREAM_NAME" stream open
STREAM=$CREATED_ID
[[ $RC -eq 0 ]] && pass create-stream "9007 stream accepted ($STREAM)" || fail create-stream "rc=$RC $(last_err A)"
create_channel A "forum-$RANDOM" forum open
FORUM=$CREATED_ID
[[ $RC -eq 0 ]] && pass create-forum "9007 forum accepted ($FORUM)" || fail create-forum "rc=$RC $(last_err A)"
create_channel A "announce-$RANDOM" stream open
ANNOUNCE=$CREATED_ID
[[ $RC -eq 0 ]] || fail create-announce "rc=$RC $(last_err A)"
# Announce = open + join_role guest (owner/admin 9002).
amy_as A event --kind 9002 --relay "$RELAY" --tags "[[\"h\",\"$ANNOUNCE\"],[\"join_role\",\"guest\"]]"
[[ $RC -eq 0 ]] && pass create-announce "join_role guest set on $ANNOUNCE" || fail create-announce "9002 join_role: rc=$RC $(last_err A)"
create_channel A "dupe-check" stream open
create_channel A "DUPE-CHECK" stream open
if [[ $RC -ne 0 ]] && last_reason A | grep -q 'already exists'; then
  pass create-dupe-name "second listed channel with the same name (case-insensitive) refused"
else
  fail create-dupe-name "duplicate listed name not refused (rc=$RC)"
fi

# 2c. Reads: relaygroup info / browse see the relay-signed 39000-39002.
amy_as B relaygroup info "$RELAY" "$STREAM"
if [[ "$(j .name)" == "$STREAM_NAME" ]]; then
  pass info-cross-user "B reads A's channel name via relaygroup info"
else
  fail info-cross-user "relaygroup info as B: name=$(j .name) rc=$RC"
fi
amy_as B relaygroup browse "$RELAY" --timeout 6
if [[ "$(j "[.groups[]?.group_id] | index(\"$STREAM\") != null")" == "true" ]]; then
  pass browse "browse lists the new channel ($(j .count) groups, $(j .unverified_dropped) unverified dropped)"
else
  fail browse "browse missing $STREAM: count=$(j .count) dropped=$(j .unverified_dropped)"
fi

# ---------------------------------------------------------------------------
banner "3. Metadata edit / rename / labels"
# ---------------------------------------------------------------------------
NEW_NAME="renamed-$RANDOM"
amy_as A relaygroup edit "$RELAY" "$STREAM" --name "$NEW_NAME" --about "new about"
[[ $RC -eq 0 ]] || fail edit-rename "relaygroup edit rc=$RC $(last_err A)"
amy_as B relaygroup info "$RELAY" "$STREAM"
if [[ "$(j .name)" == "$NEW_NAME" && "$(j .about)" == "new about" ]]; then
  pass edit-rename "relaygroup edit renamed the channel; B sees it"
else
  fail edit-rename "after edit B sees name=$(j .name) about=$(j .about)"
fi
# Labels: Buzz `t` tags on a 9002 are the channel's labels (the app's buzzLabels).
amy_as A event --kind 9002 --relay "$RELAY" --tags "[[\"h\",\"$STREAM\"],[\"t\",\"alpha\"],[\"t\",\"beta\"]]"
[[ $RC -eq 0 ]] || fail labels-set "9002 t labels rc=$RC $(last_err A)"
labels_of() { fetch_as "$1" 39000 "d=$2"; printf '%s' "$OUT" | jq -r '..|objects|select(.kind==39000)|.tags[]|select(.[0]=="t")|.[1]' 2>/dev/null | sort | tr '\n' ' '; }
L1=$(labels_of B "$STREAM")
[[ "$L1" == *alpha*beta* ]] && pass labels-set "39000 t tags: $L1" || fail labels-set "labels after set: '$L1'"
# A plain edit (about only) must keep the labels: relaygroup edit is Buzz-aware.
amy_as A relaygroup edit "$RELAY" "$STREAM" --about "about again"
L2=$(labels_of B "$STREAM")
if [[ "$L2" == "$L1" ]]; then
  pass labels-kept-by-edit "relaygroup edit --about kept labels ($L2)"
else
  fail labels-kept-by-edit "labels before '$L1' after '$L2' (rc=$RC $(last_err A))"
fi

# ---------------------------------------------------------------------------
banner "4. Join, join role, guests"
# ---------------------------------------------------------------------------
amy_as B relaygroup join "$RELAY" "$STREAM"
[[ $RC -eq 0 && "$(j .published)" == "true" ]] && pass join-stream "B joined $STREAM" || fail join-stream "rc=$RC $(last_err B)"
role_of() { # who-reads gid pubkey → role from 39002 p tag (or 39001)
  fetch_as "$1" 39002 "d=$2"
  # Buzz writes the role as the 4th value (`["p", pk, "", role]`), as quartz's buzzGuests reads it.
  printf '%s' "$OUT" | jq -r --arg p "$3" '..|objects|select(.kind==39002)|.tags[]|select(.[0]=="p" and .[1]==$p)|(.[3] // .[2] // "member")' 2>/dev/null | head -n1
}
R=$(role_of A "$STREAM" "${HEX[B]}")
[[ "$R" == "member" ]] && pass join-role-member "B is a member of the stream (39002 role '$R')" || fail join-role-member "B's 39002 role in stream: '$R'"

amy_as B relaygroup join "$RELAY" "$ANNOUNCE"
[[ $RC -eq 0 ]] || fail join-announce "rc=$RC $(last_err B)"
R=$(role_of A "$ANNOUNCE" "${HEX[B]}")
[[ "$R" == "guest" ]] && pass join-role-guest "self-join of an announce channel gives guest ('$R')" || fail join-role-guest "B's role in announce: '$R'"
fetch_as B 39000 "d=$ANNOUNCE"
JR=$(printf '%s' "$OUT" | jq -r '..|objects|select(.kind==39000)|.tags[]|select(.[0]=="join_role")|.[1]' 2>/dev/null | head -n1)
RESTR=$(printf '%s' "$OUT" | jq -r '..|objects|select(.kind==39000)|[.tags[]|.[0]]|index("restricted") != null' 2>/dev/null | head -n1)
[[ "$JR" == "guest" && "$RESTR" == "true" ]] && pass announce-39000 "39000 carries join_role=guest + restricted" || fail announce-39000 "join_role='$JR' restricted='$RESTR'"

amy_as B buzz post "$RELAY" "$ANNOUNCE" "guest trying to post"
if [[ $RC -ne 0 ]] && last_reason B | grep -q 'only members can post'; then
  pass guest-cannot-post "guest post refused: restricted: only members can post"
else
  fail guest-cannot-post "guest post rc=$RC $(last_err B)"
fi
amy_as A buzz post "$RELAY" "$ANNOUNCE" "announcement from the owner"
[[ $RC -eq 0 ]] && pass announce-owner-post "owner posts in announce channel" || fail announce-owner-post "rc=$RC $(last_err A)"
# Guest → member by a member/owner (kind 9000 role member).
amy_as A relaygroup put-user "$RELAY" "$ANNOUNCE" "${HEX[B]}" --buzz-role member
R=$(role_of A "$ANNOUNCE" "${HEX[B]}")
[[ $RC -eq 0 && "$R" == "member" ]] && pass guest-promoted "put-user --buzz-role member made B a member" || fail guest-promoted "rc=$RC role='$R'"
amy_as B buzz post "$RELAY" "$ANNOUNCE" "now I can post"
[[ $RC -eq 0 ]] && pass promoted-can-post "B posts after promotion" || fail promoted-can-post "rc=$RC $(last_err B)"

# ---------------------------------------------------------------------------
banner "5. Messages: post, thread reply, edit, react, delete"
# ---------------------------------------------------------------------------
amy_as A relaygroup message "$RELAY" "$STREAM" "plain nip29 kind 9 from A"
M_PLAIN=$(j '.event_id // empty')
[[ $RC -eq 0 ]] && pass post-relaygroup-message "relaygroup message accepted" || fail post-relaygroup-message "rc=$RC $(last_err A)"
amy_as A buzz post "$RELAY" "$STREAM" "buzz-shaped root from A"
ROOT=$(j '.event_id // empty')
[[ $RC -eq 0 ]] && pass post-buzz "buzz post accepted" || fail post-buzz "rc=$RC $(last_err A)"
amy_as B buzz post "$RELAY" "$STREAM" "hello from B"
M_B=$(j '.event_id // empty')
[[ $RC -eq 0 ]] && pass post-member "member B posts" || fail post-member "rc=$RC $(last_err B)"
amy_as B buzz read "$RELAY" "$STREAM" --limit 20
SEEN=$(j "[.messages[]?.id] | (index(\"$ROOT\") != null) and (index(\"$M_PLAIN\") != null)")
[[ "$SEEN" == "true" ]] && pass read-cross-user "B's buzz read sees A's two posts ($(j .count) messages)" || fail read-cross-user "B's read: count=$(j .count)"
amy_as C buzz read "$RELAY" "$STREAM" --limit 20
[[ $RC -eq 0 && "$(j "[.messages[]?.id] | index(\"$ROOT\") != null")" == "true" ]] \
  && pass read-non-member "non-member C reads an open channel" || fail read-non-member "C's read rc=$RC count=$(j .count)"

# Thread reply: BuzzChatMessage.build(threadRoot=, replyTo=) → ["e",root,"","reply"]. No amy verb.
gap reply-verb "amy has no group thread-reply verb (app: BuzzChatMessage.build(replyTo=)); sent via amy event"
amy_as B event --kind 9 --content "threaded reply from B" --relay "$RELAY" \
  --tags "[[\"h\",\"$STREAM\"],[\"e\",\"$ROOT\",\"\",\"reply\"],[\"p\",\"${HEX[A]}\"]]"
REPLY=$(j '.event.id // empty')
[[ $RC -eq 0 ]] && pass reply-accepted "thread reply accepted" || fail reply-accepted "rc=$RC $(last_err B)"
amy_as B event --kind 9 --content "reply to nothing" --relay "$RELAY" \
  --tags "[[\"h\",\"$STREAM\"],[\"e\",\"$(printf '%064d' 7)\",\"\",\"reply\"]]"
[[ $RC -ne 0 ]] && pass reply-unknown-parent "reply to an unknown parent refused: $(last_reason B)" \
  || fail reply-unknown-parent "reply to unknown parent was accepted"
amy_as A fetch --kind 9 --tag "e=$ROOT" --relay "$RELAY" --timeout 6
[[ "$(printf '%s' "$OUT" | jq -r "[..|objects|select(.id==\"$REPLY\")] | length" 2>/dev/null)" -ge 1 ]] \
  && pass reply-read "A fetches B's reply by #e" || fail reply-read "reply not readable by #e"

# Edit: StreamMessageEditEvent.build(channelId, target, text) → kind 40003 [h][e]. No amy verb.
gap edit-verb "amy has no message-edit verb (app: StreamMessageEditEvent, kind 40003); sent via amy event"
amy_as B event --kind 40003 --content "hello from B (edited)" --relay "$RELAY" --tags "[[\"h\",\"$STREAM\"],[\"e\",\"$M_B\"]]"
[[ $RC -eq 0 ]] && pass edit-own "B edits own message (40003)" || fail edit-own "rc=$RC $(last_err B)"
amy_as C relaygroup join "$RELAY" "$STREAM"
amy_as C event --kind 40003 --content "C hijacks" --relay "$RELAY" --tags "[[\"h\",\"$STREAM\"],[\"e\",\"$M_B\"]]"
[[ $RC -ne 0 ]] && pass edit-other-refused "C may not edit B's message: $(last_reason C)" \
  || fail edit-other-refused "C's edit of B's message accepted"

# Reaction: NIP-25 kind 7 with e/p (+h). No group react verb in amy.
gap react-verb "amy has no group reaction verb; sent via amy event (NIP-25 + h)"
amy_as C event --kind 7 --content "+" --relay "$RELAY" --tags "[[\"e\",\"$ROOT\"],[\"p\",\"${HEX[A]}\"],[\"k\",\"9\"],[\"h\",\"$STREAM\"]]"
REACT=$(j '.event.id // empty')
[[ $RC -eq 0 ]] && pass react "member C reacts" || fail react "rc=$RC $(last_err C)"
amy_as A fetch --kind 7 --tag "e=$ROOT" --relay "$RELAY" --timeout 6
[[ "$(printf '%s' "$OUT" | jq -r "[..|objects|select(.id==\"$REACT\")] | length" 2>/dev/null)" -ge 1 ]] \
  && pass react-read "A sees C's reaction" || fail react-read "reaction not readable"

# Delete: `amy delete` (NIP-09 kind 5) on own message; admin delete 9005 by the owner.
amy_as B delete "$M_B" --relay "$RELAY"
[[ $RC -eq 0 ]] && pass delete-own-nip09 "amy delete (kind 5) of own message accepted" || fail delete-own-nip09 "rc=$RC $(last_err B)"
amy_as C buzz read "$RELAY" "$STREAM" --limit 50
[[ "$(j "[.messages[]?.id] | index(\"$M_B\") == null")" == "true" ]] && pass delete-own-gone "deleted message gone from C's read" \
  || fail delete-own-gone "B's deleted message still served"
gap admin-delete-verb "amy has no NIP-29 9005 delete-event verb; sent via amy event"
amy_as A event --kind 9005 --relay "$RELAY" --tags "[[\"h\",\"$STREAM\"],[\"e\",\"$REPLY\"]]"
[[ $RC -eq 0 ]] && pass admin-delete "owner 9005 deletes B's reply" || fail admin-delete "rc=$RC $(last_err A)"
amy_as C event --kind 9005 --relay "$RELAY" --tags "[[\"h\",\"$STREAM\"],[\"e\",\"$ROOT\"]]"
[[ $RC -ne 0 ]] && pass admin-delete-refused "member C's 9005 of A's message refused" || fail admin-delete-refused "C deleted A's message"

# ---------------------------------------------------------------------------
banner "6. Roles: put-user, remove-user, ban"
# ---------------------------------------------------------------------------
amy_as A relaygroup put-user "$RELAY" "$STREAM" "${HEX[B]}" --buzz-role admin
fetch_as C 39001 "d=$STREAM"
ADM=$(printf '%s' "$OUT" | jq -r --arg p "${HEX[B]}" '..|objects|select(.kind==39001)|.tags[]|select(.[0]=="p" and .[1]==$p)|.[2]' 2>/dev/null | head -n1)
[[ $RC -eq 0 && "$ADM" == "admin" ]] && pass put-user-admin "B promoted to admin (39001 '$ADM')" || fail put-user-admin "39001 role for B: '$ADM'"
amy_as A relaygroup put-user "$RELAY" "$STREAM" "${HEX[B]}" --role moderator
if [[ $RC -ne 0 ]]; then
  pass put-user-nip29-role "NIP-29 p-tag role 'moderator' refused by Buzz: $(last_reason A)"
else
  R=$(role_of C "$STREAM" "${HEX[B]}")
  pass put-user-nip29-role "NIP-29 --role moderator accepted; B's role now '$R'"
fi
amy_as B relaygroup put-user "$RELAY" "$STREAM" "${HEX[C]}" --buzz-role owner
[[ $RC -ne 0 ]] && pass admin-cannot-grant-owner "admin B may not grant owner" || fail admin-cannot-grant-owner "admin granted owner"
amy_as A relaygroup remove-user "$RELAY" "$STREAM" "${HEX[C]}"
[[ $RC -eq 0 ]] && pass remove-user "A removed C" || fail remove-user "rc=$RC $(last_err A)"
R=$(role_of B "$STREAM" "${HEX[C]}")
[[ -z "$R" ]] && pass remove-user-roster "C gone from 39002" || fail remove-user-roster "C still in 39002 as '$R'"
gap ban-verb "amy and the app send no Buzz community ban (kind 9040, quartz ModerationBanEvent; the app only renders it); raw check in section 11"
# ---------------------------------------------------------------------------
banner "7. Buzz DMs"
# ---------------------------------------------------------------------------
# The relay answers a DM command with the DM it made in the OK
# (`response:{"channel_id":…}`). Every amy run is a cold socket, so that OK is the
# one the pool's own re-send after AUTH gets; see dm-open-channel-id. When the ack
# carries no id, the DM is found through `buzz dm list` (44100 + 40099) instead.
dm_lookup() { # who pubkey… → the DM id whose participants are exactly these
  local who="$1"; shift
  local want; want=$(printf '%s\n' "$@" | sort | jq -R . | jq -sc .)
  amy_as "$who" buzz dm list --relays "$RELAY" --limit 200
  printf '%s' "$OUT" | jq -r --argjson w "$want" '[.dms[]? | select((.participants|sort) == $w) | .dm_id] | first // empty'
}

amy_as A buzz dm open "$RELAY" "${HEX[B]}"
ACK_ID=$(j '.channel_id // empty'); ACK_MSG=$(j '.relay_message // empty')
[[ $RC -eq 0 && "$(j .published)" == "true" ]] && pass dm-open "A opened a DM with B (41010 accepted)" || fail dm-open "rc=$RC $(last_reason A)"
if [[ -n "$ACK_ID" ]]; then
  pass dm-open-channel-id "the ack carried the relay-assigned DM id $ACK_ID"
else
  fail dm-open-channel-id "channel_id null, relay_message '$ACK_MSG': the relay's real OK (with response:{channel_id}) went to the pool's post-AUTH re-send; Context.publish's own retry then got duplicate:"
fi
DM1=$(dm_lookup A "${HEX[A]}" "${HEX[B]}")
[[ -n "$DM1" ]] && pass dm-list "A's dm list finds the DM {A,B} → $DM1" || fail dm-list "no {A,B} DM in A's dm list: $(j -c .)"
amy_as A buzz dm open "$RELAY" "${HEX[B]}"
amy_as A buzz dm list --relays "$RELAY" --limit 200
N=$(printf '%s' "$OUT" | jq --arg a "${HEX[A]}" --arg b "${HEX[B]}" '[.dms[]? | select((.participants|sort) == ([$a,$b]|sort))] | length')
[[ "$N" == "1" ]] && pass dm-open-idempotent "re-opening gives the same single DM" || fail dm-open-idempotent "$N DMs for {A,B}"
DM_B=$(dm_lookup B "${HEX[A]}" "${HEX[B]}")
[[ -n "$DM1" && "$DM_B" == "$DM1" ]] && pass dm-list-cross-user "B's dm list shows the same DM" || fail dm-list-cross-user "B sees '$DM_B', A '$DM1'"
DM1=${DM1:-00000000-0000-4000-8000-000000000001}

amy_as A buzz post "$RELAY" "$DM1" "secret history line"
DM_MSG=$(j '.event_id // empty')
[[ $RC -eq 0 ]] && pass dm-post "A posts in the DM" || fail dm-post "rc=$RC $(last_reason A)"
amy_as B buzz read "$RELAY" "$DM1"
[[ "$(j "[.messages[]?.id] | index(\"$DM_MSG\") != null")" == "true" ]] && pass dm-read "B reads A's DM message" || fail dm-read "B's DM read count=$(j .count)"
amy_as C buzz read "$RELAY" "$DM1"
[[ "$(j "[.messages[]?.id] | index(\"$DM_MSG\") != null")" != "true" ]] && pass dm-private "outsider C can't read the DM (rc=$RC, $(j .count) messages)" || fail dm-private "C read the DM"

# add-member: the default `ask` on a DM of two people → needs_history_choice.
amy_as A buzz dm add-member "$RELAY" "$DM1" "${HEX[D]}"
if [[ $RC -ne 0 && "$(last_err A | jq -r .error)" == "needs_history_choice" ]]; then
  pass dm-add-ask "default --history ask → needs_history_choice"
else
  fail dm-add-ask "ask: rc=$RC $(j -c .) $(last_reason A)"
fi
# exclude → a new DM {A,B,D} without the history.
amy_as A buzz dm add-member "$RELAY" "$DM1" "${HEX[D]}" --history exclude
[[ $RC -eq 0 && "$(j .history)" == "exclude" ]] && pass dm-add-exclude "exclude accepted (ack channel_id=$(j .channel_id), created=$(j .created))" \
  || fail dm-add-exclude "exclude: rc=$RC $(last_reason A)"
DM_EX=$(dm_lookup D "${HEX[A]}" "${HEX[B]}" "${HEX[D]}")
if [[ -n "$DM_EX" && "$DM_EX" != "$DM1" ]]; then
  amy_as D buzz read "$RELAY" "$DM_EX"
  [[ $RC -eq 0 && "$(j "[.messages[]?.id] | index(\"$DM_MSG\") == null")" == "true" ]] \
    && pass dm-exclude-no-history "D is in a new DM $DM_EX and does not see DM1's history" || fail dm-exclude-no-history "D's read: rc=$RC $(j -c '[.messages[]?.content]')"
else
  fail dm-exclude-no-history "no new {A,B,D} DM in D's dm list ('$DM_EX')"
fi
# include → DM1 grows in place to {A,B,C} and C sees its history.
amy_as A buzz dm add-member "$RELAY" "$DM1" "${HEX[C]}" --history include
[[ $RC -eq 0 && "$(j .history)" == "include" ]] && pass dm-add-include "include accepted (ack channel_id=$(j .channel_id))" || fail dm-add-include "include: rc=$RC $(last_reason A)"
DM_IN=$(dm_lookup C "${HEX[A]}" "${HEX[B]}" "${HEX[C]}")
amy_as C buzz read "$RELAY" "${DM_IN:-$DM1}"
[[ "$(j "[.messages[]?.id] | index(\"$DM_MSG\") != null")" == "true" ]] && pass dm-include-history "C sees the DM's history in ${DM_IN:-$DM1} (same id as DM1: $([[ "$DM_IN" == "$DM1" ]] && echo yes || echo no))" \
  || fail dm-include-history "C can't see the history (dm '$DM_IN', count=$(j .count), rc=$RC)"

# The people cap: 9 people, the opener included; agents don't count.
amy_as A buzz dm open "$RELAY" "${EXTRA[@]:0:8}"
[[ $RC -eq 0 ]] && pass dm-cap-9-ok "DM of 9 people accepted" || fail dm-cap-9-ok "9 people: rc=$RC $(last_reason A)"
amy_as A buzz dm open "$RELAY" "${EXTRA[@]:0:9}"
if [[ $RC -ne 0 ]] && last_reason A | grep -q 'at most 9 people'; then
  pass dm-cap-10-refused "10 people refused: $(last_reason A)"
else
  fail dm-cap-10-refused "10 people: rc=$RC $(last_reason A)"
fi

# Agent G = A's agent. Registered relay-side once G has a users row (its first AUTH).
amy_as G event --kind 0 --content '{"name":"agent-g"}' --relay "$RELAY"
psql_q "UPDATE users SET agent_owner_pubkey = decode('${HEX[A]}','hex') WHERE pubkey = decode('${HEX[G]}','hex')" >/dev/null
IS_AGENT=$(psql_q "SELECT count(*) FROM users WHERE pubkey = decode('${HEX[G]}','hex') AND agent_owner_pubkey IS NOT NULL")
[[ "$IS_AGENT" == "1" ]] && info "G registered as A's agent" || fail agent-register "no users row for G to register ($IS_AGENT)"
amy_as A buzz dm open "$RELAY" "${EXTRA[@]:0:8}" "${HEX[G]}"
[[ $RC -eq 0 ]] && pass dm-cap-agent-extra "9 people + an agent accepted (agents don't count)" || fail dm-cap-agent-extra "rc=$RC $(last_reason A)"
amy_as A buzz dm open "$RELAY" "${HEX[B]}" "${HEX[G]}"
DM_AG=$(dm_lookup A "${HEX[A]}" "${HEX[B]}" "${HEX[G]}")
[[ -n "$DM_AG" ]] || fail dm-agent-open "no {A,B,G} DM found"
amy_as G buzz dm add-member "$RELAY" "${DM_AG:-$DM1}" "${HEX[C]}"
if [[ $RC -eq 0 && "$(j .history)" == "exclude" ]]; then
  pass dm-agent-add-untagged "agent's default add refused for its history tag, re-sent untagged → history=exclude"
else
  fail dm-agent-add-untagged "agent add: rc=$RC history=$(j .history) $(last_reason G)"
fi
amy_as G buzz dm add-member "$RELAY" "${DM_AG:-$DM1}" "${HEX[D]}" --history include
if [[ $RC -ne 0 ]] && last_reason G | grep -q "only people can share"; then
  pass dm-agent-include-refused "agent --history include keeps the relay's refusal"
else
  fail dm-agent-include-refused "agent include: rc=$RC $(last_reason G)"
fi
amy_as B buzz dm hide "$RELAY" "$DM1"
[[ $RC -eq 0 ]] && pass dm-hide "dm hide (41012) accepted" || fail dm-hide "rc=$RC $(last_reason B)"

# ---------------------------------------------------------------------------
banner "8. Jobs (A files a job for agent G)"
# ---------------------------------------------------------------------------
amy_as A relaygroup put-user "$RELAY" "$STREAM" "${HEX[G]}" --buzz-role bot
amy_as A buzz job request "$RELAY" "say hi" --agent "${HEX[G]}" --channel "$STREAM"
JOB=$(j '.job_id // empty')
if [[ $RC -eq 0 ]]; then
  pass job-request "43001 accepted ($JOB)"
  amy_as G buzz agent serve "$RELAY" --exec 'cat' --accept-from "${HEX[A]}" --channel "$STREAM" --once --timeout 6
  amy_as A buzz job show "$RELAY" "$JOB"
  [[ "$(j .state)" == "completed" ]] && pass job-completed "agent served the job; A sees state=completed" || fail job-completed "job state=$(j .state)"
else
  fail job-request "Buzz refuses kind 43001: '$(last_reason A)' (ingest.rs required_scope_for_kind has no arm for 43001-43006)"
fi


# ---------------------------------------------------------------------------
banner "9. Pins (NIP-29 9010 vs Buzz NIP-PM 40010/30623)"
# ---------------------------------------------------------------------------
amy_as A relaygroup pin "$RELAY" "$STREAM" "$ROOT"
if [[ $RC -eq 0 ]]; then
  fail pin-nip29 "relaygroup pin (9010) was accepted by Buzz: $(j -c .)"
else
  info "relaygroup pin on Buzz: $(last_err A)"
  gap pin-nip29 "relaygroup pin sends NIP-29 9010, which Buzz does not take: $(last_err A | jq -r '.detail' 2>/dev/null)"
fi
gap pin-nip-pm "Buzz pins are NIP-PM (40010 pin / 40011 unpin / relay-signed 30623 list, upstream e95d7a950); quartz/amy/Amethyst have none of the three kinds"
amy_as A event --kind 40010 --relay "$RELAY" --tags "[[\"h\",\"$STREAM\"],[\"e\",\"$ROOT\"]]"
[[ $RC -eq 0 ]] && info "raw 40010 accepted" || info "raw 40010: $(last_err A)"
fetch_as B 30623 "d=$STREAM"
PINS=$(printf '%s' "$OUT" | jq -r '[..|objects|select(.kind==30623)|.tags[]|select(.[0]=="e")|.[1]]|join(",")' 2>/dev/null)
[[ "$PINS" == *"$ROOT"* ]] && pass pin-nip-pm-relay "relay publishes 30623 with the pinned message (what Amethyst would need to read)" \
  || fail pin-nip-pm-relay "no 30623 with the pin: '$PINS'"

# ---------------------------------------------------------------------------
banner "10. Invite links"
# ---------------------------------------------------------------------------
# Mint as A (relay owner) with NIP-98 over plain HTTP, then claim with `amy buzz join`.
nip98() { # who method url body
  local body_hash; body_hash=$(printf '%s' "$4" | sha256sum | cut -d' ' -f1)
  local tags="[[\"u\",\"$3\"],[\"method\",\"$2\"]"
  [[ -n "$4" ]] && tags="$tags,[\"payload\",\"$body_hash\"]"
  tags="$tags]"
  HOME="$STATE_DIR/home-$1" "$AMY_BIN" --account "$1" --secret-backend plaintext --json \
    event --kind 27235 --tags "$tags" 2>/dev/null | jq -c .event | base64 -w0
}
MINT_BODY='{}'
MINT=$(curl -s -X POST "$HTTP/api/invites" -H 'Content-Type: application/json' \
  -H "Authorization: Nostr $(nip98 A POST "$HTTP/api/invites" "$MINT_BODY")" -d "$MINT_BODY")
printf 'mint: %s\n' "$MINT" >>"$LOG_FILE"
INVITE_URL=$(jq -r '.url // .invite_url // .link // empty' <<<"$MINT" 2>/dev/null)
if [[ -n "$INVITE_URL" ]]; then
  pass invite-mint "relay owner minted $INVITE_URL"
  amy_as C buzz join "$INVITE_URL"
  if [[ $RC -eq 0 ]]; then
    pass invite-claim "amy buzz join claimed it: status=$(j .status) role=$(j .role)"
  else
    fail invite-claim "amy buzz join $INVITE_URL: rc=$RC '$(last_reason C)' (BuzzInvite.httpBase() is always https://; Buzz mints http:// links for a ws:// relay)"
  fi
  # The same claim over the link's own scheme, NIP-98 signed by amy: does the relay side work?
  CODE=${INVITE_URL##*/invite/}
  CLAIM_BODY="{\"code\":\"$CODE\"}"
  CLAIM=$(curl -s -X POST "$HTTP/api/invites/claim" -H 'Content-Type: application/json' \
    -H "Authorization: Nostr $(nip98 C POST "$HTTP/api/invites/claim" "$CLAIM_BODY")" -d "$CLAIM_BODY")
  printf 'claim: %s\n' "$CLAIM" >>"$LOG_FILE"
  [[ -n "$(jq -r '.status // empty' <<<"$CLAIM" 2>/dev/null)" ]] && pass invite-claim-http "the claim works over http with an amy-signed NIP-98: $(jq -c . <<<"$CLAIM")" \
    || fail invite-claim-http "claim over http: $CLAIM"
  LIST=$(curl -s "$HTTP/api/invites" -H "Authorization: Nostr $(nip98 A GET "$HTTP/api/invites" "")")
  printf 'list: %s\n' "$LIST" >>"$LOG_FILE"
  info "GET /api/invites (new upstream): $(jq -c . <<<"$LIST" 2>/dev/null | head -c 300)"
else
  fail invite-mint "mint answered: $MINT"
fi
gap invite-manage "list/revoke invite links (GET /api/invites, POST /api/invites/revoke, upstream 792a9ce56): no amy verb or app screen"

# ---------------------------------------------------------------------------
banner "11. Community ban (raw kind 9040 by the relay owner)"
# ---------------------------------------------------------------------------
amy_as D relaygroup join "$RELAY" "$STREAM"
amy_as D buzz post "$RELAY" "$STREAM" "D before the ban"
[[ $RC -eq 0 ]] || fail ban-before "D could not post before the ban: $(last_reason D)"
amy_as A event --kind 9040 --relay "$RELAY" --tags "[[\"p\",\"${HEX[D]}\"],[\"reason\",\"harness\"]]"
if [[ $RC -eq 0 ]]; then
  # Buzz answers a banned key's AUTH `OK false "blocked: you are banned from this community"`
  # and closes the socket (relay log: auth denied, reason banned).
  amy_as D buzz post "$RELAY" "$STREAM" "D after the ban"
  BAN_MS=$(tail -n1 "$TIMINGS_FILE" | cut -f1)
  if [[ $RC -eq 0 ]]; then
    fail ban-enforced "D still posts after the ban"
  else
    pass ban-enforced "relay owner's 9040 ban: D's next post refused (exit $RC)"
    if last_reason D | grep -q 'banned'; then
      pass ban-reported "amy reports the ban: '$(last_reason D)' after $BAN_MS ms"
    else
      fail ban-reported "amy took $BAN_MS ms and reported '$(last_err D | jq -r .error)': '$(last_reason D)'; the AUTH refusal (blocked: you are banned …) never reaches the caller"
    fi
  fi
else
  fail ban-enforced "9040 refused: $(last_reason A)"
fi
