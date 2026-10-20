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

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.relay.client.single.basic.FakeWebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [NostrClient.keepConnected]: a kept relay is dialed with nothing else wanting it, stays in the
 * pool after its last publish is answered, is redialed after the relay hangs up, and is let go
 * once its last hold is released. The first test is the control: without a hold the same publish
 * leaves the pool, which is what the holds exist to stop.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NostrClientKeepConnectedTest {
    private val url = NormalizedRelayUrl("wss://kept.example.com/")
    private val event: Event = NostrSignerSync().sign(1_700_000_000L, 1, emptyArray(), "hello")

    /** Past the 300ms pool sample, so a relay nothing wants has been dropped. */
    private fun TestScope.letThePoolSettle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    private fun TestScope.publishAndAnswer(
        client: NostrClient,
        builder: FakeWebsocketBuilder,
    ) {
        client.publish(event, setOf(url))
        runCurrent()
        kotlinx.coroutines.runBlocking { builder.lastListener.onMessage("""["OK","${event.id}",true,""]""") }
        runCurrent()
    }

    @Test
    fun aRelayNothingWantsLeavesThePoolOnceItsPublishIsAnswered() =
        runTest {
            val builder = FakeWebsocketBuilder()
            val client = NostrClient(builder, this)
            try {
                letThePoolSettle()
                client.publish(event, setOf(url))
                runCurrent()
                assertEquals(1, builder.connectAttempts)
                builder.lastListener.onOpen(50, false)
                kotlinx.coroutines.runBlocking { builder.lastListener.onMessage("""["OK","${event.id}",true,""]""") }
                letThePoolSettle()
                assertFalse(url in client.availableRelaysFlow().value, "answered and unwanted, the relay leaves the pool")
            } finally {
                client.close()
            }
        }

    @Test
    fun aKeptRelayIsDialedHeldThroughQuietAndRedialedAfterAHangUp() =
        runTest {
            val builder = FakeWebsocketBuilder()
            val client = NostrClient(builder, this)
            try {
                letThePoolSettle()
                client.keepConnected(url)
                letThePoolSettle()
                assertEquals(1, builder.connectAttempts, "a hold alone dials the relay")
                builder.lastListener.onOpen(50, false)

                publishAndAnswer(client, builder)
                letThePoolSettle()
                assertTrue(url in client.availableRelaysFlow().value, "a kept relay outlives its last answered publish")
                assertEquals(1, builder.connectAttempts, "and keeps the socket it had")

                builder.lastListener.onClosed(1000, "server closed")
                // The per-relay backoff gate reads wall-clock seconds; the sweep runs on the test clock.
                Thread.sleep(3_500)
                advanceTimeBy(61_000)
                runCurrent()
                assertTrue(builder.connectAttempts > 1, "a kept relay that hung up is redialed")
            } finally {
                client.close()
            }
        }

    @Test
    fun theRelayIsLetGoOnlyWhenItsLastHoldIsReleased() =
        runTest {
            val builder = FakeWebsocketBuilder()
            val client = NostrClient(builder, this)
            try {
                letThePoolSettle()
                client.keepConnected(url)
                client.keepConnected(url)
                letThePoolSettle()
                builder.lastListener.onOpen(50, false)

                client.releaseConnection(url)
                letThePoolSettle()
                assertTrue(url in client.availableRelaysFlow().value, "one hold is left")

                client.releaseConnection(url)
                letThePoolSettle()
                assertFalse(url in client.availableRelaysFlow().value, "no hold and nothing else wants it")

                // A release with no hold left is a no-op, not a negative count a later hold must repay.
                client.releaseConnection(url)
                client.keepConnected(url)
                letThePoolSettle()
                assertTrue(url in client.availableRelaysFlow().value)
            } finally {
                client.close()
            }
        }
}
