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
package com.vitorpamplona.amethyst.commons.service.pow

import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip13Pow.tags.PoWTag
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PoWPublishQueueTest {
    val pubKey = "460c25e682fda7832b52d1f22d3d22b3176d972f60dcdc3212ed8c92ef85065c"

    val template =
        EventTemplate<TextNoteEvent>(
            1683596206,
            TextNoteEvent.KIND,
            emptyArray(),
            "A note to mine",
        )

    @Test
    fun minesTemplateThenRunsContinuation() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PoWPublishQueue(scope, maxConcurrent = 1)
            val mined = CompletableDeferred<EventTemplate<TextNoteEvent>>()

            queue.enqueue(template, pubKey, difficulty = 10) { mined.complete(it) }

            val result = withContext(Dispatchers.Default) { withTimeout(60_000) { mined.await() } }

            val powTag = result.tags.firstNotNullOfOrNull { PoWTag.parse(it) }
            assertNotNull(powTag, "mined template must carry a nonce tag")
            assertEquals(10, powTag.commitment)

            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            scope.cancel()
        }

    @Test
    fun cancellingAQueuedJobSkipsItsWork() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PoWPublishQueue(scope, maxConcurrent = 1)

            val gate = CompletableDeferred<Unit>()
            var secondRan = false

            // occupies the single worker until the gate opens
            queue.enqueueWork(kind = 1, difficulty = 10) { gate.await() }
            queue.enqueueWork(kind = 1, difficulty = 10) { secondRan = true }

            val jobs = queue.jobs.value
            assertEquals(2, jobs.size)
            assertTrue(jobs.map { it.kind }.all { it == 1 })

            queue.cancel(jobs[1].id)
            assertEquals(1, queue.jobs.value.size, "cancelled job leaves the visible queue immediately")

            gate.complete(Unit)

            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            assertFalse(secondRan, "cancelled job must never run")
            scope.cancel()
        }

    @Test
    fun jobsRunInFifoOrder() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val queue = PoWPublishQueue(scope, maxConcurrent = 1)

            val order = mutableListOf<Int>()
            val done = CompletableDeferred<Unit>()

            repeat(3) { index ->
                queue.enqueueWork(kind = 1, difficulty = 10) {
                    order.add(index)
                    if (index == 2) done.complete(Unit)
                }
            }

            withContext(Dispatchers.Default) { withTimeout(10_000) { done.await() } }
            assertEquals(listOf(0, 1, 2), order)
            scope.cancel()
        }

    private class FakePersistence : PoWJobPersistence {
        val saved = mutableListOf<String>()
        val removed = mutableListOf<String>()

        override fun save(job: PersistedPoWJob) {
            saved.add(job.id)
        }

        override fun remove(jobId: String) {
            removed.add(jobId)
        }
    }

    private fun recordFor(id: String) =
        PersistedPoWJob(
            id = id,
            accountPubkey = pubKey,
            kind = TextNoteEvent.KIND,
            difficulty = 10,
            templateJson = template.toJson(),
            replayType = PersistedPoWJob.REPLAY_BROADCAST,
        )

    @Test
    fun persistedJobIsSavedThenRemovedOnCompletion() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val persistence = FakePersistence()
            val queue = PoWPublishQueue(scope, maxConcurrent = 1, persistence = persistence)
            val mined = CompletableDeferred<Unit>()

            queue.enqueue(template, pubKey, difficulty = 10, persistAs = recordFor("job-a")) { mined.complete(Unit) }
            assertEquals(listOf("job-a"), persistence.saved)

            withContext(Dispatchers.Default) { withTimeout(60_000) { mined.await() } }
            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            assertEquals(listOf("job-a"), persistence.removed)
            scope.cancel()
        }

    @Test
    fun cancellingAPersistedJobRemovesItsCheckpoint() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val persistence = FakePersistence()
            val queue = PoWPublishQueue(scope, maxConcurrent = 1, persistence = persistence)

            val gate = CompletableDeferred<Unit>()
            queue.enqueueWork(kind = 1, difficulty = 10) { gate.await() }
            queue.enqueue(template, pubKey, difficulty = 10, persistAs = recordFor("job-b")) {}

            queue.cancel("job-b")
            assertTrue("job-b" in persistence.removed, "cancel must drop the checkpoint")

            gate.complete(Unit)
            withContext(Dispatchers.Default) { withTimeout(10_000) { queue.jobs.first { it.isEmpty() } } }
            scope.cancel()
        }

    @Test
    fun duplicateJobIdsAreEnqueuedOnce() =
        runTest {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val persistence = FakePersistence()
            val queue = PoWPublishQueue(scope, maxConcurrent = 1, persistence = persistence)

            val gate = CompletableDeferred<Unit>()
            queue.enqueueWork(kind = 1, difficulty = 10) { gate.await() }

            queue.enqueue(template, pubKey, difficulty = 10, persistAs = recordFor("job-c")) {}
            queue.enqueue(template, pubKey, difficulty = 10, persistAs = recordFor("job-c")) {}

            assertEquals(2, queue.jobs.value.size, "restore-style re-enqueue of the same id must not duplicate")
            assertEquals(listOf("job-c"), persistence.saved)

            gate.complete(Unit)
            withContext(Dispatchers.Default) { withTimeout(60_000) { queue.jobs.first { it.isEmpty() } } }
            scope.cancel()
        }
}
