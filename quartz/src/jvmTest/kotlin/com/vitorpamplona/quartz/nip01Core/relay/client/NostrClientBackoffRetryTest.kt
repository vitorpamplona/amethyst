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
package com.vitorpamplona.quartz.nip01Core.relay.client

import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.FakeWebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A relay that drops soon after it was dialed is held by its backoff (1s, doubling), so the
 * reconnect [NostrClient.onDisconnected] asks for at once is refused. It must be asked again
 * when that backoff ends, not on the next keep-alive sweep a minute later: a subscription's
 * REQ, or a publish waiting on its OK, waited that whole minute.
 *
 * Coroutine timers run on the virtual clock; the backoff gate compares real wall seconds,
 * hence the short real sleep (see [NostrClientKeepAliveTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NostrClientBackoffRetryTest {
    private val url = NormalizedRelayUrl("wss://backoff.example.com")

    @Test
    fun aDroppedRelayIsRedialedWhenItsBackoffEndsNotAtTheNextSweep() =
        runTest {
            val builder = FakeWebsocketBuilder()
            val client = NostrClient(builder, this)
            try {
                advanceTimeBy(500)
                runCurrent()

                client.subscribe("sub", mapOf(url to listOf(Filter())))
                assertEquals(1, builder.connectAttempts)

                // Dropped right after the dial: the immediate reconnect meets a closed gate.
                builder.lastListener.onOpen(50, false)
                builder.lastListener.onClosed(1000, "server closed")
                advanceTimeBy(500)
                runCurrent()
                assertEquals(1, builder.connectAttempts, "the backoff gate is still closed")

                // The gate opens three wall seconds after the dial; far short of the 60s sweep.
                Thread.sleep(3_500)
                advanceTimeBy(4_000)
                runCurrent()
                assertEquals(2, builder.connectAttempts, "redialed when the backoff ended")

                builder.lastListener.onOpen(50, false)
            } finally {
                client.close()
            }
        }

    @Test
    fun noRetryFiresOnceTheClientIsInactive() =
        runTest {
            val builder = FakeWebsocketBuilder()
            val client = NostrClient(builder, this)
            try {
                advanceTimeBy(500)
                runCurrent()

                client.subscribe("sub", mapOf(url to listOf(Filter())))
                builder.lastListener.onOpen(50, false)
                builder.lastListener.onClosed(1000, "server closed")
                advanceTimeBy(500)
                runCurrent()

                // Offline: the host puts the client down, and the pending retry must not dial.
                client.disconnect()
                val baseline = builder.connectAttempts
                Thread.sleep(3_500)
                advanceTimeBy(4_000)
                runCurrent()
                assertEquals(baseline, builder.connectAttempts)
            } finally {
                client.close()
            }
        }
}
