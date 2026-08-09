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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins `onDrained` — the one signal that tells a caller the relay served
 * *everything* below where the walk stopped, rather than merely stopping there.
 *
 * The distinction is invisible in the `Int` return and matters enormously to
 * anything recording sync coverage: without it, "the relay has nothing older"
 * and "the relay capped us / went quiet / hung up" look identical, so a coverage
 * band can never close its oldest leg and re-asks a range that will always come
 * back empty, every cycle, forever.
 *
 * Real-clock ([runBlocking]) for the same reason the idle-timeout suite is: the
 * page watchdog is a monotonic clock bumped from the socket reader thread.
 */
class NostrClientFetchAllPagesDrainTest {
    /** Captures the subscription listener so the test can play a relay, page by page. */
    private class ScriptedClient : INostrClient by EmptyNostrClient() {
        @Volatile
        var listener: SubscriptionListener? = null

        @Volatile
        var subscribeCount = 0

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            subscribeCount++
            this.listener = listener
        }

        /** Block until the walk has opened its [n]th page, so a script can answer it. */
        suspend fun awaitPage(n: Int) {
            while (subscribeCount < n) delay(2)
        }
    }

    private val relay = RelayUrlNormalizer.normalize("wss://drain.example.com")

    private fun event(createdAt: Long) =
        Event(
            id = createdAt.toString(16).padStart(64, '0'),
            pubKey = "f".repeat(64),
            createdAt = createdAt,
            kind = 1,
            tags = emptyArray(),
            content = "e$createdAt",
            sig = "0".repeat(128),
        )

    @Test
    fun anEmptyPageConfirmedByEoseDrains() =
        runBlocking {
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onEvent(event(2000), false, relay, null)
                    client.listener!!.onEvent(event(1000), false, relay, null)
                    client.listener!!.onEose(relay, null)

                    // The second page asks below 1000 and the relay says, with an
                    // EOSE, that it has nothing. THAT is a drain.
                    client.awaitPage(2)
                    client.listener!!.onEose(relay, null)
                }

            var drained = false
            val total =
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(1))),
                    idleTimeoutMs = 2_000,
                    onDrained = { drained = true },
                ) { }
            feeder.join()

            assertEquals(2, total)
            assertTrue(drained, "an empty page the relay EOSEd is proof there is nothing older")
        }

    @Test
    fun aSilentPageDoesNotDrain() =
        runBlocking {
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onEvent(event(2000), false, relay, null)
                    client.listener!!.onEvent(event(1000), false, relay, null)
                    client.listener!!.onEose(relay, null)
                    // Page two: the relay simply stops answering. Silence is not an
                    // answer — reading it as "nothing older exists" would durably
                    // record coverage that was never served.
                    client.awaitPage(2)
                }

            var drained = false
            val total =
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(1))),
                    idleTimeoutMs = 200,
                    onDrained = { drained = true },
                ) { }
            feeder.join()

            assertEquals(2, total, "the events already delivered are still kept")
            assertFalse(drained, "an idle timeout says nothing about what the relay holds")
        }

    @Test
    fun aClosedSubscriptionDoesNotDrain() =
        runBlocking {
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onEvent(event(2000), false, relay, null)
                    client.listener!!.onEvent(event(1000), false, relay, null)
                    client.listener!!.onEose(relay, null)
                    // Page two: the relay ends the subscription instead of serving
                    // it — auth-required, rate limit, policy. It declined to answer,
                    // which is not the same as answering "nothing".
                    client.awaitPage(2)
                    client.listener!!.onClosed("auth-required: we don't serve that", relay, null)
                }

            var drained = false
            client.fetchAllPages(
                relay = relay,
                filters = listOf(Filter(kinds = listOf(1))),
                idleTimeoutMs = 2_000,
                onDrained = { drained = true },
            ) { }
            feeder.join()

            assertFalse(drained, "a CLOSED is the relay declining, not an empty corpus")
        }

    @Test
    fun aRelayItCannotReachDoesNotDrain() =
        runBlocking {
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onCannotConnect(relay, "connection refused", null)
                }

            var drained = false
            client.fetchAllPages(
                relay = relay,
                filters = listOf(Filter(kinds = listOf(1))),
                idleTimeoutMs = 2_000,
                onDrained = { drained = true },
            ) { }
            feeder.join()

            assertFalse(drained, "never got to ask")
        }

    @Test
    fun aFulfilledLimitDoesNotDrain() =
        runBlocking {
            // The caller bounded the download itself, so the walk stopped on its
            // own instruction rather than at the end of the relay's corpus.
            // Nothing below the last event was ever asked for.
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onEvent(event(2000), false, relay, null)
                    client.listener!!.onEvent(event(1000), false, relay, null)
                    client.listener!!.onEose(relay, null)
                }

            var drained = false
            val total =
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(1), limit = 2)),
                    idleTimeoutMs = 2_000,
                    onDrained = { drained = true },
                ) { }
            feeder.join()

            assertEquals(2, total)
            assertEquals(1, client.subscribeCount, "the limit was met, so there was no second page")
            assertFalse(drained, "a fulfilled limit is the caller stopping, not the corpus ending")
        }
}
