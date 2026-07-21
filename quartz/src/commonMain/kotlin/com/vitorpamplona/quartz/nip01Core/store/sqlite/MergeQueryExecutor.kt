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

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement

/**
 * k-way merge executor for the two "wide fan-out, newest-N" query shapes
 * whose single-SQL plan reads O(matching history) rather than O(limit):
 *
 *  1. **home-feed** — `authors=[…] (+ kinds=[…]) [+ since/until] limit=N`.
 *     SQLite seeks every `(kind, pubkey)` combo and feeds *all* matching rows
 *     through a LIMIT-bounded sorter — O(the followed set's whole matching
 *     history). For prolific follows on a cold on-disk DB that was the
 *     `follow-feed` regression (relayBench: 97 ms vs strfry 17 ms). See
 *     `quartz/plans/2026-07-04-follow-feed-read-tradeoff.md`.
 *  2. **tag watcher** — `#<x>=[hundreds of values] (+ kinds=[…])
 *     [+ since/until] limit=N`, the reactions/replies archetype
 *     (`kinds=[7] AND #e=[note ids]`). The per-value streams come sorted off
 *     `(tag_hash[, kind], created_at)`, but their union does not, so SQLite
 *     collects every matching row and TEMP-B-TREE sorts to the limit — the
 *     tag-index analogue of the follow-feed shape. Measured by
 *     `TagAuthorIndexBenchmark` (jvmTest prodbench): `#e IN 300, limit 500`
 *     cost 12.8 ms cold at 200k events and 14.2 ms at 1M, growing with
 *     matching history.
 *
 * Each stream is already a newest-first cursor off an existing index:
 *  - authors: `query_by_kind_pubkey_created (kind, pubkey, created_at DESC)`
 *    (or `query_by_pubkey_created` for authors-only);
 *  - tags: `query_by_tags_hash_kind (tag_hash, kind, created_at DESC)`
 *    (or `query_by_tags_hash` for the no-kind case, gated by
 *    [IndexingStrategy.indexTagsByCreatedAtAlone]).
 *
 * The merge opens one lazy cursor per stream, merges their heads newest-first,
 * and stops at the limit — reading **O(limit + streams)** rows regardless of
 * how much history the authors/tags have, and reusing the existing indexes
 * (no write/size cost). With the pooled statement cache
 * ([StatementCachingConnection]) the per-stream cursors are prepared once and
 * reused across repeated polls of the same REQ.
 *
 * Merge order is `created_at DESC`, tie-broken by `id ASC`. NIP-01 leaves
 * same-`created_at` ties unspecified, so the returned set is a valid newest-N
 * either way. The `id ASC` tie-break is exact — byte-for-byte the same events
 * the single-SQL path returns — only when the store indexes id
 * ([IndexingStrategy.useAndIndexIdOnOrderBy]) **and** the stream cursor can
 * order by id off the index. The author streams can (id is on
 * `event_headers`); the tag streams cannot (the cursor orders off
 * `event_tags`, which has no id column), so a tag stream yields same-second
 * rows in rowid order — still a valid newest-N, but same-second ties may
 * differ from an id-ordered reference.
 *
 * **Cross-stream duplicates.** An author appears in exactly one author stream
 * (one pubkey per event), so the home-feed merge never double-counts. A single
 * event can carry several of the queried tag values (or a repeated tag), so it
 * can surface in several tag streams — the single-SQL path dedups with
 * `SELECT DISTINCT`. The tag merge therefore dedups by event id through a
 * `seen` set; the author merge skips that set entirely.
 */
internal object MergeQueryExecutor {
    /** Author-stream projection: a single `event_headers` scan, unqualified. */
    const val COLS = "id, pubkey, created_at, kind, tags, content, sig"

    /** Tag-stream projection: `event_tags` joins `event_headers`, so qualify. */
    private const val EH_COLS =
        "event_headers.id, event_headers.pubkey, event_headers.created_at, event_headers.kind, event_headers.tags, event_headers.content, event_headers.sig"

    /**
     * Above this many streams, fall back to the single-SQL plan: the
     * per-stream cursor setup stops paying off, and huge fan-outs are
     * collecting a lot no matter what. `kinds.size × (authors|values).size`.
     */
    const val MAX_STREAMS = 2048

    /**
     * Stream count if [filter] is merge-eligible under *either* shape, else
     * `-1`. Routing check for [QueryBuilder]; [run] re-derives which shape.
     */
    fun streamCount(
        filter: QueryBuilder.FilterWithDTags,
        indexStrategy: IndexingStrategy,
    ): Int {
        val authorStreams = authorStreamCount(filter, indexStrategy)
        if (authorStreams > 0) return authorStreams
        return tagStreamCount(filter, indexStrategy)
    }

    /**
     * Author-shape stream count, or `-1`. Eligible = a simple (no tag/search/
     * id/d-tag) query with authors + a limit, whose per-stream index exists.
     * `kinds` optional: with it, one stream per `(kind, author)`; without, one
     * per author (needs the pubkey index).
     */
    fun authorStreamCount(
        filter: QueryBuilder.FilterWithDTags,
        indexStrategy: IndexingStrategy,
    ): Int {
        if (!filter.isSimpleQuery()) return -1
        if (filter.ids != null) return -1
        if (filter.dTags != null) return -1
        if (filter.limit == null || filter.limit <= 0) return -1
        val authors = filter.authors ?: return -1
        if (authors.isEmpty()) return -1
        // Dedup: a repeated pubkey/kind would open two identical cursors and
        // emit every matching event twice (the single-SQL `IN (…)` path dedups
        // naturally), so the stream count — and the cursors — must be over the
        // distinct set.
        val distinctAuthors = authors.distinct().size
        val kinds = filter.kinds
        val streams =
            if (kinds != null && kinds.isNotEmpty()) {
                distinctAuthors * kinds.distinct().size
            } else {
                // authors-only needs the (pubkey, created_at) index to stream.
                if (!indexStrategy.indexEventsByPubkeyAlone) return -1
                distinctAuthors
            }
        // A single stream is already the optimal single index seek — let the
        // normal path handle it; only merge when there's something to merge.
        return if (streams in 2..MAX_STREAMS) streams else -1
    }

    /**
     * Tag-shape stream count, or `-1`. Eligible = a single non-`d` tag key
     * with `IN` (any-of) semantics and ≥2 distinct values, plus a limit, no
     * ids/authors/d-tag/search, and no `AND`-tags (`tagsAll`) — the large-IN
     * watcher shape. `kinds` optional: with it, one stream per
     * `(value, kind)` off `query_by_tags_hash_kind`; without, one per value
     * off `query_by_tags_hash` (needs [IndexingStrategy.indexTagsByCreatedAtAlone]).
     *
     * Authors are excluded on purpose: `tag ∩ author ∩ kind` is a covered
     * single seek under [IndexingStrategy.indexTagsWithKindAndPubkey], not a
     * fan-out, and mixing an author predicate into per-tag streams would not
     * reduce the read.
     */
    fun tagStreamCount(
        filter: QueryBuilder.FilterWithDTags,
        indexStrategy: IndexingStrategy,
    ): Int {
        if (filter.ids != null) return -1
        if (filter.authors != null) return -1
        if (filter.dTags != null) return -1
        if (filter.search != null && filter.search.isNotEmpty()) return -1
        if (filter.limit == null || filter.limit <= 0) return -1
        // AND-tags can't be expressed as a union of per-value streams.
        if (filter.nonDTagsAll != null && filter.nonDTagsAll.isNotEmpty()) return -1
        val inTags = filter.nonDTagsIn ?: return -1
        // A second tag key would AND across keys — not a single union.
        if (inTags.size != 1) return -1
        val values = inTags.values.first().distinct()
        if (values.size < 2) return -1
        val kinds = filter.kinds?.distinct()?.takeIf { it.isNotEmpty() }
        val streams =
            if (kinds != null) {
                values.size * kinds.size
            } else {
                if (!indexStrategy.indexTagsByCreatedAtAlone) return -1
                values.size
            }
        return if (streams in 2..MAX_STREAMS) streams else -1
    }

    /** Prepares one bound, newest-first cursor per author stream. */
    private fun prepareAuthorStreams(
        db: SQLiteConnection,
        filter: QueryBuilder.FilterWithDTags,
        indexStrategy: IndexingStrategy,
    ): List<SQLiteStatement> {
        // Dedup so a repeated pubkey/kind can't open two identical cursors and
        // double-emit (see authorStreamCount).
        val authors = filter.authors!!.distinct()
        val kinds = filter.kinds?.distinct()?.takeIf { it.isNotEmpty() }
        val since = filter.since
        val until = filter.until

        // `id ASC` is available for free — straight off the index, no sort —
        // only when the store built the id column into its order-by indexes;
        // matches every ORDER BY in QueryBuilder. Without it, appending id ASC
        // would force a materializing sort and defeat the lazy cursor, so we
        // leave the tie in rowid order (a valid newest-N; see the class doc).
        val orderBy =
            if (indexStrategy.useAndIndexIdOnOrderBy) "created_at DESC, id ASC" else "created_at DESC"

        val stmts = ArrayList<SQLiteStatement>((kinds?.size ?: 1) * authors.size)
        if (kinds != null) {
            val sql =
                buildString {
                    append("SELECT ").append(COLS)
                    append(" FROM event_headers INDEXED BY query_by_kind_pubkey_created")
                    append(" WHERE kind = ? AND pubkey = ?")
                    if (until != null) append(" AND created_at <= ?")
                    if (since != null) append(" AND created_at >= ?")
                    append(" ORDER BY ").append(orderBy)
                }
            for (kind in kinds) {
                for (author in authors) {
                    val stmt = db.prepare(sql)
                    var p = 1
                    stmt.bindLong(p++, kind.toLong())
                    stmt.bindText(p++, author)
                    if (until != null) stmt.bindLong(p++, until)
                    if (since != null) stmt.bindLong(p++, since)
                    stmts.add(stmt)
                }
            }
        } else {
            val sql =
                buildString {
                    append("SELECT ").append(COLS)
                    append(" FROM event_headers INDEXED BY query_by_pubkey_created")
                    append(" WHERE pubkey = ?")
                    if (until != null) append(" AND created_at <= ?")
                    if (since != null) append(" AND created_at >= ?")
                    append(" ORDER BY ").append(orderBy)
                }
            for (author in authors) {
                val stmt = db.prepare(sql)
                var p = 1
                stmt.bindText(p++, author)
                if (until != null) stmt.bindLong(p++, until)
                if (since != null) stmt.bindLong(p++, since)
                stmts.add(stmt)
            }
        }
        return stmts
    }

    /** Prepares one bound, newest-first cursor per tag-value stream. */
    private fun prepareTagStreams(
        db: SQLiteConnection,
        filter: QueryBuilder.FilterWithDTags,
        hasher: TagNameValueHasher,
    ): List<SQLiteStatement> {
        val entry = filter.nonDTagsIn!!.entries.first()
        val tagName = entry.key
        val values = entry.value.distinct()
        val kinds = filter.kinds?.distinct()?.takeIf { it.isNotEmpty() }
        val since = filter.since
        val until = filter.until

        // The tag cursors stream off event_tags (which has no id column), so
        // the tie order can only be created_at DESC — see the class doc.
        val stmts = ArrayList<SQLiteStatement>((kinds?.size ?: 1) * values.size)
        if (kinds != null) {
            val sql =
                buildString {
                    append("SELECT ").append(EH_COLS)
                    append(" FROM event_tags INDEXED BY query_by_tags_hash_kind")
                    append(" JOIN event_headers ON event_headers.row_id = event_tags.event_header_row_id")
                    append(" WHERE event_tags.tag_hash = ? AND event_tags.kind = ?")
                    if (until != null) append(" AND event_tags.created_at <= ?")
                    if (since != null) append(" AND event_tags.created_at >= ?")
                    append(" ORDER BY event_tags.created_at DESC")
                }
            for (value in values) {
                val tagHash = hasher.hash(tagName, value)
                for (kind in kinds) {
                    val stmt = db.prepare(sql)
                    var p = 1
                    stmt.bindLong(p++, tagHash)
                    stmt.bindLong(p++, kind.toLong())
                    if (until != null) stmt.bindLong(p++, until)
                    if (since != null) stmt.bindLong(p++, since)
                    stmts.add(stmt)
                }
            }
        } else {
            val sql =
                buildString {
                    append("SELECT ").append(EH_COLS)
                    append(" FROM event_tags INDEXED BY query_by_tags_hash")
                    append(" JOIN event_headers ON event_headers.row_id = event_tags.event_header_row_id")
                    append(" WHERE event_tags.tag_hash = ?")
                    if (until != null) append(" AND event_tags.created_at <= ?")
                    if (since != null) append(" AND event_tags.created_at >= ?")
                    append(" ORDER BY event_tags.created_at DESC")
                }
            for (value in values) {
                val tagHash = hasher.hash(tagName, value)
                val stmt = db.prepare(sql)
                var p = 1
                stmt.bindLong(p++, tagHash)
                if (until != null) stmt.bindLong(p++, until)
                if (since != null) stmt.bindLong(p++, since)
                stmts.add(stmt)
            }
        }
        return stmts
    }

    /**
     * Runs the merge for whichever shape [filter] matches, calling [onRow]
     * with each winning cursor positioned on the row to emit, newest-first,
     * up to `limit`. [onRow] must read the current row (it stays valid until
     * the next step). [hasher] is only consulted for the tag shape.
     */
    fun run(
        db: SQLiteConnection,
        filter: QueryBuilder.FilterWithDTags,
        indexStrategy: IndexingStrategy,
        hasher: (SQLiteConnection) -> TagNameValueHasher,
        onRow: (SQLiteStatement) -> Unit,
    ) {
        if (authorStreamCount(filter, indexStrategy) > 0) {
            // One pubkey per event ⇒ author streams never overlap: no dedup.
            mergeStreams(prepareAuthorStreams(db, filter, indexStrategy), filter.limit!!, dedup = false, onRow)
        } else {
            // A single event can match several tag values ⇒ dedup by id.
            mergeStreams(prepareTagStreams(db, filter, hasher(db)), filter.limit!!, dedup = true, onRow)
        }
    }

    /**
     * Heap-free k-way merge over the prepared [stmts]: repeatedly emits the
     * newest live head (`created_at DESC`, tie `id ASC`) until [limit] rows
     * are emitted or every stream is drained. When [dedup] is set an event id
     * already emitted is skipped (its cursor still advances), so a row that
     * surfaces in several streams is emitted once.
     */
    private fun mergeStreams(
        stmts: List<SQLiteStatement>,
        limit: Int,
        dedup: Boolean,
        onRow: (SQLiteStatement) -> Unit,
    ) {
        try {
            val k = stmts.size
            val headCreatedAt = LongArray(k)
            val headId = arrayOfNulls<String>(k)
            val live = BooleanArray(k)

            // Position each cursor on its newest row.
            for (i in 0 until k) {
                if (stmts[i].step()) {
                    headId[i] = stmts[i].getText(0)
                    headCreatedAt[i] = stmts[i].getLong(2)
                    live[i] = true
                }
            }

            val seen = if (dedup) HashSet<String>() else null
            var emitted = 0
            while (emitted < limit) {
                // Pick the newest live head: created_at DESC, then id ASC.
                var best = -1
                for (i in 0 until k) {
                    if (!live[i]) continue
                    if (best == -1 ||
                        headCreatedAt[i] > headCreatedAt[best] ||
                        (headCreatedAt[i] == headCreatedAt[best] && headId[i]!! < headId[best]!!)
                    ) {
                        best = i
                    }
                }
                if (best == -1) break

                // Emit unless this id was already emitted by another stream.
                if (seen == null || seen.add(headId[best]!!)) {
                    onRow(stmts[best]) // cursor is still on the head row
                    emitted++
                }

                // Advance the winner to its next row.
                if (stmts[best].step()) {
                    headId[best] = stmts[best].getText(0)
                    headCreatedAt[best] = stmts[best].getLong(2)
                } else {
                    live[best] = false
                }
            }
        } finally {
            for (s in stmts) s.close()
        }
    }
}
