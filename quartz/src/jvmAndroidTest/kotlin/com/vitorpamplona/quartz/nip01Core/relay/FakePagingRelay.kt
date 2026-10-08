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
package com.vitorpamplona.quartz.nip01Core.relay

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * A relay that actually serves a [corpus], page by page, the way a strfry-like relay does:
 * newest first, `until` inclusive, at most `min(limit, maxLimit)` events per REQ and
 * [defaultLimit] when the REQ carries none.
 *
 * Two knobs make it misbehave the ways real relays do:
 *  - [refuseAboveMax]: answer a `limit` above [maxLimit] with purplepag.es's CLOSED instead
 *    of clamping it.
 *  - [closeWith]: given the 1-based REQ number, a CLOSED reason to answer it with instead.
 *  - [answer]: given the 1-based REQ number and the honest page, return what the relay
 *    actually sends — e.g. a throttled relay's shrunken or empty page.
 *
 * Every REQ's filters are recorded in [requests], so a test can assert what went on the wire.
 */
class FakePagingRelay(
    private val scope: CoroutineScope,
    private val corpus: List<Event>,
    private val maxLimit: Int = 500,
    private val defaultLimit: Int = maxLimit,
    private val refuseAboveMax: Boolean = false,
    private val closeWith: (req: Int) -> String? = { null },
    private val answer: (req: Int, honest: List<Event>) -> List<Event> = { _, honest -> honest },
) : INostrClient by EmptyNostrClient() {
    val requests: MutableList<List<Filter>> = Collections.synchronizedList(mutableListOf())

    override fun subscribe(
        subId: String,
        filters: Map<NormalizedRelayUrl, List<Filter>>,
        listener: SubscriptionListener?,
    ) {
        val (relay, relayFilters) = filters.entries.single()
        requests.add(relayFilters)
        val req = requests.size
        scope.launch {
            val tooHigh = relayFilters.firstNotNullOfOrNull { f -> f.limit?.takeIf { it > maxLimit } }
            if (refuseAboveMax && tooHigh != null) {
                listener?.onClosed("blocked: limit too high: $tooHigh (max $maxLimit)", relay, relayFilters)
                return@launch
            }
            closeWith(req)?.let { reason ->
                listener?.onClosed(reason, relay, relayFilters)
                return@launch
            }
            val honest =
                relayFilters
                    .flatMap { f ->
                        corpus
                            .filter { f.match(it) }
                            .sortedByDescending { it.createdAt }
                            .take(minOf(f.limit ?: defaultLimit, maxLimit))
                    }.distinctBy { it.id }
                    .sortedByDescending { it.createdAt }
            answer(req, honest).forEach { listener?.onEvent(it, false, relay, relayFilters) }
            listener?.onEose(relay, relayFilters)
        }
    }

    companion object {
        /** [count] kind-1 events, one per second, the newest at [newest]. */
        fun corpus(
            count: Int,
            newest: Long = 1_000_000L,
        ): List<Event> =
            (0 until count).map { i ->
                val createdAt = newest - i
                Event(
                    id = createdAt.toString(16).padStart(64, '0'),
                    pubKey = "f".repeat(64),
                    createdAt = createdAt,
                    kind = 1,
                    tags = emptyArray(),
                    content = "e$createdAt",
                    sig = "0".repeat(128),
                )
            }
    }
}
