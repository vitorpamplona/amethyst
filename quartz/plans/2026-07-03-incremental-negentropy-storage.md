# Incremental live negentropy storage

**Status: planned** — backlog item 3 of the relay performance campaign
(PR #3466 follow-up).

## Problem

A cold NEG-OPEN pays a full scan + O(n log n) seal: `snapshotIdsForNegentropy`
(SQL scan of every matching row) → `NegentropyServerSession.sealVector`
(sort + seal). relayBench measured ~340 ms per reconcile at 50k events before
the single-slot snapshot cache; strfry answers ~21 ms off its always-current
in-memory view.

The cache added in #3466 (keyed on filter JSON + write generation + 30 s TTL)
only helps the *identical-filter, zero-writes-in-between* repeat. On a relay
ingesting continuously the generation moves constantly, so mirror heartbeats
are effectively always cold; any new filter is cold by definition.

## Design

### 1. `LiveNegentropyIndex` (quartz, server-only, opt-in)

An always-current sorted set of `(createdAt, id₃₂)` maintained from the
store's write path — strfry's `MemoryView` equivalent, ~40 B/entry
(1M events ≈ 40 MB; capped by `negentropy.max_sync_events`).

- **Structure**: single sorted array with binary-search insert. Nostr inserts
  are near-tail (created_at ≈ now), so the memmove is tiny in the common
  case; measure the out-of-order (backfill) worst case and only move to a
  chunked layout if it shows.
- **Snapshot**: NEG-OPEN copies the array into a sealed `IStorage` — O(n)
  arraycopy of already-sorted data (~2 MB at 50k, sub-ms), reusing the
  existing single-slot cache so back-to-back opens share one snapshot.
  Reconcile only reads (`size/getItem/iterate/indexAtOrBeforeBound`), so one
  sealed snapshot serves any number of concurrent sessions.
- **Serves index-total filters only**: no `ids/authors/kinds/tags/search`
  constraints. `since/until` ARE served — the structure is time-sorted, so a
  time window is an index sub-range. Constrained filters keep the scan+seal
  path (+ cache). The mirror-heartbeat pattern this optimizes is a broad
  time-window filter, so this covers the case that matters.

### 2. Removal correctness (the interesting part)

The index must never advertise ids the store no longer has, or peers fetch
dead ids. Removal paths differ in frequency and get different treatment:

- **Replaceable/addressable overwrite** (frequent — every kind 0/3/1xxxx/
  3xxxx update): `ReplaceableModule`/`AddressableModule` run
  `DELETE FROM event_headers WHERE …` inside the insert transaction. Add
  `RETURNING created_at, id` (bundled SQLite ≥ 3.35) and report displaced
  rows to the index alongside the insert.
- **Wholesale/rare paths** (kind-5 NIP-09, expiration sweep, right-to-vanish,
  NIP-86 `delete(filter)`, FTS reindex/clear): invalidate the whole index;
  the next NEG-OPEN rebuilds it lazily from one scan and incremental
  maintenance resumes. Deletes are rare enough that occasional rebuilds beat
  threading deltas through every module.

### 3. Gating

`IndexingStrategy.maintainLiveNegentropyIndex`, default **false** — library
defaults unchanged for app-side stores (campaign ground rule). geode's
`RelayIndexingStrategy` turns it on; config kill-switch under
`[negentropy]`.

### 4. Concurrency

Mutations happen only on the writer path (single-writer mutex — same
discipline as the FTS worker). Snapshots swap in via copy-on-write so a
reconcile never observes a mid-insert array. Shutdown: the index is memory
only, rebuilt on boot from the first NEG-OPEN's scan; no lifecycle beyond
the store's own close (ground rule 4: no worker, no uncaught exceptions).

## Measurement plan

1. Micro: quartz jvmTest benchmark, cold NEG-OPEN time at 50k events,
   before/after (expect ~340 ms → single-digit ms).
2. Headline: relayBench pairwise sync, alternating A/B runs on the 50k
   corpus (container noise ±10–30%; never trust a single run). Keep only
   if it wins end-to-end; document either way.

## Milestones

1. `LiveNegentropyIndex` + unit tests (ordering, tail/backfill inserts,
   removal, snapshot immutability under concurrent insert, cap behavior).
2. Displaced-row `RETURNING` plumbing in Replaceable/Addressable modules +
   wholesale-invalidation hooks on the rare paths.
3. `LiveEventStore.sealedNegentropyStorage` wiring: index-total filters
   from the index; everything else keeps scan+seal+cache.
4. Benchmarks (micro then relayBench A/B); revert if not a real win.
