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

import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.ReqCmd
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.IRelayPolicy
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.PolicyResult
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One connection's SQL cursors: `SQL` opens one and sends its first page,
 * `FETCH` pulls the next, `SQL-CLOSE` drops it. A cursor ends when its
 * rows run out (`"done"`), on close, or on disconnect.
 *
 * [defaultPageSize] is the relay's default REQ limit; with none, the first
 * page carries every row, as an unlimited REQ would.
 *
 * Opening and stepping run on [Dispatchers.IO]: engines block the thread
 * (SQLite) or wait on the store. [clear] can run from another thread during
 * a FETCH, so each cursor has a [Mutex].
 */
class SqlCursorRegistry(
    private val engine: SqlEngine?,
    private val send: (Message) -> Unit,
    private val defaultPageSize: Int?,
) {
    private class OpenCursor(
        val id: String,
        val rows: SqlRows,
    ) {
        val lock = Mutex()

        /** Set once the cursor must end; a FETCH in flight sees it and finishes. */
        var finished = false

        /** The rows were closed (freeing their connection); happens exactly once. */
        var released = false
    }

    private val cursors = LargeCache<String, OpenCursor>()

    suspend fun open(
        cmd: SqlCmd,
        policy: IRelayPolicy,
    ) {
        if (engine == null) {
            send(ClosedMessage(cmd.queryId, "unsupported: this relay does not accept SQL"))
            return
        }

        // Same gate as a REQ for everything: auth requirements, allow/deny
        // lists and id limits all apply to SQL the way they apply to REQs.
        val gate = policy.accept(ReqCmd(cmd.queryId, listOf(Filter())))
        if (gate is PolicyResult.Rejected) {
            send(ClosedMessage(cmd.queryId, gate.reason))
            return
        }

        // Same id replaces the previous cursor, like a REQ.
        cursors.get(cmd.queryId)?.let { finishLocked(it) }

        val pageSize = cmd.pageSize ?: defaultPageSize ?: Int.MAX_VALUE
        val rows: SqlRows
        val firstPage: List<List<Any?>>
        try {
            val opened =
                withContext(Dispatchers.IO) {
                    val r = engine.open(cmd)
                    try {
                        r to r.fetch(pageSize)
                    } catch (e: Throwable) {
                        r.close()
                        throw e
                    }
                }
            rows = opened.first
            firstPage = opened.second
        } catch (e: CancellationException) {
            throw e
        } catch (e: SqlException) {
            send(ClosedMessage(cmd.queryId, e.message ?: "invalid: query"))
            return
        } catch (e: Exception) {
            send(ClosedMessage(cmd.queryId, "error: ${e.message}"))
            return
        }

        send(SqlColsMessage(cmd.queryId, rows.columns))
        send(SqlRowsMessage(cmd.queryId, firstPage, rows.isDone))

        if (rows.isDone) {
            rows.close()
        } else {
            cursors.put(cmd.queryId, OpenCursor(cmd.queryId, rows))
        }
    }

    suspend fun fetch(cmd: FetchCmd) {
        val entry = cursors.get(cmd.queryId)
        if (entry == null) {
            send(ClosedMessage(cmd.queryId, "error: no such cursor"))
            return
        }
        entry.lock.withLock {
            if (entry.finished) {
                finish(entry)
                send(ClosedMessage(cmd.queryId, "error: no such cursor"))
                return
            }
            val page =
                try {
                    withContext(Dispatchers.IO) { entry.rows.fetch(cmd.maxRows) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    finish(entry)
                    send(ClosedMessage(cmd.queryId, "error: ${e.message}"))
                    return
                }
            send(SqlRowsMessage(cmd.queryId, page, entry.rows.isDone))
            if (entry.rows.isDone || entry.finished) finish(entry)
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
     * Finishes a cursor from outside its lock. If a FETCH holds the lock,
     * its statement is mid-step on another thread and must not be closed
     * under it: mark it, and that FETCH finishes it after its page.
     */
    private fun finishLocked(entry: OpenCursor) {
        cursors.remove(entry.id)
        if (entry.lock.tryLock()) {
            try {
                finish(entry)
            } finally {
                entry.lock.unlock()
            }
        } else {
            entry.finished = true
        }
    }

    /** Caller holds [OpenCursor.lock]. Idempotent. */
    private fun finish(entry: OpenCursor) {
        entry.finished = true
        cursors.remove(entry.id)
        if (entry.released) return
        entry.released = true
        entry.rows.close()
    }
}
