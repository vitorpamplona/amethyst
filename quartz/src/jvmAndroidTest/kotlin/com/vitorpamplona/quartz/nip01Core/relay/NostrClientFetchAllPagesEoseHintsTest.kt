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

/**
 * NIP-67 completeness hints steering [fetchAllPages]: `finish` ends the walk without the
 * extra empty-page REQ, `more` keeps paging, and an unanswered `auth` hint keeps the walk
 * from claiming DRAINED.
 */
class NostrClientFetchAllPagesEoseHintsTest {
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

        suspend fun awaitPage(n: Int) {
            while (subscribeCount < n) delay(2)
        }
    }

    private val relay = RelayUrlNormalizer.normalize("wss://hints.example.com")

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
    fun finishEndsTheWalkWithoutAnotherPage() =
        runBlocking {
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onEvent(event(2000), false, relay, null)
                    client.listener!!.onEvent(event(1000), false, relay, null)
                    client.listener!!.onEose(relay, null, listOf("finish"))
                }

            val result = client.fetchAllPages(relay = relay, filters = listOf(Filter(kinds = listOf(1))), idleTimeoutMs = 2_000) { }
            feeder.join()

            assertEquals(2, result.downloaded)
            assertEquals(PagedFetchResult.End.DRAINED, result.end, "the relay said it sent every stored match")
            assertEquals(1, client.subscribeCount, "no second REQ just to observe an empty page")
        }

    @Test
    fun moreKeepsPaging() =
        runBlocking {
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onEvent(event(2000), false, relay, null)
                    client.listener!!.onEose(relay, null, listOf("more"))

                    client.awaitPage(2)
                    client.listener!!.onEvent(event(1000), false, relay, null)
                    client.listener!!.onEose(relay, null, listOf("finish"))
                }

            val result = client.fetchAllPages(relay = relay, filters = listOf(Filter(kinds = listOf(1))), idleTimeoutMs = 2_000) { }
            feeder.join()

            assertEquals(2, result.downloaded)
            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(2, client.subscribeCount)
        }

    @Test
    fun unknownHintsAreIgnored() =
        runBlocking {
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onEvent(event(2000), false, relay, null)
                    client.listener!!.onEose(relay, null, listOf("somethingNew"))
                    client.awaitPage(2)
                    client.listener!!.onEose(relay, null, emptyList())
                }

            val result = client.fetchAllPages(relay = relay, filters = listOf(Filter(kinds = listOf(1))), idleTimeoutMs = 2_000) { }
            feeder.join()

            assertEquals(1, result.downloaded)
            assertEquals(PagedFetchResult.End.DRAINED, result.end, "falls back to the empty-page heuristic")
            assertEquals(2, client.subscribeCount)
        }

    @Test
    fun finishWithAnUnansweredAuthHintCannotClaimDrained() =
        runBlocking {
            // No NIP-42 responder is attached, so the "auth" hint cannot be acted on.
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onEvent(event(2000), false, relay, null)
                    client.listener!!.onEose(relay, null, listOf("auth", "finish"))
                }

            val result = client.fetchAllPages(relay = relay, filters = listOf(Filter(kinds = listOf(1))), idleTimeoutMs = 2_000) { }
            feeder.join()

            assertEquals(1, result.downloaded, "what was delivered still counts")
            assertEquals(PagedFetchResult.End.AUTH_REQUIRED, result.end, "the relay may hold more for an authenticated user")
            assertEquals(1, client.subscribeCount)
        }

    @Test
    fun anEmptyPageWithAnAuthHintIsNotADrain() =
        runBlocking {
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onEvent(event(2000), false, relay, null)
                    client.listener!!.onEose(relay, null, listOf("auth"))
                    client.awaitPage(2)
                    client.listener!!.onEose(relay, null, listOf("auth"))
                }

            val result = client.fetchAllPages(relay = relay, filters = listOf(Filter(kinds = listOf(1))), idleTimeoutMs = 2_000) { }
            feeder.join()

            assertEquals(1, result.downloaded)
            assertEquals(PagedFetchResult.End.AUTH_REQUIRED, result.end)
        }

    @Test
    fun finishAfterAFilterMetItsLimitReportsLimitReached() =
        runBlocking {
            val client = ScriptedClient()
            val feeder =
                launch {
                    client.awaitPage(1)
                    client.listener!!.onEvent(event(2000), false, relay, null)
                    client.listener!!.onEvent(event(1000), false, relay, null)
                    client.listener!!.onEose(relay, null, listOf("finish"))
                }

            val result = client.fetchAllPages(relay = relay, filters = listOf(Filter(kinds = listOf(1), limit = 2)), idleTimeoutMs = 2_000) { }
            feeder.join()

            assertEquals(2, result.downloaded)
            assertEquals(PagedFetchResult.End.LIMIT_REACHED, result.end)
            assertEquals(1, client.subscribeCount)
        }
}
