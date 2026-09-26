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
package com.vitorpamplona.quartz.nipXXSql

import com.vitorpamplona.quartz.nip01Core.store.IEventStore

/** A result column: its name and NQL type (never [NqlType.NULL]). */
class NqlColumn(
    val name: String,
    val type: NqlType,
)

/**
 * An NQL answer. [truncated] says the relay stopped at its row cap: [rows]
 * are the first rows of the result, in its ORDER BY order.
 */
class NqlResult(
    val columns: List<NqlColumn>,
    val rows: List<List<Any?>>,
    val truncated: Boolean = false,
)

/**
 * NQL (NIP-FF, Nostr Query Language): a strictly typed, read-only query
 * language over a store's events, seen as the `events` and `tags` sources.
 * Parameters and values are `Long` (INTEGER), `Double` (REAL), `String`
 * (TEXT), `Boolean` (BOOLEAN) or null.
 */
object Nql {
    /**
     * Runs [query] with [params] over [backend]. With [maxRows], stops there
     * and marks the result [NqlResult.truncated] when more rows remain.
     *
     * @throws SqlException `invalid` for a query that breaks NIP-FF,
     *   `unsupported` for one the store declines to scan, `error` when
     *   evaluation fails.
     */
    suspend fun run(
        query: String,
        params: List<Any?>,
        backend: SqlStoreBackend,
        maxRows: Int? = null,
    ): NqlResult {
        val values = params.map(::normalize)
        val q = NqlChecker.check(query, values)
        val rows = NqlExecutor(backend, values).run(q)
        val columns = q.outputs.map { NqlColumn(it.name ?: "", it.type) }
        val truncated = maxRows != null && rows.size > maxRows
        val kept = if (truncated) rows.subList(0, maxRows!!) else rows
        return NqlResult(columns, kept.map { it.asList() }, truncated)
    }

    /** Checks [query] with [params] without running it: the result's columns, or `invalid`. */
    fun columns(
        query: String,
        params: List<Any?>,
    ): List<NqlColumn> = NqlChecker.check(query, params.map(::normalize)).outputs.map { NqlColumn(it.name ?: "", it.type) }

    /** Parses and checks [query] with [params]: the checked query and the parameters as NQL values. */
    fun prepare(
        query: String,
        params: List<Any?>,
    ): Pair<NqlQuery, List<Any?>> {
        val values = params.map(::normalize)
        return NqlChecker.check(query, values) to values
    }

    /** `Int` to `Long`, `Float` to `Double`; anything else that isn't a value is refused. */
    private fun normalize(v: Any?): Any? =
        when (v) {
            null, is Long, is Double, is String, is Boolean -> v
            is Int -> v.toLong()
            is Short -> v.toLong()
            is Byte -> v.toLong()
            is Float -> v.toDouble()
            else -> throw SqlException.invalid("parameters are strings, numbers, booleans or null")
        }
}

/** How a relay answers `NQL` commands. */
fun interface NqlEngine {
    suspend fun run(
        query: String,
        params: List<Any?>,
        maxRows: Int?,
    ): NqlResult

    companion object {
        /** NQL over [store], through its [IEventStore.nql]. */
        fun forStore(store: IEventStore): NqlEngine = NqlEngine { query, params, maxRows -> store.nql(query, params, maxRows) }
    }
}
