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
import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Room-style connection pool for an `androidx.sqlite` database.
 *
 * `androidx.sqlite.SQLiteConnection` is not thread-safe (same contract as
 * `sqlite3*` in the C API): a single connection may only be used by one
 * thread at a time. Two coroutines hitting the same connection in parallel
 * race on `BEGIN IMMEDIATE` and prepared-statement state, which surfaces
 * as `SQLITE_ERROR: cannot start a transaction within a transaction` or
 * `SQLITE_MISUSE`.
 *
 * The pool mirrors what Room does:
 *
 *  - **One writer connection**, guarded by a coroutine [Mutex]. SQLite
 *    only allows a single writer at the file level anyway, so serialising
 *    writes here costs nothing — it just queues callers cooperatively
 *    instead of crashing them.
 *  - **N reader connections**, handed out from a [Channel] that doubles
 *    as a semaphore. Under WAL (`PRAGMA journal_mode = WAL`) readers run
 *    in parallel with the writer and with each other.
 *
 * For in-memory databases (`dbName == null`) every fresh `:memory:`
 * connection opens a *separate* database, so the pool degrades to a
 * single-connection mode where readers also acquire the writer mutex.
 * That still fixes the parallel-insert crash; it just sacrifices reader
 * concurrency for an in-memory store.
 *
 * Lifecycle:
 *  1. `init` opens the writer, runs [onConfigure] on it, then [onMigrate]
 *     so schema exists before any reader sees the file.
 *  2. Readers are opened next and each gets [onConfigure] (PRAGMAs are
 *     per-connection in SQLite — `journal_mode=WAL` is the only
 *     database-wide one; subsequent connections inherit it).
 *  3. [close] drains the reader channel and closes every connection.
 */
class SQLiteConnectionPool(
    val driver: SQLiteDriver,
    val dbName: String?,
    val numReaders: Int = 4,
    val onConfigure: (SQLiteConnection) -> Unit = {},
    val onMigrate: (SQLiteConnection) -> Unit = {},
) : AutoCloseable {
    private val isInMemory = dbName == null

    private val writerMutex = Mutex()
    val writer: SQLiteConnection

    private val readers: List<SQLiteConnection>
    private val readerChannel: Channel<SQLiteConnection>?

    init {
        writer = openConnection()
        onMigrate(writer)

        if (isInMemory) {
            readers = emptyList()
            readerChannel = null
        } else {
            readers = List(numReaders) { openConnection() }
            readerChannel = Channel(numReaders)
            readers.forEach { readerChannel.trySend(it) }
        }
    }

    private fun openConnection(): SQLiteConnection {
        val db = driver.open(dbName ?: ":memory:")
        onConfigure(db)
        return db
    }

    /**
     * Acquire the writer connection for the duration of [block]. Other
     * writers (and, in the in-memory single-connection mode, readers)
     * suspend until the lock is released. Cancellation-aware via the
     * coroutine [Mutex].
     */
    suspend fun <T> useWriter(block: (SQLiteConnection) -> T): T =
        writerMutex.withLock {
            block(writer)
        }

    /**
     * Acquire any free reader connection for [block]. With a file-backed
     * DB up to [numReaders] readers run in parallel with the writer
     * (WAL). With an in-memory DB this falls back to the writer mutex
     * because each `:memory:` connection would be a separate database.
     */
    suspend fun <T> useReader(block: (SQLiteConnection) -> T): T {
        val ch =
            readerChannel
                ?: return writerMutex.withLock { block(writer) }
        val conn = ch.receive()
        try {
            return block(conn)
        } finally {
            // Capacity == numReaders and we own the conn we received, so
            // trySend never fails unless the channel was closed mid-flight
            // (in which case the connection is being torn down anyway).
            ch.trySend(conn)
        }
    }

    override fun close() {
        readerChannel?.close()
        readers.forEach { runCatching { it.close() } }
        runCatching { writer.close() }
    }
}
