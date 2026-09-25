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

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_FULLMUTEX
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.execSQL
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.TagNameValueHasher
import kotlinx.coroutines.channels.Channel
import kotlin.concurrent.Volatile

/**
 * The relay side of the SQL profile: read-only connections for SQL
 * cursors, separate from the event store's REQ readers. Each open cursor
 * holds one; closed cursors return it for reuse.
 */
class SqlQueryService(
    private val openConnection: () -> SQLiteConnection,
    /** Builds the table sources, reading whatever it needs (e.g. the hash seed) from a connection. Called once. */
    private val buildSources: (SQLiteConnection) -> SqlTableSources = { EventStoreTableSources.sources },
) : AutoCloseable {
    private val idle = Channel<SQLiteConnection>(Channel.UNLIMITED)

    @Volatile
    private var closed = false

    @Volatile
    private var sources: SqlTableSources? = null

    /** Compiles [cmd]; [conn] is only read the first time, to build the table sources. */
    fun compile(
        cmd: SqlCmd,
        conn: SQLiteConnection,
    ): CompiledQuery {
        val src = sources ?: buildSources(conn).also { sources = it }
        return SqlCompiler.compile(cmd.sql, src, cmd.params, cmd.named)
    }

    fun acquire(): SQLiteConnection {
        check(!closed) { "SQL service is closed" }
        return idle.tryReceive().getOrNull() ?: openConnection()
    }

    fun release(conn: SQLiteConnection) {
        if (closed || idle.trySend(conn).isFailure) conn.close()
    }

    /** Closes idle connections; ones still held by cursors close when released. */
    override fun close() {
        closed = true
        while (true) {
            val conn = idle.tryReceive().getOrNull() ?: break
            conn.close()
        }
    }

    companion object {
        /**
         * SQL over [store] when it is a file-backed SQLite store, else null.
         * Connections open read-only and with `query_only` on, so the
         * database refuses writes even if something got past the compiler.
         * An in-memory database can't be shared across connections.
         */
        fun forStore(store: IEventStore): SqlQueryService? {
            val sqlite = (store as? EventStore)?.store ?: return null
            val dbName = sqlite.dbName ?: return null
            val driver = sqlite.driver
            return SqlQueryService(
                openConnection = {
                    val conn =
                        if (driver is BundledSQLiteDriver) {
                            driver.open(dbName, SQLITE_OPEN_READONLY or SQLITE_OPEN_FULLMUTEX)
                        } else {
                            driver.open(dbName)
                        }
                    try {
                        conn.execSQL("PRAGMA query_only = ON")
                        conn.execSQL("PRAGMA busy_timeout = 5000")
                    } catch (e: Throwable) {
                        conn.close()
                        throw e
                    }
                    conn
                },
                buildSources = { conn ->
                    EventStoreTableSources.forStore(TagNameValueHasher(sqlite.seedModule.getSeed(conn)), sqlite.indexStrategy)
                },
            )
        }
    }
}
