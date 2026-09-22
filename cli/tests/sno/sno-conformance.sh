#!/usr/bin/env bash
#
# sno-conformance.sh — diffs amy's DECK-0003 reader against the cyberspace
# project's own reference implementations. No relay, no account, no device.
#
# Three comparisons, each driven by the reference's own fixtures rather than
# by anything we wrote:
#
#   1. §1.9 verdicts     — the deck's rejection table (`_rejections()` in
#                          decks/sno-reference.py) plus Appendix A, through
#                          `amy sno parse` and through the reference. The
#                          valid/invalid answer AND the rule number must agree.
#   2. Avatar work       — the golden vectors in cyberspace-cli's
#                          tests/fixtures/avatar_work.json, which §8.10 says
#                          both reference implementations are pinned to,
#                          through `amy sno work` and `avatar_work()`.
#   3. Avatar payment    — synthesised events through `amy sno verify` and
#                          `verify_avatar_work()`.
#   4. Reader divergence — the two places we knowingly differ from the §1.9
#                          arbiter, pinned so neither can drift quietly.
#
# Divergences we already know about are asserted as divergences, not ignored:
# a reader that silently stopped diverging would be just as interesting as one
# that started.
#
# Usage: ./sno-conformance.sh [--no-build]
#
# Reference checkouts are cloned into state/ unless CYBERSPACE_DIR and
# CYBERSPACE_CLI_DIR already point at them.
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-sno-conformance"
LOG_DIR="$STATE_DIR/logs"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"
DRIVER="$SCRIPT_DIR/refdriver.py"
NO_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) NO_BUILD=1 ;;
    -h|--help) sed -n '3,27p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

mkdir -p "$STATE_DIR" "$LOG_DIR"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"

command -v jq >/dev/null || { fail_msg "jq is required"; exit 1; }
command -v python3 >/dev/null || { fail_msg "python3 is required"; exit 1; }

# ---- the reference checkouts ------------------------------------------------

clone_ref() {
  local url="$1" dest="$2"
  if [[ -d "$dest/.git" ]]; then info "reusing $dest"; return 0; fi
  step "cloning $url"
  GIT_LFS_SKIP_SMUDGE=1 git clone --quiet --depth 1 "$url" "$dest" >>"$LOG_FILE" 2>&1
}

: "${CYBERSPACE_DIR:=$STATE_DIR/cyberspace}"
: "${CYBERSPACE_CLI_DIR:=$STATE_DIR/cyberspace-cli}"
export CYBERSPACE_DIR CYBERSPACE_CLI_DIR

if [[ ! -f "$CYBERSPACE_DIR/decks/sno-reference.py" ]]; then
  clone_ref https://github.com/arkin0x/cyberspace "$CYBERSPACE_DIR" || true
fi
if [[ ! -f "$CYBERSPACE_CLI_DIR/src/cyberspace_core/avatar.py" ]]; then
  clone_ref https://github.com/arkin0x/cyberspace-cli "$CYBERSPACE_CLI_DIR" || true
fi

HAVE_SNO_REF=0; [[ -f "$CYBERSPACE_DIR/decks/sno-reference.py" ]] && HAVE_SNO_REF=1
HAVE_AVATAR_REF=0; [[ -f "$CYBERSPACE_CLI_DIR/src/cyberspace_core/avatar.py" ]] && HAVE_AVATAR_REF=1

if [[ $NO_BUILD -eq 0 ]]; then
  step "building amy (installDist)"
  (cd "$REPO_ROOT" && ./gradlew -q :cli:installDist) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "gradle :cli:installDist failed"; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { fail_msg "amy not built at $AMY_BIN"; exit 1; }

amy() { "$AMY_BIN" --json "$@" 2>>"$LOG_FILE"; }
ref() { python3 "$DRIVER" "$@" 2>>"$LOG_FILE"; }

# ---- 1. §1.9 verdicts -------------------------------------------------------

banner "1. §1.9 verdicts — the deck's own rejection table"

if [[ $HAVE_SNO_REF -eq 0 ]]; then
  skip_msg "sno-reference.py unavailable"
  record_result "sno-verdicts" skip "no cyberspace checkout"
else
  AGREE=0; DISAGREE=0; DIVERGENCES=""
  while IFS= read -r line; do
    NAME="$(jq -r .name <<<"$line")"
    PAYLOAD="$(jq -c .payload <<<"$line")"

    OURS="$(printf '%s' "$PAYLOAD" | amy sno parse -)"
    THEIRS="$(printf '%s' "$PAYLOAD" | ref verdict)"

    OUR_VALID="$(jq -r '.valid' <<<"$OURS")"
    OUR_RULE="$(jq -r '.rule // "null"' <<<"$OURS")"
    THEIR_VALID="$(jq -r '.valid' <<<"$THEIRS")"
    THEIR_RULE="$(jq -r '.rule // "null"' <<<"$THEIRS")"

    if [[ "$OUR_VALID" == "$THEIR_VALID" && "$OUR_RULE" == "$THEIR_RULE" ]]; then
      AGREE=$((AGREE+1))
    else
      DISAGREE=$((DISAGREE+1))
      DIVERGENCES+="    $NAME: amy(valid=$OUR_VALID rule=$OUR_RULE) ref(valid=$THEIR_VALID rule=$THEIR_RULE)"$'\n'
    fi
  done < <(ref cases)

  info "$AGREE agreed, $DISAGREE diverged"
  [[ -n "$DIVERGENCES" ]] && printf '%s' "$DIVERGENCES" >&2
  if [[ $DISAGREE -eq 0 && $AGREE -gt 0 ]]; then
    record_result "sno-verdicts" pass "$AGREE cases agree"
  else
    record_result "sno-verdicts" fail "$DISAGREE of $((AGREE+DISAGREE)) diverged"
  fi
fi

# ---- 2. avatar work ---------------------------------------------------------

banner "2. avatar work — cyberspace-cli's golden vectors"

if [[ $HAVE_AVATAR_REF -eq 0 ]]; then
  skip_msg "cyberspace-cli unavailable"
  record_result "sno-avatar-work" skip "no cyberspace-cli checkout"
else
  AGREE=0; DISAGREE=0
  while IFS= read -r line; do
    NAME="$(jq -r .name <<<"$line")"
    EXPECTED="$(jq -r .required <<<"$line")"
    PAYLOAD="$(jq -c .payload <<<"$line")"
    THEIRS="$(printf '%s' "$PAYLOAD" | ref work | jq -r .required)"

    # The fixtures carry only the fields the formula reads, and some index
    # faces past the vertex list because it never looks at them — so dress
    # each one as a payload §1.9 accepts, keeping the counts and the reach.
    FULL="$(jq -c --argjson p "$PAYLOAD" -n '
      ($p.vertices | length) as $nv
      | {v:2, name:"vector", unit:($p.unit // 0),
         mode:(if (($p.faces // []) | length) > 0 then "solid" else "points" end),
         vertices:$p.vertices,
         colors:[range($nv) | 225],
         faces:(if $nv >= 3 then [range(($p.faces // []) | length) | [(. % $nv), ((.+1) % $nv), ((.+2) % $nv)]] else [] end)}
      + (if $p.ticks then {ticks:$p.ticks} else {} end)')"

    NV="$(jq -r '.vertices | length' <<<"$PAYLOAD")"
    NF="$(jq -r '(.faces // []) | length' <<<"$PAYLOAD")"
    if [[ "$NV" -lt 3 && "$NF" -gt 0 ]]; then
      fail_msg "$NAME: $NF faces over $NV vertices cannot be expressed as a §1.9 payload"
      DISAGREE=$((DISAGREE+1))
      continue
    fi
    OURS="$(printf '%s' "$FULL" | amy sno work | jq -r .required)"

    if [[ "$OURS" == "$THEIRS" && "$OURS" == "$EXPECTED" ]]; then
      AGREE=$((AGREE+1))
      info "$NAME: $OURS bits"
    else
      DISAGREE=$((DISAGREE+1))
      fail_msg "$NAME: amy=$OURS ref=$THEIRS fixture=$EXPECTED"
    fi
  done < <(ref vectors)

  if [[ $DISAGREE -eq 0 && $AGREE -gt 0 ]]; then
    record_result "sno-avatar-work" pass "$AGREE golden vectors agree"
  else
    record_result "sno-avatar-work" fail "$DISAGREE of $((AGREE+DISAGREE)) diverged"
  fi
fi

# ---- 3. avatar payment, and the divergences we expect -----------------------

banner "3. avatar payment — and the divergences we know about"

SHAPE='{"v":2,"name":"dot","unit":0,"mode":"points","vertices":[[0,0,0],[1,0,0]],"colors":[225,225],"faces":[]}'

# id with exactly N leading zero bits, padded to 64 hex chars
id_with_bits() {
  python3 - "$1" <<'PY'
import sys
bits = int(sys.argv[1])
head = "0" * (bits // 4) + {0: "f", 1: "4", 2: "2", 3: "1"}[bits % 4]
print(head + "f" * (64 - len(head)))
PY
}

avatar_event() {  # kind, id-bits, committed|none, content
  local kind="$1" bits="$2" committed="$3" content="$4"
  local tags="[]"
  [[ "$committed" != "none" ]] && tags="[[\"nonce\",\"1\",\"$committed\"]]"
  jq -cn --arg id "$(id_with_bits "$bits")" --argjson kind "$kind" \
         --argjson tags "$tags" --arg content "$content" \
    '{id:$id, pubkey:("11"*32), kind:$kind, created_at:0, tags:$tags, content:$content, sig:("22"*32)}'
}

check_verify() {  # label, event, expected-amy-reason, expected-ref-reason
  local label="$1" event="$2" want_ours="$3" want_theirs="$4"
  local ours theirs
  ours="$(printf '%s' "$event" | amy sno verify - | jq -r .reason)"
  if [[ $HAVE_AVATAR_REF -eq 1 ]]; then
    theirs="$(printf '%s' "$event" | ref verify | jq -r .reason)"
  else
    theirs="$want_theirs"
  fi
  if [[ "$ours" == "$want_ours" && "$theirs" == "$want_theirs" ]]; then
    record_result "$label" pass "amy=$ours ref=$theirs"
  else
    record_result "$label" fail "amy=$ours (want $want_ours) ref=$theirs (want $want_theirs)"
  fi
}

# A paid avatar. The reference wants kind 33331 — it predates the move to
# 11333 that §8.10 documents — so it refuses a conformant one outright.
check_verify "verify-paid"            "$(avatar_event 11333 16 16 "$SHAPE")"  ok              not-an-avatar
check_verify "verify-no-nonce"        "$(avatar_event 11333 32 none "$SHAPE")" no-nonce       not-an-avatar
check_verify "verify-under-committed" "$(avatar_event 11333 32 8 "$SHAPE")"   under-committed not-an-avatar

# The trap: committed 30, owes 16, id carries 20. min(20,30)=20 clears 16, so
# a reader comparing the wrong number calls this paid. Both of us refuse it.
check_verify "verify-short-of-commitment" "$(avatar_event 11333 20 30 "$SHAPE")" unpaid not-an-avatar

# Empty content is §8.10's default avatar and owes no work; the reference
# calls it not-an-avatar because empty is not JSON. Same outcome — the client
# draws its default — different word for it.
check_verify "verify-default-avatar" "$(avatar_event 11333 0 none "")" default-avatar not-an-avatar

# The reference's own kind. We refuse it as an avatar because DECK-0003 gave
# 33331 to standalone objects; it accepts it and prices it.
check_verify "verify-legacy-kind" "$(avatar_event 33331 16 16 "$SHAPE")" not-an-avatar ok

# ---- 4. the two places our reader knowingly differs ------------------------

banner "4. reader divergence — pinned, not ignored"

# These are not in the deck's rejection table, so section 1 never sees them.
# Both are deliberate and documented; a change in either direction is news.

check_verdict() {  # label, payload, expected-amy-valid, expected-ref-valid, note
  local label="$1" payload="$2" want_ours="$3" want_theirs="$4" note="$5"
  local ours theirs
  ours="$(printf '%s' "$payload" | amy sno parse - | jq -r .valid)"
  if [[ $HAVE_SNO_REF -eq 1 ]]; then
    theirs="$(printf '%s' "$payload" | ref verdict | jq -r .valid)"
  else
    theirs="$want_theirs"
  fi
  if [[ "$ours" == "$want_ours" && "$theirs" == "$want_theirs" ]]; then
    record_result "$label" pass "amy=$ours ref=$theirs — $note"
  else
    record_result "$label" fail "amy=$ours (want $want_ours) ref=$theirs (want $want_theirs)"
  fi
}

# D1. A v:2 payload whose colours are still literal triples. Version 2 shipped
# as two changes that did not land together, so these exist and are right in
# every other respect; §5 permits a reader to be generous and sno-core is.
# Three of the seven objects on the network are this shape.
TRIPLES='{"v":2,"name":"legacy","unit":0,"mode":"solid","vertices":[[0,0,0],[2,0,0],[1,0,2],[1,2,1]],"colors":[[1,0.15,0.15],[0,1,0],[0,0,1],[1,1,1]],"faces":[[0,1,2]]}'
check_verdict "divergence-v2-triples" "$TRIPLES" true false "we read the legacy colour cohort; the arbiter refuses it"

# D5. A vertex past 64 units. §1.8 puts that bound on publishers only and lets
# a reader repair instead; §8's third open question admits leaving it off
# readers is unsafe. Neither reference bounds it. We draw strangers' events in
# a feed, and whole*120 overflows an Int long before the text's limit.
FAR='{"v":2,"name":"far","unit":0,"mode":"solid","vertices":[[0,0,0],[2,0,0],[1,0,2],[9999,2,1]],"colors":[238,235,239,225],"faces":[[0,1,2]]}'
check_verdict "divergence-position-bound" "$FAR" false true "we refuse an unbounded vertex; the arbiter accepts it"

print_summary

grep -q $'\tfail\t' "$RESULTS_FILE" && exit 1
exit 0
