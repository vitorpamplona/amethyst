#!/usr/bin/env bash
# Fixed-corpus relay rig for feed rendering benchmarks.
#
# Why: FeedScrollBenchmark's numbers are only comparable if every run renders the
# same notes. Against a live Global feed they do not — measured drift between two
# identical baseline runs reached 73-97% on avatar metrics and moved DrawReactions
# from 2.78 to 1.70 with no code change, because card heights (how many cards a
# fixed-distance scroll composes) and the pictureless-author mix (robohash vectors
# are expensive to draw) both follow whatever the firehose happened to deliver.
#
# This serves a frozen corpus instead. NOTE: a *synthetic* corpus (uniform generated
# notes) pins card heights beautifully but is useless for anything involving reaction
# counts — with every count at zero, SlidingAnimationCount renders nothing and the
# AnimatedContent under test is never constructed. Capture real events instead:
#
#   amy --account benchN fetch --kind 7,6,9735 --limit 600 --relay <real relays> --json
#   -> collect the e-tagged note ids -> fetch those notes -> fetch their kind 0
#   -> republish all of it into the local relay
#
# Reactions first, then the notes they point at: notes fetched directly are usually
# too fresh to have any engagement.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${WORK:-/tmp/amethyst-feed-corpus}"
AMY="$ROOT/cli/build/install/amy/bin/amy"
RELAY="ws://127.0.0.1:7447"
PORT=7447

mkdir -p "$WORK"
export HOME_ORIG="$HOME"
amy() { HOME="$WORK/amyhome" "$AMY" "$@"; }

if [ ! -x "$AMY" ]; then
  echo "building amy…"; (cd "$ROOT" && ./gradlew -q :cli:installDist)
fi

# Throwaway identities, isolated from the developer's real ~/.amy, with the
# plaintext backend so nothing prompts the macOS keychain.
if [ ! -d "$WORK/amyhome" ]; then
  mkdir -p "$WORK/amyhome"
  for n in 1 2 3 4 5; do
    amy --account "bench$n" --secret-backend plaintext init >/dev/null
  done
fi

echo "starting relay on $RELAY (db: $WORK/corpus.db)"
amy --account bench1 serve --db "$WORK/corpus.db" --port "$PORT" > "$WORK/relay.log" 2>&1 &
echo $! > "$WORK/relay.pid"
sleep 10

if [ ! -f "$WORK/.seeded" ]; then
  echo "seeding corpus…"
  # Three authors with pictures, two without: pins how many avatars fall back to a
  # generated robohash, which is the dominant swing in DrawAuthor.
  for n in 1 2 3; do
    amy --account "bench$n" event --kind 0 --created-at "$((1750000000 + n))" \
      --content "{\"name\":\"Bench $n\",\"picture\":\"https://robohash.org/bench$n.png\"}" \
      --relay "$RELAY" >/dev/null
  done
  for n in 4 5; do
    amy --account "bench$n" event --kind 0 --created-at "$((1750000000 + n))" \
      --content "{\"name\":\"Bench $n\"}" --relay "$RELAY" >/dev/null
  done

  python3 - "$WORK" <<'PY' > "$WORK/notes.sh"
import sys
WORDS = ("alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima "
         "mike november oscar papa quebec romeo sierra tango uniform victor whiskey").split()
LENGTHS = [6, 40, 12, 90, 20, 5, 55, 15, 120, 30]   # fixed cycle => fixed card heights
for i in range(80):
    n = LENGTHS[i % len(LENGTHS)]
    body = " ".join(WORDS[(i + j) % len(WORDS)] for j in range(n))
    print(f"amy --account bench{i % 5 + 1} event --kind 1 --created-at {1751000000 + i} "
          f"--content '{body}' --relay $RELAY >/dev/null")
PY
  . "$WORK/notes.sh"
  touch "$WORK/.seeded"
fi

echo "corpus: $(amy --account bench1 count --kind 1 --relay "$RELAY" --timeout 15 | awk '/total/{print $2}') notes"

for SERIAL in $(adb devices | awk '/device$/{print $1}'); do
  adb -s "$SERIAL" reverse tcp:$PORT tcp:$PORT && echo "adb reverse ready on $SERIAL"
done

cat <<'EOF'

Final step (once per device, by hand):

  Amethyst > drawer > Relays > "Local Relays"  ->  add  ws://127.0.0.1:7447 > Save

It MUST go in the "Local Relays" section ("relays running on this device").
That list is device-local. Adding the relay to "Public Outbox/Home Relays"
instead does not stick: the app re-fetches the account's kind-10002 from the
network and a loopback URL does not survive the round trip, so the entry silently
disappears within a minute. `Account.outboxHomeRelays()` unions nip65 +
privateStorage + localRelayList, so a Local Relay is used for reading all the
same.

Two automation traps if you try to script this:
  - The nav drawer and the relay settings are overlay windows `uiautomator dump`
    does not capture; it returns the feed behind them, so taps look like no-ops.
    Drive it from screenshots.
  - The relay settings screen nests a scrollable relay list that swallows
    flings, so the outer screen stops scrolling partway down.

Then the feed contains exactly the 80 corpus notes, identical on every run.
Stop the relay with:  kill $(cat "$WORK/relay.pid")
EOF
