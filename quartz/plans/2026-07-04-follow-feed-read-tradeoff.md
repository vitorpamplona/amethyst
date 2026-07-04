# follow-feed: measured read/write/size tradeoff — shipped the k-way merge

**Status: shipped.** The 1M run showed geode's `follow-feed` (`kinds=[1,6] AND
authors=[150] ORDER BY created_at DESC LIMIT 500`) at 97.7 ms vs strfry 17.6 ms.
This measured whether any change is worth it, deliberately across **read**,
**write**, and **size** so a read win doesn't quietly cost elsewhere — and the
one universally-better option (the app-level k-way merge) was then built.
`MergeQueryExecutor` now serves this shape; a fresh 1M relayBench run has geode
at **18.8 ms vs strfry 17.7 ms** (parity, down from 97.7 ms) and **46,258 ev/s
vs strfry 15,365 @8conn** (3×), with both relays returning the same 500 events.

## What the current plan actually does

Composite seek `query_by_kind_pubkey_created (kind, pubkey, created_at)` for
the 300 `(kind, pubkey)` combos, feeding every matching row into a
LIMIT-bounded top-500 sorter (`USE TEMP B-TREE FOR ORDER BY`). So it reads
**O(followed authors' matching history)** rows — cheap in memory, but on a
cold on-disk 1M DB, following prolific accounts means reading hundreds of
thousands of index rows: that's the 97.7 ms (and it's a worst case — the
relayBench follow set is the 150 *most prolific* authors).

## Read — measured (`FollowFeedReadBenchmark`, in-memory)

Three strategies, all on **existing** indexes, across two opposite follow
profiles, at two corpus sizes:

| | prolific-recent | sparse-old |
|---|---:|---:|
| **current** (composite + bounded sort) | 5.7 ms | 1.9 ms |
| **scan** (`created_at` index, early-LIMIT — strfry's shape) | **1.0 ms** | **1601.9 ms** |
| **union** (per-(author,kind) LIMIT 500, merged) | 316.9 ms | 20.0 ms |

*(scale 5 ≈ 1.05M events; scale 1 numbers: scan sparse-old 234 ms, so it
grows 234 → 1601 ms as the corpus grows.)*

- **scan** is the strfry approach and wins big for active follows (1.0 ms),
  but is **catastrophic for sparse/inactive follows** — it walks newest-first
  through the whole corpus to reach their old events, so it's slow **and
  scales with total corpus size** (234 → 1601 ms). Following people who rarely
  post is the common case; this would be a severe regression.
- **union** (300 branches) is dominated by per-branch overhead — non-viable.
- **current** is the only *robust* option: never catastrophic, flat across
  scale, bounded by the followed set (not the corpus).

**No safe SQL-level swap exists.** Each alternative trades geode's worst case
for a worse one on a common workload. The only universally-better plan is
strfry's algorithm — an **app-level k-way merge**: open a `(kind, pubkey,
created_at DESC)` cursor per `(author, kind)`, heap-merge them, stop at the
LIMIT. That reads **O(LIMIT + streams)** regardless of follow activity or
corpus size. It's a real new query-execution path (not a SQL tweak), worth
building only if prolific-follow feeds become a measured production priority.

## Write & size — neutral for every candidate

All four strategies (current, scan, union, k-way merge) run on indexes that
already exist:

| strategy | index | new index? |
|---|---|---|
| current / union / k-way | `query_by_kind_pubkey_created` (unconditional) | no |
| scan | `query_by_created_at_id` (`indexEventsByCreatedAtAlone`, on for geode) | no |

So **no read-strategy choice here changes the index set** — write throughput
(geode's 1.3× ingest lead) and on-disk footprint (geode's storage win) are
untouched no matter which we pick. Adding a *new* index to help this one
shape was considered and rejected: it would tax every insert and grow the DB
for a single query pattern, and (as the profiles investigation already
showed) SQLite reverts to the scan under `ANALYZE` anyway.

## Decision — built the k-way merge

The SQL alternatives were all rejected (each breaks a common workload and gets
worse at scale). The app-level **k-way merge** was the only approach that
improves this shape universally while staying write/size-neutral, so it was
built as `MergeQueryExecutor` and wired into both `query` and the zero-decode
`rawQuery` relay path.

It opens one lazy newest-first cursor per `(kind, pubkey)` stream off the
existing `query_by_kind_pubkey_created` index (or `query_by_pubkey_created` for
authors-only), merges their heads `(created_at DESC, id ASC)`, and stops at the
limit — reading **O(limit + streams)** rows regardless of follow activity or
corpus size, which is exactly strfry's algorithm. Eligibility is narrow
(2..2048 streams, simple filter, explicit limit, no ids/d-tags); everything
else falls through to the single-SQL plan, so no other query shape changes.

Correctness is pinned by `MergeQueryCorrectnessTest` (merge == single-SQL top-N
vs both an independent reference and the SQL path, across ties/windows/raw).
`FollowFeedReadBenchmark` gained a `merge` variant showing it flat ~10-12 ms
in-memory across both profiles — the in-memory number understates the win
because the merge trades *disk* reads for CPU; the real gain is the cold-disk
1M relayBench result above (97.7 → 18.8 ms). The `scan` guard stays as evidence
of why the tempting shortcut is catastrophic on sparse follows (1995 ms at 1M).
