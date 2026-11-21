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
package com.vitorpamplona.amethyst.commons.model.cordn

import com.vitorpamplona.cordn.client.CordnGroupManager
import com.vitorpamplona.cordn.client.CordnSyncSource
import com.vitorpamplona.cordn.spec02Envelopes.CordnDeliveredMessage
import com.vitorpamplona.cordn.spec02Envelopes.CordnEnvelope
import com.vitorpamplona.cordn.spec02Envelopes.CordnMessageKinds
import com.vitorpamplona.cordn.spec02Envelopes.ReceivedMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A catch-up's messages reach a room in batches, so filing a history is linear rather than one
 * whole-room re-sort and re-fold per message, and nothing is lost, reordered or held back once the
 * catch-up ends.
 */
class CordnDeliveryBatcherTest {
    private val alice = "a".repeat(64)

    private fun message(
        gid: String,
        cursor: Long,
    ): CordnGroupManager.Delivery.Message {
        val envelope =
            CordnEnvelope(id = "$gid-$cursor".padEnd(64, '0'), pubKey = alice, createdAt = cursor, kind = CordnMessageKinds.TEXT, tags = emptyArray(), content = "m$cursor")
        return CordnGroupManager.Delivery.Message(gid, cursor, ReceivedMessage(sender = alice, senderLeafIndex = 0, epoch = 1, envelope = envelope))
    }

    private class Recorder {
        val batches = mutableListOf<Pair<String, List<Long>>>()
        val others = mutableListOf<CordnGroupManager.Delivery>()
        val order = mutableListOf<String>()

        fun messages(
            gid: String,
            list: List<CordnDeliveredMessage>,
        ) {
            batches += gid to list.map { it.cursor }
            order += "batch:$gid:${list.size}"
        }

        fun other(delivery: CordnGroupManager.Delivery) {
            others += delivery
            order += "other:${delivery.gid}"
        }
    }

    private class FakeSource(
        private val deliveries: List<CordnGroupManager.Delivery>,
    ) : CordnSyncSource {
        override val gids: StateFlow<Set<String>> = MutableStateFlow(setOf("g"))

        override suspend fun catchUp(onDelivery: (CordnGroupManager.Delivery) -> Unit): Int {
            deliveries.forEach(onDelivery)
            return deliveries.size
        }

        override suspend fun subscribe(
            timeoutMs: Long,
            onDelivery: (CordnGroupManager.Delivery) -> Unit,
        ) = deliveries.forEach(onDelivery)
    }

    @Test
    fun aCatchUpIsFiledInBatchesAndTheRestWhenItEnds() =
        runBlocking {
            val recorder = Recorder()
            val source = BatchingCordnSyncSource(FakeSource((1L..250L).map { message("g", it) }), recorder::messages, batchSize = 100)

            source.catchUp(recorder::other)

            assertEquals(listOf(100, 100, 50), recorder.batches.map { it.second.size })
            assertEquals((1L..250L).toList(), recorder.batches.flatMap { it.second }, "every message, in delivery order")
            assertTrue(recorder.others.isEmpty(), "messages never go one by one")
        }

    @Test
    fun anyOtherDeliveryFirstFilesItsGroupsWaitingMessages() =
        runBlocking {
            val recorder = Recorder()
            val deliveries =
                listOf(
                    message("g", 1),
                    message("h", 2),
                    message("g", 3),
                    CordnGroupManager.Delivery.EpochAdvanced("g", 4, epoch = 2),
                    message("g", 5),
                )
            BatchingCordnSyncSource(FakeSource(deliveries), recorder::messages, batchSize = 100).catchUp(recorder::other)

            assertEquals(listOf("batch:g:2", "other:g", "batch:h:1", "batch:g:1"), recorder.order)
        }

    @Test
    fun aCatchUpThatFailsStillFilesWhatItRead() {
        val recorder = Recorder()
        val failing =
            object : CordnSyncSource {
                override val gids: StateFlow<Set<String>> = MutableStateFlow(setOf("g"))

                override suspend fun catchUp(onDelivery: (CordnGroupManager.Delivery) -> Unit): Int {
                    onDelivery(message("g", 1))
                    error("coordinator went away")
                }

                override suspend fun subscribe(
                    timeoutMs: Long,
                    onDelivery: (CordnGroupManager.Delivery) -> Unit,
                ) = Unit
            }

        runCatching { runBlocking { BatchingCordnSyncSource(failing, recorder::messages).catchUp(recorder::other) } }

        assertEquals(listOf("g" to listOf(1L)), recorder.batches)
    }

    @Test
    fun aLiveSubscriptionIsNotBatched() =
        runBlocking {
            val recorder = Recorder()
            BatchingCordnSyncSource(FakeSource(listOf(message("g", 1), message("g", 2))), recorder::messages).subscribe(1_000, recorder::other)

            assertTrue(recorder.batches.isEmpty())
            assertEquals(2, recorder.others.size)
        }
}
