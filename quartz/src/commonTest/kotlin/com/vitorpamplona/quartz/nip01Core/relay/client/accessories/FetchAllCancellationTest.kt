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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The fetchAll family has no internal wall-clock ceiling: a hard deadline is the
 * caller's to compose, so **cancellation is the bound**. These tests pin the
 * properties that makes true — the fetch has to actually observe cancellation,
 * and it has to clean up after itself when it does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FetchAllCancellationTest {
    /** Captures the subscription listener so the test can play a relay. */
    private class ScriptedClient : INostrClient by EmptyNostrClient() {
        var listener: SubscriptionListener? = null
        var subscribedAs: String? = null
        val unsubscribed = mutableListOf<String>()

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            this.listener = listener
            this.subscribedAs = subId
        }

        override fun unsubscribe(subId: String) {
            unsubscribed.add(subId)
        }
    }

    private val relay = RelayUrlNormalizer.normalize("wss://slow.example.com")

    private fun filters() = mapOf(relay to listOf(Filter(kinds = listOf(1))))

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

    /**
     * The cleanup runs on the cancellation path too: `unsubscribe` sends the relay its
     * CLOSE. It only holds because nothing in that `finally` suspends — a suspending
     * cleanup would throw instead of running, and leak the subscription.
     */
    @Test
    fun cancellationStillUnsubscribes() =
        runTest {
            val client = ScriptedClient()
            val feeder =
                launch {
                    var i = 0
                    while (true) {
                        delay(200)
                        client.listener!!.onEvent(event(i++), false, relay, null)
                    }
                }
            val out =
                withTimeoutOrNull(1_000) {
                    client.fetchAllWithHooks(filters = filters(), idleTimeoutMs = 300) { _, _ -> true }
                }
            feeder.cancel()
            assertNull(out, "the caller's deadline is what stops an endless trickle")
            assertEquals(listOf(client.subscribedAs), client.unsubscribed, "CLOSE must still be sent")
        }

    /**
     * The auth resolver parks on an AUTH that may never settle and is cancelled explicitly
     * on the normal path. On the cancellation path the enclosing `coroutineScope` has to be
     * what ends it — if it outlived the scope, this test would hang instead of finishing.
     */
    @Test
    fun cancellationLeavesNoRunningChildren() =
        runTest {
            val client = ScriptedClient()
            val feeder =
                launch {
                    var i = 0
                    while (true) {
                        delay(200)
                        client.listener!!.onEvent(event(i++), false, relay, null)
                    }
                }
            withTimeoutOrNull(1_000) {
                client.fetchAllWithHooks(
                    filters = filters(),
                    idleTimeoutMs = 300,
                    pendingOnAuthRequired = true,
                ) { _, _ -> true }
            }
            feeder.cancel()
        }

    /**
     * A relay feeding faster than we drain keeps the loop on the `tryReceive` fast path,
     * where nothing suspends — and a suspend hook that returns without suspending performs
     * no cancellation check either. Without an explicit `ensureActive`, the drain is deaf to
     * cancellation: this exact test drained all 200_000 events after `cancel()` before one
     * was added, and would never end against a relay that keeps feeding.
     */
    @Test
    fun floodedFastPathIsStillCancellable() =
        runTest {
            val client = ScriptedClient()
            var i = 0
            var seen = 0
            // Cancellation is delivered from inside the hook rather than by a timer: a loop
            // that never suspends also never lets virtual time advance, so a withTimeout
            // timer could not fire to prove anything either way.
            lateinit var fetchJob: Job
            fetchJob =
                launch {
                    client.fetchAllWithHooks(filters = filters(), idleTimeoutMs = 300) { _, _ ->
                        seen++
                        // The relay delivers the next event before we finish this one, so the
                        // channel is never dry and the loop stays on the fast path.
                        if (i < 200_000) client.listener!!.onEvent(event(i++), false, relay, null)
                        if (seen == 10) fetchJob.cancel()
                        true
                    }
                }
            // Let the fetch subscribe and park on the idle wait, then seed the flood.
            yield()
            client.listener!!.onEvent(event(i++), false, relay, null)
            fetchJob.join()
            assertTrue(seen in 10..20, "a cancelled flooded drain must stop promptly, drained $seen events")
            assertEquals(listOf(client.subscribedAs), client.unsubscribed, "and must still unsubscribe")
        }
}
