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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nipXXSql.FetchCmd
import com.vitorpamplona.quartz.nipXXSql.SqlCloseCmd
import com.vitorpamplona.quartz.nipXXSql.SqlCmd
import com.vitorpamplona.quartz.nipXXSql.SqlColsMessage
import com.vitorpamplona.quartz.nipXXSql.SqlRowsMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

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
    val queryId = newSubId()
    val target = relay
    val incoming = Channel<Message>(UNLIMITED)
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
                if (id == queryId && relay.url == target) incoming.trySend(msg)
            }

            override fun onDisconnected(relay: IRelayClient) {
                if (relay.url == target) incoming.trySend(ClosedMessage(queryId, "error: relay disconnected"))
            }
        }

    val relayClient = getOrCreateRelay(relay)
    var done = false
    addConnectionListener(listener)
    try {
        relayClient.connect()
        withTimeoutOrNull(idleTimeoutMs) { connectedRelaysFlow().first { relay in it } }
            ?: throw SqlQueryException("error: could not connect to $relay within ${idleTimeoutMs}ms")

        val command = SqlCmd(queryId, query, params, named, pageSize)
        val pendingOnAuthRequired = hasAuthResponder()
        val authMark = if (pendingOnAuthRequired) authSuccessMark(relay) else 0
        var retriedAfterAuth = false
        relayClient.sendIfConnected(command)

        while (!done) {
            val msg =
                withTimeoutOrNull(idleTimeoutMs) { incoming.receive() }
                    ?: throw SqlQueryException("error: no answer from $relay within ${idleTimeoutMs}ms")
            when (msg) {
                is SqlColsMessage -> {
                    onColumns(msg.columns)
                }

                is SqlRowsMessage -> {
                    msg.rows.forEach(onRow)
                    if (msg.done) {
                        done = true
                    } else {
                        relayClient.sendIfConnected(FetchCmd(queryId, pageSize ?: DEFAULT_SQL_FETCH))
                    }
                }

                is ClosedMessage -> {
                    val authWall = MachineReadablePrefix.parse(msg.message) == MachineReadablePrefix.AUTH_REQUIRED
                    if (authWall && pendingOnAuthRequired && !retriedAfterAuth &&
                        awaitAuthOutcome(relay, authMark, DEFAULT_AUTH_GRACE_MS, idleTimeoutMs) == AuthOutcome.AUTHENTICATED
                    ) {
                        retriedAfterAuth = true
                        relayClient.sendIfConnected(command)
                    } else {
                        done = true
                        throw SqlQueryException(msg.message)
                    }
                }

                else -> {}
            }
        }
    } finally {
        removeConnectionListener(listener)
        incoming.close()
        if (!done) relayClient.sendIfConnected(SqlCloseCmd(queryId))
    }
}

/** Rows per `FETCH` when the caller doesn't choose a page size. */
const val DEFAULT_SQL_FETCH = 500
