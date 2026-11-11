#!/usr/bin/env bash
#
# make-fixture.sh — writes fixture.json: one NIP-FA agent key list (kind 12000) and one
# NIP-FB scheduled task (kind 34817), signed by a fixed throwaway TEST key, with the
# plaintext each one's content decrypts to. For checking another client (Brainstorm) reads
# them as Amethyst does. Offline: no relay, no provider. See README.md.
#
# Usage: ./make-fixture.sh [OUT.json]   (default: fixture.json next to this script)
#
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "$SCRIPT_DIR/../../../.." && pwd)"
AMY_BIN="${AMY_BIN:-$REPO_ROOT/cli/build/install/amy/bin/amy}"
OUT="${1:-$SCRIPT_DIR/fixture.json}"

# TEST KEY ONLY. It is public in this repository: never use it for anything real, never
# send it funds, never save a real provider key under it.
TEST_NSEC="nsec1xp8snzzlg9t5n5prld8tj9eqv7p3mrz06jm7qdkv5z68wza4ltts2jzkme"
TEST_HEX="6836d50f5d7098d67b826e163b0e917671ee1f10d8fad7b2da91bfe0eb62d276"

# Fixed times: 2026-11-11 00:00 UTC, and the 07:00 New York occurrence on 2026-11-11.
CREATED_AT=1794355200
DUE=1794398400

command -v jq >/dev/null || { echo "jq is needed" >&2; exit 1; }
[[ -x "$AMY_BIN" ]] || { echo "amy not found at $AMY_BIN; build it with ./gradlew :cli:installDist" >&2; exit 1; }

HOME_DIR="$(mktemp -d)"
trap 'rm -rf "$HOME_DIR"' EXIT
amy() { HOME="$HOME_DIR" "$AMY_BIN" --account fixture --secret-backend plaintext --json "$@" 2>/dev/null; }
amy init --nsec "$TEST_NSEC" >/dev/null

# The private tags, as NIP-FA and NIP-FB put them in `content`. The keys are fake: one for a
# hosted provider (shaped so no secret scanner takes it for a real one), one empty for a local
# runner. The task carries a claim, a finished `ran` and an option (`length`) a reader must keep
# when it rewrites the task.
KEYS_PLAIN='[["key","fake-test-key-not-a-provider-key","https://openrouter.ai/api/v1"],["key","","http://localhost:11434/v1"]]'
TASK_PLAIN='[["task","observer"],["cron","0 7 * * *","America/New_York","'"$CREATED_AT"'"],["claim","'"$DUE"'","3f1c9a0e5b7d4c21","Desktop (Linux)","'"$((DUE + 31))"'"],["ran","'"$((DUE + 120))"'"],["length","short"]]'

encrypt() { amy encrypt --to "$TEST_HEX" "$1" | jq -r .ciphertext; }
KEYS_EVENT=$(amy event --kind 12000 --content "$(encrypt "$KEYS_PLAIN")" --created-at "$CREATED_AT" | jq -c .event)
TASK_EVENT=$(amy event --kind 34817 --content "$(encrypt "$TASK_PLAIN")" --tags '[["d","observer"]]' --created-at "$CREATED_AT" | jq -c .event)

# Check what was written: valid ids and signatures, and contents that decrypt back.
for event in "$KEYS_EVENT" "$TASK_EVENT"; do
  [[ "$(amy verify "$event" | jq -r .valid)" == "true" ]] || { echo "an event failed to verify" >&2; exit 1; }
done
[[ "$(amy decrypt --from "$TEST_HEX" "$(jq -r .content <<<"$KEYS_EVENT")" | jq -c '.plaintext | fromjson')" == "$(jq -c . <<<"$KEYS_PLAIN")" ]] || { echo "kind 12000 didn't decrypt back" >&2; exit 1; }
[[ "$(amy decrypt --from "$TEST_HEX" "$(jq -r .content <<<"$TASK_EVENT")" | jq -c '.plaintext | fromjson')" == "$(jq -c . <<<"$TASK_PLAIN")" ]] || { echo "kind 34817 didn't decrypt back" >&2; exit 1; }

jq -n \
  --arg nsec "$TEST_NSEC" --arg pubkey "$TEST_HEX" \
  --argjson keys "$KEYS_EVENT" --argjson task "$TASK_EVENT" \
  --argjson keys_plain "$KEYS_PLAIN" --argjson task_plain "$TASK_PLAIN" \
  '{
    note: "TEST KEY ONLY: public in the Amethyst repository; never use it for anything real.",
    test_nsec: $nsec,
    pubkey: $pubkey,
    events: [$keys, $task],
    decrypted: { "12000": $keys_plain, "34817": $task_plain },
    expect: {
      "12000": { default: { url: "https://openrouter.ai/api/v1" }, count: 2 },
      "34817": { d: "observer", task: "observer", cron: "0 7 * * *", tz: "America/New_York", set_at: ($task_plain[1][3] | tonumber),
                 paused: false, claim: { due: ($task_plain[2][1] | tonumber), id: "3f1c9a0e5b7d4c21", by: "Desktop (Linux)", finished: true } }
    }
  }' >"$OUT"
echo "wrote $OUT" >&2
