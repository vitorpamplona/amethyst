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
 * k-way merge executor for the **home-feed** query shape:
 * `authors=[…] (+ kinds=[…]) [+ since/until] limit=N` ordered newest-first.
 *
 * SQLite serves this by seeking every `(kind, pubkey)` combo and feeding
 * *all* matching rows through a LIMIT-bounded sorter — so it reads O(the
 * followed set's whole matching history). For prolific follows on a cold
 * on-disk DB that's the `follow-feed` regression (relayBench: 97 ms vs
 * strfry 17 ms). See `quartz/plans/2026-07-04-follow-feed-read-tradeoff.md`.
 *
 * Each `(kind, pubkey)` is already a newest-first stream off the
 * `query_by_kind_pubkey_created (kind, pubkey, created_at DESC)` index
 * (or `query_by_pubkey_created` for authors-only). This opens one lazy
 * cursor per stream and merges their heads, stopping at the limit — so it
 * reads only **O(limit + streams)** rows regardless of how much history the
 * authors have, and it reuses the existing indexes (no write/size cost).
 *
 * Merge order is `(created_at DESC, id ASC)`: a deterministic, correct
 * top-N. NIP-01 leaves same-`created_at` ties unspecified, so this only
 * pins down (and makes reproducible) which events sit exactly at a
 * same-second boundary — the returned set is a valid newest-N either way.
 */
internal object MergeQueryExecutor {
    const val COLS = "id, pubkey, created_at, kind, tags, content, sig"

    /**
     * Above this many streams, fall back to the single-SQL plan: the
     * per-stream cursor setup stops paying off, and huge author lists are
     * collecting a lot no matter what. `kinds.size × authors.size`.
     */
    const val MAX_STREAMS = 2048

    /**
     * Stream count if [filter] is merge-eligible, else `-1`. Eligible = a
     * simple (no tag/search/id/d-tag) query with authors + a limit, whose
     * per-stream index exists. `kinds` optional: with it, one stream per
     * `(kind, author)`; without, one per author (needs the pubkey index).
     */
    fun streamCount(
        filter: QueryBuilder.FilterWithDTags,
        indexStrategy: IndexingStrategy,
    ): Int {
        if (!filter.isSimpleQuery()) return -1
        if (filter.ids != null) return -1
        if (filter.dTags != null) return -1
        if (filter.limit == null || filter.limit <= 0) return -1
        val authors = filter.authors ?: return -1
        if (authors.isEmpty()) return -1
        val kinds = filter.kinds
        val streams =
            if (kinds != null && kinds.isNotEmpty()) {
                authors.size * kinds.size
            } else {
                // authors-only needs the (pubkey, created_at) index to stream.
                if (!indexStrategy.indexEventsByPubkeyAlone) return -1
                authors.size
            }
        // A single stream is already the optimal single index seek — let the
        // normal path handle it; only merge when there's something to merge.
        return if (streams in 2..MAX_STREAMS) streams else -1
    }

    /** Prepares one bound, newest-first cursor per stream. */
    private fun prepareStreams(
        db: SQLiteConnection,
        filter: QueryBuilder.FilterWithDTags,
    ): List<SQLiteStatement> {
        val authors = filter.authors!!
        val kinds = filter.kinds?.takeIf { it.isNotEmpty() }
        val since = filter.since
        val until = filter.until

        val stmts = ArrayList<SQLiteStatement>((kinds?.size ?: 1) * authors.size)
        if (kinds != null) {
            val sql =
                buildString {
                    append("SELECT ").append(COLS)
                    append(" FROM event_headers INDEXED BY query_by_kind_pubkey_created")
                    append(" WHERE kind = ? AND pubkey = ?")
                    if (until != null) append(" AND created_at <= ?")
                    if (since != null) append(" AND created_at >= ?")
                    append(" ORDER BY created_at DESC")
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
                    append(" ORDER BY created_at DESC")
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

    /**
     * Runs the merge, calling [onRow] with each winning cursor positioned on
     * the row to emit, newest-first, up to `limit`. [onRow] must read the
     * current row (it stays valid until the next step).
     */
    fun run(
        db: SQLiteConnection,
        filter: QueryBuilder.FilterWithDTags,
        onRow: (SQLiteStatement) -> Unit,
    ) {
        val stmts = prepareStreams(db, filter)
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

            var emitted = 0
            val limit = filter.limit!!
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

                onRow(stmts[best]) // cursor is still on the head row
                emitted++

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
