# Incremental live negentropy storage

**Status: shipped** — backlog item 3 of the relay performance campaign
(PR #3466 follow-up). All four milestones landed on this branch.

**Measured results.** Micro (LiveNegentropyBenchmark, 50k events,
in-container): scan+seal cold path 80–100 ms per open; index post-write
open 9–16 ms (~5–10×); first open ~70 ms (pays the one-time rebuild).
relayBench A/B (both variants in ONE run — geode with index vs geode
with `[negentropy].live_index = false` — 50k corpus, then repeated with
the relay order reversed to control for run-order bias):

 - **identical-set reconcile: 56/57 ms with the index vs 110/130 ms
   without, in both orders — the real ~2.2× win.** This phase runs
   after the delta transfer wrote events, so the old single-slot cache
   always misses — the exact gap the index closes.
 - initial (first-ever) reconcile and ingest throughput: whichever
   relay ran first won/lost respectively in BOTH runs — pure run-order
   effect, i.e. no ingest regression and (as designed) no win on the
   very first open, which pays the lazy rebuild.

Measurement note for future A/Bs: putting both variants in one
relayBench run controls container noise better than alternating runs,
but per-phase run-order bias is real (~100 ms on initial reconcile,
~10% on ingest) — always repeat with the order reversed.

**strfry head-to-head** (same container, 50k corpus, `--geode-no-search`,
strfry built from source, single run): identical-set reconcile geode
41.4 ms vs strfry 30.1 ms — the always-current-tree gap is down to
~1.4× from the campaign-opening "full scan + seal per open". Initial
(cold) reconcile 177 vs 112 ms: strfry keeps its tree across the whole
run while geode's first open pays the lazy rebuild. Ingest measured at
parity in this container (7,972 vs 7,860 ev/s — treat as ±noise, the
earlier 1.59× gap was measured on different hardware); storage 72.8 vs
106.0 MiB.

**Possible follow-up** if the residual matters: the per-open cost is now
the O(n) copy + re-seal into a `StorageVector` (~16 ms at 50k). A custom
immutable `IStorage` view over the live entries (copy-on-write chunks,
no re-seal) would make an open O(1), matching strfry's zero-copy read of
its tree — that is the "interesting part" the original backlog item
anticipated, deferred until a benchmark says the remaining ~11 ms is
worth it.

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
