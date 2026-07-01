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
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * Streaming form of [negentropySync]: emits each event **individually** as it
 * arrives, then completes when the sync finishes. Nothing is accumulated, so it
 * stays O(1) in memory regardless of how many events the relay holds.
 *
 * Events are buffered with [Channel.UNLIMITED] because [negentropySync] delivers
 * them through a non-suspending callback on the relay reader thread: a bounded
 * buffer would force the producer to drop events when the collector lags. A slow
 * collector therefore lets the buffer grow — apply your own
 * [kotlinx.coroutines.flow.buffer]/`conflate`/`collectLatest` downstream if you
 * need a different policy. Cancelling the collector cancels the sync and tears
 * down its subscriptions via [awaitClose].
 *
 * See [negentropySync] for the meaning of every parameter.
 */
fun INostrClient.negentropySyncEvents(
    relay: NormalizedRelayUrl,
    filter: Filter,
    maxEvents: Int = 0,
    maxConcurrentReqs: Int = 8,
    fetchBatch: Int = 500,
    idleTimeoutMs: Long = 120_000L,
): Flow<Event> =
    callbackFlow {
        negentropySync(
            relay = relay,
            filter = filter,
            maxEvents = maxEvents,
            maxConcurrentReqs = maxConcurrentReqs,
            fetchBatch = fetchBatch,
            idleTimeoutMs = idleTimeoutMs,
        ) { event ->
            trySend(event)
        }

        close()

        awaitClose { }
    }.buffer(Channel.UNLIMITED)

fun INostrClient.negentropySyncEvents(
    relay: String,
    filter: Filter,
    maxEvents: Int = 0,
    maxConcurrentReqs: Int = 8,
    fetchBatch: Int = 500,
    idleTimeoutMs: Long = 120_000L,
): Flow<Event> =
    negentropySyncEvents(
        relay = RelayUrlNormalizer.normalize(relay),
        filter = filter,
        maxEvents = maxEvents,
        maxConcurrentReqs = maxConcurrentReqs,
        fetchBatch = fetchBatch,
        idleTimeoutMs = idleTimeoutMs,
    )

/**
 * Streaming "try negentropy, else page" — the [Flow] form of
 * [negentropySyncOrFetch]. Emits each event individually as it arrives from
 * whichever transport delivered it, deduped by id across both phases, then
 * completes. Unlike [negentropySyncEvents] it never throws on a relay that can't
 * reconcile; it pages instead.
 *
 * See [negentropySyncEvents] for the buffering/backpressure note and
 * [negentropySync] for the meaning of every parameter.
 */
fun INostrClient.negentropySyncOrFetchEvents(
    relay: NormalizedRelayUrl,
    filter: Filter,
    maxEvents: Int = 0,
    maxConcurrentReqs: Int = 8,
    fetchBatch: Int = 500,
    idleTimeoutMs: Long = 120_000L,
): Flow<Event> =
    callbackFlow {
        negentropySyncOrFetch(
            relay = relay,
            filter = filter,
            maxEvents = maxEvents,
            maxConcurrentReqs = maxConcurrentReqs,
            fetchBatch = fetchBatch,
            idleTimeoutMs = idleTimeoutMs,
        ) { event ->
            trySend(event)
        }

        close()

        awaitClose { }
    }.buffer(Channel.UNLIMITED)

fun INostrClient.negentropySyncOrFetchEvents(
    relay: String,
    filter: Filter,
    maxEvents: Int = 0,
    maxConcurrentReqs: Int = 8,
    fetchBatch: Int = 500,
    idleTimeoutMs: Long = 120_000L,
): Flow<Event> =
    negentropySyncOrFetchEvents(
        relay = RelayUrlNormalizer.normalize(relay),
        filter = filter,
        maxEvents = maxEvents,
        maxConcurrentReqs = maxConcurrentReqs,
        fetchBatch = fetchBatch,
        idleTimeoutMs = idleTimeoutMs,
    )
