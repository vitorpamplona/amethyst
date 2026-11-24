#!/usr/bin/env bash
#
# dm-nostr-tools.sh — NIP-17 DMs between amy and a SECOND client, nostr-tools
# (nip17 + nip59, Unlicense), both directions, on one loopback relay.
#
# dm-interop-headless.sh checks amy against amy. Here the counterparty is
# nostr-tools-peer/peer.mjs, a small Node client on nostr-tools 2.26.1: its
# kind:14 comes from nostr-tools' own nip17.wrapManyEvents; for kind:7 and
# kind:15, which nostr-tools has no NIP-17 helper for, it builds the rumor by
# NIP-25 / NIP-17 and wraps it with nip59.wrapEvent.
#
# Covers text, replies, subjects, reactions, file messages (kind:15), group
# rooms with several recipients and kind:10050 routing, each way.
#
# amy only delivers to someone else's kind:10050 relay when it is a public
# address (quartz's PublicAddress: not loopback, private, CGNAT or the
# documentation ranges, so not 192.0.2.x either). The relay (`amy serve`,
# geode) runs on 127.0.0.1 unless --host names such an address on this machine;
# without one, amy's sends use --allow-fallback, which lands on the bootstrap
# relay (AMY_BOOTSTRAP_RELAYS = this relay), and dm-nt-01-routed is skipped.
#
# Usage: cli/tests/dm/dm-nostr-tools.sh [--port 8091] [--host IP] [--no-build]
#
# Identities: A and D are amy accounts; N and M are nostr-tools peers.
# Requires: jq, node >= 20, npm (nostr-tools is installed into
# nostr-tools-peer/ with `npm ci` from its package-lock.json).
#
set -uo pipefail
export LC_ALL=C.UTF-8 LANG=C.UTF-8

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-dm-nostr-tools"
LOG_DIR="$STATE_DIR/logs"
LOG_FILE="$LOG_DIR/run.log"
RESULTS_FILE="$STATE_DIR/results"
AMY_BIN="${AMY_BIN:-$REPO_ROOT/cli/build/install/amy/bin/amy}"
PEER_DIR="$SCRIPT_DIR/nostr-tools-peer"
RELAY_PORT=8091
RELAY_HOST="${RELAY_HOST:-}"
NO_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port)     RELAY_PORT="$2"; shift ;;
    --host)     RELAY_HOST="$2"; shift ;;
    --no-build) NO_BUILD=1 ;;
    -h|--help)  sed -n '3,25p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR" "$LOG_DIR"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"
# shellcheck source=../headless/helpers.sh
source "$TESTS_DIR/headless/helpers.sh"

for tool in jq node npm; do command -v "$tool" >/dev/null || { fail_msg "missing tool: $tool"; exit 1; }; done

# Whether amy would dial IP when someone else's list names it (mirrors quartz's PublicAddress).
amy_dials_ipv4() {
  is_routable_ipv4 "$1" || return 1
  case "$1" in 100.6[4-9].*|100.[7-9][0-9].*|100.1[01][0-9].*|100.12[0-7].*) return 1 ;;
    192.0.0.*|192.0.2.*|192.88.99.*|198.18.*|198.19.*|198.51.100.*|203.0.113.*) return 1 ;; esac
  local first=${1%%.*}; [[ $first -lt 224 ]]
}
RELAY_HOST="${RELAY_HOST:-127.0.0.1}"
FALLBACK=()
ROUTED_SKIP=""
if ! amy_dials_ipv4 "$RELAY_HOST"; then
  FALLBACK=(--allow-fallback)
  ROUTED_SKIP="relay on $RELAY_HOST, which amy does not dial from someone else's kind:10050; sends use --allow-fallback"
fi
RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"
RELAY_DATA="$STATE_DIR/relay"

export AMY_LOCAL_ONLY=1 AMY_ALLOW_HOSTS="$RELAY_HOST" AMY_BOOTSTRAP_RELAYS="$RELAY_URL"

cleanup() { stop_local_relay; }
trap cleanup EXIT

if [[ "$NO_BUILD" -eq 0 ]]; then
  step "building :cli:installDist"
  ( cd "$REPO_ROOT" && ./gradlew -q :cli:installDist ) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "installDist failed (see $LOG_FILE)"; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { fail_msg "amy binary missing at $AMY_BIN"; exit 1; }

if [[ ! -d "$PEER_DIR/node_modules/nostr-tools" ]]; then
  step "npm ci in $PEER_DIR"
  ( cd "$PEER_DIR" && npm ci --no-audit --no-fund ) >>"$LOG_FILE" 2>&1 || { fail_msg "npm ci failed (see $LOG_FILE)"; exit 1; }
fi
NT_VERSION=$(jq -r .version "$PEER_DIR/node_modules/nostr-tools/package.json")

OUT=""; RC=0
amy_as() {
  local who="$1"; shift
  printf '$ [%s] amy %s\n' "$who" "$*" >>"$LOG_FILE"
  OUT=$(HOME="$STATE_DIR/home" "$AMY_BIN" --account "$who" --secret-backend plaintext --json "$@" 2>>"$LOG_FILE")
  RC=$?
  printf '%s\n(rc=%s)\n' "$OUT" "$RC" >>"$LOG_FILE"
}
declare -A SK HEX
peer() { # peer WHO CMD ARGS…
  local who="$1" cmd="$2"; shift 2
  printf '$ [%s] peer %s %s\n' "$who" "$cmd" "$*" >>"$LOG_FILE"
  OUT=$(node "$PEER_DIR/peer.mjs" "$cmd" --sk "${SK[$who]}" --relay "$RELAY_URL" "$@" 2>>"$LOG_FILE")
  RC=$?
  printf '%s\n(rc=%s)\n' "$OUT" "$RC" >>"$LOG_FILE"
}
jqo() { printf '%s' "$OUT" | jq -r "$@" 2>/dev/null; }
check() {
  if [[ "$2" -eq 0 ]]; then pass_msg "$1: $3"; record_result "$1" pass "$3"; else fail_msg "$1: $4"; record_result "$1" fail "$4"; fi
}
# peer_rumor WHO JQ-SELECT → OUT holds the first rumor WHO can read that matches.
peer_rumor() {
  local who="$1" sel="$2" tries
  for tries in 1 2 3 4 5; do
    peer "$who" read --wait 4
    local hit
    hit=$(printf '%s\n' "$OUT" | jq -c "select(.rumor != null) | .rumor | select($sel)" 2>/dev/null | head -1)
    if [[ -n "$hit" ]]; then OUT="$hit"; return 0; fi
    sleep 1
  done
  OUT=""; return 1
}
# amy_find WHO JQ-SELECT → OUT holds the first message of `dm list` that matches.
amy_find() {
  local who="$1" sel="$2" tries
  for tries in 1 2 3 4 5; do
    amy_as "$who" dm list --since 0 --timeout 4 --limit 200
    local hit
    hit=$(jqo -c ".messages[] | select($sel)" | head -1)
    if [[ -n "$hit" ]]; then OUT="$hit"; return 0; fi
    sleep 1
  done
  OUT=""; return 1
}

banner "NIP-17 interop: amy vs nostr-tools $NT_VERSION on $RELAY_URL"
start_local_relay

step "identities"
for who in A D; do
  amy_as "$who" init || { fail_msg "init $who failed"; exit 1; }
  HEX[$who]=$(jqo .hex)
  amy_as "$who" relay add "$RELAY_URL" || { fail_msg "relay add $who failed"; exit 1; }
  amy_as "$who" relay publish-lists >/dev/null
done
for who in N M; do
  SK[$who]=$(node -e 'console.log(require("crypto").randomBytes(32).toString("hex"))')
  peer "$who" inbox
  HEX[$who]=$(jqo .pubkey)
  [[ "$(jqo '[.published[] | select(.ok)] | length')" == "2" ]] || { fail_msg "peer $who could not publish its kind:10050"; exit 1; }
done
info "A=${HEX[A]} D=${HEX[D]} N=${HEX[N]} M=${HEX[M]}"

# --- dm-nt-01 amy → nostr-tools: text, routed by kind:10050 -------------------------
amy_as A dm send "${HEX[N]}" "hello N, from amy" "${FALLBACK[@]}"
A_MSG=$(jqo .event_id)
if [[ -n "$ROUTED_SKIP" ]]; then
  skip_msg "dm-nt-01-routed: $ROUTED_SKIP"; record_result dm-nt-01-routed skip "$ROUTED_SKIP"
else
  [[ "$RC" -eq 0 && "$(jqo --arg n "${HEX[N]}" '[.recipients[] | select(.pubkey == $n and .relay_source == "kind_10050")] | length')" == "1" ]]
  check dm-nt-01-routed $? "amy found N's kind:10050 (published by nostr-tools) and sent there" "send: rc=$RC $OUT"
fi
peer_rumor N ".kind == 14 and .content == \"hello N, from amy\""
[[ -n "$OUT" && "$(jqo .id)" == "$A_MSG" && "$(jqo .pubkey)" == "${HEX[A]}" && "$(jqo -c '[.tags[] | select(.[0]=="p") | .[1]]')" == "[\"${HEX[N]}\"]" ]]
check dm-nt-01-read $? "nostr-tools unwraps amy's kind:14: same rumor id, author A, p = N" "N read: $OUT"

# --- dm-nt-02 nostr-tools → amy: text ----------------------------------------------
peer N send --to "${HEX[A]}" --text "hello A, from nostr-tools"
N_MSG=$(jqo .rumor.id)
amy_find A ".event_id == \"$N_MSG\""
[[ -n "$OUT" && "$(jqo .type)" == "text" && "$(jqo .author)" == "${HEX[N]}" && "$(jqo .content)" == "hello A, from nostr-tools" ]]
check dm-nt-02-read $? "amy decrypts nostr-tools' kind:14 (author N, same rumor id)" "A list: $OUT"

# --- dm-nt-03 replies -------------------------------------------------------------------
amy_as A dm send "${HEX[N]}" "amy replies" --reply-to "$N_MSG" "${FALLBACK[@]}"
A_REPLY=$(jqo .event_id)
peer_rumor N ".id == \"$A_REPLY\""
[[ -n "$OUT" && "$(jqo --arg t "$N_MSG" '[.tags[] | select(.[0]=="e" and .[1]==$t)] | length')" == "1" ]]
check dm-nt-03-amy-replies $? "amy's reply carries e = N's message" "N read: $OUT"
peer N send --to "${HEX[A]}" --text "nostr-tools replies" --reply-to "$A_MSG"
N_REPLY=$(jqo .rumor.id)
amy_find A ".event_id == \"$N_REPLY\""
[[ -n "$OUT" && "$(jqo -c .reply_to)" == "[\"$A_MSG\"]" ]]
check dm-nt-03-peer-replies $? "amy reads nostr-tools' reply (e with the 'reply' marker) as replying to A's message" "A list: $OUT"

# --- dm-nt-04 subject -----------------------------------------------------------------
peer N send --to "${HEX[A]}" --text "with a subject" --subject "Pizza night"
N_SUBJ=$(jqo .rumor.id)
amy_find A ".event_id == \"$N_SUBJ\""
[[ -n "$OUT" && "$(jqo .subject)" == "Pizza night" ]]
check dm-nt-04-peer-subject $? "amy reads nostr-tools' subject tag" "A list: $OUT"
amy_as A dm send "${HEX[N]}" "amy sets a subject" --subject "Pasta night" "${FALLBACK[@]}"
A_SUBJ=$(jqo .event_id)
peer_rumor N ".id == \"$A_SUBJ\""
[[ -n "$OUT" && "$(jqo '[.tags[] | select(.[0]=="subject") | .[1]] | join(",")')" == "Pasta night" ]]
check dm-nt-04-amy-subject $? "nostr-tools sees amy's subject tag" "N read: $OUT"

# --- dm-nt-05 reactions ----------------------------------------------------------------
amy_as A dm react "$N_MSG" "🍕" "${FALLBACK[@]}"
A_REACT=$(jqo .event_id)
peer_rumor N ".id == \"$A_REACT\""
[[ -n "$OUT" && "$(jqo .kind)" == "7" && "$(jqo .content)" == "🍕" && "$(jqo --arg t "$N_MSG" '[.tags[] | select(.[0]=="e" and .[1]==$t)] | length')" == "1" && "$(jqo --arg n "${HEX[N]}" '[.tags[] | select(.[0]=="p" and .[1]==$n)] | length')" == "1" ]]
check dm-nt-05-amy-reacts $? "nostr-tools unwraps amy's kind:7 rumor: e = N's message, p = N" "N read: $OUT"
peer N react --to "${HEX[A]}" --target "$A_MSG" --target-author "${HEX[A]}" --emoji "+"
N_REACT=$(jqo .rumor.id)
amy_find A ".event_id == \"$N_REACT\""
[[ -n "$OUT" && "$(jqo .type)" == "reaction" && "$(jqo .content)" == "+" && "$(jqo -c .target)" == "[\"$A_MSG\"]" ]]
check dm-nt-05-peer-reacts $? "amy lists nostr-tools' wrapped reaction to A's message" "A list: $OUT"

# --- dm-nt-06 file messages (kind:15) ---------------------------------------------------
KEY=$(node -e 'console.log(require("crypto").randomBytes(32).toString("hex"))')
NONCE=$(node -e 'console.log(require("crypto").randomBytes(12).toString("hex"))')
XHASH=$(printf 'cipher' | sha256sum | cut -d' ' -f1)
peer N file --to "${HEX[A]}" --url "https://blossom.example/$XHASH" --key "$KEY" --nonce "$NONCE" --type image/png --x "$XHASH"
N_FILE=$(jqo .rumor.id)
amy_find A ".event_id == \"$N_FILE\""
[[ -n "$OUT" && "$(jqo .type)" == "file" && "$(jqo .mime_type)" == "image/png" && "$(jqo .decryption_key)" == "$KEY" && "$(jqo .decryption_nonce)" == "$NONCE" && "$(jqo .hash)" == "$XHASH" && "$(jqo .encryption_algorithm)" == "aes-gcm" ]]
check dm-nt-06-peer-file $? "amy reads nostr-tools' kind:15: url, file-type, aes-gcm key and nonce, x" "A list: $OUT"
amy_as A dm send-file "${HEX[N]}" "https://blossom.example/$XHASH" --key "$KEY" --nonce "$NONCE" --mime-type image/jpeg --hash "$XHASH" --size 6 --dim 4x3 "${FALLBACK[@]}"
A_FILE=$(jqo .event_id)
peer_rumor N ".id == \"$A_FILE\""
tagv() { jqo --arg k "$1" '[.tags[] | select(.[0]==$k) | .[1]] | join(",")'; }
[[ -n "$OUT" && "$(jqo .kind)" == "15" && "$(jqo .content)" == "https://blossom.example/$XHASH" && "$(tagv file-type)" == "image/jpeg" && "$(tagv encryption-algorithm)" == "aes-gcm" && "$(tagv decryption-key)" == "$KEY" && "$(tagv decryption-nonce)" == "$NONCE" && "$(tagv x)" == "$XHASH" && "$(tagv dim)" == "4x3" ]]
check dm-nt-06-amy-file $? "nostr-tools sees amy's kind:15 tags as NIP-17 'File Message' names them" "N read: $OUT"

# --- dm-nt-07 group rooms ----------------------------------------------------------------
amy_as A dm send "${HEX[N]},${HEX[M]},${HEX[D]}" "group hello from amy" "${FALLBACK[@]}"
A_GROUP=$(jqo .event_id)
[[ "$(jqo '.recipients | length')" == "4" ]]
check dm-nt-07-amy-group-wraps $? "amy wrapped its group message for N, M, D and itself" "send: $OUT"
ROOM=$(printf '%s\n' "${HEX[N]}" "${HEX[M]}" "${HEX[D]}" | sort | jq -Rsc 'split("\n") | map(select(. != ""))')
peer_rumor M ".id == \"$A_GROUP\""
[[ -n "$OUT" && "$(jqo -c '[.tags[] | select(.[0]=="p") | .[1]] | sort')" == "$ROOM" ]]
check dm-nt-07-peer-reads-group $? "nostr-tools (M) reads amy's group message with the whole room in p" "M read: $OUT"
# nostr-tools' own helper (nip17.wrapManyEvents) writes one rumor per recipient, p-tagging only that
# recipient: no room of more than two (NIP-17 "Chat Rooms"). amy must show each copy as what it says.
peer N send --to "${HEX[A]},${HEX[M]},${HEX[D]}" --text "wrapManyEvents to three"
amy_find D ".content == \"wrapManyEvents to three\""
[[ -n "$OUT" && "$(jqo -c .to)" == "[\"${HEX[D]}\"]" && "$(jqo .author)" == "${HEX[N]}" ]]
check dm-nt-07-wrapmany-is-one-to-one $? "amy (D) reads nostr-tools' wrapManyEvents copy as the 1:1 it is (p = D only; nostr-tools deviation)" "D list: $OUT"
peer N room --to "${HEX[A]},${HEX[M]},${HEX[D]}" --text "group hello from nostr-tools"
N_GROUP=$(jqo .rumor.id)
amy_find D ".event_id == \"$N_GROUP\""
ROOM_N=$(printf '%s\n' "${HEX[A]}" "${HEX[M]}" "${HEX[D]}" | sort | jq -Rsc 'split("\n") | map(select(. != ""))')
[[ -n "$OUT" && "$(jqo -c '.to | sort')" == "$ROOM_N" && "$(jqo .author)" == "${HEX[N]}" ]]
check dm-nt-07-amy-reads-group $? "amy (D) reads a nostr-tools room message (one rumor, nip59) with the room A, M, D" "D list: $OUT"
amy_as A dm send "${HEX[N]}" "amy answers the room" --reply-to "$N_GROUP" "${FALLBACK[@]}"
A_GREPLY=$(jqo .event_id)
peer_rumor M ".id == \"$A_GREPLY\""
# NIP-17 "Chat Rooms": the room is pubkey + p tags (amy's reply also p-tags A itself, which names the same room).
ROOM_R=$(printf '%s\n' "${HEX[A]}" "${HEX[N]}" "${HEX[M]}" "${HEX[D]}" | sort | jq -Rsc 'split("\n") | map(select(. != ""))')
[[ -n "$OUT" && "$(jqo -c '[.pubkey, (.tags[] | select(.[0]=="p") | .[1])] | sort | unique')" == "$ROOM_R" ]]
check dm-nt-07-reply-stays-in-room $? "amy's reply to a group message stays in its room (pubkey + p = A, N, M, D; M got it)" "M read: $OUT"

# --- dm-nt-08 rumor integrity -------------------------------------------------------------
peer N read --wait 4
BAD=$(printf '%s\n' "$OUT" | jq -c 'select(.error != null)' | head -3)
# Not vacuous: N opened every rumor amy sent it above (hello, reply, subject, file, room, room reply).
AMY_TO_N=6
OPENED=$(printf '%s\n' "$OUT" | jq -r --arg a "${HEX[A]}" 'select(.rumor.pubkey == $a) | .rumor.id' 2>/dev/null | sort -u | grep -c .)
[[ -z "$BAD" && "$OPENED" -ge "$AMY_TO_N" ]]
check dm-nt-08-every-wrap-opens $? "every wrap amy sent N passes nostr-tools' unwrapEvent checks (seal signature, seal pubkey = rumor pubkey): $OPENED rumors" "opened $OPENED of $AMY_TO_N, unwrap errors: $BAD"

print_summary
awk -F'\t' '$2=="fail"{f=1} END{exit f}' "$RESULTS_FILE"
