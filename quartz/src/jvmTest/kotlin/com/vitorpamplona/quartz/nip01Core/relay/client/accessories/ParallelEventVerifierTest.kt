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
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParallelEventVerifierTest {
    private val signer = NostrSignerSync(KeyPair())

    private fun signed(i: Int): Event = signer.sign(TextNoteEvent.build("note $i", createdAt = i.toLong()))

    private fun tampered(i: Int): Event {
        val e = signed(i)
        // altered content, original id/sig — must fail verification
        return Event(e.id, e.pubKey, e.createdAt, e.kind, e.tags, e.content + " tampered", e.sig)
    }

    private fun <T> withVerifierScope(block: (CoroutineScope) -> T): T {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            return block(scope)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun routesValidAndTamperedEventsCorrectly() =
        withVerifierScope { scope ->
            runBlocking {
                val valid = (1..40).map { signed(it) }
                val invalid = (100..109).map { tampered(it) }

                val verified = mutableListOf<Event>()
                val rejected = mutableListOf<Event>()
                val verifier =
                    ParallelEventVerifier<String>(
                        scope = scope,
                        onInvalid = { e, _ -> rejected.add(e) },
                        onVerified = { e, _ -> verified.add(e) },
                    )

                (valid + invalid).shuffled().forEach { verifier.submit(it, "relay") }
                verifier.close()
                withTimeout(30_000) { verifier.join() }

                assertEquals(valid.map { it.id }.toSet(), verified.map { it.id }.toSet())
                assertEquals(invalid.map { it.id }.toSet(), rejected.map { it.id }.toSet())
                assertEquals(40L, verifier.verifiedCount)
                assertEquals(10L, verifier.invalidCount)
            }
        }

    @Test
    fun callbacksArriveInSubmissionOrder() =
        withVerifierScope { scope ->
            runBlocking {
                val events = (1..300).map { signed(it) }
                val order = mutableListOf<Long>()
                val verifier =
                    ParallelEventVerifier<Unit>(
                        scope = scope,
                        maxBatch = 16,
                        onVerified = { e, _ -> order.add(e.createdAt) },
                    )
                events.forEach { verifier.submit(it, Unit) }
                verifier.close()
                withTimeout(30_000) { verifier.join() }

                assertEquals(events.map { it.createdAt }, order, "parallel verify must not reorder callbacks")
            }
        }

    @Test
    fun preVerifiedShortCircuitSkipsTheCpuButStillDelivers() =
        withVerifierScope { scope ->
            runBlocking {
                // Tampered events would FAIL verification — marking them preVerified
                // must route them to onVerified without running the check.
                val events = (1..20).map { tampered(it) }
                val delivered = mutableListOf<Event>()
                val verifier =
                    ParallelEventVerifier<Unit>(
                        scope = scope,
                        preVerified = { true },
                        onVerified = { e, _ -> delivered.add(e) },
                    )
                events.forEach { verifier.submit(it, Unit) }
                verifier.close()
                withTimeout(30_000) { verifier.join() }

                assertEquals(20, delivered.size)
                assertEquals(20L, verifier.verifiedCount)
            }
        }

    @Test
    fun misbehavingCallbackDoesNotKillTheDrainLoop() =
        withVerifierScope { scope ->
            runBlocking {
                val events = (1..10).map { signed(it) }
                var delivered = 0
                val verifier =
                    ParallelEventVerifier<Unit>(
                        scope = scope,
                        maxBatch = 2,
                        onVerified = { _, _ ->
                            delivered++
                            if (delivered == 3) throw IllegalStateException("boom")
                        },
                    )
                events.forEach { verifier.submit(it, Unit) }
                verifier.close()
                withTimeout(30_000) { verifier.join() }

                assertEquals(10, delivered, "all events dispatched despite a throwing callback")
                assertTrue(verifier.verifiedCount == 10L)
            }
        }

    @Test
    fun cancellingTheScopeReleasesASubmitterBlockedOnAFullBacklog() {
        // A relay reader thread blocked on a full backlog when the sync is cancelled (the user
        // switched provider) must not stay blocked: it would hold the thread and the backlog's
        // events for the rest of the process.
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val drainStarted = CountDownLatch(1)
        val verifier =
            ParallelEventVerifier<Unit>(scope = scope, maxBatch = 1, capacity = 1) { _, _ ->
                // Slow on the first event only: the scope is cancelled meanwhile, and the drain
                // ends at its next receive, as it does mid-download.
                if (drainStarted.count > 0) {
                    drainStarted.countDown()
                    Thread.sleep(1_000)
                }
            }
        val returned = CountDownLatch(1)
        val reader =
            thread {
                repeat(10) { verifier.submit(signed(it), Unit) }
                returned.countDown()
            }
        assertTrue(drainStarted.await(5, TimeUnit.SECONDS))
        Thread.sleep(200) // the reader is now blocked on the full channel
        scope.cancel()
        assertTrue(returned.await(5, TimeUnit.SECONDS), "the blocked submitter was released")
        reader.join(1_000)
    }
}
