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
package com.vitorpamplona.quartz.nip01Core.relay.client.pool

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A relay that cannot be reached owes the outbox nothing yet: what was pending for it stays, to be
 * re-sent on the next connection, until it has failed [PoolEventOutboxState.MAX_CONNECT_FAILURES]
 * times in a row. Only then is it given up on, and that is what [PoolEventOutbox.onCannotConnect]
 * hands back for the client to report.
 */
class PoolEventOutboxCannotConnectTest {
    private val down = NormalizedRelayUrl("wss://down.relay.test")
    private val up = NormalizedRelayUrl("wss://up.relay.test")

    private fun event(i: Int) =
        Event(
            id = i.toString(16).padStart(64, '0'),
            pubKey = "00".repeat(32),
            createdAt = 1_700_000_000L,
            kind = 1,
            tags = emptyArray(),
            content = "hello",
            sig = "00".repeat(64),
        )

    @Test
    fun `a failed connection keeps every event pending for that relay`() {
        val outbox = PoolEventOutbox()
        (1..50).forEach { outbox.markAsSending(event(it), setOf(down)) }
        assertTrue(outbox.onCannotConnect(down).isEmpty(), "nothing is given up on one failure")
        assertEquals(50, outbox.activeOutboxCacheFor(down).size, "all fifty are still there to re-send")
    }

    @Test
    fun `a relay that keeps failing is given up on, and only for its own events`() {
        val outbox = PoolEventOutbox()
        outbox.markAsSending(event(1), setOf(down))
        outbox.markAsSending(event(2), setOf(down, up))
        repeat(PoolEventOutboxState.MAX_CONNECT_FAILURES - 1) { assertTrue(outbox.onCannotConnect(down).isEmpty()) }
        val gaveUp = outbox.onCannotConnect(down).map { it.id }.toSet()
        assertEquals(setOf(event(1).id, event(2).id), gaveUp, "both were owed to the relay that never came back")
        assertTrue(outbox.activeOutboxCacheFor(down).isEmpty())
        assertEquals(setOf(event(2).id), outbox.activeOutboxCacheFor(up), "the other relay is still owed its copy")
    }
}
