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

/**
 * Pins [fetchFirst]'s timeout to the package-wide convention: `idleTimeoutMs` is
 * silence measured from the most recent *progress*, not an absolute deadline
 * across the whole multi-relay wait. Repeat chatter from a relay already
 * accounted for is not progress, which is what makes the call self-bounding
 * without a ceiling parameter — a hard bound is the caller's `withTimeoutOrNull`.
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
    private val relayC = RelayUrlNormalizer.normalize("wss://c.example.com")
    private val relayD = RelayUrlNormalizer.normalize("wss://d.example.com")

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
    fun genuineProgressRestartsTheIdleWindow() =
        runTest {
            val client = ScriptedClient()
            launch {
                // Each relay's FIRST terminal signal is real progress and buys a
                // fresh window, carrying the fetch well past a single 300ms window
                // so the slow relay's event at 900ms still lands. An absolute
                // deadline would have returned null at 300ms.
                delay(250)
                client.listener!!.onClosed("rate limited", relayA, null)
                delay(250)
                client.listener!!.onClosed("rate limited", relayB, null)
                delay(250)
                client.listener!!.onClosed("rate limited", relayC, null)
                delay(150)
                client.listener!!.onEvent(event(1), false, relayD, null)
            }
            val result =
                client.fetchFirst(
                    filters = filters(relayA, relayB, relayC, relayD),
                    idleTimeoutMs = 300,
                )
            assertEquals(event(1).id, result?.id, "progress must restart the window; the slow relay's event still lands")
        }

    @Test
    fun totalSilenceReturnsNullAfterOneIdleWindow() =
        runTest {
            val client = ScriptedClient()
            val start = currentTime
            val result =
                client.fetchFirst(
                    filters = filters(relayA),
                    idleTimeoutMs = 300,
                )
            assertNull(result)
            assertEquals(300L, currentTime - start, "a silent relay costs exactly one idle window")
        }

    @Test
    fun repeatTerminalChatterDoesNotRestartTheIdleWindow() =
        runTest {
            val client = ScriptedClient()
            val chatter =
                launch {
                    // relayA re-CLOSEs forever (a reconnect loop); relayB never
                    // answers. Only relayA's FIRST CLOSED is progress — it removes
                    // relayA from `remaining`. The repeats say nothing new, so they
                    // must not push the deadline out (the rule the negentropy
                    // watchdog already uses for NOTICE/CLOSED chatter).
                    while (true) {
                        delay(200)
                        client.listener!!.onClosed("auth-required: again", relayA, null)
                    }
                }
            val start = currentTime
            val result =
                client.fetchFirst(
                    filters = filters(relayA, relayB),
                    idleTimeoutMs = 300,
                )
            chatter.cancel()
            assertNull(result)
            // First CLOSED at 200ms is the only progress; the window then expires
            // 300ms later despite chatter at 400/600/800…
            assertEquals(500L, currentTime - start, "repeat chatter must not keep the wait alive")
        }

    @Test
    fun anEventArrivingAfterTheLastTerminalSignalIsStillReturned() =
        runTest {
            val client = ScriptedClient()
            launch {
                delay(100)
                // The only relay EOSEs, emptying `remaining` and ending the loop —
                // then its matching event lands before we unsubscribe. Without the
                // post-loop drain this returns null while holding a match.
                client.listener!!.onEose(relayA, null)
                client.listener!!.onEvent(event(7), false, relayA, null)
            }
            val result =
                client.fetchFirst(
                    filters = filters(relayA),
                    idleTimeoutMs = 300,
                )
            assertEquals(event(7).id, result?.id, "an event racing the final EOSE must not be dropped")
        }

    @Test
    fun aHardWallClockBoundIsTheCallersToApply() =
        runTest {
            val client = ScriptedClient()
            val chatter =
                launch {
                    while (true) {
                        delay(50)
                        client.listener!!.onClosed("flapping", relayA, null)
                    }
                }
            // No ceiling parameter: composing withTimeoutOrNull at the call site
            // is the wall-clock bound, and costs nothing because a timed-out
            // fetchFirst yields null either way.
            val start = currentTime
            val result = withTimeoutOrNull(120) { client.fetchFirst(filters = filters(relayA, relayB), idleTimeoutMs = 10_000) }
            chatter.cancel()
            assertNull(result)
            assertEquals(120L, currentTime - start, "the caller's timeout bounds the call")
        }
}
