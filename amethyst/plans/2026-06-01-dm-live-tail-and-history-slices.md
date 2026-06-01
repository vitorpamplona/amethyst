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
