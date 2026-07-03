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
 * Constraints, by design of the call sites:
 *  - **Not thread-safe** — same contract as the underlying connection,
 *    which the pool already serializes (single writer under a mutex).
 *  - **No overlapping use of the same SQL** — checking out one SQL string
 *    twice without closing the first use would alias one native handle.
 *    Insert/query paths never nest the same statement; a checkout while
 *    the previous one is still open falls back to an uncached statement.
 */
class StatementCachingConnection(
    private val delegate: SQLiteConnection,
    /**
     * Ceiling on retained statements. Query SQL embeds one `?` per filter
     * element, so shape variety is client-controlled — without a cap a
     * long-lived relay connection would accumulate native handles without
     * bound. Once full, unseen SQL just prepares uncached. 256 comfortably
     * covers the write path's fixed set plus the recurring filter shapes.
     */
    private val maxCachedStatements: Int = 256,
) : SQLiteConnection by delegate {
    private val cache = HashMap<String, CachedStatement>()

    override fun prepare(sql: String): SQLiteStatement {
        val cached =
            cache[sql] ?: run {
                if (cache.size >= maxCachedStatements) return delegate.prepare(sql)
                CachedStatement(delegate.prepare(sql)).also { cache[sql] = it }
            }
        if (cached.checkedOut) {
            // Same SQL prepared while the previous handle is still in use —
            // stay correct with a plain uncached statement.
            return delegate.prepare(sql)
        }
        cached.checkedOut = true
        cached.clearBindings()
        return cached
    }

    override fun close() {
        cache.values.forEach { runCatching { it.finalize() } }
        cache.clear()
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
