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
import kotlinx.coroutines.flow.StateFlow

/**
 * Files one catch-up's deliveries a batch at a time instead of one by one.
 *
 * A room rebuilds its annotation index and re-sorts all its messages on every filing
 * (`CordnGroupChatroom.addAll`), and `catch_up` after a restart hands back a group's whole
 * history one delivery at a time, so filing each on arrival cost O(N^2 log N). This holds each
 * group's application messages until [batchSize] of them are waiting, or until the catch-up
 * ends ([flushAll]), and files them together: the room is current a batch behind during a
 * catch-up and exactly current after it.
 *
 * Any other delivery (an epoch change, an echo, a gap) first flushes its group's waiting
 * messages, then is filed at once, so a group never sees them out of order.
 *
 * Not thread-safe: one catch-up feeds one batcher, sequentially.
 */
class CordnDeliveryBatcher(
    private val fileMessages: (gid: String, messages: List<CordnDeliveredMessage>) -> Unit,
    private val fileOther: (CordnGroupManager.Delivery) -> Unit,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
) {
    private val waiting = LinkedHashMap<String, MutableList<CordnDeliveredMessage>>()

    fun offer(delivery: CordnGroupManager.Delivery) {
        if (delivery is CordnGroupManager.Delivery.Message) {
            val list = waiting.getOrPut(delivery.gid) { ArrayList() }
            list.add(delivery.delivered)
            if (list.size >= batchSize) flush(delivery.gid)
        } else {
            flush(delivery.gid)
            fileOther(delivery)
        }
    }

    private fun flush(gid: String) {
        val list = waiting.remove(gid) ?: return
        if (list.isNotEmpty()) fileMessages(gid, list)
    }

    /** Files everything still waiting. */
    fun flushAll() {
        waiting.keys.toList().forEach(::flush)
    }

    companion object {
        /** About a coordinator page: enough to make a catch-up linear, few enough to keep the room moving. */
        const val DEFAULT_BATCH_SIZE = 100
    }
}

/**
 * [delegate], with each [catchUp] filed through a [CordnDeliveryBatcher]: its messages go to
 * [fileMessages] in batches, everything else to the loop's own `onDelivery`. A live [subscribe]
 * passes straight through, since it delivers as messages are sent.
 */
class BatchingCordnSyncSource(
    private val delegate: CordnSyncSource,
    private val fileMessages: (gid: String, messages: List<CordnDeliveredMessage>) -> Unit,
    private val batchSize: Int = CordnDeliveryBatcher.DEFAULT_BATCH_SIZE,
) : CordnSyncSource {
    override val gids: StateFlow<Set<String>> get() = delegate.gids

    override suspend fun catchUp(onDelivery: (CordnGroupManager.Delivery) -> Unit): Int {
        val batcher = CordnDeliveryBatcher(fileMessages, onDelivery, batchSize)
        // finally: what the catch-up read is already stored and its ratchet moved on, so a
        // catch-up that fails or is cancelled halfway must still show what it got.
        try {
            return delegate.catchUp(batcher::offer)
        } finally {
            batcher.flushAll()
        }
    }

    override suspend fun subscribe(
        timeoutMs: Long,
        onDelivery: (CordnGroupManager.Delivery) -> Unit,
    ) = delegate.subscribe(timeoutMs, onDelivery)
}
