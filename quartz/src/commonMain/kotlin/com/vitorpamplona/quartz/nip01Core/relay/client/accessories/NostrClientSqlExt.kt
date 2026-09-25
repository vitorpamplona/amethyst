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
package com.vitorpamplona.quartz.nip01Core.relay.client.accessories

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.AuthOutcome
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.DEFAULT_AUTH_GRACE_MS
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.authSuccessMark
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.awaitAuthOutcome
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.hasAuthResponder
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.ClosedMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.MachineReadablePrefix
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IdAndTime
import com.vitorpamplona.quartz.nipXXSql.FetchCmd
import com.vitorpamplona.quartz.nipXXSql.FilterSql
import com.vitorpamplona.quartz.nipXXSql.SqlCloseCmd
import com.vitorpamplona.quartz.nipXXSql.SqlCmd
import com.vitorpamplona.quartz.nipXXSql.SqlColsMessage
import com.vitorpamplona.quartz.nipXXSql.SqlRowsMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/** A whole SQL result. */
class SqlResult(
    val columns: List<String>,
    val rows: List<List<Any?>>,
)

/**
 * The relay refused or dropped a SQL query. [reason] is the `CLOSED`
 * text, with its NIP-01 prefix (`invalid:`, `unsupported:`, `error:`,
 * `auth-required:`, …), or a local reason for timeouts and disconnects.
 */
class SqlQueryException(
    val reason: String,
) : Exception(reason)

/**
 * Runs a read-only SQL query on [relay] (`SQL`, then `FETCH` until the
 * relay says `done`) and returns every row. For large results prefer
 * [sqlStream], which hands rows over a page at a time.
 *
 * @throws SqlQueryException when the relay refuses the query, drops the
 *   connection, or stays silent for [idleTimeoutMs].
 */
suspend fun INostrClient.sql(
    relay: NormalizedRelayUrl,
    query: String,
    params: List<Any?> = emptyList(),
    named: Map<String, Any?> = emptyMap(),
    pageSize: Int? = null,
    idleTimeoutMs: Long = 30_000,
): SqlResult {
    var columns = emptyList<String>()
    val rows = ArrayList<List<Any?>>()
    sqlStream(relay, query, params, named, pageSize, idleTimeoutMs, onColumns = { columns = it }) { rows.add(it) }
    return SqlResult(columns, rows)
}

/**
 * Runs a read-only SQL query on [relay], handing [onColumns] the column
 * names once and [onRow] every row as pages arrive. Each page after the
 * first is requested with `FETCH [pageSize]`, so the relay never sends more
 * than the caller has consumed. An abandoned query (cancellation, error)
 * sends `SQL-CLOSE` so the relay frees the cursor.
 *
 * Like REQ and COUNT, SQL is gated by NIP-42 on relays that require it:
 * with an auth responder registered, an `auth-required:` refusal waits for
 * the AUTH to land and re-sends the query once.
 *
 * @param pageSize rows per page; null lets the relay use its default REQ limit
 *   for the first page, and [DEFAULT_SQL_FETCH] for the rest.
 * @throws SqlQueryException see [sql].
 */
suspend fun INostrClient.sqlStream(
    relay: NormalizedRelayUrl,
    query: String,
    params: List<Any?> = emptyList(),
    named: Map<String, Any?> = emptyMap(),
    pageSize: Int? = null,
    idleTimeoutMs: Long = 30_000,
    onColumns: (List<String>) -> Unit = {},
    onRow: (List<Any?>) -> Unit,
) {
    val target = relay
    val incoming = Channel<Message>(UNLIMITED)

    // Each attempt gets its own id, so nothing a dead attempt still has in flight
    // (a page, a refusal, the drop notice itself) can be mistaken for the live one.
    var command = SqlCmd(newSubId(), query, params, named, pageSize)

    val live = LiveQueryId(command.queryId)
    val listener =
        object : RelayConnectionListener {
            override suspend fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                val id =
                    when (msg) {
                        is SqlColsMessage -> msg.queryId
                        is SqlRowsMessage -> msg.queryId
                        is ClosedMessage -> msg.subId
                        else -> return
                    }
                if (id == live.id && relay.url == target) incoming.trySend(msg)
            }

            override fun onDisconnected(relay: IRelayClient) {
                if (relay.url == target) incoming.trySend(ClosedMessage(live.id, DISCONNECTED))
            }
        }

    val relayClient = getOrCreateRelay(relay)
    var done = false
    addConnectionListener(listener)
    val keepAliveSubId = pinRelay(relay)
    try {
        relayClient.connect()
        withTimeoutOrNull(idleTimeoutMs) { connectedRelaysFlow().first { relay in it } }
            ?: throw SqlQueryException("error: could not connect to $relay within ${idleTimeoutMs}ms")

        val pendingOnAuthRequired = hasAuthResponder()
        var authMark = if (pendingOnAuthRequired) authSuccessMark(relay) else 0
        var retriedAfterAuth = false
        var rowsHandedOver = false
        var reconnects = 0

        fun droppedBeforeRows(msg: Message?) = msg is ClosedMessage && msg.message == DISCONNECTED && msg.subId == command.queryId && !rowsHandedOver

        // The cursor died with the socket. Nothing handed over yet (the client's own
        // reconnect sweep lands here, as do blips): wait for the socket and start over
        // under a new id. Once rows are out, starting over could repeat them: it fails.
        suspend fun startOver() {
            if (reconnects++ >= MAX_SQL_RECONNECTS) throw SqlQueryException(DISCONNECTED)
            command = SqlCmd(newSubId(), query, params, named, pageSize)
            live.id = command.queryId
            withTimeoutOrNull(idleTimeoutMs) { connectedRelaysFlow().first { relay in it } }
                ?: throw SqlQueryException("error: could not reconnect to $relay within ${idleTimeoutMs}ms")
            // A new socket is a new NIP-42 session: the auth retry is owed again.
            if (pendingOnAuthRequired) authMark = authSuccessMark(relay)
            retriedAfterAuth = false
            relayClient.sendIfConnected(command)
        }

        relayClient.sendIfConnected(command)

        while (!done) {
            val msg =
                withTimeoutOrNull(idleTimeoutMs) { incoming.receive() }
                    ?: throw SqlQueryException("error: no answer from $relay within ${idleTimeoutMs}ms")
            val msgId =
                when (msg) {
                    is SqlColsMessage -> msg.queryId
                    is SqlRowsMessage -> msg.queryId
                    is ClosedMessage -> msg.subId
                    else -> null
                }
            // Queued before the attempt it belongs to was abandoned.
            if (msgId != command.queryId) continue

            when (msg) {
                is SqlColsMessage -> {
                    onColumns(msg.columns)
                }

                is SqlRowsMessage -> {
                    if (msg.rows.isNotEmpty()) rowsHandedOver = true
                    msg.rows.forEach(onRow)
                    if (msg.done) {
                        done = true
                    } else {
                        relayClient.sendIfConnected(FetchCmd(command.queryId, pageSize ?: DEFAULT_SQL_FETCH))
                    }
                }

                is ClosedMessage if droppedBeforeRows(msg) -> {
                    startOver()
                }

                is ClosedMessage -> {
                    val authWall = MachineReadablePrefix.parse(msg.message) == MachineReadablePrefix.AUTH_REQUIRED
                    val outcome =
                        if (authWall && pendingOnAuthRequired && !retriedAfterAuth) {
                            awaitAuthOutcome(relay, authMark, DEFAULT_AUTH_GRACE_MS, idleTimeoutMs)
                        } else {
                            null
                        }
                    when {
                        outcome == AuthOutcome.AUTHENTICATED -> {
                            retriedAfterAuth = true
                            relayClient.sendIfConnected(command)
                        }

                        // A socket dropping under the AUTH reads as a refusal too. Its drop notice is
                        // raised on the same disconnect, a hop behind the auth state: wait for it.
                        outcome != null && droppedBeforeRows(withTimeoutOrNull(DROP_NOTICE_GRACE_MS) { incoming.receive() }) -> {
                            startOver()
                        }

                        else -> {
                            done = true
                            throw SqlQueryException(msg.message)
                        }
                    }
                }

                else -> {}
            }
        }
    } finally {
        removeConnectionListener(listener)
        incoming.close()
        if (!done) relayClient.sendIfConnected(SqlCloseCmd(command.queryId))
        unsubscribe(keepAliveSubId)
    }
}

/**
 * Keeps [relay] in the pool's desired set while a SQL exchange runs: `SQL` is
 * not a REQ, so without a subscription the pool sees the relay as unwanted and
 * drops the socket between pages. The filter matches nothing. Returns the
 * subscription id to [INostrClient.unsubscribe] when done.
 */
private fun INostrClient.pinRelay(relay: NormalizedRelayUrl): String {
    val subId = newSubId()
    subscribe(subId, mapOf(relay to listOf(Filter(ids = listOf(KEEP_ALIVE_ID)))), null)
    return subId
}

/**
 * `(created_at, id)` of every event [filter] matches on [relay]'s raw store,
 * newest first — a REQ without the relay's result cap, ranking or live tail,
 * sized for NIP-77 snapshots. See [FilterSql].
 *
 * @throws SqlQueryException see [sql].
 */
suspend fun INostrClient.sqlIdsAndTimes(
    relay: NormalizedRelayUrl,
    filter: Filter,
    pageSize: Int? = DEFAULT_SQL_FETCH,
    idleTimeoutMs: Long = 30_000,
): List<IdAndTime> {
    if (FilterSql.matchesNothing(filter)) return emptyList()
    val q = FilterSql.ids(filter)
    val out = ArrayList<IdAndTime>()
    sqlStream(relay, q.sql, q.params, pageSize = pageSize, idleTimeoutMs = idleTimeoutMs) { out.add(IdAndTime((it[1] as Number).toLong(), it[0] as String)) }
    return out
}

/**
 * How many events [filter] matches on [relay]'s raw store (NIP-45 semantics:
 * `limit` ignored), as one SQL `count`.
 *
 * @throws SqlQueryException see [sql].
 */
suspend fun INostrClient.sqlCount(
    relay: NormalizedRelayUrl,
    filter: Filter,
    idleTimeoutMs: Long = 30_000,
): Long {
    if (FilterSql.matchesNothing(filter.copy(limit = null))) return 0
    val q = FilterSql.count(filter)
    return (sql(relay, q.sql, q.params, idleTimeoutMs = idleTimeoutMs).rows.single()[0] as Number).toLong()
}

/**
 * Every event [filter] matches on [relay]'s raw store, newest first: the ids
 * with [sqlIdsAndTimes], then the events [FilterSql.HYDRATE_CHUNK] at a time.
 *
 * @throws SqlQueryException see [sql].
 */
suspend fun INostrClient.sqlQuery(
    relay: NormalizedRelayUrl,
    filter: Filter,
    idleTimeoutMs: Long = 30_000,
): List<Event> {
    val out = ArrayList<Event>()
    sqlQuery(relay, filter, idleTimeoutMs) { out.add(it) }
    return out
}

/** [sqlQuery], handing each event to [onEach] a chunk at a time. */
suspend fun INostrClient.sqlQuery(
    relay: NormalizedRelayUrl,
    filter: Filter,
    idleTimeoutMs: Long = 30_000,
    onEach: (Event) -> Unit,
) {
    // Pinned across the steps too, so the socket survives the gaps between them.
    val keepAliveSubId = pinRelay(relay)
    try {
        val ids = sqlIdsAndTimes(relay, filter, idleTimeoutMs = idleTimeoutMs)
        for (chunk in ids.chunked(FilterSql.HYDRATE_CHUNK)) {
            val q = FilterSql.hydrate(chunk.map { it.id })
            val collector = FilterSql.Collector()
            sqlStream(relay, q.sql, q.params, pageSize = DEFAULT_SQL_FETCH, idleTimeoutMs = idleTimeoutMs) { collector.add(it) }
            collector.finish().forEach(onEach)
        }
    } finally {
        unsubscribe(keepAliveSubId)
    }
}

/** The attempt the socket listener routes frames to; read on the socket's thread. */
private class LiveQueryId(
    @Volatile var id: String,
)

/** The local reason a query ends with when its socket drops. */
private const val DISCONNECTED = "error: relay disconnected"

/** How long an AUTH refusal waits for the drop notice that would make it a disconnect instead. */
private const val DROP_NOTICE_GRACE_MS = 500L

/** Re-sends of a query whose socket dropped before its first row. */
private const val MAX_SQL_RECONNECTS = 3

/** Rows per `FETCH` when the caller doesn't choose a page size. */
const val DEFAULT_SQL_FETCH = 500
