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
import com.vitorpamplona.quartz.nip01Core.relay.server.policies.LimitsPolicy
import com.vitorpamplona.quartz.nip77Negentropy.NegentropySettings
import com.vitorpamplona.quartz.utils.cache.LargeCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
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
 * @param listener Observability hook fired as connections open and close,
 *   keyed by [RelaySession.id]. Defaults to a no-op.
 * @param limits Operational limits enforced on every connection (per-command
 *   via a composed [LimitsPolicy], plus the session-level message-size and
 *   subscription caps) and advertised via [RelayLimits.toNip11Limitation].
 *   Null disables limit enforcement.
 */
@OptIn(ExperimentalAtomicApi::class)
class ReqResponderServer(
    responder: ReqResponder,
    private val policyBuilder: () -> IRelayPolicy = { EmptyPolicy },
    parentContext: CoroutineContext = SupervisorJob(),
    private val negentropySettings: NegentropySettings = NegentropySettings.Default,
    private val listener: RelayServerListener = RelayServerListener.None,
    val limits: RelayLimits? = null,
) : AutoCloseable {
    /** Scope for all subscriptions. */
    private val scope = CoroutineScope(parentContext + SupervisorJob())

    private val backend = ReqResponderBackend(responder)

    /** Live count of registered connections; backs [activeConnections]. */
    private val activeCount = AtomicLong(0L)

    /** Active client sessions keyed by [RelaySession.id]. */
    private val connections = LargeCache<Long, RelaySession>()

    /** Number of connections currently registered with this server. */
    val activeConnections: Long get() = activeCount.load()

    private fun buildPolicy(): IRelayPolicy {
        val base = policyBuilder()
        return if (limits != null) LimitsPolicy(limits) + base else base
    }

    /**
     * Registers a new client connection.
     *
     * @param send Callback the server uses to send JSON messages to this client.
     *             Implementations must be safe to call from any coroutine.
     */
    fun connect(send: (String) -> Unit): RelaySession {
        val session =
            RelaySession(
                policy = buildPolicy(),
                store = backend,
                scope = scope,
                onSend = send,
                onClose = { closed ->
                    // Idempotent teardown accounting (see NostrServer.connect).
                    if (connections.remove(closed.id) != null) {
                        activeCount.addAndFetch(-1L)
                        listener.onDisconnect(closed.id)
                    }
                },
                negentropySettings = negentropySettings,
            )
        connections.put(session.id, session)
        activeCount.addAndFetch(1L)
        listener.onConnect(session.id)
        return session
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
        connections.forEach { _, session ->
            session.cancelAllSubscriptions()
            listener.onDisconnect(session.id)
        }
        connections.clear()
        activeCount.store(0L)
        scope.cancel()
    }
}
