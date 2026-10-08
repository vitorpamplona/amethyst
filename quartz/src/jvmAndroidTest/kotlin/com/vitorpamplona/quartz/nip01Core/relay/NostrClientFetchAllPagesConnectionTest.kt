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

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PagedFetchResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A paged walk must keep its relay connected from the first page to the last.
 *
 * Measured on production relays (2026-10 survey of 743, wire-logged with amy): the walk closed
 * each page's subscription before opening the next, and [NostrClient] samples the relays its
 * subscriptions want every 300 ms. A sample that landed in that gap dropped the relay from the
 * pool and disconnected it, so the next page paid a new WebSocket + TLS handshake (~0.9 s on
 * yabu.me), and the old client's teardown racing the new one delivered whole pages twice
 * (relay.nostrcheck.me: 1840 events for a 920-event page) or left a REQ unanswered until the idle
 * timeout (REQ #2 on yabu.me, yestr.me, czas.top, no.str.cr).
 */
class NostrClientFetchAllPagesConnectionTest {
    private val hub = InProcessRelays()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @AfterTest
    fun tearDown() {
        scope.cancel()
        hub.close()
    }

    @Test
    fun aWalkKeepsOneConnectionAcrossItsPages() =
        runBlocking {
            val store = hub.getOrCreate(InProcessRelays.DEFAULT_URL).store
            FakePagingRelay.corpus(1_500).forEach { store.insert(it) }

            val client = NostrClient(hub, scope)
            val dials = AtomicInteger()
            client.addConnectionListener(
                object : RelayConnectionListener {
                    override fun onConnecting(relay: IRelayClient) {
                        dials.incrementAndGet()
                    }
                },
            )

            val result =
                client.fetchAllPages(
                    relay = InProcessRelays.DEFAULT_URL,
                    filters = listOf(Filter(kinds = listOf(1), limit = 1_500)),
                    idleTimeoutMs = 5_000,
                    pageSize = 500,
                    // Work between pages (cursor bookkeeping, a pacing pause, a backoff) longer
                    // than the pool's 300 ms relay sample.
                    onNewPage = { Thread.sleep(400) },
                ) { }

            assertEquals(PagedFetchResult.End.LIMIT_REACHED, result.end)
            assertEquals(1_500, result.downloaded)
            assertEquals(1, dials.get(), "one connection for the whole walk")
            client.disconnect()
        }
}
