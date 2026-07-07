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
package com.vitorpamplona.amethyst.commons.relayClient.assemblers

import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.SubscriptionListener
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for PR #3483 review findings on FeedMetadataCoordinator:
 *
 *   - Finding 5: `queuedKind3Pubkeys` was marked-on-send, so if every index
 *     relay timed out the pubkeys were permanently marked and subsequent
 *     calls short-circuited — WoT stayed empty for the whole session.
 *     Fix: pubkeys land in `queuedKind3Pubkeys` only after ≥1 EOSE; on
 *     zero-EOSE timeout they roll out of `inFlightBatchedKind3` for retry.
 *
 *   - Finding 6: `eoseReceived: MutableSet` was mutated from per-relay
 *     `onEose` callbacks running on `Dispatchers.IO` with no sync. Fix:
 *     `BatchEoseGate` funnels EOSE notifications through a `Channel` so a
 *     single consumer coroutine is the sole reader/writer of the `seen`
 *     set.
 */
class FeedMetadataCoordinatorTest {
    private lateinit var scope: CoroutineScope
    private val relay1 = NormalizedRelayUrl("wss://relay1.test/")
    private val relay2 = NormalizedRelayUrl("wss://relay2.test/")
    private val relay3 = NormalizedRelayUrl("wss://relay3.test/")
    private val indexRelays = setOf(relay1, relay2, relay3)

    @Before
    fun setup() {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun teardown() {
        scope.cancel()
    }

    private fun pubkey(seed: Int): HexKey = seed.toString(16).padStart(64, '0')

    /**
     * Fake client that captures subscribe/unsubscribe and lets the test
     * drive EOSE notifications on any dispatcher we choose.
     */
    private class ControllableClient(
        private val delegate: INostrClient = EmptyNostrClient(),
    ) : INostrClient by delegate {
        val subscriptions = mutableMapOf<String, SubscriptionListener?>()
        val subscribeCalls = mutableListOf<Map<NormalizedRelayUrl, List<Filter>>>()
        var unsubscribeCallCount = 0
            private set

        override fun subscribe(
            subId: String,
            filters: Map<NormalizedRelayUrl, List<Filter>>,
            listener: SubscriptionListener?,
        ) {
            subscriptions[subId] = listener
            subscribeCalls.add(filters)
        }

        override fun unsubscribe(subId: String) {
            subscriptions.remove(subId)
            unsubscribeCallCount++
        }

        fun fireEose(relay: NormalizedRelayUrl) {
            subscriptions.values.filterNotNull().forEach { it.onEose(relay, forFilters = null) }
        }
    }

    @Test
    fun `loadKind3Batched retries after zero-EOSE timeout`() =
        runBlocking {
            val client = ControllableClient()
            val coordinator =
                FeedMetadataCoordinator(
                    client = client,
                    scope = scope,
                    indexRelays = indexRelays,
                )

            val pubkeys = listOf(pubkey(1), pubkey(2), pubkey(3))

            // Call 1 — no relay EOSEs; must time out.
            coordinator.loadKind3Batched(pubkeys, timeoutMs = 200)
            delay(350) // exceed the timeout

            // Call 2 — the same pubkeys must be re-subscribed since call 1
            // never got a successful EOSE. The old code would silently
            // short-circuit here.
            coordinator.loadKind3Batched(pubkeys, timeoutMs = 200)
            delay(50) // let the launcher run

            assertEquals(
                "Zero-EOSE timeout must not permanently dedup pubkeys",
                2,
                client.subscribeCalls.size,
            )
            assertEquals(
                "Second call must re-request the same author set",
                pubkeys.size,
                client.subscribeCalls[1]
                    .values
                    .first()
                    .first()
                    .authors!!
                    .size,
            )
        }

    @Test
    fun `loadKind3Batched short-circuits after successful EOSE`() =
        runBlocking {
            val client = ControllableClient()
            val coordinator =
                FeedMetadataCoordinator(
                    client = client,
                    scope = scope,
                    indexRelays = indexRelays,
                )

            val pubkeys = listOf(pubkey(1), pubkey(2))

            coordinator.loadKind3Batched(pubkeys, timeoutMs = 1_000)
            // Give the launcher time to register the listener before we fire.
            delay(50)
            indexRelays.forEach(client::fireEose)
            delay(200) // let the coordinator finish + promote to queued

            coordinator.loadKind3Batched(pubkeys, timeoutMs = 200)
            delay(50)

            assertEquals(
                "Successful call must dedup subsequent identical calls",
                1,
                client.subscribeCalls.size,
            )
        }

    @Test
    fun `loadKind3Batched promotes even when only some relays EOSE`() =
        runBlocking {
            val client = ControllableClient()
            val coordinator =
                FeedMetadataCoordinator(
                    client = client,
                    scope = scope,
                    indexRelays = indexRelays,
                )

            val pubkeys = listOf(pubkey(1))

            coordinator.loadKind3Batched(pubkeys, timeoutMs = 300)
            delay(30)
            // Only 1 of 3 EOSEs — timeout still fires but we made progress.
            client.fireEose(relay1)
            delay(400)

            coordinator.loadKind3Batched(pubkeys, timeoutMs = 200)
            delay(50)

            assertEquals(
                "≥1 EOSE = progress = promote to queued (avoid re-asking)",
                1,
                client.subscribeCalls.size,
            )
        }

    /**
     * Regression for finding 6 — pumps EOSE from many dispatchers in
     * parallel. The old MutableSet-based code could drop entries or throw
     * ConcurrentModificationException on the internal HashSet iterator.
     * BatchEoseGate must aggregate every distinct relay exactly once.
     */
    @Test
    fun `EOSE aggregator is safe under concurrent per-relay callbacks`() =
        runBlocking {
            val bigIndexSet =
                (0..19).map { NormalizedRelayUrl("wss://relay$it.test/") }.toSet()
            val client = ControllableClient()
            val coordinator =
                FeedMetadataCoordinator(
                    client = client,
                    scope = scope,
                    indexRelays = bigIndexSet,
                )

            coordinator.loadKind3Batched(listOf(pubkey(1)), timeoutMs = 2_000)
            delay(50) // wait for subscription

            // Fire EOSEs concurrently from many dispatchers.
            val jobs =
                bigIndexSet.map { relay ->
                    scope.launch(Dispatchers.IO) {
                        client.fireEose(relay)
                    }
                }
            jobs.forEach { it.join() }

            // The 2nd call must short-circuit — every relay EOSE'd, so
            // pubkey(1) is now in queuedKind3Pubkeys.
            delay(100)
            coordinator.loadKind3Batched(listOf(pubkey(1)), timeoutMs = 200)
            delay(50)

            assertEquals(
                "Under concurrent EOSE from all relays, aggregator must reach target",
                1,
                client.subscribeCalls.size,
            )
        }

    @Test
    fun `loadMetadataBatched follows the same retry semantics`() =
        runBlocking {
            val client = ControllableClient()
            val coordinator =
                FeedMetadataCoordinator(
                    client = client,
                    scope = scope,
                    indexRelays = indexRelays,
                )

            val pubkeys = listOf(pubkey(1), pubkey(2))

            // Call 1 — zero EOSE, timeout.
            coordinator.loadMetadataBatched(pubkeys, timeoutMs = 200)
            delay(350)
            // Call 2 — must re-subscribe.
            coordinator.loadMetadataBatched(pubkeys, timeoutMs = 200)
            delay(50)

            assertTrue(
                "Metadata batch also retries on zero-EOSE timeout",
                client.subscribeCalls.size >= 2,
            )
        }

    @Test
    fun `clear releases in-flight dedup so a fresh call always fires`() =
        runBlocking {
            val client = ControllableClient()
            val coordinator =
                FeedMetadataCoordinator(
                    client = client,
                    scope = scope,
                    indexRelays = indexRelays,
                )

            coordinator.loadKind3Batched(listOf(pubkey(1)), timeoutMs = 200)
            delay(50)
            // clear() must drop the in-flight tracker even mid-request.
            coordinator.clear()
            delay(300) // let call 1 finish + roll back

            coordinator.loadKind3Batched(listOf(pubkey(1)), timeoutMs = 200)
            delay(50)

            assertTrue(client.subscribeCalls.size >= 2)
        }
}
