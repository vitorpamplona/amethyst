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
     * This is a rarely used index (reports by your follows or
     * NIP-04 DMs for instance) that becomes quite large without
     * major gains.
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
) : IndexingStrategy {
    override fun shouldIndex(
        kind: Int,
        tag: Tag,
    ) = tag.size >= 2 && tag[0].length == 1
}
