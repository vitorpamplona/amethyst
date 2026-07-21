# SQLite query scaling: contentless FTS + rowid search, tag-path merge, pooled statements

**Status: shipped.** Follow-up to a scale-curve report (`SQLite vs Vespa`,
throughput vs corpus size) showing the app-side SQLite store degrading with
corpus size on several query shapes — NIP-50 search ~18× (25k→400k), batch
ingest 16×, author-timeline 13×, follow-feed 2.4× — while point reads stayed
flat. Two of those (author-timeline, follow-feed) were already addressed
(the pubkey-alone index and `MergeQueryExecutor`) and mostly reflect running
the report under the *client* `DefaultIndexingStrategy` rather than
`relayIndexingStrategy()`. This change takes the two structural read shapes
that were still corpus-bound: **NIP-50 search** and the **large-IN tag
watcher**, plus a statement-cache fix the merge paths needed.

Everything here is read/size work; the write path and on-disk index set are
unchanged except the FTS table, which gets *smaller*.

## 1. NIP-50 search — contentless FTS5 + optional rowid ordering + segment merge

The old index was `fts5(event_header_row_id, content)`: it stored a second
copy of the tokenized text, and its rowid was auto-assigned (unrelated to the
event). Search ran `… event_fts MATCH ? ORDER BY event_headers.created_at DESC
LIMIT n`, which must materialize **every** document matching the term and sort
it — cost grows with the whole corpus. That is the 18× curve.

Three changes (`FullTextSearchModule`, `IndexingStrategy`, `QueryBuilder`,
DB version 4→5):

- **Contentless** (`fts5(content, content='', contentless_delete=1)`). The
  indexed text is *derived* (`SearchableEvent.indexableContent()`, not a raw
  column), so FTS5 **external-content** — which re-reads the source column from
  the base table — cannot express it; **contentless** is the correct primitive.
  It drops the stored content copy (index shrinks) and, with
  `contentless_delete=1`, still supports the `fts_foreign_key` delete trigger.
- **rowid = `event_headers.row_id`.** Set explicitly on every insert. The
  delete trigger and reindex/catch-up paths all key off it, so the join is
  `event_headers.row_id = event_fts.rowid` with no stored linkage column.
- **`searchOrderByRowId`** (new `IndexingStrategy` flag, **default off**). When
  on, simple search orders by `event_fts.rowid DESC LIMIT n`, which FTS5
  early-terminates off the doclist — **O(limit)**, corpus-independent. rowid is
  *ingestion* order, so this ≈ recency only while events arrive in time order;
  a relay that bulk-syncs history (NIP-77) diverges, which is why it is off by
  default and left off for geode. Only the simple-search shape changes; tag∩
  search and the negentropy snapshot keep `created_at`.
- **Segment compaction.** `reindexAll` finishes with a full `'optimize'`, and
  the periodic `SQLiteEventStore.optimize()` (geode's maintenance tick) folds
  in a bounded `'merge'`, so incremental / deferred-catch-up inserts don't
  leave the index as many small segments.

Measured — `FtsSearchScalingBenchmark` (jvmTest prodbench), search a term in
~1% of events, `limit=50`, in-memory:

| corpus | created_at, fragmented | created_at, optimized | **rowid, optimized** |
|---|---:|---:|---:|
| 50k | 1.92 ms | 1.16 ms | **0.26 ms** |
| 100k | 2.16 ms | 2.23 ms | **0.22 ms** |
| 200k | 4.33 ms | 4.26 ms | **0.22 ms** |

- **rowid ordering is the corpus-independence lever**: flat ~0.22 ms vs
  created_at's 1.16 → 4.26 ms (grows with the match set) — ~19× at 200k, and
  the gap keeps widening. This is the direct answer to the search curve, for
  deployments that accept ingestion-order results.
- **segment `optimize`** helps most at small sizes / right after bulk
  incremental inserts (1.92 → 1.16 ms at 50k); FTS5 `automerge` already caps
  fragmentation in steady state, so at 100k/200k here it is within noise. It is
  a cheap, unconditional safety net (biggest for the deferred relay path, whose
  catch-up commits in 1k batches), not the main lever.

Migration (v4→v5): the old rowids can't be remapped, so `event_fts` is dropped
and rebuilt — synchronous stores rebuild in the upgrade transaction (client
corpora are small), deferred stores reset the catch-up watermark to 0 and let
the background worker repopulate (no long migration transaction).
`ContentlessFtsMigrationTest` fabricates a real v4 DB (old schema + garbage
rows + `user_version=4`) and asserts the reopen rebuilds search, wipes the
stale rows, maps rowid→row_id, and keeps the delete trigger working.

## 2. Tag watcher — extend `MergeQueryExecutor` to the tag path

`kinds=[7] AND #e=[hundreds of note ids] LIMIT n` (reactions/replies) had the
same shape the follow-feed fix already solved for authors: the per-value
streams come sorted off `(tag_hash[, kind], created_at)`, but their union does
not, so SQLite collected every matching row and TEMP-B-TREE-sorted to the limit
— O(matching history), growing with the corpus (`TagAuthorIndexBenchmark`:
12.8 ms cold at 200k → 14.2 ms at 1M).

`MergeQueryExecutor` now opens one lazy newest-first cursor per `(value[,
kind])` off `query_by_tags_hash_kind` (or `query_by_tags_hash` when there is no
kind, gated by `indexTagsByCreatedAtAlone`) and heap-merges to the limit —
**O(limit + streams)**. Unlike the author path, one event can carry several
queried tag values, so the tag merge **dedups by event id** through a `seen`
set (the author path, one pubkey per event, skips it). Eligibility is narrow
(one non-`d` tag key, ≥2 values, a limit, no ids/authors/d-tag/search, no
AND-tags); everything else falls through to the single-SQL plan. Counts and
deletes are unchanged (still single-SQL). `TagMergeCorrectnessTest` pins it
against a Kotlin reference including cross-stream dedup, since/until windows,
the raw path, and tie handling (tag cursors order off `event_tags`, which has
no id column, so same-second ties are a valid newest-N but not id-exact).

## 3. Pooled statement cache — so the merges actually cache

`StatementCachingConnection` kept **one** handle per SQL string. The k-way
merge opens *many* identical-SQL cursors at once (one per author/tag stream),
so every stream past the first missed the cache and prepared uncached, and a
repeated follow-feed / reactions REQ re-prepared all of them each poll. The
cache now keeps a small **pool** per SQL (bounded by a global cap, default
raised 256→512), so concurrent same-SQL checkouts reuse cached handles and
repeated polls reuse their per-stream cursors. `StatementCachingConnectionTest`
covers sequential reuse, concurrent distinctness/independence, freed-handle
reuse, and cap overflow → uncached fallback.

## Correctness / scope

- DB version bump 4→5 with a real-upgrade test.
- New capability test (`Fts5CapabilityProbe`) pins the FTS5 features the
  contentless index needs (contentless_delete, absent-rowid delete no-op,
  rowid ordering, merge/optimize) to the bundled SQLite (3.50.1), so a driver
  downgrade fails loudly.
- All existing `store.sqlite` tests pass unchanged except `QueryAssemblerTest`,
  whose asserted EXPLAIN output updated for the new join column
  (`event_fts.rowid`) and the contentless table's virtual-index marker
  (`0:M2`→`0:M1`).
- Defaults preserved: `searchOrderByRowId` off, so existing deployments keep
  exact `created_at DESC` search ordering; the size + optimize wins are
  unconditional.
