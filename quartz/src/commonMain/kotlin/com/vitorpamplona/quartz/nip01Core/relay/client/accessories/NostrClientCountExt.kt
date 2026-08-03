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
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountMessage
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.CountResult
import com.vitorpamplona.quartz.nip01Core.relay.commands.toClient.Message
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip45Count.HyperLogLog
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/**
 * Sends a NIP-45 COUNT query to a single relay and suspends until
 * the result arrives or the timeout expires.
 *
 * A COUNT exchange is a single response message, so [idleTimeoutMs] here is
 * trivially the package-wide idle-window convention (time since the most
 * recent message): no message can arrive before the one that completes it.
 *
 * @param relay Target relay to query.
 * @param filter The filter to count against.
 * @param idleTimeoutMs How long to wait for the response (default 15 s).
 * @return The [CountResult], or `null` on timeout.
 */
suspend fun INostrClient.count(
    relay: NormalizedRelayUrl,
    filter: Filter,
    idleTimeoutMs: Long = 15_000,
): CountResult? {
    val subId = newSubId()
    val resultChannel = Channel<CountResult>(UNLIMITED)

    val listener =
        object : RelayConnectionListener {
            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                if (msg is CountMessage && msg.queryId == subId) {
                    resultChannel.trySend(msg.result)
                }
            }
        }

    return try {
        addConnectionListener(listener)

        count(subId = subId, filters = mapOf(relay to listOf(filter)))

        withTimeoutOrNull(idleTimeoutMs) {
            resultChannel.receive()
        }
    } finally {
        // Every cleanup step belongs in the finally: closing the channel used to
        // sit after it, so a throw (or cancellation) mid-wait skipped it while the
        // sibling accessories all cleaned up fully.
        unsubscribe(subId)
        removeConnectionListener(listener)
        resultChannel.close()
    }
}

/**
 * Sends NIP-45 COUNT queries to multiple relays in parallel
 * (one filter per relay) and suspends until all results arrive
 * or the timeout expires.
 *
 * [idleTimeoutMs] is an **idle window measured from the most recent progress**, not a
 * wall-clock deadline for the whole batch — the package-wide accessory
 * convention: each *new* relay's COUNT result restarts it, so a large fan-out
 * where results keep trickling in is never cut short. A relay re-sending a result
 * it already gave is not progress and does not restart the window, which makes
 * the call self-bounding (at most one window per relay). A caller wanting a hard
 * wall-clock bound has `withTimeoutOrNull(ms) { count(...) }` — at the cost of
 * discarding the partial map, which is why this returns whatever arrived instead.
 *
 * @param filters Map of relay -> filter to count.
 * @param idleTimeoutMs Idle window between new responses (default 15 s).
 * @return Map of relay -> [CountResult] for every relay that responded in time.
 */
suspend fun INostrClient.count(
    filters: Map<NormalizedRelayUrl, List<Filter>>,
    idleTimeoutMs: Long = 15_000,
): Map<NormalizedRelayUrl, CountResult> {
    if (filters.isEmpty()) return emptyMap()

    val subIdToRelay = mutableMapOf<String, NormalizedRelayUrl>()
    val resultChannel = Channel<Pair<NormalizedRelayUrl, CountResult>>(UNLIMITED)

    val listener =
        object : RelayConnectionListener {
            override fun onIncomingMessage(
                relay: IRelayClient,
                msgStr: String,
                msg: Message,
            ) {
                if (msg is CountMessage) {
                    val relayUrl = subIdToRelay[msg.queryId] ?: return
                    resultChannel.trySend(relayUrl to msg.result)
                }
            }
        }

    val results = mutableMapOf<NormalizedRelayUrl, CountResult>()

    try {
        addConnectionListener(listener)

        filters.forEach { (relay, filterList) ->
            val subId = newSubId()
            subIdToRelay[subId] = relay
            count(subId = subId, filters = mapOf(relay to filterList))
        }

        // One idle window per new relay result. The inner loop absorbs repeats
        // (a relay answering twice) inside the SAME window, so only genuinely
        // new information pushes the deadline out — bounding the call at one
        // window per relay without needing a wall-clock ceiling.
        while (results.size < filters.size) {
            val progressed =
                withTimeoutOrNull(idleTimeoutMs) {
                    while (true) {
                        // Cancellation (this window expiring, or the caller giving up)
                        // only lands at a suspension point, and receive() does not
                        // suspend while the channel has buffered results — so check
                        // explicitly rather than draining a backlog uninterruptibly.
                        coroutineContext.ensureActive()
                        val (relay, result) = resultChannel.receive()
                        // put() returns the previous value: null means this relay
                        // had not answered yet, i.e. real progress.
                        if (results.put(relay, result) == null) break
                    }
                    true
                }
            if (progressed == null) break
        }

        // A result can land after the last window closed but before we unsubscribe;
        // it costs nothing to keep, and dropping it would understate the count.
        while (true) {
            val (relay, result) = resultChannel.tryReceive().getOrNull() ?: break
            results[relay] = result
        }
    } finally {
        subIdToRelay.keys.forEach { unsubscribe(it) }
        removeConnectionListener(listener)
        resultChannel.close()
    }

    return results
}

/**
 * Queries multiple relays for a COUNT and merges the HyperLogLog
 * registers from all responses to produce a single merged estimate.
 *
 * If any relay returns HLL data, the results are merged by taking
 * the maximum register value across all relays, and the cardinality
 * is re-estimated from the merged registers.
 *
 * If no relay returns HLL data, falls back to the maximum count
 * reported by any relay.
 *
 * @param relays List of relays to query.
 * @param filter The filter to count against.
 * @param idleTimeoutMs Idle window between responses (default 15 s) — see [count].
 * @return A merged [CountResult], or `null` if no relay responded.
 */
suspend fun INostrClient.countMerged(
    relays: List<NormalizedRelayUrl>,
    filter: Filter,
    idleTimeoutMs: Long = 15_000,
): CountResult? {
    if (relays.isEmpty()) return null

    val results =
        count(
            filters = relays.associateWith { listOf(filter) },
            idleTimeoutMs = idleTimeoutMs,
        )

    if (results.isEmpty()) return null

    val hlls = results.values.mapNotNull { it.hll }

    return if (hlls.isNotEmpty()) {
        val merged = HyperLogLog.merge(hlls)
        val estimate = HyperLogLog.estimate(merged)
        CountResult(
            count = estimate.toInt(),
            approximate = true,
            hll = merged,
        )
    } else {
        // No HLL data - use the maximum count from any relay
        results.values.maxByOrNull { it.count }
    }
}
