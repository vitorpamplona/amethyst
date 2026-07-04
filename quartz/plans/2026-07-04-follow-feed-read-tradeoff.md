# follow-feed: measured read/write/size tradeoff — keep the current plan

The 1M run showed geode's `follow-feed` (`kinds=[1,6] AND authors=[150]
ORDER BY created_at DESC LIMIT 500`) at 97.7 ms vs strfry 17.6 ms. This
measured whether any change is worth it, deliberately across **read**,
**write**, and **size** so a read win doesn't quietly cost elsewhere.

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

## Decision

Keep the current composite plan. It's the robust default; the tempting SQL
fixes each break a common workload and get worse at scale, which is exactly
the "don't break something else" risk. If follow-feed latency on prolific
follows becomes a priority, the app-level k-way merge is the only approach
that improves it universally, and it's write/size-neutral. Benchmark kept as
the evidence and as a guard for anyone tempted by the `scan` shortcut.
