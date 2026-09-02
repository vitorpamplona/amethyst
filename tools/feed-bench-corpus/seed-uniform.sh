#!/usr/bin/env bash
# Uniform corpus: every card identical in structure so a fixed-distance scroll
# crosses the same number of cards regardless of which notes loaded or in what
# order. That is what the real corpus could not give us -- the app persists no
# events, so each launch re-downloads and renders a different subset.
#
# Deliberately NOT zero-reaction: the previous synthetic corpus pinned heights but
# left every count at 0, so SlidingAnimationCount rendered nothing and the code
# under test never constructed. Every note here gets identical real reactions.
set -uo pipefail
ROOT=/Users/vitor/Documents/workspace/Amethyst
SP=/private/tmp/claude-501/-Users-vitor-Documents-workspace-Amethyst/c1db9f48-dfa5-437b-acc2-a33fcb5fe512/scratchpad
AMY="$ROOT/cli/build/install/amy/bin/amy"
DB="$SP/uniform.db"
RELAY="ws://127.0.0.1:7447"
NOTES=${NOTES:-60}
# Notes must be RECENT. The home feed only surfaces recent notes: seeded at a
# fixed 2025 timestamp, 59 of 60 notes never appeared and the feed rendered a
# single card. Spread them over the last hour instead.
NOW=$(date +%s)
BASE=$((NOW - 3600))
amy() { HOME="$SP/amyhome" "$AMY" "$@"; }

# same picture for every author => one cached bitmap, identical avatar path per card
PIC="https://robohash.org/benchshared.png"
# Each body must be UNIQUE but the SAME LENGTH. Byte-identical content is
# collapsed by the app's duplicate/spam filter -- 60 identical notes rendered as
# exactly ONE card. A fixed-width numeric suffix keeps every card the same height
# while making the content distinct.
BODY="alpha bravo charlie delta echo foxtrot golf hotel india juliet kilo lima mike november oscar papa quebec romeo sierra tango"

# Kill whatever holds the port, not just the relay we expect: a previous run can
# leave a relay bound to 7447 serving a different db, and then every publish
# silently goes nowhere (the symptom is "FAILED to parse id at note 0").
echo "stopping any relay on 7447…"
for pid in $(lsof -tiTCP:7447 -sTCP:LISTEN 2>/dev/null); do kill "$pid" 2>/dev/null; done
sleep 4
if lsof -tiTCP:7447 -sTCP:LISTEN >/dev/null 2>&1; then echo "port 7447 still busy, aborting"; exit 1; fi
rm -f "$DB"*
echo "starting relay on $RELAY (db: uniform.db)"
amy --account bench1 serve --db "$DB" --port 7447 > "$SP/uniform-relay.log" 2>&1 &
echo $! > "$SP/uniform-relay.pid"; sleep 10

declare -a PK
for n in 1 2 3 4 5; do
  PK[$n]=$(amy --account "bench$n" whoami 2>/dev/null | awk '/^hex:/{print $2}')
  amy --account "bench$n" event --kind 0 --created-at $((BASE - 100 + n)) \
      --content "{\"name\":\"Bench $n\",\"picture\":\"$PIC\"}" --relay "$RELAY" >/dev/null 2>&1
done
echo "authors seeded: ${PK[1]:0:8}… ${PK[5]:0:8}…"

for i in $(seq 0 $((NOTES-1))); do
  a=$(( i % 5 + 1 ))
  out=$(amy --account "bench$a" event --kind 1 --created-at $((BASE + i*50)) \
        --content "$BODY $(printf '%04d' $i)" --relay "$RELAY" 2>/dev/null)
  id=$(printf '%s' "$out" | grep -oE '[0-9a-f]{64}' | head -1)
  [ -z "$id" ] && { echo "FAILED to parse id at note $i"; printf '%s\n' "$out" | head -5; exit 1; }
  # identical engagement on every note: 2 likes + 1 repost
  for r in 1 2; do
    b=$(( (i + r) % 5 + 1 ))
    # fixed old timestamp: engagement must not reorder the feed or create
    # "now" cards at the top on every seeding run
    amy --account "bench$b" event --kind 7 --content "+" --created-at $((BASE + i*50 - 10*r)) \
      --tags "[[\"e\",\"$id\"],[\"p\",\"${PK[$a]}\"]]" --relay "$RELAY" >/dev/null 2>&1
  done
  # NO kind-6 reposts. A repost is its own feed entry with a different card shape,
  # and with an empty content it renders "Event is loading or can't be found in
  # your relay list" (NIP-18 wants the reposted event JSON inline). Either way it
  # breaks the constant-height property this corpus exists to provide. Cost: boost
  # counts stay 0, so only the like counter exercises SlidingAnimationCount.
[ $((i % 10)) -eq 0 ] && echo "  note $i/$NOTES"
done
echo "DONE: $(amy --account bench1 count --kind 1 --relay "$RELAY" --timeout 15 2>/dev/null | awk '/total/{print $2}') notes"
