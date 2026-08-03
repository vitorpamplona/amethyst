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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Pins [fetchFirst]'s timeout to the package-wide idle-window convention:
 * [timeoutMs] is silence measured from the most recent relay signal, not an
 * absolute deadline across the whole multi-relay wait — with [maxTotalMs] as
 * the wall-clock ceiling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FetchFirstIdleTimeoutTest {
    /** Captures the subscription listener so the test can play the relays. */
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

    private val relayA = RelayUrlNormalizer.normalize("wss://a.example.com")
    private val relayB = RelayUrlNormalizer.normalize("wss://b.example.com")

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

    private fun filters(vararg relays: NormalizedRelayUrl) = relays.associateWith { listOf(Filter(kinds = listOf(1))) }

    @Test
    fun arrivingSignalsRestartTheIdleWindow() =
        runTest {
            val client = ScriptedClient()
            launch {
                // Terminal chatter every 250ms keeps the 300ms window alive long
                // enough for the slow relay's event at 900ms — an absolute
                // deadline would have returned null at 300ms.
                delay(250)
                client.listener!!.onClosed("rate limited", relayA, null)
                delay(250)
                client.listener!!.onClosed("rate limited", relayA, null)
                delay(250)
                client.listener!!.onClosed("rate limited", relayA, null)
                delay(150)
                client.listener!!.onEvent(event(1), false, relayB, null)
            }
            val result =
                client.fetchFirst(
                    filters = filters(relayA, relayB),
                    timeoutMs = 300,
                )
            assertEquals(event(1).id, result?.id, "signals must restart the window; the slow relay's event still lands")
        }

    @Test
    fun totalSilenceReturnsNullAfterOneIdleWindow() =
        runTest {
            val client = ScriptedClient()
            val start = currentTime
            val result =
                client.fetchFirst(
                    filters = filters(relayA),
                    timeoutMs = 300,
                )
            assertNull(result)
            assertEquals(300L, currentTime - start, "a silent relay costs exactly one idle window")
        }

    @Test
    fun wallClockCeilingStopsEndlessTerminalChatter() =
        runTest {
            val client = ScriptedClient()
            val chatter =
                launch {
                    // relayA re-CLOSEs forever (a reconnect loop); relayB never
                    // answers. Every signal restarts the window, so only the
                    // ceiling can end the wait.
                    while (true) {
                        delay(200)
                        client.listener!!.onClosed("auth-required: again", relayA, null)
                    }
                }
            val start = currentTime
            val result =
                client.fetchFirst(
                    filters = filters(relayA, relayB),
                    timeoutMs = 300,
                    maxTotalMs = 1_000,
                )
            chatter.cancel()
            assertNull(result)
            assertEquals(1_000L, currentTime - start, "the ceiling must end an endlessly-restarted wait")
        }

    @Test
    fun effectivelyInfiniteIdleWindowDoesNotOverflowTheCeiling() =
        runTest {
            val client = ScriptedClient()
            launch {
                delay(100)
                client.listener!!.onEvent(event(1), false, relayA, null)
            }
            // Long.MAX_VALUE * 10 wraps to -10; the default ceiling must
            // degrade to "uncapped", not to an instantly-expired wait.
            val result =
                client.fetchFirst(
                    filters = filters(relayA),
                    timeoutMs = Long.MAX_VALUE,
                )
            assertEquals(event(1).id, result?.id, "an overflowed default ceiling must mean uncapped, not instant timeout")
        }
}
