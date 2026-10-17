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
import com.vitorpamplona.quartz.nip01Core.core.HexKey
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PageRetryBackoff
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.PagedFetchResult
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPages
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.fetchAllPagesResuming
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A relay that hangs up while an event is still being handed on. The OkHttp socket cancels the
 * coroutine that delivers its messages when the connection ends, so a listener suspended
 * downstream (amy's export waits on a bounded buffer for its writer) is cancelled half way
 * through the event. Measured with amy against a proxy that drops the connection every ~20 MB:
 * one event lost per drop, the export exiting 0 with 19,990 of 20,000. Each walk had marked the
 * event delivered before handing it on, so its re-fetched copy was dropped as a repeat.
 *
 * The consumer here suspends on the first copy of [victim], the way a full buffer does.
 */
class NostrClientFetchAllPagesCancelledDeliveryTest {
    private val relay = RelayUrlNormalizer.normalize("wss://hangs-up.example.com")
    private val corpus = FakePagingRelay.corpus(2_000)
    private val all = Filter(kinds = listOf(1))
    private val noWaits = PageRetryBackoff(PageRetryBackoff.RESUME.delaysMs) { }
    private val noThrottleReAsks = PageRetryBackoff(emptyList())

    private class Consumer(
        private val victim: HexKey,
    ) {
        val got: MutableList<HexKey> = Collections.synchronizedList(mutableListOf())
        private val firstCopy = AtomicBoolean(true)

        suspend fun take(event: Event) {
            if (event.id == victim && firstCopy.getAndSet(false)) delay(10_000)
            got.add(event.id)
        }
    }

    @Test
    fun aResumedWalkDeliversTheEventItsDropCancelled() =
        runBlocking {
            // REQ 2 re-sends the boundary event first, so index 100 is corpus[599].
            val consumer = Consumer(corpus[599].id)
            val client = FakePagingRelay(this, corpus, maxLimit = 500, dropDuring = { req -> if (req == 2) 100 else null })
            val result = client.fetchAllPagesResuming(relay, listOf(all), idleTimeoutMs = 2_000, throttleBackoff = noThrottleReAsks, resumeBackoff = noWaits, onEvent = consumer::take)

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(corpus.map { it.id }.toSet(), consumer.got.toSet())
            assertEquals(consumer.got.size, consumer.got.toSet().size, "nothing delivered twice")
            assertEquals(corpus.size, result.downloaded)
        }

    // amy runs the walk on the same thread as a slow consumer, so the pool redialed and re-sent the
    // page's REQ before the walk heard the drop: the page's listener took the replay, and dropped
    // the cancelled event's copy as one it had already seen. Measured: 9-10 of 20,000 lost, one
    // per drop, on `amy fetch --paginate --limit 0` to stdout.
    @Test
    fun aPageReplayedBeforeTheWalkHearsTheDropStillDeliversTheCancelledEvent() =
        runBlocking {
            val consumer = Consumer(corpus[100].id)
            val client = FakePagingRelay(this, corpus, maxLimit = 500, dropDuring = { req -> if (req == 1) 100 else null }, replayAfterDrop = true)
            val result = client.fetchAllPages(relay, listOf(all), idleTimeoutMs = 2_000, throttleBackoff = noThrottleReAsks, onEvent = consumer::take)

            assertEquals(PagedFetchResult.End.DRAINED, result.end)
            assertEquals(corpus.map { it.id }.toSet(), consumer.got.toSet())
            assertEquals(consumer.got.size, consumer.got.toSet().size, "nothing delivered twice")
        }

    @Test
    fun aCancelledEventDoesNotCountTowardTheLimit() =
        runBlocking {
            val consumer = Consumer(corpus[599].id)
            val client = FakePagingRelay(this, corpus, maxLimit = 500, dropDuring = { req -> if (req == 2) 100 else null })
            val result = client.fetchAllPages(relay, listOf(all.copy(limit = 700)), idleTimeoutMs = 2_000, throttleBackoff = noThrottleReAsks, onEvent = consumer::take)

            assertEquals(PagedFetchResult.End.LIMIT_REACHED, result.end)
            assertEquals(corpus.take(700).map { it.id }.toSet(), consumer.got.toSet())
            assertEquals(700, consumer.got.size)
        }

    @Test
    fun aCancelledEventAnotherFilterSharesIsStillDelivered() =
        runBlocking {
            // The older filter's first page is corpus[1000..1499], all of which the newer one also
            // matches; the drop cancels corpus[1100], which the first walk then asks for again.
            val older = all.copy(until = corpus[1_000].createdAt)
            val newer = all.copy(since = corpus[1_499].createdAt)
            val consumer = Consumer(corpus[1_100].id)
            val client = FakePagingRelay(this, corpus, maxLimit = 500, dropDuring = { req -> if (req == 1) 100 else null })
            val result = client.fetchAllPages(relay, listOf(older, newer), idleTimeoutMs = 2_000, throttleBackoff = noThrottleReAsks, onEvent = consumer::take)

            assertEquals(corpus.map { it.id }.toSet(), consumer.got.toSet())
            assertEquals(consumer.got.size, consumer.got.toSet().size, "the overlap delivered once")
            assertEquals(corpus.size, result.downloaded)
        }
}
