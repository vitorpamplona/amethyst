#!/usr/bin/env bash
#
# emoji-headless.sh — `amy emoji` (NIP-30 custom emoji packs) between two amy
# accounts over the embedded `amy serve` relay, with a throwaway Blossom server
# (../marmot/blossom-server.py) for the uploads. We assert that:
#
#   1. `emoji pack create` publishes a kind:30030 with the title, description
#      and image; `pack list` finds it; `--d` refuses a d tag already in use.
#   2. `emoji add` adds a public and a private emoji; a second add of the same
#      shortcode is skipped, `--replace` swaps its url in place.
#   3. `emoji import` from a folder uploads each image to Blossom and names it by
#      file (`party parrot.gif` → party_parrot, a second `smile` → smile_2),
#      skips shortcodes already in the pack without uploading them, and from a
#      .zip skips non-images and macOS metadata. `--dry-run` publishes nothing.
#      A folder over the image limit stops at it (`truncated`) and an image over
#      10 MB is reported in `too_large`.
#      A `shortcode url` text list is added without uploads.
#   4. Bob reads Alice's pack from the relay: public emojis only, no private
#      count; he can `use` it in his emoji list and `unuse` it again.
#   5. `emoji remove`, `pack edit` and `pack delete` round-trip.
#
# Usage: ./emoji-headless.sh [--no-build] [--port N]
#
set -uo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../.." && pwd)"
STATE_DIR="$SCRIPT_DIR/state-emoji-headless"
LOG_DIR="$STATE_DIR/logs"

RUN_TS="$(date +%Y%m%d-%H%M%S)"
LOG_FILE="$LOG_DIR/run-$RUN_TS.log"
RESULTS_FILE="$STATE_DIR/results-$RUN_TS.tsv"

AMY_BIN="$REPO_ROOT/cli/build/install/amy/bin/amy"
NO_BUILD=0
RELAY_HOST=127.0.0.2
RELAY_PORT=7797

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-build) NO_BUILD=1 ;;
    --port) RELAY_PORT="$2"; shift ;;
    -h|--help)
      sed -n '3,21p' "${BASH_SOURCE[0]}" | sed 's/^# \?//'
      exit 0 ;;
    *) printf 'unknown flag: %s\n' "$1" >&2; exit 2 ;;
  esac
  shift
done

RELAY_URL="ws://$RELAY_HOST:$RELAY_PORT"
RELAY_DATA="$STATE_DIR/relay"
BLOSSOM_PORT=$((RELAY_PORT + 1))
BLOSSOM_URL="http://127.0.0.1:$BLOSSOM_PORT"

rm -rf "$STATE_DIR"
mkdir -p "$STATE_DIR" "$LOG_DIR" "$STATE_DIR/alice" "$STATE_DIR/bob"
: >"$LOG_FILE"
: >"$RESULTS_FILE"

# shellcheck source=../lib.sh
source "$SCRIPT_DIR/../lib.sh"
# shellcheck source=../headless/helpers.sh
source "$SCRIPT_DIR/../headless/helpers.sh"

command -v jq >/dev/null || { fail_msg "jq is required"; exit 1; }
command -v python3 >/dev/null || { fail_msg "python3 is required"; exit 1; }

if [[ $NO_BUILD -eq 0 ]]; then
  step "building amy (installDist)"
  (cd "$REPO_ROOT" && ./gradlew -q :cli:installDist) >>"$LOG_FILE" 2>&1 \
    || { fail_msg "gradle :cli:installDist failed"; exit 1; }
fi
[[ -x "$AMY_BIN" ]] || { fail_msg "amy binary missing at $AMY_BIN"; exit 1; }

BLOSSOM_PID=""
cleanup() {
  [[ -n "$BLOSSOM_PID" ]] && kill "$BLOSSOM_PID" 2>/dev/null
  stop_local_relay
}
trap cleanup EXIT
start_local_relay

python3 "$SCRIPT_DIR/../marmot/blossom-server.py" --port "$BLOSSOM_PORT" --dir "$STATE_DIR/blobs" >>"$LOG_DIR/blossom.log" 2>&1 &
BLOSSOM_PID=$!
for _ in $(seq 40); do curl -s -o /dev/null "$BLOSSOM_URL/" && break; sleep 0.25; done

# Boolean flags go AFTER positionals: amy's parser reads `--flag VALUE`.
alice() { HOME="$STATE_DIR/alice" "$AMY_BIN" --account alice --secret-backend plaintext --json "$@" 2>>"$LOG_FILE"; }
bob() { HOME="$STATE_DIR/bob" "$AMY_BIN" --account bob --secret-backend plaintext --json "$@" 2>>"$LOG_FILE"; }

check() {
  # usage: check TEST_ID JSON JQ_EXPR [NOTE]
  local id="$1" json="$2" expr="$3" note="${4:-}"
  if jq -e "$expr" <<<"$json" >/dev/null 2>&1; then
    record_result "$id" pass "$note"
  else
    record_result "$id" fail "$(head -c 400 <<<"$json")"
  fi
}

banner "two accounts on $RELAY_URL, Blossom on $BLOSSOM_URL"
for who in alice bob; do
  "$who" init >>"$LOG_FILE" || { record_result "$who-init" fail; exit 1; }
  "$who" relay nip65 clear >>"$LOG_FILE"
  "$who" relay add "$RELAY_URL" >>"$LOG_FILE"
  "$who" relay publish-lists >>"$LOG_FILE"
done
ALICE_HEX="$(alice whoami | jq -r .hex)"
record_result "setup" pass "alice=${ALICE_HEX:0:8}"

step "create and list"
CREATE="$(alice emoji pack create --title "Cats" --description "Cat faces" --image https://example.com/cover.png --d cats)"
check create "$CREATE" '.d_tag == "cats" and .title == "Cats" and .description == "Cat faces" and (.published_to | length) == 1'
DUP="$(alice emoji pack create --title "Again" --d cats)"; DUP_RC=$?
[[ $DUP_RC -eq 1 ]] && record_result create-dup pass || record_result create-dup fail "exit $DUP_RC: $DUP"
LIST="$(alice emoji pack list)"
check list "$LIST" '.count == 1 and .packs[0].d_tag == "cats" and .packs[0].public_count == 0'

step "add, private add, conflicts"
ADD="$(alice emoji add cats grin https://example.com/grin.png)"
check add "$ADD" '.added == ["grin"] and .changed'
PADD="$(alice emoji add cats secret https://example.com/secret.png --private)"
check add-private "$PADD" '.added == ["secret"] and .private'
SKIP="$(alice emoji add cats grin https://example.com/grin2.png)"
check add-skip "$SKIP" '.changed == false and .skipped == ["grin"]'
REPL="$(alice emoji add cats grin https://example.com/grin2.png --replace)"
check add-replace "$REPL" '.replaced == ["grin"]'
BADCODE="$(alice emoji add cats 'bad code' https://example.com/x.png)"; BAD_RC=$?
[[ $BAD_RC -eq 2 ]] && record_result add-invalid pass || record_result add-invalid fail "exit $BAD_RC: $BADCODE"

step "import a folder"
IMG="$STATE_DIR/images"
mkdir -p "$IMG/more"
printf 'gif-1' >"$IMG/party parrot.gif"
printf 'png-1' >"$IMG/smile.png"
printf 'png-2' >"$IMG/more/smile.webp"
printf 'png-3' >"$IMG/grin.png"
printf 'text' >"$IMG/notes.txt"
DRY="$(alice emoji import cats "$IMG" --server "$BLOSSOM_URL" --dry-run)"
check import-dry "$DRY" '.dry_run and ([.to_upload[].shortcode] | sort) == ["party_parrot","smile","smile_2"] and .skipped == ["grin"]'
IMPORT="$(alice emoji import cats "$IMG" --server "$BLOSSOM_URL")"
check import-folder "$IMPORT" '.uploaded == 3 and (.added | sort) == ["party_parrot","smile","smile_2"] and .skipped == ["grin"] and .failed == []'

step "import limits (dry run)"
MANY="$STATE_DIR/many"
mkdir -p "$MANY"
for i in $(seq 1 2003); do printf 'x' >"$MANY/e$i.png"; done
head -c $((11 * 1024 * 1024)) /dev/zero >"$MANY/huge.png"
LIMITS="$(alice emoji import cats "$MANY" --dry-run)"
check import-limits "$LIMITS" '.truncated and (.to_upload | length) == 2000 and .too_large == ["huge.png"]'

step "import a zip"
python3 - "$STATE_DIR/pack.zip" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], "w") as z:
    z.writestr("pack/wave.png", b"wave")
    z.writestr("pack/readme.md", b"hi")
    z.writestr("__MACOSX/pack/._wave.png", b"junk")
    z.writestr("pack/thumbs-up.gif", b"thumbs")
PY
ZIP="$(alice emoji import cats "$STATE_DIR/pack.zip" --server "$BLOSSOM_URL" --private)"
check import-zip "$ZIP" '.private and .uploaded == 2 and (.added | sort) == ["thumbs-up","wave"]'
URL="$(alice emoji pack show cats | jq -r '.emojis[] | select(.shortcode == "wave") | .url')"
[[ "$(curl -s "$URL")" == "wave" ]] && record_result import-blob pass || record_result import-blob fail "blob at $URL"

step "import a text list"
printf '# extras\nheart https://example.com/heart.png\n:star: https://example.com/star.png\nnope\n' >"$STATE_DIR/list.txt"
TXT="$(alice emoji import cats "$STATE_DIR/list.txt")"
check import-list "$TXT" '.uploaded == 0 and .added == ["heart","star"] and .bad_lines == [4]'

step "show"
SHOW="$(alice emoji pack show cats)"
check show-owner "$SHOW" '.public_count == 6 and .private_count == 3 and ([.emojis[] | select(.private)] | length) == 3'

step "bob reads alice's pack and uses it"
NADDR="$(jq -r .naddr <<<"$SHOW")"
BSHOW="$(bob emoji pack show "$NADDR")"
check bob-show "$BSHOW" ".author == \"$ALICE_HEX\" and .public_count == 6 and .private_count == null and ([.emojis[] | select(.private)] | length) == 0"
BLIST="$(bob emoji pack list "$ALICE_HEX")"
check bob-list "$BLIST" '.count == 1'
USE="$(bob emoji use "$NADDR")"
check bob-use "$USE" '.changed and .op == "use"'
USE2="$(bob emoji use "$NADDR")"
check bob-use-again "$USE2" '.changed == false'
check bob-list-packs "$(bob emoji list)" "(.packs | map(.address)) == [\"30030:$ALICE_HEX:cats\"]"
check bob-unuse "$(bob emoji unuse "$NADDR")" '.changed and .op == "unuse"'
check bob-list-empty "$(bob emoji list)" '.packs == []'
BEDIT="$(bob emoji add "$NADDR" x https://example.com/x.png)"; BEDIT_RC=$?
[[ $BEDIT_RC -eq 2 ]] && record_result bob-cannot-edit pass || record_result bob-cannot-edit fail "exit $BEDIT_RC: $BEDIT"

step "remove, edit, delete"
check remove "$(alice emoji remove cats smile,smile_2,nope)" '.removed == ["smile","smile_2"] and .missing == ["nope"]'
check remove-private "$(alice emoji remove cats secret --private)" '.removed == ["secret"]'
EDIT="$(alice emoji pack edit cats --title Kittens --description "")"
check edit "$EDIT" '.title == "Kittens" and .description == null and .image == "https://example.com/cover.png" and .public_count == 4 and .private_count == 2'
DEL="$(alice emoji pack delete cats)"
check delete "$DEL" '.d_tag == "cats" and (.published_to | length) == 1'

print_summary
grep -q $'\tfail\t' "$RESULTS_FILE" && exit 1
exit 0
