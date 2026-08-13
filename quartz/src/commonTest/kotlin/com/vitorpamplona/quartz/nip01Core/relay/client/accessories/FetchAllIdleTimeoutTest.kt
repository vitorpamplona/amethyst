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
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the idle-window timeout semantics of the fetchAll family: `idleTimeoutMs`
 * is the maximum SILENCE between messages, not an absolute deadline — a relay
 * that keeps streaming is never cropped, and a stalled fetch ends one idle
 * window after its last message. There is no internal wall-clock ceiling; a hard
 * deadline is the caller's to compose.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FetchAllIdleTimeoutTest {
    /** Captures the subscription listener so the test can play a relay. */
    private class ScriptedClient : INostrClient by EmptyNostrClient() {
        var listener: SubscriptionListener? = null

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            this.listener = listener
        }
    }

    private val relay = RelayUrlNormalizer.normalize("wss://slow.example.com")

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
    fun streamingRelayOutlivesTheIdleWindow() =
        runTest {
            val client = ScriptedClient()
            val feeder =
                launch {
                    // 10 events, each arriving 200ms apart — every gap is under the
                    // 300ms idle window, but the total (2s) far exceeds it. An
                    // absolute deadline would crop this after the first event.
                    repeat(10) { i ->
                        delay(200)
                        client.listener!!.onEvent(event(i), false, relay, null)
                    }
                    delay(100)
                    client.listener!!.onEose(relay, null)
                }
            val collected =
                client.fetchAllWithHooks(
                    filters = mapOf(relay to listOf(Filter(kinds = listOf(1)))),
                    idleTimeoutMs = 300,
                ) { _, _ -> true }
            feeder.join()
            assertEquals(10, collected.size, "an actively streaming relay must never be cropped")
        }

    @Test
    fun silenceEndsTheFetchAfterOneIdleWindow() =
        runTest {
            val client = ScriptedClient()
            var stalledRelays: Set<NormalizedRelayUrl>? = null
            launch {
                delay(100)
                client.listener!!.onEvent(event(1), false, relay, null)
                delay(100)
                client.listener!!.onEvent(event(2), false, relay, null)
                // …then the relay goes quiet without ever sending EOSE.
            }
            val start = currentTime
            val collected =
                client.fetchAllWithHooks(
                    filters = mapOf(relay to listOf(Filter(kinds = listOf(1)))),
                    idleTimeoutMs = 300,
                    onTimeout = { stalled, _, _ -> stalledRelays = stalled },
                ) { _, _ -> true }
            assertEquals(2, collected.size, "events before the stall are kept")
            assertEquals(setOf(relay), stalledRelays, "the stalled relay is reported")
            // Ended ~one idle window after the LAST message (200 + 300), not the start.
            assertEquals(500L, currentTime - start, "the window restarts on every message")
        }

    @Test
    fun fetchAllStreamingRelayOutlivesTheIdleWindow() =
        runTest {
            val client = ScriptedClient()
            val feeder =
                launch {
                    repeat(10) { i ->
                        delay(200)
                        client.listener!!.onEvent(event(i), false, relay, null)
                    }
                    delay(100)
                    client.listener!!.onEose(relay, null)
                }
            val events =
                client.fetchAll(
                    filters = mapOf(relay to listOf(Filter(kinds = listOf(1)))),
                    idleTimeoutMs = 300,
                )
            feeder.join()
            assertEquals(10, events.size, "fetchAll shares the idle-window semantics")
        }

    @Test
    fun anEndlessTrickleIsBoundedByTheCaller() =
        runTest {
            val client = ScriptedClient()
            val feeder =
                launch {
                    // A relay that trickles forever, always inside the idle window,
                    // never reaching a terminal state. The accessory has no internal
                    // wall-clock ceiling — by design, since it cannot tell this from a
                    // relay legitimately streaming a large backlog. It is a suspending
                    // function, so the caller composes the hard deadline.
                    var i = 0
                    while (true) {
                        delay(200)
                        client.listener!!.onEvent(event(i++), false, relay, null)
                    }
                }
            val start = currentTime
            val collected =
                withTimeoutOrNull(1_000) {
                    client.fetchAllWithHooks(
                        filters = mapOf(relay to listOf(Filter(kinds = listOf(1)))),
                        idleTimeoutMs = 300,
                    ) { _, _ -> true }
                }
            feeder.cancel()
            assertNull(collected, "the caller's withTimeoutOrNull is what stops an endless trickle")
            assertEquals(1_000L, currentTime - start, "and it stops at the caller's deadline")
        }

    @Test
    fun eoseStillEndsImmediately() =
        runTest {
            val client = ScriptedClient()
            launch {
                delay(50)
                client.listener!!.onEvent(event(1), false, relay, null)
                client.listener!!.onEose(relay, null)
            }
            val start = currentTime
            val collected =
                client.fetchAllWithHooks(
                    filters = mapOf(relay to listOf(Filter(kinds = listOf(1)))),
                    idleTimeoutMs = 300,
                ) { _, _ -> true }
            assertEquals(1, collected.size)
            assertTrue(currentTime - start < 300, "a terminal EOSE must not wait out the window")
        }
}
