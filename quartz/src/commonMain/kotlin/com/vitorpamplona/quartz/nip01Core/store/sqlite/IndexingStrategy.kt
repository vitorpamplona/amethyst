/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.quartz.nip01Core.store.sqlite

import com.vitorpamplona.quartz.nip01Core.core.Tag

interface IndexingStrategy {
    /**
     * Activate this if you see too many Filters with just LIMIT, SINCE and
     * UNTIL filled up.
     *
     * Clients never support all kinds, so this is usually
     * only done with syncing services that must download ALL kinds from
     * ALL authors.
     *
     * The index will make these queries significantly faster, but maybe speed
     * is not a requirement on Sync services. The size of this index is
     * considerable.
     *
     * Keep in mind that activating too many indexes increases the size of the
     * DB so much that the indexes themselves won't fit in memory, requiring
     * frequent reloadings of the index itself from disk.
     */
    val indexEventsByCreatedAtAlone: Boolean

    /**
     * Activate this for filters that carry `authors` but no `kinds` —
     * "everything by these pubkeys". Clients rarely need it (they query
     * their supported kinds), but relays receive it constantly: profile
     * archives, account migration/backup tools, and follow-everything
     * feeds. Without it those filters degrade to a full scan of the
     * time index. strfry maintains the equivalent (`pubkey`) index
     * unconditionally.
     */
    val indexEventsByPubkeyAlone: Boolean

    /**
     * Activate this if you see too many Tag-centric Filters without
     * kind, pubkey or id.
     *
     * Clients never support all kinds, so this is usually
     * only done in rare usecases where the client supports all
     * kinds.
     *
     * The index will make these queries significantly faster, but maybe speed
     * is not a requirement on such services. Because this is an index in
     * event tags, it becomes QUITE BIG.
     *
     * Keep in mind that activating too many indexes increases the size of the
     * DB so much that the indexes themselves won't fit in memory, requiring
     * frequent reloadings of the index itself from disk.
     */
    val indexTagsByCreatedAtAlone: Boolean

    /**
     * Activate this if you see too many Tag-centric Filters without
     * kind AND pubkey at the same time.
     *
     * This shape (reports by your follows, NIP-04 DM rooms, follows-scoped
     * community feeds) is not rare on the client side: the 2026-07 filter
     * assembler survey counted 65 call sites building
     * `kinds + authors + tags`. Without this index the plan seeks
     * `(tag_hash, kind)` and reads every row for that tag/kind before
     * filtering the author.
     *
     * Measured by `TagAuthorIndexBenchmark` (jvmTest prodbench): the
     * DM-room query drops 9.4 ms → 0.6 ms (~15×) at 200k events and
     * 14.2 ms → 0.66 ms (~21×) at 1M — the gap grows with corpus size —
     * while batch-insert cost stays inside run noise (49.0 vs 47.4
     * µs/event at 1M). geode enables it; the client default stays off
     * because a client store's per-tag row counts are bounded by one
     * user's data. Flipping it on an existing DB is safe: the index is
     * built on next open by `EventIndexesModule.ensureOptionalIndexes`.
     *
     * Keep in mind that activating too many indexes increases the size of the
     * DB so much that the indexes themselves won't fit in memory, requiring
     * frequent reloadings of the index itself from disk.
     */
    val indexTagsWithKindAndPubkey: Boolean

    /**
     * Activate this to make sure queries are always in order when
     * the same created_at exists. This will impact performance and
     * the size of indexes, but it provides results that are compliant
     * with the Nostr Spec
     */
    val useAndIndexIdOnOrderBy: Boolean

    /**
     * Defer NIP-50 tokenization off the insert path. When on (and
     * [indexFullTextSearch] is on), inserts skip the FTS write — a
     * measurable slice of commit cost — and a catch-up pass indexes
     * from a persisted `row_id` watermark later: continuously in idle
     * gaps on a relay, and always drained *before* a search query runs,
     * so NIP-50 results stay exactly as fresh as the synchronous path.
     * Only pays off where something drives the catch-up (the relay
     * server does); leave it off for client-side stores.
     */
    val deferFullTextSearchIndexing: Boolean

    /**
     * Maintain the NIP-50 full-text search index (`event_fts`).
     *
     * Unlike the other flags this defaults to **on**: search is a core
     * feature and every searchable event is tokenized into an FTS virtual
     * table on insert, with an `AFTER DELETE` trigger keeping it in sync.
     *
     * Turn it **off** when this store never serves search from SQLite —
     * e.g. a relay that offloads NIP-50 to an external engine like Vespa.
     * With it off, no `event_fts` table or trigger is created, inserts
     * skip the tokenization cost, deletes skip the trigger, and any filter
     * carrying a non-empty `search` term returns no matches.
     */
    val indexFullTextSearch: Boolean

    /**
     * Order NIP-50 search results by the FTS index rowid (= event row_id,
     * i.e. **ingestion order**) instead of `created_at DESC`.
     *
     * The `event_fts` rowid is the event's `event_headers.row_id`, so
     * `ORDER BY event_fts.rowid DESC LIMIT n` walks the FTS doclist
     * newest-rowid-first and stops at the limit — **O(limit)** — where the
     * default `ORDER BY created_at DESC` must materialize *every* document
     * matching the term and sort it (cost grows with the whole corpus; the
     * NIP-50 curve that falls ~18× from 25k→400k events). The unconditional
     * contentless-index + segment-merge changes already shrink and speed the
     * default path; this flag is the one that makes search cost independent
     * of corpus size.
     *
     * The trade-off is semantic: ingestion order ≈ `created_at` order only
     * while events arrive roughly in time order. A relay that bulk-syncs
     * historical events (NIP-77) ingests old events late, so "newest rowid"
     * and "newest created_at" diverge during/after a sync. Leave it **off**
     * (the default) where strict recency ordering matters; turn it **on**
     * for stores that want corpus-independent search latency and accept
     * recently-ingested-first ordering. Only affects the simple-search query
     * shape (`search [+ kinds/authors] + limit`); tag∩search and the
     * negentropy snapshot path keep `created_at` ordering.
     */
    val searchOrderByRowId: Boolean get() = false

    /**
     * Maintain an always-current in-memory `(created_at, id)` set (a
     * [com.vitorpamplona.quartz.nip77Negentropy.LiveNegentropyIndex]) so
     * NIP-77 NEG-OPENs over the full corpus skip the scan + O(n log n)
     * seal — strfry answers those off its live tree. Costs ~140 B/event
     * of JVM heap (the id is kept as a 64-char hex string, not 32 bytes)
     * plus one indexed pre-SELECT per replaceable insert, which
     * only makes sense on a *relay*; client-side stores don't serve
     * NEG-OPENs, so the default is **off**.
     */
    val maintainLiveNegentropyIndex: Boolean get() = false

    fun shouldIndex(
        kind: Int,
        tag: Tag,
    ): Boolean
}

/**
 * By default, we index all tags that have a single letter name and some value
 */
class DefaultIndexingStrategy(
    override val indexEventsByCreatedAtAlone: Boolean = false,
    override val indexEventsByPubkeyAlone: Boolean = false,
    override val indexTagsByCreatedAtAlone: Boolean = false,
    override val indexTagsWithKindAndPubkey: Boolean = false,
    override val useAndIndexIdOnOrderBy: Boolean = false,
    override val indexFullTextSearch: Boolean = true,
    override val deferFullTextSearchIndexing: Boolean = false,
    override val searchOrderByRowId: Boolean = false,
    override val maintainLiveNegentropyIndex: Boolean = false,
) : IndexingStrategy {
    override fun shouldIndex(
        kind: Int,
        tag: Tag,
    ) = tag.size >= 2 && tag[0].length == 1
}
