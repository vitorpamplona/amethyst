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
import com.vitorpamplona.quartz.nip01Core.crypto.verify
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
import com.vitorpamplona.quartz.nipXXSql.FilterSql
import com.vitorpamplona.quartz.nipXXSql.NqlCmd
import com.vitorpamplona.quartz.nipXXSql.NqlResult
import com.vitorpamplona.quartz.nipXXSql.NqlResultMessage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.Volatile

/**
 * The relay refused or dropped an NQL query. [reason] is the `CLOSED`
 * text, with its NIP-01 prefix (`invalid:`, `unsupported:`, `error:`,
 * `auth-required:`, …), or a local reason for timeouts and disconnects.
 */
class NqlQueryException(
    val reason: String,
) : Exception(reason)

/**
 * Runs one read-only NIP-FF query on [relay] and returns its answer. The
 * relay may cap the rows ([NqlResult.truncated]); page with a condition past
 * the last row, as [nqlIdsAndTimes] does.
 *
 * Like REQ and COUNT, NQL is gated by NIP-42 on relays that require it: with
 * an auth responder registered, an `auth-required:` refusal waits for the AUTH
 * to land and re-sends the query once. A socket that drops before the answer
 * is waited out and the query re-sent, a few times.
 *
 * @param params one value per `?`: `Long`, `Double`, `String`, `Boolean` or null.
 * @throws NqlQueryException when the relay refuses the query, drops the
 *   connection for good, or stays silent for [timeoutMs].
 */
suspend fun INostrClient.nql(
    relay: NormalizedRelayUrl,
    query: String,
    params: List<Any?> = emptyList(),
    timeoutMs: Long = 30_000,
): NqlResult {
    val target = relay
    val incoming = Channel<Message>(UNLIMITED)

    // Each attempt gets its own id, so nothing a dead attempt still has in
    // flight (an answer, a refusal, the drop notice) is mistaken for the live one.
    var command = NqlCmd(newSubId(), query, params)
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
                        is NqlResultMessage -> msg.queryId
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
    addConnectionListener(listener)
    val keepAliveSubId = pinRelay(relay)
    try {
        relayClient.connect()
        withTimeoutOrNull(timeoutMs) { connectedRelaysFlow().first { relay in it } }
            ?: throw NqlQueryException("error: could not connect to $relay within ${timeoutMs}ms")

        val pendingOnAuthRequired = hasAuthResponder()
        var authMark = if (pendingOnAuthRequired) authSuccessMark(relay) else 0
        var retriedAfterAuth = false
        var reconnects = 0

        fun dropped(msg: Message?) = msg is ClosedMessage && msg.message == DISCONNECTED && msg.subId == command.queryId

        // The answer died with the socket: wait for a new one and ask again under a new id.
        suspend fun startOver() {
            if (reconnects++ >= MAX_NQL_RECONNECTS) throw NqlQueryException(DISCONNECTED)
            command = NqlCmd(newSubId(), query, params)
            live.id = command.queryId
            withTimeoutOrNull(timeoutMs) { connectedRelaysFlow().first { relay in it } }
                ?: throw NqlQueryException("error: could not reconnect to $relay within ${timeoutMs}ms")
            // A new socket is a new NIP-42 session: the auth retry is owed again.
            if (pendingOnAuthRequired) authMark = authSuccessMark(relay)
            retriedAfterAuth = false
            relayClient.sendIfConnected(command)
        }

        relayClient.sendIfConnected(command)

        while (true) {
            val msg =
                withTimeoutOrNull(timeoutMs) { incoming.receive() }
                    ?: throw NqlQueryException("error: no answer from $relay within ${timeoutMs}ms")
            val msgId =
                when (msg) {
                    is NqlResultMessage -> msg.queryId
                    is ClosedMessage -> msg.subId
                    else -> null
                }
            // Queued before the attempt it belongs to was abandoned.
            if (msgId != command.queryId) continue

            when (msg) {
                is NqlResultMessage -> {
                    return msg.result
                }

                is ClosedMessage if dropped(msg) -> {
                    startOver()
                }

                is ClosedMessage -> {
                    val authWall = MachineReadablePrefix.parse(msg.message) == MachineReadablePrefix.AUTH_REQUIRED
                    val outcome =
                        if (authWall && pendingOnAuthRequired && !retriedAfterAuth) {
                            awaitAuthOutcome(relay, authMark, DEFAULT_AUTH_GRACE_MS, timeoutMs)
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
                        outcome != null && dropped(withTimeoutOrNull(DROP_NOTICE_GRACE_MS) { incoming.receive() }) -> {
                            startOver()
                        }

                        else -> {
                            throw NqlQueryException(msg.message)
                        }
                    }
                }

                else -> {}
            }
        }
    } finally {
        removeConnectionListener(listener)
        incoming.close()
        unsubscribe(keepAliveSubId)
    }
}

/**
 * Keeps [relay] in the pool's desired set while an NQL exchange runs: `NQL` is
 * not a REQ, so without a subscription the pool sees the relay as unwanted and
 * may drop the socket. The filter matches nothing. Returns the subscription id
 * to [INostrClient.unsubscribe] when done.
 */
private fun INostrClient.pinRelay(relay: NormalizedRelayUrl): String {
    val subId = newSubId()
    subscribe(subId, mapOf(relay to listOf(Filter(ids = listOf(KEEP_ALIVE_ID)))), null)
    return subId
}

/**
 * `(created_at, id)` of every event [filter] matches on [relay]'s raw store,
 * newest first — a REQ without the relay's result cap, ranking or live tail,
 * sized for NIP-77 snapshots. Pages past the relay's row cap. See [FilterSql].
 *
 * @throws NqlQueryException see [nql].
 */
suspend fun INostrClient.nqlIdsAndTimes(
    relay: NormalizedRelayUrl,
    filter: Filter,
    timeoutMs: Long = 30_000,
): List<IdAndTime> {
    if (FilterSql.matchesNothing(filter)) return emptyList()
    val out = ArrayList<IdAndTime>()
    var after: IdAndTime? = null
    while (true) {
        val remaining = filter.limit?.let { it - out.size }
        if (remaining != null && remaining <= 0) break
        val q = FilterSql.ids(filter, after, remaining)
        val page = nql(relay, q.nql, q.params, timeoutMs)
        page.rows.forEach { out.add(IdAndTime((it[1] as Number).toLong(), it[0] as String)) }
        if (!page.truncated || page.rows.isEmpty()) break
        after = out.last()
    }
    return out
}

/**
 * How many events [filter] matches on [relay]'s raw store (NIP-45 semantics:
 * `limit` ignored), as one NQL `count`.
 *
 * @throws NqlQueryException see [nql].
 */
suspend fun INostrClient.nqlCount(
    relay: NormalizedRelayUrl,
    filter: Filter,
    timeoutMs: Long = 30_000,
): Long {
    if (FilterSql.matchesNothing(filter.copy(limit = null))) return 0
    val q = FilterSql.count(filter)
    return (nql(relay, q.nql, q.params, timeoutMs).rows.single()[0] as Number).toLong()
}

/**
 * Every event [filter] matches on [relay]'s raw store, newest first: the ids
 * with [nqlIdsAndTimes], then the events and their tags
 * [FilterSql.HYDRATE_CHUNK] at a time. NQL shows a tag's first five elements
 * only: an event with a longer tag is fetched whole with a `REQ` by id.
 *
 * @throws NqlQueryException see [nql].
 */
suspend fun INostrClient.nqlQuery(
    relay: NormalizedRelayUrl,
    filter: Filter,
    timeoutMs: Long = 30_000,
): List<Event> {
    val out = ArrayList<Event>()
    nqlQuery(relay, filter, timeoutMs) { out.add(it) }
    return out
}

/** [nqlQuery], handing each event to [onEach] a chunk at a time, newest first. */
suspend fun INostrClient.nqlQuery(
    relay: NormalizedRelayUrl,
    filter: Filter,
    timeoutMs: Long = 30_000,
    onEach: (Event) -> Unit,
) {
    // Pinned across the steps too, so the socket survives the gaps between them.
    val keepAliveSubId = pinRelay(relay)
    try {
        val ids = nqlIdsAndTimes(relay, filter, timeoutMs)
        for (chunk in ids.chunked(FilterSql.HYDRATE_CHUNK)) {
            val chunkIds = chunk.map { it.id }
            val collector = FilterSql.Collector()
            var lastId: String? = null
            while (true) {
                val q = FilterSql.events(chunkIds, lastId)
                val page = nql(relay, q.nql, q.params, timeoutMs)
                page.rows.forEach(collector::addEvent)
                if (!page.truncated || page.rows.isEmpty()) break
                lastId = page.rows.last()[0] as String
            }
            var after: Pair<String, Long>? = null
            while (true) {
                val q = FilterSql.tags(chunkIds, after)
                val page = nql(relay, q.nql, q.params, timeoutMs)
                page.rows.forEach(collector::addTag)
                if (!page.truncated || page.rows.isEmpty()) break
                val last = page.rows.last()
                after = last[0] as String to (last[1] as Number).toLong()
            }
            val result = collector.finish()
            val whole =
                if (result.incomplete.isEmpty()) {
                    emptyMap()
                } else {
                    fetchAll(relay, Filter(ids = result.incomplete), timeoutMs).filter { it.verify() }.associateBy { it.id }
                }
            val byId = result.events.associateBy { it.id }
            for (id in chunkIds) (byId[id] ?: whole[id])?.let(onEach)
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

/** Re-sends of a query whose socket dropped before its answer. */
private const val MAX_NQL_RECONNECTS = 3
