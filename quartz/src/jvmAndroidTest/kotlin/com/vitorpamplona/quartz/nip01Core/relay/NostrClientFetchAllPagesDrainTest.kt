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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PagedFetchResult
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
 * Pins [PagedFetchResult.End] — WHY a walk stopped, which is the half of the
 * answer `downloaded` cannot carry.
 *
 * It matters enormously to anything recording sync coverage: without it, "the
 * relay has nothing older" and "the relay capped us / went quiet / hung up" look
 * identical, so a coverage band can never close its oldest leg and re-asks a
 * range that will always come back empty, every cycle, forever.
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

            val result =
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(1))),
                    idleTimeoutMs = 2_000,
                ) { }
            feeder.join()

            assertEquals(2, result.downloaded)
            assertEquals(PagedFetchResult.End.DRAINED, result.end, "an empty page the relay EOSEd is proof there is nothing older")
            assertTrue(result.drained)
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

            val result =
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(1))),
                    idleTimeoutMs = 200,
                ) { }
            feeder.join()

            assertEquals(2, result.downloaded, "the events already delivered are still kept")
            assertEquals(PagedFetchResult.End.IDLE, result.end)
            assertFalse(result.drained, "an idle timeout says nothing about what the relay holds")
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

            val result =
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(1))),
                    idleTimeoutMs = 2_000,
                ) { }
            feeder.join()

            assertEquals(PagedFetchResult.End.CLOSED, result.end, "a CLOSED is the relay declining, not an empty corpus")
            assertFalse(result.drained)
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

            val result =
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(1))),
                    idleTimeoutMs = 2_000,
                ) { }
            feeder.join()

            assertEquals(PagedFetchResult.End.CANNOT_CONNECT, result.end, "never got to ask")
            assertFalse(result.drained)
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

            val result =
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(1), limit = 2)),
                    idleTimeoutMs = 2_000,
                ) { }
            feeder.join()

            assertEquals(2, result.downloaded)
            assertEquals(1, client.subscribeCount, "the limit was met, so there was no second page")
            assertEquals(PagedFetchResult.End.LIMIT_REACHED, result.end, "a fulfilled limit is the caller stopping, not the corpus ending")
            assertFalse(result.drained)
        }

    // ---- termination: the walk must END, whatever the relay does -------------

    @Test
    fun aRelayThatIgnoresTheCursorEndsTheWalkInsteadOfSteppingForever() =
        runBlocking {
            // The production bug, scripted. purplepag.es holds events stamped
            // `created_at = 0` and treats `until <= 0` as NO `until`, so the page
            // below them comes back with its NEWEST events instead. None of those
            // matches the filter's own `until`, so the page delivers nothing —
            // which used to read as "the boundary second is too dense", step one
            // second lower, and ask the identical unanswerable question again.
            // Measured against the live relay: ~5.5 pages a second, 500 events
            // discarded on each, an EOSE on every one, for as long as the process
            // ran. `aboveBoundary == received` is what tells the two apart.
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onEvent(event(2000), false, relay, null)
                    client.listener!!.onEvent(event(1000), false, relay, null)
                    client.listener!!.onEose(relay, null)

                    // Page two asks for `until = 1000` and gets events from the top
                    // of the corpus — the answer to a query nobody made.
                    client.awaitPage(2)
                    client.listener!!.onEvent(event(9000), false, relay, null)
                    client.listener!!.onEvent(event(8000), false, relay, null)
                    client.listener!!.onEose(relay, null)
                }

            val result =
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(1))),
                    idleTimeoutMs = 2_000,
                ) { }
            feeder.join()

            assertEquals(2, result.downloaded, "only the two events the relay actually answered for")
            assertEquals(2, client.subscribeCount, "and it stops on the FIRST page the relay refused to page")
            assertEquals(PagedFetchResult.End.UNPAGEABLE, result.end, "a relay ignoring `until` is not paging, and cannot be stepped past")
            assertFalse(result.drained, "which proves nothing about what it holds, so no coverage may be claimed")
        }

    @Test
    fun aCursorSteppingUnderTheEpochDrainsInsteadOfGoingNegative() =
        runBlocking {
            // `created_at` is unsigned, so nothing exists below epoch 0. A boundary
            // second AT the epoch that only ever returns duplicates has reached the
            // bottom of the time axis: the walk is done, and `until = -1` must never
            // reach a relay — one of the five indexers CLOSEs the subscription over
            // it, three answer a NOTICE and then never EOSE.
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onEvent(event(0), false, relay, null)
                    client.listener!!.onEose(relay, null)

                    // Page two re-asks the boundary inclusively and gets back only
                    // the event page one already delivered: nothing new, and nowhere
                    // left below to step to.
                    client.awaitPage(2)
                    client.listener!!.onEvent(event(0), false, relay, null)
                    client.listener!!.onEose(relay, null)
                }

            val result =
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(1))),
                    idleTimeoutMs = 2_000,
                ) { }
            feeder.join()

            assertEquals(1, result.downloaded, "the epoch event, delivered once")
            assertEquals(2, client.subscribeCount, "no third page: there is nothing under zero to ask for")
            assertEquals(PagedFetchResult.End.DRAINED, result.end, "the bottom of the time axis is an end, not a stall")
            assertTrue(result.drained)
        }

    @Test
    fun anEventStampedBeforeTheEpochCannotPinTheWalk() =
        runBlocking {
            // `pageMinTs` is an event's own `created_at`, so one relay serving a
            // negative timestamp drives the cursor under zero on the ADVANCE path
            // rather than the step path. Clamping to 0 would not save it: such an
            // event never equals the boundary, so it dodges the dedup and comes
            // back on every page, pinning the walk at 0 for good.
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onEvent(event(2000), false, relay, null)
                    client.listener!!.onEvent(event(-5), false, relay, null)
                    client.listener!!.onEose(relay, null)
                }

            val result =
                client.fetchAllPages(
                    relay = relay,
                    filters = listOf(Filter(kinds = listOf(1))),
                    idleTimeoutMs = 2_000,
                ) { }
            feeder.join()

            assertEquals(2, result.downloaded, "both events are still delivered — they were received")
            assertEquals(1, client.subscribeCount, "but there is no second page to ask")
            assertEquals(PagedFetchResult.End.DRAINED, result.end, "below the epoch there is nothing left to walk")
        }
}
