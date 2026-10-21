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
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerSync
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An event in flight when its relay's socket fails is re-sent on the next connection. The failure
 * used to reach the outbox as the relay's refusal, so the event was dropped at the first failed
 * socket and the publisher heard nothing until its own timeout: a bulk writer whose relay restarted
 * lost its whole window.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NostrClientOutboxReconnectTest {
    private val url = NormalizedRelayUrl("wss://restarting.example.com/")
    private val event: Event = NostrSignerSync().sign(1_700_000_000L, 1, emptyArray(), "hello")

    /** Each socket it builds, with what was sent on it. */
    private class RecordingBuilder : WebsocketBuilder {
        val sent = mutableListOf<MutableList<String>>()
        lateinit var lastListener: WebSocketListener

        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket {
            lastListener = out
            val mine = mutableListOf<String>().also { sent += it }
            return object : WebSocket {
                override fun needsReconnect() = false

                override fun connect() {}

                override fun disconnect() {}

                override fun send(msg: String): Boolean {
                    mine += msg
                    return true
                }
            }
        }
    }

    private fun TestScope.settle() {
        advanceTimeBy(1_000)
        runCurrent()
    }

    @Test
    fun anEventInFlightWhenTheSocketFailsIsResentOnTheNextConnection() =
        runTest {
            val builder = RecordingBuilder()
            val client = NostrClient(builder, this)
            try {
                settle()
                client.publish(event, setOf(url))
                runCurrent()
                builder.lastListener.onOpen(50, false)
                runCurrent()
                assertTrue(builder.sent[0].any { event.id in it }, "sent on the first socket")

                // The relay dies with the event unanswered: a reset, not a close it chose.
                builder.lastListener.onFailure(IOException("Connection reset"), null, null)
                runCurrent()
                assertEquals(1, client.activeOutboxCache(url).size, "still pending: the relay never answered it")

                // The per-relay backoff gate reads wall-clock seconds; the sweep runs on the test clock.
                Thread.sleep(3_500)
                advanceTimeBy(61_000)
                runCurrent()
                assertTrue(builder.sent.size > 1, "the relay is dialed again for what it is still owed")
                builder.lastListener.onOpen(50, false)
                runCurrent()
                assertTrue(builder.sent.last().any { event.id in it }, "and the event goes out on the new socket")

                runBlocking { builder.lastListener.onMessage("""["OK","${event.id}",true,""]""") }
                runCurrent()
                assertEquals(0, client.activeOutboxCache(url).size, "the answer on the new socket ends it")
            } finally {
                client.close()
            }
        }
}
