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

import com.vitorpamplona.quartz.nip01Core.relay.server.policies.EmptyPolicy
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.coroutines.CoroutineContext

/**
 * A transport-agnostic relay engine for relays that answer REQs from a
 * [ReqResponder] instead of an event store — search relays, redirectors that
 * forward to an HTTP backend, and relays that emit computed/projected data.
 *
 * This is the storage-free sibling of [NostrServer]. It owns the full NIP-01
 * wire protocol — challenge/auth, command parsing, the [IRelayPolicy], EVENT/
 * EOSE/CLOSED framing, and subscription lifecycle — so callers only provide a
 * per-connection `send` callback and feed it raw JSON frames, exactly like
 * [NostrServer]. EVENT publishes are rejected (there is nothing to store) and
 * negentropy is disabled, per [ReqResponderBackend] / [SessionBackend].
 *
 * ```
 * val server = ReqResponderServer(
 *     responder = SearchResponder(searchApi),
 *     policyBuilder = { FullAuthPolicy(relay) }, // optional NIP-42 gating
 * )
 *
 * // per WebSocket connection:
 * server.serve(send = { json -> launch { socket.send(json) } }) { session ->
 *     for (frame in incoming) session.receive(frame.text)
 * }
 * ```
 *
 * Both this class and [RelaySession] implement [AutoCloseable].
 *
 * @param responder Produces the events that answer each REQ.
 * @param policyBuilder Builds a fresh [IRelayPolicy] per connection. Defaults to
 *   [EmptyPolicy] (accept REQ/COUNT, no signature verification — appropriate for
 *   a read-only relay). Pass a [com.vitorpamplona.quartz.nip01Core.relay.server.policies.FullAuthPolicy]
 *   to gate access behind NIP-42.
 * @param parentContext Parent coroutine context for all subscriptions.
 * @param negentropySettings NIP-77 tuning. Negentropy is effectively a no-op
 *   here (the snapshot is empty) but the setting is plumbed for symmetry.
 */
class ReqResponderServer(
    responder: ReqResponder,
    private val policyBuilder: () -> IRelayPolicy = { EmptyPolicy },
    parentContext: CoroutineContext = SupervisorJob(),
    private val negentropySettings: NegentropySettings = NegentropySettings.Default,
) : AutoCloseable {
    /** Scope for all subscriptions. */
    private val scope = CoroutineScope(parentContext + SupervisorJob())

    private val backend = ReqResponderBackend(responder)

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
            store = backend,
            scope = scope,
            onSend = send,
            onClose = { session ->
                connections.remove(session.hashCode())
            },
            negentropySettings = negentropySettings,
        ).also { session ->
            connections.put(session.hashCode(), session)
        }

    /**
     * Registers a new client connection and serves it for the duration of
     * [incoming]. The session is automatically closed when [incoming] returns.
     *
     * @param send Callback the server uses to send JSON messages to this client.
     * @param incoming Suspend block that yields raw JSON strings from the client
     *                 (e.g., reading WebSocket text frames in a loop).
     */
    suspend fun serve(
        send: (String) -> Unit,
        incoming: suspend (RelaySession) -> Unit,
    ) {
        val session = connect(send)
        try {
            incoming(session)
        } finally {
            session.close()
        }
    }

    /** Shuts down the server, cancelling all subscriptions. */
    override fun close() {
        connections.forEach { _, session -> session.cancelAllSubscriptions() }
        connections.clear()
        scope.cancel()
    }
}
