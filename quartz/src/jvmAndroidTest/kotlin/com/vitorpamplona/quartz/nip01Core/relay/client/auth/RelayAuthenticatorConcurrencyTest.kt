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
package com.vitorpamplona.quartz.nip01Core.relay.client.auth

import com.vitorpamplona.quartz.nip01Core.relay.client.EmptyNostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.listeners.RelayConnectionListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.IRelayClient
import com.vitorpamplona.quartz.nip01Core.relay.commands.toRelay.Command
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.test.Test

/**
 * Reproduces issue #2946 — `ClassCastException: LinkedHashMap$Entry cannot be
 * cast to HashMap$TreeNode` thrown from
 * `RelayAuthenticator$clientListener.onDisconnected`.
 *
 * OkHttp dispatches WebSocket callbacks on one thread per relay, so when many
 * relays connect/disconnect simultaneously the listener's internal map is
 * mutated concurrently. Once a bucket exceeds the HashMap TREEIFY_THRESHOLD (8)
 * the concurrent treeification corrupts internal state.
 *
 * On the buggy code this test fails non-deterministically with a
 * `ClassCastException` (or `ConcurrentModificationException` /
 * `NullPointerException`). After the fix it must pass cleanly every run.
 */
class RelayAuthenticatorConcurrencyTest {
    private class CapturingClient(
        private val delegate: INostrClient = EmptyNostrClient(),
    ) : INostrClient by delegate {
        @Volatile var captured: RelayConnectionListener? = null

        override fun addConnectionListener(listener: RelayConnectionListener) {
            captured = listener
        }
    }

    private class FakeRelayClient(
        override val url: NormalizedRelayUrl,
    ) : IRelayClient {
        override fun connect() = Unit

        override fun needsToReconnect() = false

        override fun connectAndSyncFiltersIfDisconnected(ignoreRetryDelays: Boolean) = Unit

        override fun isConnected() = false

        override fun sendOrConnectAndSync(cmd: Command) = Unit

        override fun sendIfConnected(cmd: Command) = Unit

        override fun disconnect() = Unit
    }

    @Test
    fun concurrentConnectingAndDisconnecting_doesNotCorruptInternalState() {
        runBlocking {
            // The race only fires while the underlying HashMap is structurally
            // growing — rehashing and bucket treeification. Once the map reaches
            // its steady-state size, put/remove on existing keys touch a single
            // node and won't reproduce. So drive many short "burst" cycles, each
            // starting from an empty map and growing it past
            // MIN_TREEIFY_CAPACITY (64) under concurrent load.
            repeat(50) { burst ->
                val client = CapturingClient()
                val authenticator =
                    RelayAuthenticator(
                        client = client,
                        signWithAllLoggedInUsers = { emptyList() },
                    )
                val listener =
                    client.captured
                        ?: error("RelayAuthenticator did not register a listener")

                val relays =
                    (0 until 256).map {
                        FakeRelayClient(NormalizedRelayUrl("wss://relay-$burst-$it.example/"))
                    }

                withContext(Dispatchers.IO) {
                    (0 until 64)
                        .map { workerId ->
                            async {
                                // Each worker walks the relay set, connecting and
                                // disconnecting. Connects grow the map (rehash /
                                // treeify); disconnects shrink it; concurrent reads
                                // run alongside.
                                relays.forEachIndexed { idx, relay ->
                                    if ((workerId + idx) and 1 == 0) {
                                        listener.onConnecting(relay)
                                    } else {
                                        listener.onDisconnected(relay)
                                    }
                                    authenticator.hasFinishedAuthentication(relay.url)
                                }
                            }
                        }.awaitAll()
                }
            }
        }
    }
}
