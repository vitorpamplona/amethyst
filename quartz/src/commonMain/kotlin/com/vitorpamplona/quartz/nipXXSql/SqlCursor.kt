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

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * A server-side cursor: one prepared statement stepped a page at a time,
 * which is what a `FETCH` frame drives. Rows come back as `Long`,
 * `Double`, `String` or `null`.
 *
 * Holding a cursor open keeps a read transaction open on [connection],
 * which stops WAL checkpoints from resetting the log. Callers must bound
 * its lifetime (idle timeout, max age) and [close] it.
 *
 * Not thread-safe; one cursor per connection at a time, like any
 * `SQLiteStatement`.
 */
class SqlCursor(
    connection: SQLiteConnection,
    query: CompiledQuery,
) : AutoCloseable {
    private val stmt: SQLiteStatement = connection.prepare(query.sql)

    val columns: List<String>

    var isDone = false
        private set

    init {
        try {
            query.args.forEachIndexed { i, v ->
                val index = i + 1
                when (v) {
                    null -> stmt.bindNull(index)
                    is Long -> stmt.bindLong(index, v)
                    is Double -> stmt.bindDouble(index, v)
                    is String -> stmt.bindText(index, v)
                    else -> stmt.bindText(index, v.toString())
                }
            }
            columns = List(stmt.getColumnCount()) { stmt.getColumnName(it) }
        } catch (e: Throwable) {
            stmt.close()
            throw e
        }
    }

    /**
     * Steps up to [max] rows, stopping early (with at least one row) once
     * [budget] has elapsed, so a slow producer returns a short page rather
     * than holding the caller. The budget is checked between rows only: one
     * step that takes long (a big aggregate or sort) can't be cut short.
     * After the last row [isDone] turns true and the statement is released.
     */
    fun fetch(
        max: Int,
        budget: Duration = Duration.INFINITE,
    ): List<List<Any?>> {
        if (isDone) return emptyList()
        val start = TimeSource.Monotonic.markNow()
        val rows = ArrayList<List<Any?>>(minOf(max, 1024))
        while (rows.size < max) {
            if (rows.isNotEmpty() && start.elapsedNow() >= budget) break
            if (!stmt.step()) {
                close()
                break
            }
            rows.add(
                List(columns.size) { i ->
                    when (stmt.getColumnType(i)) {
                        SQLITE_DATA_NULL -> null
                        SQLITE_DATA_INTEGER -> stmt.getLong(i)
                        SQLITE_DATA_FLOAT -> stmt.getDouble(i)
                        SQLITE_DATA_BLOB -> stmt.getBlob(i).toHexKey()
                        else -> stmt.getText(i)
                    }
                },
            )
        }
        return rows
    }

    override fun close() {
        if (!isDone) {
            isDone = true
            stmt.close()
        }
    }
}
