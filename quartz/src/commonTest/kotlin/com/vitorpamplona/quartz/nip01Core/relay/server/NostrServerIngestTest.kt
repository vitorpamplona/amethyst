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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import com.vitorpamplona.quartz.utils.DeterministicSigner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The local-ingest path ([NostrServer.ingest]) and its relay-to-relay
 * trust switch: `skipVerify = true` (a mirror streaming from a trusted
 * upstream) must land forged-signature events, while the default keeps
 * verify-everything semantics through the IngestQueue's parallel hook.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NostrServerIngestTest {
    private val signer = DeterministicSigner(KeyPair())

    private fun signedEvent(content: String = "hello"): Event = signer.sign(createdAt = 1000L, kind = 1, tags = emptyArray(), content = content)

    /** A structurally valid event whose signature verifies against nothing. */
    private fun forgedEvent(id: Int = 1): Event =
        Event(
            id = id.toString().padStart(64, '0'),
            pubKey = signer.pubKey,
            createdAt = 1000L + id,
            kind = 1,
            tags = emptyArray(),
            content = "forged",
            sig = "f".repeat(128),
        )

    private fun createServer(dispatcher: CoroutineDispatcher): NostrServer =
        NostrServer(
            store = EventStore(null),
            policyBuilder = { EmptyPolicy },
            parentContext = dispatcher,
            parallelVerify = true,
        )

    private suspend fun NostrServer.ingestOutcome(
        event: Event,
        skipVerify: Boolean,
    ): IEventStore.InsertOutcome {
        val outcome = CompletableDeferred<IEventStore.InsertOutcome>()
        ingest(event, skipVerify) { outcome.complete(it) }
        return outcome.await()
    }

    @Test
    fun forgedEventIsRejectedByDefault() =
        runTest {
            val server = createServer(UnconfinedTestDispatcher(testScheduler))

            val outcome = server.ingestOutcome(forgedEvent(), skipVerify = false)

            assertTrue(outcome is IEventStore.InsertOutcome.Rejected)
            assertTrue(outcome.reason.contains("signature"))

            server.close()
        }

    @Test
    fun forgedEventLandsWhenTrusted() =
        runTest {
            val server = createServer(UnconfinedTestDispatcher(testScheduler))

            val outcome = server.ingestOutcome(forgedEvent(), skipVerify = true)

            assertEquals(IEventStore.InsertOutcome.Accepted, outcome)

            server.close()
        }

    @Test
    fun validEventLandsEitherWay() =
        runTest {
            val server = createServer(UnconfinedTestDispatcher(testScheduler))

            assertEquals(
                IEventStore.InsertOutcome.Accepted,
                server.ingestOutcome(signedEvent("via verify"), skipVerify = false),
            )
            assertEquals(
                IEventStore.InsertOutcome.Accepted,
                server.ingestOutcome(signedEvent("via trust"), skipVerify = true),
            )

            server.close()
        }

    @Test
    fun trustIsPerSubmissionNotPerQueue() =
        runTest {
            // A trusted mirror and an untrusted publisher share the same
            // IngestQueue; the skip must apply row-by-row, never leak from
            // one submission to the next.
            val server = createServer(UnconfinedTestDispatcher(testScheduler))

            assertEquals(
                IEventStore.InsertOutcome.Accepted,
                server.ingestOutcome(forgedEvent(1), skipVerify = true),
            )
            val untrusted = server.ingestOutcome(forgedEvent(2), skipVerify = false)
            assertTrue(untrusted is IEventStore.InsertOutcome.Rejected)

            server.close()
        }

    @Test
    fun trustedIngestFansOutToLiveSubscribers() =
        runTest {
            // Mirror traffic must feed live REQs exactly like a client
            // publish — the skip changes verification, not delivery.
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val server = createServer(dispatcher)

            val received = mutableListOf<String>()
            val session = server.connect { received.add(it) }
            session.receive("""["REQ","live",{"kinds":[1]}]""")
            assertTrue(received.any { it.startsWith("[\"EOSE\"") })

            val forged = forgedEvent()
            assertEquals(
                IEventStore.InsertOutcome.Accepted,
                server.ingestOutcome(forged, skipVerify = true),
            )

            assertTrue(received.any { it.startsWith("[\"EVENT\",\"live\"") && it.contains(forged.id) })

            server.close()
        }
}
