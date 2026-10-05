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
package com.vitorpamplona.quartz.nip01Core.relay

import com.vitorpamplona.geode.InProcessRelays
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.accessories.publishAndConfirm
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocket
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebsocketBuilder
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.measureTimedValue

/**
 * Marmot only needs one relay to accept a commit, but every commit waited for the slowest relay in
 * the group's scope. One relay that never answers cost each commit the whole timeout, so creating a
 * group (two commits) or adding a member took about a minute while plain messages stayed fast.
 */
class PublishUntilFirstAcceptTest {
    private val hub = InProcessRelays()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val silent: NormalizedRelayUrl = RelayUrlNormalizer.normalize("ws://127.0.0.1:7771/")

    @AfterTest
    fun tearDown() {
        scope.cancel()
        hub.close()
    }

    /** The hub, except [silent] takes every EVENT and never answers it. */
    private inner class OneSilentRelay : WebsocketBuilder {
        override fun build(
            url: NormalizedRelayUrl,
            out: WebSocketListener,
        ): WebSocket {
            val inner = hub.build(url, out)
            if (url != silent) return inner
            return object : WebSocket by inner {
                override fun send(msg: String): Boolean = if (msg.startsWith("[\"EVENT\"")) true else inner.send(msg)
            }
        }
    }

    private fun publishTo(untilFirstAccept: Boolean) =
        runBlocking {
            val client = NostrClient(OneSilentRelay(), scope)
            val event = NostrSignerInternal(KeyPair()).sign(TextNoteEvent.build("one accept is enough"))
            val timed =
                measureTimedValue {
                    client.publishAndConfirm(
                        event = event,
                        relayList = setOf(InProcessRelays.DEFAULT_URL, silent),
                        timeoutInSeconds = 6,
                        untilFirstAccept = untilFirstAccept,
                    )
                }
            client.disconnect()
            timed
        }

    @Test
    fun returnsAtTheFirstAcceptInsteadOfWaitingForTheSilentRelay() {
        val (accepted, elapsed) = publishTo(untilFirstAccept = true)
        assertTrue(accepted, "the healthy relay accepted")
        assertTrue(elapsed.inWholeMilliseconds < 3_000, "returned at the first OK, not the 6s timeout (took $elapsed)")
    }

    @Test
    fun byDefaultStillWaitsForEveryRelay() {
        val (accepted, elapsed) = publishTo(untilFirstAccept = false)
        assertTrue(accepted, "the healthy relay accepted")
        assertTrue(elapsed.inWholeMilliseconds >= 5_500, "the default keeps waiting for the silent relay (took $elapsed)")
    }
}
