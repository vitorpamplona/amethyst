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
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip01Core.store.sqlite.SQLiteEventStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SqlLimits(
    /** Relay-wide open cursors. Each holds one dedicated connection, so this is also the pool size. */
    val maxOpenCursors: Int = 4,
    val maxCursorsPerSession: Int = 2,
    /** Rows in the first page when the client doesn't ask for a size. */
    val defaultPageRows: Int = 500,
    /** Clamp for the first page and for every FETCH. */
    val maxPageRows: Int = 5_000,
    /** A page is cut short (keeping at least one row) once this much time went into it. */
    val pageTimeBudget: Duration = 250.milliseconds,
    /** A cursor with no FETCH for this long is closed. */
    val idleTimeout: Duration = 30.seconds,
    /** A cursor is closed this long after it opened, however active. */
    val maxLifetime: Duration = 5.minutes,
    /** Every recursive CTE must end with `LIMIT n`, n ≤ this. */
    val maxRecursiveRows: Long = 100_000,
    val maxQueryLength: Int = 16 * 1024,
)

/**
 * The relay side of the SQL profile: a small pool of **dedicated,
 * read-only** connections (separate from the event store's REQ readers, so
 * a slow SQL query can at worst stall other SQL cursors, never REQs) and
 * the per-session [tableSources] that decide what `events` / `tags`
 * contain for whoever is asking — the SQL equivalent of the REQ policy.
 *
 * Relays opt in by passing one of these to the server; without it SQL
 * frames are answered `unsupported`. The server that receives it owns it
 * and closes it on shutdown.
 */
class SqlQueryService(
    private val openConnection: () -> SQLiteConnection,
    val limits: SqlLimits = SqlLimits(),
    private val tableSources: (RequestContext) -> SqlTableSources = { EventStoreTableSources.build() },
) : AutoCloseable {
    private val permits = Semaphore(limits.maxOpenCursors)
    private val idle = Channel<SQLiteConnection>(Channel.UNLIMITED)

    @Volatile
    private var closed = false

    fun compile(
        ctx: RequestContext,
        cmd: SqlCmd,
    ): CompiledQuery {
        if (cmd.sql.length > limits.maxQueryLength) {
            throw SqlException.unsupported("query longer than ${limits.maxQueryLength} characters")
        }
        return SqlCompiler.compile(cmd.sql, tableSources(ctx), cmd.params, cmd.named, limits.maxRecursiveRows)
    }

    /** A connection for one cursor, or null when every one is busy. Never waits. */
    fun tryAcquire(): SQLiteConnection? {
        if (closed || !permits.tryAcquire()) return null
        return try {
            idle.tryReceive().getOrNull() ?: openConnection()
        } catch (e: Throwable) {
            permits.release()
            throw e
        }
    }

    fun release(conn: SQLiteConnection) {
        if (closed || idle.trySend(conn).isFailure) conn.close()
        permits.release()
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
         * SQL over a file-backed [SQLiteEventStore]. Connections open
         * read-only and with `query_only` on, so the database refuses writes
         * even if something got past the compiler. An in-memory store can't
         * be shared across connections and is refused.
         */
        fun forStore(
            store: SQLiteEventStore,
            limits: SqlLimits = SqlLimits(),
            tableSources: (RequestContext) -> SqlTableSources = { EventStoreTableSources.build() },
        ): SqlQueryService {
            val dbName = requireNotNull(store.dbName) { "SQL needs a file-backed event store" }
            val driver = store.driver
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
                limits = limits,
                tableSources = tableSources,
            )
        }
    }
}
