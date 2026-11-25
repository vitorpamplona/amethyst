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
package com.vitorpamplona.amethyst.commons.model.marmot

import com.vitorpamplona.marmot.appComponents.GroupProfileV1
import com.vitorpamplona.marmot.client.InMemoryMlsGroupStateStore
import com.vitorpamplona.marmot.client.MarmotIngestResult
import com.vitorpamplona.marmot.client.MarmotManager
import com.vitorpamplona.marmot.client.MarmotPublisher
import com.vitorpamplona.marmot.client.MarmotReceiver
import com.vitorpamplona.marmot.client.ingest
import com.vitorpamplona.marmot.groups.MlsGroupStateStore
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarmotReceiveFlusherTest {
    /** Counts the group-state writes, the thing marmot-kmp batches every 32 opened messages. */
    private class CountingStateStore(
        private val inner: InMemoryMlsGroupStateStore = InMemoryMlsGroupStateStore(),
    ) : MlsGroupStateStore by inner {
        var saves = 0

        override suspend fun save(
            nostrGroupId: String,
            state: ByteArray,
        ) {
            saves++
            inner.save(nostrGroupId, state)
        }
    }

    private val accepting = MarmotPublisher { _, _ -> true }
    private val nostrGroupId = "5a".repeat(32)

    /**
     * Three messages, under the 32 marmot-kmp waits for before it writes the group: without a flush
     * the receive ratchets they moved stay in memory only, and a restart opens them again.
     */
    @Test
    fun aBurstUnderTheBatchIsWrittenByTheFlush() =
        runBlocking {
            val alice = MarmotManager(NostrSignerInternal(KeyPair()), InMemoryMlsGroupStateStore(), publisher = accepting)
            val bobStore = CountingStateStore()
            val bob = MarmotManager(NostrSignerInternal(KeyPair()), bobStore, publisher = accepting)
            alice.createCurrentProfileGroup(nostrGroupId = nostrGroupId, relays = listOf("wss://relay.invalid"), profile = GroupProfileV1("flush", ""))
            val (_, welcome) = alice.addMember(nostrGroupId, bob.generateKeyPackageEvent(relays = emptyList()), emptyList())
            bob.ingest(welcome!!.giftWrapEvent)

            val receiver = MarmotReceiver(bob) { _, _ -> }
            val scope = CoroutineScope(SupervisorJob())
            val flusher = MarmotReceiveFlusher(scope, flush = { bob.flushReceivedState() })
            val before = bobStore.saves
            repeat(3) {
                val result = receiver.receive(alice.buildTextMessage(nostrGroupId, "m$it").outbound.signedEvent)
                assertTrue("$result", result is MarmotIngestResult.Message)
            }
            assertEquals("marmot-kmp has not written the group yet", before, bobStore.saves)

            flusher.flushNow()
            assertEquals("the flush writes it", before + 1, bobStore.saves)
            flusher.flushNow()
            assertEquals("and nothing moved since", before + 1, bobStore.saves)
            scope.cancel()
        }

    @Test
    fun requestsInABurstWriteOnceAfterItGoesQuiet() =
        runTest {
            var flushes = 0
            val flusher = MarmotReceiveFlusher(backgroundScope, flush = { flushes++ }, quietMs = 1_000, maxWaitMs = 10_000, context = StandardTestDispatcher(testScheduler), timeSource = testScheduler.timeSource)
            repeat(5) {
                flusher.request()
                advanceTimeBy(500)
            }
            assertEquals("still in the burst", 0, flushes)
            advanceTimeBy(1_100)
            runCurrent()
            assertEquals(1, flushes)
        }

    @Test
    fun aBurstThatNeverStopsIsWrittenAtTheLongestWait() =
        runTest {
            var flushes = 0
            val flusher = MarmotReceiveFlusher(backgroundScope, flush = { flushes++ }, quietMs = 1_000, maxWaitMs = 5_000, context = StandardTestDispatcher(testScheduler), timeSource = testScheduler.timeSource)
            repeat(12) {
                flusher.request()
                advanceTimeBy(500)
            }
            runCurrent()
            assertEquals("written once at 5 s although events kept coming", 1, flushes)
        }
}
