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
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.streamAllPagesFromPoolWithHooks
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The streamed pool walk (`amy fetch --paginate --limit 0`) must hold memory to a bounded
 * backlog. Its events used to cross into the consumer through an unbounded channel with
 * `trySend`, so nothing slowed a relay's walk when the consumer (signature check, store write,
 * a slow stdout pipe) fell behind: each page's EOSE was processed at once, the next REQ went
 * out, and the whole walk could pile up on the heap.
 */
class NostrClientStreamAllPagesBackpressureTest {
    private val relay = RelayUrlNormalizer.normalize("wss://stream.example.com")

    @Test
    fun aSlowConsumerHoldsTheWalkBack() =
        runBlocking {
            // Hash-like ids: the cross-relay dedup keys on the id's leading bytes, which
            // FakePagingRelay.corpus's zero-padded ids all share.
            val corpus = FakePagingRelay.corpus(3_000).map { Event(sha256Hex(it.id), it.pubKey, it.createdAt, it.kind, it.tags, it.content, it.sig) }
            val client = FakePagingRelay(this, corpus, maxLimit = 500)
            val release = CompletableDeferred<Unit>()
            var seen = 0

            val walk =
                async {
                    client.streamAllPagesFromPoolWithHooks(mapOf(relay to listOf(Filter(kinds = listOf(1)))), idleTimeoutMs = 10_000) { _, _ ->
                        if (++seen == 1) release.await()
                        true
                    }
                }

            // The consumer is stuck on its first event: the walk must not run ahead of it.
            delay(1_000)
            assertEquals(1, client.requests.size, "no further page while the consumer is behind: ${client.requests.size} REQs")

            release.complete(Unit)
            assertEquals(3_000, walk.await())
            assertTrue(client.requests.size > 1)
        }

    private fun sha256Hex(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
