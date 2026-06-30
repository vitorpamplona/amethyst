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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Flow form of [negentropySync]: runs the sync while collected and emits the
 * accumulated events as a growing list on each new arrival, then completes when
 * the sync finishes (mirroring [com.vitorpamplona.quartz.nip01Core.relay.client.reqs.fetchAsFlow]).
 * Cancelling the collector cancels the sync and tears down its subscriptions via
 * [awaitClose].
 *
 * See [negentropySync] for the meaning of every parameter.
 */
fun INostrClient.negentropySyncAsFlow(
    relay: NormalizedRelayUrl,
    filter: Filter,
    maxEvents: Int = 0,
    maxConcurrentReqs: Int = 8,
    fetchBatch: Int = 500,
    timeoutMs: Long = 30_000L,
): Flow<List<Event>> =
    callbackFlow {
        var current = listOf<Event>()

        negentropySync(
            relay = relay,
            filter = filter,
            maxEvents = maxEvents,
            maxConcurrentReqs = maxConcurrentReqs,
            fetchBatch = fetchBatch,
            timeoutMs = timeoutMs,
        ) { event ->
            current = current + event
            trySend(current)
        }

        close()

        awaitClose { }
    }

fun INostrClient.negentropySyncAsFlow(
    relay: String,
    filter: Filter,
    maxEvents: Int = 0,
    maxConcurrentReqs: Int = 8,
    fetchBatch: Int = 500,
    timeoutMs: Long = 30_000L,
): Flow<List<Event>> =
    negentropySyncAsFlow(
        relay = RelayUrlNormalizer.normalize(relay),
        filter = filter,
        maxEvents = maxEvents,
        maxConcurrentReqs = maxConcurrentReqs,
        fetchBatch = fetchBatch,
        timeoutMs = timeoutMs,
    )
