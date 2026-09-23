#!/usr/bin/env bash
#
# sno-conformance.sh — diffs amy's DECK-0003 reader against the cyberspace
# project's own reference implementations. No relay, no account, no device.
#
# Eight comparisons, each driven by the reference's own fixtures rather than
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
#   4. Reader divergence — where we knowingly differ from the §1.9 arbiter,
#                          and one place we used to and no longer do, pinned so
#                          neither can drift quietly.
#   5. Region keys       — §2.2 decodes and §7.2 keys against cyberspace-cli.
#   6. Hint boxes        — §7.7's golden vectors through `amy cyberspace hint`.
#   7. Bags              — a ciphertext cyberspace-cli sealed, opened by
#                          `amy cyberspace open` from the coordinate alone.
#   8. Sweeps            — the same bag found by `amy cyberspace sweep` from
#                          its hint box, with no coordinate at all.
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
    -h|--help) sed -n '3,33p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'; exit 0 ;;
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

banner "4. reader divergence and agreement — pinned, not ignored"

# None of these are in the deck's rejection table, so section 1 never sees
# them. Each is deliberate and documented; a change in either direction is
# news, including a divergence that quietly reappears after being repealed.

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

# D5, repealed. A vertex past 64 units. §1.8 puts that bound on publishers only
# and gives a reader the choice — "MAY reject such a payload and MAY instead
# repair it by growing the extent" — and neither reference bounds it: both grow
# the extent. Amethyst rejected, which made it the only reader that refused an
# object the rest of the network drew, so it repairs now and this is pinned as
# agreement. A reader that started refusing again would be news.
FAR='{"v":2,"name":"far","unit":0,"mode":"solid","vertices":[[0,0,0],[2,0,0],[1,0,2],[9999,2,1]],"colors":[238,235,239,225],"faces":[[0,1,2]]}'
check_verdict "agreement-position-repair" "$FAR" true true "both repair a vertex past the grid rather than refusing it"

# D5b. What is left of that bound: the lattice itself. A position is a total
# tick count in an Int, so past Int.MAX_VALUE ticks — 17,895,697 units — there
# is no coordinate to repair, only one that would wrap. The reference keeps its
# positions in a double and carries this one fine, which is the divergence.
WRAP='{"v":2,"name":"wrap","unit":0,"mode":"solid","vertices":[[0,0,0],[2,0,0],[1,0,2],[17895698,2,1]],"colors":[238,235,239,225],"faces":[[0,1,2]]}'
check_verdict "divergence-lattice-limit" "$WRAP" false true "past what an Int lattice holds we refuse; the arbiter keeps it in a float"

# ---- 5. cyberspace region keys ---------------------------------------------

banner "5. region keys — CYBERSPACE_V2 §2.2 and §7.2 against cyberspace-cli"

# A region key is a consensus value: §7.2 turns it into an AES key, so a byte
# of difference is a bag they hid that we cannot open. Four coordinates from
# §9.8's golden vectors (plus §7.7's ideaspace point) at four heights each.
if [[ $HAVE_AVATAR_REF -eq 0 ]]; then
  skip_msg "cyberspace-cli unavailable"
  record_result "cyberspace-region-keys" skip "no cyberspace-cli checkout"
else
  AGREE=0; DISAGREE=0
  for COORD in \
    c492492492492492492492edf5bee7267451c787d95ba4d7840c76d1e33c9940 \
    c4924924924924924924921f79235dae293ada913e78294253a235239a332854 \
    e000000000000000000001200041040208048040000000000000000000000000 \
    a4b64924924924924924924924924924924924924924924924924d84b60d9c8f
  do
    for H in 0 1 4 8; do
      THEIRS="$(ref region "$COORD" "$H")"
      OURS="$(amy cyberspace region "$COORD" --height "$H")"
      T_KEY="$(jq -r .key <<<"$THEIRS")"; O_KEY="$(jq -r .key <<<"$OURS")"
      T_ID="$(jq -r .lookup_id <<<"$THEIRS")"; O_ID="$(jq -r .lookup_id <<<"$OURS")"
      # And the decode underneath it, which is where a wrong bit would start.
      T_XYZ="$(jq -r '"\(.x) \(.y) \(.z) \(.plane)"' <<<"$THEIRS")"
      O_XYZ="$(amy cyberspace coord "$COORD" | jq -r '"\(.x) \(.y) \(.z) \(.plane)"')"

      if [[ "$O_KEY" == "$T_KEY" && "$O_ID" == "$T_ID" && "$O_XYZ" == "$T_XYZ" ]]; then
        AGREE=$((AGREE+1))
      else
        DISAGREE=$((DISAGREE+1))
        fail_msg "${COORD:0:8}… h$H: key amy=${O_KEY:0:16} ref=${T_KEY:0:16}; xyz amy=[$O_XYZ] ref=[$T_XYZ]"
      fi
    done
  done

  if [[ $DISAGREE -eq 0 && $AGREE -gt 0 ]]; then
    record_result "cyberspace-region-keys" pass "$AGREE keys and decodes agree"
  else
    record_result "cyberspace-region-keys" fail "$DISAGREE of $((AGREE+DISAGREE)) diverged"
  fi
fi

# ---- 6. §7.7 hint boxes -----------------------------------------------------

banner "6. hint boxes — §7.7's golden vectors through amy"

# A hint is the hider's difficulty knob: the box it names decides how many
# region keys a seeker has to derive. Getting the aligned base or the sector
# rule wrong means sweeping the wrong box, which finds nothing and says so
# only after the work is done.
if [[ $HAVE_SNO_REF -eq 0 ]]; then
  skip_msg "cyberspace checkout unavailable"
  record_result "cyberspace-hints" skip "no cyberspace checkout"
else
  AGREE=0; DISAGREE=0
  while IFS= read -r line; do
    NAME="$(jq -r .name <<<"$line")"
    H="$(jq -r .bag_height <<<"$line")"
    WANT_LOG2="$(jq -r .candidates_log2 <<<"$line")"
    WANT_HINT="$(jq -c '[.tags[] | select(.[0] == "hint")][0]' <<<"$line")"
    WANT_SECTORS="$(jq -c '[.tags[] | select(.[0] != "hint")]' <<<"$line")"

    # A bag carrying exactly the tags the reference says a hider writes.
    BAG="$(jq -cn --argjson tags "$(jq -c .tags <<<"$line")" --arg h "$H" '
      {id:("a"*64), pubkey:("b"*64), created_at:1, kind:33330,
       tags:([["d",("c"*64)],["h",$h]] + $tags), content:"", sig:("0"*128)}')"

    OURS="$(printf '%s' "$BAG" | amy cyberspace hint -)"
    GOT_HINT="$(jq -c '["hint", .box, (.heights[0]|tostring), (.heights[1]|tostring), (.heights[2]|tostring)]' <<<"$OURS")"
    GOT_SECTORS="$(jq -c '.sector_tags' <<<"$OURS")"
    GOT_LOG2="$(jq -r .gap_bits <<<"$OURS")"

    if [[ "$GOT_HINT" == "$WANT_HINT" && "$GOT_SECTORS" == "$WANT_SECTORS" && "$GOT_LOG2" == "$WANT_LOG2" ]]; then
      AGREE=$((AGREE+1))
      info "$NAME: 2^$GOT_LOG2 candidates, $(jq -r '.sector_tags | length' <<<"$OURS") sector tags"
    else
      DISAGREE=$((DISAGREE+1))
      fail_msg "$NAME: hint amy=$GOT_HINT ref=$WANT_HINT; sectors amy=$GOT_SECTORS ref=$WANT_SECTORS; gap amy=$GOT_LOG2 ref=$WANT_LOG2"
    fi
  done < <(ref hints)

  if [[ $DISAGREE -eq 0 && $AGREE -gt 0 ]]; then
    record_result "cyberspace-hints" pass "$AGREE golden vectors agree"
  else
    record_result "cyberspace-hints" fail "$DISAGREE of $((AGREE+DISAGREE)) diverged"
  fi
fi

# ---- 7. §7.6 bags, round-tripped through the reference's cipher -------------

banner "7. bags — sealed by cyberspace-cli, opened by amy"

# The strongest statement this harness can make about §7: a ciphertext nobody
# here produced. cyberspace-cli derives the region key with its own modules,
# seals a plaintext with its own AES-GCM and writes the §8.6 tags with its own
# event builder; amy is handed nothing but that event and a coordinate, and has
# to arrive at the same 32 bytes to read it. Every link in §2.2 -> §4.7 -> §7.2
# -> §7.6 is inside that one assertion.
if [[ $HAVE_AVATAR_REF -eq 0 ]]; then
  skip_msg "cyberspace-cli unavailable"
  record_result "cyberspace-bags" skip "no cyberspace-cli checkout"
else
  AGREE=0; DISAGREE=0
  for COORD in \
    c492492492492492492492edf5bee7267451c787d95ba4d7840c76d1e33c9940 \
    a4b64924924924924924924924924924924924924924924924924d84b60d9c8f
  do
    for H in 0 4; do
      PLAIN="a bag at ${COORD:0:8} height $H"
      SEALED="$(printf '%s' "$PLAIN" | ref encrypt "$COORD" "$H")"
      BAG="$(jq -c .event <<<"$SEALED")"

      # Opened from the coordinate alone: our decode, our Cantor roots, our
      # §7.2 derivation, their ciphertext.
      GOT="$(printf '%s' "$BAG" | amy cyberspace open - --coord "$COORD")"
      OPENED="$(jq -r .opened <<<"$GOT")"
      TEXT="$(jq -r '.text // ""' <<<"$GOT")"

      # And the negative §7.6 insists on: the wrong key is a verdict, not an
      # error. "A failed decryption therefore means only that the reader does
      # not hold this region's key; it MUST NOT be treated as an error."
      WRONG="$(printf '%s' "$BAG" | amy cyberspace open - --key "$(printf 'ab%.0s' $(seq 32))")"
      WRONG_EXIT=$?
      WRONG_OPENED="$(jq -r '.opened' <<<"$WRONG")"

      if [[ "$OPENED" == "true" && "$TEXT" == "$PLAIN" && "$WRONG_OPENED" == "false" && $WRONG_EXIT -eq 0 ]]; then
        AGREE=$((AGREE+1))
        info "${COORD:0:8}… h$H: opened their ciphertext from the coordinate"
      else
        DISAGREE=$((DISAGREE+1))
        fail_msg "${COORD:0:8}… h$H: opened=$OPENED text='$TEXT' want='$PLAIN'; wrong-key opened=$WRONG_OPENED exit=$WRONG_EXIT"
      fi
    done
  done

  if [[ $DISAGREE -eq 0 && $AGREE -gt 0 ]]; then
    record_result "cyberspace-bags" pass "$AGREE bags sealed there, opened here"
  else
    record_result "cyberspace-bags" fail "$DISAGREE of $((AGREE+DISAGREE)) failed to round-trip"
  fi
fi

# ---- 8. §7.7 sweeps ---------------------------------------------------------

banner "8. sweeps — finding a bag from its hint box alone"

# The same bag, this time without being told where it is. amy gets the box and
# the bag's height, derives every candidate region key inside it and looks for
# the one whose lookup_id is the `d` tag the hider published — §7.7's
# position-free search, end to end. The box itself comes from the reference's
# interleave, so a disagreement about alignment shows up as a bag that is not
# in the box rather than as a test grading its own arithmetic.
if [[ $HAVE_AVATAR_REF -eq 0 ]]; then
  skip_msg "cyberspace-cli unavailable"
  record_result "cyberspace-sweeps" skip "no cyberspace-cli checkout"
else
  AGREE=0; DISAGREE=0
  COORD=c492492492492492492492edf5bee7267451c787d95ba4d7840c76d1e33c9940
  SEALED="$(printf 'found by sweeping' | ref encrypt "$COORD" 4)"
  WANT_KEY="$(jq -r .key <<<"$SEALED")"

  # A cube (gap 6), a slab with one axis pinned to the bag's own height (gap 4),
  # and the degenerate box that is a destination rather than a search (gap 0).
  for BOX in "6 6 6" "4 6 6" "4 4 4"; do
    # shellcheck disable=SC2086
    HINT="$(ref box "$COORD" $BOX | jq -c .tag)"
    BAG="$(jq -c --argjson hint "$HINT" '.event | .tags += [$hint]' <<<"$SEALED")"

    GOT="$(printf '%s' "$BAG" | amy cyberspace sweep -)"
    FOUND="$(jq -r .found <<<"$GOT")"
    GOT_KEY="$(jq -r '.key // ""' <<<"$GOT")"
    EXAMINED="$(jq -r .examined <<<"$GOT")"
    CANDIDATES="$(jq -r .candidates <<<"$GOT")"

    # A sweep that found it must not have walked past the box it was given.
    if [[ "$FOUND" == "true" && "$GOT_KEY" == "$WANT_KEY" && "$EXAMINED" -le "$CANDIDATES" ]]; then
      AGREE=$((AGREE+1))
      info "box [$BOX]: found after $EXAMINED of $CANDIDATES candidates"
    else
      DISAGREE=$((DISAGREE+1))
      fail_msg "box [$BOX]: found=$FOUND key=${GOT_KEY:0:16} want=${WANT_KEY:0:16} examined=$EXAMINED/$CANDIDATES"
    fi
  done

  # And the refusal §7.7 exists for: a hint is a stranger's choice of
  # difficulty, so a box nobody asked to pay for is quoted and declined rather
  # than swept. 2^33 keys is hours.
  HUGE="$(ref box "$COORD" 15 15 15 | jq -c .tag)"
  BAG="$(jq -c --argjson hint "$HUGE" '.event | .tags += [$hint]' <<<"$SEALED")"
  if printf '%s' "$BAG" | amy cyberspace sweep - >/dev/null 2>&1; then
    DISAGREE=$((DISAGREE+1))
    fail_msg "a gap-33 hint was swept without being asked for"
  else
    AGREE=$((AGREE+1))
    info "a gap-33 hint is quoted and declined, not swept"
  fi

  if [[ $DISAGREE -eq 0 && $AGREE -gt 0 ]]; then
    record_result "cyberspace-sweeps" pass "$AGREE boxes swept as §7.7 describes"
  else
    record_result "cyberspace-sweeps" fail "$DISAGREE of $((AGREE+DISAGREE)) diverged"
  fi
fi

print_summary

grep -q $'\tfail\t' "$RESULTS_FILE" && exit 1
exit 0
