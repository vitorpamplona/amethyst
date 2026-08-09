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
package com.vitorpamplona.quartz.nip01Core.relay.server.inprocess

import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.relay.server.NostrServer
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy
import com.vitorpamplona.quartz.nip01Core.relay.sockets.WebSocketListener
import com.vitorpamplona.quartz.nip01Core.store.sqlite.EventStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the [WebSocketListener] contract on the in-process transport against a
 * server whose policy sends from inside `onConnect` — [FullAuthPolicy] pushes
 * its AUTH challenge synchronously while `server.connect` is still on the
 * stack, before the socket has stored its own state.
 *
 * Both tests reproduce (pre-fix, deterministically) the CI-only stall in
 * geode's Nip42AuthDmDeliveryTest: the challenge used to be delivered before
 * `onOpen` and before `incoming` was assigned, so a listener that answered it
 * concurrently (RelayAuthenticator signs on its own coroutine) could call
 * [InProcessWebSocket.send] on a half-built socket, get `false`, and lose the
 * AUTH reply forever — the challenge is dedup'd as already-answered, an EVENT
 * rejected `auth-required:` never re-triggers auth, and the DM never lands.
 */
class InProcessWebSocketTest {
    private val relayUrl = NormalizedRelayUrl("wss://relay.example.com/")

    private fun newServer() =
        NostrServer(
            store = EventStore(null),
            policyBuilder = { FullAuthPolicy(relayUrl) },
        )

    @Test
    fun onOpenPrecedesEveryMessage() =
        runTest {
            withContext(Dispatchers.Default) {
                val server = newServer()
                val callbacks = Channel<String>(UNLIMITED)

                val listener =
                    object : WebSocketListener {
                        override fun onOpen(
                            pingMillis: Int,
                            compression: Boolean,
                        ) {
                            callbacks.trySend("open")
                        }

                        override suspend fun onMessage(text: String) {
                            callbacks.trySend("message")
                        }

                        override fun onClosed(
                            code: Int,
                            reason: String,
                        ) {
                        }

                        override fun onFailure(
                            t: Throwable,
                            code: Int?,
                            response: String?,
                        ) {
                        }
                    }

                val socket = InProcessWebSocket(server, listener)
                try {
                    socket.connect()

                    // FullAuthPolicy sends its AUTH challenge at connect time, so both
                    // callbacks are guaranteed to arrive; the contract is their order.
                    val received = mutableListOf<String>()
                    withTimeout(5_000) {
                        while ("message" !in received) received.add(callbacks.receive())
                    }

                    assertEquals(
                        "open",
                        received.first(),
                        "the connect-time AUTH challenge must not be delivered before onOpen; got $received",
                    )
                } finally {
                    socket.disconnect()
                    server.close()
                }
            }
        }

    @Test
    fun replySentFromChallengeHandlerIsNotDropped() =
        runTest {
            withContext(Dispatchers.Default) {
                val server = newServer()

                var socket: InProcessWebSocket? = null
                val replyAccepted = Channel<Boolean>(UNLIMITED)

                val listener =
                    object : WebSocketListener {
                        override fun onOpen(
                            pingMillis: Int,
                            compression: Boolean,
                        ) {
                        }

                        override suspend fun onMessage(text: String) {
                            // Answer the AUTH challenge immediately, the way
                            // RelayAuthenticator does. The socket must be fully
                            // wired by the time any server frame is delivered,
                            // so this send must be accepted — a `false` here is
                            // a silently lost AUTH and a dead NIP-42 handshake.
                            replyAccepted.trySend(socket?.send("""["CLOSE","probe"]""") == true)
                        }

                        override fun onClosed(
                            code: Int,
                            reason: String,
                        ) {
                        }

                        override fun onFailure(
                            t: Throwable,
                            code: Int?,
                            response: String?,
                        ) {
                        }
                    }

                val s = InProcessWebSocket(server, listener)
                socket = s
                try {
                    s.connect()

                    val accepted = withTimeout(5_000) { replyAccepted.receive() }

                    assertTrue(
                        accepted,
                        "a reply sent from the first onMessage must reach the server, not be dropped",
                    )
                } finally {
                    s.disconnect()
                    server.close()
                }
            }
        }
}
