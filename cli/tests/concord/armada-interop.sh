#!/usr/bin/env bash
#
# armada-interop.sh — live Concord interop: amy (quartz + commons, the code the
# Amethyst app runs) against Armada (soapbox.pub's web client), both on one
# loopback geode relay, with fresh keys for every identity on every run.
#
# Armada is driven headlessly by armada-driver.test.tsx: Armada's own React
# hooks and src/concord modules mounted with @testing-library in vitest's jsdom
# environment (how Armada's own hook tests mount them), plus the account-wide
# services its App mounts (WireSync, ControlPlaneSync, CommunityDuties, the
# resume hooks). Only the relay pool, the signed-in user, the app config and
# toasts are seams. Each Armada identity is one driver process answering JSON
# commands on a local port (see armadaCommands.tsx for the command table).
#
# Armada is AGPL-3.0: it is NEVER vendored here. Point ARMADA_DIR at a scratch
# checkout with `npm ci` done; the harness copies the two driver files into
# $ARMADA_DIR/src/__interop__/ (so `@/…` imports resolve) and runs vitest there.
#
# Identities:  X = Armada (owns community A, Roles v2)   Y = Armada
#              P = amy    (owns community B, legacy)    Q, R, S = amy
#
# Scenarios (each line in the summary is one pass/fail/gap with evidence):
#   a  Armada creates → Direct Invite + link → amy accepts / joins → same channels+metadata
#   b  messages both ways; edits, reactions and deletes both ways, read through each fold
#   c  amy creates → Direct Invite + link → Armada accepts / joins → messages both ways
#   d  roles: Armada (v2) role+grant read by amy; amy (legacy) role+grant read by Armada;
#      authority exercised across clients
#   e  moderation: kicks and bans from each side, Refounding, removed member cut off
#   f  private channels, pins, disappearing timer, late joiner after a Refounding,
#      recover, dissolve
#
# Usage:
#   ARMADA_DIR=/path/to/armada cli/tests/concord/armada-interop.sh [--port N] [--keep]
#            [--only a,b,c]
# Env:  NODE_DIR (dir holding node ≥ 22; default: node on PATH, else /opt/node22/bin)
#       AMY_BIN (default: cli/build/install/amy/bin/amy)
#       STATE_DIR (default: $TMPDIR/amy-armada-interop, wiped each run)
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
TESTS_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
STATE_DIR="${STATE_DIR:-${TMPDIR:-/tmp}/amy-armada-interop}"
LOG_DIR="$STATE_DIR/logs"
LOG_FILE="$LOG_DIR/run.log"
RESULTS_FILE="$STATE_DIR/results"
EVIDENCE_FILE="$STATE_DIR/evidence.tsv"
AMY_BIN="${AMY_BIN:-$REPO_ROOT/cli/build/install/amy/bin/amy}"
RELAY_PORT="${RELAY_PORT:-7781}"
DRIVER_BASE_PORT="${DRIVER_BASE_PORT:-7790}"
KEEP=0
ONLY="a,b,c,d,e,f"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --port) RELAY_PORT="$2"; shift ;;
    --keep) KEEP=1 ;;
    --only) ONLY="$2"; shift ;;
    -h|--help) sed -n '3,37p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done
want() { [[ ",$ONLY," == *",$1,"* ]]; }

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR" "$LOG_DIR"
: >"$LOG_FILE"; : >"$RESULTS_FILE"; : >"$EVIDENCE_FILE"

# shellcheck source=../lib.sh
source "$TESTS_DIR/lib.sh"
# shellcheck source=../headless/helpers.sh
source "$TESTS_DIR/headless/helpers.sh"

export LC_ALL=C.UTF-8
export NO_PROXY='*' no_proxy='*'

# --- preflight ---------------------------------------------------------------
[[ -n "${ARMADA_DIR:-}" ]] || { fail_msg "set ARMADA_DIR to an Armada checkout (npm ci done)"; exit 2; }
ARMADA_DIR="$(cd -- "$ARMADA_DIR" && pwd)"
[[ -d "$ARMADA_DIR/src/concord" && -d "$ARMADA_DIR/node_modules/vitest" ]] \
  || { fail_msg "$ARMADA_DIR is not an Armada checkout with node_modules (run npm ci there)"; exit 2; }
if [[ -n "${NODE_DIR:-}" ]]; then PATH="$NODE_DIR:$PATH"
elif ! node -e 'process.exit(+process.versions.node.split(".")[0] < 22 ? 1 : 0)' 2>/dev/null; then PATH="/opt/node22/bin:$PATH"; fi
node -e 'process.exit(+process.versions.node.split(".")[0] < 22 ? 1 : 0)' || { fail_msg "need node >= 22 (set NODE_DIR)"; exit 2; }
for t in jq curl; do command -v "$t" >/dev/null || { fail_msg "missing $t"; exit 2; }; done
[[ -x "$AMY_BIN" ]] || { fail_msg "amy not built: ./gradlew :cli:installDist"; exit 2; }

ARMADA_COMMIT="$(git -C "$ARMADA_DIR" rev-parse --short HEAD 2>/dev/null || cat "$ARMADA_DIR/.armada-commit" 2>/dev/null || echo unknown)"
AMY_COMMIT="$(git -C "$REPO_ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"

# The relay's address. Direct Invites resolve the recipient's kind 10050 / NIP-65 relays, and
# amy (quartz's RelayUrlNormalizer.isDialableFromOthers + SurgeDns) refuses to dial a relay from
# someone else's list that is a private/loopback/documentation IP or resolves to loopback. So the
# relay listens on a non-loopback address and everyone NAMES it by a dotted hostname that amy's
# JVM resolves through a private hosts file (-Djdk.net.hosts.file; nothing else resolves, so amy
# cannot reach any public relay either). Armada's driver opens every socket at the address.
RELAY_IP="${RELAY_IP:-$(detect_routing_host || echo 127.0.0.2)}"
RELAY_NAME="${RELAY_NAME:-relay.concord-interop.test}"
RELAY_HOST="$RELAY_IP"
RELAY_URL="ws://$RELAY_IP:$RELAY_PORT"
RELAY_SOCKET="$RELAY_URL"
RELAY_DATA="$STATE_DIR/relay"
DRIVER_HOST="$RELAY_IP"
printf '%s %s\n' "$RELAY_IP" "$RELAY_NAME" >"$STATE_DIR/hosts"
AMY_JAVA_OPTS="$(printf '%s' "${JAVA_TOOL_OPTIONS:-}" | sed -E 's/-D(https?|socks)\.(proxyHost|proxyPort|nonProxyHosts)=[^ ]*//g') -Djdk.net.hosts.file=$STATE_DIR/hosts"
# amy's seed set for looking up other people's relay lists: only our relay, never the public defaults.
NAMED_RELAY_URL="ws://$RELAY_NAME:$RELAY_PORT"
export AMY_BOOTSTRAP_RELAYS="$NAMED_RELAY_URL"
[[ "$RELAY_IP" == 127.* ]] && warn "no non-loopback address on this host: amy→Armada Direct Invites cannot be delivered (amy never dials a loopback relay from someone else's list)"

banner "Concord interop: amy $AMY_COMMIT vs Armada $ARMADA_COMMIT on $NAMED_RELAY_URL ($RELAY_IP)"

PIDS=()
declare -A DRIVER_PORT
declare -A HEX
cleanup() {
  if [[ "$KEEP" -eq 0 ]]; then
    for who in "${!DRIVER_PORT[@]}"; do arm_raw "$who" quit >/dev/null 2>&1; done
    sleep 1
    for p in "${PIDS[@]}"; do kill "$p" 2>/dev/null; done
    stop_local_relay
  else
    info "--keep: relay and drivers left running (state in $STATE_DIR)"
  fi
}
trap cleanup EXIT

start_local_relay
# From here on every party names the relay by its hostname.
RELAY_URL="$NAMED_RELAY_URL"

# --- amy identities ----------------------------------------------------------
AMY_HOME="$STATE_DIR/amy"
mkdir -p "$AMY_HOME"
LAST_ERR="$STATE_DIR/last.err"
# amy_as WHO args… → stdout JSON; stderr kept in $LAST_ERR and the log.
amy_as() {
  local who="$1"; shift
  local rc
  HOME="$AMY_HOME" JAVA_TOOL_OPTIONS="$AMY_JAVA_OPTS" "$AMY_BIN" --account "$who" --secret-backend plaintext --json "$@" 2>"$LAST_ERR"; rc=$?
  { printf '$ amy[%s] %s (rc=%d)\n' "$who" "$*" "$rc"; grep -v '^Picked up' "$LAST_ERR"; } >>"$LOG_FILE"
  return $rc
}
for who in P Q R S; do
  HEX[$who]=$(amy_as "$who" init | jq -r .hex)
  amy_as "$who" relay add "$RELAY_URL" >/dev/null
  amy_as "$who" relay publish-lists >/dev/null
  info "amy $who = ${HEX[$who]}"
done

# --- Armada identities (one driver process each) -----------------------------
INTEROP_DIR="$ARMADA_DIR/src/__interop__"
mkdir -p "$INTEROP_DIR"
cp "$SCRIPT_DIR/armada-driver.test.tsx" "$SCRIPT_DIR/armadaCommands.tsx" "$INTEROP_DIR/"
start_armada() {
  local who="$1" port="$2"
  local sk; sk=$(node -e 'console.log(require("crypto").randomBytes(32).toString("hex"))')
  DRIVER_PORT[$who]="$port"
  ( cd "$ARMADA_DIR" && ARMADA_SK="$sk" RELAY="$RELAY_URL" RELAY_SOCKET="$RELAY_SOCKET" DRIVER_HOST="$DRIVER_HOST" DRIVER_PORT="$port" \
      DRIVER_LOG="$LOG_DIR/armada-$who.log" DRIVER_READY="$STATE_DIR/armada-$who.ready" \
      ARMADA_TEST_WORKERS=1 VITE_CONFIG_NATIVE_IGNORE_WARNING=true \
      exec npx vitest run src/__interop__/armada-driver.test.tsx --reporter=dot ) \
      >"$LOG_DIR/armada-$who.vitest.log" 2>&1 &
  PIDS+=("$!")
}
start_armada X "$DRIVER_BASE_PORT"
start_armada Y "$((DRIVER_BASE_PORT + 1))"
for who in X Y; do
  for _ in $(seq 120); do [[ -s "$STATE_DIR/armada-$who.ready" ]] && break; sleep 1; done
  [[ -s "$STATE_DIR/armada-$who.ready" ]] || { fail_msg "Armada driver $who never came up (see $LOG_DIR/armada-$who.vitest.log)"; tail -30 "$LOG_DIR/armada-$who.vitest.log" >&2; exit 1; }
  HEX[$who]=$(head -1 "$STATE_DIR/armada-$who.ready")
  info "Armada $who = ${HEX[$who]}"
done

# arm WHO CMD [JSON] → the command's JSON; rc 1 when it answered ok:false.
arm_raw() { curl -s --noproxy '*' --max-time 240 -H 'content-type: application/json' --data-binary "${3:-{\}}" "http://$DRIVER_HOST:${DRIVER_PORT[$1]}/$2"; }
arm() {
  local out; out=$(arm_raw "$@")
  printf '$ armada[%s] %s %s\n  -> %s\n' "$1" "$2" "${3:-}" "$(printf '%s' "$out" | head -c 2000)" >>"$LOG_FILE"
  printf '%s' "$out"
  [[ "$(printf '%s' "$out" | jq -r '.ok // false' 2>/dev/null)" == "true" ]]
}
for who in X Y; do arm "$who" publish-relay-lists >/dev/null; done

# --- results -----------------------------------------------------------------
# result ID pass|fail|gap "evidence"
result() {
  local id="$1" st="$2" ev="$3"
  case "$st" in
    pass) pass_msg "$id — $ev" ;;
    fail) fail_msg "$id — $ev" ;;
    gap)  skip_msg "$id — GAP: $ev" ;;
  esac
  record_result "$id" "$st" "$ev"
  printf '%s\t%s\t%s\n' "$id" "$st" "$ev" >>"$EVIDENCE_FILE"
}
# check ID "evidence on pass" "evidence on fail" — reads the test's exit status.
check() { if [[ $1 -eq 0 ]]; then result "$2" pass "$3"; else result "$2" fail "$4"; fi; }
err_of() { grep -v '^Picked up' "$LAST_ERR" | tail -1 | head -c 300; }
# Poll an amy read until a jq predicate holds (amy reads are one-shot).
amy_until() {
  local who="$1" pred="$2" tries="$3"; shift 3
  local out
  for _ in $(seq "$tries"); do
    out=$(amy_as "$who" "$@") && printf '%s' "$out" | jq -e "$pred" >/dev/null 2>&1 && { printf '%s' "$out"; return 0; }
    sleep 2
  done
  printf '%s' "$out"; return 1
}
general_of() { printf '%s' "$1" | jq -r '.channels[] | select(.name=="general") | .id'; }
msg_id() { printf '%s' "$1" | jq -r --arg t "$2" '[.messages[] | select(.content==$t) | .id // .event_id][0] // empty'; }

A="" B="" BCH=general

# =============================================================================
# a. Armada creates → invites amy → amy accepts → same channels + metadata
# =============================================================================
scenario_a() {
  banner "a. Armada creates, amy joins"
  local out rc
  out=$(arm X create '{"name":"Armada Harbor"}'); rc=$?
  A=$(printf '%s' "$out" | jq -r '.communityId // empty')
  check $rc a1-armada-create "community ${A:0:12}" "create failed: $out"
  [[ -n "$A" ]] || return
  local aview; aview=$(arm X channels "{\"cid\":\"$A\",\"wait\":[\"general\"]}")
  info "Armada view: $(printf '%s' "$aview" | jq -c '{ranked, channels: [.channels[]|{name,private}]}')"

  arm X dm-invite "{\"cid\":\"$A\",\"to\":\"${HEX[P]}\"}" >/dev/null
  out=$(amy_until P ".invites[]? | select(.community_id==\"$A\")" 10 concord invites); rc=$?
  local wrap; wrap=$(printf '%s' "$out" | jq -r ".invites[] | select(.community_id==\"$A\") | .wrap_id" 2>/dev/null | head -1)
  check $rc a2-amy-sees-direct-invite "wrap ${wrap:0:12} from Armada, name $(printf '%s' "$out" | jq -c ".invites[]|select(.community_id==\"$A\")|.name" 2>/dev/null)" "no invite in amy inbox: $(printf '%s' "$out" | head -c 300)"
  out=$(amy_as P concord accept "$wrap"); rc=$?
  check $rc a3-amy-accepts-direct-invite "$(printf '%s' "$out" | jq -c '{name,guestbook_join}' 2>/dev/null)" "accept failed: $(err_of)"

  local pview; pview=$(amy_as P concord channels "$A")
  local an pn ag pg
  an=$(printf '%s' "$aview" | jq -r .metadata.name); pn=$(printf '%s' "$pview" | jq -r .name)
  ag=$(general_of "$aview"); pg=$(general_of "$pview")
  [[ -n "$ag" && "$an" == "$pn" && "$ag" == "$pg" ]]
  check $? a4-same-metadata-and-general "name '$pn', #general ${pg:0:12} on both" "Armada name='$an' general=$ag; amy name='$pn' general=$pg"
  # The channels a keyless member can read are the same set on both sides.
  local apub ppub
  apub=$(printf '%s' "$aview" | jq -c '[.channels[]|select(.private==false)|.id]|sort')
  ppub=$(printf '%s' "$pview" | jq -c '[.channels[]|select(.readable==true)|.id]|sort')
  # Non-empty: two failed views would both read as "" and compare equal.
  [[ -n "$apub" && "$apub" == "$ppub" ]]
  check $? a5-same-readable-channels "public channels $apub" "Armada public=$apub, amy readable=$ppub"
  local apriv ppriv
  apriv=$(printf '%s' "$aview" | jq -c '[.channels[]|select(.private)|.id]|sort')
  ppriv=$(printf '%s' "$pview" | jq -c '[.channels[]|select(.private)|.id]|sort')
  [[ -n "$apriv" && "$apriv" == "$ppriv" ]]
  check $? a6-same-private-channel-ids "private (sealed to amy) $ppriv" "Armada private=$apriv, amy private=$ppriv"

  # Link path: Armada's link, amy Q joins it.
  out=$(arm X link "{\"cid\":\"$A\"}"); rc=$?
  local url; url=$(printf '%s' "$out" | jq -r '.url // empty')
  check $rc a7-armada-mints-link "${url:0:60}…" "createLink failed: $out"
  out=$(amy_as Q concord join "$url"); rc=$?
  [[ $rc -eq 0 && "$(printf '%s' "$out" | jq -r .community_id)" == "$A" ]]
  check $? a8-amy-joins-armada-link "$(printf '%s' "$out" | jq -c '{community_id: .community_id[0:12], name}' 2>/dev/null)" "join failed: $(err_of) $out"
  LINK_A="$url"
  # Y (Armada) joins by link too, as an Armada observer that isn't the owner.
  arm Y join "{\"url\":\"$url\"}" >/dev/null || warn "Armada Y could not join A by link"
  # Both amy members show up in the owner's member list (Guestbook Joins).
  out=$(arm X members "{\"cid\":\"$A\",\"wait\":\"${HEX[Q]}\"}" && arm X members "{\"cid\":\"$A\",\"wait\":\"${HEX[P]}\"}"); rc=$?
  check $rc a9-armada-sees-amy-joins "P and Q in Armada's member list" "missing: $(printf '%s' "$out" | tail -c 300)"
}

# =============================================================================
# b. messages both ways; edits / reactions
# =============================================================================
scenario_b() {
  banner "b. messages both ways (community A)"
  [[ -n "$A" ]] || { result b0 fail "no community A"; return; }
  local out rc
  arm X send "{\"cid\":\"$A\",\"ch\":\"general\",\"text\":\"b1 hello from armada\"}" >/dev/null
  out=$(amy_until P '.messages | map(.content) | index("b1 hello from armada")' 10 concord read "$A" general); rc=$?
  check $rc b1-armada-to-amy "amy P reads 'b1 hello from armada' (author ${HEX[X]:0:8})" "amy read: $(printf '%s' "$out" | jq -c '[.messages[].content]' 2>/dev/null)"
  amy_as P concord send "$A" general "b2 hello from amy" >/dev/null
  out=$(arm X read "{\"cid\":\"$A\",\"ch\":\"general\",\"wait\":[\"b2 hello from amy\"]}"); rc=$?
  local au; au=$(printf '%s' "$out" | jq -r '.messages[]? | select(.content=="b2 hello from amy") | .author' | head -1)
  [[ $rc -eq 0 && "$au" == "${HEX[P]}" ]]
  check $? b2-amy-to-armada "Armada X reads 'b2 hello from amy' by ${au:0:8}" "Armada read: $(printf '%s' "$out" | head -c 300)"
  amy_as Q concord send "$A" general "b3 link joiner speaks" >/dev/null
  out=$(arm Y read "{\"cid\":\"$A\",\"ch\":\"general\",\"wait\":[\"b3 link joiner speaks\",\"b1 hello from armada\"]}"); rc=$?
  check $rc b3-link-joiner-to-armada-member "Armada Y (non-owner) reads amy Q and Armada X" "Armada Y read: $(printf '%s' "$out" | head -c 300)"

  # Edits / reactions / deletes, both ways: each client's verbs, read through the other's fold.
  out=$(arm X read "{\"cid\":\"$A\",\"ch\":\"general\",\"wait\":[\"b2 hello from amy\"]}")
  local target; target=$(msg_id "$out" "b2 hello from amy")
  local own; own=$(msg_id "$out" "b1 hello from armada")
  arm X react "{\"cid\":\"$A\",\"ch\":\"general\",\"target\":\"$target\",\"emoji\":\"+\"}" >/dev/null
  arm X edit "{\"cid\":\"$A\",\"ch\":\"general\",\"target\":\"$own\",\"text\":\"b1 hello from armada (edited)\"}" >/dev/null
  out=$(arm Y read "{\"cid\":\"$A\",\"ch\":\"general\",\"wait\":[\"(edited)\"]}"); rc=$?
  check $rc b4-armada-edit-armada-reader "Armada Y sees the edit (control: Armada↔Armada)" "$(printf '%s' "$out" | head -c 300)"
  out=$(amy_until P "[.messages[] | select(.event_id == \"$own\")][0] | .content == \"b1 hello from armada (edited)\" and .edited" 10 concord read "$A" general); rc=$?
  check $rc b5-armada-edit-to-amy "amy P reads $(printf '%s' "$out" | jq -c --arg own "$own" '[.messages[] | select(.event_id == $own) | {content, edited}][0]' 2>/dev/null)" "amy read: $(printf '%s' "$out" | jq -c '[.messages[] | {content, edited}]' 2>/dev/null)"
  local tally; tally=$(printf '%s' "$out" | jq -c --arg t "$target" '[.messages[] | select(.event_id == $t) | .reactions][0]' 2>/dev/null)
  [[ "$(printf '%s' "$tally" | jq -r '.["👍"] // 0')" == "1" ]]
  check $? b6-armada-reaction-to-amy "amy P tallies $tally on its own message" "amy tallies '$tally'"

  amy_as P concord edit "$A" general "$target" "b2 hello from amy (edited)" >/dev/null
  out=$(arm X read "{\"cid\":\"$A\",\"ch\":\"general\",\"wait\":[\"b2 hello from amy (edited)\"]}"); rc=$?
  [[ $rc -eq 0 && "$(printf '%s' "$out" | jq -r --arg t "$target" '[.messages[] | select(.id == $t) | .edited][0]')" == "true" ]]
  check $? b7-amy-edit-to-armada "Armada X reads amy's edit, marked edited" "$(printf '%s' "$out" | jq -c '[.messages[] | {content, edited}]' 2>/dev/null)"
  local react; react=$(amy_as P concord react "$A" general "$own" "🤙" | jq -r '.rumor_id // empty')
  out=$(arm X read "{\"cid\":\"$A\",\"ch\":\"general\",\"settleMs\":6000}")
  tally=$(printf '%s' "$out" | jq -c --arg o "$own" '[.messages[] | select(.id == $o) | .reactions][0]' 2>/dev/null)
  [[ -n "$react" && "$(printf '%s' "$tally" | jq -r '.["🤙"] // 0')" == "1" ]]
  check $? b8-amy-reaction-to-armada "Armada X tallies $tally on its message" "react ${react:-failed}: Armada tallies '$tally'"
  amy_as P concord delete "$A" general "$react" >/dev/null
  out=$(arm X read "{\"cid\":\"$A\",\"ch\":\"general\",\"settleMs\":6000}")
  tally=$(printf '%s' "$out" | jq -c --arg o "$own" '[.messages[] | select(.id == $o) | .reactions][0]' 2>/dev/null)
  [[ "$(printf '%s' "$tally" | jq -r '.["🤙"] // 0')" == "0" ]]
  check $? b9-amy-unreact-to-armada "Armada X drops the withdrawn 🤙: $tally" "Armada still tallies '$tally'"
  local doomed; doomed=$(amy_as P concord send "$A" general "b10 about to delete" | jq -r '.rumor_id // empty')
  arm X read "{\"cid\":\"$A\",\"ch\":\"general\",\"wait\":[\"b10 about to delete\"]}" >/dev/null
  amy_as P concord delete "$A" general "$doomed" >/dev/null
  out=$(arm X read "{\"cid\":\"$A\",\"ch\":\"general\",\"absent\":[\"b10 about to delete\"],\"settleMs\":6000}")
  [[ -n "$doomed" && "$(printf '%s' "$out" | jq -r '.leaked | length')" == "0" ]]
  check $? b10-amy-delete-to-armada "Armada X no longer shows amy's deleted message" "Armada still shows: $(printf '%s' "$out" | jq -c .leaked)"
  arm Y send "{\"cid\":\"$A\",\"ch\":\"general\",\"text\":\"b11 armada deletes this\"}" >/dev/null
  out=$(amy_until P '.messages | map(.content) | index("b11 armada deletes this")' 10 concord read "$A" general)
  local gone; gone=$(arm Y read "{\"cid\":\"$A\",\"ch\":\"general\",\"wait\":[\"b11 armada deletes this\"]}" | jq -r '[.messages[] | select(.content == "b11 armada deletes this") | .id][0] // empty')
  arm Y delete "{\"cid\":\"$A\",\"ch\":\"general\",\"target\":\"$gone\"}" >/dev/null
  out=$(amy_until P '.messages | map(.content) | index("b11 armada deletes this") | not' 10 concord read "$A" general); rc=$?
  check $rc b11-armada-delete-to-amy "amy P drops Armada Y's deleted message" "amy still reads it: $(printf '%s' "$out" | jq -c '[.messages[].content]' 2>/dev/null)"
}

# =============================================================================
# c. amy creates → invites Armada → Armada joins → messages both ways
# =============================================================================
scenario_c() {
  banner "c. amy creates, Armada joins"
  local out rc
  out=$(amy_as P concord create --name "Amy Hall" --about "made by amy" --relay "$RELAY_URL"); rc=$?
  B=$(printf '%s' "$out" | jq -r '.community_id // empty')
  check $rc c1-amy-create "community ${B:0:12}" "create failed: $(err_of)"
  [[ -n "$B" ]] || return
  out=$(amy_as P concord invite "$B" --to "${HEX[Y]}"); rc=$?
  local relays; relays=$(printf '%s' "$out" | jq -c '.published_to' 2>/dev/null)
  [[ $rc -eq 0 && "$relays" == *"$RELAY_NAME"* ]]
  check $? c2-amy-direct-invite-to-armada "sent to $relays (Y's 10050)" "invite: $(err_of) $out"
  out=$(arm Y invites "{\"wait\":\"$B\"}"); rc=$?
  check $rc c3-armada-sees-direct-invite "$(printf '%s' "$out" | jq -c ".invites[]|select(.community_id==\"$B\")|{name,sender:.sender[0:8]}" 2>/dev/null)" "Armada inbox: $(printf '%s' "$out" | head -c 300)"
  out=$(arm Y accept "{\"cid\":\"$B\"}"); rc=$?
  check $rc c4-armada-accepts "$(printf '%s' "$out" | jq -c '{name}' 2>/dev/null)" "$(printf '%s' "$out" | head -c 300)"
  local yview pview
  yview=$(arm Y channels "{\"cid\":\"$B\",\"wait\":[\"general\"]}"); rc=$?
  pview=$(amy_as P concord channels "$B")
  [[ $rc -eq 0 && "$(general_of "$yview")" == "$(general_of "$pview")" && "$(printf '%s' "$yview" | jq -r .metadata.name)" == "Amy Hall" ]]
  local c5=$?
  [[ $c5 -ne 0 ]] && info "Armada Y opens B's channel editions as: $(arm Y editions "{\"cid\":\"$B\",\"vsk\":\"2\"}" | jq -c '[.editions[]?|{vsk,content}]')"
  check $c5 c5-same-metadata-and-general "Armada Y: $(printf '%s' "$yview" | jq -c '{name: .metadata.name, description: .metadata.description, ranked, channels: [.channels[].name]}' 2>/dev/null)" "Armada: $(printf '%s' "$yview" | head -c 300) / amy: $(printf '%s' "$pview" | head -c 200)"
  # Metadata alone, without waiting on #general (c5 covers that).
  yview=$(arm Y channels "{\"cid\":\"$B\"}")
  info "Armada Y view of B: $(printf '%s' "$yview" | jq -c '{metadata, ranked, channels}' 2>/dev/null)"
  [[ "$(printf '%s' "$yview" | jq -r .metadata.description)" == "made by amy" ]]
  check $? c6-description "description 'made by amy' on Armada" "Armada description: $(printf '%s' "$yview" | jq -c .metadata.description)"

  out=$(amy_as P concord invite "$B" --base "https://armada.example/"); rc=$?
  local url; url=$(printf '%s' "$out" | jq -r '.url // .link // empty')
  check $rc c7-amy-mints-link "${url:0:60}…" "invite link: $(err_of) $out"
  LINK_B="$url"
  out=$(arm X join "{\"url\":\"$url\"}"); rc=$?
  check $rc c8-armada-joins-amy-link "$(printf '%s' "$out" | jq -c '{name, preview_name}' 2>/dev/null)" "$(printf '%s' "$out" | head -c 300)"
  amy_as Q concord join "$url" >/dev/null || warn "amy Q could not join B by link: $(err_of)"

  amy_as P concord send "$B" general "c9 hello from amy owner" >/dev/null
  out=$(arm Y read "{\"cid\":\"$B\",\"ch\":\"general\",\"wait\":[\"c9 hello from amy owner\"]}"); rc=$?
  check $rc c9-amy-to-armada "Armada Y reads the amy owner's message" "$(printf '%s' "$out" | head -c 300)"
  arm Y send "{\"cid\":\"$B\",\"ch\":\"general\",\"text\":\"c10 hello from armada guest\"}" >/dev/null
  arm X send "{\"cid\":\"$B\",\"ch\":\"general\",\"text\":\"c10 hello from armada link joiner\"}" >/dev/null
  out=$(amy_until P '(.messages|map(.content)) as $m | ($m|index("c10 hello from armada guest")) and ($m|index("c10 hello from armada link joiner"))' 10 concord read "$B" general); rc=$?
  check $rc c10-armada-to-amy "amy P reads both Armada members" "amy read: $(printf '%s' "$out" | jq -c '[.messages[].content]' 2>/dev/null)"
  # A moderator hide (CORD-03 §3) from amy, the owner: every reader drops the Armada member's message.
  local guest; guest=$(printf '%s' "$out" | jq -r '[.messages[] | select(.content == "c10 hello from armada guest") | .event_id][0] // empty')
  amy_as P concord hide "$B" general "$guest" >/dev/null; rc=$?
  out=$(arm X read "{\"cid\":\"$B\",\"ch\":\"general\",\"absent\":[\"c10 hello from armada guest\"],\"wait\":[\"c10 hello from armada link joiner\"]}")
  [[ $rc -eq 0 && -n "$guest" && "$(printf '%s' "$out" | jq -r '.leaked | length')" == "0" ]]
  check $? c10b-amy-owner-hide-to-armada "Armada X drops the hidden message, keeps the other" "hide rc=$rc ($(err_of)); Armada X still shows: $(printf '%s' "$out" | jq -c '.leaked' 2>/dev/null)"
  out=$(amy_as P concord roles "$B")
  info "amy B roster: $(printf '%s' "$out" | jq -c . | head -c 400)"

  # A Private Channel amy opens to both Armada members: its key travels by Direct Invite
  # (a catch-up bundle, CORD-05 §6), and its edition carries "private": true. Later B checks
  # run here as well as in #general.
  out=$(amy_as P concord channel create "$B" ops --private --role Ops); rc=$?
  local opsrole; opsrole=$(printf '%s' "$out" | jq -r '.access_role_id // .role_id // empty')
  check $rc c11-amy-private-channel-ops "ops ${opsrole:+role ${opsrole:0:12}}" "$(err_of)"
  amy_as P concord grant "$B" "${HEX[Y]}" "$opsrole" >/dev/null || warn "grant Ops → Y: $(err_of)"
  amy_as P concord grant "$B" "${HEX[X]}" "$opsrole" >/dev/null || warn "grant Ops → X: $(err_of)"
  local who
  for who in X Y; do
    out=$(arm "$who" invites "{\"wait\":\"$B\"}")
    info "Armada $who inbox: $(printf '%s' "$out" | jq -c '.invites' 2>/dev/null)"
    arm "$who" accept "{\"cid\":\"$B\"}" >/dev/null
  done
  out=$(arm Y channels "{\"cid\":\"$B\",\"wait\":[\"ops\"]}"); rc=$?
  check $rc c12-armada-gets-ops-key "Armada Y folds $(printf '%s' "$out" | jq -c '[.channels[]|{name,private}]' 2>/dev/null)" "$(printf '%s' "$out" | head -c 300)"
  amy_as P concord send "$B" ops "c13 ops from amy" >/dev/null
  out=$(arm X read "{\"cid\":\"$B\",\"ch\":\"ops\",\"wait\":[\"c13 ops from amy\"]}"); rc=$?
  check $rc c13-amy-to-armada-private "Armada X reads amy in ops" "$(printf '%s' "$out" | head -c 300)"
  arm Y send "{\"cid\":\"$B\",\"ch\":\"ops\",\"text\":\"c14 ops from armada\"}" >/dev/null
  out=$(amy_until P '.messages|map(.content)|index("c14 ops from armada")' 10 concord read "$B" ops); rc=$?
  check $rc c14-armada-to-amy-private "amy P reads Armada Y in ops" "$(printf '%s' "$out" | jq -c '[.messages[]?.content]' 2>/dev/null) $(err_of)"
}

# =============================================================================
# d. roles
# =============================================================================
ROLE_MODS="" ROLE_HELP=""
scenario_d() {
  banner "d. roles (Roles v2 on A, legacy on B)"
  local out rc
  if [[ -n "$A" ]]; then
    # Armada (v2): a ranked Role with KICK+BAN+PIN (+ MANAGE_ROLES, so amy can place one under it), granted to amy P.
    out=$(arm X create-role "{\"cid\":\"$A\",\"name\":\"Mods\",\"perms\":[\"KICK\",\"BAN\",\"PIN_MESSAGES\",\"MANAGE_MESSAGES\",\"MANAGE_ROLES\"],\"grant\":\"${HEX[P]}\"}"); rc=$?
    ROLE_MODS=$(printf '%s' "$out" | jq -r '.role_id // empty')
    check $rc d1-armada-v2-role-and-grant "role Mods ${ROLE_MODS:0:12} granted to amy P" "$(printf '%s' "$out" | head -c 300)"
    local av pv
    av=$(arm X roles "{\"cid\":\"$A\",\"waitGrant\":\"${HEX[P]}\"}")
    pv=$(amy_until P ".grants[]? | select(.member==\"${HEX[P]}\")" 10 concord roles "$A"); rc=$?
    info "Armada: $(printf '%s' "$av" | jq -c '{ranked, roles, grants}' | head -c 600)"
    info "amy:    $(printf '%s' "$pv" | jq -c '{roles, grants}' | head -c 600)"
    local ap pp
    ap=$(printf '%s' "$av" | jq -r '.roles[]|select(.name=="Mods")|.bits')
    pp=$(printf '%s' "$pv" | jq -r '.roles[]|select(.name=="Mods")|.permissions|tostring')
    [[ $rc -eq 0 && -n "$ap" && "$ap" == "$pp" ]]
    check $? d2-amy-reads-v2-role "Mods permissions $pp on both" "Armada bits=$ap amy permissions=$pp"
    local pr; pr=$(printf '%s' "$pv" | jq -c "[.grants[]|select(.member==\"${HEX[P]}\")|.roles[]]")
    [[ "$pr" == '["Mods"]' ]]
    check $? d3-amy-reads-v2-grant "amy folds P holding $pr" "amy grants for P: $pr"
    # Rank order agrees: Mods is the only Role, P ranks above roleless Q on both.
    local arank prank
    arank=$(printf '%s' "$av" | jq -r "[.grants[]|select(.member==\"${HEX[P]}\")|.position][0]")
    prank=$(printf '%s' "$pv" | jq -r "[.grants[]|select(.member==\"${HEX[P]}\")|.rank][0]")
    info "P's rank: Armada position=$arank, amy rank=$prank"
    # amy defines a Roles v2 Role (vsk 13, keyed by rank under P's own Mods): Armada folds it.
    out=$(amy_as P concord role "$A" Probe 2 KICK); rc=$?
    local seen=""
    if [[ $rc -eq 0 ]]; then
      for _ in $(seq 10); do
        seen=$(arm X roles "{\"cid\":\"$A\"}" | jq -c '[.roles[] | select(.name == "Probe") | {permissions, rank}][0] // empty')
        [[ -n "$seen" ]] && break
        sleep 2
      done
    fi
    [[ $rc -eq 0 && -n "$seen" ]]
    check $? d4-amy-defines-v2-role "Armada folds amy's Probe role $seen" "amy role rc=$rc ($(err_of)); Armada roles: $(arm X roles "{\"cid\":\"$A\"}" | jq -c '[.roles[].name]' 2>/dev/null)"
    # Armada judges amy P's authority the way amy does.
    out=$(arm X authorized "{\"cid\":\"$A\",\"who\":\"${HEX[P]}\",\"perm\":\"BAN\"}")
    [[ "$(printf '%s' "$out" | jq -r .authorized)" == "true" ]]
    check $? d5-armada-judges-amy-ban-authority "Armada: P holds BAN" "$(printf '%s' "$out")"
  fi
  if [[ -n "$B" ]]; then
    # amy (legacy vsk 1): a positioned Role with KICK+PIN, granted to Armada X.
    # (amy concord grant takes one ROLE-ID and REPLACES the member's Grant, so this also
    # takes Ops from X: amy rotates ops to Y alone, which Armada Y must follow.)
    out=$(amy_as P concord role "$B" Helpers 1 KICK PIN_MESSAGES MANAGE_MESSAGES); rc=$?
    ROLE_HELP=$(printf '%s' "$out" | jq -r '.role_id // empty')
    check $rc d6-amy-legacy-role "role Helpers ${ROLE_HELP:0:12} at position 1" "$(err_of)"
    out=$(amy_as P concord grant "$B" "${HEX[X]}" "$ROLE_HELP"); rc=$?
    check $rc d7-amy-grant-to-armada "granted Helpers to Armada X" "$(err_of)"
    result d7b-amy-multi-role-grant gap "amy concord grant takes one ROLE-ID and replaces the member's whole Grant (ConcordModCommands.grant: roleIds = listOf(roleId)); Armada's setMemberRoles takes a list"
    local yv pv
    yv=$(arm Y roles "{\"cid\":\"$B\",\"waitGrant\":\"${HEX[X]}\"}"); rc=$?
    pv=$(amy_as P concord roles "$B")
    info "Armada Y: $(printf '%s' "$yv" | jq -c '{ranked, roles, grants}' | head -c 600)"
    info "amy P:    $(printf '%s' "$pv" | jq -c '{roles, grants}' | head -c 600)"
    local yb pb
    yb=$(printf '%s' "$yv" | jq -r '.roles[]|select(.name=="Helpers")|.bits')
    pb=$(printf '%s' "$pv" | jq -r '.roles[]|select(.name=="Helpers")|.permissions|tostring')
    [[ $rc -eq 0 && -n "$yb" && "$yb" == "$pb" ]]
    local d8=$?
    [[ $d8 -ne 0 ]] && info "Armada Y opens B's Role editions as: $(arm Y editions "{\"cid\":\"$B\",\"vsk\":\"1\"}" | jq -c '[.editions[]?|{vsk,content}]')"
    check $d8 d8-armada-reads-legacy-role "Helpers permissions $pb on both" "Armada bits=$yb amy=$pb"
    local yr; yr=$(printf '%s' "$yv" | jq -c "[.grants[]|select(.member==\"${HEX[X]}\")|.roles[]]")
    [[ "$yr" == '["Helpers"]' ]]
    check $? d9-armada-reads-legacy-grant "Armada folds X holding $yr (position $(printf '%s' "$yv" | jq -r "[.grants[]|select(.member==\"${HEX[X]}\")|.position][0]"))" "Armada grants for X: $yr"
    out=$(arm Y authorized "{\"cid\":\"$B\",\"who\":\"${HEX[X]}\",\"perm\":\"KICK\"}")
    local yk; yk=$(printf '%s' "$out" | jq -r .authorized)
    out=$(arm Y authorized "{\"cid\":\"$B\",\"who\":\"${HEX[X]}\",\"perm\":\"BAN\"}")
    local yban; yban=$(printf '%s' "$out" | jq -r .authorized)
    [[ "$yk" == true && "$yban" == false ]]
    check $? d10-armada-legacy-permissions "Armada: X may KICK, may not BAN" "KICK=$yk BAN=$yban"
    # The Grant above took Ops from X, so amy rotated ops to Y alone (CORD-03/06).
    amy_as P concord send "$B" ops "d11 ops after the rotation" >/dev/null
    out=$(arm Y read "{\"cid\":\"$B\",\"ch\":\"ops\",\"wait\":[\"d11 ops after the rotation\"]}"); rc=$?
    check $rc d11-armada-follows-amy-channel-rotation "Armada Y reads ops at channel epoch $(printf '%s' "$out" | jq -r .epoch 2>/dev/null)" "$(printf '%s' "$out" | head -c 300)"
    out=$(arm X read "{\"cid\":\"$B\",\"ch\":\"ops\",\"absent\":[\"d11 ops after the rotation\"],\"settleMs\":8000}"); rc=$?
    # Read and nothing leaked, or the channel never opened for X: any other failure (a dead driver,
    # a timeout elsewhere) is no evidence of the cut. Pinned as e9 does.
    [[ ( $rc -eq 0 && "$(printf '%s' "$out" | jq -r '.leaked|length')" == 0 ) || "$(printf '%s' "$out" | jq -r .error)" == "timed out waiting for channel ops" ]]
    check $? d12-armada-cut-by-amy-channel-rotation "Armada X no longer reads ops ($(printf '%s' "$out" | jq -r '.error // ("epoch " + (.epoch|tostring))' 2>/dev/null))" "Armada X read it: $(printf '%s' "$out" | head -c 300)"
  fi
}

# =============================================================================
# e. moderation
# =============================================================================
scenario_e() {
  banner "e. moderation"
  local out rc
  # B's working channel: #general when Armada folds it, else amy's private "ops" (see c5).
  BCH=general
  if [[ -n "$B" ]] && ! arm Y channels "{\"cid\":\"$B\"}" | jq -e '.channels[]?|select(.name=="general")' >/dev/null; then
    BCH=ops; warn "Armada folds no #general in amy's community (c5); B checks below run in the private channel ops"
  fi
  if [[ -n "$B" ]]; then
    # e1: Armada X (Helpers, KICK, granted by amy) kicks amy Q from amy's community.
    out=$(arm X kick "{\"cid\":\"$B\",\"target\":\"${HEX[Q]}\"}"); rc=$?
    check $rc e1-armada-kicks-in-amy-community "Armada X published the Kick" "$(printf '%s' "$out" | head -c 300)"
    if [[ $rc -eq 0 ]]; then
      out=$(amy_until Q 'false' 1 concord read "$B" general); rc=$?
      local qerr; qerr=$(err_of)
      [[ "$qerr" == *kick* ]]
      check $? e2-amy-honors-armada-kick "amy Q told: $qerr" "amy Q still reads B (rc=$rc): $(printf '%s' "$out" | head -c 200) $qerr"
      out=$(arm Y members "{\"cid\":\"$B\",\"gone\":\"${HEX[Q]}\"}"); rc=$?
      check $rc e3-armada-member-list-drops-kicked "Q gone from Armada Y's member list" "$(printf '%s' "$out" | head -c 300)"
    else
      result e2-amy-honors-armada-kick fail "blocked: no Kick was published (e1, Armada drops amy's Helpers Role, d8)"
    fi

    # e4: amy owner bans Armada X and Refounds; Armada Y must follow, X must not read on.
    out=$(amy_as P concord ban "$B" "${HEX[X]}"); rc=$?
    check $rc e4-amy-bans-armada "amy P banned X" "$(err_of)"
    out=$(arm Y roles "{\"cid\":\"$B\",\"waitBanned\":\"${HEX[X]}\"}"); rc=$?
    check $rc e5-armada-honors-amy-ban "Armada Y's fold: banned $(printf '%s' "$out" | jq -c '.banned|map(.[0:8])' 2>/dev/null)" "$(printf '%s' "$out" | head -c 300)"
    out=$(amy_as P concord refound "$B" --remove "${HEX[X]}"); rc=$?
    check $rc e6-amy-refounds "$(printf '%s' "$out" | jq -c '{new_epoch: (.new_epoch // .epoch), recipients: (.recipients // .blobs // null)}' 2>/dev/null)" "$(err_of)"
    out=$(arm Y epoch "{\"cid\":\"$B\",\"atLeast\":1}"); rc=$?
    check $rc e7-armada-follows-amy-refounding "Armada Y now at epoch $(printf '%s' "$out" | jq -r .epoch 2>/dev/null)" "$(printf '%s' "$out" | head -c 300)"
    amy_as P concord send "$B" "$BCH" "e8 after the refounding" >/dev/null
    out=$(arm Y read "{\"cid\":\"$B\",\"ch\":\"$BCH\",\"wait\":[\"e8 after the refounding\"]}"); rc=$?
    check $rc e8-armada-reads-post-refound "Armada Y reads epoch-1 message" "$(printf '%s' "$out" | head -c 300)"
    out=$(arm X read "{\"cid\":\"$B\",\"ch\":\"$BCH\",\"absent\":[\"e8 after the refounding\"],\"settleMs\":10000}"); rc=$?
    [[ ( $rc -eq 0 && "$(printf '%s' "$out" | jq -r '.leaked|length')" == 0 ) || "$(printf '%s' "$out" | jq -r .error)" == "timed out waiting for channel $BCH" ]]
    check $? e9-banned-armada-cut-off "Armada X (banned) cannot read it: $(printf '%s' "$out" | jq -r '.error // ("epoch " + (.epoch|tostring))' 2>/dev/null)" "$(printf '%s' "$out" | head -c 300)"
    arm Y send "{\"cid\":\"$B\",\"ch\":\"$BCH\",\"text\":\"e10 armada at epoch 1\"}" >/dev/null
    out=$(amy_until P '.messages|map(.content)|index("e10 armada at epoch 1")' 10 concord read "$B" "$BCH"); rc=$?
    check $rc e10-amy-reads-armada-post-refound "amy P reads Armada Y at epoch $(printf '%s' "$out" | jq -r .epoch 2>/dev/null)" "$(printf '%s' "$out" | jq -c '{epoch, m: [.messages[].content]}' 2>/dev/null)"
  fi
  if [[ -n "$A" ]]; then
    # e11: amy P (Mods: KICK+BAN, granted by Armada) bans Armada Y in Armada's community.
    out=$(amy_as P concord ban "$A" "${HEX[Y]}"); rc=$?
    check $rc e11-amy-bans-in-armada-community "amy P (Mods) published the ban" "$(err_of)"
    out=$(arm X roles "{\"cid\":\"$A\",\"waitBanned\":\"${HEX[Y]}\"}"); rc=$?
    check $rc e12-armada-honors-amy-moderator-ban "Armada X's fold bans Y on amy P's authority" "$(printf '%s' "$out" | head -c 300)"
    # e13: Armada owner bans amy Q: Banlist + Grant strip + Refounding (useModeration.ban).
    out=$(arm X ban "{\"cid\":\"$A\",\"target\":\"${HEX[Q]}\"}"); rc=$?
    check $rc e13-armada-bans-amy "phases $(printf '%s' "$out" | jq -c .phases 2>/dev/null), epoch $(printf '%s' "$out" | jq -r .epoch 2>/dev/null)" "$(printf '%s' "$out" | head -c 300)"
    out=$(amy_until P ".banned | index(\"${HEX[Q]}\")" 10 concord roles "$A"); rc=$?
    check $rc e14-amy-honors-armada-ban "amy P's fold bans Q" "$(printf '%s' "$out" | jq -c .banned 2>/dev/null)"
    local xe; xe=$(arm X epoch "{\"cid\":\"$A\"}" | jq -r .epoch)
    out=$(amy_as P concord rekey "$A"); rc=$?
    # `rekey` exits 0 whatever it found (`rekeyed:false`, `no_blob_for_us`), so the verdict is the
    # adoption itself, at the epoch Armada moved to.
    local pr pe
    pr=$(printf '%s' "$out" | jq -r ".communities[]|select(.community_id==\"$A\")|.rekeyed" 2>/dev/null)
    pe=$(printf '%s' "$out" | jq -r ".communities[]|select(.community_id==\"$A\")|.root_epoch // empty" 2>/dev/null)
    info "amy P rekey: $(printf '%s' "$out" | head -c 400)"
    out2=$(amy_as P concord channels "$A")
    [[ $rc -eq 0 && -n "$xe" && "$xe" -ge 1 && "$pr" == true && "$pe" == "$xe" ]]
    check $? e15-amy-follows-armada-refounding "Armada at epoch $xe; amy rekey: $(printf '%s' "$out" | jq -c . 2>/dev/null | head -c 200)" "Armada epoch $xe; amy rekeyed=$pr at epoch $pe, rc=$rc $(err_of) $out"
    arm X send "{\"cid\":\"$A\",\"ch\":\"general\",\"text\":\"e16 after armada refounding\"}" >/dev/null
    out=$(amy_until P '.messages|map(.content)|index("e16 after armada refounding")' 10 concord read "$A" general); rc=$?
    check $rc e16-amy-reads-post-refound "amy P reads Armada's epoch-$(printf '%s' "$out" | jq -r .epoch 2>/dev/null) message" "$(printf '%s' "$out" | jq -c '{epoch, m: [.messages[].content]}' 2>/dev/null) $(err_of)"
    amy_as Q concord rekey "$A" >/dev/null
    out=$(amy_as Q concord read "$A" general); rc=$?
    ! printf '%s' "$out" | jq -e '.messages|map(.content)|index("e16 after armada refounding")' >/dev/null 2>&1
    check $? e17-banned-amy-cut-off "amy Q (banned) does not read it (epoch $(printf '%s' "$out" | jq -r .epoch 2>/dev/null), $(err_of | head -c 100))" "amy Q read the post-ban message: $(printf '%s' "$out" | head -c 300)"
    amy_as P concord send "$A" general "e18 amy at the new epoch" >/dev/null
    out=$(arm X read "{\"cid\":\"$A\",\"ch\":\"general\",\"wait\":[\"e18 amy at the new epoch\"]}"); rc=$?
    check $rc e18-armada-reads-amy-post-refound "Armada X reads amy P at epoch $(printf '%s' "$out" | jq -r .epoch 2>/dev/null)" "$(printf '%s' "$out" | head -c 300)"
    # Unban from Armada; amy folds it.
    out=$(arm X unban "{\"cid\":\"$A\",\"target\":\"${HEX[Q]}\"}"); rc=$?
    out=$(amy_until P ".banned | index(\"${HEX[Q]}\") | not" 10 concord roles "$A"); rc2=$?
    [[ $rc -eq 0 && $rc2 -eq 0 ]]
    check $? e19-armada-unban-honored-by-amy "amy P's banlist: $(printf '%s' "$out" | jq -c '.banned|map(.[0:8])' 2>/dev/null)" "unban rc=$rc; amy banlist $(printf '%s' "$out" | jq -c .banned 2>/dev/null)"
  fi
}

# =============================================================================
# f. private channels, pins, timer, late joiner, recover, dissolve
# =============================================================================
scenario_f() {
  banner "f. private channels, pins, timer, recover, dissolve"
  local out rc
  if [[ -n "$B" ]]; then
    # amy private channel + access role granted to Armada Y: amy vends the key by Direct Invite.
    out=$(amy_as P concord channel create "$B" vault --private --role Vault); rc=$?
    local vrole; vrole=$(printf '%s' "$out" | jq -r '.role_id // .access_role_id // empty')
    check $rc f1-amy-private-channel "vault $(printf '%s' "$out" | jq -c '{channel_id: (.channel_id // .id)[0:12], role: (.role_id // .access_role_id)[0:12]}' 2>/dev/null)" "$(err_of)"
    out=$(amy_as P concord grant "$B" "${HEX[Y]}" "$vrole"); rc=$?
    info "grant Vault → Y: $(printf '%s' "$out" | jq -c . | head -c 400)"
    amy_as P concord send "$B" vault "f2 secret from amy" >/dev/null
    out=$(arm Y invites "{\"wait\":\"$B\"}")
    info "Armada Y inbox: $(printf '%s' "$out" | jq -c .invites)"
    if printf '%s' "$out" | jq -e ".invites[]|select(.community_id==\"$B\")" >/dev/null; then arm Y accept "{\"cid\":\"$B\"}" >/dev/null; fi
    out=$(arm Y read "{\"cid\":\"$B\",\"ch\":\"vault\",\"wait\":[\"f2 secret from amy\"]}"); rc=$?
    check $rc f2-armada-reads-amy-private-channel "Armada Y reads vault after the grant" "$(printf '%s' "$out" | head -c 300)"
    arm Y send "{\"cid\":\"$B\",\"ch\":\"vault\",\"text\":\"f3 secret reply from armada\"}" >/dev/null
    out=$(amy_until P '.messages|map(.content)|index("f3 secret reply from armada")' 10 concord read "$B" vault); rc=$?
    check $rc f3-amy-reads-armada-private-reply "amy P reads Armada Y in vault" "$(printf '%s' "$out" | jq -c '[.messages[]?.content]' 2>/dev/null) $(err_of)"

    # Pins: amy owner pins; Armada Y sees the pin. Armada Y (Helpers has PIN_MESSAGES) pins; amy sees.
    # Pins in vault (amy's grant of Vault replaced Y's Ops, so Y now reads vault, not ops).
    local PCH=vault
    [[ "$BCH" == general ]] && PCH=general
    out=$(amy_as P concord read "$B" "$PCH")
    local mid; mid=$(printf '%s' "$out" | jq -r '[.messages[]|select(.content=="f2 secret from amy" or .content=="e8 after the refounding")|.event_id][0] // [.messages[-1].event_id][0]')
    out=$(amy_as P concord pin "$B" "$PCH" "$mid"); rc=$?
    check $rc f4-amy-pins "pinned ${mid:0:12}" "$(err_of)"
    out=$(arm Y pins "{\"cid\":\"$B\",\"ch\":\"$PCH\",\"wait\":\"$mid\"}"); rc=$?
    check $rc f5-armada-sees-amy-pin "Armada Y verifies pin $(printf '%s' "$out" | jq -c '[.pins[]?.content]' 2>/dev/null)" "$(printf '%s' "$out" | head -c 300)"
    # Helpers went to X, whom e4 banned, and amy's grant replaces a member's whole Grant, so
    # Y holds only Vault here. Make Y staff with Helpers (PIN_MESSAGES): amy's grant carries the
    # Control write key for a member it makes staff (CORD-04 §3), which Armada needs to pin.
    # Y loses Vault with it, so the pin goes in #general, on Y's epoch-1 message.
    if [[ -n "${ROLE_HELP:-}" ]]; then
      out=$(amy_as P concord grant "$B" "${HEX[Y]}" "$ROLE_HELP"); rc=$?
      [[ $rc -eq 0 ]] || warn "amy could not grant Helpers to Y: $(err_of)"
      printf 'grant Helpers -> Y: %s\n' "$out" >>"$LOG_FILE"
      # Wait until Armada has folded the new Grant (Y already held Vault, so waitGrant can't tell).
      local tries=0 yp=false
      while (( tries < 30 )); do
        yp=$(arm Y authorized "{\"cid\":\"$B\",\"who\":\"${HEX[Y]}\",\"perm\":\"PIN_MESSAGES\"}" | jq -r .authorized)
        [[ "$yp" == true ]] && break
        sleep 2; tries=$((tries + 1))
      done
      info "Armada judges Y PIN_MESSAGES=$yp after $((tries * 2))s"
    fi
    local YCH="$BCH" ymsg="e10 armada at epoch 1"
    out=$(arm Y read "{\"cid\":\"$B\",\"ch\":\"$YCH\",\"wait\":[\"$ymsg\"]}")
    local yid; yid=$(msg_id "$out" "$ymsg")
    # Armada adopts the Grant's control_wrap asynchronously (useStaffKeyWatch), so retry while it
    # still says it holds no write key.
    local ptries=0
    while :; do
      out=$(arm Y pin "{\"cid\":\"$B\",\"ch\":\"$YCH\",\"target\":\"$yid\"}"); rc=$?
      [[ $rc -eq 0 ]] && printf '%s' "$out" | jq -e '.ok == true' >/dev/null 2>&1 && break
      printf '%s' "$out" | grep -q "write key" || break
      (( ptries >= 15 )) && break
      sleep 2; ptries=$((ptries + 1))
    done
    check $rc f6-armada-pins-in-amy-community "Armada Y (Helpers, PIN_MESSAGES) pinned ${yid:0:12}" "$(printf '%s' "$out" | head -c 300)"
    out=$(amy_until P ".pins? // .entries? // [] | map(.rumor_id // .id // .event_id) | index(\"$yid\")" 10 concord pins "$B" "$YCH"); rc=$?
    check $rc f7-amy-sees-armada-pin "amy P's verified Pin List holds it: $(printf '%s' "$out" | jq -c '[(.pins // .entries // [])[]|(.content // .rumor_id // .id)]' 2>/dev/null | head -c 200)" "amy pins: $(printf '%s' "$out" | head -c 300) $(err_of)"

    # Timer: amy sets 1d; Armada folds it.
    out=$(amy_as P concord timer "$B" 1d); rc=$?
    out=$(arm Y channels "{\"cid\":\"$B\"}")
    local yt; yt=$(printf '%s' "$out" | jq -r .metadata.message_expiration)
    for _ in 1 2 3 4 5; do [[ "$yt" == 86400 ]] && break; sleep 3; yt=$(arm Y channels "{\"cid\":\"$B\"}" | jq -r .metadata.message_expiration); done
    [[ "$yt" == 86400 ]]
    check $? f8-amy-timer-to-armada "Armada Y reads message_expiration=$yt" "Armada Y message_expiration=$yt"
  fi
  if [[ -n "$A" ]]; then
    # Timer: Armada sets 1w; amy prints it.
    arm X timer "{\"cid\":\"$A\",\"seconds\":604800}" >/dev/null
    out=$(amy_until P '(.seconds // .message_expiration // .timer) == 604800' 10 concord timer "$A"); rc=$?
    check $rc f9-armada-timer-to-amy "amy P: $(printf '%s' "$out" | jq -c . 2>/dev/null | head -c 120)" "amy timer: $(printf '%s' "$out" | head -c 200) $(err_of)"
    # Pins: Armada owner pins amy P's message; amy reads the Pin List.
    out=$(arm X read "{\"cid\":\"$A\",\"ch\":\"general\",\"wait\":[\"e18 amy at the new epoch\"]}")
    local pid; pid=$(msg_id "$out" "e18 amy at the new epoch")
    out=$(arm X pin "{\"cid\":\"$A\",\"ch\":\"general\",\"target\":\"$pid\"}"); rc=$?
    out=$(amy_until P ".pins? // .entries? // [] | map(.rumor_id // .id // .event_id) | index(\"$pid\")" 10 concord pins "$A" general); rc2=$?
    [[ $rc -eq 0 && $rc2 -eq 0 ]]
    check $? f10-armada-pin-to-amy "amy P verifies Armada's pin of ${pid:0:12}" "pin rc=$rc; amy pins: $(printf '%s' "$out" | head -c 300) $(err_of)"
    # Private channel on Roles v2: Armada's starter #private, opened to a Role granted to amy P.
    out=$(arm X create-role "{\"cid\":\"$A\",\"name\":\"Insiders\",\"perms\":[]}"); rc=$?
    local ins; ins=$(printf '%s' "$out" | jq -r '.role_id // empty')
    arm X overwrite "{\"cid\":\"$A\",\"ch\":\"private\",\"role\":\"$ins\",\"allow\":[\"VIEW_CHANNEL\",\"SEND_MESSAGES\",\"READ_MESSAGE_HISTORY\"]}" >/dev/null || warn "overwrite failed"
    out=$(arm X grant "{\"cid\":\"$A\",\"member\":\"${HEX[P]}\",\"roleIds\":[\"$ROLE_MODS\",\"$ins\"]}"); rc=$?
    info "grant Insiders → P: $(printf '%s' "$out" | head -c 200)"
    arm X send "{\"cid\":\"$A\",\"ch\":\"private\",\"text\":\"f11 armada private\"}" >/dev/null
    # By id: the channel's name is sealed under its key (CORD-03 §2), so amy may not list it by name.
    local privid; privid=$(arm X channels "{\"cid\":\"$A\"}" | jq -r '.channels[]|select(.name=="private")|.id')
    local got=1
    for _ in $(seq 12); do
      out=$(amy_as P concord invites)
      local w; w=$(printf '%s' "$out" | jq -r ".invites[]?|select(.community_id==\"$A\")|.wrap_id" | head -1)
      [[ -n "$w" ]] && amy_as P concord accept "$w" >/dev/null
      amy_as P concord rekey "$A" >/dev/null
      out=$(amy_as P concord read "$A" "$privid") && printf '%s' "$out" | jq -e '.messages|map(.content)|index("f11 armada private")' >/dev/null && { got=0; break; }
      sleep 3
    done
    local pname; pname=$(amy_as P concord channels "$A" | jq -r ".channels[]|select(.id==\"$privid\")|.name")
    if [[ "$pname" == private ]]; then result f11b-amy-opens-sealed-channel-name pass "amy shows the sealed name 'private'"
    else result f11b-amy-opens-sealed-channel-name gap "amy lists ${privid:0:12} as '$pname': the CLI's ConcordActions.foldCommunity folds without channel keys, so a sealed name (CORD-03 §2) never opens (the app's ConcordCommunitySession passes them)"; fi
    check $got f11-amy-reads-armada-v2-private-channel "amy P reads #private (${privid:0:12}) after Armada's role+overwrite" "amy: $(printf '%s' "$out" | head -c 200) $(err_of); channels: $(amy_as P concord channels "$A" | jq -c '[.channels[]|{name,private,readable}]')"

    # Joiners after a ban's Refounding. Armada deliberately leaves the refounder's links on the
    # cut epoch (useRekey.ts "NOT when this rotation EXCLUDED somebody", CORD-06 §3: a stale
    # bundle "vends the superseded root, so whoever joins through it lands on a dead epoch").
    if [[ -n "${LINK_A:-}" ]]; then
      out=$(amy_as R concord join "$LINK_A"); rc=$?
      out=$(amy_as R concord read "$A" general); rc2=$?
      local repoch; repoch=$(printf '%s' "$out" | jq -r '.epoch // empty')
      [[ $rc -eq 0 && "$repoch" == 0 ]] && ! printf '%s' "$out" | jq -e '.messages|map(.content)|index("e18 amy at the new epoch")' >/dev/null
      check $? f12-old-link-after-ban-lands-on-dead-epoch "amy R via the pre-ban link sits at epoch $repoch and reads nothing new (as Armada intends)" "join rc=$rc $(err_of); read: $(printf '%s' "$out" | head -c 200)"
    fi
    out=$(arm X link "{\"cid\":\"$A\"}")
    local url2; url2=$(printf '%s' "$out" | jq -r '.url // empty')
    out=$(amy_as S concord join "$url2"); rc=$?
    out=$(amy_until S '.messages|map(.content)|index("e18 amy at the new epoch")' 5 concord read "$A" general); rc2=$?
    [[ $rc -eq 0 && $rc2 -eq 0 ]]
    check $? f12b-new-link-after-refound "amy S via a post-ban link reads epoch $(printf '%s' "$out" | jq -r .epoch 2>/dev/null) history" "join rc=$rc $(err_of); read: $(printf '%s' "$out" | head -c 200)"
    out=$(amy_as P concord recover "$A"); rc=$?
    check $rc f13-amy-recover-after-armada-refound "$(printf '%s' "$out" | jq -c . 2>/dev/null | head -c 200)" "$(err_of) $out"
  fi
  if [[ -n "$B" ]]; then
    out=$(amy_as P concord dissolve "$B" --yes); rc=$?
    out2=$(arm Y dissolved "{\"cid\":\"$B\",\"wait\":true}"); rc2=$?
    [[ $rc -eq 0 && $rc2 -eq 0 ]]
    check $? f14-amy-dissolve-to-armada "Armada Y sees the tombstone at $(printf '%s' "$out2" | jq -r .dissolved_at 2>/dev/null)" "dissolve rc=$rc $(err_of); Armada: $(printf '%s' "$out2" | head -c 200)"
  fi
  if [[ -n "$A" ]]; then
    out=$(arm X dissolve "{\"cid\":\"$A\"}"); rc=$?
    out2=$(amy_until P '.dissolved == true' 10 concord channels "$A"); rc2=$?
    [[ $rc -eq 0 && $rc2 -eq 0 ]]
    check $? f15-armada-dissolve-to-amy "amy P reads dissolved=true" "dissolve: $(printf '%s' "$out" | head -c 200); amy: $(printf '%s' "$out2" | head -c 200)"
  fi
}

want a && scenario_a
want b && scenario_b
want c && scenario_c
want d && scenario_d
want e && scenario_e
want f && scenario_f

print_summary
GAPS=$(awk -F'\t' '$2=="gap"' "$RESULTS_FILE" | wc -l)
FAILS=$(awk -F'\t' '$2=="fail"' "$RESULTS_FILE" | wc -l)
printf '  gaps: %d   evidence: %s\n  Armada %s, amy %s\n\n' "$GAPS" "$EVIDENCE_FILE" "$ARMADA_COMMIT" "$AMY_COMMIT"
[[ "$FAILS" -eq 0 ]]
