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
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.server.backend.RequestContext
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * One connection's SQL cursors: `SQL` opens one and sends its first page,
 * `FETCH` pulls the next, `SQL-CLOSE` drops it. A cursor ends when its
 * rows run out (`"done"`), on close, on disconnect, or when it sits idle
 * or outlives [SqlLimits.maxLifetime] (`CLOSED "closed: cursor expired"`).
 *
 * Stepping runs on [Dispatchers.IO]: SQLite blocks the thread. An expiry
 * timer can race a FETCH, so each cursor has its own [Mutex]; commands on
 * one connection already arrive one at a time.
 */
class SqlCursorRegistry(
    private val service: SqlQueryService?,
    private val ctx: RequestContext,
    private val send: (Message) -> Unit,
    private val scope: CoroutineScope,
) {
    private class OpenCursor(
        val id: String,
        val cursor: SqlCursor,
        val conn: SQLiteConnection,
    ) {
        val lock = Mutex()
        val born: TimeMark = TimeSource.Monotonic.markNow()
        var lastUsed: TimeMark = born
        var timer: Job? = null

        /** Set once the cursor must end; a FETCH in flight sees it and finishes. */
        var finished = false

        /** The connection went back to the pool; happens exactly once. */
        var released = false
    }

    private val cursors = LargeCache<String, OpenCursor>()

    val openCount: Int get() = cursors.size()

    suspend fun open(
        cmd: SqlCmd,
        policy: IRelayPolicy,
    ) {
        if (service == null) {
            send(ClosedMessage(cmd.queryId, "unsupported: this relay does not accept SQL"))
            return
        }
        policy.acceptSql(cmd)?.let { reason ->
            send(ClosedMessage(cmd.queryId, reason))
            return
        }

        // Same id replaces the previous cursor, like a REQ.
        cursors.remove(cmd.queryId)?.let { finishLocked(it) }

        val limits = service.limits
        if (cursors.size() >= limits.maxCursorsPerSession) {
            send(ClosedMessage(cmd.queryId, "blocked: too many open cursors on this connection"))
            return
        }

        val compiled =
            try {
                service.compile(ctx, cmd)
            } catch (e: SqlException) {
                send(ClosedMessage(cmd.queryId, e.message ?: "invalid: query"))
                return
            }

        val conn =
            try {
                service.tryAcquire()
            } catch (e: Exception) {
                send(ClosedMessage(cmd.queryId, "error: ${e.message}"))
                return
            }
        if (conn == null) {
            send(ClosedMessage(cmd.queryId, "blocked: all SQL cursors are busy, try again later"))
            return
        }

        val pageSize = (cmd.pageSize ?: limits.defaultPageRows).coerceIn(1, limits.maxPageRows)
        val opened =
            try {
                withContext(Dispatchers.IO) {
                    val cursor = SqlCursor(conn, compiled)
                    try {
                        cursor to cursor.fetch(pageSize, limits.pageTimeBudget)
                    } catch (e: Throwable) {
                        cursor.close()
                        throw e
                    }
                }
            } catch (e: CancellationException) {
                service.release(conn)
                throw e
            } catch (e: Exception) {
                service.release(conn)
                send(ClosedMessage(cmd.queryId, "error: ${e.message}"))
                return
            }

        val (cursor, firstPage) = opened
        send(SqlColsMessage(cmd.queryId, cursor.columns))
        send(SqlRowsMessage(cmd.queryId, firstPage, cursor.isDone))

        if (cursor.isDone) {
            service.release(conn)
        } else {
            val entry = OpenCursor(cmd.queryId, cursor, conn)
            cursors.put(cmd.queryId, entry)
            arm(entry)
        }
    }

    suspend fun fetch(cmd: FetchCmd) {
        val entry = cursors.get(cmd.queryId)
        if (entry == null || service == null) {
            send(ClosedMessage(cmd.queryId, "error: no such cursor"))
            return
        }
        val limits = service.limits
        entry.lock.withLock {
            if (entry.finished) {
                finish(entry)
                send(ClosedMessage(cmd.queryId, "error: no such cursor"))
                return
            }
            entry.lastUsed = TimeSource.Monotonic.markNow()
            val page =
                try {
                    withContext(Dispatchers.IO) { entry.cursor.fetch(cmd.maxRows.coerceAtMost(limits.maxPageRows), limits.pageTimeBudget) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    finish(entry)
                    send(ClosedMessage(cmd.queryId, "error: ${e.message}"))
                    return
                }
            send(SqlRowsMessage(cmd.queryId, page, entry.cursor.isDone))
            if (entry.cursor.isDone || entry.finished) finish(entry) else arm(entry)
        }
    }

    suspend fun close(cmd: SqlCloseCmd) {
        val entry = cursors.get(cmd.queryId) ?: return
        entry.lock.withLock { finish(entry) }
    }

    /** Drops every cursor; called when the connection goes away. Non-suspending. */
    fun clear() {
        val all = ArrayList<OpenCursor>()
        cursors.forEach { _, entry -> all.add(entry) }
        all.forEach { finishLocked(it) }
    }

    /**
     * Finishes a cursor from outside its lock. If a FETCH holds the lock
     * right now, its statement is mid-step on another thread and must not
     * be closed under it: mark it and let that FETCH's own finish run.
     */
    private fun finishLocked(entry: OpenCursor) {
        cursors.remove(entry.id)
        entry.timer?.cancel()
        if (entry.lock.tryLock()) {
            try {
                finish(entry)
            } finally {
                entry.lock.unlock()
            }
        } else {
            // The FETCH in flight checks this after its page and finishes.
            entry.finished = true
        }
    }

    /** Caller holds [OpenCursor.lock]. Idempotent. */
    private fun finish(entry: OpenCursor) {
        entry.finished = true
        cursors.remove(entry.id)
        entry.timer?.cancel()
        if (entry.released) return
        entry.released = true
        entry.cursor.close()
        service?.release(entry.conn)
    }

    /** (Re)schedules expiry at the earlier of the idle and lifetime deadlines. Caller holds the lock. */
    private fun arm(entry: OpenCursor) {
        val limits = service?.limits ?: return
        if (entry.finished) {
            // clear() marked it while the lock was held elsewhere.
            finish(entry)
            return
        }
        entry.timer?.cancel()
        val wait = minOf(limits.idleTimeout - entry.lastUsed.elapsedNow(), limits.maxLifetime - entry.born.elapsedNow())
        entry.timer =
            scope.launch {
                delay(wait)
                entry.lock.withLock {
                    if (entry.finished) {
                        finish(entry)
                        return@launch
                    }
                    val idleLeft = limits.idleTimeout - entry.lastUsed.elapsedNow()
                    val lifeLeft = limits.maxLifetime - entry.born.elapsedNow()
                    if (idleLeft.isPositive() && lifeLeft.isPositive()) {
                        arm(entry)
                    } else {
                        finish(entry)
                        send(ClosedMessage(entry.id, "closed: cursor expired"))
                    }
                }
            }
    }
}
