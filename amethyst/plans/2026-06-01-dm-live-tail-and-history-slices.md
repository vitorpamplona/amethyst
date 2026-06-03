# DM loading: live tail + bounded history slices

## Problem

The DM loaders used a single subscription whose `since` floor grew as the user
scrolled (`loadMore`: 7d → 14d → 28d → …). The filter carried **only `since`,
no `until`**, so every widen re-requested the whole window and the relay
re-streamed the entire history from the new floor. Traces showed this directly:

```
[giftwrap] load summary: 589 event(s)  (14d)
[giftwrap] load summary: 1486 event(s) (28d)
[giftwrap] load summary: 2609 event(s) (56d)
```

Each step re-downloaded everything it already had plus the new slice — the
"getting all events over and over again" the window owner reported. It also
cascaded: a few pixels of scroll walked the window to the 10-year backstop,
because widening pulls older *messages* but the rooms list is keyed by
*conversation*, so a handful of busy correspondents flood thousands of events
without adding a single new row, and the "scrolled near the oldest room" trigger
never clears.

## Design (owner's call)

Split each DM protocol into two responsibilities:

1. **Live tail** — keep the existing filters at a fixed ~1-week floor with **no
   `until`** (open to the future). Never widens. New messages always arrive.
2. **History slices** — new assemblers that load *the past* in **bounded
   `since`+`until` slices**, each fetched once. Widening fetches only the new
   band `[newFloor, previousFloor]`; the data already held in `[previousFloor,
   now]` is never re-requested.

Because consecutive slices are disjoint, re-issuing the (advanced) historical
filter does not re-stream earlier slices — they live in `LocalCache`. The
NIP-17 ±2-day wrapper-timestamp margin is applied to the slice `since` (via
`filterGiftWrapsToPubkey`), giving a 2-day overlap between adjacent slices so no
gap can open from a randomized outer timestamp. NIP-04 (kind 4) uses exact
timestamps, so its slices need no margin.

### Slice math (gift-wrap history window)

> Superseded by the two updates below — kept for the history of the design. The
> `TimeWindowPagination` class this described has been removed; the history
> managers now page by `until`+`limit` per relay (`UntilLimitPager`).

`TimeWindowPagination.since` starts at `now − 1week` (= the live-tail floor).

- `loadMore`: `until = window.since` (current floor); `window.loadMore()` moves
  `since` back geometrically; new slice = `[window.since, until]`.
- `loadEverything`: `until = window.since`; `window.loadAll()` → `since = floor`;
  slice = `[floor, until]` — one request for the remaining past.
- `updateFilter` returns the **current slice** only (or empty before the first
  `loadMore`), so the manager is idle until the user asks for older history.

### Rooms-list cascade stop (stall-gate)

The rooms list auto-fill widens only while it makes progress: it remembers the
private-room count at the last widen and stops once a widen brings in no new
private room (kept on the history manager so it survives leaving/reopening the
screen). "Fill until full **or nothing new found**", instead of walking to the
10-year backstop.

## Touch list

- `commons/.../FilterGiftWrapsToPubkey.kt`, `amethyst/.../FilterNip04DMsToMe.kt`,
  `FilterNip04DMsFromMe.kt` — add optional `until`.
- `AccountGiftWrapsEoseManager` — becomes the live tail (fixed week, no until).
- `AccountGiftWrapsHistoryEoseManager` (new) — owns the window, bounded slices,
  `loadMore`/`loadEverything`/`exhausted`, per-load instrumentation.
- `ChatroomListNip04SubAssembler` / `ChatroomNip04SubAssembler` — live tail.
- `ChatroomListNip04HistorySubAssembler` / `ChatroomNip04HistorySubAssembler`
  (new) — follow the gift-wrap history slice bounds.
- `AccountFilterAssembler`, `ChatroomListFilterAssembler`,
  `ChatroomFilterAssembler`, `RelaySubscriptionsCoordinator` — wire the new
  managers.
- `ChatroomListFeedView`, `ChatroomView` — point "load older" at the history
  managers; combine live+history `loadingMore` for spinners; add the stall-gate.

## Update: time-slices → `until`+`limit` paging

The time-slice history (above) bounded re-downloads but still couldn't tell
"this relay is empty" from "this is a gap" — an empty slice might sit above
older messages, so the only stop was the 10-year `maxLookback`, and a wide late
slice could pull a 20k-event firehose.

The history managers now page **backward by `until`+`limit`, per relay**
(`UntilLimitPager`). A relay returns up to `limit` (10000) events older than its
cursor, **skipping gaps**, so an empty page + EOSE is a gap-proof "nothing older"
signal. A relay returning fewer is treated as its own cap, not exhaustion (only
empty ends it). A relay answering CLOSED isn't "empty" (it may answer post-auth),
so the **global** exhausted flag flips only when a whole round advances no relay
at all. `limit` also caps per-request volume.

Both NIP-04 history managers now paginate themselves (per relay) instead of
following the gift-wrap slice; `loadEverything` pages to the end by auto-issuing
the next round until exhausted. The live tail and stall-gate are unchanged.

## Update 2: NIP-04 filters scoped per relay

A conversation's NIP-04 filters named the whole participant set on every relay,
so a relay that belongs to one correspondent was still asked about all of them
(`{authors:[bob,charlie]}` sent to a relay that is only charlie's), and the
`from-me` leg (`authors:[me]`) was sent to the correspondents' inbox relays —
which auth-walled relays (ditto: "all authors must be authenticated") reject
outright, stalling the load.

`Nip04DmRelays` is now two **per-relay key maps** (`relay → which keys to name
there`), built from the outbox model:

- **to me** (`#p:[me]`) — my inbox carries the whole group; each correspondent's
  outbox carries only that correspondent.
- **from me** (`authors:[me]`) — my outbox carries the whole group; each
  correspondent's inbox carries only that correspondent.

Relays shared across roles union their key sets, so a relay only ever sees the
keys that actually own it.

## Update 3: per-relay independent paging + in-stream markers (convo only)

The round model paced every relay at the slowest one: each `loadMore` issued one
page to all active relays and waited for the slowest to settle before the next.
Fast own-relays that hold the whole conversation were stuck behind a
correspondent's 15 s timeout.

`ChatroomNip04HistorySubAssembler` was rewritten to page **each relay
independently, no rounds**. A relay continues to its next page the instant *it*
EOSEs (the subscription layer diffs per relay, so re-issuing only re-REQs the
relay whose cursor moved; the others' in-flight REQs are untouched). Fast relays
race to the bottom in back-to-back pages; slow / auth-walled relays catch up at
their own pace — **none are abandoned** (they keep their subscription open and
keep trying), so every relay converges on the same window.

- A relay is **done** on an empty page; one that won't answer (auth CLOSE,
  unreachable, silent) is flagged **stalled** but kept open.
- `loadingMore` reflects "is anything still advancing"; it clears once every
  relay is done or stalled. It is exposed as a flow that **starts `false`** (not
  `windowLoad.loading`, which starts `true` and would wedge the scroll loader's
  `!loading` gate on first open), and the assembler tracks `windowActive` itself
  so the first `loadMore` actually starts the window.
- `relayProgress` (`relay → reached-back / done / stalled`) feeds **in-stream
  markers** (`RelayReachMarker`, wired through `ChatFeedView.markersInGap`): a
  thin divider per relay at the depth it has reached, sliding down as it pages
  and converging — `↓` reaching, `…` stalled, `✓` done.

The **rooms-list and gift-wrap** history managers still use the round model
(`AccountGiftWrapsHistoryEoseManager`,
`ChatroomListNip04HistorySubAssembler`) — they query only the account's own
(fast, reachable) relays, so the lock-step never bites there. Only the
conversation screen, which fans out to correspondents' relays, needed the
per-relay rewrite.

### Window completion backstops (`WindowLoadTracker`)

The shared window tracker finishes when every relay reaches a terminal signal
(EOSE / CLOSED / cannot-connect), with three backstops for misbehaving relays:
**idle** (every still-waited relay was heard from and the stream went quiet),
**silence** (a relay that got its REQ but answered nothing for 10 s), and
**connect-grace** (a relay that never even received its REQ within 15 s, stuck
connecting). The two REQ-aware backstops are gated behind `tracksReqSends`, set
only by the convo manager — without it an always-empty `reqSentAt` would make
every relay look connect-stalled and complete the window before its REQs even
went out. Silent relays are reported via `onAbandoned`; the tracker only stops
waiting, the owner decides what to do (the convo keeps them and flags stalled).

## Diagnostics

The whole path logs under one tag, **`DMPagination`** (debug builds):
`DmRelayDiagnosticsLogger` folds the per-relay connection timeline (REQ sent,
connect/disconnect, CLOSED/NOTICE/OK-fail) into it; `DmRelayLog` prints the
"relays by source" breakdown per subscription; and each assembler logs its
milestones (paging start, a relay reaching the bottom / stalling with the
reason, the settle summary of done-vs-still-trying).
