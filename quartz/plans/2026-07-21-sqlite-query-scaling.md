# SQLite query scaling: contentless FTS + rowid search, tag-path merge, pooled statements

**Status: shipped.** Follow-up to a scale-curve report (`SQLite vs Vespa`,
throughput vs corpus size) showing the app-side SQLite store degrading with
corpus size on several query shapes — NIP-50 search ~18× (25k→400k), batch
ingest 16×, author-timeline 13×, follow-feed 2.4× — while point reads stayed
flat. Two of those (author-timeline, follow-feed) were already addressed
(the pubkey-alone index and `MergeQueryExecutor`) and mostly reflect running
the report under the *client* `DefaultIndexingStrategy` rather than
`relayIndexingStrategy()`. This change adds the **large-IN tag watcher** to the
merge executor, fixes the **FTS delete path** (which degraded with corpus
size) and shrinks the FTS index, fixes **NIP-50 search ordering** (it was
sorting by `created_at`, not relevance), and fixes a **statement-cache** miss
the merge paths hit.

It does **not** fix NIP-50 *search* latency: bm25 relevance scoring (like the
old `created_at` sort) must visit every match, so search cost still grows with
the match set (§1). Corpus-independent search is an external-engine job.

Everything here is read/size work; the write path and on-disk index set are
unchanged except the FTS table, which gets *smaller* and deletes faster.

## 1. FTS — contentless index (fast deletes + smaller), NOT a search-scaling fix

The old index was `fts5(event_header_row_id, content)`: it stored a second
copy of the tokenized text, its rowid was auto-assigned (unrelated to the
event), and — the real problem — the `fts_foreign_key` delete trigger deleted
by the `event_header_row_id` *column*, which FTS5 cannot seek, so it **scanned
the whole index per delete**.

Changes (`FullTextSearchModule`, `QueryBuilder`, DB version 4→5):

- **rowid = `event_headers.row_id`.** Set explicitly on every insert; the
  delete trigger, reindex, and catch-up paths key off it. Deletes become an
  O(log n) primary-key seek instead of an O(n) column scan. This is the win:
  every event removal fires the trigger — replaceable rotation, kind-5,
  expiration, right-to-vanish — so on the old schema deletion throughput
  degraded with corpus size.
- **Contentless** (`fts5(content, content='', contentless_delete=1)`). The
  indexed text is *derived* (`SearchableEvent.indexableContent()`, not a raw
  column), so FTS5 **external-content** — which re-reads the source column from
  the base table — cannot express it; **contentless** is the correct primitive.
  It drops the stored content copy (index shrinks) and `contentless_delete=1`
  keeps the delete trigger working.
- **Segment compaction.** `reindexAll` finishes with `'optimize'`, and the
  periodic `SQLiteEventStore.optimize()` (geode's maintenance tick) folds in a
  bounded `'merge'`, so incremental / deferred-catch-up inserts don't leave the
  index as many small segments.

Delete cost — `FtsSearchScalingBenchmark.deleteByColumnVsByRowid`, 500 deletes,
in-memory:

| rows | by column (old) | by rowid (new) |
|---|---:|---:|
| 2k | 91.8 ms | 6.6 ms |
| 8k | 362.0 ms | 4.7 ms |

By-column grows ~linearly with the table (O(n)/delete); by-rowid is flat —
~78× at 8k rows and widening.

**Search ordering fixed to relevance (NIP-50), which the store was getting
wrong.** NIP-50 says results are returned "in descending order by quality of
search result ... not by the usual `.created_at`", with the limit applied after
the score — but the store sorted search by `created_at DESC` (pre-existing).
*Every* search filter now orders by FTS5 bm25 (`ORDER BY event_fts.rank`,
`created_at DESC` as a tie-break): the tag-free shape (`makeSimpleSearch`) and
`search + tag` (the `prepareRowIDSubQueries`/`makeQueryIn` path, which carries
the rank column through the row-id subquery via `projectRank` and applies the
LIMIT by rank). Only the negentropy snapshot keeps `created_at` — a sync set,
not a ranked result. Verified against stronger-but-older matches outranking
weaker-but-newer ones, tag-scoped, with the limit cutting by score
(`SearchRelevanceOrderTest`, `Fts5CapabilityProbe`).

An earlier draft of *this* change instead added a `searchOrderByRowId` flag
(order by the FTS rowid for O(limit) search); that is *ingestion* order — wrong
events under a limit once ingestion diverges from time order, and not relevance
either — so it was removed.

This is a **correctness** fix, not a scaling one: bm25 (like the created_at
sort) must score every match, so search latency still grows with the match set
(the report's 18× curve) and `optimize` doesn't change the asymptotics
(measured within noise at 100k/200k). Corpus-independent search is genuinely an
external-engine job (the Vespa side of the report), not this index.

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
- Search ordering unchanged: still exact `created_at DESC` (NIP-01 `limit`
  semantics); the delete + size + optimize wins are unconditional and
  spec-neutral.
