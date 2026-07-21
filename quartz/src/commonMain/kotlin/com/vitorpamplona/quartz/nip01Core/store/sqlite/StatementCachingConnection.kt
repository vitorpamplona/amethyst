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
 * A [SQLiteConnection] decorator that keeps every prepared statement and
 * hands the same handle back on the next [prepare] of the same SQL. The
 * event-ingest hot path prepares the identical INSERT statements once per
 * event; sqlite3_prepare is pure overhead the second time around.
 *
 * Callers keep their idiomatic `prepare(sql).use { … }` blocks untouched:
 * the statement wrapper turns `close()` into a return-to-cache no-op (the
 * real reset + clearBindings happens on the next checkout). Statements are
 * only truly finalized when the connection itself closes.
 *
 * Each SQL string caches a small **pool** of handles rather than a single
 * one, so overlapping checkouts of the *same* SQL all reuse cached handles.
 * That is exactly the k-way-merge query shape ([MergeQueryExecutor]): it
 * opens one identical-SQL cursor per author/tag stream — dozens to hundreds
 * live at once — which a single-handle cache could not serve (every stream
 * past the first fell back to an uncached prepare). The pool lets a repeated
 * follow-feed / reactions-watcher REQ reuse its per-stream cursors instead
 * of re-preparing them each poll.
 *
 * Constraints, by design of the call sites:
 *  - **Not thread-safe** — same contract as the underlying connection,
 *    which the pool already serializes (single writer under a mutex; each
 *    reader held by one coroutine at a time).
 */
class StatementCachingConnection(
    private val delegate: SQLiteConnection,
    /**
     * Ceiling on retained statements across all SQL strings. Query SQL
     * embeds one `?` per filter element, so shape variety is
     * client-controlled — without a cap a long-lived relay connection would
     * accumulate native handles without bound. Once full, unseen SQL (or an
     * extra concurrent copy of a cached SQL) just prepares uncached. 512
     * covers the write path's fixed set, the recurring single-shot filter
     * shapes, and a few hundred concurrent per-stream merge cursors.
     */
    private val maxCachedStatements: Int = 512,
) : SQLiteConnection by delegate {
    // One reusable pool per SQL string. Several entries of the same pool may
    // be checked out simultaneously (the merge path); a `prepare` reuses the
    // first free entry, grows the pool while under the global cap, and only
    // then falls back to an uncached statement.
    private val cache = HashMap<String, ArrayList<CachedStatement>>()
    private var cachedCount = 0

    override fun prepare(sql: String): SQLiteStatement {
        val pool = cache[sql]
        if (pool != null) {
            for (i in pool.indices) {
                val stmt = pool[i]
                if (!stmt.checkedOut) {
                    stmt.checkedOut = true
                    stmt.clearBindings()
                    return stmt
                }
            }
            // Every pooled handle for this SQL is in use — grow if the global
            // budget allows, else serve an uncached statement.
            if (cachedCount >= maxCachedStatements) return delegate.prepare(sql)
            return CachedStatement(delegate.prepare(sql)).also {
                it.checkedOut = true
                pool.add(it)
                cachedCount++
            }
        }
        if (cachedCount >= maxCachedStatements) return delegate.prepare(sql)
        return CachedStatement(delegate.prepare(sql)).also {
            it.checkedOut = true
            cache[sql] = arrayListOf(it)
            cachedCount++
        }
    }

    override fun close() {
        cache.values.forEach { pool -> pool.forEach { runCatching { it.finalize() } } }
        cache.clear()
        cachedCount = 0
        delegate.close()
    }

    private class CachedStatement(
        private val delegate: SQLiteStatement,
    ) : SQLiteStatement by delegate {
        var checkedOut = false

        /**
         * Return to cache. The real handle stays prepared, but must be
         * reset *now*: an un-reset statement keeps its cursor (and its
         * table read locks) open, which turns a later `DELETE`/`DROP` on
         * the same connection into `SQLITE_LOCKED: database table is
         * locked`.
         */
        override fun close() {
            runCatching { delegate.reset() }
            checkedOut = false
        }

        fun finalize() = delegate.close()
    }
}
