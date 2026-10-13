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
package com.vitorpamplona.quartz.nip01Core.relay.client.reqs

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every emission of these flows is the whole list so far, so a collector that falls behind only
 * needs the newest one. The flows used to keep a 64-slot buffer and drop sends once it filled, which
 * dropped exactly that newest list: a burst left the collector holding the first 64 events.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AsFlowBurstTest {
    private class ScriptedClient : INostrClient by EmptyNostrClient() {
        var listener: SubscriptionListener? = null

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            this.listener = listener
        }

        override fun unsubscribe(subId: String) {}
    }

    private val relay = RelayUrlNormalizer.normalize("wss://burst.example.com")

    private fun event(i: Int) =
        Event(
            id = i.toString(16).padStart(64, '0'),
            pubKey = "f".repeat(64),
            createdAt = i.toLong(),
            kind = 1,
            tags = emptyArray(),
            content = "e$i",
            sig = "0".repeat(128),
        )

    @Test
    fun subscribeAsFlowKeepsTheNewestListThroughABurst() =
        runTest {
            val client = ScriptedClient()
            var latest = emptyList<Event>()
            val job = launch { client.subscribeAsFlow(relay, Filter(kinds = listOf(1))).collect { latest = it } }
            advanceUntilIdle()

            val listener = client.listener!!
            repeat(200) { listener.onEvent(event(it), false, relay, null) }
            advanceUntilIdle()

            assertEquals(200, latest.size)
            job.cancel()
        }

    @Test
    fun fetchAsFlowEndsWithTheWholeList() =
        runTest {
            val client = ScriptedClient()
            var last: List<Event>? = null
            val job = launch { last = client.fetchAsFlow(relay, Filter(kinds = listOf(1))).last() }
            advanceUntilIdle()

            val listener = client.listener!!
            repeat(200) { listener.onEvent(event(it), false, relay, null) }
            listener.onEose(relay, null)
            advanceUntilIdle()

            assertEquals(200, last?.size)
            job.cancel()
        }
}
