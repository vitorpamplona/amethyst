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

import com.vitorpamplona.quartz.nip01Core.relay.server.policies.VerifyPolicy
import com.vitorpamplona.quartz.nip01Core.store.IEventStore
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

/**
 * Represents a Nostr relay server that manages client connections, event storage, and verification.
 *
 * This class acts as the central coordinator for a relay server, handling the lifecycle of [RelaySession]s
 * and providing access to the underlying event store.
 *
 * @param store The [IEventStore] backing this relay.
 * @param policyBuilder Controls requirements for relay commands.
 */
class NostrServer(
    private val store: IEventStore,
    private val policyBuilder: () -> IRelayPolicy = { VerifyPolicy },
    private val parentContext: CoroutineContext = SupervisorJob(),
) {
    private val subStore = LiveEventStore(store)

    /** Scope for all subscriptions. */
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
            policy = policyBuilder(),
            store = subStore,
            scope = scope,
            onSend = send,
            onClose = { session ->
                connections.remove(session.hashCode())
            },
        ).also { session ->
            connections.put(session.hashCode(), session)
        }

    /**
     * Shuts down the server, cancelling all subscriptions and sessions.
     */
    fun shutdown() {
        connections.forEach { _, session -> session.cancelAllSubscriptions() }
        connections.clear()
        scope.cancel()
    }
}
