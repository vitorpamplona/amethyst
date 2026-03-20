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
package com.vitorpamplona.quartz.nip01Core.relay.server

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.verify
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

/**
 * This class manages per-connection subscriptions as coroutines. Each
 * subscription ([REQ]) launches a child coroutine that first replays stored
 * events matching the filters, sends EOSE, and then streams live events.
 * Closing a subscription ([CLOSE]) immediately cancels its coroutine.
 *
 * The server is transport-agnostic: callers feed incoming JSON via
 * [processMessage] and receive outgoing JSON via the [send] callback
 * provided to [connect]. This allows use with any WebSocket library.
 *
 * @param store The [EventStore] backing this relay.
 * @param relayUrl The URL of this relay, used for NIP-42 authentication.
 * @param requireAuth When true, clients must authenticate (NIP-42) before
 *                    sending EVENT, REQ, or COUNT commands.
 * @param verify Validates incoming events. Defaults to cryptographic
 *                      verification (id + signature). Override for testing.
 */
class NostrServer(
    private val store: IEventStore,
    val relayUrl: NormalizedRelayUrl = NormalizedRelayUrl("wss://relay.example.com/"),
    val requireAuth: Boolean = false,
    private val parentContext: CoroutineContext = SupervisorJob(),
    private val verify: (Event) -> Boolean = { it.verify() },
) {
    private val subStore = LiveEventStore(store)

    private val scope = CoroutineScope(parentContext + SupervisorJob())

    /** Active client sessions keyed by an opaque connection id. */
    private val connections = LargeCache<Int, RelaySession>()

    /**
     * Registers a new client connection.
     *
     * @param send Callback the server uses to send JSON messages to this client.
     *             Implementations must be safe to call from any coroutine.
     */
    fun connect(send: (String) -> Unit) =
        RelaySession(
            server = this,
            store = subStore,
            verify = verify,
            scope = scope,
            onSend = send,
            onClose = ::disconnect,
        ).also { session -> connections.put(session.hashCode(), session) }

    /**
     * Removes a client connection and cancels all its subscriptions.
     */
    private fun disconnect(session: RelaySession) {
        connections.remove(session.hashCode())
    }

    /**
     * Shuts down the server, cancelling all subscriptions and sessions.
     */
    fun shutdown() {
        connections.forEach { id, session -> session.cancelAllSubscriptions() }
        connections.clear()
        scope.cancel()
    }
}
